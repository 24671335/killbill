/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.Operation;
import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class DirectPaymentAutomatonRunner {

    protected final StateMachineConfig stateMachineConfig;
    protected final PaymentDao paymentDao;
    protected final GlobalLocker locker;
    protected final PluginDispatcher<OperationResult> paymentPluginDispatcher;
    protected final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    protected final Clock clock;

    @Inject
    public DirectPaymentAutomatonRunner(@javax.inject.Named(PaymentModule.STATE_MACHINE_PAYMENT) final StateMachineConfig stateMachineConfig,
                                        final PaymentConfig paymentConfig,
                                        final PaymentDao paymentDao,
                                        final GlobalLocker locker,
                                        final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                        final Clock clock,
                                        @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        this.stateMachineConfig = stateMachineConfig;
        this.paymentDao = paymentDao;
        this.locker = locker;
        this.pluginRegistry = pluginRegistry;
        this.clock = clock;

        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executor);

    }

    public UUID run(final TransactionType transactionType, final Account account,
                    @Nullable final UUID directPaymentId, final String directPaymentTransactionExternalKey,
                    final boolean shouldLockAccount,  final boolean isExternalPayment, final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(transactionType, account, null, directPaymentId, null, directPaymentTransactionExternalKey, null, null, shouldLockAccount, isExternalPayment, properties, callContext, internalCallContext);
    }

    public UUID run(final TransactionType transactionType, final Account account,
                    @Nullable final UUID directPaymentId, final String directPaymentTransactionExternalKey,
                    final BigDecimal amount, final Currency currency,
                    final boolean shouldLockAccount, final boolean isExternalPayment, final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return run(transactionType, account, null, directPaymentId, null, directPaymentTransactionExternalKey, amount, currency, shouldLockAccount, isExternalPayment, properties, callContext, internalCallContext);
    }

    public UUID run(final TransactionType transactionType, final Account account, @Nullable final UUID paymentMethodId,
                    @Nullable final UUID directPaymentId, @Nullable final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                    @Nullable final BigDecimal amount, @Nullable final Currency currency,
                    final boolean shouldLockAccount, final boolean isExternalPayment, final Iterable<PluginProperty> properties,
                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final DateTime utcNow = clock.getUTCNow();

        final DirectPaymentStateContext directPaymentStateContext = new DirectPaymentStateContext(directPaymentId, directPaymentExternalKey, directPaymentTransactionExternalKey, transactionType,
                                                                                                  account, paymentMethodId, amount, currency, shouldLockAccount, isExternalPayment, properties, internalCallContext, callContext);
        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, utcNow, paymentDao, pluginRegistry, internalCallContext);

        final UUID nonNullPaymentMethodId;
        final String currentStateMachineName;
        final String currentStateName;
        if (directPaymentId != null) {
            final DirectPaymentModelDao directPaymentModelDao = daoHelper.getDirectPayment();
            nonNullPaymentMethodId = directPaymentModelDao.getPaymentMethodId();
            currentStateName = directPaymentModelDao.getCurrentStateName();
            currentStateMachineName = getStateMachineName(currentStateName);

            // Check for illegal states (should never happen)
            Preconditions.checkState(currentStateMachineName != null, "State machine name cannot be null for direct payment " + directPaymentId);
            Preconditions.checkState(currentStateName != null, "State name cannot be null for direct payment " + directPaymentId);
            Preconditions.checkState(paymentMethodId == null || nonNullPaymentMethodId.equals(paymentMethodId), "Specified payment method id " + paymentMethodId + " doesn't match the one on the payment " + nonNullPaymentMethodId);
        } else {
            // If the payment method is not specified, retrieve the default one on the account
            nonNullPaymentMethodId = Objects.firstNonNull(paymentMethodId, daoHelper.getDefaultPaymentMethodId());

            switch (transactionType) {
                case AUTHORIZE:
                    currentStateMachineName = "AUTHORIZE";
                    currentStateName = "AUTH_INIT";
                    break;
                case CREDIT:
                    currentStateMachineName = "CREDIT";
                    currentStateName = "CREDIT_INIT";
                    break;
                case PURCHASE:
                    currentStateMachineName = "PURCHASE";
                    currentStateName = "PURCHASE_INIT";
                    break;
                default:
                    throw new IllegalStateException("Unsupported transaction type " + transactionType + " for null direct payment id");
            }
        }

        directPaymentStateContext.setPaymentMethodId(nonNullPaymentMethodId);

        final String operationStateMachineName;
        final String operationName;
        final OperationCallback operationCallback;
        final LeavingStateCallback leavingStateCallback;
        final EnteringStateCallback enteringStateCallback;
        switch (transactionType) {
            case PURCHASE:
                operationStateMachineName = "PURCHASE";
                operationName = "OP_PURCHASE";
                operationCallback = new PurchaseOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new PurchaseInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new PurchaseCompleted(daoHelper, directPaymentStateContext);
                break;
            case AUTHORIZE:
                operationStateMachineName = "AUTHORIZE";
                operationName = "OP_AUTHORIZE";
                operationCallback = new AuthorizeOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new AuthorizeInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new AuthorizeCompleted(daoHelper, directPaymentStateContext);
                break;
            case CAPTURE:
                operationStateMachineName = "CAPTURE";
                operationName = "OP_CAPTURE";
                operationCallback = new CaptureOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new CaptureInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new CaptureCompleted(daoHelper, directPaymentStateContext);
                break;
            case VOID:
                operationStateMachineName = "VOID";
                operationName = "OP_VOID";
                operationCallback = new VoidOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new VoidInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new VoidCompleted(daoHelper, directPaymentStateContext);
                break;
            case REFUND:
                operationStateMachineName = "REFUND";
                operationName = "OP_REFUND";
                operationCallback = new RefundOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new RefundInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new RefundCompleted(daoHelper, directPaymentStateContext);
                break;
            case CREDIT:
                operationStateMachineName = "CREDIT";
                operationName = "OP_CREDIT";
                operationCallback = new CreditOperation(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
                leavingStateCallback = new CreditInitiated(daoHelper, directPaymentStateContext);
                enteringStateCallback = new CreditCompleted(daoHelper, directPaymentStateContext);
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type " + transactionType);
        }

        runStateMachineOperation(currentStateMachineName, currentStateName, operationStateMachineName, operationName, leavingStateCallback, operationCallback, enteringStateCallback);

        return directPaymentStateContext.getDirectPaymentId();
    }

    // Hack for now
    protected String getStateMachineName(final String currentStateName) {
        for (final StateMachine stateMachine : stateMachineConfig.getStateMachines()) {
            for (final State state : stateMachine.getStates()) {
                if (state.getName().equals(currentStateName)) {
                    return stateMachine.getName();
                }
            }
        }
        return null;
    }

    protected void runStateMachineOperation(final String initialStateMachineName, final String initialStateName,
                                            final String operationStateMachineName, final String operationName,
                                            final LeavingStateCallback leavingStateCallback, final OperationCallback operationCallback, final EnteringStateCallback enteringStateCallback) throws PaymentApiException {
        try {
            final StateMachine initialStateMachine = stateMachineConfig.getStateMachine(initialStateMachineName);
            final State initialState = initialStateMachine.getState(initialStateName);

            final StateMachine operationStateMachine = stateMachineConfig.getStateMachine(operationStateMachineName);
            final Operation operation = operationStateMachine.getOperation(operationName);

            initialState.runOperation(operation, operationCallback, enteringStateCallback, leavingStateCallback);
        } catch (final MissingEntryException e) {
            // TODO ErrorCode State Machine?
            throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
        } catch (final OperationException e) {
            if (e.getCause() == null) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
            } else if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else {
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
            }
        }
    }
}
