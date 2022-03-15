#! /bin/bash

# login to the OpenShift cluster before launching this script

# switch to the right project
oc project prod-quarkus-bot

# delete problematic image
oc delete is ubi-quarkus-native-binary-s2i

mvn clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.native.container-build=true -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-native-image:22.0-java17 -Dnative

# add kubernetes.io/tls-acme: 'true' to the route to renew the SSL certificate automatically
