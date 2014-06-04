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

package org.killbill.billing.payment.api.svcs;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentInternalApi;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;

public class DefaultPaymentInternalApi implements PaymentInternalApi {

    private final PaymentProcessor paymentProcessor;
    private final PaymentMethodProcessor methodProcessor;

    @Inject
    public DefaultPaymentInternalApi(final PaymentProcessor paymentProcessor, final PaymentMethodProcessor methodProcessor) {
        this.paymentProcessor = paymentProcessor;
        this.methodProcessor = methodProcessor;
    }

    @Override
    public DirectPayment getPayment(final UUID paymentId, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        final DirectPayment payment = paymentProcessor.getPayment(paymentId, false, properties, context);
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedInactive, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedInactive, false, properties, context);
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final InternalTenantContext context) throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, context);
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, false, properties, context);
    }
}
