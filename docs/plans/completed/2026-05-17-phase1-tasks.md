# Tuga Ecosystem Phase 1 — Executable Tasks

**Context:** Refactoring workspace at `<workspace>/` containing 5 Android Gradle projects (TugaStore, TugaOBD, TugaGPS, TugaMedia, TugaSync). Target: Geely Tugella head unit, Android 5.1 (API 22), targetSdk 28. See `2026-05-17-tuga-ecosystem-expansion.md` for full plan context.

**Stack:** Kotlin 1.9.22, AGP 8.2.2, Gradle 8.5. **DO NOT update versions.**

**Pre-existing state (already done):**
- Workspace `.gitignore` created with secrets protection
- Initial commit on `main` branch made

### Task 1: Rotate keystore password

The keystore at `TugaStore/tugastore-release.jks` was generated earlier with a password that was in plain text in ChatGPT messages and might be in backups. Rotate it. (Original password scrubbed from this doc before publication.)

Steps:
- [x] Generate new strong random password (20+ chars alphanumeric). Generated 28-char alphanumeric password via /dev/urandom.
- [x] Use `keytool -storepasswd -keystore TugaStore/tugastore-release.jks` and `keytool -keypasswd -alias tugastore -keystore TugaStore/tugastore-release.jks` to change BOTH store password AND key password to the new value. Note: keystore is PKCS12 format which unifies store and key passwords — `-storepasswd` covers both, and `-keypasswd` is unsupported for PKCS12.
- [x] Update `TugaStore/keystore.properties` with the new password.
- [x] Verify keystore.properties is NOT staged: `git status --ignored | grep keystore.properties` should show ignored. Both `TugaStore/keystore.properties` and `TugaStore/tugastore-release.jks` confirmed in ignored list.
- [x] Build a release APK and verify with `apksigner verify --print-certs`. `./gradlew assembleRelease` BUILD SUCCESSFUL; `apksigner verify` exit 0; certificate SHA-1 06ebb7ac36717017003232f471908c97d3407c1f.

### Task 2: Rename packages from com.example.tugastore.* to com.miktuga.*

Critical to do BEFORE first community release. Mapping:
- `com.example.tugastore` → `com.miktuga.store`
- `com.example.tugastore.obd` → `com.miktuga.obd`
- `com.example.tugastore.gps` → `com.miktuga.gps`
- `com.example.tugastore.media` → `com.miktuga.media`
- `com.example.tugastore.sync` → `com.miktuga.sync`

For each of the 5 projects:
- [x] Update `applicationId` and `namespace` in `app/build.gradle.kts`. All 5 app/build.gradle.kts updated: TugaStore→com.miktuga.store, TugaOBD→com.miktuga.obd, TugaGPS→com.miktuga.gps, TugaMedia→com.miktuga.media, TugaSync→com.miktuga.sync.
- [x] Move `app/src/main/java/com/example/tugastore[/x]/` to `app/src/main/java/com/miktuga/[x]/`. Used `git mv` to preserve history; empty `com/example` directories removed.
- [x] Update `package` declaration in all .kt files inside that directory. 9 .kt files updated across all 5 projects.
- [x] Update `TugaStore/app/src/main/assets/catalog.json` — all `packageName` fields use new naming. 5 packageName entries rewritten.
- [x] `./gradlew clean assembleDebug` — must succeed. All 5 apps BUILD SUCCESSFUL; `aapt dump badging` confirms applicationIds com.miktuga.{store,obd,gps,media,sync}.

### Task 3: Move to workspace-level Gradle multi-module

Restructure 5 separate Gradle projects into ONE workspace project with shared library `tuga-design`.

