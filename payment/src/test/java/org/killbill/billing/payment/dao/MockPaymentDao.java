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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.RefundStatus;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MockPaymentDao implements PaymentDao {

    private final Map<UUID, DirectPaymentModelDao> payments = new HashMap<UUID, DirectPaymentModelDao>();
    private final Map<UUID, DirectPaymentTransactionModelDao> transactions = new HashMap<UUID, DirectPaymentTransactionModelDao>();
    private final Map<String, PaymentAttemptModelDao> attempts = new HashMap<String, PaymentAttemptModelDao>();

    public void reset() {
        payments.clear();
        transactions.clear();
        attempts.clear();
    }
    @Override
    public PaymentAttemptModelDao insertPaymentAttempt(final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        synchronized (this) {
            attempts.put(attempt.getTransactionExternalKey(), attempt);
            return attempt;
        }
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, final String state, final InternalCallContext context) {
        boolean success = false;
        synchronized (this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getId().equals(paymentAttemptId)) {
                    cur.setStateName(state);
                    success = true;
                }
            }
        }
        if (!success) {
            throw new RuntimeException("Counld not find attempt " + paymentAttemptId);
        }
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttemptByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            return attempts.get(externalKey);
        }
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransactionByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (DirectPaymentTransactionModelDao cur : transactions.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public DirectPaymentModelDao getDirectPaymentByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (DirectPaymentModelDao cur : payments.values()) {
                if (cur.getExternalKey().equals(externalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public Pagination<DirectPaymentModelDao> getDirectPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public DirectPaymentModelDao insertDirectPaymentWithFirstTransaction(final DirectPaymentModelDao directPayment, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        synchronized (this) {
            payments.put(directPayment.getId(), directPayment);
            transactions.put(directPaymentTransaction.getId(), directPaymentTransaction);
        }
        return directPayment;
    }

    @Override
    public DirectPaymentTransactionModelDao updateDirectPaymentWithNewTransaction(final UUID directPaymentId, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        synchronized (this) {
            transactions.put(directPaymentTransaction.getId(), directPaymentTransaction);
        }
        return directPaymentTransaction;
    }

    @Override
    public void updateDirectPaymentAndTransactionOnCompletion(final UUID directPaymentId, final String currentPaymentStateName, final UUID directTransactionId, final PaymentStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency, final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
        synchronized (this) {
            final DirectPaymentModelDao payment = payments.get(directPaymentId);
            if (payment != null) {
                payment.setCurrentStateName(currentPaymentStateName);
            }
            final DirectPaymentTransactionModelDao transaction = transactions.get(directTransactionId);
            if (transaction != null) {
                transaction.setPaymentStatus(paymentStatus);
                transaction.setProcessedAmount(processedAmount);
                transaction.setProcessedCurrency(processedCurrency);
                transaction.setGatewayErrorCode(gatewayErrorCode);
                transaction.setGatewayErrorMsg(gatewayErrorMsg);
            }
        }
    }

    @Override
    public DirectPaymentModelDao getDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        synchronized (this) {
            return payments.get(directPaymentId);
        }
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransaction(final UUID directTransactionId, final InternalTenantContext context) {
        synchronized (this) {
            return transactions.get(directTransactionId);
        }
    }

    @Override
    public List<DirectPaymentModelDao> getDirectPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(payments.values(), new Predicate<DirectPaymentModelDao>() {
                @Override
                public boolean apply(final DirectPaymentModelDao input) {
                    return input.getAccountId().equals(accountId);
                }
            }));
        }
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<DirectPaymentTransactionModelDao>() {
                @Override
                public boolean apply(final DirectPaymentTransactionModelDao input) {
                    final DirectPaymentModelDao payment = payments.get(input.getDirectPaymentId());
                    if (payment != null) {
                        return payment.getAccountId().equals(accountId);
                    } else {
                        return false;
                    }
                }
            }));
        }
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<DirectPaymentTransactionModelDao>() {
                @Override
                public boolean apply(final DirectPaymentTransactionModelDao input) {
                        return input.getDirectPaymentId().equals(directPaymentId);
                }
            }));
        }
    }


    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        return attempts.get(attemptId);
    }


    private final List<PaymentMethodModelDao> paymentMethods = new LinkedList<PaymentMethodModelDao>();

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        paymentMethods.add(paymentMethod);
        return paymentMethod;
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        for (final PaymentMethodModelDao cur : paymentMethods) {
            if (cur.getId().equals(paymentMethodId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        final List<PaymentMethodModelDao> result = new ArrayList<PaymentMethodModelDao>();
        for (final PaymentMethodModelDao cur : paymentMethods) {
            if (cur.getAccountId().equals(accountId)) {
                result.add(cur);
            }
        }
        return result;
    }

    @Override
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        final Iterator<PaymentMethodModelDao> it = paymentMethods.iterator();
        while (it.hasNext()) {
            final PaymentMethodModelDao cur = it.next();
            if (cur.getId().equals(paymentMethodId)) {
                it.remove();
                break;
            }
        }
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final String pluginName, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return ImmutableList.<PaymentMethodModelDao>of();
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethod(paymentMethodId, context);
    }
}
