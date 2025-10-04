/**
 * Light Security Monitor V21.0.5
 * Author: Claude AI + WarlockWeary + ChatGPT + Grok
 *
 * Wizard-style setup for LSM
 * - Full core logic with state preservation and race condition fixes
 * - Wizard: 6 steps + summary (full setup page)
 * - Features: Debounce filtering, EZ Dashboard support, blink method toggle, unified UI styling
 *
 * CORE FUNCTIONALITY FIXES:
 * - FIXED: State preservation in initialize() - settings changes no longer wipe daily logs
 * - FIXED: Blinking race condition with state.blinkingStopped flag in toggleBlink()/stopBlinking()
 * - FIXED: Contact sensor variable bug in getStatusSummary() (${l} changed to ${c})
 * - FIXED: Missing blinkingStopped flag initialization in checkStatus() alert state
 * - FIXED: Notification timing - grace period and repeat intervals work correctly
 * - FIXED: Daily reset only clears logs at scheduled time or manual button press
 * - FIXED: Daily reset Of Cron
 * - FIXED: Adding runEvery1Minute("updateStatusTileLive") to initialize()
 * - FIXED: Add the timestamp directly in updateStatusTile()
 * 
 * NEW FEATURES ADDED:
 * - ADDED: Multi-page wizard setup (6 guided steps + summary) with setting pre-population on re-run
 * - ADDED: Wizard toggle with auto-clear after completion to prevent loops
 * - ADDED: Instance name protection - hidden and locked after initial setup
 * - ADDED: Blink method toggle (On/Off Toggle vs Flash Command) with device capability detection
 * - ADDED: Blinking method display in status summary showing active command type
 * - ADDED: Device type prefixes (CONTACT:, LOCK:, LIGHT:) for all monitored devices
 * - ADDED: Short name with original name reference display in status summary
 * - ADDED: Enhanced status summary with monitoring icon (👁️) and better hierarchy
 * 
 * UI/UX IMPROVEMENTS:
 * - IMPROVED: Unified visual styling with horizontal separators across all 7 pages
 * - IMPROVED: Bold section headers with consistent formatting throughout
 * - IMPROVED: Mobile-responsive two-line page titles with separator lines
 * - IMPROVED: Page title format: "-= LIGHT SECURITY MONITOR =-" / "-= CURRENT SETTINGS =-"
 * - IMPROVED: All wizard pages (1-6) now match summary page styling
 * - IMPROVED: Section separators between major setting groups on summary page
 * - IMPROVED: "CONTROLLING LIGHTS" section with individual LIGHT: prefixes
 * - IMPROVED: "MONITORING SENSORS" header replaces generic "MONITOR SENSORS"
 * 
 * CONFIGURATION TUNING:
 * - TUNED: Debounce defaults reduced to 1 second based on real Z-Wave hardware testing
 * - TUNED: Debounce range restricted to 0-10 seconds (from 0-120) for sensor chatter
 * - TUNED: eventMergeGapSeconds and contactMergeGapSeconds optimized for millisecond duplicates
 * 
 * CODE QUALITY:
 * - Verified notification system handles quick open/close without false alerts
 * - Confirmed child device names persist through settings changes
 * - Validated dashboard tiles maintain connection after wizard re-run
 * - Tested wizard reset functionality with setting preservation
 * - Confirmed debounce logic filters hardware chatter while logging legitimate events
 * 
 * TESTING NOTES:
 * - Tile updates every 1 minute via runEvery1Minute schedule
 * - Daily reset at configured time clears logs but preserves schedule
 * - Debounce settings (1 second) tuned for specific Z-Wave hardware behavior
 * - Settings changes preserve daily activity logs and child device names
*/

definition(
    name: "Light Security Monitor",
    namespace: "LSM",
    author: "WarlockWeary + ChatGPT + Grok + Claude",
    description: "Wizard setup for LSM. Monitors sensors/locks, controls bulbs, reports.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: false
)

preferences {
    if (state.initialSetupComplete) {
        page(name: "summaryPage")
    } else {
        page(name: "wizardPage1")
        page(name: "wizardPage2")
        page(name: "wizardPage3")
        page(name: "wizardPage4")
        page(name: "wizardPage5")
        page(name: "wizardPage6")
        page(name: "summaryPage")
    }
}

// === Wizard Page 1: Welcome + Instance Name ===
def wizardPage1() {
    dynamicPage(name: "wizardPage1", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 1/6: Instance Name =-</b>", nextPage: "wizardPage2", install: false, uninstall: true) {
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Introduction</b>") {
            paragraph "Welcome! This wizard guides setup in steps.\n\nConfigure sensors, lights, notifications, reports, and advanced options.\n\nSummary page follows completion."
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Step 1: Instance Name</b>") {
            input "namePrefix", "text", title: "Add Instance Name - Required (e.g. HOUSE, GARAGE) - SET ONCE THEN DO NOT CHANGE!", required: true, submitOnChange: true
        }
    }
}

// === Wizard Page 2: Devices ===
def wizardPage2() {
    dynamicPage(name: "wizardPage2", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 2/6: Devices =-</b>", nextPage: "wizardPage3", install: false, uninstall: true) {
        if (!namePrefix) href "wizardPage1", title: "Back: Instance Name"
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Select devices to monitor:</b>") {
            input "contacts", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: true, submitOnChange: true
            if (contacts) {
                contacts.each { c ->
                    input "contactShortName_${c.deviceNetworkId}", "text", title: "Short Name for ${c.displayName}", defaultValue: c.displayName, required: false, submitOnChange: true
                }
            }
            input "locks", "capability.lock", title: "Z-Wave Door Locks", multiple: true, required: true, submitOnChange: true
            if (locks) {
                locks.each { l ->
                    input "lockShortName_${l.deviceNetworkId}", "text", title: "Short Name for ${l.displayName}", defaultValue: l.displayName, required: false, submitOnChange: true
                }
            }
        }
    }
}

