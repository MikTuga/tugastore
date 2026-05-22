# План развития экосистемы Tuga (v3)

**Дата:** 2026-05-17  
**Статус:** v3 — переработано после уточнения контекста от пользователя  
**Цель:** Закрыть нишу "под старый Android (5.1) ничего нового не выходит" для community владельцев Geely Tugella и других авто на прошивке Geely Garage. Доставка через USB первый раз, дальше — обновления по WiFi.

## Текущий статус

| Phase | Статус | Дата |
|---|---|---|
| **Phase 1A** — Workspace refactor (бренд + signing + cross-app settings + feedback) | ✅ **DONE** | 2026-05-17 |
| **Phase 1B** — Multi-repo split (7 GitHub repos + JitPack lib) | ✅ **DONE** | 2026-05-23 |
| **Phase 2** — miktuga.ru + signed manifest API + feedback backend | ⏳ В очереди | TBD |
| **Phase 3** — OTA-клиент в TugaStore | ⏳ В очереди | TBD |

### Phase 1A итоги (через /ralphex за 2h 37m, монорепо `tuga-ecosystem` на ветке `tuga-ecosystem-expansion`)

- ✅ Workspace-level Gradle multi-module (один `./gradlew build` на 6 апок)
- ✅ Все пакеты `com.example.*` → `com.miktuga.*`
- ✅ Keystore rotated (новый password), v1+v2+v3 signing, single SHA-1 `e2ded6293acc1541ffd8962b3a28a69d3835bbd0`
- ✅ Shared design library `tuga-design` (colors/themes/drawables/TugaSetting schema/FeedbackSubmitter)
- ✅ TugaSettings (6-я утилита), inline UI с segmented buttons
- ✅ Feedback flow: HTTPS POST → fallback в TugaStore-private filesDir/feedback/ → auto-retry на startup с WiFi
- ✅ `PackageInstaller.Session` вместо `Intent.ACTION_VIEW`
- ✅ TugaStore bumped to v0.2.1, 5 утилит v0.1.0
- ✅ 5 раундов code review (Claude + Codex), все findings зафиксены или обоснованы

### Phase 1B итоги (multi-repo split за ~3h, 2026-05-23)

После решения "community-maintainable apps + discoverability" монорепо разнесён на 7 публичных GitHub-репо:

- ✅ `MikTuga/tuga-design` — JitPack-published Android library (`com.github.miktuga:tuga-design:0.1.0`)
- ✅ `MikTuga/tugastore` — main app, v0.2.1, central под MikTuga keystore
- ✅ `MikTuga/tugasettings` — settings ContentProvider, **permission downgraded** `signature` → `normal` (v0.1.1) чтобы community apps работали без shared keystore
- ✅ `MikTuga/tugaobd` / `tugagps` / `tugamedia` / `tugasync` — все v0.1.0, community-maintainable
- ✅ Все 6 consumer apps зависят от tuga-design через JitPack, build standalone success
- ✅ TugaStore + TugaSettings локальные keystore-копии в их репо (gitignored), общий SHA-1 цертификат
- ✅ Per-app .gitignore с secret protection
- ✅ Workspace остаётся для локальной dev convenience (7 папок рядом, push per repo)
- ✅ Author email во всех commits — GitHub noreply (`285482067+MikTuga@users.noreply.github.com`)
- ✅ Backup монорепо .git → `/tmp/tuga-workspace-monorepo-git-*.tar.gz`

## Контекст (важно)

- **Целевая аудитория:** community Tugella + Geely Garage (рутованный Android 5.1). Не personal-only проект.
- **WiFi на голове работает** — пользователи подключают к домашней сети. HTTPS, REST API работают.
- **Email-клиентов на голове обычно нет** — `mailto:` не работает.
- **Обновления планируются частыми** — поэтому OTA это core, не оверкилл.
- **Бренд MikTuga** — важен для репутации в community.

## Что было в плане v1 и v2

