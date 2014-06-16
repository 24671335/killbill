/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class RefundJson extends JsonBase {

    private final String refundId;
    private final String paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final Boolean isAdjusted;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final String status;
    private final List<InvoiceItemJson> adjustments;

    @JsonCreator
    public RefundJson(@JsonProperty("refundId") final String refundId,
                      @JsonProperty("paymentId") final String paymentId,
                      @JsonProperty("amount") final BigDecimal amount,
                      @JsonProperty("currency") final String currency,
                      @JsonProperty("status") final String status,
                      @JsonProperty("adjusted") final Boolean isAdjusted,
                      @JsonProperty("requestedDate") final DateTime requestedDate,
                      @JsonProperty("effectiveDate") final DateTime effectiveDate,
                      @JsonProperty("adjustments") @Nullable final List<InvoiceItemJson> adjustments,
                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.isAdjusted = isAdjusted;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.adjustments = adjustments;
    }

    public RefundJson(final DirectPayment refund) {
        this(refund, null, null);
    }

    public RefundJson(final DirectPayment refund, @Nullable final List<InvoiceItem> adjustments, @Nullable final List<AuditLog> auditLogs) {
        this(refund.getId().toString(), refund.getId().toString(), refund.getRefundedAmount(), refund.getCurrency().toString(),
             null /* TODO [PAYMENT] refund.getRefundStatus().toString() */, false /* TODO [PAYMENT] refund.isAdjusted() */, refund.getCreatedDate(), refund.getCreatedDate(),
             adjustments == null ? null : ImmutableList.<InvoiceItemJson>copyOf(Collections2.transform(adjustments, new Function<InvoiceItem, InvoiceItemJson>() {
                 @Override
                 public InvoiceItemJson apply(@Nullable final InvoiceItem input) {
                     return new InvoiceItemJson(input);
                 }
             })),
             toAuditLogJson(auditLogs));
    }

    public String getRefundId() {
        return refundId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isAdjusted() {
        return isAdjusted;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public List<InvoiceItemJson> getAdjustments() {
        return adjustments;
    }

    public String getStatus() { return status; }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RefundJson");
        sb.append("{refundId='").append(refundId).append('\'');
        sb.append(", paymentId='").append(paymentId).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", status=").append(status);
        sb.append(", isAdjusted=").append(isAdjusted);
        sb.append(", requestedDate=").append(requestedDate);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", adjustments=").append(adjustments);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = refundId != null ? refundId.hashCode() : 0;
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (isAdjusted != null ? isAdjusted.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (adjustments != null ? adjustments.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!this.equalsNoIdNoDates(obj)) {
            return false;
        } else {
            final RefundJson other = (RefundJson) obj;
            if (refundId == null) {
                if (other.getRefundId() != null) {
                    return false;
                }
            } else if (!refundId.equals(other.getRefundId())) {
                return false;
            }

            if (requestedDate == null) {
                if (other.getRequestedDate() != null) {
                    return false;
                }
            } else if (requestedDate.compareTo(other.getRequestedDate()) != 0) {
                return false;
            }

            if (effectiveDate == null) {
                if (other.getEffectiveDate() != null) {
                    return false;
                }
            } else if (effectiveDate.compareTo(other.getEffectiveDate()) != 0) {
                return false;
            }

            return true;
        }
    }

    public boolean equalsNoIdNoDates(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final RefundJson other = (RefundJson) obj;
        if (isAdjusted == null) {
            if (other.isAdjusted != null) {
                return false;
            }
        } else if (!isAdjusted.equals(other.isAdjusted)) {
            return false;
        }

        if (paymentId == null) {
            if (other.paymentId != null) {
                return false;
            }
        } else if (!paymentId.equals(other.paymentId)) {
            return false;
        }

        if (amount == null) {
            if (other.amount != null) {
                return false;
            }
        } else if (!amount.equals(other.amount)) {
            return false;
        }

        if (currency == null) {
            if (other.currency != null) {
                return false;
            }
        } else if (!currency.equals(other.currency)) {
            return false;
        }

        if (status == null) {
            if (other.status != null) {
                return false;
            }
        } else if (!status.equals(other.status)) {
            return false;
        }

        if (adjustments == null) {
            if (other.adjustments != null) {
                return false;
            }
        } else if (!adjustments.equals(other.adjustments)) {
            return false;
        }

        return true;
    }
}
