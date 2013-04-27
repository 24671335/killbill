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
package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestBundleTransfer extends TestIntegrationBase {


    @Test(groups = "slow")
    public void testBundleTransferWithBPAnnualOnly() throws Exception {

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(3));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC

        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);

        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "mycutebundle", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       callContext));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId(), callContext).getCurrentPlan().getBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(40);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(17));

        busHandler.pushExpectedEvent(NextEvent.TRANSFER);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "mycutebundle", clock.getUTCNow(), false, false, callContext);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        List<Invoice> invoices =invoiceUserApi.getInvoicesByAccount(newAccount.getId(), callContext);
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        InvoiceItem theItem = invoiceItems.get(0);
        assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012,5,11)) == 0);
        assertTrue(theItem.getEndDate().compareTo(new LocalDate(2013,5,11)) == 0);
        assertTrue(theItem.getAmount().compareTo(new BigDecimal("2399.9500")) == 0);
    }

    @Test(groups = "slow")
    public void testBundleTransferWithBPMonthlyOnly() throws Exception {

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC

        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);

        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "mycutebundle", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       callContext));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId(), callContext).getCurrentPlan().getBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(32);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        busHandler.pushExpectedEvent(NextEvent.TRANSFER);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "mycutebundle", clock.getUTCNow(), false, false, callContext);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // Verify the BCD of the new account
        final Integer oldBCD = accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal();
        final Integer newBCD = accountUserApi.getAccountById(newAccount.getId(), callContext).getBillCycleDayLocal();
        assertEquals(oldBCD, (Integer) 1);
        // Day of the transfer
        assertEquals(newBCD, (Integer) 3);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(newAccount.getId(), callContext);
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        final InvoiceItem theItem = invoiceItems.get(0);
        assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012, 5, 3)) == 0);
        assertTrue(theItem.getEndDate().compareTo(new LocalDate(2012, 6, 3)) == 0);
        assertTrue(theItem.getAmount().compareTo(new BigDecimal("249.95")) == 0);
    }

    @Test(groups = "slow")
    public void testBundleTransferWithBPMonthlyOnlyWIthCancellationImm() throws Exception {

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(9));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC

        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);

        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "mycutebundle", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       callContext));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), callContext).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId(), callContext).getCurrentPlan().getBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(32);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithNonOsgiPaymentMethod(getAccountData(15));

        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        busHandler.pushExpectedEvent(NextEvent.TRANSFER);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE_ADJUSTMENT);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "mycutebundle", clock.getUTCNow(), false, true, callContext);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        List<Invoice> invoices =invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);


        // CHECK OLD ACCOUNTS ITEMS
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012,5,1), new LocalDate(2012,5,9), InvoiceItemType.RECURRING, new BigDecimal("66.66")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012,5,3), new LocalDate(2012,5,9), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-49.99")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012,5,3), new LocalDate(2012,5,3), InvoiceItemType.CBA_ADJ, new BigDecimal("49.99")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        // CHECK NEW ACCOUNT ITEMS
        invoices =invoiceUserApi.getInvoicesByAccount(newAccount.getId(), callContext);
        assertEquals(invoices.size(), 1);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012,5,3), new LocalDate(2012,5,15), InvoiceItemType.RECURRING, new BigDecimal("99.98")));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);
    }
}
