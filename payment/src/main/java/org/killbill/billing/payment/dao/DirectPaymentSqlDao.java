/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.dao;

import java.util.Iterator;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface DirectPaymentSqlDao extends EntitySqlDao<DirectPaymentModelDao, DirectPayment> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateDirectPaymentForNewTransaction(@Bind("id") final String directPaymentId,
                                              @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateCurrentPaymentStateName(@Bind("id") final String directPaymentId,
                                       @Bind("currentStateName") final String currentStateName,
                                       @BindBean final InternalCallContext context);


    @SqlQuery
    public DirectPaymentModelDao getDirectPaymentByExternalKey(@Bind("externalKey") final String externalKey,
                                                               @BindBean final InternalTenantContext context);
    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<DirectPaymentModelDao> getByPluginName(@Bind("pluginName") final String pluginName,
                                                           @Bind("offset") final Long offset,
                                                           @Bind("rowCount") final Long rowCount,
                                                           @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getCountByPluginName(@Bind("pluginName") final String pluginName,
                                     @BindBean final InternalTenantContext context);
}
