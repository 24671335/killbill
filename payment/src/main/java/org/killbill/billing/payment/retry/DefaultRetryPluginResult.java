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

package org.killbill.billing.payment.retry;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.killbill.billing.retry.plugin.api.RetryPluginApi.RetryPluginResult;

public class DefaultRetryPluginResult implements RetryPluginResult {

    private final boolean isAborted;
    private final DateTime nextRetryDate;
    private final BigDecimal adjustedRetryAmount;

    public DefaultRetryPluginResult(final boolean isAborted, final DateTime nextRetryDate, final BigDecimal adjustedRetryAmount) {
        this.isAborted = isAborted;
        this.nextRetryDate = nextRetryDate;
        this.adjustedRetryAmount = adjustedRetryAmount;
    }

    public DefaultRetryPluginResult(final boolean isAborted) {
        this(isAborted, null, null);
    }

    @Override
    public boolean isAborted() {
        return isAborted;
    }

    @Override
    public DateTime getNextRetryDate() {
        return nextRetryDate;
    }

    @Override
    public BigDecimal getAdjustedAmount() {
        return adjustedRetryAmount;
    }
}
