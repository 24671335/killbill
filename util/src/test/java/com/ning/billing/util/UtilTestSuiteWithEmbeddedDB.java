/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.customfield.api.DefaultCustomFieldUserApi;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.export.dao.DatabaseExportDao;
import com.ning.billing.util.glue.TestUtilModuleWithEmbeddedDB;
import com.ning.billing.util.tag.dao.DefaultTagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static org.testng.Assert.assertTrue;

public abstract class UtilTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB implements TestListenerStatus {

    private static final Logger log = LoggerFactory.getLogger(UtilTestSuiteWithEmbeddedDB.class);

    protected static final long DELAY = 10000;

    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected DefaultCustomFieldUserApi customFieldUserApi;
    @Inject
    protected CustomFieldDao customFieldDao;
    @Inject
    protected DatabaseExportDao dao;
    @Inject
    protected NotificationQueueService queueService;
    @Inject
    protected TagDefinitionDao tagDefinitionDao;
    @Inject
    protected DefaultTagDao tagDao;
    @Inject
    protected AuditDao auditDao;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected IDBI idbi;

    protected TestApiListener eventsListener;

    private boolean isListenerFailed;
    private String listenerFailedMsg;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleWithEmbeddedDB(configSource));
        g.injectMembers(this);

        eventsListener = new TestApiListener(this, idbi);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        resetTestListenerStatus();
        eventsListener.reset();

        eventBus.start();
        eventBus.register(eventsListener);

        controlCacheDispatcher.clearAll();

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        // Make sure we finish in a clean state
        assertListenerStatus();

        eventBus.unregister(eventsListener);
        eventBus.stop();
    }

    @Override
    public void failed(final String msg) {
        isListenerFailed = true;
        listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        isListenerFailed = false;
        listenerFailedMsg = null;
    }

    protected void assertListenerStatus() {
        assertTrue(eventsListener.isCompleted(DELAY));
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }
}
