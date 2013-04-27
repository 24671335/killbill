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

import java.net.URI;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Limit;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationError;
import com.ning.billing.util.config.catalog.ValidationErrors;

@Entity
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPlanPhase extends ValidatingConfig<StandaloneCatalog> implements PlanPhase {
    @SuppressWarnings("unused")
    @Id @GeneratedValue 
    private long id; // set id automatically

    @XmlAttribute(required = true)
    private PhaseType type;

    @XmlElement(required = true)
    private DefaultDuration duration;

    @XmlElement(required = true)
    private BillingPeriod billingPeriod;

    @XmlElement(required = false)
    private DefaultInternationalPrice recurringPrice;

    @XmlElement(required = false)
    private DefaultInternationalPrice fixedPrice;

    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];

    //Not exposed in XML
    @OneToOne(cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private DefaultPlan plan;

    public static String phaseName(final String planName, final PhaseType phasetype) {
        return planName + "-" + phasetype.toString().toLowerCase();
    }

    public static String planName(final String phaseName) throws CatalogApiException {
        for (final PhaseType type : PhaseType.values()) {
            if (phaseName.endsWith(type.toString().toLowerCase())) {
                return phaseName.substring(0, phaseName.length() - type.toString().length() - 1);
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_BAD_PHASE_NAME, phaseName);
    }
    
    DefaultPlanPhase(){}

    DefaultPlanPhase(PhaseType type, DefaultDuration duration, BillingPeriod billingPeriod,
            DefaultInternationalPrice recurringPrice, DefaultInternationalPrice fixedPrice, DefaultLimit[] limits) {
        this.type = type;
        this.duration = duration;
        this.billingPeriod = billingPeriod;
        this.recurringPrice = recurringPrice;
        this.fixedPrice = fixedPrice;
        this.limits = limits = new DefaultLimit[0];
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getRecurringPrice()
      */
    @Override
    public DefaultInternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getInternationalPrice()
      */
    @Override
    public InternationalPrice getFixedPrice() {
        return fixedPrice;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getCohort()
      */
    @Override
    public PhaseType getPhaseType() {
        return type;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getBillCycleDuration()
      */
    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getName()
      */
    @Override
    public String getName() {
        return phaseName(plan.getName(), this.getPhaseType());
    }

    /* (non-Javadoc)
      * @see com.ning.billing.catalog.IPlanPhase#getPlan()
      */
    @Override
    public Plan getPlan() {
        return plan;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.ning.billing.catalog.IPlanPhase#getDuration()
     */
    @Override
    public Duration getDuration() {
        return duration;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.ning.billing.catalog.PlanPhase#getLimit()
     */
    @Override
    public DefaultLimit[] getLimits() {
        return limits;
    }
    
    
    protected Limit findLimit(String unit) {
        for(Limit limit: limits) {
            if(limit.getUnit().getName().equals(unit) ) {
                    return limit;
            }
        }
        return null;
    }
    
    
    @Override
    public boolean compliesWithLimits(String unit, double value) {
        Limit l = findLimit(unit);
        if (l == null) {
            return getPlan().getProduct().compliesWithLimits(unit, value);
        }
        return l.compliesWith(value);
    }


    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        //Validation: check for nulls
        if (billingPeriod == null) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has a recurring price but no billing period", type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }

        //Validation: if there is a recurring price there must be a billing period
        if ((recurringPrice != null) && (billingPeriod == null || billingPeriod == BillingPeriod.NO_BILLING_PERIOD)) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has a recurring price but no billing period", type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }

        //Validation: if there is no recurring price there should be no billing period
        if ((recurringPrice == null) && billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has no recurring price but does have a billing period. The billing period should be set to '%s'",
                                                         type.toString(), plan.getName(), BillingPeriod.NO_BILLING_PERIOD),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }

        //Validation: if there BP is set to NO_BILLING_PERIOD there must be a fixed price
        if ((billingPeriod == BillingPeriod.NO_BILLING_PERIOD && fixedPrice == null)) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has no billing period. It must have a fixed price set.",
                                                         type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }

        //Validation: there must be at least one of recurringPrice or fixedPrice
        if ((recurringPrice == null) && fixedPrice == null) {
            errors.add(new ValidationError(String.format("Phase %s of plan %s has neither a recurring price or a fixed price.",
                                                         type.toString(), plan.getName()),
                                           catalog.getCatalogURI(), DefaultPlanPhase.class, type.toString()));
        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog root, final URI uri) {
        if (fixedPrice != null) {
            fixedPrice.initialize(root, uri);
        }
        if (recurringPrice != null) {
            recurringPrice.initialize(root, uri);
        }
    }

    protected DefaultPlanPhase setFixedPrice(final DefaultInternationalPrice price) {
        this.fixedPrice = price;
        return this;
    }

    protected DefaultPlanPhase setRecurringPrice(final DefaultInternationalPrice price) {
        this.recurringPrice = price;
        return this;
    }

    protected DefaultPlanPhase setPhaseType(final PhaseType cohort) {
        this.type = cohort;
        return this;
    }

    protected DefaultPlanPhase setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    protected DefaultPlanPhase setDuration(final DefaultDuration duration) {
        this.duration = duration;
        return this;
    }

    protected DefaultPlanPhase setPlan(final DefaultPlan plan) {
        this.plan = plan;
        return this;
    }

    protected DefaultPlanPhase setBillCycleDuration(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

}