// === Wizard Page 3: Lights & Colors ===
def wizardPage3() {
    dynamicPage(name: "wizardPage3", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 3/6: Lights & Colors =-</b>", nextPage: "wizardPage4", install: false, uninstall: true) {
        if (!contacts || !locks) href "wizardPage2", title: "Back: Devices"
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Select Light(s) to control:</b>") {
            input "nightLights", "capability.colorControl", title: "Bulb(s) to Control", multiple: true, required: true, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Color & Brightness Settings:</b>") {
            input "secureColor", "enum", title: "Color when SECURE", options: colorOptions(), defaultValue: "Green", submitOnChange: true
            input "secureLevel", "number", title: "Brightness for SECURE (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
            input "alertColor", "enum", title: "Color when ALERT", options: colorOptions(), defaultValue: "Red", submitOnChange: true
            input "alertLevel", "number", title: "Brightness for ALERT (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Blink Settings:</b>") {
            input "blinkEnable", "bool", title: "Enable blinking for Alert?", defaultValue: false, submitOnChange: true
            input "blinkMethod", "enum", title: "Blink Method Command - On/Off OR Flash", options: ["On/Off Toggle", "Flash Command"], defaultValue: "On/Off Toggle", required: false, submitOnChange: true
            input "blinkStartTime", "time", title: "Blink Only After (start time)", required: false, submitOnChange: true
            input "blinkEndTime", "time", title: "Stop Blinking (end time)", required: false, submitOnChange: true
            input "blinkInterval", "enum", title: "Blink Interval (seconds)", options: (1..10).collect { it.toString() }, defaultValue: "2", required: false, submitOnChange: true
            input "blinkMaxMinutes", "number", title: "Auto-stop blinking after (minutes, 0 = never)", defaultValue: 60, required: false, submitOnChange: true
        }
    }
}

// === Wizard Page 4: Notifications ===
def wizardPage4() {
    dynamicPage(name: "wizardPage4", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 4/6: Notifications =-</b>", nextPage: "wizardPage5", install: false, uninstall: true) {
        if (!nightLights) href "wizardPage3", title: "Back: Lights"
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Notification settings:</b>") {
            input "gracePeriodMinutes", "number", title: "Grace Period for First Alert (minutes, 0 = immediate)", defaultValue: 15, range: "0..60", submitOnChange: true
            input "notifyDevices", "capability.notification", title: "Notification Device(s)", multiple: true, required: false, submitOnChange: true
            input "sendPush", "bool", title: "Send Notifications?", defaultValue: true, submitOnChange: true
            input "repeatMinutes", "number", title: "Repeat Notification Every (minutes)", defaultValue: 15, submitOnChange: true
            input "startTime", "time", title: "Notify Only After (start time)", required: true, submitOnChange: true
            input "endTime", "time", title: "Stop Notify After (end time)", required: true, submitOnChange: true
        }
    }
}

// === Wizard Page 5: Daily Reports ===
def wizardPage5() {
    dynamicPage(name: "wizardPage5", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 5/6: Daily Reports =-</b>", nextPage: "wizardPage6", install: false, uninstall: true) {
        if (!startTime || !endTime) href "wizardPage4", title: "Back: Notifications"
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Daily Report:</b>") {
            input "dailyReportEnable", "bool", title: "Enable daily report?", defaultValue: false, submitOnChange: true
            input "dailyReportTime", "time", title: "Daily report time", required: false, submitOnChange: true
            paragraph "Set the start time for the 24-hour daily report (default 6:00 AM). End time auto-sets 24 hours later."
            input "dailyReportStartTime", "time", title: "Daily report start time", defaultValue: "06:00 AM", submitOnChange: true
        }
    }
}

// === Wizard Page 6: Advanced + Logging ===
def wizardPage6() {
    dynamicPage(name: "wizardPage6", title: "<b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= Step 6/6: Advanced =-</b>", nextPage: "summaryPage", install: false, uninstall: true) {
        if (!dailyReportEnable) href "wizardPage5", title: "Back: Reports"
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Advanced Settings:</b>") {
            input "eventMergeGapSeconds", "number", title: "Debounce-Ignore duplicate lock/unlock events within time window. Seconds (0-10, 0=off)", defaultValue: 1, range: "0..10", required: false, submitOnChange: true
            input "contactMergeGapSeconds", "number", title: "Debounce-Ignore duplicate contact open/close events within time window. Seconds (0-10, 0=off)", defaultValue: 1, range: "0..10", required: false, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Logging:</b>") {
            input "logEnable", "bool", title: "Enable debug logging?", defaultValue: false, submitOnChange: true
            input "logAutoDisable", "bool", title: "Auto-disable logging after 30 minutes?", defaultValue: false, submitOnChange: true
        }
    }
}

