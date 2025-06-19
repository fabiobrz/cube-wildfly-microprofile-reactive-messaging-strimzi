# Testing a WildFly MicroProfile Reactive Messaging - Strimzi application with Arquillian Cube

This is an equivalent of https://github.com/wildfly-extras/wildfly-cloud-tests/tree/main/tests/microprofile/reactive-messaging/strimzi - but done with Arquillian Cube

## Setup

### Minikube

- Install `minikube`, usually:
```shell
curl -LO https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64
```

(see https://minikube.sigs.k8s.io/docs/start/?arch=%2Flinux%2Fx86-64%2Fstable%2Fbinary+download)

- Start `minikube`:
```shell
minikube start
```

- Enable the `minikube` _registry_ addon:
```shell
minikube addons enable registry
```

- In a separate shell terminal, port-forward the registry service: 
```shell
kubectl port-forward --namespace kube-system service/registry 5000:80 &
```

- Enable the `minikube` _ingress_ addon:
```shell
minikube addons enable ingress
```

- Create a `kafka` namespace:
```shell
kubectl create namespace kafka
```

### Arquillian Cube

This example is currently using an Arquillian Cube feature branch, so let's, clone and build it:

```shell
git clone git@github.com:fabiobrz/arquillian-cube.git
cd arquillian-cube
git checkout feat.k8s.wait-for-deps
mvn clean install -DskipTests
```

## Run the example

```shell
git clone git@github.com:fabiobrz/cube-wildfly-microprofile-reactive-messaging-strimzi.git
cd cube-wildfly-microprofile-reactive-messaging-strimzi
mvn  clean verify -Dmaven.home=$(which mvn)
```

The `-Dmaven.home=$(which mvn)` property is needed due to some issues with the embedded (downloaded) Maven binaries 
that the Arquillian Cube JKube integration would use by default.

## Notes

- JDK 21 is required to run the example.
- https://raw.githubusercontent.com/strimzi/strimzi-kafka-operator/refs/heads/main/examples/kafka/kafka-ephemeral.yaml is used 
 during the cluster environment setup in order to create the Kafka CRs, rather than the original test
 definitions, which would make Strinzi 0.46 (latest) throw reconciliation errors.
- [An `Ingress` resource](./src/main/resources/wildfly-app-ingress.yml) is created by Arquillian Cube 
 before running the test, rather than relying on a port-forward approach, as 
 [the original test is doing](https://github.com/wildfly-extras/wildfly-cloud-tests/blob/main/tests/microprofile/reactive-messaging/strimzi/src/test/java/org/wildfly/test/cloud/microprofile/reactive/messaging/strimzi/ReactiveMessagingWithStrimziIT.java).