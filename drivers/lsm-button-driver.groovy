/**
 * LSMTEST Report Button 21.0.0"
 * Custom PushableButton for Light Security Monitor
 * Author: WarlockWeary + ChatGPT + Grok + Claude
 *
 * Works with Hubitat EZ Dashboard:
 * - Always exposes numberOfButtons
 * - Button 1 = Force Daily Report
 */
metadata {
    definition(name: "LSM Report Button", namespace: "LSM", author: "WarlockWeary + ChatGPT + Grok + Claude") {
        capability "PushableButton"
        capability "Actuator"
        capability "Sensor"

        attribute "numberOfButtons", "number"
        attribute "button", "string"
        attribute "lastReport", "string"
    }
    preferences {
        input name: "numButtons", type: "number", title: "Number of Buttons", defaultValue: 1, range: "1..5"
    }
}

// === Lifecycle ===
def installed() {
    if (!numButtons) device.updateSetting("numButtons", [value: 1, type: "number"])
    sendEvent(name: "numberOfButtons", value: numButtons ?: 1)
    log.info "LSMTEST Report Button installed with ${numButtons ?: 1} button(s)"
}

def updated() {
    sendEvent(name: "numberOfButtons", value: numButtons ?: 1)
    log.info "LSMTEST Report Button updated with ${numButtons ?: 1} button(s)"
}

// === Commands ===
def push(buttonNum = 1) {
    def btn = buttonNum.toInteger()
    if (btn > 0 && btn <= (numButtons ?: 1)) {
        sendEvent(name: "pushed", value: btn, isStateChange: true)
        sendEvent(name: "button", value: "pushed", isStateChange: true)
        log.info "LSMTEST Report Button - Button ${btn} pushed"
        if (btn == 1) {  // Button 1 = Daily Report
            parent?.sendDailyReportForce(false)
            sendEvent(name: "lastReport", value: new Date().format("MMM d yyyy HH:mm:ss", location.timeZone))
        }
    } else {
        log.warn "Invalid button number: ${btn}"
    }
}
