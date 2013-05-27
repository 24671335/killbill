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

package com.ning.billing.ovedue.notification;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Type;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.jackson.ObjectMapper;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationQueue;

public class TestDefaultOverdueCheckPoster extends OverdueTestSuiteWithEmbeddedDB {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private EntitySqlDaoTransactionalJdbiWrapper entitySqlDaoTransactionalJdbiWrapper;
    private NotificationQueue overdueQueue;
    private DateTime testReferenceTime;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        entitySqlDaoTransactionalJdbiWrapper = new EntitySqlDaoTransactionalJdbiWrapper(getDBI(), clock, cacheControllerDispatcher, nonEntityDao);

        overdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                     DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
        Assert.assertTrue(overdueQueue.isStarted());

        testReferenceTime = clock.getUTCNow();
    }

    @Test(groups = "slow")
    public void testShouldntInsertMultipleNotificationsPerOverdueable() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final Blockable overdueable = Mockito.mock(Subscription.class);
        Mockito.when(overdueable.getId()).thenReturn(subscriptionId);

        insertOverdueCheckAndVerifyQueueContent(overdueable, 10, 10);
        insertOverdueCheckAndVerifyQueueContent(overdueable, 5, 5);
        insertOverdueCheckAndVerifyQueueContent(overdueable, 15, 5);

        // Check we don't conflict with other overdueables
        final UUID bundleId = UUID.randomUUID();
        final Blockable otherOverdueable = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(otherOverdueable.getId()).thenReturn(bundleId);

        insertOverdueCheckAndVerifyQueueContent(otherOverdueable, 10, 10);
        insertOverdueCheckAndVerifyQueueContent(otherOverdueable, 5, 5);
        insertOverdueCheckAndVerifyQueueContent(otherOverdueable, 15, 5);

        // Verify the final content of the queue
        Assert.assertEquals(overdueQueue.getFutureNotificationsForAccount(internalCallContext).size(), 2);
    }

    private void insertOverdueCheckAndVerifyQueueContent(final Blockable overdueable, final int nbDaysInFuture, final int expectedNbDaysInFuture) throws IOException {
        final DateTime futureNotificationTime = testReferenceTime.plusDays(nbDaysInFuture);
        poster.insertOverdueCheckNotification(overdueable, futureNotificationTime, internalCallContext);

        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(overdueable.getId(), Type.get(overdueable));
        final List<Notification> notificationsForKey = getNotificationsForOverdueable(overdueable);
        Assert.assertEquals(notificationsForKey.size(), 1);
        Assert.assertEquals(notificationsForKey.get(0).getNotificationKey(), objectMapper.writeValueAsString(notificationKey));
        Assert.assertEquals(notificationsForKey.get(0).getEffectiveDate(), testReferenceTime.plusDays(expectedNbDaysInFuture));
    }

    private List<Notification> getNotificationsForOverdueable(final Blockable overdueable) {
        return entitySqlDaoTransactionalJdbiWrapper.execute(new EntitySqlDaoTransactionWrapper<List<Notification>>() {
            @Override
            public List<Notification> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return ((DefaultOverdueCheckPoster) poster).getFutureNotificationsForAccountAndOverdueableInTransaction(entitySqlDaoWrapperFactory, overdueQueue, overdueable, internalCallContext);
            }
        });
    }
}
