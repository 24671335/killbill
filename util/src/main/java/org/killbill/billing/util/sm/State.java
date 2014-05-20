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

package org.killbill.billing.util.sm;

import org.killbill.billing.util.sm.Operation.OperationCallback;

public interface State extends StateMachineEntry {

    public interface EnteringStateCallback {
        public void enteringState(final State newState);
    }

    public interface LeavingStateCallback {
        public void leavingState(final State oldState);
    }

    public void runOperation(final Operation operation, final OperationCallback operationCallback,  final EnteringStateCallback enteringStateCallback, final LeavingStateCallback leavingStateCallback)
            throws MissingEntryException;

}
