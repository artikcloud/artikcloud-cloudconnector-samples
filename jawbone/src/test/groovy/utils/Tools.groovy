package utils

import spock.lang.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.core.IsCollectionContaining.*
import static org.hamcrest.core.IsEqual.*
import java.util.LinkedHashMap
import org.scalactic.*

class Tools {
    static def parser = new JsonSlurper()

    /**
     * Read content in test/resources/responses/<verb>.<path with / replaced by _>.<num>.json
     * @param req the inpu request (from where verb and path is extracted)
     * @param num number of the reponse for the request (defaul "01")
     * @return the reponse read from resources
     */
    static def readResponse(req, num = "01") {
        def fpath = "/responses/" + req.method.name().toUpperCase() + "." + new URI(req.url).getPath().replace('/', '_').substring(1) +"." + num + ".json"
        def rsrc = Tools.class.getResource(fpath)
        if (rsrc == null) throw new IllegalArgumentException("resource not found :'${fpath}'")
        def msg = rsrc.getText('UTF-8')
        def fetchedResponse = new Response(HttpURLConnection.HTTP_OK, "application/json", msg)
    }

    static def readFile(Object caller, String path) {
        //def base = caller.getClass().getPackage().getName().replace('.', '/')
        //caller.getClass().getResource("/"+ base + "/" + path).getText('UTF-8')
        caller.getClass().getResource(path).getText('UTF-8')
    }

    static def cmpTasks(Collection<Task> l1, Collection<Task> l2) {
        //tasksPrepareToCmp(l1) == tasksPrepareToCmp(l2)
        assertThat("number of tasks", l1.size(), equalTo(l2.size()))
        def pl1 = tasksPrepareToCmp(l1)
        def pl2 = tasksPrepareToCmp(l2)
        for(i in 0 .. pl1.size()-1) {
             assertThat(pl1[i], equalTo(pl2[i]))
        }
        0 == 0
    }

    static def tryConvertTaskToEaseCmp (Event e) {
        if (e.kind == EventType.data) {
            try {
                return new Event(e.ts, normalize(e.payload), e.kind, e.extSubDeviceId, e.extSubDeviceTypeId)
            } catch(Exception exc) {
                // ignore => default return
            }
        }
        return e
    }

    static def tryConvertTaskToEaseCmp(RequestDef e) {
        return e.toString()
    }

    static def tasksPrepareToCmp(Collection<Event> l) {
        l.collect{tryConvertTaskToEaseCmp(it)}//.sort()
    }

    static def normalize(item) {
        if (item instanceof Map) {
            return new LinkedHashMap(item.collectEntries { key, value -> [key, normalize(value)] }).sort { it.key }
        } else if (item instanceof List) {
            return item.collect{ it -> normalize(it) }
        } else if (item instanceof String) {
            try {
                return JsonOutput.toJson(normalize(parser.parseText(item)))
            } catch(Exception exc){
                return item
            }
        } else if (item instanceof ThirdPartyNotification) {
            return new ThirdPartyNotification(item.selector, item.requestsOfData, normalize(item.dataProvided))
        } else if (item instanceof NotificationResponse) {
            return new NotificationResponse(normalize(item.thirdPartyNotifications), item.responseToCallback)
        } else if (item instanceof Good) {
            return new Good(normalize(item.get()))
        } else {
            return item
        }
    }
}
