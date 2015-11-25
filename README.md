SAMI CloudConnector SDK and Examples
-----------------------

Background
==============================

A device can send their data directly to SAMI via [API calls](https://developer.samsungsami.io/sami/sami-documentation/sending-and-receiving-data.html). However, some devices already send their data into their own cloud. To support this case, SAMI provides a new way for a device to send data. SAMI uses the 3rd party cloud, instead of the device, as the data source. 

A developer builds a Cloud Connector to achieve it. SAMI pulls data from the Cloud for each individual device once the owner of the device (a SAMI user) gives the permission. 

 * Learn a [high level overview]() of the Cloud Connector concept. TODO by ywu: to add the link.
 * [Cloud Connector Tutorial]() explains the workflow and code using Moves as an example. TODO by ywu: to add the link.

CloudConnector SDK, template, and examples
==============================

You write Cloud Connector Groovy code using CloudConnector SDK.

This repository contains:

 1. libs: SDK libraries
 2. apidoc: Cloud Connector SDK API documenation
 3. template: a template project. You can build and test your own Cloud Connector code based on it.
 4. sample-xxxx: Cloud Connector examples. The examples have been tested and work in production.

sample-xxxx has the same structures as template folder. You can compile and test sample Cloud Connector by following the instructions in template/README.
