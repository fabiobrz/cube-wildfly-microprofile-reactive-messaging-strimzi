/*
 * JBoss, Home of Professional Open Source.
 *  Copyright 2022 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jboss.arquillian.cube.wildfly.microprofile.reactive.messaging.strimzi;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@ApplicationScoped
public class ReactiveMessagingWithStrimziBean {
    @Inject
    @Channel("to-strimzi")
    private Emitter<String> emitter;

    private List<String> received = new ArrayList<>();

    private boolean seenOne;

    @Incoming("from-strimzi")
    public void receive(String value) {
        System.out.println("Received: " + value);
        if (value.equals("one")) {
            if (seenOne) {
                // Avoid adding duplicate 'one' entries since the test might send more than one. See the comment there
                System.out.println("'one' already in list. Skipping");
                return;
            }
            seenOne = true;
        }
        received.add(value);
    }

    void send(String value) {
        System.out.println("Sending: " + value);
        emitter.send(value);
    }

    public List<String> getReceived() {
        return received;
    }
}
