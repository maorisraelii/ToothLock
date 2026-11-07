# ToothLock — Project Memory & Continuity Guide
> **Note for AI Coding Agent (Cline / Roo Code / Cursor / Antigravity):**  
> This file contains the complete development context, architectural decisions, and active state of **ToothLock**. Read this file first to resume work seamlessly.

---

## 1. Project Overview & Identity
- **Product Name:** ToothLock
- **Repository:** `maorisraelii/ToothLock` (Branch: `main`)
- **Purpose:** Prevents falling asleep without brushing teeth by monitoring night routine, detecting sleep via Samsung Galaxy Watch (Wear OS), and triggering aggressive vibrating alarms that bypass Sleep Mode.
- **Platform:** Native Android (Kotlin, minSdk 26, targetSdk 34) + Wear OS integration.
- **Components:**
  1. **Home Screen App Widget (`ToothWidgetProvider`):** 1-tap toggle (Red: "לא צחצחתי", Green: "צחצחתי! ✨"), streak tracking. Status resets daily at 04:00 AM (not midnight) to accommodate night routines.
  2. **Watch & Sleep Detector (`WatchAndSleepDetector`):** Checks Google Wearable Node API & Bluetooth for Galaxy Watch, and detects Samsung Sleep Mode (Bedtime Mode / ZenMode / DND).
  3. **Urgent Notification Helper (`NotificationHelper`):** High priority notification channel `tooth_alarm_v3` using `NotificationCompat.CATEGORY_ALARM`, `setFullScreenIntent(true)`, and `Notification.FLAG_INSISTENT` so the Galaxy Watch vibrates vigorously with its full alarm strength until interacted with.
  4. **Direct Watch Actions (`NotificationActionReceiver`):** Interactive notification actions on the watch ("צחצחתי עכשיו!", "נודניק 10 דק'").
  5. **Background Scheduler (`ToothCheckWorker` + `BootReceiver`):** WorkManager daily periodic check at bedtime (default 23:00) persisting across phone reboots.

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
  6. Present the PR to the user for review/approval before merging to `main`.

