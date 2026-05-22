# Tuga Store — CLAUDE.md

## Что это

Android-приложение для автомобильной головы Geely Tugella (Android 5.1). Собственный app store + диагностика устройства + экосистема из 5 утилит (OBD, GPS, Media, Sync, Settings). Без Google Play, но **есть WiFi** на голове — пользователи подключают её к домашней сети.

**Целевая аудитория:** community владельцев Geely Tugella и других авто на прошивке Geely Garage (рутованный Android 5.1). Под старый Android новый софт почти не выходит — TugaStore + утилиты закрывают эту нишу. Первая установка через USB-флешку, дальше обновления **по воздуху через miktuga.ru** (планируется в Phase 2/3 плана v3).

## Tech Stack

- **Язык:** Kotlin 1.9.22 (НЕ обновлять — несовместимо со старым Android)
- **UI:** XML layouts (без Compose — старый Android)
- **Min SDK:** 22 (Android 5.1)
- **Target SDK:** 28 (минимизация restrictions)
- **Сборка:** Gradle 8.5, AGP 8.2.2 (НЕ обновлять — может сломать совместимость с API 22)
- **Зависимости:** androidx.core, appcompat, material, constraintlayout
- **Package:** `com.miktuga.store` (был `com.example.tugastore` до v0.2.1)

## Workspace structure

С v0.2.1 — **единый workspace Gradle проект** в `<workspace>/`. До этого были 5 независимых проектов:

```
MikTuga/                       ← workspace root (Gradle multi-module)
├── settings.gradle.kts        ← включает :tuga-design + :tugastore + 5 утилит
├── build.gradle.kts           ← workspace-level
├── gradle.properties          ← android.nonTransitiveRClass=false (R-классы апок видят ресурсы tuga-design)
├── gradlew                    ← один wrapper на всё
├── _signing/                  ← общая keystore (gitignored)
│   ├── tuga-release.jks
│   └── keystore.properties
├── apps.yaml                  ← single source of truth для build-pipeline (id, package, version, main_activity)
├── release-manifest.json      ← генерируется scripts/verify-release.sh (gitignored)
├── scripts/                   ← build-all.sh, verify-release.sh, install-smoke.sh, _apps_lib.sh
├── tuga-design/               ← Android library: общие colors/themes/drawables + TugaSetting schema + FeedbackSubmitter
├── TugaStore/                 ← главное приложение (стор + диагностика + FeedbackActivity)
├── TugaOBD/                   ← Tuga OBD (com.miktuga.obd, demo dashboard)
├── TugaGPS/                   ← Tuga GPS (com.miktuga.gps, координаты + компас)
├── TugaMedia/                 ← Tuga Media (com.miktuga.media, скан USB)
├── TugaSync/                  ← Tuga Sync (com.miktuga.sync, USB↔устройство)
├── TugaSettings/              ← Tuga Settings (com.miktuga.settings, общие настройки + ContentProvider)
└── TugaStore-Release/         ← deployment-папка для флешки (gitignore)
```

Каждый под-проект — отдельный Gradle модуль (`:tugastore`, `:tugaobd`, ...), их `projectDir` указывает на `<App>/app`. Стек идентичный (Kotlin 1.9.22, AGP 8.2.2, minSdk 22, targetSdk 28). Design tokens (colors, themes, общие drawables) **больше не дублируются** — лежат в `tuga-design/` и подключаются через `implementation(project(":tuga-design"))`.

## Структура TugaStore

```
TugaStore/app/src/main/
├── java/com/miktuga/store/
│   ├── MainActivity.kt          # Главный dashboard + status bar + Feedback overflow menu + auto-retry feedback queue
│   ├── DiagnosticsActivity.kt   # Карточки-секции
│   ├── CatalogActivity.kt       # Поиск + chips + sort + AutoComplete history
│   ├── CatalogItem.kt           # Data class + resolveApk() с fallback paths
│   ├── ApkInstaller.kt          # PackageInstaller.Session (v0.2.1, было ACTION_VIEW)
│   ├── InstallResultReceiver.kt # BroadcastReceiver для PackageInstaller IntentSender
│   └── FeedbackActivity.kt      # Форма обратной связи (type/message/email/diagnostic checkbox)
├── res/                          # ТОЛЬКО app-specific resources (общие — в tuga-design)
│   ├── layout/                   # activity_main/diagnostics/catalog/feedback + item_catalog/diag_*
│   ├── values/strings.xml        # только TugaStore-specific строки
│   ├── drawable/                 # ic_more_vert (overflow), специфичные иконки
│   ├── mipmap-*/                 # launcher icon (ShopBag)
│   └── xml/file_paths.xml        # FileProvider paths
├── assets/catalog.json           # Каталог из 6 приложений (5 утилит + self)
└── AndroidManifest.xml           # Все activity landscape; FeedbackActivity exported для intent action com.miktuga.action.FEEDBACK
```

