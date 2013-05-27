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

package com.ning.billing.analytics.model;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionState;
import com.ning.billing.mock.MockPlan;
import com.ning.billing.mock.MockSubscription;

public class TestBusinessSubscriptionEvent extends AnalyticsTestSuiteNoDB {

    private Product product;
    private Plan plan;
    private PlanPhase phase;
    private Subscription subscription;

    private final Catalog catalog = Mockito.mock(Catalog.class);

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        plan = new MockPlan("platinum-monthly", product);
        phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(catalog.findPhase(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(phase);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);

        subscription = new MockSubscription(SubscriptionState.ACTIVE, plan, phase);
    }

    @Test(groups = "fast")
    public void testValueOf() throws Exception {
        BusinessSubscriptionEvent event;

        event = BusinessSubscriptionEvent.valueOf("ADD_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);

        event = BusinessSubscriptionEvent.valueOf("CANCEL_BASE");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.BASE);

        event = BusinessSubscriptionEvent.valueOf("SYSTEM_CANCEL_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);
    }

    @Test(groups = "fast")
    public void testFromSubscription() throws Exception {
        BusinessSubscriptionEvent event;

        final DateTime now = new DateTime();

        event = BusinessSubscriptionEvent.subscriptionCreated(subscription.getCurrentPlan().getName(), catalog, now, now);
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "ADD_BASE");

        event = BusinessSubscriptionEvent.subscriptionCancelled(subscription.getCurrentPlan().getName(), catalog, now, now);
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "CANCEL_BASE");

        event = BusinessSubscriptionEvent.subscriptionChanged(subscription.getCurrentPlan().getName(), catalog, now, now);
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CHANGE);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "CHANGE_BASE");

        event = BusinessSubscriptionEvent.subscriptionPhaseChanged(subscription.getCurrentPlan().getName(), subscription.getState(), catalog, now, now);
        // The subscription is still active, it's a system change
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CHANGE);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "SYSTEM_CHANGE_BASE");

        subscription = new MockSubscription(SubscriptionState.CANCELLED, plan, phase);
        event = BusinessSubscriptionEvent.subscriptionPhaseChanged(subscription.getCurrentPlan().getName(), subscription.getState(), catalog, now, now);
        // The subscription is cancelled, it's a system cancellation
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "SYSTEM_CANCEL_BASE");
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final DateTime now = new DateTime();
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(subscription.getCurrentPlan().getName(), catalog, now, now);
        Assert.assertSame(event, event);
        Assert.assertEquals(event, event);
        Assert.assertTrue(event.equals(event));
    }
}
