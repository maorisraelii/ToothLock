# ToothLock — Project Memory & Continuity Guide
> **Note for AI Coding Agent (Cline / Roo Code / Cursor / Antigravity):**  
> This file contains the complete development context, architectural decisions, and active state of **ToothLock**. Read this file first to resume work seamlessly.

---

## 1. Project Overview & Identity
- **Product Name:** ToothLock
- **Repository:** `maorisraelii/ToothLock` (Branch: `main`)
- **Purpose:** Prevents falling asleep without brushing teeth by monitoring night routine, detecting sleep via Samsung Galaxy Watch (Wear OS), and triggering aggressive vibrating alarms that bypass Sleep Mode.
- **Platform:** Native Android (Kotlin, minSdk 26, targetSdk 34) + Wear OS Companion App.
- **Architecture & Key Features:**
  1. **Wear OS Companion Module (`:wear`):**
     - Native Wear OS app running directly on Samsung Galaxy Watch.
     - Detects wrist presence in real time using `Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT` (0.0 = On-Body, 1.0 = Off-Body).
     - Directly triggers hardware vibrator via Google Play Services Wearable messages (`/vibrate_pulse`), bypassing Samsung phone-screen-on notification suppression!
     - Bi-directional sync with phone: tapping "צחצחתי! ✨" on watch immediately syncs phone status, widgets, and cancels alarms.
  2. **Zero-Phone-Vibration Watch Channel:**
     - Channel `tooth_watch_silent_phone_v5` has `enableVibration(false)` and `vibrationPattern = null`, guaranteeing the phone is 100% silent and vibration-free while vibrating only the watch.
  3. **Three-Tab Modern Phone UI:**
     - **Tab 1: ראשי (Home):** Hero status card with flame streak badge, 7-day brushing history strip (א'-ש'), and a smart 2-Minute Brush Timer with 30-second quadrant haptics + audio chime cues that marks brushing done automatically on completion.
     - **Tab 2: מעבדת בדיקות (Test Lab):** Complete diagnostic suite: live Wear OS / Bluetooth scanner, On-Body wrist sensor status, direct watch vibration test, 3-second delayed test (to allow turning screen off for standard Samsung bridge), phone emergency alarm test, live sleep/DND diagnostic, and a 15-second rapid escalation simulator.
     - **Tab 3: הגדרות (Settings):** Bedtime target configuration, widget pin request, and live OTA update checker.
  4. **Android Quick Settings Tile (`ToothLockTileService`):**
     - 1-tap toggle directly from the Android pull-down notification shade anywhere, anytime.
  5. **04:00 AM Rollover Cycle:** Status resets at 04:00 AM instead of midnight to protect late-night brushing and preserve streak history.
  6. **Home Screen App Widget (`ToothWidgetProvider`):** 1-tap toggle (Red: "לא צחצחתי", Green: "צחצחתי! ✨"), streak tracking.
  7. **In-App OTA Updater (`OtaUpdater`) with Dynamic Version Injection:**
     - Builds inject `-PversionCode=${VERSION_CODE} -PversionName=${VERSION_NAME}` dynamically into Gradle, ensuring the installed APK accurately reflects the release tag and preventing infinite update loops.

---

## 2. CI/CD & Cloud Build System
- **Workflow:** `.github/workflows/build_apk.yml`
- **Persistent Signing Keystore:** `toothlock-signing.keystore` is auto-generated on the first CI run and committed back to the repository. This guarantees all future APKs share the exact same signature so users can update seamlessly without uninstalling or losing streak data.
- **Multi-APK Builds:** Every push to `main` builds both `ToothLock.apk` (Phone) and `ToothLock-Watch.apk` (Wear OS Watch).
- **Automated Releases:** Publishes GitHub Release tagged `v1.0.<run_number>` with Hebrew release notes.

---

## 3. Development Workflow Rule: Pull Requests (PR) Only
- **Strict Rule:** Always work with branches and Pull Requests. Do not push directly to `main`.
- **Workflow Steps:**
  1. Create a descriptive branch: `git checkout -b feature/xyz` or `fix/xyz`.
  2. Implement changes, test thoroughly.
  3. Commit and push branch to origin: `git push -u origin <branch-name>`.
  4. Open a Pull Request using `gh pr create --title "..." --body "..."`.
  5. The PR triggers automated build verification in GitHub Actions.
  6. **Full Autonomous Execution:** Once CI build passes, automatically approve and merge the PR (`gh pr merge --merge --delete-branch`), verify the resulting release build, and present the completed update. Do not pause to wait for user confirmation.