## Дизайн-система (общая для всех 6 апок, лежит в `tuga-design/`)

- **Тёмная тема:** background `#0B0F14`, surface `#11161D`, card `#1A2129`, border `#2A3340`
- **Текст:** primary `#FFFFFF`, secondary `#B0BEC5`, dim `#78909C`
- **Акценты:** blue `#2196F3`, teal `#26A69A`, purple `#AB47BC`
- **Статусы:** ok `#4CAF50`, warn `#FFA726`, error `#EF5350`
- **Шрифт:** sans-serif-light для крупных значений, monospace для координат
- **Кнопки:** rounded corners 12dp, `AppCompatButton` с custom background (НЕ `<Button>` под Material — там tint перекрывает!)
- **Базовая тема:** `Theme.TugaStore` (унаследовано всеми 6 апками через `parent="@style/Theme.TugaStore"` в собственных themes.xml)

## Сборка

### Все 6 апок сразу (через workspace Gradle)

```bash
export ANDROID_HOME=~/Library/Android/sdk
cd <workspace>

# Debug
./gradlew assembleDebug
# → каждое <App>/app/build/outputs/apk/debug/app-debug.apk

# Release (подписано общим release-key)
./gradlew assembleRelease
# → каждое <App>/app/build/outputs/apk/release/<appname>-release.apk
```

### Отдельный модуль

```bash
./gradlew :tugastore:assembleDebug
./gradlew :tugasettings:assembleRelease
```

### Через scripts/

```bash
scripts/build-all.sh              # debug, все 6 модулей
scripts/build-all.sh --release    # release
scripts/build-all.sh --clean      # с clean
scripts/verify-release.sh         # apksigner verify + cross-check apps.yaml + release-manifest.json
scripts/install-smoke.sh          # adb install/start/relaunch smoke test
```

### Подпись

Один **release keystore** на все 6 апок: `_signing/tuga-release.jks` + `_signing/keystore.properties` (оба gitignored). RSA 2048, validity 30 лет, alias `tugastore`. Все 6 апок собираются с **v1+v2+v3 signing schemes одновременно** (`enableV1Signing`/`enableV2Signing`/`enableV3Signing` в Kotlin DSL, не `isV1SigningEnabled`).

**Зачем общая подпись:** TugaSettings ContentProvider читается/пишется только из приложений с такой же подписью (`com.miktuga.permission.READ_SETTINGS` / `WRITE_SETTINGS` с `protectionLevel="signature"` + ручная проверка `checkSignatures()` в insert/update/delete).

**SHA-1 сертификата** (постоянный для всех release-сборок): `06ebb7ac36717017003232f471908c97d3407c1f`.

Lint-чек `ExpiredTargetSdkVersion` отключён (намеренно targetSdk=28). Также отключён `UseAppTint` — workspace lint цеплялся за pre-existing `android:tint` в layouts.

### Deployment-пакет для флешки

`<workspace>/TugaStore-Release/` — папка под FAT32-флешку:

```
TugaStore-Release/
├── README.md               # инструкция для конечного пользователя
├── TugaStore.apk           # ставится первым через ApkInstaller на голове
└── apps/                   # будут найдены TugaStore'ом в /storage/usbotg/usbotg-otg1/apps/
    ├── tugaobd.apk
    ├── tugagps.apk
    ├── tugamedia.apk
    ├── tugasync.apk
    └── tugasettings.apk
```

Размер ~30 МБ. Обновляется после `./gradlew assembleRelease` копированием 6 свежих APK:

```bash
cp TugaStore/app/build/outputs/apk/release/tugastore-release.apk TugaStore-Release/TugaStore.apk
for a in obd gps media sync settings; do
  cp Tuga${a^^}/app/build/outputs/apk/release/tuga${a}-release.apk TugaStore-Release/apps/tuga${a}.apk
done
```

(в bash 3.2 на Mac `${a^^}` не работает — используй полные пути или `tr '[:lower:]' '[:upper:]'`.)

## Локальная разработка на эмуляторе

Окружение: Mac на Apple Silicon, Android Studio Panda 4, SDK в `~/Library/Android/sdk`.

### AVD

- **Tugella_API22** — Nexus 7, Android 5.1.1 (API 22), ARM64, 1.5 ГБ RAM, GPU host. Для проверки на ОС близкой к настоящей Tugella.
- **Pixel7_API35** — Pixel 7, Android 15 (API 35), ARM64, Play Store. Для общей Android-разработки.

