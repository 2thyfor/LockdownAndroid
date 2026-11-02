# Lockdown for Android — no-reset build

Native Kotlin + Jetpack Compose prototype for Samsung Galaxy and other Android devices. This edition does **not** require Device Owner provisioning, root, or a factory reset. It uses an Android Accessibility Service to detect when a selected app becomes foreground and immediately opens a Lockdown screen.

## Features in this prototype
- Lists launchable apps and lets you choose which to block.
- Schedules a one-time start at the next occurrence of a 24-hour clock time.
- Blocks for a chosen duration (1 minute to 7 days).
- Stores schedule and app selection locally and restores pending alarms after reboot.
- Generates a random six-digit PIN on first launch and stores only a salted SHA-256 hash. The PIN is not displayed.
- Allows changing the PIN from app settings only when no session is active; the PIN can unlock a session early if the user already knows it.

## Honest limitations
This is a best-effort app blocker, not an OS-enforced kiosk. Without Device Owner or a separate trusted administrator, Android does not let an ordinary app prevent the phone owner from force-stopping or uninstalling it, disabling its Accessibility permission, using Safe Mode, or changing system settings. Accessibility event delivery and background behavior can vary by Samsung model/One UI version. The PIN is not a way to prevent those system-level bypasses. There is no in-app early-unlock control in this prototype. The first-run random PIN is intentionally hidden, so early PIN unlock is unavailable until the user changes it after a session ends. It is not a way to prevent system-level bypasses.

The PIN is generated randomly and never shown, so it is intentionally unavailable during a session. When no session is active, Settings > Generate a new random PIN replaces it. If you want a usable manual override, have a trusted person set a PIN in a later build; this build does not expose the random PIN.

## Build
1. Install Android Studio (with Android SDK Platform 35).
2. Open this `LockdownAndroid` folder.
3. Allow Gradle sync.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

This environment does not have Android SDK/Gradle installed, so the project has not been compiled or tested on a physical Samsung device. Treat it as a source prototype, not a verified release APK.

## First-time setup on Samsung
1. Install the APK.
2. Open Lockdown and tap **Enable Lockdown Accessibility**.
3. In Android Accessibility settings, find Lockdown under installed services and enable it. Review the permission and confirm.
4. Return to Lockdown, select nonessential apps, set a start time and duration, then schedule.
5. In Samsung Settings > Apps > Lockdown > Battery, allow background activity / set battery use to unrestricted if the phone's firmware is delaying alarms or service events.

Do not block Phone, Settings, System UI, the launcher, emergency services, or other essential system packages. Test with one nonessential app first.
