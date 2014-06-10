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

package org.killbill.billing.payment.bus;

import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.control.InvoicePaymentControlPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceHandler {

    private final AccountInternalApi accountApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final DirectPaymentApi paymentApi;
    private final NonEntityDao nonEntityDao;

    private static final Logger log = LoggerFactory.getLogger(InvoiceHandler.class);

    private final static PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }
        @Override
        public boolean shouldRetryOnFailure() {
            return true;
        }
        @Override
        public String getRetryPluginName() {
            return InvoicePaymentControlPluginApi.PLUGIN_NAME;
        }
    };

    @Inject
    public InvoiceHandler(final AccountInternalApi accountApi,
                          final DirectPaymentApi paymentApi,
                          final NonEntityDao nonEntityDao,
                          final InternalCallContextFactory internalCallContextFactory) {
        this.accountApi = accountApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentApi = paymentApi;
        this.nonEntityDao = nonEntityDao;
    }

    @Subscribe
    public void processInvoiceEvent(final InvoiceCreationInternalEvent event) {

        log.info("Received invoice creation notification for account {} and invoice {}",
                 event.getAccountId(), event.getInvoiceId());

        final Account account;
        try {
            final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
            account = accountApi.getAccountById(event.getAccountId(), internalContext);

            final CallContext callContext = internalContext.toCallContext(nonEntityDao.retrieveIdFromObject(internalContext.getTenantRecordId(), ObjectType.TENANT));
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, null, account.getCurrency(), event.getInvoiceId().toString(), UUID.randomUUID().toString(),
                                                        ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        } catch (final AccountApiException e) {
            log.error("Failed to process invoice payment", e);
        } catch (final PaymentApiException e) {
            // Log as error unless:
            if (e.getCode() != ErrorCode.PAYMENT_NULL_INVOICE.getCode() /* Nothing left to be paid */ &&
                e.getCode() != ErrorCode.PAYMENT_CREATE_PAYMENT.getCode() /* User payment error */) {
                log.error("Failed to process invoice payment {}", e.toString());
            }
        }
    }
}