Steps:
- [x] Create `tuga-design/` Android library module at workspace root with `com.android.library` plugin. Module created at `<workspace>/tuga-design/` with namespace `com.miktuga.design`, minSdk 22, api dependencies on appcompat/material/core-ktx.
- [x] Move common resources from `TugaStore/app/src/main/res/` to `tuga-design/src/main/res/`:
   - `values/colors.xml`
   - `values/themes.xml` (base theme `Theme.TugaStore` — apps extend it)
   - `drawable/card_background.xml`, `tile_background.xml`, `icon_circle_*.xml`, `status_dot_*.xml`, `button_filled_*.xml`, `button_disabled.xml`, common `ic_*.xml` (storage, android, usb, shield)
   Moved via `git mv` to preserve history; 16 drawables + colors.xml + themes.xml relocated.
- [x] Create workspace-level `settings.gradle.kts` that includes:
   - `:tuga-design`
   - `:tugastore` (root project of TugaStore renamed as nested module)
   - `:tugaobd`, `:tugagps`, `:tugamedia`, `:tugasync`
   Workspace settings.gradle.kts maps each app's projectDir to `<App>/app`. Also added workspace `build.gradle.kts`, `gradle.properties` (with `android.nonTransitiveRClass=false` so app R-classes still include library resources), and gradle wrapper at workspace root.
- [x] Each consumer `app/build.gradle.kts`: add `implementation(project(":tuga-design"))`. All 5 apps updated. TugaStore keystore path adjusted to read from `../keystore.properties` since rootProject is now the workspace.
- [x] Delete duplicated resources from 5 consumer apps (after migration to tuga-design verified working). 4 consumer apps (TugaOBD/TugaGPS/TugaMedia/TugaSync) had 16 drawables + colors.xml + themes.xml removed each (`ic_usb.xml` only from TugaMedia/TugaSync where it existed); only app-specific drawables and strings.xml remain per app.
- [x] `./gradlew build` at workspace root must succeed. BUILD SUCCESSFUL — 469 tasks ran; all 5 debug APKs produced. Required disabling lint check `UseAppTint` (pre-existing `android:tint` usage in layouts surfaced by lint at workspace build).

### Task 4: Unify signing config across all 5 apps

- [x] Create `_signing/` directory at workspace root. Created at `<workspace>/_signing/`; already covered by workspace `.gitignore` (line 18: `_signing/`).
- [x] Move `TugaStore/tugastore-release.jks` → `_signing/tuga-release.jks`. Used `mv` (file is gitignored so `git mv` not applicable).
- [x] Move `TugaStore/keystore.properties` → `_signing/keystore.properties`. Updated `storeFile` value to `_signing/tuga-release.jks` (path relative to workspace root).
- [x] Update path references in `TugaStore/app/build.gradle.kts` to read from `_signing/`. Switched from `project.file("../keystore.properties")` to `rootProject.file("_signing/keystore.properties")`, and from `project.file("../" + storeFile)` to `rootProject.file(storeFile)`.
- [x] Add same signingConfigs block to `TugaOBD`, `TugaGPS`, `TugaMedia`, `TugaSync` `app/build.gradle.kts`. All 4 utility apps now import `java.util.Properties`, load `_signing/keystore.properties`, define identical `signingConfigs.release`, and wire `signingConfig = signingConfigs.findByName("release")` in `buildTypes.release`.
- [x] **Explicitly enable v1+v2+v3 signing in every signingConfig:** Used AGP 8.2 Kotlin DSL property names (`enableV1Signing`/`enableV2Signing`/`enableV3Signing`) instead of plan's `isV1SigningEnabled` etc. — verified all three schemes are active in produced APKs.
- [x] `./gradlew assembleRelease` — all 5 APKs must produce. BUILD SUCCESSFUL in 3s; 241 tasks. APKs at `<App>/app/build/outputs/apk/release/tuga{store,obd,gps,media,sync}-release.apk`.
- [x] `apksigner verify --print-certs` on each — must show same SHA-1 fingerprint. All 5 verify v1+v2+v3=true; identical SHA-1 `06ebb7ac36717017003232f471908c97d3407c1f`, SHA-256 `dc20c7a3446d5a234f4c669865f87e2e4f6ce8e937b3318d3d4579d79f5ea43b`, DN `CN=Tuga Store, OU=Personal, O=MikTuga, L=Unknown, ST=Unknown, C=RU`.

