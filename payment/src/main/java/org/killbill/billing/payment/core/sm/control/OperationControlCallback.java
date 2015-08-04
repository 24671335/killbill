/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm.control;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.core.sm.OperationCallbackBase;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.routing.plugin.api.OnFailurePaymentRoutingResult;
import org.killbill.billing.routing.plugin.api.PaymentRoutingApiException;
import org.killbill.billing.routing.plugin.api.PaymentRoutingContext;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.billing.routing.plugin.api.PriorPaymentRoutingResult;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public abstract class OperationControlCallback extends OperationCallbackBase<Payment, PaymentApiException> implements OperationCallback {

    private static final Logger logger = LoggerFactory.getLogger(OperationControlCallback.class);

    protected final PaymentProcessor paymentProcessor;
    protected final PaymentStateControlContext paymentStateControlContext;
    private final OSGIServiceRegistration<PaymentRoutingPluginApi> paymentControlPluginRegistry;

    protected OperationControlCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final PaymentStateControlContext paymentStateContext, final PaymentProcessor paymentProcessor, final OSGIServiceRegistration<PaymentRoutingPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, paymentStateContext);
        this.paymentProcessor = paymentProcessor;
        this.paymentControlPluginRegistry = retryPluginRegistry;
        this.paymentStateControlContext = paymentStateContext;
    }

    @Override
    protected abstract Payment doCallSpecificOperationCallback() throws PaymentApiException;

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {

            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {

                final PaymentRoutingContext paymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                     paymentStateContext.getPaymentMethodId(),
                                                                                                     paymentStateControlContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentExternalKey(),
                                                                                                     paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     paymentStateContext.getAmount(),
                                                                                                     paymentStateContext.getCurrency(),
                                                                                                     paymentStateContext.getProperties(),
                                                                                                     paymentStateControlContext.isApiPayment(),
                                                                                                     paymentStateContext.getCallContext());

                final PriorPaymentRoutingResult pluginResult;
                try {
                    pluginResult = executePluginPriorCalls(paymentStateControlContext.getPaymentControlPluginNames(), paymentControlContext);
                    if (pluginResult != null && pluginResult.isAborted()) {
                        // Transition to ABORTED
                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.EXCEPTION);
                    }
                } catch (final PaymentRoutingApiException e) {
                    // Transition to ABORTED and throw PaymentControlApiException to caller.
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                final boolean success;
                try {
                    // Adjust amount with value returned by plugin if necessary
                    adjustStateContextValues(paymentStateContext, pluginResult);

                    final Payment result = doCallSpecificOperationCallback();
                    ((PaymentStateControlContext) paymentStateContext).setResult(result);
                    final PaymentTransaction transaction = ((PaymentStateControlContext) paymentStateContext).getCurrentTransaction();

                    success = transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.PENDING;
                    if (success) {
                        return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.SUCCESS);
                    } else {
                        throw new OperationException(null, getOperationResultOnException(paymentStateContext));
                    }
                } catch (final PaymentApiException e) {
                    // Wrap PaymentApiException, and throw a new OperationException with an ABORTED/FAILURE state based on the retry result.
                    throw new OperationException(e, getOperationResultOnException(paymentStateContext));
                } catch (final RuntimeException e) {
                    throw e;
                }
            }
        });
    }

    @Override
    protected OperationException unwrapExceptionFromDispatchedTask(final PaymentStateContext paymentStateContext, final Exception e) {

        // If this is an ExecutionException we attempt to extract the cause first
        final Throwable originalExceptionOrCause = e instanceof ExecutionException ? MoreObjects.firstNonNull(e.getCause(), e) : e;

        if (originalExceptionOrCause instanceof OperationException) {
            return (OperationException) originalExceptionOrCause;
        } else if (originalExceptionOrCause instanceof LockFailedException) {
            final String format = String.format("Failed to lock account %s", paymentStateContext.getAccount().getExternalKey());
            logger.error(String.format(format));
        } else if (originalExceptionOrCause instanceof TimeoutException) {
            logger.warn("RetryOperationCallback call TIMEOUT for account {}", paymentStateContext.getAccount().getExternalKey());
        } else if (originalExceptionOrCause instanceof InterruptedException) {
            logger.error("RetryOperationCallback call was interrupted for account {}", paymentStateContext.getAccount().getExternalKey());
        } else /* most probably RuntimeException */ {
            logger.warn("RetryOperationCallback failed for account {}", paymentStateContext.getAccount().getExternalKey(), e);
        }
        return new OperationException(e, getOperationResultOnException(paymentStateContext));
    }


    private void adjustStateContextValues(final PaymentStateContext inputContext, @Nullable final PriorPaymentRoutingResult pluginResult) {
        if (pluginResult == null) {
            return;
        }

        final PaymentStateControlContext input = (PaymentStateControlContext) inputContext;
        if (pluginResult.getAdjustedAmount() != null) {
            input.setAmount(pluginResult.getAdjustedAmount());
        }
        if (pluginResult.getAdjustedCurrency() != null) {
            input.setCurrency(pluginResult.getAdjustedCurrency());
        }
        if (pluginResult.getAdjustedPaymentMethodId() != null) {
            input.setPaymentMethodId(pluginResult.getAdjustedPaymentMethodId());
        }
        if (pluginResult.getAdjustedPluginProperties() != null) {
            input.setProperties(pluginResult.getAdjustedPluginProperties());
        }
    }

    private OperationResult getOperationResultOnException(final PaymentStateContext paymentStateContext) {
        final PaymentStateControlContext paymentStateControlContext = (PaymentStateControlContext) paymentStateContext;
        final OperationResult operationResult = paymentStateControlContext.getRetryDate() != null ? OperationResult.FAILURE : OperationResult.EXCEPTION;
        return operationResult;
    }

    private PriorPaymentRoutingResult executePluginPriorCalls(final List<String> paymentControlPluginNames, final PaymentRoutingContext paymentControlContextArg) throws PaymentRoutingApiException {
        // Return as soon as the first plugin aborts, or the last result for the last plugin
        PriorPaymentRoutingResult prevResult = null;

        PaymentRoutingContext inputPaymentControlContext = paymentControlContextArg;

        for (final String pluginName : paymentControlPluginNames) {
            final PaymentRoutingPluginApi plugin = paymentControlPluginRegistry.getServiceForName(pluginName);
            if (plugin == null) {
                // First call to plugin, we log warn, if plugin is not registered
                logger.warn("Skipping unknown payment control plugin {} when fetching results", pluginName);
                continue;
            }
            prevResult = plugin.priorCall(inputPaymentControlContext, paymentStateContext.getProperties());
            if (prevResult.isAborted()) {
                break;
            }
            inputPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                          prevResult.getAdjustedPaymentMethodId() != null ? prevResult.getAdjustedPaymentMethodId() : inputPaymentControlContext.getPaymentMethodId(),
                                                                          paymentStateControlContext.getAttemptId(),
                                                                          paymentStateContext.getPaymentId(),
                                                                          paymentStateContext.getPaymentExternalKey(),
                                                                          paymentStateContext.getPaymentTransactionExternalKey(),
                                                                          paymentStateContext.getTransactionType(),
                                                                          prevResult.getAdjustedAmount() != null ? prevResult.getAdjustedAmount() : inputPaymentControlContext.getAmount(),
                                                                          prevResult.getAdjustedCurrency() != null ? prevResult.getAdjustedCurrency() : inputPaymentControlContext.getCurrency(),
                                                                          prevResult.getAdjustedPluginProperties() != null ? prevResult.getAdjustedPluginProperties() : inputPaymentControlContext.getPluginProperties(),
                                                                          paymentStateControlContext.isApiPayment(),
                                                                          paymentStateContext.getCallContext());

        }
        return prevResult;
    }

    public static class DefaultPaymentControlContext extends DefaultCallContext implements PaymentRoutingContext {

        private final Account account;
        private final UUID paymentMethodId;
        private final UUID attemptId;
        private final UUID paymentId;
        private final String paymentExternalKey;
        private final UUID transactionId;
        private final String transactionExternalKey;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final BigDecimal processedAmount;
        private final Currency processedCurrency;
        private final boolean isApiPayment;
        private final Iterable<PluginProperty> properties;

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency,
                                            final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            this(account, paymentMethodId, attemptId, paymentId, paymentExternalKey, null, transactionExternalKey, transactionType, amount, currency, null, null, properties, isApiPayment, callContext);
        }

        public DefaultPaymentControlContext(final Account account, final UUID paymentMethodId, final UUID attemptId, @Nullable final UUID paymentId, final String paymentExternalKey, @Nullable final UUID transactionId, final String transactionExternalKey, final TransactionType transactionType,
                                            final BigDecimal amount, final Currency currency, @Nullable final BigDecimal processedAmount, @Nullable final Currency processedCurrency, final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentMethodId = paymentMethodId;
            this.attemptId = attemptId;
            this.paymentId = paymentId;
            this.paymentExternalKey = paymentExternalKey;
            this.transactionId = transactionId;
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
        public UUID getAccountId() {
            return account.getId();
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
        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        @Override
        public UUID getPaymentId() {
            return paymentId;
        }

        @Override
        public UUID getAttemptPaymentId() {
            return attemptId;
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

        public UUID getTransactionId() {
            return transactionId;
        }

        @Override
        public Iterable<PluginProperty> getPluginProperties() {
            return properties;
        }

        @Override
        public String toString() {
            return "DefaultPaymentControlContext{" +
                   "account=" + account +
                   ", paymentMethodId=" + paymentMethodId +
                   ", attemptId=" + attemptId +
                   ", paymentId=" + paymentId +
                   ", paymentExternalKey='" + paymentExternalKey + '\'' +
                   ", transactionId=" + transactionId +
                   ", transactionExternalKey='" + transactionExternalKey + '\'' +
                   ", transactionType=" + transactionType +
                   ", amount=" + amount +
                   ", currency=" + currency +
                   ", processedAmount=" + processedAmount +
                   ", processedCurrency=" + processedCurrency +
                   ", isApiPayment=" + isApiPayment +
                   ", properties=" + properties +
                   '}';
        }
    }
}