// === Summary Page: Full Setup ===
def summaryPage() {
    dynamicPage(name: "summaryPage", title: "<hr style='background-color:#000000; height: 3px; border: 0;'><b>-= LIGHT SECURITY MONITOR =-</b><br><b>-= CURRENT SETTINGS =-</b><hr style='background-color:#000000; height: 3px; border: 0;'>", install: true, uninstall: true, refreshInterval: 0) {
        section("") {
            paragraph getStatusSummary()
            input name: "btnTestDaily", type: "button", title: "📜 TEST DAILY REPORT"
            input name: "btnClearLogs", type: "button", title: "🗑️ CLEAR ACTIVITY LOGS"
        }
        
        // Only show namePrefix input during initial setup, display as read-only after
        if (!state.initialSetupComplete) {
            section("<b>Select name for monitor:</b>") {
                input "namePrefix", "text", title: "Add Instance Name - Required (e.g. HOUSE, GARAGE) - THIS CAN NOT BE CHANGED !", required: true, submitOnChange: true
            }
     } else {
        section("<b>YOUR LSM MONITOR INSTANCE:  ${namePrefix?.toUpperCase()}</b>") {
        paragraph "<hr style='background-color:#000000; height: 3px; border: 0; margin-top: 10px; margin-bottom: 10px;'>"
        paragraph "<b>LIGHT SECURITY MONITOR - CHANGE SETTINGS</b>"
        paragraph "<hr style='background-color:#000000; height: 3px; border: 0; margin-top: 10px; margin-bottom: 10px;'>"
    }
}
        
        section("<b>Select devices to monitor:</b>") {
            input "contacts", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: true, submitOnChange: true
            if (contacts) {
                contacts.each { c ->
                    input "contactShortName_${c.deviceNetworkId}", "text", title: "Short Name for ${c.displayName}", defaultValue: c.displayName, required: false, submitOnChange: true
                }
            }
            input "locks", "capability.lock", title: "Z-Wave Door Locks", multiple: true, required: true, submitOnChange: true
            if (locks) {
                locks.each { l ->
                    input "lockShortName_${l.deviceNetworkId}", "text", title: "Short Name for ${l.displayName}", defaultValue: l.displayName, required: false, submitOnChange: true
                }
            }
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Select Light(s) to control:</b>") {
            input "nightLights", "capability.colorControl", title: "Bulb(s) to Control", multiple: true, required: true, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Color & Brightness Settings:</b>") {
            input "secureColor", "enum", title: "Color when SECURE", options: colorOptions(), defaultValue: "Green", submitOnChange: true
            input "secureLevel", "number", title: "Brightness for SECURE (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
            input "alertColor", "enum", title: "Color when ALERT", options: colorOptions(), defaultValue: "Red", submitOnChange: true
            input "alertLevel", "number", title: "Brightness for ALERT (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
        }
		section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Blink Settings:</b>") {
			input "blinkEnable", "bool", title: "Enable blinking for Alert?", defaultValue: false, submitOnChange: true
			input "blinkMethod", "enum", title: "Blink Method Command - On/Off OR Flash", options: ["On/Off Toggle", "Flash Command"], defaultValue: "On/Off Toggle", required: false, submitOnChange: true
			input "blinkStartTime", "time", title: "Blink Only After (start time)", required: false, submitOnChange: true
			input "blinkEndTime", "time", title: "Stop Blinking (end time)", required: false, submitOnChange: true
			input "blinkInterval", "enum", title: "Blink Interval (seconds)", options: (1..10).collect { it.toString() }, defaultValue: "2", required: false, submitOnChange: true
			input "blinkMaxMinutes", "number", title: "Auto-stop blinking after (minutes, 0 = never)", defaultValue: 60, required: false, submitOnChange: true
		}
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Notification settings:</b>") {
            input "gracePeriodMinutes", "number", title: "Grace Period for First Alert (minutes, 0 = immediate)", defaultValue: 15, range: "0..60", submitOnChange: true
            input "notifyDevices", "capability.notification", title: "Notification Device(s)", multiple: true, required: false, submitOnChange: true
            input "sendPush", "bool", title: "Send Notifications?", defaultValue: true, submitOnChange: true
            input "repeatMinutes", "number", title: "Repeat Notification Every (minutes)", defaultValue: 15, submitOnChange: true
            input "startTime", "time", title: "Notify Only After (start time)", required: true, submitOnChange: true
            input "endTime", "time", title: "Stop Notify After (end time)", required: true, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Daily Report:</b>") {
            input "dailyReportEnable", "bool", title: "Enable daily report?", defaultValue: false, submitOnChange: true
            input "dailyReportTime", "time", title: "Daily report time", required: false, submitOnChange: true
            paragraph "Set the start time for the 24-hour daily report (default 6:00 AM). End time auto-sets 24 hours later."
            input "dailyReportStartTime", "time", title: "Daily report start time", defaultValue: "06:00 AM", submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Advanced Settings:</b>") {
            input "eventMergeGapSeconds", "number", title: "Debounce-Ignore duplicate lock/unlock events within time window. Seconds (0-10, 0=off)", defaultValue: 1, range: "0..10", required: false, submitOnChange: true
            input "contactMergeGapSeconds", "number", title: "Debounce-Ignore duplicate contact open/close events within time window. Seconds (0-10, 0=off)", defaultValue: 1, range: "0..10", required: false, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Logging:</b>") {
            input "logEnable", "bool", title: "Enable debug logging?", defaultValue: false, submitOnChange: true
            input "logAutoDisable", "bool", title: "Auto-disable logging after 30 minutes?", defaultValue: false, submitOnChange: true
        }
        section("<hr style='background-color:#000000; height: 2px; border: 0; margin-top: 10px; margin-bottom: 10px;'><b>Wizard Reset:</b>") {
            input "resetWizard", "bool", title: "Re-run setup wizard on next open?", defaultValue: false
        }
    }
}

// === Installed/Updated ===
def installed() {
    state.initialSetupComplete = true
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    if (resetWizard) {
        state.initialSetupComplete = false
        app.updateSetting("resetWizard", [value: "false", type: "bool"])  // Auto-reset the toggle
    } else {
        state.initialSetupComplete = true
    }
    initialize()
    if (logAutoDisable) runIn(1800, scheduleLogDisable)
}

// === Initialize ===
def initialize() {
    getTileDevice()
    
    // EZ Dashboard Child Device
    def ezChild = getChildDevice("${app.id}-EZTile")
    if (!ezChild) {
        try {
            ezChild = addChildDevice("LSM", "LSM EZ Tile Device", "${app.id}-EZTile",
                [name: "LSM EZ TILE - ${namePrefix?.toUpperCase() ?: app.label.toUpperCase()}", isComponent: true])
            logInfo("Created EZ Dashboard child device: ${ezChild.displayName}")
        } catch (e) {
            log.error "Failed to create EZ Dashboard child device: ${e}"
        }
    }
    
    // Report Button Child Device
    def button = getChildDevice("LSM-BTN-${app.id}")
    if (!button) {
        try {
            button = addChildDevice("LSM", "LSM Report Button", "LSM-BTN-${app.id}",
                [name: "LSM REPORT BUTTON - ${namePrefix?.toUpperCase() ?: app.label.toUpperCase()}"])
            logInfo("Created report button device: ${button.displayName}")
        } catch (e) {
            log.error "Failed to create report button device: ${e}"
        }
    }
    
    subscribe(contacts, "contact", eventHandler)
    locks?.each { lock -> subscribe(lock, "lock", eventHandler) }
    subscribe(button, "pushed", reportButtonHandler)
    
    runEvery1Minute("updateStatusTileLive")  // Always establish tile updates
    runIn(2, checkStatus)
    if (dailyReportEnable && dailyReportTime) schedule(dailyReportTime, sendDailyReport)

    if (!dailyReportStartTime) app.updateSetting("dailyReportStartTime", [value: "06:00 AM", type: "time"])
    unschedule("dailyResetClear")
    schedule(dailyReportStartTime ?: "06:00 AM", dailyResetClear)

    // Only initialize state variables if they don't exist (first run)
    if (state.previousSecure == null) state.previousSecure = null
    if (state.lastNotificationTime == null) state.lastNotificationTime = null
    if (state.alarmLog == null) state.alarmLog = []
    if (state.notifyLog == null) state.notifyLog = []
    logInfo("Initialized")
}