### Task 5: Add CHANGELOG.md to every project

Create `CHANGELOG.md` in each of 5 project roots (TugaStore, TugaOBD, TugaGPS, TugaMedia, TugaSync) using Keep a Changelog format with versions 0.1.0 + 0.2.0 entries. Include:
- TugaStore: full history (v0.1.0 base MVP, v0.2.0 redesign + 4 utilities)
- Others: v0.1.0 entry only

Steps:
- [x] Create `TugaStore/CHANGELOG.md` with v0.1.0 + v0.2.0 entries. Keep a Changelog 1.1.0 format; v0.2.0 covers dashboard rework, card-based DiagnosticsActivity, CatalogActivity (search history + chips + sort), `CatalogItem.resolveApk()` fallback chain, sibling utilities bundle, signing config; v0.1.0 covers the pre-git MVP baseline.
- [x] Create `TugaOBD/CHANGELOG.md` with v0.1.0 entry. Demo dashboard, sin-wave metrics, DTC bar, stub ПОДКЛЮЧИТЬ button, landscape lock, `com.miktuga.obd` package, shared `_signing` keystore.
- [x] Create `TugaGPS/CHANGELOG.md` with v0.1.0 entry. LocationManager (GPS+Network), speed/altitude/accuracy, last-known fallback, TYPE_ROTATION_VECTOR compass with declination correction, monospace coords.
- [x] Create `TugaMedia/CHANGELOG.md` with v0.1.0 entry. USB scan (mp3/m4a/flac/ogg/wav + mp4/mkv/avi/webm/mov), filter chips, `/sdcard/Music` fallback, ACTION_VIEW handoff to stock player.
- [x] Create `TugaSync/CHANGELOG.md` with v0.1.0 entry. Two profiles (Music / Reports), bidirectional USB↔Device, AsyncTask with skip-if-same-size, progress file count.

### Task 6: Create TugaSettings as 6th app

New Android project at `<workspace>/TugaSettings/` (or as new module if migrated to workspace Gradle in Task 3). Package `com.miktuga.settings`.

Features:
- [x] UI: list of settings with INLINE controls (NO dialog-per-row):
   - Units speed (km/h ⟷ mph) — segmented buttons in row
   - Units temp (°C ⟷ °F) — segmented buttons
   - Units distance (m ⟷ ft) — segmented buttons
   - USB mount path — folder picker browsing `/storage/`
   - Music folder — folder picker
   - Reports folder — folder picker
   - Auto-update check — switch
   - Feedback email field REMOVED (baked at build time)
   `SettingsActivity` renders three section cards (Units/Paths/System); each unit row uses `AppCompatButton` pairs styled via `segment_selected.xml`/`segment_unselected.xml`. Path rows show monospace current value + inline "Изменить" button launching `FolderPickerActivity` (file browser starting at `/storage/`). Auto-update is a `SwitchCompat` row. No dialogs.
- [x] Storage: ContentProvider in TugaSettings with:
   - Custom permissions `com.miktuga.permission.READ_SETTINGS` and `WRITE_SETTINGS`, both `protectionLevel="signature"`
   - Inside update/insert: additional `packageManager.checkSignatures(callingPackage, "com.miktuga.settings")` defense
   `SettingsProvider` declares both permissions in `AndroidManifest.xml`, wires them as `readPermission`/`writePermission` on the provider tag, and `enforceCallerIsTugaSigned()` runs `checkSignatures` on every insert/update/delete (skipped only when caller == own package). Backed by SharedPreferences `tugasettings_store`. `aapt dump badging` confirms both permissions present.
