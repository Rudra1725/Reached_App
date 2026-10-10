<div align="center">

# Reached 📍

### Autonomous, Battery-Efficient Geofencing & SMS Dispatcher for Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Google Play Services](https://img.shields.io/badge/API-Play%20Services%20Geofencing-4285F4?style=for-the-badge&logo=googleplay&logoColor=white)](https://developers.google.com/android/reference/com/google/android/gms/location/GeofencingClient)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <b>Eliminate repetitive "I've reached safely" texts.</b><br>
  Reached runs silently in the background, arms itself at your schedule, tracks your arrival via hardware geofencing, dispatches confirmation texts, and disarms itself to preserve 100% of your day's battery.
</p>

[📦 Download Latest Release APK](https://github.com/Rudra1725/Reached_App/releases/latest) • [🌐 Live Demo / Landing Page](https://reached-two.vercel.app) • [🐛 Report Bug](https://github.com/Rudra1725/Reached_App/issues)

</div>

---

## 📌 The Problem

Commuters, students, and daily travelers often need to notify family or guardians the moment they reach their destination (e.g., college, school, workplace). 

* **Manual texting is forgetful:** Forgetting to text leads to unnecessary panic or missed notifications.
* **Continuous GPS trackers drain battery:** Traditional continuous-location tracker apps drain 15–30% of battery per commute by running continuous foreground location loops.

**Reached 📍 solves this completely:** It requires **zero** foreground GPS tracking during the day. It schedules an exact low-power alarm clock trigger, activates an OS-level circular geofence perimeter, automatically dispatches arrival SMS messages across multiple recipients via Android's native `SmsManager`, and self-terminates immediately upon trigger.

---

## ✨ Features

- 🔋 **Zero Continuous GPS Drain:** Leverages Google Play Services Hardware Geofencing rather than continuous location tracking loops.
- ⏰ **Deep-Doze Bypass (`AlarmManager.setAlarmClock`):** Pierces aggressive OEM battery killers (MIUI/HyperOS, Realme UI, ColorOS) using prioritized alarm-clock intents.
- 🗂️ **Multi-Slot Preset Architecture:** Store up to 3 isolated presets (e.g., *College Campus*, *Workplace*, *Home Base*), each with independent coordinates, daily schedules, custom message templates, and dedicated contact lists.
- 👥 **Multi-Contact SMS Routing:** Automatically splits and delivers customized SMS updates to multiple recipients per preset without third-party gateways.
- 🛡️ **Debounce & Self-Disarm Guard:** Built-in 15-second debounce cooldown prevents duplicate SMS firing, followed by an immediate auto-disarm to shut down background radios.
- 🎨 **Obsidian Dark UI:** Sleek glassmorphism aesthetic built with Material Components, smooth animations, live status badges, and direct developer links.

---

## 🏗️ Architecture & How It Works
