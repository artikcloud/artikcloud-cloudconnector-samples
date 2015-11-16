package com.sample

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import com.samsung.sami.cloudconnector.api.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()

    def ctx = new Context() {
      String clientId(){
        "clientId"
      }
      String clientSecret(){
        "clientSecret"
      }
      String cloudId(){
        "com.sample.cloud"
      }
      void debug(Object obj){
         println(obj)
      }
      long now(){
        10L
      }
      Map<String, String> parameters(){
        ["k":"v"]
      }
      List<String> scope(){
        ["all"]
      }
      RequestDefTools requestDefTools() { new RequestDefTools(){
        java.util.Iterator<String> listFilesFromMultipartFormData(RequestDef req) { [] }

        Option<String> readFileFromMultipartFormData(RequestDef req, String key) { Option.apply(null)}

        List<String> getDataFromContent(String content, String key){ []}
      }}
    }

    def "reject Notification without NotificationId"() {
  		when:
  		def req = new RequestDef("https://foo/cloudconnector/dt00/thirdpartynotification")
  		def res = sut.onNotification(ctx, req)
  		then:
  		res.isBad()
    }

    def "accept valid Notification"() {
  		when:
  		def did = 'xxxx'
  		def req = new RequestDef('https://foo/cloudconnector/dt00/thirdpartynotification')
  			.withHeaders(['notificationId': did])
  			.withContent('{"messages":["m1", "m2"]}', 'application/json')
  		def res = sut.onNotification(ctx, req)

  		then:
    		res.isGood()
  		res.get() == new NotificationResponse([
  			new ThirdPartyNotification(new BySamiDeviceId(did), [
  				new RequestDef("${MyCloudConnector.API_ENDPOINT_URL}/messages/m1"),
  				new RequestDef("${MyCloudConnector.API_ENDPOINT_URL}/messages/m2")
  			])
  		])
    }
}