### Workflow

**В Android Studio:** выбрать в дропдауне `Tugella_API22` → ▶ Run.

**Через CLI:**
```bash
~/Library/Android/sdk/emulator/emulator -avd Tugella_API22 &
~/Library/Android/sdk/platform-tools/adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done

adb install -r TugaStore/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.miktuga.store/.MainActivity
```

### Установка дочерних APK на эмулятор

```bash
adb shell mkdir -p /data/local/tmp/apps
for a in obd gps media sync settings; do
  adb push Tuga${a}/app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/apps/tuga${a}.apk
done
```

`CatalogItem.resolveApk()` имеет fallback на `/data/local/tmp/apps/`, `/sdcard/apps/`, `/sdcard/Download/` — поэтому TugaStore их подхватит на эмуляторе, где USB-путь read-only.

### Гочи эмулятора на Apple Silicon

1. **`adb shell input tap X Y` может крашить эмулятор API 22 ARM64** (segfault при повторных вводах). Для автоматизированной навигации лучше `am start -n pkg/.Activity`.
2. **x86_64 образы не запускаются** на Apple Silicon (QEMU2 host limitation). Только ARM64.
3. **`hw.initialOrientation=landscape`** в config.ini может игнорироваться. У нас activities залочены `screenOrientation=landscape` в манифесте.
4. На API 22 текстовые флаги `am start --activity-clear-task` не работают. Используй int: `-f 268468224` (NEW_TASK | CLEAR_TASK).
5. **`<Button>` под `Theme.MaterialComponents` автоматически инфлейтится как MaterialButton** — его `backgroundTint` перекрывает custom drawable. Используй `<androidx.appcompat.widget.AppCompatButton>`.

### Скриншот через adb

```bash
adb exec-out screencap -p > screen.png
sips -r 270 screen.png --out screen_rotated.png  # framebuffer всегда в portrait
```

## Устройство (голова Tugella + Geely Garage прошивка)

- Android 5.1 (рутованный)
- USB-флешка монтируется в `/storage/usbotg/usbotg-otg1` (FAT32, ExFAT не читается)
- Встроенные ApkInstaller / Root Explorer
- Test-keys в Build.TAGS, root доступен
- **WiFi работает** — пользователи подключают голову к домашней сети
- HTTPS, POST/GET к публичным API работают (есть TLS support)
- Email-клиенты обычно НЕ установлены — `mailto:` интенты не работают (поэтому **Feedback использует HTTPS POST → fallback на TugaStore-private `filesDir/feedback/`**)
- Прошивка от Geely Garage популярна в community владельцев Tugella и других моделей Geely

## Правила разработки

- Без тяжёлых библиотек — устройство слабое
- XML layouts, не Compose
- Минимум permissions — только то что реально нужно
- **Offline-first, но WiFi доступен** — feature должна работать без сети, но online может улучшать (OTA, feedback, cloud sync)
- НЕ использовать `mailto:` — на голове нет email-клиента. Используй HTTPS POST к API или запись в private `filesDir/` для последующего sync (см. `FeedbackSubmitter` в `tuga-design`)
- APK для каталога кладутся на флешку в `/apps/`
- Версионирование: 0.x.0 (MVP стадия), versionCode = monotonic int
- Все activities `android:screenOrientation="landscape"` — у головы только landscape
- **Не обновлять Kotlin/AGP/Gradle** — стек зафиксирован под совместимость с API 22
- При создании нового компонента: design tokens из `tuga-design/src/main/res/values/colors.xml` (НЕ хардкодить hex), формы из общих drawables
- **Settings:** новые preferences добавляй в `tuga-design/.../settings/TugaSetting.kt` (sealed class) — типизированный get/set через `TugaSettingsClient`, бэкенд — TugaSettings ContentProvider
- **Установка APK:** только через `ApkInstaller.install()` (`PackageInstaller.Session`); НЕ использовать `Intent.ACTION_VIEW`

## Naming convention (вся экосистема)

Все наши приложения имеют префикс **Tuga**. Соглашение:

| Контекст | Стиль | Примеры |
|---|---|---|
| Имя директории и Gradle `rootProject.name` | без пробела | `TugaStore`, `TugaOBD`, `TugaGPS`, `TugaMedia`, `TugaSync`, `TugaSettings` |
| Gradle module path | lowercase | `:tugastore`, `:tugaobd`, `:tugasettings` |
| Display name в `strings.xml` / launcher label | с пробелом | "Tuga Store", "Tuga OBD", "Tuga Settings" |
| APK filename на флешке | lowercase, без разделителей | `tugaobd.apk`, `tugasettings.apk` |
| Package name (Kotlin / applicationId) | namespace под `miktuga` | `com.miktuga.store`, `com.miktuga.obd`, `.gps`, `.media`, `.sync`, `.settings` |
| Заголовки в каталоге (`catalog.json` `title`) | с пробелом | "Tuga OBD", "Tuga Settings" |