- [x] In tuga-design library: typed schema
   ```kotlin
   sealed class TugaSetting<T>(val key: String, val default: T) {
       data object UnitsSpeed : TugaSetting<UnitsSpeedValue>("units_speed", UnitsSpeedValue.KMH)
       data object UnitsTemp : TugaSetting<UnitsTempValue>("units_temp", UnitsTempValue.CELSIUS)
       // ...
   }
   ```
   Plus `TugaSettingsClient.get(ctx, setting: TugaSetting<T>): T` typed accessor.
   Implemented at `tuga-design/src/main/java/com/miktuga/design/settings/TugaSetting.kt` (sealed class with 7 entries: UnitsSpeed/UnitsTemp/UnitsDistance/UsbMountPath/MusicFolder/ReportsFolder/AutoUpdateCheck plus `UnitsSpeedValue`/`UnitsTempValue`/`UnitsDistanceValue` enums). `TugaSettingsClient.kt` provides typed `get`/`set` over `content://com.miktuga.settings.provider/settings`, returning declared default on missing-provider / SecurityException so consumers never have to handle the empty-provider case.
- [x] Add `tuga-settings` entry to `TugaStore/app/src/main/assets/catalog.json`. Added as 6th entry with id `tuga-settings`, packageName `com.miktuga.settings`, version `0.1.0`, apkPath `/storage/usbotg/usbotg-otg1/apps/tugasettings.apk`.

### Task 7: Add Feedback to TugaStore + menu in other apps

In TugaStore: new `FeedbackActivity` with form (type spinner, message text, optional email field, attach diagnostic checkbox). On submit:
- [x] Build JSON `{app, version, type, message, email?, diagnostic?}`. `FeedbackPayload` in `tuga-design/.../feedback/FeedbackSubmitter.kt` serializes via `JSONObject` and adds an extra `timestamp` (ms epoch); `email`/`diagnostic` are omitted when blank.
- [x] Check WiFi: try HTTPS POST to `https://miktuga.ru/api/feedback` (endpoint TBD in Phase 2 — for now, just log network error gracefully). `FeedbackSubmitter.hasNetwork()` reads `ConnectivityManager.activeNetworkInfo`; on success `tryPost()` does HttpsURLConnection POST (5s connect/read timeouts); errors logged at WARN and swallowed.
- [x] If network fails OR no WiFi: save JSON to TugaStore-private `filesDir/feedback/{timestamp}.json`. `writeToQueue()` creates `<context.filesDir>/feedback/<yyyyMMdd_HHmmss>_<ms%10000>.json` (suffix prevents collisions when two submissions fall in the same second). Plan originally said `/sdcard/Tuga/feedback/` but was moved to private storage during Phase 1 review — utility apps invoke TugaStore's FeedbackActivity via intent, so only TugaStore ever queues, and shared storage was a vector for other apps to plant payloads that we'd later upload.
- [x] Show toast "Сообщение сохранено, отправим когда появится сеть". `FeedbackActivity.submit()` dispatches on the main-thread callback: SENT → "Сообщение отправлено, спасибо", QUEUED → "Сообщение сохранено, отправим когда появится сеть", FAILED → "Не удалось отправить или сохранить" + button re-enabled.
- [x] On TugaStore startup with WiFi: scan TugaStore-private `filesDir/feedback/` and retry POST for pending. `MainActivity.onResume()` calls `FeedbackSubmitter.retryPending()` which checks network, lists `.json` files, POSTs each, deletes on 2xx.
- [x] In other 4 apps: simpler overflow menu item "Обратная связь" → launches TugaStore's FeedbackActivity via intent (or duplicate minimal flow if cross-app intent complexity). Added `ic_more_vert` overflow ImageButton to each utility's header (OBD/GPS/Media/Sync); click opens `PopupMenu` with single "Обратная связь" item that calls `FeedbackLauncher.launch(this, packageName, versionName)`. Launcher tries explicit ComponentName first, action intent fallback, toast on resolve failure. TugaStore's `FeedbackActivity` is exported with intent-filter on action `com.miktuga.action.FEEDBACK`. Build passes for all 5 apps; v1+v2+v3 release signatures verified.

