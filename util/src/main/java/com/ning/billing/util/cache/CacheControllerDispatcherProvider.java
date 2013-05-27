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

package com.ning.billing.util.cache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import com.ning.billing.util.cache.Cachable.CacheType;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.loader.CacheLoader;

// Build the abstraction layer between EhCache and Kill Bill
public class CacheControllerDispatcherProvider implements Provider<CacheControllerDispatcher> {

    private final CacheManager cacheManager;

    @Inject
    public CacheControllerDispatcherProvider(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public CacheControllerDispatcher get() {
        final Map<CacheType, CacheController<Object, Object>> cacheControllers = new LinkedHashMap<CacheType, CacheController<Object, Object>>();
        for (final String cacheName : cacheManager.getCacheNames()) {
            final CacheType cacheType = CacheType.findByName(cacheName);

            final Collection<EhCacheBasedCacheController<Object, Object>> cacheControllersForCacheName = getCacheControllersForCacheName(cacheName);
            // EhCache supports multiple cache loaders per type, but not Kill Bill - take the first one
            if (cacheControllersForCacheName.size() > 0) {
                final EhCacheBasedCacheController<Object, Object> ehCacheBasedCacheController = cacheControllersForCacheName.iterator().next();
                cacheControllers.put(cacheType, ehCacheBasedCacheController);
            }
        }

        return new CacheControllerDispatcher(cacheControllers);
    }

    public Collection<EhCacheBasedCacheController<Object, Object>> getCacheControllersForCacheName(final String name) {
        final Cache cache = cacheManager.getCache(name);

        // The CacheLoaders were registered in EhCacheCacheManagerProvider
        return Collections2.transform(cache.getRegisteredCacheLoaders(), new Function<CacheLoader, EhCacheBasedCacheController<Object, Object>>() {
            @Override
            public EhCacheBasedCacheController<Object, Object> apply(final CacheLoader input) {
                return new EhCacheBasedCacheController<Object, Object>(cache);
            }
        });
    }
}
