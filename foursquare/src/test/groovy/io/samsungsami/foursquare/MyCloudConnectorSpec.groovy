package io.samsungsami.foursquare

import static java.net.HttpURLConnection.*

import utils.FakeContext
import static utils.Tools.*
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import com.samsung.sami.cloudconnector.api_v1.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput


class MyCloudConnectorSpec extends Specification {

		def sut = new MyCloudConnector()
	    def parser = new JsonSlurper()
	    def ctx = new FakeContext() {
	        long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
	    }
	    def extId = "166079265"
	    def apiEndpoint = "https://api.foursquare.com/v2"
	    def device = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "1j0v33o6c5b34cVPqIiB_M2LYb_iM5S9Vcy7Rx7jA2630pK7HIjEXvJoiE8V5rRF", Empty.option(), Option.apply("bearer"), [], Empty.option()), ctx.cloudId(), Empty.option())
	    def allowedKeys = [
	        "createdAt", 
	        "timeZoneOffset", 
	        "venue", "location", "lat", "lng",
	        "name", "address", "city", "state", "country", "postalCode", "formattedAddress"
	    ]
	    def extIdKeys = [ "user", "id" ]


	    def "reject Notification with invalid pushSecret"() {
			when:
			def invalidMsg = readFile(this, "apiNotificationBadSignature.json")
			def req = new RequestDef('https://foo/cloudconnector/' + extId + '/thirdpartynotification')
					.withContent(invalidMsg, "application/json")
			def res = sut.onNotification(ctx, req)

			then:
			res.isBad()
		}

		def "reject Notification with empty checkinUserId"() {
			when:
			def invalidMsg = readFile(this, "secondCheckinEmptyUserId.json")
			def req = new RequestDef('https://foo/cloudconnector/' + extId + '/thirdpartynotification')
					.withContent(invalidMsg, "application/json")
			def res = sut.onNotification(ctx, req)

			then:
			res.isBad()
		}

		def "test Function filterObjByKeepingKeys"() {
			when:
			def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))[0]
			def checkinFiltered = sut.filterObjByKeepingKeys(checkin, allowedKeys)
			def expectedCheckinFiltered = parser.parseText(readFile(this, "expectedFilteredCheckin.json"))
			then:
			checkinFiltered == expectedCheckinFiltered
		}

		def "test Function generateNotificationsFromCheckins"() {
			when:
			def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))
			def notificationGenerated = sut.generateNotificationsFromCheckins(checkin)
			def expectedNotificationGenerated = [
				/*
				new ThirdPartyNotification(new ByExternalDeviceId("1",),[],[
					JsonOutput.toJson(
						parser.parseText(readFile(this, "expectedNotificationContent.json"))[0]
					)
				]), 
				new ThirdPartyNotification(new ByExternalDeviceId("166079265",),[],[
					JsonOutput.toJson(
						parser.parseText(readFile(this, "expectedNotificationContent.json"))[1]
					)
				])
				*/
				new ThirdPartyNotification(new ByExternalDeviceId("1",),[],[
					'''{"timestamp":1458147313,"timeZoneOffset":0,"venue":{"location":{"address":"568 Broadway Fl 10","city":"New York","country":"United States","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012"],"lat":40.72412842453194,"long":-73.99726510047911,"postalCode":"10012","state":"NY"},"name":"Foursquare HQ"}}'''
				]), 
				new ThirdPartyNotification(new ByExternalDeviceId("166079265",),[],[
					'''{"timestamp":1458123760,"timeZoneOffset":60,"venue":{"location":{"address":"2 Boulevard de Strasbourg","city":"Paris","country":"France","formattedAddress":["2 Boulevard de Strasbourg","75010 Paris"],"lat":48.86950328317372,"long":2.354668378829956,"postalCode":"75010","state":"\\u00cele-de-France"},"name":"Soci\\u00e9t\\u00e9 G\\u00e9n\\u00e9rale"}}'''
				])

			]
			then:
			notificationGenerated[0] == expectedNotificationGenerated[0]
			notificationGenerated[1] == expectedNotificationGenerated[1]
			notificationGenerated == expectedNotificationGenerated
		}

		// renameJson(obj) -< transformJson(obj, f) remove all empty values
		def "test Function renameJson"() {
			when:
			def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))[0]
			def checkinRenamed = sut.renameJson(checkin)
			def expectedCheckinRenamed = parser.parseText(readFile(this, "expectedRenamedCheckin.json"))
			then:
			checkinRenamed == expectedCheckinRenamed
		}

	    def "create data from push notification (without characters special)"() {
			when:
			def msg = readFile(this, "apiMultiNotification.json")
			def req = new RequestDef('https://foo/cloudconnector/' + extId + '/thirdpartynotification')
					.withContent(msg, "application/json")
			def res = sut.onNotification(ctx, req)
			def expectedData= [
					['''{"timestamp":1458647923,"timeZoneOffset":60,"venue":{"location":{"address":"2 Boulevard de Strasbourg","city":"Paris","country":"France","formattedAddress":["2 Boulevard de Strasbourg","75010 Paris"],"lat":48.86950328317372,"long":2.354668378829956,"postalCode":"75010","state":"Ile-de-France"},"name":"Societe Generale"}}'''],
					['''{"timestamp":1458670144,"timeZoneOffset":60,"venue":{"location":{"country":"France","formattedAddress":["75000"],"lat":48.8542115806468,"long":2.352619171142578,"postalCode":"75000","state":"Ile-de-France"},"name":"Paris"}}''']
			]
			def expectedResponse = new NotificationResponse([
					new ThirdPartyNotification(new ByExternalDeviceId(device.extId.get()), [], expectedData[0]),
					new ThirdPartyNotification(new ByExternalDeviceId(device.extId.get()), [], expectedData[1])
				])
					
			then:
			res.isGood()
			res.get().thirdPartyNotifications.dataProvided.size() == 2
			res.get().thirdPartyNotifications.dataProvided[0] == expectedData[0]
			res.get().thirdPartyNotifications.dataProvided[1] == expectedData[1]
			res.get() == expectedResponse
			
		}
