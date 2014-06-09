/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.retry.plugin.api.PaymentControlApiException;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi.FailureCallResult;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi.PaymentControlContext;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi.PriorPaymentControlResult;
import org.killbill.billing.retry.plugin.api.UnknownEntryException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public abstract class RetryOperationCallback extends PluginOperation implements OperationCallback {

    protected final DirectPaymentProcessor directPaymentProcessor;
    private final OSGIServiceRegistration<PaymentControlPluginApi> paymentControlPluginRegistry;

    private final Logger logger = LoggerFactory.getLogger(RetryOperationCallback.class);

    protected RetryOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext);
        this.directPaymentProcessor = directPaymentProcessor;
        this.paymentControlPluginRegistry = retryPluginRegistry;
    }

    private PriorPaymentControlResult getPluginResult(final String pluginName, final PaymentControlContext paymentControlContext) throws PaymentControlApiException {

        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        try {
            final PriorPaymentControlResult result = plugin.priorCall(paymentControlContext);
            return result;
        } catch (UnknownEntryException e) {
            return new DefaultPriorPaymentControlResult(true);
        }
    }

    private DateTime getNextRetryDate(final String pluginName, final PaymentControlContext paymentControlContext) {
        try {
            final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            final FailureCallResult result = plugin.onFailureCall(paymentControlContext);
            return result.getNextRetryDate();
        } catch (PaymentControlApiException e) {
            logger.warn("Plugin " + pluginName + " failed to return next retryDate for payment " + paymentControlContext.getPaymentExternalKey(), e);
            return null;
        }
    }

    private void onCompletion(final String pluginName, final PaymentControlContext paymentControlContext) {
        final PaymentControlPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
        try {
            plugin.onCompletionCall(paymentControlContext);
        } catch (PaymentControlApiException e) {
            logger.warn("Plugin " + pluginName + " failed to complete onCompletion call for " + paymentControlContext.getPaymentExternalKey(), e);
        }
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithTimeout(new WithAccountLockCallback<OperationResult>() {

            private DirectPaymentTransaction getTransaction(final DirectPayment payment, final String transactionExternalKey) {
                return Iterables.tryFind(payment.getTransactions(), new Predicate<DirectPaymentTransaction>() {
                    @Override
                    public boolean apply(final DirectPaymentTransaction input) {
                        return input.getExternalKey().equals(transactionExternalKey);
                    }
                }).get();
            }

            @Override
            public OperationResult doOperation() throws OperationException {

                final RetryableDirectPaymentStateContext retryableDirectPaymentStateContext = (RetryableDirectPaymentStateContext) directPaymentStateContext;
                final PaymentControlContext paymentControlContext = new DefaultPaymentControlContext(directPaymentStateContext.account,
                                                                                                     directPaymentStateContext.directPaymentExternalKey,
                                                                                                     directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                                     directPaymentStateContext.transactionType,
                                                                                                     directPaymentStateContext.amount,
                                                                                                     directPaymentStateContext.currency,
                                                                                                     directPaymentStateContext.properties,
                                                                                                     retryableDirectPaymentStateContext.isApiPayment(),
                                                                                                     directPaymentStateContext.callContext);

                // Note that we are using OperationResult.EXCEPTION result to transition to final ABORTED state -- see RetryStates.xml
                final PriorPaymentControlResult pluginResult;
                try {
                    pluginResult = getPluginResult(retryableDirectPaymentStateContext.getPluginName(), paymentControlContext);
                    if (pluginResult.isAborted()) {
                        return OperationResult.EXCEPTION;
                    }
                } catch (PaymentControlApiException e) {
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                try {
                    // Adjust amount with value returned by plugin if necessary
                    if (directPaymentStateContext.getAmount() == null ||
                        (pluginResult.getAdjustedAmount() != null && pluginResult.getAdjustedAmount().compareTo(directPaymentStateContext.getAmount()) != 0)) {
                        ((RetryableDirectPaymentStateContext) directPaymentStateContext).setAmount(pluginResult.getAdjustedAmount());
                    }

                    final DirectPayment result = doPluginOperation();
                    final DirectPaymentTransaction transaction = getTransaction(result, directPaymentStateContext.directPaymentTransactionExternalKey);
                    ((RetryableDirectPaymentStateContext) directPaymentStateContext).setResult(result);

                    final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(directPaymentStateContext.account,
                                                                                                                result.getId(),
                                                                                                                directPaymentStateContext.directPaymentExternalKey,
                                                                                                                directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                                                directPaymentStateContext.transactionType,
                                                                                                                transaction.getAmount(),
                                                                                                                transaction.getCurrency(),
                                                                                                                transaction.getProcessedAmount(),
                                                                                                                transaction.getProcessedCurrency(),
                                                                                                                directPaymentStateContext.properties,
                                                                                                                retryableDirectPaymentStateContext.isApiPayment(),
                                                                                                                directPaymentStateContext.callContext);

                    onCompletion(retryableDirectPaymentStateContext.getPluginName(), updatedPaymentControlContext);

                } catch (PaymentApiException e) {
                    final DateTime retryDate = getNextRetryDate(retryableDirectPaymentStateContext.getPluginName(), paymentControlContext);
                    if (retryDate == null) {
                        throw new OperationException(e, OperationResult.EXCEPTION);
                    } else {
                        ((RetryableDirectPaymentStateContext) directPaymentStateContext).setRetryDate(retryDate);
                        throw new OperationException(e, OperationResult.FAILURE);
                    }
                } catch (Exception e) {
                    // STEPH Any other exception we abort the retry logic
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }
                return OperationResult.SUCCESS;
            }
        });
    }

    public class DefaultPaymentControlContext extends DefaultCallContext implements PaymentControlContext {

        private final Account account;
        private final UUID paymentId;
        private final String paymentExternalKey;
        private final String transactionExternalKey;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final BigDecimal processedAmount;
        private final Currency processedCurrency;
        private final boolean isApiPayment;
        private final Iterable<PluginProperty> properties;

        public DefaultPaymentControlContext(final Account account, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency,
                                            final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            this(account, null, paymentExternalKey, transactionExternalKey, transactionType, amount, currency, null, null, properties, isApiPayment, callContext);
        }

        public DefaultPaymentControlContext(final Account account, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType,
                                            final BigDecimal amount, final Currency currency, @Nullable final BigDecimal processedAmount, @Nullable final Currency processedCurrency, final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentId = paymentId;
            this.paymentExternalKey = paymentExternalKey;
            this.transactionExternalKey = transactionExternalKey;
            this.transactionType = transactionType;
            this.amount = amount;
            this.currency = currency;
            this.processedAmount = processedAmount;
            this.processedCurrency = processedCurrency;
            this.properties = properties;
            this.isApiPayment = isApiPayment;
        }

        @Override
        public Account getAccount() {
            return account;
        }

        @Override
        public String getPaymentExternalKey() {
            return paymentExternalKey;
        }

        @Override
        public String getTransactionExternalKey() {
            return transactionExternalKey;
        }

        @Override
        public TransactionType getTransactionType() {
            return transactionType;
        }

        @Override
        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public Currency getCurrency() {
            return currency;
        }

        @Override
        public UUID getPaymentId() {
            return null;
        }

        @Override
        public BigDecimal getProcessedAmount() {
            return processedAmount;
        }

        @Override
        public Currency getProcessedCurrency() {
            return processedCurrency;
        }

        @Override
        public boolean isApiPayment() {
            return isApiPayment;
        }

        @Override
        public Iterable<PluginProperty> getPluginProperties() {
            return properties;
        }
    }
}
