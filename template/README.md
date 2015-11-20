The Template is a project where the developer can write a CloudConnector custom code, configure it and test it (unit + integration).
The developer could take a look into sibling directory sample-xxx to see how some CloudConnector for [SAMI](https://www.samsungsami.io/) had been created and tested.

# Install

* Pre-required: [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) already installed. 
* [Download[(https://github.com/samsungsamiio/sami-cloudconnector-sdk/archive/master.zip) or Fork the SDK with the template from [github](https://github.com/samsungsamiio/sami-cloudconnector-sdk)
* Every commands of the doc (below) should be launched from this project directory (the folder with the file build.gradle).
* The command '../gradlew' will download tools the first time you'll use it.

# Usages

1. Code the MyCloudConnector:
  * edit [src/main/groovy/com/sample/MyCloudConnector.groovy](src/main/groovy/com/sample/MyCloudConnector.groovy)
    * overwrite needed functions to process subscription, notifications and to fetch data from the third party cloud
  * available libraries you can use in your groovy:
    * [sami-cloudconnector-api 1.0.0](TODO)
    * [joda-time 2.3](http://www.joda.org/joda-time/apidocs/index.html) to manipulate date and time
    * [commons-codec 1.10](https://commons.apache.org/proper/commons-codec/archives/1.10/apidocs/index.html)
    * [scalactic 2.2.4](http://www.scalactic.org/) some helpers, and the classes [Or, Good, Bad](http://www.scalactic.org/user_guide/OrAndEvery)
  * compile
  ```
  ../gradlew classes
  ```
2. Test (unit) the MyCloudConnector
  * edit [src/test/groovy/com/sample/MyCloudConnectorSpec.groovy](src/test/groovy/com/sample/MyCloudConnectorSpec.groovy)
  * run Unit Test (use cleanTest to force run, else gradlew run test only if code was changed since last run)
  ```
  ../gradlew cleanTest test
  ```
3. Configure the CloudConnector (to prepare SAMI form and  to use with local http server)
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


# Coding MyCloudConnector.groovy

MyCloudConnector is a class that extends com.samsung.sami.cloudconnector.api.CloudConnector, see it's [apidoc](TODO) to have the list of every functions and structures and some explication about there goals and usage.


## Conventions

* CloudConnector should be stateless, no instance's fields, but you can use final constant.
* Every functions return a [Or<G,Failure>](http://doc.scalatest.org/2.2.4/index.html#org.scalactic.Or), in fact you return :
  * 'new Good(g)' where g is an instance of G, when everything is OK
  * 'new Bad(new Failure('this is an error message')), when something wrong append


## Tips

* use Context to retrieve some configuration data, like clientId, clientSecret but also your custom parameters:
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
  Those parameters will be sent to you groovy code in Context. You can use them like this for example:
```
@Override
Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
    ...
    ctx.parameters()["myUrl"]...
    ctx.parameters()["numberOfSomething"]...
    ...
}
```
* unit test, and so some integration test before submitting
* if you like type checking, uncomments the class annotation `//@CompileStatic` (in this case json manipulation will be more verbose)


## Unit Tests

For "Unit tests", the samples and the template use [Spock framework](http://spockframework.github.io/spock/docs/1.0/index.html), a groovy framework that report in-equality more friendly than JUnit for groovy code. But you can use your favorite framework.

The class `utils.FakeContext` provide a default Context implementation that you can use, customize,... in your test.
The class `utils.Tools` provide some helper function for your test, eg to compare list of events.

You can see tests in samples project to view more use case, like how to compare Json, Events...


## Integration Tests

For manual "Integration test", the SDK provide a simple way to run an HTTP (HTTPS) server able to run your CloudConnector locally. So you can test authentification and behavior with the third party cloud without the need to upload code to SAMI.

If you keep the package 'com.sample': run `../gradlew runTestServer` to start the server (see Usages section), else replace 'com.sample` by the right package name in the file `build.gradle`.

It is possible to customize ports, hostname, certificate by editing the file [src/test/groovy/utils/MyCloudConnectorRun.groovy](src/test/groovy/utils/MyCloudConnectorRun.groovy).


# FAQ

1. When is signAndPrepare() in CloudConnector Groovy code called? Is it called when each user clicks "Authorize" button on the device of that type in the User Portal?

  > No, "Authorize" button start the authentication/authorization process that is configurable via json/form. From apidoc, signAndPrepare(...) is "call automatically on every API request to the 3rd party cloud. You can use this method to complete request with authorisation data, ..."
