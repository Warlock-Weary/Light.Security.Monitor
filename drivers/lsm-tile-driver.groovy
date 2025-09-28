/**
 * LSM Tile Device v20.0
 * Virtual child device for Light Security Monitor
 * Provides `statusTile` attribute for legacy dashboards
 */
metadata {
    definition(name: "LSM Tile Device", namespace: "LSM", author: "WarlockWeary + ChatGPT + Grok") {
        capability "Actuator"
        capability "Refresh"
        attribute "statusTile", "string"
    }
}
def refresh() {
    log.debug "LSM Tile Device refresh() called"
}
def updateTile(txt) {
    sendEvent(name: "statusTile", value: txt, isStateChange: true)
}
