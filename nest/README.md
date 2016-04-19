A sample CloudConnector for [ARTIK Cloud](https://www.artik.io/cloud/) to use [nest](https://www.nest.com) cloud as action target.

Follow the installation and usage instructions in "../template/README" to compile, perform unit and integration testing in the current project folder.

You may have to change some configuration parameters for your case. 

- This connector(for thermostat) needs no scopes, the permission is configured on the Nest Apps page 

- This connector designed by considering future other device type of Nest, until 19/04/2016 you need to config it as `productType: thermostat`

- This connector doesn't support webhook/notification so far.

- This connector offers you the actions(for thermostat) as following :
  - `getAllData()`
  - `setTemperature(String deviceId, Number temp)`
  - `setAway(String structureId)` ("A structure represents a physical building.")
  - `setHome(String structureId)`

References:

* [nest Developer Documentation](https://developer.nest.com/documentation/cloud/rest-guide)
