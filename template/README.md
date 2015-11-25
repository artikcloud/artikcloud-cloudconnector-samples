This is a template project. Based on it, you can write a CloudConnector custom code, configure parameters, and permform both unit and integration testing. 

Consult sibling directories sample-xxx for examples of the Cloud Connector. These Cloud Connectors have been tested and work in production. For example, you can connect a device of type "moves" in the SAMI [User Portal](https://portal.samsungsami.io).

# Install

* Pre-required: [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) already installed. 
* Sync the repository that contains the Cloud Connector SDK and the template from [github](https://github.com/samsungsamiio/sami-cloudconnector-sdk)

Each command mentioned in this document should be launched from the current project directory, which contains build.gradle file. 
{:.info}

When running '../gradlew XXXX' commands in Section "Usages", `gradlew` will download the required tools and libraries on demand.
{:.info}

# Usages

### Code & compile MyCloudConnector Groovy code:
You can compile the template project without changing any code. However, the Cloud Connector built from it is useless since it does not do any real operations.

 * Edit [src/main/groovy/com/sample/MyCloudConnector.groovy](src/main/groovy/com/sample/MyCloudConnector.groovy)
    * Overwrite necessary methods to process subscription, notifications, and data fetching from the third party cloud
 * You can use the following libraries in Groovy code :
    * [sami-cloudconnector-api 1.0.0]()(TODO by David  <--add link)
    * [joda-time 2.3](http://www.joda.org/joda-time/apidocs/index.html) for date and time manipulation
    * [commons-codec 1.10](https://commons.apache.org/proper/commons-codec/archives/1.10/apidocs/index.html)
    * [scalactic 2.2.4](http://www.scalactic.org/), which provides a few helper constructs including classes [Or, Good, Bad](http://www.scalactic.org/user_guide/OrAndEvery)
  * Compile to check compilation errors
  ```
  ../gradlew classes
  ```

### Unit test

 * Edit [src/test/groovy/com/sample/MyCloudConnectorSpec.groovy](src/test/groovy/com/sample/MyCloudConnectorSpec.groovy)
 * Run unit test. Make sure to provide 'cleanTest' in the command to force run. Otherwise gradlew skip running test if the code was not changed since last time the command runs:
  ```
  ../gradlew cleanTest test
  ```

### Integration testing in the local environment:

 * Configure the CloudConnector (to prepare SAMI form and  to use with local http server)
    * Edit [src/main/groovy/com/sample/cfg.json](src/main/groovy/com/sample/cfg.json)
    * or create a new one from the ["commented" json](src/main/groovy/com/sample/cfg.json.sample) (TODO by David: "comments to remove" is there before. Should remove this sentence or not?)
 * Test the CloudConnector on a local HTTP server
    * Note that, to receive notification, your local server should be accessible via internet (for example, use a server accessible from the outside or use ssh tunnel with port forwarding)
    * Note that you can customize the port of the local server (9080 by default for http and 9083 for https) by editing [utils.MyCloudConnectorRun](src/test/groovy/utils/MyCloudConnectorRun.groovy)
    * Temporarily update the configurations on the 3rd party cloud to use your local server for authentication and notification.
    * Run the test server (device type is hardcoded to 0000)
  ```
  ../gradlew runTestServer
  ```
    In the console, you should see the redirect and notification URI as follows:
     ```
     redirect uri: http://localhost:9080/cloudconnectors/0000/auth
     notification uri: http://localhost:9080/cloudconnectors/0000/thirdpartynotifications
     ```
    * Start subscribing of a device by clicking http://localhost:9080/cloudconnectors/0000/start_subscription
    * Generate new data in the 3rd party application, which triggers the notification from the 3rd party cloud to your local test server.
    * In the console, the test server should print a line with "0000: queuing event Event(" for every message that has been forwarded to your local test server.

*After finishing your integration testing, you should change the configuration on the 3rd party cloud to use SAMI instead of your local test server for authentication and notification.*

# Notes for MyCloudConnector.groovy

MyCloudConnector is a derived class that extends `com.samsung.sami.cloudconnector.api.CloudConnector`. Check out the following documentations to learn how to code it.

 * [High level explainations of CloudConnector methods]() (link to Section "Cloud Connector Groovy code" at documenation "Cloud Connector Overview" //TODO by ywu)
 * [Moves Cloud Connector code explained](TODO by ywu link to the corresponding documentation sites)
 * [CloudConnector API Doc]()(TODO by David <-- add link), which lists functions and structures, and explains goals and usages.

### Best practices

 * CloudConnector should be stateless. So it should not have instance data, but you can use final constants.
 * All methods return [Or<T, Failure\>](http://doc.scalatest.org/2.2.4/index.html#org.scalactic.Or) instead of a value of type T. `Or` is a `Good` or `Bad` instance depending on if the execution of the method succeeds or runs into an error. Create `Good` or `Bad` instance as following: 
    * `new Good(t)`, where t is an instance of `T`.
    * `new Bad(new Failure('this is an error message, put error details here'))`.

### Tips

* Use Context to retrieve configuration data such as clientId, clientSecret or your custom parameters:
```
{
  ...
    "parameters": {
      "myUrl": "http://www.foo.com/bar",
      "numberOfSomething": "10",
    },
  ...
}
```
  Those parameters will be sent to you groovy code in Context. You can access these parameter as the following example:
```
@Override
Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
    ...
    ctx.parameters()["myUrl"]...
    ctx.parameters()["numberOfSomething"]...
    ...
}
```
* Perform unit and integration testing before submitting the Groovy Code in SAMI Developer Portal. This will increase the probability that the code is approved by SAMI and it works as you expected. 
* In the unit test, use text file from src/test/resources/<package> to store json,... and read content with utils.Tools.readFile() (TODO by David what does '...'' mean in this sentence? please revise it.)
* If you want to do type checking, uncomments the class annotation `//@CompileStatic`. Then, json manipulation will be more verbose.

### Unit Tests

Both samples and the template use [Spock framework](http://spockframework.github.io/spock/docs/1.0/index.html) for unit test. It is a groovy framework that reports in-equality more friendly than JUnit. However, you can use your favorite framework to do your own unit tests.

The class `utils.FakeContext` provides a default Context implementation, which that you can use and customize in your tests.

The class `utils.Tools` provides helper functions, for example, comparing list of events.

There are more unit test examples in samples projects, for example, how to compare Json and Events et al.

### Integration Testing

You can perform manual integration testing. The SDK provides an easy way to run an HTTP (HTTPS) local server. The server runs your Cloud Connector. So you can test authentication and fetching data with the third party cloud without uploading your code to SAMI Developer Portal.

If you keep the package 'com.sample': run `../gradlew runTestServer` to start the server (see Usages section), else replace 'com.sample` by the right package name in the file `build.gradle`.

It is possible to customize ports, hostname, certificate by editing the file [src/test/groovy/utils/MyCloudConnectorRun.groovy](src/test/groovy/utils/MyCloudConnectorRun.groovy).

You can configure logging in [src/test/resources/logback-test.xml](src/test/resources/logback-test.xml).
