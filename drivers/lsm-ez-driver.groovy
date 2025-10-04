/**
 * LSMTEST EZ Tile Device v21
 * Enhanced child device for Light Security Monitor with EZ-Dashboard support
 * Author: WarlockWeary + ChatGPT + Grok + Claude
 *
 * Features:
 * - Dashboard-friendly attributes for simplified EZ-Dashboard display
 * - Multiple capability mappings for flexible dashboard usage
 * - Lock and contact activity summaries with event counts and durations
 * - Lists of currently open/unlocked devices (short name format)
 * - Read-only design (no manual override commands)
 * - Auto-disable debug logging for production
 * - Legacy attributes maintained for compatibility with older tiles
 *
 * Attributes:
 * - LSM-STATUS - : Secure/Unsecure system state
 * - UNSECURED-DEVICES - : Total count of unsecure devices
 * - SECURE-PERCENT - : Secure percentage over time
 * - LOCKS-OPEN - : Count of unlocked locks
 * - CONTACTS-OPEN - : Count of open contacts
 * - ACTIVITY - : Daily opens + total unsecured time
 * - LOCK-ACTIVITY - : Lock activity summary (count + duration)
 * - CONTACT-ACTIVITY - : Contact activity summary (count + duration)
 * - LOCKS - : Comma-separated short names of unlocked locks
 * - CONTACTS - : Comma-separated short names of open contacts
 * - LAST-UPDATE - : Timestamp of last update
 */

metadata {
    definition(name: "LSM EZ Tile Device", namespace: "LSM", author: "WarlockWeary + ChatGPT + Grok + Claude") {
        capability "Actuator"
        capability "ContactSensor"   // closed = secure, open = unsecure
        capability "Sensor"
        capability "Battery"         // secure percentage (0-100)
        capability "RelativeHumidityMeasurement" // total unsecure device count
        capability "TemperatureMeasurement"      // lock count specifically
        
        // Dashboard-optimized attributes with visual separators
        attribute "LSM-STATUS -", "string"         // SECURE / UNSECURE
        attribute "UNSECURED-DEVICES -", "number"  // Total count of unsecure devices
        attribute "SECURE-PERCENT -", "number"     // Percentage secure over time
        attribute "LOCKS-OPEN -", "number"         // Number of unlocked locks
        attribute "CONTACTS-OPEN -", "number"      // Number of open contacts
        attribute "ACTIVITY -", "string"           // Daily opens count + unsecured time
        attribute "LOCK-ACTIVITY -", "string"      // Lock activity summary
        attribute "CONTACT-ACTIVITY -", "string"   // Contact activity summary
        attribute "LOCKS -", "string"              // Names of unlocked locks (short)
        attribute "CONTACTS -", "string"           // Names of open contacts (short)
        attribute "LAST-UPDATE -", "string"        // Timestamp of last update
        
        // Legacy compatibility attributes
        attribute "numberOfUnsecureDevices", "number"
        attribute "securePercentage", "number" 
        attribute "lockCount", "number"
        attribute "contactCount", "number"
        attribute "securityStatus", "string"
        attribute "lastUpdated", "string"
        
        // Commands for app control only (no manual overrides)
        command "setSecureState", [
            [name:"isSecure", type:"ENUM", constraints:["true","false"]],
            [name:"unsecureCount", type:"NUMBER"],
            [name:"securePct", type:"NUMBER"],
            [name:"lockCount", type:"NUMBER"],
            [name:"contactCount", type:"NUMBER"],
            [name:"totalOpens", type:"NUMBER"],
            [name:"unsecuredTime", type:"STRING"],
            [name:"lockActivity", type:"STRING"],
            [name:"contactActivity", type:"STRING"],
            [name:"lockNames", type:"STRING"],
            [name:"contactNames", type:"STRING"]
        ]
        command "refresh"
    }
    
    preferences {
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        input "useEnhancedNames", "bool", title: "Use enhanced attribute names for dashboard", defaultValue: true
    }
}