// === Utility: Color Options ===
def colorOptions() {
    ["Red", "Green", "Blue", "Yellow", "Purple", "Pink", "White", "Orange", "Cyan", "Magenta"]
}

// === Logging Utility ===
def logInfo(msg) { if (logEnable) log.info "-= Security Monitor =- ${msg}" }

// === Child Device Helper ===
def getTileDevice() {
    def child = getChildDevice("LSM-${app.id}")
    if (!child) {
        try {
                child = addChildDevice("LSM", "LSM Tile Device", "LSM-${app.id}",
                [name: "LSM TILE - ${namePrefix?.toUpperCase() ?: app.label.toUpperCase()}", isComponent: true])
            logInfo("Created child device: ${child.displayName}")
        } catch (e) {
            log.error "Failed to create child device: ${e}"
        }
    }
    return child
}

// === Helper: Lock Short Name ===
def getLockShortName(deviceId) {
    return settings."lockShortName_${deviceId}"
}

// === Helper: Contact Short Name ===
def getContactShortName(deviceId) {
    return settings."contactShortName_${deviceId}"
}

// === Helper: Resolve displayName -> short name ===
def toShortLabel(displayName) {
    def ld = locks?.find { it.displayName == displayName }
    if (ld) return getLockShortName(ld.deviceNetworkId) ?: displayName

    def cd = contacts?.find { it.displayName == displayName }
    if (cd) return getContactShortName(cd.deviceNetworkId) ?: displayName

    return displayName
}

// === Helper: Format Seconds as hh mm ss ===
def fmtTime(totalSeconds) {
    int s = Math.max(0, (totalSeconds ?: 0) as int)
    def hours = (s / 3600) as int
    def minutes = ((s % 3600) / 60) as int
    def seconds = s % 60
    return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
}

// === UNIFIED TIME CALCULATION ===
def calculateSecureTime(windowStart, windowEnd) {
    def results = [:]
    long startMs = (windowStart as long)
    long endMs = (windowEnd as long)
    int totalSeconds = Math.max(0, ((endMs - startMs) / 1000L) as int)

    def deviceActivity = [:]
    long nowMs = now()

    (state.alarmLog ?: []).each { entry ->
        if (!entry?.start) return
        long eStart0 = entry.start as long
        Long eEnd0 = entry.end != null ? (entry.end as long) : null
        boolean overlaps = (eStart0 < endMs) && ((eEnd0 == null) || (eEnd0 > startMs))
        if (!overlaps) return

        long s = Math.max(eStart0, startMs)

        long e
        if (eEnd0 != null) {
            e = Math.min(eEnd0, endMs)
        } else {
            e = Math.min(nowMs, endMs)
        }

        if (e <= s) return

        int secs = ((e - s) / 1000L) as int
        def k = entry.device
        if (!deviceActivity[k]) deviceActivity[k] = [count: 0, seconds: 0]
        deviceActivity[k].count++
        deviceActivity[k].seconds += secs
    }

    int unsecureSeconds = deviceActivity.collect { it.value.seconds }.sum() ?: 0
    int secureSeconds = Math.max(0, totalSeconds - unsecureSeconds)
    int securePct = totalSeconds > 0 ? Math.round(secureSeconds * 100.0 / totalSeconds) as int : 100

    results.totalSeconds = totalSeconds
    results.secureSeconds = secureSeconds
    results.unsecureSeconds = unsecureSeconds
    results.securePct = securePct
    results.deviceActivity = deviceActivity
    return results
}

// === Status Summary for Setup Page ===
def getStatusSummary() {
    def sb = ""
    sb += "👁️ <b>MONITORING SENSORS:</b><br>"
    
    if (contacts) {
        contacts.each { c ->
            def shortLabel = getContactShortName(c.deviceNetworkId)
            if (shortLabel && shortLabel.trim() && shortLabel != c.displayName) {
                sb += "&nbsp;&nbsp;<b>CONTACT:</b> ${shortLabel} - ( Original: ${c.displayName} )<br>"
            } else {
                sb += "&nbsp;&nbsp;<b>CONTACT:</b> ${c.displayName}<br>"
            }
        }
    }
    
    if (locks) {
        locks.each { l ->
            def shortLabel = getLockShortName(l.deviceNetworkId)
            if (shortLabel && shortLabel.trim() && shortLabel != l.displayName) {
                sb += "&nbsp;&nbsp;<b>LOCK:</b> ${shortLabel} - ( Original: ${l.displayName} )<br>"
            } else {
                sb += "&nbsp;&nbsp;<b>LOCK:</b> ${l.displayName}<br>"
            }
        }
    }
    
    sb += "<br>"
    sb += "🌟 <b>CONTROLLING LIGHTS:</b><br>"
    if (nightLights) {
        nightLights.each { 
            sb += "&nbsp;&nbsp;<b>LIGHT:</b> ${it.displayName}<br>" 
        }
    } else {
        sb += "&nbsp;&nbsp;Not Set<br>"
    }
    
    sb += "<br>"
    sb += "🔐 <b>SECURE:</b> ${(secureColor ?: "Green").toUpperCase()} AT ${(secureLevel ?: 100)}%<br>"
    sb += "❌ <b>ALERT:</b> ${(alertColor ?: "Red").toUpperCase()} AT ${(alertLevel ?: 100)}%<br><br>"
    
    if (blinkEnable) {
        def stopText = (blinkMaxMinutes == 0) ? "NEVER" : "AUTO-STOP AFTER ${blinkMaxMinutes ?: 10}M"
        def method = blinkMethod ?: "On/Off Toggle"
        sb += "🚨 <b>BLINKING:</b> ${method.toUpperCase()} - EVERY ${blinkInterval ?: 3}S (${stopText})<br>"
        def bStart = blinkStartTime ? timeToday(blinkStartTime, location.timeZone) : null
        def bEnd = blinkEndTime ? timeToday(blinkEndTime, location.timeZone) : null
        if (bStart && bEnd) sb += "&nbsp;&nbsp;<b>TIME LIMIT:</b> ${bStart.format('h:mm a')} – ${bEnd.format('h:mm a')}<br>"
        sb += "<br>"
    } else {
        sb += "🚨 <b>BLINKING:</b> DISABLED<br><br>"
    }
    
    if (sendPush) {
        sb += "📲 <b>NOTIFICATIONS:</b> ENABLED EVERY ${(repeatMinutes ?: 15)}M"
        def grace = gracePeriodMinutes ?: 0
        if (grace > 0) sb += "<br>&nbsp;&nbsp;<b>FIRST ALERT DELAYED:</b> ${grace}M"
        def nStart = startTime ? timeToday(startTime, location.timeZone) : null
        def nEnd = endTime ? timeToday(endTime, location.timeZone) : null
        if (nStart && nEnd) sb += "<br>&nbsp;&nbsp;<b>TIME LIMIT:</b> ${nStart.format('h:mm a')} – ${nEnd.format('h:mm a')}"
        sb += "<br><br>"
    } else {
        sb += "📲 <b>NOTIFICATIONS:</b> DISABLED<br><br>"
    }
    
    def daily = dailyReportTime ? timeToday(dailyReportTime, location.timeZone) : null
    sb += "📜 <b>DAILY REPORT:</b> ${dailyReportEnable && daily ? "ENABLED AT ${daily.format('h:mm a')}" : "DISABLED"}<br><br>"
    sb += "📝 <b>LOGGING:</b> ${logEnable ? "ENABLED" : "DISABLED"} (AUTO-DISABLE: ${logAutoDisable ? "YES" : "NO"})<br><br>"
    if (state.lastCheck) sb += "📅 <b>LAST CHECK:</b> ${state.lastCheck}<br>"
    
    return sb
}

