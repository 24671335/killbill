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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;

public class DirectPaymentAutomatonDAOHelper {

    protected final DirectPaymentStateContext directPaymentStateContext;
    protected final DateTime utcNow;
    protected final InternalCallContext internalCallContext;

    protected final PaymentDao paymentDao;

    private final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;

    // Used to build new payments and transactions
    public DirectPaymentAutomatonDAOHelper(final DirectPaymentStateContext directPaymentStateContext,
                                           final DateTime utcNow, final PaymentDao paymentDao,
                                           final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                           final InternalCallContext internalCallContext) throws PaymentApiException {
        this.directPaymentStateContext = directPaymentStateContext;
        this.utcNow = utcNow;
        this.paymentDao = paymentDao;
        this.pluginRegistry = pluginRegistry;
        this.internalCallContext = internalCallContext;
    }

    public void createNewDirectPaymentTransaction() {
        final DirectPaymentTransactionModelDao paymentTransactionModelDao;
        if (directPaymentStateContext.getDirectPaymentId() == null) {
            final DirectPaymentModelDao newPaymentModelDao = buildNewDirectPaymentModelDao();
            final DirectPaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao(newPaymentModelDao.getId());

            final DirectPaymentModelDao paymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
            paymentTransactionModelDao = paymentDao.getDirectTransactionsForDirectPayment(paymentModelDao.getId(), internalCallContext).get(0);
        } else {
            final DirectPaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao(directPaymentStateContext.getDirectPaymentId());
            paymentTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(directPaymentStateContext.getDirectPaymentId(), newPaymentTransactionModelDao, internalCallContext);
        }

        // Update the context
        directPaymentStateContext.setDirectPaymentTransactionModelDao(paymentTransactionModelDao);
    }

    public void processPaymentInfoPlugin(final PaymentStatus paymentStatus, @Nullable final PaymentInfoPlugin paymentInfoPlugin,
                                         final String currentPaymentStateName) {
        final BigDecimal processedAmount = paymentInfoPlugin == null ? null : paymentInfoPlugin.getAmount();
        final Currency processedCurrency = paymentInfoPlugin == null ? null : paymentInfoPlugin.getCurrency();
        final String gatewayErrorCode = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayErrorCode();
        final String gatewayErrorMsg = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayError();

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(directPaymentStateContext.getDirectPaymentId(),
                                                                 currentPaymentStateName,
                                                                 directPaymentStateContext.getDirectPaymentTransactionModelDao().getId(),
                                                                 paymentStatus,
                                                                 processedAmount,
                                                                 processedCurrency,
                                                                 gatewayErrorCode,
                                                                 gatewayErrorMsg,
                                                                 internalCallContext);

        // Update the context
        directPaymentStateContext.setDirectPaymentTransactionModelDao(paymentDao.getDirectPaymentTransaction(directPaymentStateContext.getDirectPaymentTransactionModelDao().getId(), internalCallContext));
    }

    public UUID getDefaultPaymentMethodId() throws PaymentApiException {
        final UUID paymentMethodId = directPaymentStateContext.getAccount().getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, directPaymentStateContext.getAccount().getId());
        }
        return paymentMethodId;
    }

    public PaymentPluginApi getPaymentProviderPlugin() throws PaymentApiException {
        final UUID paymentMethodId = directPaymentStateContext.getPaymentMethodId();
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, internalCallContext);
        if (methodDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return getPaymentPluginApi(methodDao.getPluginName());
    }

    public DirectPaymentModelDao getDirectPayment() throws PaymentApiException {
        final DirectPaymentModelDao paymentModelDao;
        paymentModelDao = paymentDao.getDirectPayment(directPaymentStateContext.getDirectPaymentId(), internalCallContext);
        if (paymentModelDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, directPaymentStateContext.getDirectPaymentId());
        }
        return paymentModelDao;
    }

    private DirectPaymentModelDao buildNewDirectPaymentModelDao() {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;

        return new DirectPaymentModelDao(createdDate,
                                         updatedDate,
                                         directPaymentStateContext.getAccount().getId(),
                                         directPaymentStateContext.getPaymentMethodId(),
                                         directPaymentStateContext.getDirectPaymentExternalKey());
    }

    private DirectPaymentTransactionModelDao buildNewDirectPaymentTransactionModelDao(final UUID directPaymentId) {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;
        final DateTime effectiveDate = utcNow;
        final String gatewayErrorCode = null;
        final String gatewayErrorMsg = null;

        return new DirectPaymentTransactionModelDao(createdDate,
                                                    updatedDate,
                                                    directPaymentStateContext.getDirectPaymentTransactionExternalKey(),
                                                    directPaymentId,
                                                    directPaymentStateContext.getTransactionType(),
                                                    effectiveDate,
                                                    PaymentStatus.UNKNOWN,
                                                    directPaymentStateContext.getAmount(),
                                                    directPaymentStateContext.getCurrency(),
                                                    gatewayErrorCode,
                                                    gatewayErrorMsg);
    }

    private PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
    }
}