### Task 8: Replace ApkInstaller.install with PackageInstaller.Session

Current `TugaStore/app/src/main/java/com/miktuga/store/ApkInstaller.kt` (after rename in Task 2) uses `Intent.ACTION_VIEW` which is fire-and-forget. Replace with `PackageInstaller.Session` API (available API 21+):
- [x] Create session, write APK bytes to session. `ApkInstaller.install()` now calls `packageManager.packageInstaller.createSession(MODE_FULL_INSTALL)` with `setSize(file.length())`, then streams the APK into `session.openWrite("apk", 0, length)` with a 64 KB buffer and `session.fsync()`.
- [x] Commit with IntentSender — receives install result callback. Build broadcast `PendingIntent` for action `com.miktuga.store.INSTALL_RESULT` (sessionId as request code; `FLAG_UPDATE_CURRENT` + `FLAG_MUTABLE` on API 31+); `session.commit(pending.intentSender)`. New top-level `InstallResultReceiver` registered in `AndroidManifest.xml` (`android:exported="false"`) handles the callback.
- [x] Show status: "Устанавливается…" then "Установлено" or specific error message. Caller shows "Устанавливается…" toast on dispatch. Receiver handles `STATUS_PENDING_USER_ACTION` (launches `EXTRA_INTENT` with `FLAG_ACTIVITY_NEW_TASK`), `STATUS_SUCCESS` ("<app> · установлено"), and per-code failure toasts (`STATUS_FAILURE_*` → "Конфликт версий" / "APK несовместим с устройством" / "Повреждённый APK" / "Недостаточно места" / etc.). `CatalogActivity` passes the catalog title as the label. Workspace `./gradlew assembleRelease` BUILD SUCCESSFUL; `apksigner verify` confirms v1+v2+v3=true and SHA-1 fingerprint unchanged (`06ebb7ac36717017003232f471908c97d3407c1f`).

### Task 9: Create build automation scripts

In workspace root, create `scripts/` directory:

Steps:
- [x] `scripts/build-all.sh` (bash 3.2 compatible): reads `apps.yaml`, runs `./gradlew :{module}:assembleDebug` (or `assembleRelease` with `--release`) per app, collects failures and prints PASSED/FAILED summary. Supports `--clean`. Case conversion via `tr '[:lower:]' '[:upper:]'`, no associative arrays.
- [x] `scripts/verify-release.sh`: runs `apksigner verify --verbose --print-certs` per release APK; pulls SHA-1 cert from per-scheme lines (`V3.0 Signer: certificate SHA-1 digest: ...`); computes SHA-256 via `shasum -a 256`; reads `versionCode`/`versionName`/`package` via `aapt dump badging`; cross-checks against `apps.yaml` and fails on any drift; first APK's cert SHA-1 becomes the reference, subsequent APKs must match. Writes workspace-root `release-manifest.json`. Verified live: all 6 APKs pass with shared SHA-1 `06ebb7ac36717017003232f471908c97d3407c1f`, all v1+v2+v3=true.
- [x] `scripts/install-smoke.sh`: resolves `adb` via PATH/`ANDROID_HOME`/`~/Library/Android/sdk`; for each app: `adb uninstall` → `adb install -r` (clean) → `am start -n <pkg>/<main_activity>` → wait up to 8s for `pidof` (with `ps | grep` fallback for pre-API-23 toybox) → `install -r` again to exercise the update flow → relaunch → wait for new PID. Per-app failure tracking + summary. Supports `--debug` to install `app-debug.apk` instead.
- [x] `apps.yaml` at workspace root: flat-YAML manifest listing all 6 apps with `id`, `module`, `package`, `version_name`, `version_code`, `main_activity` (`.SettingsActivity` for tugasettings, `.MainActivity` for others), `apk_filename`, `project_dir`. Parsed by shared `scripts/_apps_lib.sh` (bash 3.2 regex parser into parallel indexed arrays). `release-manifest.json` is git-ignored as a generated artifact.

