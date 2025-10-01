/**
 * Light Security Monitor v20.0.3
 * Author: WarlockWeary + ChatGPT + Grok + Claude
 *
 * Features:
 * - Contact Sensors + multi-lock monitoring with custom short names
 * - Bulb color control with + blinking (time-restricted)
 * - Notifications (repeatable, delayed start, grace period, time-restricted)
 * - Enhanced Daily Report (secure %, alarms, activity with counts/durations)
 * - Dashboard status tile with Secure Time (hh mm + %) and auto-refresh
 * - Polished multi-line formatting with active session tracking
 * - Grace period delay for first notification
 * - Dashboard button to trigger daily report
 * - Report Button child device to trigger daily reports manually
 * - Auto-reset logs daily at configurable time
 * - Debounce for both locks AND contact sensors to filter duplicate events
 * - Live auto-refresh of dashboard and EZ tiles on device state changes
 * - 1-minute scheduled updates ensure tiles stay current even when secure (for clocks and timers)
 * - Active session timers displayed for devices currently open/unlocked
 * - Short names used consistently in reports, dashboard tile, and EZ tile
 * - Added EZ Dashboard support with 11 different display show/attributes
 * 
 * FIXES / ENHANCEMENTS:
 * - Fixed stopBlinking() function that was always setting red lights
 * - Added diagnostics to track light color changes
 * - Improved state change logic with delayed tile updates
 * - Fixed blinking with reliable on/off commands
 * - Aligned dashboard tile secure time with daily report
 * - Updated to show active session time on dashboard, daily totals in report
 * - FIXED: Duration tracking bug where locks and contact sensors showed 00h 00m in daily reports
 * - FIXED: Premature closing of alarm log entries for devices still open/unlocked
 * - ADDED: Debug logging and improved event tracking
 * - FIXED: Event logging now only triggers on actual state changes to prevent duplicates
 * - FIXED: Time calculation discrepancies between dashboard and daily report (unified calculation)
 * - FIXED: Removed "minimum 1 minute" rule that was causing errors
 * - ADDED: Contact sensor debounce to handle duplicate events from buggy devices
 * - FIXED: Daily report unsecure time now sums individual device times
 * - FIXED: Still-open devices (contacts/locks) capped at current time instead of 24h window end
 * - ENHANCED: Daily/Forced Reports now show total event count (Nx) in the DEVICE ACTIVITY header
 * > Fixed repeat notifications when devices are open before window.
 * - ADDED: EZ Dashboard support with 11 - options:
 * - LSM-STATUS -
 * - UNSECURED-DEVICES -
 * - SECURE-PERCENT -
 * - LOCKS-OPEN -
 * - CONTACTS-OPEN -
 * - ACTIVITY -
 * - LOCK-ACTIVITY -
 * - CONTACT-ACTIVITY -
 * - LOCKS -
 * - CONTACTS -
 * - LAST-UPDATE -
 */


definition(
    name: "Light Security Monitor",
    namespace: "LSM",
    author: "WarlockWeary + ChatGPT + Grok + Claude",
    description: "Monitor sensors and multiple locks, control bulbs with color/blink, daily reports, test buttons, and dashboard status tile. Includes grace period and customizable 24-hour report. FIXED duration tracking and time calculations. Added contact sensor debounce.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: false
)

