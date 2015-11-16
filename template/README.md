A sample CloudConnector for [SAMI](https://www.samsungsami.io/) that can be used as a template to create your own CloudConnector. 

Install
=======

* Pre-required: [JDK]() already installed. 
* Every commands of the doc (below) should be launch from the this project directory (the folder with the file build.gradle).
* The command '../gradlew' will download tools the first time.

Usages
======

1. Code the MyCloudConnector:
  * edit [src/main/groovy/com/sample/MyCloudConnector.groovy](src/main/groovy/com/sample/MyCloudConnector.groovy)
  * available libraries you can use in your groovy:
    * [sami-cloudconnector-api 1.0.0](TODO)
    * [commons-codec 1.10](https://commons.apache.org/proper/commons-codec/archives/1.10/apidocs/index.html)
  * compile and check
  ```
  ../gradlew compile
  ```
2. Test (unit) the MyCloudConnector
  * edit [src/test/groovy/com/sample/MyCloudConnectorSpec.groovy](src/test/groovy/com/sample/MyCloudConnectorSpec.groovy)
  * force run Test
  ```
  ../gradlew cleanTest test
  ```
3. Configure the CloudConnector
  * edit [src/main/groovy/com/sample/cfg.json](src/main/groovy/com/sample/cfg.json)
  * or create a new one from the ["commented" json](src/main/groovy/com/sample/cfg.json.sample) (comments to remove)
4. Try the CloudConnector with a local http server
  * run the test server (device type is hardcoded to 0000)
  ```
  ../gradlew runTestServer
  ```
  * start subscribe of a device http://localhost:9080/cloudconnectors/0000/start_subscription
  * update the conf on 3rd party cloud to use your local server
    * redirect uri and notification uri are logged at the start of the server and should be like :
      ```
      redirect uri: http://localhost:9080/cloudconnectors/0000/auth
      notification uri: http://localhost:9080/cloudconnectors/0000/thirdpartynotifications
      ```
    * to receive notification your server should be accessible via internet (eg: redirect port on your router to your localhost, use ssh tunnel (forward port))
  * generate notification change on the 3rd party cloud
  * log of test server should have line with "0000: queuing event Event(" for every messages that should be forwarded to sami
  * you can customize port,... by editing [utils.MyCloudConnectorRun](src/test/groovy/utils/MyCloudConnectorRun.groovy)
