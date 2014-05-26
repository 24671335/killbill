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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.RefundStatus;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testRefund() {
        final UUID accountId = UUID.randomUUID();
        final UUID paymentId1 = UUID.randomUUID();
        final BigDecimal amount1 = new BigDecimal(13);
        final Currency currency = Currency.USD;

        final RefundModelDao refund1 = new RefundModelDao(accountId, paymentId1, amount1, currency, amount1, currency, true);

        paymentDao.insertRefund(refund1, internalCallContext);
        final RefundModelDao refundCheck = paymentDao.getRefund(refund1.getId(), internalCallContext);
        assertNotNull(refundCheck);
        assertEquals(refundCheck.getAccountId(), accountId);
        assertEquals(refundCheck.getPaymentId(), paymentId1);
        assertEquals(refundCheck.getAmount().compareTo(amount1), 0);
        assertEquals(refundCheck.getCurrency(), currency);
        assertEquals(refundCheck.isAdjusted(), true);
        assertEquals(refundCheck.getRefundStatus(), RefundStatus.CREATED);

        final BigDecimal amount2 = new BigDecimal(7.00);
        final UUID paymentId2 = UUID.randomUUID();

        RefundModelDao refund2 = new RefundModelDao(accountId, paymentId2, amount2, currency, amount2, currency, true);
        paymentDao.insertRefund(refund2, internalCallContext);
        paymentDao.updateRefundStatus(refund2.getId(), RefundStatus.COMPLETED, amount2, currency, internalCallContext);

        List<RefundModelDao> refundChecks = paymentDao.getRefundsForPayment(paymentId1, internalCallContext);
        assertEquals(refundChecks.size(), 1);

        refundChecks = paymentDao.getRefundsForPayment(paymentId2, internalCallContext);
        assertEquals(refundChecks.size(), 1);

        refundChecks = paymentDao.getRefundsForAccount(accountId, internalCallContext);
        assertEquals(refundChecks.size(), 2);
        for (RefundModelDao cur : refundChecks) {
            if (cur.getPaymentId().equals(paymentId1)) {
                assertEquals(cur.getAmount().compareTo(amount1), 0);
                assertEquals(cur.getRefundStatus(), RefundStatus.CREATED);
            } else if (cur.getPaymentId().equals(paymentId2)) {
                assertEquals(cur.getAmount().compareTo(amount2), 0);
                assertEquals(cur.getRefundStatus(), RefundStatus.COMPLETED);
            } else {
                fail("Unexpected refund");
            }
        }
    }

    @Test(groups = "slow")
    public void testUpdateStatus() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = null; // TODO STEPH_RETRY new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), paymentMethodId, effectiveDate, amount, currency);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithFirstAttempt(payment, attempt, internalCallContext);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);

        final PaymentStatus paymentStatus = PaymentStatus.SUCCESS;
        final String gatewayErrorCode = "OK";

        clock.addDays(1);
        paymentDao.updatePaymentAndAttemptOnCompletion(payment.getId(), paymentStatus, amount, currency, attempt.getId(), gatewayErrorCode, null, internalCallContext);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.SUCCESS);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 1);
        final PaymentAttemptModelDao savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        /*
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedAttempt.getGatewayErrorCode(), gatewayErrorCode);
        assertEquals(savedAttempt.getRequestedAmount().compareTo(amount), 0);
        */
    }

    @Test(groups = "slow")
    public void testPaymentWithAttempt() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = null; //new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), paymentMethodId, clock.getUTCNow(), amount, currency);

        PaymentModelDao savedPayment = paymentDao.insertPaymentWithFirstAttempt(payment, attempt, internalCallContext);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        PaymentAttemptModelDao savedAttempt = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(savedAttempt.getId(), attempt.getId());
        /*
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.UNKNOWN);
        */

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 1);
        savedAttempt = attempts.get(0);
        /*
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.UNKNOWN);
        */

    }

    @Test(groups = "slow")
    public void testNewAttempt() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao firstAttempt = null; //new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), paymentMethodId, effectiveDate, amount, currency);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithFirstAttempt(payment, firstAttempt, internalCallContext);

        final PaymentModelDao lastPayment = paymentDao.getLastPaymentForPaymentMethod(accountId, paymentMethodId, internalCallContext);
        assertNotNull(lastPayment);
        assertEquals(lastPayment.getId(), payment.getId());
        assertEquals(lastPayment.getAccountId(), accountId);
        assertEquals(lastPayment.getInvoiceId(), invoiceId);
        assertEquals(lastPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(lastPayment.getAmount().compareTo(amount), 0);
        assertEquals(lastPayment.getCurrency(), currency);
        assertEquals(lastPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(lastPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        clock.addDays(3);
        final DateTime newEffectiveDate = clock.getUTCNow();
        final UUID newPaymentMethodId = UUID.randomUUID();
        final BigDecimal newAmount = new BigDecimal("15.23");
        final PaymentAttemptModelDao secondAttempt = null; //new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), newPaymentMethodId, newEffectiveDate, newAmount, currency);
        paymentDao.updatePaymentWithNewAttempt(payment.getId(), secondAttempt, internalCallContext);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), newPaymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(newAmount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(newEffectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 2);
        final PaymentAttemptModelDao savedAttempt1 = attempts.get(0);

        /*
        assertEquals(savedAttempt1.getPaymentId(), payment.getId());
        assertEquals(savedAttempt1.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedAttempt1.getAccountId(), accountId);
        assertEquals(savedAttempt1.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt1.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt1.getGatewayErrorCode(), null);
        assertEquals(savedAttempt1.getGatewayErrorMsg(), null);
        assertEquals(savedAttempt1.getRequestedAmount().compareTo(amount), 0);

*/

        final PaymentAttemptModelDao savedAttempt2 = attempts.get(1);

        /*
        assertEquals(savedAttempt2.getPaymentId(), payment.getId());
        assertEquals(savedAttempt2.getPaymentMethodId(), newPaymentMethodId);
        assertEquals(savedAttempt2.getAccountId(), accountId);
        assertEquals(savedAttempt2.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt2.getProcessingStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedAttempt2.getGatewayErrorCode(), null);
        assertEquals(savedAttempt2.getGatewayErrorMsg(), null);
        assertEquals(savedAttempt2.getRequestedAmount().compareTo(newAmount), 0);
    */
    }


    @Test(groups = "slow")
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;
        final String externalPaymentId = UUID.randomUUID().toString();

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, null, null,
                                                                       accountId, pluginName, isActive);

        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, internalCallContext);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        final List<PaymentMethodModelDao> result = paymentDao.getPaymentMethods(accountId, internalCallContext);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        paymentDao.deletedPaymentMethod(paymentMethodId, internalCallContext);

        PaymentMethodModelDao deletedPaymentMethod = paymentDao.getPaymentMethod(paymentMethodId, internalCallContext);
        assertNull(deletedPaymentMethod);

        deletedPaymentMethod = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, internalCallContext);
        assertNotNull(deletedPaymentMethod);
        assertFalse(deletedPaymentMethod.isActive());
        assertEquals(deletedPaymentMethod.getAccountId(), accountId);
        assertEquals(deletedPaymentMethod.getId(), paymentMethodId);
        assertEquals(deletedPaymentMethod.getPluginName(), pluginName);
    }

    @Test(groups = "slow")
    public void testDirectPayment() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String externalName = "fo0";

        final DateTime utcNow = clock.getUTCNow();
        final DirectPaymentModelDao dpmd = new DirectPaymentModelDao(utcNow, utcNow, accountId, paymentMethodId, externalName);
        final DirectPaymentTransactionModelDao dptmd = new DirectPaymentTransactionModelDao(utcNow, utcNow, UUID.randomUUID().toString(), dpmd.getId(), TransactionType.AUTHORIZE,
                                                                                            utcNow, PaymentStatus.UNKNOWN, BigDecimal.TEN, Currency.USD, null, null);
        DirectPaymentModelDao savedDirectPayment = paymentDao.insertDirectPaymentWithFirstTransaction(dpmd, dptmd, internalCallContext);
        assertNotNull(savedDirectPayment);
        assertEquals(savedDirectPayment.getAccountId(), accountId);
        assertEquals(savedDirectPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedDirectPayment.getExternalKey(), externalName);

        savedDirectPayment = paymentDao.getDirectPayment(dpmd.getId(), internalCallContext);
        assertNotNull(savedDirectPayment);
        assertNotNull(savedDirectPayment.getPaymentNumber());
        assertEquals(savedDirectPayment.getAccountId(), accountId);
        assertEquals(savedDirectPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedDirectPayment.getExternalKey(), externalName);

        DirectPaymentTransactionModelDao savedTransaction = paymentDao.getDirectPaymentTransaction(dptmd.getId(), internalCallContext);
        assertNotNull(savedTransaction);
        assertEquals(savedTransaction.getDirectPaymentId(), dpmd.getId());
        assertEquals(savedTransaction.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction.getEffectiveDate().compareTo(utcNow), 0);
        assertEquals(savedTransaction.getPaymentStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedTransaction.getCurrency(), Currency.USD);
        assertNull(savedTransaction.getGatewayErrorCode());
        assertNull(savedTransaction.getGatewayErrorMsg());

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(dpmd.getId(), PaymentStatus.SUCCESS, BigDecimal.TEN, Currency.USD, dptmd.getId(), "100", "Excellent", "AUTH_SUCCESS", internalCallContext);

        savedDirectPayment = paymentDao.getDirectPayment(dpmd.getId(), internalCallContext);
        assertNotNull(savedDirectPayment);
        assertEquals(savedDirectPayment.getAccountId(), accountId);
        assertEquals(savedDirectPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedDirectPayment.getExternalKey(), externalName);

        savedTransaction = paymentDao.getDirectPaymentTransaction(dptmd.getId(), internalCallContext);
        assertNotNull(savedTransaction);
        assertEquals(savedTransaction.getDirectPaymentId(), dpmd.getId());
        assertEquals(savedTransaction.getTransactionType(), TransactionType.AUTHORIZE);
        assertEquals(savedTransaction.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(savedTransaction.getEffectiveDate().compareTo(utcNow), 0);
        assertEquals(savedTransaction.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedTransaction.getCurrency(), Currency.USD);
        assertEquals(savedTransaction.getGatewayErrorCode(), "100");
        assertEquals(savedTransaction.getGatewayErrorMsg(), "Excellent");

        List<DirectPaymentModelDao> perAccountPayments = paymentDao.getDirectPaymentsForAccount(accountId, internalCallContext);
        assertEquals(perAccountPayments.size(), 1);

        List<DirectPaymentTransactionModelDao> perAccountTransactions = paymentDao.getDirectTransactionsForAccount(accountId, internalCallContext);
        assertEquals(perAccountTransactions.size(), 1);

    }
}
