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
