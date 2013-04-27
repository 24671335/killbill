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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;

import com.google.inject.Inject;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestCustomFieldApi extends TestIntegrationBase {


    private Account account;

    @Inject
    private CustomFieldUserApi customFieldApi;

    @Override
    @BeforeMethod(groups = {"slow"})
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
    }

    @Test(groups = "slow")
    public void testCustomFieldForAccount() throws CustomFieldApiException {
        addCustomField("name1", "value1", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());
        addCustomField("name2", "value2", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());

        List<CustomField> fields = customFieldApi.getCustomFieldsForAccount(account.getId(), callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForAccountType(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(fields.size(), 2);
    }


    @Test(groups = "slow")
    public void testCustomFieldForInvoice() throws CustomFieldApiException, EntitlementUserApiException {

        //
        // Create necessary logic to end up with an Invoice object on that account.
        //
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       callContext));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        Assert.assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        Assert.assertEquals(invoice.getAccountId(), account.getId());


        addCustomField("name1", "value1", invoice.getId(), ObjectType.INVOICE, clock.getUTCNow());
        addCustomField("name2", "value2", invoice.getId(), ObjectType.INVOICE, clock.getUTCNow());

        List<CustomField> fields = customFieldApi.getCustomFieldsForAccount(invoice.getId(), callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForAccountType(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        //
        // Add custom field on account and retry
        //
        addCustomField("foo", "bar", account.getId(), ObjectType.ACCOUNT, clock.getUTCNow());

        fields = customFieldApi.getCustomFieldsForAccount(invoice.getId(), callContext);
        Assert.assertEquals(fields.size(), 3);

        fields = customFieldApi.getCustomFieldsForAccountType(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);

        fields = customFieldApi.getCustomFieldsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(fields.size(), 2);
    }

    private void addCustomField(String name, String value, UUID objectId, ObjectType type, DateTime createdDate) throws CustomFieldApiException {
        CustomField f = new StringCustomField(name, value, type, objectId, clock.getUTCNow());
        busHandler.pushExpectedEvents(NextEvent.CUSTOM_FIELD);
        List<CustomField> fields = new ArrayList<CustomField>();
        fields.add(f);
        customFieldApi.addCustomFields(fields, callContext);
        assertTrue(busHandler.isCompleted(DELAY));

    }

}