- **v1** — первая версия, написана исходя из предположения "personal project"
- **v2** — учла критику /codex (security, ContentProvider, etc.)
- **v3** — учла **community context** (юзеров много, WiFi работает) + 10 технических исправлений от subagent review

## Изменения от v2

| Тема | v2 говорил | v3 говорит |
|---|---|---|
| Phase 2 (miktuga.ru) | "может overkill" | **Core. Канал доставки обновлений и discovery для community.** |
| Phase 3 (OTA) | "сначала проверить нужно ли" | **Core. Юзеры обновляются по WiFi, без USB каждый раз.** |
| Feedback (mailto) | "достаточно для MVP" | **Не работает на голове.** HTTPS POST к API + offline fallback на /sdcard |
| Composite build + Maven coords | "сложный гибрид" | **Workspace-level Gradle.** Один `settings.gradle.kts` в корне, все 6 модулей + library, `implementation(project(":tuga-design"))` |
| TugaSettings UI | "dialog per row" | **Inline controls.** Переключатели и кнопки прямо в строке, без модальных окон (driver-friendly) |
| Cross-app settings | "ContentProvider" | **ContentProvider + writePermission + signature check + типизированная схема (sealed class)** |
| APK signature на API 22 | "одинаковый SHA-1" | **v1+v2+v3 явно включены** + bouncycastle для ручной верификации сигнатуры |
| OTA download | "скачать APK по URL" | **Atomic: .part file → SHA-256 + cert verify → rename → PackageInstaller.Session с callback** |
| Phase 1 время | "~6h" | **22-30h реально** (3-4 рабочих дня) |
| Keystore password | "хранится в keystore.properties" | **Ротировать** перед публикацией (старый был в моих сообщениях ChatGPT) |

## Out of scope

- Real OBD-II BT-связь (отдельный план, v0.3.x)
- GPX-трек запись (отдельный план)
- Multi-vehicle / multi-user accounts на сайте
- Аналитика (Sentry, GA) — позже, когда будет реальный трафик
- CI/CD на GitHub Actions — минимальное в Phase 2

## Фазы

### Phase 1 — Подготовка (22-30 часов, эта сессия)

Внутренний рефакторинг + foundation для OTA. Без сайта.

#### 1A. Git + проверка безопасности (2h)
1. `git init` на корне `<workspace>/`
2. Workspace `.gitignore` (jks, keystore.properties, build/, _signing/, TugaStore-Release/)
3. **`apps.yaml`** — single source of truth для всех 6 приложений (id, package, версия, путь, fingerprint)
4. **Перед `git commit`:** `git ls-files | grep -E '\.(jks|keystore)$'` → должно быть пусто
5. **Ротировать пароль keystore** (новый случайный)
6. Initial commit

#### 1B. Переименование пакетов `com.example.*` → `com.miktuga.*` (2h)
Критично сделать ДО первой публикации в community — потом не сменишь без потери update path.

- 5 пакетов: `tugastore`, `tugastore.obd`, `tugastore.gps`, `tugastore.media`, `tugastore.sync` → `com.miktuga.store`, `com.miktuga.obd`, и т.д.
- Обновить `applicationId`, `namespace`, переместить Kotlin packages, обновить catalog.json
- Uninstall старых версий с эмулятора, reinstall новых

#### 1C. Workspace-level Gradle + общая библиотека дизайна (4h)
**Один Gradle workspace** вместо 6 отдельных проектов. Один `settings.gradle.kts` в корне:

```
MikTuga/
├── settings.gradle.kts        ← include(":tuga-design", ":tugastore", ":tugaobd", ...)
├── build.gradle.kts (root)
├── tuga-design/               ← Android library с цветами/темой/иконками
│   ├── build.gradle.kts
│   └── src/main/...
├── TugaStore/app/             ← подключает: implementation(project(":tuga-design"))
├── TugaOBD/app/
└── ...
```

