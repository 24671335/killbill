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

package com.ning.billing.invoice;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.TestInvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class InvoiceTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTestSuiteWithEmbeddedDB.class);

    protected static final Currency accountCurrency = Currency.USD;

    @Inject
    protected InvoiceService invoiceService;
    @Inject
    protected InternalBus bus;
    @Inject
    protected CacheControllerDispatcher controllerDispatcher;
    @Inject
    protected InvoiceUserApi invoiceUserApi;
    @Inject
    protected InvoicePaymentApi invoicePaymentApi;
    @Inject
    protected InvoiceMigrationApi migrationApi;
    @Inject
    protected InvoiceGenerator generator;
    @Inject
    protected BillingInternalApi billingApi;
    @Inject
    protected AccountInternalApi accountApi;
    @Inject
    protected EntitlementInternalApi entitlementApi;
    @Inject
    protected BusService busService;
    @Inject
    protected InvoiceDao invoiceDao;
    @Inject
    protected TagUserApi tagUserApi;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected Clock clock;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected InvoiceInternalApi invoiceInternalApi;
    @Inject
    protected NextBillingDateNotifier nextBillingDateNotifier;
    @Inject
    protected NotificationQueueService notificationQueueService;
    @Inject
    protected TestInvoiceHelper invoiceUtil;
    @Inject
    protected TestInvoiceNotificationQListener testInvoiceNotificationQListener;

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = InvoiceTestSuiteNoDB.class.getResource(resource);
        Assert.assertNotNull(url);

        configSource.merge(url);
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        loadSystemPropertiesFromClasspath("/resource.properties");

        final Injector injector = Guice.createInjector(new TestInvoiceModuleWithEmbeddedDb(configSource));
        injector.injectMembers(this);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        controllerDispatcher.clearAll();
        bus.start();
        restartInvoiceService(invoiceService);
    }

    private void restartInvoiceService(final InvoiceService invoiceService) throws Exception {
        ((DefaultInvoiceService) invoiceService).initialize();
        ((DefaultInvoiceService) invoiceService).registerForNotifications();
        ((DefaultInvoiceService) invoiceService).start();
    }

    private void stopInvoiceService(final InvoiceService invoiceService) throws Exception {
        ((DefaultInvoiceService) invoiceService).unregisterForNotifications();
        ((DefaultInvoiceService) invoiceService).stop();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        bus.stop();
        stopInvoiceService(invoiceService);
    }
}