// === Main Command (called by parent app) ===
def setSecureState(isSecure, unsecureCount = 0, securePct = 100,
                   lockCount = 0, contactCount = 0, totalOpens = 0,
                   unsecuredTime = "0h 0m 0s", lockActivity = "",
                   contactActivity = "", lockNames = "", contactNames = "") {
    def timestamp = new Date().format("MMM d h:mm a", location.timeZone)
    def status = isSecure ? "SECURE" : "UNSECURE"
    
    // Primary capability for EZ-Dashboard (contact sensor only)
    sendEvent(name: "contact", value: isSecure ? "closed" : "open")
    
    // Enhanced dashboard attributes (with visual separators)
    if (useEnhancedNames != false) {
        sendEvent(name: "LSM-STATUS -", value: status)
        sendEvent(name: "UNSECURED-DEVICES -", value: unsecureCount as Integer)
        sendEvent(name: "SECURE-PERCENT -", value: securePct as Integer)
        sendEvent(name: "LOCKS-OPEN -", value: lockCount as Integer)
        sendEvent(name: "CONTACTS-OPEN -", value: contactCount as Integer)
        sendEvent(name: "ACTIVITY -", value: "${totalOpens}x - ${unsecuredTime}")
        sendEvent(name: "LOCK-ACTIVITY -", value: lockActivity ?: "No activity")
        sendEvent(name: "CONTACT-ACTIVITY -", value: contactActivity ?: "No activity")
        sendEvent(name: "LOCKS -", value: lockNames ?: "NONE-OPEN")
        sendEvent(name: "CONTACTS -", value: contactNames ?: "NONE-OPEN")
        sendEvent(name: "LAST-UPDATE -", value: timestamp)
    }
    
    // Legacy compatibility attributes
    sendEvent(name: "securityStatus", value: status)
    sendEvent(name: "numberOfUnsecureDevices", value: unsecureCount as Integer)
    sendEvent(name: "securePercentage", value: securePct as Integer)
    sendEvent(name: "lockCount", value: lockCount as Integer)
    sendEvent(name: "contactCount", value: contactCount as Integer)
    sendEvent(name: "lastUpdated", value: timestamp)
    
    // Standard capability mappings for flexible EZ-Dashboard options
    sendEvent(name: "battery", value: securePct as Integer)           // Secure % as battery level
    sendEvent(name: "humidity", value: unsecureCount as Integer)      // Total unsecure devices as humidity
    sendEvent(name: "temperature", value: lockCount as Integer)       // Lock count as temperature
    
    if (logEnable) {
        log.info "LSMTEST EZ Tile updated: Status=${status}, Unsecure=${unsecureCount}, Secure%=${securePct}, Locks=${lockCount}, Contacts=${contactCount}, Opens=${totalOpens}x, Time=${unsecuredTime}, LockActivity=${lockActivity}, ContactActivity=${contactActivity}, LockNames=${lockNames}, ContactNames=${contactNames}"
    }
}

// === Refresh command (triggers parent app update only) ===
def refresh() {
    if (logEnable) log.debug "LSMTEST EZ Tile: Refresh requested - triggering parent app status check"
    parent?.checkStatus() // Trigger parent app to update status
}

// === Device Lifecycle ===
def installed() {
    log.info "LSMTEST EZ Tile Device v20.0.3 (Read-Only) installed successfully"
    // Set default values
    sendEvent(name: "contact", value: "closed")
    if (useEnhancedNames != false) {
        sendEvent(name: "LSM-STATUS -", value: "SECURE")
        sendEvent(name: "UNSECURED-DEVICES -", value: 0)
        sendEvent(name: "SECURE-PERCENT -", value: 100)
        sendEvent(name: "LOCKS-OPEN -", value: 0)
        sendEvent(name: "CONTACTS-OPEN -", value: 0)
        sendEvent(name: "ACTIVITY -", value: "0x - 0h 0m 0s")
        sendEvent(name: "LOCK-ACTIVITY -", value: "No activity")
        sendEvent(name: "CONTACT-ACTIVITY -", value: "No activity")
        sendEvent(name: "LOCKS -", value: "NONE-OPEN")
        sendEvent(name: "CONTACTS -", value: "NONE-OPEN")
    }
    
    // Initialize legacy attributes
    sendEvent(name: "securityStatus", value: "SECURE")
    sendEvent(name: "numberOfUnsecureDevices", value: 0)
    sendEvent(name: "securePercentage", value: 100)
    sendEvent(name: "lockCount", value: 0)
    sendEvent(name: "contactCount", value: 0)
    sendEvent(name: "battery", value: 100)
    sendEvent(name: "humidity", value: 0)
    sendEvent(name: "temperature", value: 0)
}

def updated() {
    log.info "LSMTEST EZ Tile Device v20.0.3 configuration updated"
    if (logEnable) {
        log.info "Debug logging enabled - will auto-disable in 30 minutes"
        runIn(1800, logsOff)
    }
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "LSMTEST EZ Tile: Debug logging auto-disabled"
}
