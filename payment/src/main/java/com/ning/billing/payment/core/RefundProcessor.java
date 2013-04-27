/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.payment.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.api.DefaultRefund;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.payment.dao.RefundModelDao;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.inject.name.Named;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class RefundProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final InvoiceInternalApi invoiceApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public RefundProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                           final AccountInternalApi accountApi,
                           final InvoiceInternalApi invoiceApi,
                           final InternalBus eventBus,
                           final InternalCallContextFactory internalCallContextFactory,
                           final TagInternalApi tagUserApi,
                           final PaymentDao paymentDao,
                           final GlobalLocker locker,
                           @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountApi, eventBus, paymentDao, tagUserApi, locker, executor);
        this.invoiceApi = invoiceApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    /**
     * Create a refund and adjust the invoice or invoice items as necessary.
     *
     * @param account                   account to refund
     * @param paymentId                 payment associated with that refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param isAdjusted                whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param context                   the call context
     * @return the created context
     * @throws PaymentApiException
     */
    public Refund createRefund(final Account account, final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                               final boolean isAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalCallContext context)
            throws PaymentApiException {

        return new WithAccountLock<Refund>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Refund>() {

            @Override
            public Refund doOperation() throws PaymentApiException {
                // First, compute the refund amount, if necessary
                final BigDecimal refundAmount = computeRefundAmount(paymentId, specifiedRefundAmount, invoiceItemIdsWithAmounts, context);

                try {
                    final PaymentModelDao payment = paymentDao.getPayment(paymentId, context);
                    if (payment == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentId);
                    }

                    final RefundModelDao refundInfo = new RefundModelDao(account.getId(), paymentId, refundAmount, account.getCurrency(), isAdjusted);
                    paymentDao.insertRefund(refundInfo, context);

                    final PaymentPluginApi plugin = getPaymentProviderPlugin(payment.getPaymentMethodId(), context);
                    plugin.processRefund(account.getId(), paymentId, refundAmount, account.getCurrency(), context.toCallContext());

                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PLUGIN_COMPLETED, context);

                    invoiceApi.createRefund(paymentId, refundAmount, isAdjusted, invoiceItemIdsWithAmounts, refundInfo.getId(), context);

                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.COMPLETED, context);

                    return new DefaultRefund(refundInfo.getId(), refundInfo.getCreatedDate(), refundInfo.getUpdatedDate(),
                                             paymentId, refundInfo.getAmount(), account.getCurrency(),
                                             isAdjusted, refundInfo.getCreatedDate());
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getErrorMessage());
                } catch (InvoiceApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    /**
     * Compute the refund amount (computed from the invoice or invoice items as necessary).
     *
     * @param paymentId                 payment id associated with this refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @return the refund amount
     */
    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                                           final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalTenantContext context)
            throws PaymentApiException {
        try {
            final List<InvoiceItem> items = invoiceApi.getInvoiceForPaymentId(paymentId, context).getInvoiceItems();

            BigDecimal amountFromItems = BigDecimal.ZERO;
            for (final UUID itemId : invoiceItemIdsWithAmounts.keySet()) {
                amountFromItems = amountFromItems.add(Objects.firstNonNull(invoiceItemIdsWithAmounts.get(itemId),
                                                                           getAmountFromItem(items, itemId)));
            }

            // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
            if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && specifiedRefundAmount != null && specifiedRefundAmount.compareTo(amountFromItems) != 0) {
                throw new IllegalArgumentException("You can't specify a refund amount that doesn't match the invoice items amounts");
            }

            return Objects.firstNonNull(specifiedRefundAmount, amountFromItems);
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }

        throw new IllegalArgumentException("Unable to find invoice item for id " + itemId);
    }

    public Refund getRefund(final UUID refundId, final boolean withPluginInfo /* not yet implemented */ , final InternalTenantContext context)
            throws PaymentApiException {
        RefundModelDao result = paymentDao.getRefund(refundId, context);
        if (result == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(Collections.singletonList(result));
        if (filteredInput.size() == 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }

        if (completePluginCompletedRefund(filteredInput, context)) {
            result = paymentDao.getRefund(refundId, context);
        }
        return new DefaultRefund(result.getId(), result.getCreatedDate(), result.getUpdatedDate(),
                                 result.getPaymentId(), result.getAmount(), result.getCurrency(),
                                 result.isAdjusted(), result.getCreatedDate());
    }

    public List<Refund> getAccountRefunds(final Account account, final InternalTenantContext context)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForAccount(account.getId(), context);
        if (completePluginCompletedRefund(result, context)) {
            result = paymentDao.getRefundsForAccount(account.getId(), context);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> getPaymentRefunds(final UUID paymentId, final InternalTenantContext context)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForPayment(paymentId, context);
        if (completePluginCompletedRefund(result, context)) {
            result = paymentDao.getRefundsForPayment(paymentId, context);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> toRefunds(final List<RefundModelDao> in) {
        return new ArrayList<Refund>(Collections2.transform(in, new Function<RefundModelDao, Refund>() {
            @Override
            public Refund apply(final RefundModelDao cur) {
                return new DefaultRefund(cur.getId(), cur.getCreatedDate(), cur.getUpdatedDate(),
                                         cur.getPaymentId(), cur.getAmount(), cur.getCurrency(),
                                         cur.isAdjusted(), cur.getCreatedDate());
            }
        }));
    }

    private List<RefundModelDao> filterUncompletedPluginRefund(final List<RefundModelDao> input) {
        return new ArrayList<RefundModelDao>(Collections2.filter(input, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() != RefundStatus.CREATED;
            }
        }));
    }

    private boolean completePluginCompletedRefund(final List<RefundModelDao> refunds, final InternalTenantContext tenantContext) throws PaymentApiException {

        final Collection<RefundModelDao> refundsToBeFixed = Collections2.filter(refunds, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() == RefundStatus.PLUGIN_COMPLETED;
            }
        });
        if (refundsToBeFixed.size() == 0) {
            return false;
        }

        try {

            // TODO context should be created for each refund and have the correct userToken
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(refundsToBeFixed.iterator().next().getId(), ObjectType.REFUND, "RefundProcessor",
                                                                                                     CallOrigin.INTERNAL, UserType.SYSTEM, null);

            final Account account = accountInternalApi.getAccountById(refundsToBeFixed.iterator().next().getAccountId(), context);
            new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {
                    try {
                        for (final RefundModelDao cur : refundsToBeFixed) {

                            // TODO - we currently don't save the items to be adjusted. If we crash, they won't be adjusted...
                            invoiceApi.createRefund(cur.getPaymentId(), cur.getAmount(), cur.isAdjusted(), ImmutableMap.<UUID, BigDecimal>of(), cur.getId(), context);
                            paymentDao.updateRefundStatus(cur.getId(), RefundStatus.COMPLETED, context);
                        }
                    } catch (InvoiceApiException e) {
                        throw new PaymentApiException(e);
                    }
                    return null;
                }
            });
            return true;
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }
}
