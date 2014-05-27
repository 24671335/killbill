/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface DirectTransactionSqlDao extends EntitySqlDao<DirectPaymentTransactionModelDao, DirectPaymentTransaction> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateTransactionStatus(@Bind("id") final String transactionId,
                                 @Bind("paymentStatus") final String paymentStatus,
                                 @Bind("gatewayErrorCode") final String gatewayErrorCode,
                                 @Bind("gatewayErrorMsg") final String gatewayErrorMsg,
                                 @BindBean final InternalCallContext context);

    @SqlQuery
    public List<DirectPaymentTransactionModelDao> getByDirectPaymentId(@Bind("directPaymentId") final UUID directPaymentId,
                                                                       @BindBean final InternalTenantContext context);
}