При создании новой утилиты придерживайся этих правил + добавь запись в `apps.yaml` (id/module/package/versions/main_activity/apk_filename/project_dir).

## SharedPreferences

- **TugaStore** использует `tugastore_prefs`: `sort` (enum имя сортировки), `search_history` (JSON array, max 8)
- **Общие настройки экосистемы** живут в `TugaSettings` через `SettingsProvider` (ContentProvider, signature-protected). Доступ — `TugaSettingsClient.get(ctx, TugaSetting.UnitsSpeed)` из `tuga-design`. На отсутствие провайдера / SecurityException — возвращает declared default.
- **Каждая апка-потребитель** обязана объявить обе `<uses-permission>` в своём `AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="com.miktuga.permission.READ_SETTINGS" />
  <uses-permission android:name="com.miktuga.permission.WRITE_SETTINGS" />
  ```
  Permissions объявлены в TugaSettings с `protectionLevel="signature"` — без `<uses-permission>` в апке-потребителе любой `get`/`set` молча сваливается в default. Порядок установки — TugaSettings первой (иначе остальным потребуется переустановка после её появления, чтобы Android грантанул signature-уровень).
- При создании новой утилиты — если нужны локальные prefs, используй `<утилита>_prefs`; если кросс-апковые — добавь enum-entry в `TugaSetting`.

## Деплой на голову (когда есть устройство)

1. Собрать release APK для всех 6 модулей: `./gradlew assembleRelease`
2. Запустить `scripts/verify-release.sh` — должно вывести 0 failures и одинаковый SHA-1 cert
3. Обновить `<workspace>/TugaStore-Release/` свежими APK (см. секцию Deployment выше)
4. Скопировать содержимое в корень FAT32-флешки
5. Вставить в голову → установить через ApkInstaller `TugaStore.apk`
6. Открыть Tuga Store → Каталог → нажать УСТАНОВИТЬ для каждой утилиты (рекомендуемый порядок: TugaSettings первой)

Подробности — в `<workspace>/TugaStore-Release/README.md`.

## Feedback flow

Реализовано в `tuga-design/src/main/java/com/miktuga/design/feedback/FeedbackSubmitter.kt` + `TugaStore/.../FeedbackActivity.kt`:

1. Пользователь жмёт overflow → "Обратная связь" в любом из 6 апок
2. В утилитах overflow → `FeedbackLauncher.launch()` запускает TugaStore-овую `FeedbackActivity` через explicit ComponentName (fallback на action intent `com.miktuga.action.FEEDBACK`)
3. Форма: type spinner / message / email / "приложить диагностику"
4. На submit:
   - Есть WiFi (`ConnectivityManager.activeNetworkInfo`)? → POST на `https://miktuga.ru/api/feedback` (5s connect/read timeout)
   - Нет сети / POST провалился → JSON в TugaStore-private `filesDir/feedback/<yyyyMMdd_HHmmss>_<ms%10000>.json` = `/data/data/com.miktuga.store/files/feedback/...` (suffix защищает от коллизий; private storage не даёт другим апкам подкинуть свой payload в очередь)
5. Toast: SENT/QUEUED/FAILED — три ветки
6. `MainActivity.onResume()` в TugaStore вызывает `FeedbackSubmitter.retryPending()` — листит `.json`, POSTит, удаляет на 2xx

Endpoint `miktuga.ru/api/feedback` ещё не запущен (Phase 2). Пока всё копится в TugaStore-private `filesDir/feedback/`.

## Что отличается в v0.2.1 от v0.2.0 (Phase 1 refactor)

Если откатываешь к v0.2.0 — учти:
- Packages были `com.example.tugastore.*` → стали `com.miktuga.*`
- 5 независимых Gradle проектов → 1 workspace + библиотека `tuga-design`
- Дублированные `colors.xml` / `themes.xml` / общие drawables → переехали в `tuga-design`
- Keystore был в `TugaStore/tugastore-release.jks` → `_signing/tuga-release.jks` (общий для всех 6)
- Только TugaStore был release-signed → теперь все 6
- `ApkInstaller` использовал `Intent.ACTION_VIEW` → `PackageInstaller.Session` с broadcast callback
- Не было feedback flow / overflow menus → есть в каждой апке
- Не было TugaSettings → есть как 6-я апка + ContentProvider + typed schema в `tuga-design`
