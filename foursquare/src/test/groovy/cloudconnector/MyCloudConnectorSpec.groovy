package cloudconnector

import static java.net.HttpURLConnection.*

import utils.FakeContext
import static utils.Tools.*
import spock.lang.*
import org.scalactic.*
import scala.Option
import org.joda.time.format.DateTimeFormat
import org.joda.time.*
import cloud.artik.cloudconnector.api_v1.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class MyCloudConnectorSpec extends Specification {

        def sut = new MyCloudConnector()
        def parser = new JsonSlurper()
        def ctx = new FakeContext() {
            long now() { new DateTime(1970, 1, 17, 0, 0, DateTimeZone.UTC).getMillis() }
        }
        def extId = "987654321"
        def apiEndpoint = "https://api.foursquare.com/v2"
        def device = new DeviceInfo("deviceId", Option.apply(extId), new Credentials(AuthType.OAuth2, "", "abcdefg", Empty.option(), Option.apply("bearer"), [], Empty.option()), ctx.cloudId(), Empty.option())
        def allowedKeys = [
            "createdAt",
            "timeZoneOffset",
            "venue", "location", "lat", "lng",
            "name", "address", "city", "state", "country", "postalCode", "formattedAddress"
        ]
        def extIdKeys = [ "user", "id" ]
/*
        def "reject Notification with invalid pushSecret"() {
            when:
            def invalidMsg = readFile(this, "apiNotificationBadSignature.json")
            def req = new RequestDef('https://foo/cloudconnector/' + extId + '/thirdpartynotification')
                    .withContent(invalidMsg, "application/json")
            def res = sut.onNotification(ctx, req)

            then:
            res.isBad()
        }
*/
        def "reject Notification with empty checkinUserId"() {
            when:
            def invalidMsg = readFile(this, "secondCheckinEmptyUserId.json")
            def req = new RequestDef('https://foo/cloudconnector/' + extId + '/thirdpartynotification')
                    .withContent(invalidMsg, "application/json")
            def res = sut.onNotification(ctx, req)

            then:
            res.isBad()
        }

        def "test Function filterByAllowedKeys"() {
            when:
            def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))[0]
            def checkinFiltered = sut.filterByAllowedKeys(checkin, allowedKeys)
            def expectedCheckinFiltered = parser.parseText(readFile(this, "expectedFilteredCheckin.json"))
            then:
            checkinFiltered == expectedCheckinFiltered
        }

        def "test Function generateNotificationsFromCheckins"() {
            when:
            def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))
            def notificationGenerated = sut.generateNotificationsFromCheckins(checkin)
            def expectedNotificationGenerated = [
                new ThirdPartyNotification(new ByExtId("1",),[],[
                    '''{"createdAt":1458147313,"entities":[],"id":"56e98ff1498ea418b2c6f1b5","shout":"I'm in your consumers, testing your push API!","timeZone":"UTC","timeZoneOffset":0,"type":"checkin","user":{"firstName":"Jimmy","gender":"male","id":"1","lastName":"Foursquare","photo":"https://is0.4sqi.net/userpix_thumbs/S54EHRPJAHQK0VHP.jpg","relationship":"self"},"venue":{"categories":[{"icon":"https://ss3.4sqi.net/img/categories/shops/technology.png","id":"4bf58dd8d108988d125941735","name":"Tech Startup","parents":["Professional & Other Places","Office"],"pluralName":"Tech Startups","primary":true,"shortName":"Tech Startup"}],"contact":{"facebook":"80690156072","facebookName":"Foursquare","facebookUsername":"foursquare","formattedPhone":"(646) 449-7700","phone":"6464497700","twitter":"foursquare"},"id":"4ef0e7cf7beb5932d5bdeb4e","location":{"address":"568 Broadway Fl 10","cc":"US","city":"New York","country":"United States","crossStreet":"at Prince St","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012"],"lat":40.72412842453194,"lng":-73.99726510047911,"postalCode":"10012","state":"NY"},"name":"Foursquare HQ","stats":{"checkinsCount":84644,"tipCount":299,"usersCount":11288},"storeId":"HQHQHQHQ","url":"https://foursquare.com","verified":true}}'''
                ]),
                new ThirdPartyNotification(new ByExtId("987654321",),[],[
                    '''{"createdAt":1458123760,"id":"56e933f0498eb8032f11ba1e","timeZone":"Europe/Some City","timeZoneOffset":60,"type":"checkin","user":{"firstName":"Toto","gender":"male","id":"987654321","lastName":"Tata","photo":"https://is0.4sqi.net/userpix_thumbs/987654321-TXWAYFILSDZYFD3T.jpg","relationship":"self"},"venue":{"allowMenuUrlEdit":true,"categories":[{"icon":"https://ss3.4sqi.net/img/categories/shops/financial.png","id":"4bf58dd8d108988d10a951735","name":"Bank","parents":["Shop & Service"],"pluralName":"Banks","primary":true,"shortName":"Bank"}],"contact":{},"id":"4da8402b4df0af29b708bf87","location":{"address":"some address","cc":"FR","city":"Some City","country":"France","formattedAddress":["some address","75010 Some City"],"lat":108.86950328317372,"lng":2.354668378829956,"postalCode":"75010","state":"Some State"},"name":"Soci\\u00e9t\\u00e9 G\\u00e9n\\u00e9rale","stats":{"checkinsCount":64,"tipCount":0,"usersCount":16},"verified":false}}'''
                ])

            ]
            then:
            normalize(notificationGenerated) == normalize(expectedNotificationGenerated)
        }

        // renameJsonKey(obj) -< transformJson(obj, f) remove all empty values
        def "test Function renameJsonKey"() {
            when:
            def checkin = parser.parseText(readFile(this, "sampleCheckin.json"))[0]
            def checkinRenamed = sut.renameJsonKey(checkin)
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
                    ['''{"createdAt":1458647923,"id":"56f1337338fabeb7399bcc15","timeZone":"Europe/Some City","timeZoneOffset":60,"type":"checkin","user":{"firstName":"Toto","gender":"male","id":"987654321","lastName":"Tata","photo":"https://is0.4sqi.net/userpix_thumbs/987654321-TXWAYFILSDZYFD3T.jpg","relationship":"self"},"venue":{"allowMenuUrlEdit":true,"categories":[{"icon":"https://ss3.4sqi.net/img/categories/shops/financial.png","id":"4bf58dd8d108988d10a951735","name":"Bank","parents":["Shop & Service"],"pluralName":"Banks","primary":true,"shortName":"Bank"}],"contact":{},"id":"4da8402b4df0af29b708bf87","location":{"address":"some address","cc":"FR","city":"Some City","country":"France","formattedAddress":["some address","75010 Some City"],"lat":108.86950328317372,"lng":2.354668378829956,"postalCode":"75010","state":"Ile-de-France"},"name":"Societe Generale","stats":{"checkinsCount":68,"tipCount":0,"usersCount":16},"verified":false}}'''],
                    ['''{"createdAt":1458670144,"id":"56f18a40498eabd8430e2c0c","timeZone":"Europe/Some City","timeZoneOffset":60,"type":"checkin","user":{"firstName":"Toto","gender":"male","id":"987654321","lastName":"Tata","photo":"https://is0.4sqi.net/userpix_thumbs/987654321-TXWAYFILSDZYFD3T.jpg","relationship":"self"},"venue":{"categories":[{"icon":"https://ss3.4sqi.net/img/categories/parks_outdoors/default.png","id":"50aa9e094b90af0d42d5de0d","name":"City","parents":["Outdoors & Recreation","States & Municipalities"],"pluralName":"Cities","primary":true,"shortName":"City"}],"contact":{"facebook":"207251779638","facebookName":"Some City","facebookUsername":"Some City","twitter":"Some City"},"id":"50dbd18d498eb594ef575b0a","location":{"cc":"FR","country":"France","formattedAddress":["75000"],"lat":108.8542115806468,"lng":2.352619171142578,"postalCode":"75000","state":"Ile-de-France"},"name":"Some City","stats":{"checkinsCount":172856,"tipCount":249,"usersCount":88104},"storeId":"","url":"http://www.Some City.fr","verified":true}}''']
            ]
            def expectedResponse = new NotificationResponse( expectedData.collect { data ->
                    new ThirdPartyNotification(new ByExtId(device.extId.get()), [], data)
            })

            then:
            res.isGood()
            normalize(res.get()) == normalize(expectedResponse)

        }

        def "create events from created data"() {
            when:
            def bodyEvent_ts = 1432027166000L
            def data= [
                    '''{"timestamp":1458647923,"timeZoneOffset":60,"venue":{"location":{"address":"some address","city":"Some City","country":"France","formattedAddress":["some address","75010 Some City"],"lat":108.86950328317372,"long":2.354668378829956,"postalCode":"75010","state":"Ile-de-France"},"name":"Societe Generale"}}''',
                    '''{"timestamp":1458670144,"timeZoneOffset":60,"venue":{"location":{"country":"France","formattedAddress":["75000"],"lat":108.8542115806468,"long":2.352619171142578,"postalCode":"75000","state":"Ile-de-France"},"name":"Some City"}}''',
                    '''{"timestamp":1458147313,"timeZoneOffset":0,"venue":{"location":{"address":"568 Broadway Fl 10","city":"New York","country":"United States","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012"],"lat":40.72412842453194,"long":-73.99726510047911,"postalCode":"10012","state":"NY"},"name":"Foursquare HQ"}}'''
            ]
            def res = data.collectMany{ it -> sut.onNotificationData(ctx, null, it).get()}
            // timestamp *= 1000
            def expectedEvents = [
                    new Event(1458647923000,'''{"timeZoneOffset":60,"venue":{"location":{"address":"some address","city":"Some City","country":"France","formattedAddress":["some address","75010 Some City"],"lat":108.86950328317372,"long":2.354668378829956,"postalCode":"75010","state":"Ile-de-France"},"name":"Societe Generale"}}'''),
                    new Event(1458670144000,'''{"timeZoneOffset":60,"venue":{"location":{"country":"France","formattedAddress":["75000"],"lat":108.8542115806468,"long":2.352619171142578,"postalCode":"75000","state":"Ile-de-France"},"name":"Some City"}}'''),
                    new Event(1458147313000,'''{"timeZoneOffset":0,"venue":{"location":{"address":"568 Broadway Fl 10","city":"New York","country":"United States","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012"],"lat":40.72412842453194,"long":-73.99726510047911,"postalCode":"10012","state":"NY"},"name":"Foursquare HQ"}}''')
            ]

            then:
            res == expectedEvents
        }

}