// === Time Windows ===
def inTimeWindow() {
    if (!startTime || !endTime) return true
    def now = new Date()
    def start = timeToday(startTime, location.timeZone ?: TimeZone.getDefault())
    def end = timeToday(endTime, location.timeZone ?: TimeZone.getDefault())
    (end < start) ? (now.after(start) || now.before(end)) : (now.after(start) && now.before(end))
}

def inBlinkTimeWindow() {
    if (!blinkStartTime || !blinkEndTime) return true
    def now = new Date()
    def start = timeToday(blinkStartTime, location.timeZone ?: TimeZone.getDefault())
    def end = timeToday(blinkEndTime, location.timeZone ?: TimeZone.getDefault())
    (end < start) ? (now.after(start) || now.before(end)) : (now.after(start) && now.before(end))
}

// === Daily Report ===
def sendDailyReport(clearLogs = false) {
    if (!dailyReportEnable) return

    def tz = location.timeZone ?: TimeZone.getDefault()
    def nowDate = new Date()
    def nowStr = nowDate.format("MMM d yyyy", tz)

    def start = timeToday(dailyReportStartTime ?: "06:00 AM", tz).getTime()
    if (nowDate.getTime() < start) {
        use(groovy.time.TimeCategory) { start = start - 1.day }
    }
    def end = start + (24L * 60L * 60L * 1000L)

    def timeData = calculateSecureTime(start, end)
    def deviceActivity = timeData.deviceActivity
    def totalOpens = deviceActivity.collect { (it.value.count ?: 0) }.sum() ?: 0

    def report = [
        "📜 LSM Daily Report (${nowStr})",
        "",
        "== SECURE/UNSECURE TIME ==",
        "TOTAL: ${fmtTime(timeData.secureSeconds)} (${timeData.securePct}% - 24h)",
        "DEVICES OPEN: ${fmtTime(timeData.unsecureSeconds)}",
        "",
        "== DEVICE ACTIVITY (${deviceActivity.size()}x)-(${totalOpens}x) =="
    ]

    if (deviceActivity.size() == 0) {
        report << "No activity recorded"
    } else {
        deviceActivity.each { dev, data ->
            def shortDev = toShortLabel(dev) ?: dev
            report << "${shortDev} (${data.count}x) (${fmtTime(data.seconds)})"
        }
    }

    def msg = report.join("\n")
    sendMessage(msg)
    logInfo("📜 Daily Report sent - Secure: ${fmtTime(timeData.secureSeconds)} (${timeData.securePct}%)")

    if (clearLogs) {
        state.alarmLog = []
        state.notifyLog = []
    }
}

// === Force Daily Report ===
def sendDailyReportForce(clearLogs = false) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def nowDate = new Date()
    def nowStr = nowDate.format("MMM d yyyy", tz)

    def start = timeToday(dailyReportStartTime ?: "06:00 AM", tz).getTime()
    if (nowDate.getTime() < start) {
        use(groovy.time.TimeCategory) { start = start - 1.day }
    }
    def end = start + (24L * 60L * 60L * 1000L)

    def timeData = calculateSecureTime(start, end)
    def deviceActivity = timeData.deviceActivity
    def totalOpens = deviceActivity.collect { (it.value.count ?: 0) }.sum() ?: 0

    def report = ["📜 LSM Daily Report (${nowStr})", ""]
    report << "== SECURE/UNSECURE TIME =="
    report << "TOTAL: ${fmtTime(timeData.secureSeconds)} (${timeData.securePct}% - 24h)"
    report << "DEVICES OPEN: ${fmtTime(timeData.unsecureSeconds)}"
    report << ""
    report << "== DEVICE ACTIVITY (${deviceActivity.size()}x)-(${totalOpens}x) =="

    if (deviceActivity.size() == 0) {
        report << "No activity recorded"
    } else {
        deviceActivity.each { dev, data ->
            def shortDev = toShortLabel(dev) ?: dev
            report << "${shortDev} (${data.count}x) (${fmtTime(data.seconds)})"
        }
    }

    def msg = report.join("\n")
    sendMessage(msg)
    logInfo("📜 Daily Report sent - Secure: ${fmtTime(timeData.secureSeconds)} (${timeData.securePct}%)")

    if (clearLogs) {
        state.alarmLog = []
        state.notifyLog = []
    }
}