Преимущества:
- `./gradlew build` собирает всё одной командой
- Никаких Maven coords fiction, никаких composite build гимнастик
- `keystore.properties.example` для новых разработчиков
- Скрипт `bootstrap.sh` для setup

#### 1D. Единая подпись для всех 6 приложений (1h)
- `_signing/tuga-release.jks` + `keystore.properties` (gitignored)
- В каждом app build.gradle.kts: signingConfig из общего файла
- **Явно `v1SigningEnabled = true; v2SigningEnabled = true; v3SigningEnabled = true`** — иначе на Android 5.1 верификация подписи может вернуть null

#### 1E. CHANGELOG + бренд update (2h)
- `CHANGELOG.md` в каждом из 6 проектов (Keep a Changelog format)
- README.md на корне workspace (entry point для новых разработчиков)
- В каждом приложении: footer с MikTuga + версией + ссылкой на CHANGELOG

#### 1F. TugaSettings — общие настройки (5h)
6-я утилита для общих настроек экосистемы.

UI:
- Список настроек с **inline controls** (НЕ dialogs!)
- Enum (km/h vs mph): segmented control в строке
- Boolean (auto-update): switch в строке
- Папка (USB path, Music): folder picker (browser по /storage/)
- Каждая строка показывает текущее значение

Хранилище:
- ContentProvider в TugaSettings (signature-level permission)
- **READ + WRITE permissions** (не только READ)
- Внутри `update/insert`: дополнительная signature проверка
- **Типизированная схема** в tuga-design: `sealed class TugaSetting<T>` — компиляторная проверка ключей и типов
- Все остальные Tuga apps читают через `TugaSettingsClient.get(ctx, TugaSetting.UnitsSpeed)` — никаких magic strings

#### 1G. Feedback в каждой апке (3h)
**НЕ mailto** (не работает на голове). Вместо этого:

- В каждом приложении: меню "Обратная связь" → форма (тема + описание + email опционально + чекбокс "приложить диагностику")
- При тапе "Отправить":
  - Если WiFi есть → HTTPS POST на `https://miktuga.ru/api/feedback` (в Phase 2 endpoint появится; пока — fallback)
  - Если нет сети → сохранить JSON в `/sdcard/Tuga/feedback/<timestamp>.json`
  - При следующем запуске с WiFi → автоматически отправить накопленные

#### 1H. Update update-flow в каталоге TugaStore (3h)
Подготовка к OTA (без backend пока — заглушка):

- В каталог-карточке: бейдж "обновление доступно: v0.2.1 → v0.2.2"
- На главной странице tile "Каталог": красная точка если есть обновления
- Кнопка "Обновить всё" в каталоге если >2 апдейтов
- Логика: TugaStore проверяет `miktuga.ru/release-manifest.json` (Phase 2) — пока заглушка возвращает пустой manifest

#### 1I. PackageInstaller.Session API для установки (2h)
Заменить текущий `Intent.ACTION_VIEW` на `PackageInstaller.Session`:
- Даёт callback об успехе/ошибке (текущий fire-and-forget)
- Прогресс установки можно показать
- На Android 5.1 API 21+ работает

#### 1J. install-smoke.sh + verify-release.sh (2h)
Скрипты на bash 3.2 совместимости (Apple ships 3.2).
- `build-all.sh` — собирает все 6 APK debug/release
- `verify-release.sh` — apksigner verify + SHA-256 + fingerprint check для всех
- `install-smoke.sh` — push на эмулятор, запустить, проверить что не упало (через pidof, не dumpsys)
- Failures collected, summary в конце

#### 1K. Обновить deployment package + docs (2h)
- `TugaStore-Release/` обновить с 6 APK (включая TugaSettings)
- `TugaStore-Release/README.md` обновить инструкцию (упомянуть feedback flow, OTA в будущем)
- `TugaStore/CLAUDE.md` (уже частично обновлён)
- Workspace `README.md` для разработчиков

