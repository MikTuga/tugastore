# Changelog

All notable changes to TugaStore will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1] - 2026-05-17

Phase 1 ecosystem refactor — TugaStore is no longer a standalone Gradle project. It now lives as the `:tugastore` module inside a workspace at `<workspace>/` alongside 5 sibling apps and the shared `tuga-design` Android library. Same workspace ships **TugaSettings** as the 6th app.

### Added
- `FeedbackActivity` with a structured form (type spinner / message / optional email / "attach diagnostic" checkbox). On submit, dispatches via shared `FeedbackSubmitter`: HTTPS POST to `https://miktuga.ru/api/feedback` when WiFi is up (endpoint queued for Phase 2), else writes JSON to TugaStore-private `filesDir/feedback/<yyyyMMdd_HHmmss>_<ms%10000>.json` (`/data/data/com.miktuga.store/files/feedback/`) with collision-safe suffix. Three-branch toast: SENT / QUEUED / FAILED. Queue lives in app-private storage so other apps with WRITE_EXTERNAL_STORAGE can't plant payloads that we'd later upload as first-party feedback.
- `MainActivity.onResume()` auto-retry of queued feedback: scans `filesDir/feedback/*.json`, re-POSTs each, deletes on 2xx — drains the queue whenever the user opens TugaStore on WiFi.
- Overflow menu item "Обратная связь" on `MainActivity` (ic_more_vert) launching `FeedbackActivity`. `FeedbackActivity` is exported with intent-filter on action `com.miktuga.action.FEEDBACK` so sibling utilities can invoke the same form.
- `InstallResultReceiver` (`android:exported="false"`) wired to handle `PackageInstaller.Session` callbacks: STATUS_PENDING_USER_ACTION launches the system confirmation intent; STATUS_SUCCESS shows "<app> · установлено"; per-code failure toasts (CONFLICT / INCOMPATIBLE / INVALID / STORAGE / aborted / blocked).
- TugaSettings as 6th catalog entry in `assets/catalog.json` (`com.miktuga.settings`, 0.1.0, `apps/tugasettings.apk` path).
- Workspace-level docs: top-level `README.md` as entry point for new contributors.

### Changed
- **Packages renamed** from `com.example.tugastore.*` to `com.miktuga.*` (this app: `com.miktuga.store`). Done before the first community release to lock the namespace.
- `ApkInstaller.install()` rewritten from `Intent.ACTION_VIEW` (fire-and-forget) to `PackageInstaller.Session` (API 21+): creates a session sized via `setSize(file.length())`, streams 64 KB at a time into `session.openWrite("apk", 0, length)` with `fsync`, commits with a broadcast `PendingIntent` so the new `InstallResultReceiver` can report success/failure. Caller shows "Устанавливается…" toast on dispatch.
- Build infrastructure: TugaStore is now a Gradle module inside the workspace (`projectDir = TugaStore/app`); module path is `:tugastore`. Workspace `settings.gradle.kts` owns the include list. App-level R-classes still see library resources via workspace `gradle.properties` (`android.nonTransitiveRClass=false`).
- Resources reorganized: common `colors.xml`, `themes.xml` (base `Theme.TugaStore`), and 16 shared drawables (card / button / status-dot / icon-circle / common ic_*) moved to `tuga-design/src/main/res/`. App `res/` keeps only TugaStore-specific assets (ic_more_vert, layouts, mipmaps, FileProvider paths). `implementation(project(":tuga-design"))` wires them back.
- Signing keystore moved to workspace root `_signing/tuga-release.jks` (was `TugaStore/tugastore-release.jks`); `keystore.properties` now read via `rootProject.file("_signing/keystore.properties")`. **Keystore password rotated** — the original password was leaked in plain-text chat history.
- All 6 apps now use a single shared release signing config (same keystore alias). Required for the new signature-protected TugaSettings ContentProvider. SHA-1 cert `06ebb7ac36717017003232f471908c97d3407c1f` is now the ecosystem-wide fingerprint.
- Signing config switched from Groovy DSL property names (`isV1SigningEnabled` etc.) to AGP 8.2 Kotlin DSL (`enableV1Signing`/`enableV2Signing`/`enableV3Signing`) — all three schemes explicitly enabled.
- Lint check `UseAppTint` disabled at module level (workspace-wide lint surfaced pre-existing `android:tint` usage in layouts that pre-dates the refactor).
- Catalog now lists 6 entries (was 5): self + 5 utilities including TugaSettings.

