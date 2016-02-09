package io.samsungsami.jawbone

import com.samsung.sami.cloudconnector.api_v1.*
import org.joda.time.*
import org.scalactic.*
import spock.lang.*
import utils.*

import static java.net.HttpURLConnection.*
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()

    def ctx = new FakeContext(){
	}

	def dtid = "dtid"
	def did = "did"

	def "create request to fetch notification from notification"() {
		when:
		def msg = readFile(this, "apiNotification.json")
		def req = new RequestDef('https://foo/cloudconnector/' + dtid + '/thirdpartynotification')
				.withContent(msg, "application/json")
				.addQueryParams(['samiDeviceId': did])
		def res = sut.onNotification(ctx, req)

		def expectedApi1 = new RequestDef(sut.API_ENDPOINT_URL + "moves/qzkzPoF6LjwyxHC_TpGsIJGJTS9rxqKR").addQueryParams(['type':"move"])
		def expectedApi2 = new RequestDef(sut.API_ENDPOINT_URL + "body_events/4lQSlf6UA8f_S9hzOrhyjHp-HnylKDz5").addQueryParams(['type':"body"])
		def expectedResponse = new NotificationResponse([
				new ThirdPartyNotification(new BySamiDeviceId(did), [expectedApi1, expectedApi2])
		])

		then:
		res.isGood
		res.get().thirdPartyNotifications[0].requestsOfData.size() == 2
		res.get().thirdPartyNotifications[0].requestsOfData[0] == expectedApi1
		res.get().thirdPartyNotifications[0].requestsOfData[1] == expectedApi2
		res.get() == expectedResponse
	}

	def "create events from fetch response"() {
		when:
		def msg = readFile(this, "bodyEvent1.json")
		def req = new RequestDef(null).addQueryParams(["samiDeviceId":did, 'type':"body"])
		def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
		def res = sut.onFetchResponse(ctx, req, null , fetchedResponse)

		def bodyEvent_ts=1432027166000L
		def expectedEvents = [
				new Event(bodyEvent_ts, '''{"category":"body","measuregrp":{"data":{"bmi":null,"body_fat":20,"date":20150519,"details":{"tz":null},"image":"","lean_mass":null,"note":null,"place_acc":0,"place_lat":null,"place_lon":null,"place_name":"","shared":false,"time_created":1432027154,"time_updated":1432027166,"title":null,"type":"body","waistline":null,"weight":90,"weight_delta":-2,"xid":"4lQSlf6UA8fbBjvNH8VQQic1UdEDnV39"},"meta":{"code":200,"message":"OK","time":1433772845,"user_xid":"0XKM3EPKpEJObJqAMjfiEg"}}}''')
		]

		then:
		res.isGood()
		res.get()[0] == expectedEvents[0]
		res.get().size() == expectedEvents.size()
		res.get() == expectedEvents
	}
/*
	def "create data from push notification"() {
		when:
		def msg = readFile(this, "apiPushNotification.json")
		def req = new RequestDef('https://foo/cloudconnector/' + dtid + '/thirdpartynotification')
				.withContent(msg, "application/json")
				.addQueryParams(['samiDeviceId': did])
		def res = sut.onNotification(ctx, req)

		def expectedData= [
				'''{"action":"enter_sleep_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1379364951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}''',
				'''{"action":"exit_sleep_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1379464951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}''',
				'''{"action":"enter_stopwatch_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1399434951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}'''
		]
		def expectedResponse = new NotificationResponse([
				new ThirdPartyNotification(new BySamiDeviceId(did), [], expectedData)
		])

		then:
		res.isGood
		res.get().thirdPartyNotifications[0].dataProvided.size() == 3
		res.get().thirdPartyNotifications[0].dataProvided[0] == expectedData[0]
		res.get().thirdPartyNotifications[0].dataProvided[1] == expectedData[1]
		res.get().thirdPartyNotifications[0].dataProvided[2] == expectedData[2]
		res.get() == expectedResponse
	}

	def "create events from created data"() {
		when:
		def bodyEvent_ts = 1432027166000L
		def data= [
				'''{"action":"enter_sleep_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1379364951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}''',
				'''{"action":"exit_sleep_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1379464951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}''',
				'''{"action":"enter_stopwatch_mode","secret_hash":"2e920c710603fea850e4204c14a99aea48c6a6beddaaddfa49ffe953b5e04aca","timestamp":1399434951,"user_xid":"RGaCBFg9CsB83FsEcMY44A"}'''
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

	def "reject callback with invalid signature"() {
		when:
		def invalidMsg = readFile(this, "apiNotificationBadSignature.json")
		def req = new RequestDef('https://foo/cloudconnector/' + dtid + '/thirdpartynotification')
				.withContent(invalidMsg, "application/json")
		def res = sut.onNotification(ctx, req)

		then:
		res.isBad()
	}*/
}
