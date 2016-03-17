package com.sample

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import com.samsung.sami.cloudconnector.api_v1.*
import utils.FakeContext

class MyCloudConnectorSpec extends Specification {

		def sut = new MyCloudConnector()

		def ctx = new FakeContext()

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
					new RequestDef("${ctx.parameters()['endpoint']}/messages/m1"),
					new RequestDef("${ctx.parameters()['endpoint']}/messages/m2")
				])
			])
		}
}
