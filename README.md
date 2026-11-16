# Daily Growth (Android)

A discreet Android wellness tracker with a days-without-looking streak, a one-time accountability access code, neutral daily notifications, and an on-device DNS filtering VPN.

## Included
- Small Android home-screen widget showing the current streak; tap it to open Daily Growth. It refreshes when the app opens, the streak is reset, and Android sends date/time changes, with a daily system refresh as a fallback.
- Kotlin + Jetpack Compose Android project (minimum Android 8.0 / API 26).
- Neutral app name, icon, notification wording, and dark navy/green interface.
- Streak counter, personal best, total tracked days, and PIN-gated reset.
- Random six-digit accountability code generated on first launch. The app stores a salted SHA-256 hash, not the code. The code is shown once so it can be given to a trusted accountability partner; it is not recoverable afterward.
- PIN required to reset the streak or turn protection off from inside the app.
- DNS filtering VPN with a built-in starter set plus a downloadable adult-domain list from StevenBlack/hosts `porn-only` variant.
- The downloaded list is stored locally and refreshed automatically about once every 24 hours when a network is available. A manual **Update website list now** action is also included.
- The app shows the downloaded domain count and last successful update time.
- Service status is reported separately from the saved preference, so the screen can reflect whether the VPN service successfully established its tunnel.
- Cloudflare Family DNS (1.1.1.3) is used for domains that are not on the local list.
- Background service notification, daily neutral check-in via WorkManager, and attempts to restore protection after reboot when enabled.
- GitHub Actions workflow to build a debug APK.

## Blocklist source and privacy
The expanded domain list is downloaded from:
`https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts`

StevenBlack/hosts documents the `porn-only` variant as a category-specific list (about 76,801 entries in its published list table at the time this project was updated). The source is maintained separately from this app; its entries may change over time. The app validates the response and refuses to replace the last known-good list if the download is unexpectedly small or fails. The list is downloaded over HTTPS and stored locally. The app does not upload browsing history or send a list of visited domains to the list provider.

DNS requests for non-listed domains are forwarded to Cloudflare Family DNS, so Cloudflare receives those DNS queries. The app does not keep a browsing-history log.

## Important limitations
This is a best-effort DNS filter, not an unbreakable filter. The domain list is broad but cannot guarantee every adult site is covered, and false positives are possible. DNS filtering can be bypassed by apps or browsers using DNS-over-HTTPS / DNS-over-TLS, a separate VPN, proxies, or other encrypted DNS methods. The VPN currently handles IPv4 UDP DNS requests; it is not a full traffic-inspection VPN and does not inspect page content. It cannot block explicit content hosted on a non-blocked general-purpose domain. Android system settings can still revoke VPN permission, force-stop the app, or uninstall it. A normal app cannot prevent those actions without device-owner / managed-device privileges.

## Build on Windows
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this folder in Android Studio and allow Gradle sync.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).

Or use GitHub Actions in `.github/workflows/android.yml` to build the debug APK. The project does not include a Gradle wrapper JAR.

## First launch
1. Open Daily Growth.
2. Give the one-time six-digit code to your trusted accountability partner and confirm that you saved it. The code is never shown again.
3. Enable Background protection and approve Android's VPN permission prompt.
4. Keep the Android VPN key/notification active. Android may show the app name in system VPN settings.
5. Keep an internet connection available for the initial blocklist download. The app will refresh the list automatically about daily; you can also tap **Update website list now**.

## PIN behavior
The six-digit code is generated randomly. A 16-byte random salt and the SHA-256 hash are stored locally; the plaintext code is not retained. The code is required for streak resets and turning protection off from inside the app. It does not stop someone with physical access to Android settings from revoking VPN access or uninstalling the app.


## Wireless Android Auto Bluetooth pause

Version 1.3.0 listens for Bluetooth ACL connect/disconnect broadcasts for the paired device named `smartBox-8441`. While connected, DNS queries bypass the local adult-domain list and use standard Cloudflare DNS (1.1.1.1); when disconnected, filtering returns to the normal Cloudflare Family DNS (1.1.1.3) and local blocklist. The device `GERALD SENTRA` is intentionally ignored. The VPN remains connected to remain compatible with Android Always-on VPN.

Android 12+ requires the Nearby devices / Bluetooth permission for device-name matching. If permission is denied, automatic car mode will not work. If the phone is already connected to `smartBox-8441` when the service starts, disconnect and reconnect that device once so the app receives the connection event. This Bluetooth trigger is an approximation of Android Auto activity, not a direct Android Auto session API.
