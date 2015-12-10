package utils

import com.samsung.sami.cloudconnector.testkit.http.SimpleHttpServer

class MyCloudConnectorRun {

  public static void main(String[] args) {
    // @param hostname - hostname to use (default "0.0.0.0")
    // @param port - http port to use (can be null => 9000)
    // @param httpsPort - https port to use (can be null => no ssl), if not null a self signed certificat is used
    // @param httpsKeyStorePath The path to the keystore containing the private key and certificate, if not provided generates a keystore for you (optional)
    // @param httpsKeyStoreType - The key store type, defaults to JKS (optional)
    // @param httpsKeyStorePassword - The password, defaults to a blank password (optional)
    // @param httpsKeyStoreAlgorithm - The key store algorithm, defaults to the platforms default algorithm (optional)
    def srvCfg = SimpleHttpServer.makeServerConfig('test0.alchim31.net', 9080, 9083, "etc/letsencrypt/archive/test0.alchim31.net/keystore.jks", "JKS", "changeit0", null)
    def base = ((args.length > 0)? args[0] : MyCloudConnectorRun.getPackage().getName()).replace('.', '/')
    println('base : ' + base)
    def server = SimpleHttpServer.start(
      MyCloudConnectorRun.getResource("/${base}/cfg.json").getText('UTF-8'),
      MyCloudConnectorRun.getResource("/${base}/MyCloudConnector.groovy").getText('UTF-8'),
      srvCfg
    )
    //server.stop()
  }
}
