# Light Security Monitor (LSM)

**Version:** 20.0.0  
**Author:** WarlockWeary + ChatGPT + Grok + Claude  

Light Security Monitor is a Hubitat SmartApp designed to monitor contact sensors and door locks, control smart bulbs, send notifications, and generate daily reports. It includes full support for both classic Hubitat dashboards and EZ Dashboard with 11 attributes.

---

## ✨ Features
- Contact sensors + multi-lock monitoring with custom short names
- Secure/Alert bulb control with blinking + time windows + (time-restricted)
- Repeatable notifications with grace period + time windows + (time-restricted)
- Daily reports with secure %, alarms, activity counts/durations
- Dashboard status tile (legacy) + EZ Tile device with 11 attributes
- Report Button child device to manually trigger reports
- Active session timers for currently open/unlocked devices
- Debounce logic for both locks and contacts to prevent duplicates

---

## 📦 Installation via Hubitat Package Manager (HPM)

1. Open **Hubitat Package Manager** on your hub.  
2. Select **Install → From a URL**.  
3. Enter the manifest URL: https://raw.githubusercontent.com/Warlock-Weary/Light.Security.Monitor/main/repository.json

---------------------------------------------------------------------------------------------------------------------------------

🔒 ##Light Security Monitor – Safe Update Guide

To keep your dashboards intact during updates, always update in place.
Do not delete the app or drivers unless you intend to start over from scratch!


✅ ##Updating via Hubitat Package Manager (Recommended)

Open Hubitat Package Manager (HPM) → Update.
HPM will download and overwrite the latest LSM app & driver code.
All child devices (LSM TILE, LSM EZ TILE, LSM REPORT BUTTON) stay in place.
Dashboards remain linked to these devices → no re-work required.

🛠 ##Manual Update (Safe Method)

If you’re editing/testing code locally:
Go to Apps Code → open Light Security Monitor.
Paste in the updated app code → Save.
Do the same under Drivers Code for LSM Tile Device, LSM EZ Tile Device, LSM Report Button.
Do not uninstall the app instance → this preserves your dashboards.

🚫 ##What Not To Do

❌ Don’t delete the LSM app instance from Apps.
→ This removes all child devices and breaks dashboards.

❌ Don’t rename namespace, name, or id in the manifest or drivers.
→ Hubitat will treat them as new devices and create duplicates.

🧰 ##Recovery Tips (If You Must Reinstall)

Export your dashboard JSON (Settings → Advanced → Export).
Reinstall LSM and let it recreate child devices.
Reimport your dashboard JSON so tiles rebind automatically.
