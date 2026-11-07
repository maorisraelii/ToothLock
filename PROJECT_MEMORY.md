# ToothLock — Project Memory & Continuity Guide
> **Note for AI Coding Agent (Cline / Roo Code / Cursor / Antigravity):**  
> This file contains the complete development context, architectural decisions, and active state of **ToothLock**. Read this file first to resume work seamlessly.

---

## 1. Project Overview & Identity
- **Product Name:** ToothLock
- **Repository:** `maorisraelii/ToothLock` (Branch: `main`)
- **Purpose:** Prevents falling asleep without brushing teeth by monitoring night routine, detecting sleep via Samsung Galaxy Watch (Wear OS), and triggering aggressive vibrating alarms that bypass Sleep Mode.
- **Platform:** Native Android (Kotlin, minSdk 26, targetSdk 34) + Wear OS integration.
- **V2 & V3 UI & Features Architecture:**
  1. **Three-Tab Modern UI:**
     - **Tab 1: ראשי (Home):** Hero status card with flame streak badge, 7-day brushing history strip (א'-ש'), and a smart 2-Minute Brush Timer with 30-second quadrant haptics + audio chime cues that marks brushing done automatically on completion.
     - **Tab 2: מעבדת בדיקות (Test Lab):** Complete diagnostic suite: live Wear OS / Bluetooth scanner, 5-pulse watch haptic test, phone emergency alarm test, live sleep/DND diagnostic, and a 15-second rapid escalation simulator.
     - **Tab 3: הגדרות (Settings):** Bedtime target configuration, widget pin request, and live OTA update checker.
  2. **Android Quick Settings Tile (`ToothLockTileService`):**
     - Allows 1-tap toggling of teeth brushing status directly from the Android pull-down notification shade anywhere, anytime.
     - Keeps Home Screen Widget, Quick Settings Tile, and App in full bidirectional sync with instant haptic confirmation.
  3. **Watch-Focused Pulsing & Escalation (`NotificationHelper`):**
     - **When Watch is Connected/Worn:** Alerts are sent exclusively to the Galaxy Watch using a multi-pulse haptic train (5 pulses over ~8 seconds) while keeping the phone silent and vibration-free.
     - **3-Alert Escalation System:** Alerts 1, 2, and 3 (spaced 7 minutes apart) vibrate on the watch wrist. If all 3 are ignored and the user has not marked brushed, the app **escalates to a full Emergency Phone Alarm** (`tooth_emergency_phone_v4`) with loud siren, violent vibration, and full-screen intent to wake the user before deep sleep.
  4. **04:00 AM Rollover Cycle:** Status resets at 04:00 AM instead of midnight to protect late-night brushing and preserve streak history.
  5. **Home Screen App Widget (`ToothWidgetProvider`):** 1-tap toggle (Red: "לא צחצחתי", Green: "צחצחתי! ✨"), streak tracking.
  6. **In-App OTA Updater (`OtaUpdater`):** Checks GitHub Releases API for new versions automatically on startup or via manual button click. Downloads `ToothLock.apk` in background with progress display, and triggers native package installer with 1-click update via `FileProvider`.

---

## 2. CI/CD & Cloud Build System
- **Workflow:** `.github/workflows/build_apk.yml`
- **Persistent Signing Keystore:** `toothlock-signing.keystore` is auto-generated on the first CI run and committed back to the repository. This guarantees all future APKs share the exact same signature so users can update seamlessly without uninstalling or losing streak data.
- **Automated Releases:** Every push to `main` builds `ToothLock.apk` and publishes a GitHub Release tagged `v1.0.<run_number>` with Hebrew release notes.
- **Local / Remote Script:** `build_apk.ps1` allows 1-click cloud build and download or local compilation + automatic ADB installation.

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
