/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.automaton.DefaultStateMachineConfig;
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
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultDirectPayment;
import org.killbill.billing.payment.api.DefaultDirectPaymentTransaction;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.AuthorizeCompleted;
import org.killbill.billing.payment.core.sm.AuthorizeInitiated;
import org.killbill.billing.payment.core.sm.AuthorizeOperation;
import org.killbill.billing.payment.core.sm.CaptureCompleted;
import org.killbill.billing.payment.core.sm.CaptureInitiated;
import org.killbill.billing.payment.core.sm.CaptureOperation;
import org.killbill.billing.payment.core.sm.DirectPaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.DirectPaymentStateContext;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.xmlloader.XMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.io.Resources;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class DirectPaymentProcessor extends ProcessorBase {

    private final Clock clock;

    private final StateMachineConfig stateMachineConfig;
    private final PluginDispatcher<DirectPayment> paymentPluginDispatcher;
    private final PluginDispatcher<OperationResult> SMPaymentPluginDispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    private static final Logger log = LoggerFactory.getLogger(DirectPaymentProcessor.class);

    @Inject
    public DirectPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                  final AccountInternalApi accountUserApi,
                                  final InvoiceInternalApi invoiceApi,
                                  final TagInternalApi tagUserApi,
                                  final PaymentDao paymentDao,
                                  final NonEntityDao nonEntityDao,
                                  final PersistentBus eventBus,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final Clock clock,
                                  final GlobalLocker locker,
                                  final PaymentConfig paymentConfig,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentPluginDispatcher = new PluginDispatcher<DirectPayment>(paymentPluginTimeoutSec, executor);
        this.SMPaymentPluginDispatcher = new PluginDispatcher<OperationResult>(paymentPluginTimeoutSec, executor);

        try {
            stateMachineConfig = XMLLoader.getObjectFromString(Resources.getResource("PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public DirectPayment createAuthorization(final Account account, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        final String directPaymentTransactionExternalKey = directPaymentExternalKey; // TODO API change
        final UUID paymentMethodId = account.getPaymentMethodId(); // TODO API change

        final DateTime utcNow = clock.getUTCNow();
        final DirectPaymentStateContext directPaymentStateContext = new DirectPaymentStateContext(directPaymentId, directPaymentExternalKey, directPaymentTransactionExternalKey, TransactionType.AUTHORIZE, account, paymentMethodId, amount, currency);
        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, utcNow, paymentDao, pluginRegistry, callContext);
        directPaymentStateContext.setPaymentMethodId(Objects.firstNonNull(paymentMethodId, daoHelper.getDefaultPaymentMethodId(account)));

        // Retrieve as a safety check
        if (directPaymentId != null) {
            daoHelper.getDirectPayment();
        }

        final AuthorizeOperation authorizeOperation = new AuthorizeOperation(account, daoHelper, locker, SMPaymentPluginDispatcher, properties, directPaymentStateContext, callContext, context);
        final AuthorizeInitiated authorizeInitiated = new AuthorizeInitiated(daoHelper, directPaymentStateContext);
        final AuthorizeCompleted authorizeCompleted = new AuthorizeCompleted(daoHelper, directPaymentStateContext);

        runStateMachineOperation("AUTHORIZE", "AUTH_INIT", "OP_AUTHORIZE", authorizeInitiated, authorizeOperation, authorizeCompleted);

        final DirectPaymentTransactionModelDao directPaymentTransactionModelDao = directPaymentStateContext.getDirectPaymentTransactionModelDao();
        return toDirectPayment(directPaymentTransactionModelDao.getDirectPaymentId(), directPaymentStateContext.getPaymentInfoPlugin(), callContext);
    }

    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        final String directPaymentTransactionExternalKey = null; // TODO API change
        final UUID paymentMethodId = account.getPaymentMethodId(); // TODO API change

        final DateTime utcNow = clock.getUTCNow();
        final DirectPaymentStateContext directPaymentStateContext = new DirectPaymentStateContext(directPaymentId, directPaymentTransactionExternalKey, TransactionType.CAPTURE, account, paymentMethodId, amount, currency);
        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, utcNow, paymentDao, pluginRegistry, callContext);
        directPaymentStateContext.setPaymentMethodId(Objects.firstNonNull(paymentMethodId, daoHelper.getDefaultPaymentMethodId(account)));

        // Retrieve as a safety check
        daoHelper.getDirectPayment();

        final CaptureOperation captureOperation = new CaptureOperation(account, daoHelper, locker, SMPaymentPluginDispatcher, properties, directPaymentStateContext, callContext, context);
        final CaptureInitiated captureInitiated = new CaptureInitiated(daoHelper, directPaymentStateContext);
        final CaptureCompleted captureCompleted = new CaptureCompleted(daoHelper, directPaymentStateContext);

        // TODO Retrieve state from DB
        runStateMachineOperation("AUTHORIZE", "AUTH_SUCCESS", "CAPTURE", "OP_CAPTURE", captureInitiated, captureOperation, captureCompleted);

        final DirectPaymentTransactionModelDao directPaymentTransactionModelDao = directPaymentStateContext.getDirectPaymentTransactionModelDao();
//        return toDirectPayment(directPaymentTransactionModelDao.getDirectPaymentId(), directPaymentStateContext.getPaymentInfoPlugin(), callContext);
        return getPayment(directPaymentId, true, properties, context, callContext);
    }

    public DirectPayment createPurchase(final Account account, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String externalKey, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        return initiateDirectPayment(TransactionType.PURCHASE,
                                     new PluginWrapper() {
                                         @Override
                                         public PaymentInfoPlugin doPluginOperation(final PaymentPluginApi plugin, final Account account, final BigDecimal amount, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
                                             return plugin.processPayment(account.getId(), directPaymentId, account.getPaymentMethodId(), amount, currency, properties, callContext);
                                         }
                                     },
                                     directPaymentId,
                                     externalKey,
                                     account,
                                     amount,
                                     currency,
                                     properties,
                                     context,
                                     callContext
                                    );
    }

    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        return createDirectPayment(TransactionType.VOID,
                                   new PluginWrapper() {
                                       @Override
                                       public PaymentInfoPlugin doPluginOperation(final PaymentPluginApi plugin, final Account account, final BigDecimal amount, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
                                           return plugin.voidPayment(account.getId(), directPaymentId, account.getPaymentMethodId(), properties, callContext);
                                       }
                                   },
                                   directPaymentId,
                                   account,
                                   null,
                                   null,
                                   properties,
                                   context,
                                   callContext
                                  );
    }

    public DirectPayment createCredit(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        return createDirectPayment(TransactionType.CREDIT,
                                   new PluginWrapper() {
                                       @Override
                                       public PaymentInfoPlugin doPluginOperation(final PaymentPluginApi plugin, final Account account, final BigDecimal amount, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
                                           return plugin.voidPayment(account.getId(), directPaymentId, account.getPaymentMethodId(), properties, callContext);
                                       }
                                   },
                                   directPaymentId,
                                   account,
                                   amount,
                                   currency,
                                   properties,
                                   context,
                                   callContext
                                  );
    }

    public List<DirectPayment> getAccountPayments(final UUID accountId, final InternalTenantContext tenantContext) throws PaymentApiException {
        final List<DirectPaymentModelDao> paymentsModelDao = paymentDao.getDirectPaymentsForAccount(accountId, tenantContext);
        final List<DirectPaymentTransactionModelDao> transactionsModelDao = paymentDao.getDirectTransactionsForAccount(accountId, tenantContext);

        return Lists.transform(paymentsModelDao, new Function<DirectPaymentModelDao, DirectPayment>() {

            @Override
            public DirectPayment apply(final DirectPaymentModelDao curDirectPaymentModelDao) {
                return toDirectPayment(curDirectPaymentModelDao, transactionsModelDao, null);
            }
        });
    }

    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context, final InternalTenantContext tenantContext) throws PaymentApiException {
        final DirectPaymentModelDao paymentModelDao = paymentDao.getDirectPayment(directPaymentId, tenantContext);
        if (paymentModelDao == null) {
            return null;
        }

        final InternalTenantContext tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        final List<DirectPaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(paymentModelDao.getAccountId(), tenantContextWithAccountRecordId);

        final PaymentPluginApi plugin = withPluginInfo ? getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), tenantContext) : null;
        PaymentInfoPlugin pluginInfo = null;
        if (plugin != null) {
            try {
                pluginInfo = plugin.getPaymentInfo(paymentModelDao.getAccountId(), directPaymentId, properties, context);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO, directPaymentId, e.toString());
            }
        }
        return toDirectPayment(paymentModelDao, transactionsForAccount, pluginInfo);
    }

    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties,
                                                 final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<DirectPayment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<DirectPayment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return getPayments(offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<DirectPaymentModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<DirectPaymentModelDao> build() {
                                           // Find all payments for all accounts
                                           return paymentDao.getDirectPayments(pluginName, offset, limit, internalTenantContext);
                                       }
                                   },
                                   new Function<DirectPaymentModelDao, DirectPayment>() {
                                       @Override
                                       public DirectPayment apply(final DirectPaymentModelDao paymentModelDao) {
                                           PaymentInfoPlugin pluginInfo = null;
                                           try {
                                               pluginInfo = pluginApi.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               log.warn("Unable to find payment id " + paymentModelDao.getId() + " in plugin " + pluginName);
                                               // We still want to return a payment object, even though the plugin details are missing
                                           }

                                           return toDirectPayment(paymentModelDao.getId(), pluginInfo, internalTenantContext);
                                       }
                                   }
                                  );
    }

    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<DirectPayment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<DirectPayment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return searchPayments(searchKey, offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentInfoPlugin, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentInfoPlugin> build() throws PaymentApiException {
                                           try {
                                               return pluginApi.searchPayments(searchKey, offset, limit, properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_SEARCH_PAYMENTS, pluginName, searchKey);
                                           }
                                       }
                                   },
                                   new Function<PaymentInfoPlugin, DirectPayment>() {
                                       @Override
                                       public DirectPayment apply(final PaymentInfoPlugin paymentInfoPlugin) {
                                           if (paymentInfoPlugin.getKbPaymentId() == null) {
                                               // Garbage from the plugin?
                                               log.debug("Plugin {} returned a payment without a kbPaymentId for searchKey {}", pluginName, searchKey);
                                               return null;
                                           }

                                           return toDirectPayment(paymentInfoPlugin.getKbPaymentId(), paymentInfoPlugin, internalTenantContext);
                                       }
                                   }
                                  );
    }

    private void runStateMachineOperation(final String initialStateMachineName, final String initialStateName, final String operationName,
                                          final LeavingStateCallback leavingStateCallback, final OperationCallback operationCallback, final EnteringStateCallback enteringStateCallback) throws PaymentApiException {
        runStateMachineOperation(initialStateMachineName, initialStateName, initialStateMachineName, operationName, leavingStateCallback, operationCallback, enteringStateCallback);
    }

    private void runStateMachineOperation(final String initialStateMachineName, final String initialStateName,
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

    private DirectPayment toDirectPayment(final UUID directPaymentId, @Nullable final PaymentInfoPlugin pluginInfo, final InternalTenantContext tenantContext) {
        final DirectPaymentModelDao paymentModelDao = paymentDao.getDirectPayment(directPaymentId, tenantContext);
        if (paymentModelDao == null) {
            log.warn("Unable to find direct payment id " + directPaymentId);
            return null;
        }

        final InternalTenantContext tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        final List<DirectPaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(paymentModelDao.getAccountId(), tenantContextWithAccountRecordId);

        return toDirectPayment(paymentModelDao, transactionsForAccount, pluginInfo);
    }

    private DirectPayment toDirectPayment(final DirectPaymentModelDao curDirectPaymentModelDao, final Iterable<DirectPaymentTransactionModelDao> transactionsModelDao, @Nullable final PaymentInfoPlugin pluginInfo) {
        final Ordering<DirectPaymentTransaction> perPaymentTransactionOrdering = Ordering.<DirectPaymentTransaction>from(new Comparator<DirectPaymentTransaction>() {
            @Override
            public int compare(final DirectPaymentTransaction o1, final DirectPaymentTransaction o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });

        final Iterable<DirectPaymentTransactionModelDao> filteredTransactions = Iterables.filter(transactionsModelDao, new Predicate<DirectPaymentTransactionModelDao>() {
            @Override
            public boolean apply(final DirectPaymentTransactionModelDao curDirectPaymentTransactionModelDao) {
                return curDirectPaymentTransactionModelDao.getDirectPaymentId().equals(curDirectPaymentModelDao.getId());
            }
        });

        final Iterable<DirectPaymentTransaction> transactions = Iterables.transform(filteredTransactions, new Function<DirectPaymentTransactionModelDao, DirectPaymentTransaction>() {
            @Override
            public DirectPaymentTransaction apply(final DirectPaymentTransactionModelDao input) {
                return new DefaultDirectPaymentTransaction(input.getId(), input.getCreatedDate(), input.getUpdatedDate(), input.getDirectPaymentId(),
                                                           input.getTransactionType(), input.getEffectiveDate(), 0, input.getPaymentStatus(), input.getAmount(), input.getCurrency(),
                                                           input.getGatewayErrorCode(), input.getGatewayErrorMsg(), pluginInfo);
            }
        });

        final List<DirectPaymentTransaction> sortedTransactions = perPaymentTransactionOrdering.immutableSortedCopy(transactions);
        return new DefaultDirectPayment(curDirectPaymentModelDao.getId(), curDirectPaymentModelDao.getCreatedDate(), curDirectPaymentModelDao.getUpdatedDate(), curDirectPaymentModelDao.getAccountId(),
                                        curDirectPaymentModelDao.getPaymentMethodId(), curDirectPaymentModelDao.getPaymentNumber(), curDirectPaymentModelDao.getExternalKey(), sortedTransactions);
    }

    private static interface PluginWrapper {

        PaymentInfoPlugin doPluginOperation(final PaymentPluginApi plugin, final Account account, final BigDecimal amount, final UUID directPaymentId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException;
    }

    private DirectPayment initiateDirectPayment(final TransactionType transactionType, final PluginWrapper pluginWrapper, @Nullable final UUID directPaymentId, final String externalKey, final Account account,
                                                final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        Preconditions.checkArgument(account.getCurrency().equals(currency), String.format("Currency %s doesn't match the one on the account (%s)", currency, currency));

        try {
            return paymentPluginDispatcher.dispatchWithTimeout(new CallableWithAccountLock<DirectPayment>(locker,
                                                                                                          account.getExternalKey(),
                                                                                                          new WithAccountLockCallback<DirectPayment>() {

                                                                                                              @Override
                                                                                                              public DirectPayment doOperation() throws PaymentApiException {
                                                                                                                  return createOrUpdateDirectPayment(directPaymentId, account, externalKey, transactionType, amount, currency, callContext, pluginWrapper, properties, context);
                                                                                                              }
                                                                                                          }
            ));
        } catch (final TimeoutException e) {
            // TODO PIERRE
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, account.getId(), null);
        } catch (final RuntimeException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, null);
        }
    }

    private DirectPayment createOrUpdateDirectPayment(final UUID directPaymentId, final Account account, final String externalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency, final InternalCallContext callContext, final PluginWrapper pluginWrapper, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        final DateTime utcNow = clock.getUTCNow();
        final DirectPaymentModelDao paymentModelDao;
        final DirectPaymentTransactionModelDao paymentTransactionModelDao;
        if (directPaymentId == null) {
            final UUID paymentMethodId = getDefaultPaymentMethodId(account);
            final DirectPaymentModelDao pmd = new DirectPaymentModelDao(utcNow, utcNow, account.getId(), paymentMethodId, externalKey);
            final DirectPaymentTransactionModelDao ptmd = new DirectPaymentTransactionModelDao(utcNow, utcNow, pmd.getId(),
                                                                                               transactionType, utcNow, PaymentStatus.UNKNOWN,
                                                                                               amount, currency, null, null);

            paymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(pmd, ptmd, callContext);
            paymentTransactionModelDao = paymentDao.getDirectTransactionsForAccount(account.getId(), callContext).get(0);
        } else {
            paymentModelDao = paymentDao.getDirectPayment(directPaymentId, callContext);
            if (paymentModelDao == null) {
                throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, directPaymentId);
            }

            final DirectPaymentTransactionModelDao ptmd = new DirectPaymentTransactionModelDao(utcNow, utcNow, directPaymentId,
                                                                                               transactionType, utcNow, PaymentStatus.UNKNOWN,
                                                                                               amount, currency, null, null);
            paymentTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(directPaymentId, ptmd, callContext);
        }

        return getDirectPayment(pluginWrapper, account, amount, currency, paymentModelDao.getId(), paymentTransactionModelDao.getId(), properties, context, callContext);
    }

    private DirectPayment createDirectPayment(final TransactionType transactionType, final PluginWrapper pluginWrapper, final UUID directPaymentId,
                                              final Account account, @Nullable final BigDecimal amount, @Nullable final Currency currency,
                                              final Iterable<PluginProperty> properties, final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        Preconditions.checkArgument((amount == null && currency == null) || account.getCurrency().equals(currency), String.format("Currency %s doesn't match the one on the account (%s)", currency, currency));

        try {
            return paymentPluginDispatcher.dispatchWithTimeout(new CallableWithAccountLock<DirectPayment>(locker,
                                                                                                          account.getExternalKey(),
                                                                                                          new WithAccountLockCallback<DirectPayment>() {

                                                                                                              @Override
                                                                                                              public DirectPayment doOperation() throws PaymentApiException {
                                                                                                                  final DateTime utcNow = clock.getUTCNow();
                                                                                                                  final DirectPaymentTransactionModelDao ptmd = new DirectPaymentTransactionModelDao(utcNow, utcNow, directPaymentId,
                                                                                                                                                                                                     transactionType, utcNow, PaymentStatus.UNKNOWN,
                                                                                                                                                                                                     amount, currency, null, null);
                                                                                                                  final DirectPaymentTransactionModelDao inserted = paymentDao.updateDirectPaymentWithNewTransaction(directPaymentId, ptmd, callContext);

                                                                                                                  return getDirectPayment(pluginWrapper, account, amount, currency, directPaymentId, inserted.getId(), properties, context, callContext);
                                                                                                              }
                                                                                                          }
            ));
        } catch (final TimeoutException e) {
            // TODO PIERRE
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, account.getId(), null);
        } catch (final RuntimeException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, null);
        }
    }

    private DirectPayment getDirectPayment(final PluginWrapper pluginWrapper, final Account account, @Nullable final BigDecimal amount, @Nullable final Currency currency,
                                           final UUID directPaymentId, final UUID directPaymentTransactionId, final Iterable<PluginProperty> properties,
                                           final CallContext context, final InternalCallContext callContext) throws PaymentApiException {
        final PaymentPluginApi plugin = getPaymentProviderPlugin(account, callContext);

        try {
            final PaymentInfoPlugin infoPlugin;
            try {
                infoPlugin = pluginWrapper.doPluginOperation(plugin, account, amount, directPaymentId, properties, context);
            } catch (final RuntimeException e) {
                // Handle case of plugin RuntimeException to be handled the same as a Plugin failure (PaymentPluginApiException)
                final String formatError = String.format("Plugin threw RuntimeException for direct payment %s", directPaymentId);
                throw new PaymentPluginApiException(formatError, e);
            }

            processPaymentInfoPlugin(infoPlugin, account, amount, currency, directPaymentId, directPaymentTransactionId, callContext);
        } catch (final PaymentPluginApiException e) {
            paymentDao.updateDirectPaymentAndTransactionOnCompletion(directPaymentId, PaymentStatus.PAYMENT_FAILURE_ABORTED, amount, currency, directPaymentTransactionId, null, e.getMessage(), callContext);
        }

        return getPayment(directPaymentId, false, properties, context, callContext);
    }

    private PaymentStatus processPaymentInfoPlugin(final PaymentInfoPlugin infoPlugin, final Account account, @Nullable final BigDecimal amount, @Nullable final Currency currency,
                                                   final UUID directPaymentId, final UUID directPaymentTransactionId, final InternalCallContext callContext) throws PaymentPluginApiException {
        final PaymentStatus paymentStatus;
        switch (infoPlugin.getStatus()) {
            case PROCESSED:
                paymentStatus = PaymentStatus.SUCCESS;
                break;
            case PENDING:
                paymentStatus = PaymentStatus.PENDING;
                break;
            case ERROR:
                paymentStatus = PaymentStatus.PLUGIN_FAILURE_ABORTED;
                break;
            case UNDEFINED:
            default:
                final String formatError = String.format("Plugin return status %s for direct payment %s", infoPlugin.getStatus(), directPaymentId);
                // This will be caught as a retryable Plugin failure
                throw new PaymentPluginApiException("", formatError);
        }

        // Update Payment/PaymentAttempt status
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(directPaymentId, paymentStatus, amount, currency,
                                                                 directPaymentTransactionId, infoPlugin.getGatewayErrorCode(), infoPlugin.getGatewayError(), callContext);

        return paymentStatus;
    }
}