// === Dashboard Tile Update ===
def updateStatusTile() {
    def child = getTileDevice()
    if (!child) return

    def openDevs = (contacts?.findAll { it.currentValue("contact") == "open" }*.displayName) ?: []
    def unlockedLocks = (locks?.findAll { it.currentValue("lock") == "unlocked" }*.displayName) ?: []

    def tz = (location?.timeZone) ?: TimeZone.getDefault()
    def now = new Date()
    def currentTime = now.time
    state.lastTileUpdate = now.format("MMM d yyyy hh:mm a", tz)  // Add this

    // Daily-window
    def reportStartTime = timeToday(dailyReportStartTime ?: "06:00 AM", tz)
    if (now.before(reportStartTime)) {
        use(groovy.time.TimeCategory) { reportStartTime = reportStartTime - 1.day }
    }
    def dailyWindowStart = reportStartTime.time
    def reportStartStr = reportStartTime.format('h:mm a', tz)

    // Rolling 24h
    def rollingStart = currentTime - (24L * 60L * 60L * 1000L)
    def timeDataDaily = calculateSecureTime(dailyWindowStart, currentTime)
    def timeDataRolling = calculateSecureTime(rollingStart, currentTime)

    // Active sessions
    def activeSessions = [:]
    def entries = state.alarmLog ?: []
    def activeEntries = entries.findAll { !it.end }
    activeEntries.each { entry ->
        def eStart = entry.start
        def eEnd = currentTime
        def dur = (eEnd > eStart) ? ((eEnd - eStart) / 1000).toInteger() : 0
        activeSessions[entry.device] = dur
    }

    // Lock/Contact activity
    def lockActivity = [seconds: 0, count: 0]
    def contactActivity = [seconds: 0, count: 0]
    timeDataDaily.deviceActivity.each { dev, data ->
        def isLock = locks?.find { it.displayName == dev }
        if (isLock) {
            lockActivity.seconds += data.seconds
            lockActivity.count += data.count
        } else {
            contactActivity.seconds += data.seconds
            contactActivity.count += data.count
        }
    }

    def totalOpens = (timeDataDaily.deviceActivity?.collect { (it.value?.count ?: 0) }?.sum() ?: 0) as Integer

    // Build tile
    def headerIcon = openDevs || unlockedLocks ? "❌" : "✅"
    def tile = "${headerIcon} SECURITY MONITOR STATUS<br>"
    def deviceLines = []

    if (openDevs || unlockedLocks) {
        if (unlockedLocks) {
            unlockedLocks.each { name ->
                def shortName = getLockShortName(locks.find { it.displayName == name }?.deviceNetworkId) ?: name
                def activeTime = activeSessions[name] ?: 0
                def todayCount = timeDataDaily.deviceActivity[name]?.count ?: 0
                deviceLines << "OL: ${shortName} (${todayCount}x) (${fmtTime(activeTime)})"
            }
        }
        if (openDevs) {
            openDevs.each { name ->
                def activeTime = activeSessions[name] ?: 0
                def todayCount = timeDataDaily.deviceActivity[name]?.count ?: 0
                deviceLines << "OC: ${toShortLabel(name)} (${todayCount}x) (${fmtTime(activeTime)})"
            }
        }
    } else {
        tile += "✅ ALL DEVICES SECURE - ${timeDataRolling.securePct}%<br>"
    }

    tile += "──────────────────────<br>"

    deviceLines.each { line -> tile += "${line}<br>" }
    if (deviceLines.size() > 0) tile += "<br>"

if (openDevs.isEmpty() && unlockedLocks.isEmpty()) {
    tile += "Secure Today: ${reportStartStr} · (${fmtTime(timeDataDaily.secureSeconds)})<br>"
    tile += "Device Totals: (${totalOpens}x) (${fmtTime(timeDataDaily.unsecureSeconds)})<br>"
    tile += "Lock Activity: (${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})<br>"
    tile += "Contact Activity: (${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})<br>"
    def nStart = startTime ? timeToday(startTime, location.timeZone) : null
    def nEnd = endTime ? timeToday(endTime, location.timeZone) : null
    def notifyStatus = sendPush ? "ON (${repeatMinutes ?: 15}m)" + (nStart && nEnd ? " ${nStart.format('h:mm a')} – ${nEnd.format('h:mm a')}" : "") : "OFF"
    def bStart = blinkStartTime ? timeToday(blinkStartTime, location.timeZone) : null
    def bEnd = blinkEndTime ? timeToday(blinkEndTime, location.timeZone) : null
    def blinkStatus = blinkEnable ? "ON (${blinkInterval ?: 3}s)" + (bStart && bEnd ? " ${bStart.format('h:mm a')} – ${bEnd.format('h:mm a')}" : "") : "OFF"
    tile += "Notifications: ${notifyStatus}<br>"
    tile += "Blinking: ${blinkStatus}<br>"
    if (state.lastTileUpdate) tile += "Last Update: ${state.lastTileUpdate}<br>"
} else {
        tile += "Device Totals: (${totalOpens}x) (${fmtTime(timeDataDaily.unsecureSeconds)})<br>"
        tile += "Lock Activity: (${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})<br>"
        tile += "Contact Activity: (${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})<br>"
    }

    child.sendEvent(name: "statusTile", value: tile, isStateChange: true)
    child.sendEvent(name: "image", value: tile, isStateChange: true)

    // EZ Dashboard
    def ezChild = getChildDevice("${app.id}-EZTile")
    if (ezChild) {
        def allSecure = (openDevs.isEmpty() && unlockedLocks.isEmpty())
        def securePct = timeDataRolling.securePct ?: 100
        def currentLockCount = unlockedLocks?.size() ?: 0
        def currentContactCount = openDevs?.size() ?: 0
        def totalUnsecureCount = currentLockCount + currentContactCount
        def unsecuredTimeFormatted = fmtTime(timeDataDaily.unsecureSeconds)

        def lockActivityStr = "(${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})"
        def contactActivityStr = "(${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})"

        def lockNames = unlockedLocks?.collect { name ->
            getLockShortName(locks.find { it.displayName == name }?.deviceNetworkId) ?: name
        }?.join(", ") ?: ""

        def contactNames = openDevs?.collect { name ->
            toShortLabel(name) ?: name
        }?.join(", ") ?: ""

        ezChild.setSecureState(
            allSecure,
            totalUnsecureCount,
            securePct,
            currentLockCount,
            currentContactCount,
            totalOpens,
            unsecuredTimeFormatted,
            lockActivityStr,
            contactActivityStr,
            lockNames,
            contactNames
        )
    }
}

