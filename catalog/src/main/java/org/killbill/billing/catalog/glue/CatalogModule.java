/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.catalog.glue;

import org.killbill.billing.catalog.DefaultCatalogService;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.user.DefaultCatalogUserApi;
import org.killbill.billing.catalog.io.ICatalogLoader;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.CatalogConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.ConfigurationObjectFactory;

public class CatalogModule extends KillBillModule {

    public CatalogModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installConfig() {
        final CatalogConfig config = new ConfigurationObjectFactory(skifeConfigSource).build(CatalogConfig.class);
        bind(CatalogConfig.class).toInstance(config);
    }

    protected void installCatalog() {
        bind(CatalogService.class).to(DefaultCatalogService.class).asEagerSingleton();
        bind(ICatalogLoader.class).to(VersionedCatalogLoader.class).asEagerSingleton();
    }

    protected void installCatalogUserApi() {
        bind(CatalogUserApi.class).to(DefaultCatalogUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installCatalog();
        installCatalogUserApi();
    }
}
