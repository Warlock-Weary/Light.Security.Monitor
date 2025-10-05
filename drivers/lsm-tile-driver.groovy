/**
 * LSMTEST Tile Device V22.0.1
 * Virtual child device for Light Security Monitor
 * Provides `statusTile`, `image`, and `activityLog` attributes for legacy dashboards
 */
metadata {
    definition(name: "LSM Tile Device", namespace: "LSM", author: "WarlockWeary + ChatGPT + Grok + Claude") {
        capability "Actuator"
        capability "Refresh"
        attribute "statusTile", "string"
        attribute "activityLog", "string"
    }
}
def refresh() {
    log.debug "LSM Tile Device refresh() called"
}
def updateTile(txt) {
    sendEvent(name: "statusTile", value: txt, isStateChange: true)
}
