Sami CloudConnector SDK
-----------------------


How to create a CloudConnector
==============================

1. Create a [Device Type on SAMI](https://developer.samsungsami.io/sami/sami-documentation/developer-user-portals.html#creating-a-device-type) with a Manifest
2. Create an Application on 3rd party cloud. Use the following url
  * redirect uri : `https://api.samsungsami.io/v1.1/cloudconnectors/${devide_type_id}/auth`
  * notification uri : `https://api.samsungsami.io/v1.1/cloudconnectors/${devide_type_id}/thirdpartynotifications`
3. Read the 3rd party cloud API doc
4. Create a new project by coping and adapting the template (stay under the same parent directory to share libs and build tools)
5. Submit the groovy and the configuration as CloudConnector definition for the Device Type (via the Web UI)


How to deploy a CloudConnector
==============================

**TODO**

How to use a CloudConnector
===========================

1. Create a Device of the device Type with the CloudConnector
2. "Authorize" (blue button on the devices list) the device to connect to 3rd party cloud (via the CloudConnector)
