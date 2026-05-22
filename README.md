# Tuga Store — экосистема для Geely Tugella и Geely Garage

Self-hosted app store + диагностика + 5 утилит для автомобильной головы на прошивке Geely Garage (рутованный Android 5.1). Первая установка через USB-флешку, дальше обновления по воздуху через **miktuga.ru** (Phase 2/3, в разработке). Целевая аудитория — community владельцев Tugella и других авто на этой прошивке, для которых под старый Android новый софт почти не выпускают.

> Этот README — про модуль `:tugastore`. Workspace-уровень (как устроены все 6 апок вместе, как их собрать одной командой) — см. `<workspace>/README.md`.

## Что в экосистеме

| Приложение | Пакет | Что делает |
|---|---|---|
| **Tuga Store** | `com.miktuga.store` | Стор + расширенная диагностика устройства + форма обратной связи |
| **Tuga OBD** | `com.miktuga.obd` | Дашборд OBD-II (скорость, обороты, температура, топливо) |
| **Tuga GPS** | `com.miktuga.gps` | GPS-координаты, скорость, высота, компас |
| **Tuga Media** | `com.miktuga.media` | Сканер музыки и видео на USB → системный плеер |
| **Tuga Sync** | `com.miktuga.sync` | Синхронизация файлов между флешкой и устройством |
| **Tuga Settings** | `com.miktuga.settings` | Общие настройки экосистемы (единицы, пути) + signature-protected ContentProvider |

Workspace: `<workspace>/` — единый Gradle multi-module проект с библиотекой `tuga-design` (общие colors/themes/drawables + `TugaSetting` schema + `FeedbackSubmitter`).

## Tech Stack

- Kotlin 1.9.22 + XML layouts (не Compose, для совместимости с Android 5.1)
- Min SDK 22 (Android 5.1, Lollipop), Target SDK 28
- Gradle 8.5, AGP 8.2.2
- AndroidX AppCompat + Material Components 1.11.0
- Без тяжёлых зависимостей

## TugaStore features

- **Главный экран** — dashboard с 3 tile (Диагностика, Каталог, Экспорт) + status bar (USB, Root, Storage, Android version) + overflow menu с "Обратной связью"
- **Диагностика** — 10+ секций в карточках: системная информация, дисплей, память, хранилище, USB mount points, root detection, связь, сенсоры, permissions, установленные пакеты
- **Каталог** — поиск с историей, фильтры по статусу (Все/Установлены/Доступны/Запланированы), 4 варианта сортировки, карточки с цветовой кодировкой
- **Установка через PackageInstaller.Session** — silent install в фоне с системным подтверждением (v0.2.1, было `Intent.ACTION_VIEW`)
- **Обратная связь** — `FeedbackActivity` с формой (type/message/email/diagnostic); POST на `miktuga.ru/api/feedback` если есть WiFi, fallback на TugaStore-private `filesDir/feedback/<ts>.json`; авто-ретрай очереди при следующем onResume
- **Экспорт отчёта** — текстовая выгрузка диагностики на USB-флешку
- **Кастомная иконка** — shopping bag с сеткой приложений + MikTuga брендинг
- **Тёмная тема** с навигационным акцентом

## Сборка

### Workspace (все 6 апок одной командой)

```bash
export ANDROID_HOME=~/Library/Android/sdk
cd <workspace>

./gradlew assembleDebug      # все 6 debug
./gradlew assembleRelease    # все 6 release
```

### Только TugaStore

```bash
cd <workspace>
./gradlew :tugastore:assembleDebug
# → TugaStore/app/build/outputs/apk/debug/app-debug.apk

./gradlew :tugastore:assembleRelease
# → TugaStore/app/build/outputs/apk/release/tugastore-release.apk
```

### Подпись (release)

Общий keystore для всех 6 апок: `_signing/tuga-release.jks` + `_signing/keystore.properties` (оба gitignored, в workspace root). RSA 2048, alias `tugastore`. Все 6 апок собираются с v1+v2+v3 schemes одновременно.

**SHA-1 release-сертификата:** `e2ded6293acc1541ffd8962b3a28a69d3835bbd0` (одинаков для всех 6 апок — required для signature-protected ContentProvider TugaSettings).

### Через scripts/

```bash
scripts/build-all.sh --release   # все 6 release
scripts/verify-release.sh        # apksigner + cross-check apps.yaml → release-manifest.json
scripts/install-smoke.sh         # adb install + start + relaunch на подключённом устройстве
```

## Локальное тестирование на эмуляторе

На Mac (Apple Silicon):

```bash
sdkmanager "system-images;android-22;google_apis;arm64-v8a"
avdmanager create avd -n Tugella_API22 \
  -k "system-images;android-22;google_apis;arm64-v8a" -d "Nexus 7"

emulator -avd Tugella_API22 &
adb install -r TugaStore/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.miktuga.store/.MainActivity
```

Альтернатива: открыть workspace в Android Studio → выбрать `Tugella_API22` → ▶ Run.

