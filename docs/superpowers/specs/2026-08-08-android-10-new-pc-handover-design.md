# Android 10+ and New-PC Handover Design

## Goal

Produce one internally distributed Android APK for Android 10 through Android 16, improve the supported continuous-tracking workflow, and provide a complete Windows new-PC deployment and verification handover. The documentation must distinguish verified results from pending real-device validation.

## Scope

- Lower Android `minSdk` from 34 to 29 while keeping `compileSdk` and `targetSdk` at 36.
- Keep one APK and use explicit API-level branches where Android behavior differs.
- Keep location collection in the existing location foreground service with a persistent notification.
- After reboot, show a notification asking the user to open the app and resume tracking if tracking was active before shutdown. Do not start the location foreground service silently from `BOOT_COMPLETED`.
- Keep WorkManager limited to retrying queued uploads. It must not claim to restart the location service.
- Add or verify direct navigation to the relevant Android Settings screens when location, background location, notifications, or battery policy blocks operation.
- Fix the remaining secret-binding edge case so changing or retaining a custom CA cannot accidentally reuse an imported bearer token.
- Add complete Windows new-PC deployment, Android operations, requirements, settings, and test-evidence documents.
- Push tracked source and documentation to the existing feature branch. Do not merge to `main` as part of this work.

Route-history UI is excluded from this work and remains a later improvement.

## Android Compatibility

The application will use `minSdk = 29`, `targetSdk = 36`, and Java 17. Android 10 is the installation floor. Existing dependencies must remain unless a verified API-29 incompatibility requires a version change.

Version-specific behavior:

- Android 10 (API 29): request fine/coarse location and background location using the platform flow available on that version; the location service remains a declared foreground service of type `location`.
- Android 11-12 (API 30-32): guide background-location approval through application Settings when the system does not offer "Allow all the time" in the initial dialog.
- Android 13 (API 33): request notification permission before relying on the foreground notification.
- Android 14-16 (API 34-36): retain the `FOREGROUND_SERVICE_LOCATION` permission and start tracking only from a visible user action after prerequisites pass.

Every API introduced after 29 must be guarded by an SDK check or isolated behind an existing AndroidX compatibility API. Lint with `minSdk 29` is the compatibility gate.

## Continuous Tracking Contract

The supported continuous mode is:

1. The user selects a valid active profile and grants precise location, background location, and notifications where applicable.
2. The user presses Start while the activity is visible.
3. The location foreground service shows a persistent notification and continues when the screen is off or the app activity is dismissed.
4. Queued uploads survive network interruption and WorkManager retries them.
5. If the OS or device vendor stops the service, the app reports that tracking is no longer active; it does not claim guaranteed self-recovery.
6. If the phone reboots while tracking was active, a boot receiver posts a notification that opens the app so the user can explicitly resume.
7. If the user force-stops the app, Android prevents automatic recovery until the user opens the app again. This limitation must be visible in the handover documentation.

Company-owned manually installed phones and personal phones use the same APK. Their checklists differ: company devices may require unrestricted battery mode as an operating policy, while personal-device users must explicitly consent and may retain optimized battery mode with a documented reliability trade-off.

## Permission and Settings UX

The existing status screen remains the control point. It will determine the next required action by Android version and provide a button that either requests a runtime permission or opens the correct Settings page.

Required settings paths:

- Application details for permanently denied permissions.
- Location permission page for background location approval.
- Notification settings when notifications are denied or disabled.
- Battery optimization settings when the device restricts the app.

The UI must not promise that changing a setting guarantees uninterrupted tracking. It must clearly show whether tracking is currently running and retain the foreground notification as the primary running indicator.

## Secret Handling Correction

An imported pilot token remains hidden and is persisted only through encrypted profile secrets. Changing host, port, scheme, TLS mode, certificate pin, or custom CA clears the pending imported token. Applying an imported profile also clears stale custom-CA bytes unless the newly imported profile explicitly enters a separate CA selection flow. Tests must cover token preservation during an unchanged import/save and token clearing when trust configuration changes.

## New-PC Handover

Create a single entry-point guide for a nontechnical owner and linked technical detail for IT. It must cover:

- Windows 10/11 x64, Administrator access, fixed NTFS `D:` drive with at least 20 GB free, Internet access, and sleep prevention.
- Git, JDK 17, Android SDK platform 36/build-tools/platform-tools, Node/WinSW pinned caches, PostgreSQL 17.10/PostGIS 3.6.2, and optional Cloudflare `cloudflared` for a temporary pilot.
- Clone/checkout commands, Git safe-directory recovery, local Android build, expected APK path, server/database installation, service verification, firewall boundary, Quick Tunnel Start/Status/Stop, backup, restore, and uninstall boundaries.
- Exact commands with paths safe for spaces, plus recovery notes for the missing worktree cache and unsupported WinSW `refresh` failures found during this pilot.
- Separation of tracked source from runtime secrets, database contents, logs, generated profile, and APK artifacts.

## Requirements and Test Evidence

Create a requirements/test matrix that records:

- Host OS, CPU architecture, disk, permissions, network, JDK, Android SDK, PowerShell, Node, PostgreSQL/PostGIS, WinSW, and cloudflared requirements.
- Settings required on the server and on company/personal Android phones.
- Fresh verification commands and exact pass/fail totals for Node, Pester, Android unit tests, lint, and APK assembly.
- Current real-device evidence: Android 14+ pilot, authenticated Quick Tunnel, local/public health, and server receiving GPS.
- Emulator targets required for API 29, 31, 34, and 36.
- Explicit pending improvement: no physical Android 10-13 device is currently available, so real-device compatibility for those releases is not yet verified.

No document may imply that an unexecuted emulator or real-device test passed.

## Verification

Implementation acceptance requires:

- Unit tests for permission decisions, import token/custom-CA state, and reboot reminder decisions.
- Android lint and compile with `minSdk 29`.
- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` passing.
- Existing Node receiver and Pester Windows suites passing without regression.
- Manifest inspection confirming the boot receiver is non-exported where Android permits and requests only the minimum required boot permission.
- A generated debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Documentation scans confirming no real tunnel URL, bearer token, password, runtime profile, or database data is tracked.

Emulator execution for API 29, 31, 34, and 36 is required when those system images are installed. If installation or execution is not feasible in the current environment, the exact missing system images and commands must be recorded as pending rather than reported as passing.

## Git and Security

All work remains on `feature/cloudflare-quick-tunnel-pilot` and is pushed to the existing GitHub repository after verification. Runtime files under `D:\InternalGPS`, server credentials, DPAPI blobs, Quick Tunnel profiles/state/logs, GPS history, and APK binaries remain outside Git. The temporary Quick Tunnel may continue running independently of the push until the operator runs Stop.
