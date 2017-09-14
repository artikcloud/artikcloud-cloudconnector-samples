package utils

import cloud.artik.cloudconnector.testkit.http.SimpleHttpServer

class MyCloudConnectorRun {

  public static void main(String[] args) {
    // @param baseUri - the base uri of the public endpoint of proxy to localhot:9080
    // OR
    // @param hostname - hostname to use (default "0.0.0.0")
    // @param port - http port to use (can be null => 9000)
    // @param httpsPort - https port to use (can be null => no ssl), if not null and no keyStore defined then a self signed certificat is used
    // @param httpsKeyStorePath The path to the keystore containing the private key and certificate, if not provided generates a keystore for you (optional)
    // @param httpsKeyStoreType - The key store type, defaults to JKS (optional)
    // @param httpsKeyStorePassword - The password, defaults to a blank password (optional)
    // @param httpsKeyStoreAlgorithm - The key store algorithm, defaults to the platforms default algorithm (optional)
    def srvCfg = (System.getProperty("baseUri") != null)
        ?SimpleHttpServer.makeServerConfig(System.getProperty("baseUri"))
        :SimpleHttpServer.makeServerConfig('localhost', 9080, null, null, null, null, null)
    def base = ((args.length > 0)? args[0] : MyCloudConnectorRun.getPackage().getName()).replace('.', '/')
    println('base : ' + base)
    def server = SimpleHttpServer.start(
      MyCloudConnectorRun.getResource("/${base}/cfg.json").toURI(),
      MyCloudConnectorRun.getResource("/${base}/MyCloudConnector.groovy").toURI(),
      srvCfg
    )
    //server.stop()
  }
}