**Acceptance Phase 1:**
- `./gradlew build` на корне — все 6 APK подписаны одним ключом
- `install-smoke.sh` — все 6 устанавливаются и стартуют на эмуляторе
- TugaSettings — изменение km/h → mph в нём отражается в TugaOBD/TugaGPS
- Feedback в любой апке — успешно записывает в /sdcard/Tuga/feedback/
- Каталог показывает заглушку "обновлений нет" (без crash при отсутствии manifest)
- Git tagged `v0.2.1`

### Phase 2 — Сайт + backend (10-12 часов, следующая сессия)

#### 2.1 Домен и хостинг
- Купить `miktuga.ru` (~₽500/год)
- Cloudflare nameservers
- Cloudflare Pages для статики, Workers для API

#### 2.2 Лендинг miktuga.ru
**Информационная иерархия (главное наверху):**
- Hero: "Tuga Store — приложения для головы Geely Tugella" + скриншот UI на реальной голове + кнопка "Скачать USB-образ"
- Mid: 6 утилит с описаниями и скриншотами
- Bottom: для разработчиков (GitHub, changelog, API)
- Юридический disclaimer "unofficial, для community владельцев"

#### 2.3 OTA Manifest API
`https://miktuga.ru/api/manifest.json` — список последних версий + URL для скачивания. Подписан Ed25519 ключом (private хранится offline).

```json
{
  "version": 1,
  "apps": [
    {
      "package": "com.miktuga.store",
      "versionName": "0.2.1",
      "versionCode": 3,
      "url": "https://github.com/MikTuga/releases/.../tugastore.apk",
      "sha256": "abc...",
      "size": 7100000,
      "minSdk": 22,
      "changelog": "..."
    }
  ],
  "signed_at": "2026-05-17T12:00:00Z"
}
```

И отдельный файл `release-manifest.sig` с подписью.

#### 2.4 APK хостинг (multi-repo coordination)
**GitHub Releases per repo** (бесплатно, public URLs). Каждый из 6 app repos имеет свои releases.

```
github.com/MikTuga/tugastore/releases/download/v0.2.1/tugastore-release.apk
github.com/MikTuga/tugaobd/releases/download/v0.1.0/tugaobd-release.apk
github.com/MikTuga/tugagps/releases/download/v0.1.0/tugagps-release.apk
github.com/MikTuga/tugamedia/releases/download/v0.1.0/tugamedia-release.apk
github.com/MikTuga/tugasync/releases/download/v0.1.0/tugasync-release.apk
github.com/MikTuga/tugasettings/releases/download/v0.1.1/tugasettings-release.apk
```

Manifest API агрегирует ссылки на свежие release assets через GitHub API.

**Release pipeline per app (manual для MVP):**
1. Bump versionName + versionCode в `app/build.gradle.kts`
2. Update CHANGELOG.md
3. Build release APK (только TugaStore + TugaSettings — community repos подписывают своими ключами)
4. `gh release create vX.Y.Z --notes "..." app/build/outputs/apk/release/*.apk`
5. Manifest builder (Cloudflare Worker или GitHub Action) подхватит новый release

#### 2.5 Feedback API
`POST /api/feedback`:
- Принимает JSON, валидирует
- Cloudflare Turnstile (защита от спама)
- Rate limit 5 запросов/час с IP
- Сохраняет в Cloudflare D1 (SQLite)
- Отправляет уведомление в Telegram bot / на team-email (адрес настраивается в Worker env, не хардкодится в коде)

#### 2.6 Crash reporting
В каждом Tuga app: `Thread.setDefaultUncaughtExceptionHandler` пишет stack trace в `/sdcard/Tuga/crashes/`. TugaStore при запуске с WiFi отправляет на `/api/crash`.

### Phase 3 — OTA-клиент в TugaStore (6-8 часов, следующая сессия)

#### 3.1 Manifest polling
- При запуске TugaStore (если WiFi доступен): GET `https://miktuga.ru/api/manifest.json` + `release-manifest.sig`
- Verify Ed25519 signature через bouncycastle (pinned public key в коде)
- Compare versionCodes vs installed
- Бейдж в UI если есть обновления

