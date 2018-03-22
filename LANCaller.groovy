/**
 *  LAN Caller
 *
 *  Version 0.1 March 20 2018
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "LAN Caller", namespace: "PerezFamily", author: "Jorel Perez") {
        capability "Alarm"
        capability "Speech Synthesis"        
        capability "Tone"
        /* Per http://docs.smartthings.com/en/latest/device-type-developers-guide/overview.html#actuator-and-sensor */
        capability "Sensor"
        capability "Actuator"

        // Custom Commands        
        command "chime"
        command "doorbell"
    }
    
    preferences {
        input("DeviceLocalLan", "text", title:"IP Address", description:"Please enter the server's IP address:", defaultValue:"" , required: false, displayDuringSetup: true)
        input("DevicePort", "text", title:"Port", description:"Port the server listens on:", defaultValue:"8000", required: false, displayDuringSetup: true)
        input("ServicePath", "text", title:"Path", description:"Path to the service:", defaultValue:"AnnouncerService", required: false, displayDuringSetup: true)
        input("ReplyOnEmpty", "bool", title:"Say Nothing", description:"Do you want to pass dummy text when there is nothing to say?", defaultValue: false, displayDuringSetup: true)        
    }

    simulator {
        // reply messages
        ["siren","off"].each 
            {
                reply "$it": "alarm:$it"
            }
    }

    tiles {
                
        standardTile("siren", "device.alarm", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"alarm.siren", icon:"st.secondary.siren"
        }

        standardTile("off", "device.alarm", inactiveLabel: false, decoration: "flat") {
            state "default", label:'Off', action:"alarm.off", icon:"st.quirky.spotter.quirky-spotter-sound-off"
        }       
        
        
        standardTile("speak", "device.speech", inactiveLabel: false, decoration: "flat") 
        {
            state "default", label:'Speak', action:"Speech Synthesis.speak", icon:"st.Electronics.electronics13"
        }
        
        standardTile("beep", "device.tone", inactiveLabel: false, decoration: "flat") {
            state "default", label:'Tone', action:"tone.beep", icon:"st.Entertainment.entertainment2"
        }
        
        main ("speak")
        details(["siren","off","speak","beep"])
    }
}

/** Generally matches TTSServer/app/build.gradle */
String getVersion() {
	return "24 built March 2018"
}


/** Alarm Capability, Off.
 *  Turns off the alarm.
 */
def off() {
    log.debug "Executing 'off'"
    sendEvent(name:"alarm", value:"off")
    def command="Off"
    // return 
    sendIPCommand(command, "")
}

/** Alarm Capability: Siren 
 *  Sounds the siren continuously until a stop command is sent
 */
def siren() {
    log.debug "Executing 'alarm'"
    sendEvent(name:"alarm", value:"siren")
    def command="Alarm"
    // return 
    sendIPCommand(command, "")
}

/** Alarm Capability: Strobe
 * Does nothing
 */
def strobe() {
    log.debug "Executing 'strobe'"
}

/** Alarm Capability: Both
 * Does nothing
 */
def both() {
    log.debug "Executing 'both'"
}


/** Tone Capability: Beep
 *  Sounds a short beep
 */
def beep() {
    log.debug "Executing 'beep'"
    def command="Beep"
    // return 
    sendIPCommand(command,"")
}

/** speechSynthesis Capability: Speak
 */
def speak(toSay) {
    log.debug "Executing 'speak'"

    def jsonStart = "{\"text\":\""
    def jsonEnd = "\"}"

    if (!toSay?.trim()) {
        if (ReplyOnEmpty) {
            toSay = "Announcer Version ${version}"
        }
    }

    if (toSay?.trim()) {
        def command="Speak"
        // return 
        sendIPCommand(command,jsonStart+toSay+jsonEnd)
    }
}

def chime() {
    log.debug "Executing 'chime'"    
    def command="Chime"
    // return 
    sendIPCommand(command,"")
}

def doorbell() {
    log.debug "Executing 'doorbell'"    
    def command="Doorbell"
    // return 
    sendIPCommand(command,"")
}

/** Prepares the hubAction to be executed.
 *  Pre-V25, this was executed in-line.  
 *  Now it is returned, not executed, and must be returned up the calling chain.
 */
private sendIPCommand(commandString, data) {   
    
    if (DeviceLocalLan?.trim()) {
    
        def hosthex = convertIPtoHex(DeviceLocalLan)
        def porthex = convertPortToHex(DevicePort)
        device.deviceNetworkId = "$hosthex:$porthex"

        // def headers = [:]
        // headers.put("HOST", "$DeviceLocalLan:$DevicePort")
        // headers.put("Content-Type", "application/json")

        def hubAction = new physicalgraph.device.HubAction(
            method: "POST",
            path: "/$ServicePath/$commandString",
            headers: [
                HOST: "$DeviceLocalLan:$DevicePort",
                Accept: "*/*"
            ], 
            body: data
        )

        log.debug hubAction

        try {
            sendHubCommand(hubAction)
        }
        catch (Exception e)
        {
            log.debug "Hit Exception $e"
        }
        // sendHubCommand(hubAction)
        // return hubAction
    }
}

def parse(String message) {

    def msg = stringToMap(message)

    if (!msg.containsKey("headers")) {
        log.error "No HTTP headers found in '${message}'"
        return null
    }

    // parse HTTP response headers
    def headers = new String(msg.headers.decodeBase64())
    def parsedHeaders = parseHttpHeaders(headers)
    log.debug "parsedHeaders: ${parsedHeaders}"

    if (parsedHeaders.status != 200) {
        log.error "Return Code: ${parsedHeaders.status} Server error: ${parsedHeaders.reason}"
        return null
    }

    // parse HTTP response body
    if (!msg.body) {
        log.error "No HTTP body found in '${message}'"
        return null
    } else {
	    def body = new String(msg.body.decodeBase64())
	    parseHttpResponse(body)
    	log.debug "body: ${body}"
	}    
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    return result
}

private def parseHttpResponse(String data) {
    log.debug("parseHttpResponse(${data})")
	def splitresponse = data.split("=")
    def port = splitresponse[0]
	def status = splitresponse[1]
	if (status == "active"){
		createEvent(name: "switch", value: "open", descriptionText: "$device.displayName is open", isStateChange: "true")
	} else if (status == "inactive"){
		createEvent(name: "switch", value: "close", descriptionText: "$device.displayName is closed", isStateChange: "true")
	}
    return status
}


private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex

}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04X', port.toInteger() )
    log.debug "Port entered is $port and the converted hex code is $hexport"
    return hexport
}
