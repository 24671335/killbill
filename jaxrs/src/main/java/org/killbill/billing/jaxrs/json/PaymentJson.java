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
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.clock.DefaultClock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentJson extends JsonBase {

    private final BigDecimal paidAmount;
    private final BigDecimal amount;
    private final String accountId;
    private final String invoiceId;
    private final String paymentId;
    private final String paymentNumber;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final Integer retryCount;
    private final String currency;
    private final String status;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    private final String paymentMethodId;
    private final String bundleKeys;
    private final List<RefundJson> refunds;
    private final List<ChargebackJson> chargebacks;

    @JsonCreator
    public PaymentJson(@JsonProperty("amount") final BigDecimal amount,
                       @JsonProperty("paidAmount") final BigDecimal paidAmount,
                       @JsonProperty("accountId") final String accountId,
                       @JsonProperty("invoiceId") final String invoiceId,
                       @JsonProperty("paymentId") final String paymentId,
                       @JsonProperty("paymentNumber") final String paymentNumber,
                       @JsonProperty("paymentMethodId") final String paymentMethodId,
                       @JsonProperty("requestedDate") final DateTime requestedDate,
                       @JsonProperty("effectiveDate") final DateTime effectiveDate,
                       @JsonProperty("retryCount") final Integer retryCount,
                       @JsonProperty("currency") final String currency,
                       @JsonProperty("status") final String status,
                       @JsonProperty("gatewayErrorCode") final String gatewayErrorCode,
                       @JsonProperty("gatewayErrorMsg") final String gatewayErrorMsg,
                       @JsonProperty("externalBundleKeys") final String bundleKeys,
                       @JsonProperty("refunds") final List<RefundJson> refunds,
                       @JsonProperty("chargebacks") final List<ChargebackJson> chargebacks,
                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.paymentNumber = paymentNumber;
        this.paymentMethodId = paymentMethodId;
        this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
        this.effectiveDate = DefaultClock.toUTCDateTime(effectiveDate);
        this.currency = currency;
        this.retryCount = retryCount;
        this.status = status;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.bundleKeys = bundleKeys;
        this.refunds = refunds;
        this.chargebacks = chargebacks;
    }

    public PaymentJson(final DirectPayment payment, final String bundleExternalKey,
                       final List<RefundJson> refunds, final List<ChargebackJson> chargebacks) {
        this(payment, bundleExternalKey, refunds, chargebacks, null);
    }

    public PaymentJson(final DirectPayment payment, final String bundleExternalKey,
                       final List<RefundJson> refunds, final List<ChargebackJson> chargebacks,
                       @Nullable final List<AuditLog> auditLogs) {
        this(payment.getAuthAmount() /* TODO [PAYMENT] payment.getAmount() */,
             payment.getCapturedAmount() /* TODO [PAYMENT] payment.getPaidAmount() */,
             payment.getAccountId().toString(),
             null /* TODO [PAYMENT] payment.getInvoiceId().toString() */, payment.getId().toString(),
             payment.getPaymentNumber().toString(),
             payment.getPaymentMethodId().toString(),
             payment.getCreatedDate(), payment.getCreatedDate(),
             1 /* TODO [PAYMENT] payment.getAttempts().size() */,
             payment.getCurrency().toString(),
             null /*payment.getPaymentStatus().toString() */,
             null /*payment.getAttempts().get(payment.getAttempts().size() - 1).getGatewayErrorCode() */,
             null /*payment.getAttempts().get(payment.getAttempts().size() - 1).getGatewayErrorMsg() */,
             bundleExternalKey, refunds, chargebacks, toAuditLogJson(auditLogs));
    }

    public PaymentJson(final DirectPayment payment, final List<AuditLog> auditLogs) {
        this(payment, null, null, null, auditLogs);
    }

    public String getBundleKeys() {
        return bundleKeys;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public List<RefundJson> getRefunds() {
        return refunds;
    }

    public List<ChargebackJson> getChargebacks() {
        return chargebacks;
    }

    @Override
    public String toString() {
        return "PaymentJson{" +
               "paidAmount=" + paidAmount +
               ", amount=" + amount +
               ", accountId='" + accountId + '\'' +
               ", invoiceId='" + invoiceId + '\'' +
               ", paymentId='" + paymentId + '\'' +
               ", paymentNumber='" + paymentNumber + '\'' +
               ", requestedDate=" + requestedDate +
               ", effectiveDate=" + effectiveDate +
               ", retryCount=" + retryCount +
               ", currency='" + currency + '\'' +
               ", status='" + status + '\'' +
               ", gatewayErrorCode='" + gatewayErrorCode + '\'' +
               ", gatewayErrorMsg='" + gatewayErrorMsg + '\'' +
               ", paymentMethodId='" + paymentMethodId + '\'' +
               ", bundleKeys='" + bundleKeys + '\'' +
               ", refunds=" + refunds +
               ", chargebacks=" + chargebacks +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PaymentJson that = (PaymentJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (bundleKeys != null ? !bundleKeys.equals(that.bundleKeys) : that.bundleKeys != null) {
            return false;
        }
        if (chargebacks != null ? !chargebacks.equals(that.chargebacks) : that.chargebacks != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (gatewayErrorCode != null ? !gatewayErrorCode.equals(that.gatewayErrorCode) : that.gatewayErrorCode != null) {
            return false;
        }
        if (gatewayErrorMsg != null ? !gatewayErrorMsg.equals(that.gatewayErrorMsg) : that.gatewayErrorMsg != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (paidAmount != null ? paidAmount.compareTo(that.paidAmount) != 0 : that.paidAmount != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (refunds != null ? !refunds.equals(that.refunds) : that.refunds != null) {
            return false;
        }
        if (requestedDate != null ? requestedDate.compareTo(that.requestedDate) != 0 : that.requestedDate != null) {
            return false;
        }
        if (retryCount != null ? !retryCount.equals(that.retryCount) : that.retryCount != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = paidAmount != null ? paidAmount.hashCode() : 0;
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (retryCount != null ? retryCount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (gatewayErrorCode != null ? gatewayErrorCode.hashCode() : 0);
        result = 31 * result + (gatewayErrorMsg != null ? gatewayErrorMsg.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (bundleKeys != null ? bundleKeys.hashCode() : 0);
        result = 31 * result + (refunds != null ? refunds.hashCode() : 0);
        result = 31 * result + (chargebacks != null ? chargebacks.hashCode() : 0);
        return result;
    }
}