**Известные ограничения:**
- На Apple Silicon работает только ARM64-образ (x86_64 не поддерживается)
- API 22 ARM64 эмулятор может крашиться при repeated `adb shell input tap` — для UI-навигации используй `am start` или интерактивно через окно
- При локальном тесте каталога: `adb push tugaobd.apk /data/local/tmp/apps/tugaobd.apk` — TugaStore их найдёт через fallback paths в `CatalogItem.resolveApk()`

## Установка на реальную голову Tugella

Готовый deployment-пакет в `<workspace>/TugaStore-Release/` (~30 МБ):

```
TugaStore-Release/
├── README.md            ← инструкция для конечного пользователя
├── TugaStore.apk        ← ставится первым через ApkInstaller на голове
└── apps/                ← TugaStore найдёт по /storage/usbotg/usbotg-otg1/apps/
    ├── tugaobd.apk
    ├── tugagps.apk
    ├── tugamedia.apk
    ├── tugasync.apk
    └── tugasettings.apk
```

Скопировать содержимое в корень **FAT32**-флешки → вставить в голову → установить `TugaStore.apk` → открыть Tuga Store → Каталог → УСТАНОВИТЬ для каждой утилиты. Рекомендуемый порядок: TugaSettings первой (создаёт shared ContentProvider).

Подробно: см. `TugaStore-Release/README.md`.

## Структура каталога

`TugaStore/app/src/main/assets/catalog.json` — список приложений:

```json
{
  "id": "tuga-obd",
  "title": "Tuga OBD",
  "description": "Модуль для OBD-метрик, ошибок и телеметрии.",
  "packageName": "com.miktuga.obd",
  "version": "0.1.0",
  "apkPath": "/storage/usbotg/usbotg-otg1/apps/tugaobd.apk"
}
```

Логика статусов:
- Пакет установлен → "v0.x.0 · установлено" + кнопка **ОТКРЫТЬ** (teal)
- APK найден на USB / fallback paths → "APK найден · v0.x.0" + **УСТАНОВИТЬ** (orange)
- apkPath пустой → "Запланировано" + неактивная "—"
- apkPath есть, файла нет → "APK не найден" + неактивная "—"

## Обратная связь

`FeedbackActivity` в TugaStore + overflow menu пункт "Обратная связь" в 4 утилитах (OBD/GPS/Media/Sync), которые запускают TugaStore-овую активность через explicit ComponentName (fallback на action intent `com.miktuga.action.FEEDBACK`).

Логика отправки (`tuga-design/.../feedback/FeedbackSubmitter.kt`):
1. Сериализация `{app, version, type, message, email?, diagnostic?, timestamp}` через `JSONObject`
2. Если WiFi есть → `HttpsURLConnection.POST` на `https://miktuga.ru/api/feedback` (5s timeouts)
3. Если нет / failed → TugaStore-private `filesDir/feedback/<yyyyMMdd_HHmmss>_<ms%10000>.json` (`/data/data/com.miktuga.store/files/feedback/`)
4. Toast: SENT / QUEUED / FAILED — три ветки
5. `MainActivity.onResume()` вызывает `retryPending()` — листит `.json`, POSTит каждый, удаляет на 2xx

Endpoint `miktuga.ru/api/feedback` запустится в Phase 2.

## Naming convention

Все приложения экосистемы имеют префикс **Tuga**:

| Контекст | Стиль | Примеры |
|---|---|---|
| Имя директории / Gradle name | без пробела | `TugaStore`, `TugaOBD`, `TugaSettings` |
| Display name | с пробелом | "Tuga Store", "Tuga OBD" |
| APK filename | lowercase | `tugaobd.apk`, `tugasettings.apk` |
| Package name | под `com.miktuga.*` | `com.miktuga.obd`, `.gps`, `.media`, `.sync`, `.settings` |

## Версии

- **v0.1.0** — базовый MVP (главная, диагностика, каталог)
- **v0.2.0** — Install/Open кнопки, расширенная диагностика, USB-установка, переименование Car App Store → Tuga Store, кастомная иконка, release-сборка с подписью, dashboard UI redesign (tiles + cards), 4 утилиты (OBD/GPS/Media/Sync), поиск + filter chips + sort в каталоге, deployment-пакет для флешки
- **v0.2.1** — Phase 1 ecosystem refactor: rename `com.example.tugastore.*` → `com.miktuga.*`, workspace Gradle multi-module + библиотека `tuga-design`, unified signing на 6 апок, 6-я апка **TugaSettings** + signature-protected ContentProvider + typed `TugaSetting` schema, `FeedbackActivity` + auto-retry queue, replace `Intent.ACTION_VIEW` install → `PackageInstaller.Session`, build automation scripts + `apps.yaml`
- **v0.3.0** (планируется) — лендинг miktuga.ru, OTA-обновления по WiFi, серверный feedback endpoint, реальный OBD через ELM327

## Контакт

MikTuga (GitHub Issues), 2026.
