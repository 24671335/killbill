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

package com.ning.killbill.osgi.libs.killbill;

import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.api.sanity.AnalyticsSanityApi;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.JunctionApi;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.usage.api.UsageUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.ExportUserApi;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.api.TagUserApi;

public class OSGIKillbillAPI extends OSGIKillbillLibraryBase implements OSGIKillbill {


    private static final String KILLBILL_SERVICE_NAME = "com.ning.billing.osgi.api.OSGIKillbill";

    private final ServiceTracker<OSGIKillbill, OSGIKillbill> killbillTracker;

    public OSGIKillbillAPI(BundleContext context) {
        killbillTracker = new ServiceTracker(context, KILLBILL_SERVICE_NAME, null);
        killbillTracker.open();
    }

    public void close() {
        if (killbillTracker != null) {
            killbillTracker.close();
        }
    }

    @Override
    public AccountUserApi getAccountUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<AccountUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AccountUserApi executeWithService(final OSGIKillbill service) {
                return service.getAccountUserApi();
            }
        });
    }

    @Override
    public AnalyticsSanityApi getAnalyticsSanityApi() {
        return withServiceTracker(killbillTracker, new APICallback<AnalyticsSanityApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AnalyticsSanityApi executeWithService(final OSGIKillbill service) {
                return service.getAnalyticsSanityApi();
            }
        });
    }

    @Override
    public AnalyticsUserApi getAnalyticsUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<AnalyticsUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AnalyticsUserApi executeWithService(final OSGIKillbill service) {
                return service.getAnalyticsUserApi();
            }
        });

    }

    @Override
    public CatalogUserApi getCatalogUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<CatalogUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public CatalogUserApi executeWithService(final OSGIKillbill service) {
                return service.getCatalogUserApi();
            }
        });
    }

    @Override
    public EntitlementMigrationApi getEntitlementMigrationApi() {
        return withServiceTracker(killbillTracker, new APICallback<EntitlementMigrationApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public EntitlementMigrationApi executeWithService(final OSGIKillbill service) {
                return service.getEntitlementMigrationApi();
            }
        });
    }

    @Override
    public EntitlementTimelineApi getEntitlementTimelineApi() {
        return withServiceTracker(killbillTracker, new APICallback<EntitlementTimelineApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public EntitlementTimelineApi executeWithService(final OSGIKillbill service) {
                return service.getEntitlementTimelineApi();
            }
        });
    }

    @Override
    public EntitlementTransferApi getEntitlementTransferApi() {
        return withServiceTracker(killbillTracker, new APICallback<EntitlementTransferApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public EntitlementTransferApi executeWithService(final OSGIKillbill service) {
                return service.getEntitlementTransferApi();
            }
        });
    }

    @Override
    public EntitlementUserApi getEntitlementUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<EntitlementUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public EntitlementUserApi executeWithService(final OSGIKillbill service) {
                return service.getEntitlementUserApi();
            }
        });
    }

    @Override
    public InvoiceMigrationApi getInvoiceMigrationApi() {
        return withServiceTracker(killbillTracker, new APICallback<InvoiceMigrationApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public InvoiceMigrationApi executeWithService(final OSGIKillbill service) {
                return service.getInvoiceMigrationApi();
            }
        });
    }

    @Override
    public InvoicePaymentApi getInvoicePaymentApi() {
        return withServiceTracker(killbillTracker, new APICallback<InvoicePaymentApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public InvoicePaymentApi executeWithService(final OSGIKillbill service) {
                return service.getInvoicePaymentApi();
            }
        });
    }

    @Override
    public InvoiceUserApi getInvoiceUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<InvoiceUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public InvoiceUserApi executeWithService(final OSGIKillbill service) {
                return service.getInvoiceUserApi();
            }
        });
    }

    @Override
    public OverdueUserApi getOverdueUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<OverdueUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public OverdueUserApi executeWithService(final OSGIKillbill service) {
                return service.getOverdueUserApi();
            }
        });
    }

    @Override
    public PaymentApi getPaymentApi() {
        return withServiceTracker(killbillTracker, new APICallback<PaymentApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public PaymentApi executeWithService(final OSGIKillbill service) {
                return service.getPaymentApi();
            }
        });
    }

    @Override
    public TenantUserApi getTenantUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<TenantUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public TenantUserApi executeWithService(final OSGIKillbill service) {
                return service.getTenantUserApi();
            }
        });
    }

    @Override
    public UsageUserApi getUsageUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<UsageUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public UsageUserApi executeWithService(final OSGIKillbill service) {
                return service.getUsageUserApi();
            }
        });
    }

    @Override
    public AuditUserApi getAuditUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<AuditUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public AuditUserApi executeWithService(final OSGIKillbill service) {
                return service.getAuditUserApi();
            }
        });
    }

    @Override
    public CustomFieldUserApi getCustomFieldUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<CustomFieldUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public CustomFieldUserApi executeWithService(final OSGIKillbill service) {
                return service.getCustomFieldUserApi();
            }
        });
    }

    @Override
    public ExportUserApi getExportUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<ExportUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public ExportUserApi executeWithService(final OSGIKillbill service) {
                return service.getExportUserApi();
            }
        });
    }

    @Override
    public TagUserApi getTagUserApi() {
        return withServiceTracker(killbillTracker, new APICallback<TagUserApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public TagUserApi executeWithService(final OSGIKillbill service) {
                return service.getTagUserApi();
            }
        });
    }

    @Override
    public JunctionApi getJunctionApi() {
        return withServiceTracker(killbillTracker, new APICallback<JunctionApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public JunctionApi executeWithService(final OSGIKillbill service) {
                return service.getJunctionApi();
            }
        });
    }

    @Override
    public RecordIdApi getRecordIdApi() {
        return withServiceTracker(killbillTracker, new APICallback<RecordIdApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public RecordIdApi executeWithService(final OSGIKillbill service) {
                return service.getRecordIdApi();
            }
        });
    }

    @Override
    public PluginConfigServiceApi getPluginConfigServiceApi() {
        return withServiceTracker(killbillTracker, new APICallback<PluginConfigServiceApi, OSGIKillbill>(KILLBILL_SERVICE_NAME) {
            @Override
            public PluginConfigServiceApi executeWithService(final OSGIKillbill service) {
                return service.getPluginConfigServiceApi();
            }
        });
    }
}