preferences {
    page(name: "mainPage")
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

// === Helper: Contact Short Name by deviceNetworkId ===
def getContactShortName(deviceId) {
    return settings."contactShortName_${deviceId}"
}

// === Helper: Resolve displayName -> short name (works for locks or contacts) ===
def toShortLabel(displayName) {
    def ld = locks?.find { it.displayName == displayName }
    if (ld) return getLockShortName(ld.deviceNetworkId) ?: displayName

    def cd = contacts?.find { it.displayName == displayName }
    if (cd) return getContactShortName(cd.deviceNetworkId) ?: displayName

    return displayName
}

// === Helper: Format Seconds as hh mm ss (clamp negatives) ===
def fmtTime(totalSeconds) {
    int s = Math.max(0, (totalSeconds ?: 0) as int)
    def hours = (s / 3600) as int
    def minutes = ((s % 3600) / 60) as int
    def seconds = s % 60
    return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
}

// === UNIFIED TIME CALCULATION (sums device activity times) ===
def calculateSecureTime(windowStart, windowEnd) {
    def results = [:]
    long startMs = (windowStart as long)
    long endMs = (windowEnd as long)
    int totalSeconds = Math.max(0, ((endMs - startMs) / 1000L) as int)

    def deviceActivity = [:]
    long nowMs = now()   // <-- FIX: capture "now" once for active entries

    (state.alarmLog ?: []).each { entry ->
        if (!entry?.start) return
        long eStart0 = entry.start as long
        Long eEnd0 = entry.end != null ? (entry.end as long) : null
        boolean overlaps = (eStart0 < endMs) && ((eEnd0 == null) || (eEnd0 > startMs))
        if (!overlaps) return

        long s = Math.max(eStart0, startMs)

        // FIX: If entry is still open (end == null), cap at "now" instead of endMs
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

    // Sum individual device times for unsecure time
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
    sb += "🔒 <b>MONITOR SENSORS:</b><br>"

    // Contacts: show Short (original: Full) when short differs
    if (contacts) {
        contacts.each { c ->
            def shortLabel = getContactShortName(c.deviceNetworkId)
            if (shortLabel && shortLabel.trim() && shortLabel != c.displayName) {
                sb += "&nbsp;&nbsp;${shortLabel} <i>(original: ${c.displayName})</i><br>"
            } else {
                sb += "&nbsp;&nbsp;${c.displayName}<br>"
            }
        }
    }

    // Locks: show Short (original: Full) when short differs
    if (locks) {
        locks.each { l ->
            def shortLabel = getLockShortName(l.deviceNetworkId)
            if (shortLabel && shortLabel.trim() && shortLabel != l.displayName) {
                sb += "&nbsp;&nbsp;${shortLabel} <i>(original: ${l.displayName})</i><br>"
            } else {
                sb += "&nbsp;&nbsp;${l.displayName}<br>"
            }
        }
    }

    sb += "<br>"
    sb += "🌟 <b>LIGHT(S):</b><br>"
    if (nightLights) nightLights.each { sb += "&nbsp;&nbsp;${it.displayName}<br>" }
    else sb += "&nbsp;&nbsp;Not Set<br>"
    sb += "<br>"
    sb += "🔐 <b>SECURE:</b> ${(secureColor ?: "Green").toUpperCase()} AT ${(secureLevel ?: 100)}%<br>"
    sb += "❌ <b>ALERT:</b> ${(alertColor ?: "Red").toUpperCase()} AT ${(alertLevel ?: 100)}%<br><br>"

    if (blinkEnable) {
        def stopText = (blinkMaxMinutes == 0) ? "NEVER" : "AUTO-STOP AFTER ${blinkMaxMinutes ?: 10}M"
        sb += "🚨 <b>BLINKING:</b> EVERY ${blinkInterval ?: 3}S (${stopText})<br>"
        def bStart = blinkStartTime ? timeToday(blinkStartTime, location.timeZone) : null
        def bEnd = blinkEndTime ? timeToday(blinkEndTime, location.timeZone) : null
        if (bStart && bEnd) sb += "&nbsp;&nbsp;<b>TIME LIMIT:</b> ${bStart.format('h:mm a')} – ${bEnd.format('h:mm a')}<br>"
        sb += "<br>"
    } else sb += "🚨 <b>BLINKING:</b> DISABLED<br><br>"

    if (sendPush) {
        sb += "📲 <b>NOTIFICATIONS:</b> ENABLED EVERY ${(repeatMinutes ?: 15)}M"
        def grace = gracePeriodMinutes ?: 0
        if (grace > 0) sb += "<br>&nbsp;&nbsp;<b>FIRST ALERT DELAYED:</b> ${grace}M"
        def nStart = startTime ? timeToday(startTime, location.timeZone) : null
        def nEnd = endTime ? timeToday(endTime, location.timeZone) : null
        if (nStart && nEnd) sb += "<br>&nbsp;&nbsp;<b>TIME LIMIT:</b> ${nStart.format('h:mm a')} – ${nEnd.format('h:mm a')}"
        sb += "<br><br>"
    } else sb += "📲 <b>NOTIFICATIONS:</b> DISABLED<br><br>"

    def daily = dailyReportTime ? timeToday(dailyReportTime, location.timeZone) : null
    sb += "📜 <b>DAILY REPORT:</b> ${dailyReportEnable && daily ? "ENABLED AT ${daily.format('h:mm a')}" : "DISABLED"}<br><br>"
    sb += "📝 <b>LOGGING:</b> ${logEnable ? "ENABLED" : "DISABLED"} (AUTO-DISABLE: ${logAutoDisable ? "YES" : "NO"})<br><br>"
    if (state.lastCheck) sb += "📅 <b>LAST CHECK:</b> ${state.lastCheck}<br>"
    return sb
}

// === Setup Page ===
def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Light Security Monitor Setup</b>", install: true, uninstall: true, refreshInterval: 0) {
        section("") {
            paragraph getStatusSummary()
            input name: "btnTestDaily", type: "button", title: "📜 TEST DAILY REPORT"
            input name: "btnClearLogs", type: "button", title: "🗑️ CLEAR ACTIVITY LOGS"
        }
        section("<b>Select name for monitor:</b>") {
            input "namePrefix", "text", title: "Add Instance Name - Required (e.g. HOUSE, GARAGE)", required: true, submitOnChange: true
        }
        section("<b>Select devices to monitor:</b>") {
            input "contacts", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: true, submitOnChange: true
            input "locks", "capability.lock", title: "Z-Wave Door Locks", multiple: true, required: true, submitOnChange: true

            // Per-contact short names (optional)
            if (contacts) {
                contacts.each { c ->
                    input "contactShortName_${c.deviceNetworkId}", "text",
                          title: "Short Name for ${c.displayName}",
                          defaultValue: c.displayName, required: false, submitOnChange: true
                }
            }

            // Per-lock short names (optional)
            if (locks) {
                locks.each { lock ->
                    input "lockShortName_${lock.deviceNetworkId}", "text",
                          title: "Short Name for ${lock.displayName}",
                          defaultValue: lock.displayName, required: false, submitOnChange: true
                }
            }
        }
        section("<b>Select device(s) to control:</b>") {
            input "nightLights", "capability.colorControl", title: "Bulb(s) to Control", multiple: true, required: true, submitOnChange: true
        }
        section("<b>Color & Brightness Settings:</b>") {
            input "secureColor", "enum", title: "Color when SECURE", options: colorOptions(), defaultValue: "Green", submitOnChange: true
            input "secureLevel", "number", title: "Brightness for SECURE (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
            input "alertColor", "enum", title: "Color when ALERT", options: colorOptions(), defaultValue: "Red", submitOnChange: true
            input "alertLevel", "number", title: "Brightness for ALERT (0-100)", defaultValue: 100, range: "0..100", submitOnChange: true
        }
        section("<b>Blink Settings:</b>") {
            input "blinkEnable", "bool", title: "Enable blinking for Alert?", defaultValue: false, submitOnChange: true
            input "blinkStartTime", "time", title: "Blink Only After (start time)", required: false, submitOnChange: true
            input "blinkEndTime", "time", title: "Stop Blinking (end time)", required: false, submitOnChange: true
            input "blinkInterval", "enum", title: "Blink Interval (seconds)", options: (1..10).collect { it.toString() }, defaultValue: "2", required: false, submitOnChange: true
            input "blinkMaxMinutes", "number", title: "Auto-stop blinking after (minutes, 0 = never)", defaultValue: 60, required: false, submitOnChange: true
        }
        section("<b>Notification settings:</b>") {
            input "gracePeriodMinutes", "number", title: "Grace Period for First Alert (minutes, 0 = immediate)", defaultValue: 15, range: "0..60", submitOnChange: true
            input "notifyDevices", "capability.notification", title: "Notification Device(s)", multiple: true, required: false, submitOnChange: true
            input "sendPush", "bool", title: "Send Notifications?", defaultValue: true, submitOnChange: true
            input "repeatMinutes", "number", title: "Repeat Notification Every (minutes)", defaultValue: 15, submitOnChange: true
            input "startTime", "time", title: "Notify Only After (start time)", required: true, submitOnChange: true
            input "endTime", "time", title: "Stop Notify After (end time)", required: true, submitOnChange: true
        }
        section("<b>Daily Report:</b>") {
            input "dailyReportEnable", "bool", title: "Enable daily report?", defaultValue: false, submitOnChange: true
            input "dailyReportTime", "time", title: "Daily report time", required: false, submitOnChange: true
            paragraph "Set the start time for the 24-hour daily report (default 6:00 AM). End time auto-sets 24 hours later."
            input "dailyReportStartTime", "time", title: "Daily report start time", defaultValue: "06:00 AM", submitOnChange: true
        }
        section("<b>Advanced Settings:</b>") {
            input "eventMergeGapSeconds", "number",
                title: "Debounce-Ignore duplicate lock/unlock events within time window. Seconds (0-120, 0=off)",
                defaultValue: 10, range: "0..120", required: false, submitOnChange: true
            
            input "contactMergeGapSeconds", "number",
                title: "Debounce-Ignore duplicate contact open/close events within time window. Seconds (0-120, 0=off)",
                defaultValue: 3, range: "0..120", required: false, submitOnChange: true
        }
        section("<b>Logging:</b>") {
            input "logEnable", "bool", title: "Enable debug logging?", defaultValue: false, submitOnChange: true
            input "logAutoDisable", "bool", title: "Auto-disable logging after 30 minutes?", defaultValue: false, submitOnChange: true
        }
    }
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
    def totalOpens = deviceActivity.collect { (it.value.count ?: 0) }.sum() ?: 0   // NEW

    def report = [
        "📜 LSM Daily Report (${nowStr})",
        "",
        "== SECURE/UNSECURE TIME ==",
        "TOTAL: ${fmtTime(timeData.secureSeconds)} (${timeData.securePct}% - 24h)",
        "DEVICES OPEN: ${fmtTime(timeData.unsecureSeconds)}",
        "",
        "== DEVICE ACTIVITY (${deviceActivity.size()}x)-(${totalOpens}x) =="   // UPDATED
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
    def secureSeconds = timeData.secureSeconds
    def unsecureSeconds = timeData.unsecureSeconds
    def securePct = timeData.securePct
    def deviceActivity = timeData.deviceActivity
    def totalOpens = deviceActivity.collect { (it.value.count ?: 0) }.sum() ?: 0   // NEW

    def report = ["📜 LSM Daily Report (${nowStr})", ""]
    report << "== SECURE/UNSECURE TIME =="
    report << "TOTAL: ${fmtTime(secureSeconds)} (${securePct}% - 24h)"
    report << "DEVICES OPEN: ${fmtTime(unsecureSeconds)}"
    report << ""
    report << "== DEVICE ACTIVITY (${deviceActivity.size()}x)-(${totalOpens}x) =="   // UPDATED

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
    logInfo("📜 Daily Report sent - Secure: ${fmtTime(secureSeconds)} (${securePct}%)")

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

    // === Daily-window (6AM anchor) for the "Secure Since" section ===
    def reportStartTime = timeToday(dailyReportStartTime ?: "06:00 AM", tz)
    if (now.before(reportStartTime)) {
        use(groovy.time.TimeCategory) { reportStartTime = reportStartTime - 1.day }
    }
    def dailyWindowStart = reportStartTime.time
    def reportStartStr = reportStartTime.format('h:mm a', tz)

    // === Rolling 24h window for the % shown on "ALL DEVICES SECURE" line ===
    def rollingStart = currentTime - (24L * 60L * 60L * 1000L)
    def timeDataDaily = calculateSecureTime(dailyWindowStart, currentTime)
    def timeDataRolling = calculateSecureTime(rollingStart, currentTime)

    // Active session times for currently open/unlocked devices
    def activeSessions = [:]
    def entries = state.alarmLog ?: []
    def activeEntries = entries.findAll { !it.end }
    activeEntries.each { entry ->
        def eStart = entry.start
        def eEnd = currentTime
        def dur = (eEnd > eStart) ? ((eEnd - eStart) / 1000).toInteger() : 0
        activeSessions[entry.device] = dur
    }

    // Calculate lock and contact activity totals
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

    // Always calculate totalOpens
    def totalOpens = (timeDataDaily.deviceActivity?.collect { (it.value?.count ?: 0) }?.sum() ?: 0) as Integer

    // Build status section
    def headerIcon = openDevs || unlockedLocks ? "❌" : "✅"
    def tile = "${headerIcon} SECURITY MONITOR STATUS<br>"
    def deviceLines = []

    if (openDevs || unlockedLocks) {
        // Unsecure state: List individual devices
        if (unlockedLocks) {
            unlockedLocks.each { name ->
                def shortName = getLockShortName(locks.find { it.displayName == name }?.deviceNetworkId) ?: name
                def activeTime = activeSessions[name] ?: 0
                def todayCount = timeDataDaily.deviceActivity[name]?.count ?: 0
                deviceLines << "LOCK: ${shortName} (${todayCount}x) (${fmtTime(activeTime)})"
            }
        }
        if (openDevs) {
            openDevs.each { name ->
                def activeTime = activeSessions[name] ?: 0
                def todayCount = timeDataDaily.deviceActivity[name]?.count ?: 0
                deviceLines << "CONTACT: ${toShortLabel(name)} (${todayCount}x) (${fmtTime(activeTime)})"
            }
        }
    } else {
        // Secure state: Show secure status with percentage
        tile += "✅ ALL DEVICES SECURE - ${timeDataRolling.securePct}%<br>"
    }

    // Separator
    tile += "──────────────────────<br>"

    // Device lines (only for unsecure)
    deviceLines.each { line -> tile += "${line}<br>" }
    if (deviceLines.size() > 0) tile += "<br>"

    // === Secure-only summary ===
    if (openDevs.isEmpty() && unlockedLocks.isEmpty()) {
        tile += "System Secure: ${reportStartStr} · (${fmtTime(timeDataDaily.secureSeconds)})<br>"
        tile += "Device Totals: (${totalOpens}x) (${fmtTime(timeDataDaily.unsecureSeconds)})<br>"
        tile += "Lock Activity: (${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})<br>"
        tile += "Contact Activity: (${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})<br>"
        def nStart = startTime ? timeToday(startTime, location.timeZone) : null
        def nEnd = endTime ? timeToday(endTime, location.timeZone) : null
        def notifyStatus = sendPush ?
            "ON (${repeatMinutes ?: 15}m)" + (nStart && nEnd ? " ${nStart.format('h:mm a')} – ${nEnd.format('h:mm a')}" : "") :
            "OFF"
        def bStart = blinkStartTime ? timeToday(blinkStartTime, location.timeZone) : null
        def bEnd = blinkEndTime ? timeToday(blinkEndTime, location.timeZone) : null
        def blinkStatus = blinkEnable ?
            "ON (${blinkInterval ?: 3}s)" + (bStart && bEnd ? " ${bStart.format('h:mm a')} – ${bEnd.format('h:mm a')}" : "") :
            "OFF"
        tile += "Notifications: ${notifyStatus}<br>"
        tile += "Blinking: ${blinkStatus}<br>"
    } else {
        // === Unsecure summary (no System Secure) ===
        tile += "Device Totals: (${totalOpens}x) (${fmtTime(timeDataDaily.unsecureSeconds)})<br>"
        tile += "Lock Activity: (${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})<br>"
        tile += "Contact Activity: (${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})<br>"
    }

    // Send to Legacy Tile Device
    child.sendEvent(name: "statusTile", value: tile, isStateChange: true)
    child.sendEvent(name: "image", value: tile, isStateChange: true)

    // === EZ Dashboard Child Device Support ===
    def ezChild = getChildDevice("${app.id}-EZTile")
    if (ezChild) {
        def allSecure = (openDevs.isEmpty() && unlockedLocks.isEmpty())
        def securePct = timeDataRolling.securePct ?: 100
        def currentLockCount = unlockedLocks?.size() ?: 0
        def currentContactCount = openDevs?.size() ?: 0
        def totalUnsecureCount = currentLockCount + currentContactCount
        def unsecuredTimeFormatted = fmtTime(timeDataDaily.unsecureSeconds)

        // Format lock/contact activity for EZ tile
        def lockActivityStr = "(${lockActivity.count}x) (${fmtTime(lockActivity.seconds)})"
        def contactActivityStr = "(${contactActivity.count}x) (${fmtTime(contactActivity.seconds)})"

// ➕ NEW: build name lists
        def lockNames = unlockedLocks?.collect { name ->
        getLockShortName(locks.find { it.displayName == name }?.deviceNetworkId) ?: name }?.join(", ") ?: ""

        def contactNames = openDevs?.collect { name ->
        toShortLabel(name) ?: name }?.join(", ") ?: ""

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

// === Installed/Updated ===
def installed() { initialize() }
def updated() {
    unsubscribe()
    unschedule()
    initialize()
    if (logAutoDisable) runIn(1800, scheduleLogDisable)
}

// === Initialize ===
def initialize() {
    getTileDevice()
    
    // === EZ Dashboard Child Device Creation ===
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
    runIn(2, checkStatus)
    if (dailyReportEnable && dailyReportTime) schedule(dailyReportTime, sendDailyReport)

    if (!dailyReportStartTime) app.updateSetting("dailyReportStartTime", [value: "06:00 AM", type: "time"])
    unschedule("dailyResetClear")
    schedule(dailyReportStartTime ?: "06:00 AM", dailyResetClear)

    state.previousSecure = null
    state.lastNotificationTime = null
    logInfo("Initialized")
}

// Helper for lock debounce
private boolean reopenIfRecentLock(String deviceName, long nowMs) {
    int gapSec = (eventMergeGapSeconds ?: 10) as Integer
    if (gapSec <= 0) return false
    def lastClosed = (state.alarmLog ?: []).reverse()
        .find { it.device == deviceName && it.end }
    if (!lastClosed) return false
    long gap = (nowMs - (lastClosed.end as Long)) / 1000
    if (gap >= 0 && gap <= gapSec) {
        lastClosed.end = null
        return true
    }
    return false
}

// Helper for contact debounce
private boolean reopenIfRecentContact(String deviceName, long nowMs) {
    int gapSec = (contactMergeGapSeconds ?: 3) as Integer
    if (gapSec <= 0) return false
    def lastClosed = (state.alarmLog ?: []).reverse()
        .find { it.device == deviceName && it.end }
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
            stopBlinking()
        } else {
            setLights(alertColor, alertLevel)
            if (sendPush && inTimeWindow()) scheduleNotification()
            if (blinkEnable && inBlinkTimeWindow()) toggleBlink()
            runEvery1Minute("updateStatusTileLive")
        }

        state.previousSecure = isSecure
        updateStatusTile()
    } else {
        logInfo("No state change detected - staying ${isSecure ? 'secure' : 'alert'}")
        if (closedAny) updateStatusTile()
    }
}

// === Live Dashboard Tile Update ===
def updateStatusTileLive() {
    updateStatusTile()
    
    // Check if we need to start notifications for already-open devices
    def anyOpen = contacts?.any { it.currentValue("contact") == "open" }
    def anyUnlocked = locks?.any { it.currentValue("lock") == "unlocked" }
    def isUnsecure = anyOpen || anyUnlocked
    
    if (isUnsecure && sendPush && inTimeWindow()) {
        // If no notification is scheduled and we're past grace period, start notifications
        def hasScheduledNotification = state.lastNotificationTime != null
        if (!hasScheduledNotification) {
            logInfo("🔔 Tile update detected unsecure devices during notification window - starting notifications")
            scheduleNotification()
        }
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
    if (blinkEnable && inBlinkTimeWindow()) {
        nightLights?.each { light ->
            def currentState = light.currentValue("switch") == "on" ? "off" : "on"
            light."${currentState}"()
        }
        runIn(blinkInterval?.toInteger() ?: 2, toggleBlink)
        if (blinkMaxMinutes > 0) runIn(blinkMaxMinutes * 60, stopBlinking)
    }
}

def stopBlinking() {
    logInfo("stopBlinking() called")
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

// === Clear Logs Button ===
def doClearLogs() {
    logInfo("🧹 Manual clear logs from button")
    dailyResetClear()
}

// === Logging ===
def scheduleLogDisable() {
    logEnable = false
    logInfo("Debug logging disabled")
}

// === Daily Reset ===
def dailyResetClear() {
    logInfo("🧹 Running daily reset at ${new Date().format('MMM d yyyy hh:mm a', location.timeZone)}")
    state.alarmLog = []
    state.notifyLog = []
    state.lastNotificationTime = null
    logInfo("🧹 Logs cleared at daily reset")
    runIn(2, updateStatusTile)
}

// === Report Button Handler ===
def reportButtonHandler(evt) {
    logInfo("📜 Report button pressed, sending daily report...")
    sendDailyReportForce(false)
}