// === Live Dashboard Update ===
def updateStatusTileLive() {
    updateStatusTile()
    
    def anyOpen = contacts?.any { it.currentValue("contact") == "open" }
    def anyUnlocked = locks?.any { it.currentValue("lock") == "unlocked" }
    
    if (anyOpen || anyUnlocked) {
        if (sendPush && inTimeWindow() && !state.notificationInitiated) {
            logInfo("🔔 Starting notification cycle for already-open devices")
            state.notificationInitiated = true
            scheduleNotification()
        }
    }
}

// === Debounce Helpers ===
private boolean reopenIfRecentLock(String deviceName, long nowMs) {
    int gapSec = (eventMergeGapSeconds ?: 10) as Integer
    if (gapSec <= 0) return false
    def lastClosed = (state.alarmLog ?: []).reverse().find { it.device == deviceName && it.end }
    if (!lastClosed) return false
    long gap = (nowMs - (lastClosed.end as Long)) / 1000
    if (gap >= 0 && gap <= gapSec) {
        lastClosed.end = null
        return true
    }
    return false
}

private boolean reopenIfRecentContact(String deviceName, long nowMs) {
    int gapSec = (contactMergeGapSeconds ?: 3) as Integer
    if (gapSec <= 0) return false
    def lastClosed = (state.alarmLog ?: []).reverse().find { it.device == deviceName && it.end }
    if (!lastClosed) return false
    long gap = (nowMs - (lastClosed.end as Long)) / 1000
    if (gap >= 0 && gap <= gapSec) {
        lastClosed.end = null
        return true
    }
    return false
}

// === Event Handler ===
def eventHandler(evt) {
    logInfo("Event: ${evt.displayName} - ${evt.name}: ${evt.value}")
    checkStatus(evt)
    updateStatusTile()
}

// === Check Status ===
def checkStatus(evt = null) {
    state.lastCheck = new Date().format("MMM d yyyy hh:mm a", location.timeZone)

    def currentTime = now()
    def closedAny = false

    state.alarmLog?.findAll { !it.end }?.each { entry ->
        def deviceName = entry.device
        def shouldClose = false

        def contactDevice = contacts?.find { it.displayName == deviceName }
        def lockDevice = locks?.find { it.displayName == deviceName }

        if (contactDevice && contactDevice.currentValue("contact") == "closed") {
            shouldClose = true
        } else if (lockDevice && lockDevice.currentValue("lock") == "locked") {
            shouldClose = true
        }

        if (shouldClose) {
            entry.end = currentTime
            def duration = ((currentTime - entry.start) / 1000).toInteger()
            logInfo("📝 Closing log entry for ${deviceName} - Duration: ${fmtTime(duration)}")
            closedAny = true
        }
    }

    def anyOpen = contacts?.any { it.currentValue("contact") == "open" }
    def anyUnlocked = locks?.any { it.currentValue("lock") == "unlocked" }
    def isSecure = !anyOpen && !anyUnlocked

    logInfo("Secure: ${isSecure}, anyOpen: ${anyOpen}, anyUnlocked: ${anyUnlocked}, Lights: ${nightLights}, Final Color: ${isSecure ? secureColor : alertColor}, Event: ${evt?.displayName ?: 'Timer'}")

    if (evt && !isSecure) {
        if (evt.name == "contact" && evt.value == "open") {
            def key = evt.displayName
            if (!state.alarmLog?.find { it.device == key && !it.end }) {
                state.alarmLog = state.alarmLog ?: []
                def timestamp = now()
                if (!reopenIfRecentContact(key, timestamp)) {
                    def readableTime = new Date(timestamp).format("MMM d yyyy hh:mm:ss a", location.timeZone)
                    state.alarmLog << [device: key, start: timestamp, end: null]
                    logInfo("📝 Logging new contact event: ${key} at ${timestamp} (${readableTime})")
                }
            }
        }

        if (evt.name == "lock" && evt.value == "unlocked") {
            def key = evt.displayName
            if (!state.alarmLog?.find { it.device == key && !it.end }) {
                state.alarmLog = state.alarmLog ?: []
                def timestamp = now()
                if (!reopenIfRecentLock(key, timestamp)) {
                    def readableTime = new Date(timestamp).format("MMM d yyyy hh:mm:ss a", location.timeZone)
                    state.alarmLog << [device: key, start: timestamp, end: null]
                    logInfo("📝 Logging new lock event: ${key} at ${timestamp} (${readableTime})")
                }
            }
        }
    }

    if (state.previousSecure != isSecure) {
        logInfo("State changed from ${state.previousSecure} to ${isSecure}")

        if (isSecure) {
            setLights(secureColor, secureLevel)
            unschedule("sendRepeatNotification")
            runEvery1Minute("updateStatusTileLive")
            state.notificationInitiated = null
            state.lastNotificationTime = null
            stopBlinking()
        } else {
            setLights(alertColor, alertLevel)
            if (sendPush && inTimeWindow()) scheduleNotification()
            if (blinkEnable && inBlinkTimeWindow()) {
                state.blinkingStopped = false  // Clear the stop flag
                toggleBlink()
            }
            runEvery1Minute("updateStatusTileLive")
        }

        state.previousSecure = isSecure
        updateStatusTile()
    } else {
        logInfo("No state change detected - staying ${isSecure ? 'secure' : 'alert'}")
        if (closedAny) updateStatusTile()
    }
}

