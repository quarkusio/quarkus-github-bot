#! /bin/bash

# login to the OpenShift cluster before launching this script

mvn clean package -Dquarkus.kubernetes.deploy=true -Dnative

# add kubernetes.io/tls-acme: 'true' to the route to renew the SSL certificate automatically