#### 3.2 Atomic download + verify + install
1. Download APK в `tugaobd.apk.part`
2. SHA-256 verify против значения из подписанного manifest
3. Extract signing cert через `getPackageArchiveInfo` (с проверкой что pi != null — если null, abort и log)
4. Cert fingerprint check:
   - **Central apps** (TugaStore, TugaSettings): pinned expected SHA-1 `e2ded629...`. Failure → abort.
   - **Community apps** (OBD/GPS/Media/Sync): сравнить с уже установленной версией. Если совпадает (continuity) → ok. Если первая установка → trust the manifest's published cert SHA-1 (хранится в manifest entry).
5. Compare versionCode (новый > установленный)
6. Rename `.part` → final
7. `PackageInstaller.Session.commit()` с callback

**Threat model:** Ed25519-signed manifest = root of trust. SHA-256 + cert fingerprint = defense-in-depth. Multi-maintainer reality: community apps могут иметь разные cert fingerprints (community-published versions), но manifest указывает который ожидать для текущего release.

#### 3.3 Auto-update toggle + offline cache
- TugaSettings ключ `auto_update_check: bool`
- Manifest cache в SharedPreferences, fallback при недоступности сети
- "Обновлений недоступны — проверьте сеть" если cache stale

#### 3.4 Rollback
- Каждый release tagged в Git
- `scripts/rollback-release.sh <version>` переписывает manifest указывая старую версию
- Пользователь с bad APK: переустановить вручную с USB образа

## Acceptance

### Phase 1
- [ ] `git init` + initial commit без secrets
- [ ] Все 6 пакетов `com.miktuga.*`
- [ ] Workspace-level Gradle, `./gradlew build` собирает всё
- [ ] Все 6 APK подписаны одним ключом (v1+v2+v3)
- [ ] TugaSettings inline UI, типизированная схема, ContentProvider с write protection
- [ ] Feedback в каждой апке, сохраняет на /sdcard если нет сети
- [ ] PackageInstaller.Session используется вместо ACTION_VIEW
- [ ] CHANGELOG.md в каждом проекте
- [ ] Deployment package обновлён
- [ ] `install-smoke.sh` зелёный для всех 6
- [ ] Git tagged `v0.2.1`

### Phase 2
- [ ] miktuga.ru разрешается, статика работает
- [ ] /api/manifest.json + /api/manifest.sig доступны по HTTPS
- [ ] /api/feedback принимает POST, сохраняет в D1, шлёт email
- [ ] APK на GitHub Releases

### Phase 3
- [ ] TugaStore при WiFi проверяет manifest, signature verify работает
- [ ] Download + SHA-256 + cert fingerprint verify работают
- [ ] Update flow: установлен v0.2.1 → manifest v0.2.2 → бейдж → tap Обновить → новая версия установлена
- [ ] Offline fallback работает
- [ ] Rollback документирован

## Решения пользователя (2026-05-17)

1. **Цель релиза:** после всех 3 фаз (~3 недели работы). Сначала Phase 1 локально, потом 2+3, потом анонс в community.
2. **Git:** пока локально, без push на GitHub. Через 2-3 фазы — публичный GitHub repo для community.
3. **Каналы distribution:** Telegram канал/чат + Geely Garage партнёрство + drive2.ru пост + 4pda ветка. Лендинг miktuga.ru — гейтвей со ссылками на эти 4 канала.
4. **Keystore security:** ротировать пароль СЕЙЧАС в рамках Phase 1A. Backup нового keystore в 2 места (LastPass + физический носитель).

Дополнительно из решений:
- TugaSettings будет показывать "Каналы поддержки" — Telegram, Geely Garage, drive2 ссылки
- Phase 2 лендинг должен включать "Где обсуждать" блок с 4 каналами
- Feedback API в Phase 2 шлёт уведомление в Telegram bot (не только email)
