This is a template project. Based on it, you can write a CloudConnector custom code, configure parameters, and permform both unit and integration testing. 

Consult sibling directory sample-xxx to see how some CloudConnector for [SAMI](https://www.samsungsami.io/) had been created and tested.



# Install

* Pre-required: [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) already installed. 
* Sync the repository that contains the Cloud Connector SDK and the template from [github](https://github.com/samsungsamiio/sami-cloudconnector-sdk)
* Each command mentioned in this document should be launched from the current project directory, which contains build.gradle file.
* Run'../gradlew' to download all necessary tools.

# Usages

### Code & compile MyCloudConnector Groovy code:
You can compile the template project without changing any code. However, the Cloud Connector built from it is useless since it does not do any real operations.

 * Edit [src/main/groovy/com/sample/MyCloudConnector.groovy](src/main/groovy/com/sample/MyCloudConnector.groovy)
    * Overwrite necessary methods to process subscription, notifications, and data fetching from the third party cloud
 * You can use the following libraries in Groovy code :
    * [sami-cloudconnector-api 1.0.0](TODO)
    * [joda-time 2.3](http://www.joda.org/joda-time/apidocs/index.html) for date and time manipulation
    * [commons-codec 1.10](https://commons.apache.org/proper/commons-codec/archives/1.10/apidocs/index.html)
    * [scalactic 2.2.4](http://www.scalactic.org/), which provides a few helper constructs including classes [Or, Good, Bad](http://www.scalactic.org/user_guide/OrAndEvery)
  * Compile to check compilation errors
  ```
  ../gradlew classes
  ```

### Unit test MyCloudConnector

 * Edit [src/test/groovy/com/sample/MyCloudConnectorSpec.groovy](src/test/groovy/com/sample/MyCloudConnectorSpec.groovy)
 * Run unit test. Make sure to provide 'cleanTest' in the command to force run. Otherwise gradlew skip running test if the code was not changed since last time the command runs:
  ```
  ../gradlew cleanTest test
  ```

### Integration testing in the local environment:

 * Configure the CloudConnector (to prepare SAMI form and  to use with local http server)
    * edit [src/main/groovy/com/sample/cfg.json](src/main/groovy/com/sample/cfg.json)
    * or create a new one from the ["commented" json](src/main/groovy/com/sample/cfg.json.sample) (comments to remove)
 * Try the CloudConnector with a local http server
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

# Notes for MyCloudConnector.groovy

MyCloudConnector is a derived class that extends com.samsung.sami.cloudconnector.api.CloudConnector. Check out the following documentations to learn how to code it.

 * [CloudConnector API Doc](TODO), which lists functions and structures, and explains goals and usages.
 * [Moves Cloud Connector code explained](TODO by ywu link to the corresponding documentation sites)

## Best practices

* CloudConnector should be stateless. So it should not have instance data although you can use final constants.
* Every functions return a [Or<G,Failure>](http://doc.scalatest.org/2.2.4/index.html#org.scalactic.Or), in fact you return:(TODO I do not understand this sentence starting form "Every funcitons....", please reword)
  * 'new Good(g)' where g is an instance of G if everything is OK.
  * 'new Bad(new Failure('this is an error message')), when something goes wrong.

## Tips

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
* In the unit test, use text file from src/test/resources/<package> to store json,... and read content with utils.Tools.readFile() (TODO what does ... mean in this sentence? please revise it.)
* If you want to do type checking, uncomments the class annotation `//@CompileStatic`. Then, json manipulation will be more verbose.

## Unit Tests

Both samples and the template use [Spock framework](http://spockframework.github.io/spock/docs/1.0/index.html) for unit test. It is a groovy framework that reports in-equality more friendly than JUnit. However, you can use your favorite framework to do your own unit tests.

The class `utils.FakeContext` provides a default Context implementation, which that you can use and customize in your tests.

The class `utils.Tools` provides helper functions, for example, comparing list of events.

There are more unit test examples in samples projects, for example, how to compare Json and Events et al.

## Integration Testing

You can do manual integration testing. The SDK provides an easy way to run an HTTP (HTTPS) local server. The server runs your Cloud Connector. So you can test authentication and fetching data with the third party cloud without uploading your code to SAMI Developer Portal.

If you keep the package 'com.sample': run `../gradlew runTestServer` to start the server (see Usages section), else replace 'com.sample` by the right package name in the file `build.gradle`.

It is possible to customize ports, hostname, certificate by editing the file [src/test/groovy/utils/MyCloudConnectorRun.groovy](src/test/groovy/utils/MyCloudConnectorRun.groovy).

You can configure logging in [src/test/resources/logback-test.xml](src/test/resources/logback-test.xml).