### Removed
- Per-project `TugaStore/tugastore-release.jks` and `TugaStore/keystore.properties` — replaced by workspace `_signing/`.
- Duplicated `colors.xml` / `themes.xml` / common drawables that previously lived in each of the 5 utility apps (16 drawables + colors + themes per utility) — consolidated into `tuga-design`.

### Security
- Rotated release keystore password (was leaked via ChatGPT message logs / potential backups).
- TugaSettings ContentProvider is protected by `signature`-level custom permissions (`com.miktuga.permission.READ_SETTINGS` / `WRITE_SETTINGS`) **plus** an explicit `packageManager.checkSignatures(callerPackage, ownPackage)` inside every write — defense-in-depth in case manifest-level permission checks are bypassed.
- Feedback retry queue moved from shared external storage (`/sdcard/Tuga/feedback/`) to TugaStore-private `filesDir/feedback/`. Previously any app with WRITE_EXTERNAL_STORAGE could drop a `.json` file there and TugaStore would have POSTed it as first-party feedback on next retry; the new path is writable only by TugaStore itself.

## [0.2.0] - 2026-05-17

### Added
- Dashboard tile layout on `MainActivity` with Diagnostics / Catalog / Export tiles and bottom status bar (USB / Root / Storage / Android version / installed app count).
- `DiagnosticsActivity` reworked from monospace dump into card-based sections: System, Display, Memory, Storage, USB, Root, Connectivity, Sensors, Permissions, Packages — each with summary badge.
- `CatalogActivity` with `AutoCompleteTextView` search (history persisted in `SharedPreferences`, max 8 entries), filter chips (All / Installed / Available / Planned), sort menu (4 options), per-item status dots and colour-coded actions.
- `CatalogItem.resolveApk()` fallback chain: USB → `/data/local/tmp/apps/` → `/sdcard/apps/` → `/sdcard/Download/`.
- Report export from Diagnostics writes a plain-text dump to `getExternalFilesDir/reports/` and a duplicate copy to USB when mounted.
- Tuga ecosystem catalog (`assets/catalog.json`) listing 5 sibling apps: Tuga OBD, Tuga GPS, Tuga Media, Tuga Sync (+ self).
- Launcher icon: rounded transparent-corner ShopBag across 5 mipmap densities.
- Sibling utilities released alongside this version: **TugaOBD**, **TugaGPS**, **TugaMedia**, **TugaSync** — see each project's CHANGELOG for details.
- Release signing config in `app/build.gradle.kts` driven by `keystore.properties` + `tugastore-release.jks` (RSA 2048, 30-year validity, alias `tugastore`).
- Deployment package layout in `TugaStore-Release/` for FAT32 USB distribution to Geely Tugella head units.

### Changed
- All activities locked to `android:screenOrientation="landscape"` to match Tugella head unit hardware.
- Catalog buttons migrated from `<Button>` (which Material theme silently re-inflates as `MaterialButton` and tints) to `<androidx.appcompat.widget.AppCompatButton>` so custom drawables render correctly.

### Fixed
- Status bar on dashboard refreshes via `onResume` so newly installed sibling apps appear in the counter without restarting.

## [0.1.0] - 2026-04-01

### Added
- Initial MVP: standalone Android app store for Geely Tugella head unit (Android 5.1, API 22).
- Bundled APK install flow via `ApkInstaller` using `FileProvider` + `Intent.ACTION_VIEW`.
- Basic catalog loaded from `assets/catalog.json`.
- Initial diagnostics screen (monospace text dump of system properties).
- Kotlin 1.9.22 / AGP 8.2.2 / Gradle 8.5 toolchain pinned for API 22 compatibility.
- minSdk 22, targetSdk 28 with `ExpiredTargetSdkVersion` lint check disabled (intentional for Tugella).
- Package: `com.example.tugastore` (renamed to `com.miktuga.store` in a later Phase 1 refactor).
