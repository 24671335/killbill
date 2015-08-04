/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm.control;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.util.UUIDs;

import com.google.common.base.Preconditions;

public class DefaultControlInitiated implements LeavingStateCallback {

    private final PaymentStateControlContext stateContext;
    private final PaymentDao paymentDao;

    public DefaultControlInitiated(final PaymentStateContext stateContext, final PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
        this.stateContext = (PaymentStateControlContext) stateContext;
    }

    @Override
    public void leavingState(final State state) throws OperationException {
        if (stateContext.getPaymentId() != null && stateContext.getPaymentExternalKey() == null) {
            final PaymentModelDao payment = paymentDao.getPayment(stateContext.getPaymentId(), stateContext.getInternalCallContext());
            Preconditions.checkNotNull(payment, "payment cannot be null for id " + stateContext.getPaymentId());
            stateContext.setPaymentExternalKey(payment.getExternalKey());
        } else if (stateContext.getPaymentExternalKey() == null) {
            stateContext.setPaymentExternalKey(UUIDs.randomUUID().toString());
        }
        if (stateContext.getTransactionId() != null && stateContext.getPaymentTransactionExternalKey() == null) {
            final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(stateContext.getTransactionId(), stateContext.getInternalCallContext());
            Preconditions.checkNotNull(paymentTransactionModelDao, "paymentTransaction cannot be null for id " + stateContext.getTransactionId());
            stateContext.setPaymentTransactionExternalKey(paymentTransactionModelDao.getTransactionExternalKey());
        } else if (stateContext.getPaymentTransactionExternalKey() == null) {
            stateContext.setPaymentTransactionExternalKey(UUIDs.randomUUID().toString());
        }
    }
}
