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

package com.ning.billing.osgi;

import java.util.Observable;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExternalBus;

import com.google.common.eventbus.Subscribe;

public class KillbillEventObservable extends Observable {


    private Logger logger = LoggerFactory.getLogger(KillbillEventObservable.class);

    private final ExternalBus externalBus;

    @Inject
    public KillbillEventObservable(final ExternalBus externalBus) {
        this.externalBus = externalBus;
    }

    public void register() {
        externalBus.register(this);
    }

    public void unregister() {
        deleteObservers();
        if (externalBus != null) {
            externalBus.unregister(this);
        }
    }

    @Subscribe
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {
        logger.debug("Received external event " + killbillEvent.toString());
        setChanged();
        notifyObservers(killbillEvent);
    }
}
