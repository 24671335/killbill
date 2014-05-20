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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;

import org.killbill.billing.util.config.catalog.ValidationErrors;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultStateMachine extends StateMachineValidatingConfig<DefaultStateMachineConfig> implements StateMachine {

    @XmlAttribute(required = true)
    @XmlID
    private String name;

    @XmlElementWrapper(name = "states", required = true)
    @XmlElement(name = "state", required = true)
    private DefaultState[] states;

    @XmlElementWrapper(name = "transitions", required = true)
    @XmlElement(name = "transition", required = true)
    private DefaultTransition[] transitions;

    @XmlElementWrapper(name = "operations", required = true)
    @XmlElement(name = "operation", required = true)
    private DefaultOperation[] operations;

    private DefaultStateMachineConfig stateMachineConfig;

    @Override
    public void initialize(final DefaultStateMachineConfig root, final URI uri) {
        stateMachineConfig = root;
        for (DefaultState cur : states) {
            cur.initialize(root, uri);
            cur.setStateMachine(this);
        }
        for (DefaultTransition cur : transitions) {
            cur.initialize(root, uri);
            cur.setStateMachine(this);
        }
        for (DefaultOperation cur : operations) {
            cur.initialize(root, uri);
        }
    }

    @Override
    public ValidationErrors validate(final DefaultStateMachineConfig root, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public State[] getStates() {
        return states;
    }

    @Override
    public Transition[] getTransitions() {
        return transitions;
    }

    @Override
    public Operation[] getOperations() {
        return operations;
    }

    @Override
    public State getState(final String stateName) throws MissingEntryException {
        return (State) getEntry(states, stateName);
    }

    @Override
    public Transition getTransition(final String transitionName) throws MissingEntryException {
        return (Transition) getEntry(transitions, transitionName);
    }

    @Override
    public Operation getOperation(final String operationName) throws MissingEntryException {
        return (Operation) getEntry(operations, operationName);
    }

    public void setStates(final DefaultState[] states) {
        this.states = states;
    }

    public void setTransitions(final DefaultTransition[] transitions) {
        this.transitions = transitions;
    }

    public void setOperations(final DefaultOperation[] operations) {
        this.operations = operations;
    }

    public DefaultStateMachineConfig getStateMachineConfig() {
        return stateMachineConfig;
    }

    public void setStateMachineConfig(final DefaultStateMachineConfig stateMachineConfig) {
        this.stateMachineConfig = stateMachineConfig;
    }

    public DefaultTransition findTransition(final State initialState, final Operation operation, final OperationResult operationResult)
            throws MissingEntryException {
        try {
            return Iterables.tryFind(ImmutableList.<DefaultTransition>copyOf(transitions), new Predicate<DefaultTransition>() {
                @Override
                public boolean apply(final DefaultTransition input) {
                    return input.getInitialState().getName().equals(initialState.getName()) &&
                           input.getOperation().getName().equals(operation.getName()) &&
                           input.getOperationResult().equals(operationResult);
                }
            }).get();
        } catch (IllegalStateException e) {
            throw new MissingEntryException("Missing transition for initialState " + initialState.getName() +
                                            ", operation = " + operation.getName() + ", result = " + operationResult, e);
        }
    }
}

