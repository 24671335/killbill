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

package com.ning.billing.analytics;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessAccountSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceSqlDao;
import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.payment.PaymentInternalApi;

public class BusinessInvoicePaymentDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessInvoicePaymentDao.class);

    private final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;
    private final AccountInternalApi accountApi;
    private final InvoiceInternalApi invoiceApi;
    private final PaymentInternalApi paymentApi;
    private final Clock clock;
    private final BusinessInvoiceDao invoiceDao;
    private final BusinessAccountDao accountDao;

    @Inject
    public BusinessInvoicePaymentDao(final BusinessInvoicePaymentSqlDao invoicePaymentSqlDao, final AccountInternalApi accountApi,
                                     final InvoiceInternalApi invoiceApi, final PaymentInternalApi paymentApi,
                                     final Clock clock, final BusinessInvoiceDao invoiceDao, final BusinessAccountDao accountDao) {
        this.invoicePaymentSqlDao = invoicePaymentSqlDao;
        this.accountApi = accountApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.invoiceDao = invoiceDao;
        this.accountDao = accountDao;
    }

    public void invoicePaymentPosted(final UUID accountId, @Nullable final UUID paymentId, final String message, final InternalCallContext context) {
        // Payment attempt with no default payment method. Ignore.
        if (paymentId == null) {
            return;
        }

        final Account account;
        try {
            account = accountApi.getAccountById(accountId, context);
        } catch (AccountApiException e) {
            log.warn("Ignoring payment {}: account {} does not exist", paymentId, accountId);
            return;
        }

        final Payment payment;
        try {
            payment = paymentApi.getPayment(paymentId, context);
        } catch (PaymentApiException e) {
            log.warn("Ignoring payment {}: payment does not exist", paymentId);
            return;
        }

        PaymentMethod paymentMethod = null;
        try {
            paymentMethod = paymentApi.getPaymentMethodById(payment.getPaymentMethodId(), true, context);
        } catch (PaymentApiException e) {
            log.info("For payment {}: payment method {} does not exist", paymentId, payment.getPaymentMethodId());
        }

        Invoice invoice = null;
        InvoicePayment invoicePayment = null;
        try {
            invoicePayment = invoiceApi.getInvoicePaymentForAttempt(paymentId, context);
            if (invoicePayment != null) {
                invoice = invoiceApi.getInvoiceById(invoicePayment.getInvoiceId(), context);
            }
        } catch (InvoiceApiException e) {
            log.warn("Unable to find invoice {} for payment {}",
                     invoicePayment != null ? invoicePayment.getInvoiceId() : "unknown", paymentId);
        }

        createPayment(account, invoice, invoicePayment, payment, paymentMethod, message, context);
    }

    private void createPayment(final Account account, @Nullable final Invoice invoice, @Nullable final InvoicePayment invoicePayment, final Payment payment,
                               @Nullable final PaymentMethod paymentMethod,
                               final String message, final InternalCallContext context) {
        // paymentMethod may be null if the payment method has been deleted
        final String cardCountry;
        final String cardType;
        final String paymentMethodString;
        if (paymentMethod != null) {
            final PaymentMethodPlugin pluginDetail = paymentMethod.getPluginDetail();
            cardCountry = PaymentMethodUtils.getCardCountry(pluginDetail);
            cardType = PaymentMethodUtils.getCardType(pluginDetail);
            paymentMethodString = PaymentMethodUtils.getPaymentMethodType(pluginDetail);
        } else {
            cardCountry = null;
            cardType = null;
            paymentMethodString = null;
        }

        // invoicePayment may be null on payment failures
        final String invoicePaymentType;
        final UUID linkedInvoicePaymentId;
        final DateTime createdDate;
        final DateTime updatedDate;
        if (invoicePayment != null) {
            invoicePaymentType = invoicePayment.getType().toString();
            linkedInvoicePaymentId = invoicePayment.getLinkedInvoicePaymentId();
            createdDate = invoicePayment.getCreatedDate();
            updatedDate = invoicePayment.getUpdatedDate();
        } else {
            invoicePaymentType = null;
            linkedInvoicePaymentId = null;
            // TODO PIERRE
            createdDate = clock.getUTCNow();
            updatedDate = createdDate;
        }

        final BusinessInvoicePaymentModelDao businessInvoicePayment = new BusinessInvoicePaymentModelDao(
                account.getExternalKey(),
                payment.getAmount(),
                cardCountry,
                cardType,
                createdDate,
                payment.getCurrency(),
                payment.getEffectiveDate(),
                payment.getInvoiceId(),
                message,
                payment.getId(),
                paymentMethodString,
                "Electronic",
                paymentMethod == null ? null : paymentMethod.getPluginName(),
                payment.getPaymentStatus().toString(),
                payment.getAmount(),
                updatedDate,
                invoicePaymentType,
                linkedInvoicePaymentId);

        // Update the account record
        final BusinessAccountModelDao bac = accountDao.createBusinessAccountFromAccount(account, context);

        // Make sure to limit the scope of the transaction to avoid InnoDB deadlocks
        invoicePaymentSqlDao.inTransaction(new Transaction<Void, BusinessInvoicePaymentSqlDao>() {
            @Override
            public Void inTransaction(final BusinessInvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                // Delete the existing payment if it exists - this is to make the call idempotent
                transactional.deleteInvoicePayment(payment.getId().toString(), context);

                // Create the bip record
                transactional.createInvoicePayment(businessInvoicePayment, context);

                if (invoice != null) {
                    // Update bin to get the latest invoice balance
                    final BusinessInvoiceSqlDao invoiceSqlDao = transactional.become(BusinessInvoiceSqlDao.class);
                    invoiceDao.rebuildInvoiceInTransaction(account.getExternalKey(), invoice, invoiceSqlDao, context);
                }

                // Update bac to get the latest account balance, total invoice balance, etc.
                final BusinessAccountSqlDao accountSqlDao = transactional.become(BusinessAccountSqlDao.class);
                accountDao.updateAccountInTransaction(bac, accountSqlDao, context);

                log.info("Added payment {}", businessInvoicePayment);
                return null;
            }
        });
    }
}