// === Get Current Status Message ===
def getCurrentStatusMessage() {
    def openDevs = contacts?.findAll { it.currentValue("contact") == "open" }*.displayName
    def unlockedLocks = locks?.findAll { it.currentValue("lock") == "unlocked" }*.displayName
    
    def currentTime = now()
    def activeSessions = [:]
    def entries = state.alarmLog ?: []
    entries.findAll { !it.end }.each { entry ->
        def eStart = entry.start
        def eEnd = currentTime
        def dur = (eEnd > eStart) ? ((eEnd - eStart) / 1000).toInteger() : 0
        activeSessions[entry.device] = dur
    }
    
    def status = []
    if (openDevs || unlockedLocks) {
        if (unlockedLocks) {
            def lockStr = unlockedLocks.collect { name ->
                def shortName = getLockShortName(locks.find { it.displayName == name }?.deviceNetworkId) ?: name
                def activeTime = activeSessions[name] ?: 0
                "${shortName} (${fmtTime(activeTime)} active)"
            }.join(', ')
            status << "❌ Lock(s): ${lockStr}"
        }
        if (openDevs) {
            def contactStr = openDevs.collect { name ->
                def activeTime = activeSessions[name] ?: 0
                "${toShortLabel(name)} (${fmtTime(activeTime)} active)"
            }.join(', ')
            status << "⚠️ Contact: ${contactStr}"
        }
    } else {
        status << "✅ ALL DEVICES SECURE"
    }
    return status.join('\n')
}

// === Notifications ===
def scheduleNotification() {
    unschedule("sendRepeatNotification")
    def grace = (gracePeriodMinutes ?: 0) * 60
    if (grace > 0 && !state.lastNotificationTime) {
        runIn(grace, sendRepeatNotification)
    } else {
        sendRepeatNotification()
    }
}

def sendRepeatNotification() {
    logInfo("🔔 sendRepeatNotification called at ${new Date()}")
    def msg = getCurrentStatusMessage()
    logInfo("🔔 Message: ${msg}")
    logInfo("🔔 inTimeWindow: ${inTimeWindow()}")
    logInfo("🔔 notifyDevices: ${notifyDevices}")
    
    if ((msg.contains("❌") || msg.contains("⚠️")) && inTimeWindow()) {
        sendMessage("⚠ Security Alert! Unsecure device(s) detected!\n${msg}")
        state.lastNotificationTime = now()
        if (repeatMinutes) {
            logInfo("🔔 Scheduling next notification in ${repeatMinutes} minutes")
            runIn(repeatMinutes * 60, sendRepeatNotification)
        }
    } else {
        logInfo("🔔 Notification NOT sent - conditions not met")
    }
}

def sendMessage(msg) {
    logInfo("📤 Sending message: ${msg}")
    if (notifyDevices) {
        notifyDevices?.each { it.deviceNotification(msg) }
        logInfo("✅ Message sent to ${notifyDevices.size()} device(s)")
    } else {
        logInfo("⚠️ No notification devices configured - message not sent")
    }
}

// === Light Control ===
def setLights(color, level) {
    logInfo("Setting lights: color=${color}, level=${level}")
    def sat = (color == "White") ? 0 : 100
    nightLights?.each {
        it.setColor([hue: getHue(color), saturation: sat, level: level])
    }
}

def getHue(color) {
    switch(color) {
        case "Red": return 0
        case "Green": return 33
        case "Blue": return 66
        case "Yellow": return 16
        case "Purple": return 83
        case "Pink": return 90
        case "White": return 0
        case "Orange": return 8
        case "Cyan": return 50
        case "Magenta": return 75
        default: return 0
    }
}

// === Blinking ===
def toggleBlink() {
    unschedule("toggleBlink")
    if (state.blinkingStopped) return
    if (blinkEnable && inBlinkTimeWindow()) {
        if (blinkMethod == "Flash Command") {
            // Use device flash command if available
            nightLights?.each { light ->
                if (light.hasCommand("flash")) {
                    light.flash()
                } else {
                    logInfo("Warning: ${light.displayName} does not support flash command, using on/off instead")
                    def currentState = light.currentValue("switch") == "on" ? "off" : "on"
                    light."${currentState}"()
                }
            }
        } else {
            // Default on/off toggle
            nightLights?.each { light ->
                def currentState = light.currentValue("switch") == "on" ? "off" : "on"
                light."${currentState}"()
            }
        }
        runIn(blinkInterval?.toInteger() ?: 2, toggleBlink)
        if (blinkMaxMinutes > 0) runIn(blinkMaxMinutes * 60, stopBlinking)
    }
}

def stopBlinking() {
    logInfo("stopBlinking() called")
    state.blinkingStopped = true  // Add this flag
    unschedule("toggleBlink")
    def anyOpen = contacts?.any { it.currentValue("contact") == "open" }
    def anyUnlocked = locks?.any { it.currentValue("lock") == "unlocked" }
    def isSecure = !anyOpen && !anyUnlocked
    if (isSecure) {
        logInfo("stopBlinking: System is secure, setting secure color")
        setLights(secureColor, secureLevel)
    } else {
        logInfo("stopBlinking: System is not secure, setting alert color")
        setLights(alertColor, alertLevel)
    }
}

// === Button Handler ===
def appButtonHandler(btn) {
    switch(btn) {
        case "btnTestDaily": doTestDaily(); break
        case "btnClearLogs": doClearLogs(); break
    }
}

// === Test Buttons ===
def doRunNow() { checkStatus() }
def doTestSecure() { setLights(secureColor, secureLevel) }
def doTestAlert() { setLights(alertColor, alertLevel) }
def doTestDaily() {
    logInfo("📜 Testing daily report...")
    sendDailyReportForce(false)
}

def doClearLogs() {
    logInfo("🧹 Manual clear logs from App - button")
    dailyResetClear()
}

// === Logging ===
def scheduleLogDisable() {
    app.updateSetting("logEnable", [value:"false", type:"bool"])
    log.info "Debug logging disabled automatically"
}

// === Daily Reset ===
def dailyResetClear() {
    logInfo("🧹 Running daily reset at ${new Date().format('MMM d yyyy hh:mm a', location.timeZone)}")
    state.alarmLog = []
    state.notifyLog = []
    state.lastNotificationTime = null
    state.notificationInitiated = null
    logInfo("🧹 Logs cleared at daily reset")
    runEvery1Minute("updateStatusTileLive")  // Re-establish the tile update schedule
    runIn(2, updateStatusTile)
}

// === Report Button Handler ===
def reportButtonHandler(evt) {
    logInfo("📜 Report button pressed, sending daily report...")
    sendDailyReportForce(false)
}
