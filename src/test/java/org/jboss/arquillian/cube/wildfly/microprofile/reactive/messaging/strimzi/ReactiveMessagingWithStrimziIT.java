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
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.ws.rs.core.MediaType;

import org.arquillian.cube.kubernetes.annotations.KubernetesResource;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.runner.RunWith;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
//@Tag(KUBERNETES)
//@WildFlyKubernetesIntegrationTest(
//        namespace = "kafka",
//        kubernetesResources = {
//                @KubernetesResource(definitionLocation = "https://strimzi.io/install/latest?namespace=kafka"),
//                @KubernetesResource(
//                        definitionLocation = "src/test/container/strimzi-cluster.yml",
//                        additionalResourcesCreated = {
//                                @Resource(type = ResourceType.DEPLOYMENT, name = "my-cluster-entity-operator")
//                        }),
//                @KubernetesResource(definitionLocation = "src/test/container/strimzi-topic.yml")
//        })
@KubernetesResource("classpath:wildfly-app-ingress.yml")
@RunWith(Arquillian.class)
public class ReactiveMessagingWithStrimziIT {

    @ArquillianResource
    private KubernetesClient kubernetesClient;

    private String serviceUrl;

    @Test
    @InSequence(10)
    public void validate_ingress() {
        final String ingressName = "wildfly-app-ingress";
        // An ingress has been added as well by Arquillian Cube, via the hello-world-ingress.yaml additional resource
        final IngressList ingresses = kubernetesClient.network().v1().ingresses().list();
        Assertions.assertThat(ingresses.getItems())
                .hasSize(1)
                .extracting(Ingress::getMetadata)
                .extracting(ObjectMeta::getName)
                .containsExactlyInAnyOrder(ingressName);
        // validating the ingress
        final Ingress ingress = ingresses.getItems().get(0);
        assertNotNull(ingress);
        assertNotNull(ingress.getSpec());
        assertNotNull(ingress.getSpec().getRules());
        assertFalse(ingress.getSpec().getRules().isEmpty());
        Assertions.assertThat(ingress.getSpec().getRules()).hasSize(1);
        assertNotNull(ingress.getStatus());
        assertNotNull(ingress.getStatus().getLoadBalancer());
        assertNotNull(ingress.getStatus().getLoadBalancer().getIngress());
        // wait until one ingress is actually ready, 60 seconds at most currently works for CI...
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> kubernetesClient.network().v1().ingresses().withName(ingressName).get().getStatus().getLoadBalancer().getIngress().size() == 1);
        final IngressLoadBalancerIngress ingressLoadBalancerIngress = kubernetesClient.network().v1().ingresses()
                .withName(ingressName).get().getStatus().getLoadBalancer().getIngress().get(0);
        assertNotNull(ingressLoadBalancerIngress);
        final String ingressIp = ingressLoadBalancerIngress.getIp();
        assertNotNull(ingressIp);
        // and finally calling the WildFly app
        serviceUrl = String.format("http://%s/hello", ingressIp);
    }

    @Test
    @InSequence(20)
    public void test() throws Exception {
        postMessage("one");

        List<String> list = getReceived();
        if (list.size() == 0) {
            // Occasionally we might start sending messages before the subscriber is connected property
            // (the connection happens async as part of the application start) so retry until we get this first message
            Thread.sleep(1000);
            long end = System.currentTimeMillis() + 20000;
            while (true) {
                list = getReceived();
                if (getReceived().size() != 0) {
                    break;
                }

                if (System.currentTimeMillis() > end) {
                    break;
                }
                postMessage("one");
                Thread.sleep(1000);
            }
        }


        postMessage("two");

        long end = System.currentTimeMillis() + 20000;
        while (list.size() != 2 && System.currentTimeMillis() < end) {
            list = getReceived();
            Thread.sleep(1000);
        }
        waitUntilListPopulated(20000, "one", "two");

    }

    private void waitUntilListPopulated(long timoutMs, String... expected) throws Exception {
        List<String> list = new ArrayList<>();
        long end = System.currentTimeMillis() + timoutMs;
        while (list.size() < expected.length && System.currentTimeMillis() < end) {
            list = getReceived();
            Thread.sleep(1000);
        }
        assertArrayEquals(expected, list.toArray(new String[list.size()]));
    }

    private List<String> getReceived() throws Exception {
        Response r = RestAssured.get(serviceUrl);
        assertEquals(200, r.getStatusCode());
        return r.as(List.class);
    }

    private void postMessage(String s) throws Exception {
        int status = RestAssured.given().header("Content-Type", MediaType.TEXT_PLAIN).post(serviceUrl).getStatusCode();
        assertEquals(200, status);
    }

}
