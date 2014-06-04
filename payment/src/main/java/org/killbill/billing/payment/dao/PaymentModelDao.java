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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;

public class PaymentModelDao extends EntityBase implements EntityModelDao<DirectPayment> {

    public static final Integer INVALID_PAYMENT_NUMBER = new Integer(-13);

    private UUID accountId;
    private UUID invoiceId;
    private UUID paymentMethodId;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal processedAmount;
    private Currency processedCurrency;
    private DateTime effectiveDate;
    private Integer paymentNumber;
    private PaymentStatus paymentStatus;
    private String extFirstPaymentRefId;
    private String extSecondPaymentRefId;

    public PaymentModelDao() { /* For the DAO mapper */ }

    public PaymentModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                           final UUID invoiceId, final UUID paymentMethodId,
                           final Integer paymentNumber, final BigDecimal amount, final Currency currency, final BigDecimal processedAmount, final Currency processedCurrency,
                           final PaymentStatus paymentStatus, final DateTime effectiveDate, final String extFirstPaymentRefId, final String extSecondPaymentRefId) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.amount = amount;
        this.currency = currency;
        this.processedAmount = processedAmount;
        this.processedCurrency = processedCurrency;
        this.paymentStatus = paymentStatus;
        this.effectiveDate = effectiveDate;
        this.extFirstPaymentRefId = extFirstPaymentRefId;
        this.extSecondPaymentRefId = extSecondPaymentRefId;
    }

    public PaymentModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentMethodId,
                           final BigDecimal amount, final Currency currency, final DateTime effectiveDate, final PaymentStatus paymentStatus) {
        this(UUID.randomUUID(), null, null, accountId, invoiceId, paymentMethodId, INVALID_PAYMENT_NUMBER, amount, currency, amount, currency, paymentStatus, effectiveDate, null, null);
    }

    public PaymentModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentMethodId,
                           final BigDecimal amount, final Currency currency, final DateTime effectiveDate) {
        this(UUID.randomUUID(), null, null, accountId, invoiceId, paymentMethodId, INVALID_PAYMENT_NUMBER, amount, currency, amount, currency, PaymentStatus.UNKNOWN, effectiveDate, null, null);
    }

    public PaymentModelDao(final PaymentModelDao src, final PaymentStatus newPaymentStatus) {
        this(src.getId(), src.getCreatedDate(), src.getUpdatedDate(), src.getAccountId(), src.getInvoiceId(), src.getPaymentMethodId(),
             src.getPaymentNumber(), src.getAmount(), src.getCurrency(), src.getProcessedAmount(), src.getProcessedCurrency(), newPaymentStatus, src.getEffectiveDate(), null, null);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getProcessedAmount() {
        return processedAmount;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getExtFirstPaymentRefId() {
        return extFirstPaymentRefId;
    }

    public String getExtSecondPaymentRefId() {
        return extSecondPaymentRefId;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public void setInvoiceId(final UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public void setProcessedAmount(final BigDecimal processedAmount) {
        this.processedAmount = processedAmount;
    }

    public void setProcessedCurrency(final Currency processedCurrency) {
        this.processedCurrency = processedCurrency;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public void setPaymentNumber(final Integer paymentNumber) {
        this.paymentNumber = paymentNumber;
    }

    public void setPaymentStatus(final PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setExtFirstPaymentRefId(final String extFirstPaymentRefId) {
        this.extFirstPaymentRefId = extFirstPaymentRefId;
    }

    public void setExtSecondPaymentRefId(final String extSecondPaymentRefId) {
        this.extSecondPaymentRefId = extSecondPaymentRefId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", paymentNumber=").append(paymentNumber);
        sb.append(", paymentStatus=").append(paymentStatus);
        sb.append(", extFirstPaymentRefId='").append(extFirstPaymentRefId).append('\'');
        sb.append(", extSecondPaymentRefId='").append(extSecondPaymentRefId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final PaymentModelDao that = (PaymentModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (processedAmount != null ? !processedAmount.equals(that.processedAmount) : that.processedAmount != null) {
            return false;
        }
        if (processedCurrency != that.processedCurrency) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (extFirstPaymentRefId != null ? !extFirstPaymentRefId.equals(that.extFirstPaymentRefId) : that.extFirstPaymentRefId != null) {
            return false;
        }
        if (extSecondPaymentRefId != null ? !extSecondPaymentRefId.equals(that.extSecondPaymentRefId) : that.extSecondPaymentRefId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (paymentStatus != that.paymentStatus) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (processedAmount != null ? processedAmount.hashCode() : 0);
        result = 31 * result + (processedCurrency != null ? processedCurrency.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (paymentStatus != null ? paymentStatus.hashCode() : 0);
        result = 31 * result + (extFirstPaymentRefId != null ? extFirstPaymentRefId.hashCode() : 0);
        result = 31 * result + (extSecondPaymentRefId != null ? extSecondPaymentRefId.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.PAYMENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.PAYMENT_HISTORY;
    }

}
