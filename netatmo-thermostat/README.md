A sample CloudConnector for [ARTIK Cloud](https://www.artik.io/cloud/) to use [Netatmo Thermostat](https://www.netatmo.com) cloud as DataSource.

Follow the installation and usage instructions in "../template/README" to compile, perform unit and integration testing in the current project folder.

You may have to change some configuration parameters for your case. 

- This connector(for thermostat) needs scopes `read_thermostat`, `write_thermostat`

- This connector can switch to adapt Netatmo WeatherStation/Thermostat, but you need to config it as `netatmoProduct: thermostat` or `netatmoProduct: weatherstation`

- This connector doesn't support webhook/notification so far.

- This connector offers you the actions(for thermostat) as following :
  - `getAllData()`
  - `getData(String deviceId)`
  - `setTemperatureDuring(String deviceId, String moduleId, Number duration, Number temp)`
  - `setTemperature(String deviceId, String moduleId, Number temp)` (hard-coded as setTemperatureDuring 12h)
  - `setMode(String deviceId, String moduleId, String mode)` (mode == "program"|"away"|"hg"|"off"|"max", mode "max" is hard-coded for the next 12h)

References:

* [Netatmo Developer Documentation about Thermostat](https://dev.netatmo.com/doc/devices/thermostat)