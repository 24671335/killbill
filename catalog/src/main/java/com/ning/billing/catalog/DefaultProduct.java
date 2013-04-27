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
import java.util.Arrays;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.IndexColumn;

import com.ning.billing.catalog.api.Limit;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.config.catalog.ValidatingConfig;
import com.ning.billing.util.config.catalog.ValidationErrors;

@Entity
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultProduct extends ValidatingConfig<StandaloneCatalog> implements Product {
 

    @SuppressWarnings("unused")
    @Id @GeneratedValue 
    private long id; // set id automatically

    @Column(name="product_name")
    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlAttribute(required = false)
    private Boolean retired = false;

    @XmlElement(required = true)
    private ProductCategory category;


    @CollectionOfElements
    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(name="catalog_product_includes", 
                joinColumns={@JoinColumn(name="product_id")}, 
                inverseJoinColumns={@JoinColumn(name="included_id")})
    @IndexColumn(name="id")
    @XmlElementWrapper(name = "included", required = false)
    @XmlIDREF
    @XmlElement(name = "addonProduct", required = true)
    private DefaultProduct[] included = new DefaultProduct[0];

    @CollectionOfElements
    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(name="catalog_product_available", 
    joinColumns={@JoinColumn(name="product_id")}, 
    inverseJoinColumns={@JoinColumn(name="available_id")})

    @IndexColumn(name="id")
    @XmlElementWrapper(name = "available", required = false)
    @XmlIDREF
    @XmlElement(name = "addonProduct", required = true)
    private DefaultProduct[] available = new DefaultProduct[0];
    
    @CollectionOfElements
    @OneToMany(cascade = CascadeType.ALL)
    @IndexColumn(name="id")
    @XmlElementWrapper(name = "limits", required = false)
    @XmlElement(name = "limit", required = true)
    private DefaultLimit[] limits = new DefaultLimit[0];

    //Not included in XML
    private String catalogName;

    @Override
    public String getCatalogName() {
        return catalogName;
    }

    @Override
    public boolean isRetired() {
        return retired;
    }

    @Override
    public ProductCategory getCategory() {
        return category;
    }

    @Override
    public DefaultProduct[] getIncluded() {
        return included;
    }

    @Override
    public DefaultProduct[] getAvailable() {
        return available;
    }

    public DefaultProduct() {
    }

    public DefaultProduct(final String name, final ProductCategory category) {
        this.category = category;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isIncluded(final DefaultProduct addon) {
        for (final DefaultProduct p : included) {
            if (addon == p) {
                return true;
            }
        }
        return false;
    }

    public boolean isAvailable(final DefaultProduct addon) {
        for (final DefaultProduct p : included) {
            if (addon == p) {
                return true;
            }
        }
        return false;
    }

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
            return true;
        }
        return l.compliesWith(value);
    }


    
    @Override
    public void initialize(final StandaloneCatalog catalog, final URI sourceURI) {
        catalogName = catalog.getCatalogName();
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        //TODO: MDW validation: inclusion and exclusion lists can only contain addon products
        //TODO: MDW validation: a given product can only be in, at most, one of inclusion and exclusion lists
        return errors;
    }

    protected DefaultProduct setName(final String name) {
        this.name = name;
        return this;
    }

    protected DefaultProduct setCatagory(final ProductCategory category) {
        this.category = category;
        return this;
    }

    protected DefaultProduct setCategory(final ProductCategory category) {
        this.category = category;
        return this;
    }

    protected DefaultProduct setIncluded(final DefaultProduct[] included) {
        this.included = included;
        return this;
    }

    protected DefaultProduct setAvailable(final DefaultProduct[] available) {
        this.available = available;
        return this;
    }

    protected DefaultProduct setCatalogName(final String catalogName) {
        this.catalogName = catalogName;
        return this;
    }

    public DefaultProduct setRetired(final boolean retired) {
        this.retired = retired;
        return this;
    }

    @Override
    public String toString() {
        return "DefaultProduct [name=" + name + ", retired=" + retired + ", category=" + category + ", included="
                + Arrays.toString(included) + ", available=" + Arrays.toString(available) + ", catalogName="
                + catalogName + "]";
    }
}
