package utils

import spock.lang.*
import groovy.json.JsonSlurper
import com.samsung.sami.cloudconnector.api_v1.*

class Tools {
    static def readFile(Object caller, String path) {
        //def base = caller.getClass().getPackage().getName().replace('.', '/')
        //caller.getClass().getResource("/"+ base + "/" + path).getText('UTF-8')
        caller.getClass().getResource(path).getText('UTF-8')
    }
}