### Task 10: Update deployment package + docs

- [x] Rebuild all 6 APK (release, signed with rotated keystore). `./gradlew :tugastore:assembleRelease :tugaobd:assembleRelease :tugagps:assembleRelease :tugamedia:assembleRelease :tugasync:assembleRelease :tugasettings:assembleRelease` BUILD SUCCESSFUL; TugaStore bumped to versionCode 3 / versionName 0.2.1, utilities remain 0.1.0/vc1.
- [x] Update `TugaStore-Release/`: `TugaStore.apk` at root + `apps/{tugaobd,tugagps,tugamedia,tugasync,tugasettings}.apk` (6 APKs total, all release-signed; ~30 MB).
- [x] Update `TugaStore-Release/README.md`: added TugaSettings to package list + features section + recommended-first install order; documented `PackageInstaller.Session` flow; added "Обратная связь" section explaining online POST → fallback to TugaStore-private `filesDir/feedback/<ts>.json` → auto-retry on TugaStore onResume; added troubleshooting entries for cross-signed conflict and Tuga Settings not propagating.
- [x] Update `TugaStore/CLAUDE.md` — rewritten to reflect: workspace Gradle multi-module structure (single root + `:tuga-design` library + 6 app modules), `com.miktuga.*` package namespace, `_signing/` shared keystore + v1+v2+v3 + AGP 8.2 Kotlin DSL property names, `PackageInstaller.Session` install path, `tuga-design` location for design tokens + `TugaSetting` schema + `FeedbackSubmitter`, build scripts, and a v0.2.0→v0.2.1 migration delta section.
- [x] Update `TugaStore/README.md` — rewritten with 6-app ecosystem table, workspace build commands, deployment refresh recipe, Feedback flow summary, version history including v0.2.1 entry, and a pointer to the new workspace-level README.
- [x] Create workspace-level `README.md` at `<workspace>/README.md` — entry point covering rationale, all 7 modules (6 apps + `:tuga-design`), tech stack, full workspace layout, build/scripts/keystore/deploy/emulator workflows, cross-app shared state (`TugaSettingsClient` + `FeedbackSubmitter`), contribution rules, and docs map.
- [x] Update `CHANGELOG.md` in TugaStore: added `[0.2.1] - 2026-05-17` entry covering FeedbackActivity + retry queue, overflow menu, InstallResultReceiver, TugaSettings catalog entry, workspace-level README; Changed section covering package rename, ApkInstaller→Session rewrite, workspace Gradle migration, resource consolidation into tuga-design, keystore move + rotation, unified ecosystem-wide signing, AGP 8.2 Kotlin DSL property names, lint UseAppTint disable, 6-entry catalog; Removed section (per-project keystore, duplicated resources); Security section (rotated keystore, signature-protected ContentProvider + checkSignatures defense-in-depth).
- [x] `apps.yaml` synced with TugaStore 0.2.1/vc3; `scripts/verify-release.sh` rerun → 0 failures, all 6 APKs share SHA-1 `06ebb7ac36717017003232f471908c97d3407c1f`, v1+v2+v3=true. `git ls-files | grep -iE "keystore|jks|p12|pem"` returns empty — no secrets tracked.
- [x] Final commit on `tuga-ecosystem-expansion` branch with summary message. (handled by this commit)

### Acceptance for Phase 1

All these must work:
- `./gradlew build` at workspace root — builds all 6 apps in one command
- All 6 APKs signed with same fingerprint (v1+v2+v3)
- TugaSettings: changing km/h → mph reflects in TugaOBD/TugaGPS speed display
- Feedback in any app: saves to TugaStore-private `filesDir/feedback/` if no WiFi
- TugaStore catalog still works (5/6 installed, search/filter/sort intact)
- No secrets in `git ls-files`
- `apksigner verify` clean for all 6
- `TugaStore-Release/` updated with 6 APKs ready for USB deployment
