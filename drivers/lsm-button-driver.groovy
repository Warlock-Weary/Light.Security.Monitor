/**
 * LSM Report Button v20.0
 * Custom PushableButton for Light Security Monitor
 * Author: WarlockWeary + ChatGPT + Grok + Claude
 */

metadata {
    definition(name: "LSM Report Button", namespace: "LSM", author: "WarlockWeary + ChatGPT + Grok + Claude") {
        capability "PushableButton"
        capability "Actuator"
        capability "Sensor"
        
        attribute "numberOfButtons", "number"
        attribute "button", "string"
    }
    
    preferences {
        input name: "numButtons", type: "number", title: "Number of Buttons", defaultValue: 1, range: "1..5"
    }
}

def installed() {
    updateSetting("numButtons", [value: 1])
    sendEvent(name: "numberOfButtons", value: 1)
    log.debug "LSM Report Button installed with ${numButtons} buttons"
}

def updated() {
    sendEvent(name: "numberOfButtons", value: numButtons)
}

def push(buttonNum = 1) {
    def btn = buttonNum.toInteger()
    if (btn > 0 && btn <= numButtons) {
        sendEvent(name: "button", value: "${btn} pushed", isStateChange: true)
//      log.debug "Button ${btn} pushed"
        if (btn == 1) { // Trigger daily report on button 1
            parent.sendDailyReportForce(false)
        }
    } else {
        log.warn "Invalid button number: ${btn}"
    }
}
