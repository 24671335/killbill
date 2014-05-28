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

package org.killbill.billing.payment.glue;

import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.DefaultPaymentProviderPluginRegistry;
import org.killbill.billing.payment.provider.DefaultRetryProviderPlugin;
import org.killbill.billing.payment.provider.DefaultRetryProviderPluginRegistry;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.util.config.PaymentConfig;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DefaultRetryProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<RetryPluginApi>> {


    private final PaymentConfig paymentConfig;
    private final DefaultRetryProviderPlugin externalRetryProviderPlugin;

    @Inject
    public DefaultRetryProviderPluginRegistryProvider(final PaymentConfig paymentConfig, final DefaultRetryProviderPlugin externalRetryProviderPlugin) {
        this.paymentConfig = paymentConfig;
        this.externalRetryProviderPlugin = externalRetryProviderPlugin;
    }

    @Override
    public OSGIServiceRegistration<RetryPluginApi> get() {
        final DefaultRetryProviderPluginRegistry pluginRegistry = new DefaultRetryProviderPluginRegistry(paymentConfig);

        // Make the external payment provider plugin available by default
        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }
            @Override
            public String getRegistrationName() {
                return ExternalPaymentProviderPlugin.PLUGIN_NAME;
            }
        };
        pluginRegistry.registerService(desc, externalRetryProviderPlugin);

        return pluginRegistry;
    }

}
