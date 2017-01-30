A sample CloudConnector for [ARTIK Cloud](https://www.artik.io/cloud/) to use [nest](https://www.nest.com) cloud as action target.

Follow the installation and usage instructions in "../template/README" to compile, perform unit and integration testing in the current project folder.

You may have to change some configuration parameters for your case. 

- This connector(for thermostat) needs no scopes, the permission is configured on the Nest Apps page 

- This connector designed by considering future other device type of Nest, until 19/04/2016 you need to config it as `productType: thermostat`

- This connector doesn't support webhook/notification so far.

- This connector offers you the actions(for thermostat) as following :
  - `getAllData()`
  - `refresh()`
  - `setTemperatureByDeviceName(String deviceName, Number temp)`
  - `setTemperatureByDeviceId(String deviceId, Number temp)`
  - `setTemperatureInFahrenheitByDeviceName(String deviceName, Number temp)`
  - `setTemperatureInFahrenheitByDeviceId(String deviceId, Number temp)`
  - `setAwayByStructureName(String structureName)` ("A structure represents a physical building.")
  - `setAwayByStructureId(String structureId)` 
  - `setHomeByStructureName(String structureName)`
  - `setHomeByStructureId(String structureId)`
  - `setOffByDeviceId(String deviceId)`
  - `setOffByDeviceName(String deviceName)`
  - `setHeatModeByDeviceId(String deviceId)`
  - `setHeatModeByDeviceName(String deviceName)`
  - `setCoolModeByDeviceId(String deviceId)`
  - `setCoolModeByDeviceName(String deviceName)`
  - `setHeatCoolModeByDeviceId(String deviceId)`
  - `setHeatCoolModeByDeviceName(String deviceName)`

- For actions by device name:
  - the device (resp. structure) name should be compared case sensitive and if nothing found, case insensitive
  - if case insensitive nothing found neither, apply action to all devices/buildings of user 
  - a wildcard device (resp. structure) name="*" should be equivalent to all device (resp structure) names

References:

* [nest Developer Documentation](https://developer.nest.com/documentation/cloud/rest-guide)
