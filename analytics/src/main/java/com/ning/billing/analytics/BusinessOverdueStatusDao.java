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

package com.ning.billing.analytics;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessOverdueStatusSqlDao;
import com.ning.billing.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.JunctionApi;
import com.ning.billing.junction.api.Type;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BusinessOverdueStatusDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessOverdueStatusDao.class);

    private final BusinessOverdueStatusSqlDao overdueStatusSqlDao;

    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementApi;
    private final JunctionApi junctionApi;

    @Inject
    public BusinessOverdueStatusDao(final BusinessOverdueStatusSqlDao overdueStatusSqlDao, final AccountInternalApi accountApi,
                                    final EntitlementInternalApi entitlementApi, final JunctionApi junctionApi) {

        this.overdueStatusSqlDao = overdueStatusSqlDao;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.junctionApi = junctionApi;
    }

    public void overdueStatusChanged(final Type objectType, final UUID objectId, final InternalCallContext context) {
        if (Type.SUBSCRIPTION_BUNDLE.equals(objectType)) {
            overdueStatusChangedForBundle(objectId, context);
        } else {
            log.info("Ignoring overdue status change for object id {} (type {})", objectId.toString(), objectType.toString());
        }
    }

    private void overdueStatusChangedForBundle(final UUID bundleId, final InternalCallContext context) {
        final SubscriptionBundle bundle;
        try {
            bundle = entitlementApi.getBundleFromId(bundleId, context);
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring update for bundle {}: bundle does not exist", bundleId);
            return;
        }

        final Account account;
        try {
            account = accountApi.getAccountById(bundle.getAccountId(), context);
        } catch (AccountApiException e) {
            log.warn("Ignoring update for bundle {}: account {} does not exist", bundleId, bundle.getAccountId());
            return;
        }

        final String accountKey = account.getExternalKey();
        final String externalKey = bundle.getExternalKey();

        overdueStatusSqlDao.inTransaction(new Transaction<Void, BusinessOverdueStatusSqlDao>() {
            @Override
            public Void inTransaction(final BusinessOverdueStatusSqlDao transactional, final TransactionStatus status) throws Exception {
                log.info("Started rebuilding overdue statuses for bundle id {}", bundleId);
                transactional.deleteOverdueStatusesForBundle(bundleId.toString(), context);
                final List<BlockingState> blockingHistory = junctionApi.getBlockingHistory(bundleId, context.toTenantContext());
                if (blockingHistory != null && blockingHistory.size() > 0) {
                    final List<BlockingState> overdueStates = ImmutableList.<BlockingState>copyOf(blockingHistory);
                    final List<BlockingState> overdueStatesReversed = Lists.reverse(overdueStates);

                    DateTime previousStartDate = null;
                    for (final BlockingState state : overdueStatesReversed) {
                        final BusinessOverdueStatusModelDao overdueStatus = new BusinessOverdueStatusModelDao(accountKey, bundleId, previousStartDate,
                                                                                                              externalKey, state.getTimestamp(), state.getStateName());
                        log.info("Adding overdue state {}", overdueStatus);
                        overdueStatusSqlDao.createOverdueStatus(overdueStatus, context);

                        previousStartDate = state.getTimestamp();
                    }
                }

                log.info("Finished rebuilding overdue statuses for bundle id {}", bundleId);
                return null;
            }
        });
    }
}
