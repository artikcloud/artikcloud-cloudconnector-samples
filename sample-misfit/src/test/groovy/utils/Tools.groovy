package utils

import spock.lang.*
import groovy.json.JsonSlurper
import com.samsung.sami.cloudconnector.api.*

class Tools {
    static def parser = new JsonSlurper()

    static def readFile(Object caller, String path) {
        //def base = caller.getClass().getPackage().getName().replace('.', '/')
        //caller.getClass().getResource("/"+ base + "/" + path).getText('UTF-8')
        caller.getClass().getResource(path).getText('UTF-8')
    }

    static def cmpEvents(Collection<Event> l1, Collection<Event> l2) {
        eventsPrepareToCmp(l1) == eventsPrepareToCmp(l2)
    }

    static def eventToStrs(Event e) {
        [e.ts, parser.parseText(e.payload)]
    }

    static def eventsPrepareToCmp(Collection<Event> l) {
        l.collect{eventToStrs(it)}.sort()
    }
}
