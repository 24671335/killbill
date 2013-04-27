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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap.Builder;

public class InvoiceDaoHelper {


    /**
     * Find amounts to adjust for individual items, if not specified.
     * The user gives us a list of items to adjust associated with a given amount (how much to refund per invoice item).
     * In case of full adjustments, the amount can be null: in this case, we retrieve the original amount for the invoice
     * item.
     *
     * @param invoiceId                     original invoice id
     * @param entitySqlDaoWrapperFactory    the EntitySqlDaoWrapperFactory from the current transaction
     * @param invoiceItemIdsWithNullAmounts the original mapping between invoice item ids and amount to refund (contains null)
     * @param context                       the tenant context
     * @return the final mapping between invoice item ids and amount to refund
     * @throws com.ning.billing.invoice.api.InvoiceApiException
     *
     */
    public Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId,
                                                        final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                        final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                        final InternalTenantContext context) throws InvoiceApiException {
        // Populate the missing amounts for individual items, if needed
        final Builder<UUID, BigDecimal> invoiceItemIdsWithAmountsBuilder = new Builder<UUID, BigDecimal>();
        if (invoiceItemIdsWithNullAmounts.size() == 0) {
            return invoiceItemIdsWithAmountsBuilder.build();
        }

        // Retrieve invoice before the Refund
        final InvoiceModelDao invoice = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getById(invoiceId, context);
        if (invoice != null) {
            populateChildren(invoice, entitySqlDaoWrapperFactory, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for id " + invoiceId);
        }

        for (final UUID invoiceItemId : invoiceItemIdsWithNullAmounts.keySet()) {
            final BigDecimal adjAmount = Objects.firstNonNull(invoiceItemIdsWithNullAmounts.get(invoiceItemId),
                                                              getInvoiceItemAmountForId(invoice, invoiceItemId));
            final BigDecimal adjAmountRemainingAfterRepair = computeItemAdjustmentAmount(invoiceItemId, adjAmount, invoice.getInvoiceItems());
            if (adjAmountRemainingAfterRepair.compareTo(BigDecimal.ZERO) > 0) {
                invoiceItemIdsWithAmountsBuilder.put(invoiceItemId, adjAmountRemainingAfterRepair);
            }
        }

        return invoiceItemIdsWithAmountsBuilder.build();
    }

    /**
     * @param invoiceItem  item we are adjusting
     * @param requestedPositiveAmountToAdjust
     *                     amount we are adjusting for that item
     * @param invoiceItems list of all invoice items on this invoice
     * @return the amount we should really adjust based on whether or not the item got repaired
     */
    private BigDecimal computeItemAdjustmentAmount(final UUID invoiceItem, final BigDecimal requestedPositiveAmountToAdjust, final List<InvoiceItemModelDao> invoiceItems) {

        BigDecimal positiveRepairedAmount = BigDecimal.ZERO;

        final Collection<InvoiceItemModelDao> repairedItems = Collections2.filter(invoiceItems, new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(final InvoiceItemModelDao input) {
                return (input.getType() == InvoiceItemType.REPAIR_ADJ && input.getLinkedItemId().equals(invoiceItem));
            }
        });
        for (final InvoiceItemModelDao cur : repairedItems) {
            // Repair item are negative so we negate to make it positive
            positiveRepairedAmount = positiveRepairedAmount.add(cur.getAmount().negate());
        }
        return (positiveRepairedAmount.compareTo(requestedPositiveAmountToAdjust) >= 0) ? BigDecimal.ZERO : requestedPositiveAmountToAdjust.subtract(positiveRepairedAmount);
    }

    private BigDecimal getInvoiceItemAmountForId(final InvoiceModelDao invoice, final UUID invoiceItemId) throws InvoiceApiException {
        for (final InvoiceItemModelDao invoiceItem : invoice.getInvoiceItems()) {
            if (invoiceItem.getId().equals(invoiceItemId)) {
                return invoiceItem.getAmount();
            }
        }

        throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
    }

    public BigDecimal computePositiveRefundAmount(final InvoicePaymentModelDao payment, final BigDecimal requestedAmount, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final BigDecimal maxRefundAmount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        final BigDecimal requestedPositiveAmount = requestedAmount == null ? maxRefundAmount : requestedAmount;
        // This check is good but not enough, we need to also take into account previous refunds
        // (But that should have been checked in the payment call already)
        if (requestedPositiveAmount.compareTo(maxRefundAmount) > 0) {
            throw new InvoiceApiException(ErrorCode.REFUND_AMOUNT_TOO_HIGH, requestedPositiveAmount, maxRefundAmount);
        }

        // Verify if the requested amount matches the invoice items to adjust, if specified
        BigDecimal amountFromItems = BigDecimal.ZERO;
        for (final BigDecimal itemAmount : invoiceItemIdsWithAmounts.values()) {
            amountFromItems = amountFromItems.add(itemAmount);
        }

        // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
        if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && requestedPositiveAmount.compareTo(amountFromItems) != 0) {
            throw new InvoiceApiException(ErrorCode.REFUND_AMOUNT_DONT_MATCH_ITEMS_TO_ADJUST, requestedPositiveAmount, amountFromItems);
        }
        return requestedPositiveAmount;
    }


    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        return getUnpaidInvoicesByAccountFromTransaction(invoices, upToDate);
    }


    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final List<InvoiceModelDao> invoices, @Nullable final LocalDate upToDate) {
        final Collection<InvoiceModelDao> unpaidInvoices = Collections2.filter(invoices, new Predicate<InvoiceModelDao>() {
            @Override
            public boolean apply(final InvoiceModelDao in) {
                return (InvoiceModelDaoHelper.getBalance(in).compareTo(BigDecimal.ZERO) >= 1) && (upToDate == null || !in.getTargetDate().isAfter(upToDate));
            }
        });
        return new ArrayList<InvoiceModelDao>(unpaidInvoices);

    }

    /**
     * Create an adjustment for a given invoice item. This just creates the object in memory, it doesn't write it to disk.
     *
     * @param invoiceId         the invoice id
     * @param invoiceItemId     the invoice item id to adjust
     * @param effectiveDate     adjustment effective date, in the account timezone
     * @param positiveAdjAmount the amount to adjust. Pass null to adjust the full amount of the original item
     * @param currency          the currency of the amount. Pass null to default to the original currency used
     * @return the adjustment item
     */
    public InvoiceItemModelDao createAdjustmentItem(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID invoiceId, final UUID invoiceItemId,
                                                    final BigDecimal positiveAdjAmount, final Currency currency,
                                                    final LocalDate effectiveDate, final InternalCallContext context) throws InvoiceApiException {
        // First, retrieve the invoice item in question
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final InvoiceItemModelDao invoiceItemToBeAdjusted = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
        if (invoiceItemToBeAdjusted == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
        }

        // Validate the invoice it belongs to
        if (!invoiceItemToBeAdjusted.getInvoiceId().equals(invoiceId)) {
            throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_FOR_INVOICE_ITEM_ADJUSTMENT, invoiceItemId, invoiceId);
        }

        // Retrieve the amount and currency if needed
        final BigDecimal amountToAdjust = Objects.firstNonNull(positiveAdjAmount, invoiceItemToBeAdjusted.getAmount());
        // TODO - should we enforce the currency (and respect the original one) here if the amount passed was null?
        final Currency currencyForAdjustment = Objects.firstNonNull(currency, invoiceItemToBeAdjusted.getCurrency());

        // Finally, create the adjustment
        // Note! The amount is negated here!
        return new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.ITEM_ADJ, invoiceItemToBeAdjusted.getInvoiceId(), invoiceItemToBeAdjusted.getAccountId(),
                                       null, null, null, null, effectiveDate, effectiveDate, amountToAdjust.negate(), null, currencyForAdjustment, invoiceItemToBeAdjusted.getId());
    }

    /**
     * Create an invoice item
     *
     * @param entitySqlDaoWrapperFactory the EntitySqlDaoWrapperFactory from the current transaction
     * @param item                       the invoice item to create
     * @param context                    the call context
     */
    public void insertItem(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                           final InvoiceItemModelDao item,
                           final InternalCallContext context) throws EntityPersistenceException {
        final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        transInvoiceItemDao.create(item, context);
    }


    public void populateChildren(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
    }

    public void populateChildren(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
    }

    public List<InvoiceModelDao> getAllInvoicesByAccountFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getAllInvoicesByAccount(accountId.toString(), context);
        populateChildren(invoices, entitySqlDaoWrapperFactory, context);
        return invoices;
    }

    public BigDecimal getRemainingAmountPaidFromTransaction(final UUID invoicePaymentId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final BigDecimal amount = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getRemainingAmountPaid(invoicePaymentId.toString(), context);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void getInvoiceItemsWithinTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        for (final InvoiceModelDao invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        }
    }

    private void getInvoiceItemsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final String invoiceId = invoice.getId().toString();

        final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final List<InvoiceItemModelDao> items = transInvoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId, context);
        invoice.addInvoiceItems(items);
    }

    private void getInvoicePaymentsWithinTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        for (final InvoiceModelDao invoice : invoices) {
            getInvoicePaymentsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
        final String invoiceId = invoice.getId().toString();
        final List<InvoicePaymentModelDao> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId, context);
        invoice.addPayments(invoicePayments);
    }


}