/*	
		def "create events from created data"() {
			when:
			def bodyEvent_ts = 1432027166000L
			def data= [
					'''{"timestamp":1458647923,"timeZoneOffset":60,"venue":{"location":{"address":"2 Boulevard de Strasbourg","city":"Paris","country":"France","formattedAddress":["2 Boulevard de Strasbourg","75010 Paris"],"lat":48.86950328317372,"long":2.354668378829956,"postalCode":"75010","state":"Ile-de-France"},"name":"Societe Generale"}}''',
					'''{"timestamp":1458670144,"timeZoneOffset":60,"venue":{"location":{"country":"France","formattedAddress":["75000"],"lat":48.8542115806468,"long":2.352619171142578,"postalCode":"75000","state":"Ile-de-France"},"name":"Paris"}}''',
					'''{"timestamp":1458147313,"timeZoneOffset":0,"venue":{"location":{"address":"568 Broadway Fl 10","city":"New York","country":"United States","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012"],"lat":40.72412842453194,"long":-73.99726510047911,"postalCode":"10012","state":"NY"},"name":"Foursquare HQ"}}'''
			]
			def res = data.collectMany{ datum -> sut.onNotificationData(ctx, null, datum).get()}
			def expectedEvents = [
					new Event(1379364951000,'''{"category":"sleep_mode","measuregrp":{"state":true}}'''),
					new Event(1379464951000,'''{"category":"sleep_mode","measuregrp":{"state":false}}'''),
					new Event(1399434951000,'''{"category":"watch_mode","measuregrp":{"state":true}}''')
			]

			then:
			res == expectedEvents
		}
*/
}
