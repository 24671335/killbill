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

package com.ning.billing.catalog;

import java.math.BigDecimal;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.CurrencyValueNull;
import com.ning.billing.catalog.api.Price;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationErrors;

@Embeddable
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPrice extends ValidatingConfig<StandaloneCatalog> implements Price {
    
    @Enumerated(EnumType.STRING)
    @XmlElement(required = true)
    private Currency currency;

    @XmlElement(required = true, nillable = true)
    private BigDecimal value;

    public DefaultPrice() {
        // for serialization support
    }

    public DefaultPrice(final BigDecimal value, final Currency currency) {
        // for sanity support
        this.value = value;
        this.currency = currency;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPrice#getCurrency()
      */
    @Override
    public Currency getCurrency() {
        return currency;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPrice#getValue()
      */
    @Override
    public BigDecimal getValue() throws CurrencyValueNull {
        if (value == null) {
            throw new CurrencyValueNull(currency);
        }
        return value;
    }

    protected DefaultPrice setCurrency(final Currency currency) {
        this.currency = currency;
        return this;
    }

    protected DefaultPrice setValue(final BigDecimal value) {
        this.value = value;
        return this;
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;

    }
}
