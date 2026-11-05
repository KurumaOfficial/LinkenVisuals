# HANDOFF — эстафета между чатами

> Методология: этот файл обновляется в конце КАЖДОЙ задачи. Следующий чат читает его первым и продолжает с «Следующей задачи». Детали решений — `MERGE_DECISIONS.md`, скоуп — `ROADMAP.md`.
>
> **SCOPE DECISION 2026-10-24:** полный **Punchy** (181 класс / 62 261 строка), включая Punchy-зависимый `ViewModel Animated`, **не портируем**. Это не открытый остаток, а явно исключённый объём по решению пользователя. `HandMotion` (walk/sprint/water/fall/attack) — финальный принятый subset.

## Состояние на 2026-11-05 (D122 — фикс экрана подключения + фон 4K) — v1.0.150

### Экран загрузки/подключения
Корень чёрного экрана: donor `LinkenConnectRenderer` рисует через matrix-stack overload'ы `Render2D`, но не вызывал `Render2D.prepareHudFrame(context)`. В 1.21.11 `Render2D.activeContext` не заполняется сам, поэтому `drawFont`/`drawTexture`/`drawRoundedRect` молча возвращались: оставался только фон/тост «Подключение...». Исправление — `Render2D.prepareHudFrame(context)` + `LinkGuiRenderContext.resetFade()` в начале `LinkenConnectRenderer.render`. Проверено на dev-превью: фон башни, логотип, часы, центральная карточка со спиннером, сервер, прогресс, статус и «Отмена» рисуются.

### Фон главного меню
Заменил обе копии (`mainmenu/bg-tower.png`, `textures/linkmenu/bg-tower.png`) на присланный апскейл **5080×3996** (10 988 784 B, RGB, точный aspect 1270:999). Константы `1270×999` оставлены как design-размер cover (пропорции совпадают). Проверен кадр 2560×1440 (`run/screenshots/visual-menu-late.png`).

### Проверки
- `test`: 83 suites / 325 tests, 0 fail/error/skip.
- Jar **1.0.150**: `54 556 473 B`, SHA-256 `F9FDFDA6EEC1BE028DED4F9679F60A776DF50E38D03D510F2E6F5A78520560F2`.
- Запуск 1.21.11: `linkenvisuals 1.0.150`, `Initialized successfully. Active Style: Linken`, ошибок нет.
- Connect preview: `run/screenshots/visual-connect-preview.png` — фон, логотип, часы, карточка, спиннер, сервер, статус, прогресс, «Отмена».

## Состояние на 2026-11-05 (D121 — переключатель свечения GUI) — v1.0.149

- В donor `UI` добавлена настройка `settings.ui.glow` (default **true**), общий флаг `dev.linkenvisuals.render.UiGlow`.
- Тумблер выведен в шапку секции **Интерфейс** правой панели LinkGUI: `GUI GLOW` + switch. Настройка также автоматически видна в окне модуля UI и сохраняется в конфиг.
- При OFF отключаются: `ClickGuiFirePass` (анимированный кант), мягкие halo-обводки donor/port LinkGUI (`glow`, `glowCircle`, `tileGlow`), `LinkGuiChrome.drawPanelGlow/Ring`, `WindowMaterial` radial/sheen. Рамки панелей и акцентные заливки остаются.
- Проверено визуально: ON — тонкий контур, OFF — свечение полностью отсутствует (скриншоты `visual-clickgui.png` до/после переключения).
- Версия **1.0.149**, jar задеплоен в `D:\MultiMC\instances\1.21.111\.minecraft\mods\linkenvisuals-1.0.149.jar`.
- Фон главного меню: оригинал **1270×999** передан заказчику (`C:\Users\gameg\Desktop\LinkenVisuals-bg-tower-original-1270x999.png`) для апскейла; целевой размер — 4× (5080×3996) с сохранением пропорций 1270:999, PNG, без кропа/перерисовки. После получения файла заменить `mainmenu/bg-tower.png` и `textures/linkmenu/bg-tower.png`.

## Состояние на 2026-11-05 (D120 — quality-pass 1.0.147 → 1.0.148)

Заказчик прислал скриншоты 1.0.147: «меню сильно светится», «беграунд не качественный», «текст welcome не качественный». Все три пункта воспроизведены на 2560×1440 и исправлены; отдельно проведён аудит merge-сборки.

### Сделано
1. **Cursive Welcome.** `MainMenuWelcomeAnim` и donor `CursiveWelcome` теперь растеризуют путь в **3×** (1296×384 / 1284×369) и используют `LinearTexture` с `FilterMode.LINEAR` для min/mag. Дефолтный `AbstractTexture` в 1.21.11 ставит **NEAREST** на minification — именно он давал рваные края при downscale. В port-анимацию также добавлен donor layer offset `(210.962,63.445)−(217.377,69.099)`.
2. **Пересвет LinkGUI.** `ClickGuiFirePass`: halo 20→10, `edgeAlpha` 0.85→0.22, `bodyAlpha` 0.18→0.05. В donor/port `LinkGuiScreen` добавлен `GLOW_SCALE=0.55`. `LinkGuiChrome`: panel glow 22/0.18→14/0.10, ring 0.22→0.10, glass border 0.9/0.25→0.55/0.15, card outline 0.5→0.32. `WindowMaterial`: radial 28→12, sheen 18→9.
3. **Фон меню.** Donor `LinkGuiScreen.drawVignette` и port `drawBackdrop` больше не зовут vanilla `context.applyBlur()`. Используется `HudGlassCapture.refresh()` (1/2→1/4→1/8, LINEAR) + 9-tap `GlassRenderer`/`MenuChrome.drawScreenBackground`; сверху мягкий вертикальный veil. `MenuChrome.drawScreenBackground` теперь сам обновляет capture — экраны, открытые поверх мира (в т.ч. connect), не сэмплируют пустую/устаревшую текстуру (причина чёрного фона).
4. **Accessor-миксины.** В `dev.linkenvisuals.mixins.json` возвращены безопасные accessor'ы (`CreateWorldScreenAccessor`, `TextFieldWidgetAccessor`, `MinecraftClientAccessor`, `OptionsScreenAccessor`, `GameOptionsScreenAccessor`, `SelectWorldScreenAccessor`, `HandledScreenAccessor`, `FogRendererAccessor`). Без них Create World / Alt Manager падали с `IllegalClassLoadError`/ClassCastException. Behavior-миксины порта сознательно не включались: donor-first merge рендерит donor HUD/экраны, включение всех порт-миксинов даёт двойной HUD и конфликт routing.

### Проверки
- `test`: **83 suites / 325 tests**, 0 fail/error/skip.
- Визуальные кадры 2560×1440: `run/screenshots/visual-menu-welcome.png` (полное cursive «Welcome»), `visual-menu-late.png` (main menu), `visual-clickgui.png` (Rose, pinned World/StreamerMode/Particles), `visual-clickgui-late.png`.
- Autotest 1.0.147 до правок: 1085 шагов, 1069 ok / 16 fail. После accessor-фикса: **1071 ok / 14 fail / 0 skip** (1085 шагов); Create World-краши ушли.

### Остаток (не визуальные баги merge)
Autotest после accessor-фикса всё ещё показывает провалы **port-only** проверок: HUD preview (Target/Coordinates/Cooldowns/Armor/Inventory/LowArmor/CustomVitals/KillStreak/HelperItems), SelfNametags, KillStreak tracking, alternative pause route, ItemRadius labels. Причина — в 1.0.147 активен donor HUD и отключены behavior-миксины порта (`InGameHudMixin`, `MinecraftClientMixin`, `PlayerEntityMixin` и т.д.). Это отдельный скоуп: включать их «вслепую» нельзя, будет двойной HUD/конфликт экранов.

## Состояние на 2026-10-24 (D119 — встроенные optimization-моды, Fabric Jar-in-Jar) — v1.0.140

### Сделано
1. **Папка `source/optimization-mods/`**: сюда кладутся release-джарники оптимизации. Gradle-задача `prepareOptimizationJars` копирует каждый `*.jar` в `META-INF/jars/` внутри финального jar; `processResources` добавляет соответствующие записи в `jars` массива `fabric.mod.json`. Исходный `fabric.mod.json` остаётся валидным JSON (`"jars": []`), поэтому Loom не ругается на парсинг.
2. **Загрузка**: это штатный Fabric Jar-in-Jar. Fabric Loader сам находит nested-моды, применяет их mixins/entrypoints и зависимости; в логе запуска они видны как обычные моды.
3. **Проверка `verifyEmbeddedOptimizationJars`** (входит в `check`): каждый jar из папки есть в финальном jar, объявлен в `fabric.mod.json`, содержит валидный `fabric.mod.json` с id; duplicate mod id (включая `linkenvisuals`) валит сборку.
4. **Тестовый jar**: `optimization-mods/linkenvisuals-test-optimization-1.0.0.jar`, metadata-only, id `linkenvisuals_test_optimization`, без кода/mixins. В логе: `Loading 50 mods: ... \-- linkenvisuals_test_optimization 1.0.0`.
5. **README** в папке: только release-джарники, лицензия должна разрешать redistribution, нельзя два мода с одинаковым id. Реальные оптимизаторы (Sodium/Lithium и т.п.) не добавлялись: их версия/лицензия/совместимость с 1.21.11 — отдельное решение пользователя.

### Проверки
- 82 набора / 324 теста, 0 fail/error/skip в обоих flavor.
- Linken jar: `linkenvisuals-1.0.140.jar`, **11 197 037 байт**, SHA-256 `E236C35996C8064215570469AB1C19F0D42AF7F8F9084C7994BC77148EDCD679`; Strange jar: **11 197 023 байта**, SHA-256 `D27C9321BB0E7AE985896A16D21C6B295E10D0EAC29AA4192F9DC5E408774636`.
- `verifyEmbeddedOptimizationJars`: `embedded linkenvisuals_test_optimization 1.0.0`, `verified 1 embedded optimization jar(s)`.
- Запуск 1.21.11: Fabric Loader загрузил 50 модов, включая nested test mod; `Sound engine started`, ERROR/exception нет.
- Evidence: `run/screenshots/evidence-d119/` — `jar-contents.txt`, `launch-log.txt`.
- Commits: code `8b193b2`; docs `docs commit follows`.

### Следующая задача
- Добавлять реальные optimization-моды только по приказу пользователя, с проверкой лицензии и совместимости с 1.21.11.
- Помнить: nested-моды нельзя обновлять отдельно от LinkenVisuals; при обновлении jar пересобрать и развернуть.

## Состояние на 2026-10-24 (D118 — pixel-parity главного меню, splash bootstrap, язык игры) — v1.0.140

### Сделано
1. **Главное меню, вертикальные метрики**: `LinkenMenuLayout` использует donor line boxes, а не номинальные кегли: eyebrow 13.02, subtitle 17.64, disclaimer 20.79. Стек кнопок встал ровно на donor `y=524/583/642` (было 517/576/635); размеры/ширины не менялись. Регрессия закреплена в существующем `LinkenMenuLayoutTest` (счётчик тестов остался 324).
2. **MSDF-метрики**: `MsdfText.write` добавляет donor advance `+0.025 em` (половина SDF-толщины 0.05). Headline/subtitle/кнопки совпали по ширине: head1 369 vs 369, head2 363 vs 365, subtitle 186 vs 188 px. Wordmark оставлен на per-glyph `drawSpaced`-пути и сохранил donor bbox `924..995 × 49..66`.
3. **Welcome**: убрана ellipsize-обрезка второй строки; `__Kuruma__` в ru_ru больше не превращается в `__Kurum...`.
4. **Фон**: sub-pixel cover через translate+scale и один horizontal-gradient quad вместо 48 полос. Background center diff 1.62 mean / p95 5; после компенсации 8 px parallax — 1.48 / p95 6.
5. **Splash bootstrap**: портирован donor `SplashFont` (CPU MSDF → `NativeImage` → GUI texture). Первый кадр теперь содержит wordmark, VISUALS, stage, percent и footer; до правки был почти пустой чёрный экран с уголками и полосой. Текстуры освобождаются в `LinkenLoadingScreen.dispose()` при снятии overlay.
6. **Splash → menu**: `LinkenMainMenuScreen` больше не пропускает кадр при `overlay != null`, если `Pipelines.resourcesReady()`; меню рисуется под затухающим splash.
7. **Язык игры — источник истины**: `I18n.getEffectiveLanguage()` при `ru*` всегда возвращает `ru_ru`, независимо от сохранённого `language`. Для не-русского клиента — английский fallback; явные `ru_ru`/`uk_ua` остаются для Classic GUI. `ConfigManager` мигрирует старый `language: en_us` в `auto` и больше не пишет `en_us`. Проверено: game `ru_ru` + config `en_us` → русский UI; game `en_us` + config `auto` → английский UI.
8. **Остаток**: одиночный pure-black кадр при старте совпадает с переключением окна в fullscreen (engine clear); классифицирован D, не UI. Bootstrap до/после — `evidence-d118/loading-bootstrap-before.png` / `...-after.png`.

### Проверки
- 82 набора / 324 теста, 0 fail/error/skip в обоих flavor.
- Linken autotest **1158/1158**, 0 fail/skip, 0 новых ERROR/exception.
- Strange autotest **1148/1148**, 0 fail/skip; 3 ERROR — только offline-auth/Realms.
- Numeric diff ru_ru 1920×1080/guiScale 2: full frame mean 3.13, p95 9, >16 3.53%; buttons 5.15; account 4.93; wordmark 2.86; background 1.62. Evidence: `run/screenshots/evidence-d118/` (`mainmenu-donor.png`, `mainmenu-port-after.png`, `mainmenu-ui-compare.png`, `lang-ru-auto.png`, `lang-en-auto.png`, `metrics.txt`).
- Jar hash: `linkenvisuals-1.0.140.jar` — `1B43021B068B63D987124D6A8069F4B7167123B3CD2E7686697C11081FEA5F86`; `linkenvisuals-strange-1.0.140.jar` — `9F17C7B4F494B6B8CEDE9E841B32E06FFB8828DB1336FD204296153C4C54BC44`.
- Commits: code `928eb1d`, docs `docs commit follows`.

### Следующая задача
- Pause: donor 1.21.4 показывает динамические плитки из `GameMenuScreen` (Accounts/Feedback/Report Bugs/Statistics), порт сознательно оставляет компактный набор; не добавлять контролы без приказа. Сверить только геометрию/цвета.
- LinkGUI: donor screenshot снят в другой теме/наборе модулей; для pixel-diff выставить одинаковую тему и фильтр.
- Остаток типографики: eyebrow 188 vs 194 px (per-glyph 1.05 measurement donor); не менять глобально без проверки полей ввода.

## Состояние на 2026-10-24 (D117b — возврат прежнего LV-логотипа, SDF-края) — v1.0.140

### Сделано
1. **Логотип возвращён**: D117-геометрический LV отменён по приказу пользователя. `lv_logo.png` 512×512 и `logo.png` 120×120 восстановлены из `6c6919a` (рисунок не менялся); это тот же LV-монограмм, что был до D117.
2. **Качество краёв**: оба исходника прогнаны через SDF-мастер 2048×2048 — premultiplied Lanczos upscale, бинарный контур (alpha ≥ 0.5), Euclidean distance transform, coverage с 1 px antialiasing на целевом размере. Alpha MAE к оригиналу: 512 — 0.0021, 120 — 0.0044; пикселей с изменением alpha > 0.25 — 0.10% / 0.00%. Форма/рисунок не менялись.
3. **Малые размеры**: `LinkenMenuChrome.drawLogo` при `size <= 128` берёт 120 px `logo.png` (UV 120), выше — 512 px `lv_logo.png` (UV 512). Сейчас единственные вызовы — LinkGUI header 24 px и footer 12 px, поэтому фактически используется 120 px; ветка 512 остаётся для крупных вызовов. Причина: 512→16–24 px — ~21× minification без mipmap, дававшая мягкость/шиммер.
4. **Проверки**: **82 набора / 324 теста**, 0 fail/error/skip (Linken `build` и Strange `build`); Linken autotest **1158/1158** (1920×1080, 0 fail/skip, 0 новых ERROR/exception, прогон после D117b); Strange **1148/1148** (прогон D117; D117b меняет только PNG и ветку `drawLogo`, покрытую unit-тестами; 3 ERROR — только offline-auth/Realms). Логотип в LinkGUI header проверен скриншотом и zoom-кропом.
5. **Evidence**: `run/screenshots/evidence-d117b/` — `linkgui-header.png`, `linkgui-header-zoom.png`, `logo-compare-old-new.png` (old/new при 20/28/48/120 px), `pause.png`, `report.log`.
6. **Сборка**: `linkenvisuals-1.0.140.jar` — 11 180 156 байт, SHA-256 `34282DFE9ABD6874B63580083941EEFBA4D678AB5620FDC8720869999C3A2259`; `linkenvisuals-strange-1.0.140.jar` — 11 180 144 байта, SHA-256 `586D71642799253C4822088C6702931F529AA476596214AFA64FADDC585C341B`. Linken-jar развёрнут в MultiMC, SHA-256 совпадает; активен один `linkenvisuals*.jar`.
7. **Commit**: code `a87d04b` `fix(ui): restore pre-D117 LV logo with SDF edges (v1.0.140, D117b)`; docs commit follows.

### Следующая задача
1. **Pixel-perfect аудит запуска и всех экранов** (главная цель следующего чата): splash/loading, main menu, pause, Create World, LinkGUI; donor 1.21.4 запускается из `D:\MultiMC\instances\1.21.4`, порт — из `1.21.111`. Снимать F2 в одинаковом разрешении/GUI scale, складывать side-by-side/overlay/diff, фиксировать числовой diff и чинить только различия порта.
2. **Human pixel acceptance**: side-by-side с donor для main menu / pause / loading; для Create World — только сверка языка стиля (у donor 1.21.4 нет своего экрана создания мира).
3. **Human multiplayer acceptance**: реальный второй игрок на `localhost:25566` (splash/team prefix).
4. **Punchy / ViewModel Animated не открывать**; `HandMotion` — финальный subset.

### Риски
- Точный «1:1» для Create World невозможен: у donor 1.21.4 нет собственного экрана создания мира; это Linken-shell над ванильной формой.
- Game/More form column уже карточки (ванильная ширина 618 design px против 1024) — осознанный воздушный layout без перестройки ванильной сетки.
- LV-логотип — прежний растр 512×512, края пересобраны SDF; рисунок не менялся, при очень крупном масштабе остаётся билинейная мягкость.
- Donor 1.21.4 и порт 1.21.11 — разные версии MC; сравнение только по визуальным якорям (панель/строки/отступы), не по байтам кадра.

## Состояние на 2026-10-24 (D117 — Linken-native Create World, loading, logo, ESC cleanup) — v1.0.140

> **Обновление D117b:** пункт 2 ниже (геометрический логотип) отменён — возвращён прежний LV-монограмм из `6c6919a`, края пересобраны SDF; см. верхний раздел.

### Сделано
1. **ESC**: удалён tile `LINKENVISUALS HUD` (`pause.clickgui`) из `LinkenPauseScreen.buildEntries()`; ключ удалён из en/ru/uk. Donor pause-screen не содержит отдельного входа в LinkGUI/HUD.
2. **Логотип**: LV-монограмм перерисован программно (геометрический LV с antialiasing, рендер 2048→512/120) — без «лесенок». `lv_logo.png` 512×512 (8 929 B), `logo.png` 120×120 (1 360 B). LV-бейдж убран из текстового wordmark (`LinkenMenuChrome.drawWordmark`, welcome-intro, pause topbar); `drawLogo` остался только в LinkGUI (header/footer) и HUD.
3. **Loading screen 1:1 с donor 9d**: градиент `BG_TOP/MID/BOTTOM`, эллиптический accent-glow, две угловые скобки, wordmark-блок `linken.` + `VISUALS` на 44% высоты, progress rail 460 px на 200 px от низа, stage/percent, stacked footer (`Version ..., build ...` + disclaimer). Фоновая фотография и LV-логотип убраны.
4. **Create World**: полностью LINKEN-скин поверх ванильного `CreateWorldScreen` — фон, wordmark/version, tab-pills, glass-карточка, поля/cycling-строки/кнопки/чекбоксы, footer и WORLD SUMMARY на всех вкладках. Ванильная логика, вкладки и ввод сохранены; добавлены `CreateWorldWidgetRenderMixin`, `CreateWorldTabNavigationMixin`, `TextFieldWidgetAccessor`, `CreateWorldScreenAccessor`; скин рисуется на `render` TAIL, `renderDarkening` отменён.
5. **Стабильная геометрия**: карточка 1024 design px на всех вкладках; ванильные `TabButtonWidget` переставляются `alignTabs` под ширину карточки (хитбоксы остаются живыми); header/folder/summary выровнены по внутренним краям карточки.
6. **Фиксы**: `LinkenMenuChrome.rgba(int rgb, int alpha)` — int-литерал теперь 0..255 alpha, а не fade (активная вкладка стала полупрозрачной); folder hint — `I18n.get("selectWorld.targetFolder", hint)`, fallback `I18n` читает сырой `Language.getInstance().get(key)` (ванильный `I18n.translate(key)` давал `Format error` на параметризованных ключах).
7. **Autotest**: `clear tutorial toasts` перед Create World; реальный клик по пилюле вкладки с проверкой `isCurrentTab()` (`clickCreateWorldTab`) — доказывает, что переставленные хитбоксы работают; preview `ProgressScreen` (62%, `BAKING MODELS`) + `lv-autotest-23-loading.png`.
8. **Проверки**: **82 набора / 324 теста**, 0 fail/error/skip в обоих flavors; Linken autotest **1158/1158** (1920×1080, 0 fail/skip, 0 новых ERROR/exception); Strange **1148/1148** (0 fail/skip; 3 ERROR — только offline-auth/Realms). Шейдеры/звук: `Sound engine started`, shader-ERROR нет. Evidence: `run/screenshots/evidence-d117/autotest-1920/` (46 снимков + `report.log`) и `autotest-strange-1920-console.log`.
9. **Сборка**: `linkenvisuals-1.0.140.jar` — 11 178 121 байт, SHA-256 `94EB5A38BA11864ECE1C034B7DF93DC0679FF11ABE647855FC4564596A04622A`; `linkenvisuals-strange-1.0.140.jar` — 11 178 109 байт, SHA-256 `7AF6ECCD9E9FC961DF880426E6399A9140F9C7C7795EC67BD141385C1B00D450`. Linken-jar развёрнут в MultiMC, SHA-256 совпадает; активен один `linkenvisuals*.jar`.
10. **Commit**: code `ee65d37` `feat(ui): rebuild Linken Create World, loading and logo (v1.0.140, D117)`; docs commit follows.

### Следующая задача
1. **Human pixel acceptance**: side-by-side с donor для main menu / pause / loading; для Create World — только сверка языка стиля (у donor 1.21.4 нет своего экрана создания мира).
2. **Human multiplayer acceptance**: реальный второй игрок на `localhost:25566` (splash/team prefix).
3. **Punchy / ViewModel Animated не открывать**; `HandMotion` — финальный subset.

### Риски
- Точный «1:1» для Create World невозможен: у donor 1.21.4 нет собственного экрана создания мира; это Linken-shell над ванильной формой.
- Game/More form column уже карточки (ванильная ширина 618 design px против 1024) — осознанный воздушный layout без перестройки ванильной сетки.
- LV-логотип — процедурный растр 512×512; при очень крупном масштабе остаётся билинейная мягкость, но «лесенок» нет.

## Состояние на 2026-10-24 (D116 — donor animation parity, live/autotest evidence) — v1.0.140

### Сделано
1. **D116 — donor animation parity в LinkGUI**: `AnimatedString` повторяет донорский кроссфейд — поиск 150 мс, значения mode/keybind 160 мс, `EASE_OUT_CUBIC`; поиск кроссфейдит prefix/suffix, значения переходят old/new slide-fade. Enum-dropdown использует экспоненциальное сглаживание 20/с, сохраняет `lastOpenMode`/`lastOpenModeBox` до конца закрытия, плавно меняет высоту панели и каскадно раскрывает строки. Слайдер: значение катится к реальному 14/с со snap `max(0.001, step*0.25)`, позиция knob — 20/с, при drag следует за сырой позицией курсора; knob растёт 5.0→6.7 px и получает accent halo. Bind-chip на карточках и bind-row в настройках используют donor-style `drawMorphValue`, ширина chip резервирует ширину уходящего текста. Все animation-id owner-qualified (`sld:`, `modev:`, `keybindv:`, `cardbind:`, `bindrow:`).
2. **D116 — остальные анимации/UI**: панели Profile / Theme / Friends/Waypoints / настройки модуля получили spring fade+slide; сетка модулей плавно переезжает на новые слоты, новые карточки появляются fade-in, ушедшие — гаснут (`gridPos`/`gridExit`); смена темы кроссфейдит accent/panel/text/backdrop ~300 мс (`LinkGuiTheme.tick`). `HudManager.render` выходит при `currentScreen != null` (кроме ChatScreen/edit mode), поэтому hotbar/armor/potion/HUD не просвечивают за LinkGUI, pause, Create World и ванильными экранами.
3. **D116 — Linken Create World**: `CreateWorldScreen` получил Linken backdrop, компактную glass-панель, Linken-кнопки/слайдеры, mint-рамку поля имени, LV + `WORLD SETUP` в футере и i18n en/ru/uk. Функциональные vanilla-вкладки Game/World/More сохранены; это осознанный Linken shell, а не полная копия donor (у donor такого экрана нет). Autotest: `create-world` на 1280×720 и 1920×1080.
4. **D116 — live-проверка**: `LiveTestDriver` на Paper 1.21.11 (`localhost:25566`) в окне 1280×720 при GUI scale 1 проверил `self=Hidden`, `botDistance=13.9`; tab list показывает `Hidden` и `[MVP] SecondPlayer`, item-radius splash показывает `77%`. Состояние конфига/настроек после прогона восстановлено. Watermark санитизирует username через `StreamerRules`.
5. **D116 — autotest sizing**: в `build.gradle` добавлены `-PautotestWidth=` / `-PautotestHeight=`; финальные прогоны: Linken **1150/1150** при 1280×720 (GUI scale 2) и 1920×1080 (GUI scale 3), Strange **1140/1140** при 1920×1080 (GUI scale 3), 0 fail/skip.
6. **Доказательства**: `run/screenshots/evidence-d116/` — `live-tab.png`, `live-item-radius.png`, `live-self-nametag.png` и три каталога `autotest-1280/`, `autotest-1920/`, `autotest-strange-1920/` (по 43 скриншота + `report.log`). Отчёты: `linkenvisuals-autotest-20261024-172141.log` (1150), `...172449.log` (1150), `...171824.log` (1140).
7. **Проверки**: **82 набора / 324 теста**, 0 failures/errors/skips в обоих flavors. В Linken-прогонах `latest.log` — 0 новых ERROR/exception; 1920-консоль содержала один `sessionserver.mojang.com ... Read timed out`, это не mod-ошибка. В Strange 3 новых ERROR — только offline-auth/Realms (`RealmsServiceException`), не ошибки мода.
8. **Сборка**: `linkenvisuals-1.0.140.jar` — 11 183 034 байта, SHA-256 `420C9A9F2DE4732CA1DAAFCC588B8640C8E498C2303F0078B5700C089B0A0561`; `linkenvisuals-strange-1.0.140.jar` — 11 183 024 байта, SHA-256 `9787C1FA0AB7F333592B0564CE7848ED7503A37806EC91C7895D4983386401C7`. Linken-jar развёрнут в `D:\MultiMC\instances\1.21.111\.minecraft\mods\linkenvisuals-1.0.140.jar`, SHA-256 совпадает; активен ровно один `linkenvisuals*.jar`, старый `1.0.74` отключён.
9. **Commit**: code `ed3cba0` `feat(ui): finish donor LinkGUI animation parity (v1.0.140, D116)`; docs commit — отдельный коммит.

### Следующая задача
1. **Human multiplayer acceptance**: второй реальный игрок на `localhost:25566` — splash `NN%`, team-prefix nametag, ping/tab. `SecondPlayer` — mineflayer-бот, поэтому это не человеческая приёмка.
2. **Human pixel acceptance**: side-by-side с donor на 1280/1920, hover/press на всех sub-screens.
3. **Punchy не открывать**; `HandMotion` — финальный subset.

### Риски
- `SecondPlayer` — протокольный mineflayer-бот, не человеческий клиент; side-by-side human acceptance остаётся ручной задачей.
- Create World — Linken shell над vanilla-логикой; вкладки/тултипы остаются ванильными, полная donor-копия невозможна (экрана нет у donor).
- LinkGUI Interface/settings panels скроллятся; последняя строка у края viewport'а частично обрезана — affordance скролла, не overflow.
- Анимации проверены unit-тестами и финальными скриншотами, но покадрового pixel-diff против donor нет.
- 1280-скриншоты — 1280×720; 1920-скриншоты — 1920×1061 из-за рамки окна при override 1920×1080.
- `run/options.txt` восстановлен: `guiScale:3`, `pauseOnLostFocus:true`, `overrideWidth:0`, `overrideHeight:0`; `default.json` чистый (Streamer Mode/Self Nametags/Item Radius off, Potion Radius 4.0).

## Состояние на 2026-10-24 (D115 — helper mouse, splash labels, branding) — v1.0.139

### Сделано
1. **D115/v1.0.139 — helper mouse bindings**: `KeybindSetting` расширен флагом `mouseAllowed`; LMB/RMB хранятся как `MOUSE1`/`MOUSE2`, читаются донорские `M1`/`M2`, отображаются как `LMB`/`RMB`. `MouseMixin` (`onMouseButton`) вызывает `Modules.onHelperMouse`, `Ft/Hw/Rw` получили `onMouse`, ClickGUI/LinkGUI/Classic захватывают кнопку при listening. `HelperItemsHud` показывает mouse-бинд; autotest проверяет, что LMB и RMB выбирают те же hotbar-слоты, что и key bind. Key-only настройки больше не могут получить мёртвый `M1`.
2. **D115 — ItemRadius splash labels fixed**: реальный баг D112 — `forward = right × up` из `Camera.getHorizontalPlane/getVerticalPlane` давал near-zero depth, поэтому `NN%` не рисовались ни над одним игроком. Теперь базис берётся из `Camera.getRotation()`; FakePlayer добавлен в `nearbyPlayers` (он не входит в `world.getPlayers()`); добавлены screen-bounds и cap 4–10 design px. Autotest: FakePlayer + splash potion + `check radius labels` + screenshot `item-radius-labels`.
3. **D115 — SelfNametags team prefix**: `StreamerTextSanitizerTest` (4) фиксирует `[MVP] Ivan` → `[MVP] Streamer`, legacy `§7[Team] §fIvan`, чужой ник не трогается, `Ivanov` не считается self-ником.
4. **DynamicIsland закрыт по donor-правилу**: donor `DynamicIsland.onRender2D` сразу выходит при `LinkHudStyle.active()`; Linken-сборка всегда Link style. Media/lyrics уже покрыты `MediaPlayerHud`/`LyricsHud`; второй media-HUD не добавляется. В Strange-сборке Linken-HUD-элемент не показывается из-за flavor-lock. Не числится незакрытым.
5. **BoostFPS закрыт по donor-правилу**: donor `UI.getBoostFpsMode()` всегда возвращает `DEFAULT`, а `boostFpsUi` имеет `visible=false`. Наш порт не выводит мёртвый `Default/Middle/High`; `Optimization` остаётся реальным управляемым FPS-модулем.
6. **Branding**: добавлен прозрачный LV-монограмм `lv_logo.png` (из пользовательского файла), `logo.png`/`ico_logo.png` заменены на LV; `strange_logo.png` — Strange-логотип. LV используется в Linken main menu/welcome/loading/pause/HUD watermark/KeybindPanel/LinkGUI header+footer; Strange logo — только в Strange screens (`MainMenuScreen`, `ClickGuiScreen`, Strange HUD/loading).
7. **Проверки**: **315 тестов**, 0 fail/error/skip в обоих flavors; Linken autotest **1147/1147** (1280×720 и 1920×1080), Strange **1137/1137** (1920×1080), 0 fail/skip. Новых ERROR/exception/shader/sound нет; только offline-auth/Realms. `BrandingAssetsTest` проверяет упаковку LV/Strange ассетов.
8. **Commit**: code `610a03d` `feat(helper): port mouse binds, fix splash labels, split LV branding (v1.0.139, D115)`; docs commit follows.

### Следующая задача
1. **Human multiplayer acceptance**: реальный второй игрок в радиусе splash и team-prefix nametag на сервере; формулы/FakePlayer/team-prefix тесты уже есть, live-кейс — только вручную.
2. **Human pixel acceptance**: 1280×720 и 1920×1080 screenshots просмотрены; остаётся side-by-side с donor и проверка hover/press на всех sub-screens.
3. **Punchy не открывать**: полный Punchy и `ViewModel Animated` исключены пользователем (2026-10-24); `HandMotion` — финальный subset.

### Риски
- LinkGUI Interface-панель скроллится; на 1920 последняя строка (`Kill Streak HUD`) частично обрезана viewport'ом — это не overflow, а affordance скролла.
- Helper HUD по умолчанию стоит справа над hotbar (`0.77, 0.86`) и не пересекает chat/hotbar при GUI scale 3; при scale 5 возможен сдвиг — проверять вручную.
- LV-логотип — растровый (512×512), получен из пользовательского файла; Strange-логотип остаётся отдельным ассетом.

## Состояние на 2026-10-24 (D114 — HelperItemsHud + Ft/Hw/Rw) — v1.0.138

> Историческая запись. Пункты «Следующая задача» ниже закрыты в D115: mouse bindings, DynamicIsland, BoostFPS; см. верхний раздел.

### Сделано
1. **D114/v1.0.138**: закрыт `HelperItemsHud`: добавлены `FtHelperModule`, `HwHelperModule`, `RwHelperModule` (Visual/Misc, keybind-настройки), `HelperActions` (выбор hotbar-предмета + chat feedback) и `HelperItemsHud` — плитки 18 px с item icon и key label, кэш 100 мс, preview в edit mode. `KeyboardMixin` вызывает `Modules.onHelperKey`.
2. **Проверки**: **304 теста** (+3 `HelperModulesTest`), 0 fail/error/skip; autotest Linken **1136/1136**, Strange **1126/1126**, 0 fail/skip; `check helper hud` + screenshot `helper-items`. Дополнительный прогон при **1280×720** — Linken **1136/1136**, LinkGUI/custom-vitals/helper-items screenshots проверены, layout помещается. Новых ERROR/exception/shader/sound нет.
3. **Commit**: `c1c2a14` `feat(hud): port HelperItemsHud and Ft/Hw/Rw helpers (v1.0.138, D114)`.
4. **Сборка**: `linkenvisuals-1.0.138.jar` — 11 129 420 байт, SHA-256 `96CC4CF33F7C93F379F9AFEA5BEEB420B08122412ACC7E20965BC2C9FBD4F90A`; `linkenvisuals-strange-1.0.138.jar` — 11 129 420 байт, SHA-256 `0E7B71D3B2186B772BF4BCC886A451744F27A04863E6B4D8EC27E0A8F76E8F4B`.

### Следующая задача
1. **DynamicIsland** (1496 + 1043 строк helper) — donor сам скрывает его в Link style; порт нужен только для полного vanilla/Strange-паритета.
2. **Mouse-бинды helper-модулей** (сейчас key-only; donor поддерживает ЛКМ/ПКМ).
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance; BoostFPS donor-DEFAULT.

### Риски
- HelperItemsHud — key-only; mouse bindings donor не портированы.
- `HelperItemsHud` показывает только реально найденные hotbar-предметы; preview показывает samples.
- `DynamicIsland` остаётся крупным незакрытым блоком; **Punchy исключён из scope** и не считается остатком.

## Состояние на 2026-10-24 (D113 — UI background/text colours) — v1.0.137

### Сделано
1. **D113/v1.0.137**: Interface получил `Custom Theme Colours`, `Background Colour`, `Text Colour`. При включении `LinkGuiTheme` перекрашивает panel/row/field/text/backdrop от заданного background и text; `HudStyle.base()` использует `LinkGuiTheme.hudBase()` (кастомный background влияет на HUD-панели). Цвета применяются сразу, без перезапуска.
2. **Проверки**: **301 тест**, 0 fail/error/skip; autotest Linken **1120/1120**, Strange **1110/1110**, 0 fail/skip; шаг `check custom UI colours` + screenshot `linkgui-custom-colours`. Новых ERROR/exception/shader/sound нет.
3. **Commit**: `29ac193` `feat(ui): add custom background and text colours (v1.0.137, D113)`.
4. **Сборка**: `linkenvisuals-1.0.137.jar` — 11 115 791 байт, SHA-256 `B2BA90210F3FEE21147A302A838444001FE4063075AB12CCB39A52C9D4D501BF`; `linkenvisuals-strange-1.0.137.jar` — 11 115 780 байт, SHA-256 `7D53F4FC6CC2A97761C05EE4C3E24F7DB4C9B538216B2ADCB2D6C76DCAB57533`.

### Следующая задача
1. **HelperItemsHud + Ft/Hw/Rw helper modules** (закрыть blocked HUD).
2. **Punchy исключён из scope** (2026-10-24); `HandMotion` — финальный subset, не open item.
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.
4. `DynamicIsland` остаётся skipped (donor сам скрывает его в Link style); `BoostFPS` у донора принудительно DEFAULT.

### Риски
- `Text Colour` применяется к LinkGUI-тексту/темам; HUD-текст с семантическими цветами (health/armor) сохраняет свои цвета.
- Кастомный background влияет на HUD base, но не на все hardcoded акценты; pixel-diff открыт.
- `BoostFPS` не выведен, потому что donor `getBoostFpsMode()` всегда возвращает DEFAULT.

## Состояние на 2026-10-23 (D112 — ItemRadius proximity labels) — v1.0.136

### Сделано
1. **D112/v1.0.136**: закрыт donor-пробел `ItemRadius` — `NN%` над игроками в радиусе splash-зелья. `ItemRadiusRenderer` запоминает impact/radius при удерживании splash potion, `renderLabels(DrawContext)` проецирует головы игроков через камеру и рисует MSDF-текст с чёрным outline; формула `ItemRadiusRules.proximityPercent` (100% в центре, 0% на краю). Вызов — из `HudManager` рядом с FortniteDamage.
2. **Проверки**: **301 тест** (+1 `ItemRadiusRulesTest`), 0 fail/error/skip; autotest Linken **1106/1106**, Strange **1096/1096**, 0 fail/skip. Отдельного multiplayer-кейса с игроком в радиусе нет — добавлен `lastLabelVisible` probe и pure-тест формулы.
3. **Commit**: `26b843e` `feat(world): port ItemRadius splash proximity labels (v1.0.136, D112)`.
4. **Сборка**: `linkenvisuals-1.0.136.jar` — 11 114 360 байт, SHA-256 `BE5548AC383B83A7FD87A7E89718D58579B423B17BAD732440E7461223617770`; `linkenvisuals-strange-1.0.136.jar` — 11 114 354 байта, SHA-256 `22E5E32CBEAA916D4092EA7DCF1F87B3488402E797ED03201EA819E2576438CB`.

### Следующая задача
1. **UI background/text colours + BoostFPS** (остаток donor UI); BoostFPS у донора принудительно DEFAULT.
2. **Punchy-lite expansion не планируется**: Punchy исключён из scope; HandMotion финальный.
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.
4. Remaining donor visual modules: `HelperItemsHud` blocked, `DynamicIsland` skipped.

### Риски
- Labels — screen-space HUD, не donor 3D world billboard; визуально цифры читаемы, но pixel-diff не закрыт.
- Нет автоматического сценария с реальным вторым игроком в радиусе; проверена формула и probe, нужна live-проверка.

## Состояние на 2026-10-23 (D111 — UI palette/fade/blur) — v1.0.135

### Сделано
1. **D111/v1.0.135**: Interface получил donor-настройки `Secondary Colour`, `Theme Fade`, `GUI Blur`. `Theme.secondary()` берёт `Secondary Colour`; при выключенном `Theme Fade` secondary сворачивается в accent (HUD-градиенты становятся одноцветными). `GUI Blur=false` отключает blur в `LinkGuiScreen` и `ClickGuiScreen` (оба переопределяют `renderBackground`; Link backdrop дополнительно не вызывает `applyBlur`).
2. **Проверки**: **300 тестов** (0 fail/error/skip); autotest Linken **1106/1106**, Strange **1096/1096**, 0 fail/skip; `check UI palette` (fade-off secondary == accent) и screenshot `linkgui-noblur` (реально без blur). Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
3. **Commit**: `fa38938` `feat(ui): expose secondary colour, fade and blur (v1.0.135, D111)`.
4. **Сборка**: `linkenvisuals-1.0.135.jar` — 11 111 719 байт, SHA-256 `FC0CEC2721C1C5A5FF6048A21C3AC12C441BC3673560EA06B56457C85DBBA82B`; `linkenvisuals-strange-1.0.135.jar` — 11 111 713 байт, SHA-256 `04EE5DF0090B5FB4CAE4E50ECD1CF75D102F2B9D8A2D43F08C3C463392392C52`.

### Следующая задача
1. **UI background/text colours + BoostFPS** (donor UI остаток); сверить `LinkGuiTheme`/`HudStyle`.
2. **Punchy-lite expansion** (swim/climb/elytra/fall/item idle); full Punchy remains open.
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.
4. Remaining donor visual modules: `HelperItemsHud` blocked, `DynamicIsland` skipped.

### Риски
- Donor `Theme Fade` интерполирует primary/secondary по offset; порт использует secondary как статический второй цвет и сворачивает его в accent при выключении. Это осознанное упрощение, pixel-diff не закрыт.
- `Background Colour`, `Text Colour`, `BoostFPS` пока не выведены; не считать UI закрытым.
- No-blur screenshot проверяет отсутствие blur, но не 1280×720/4K pixel parity.

## Состояние на 2026-10-23 (D110 — SelfNametags) — v1.0.134

### Сделано
1. **D110/v1.0.134**: donor `SelfNametags` — это не отдельный рендерер, а переключатель само-санитизации: `SelfNametagsModule` (Visual, default OFF, без настроек) + `PlayerEntityMixin.getDisplayName` применяет `StreamerTextSanitizer` с правилами активного `StreamerMode` только к локальному игроку. Без включённого StreamerMode/кастомного имени поведение не меняется, как у донора.
2. **Проверки**: **300 тестов** (+1 `SelfNametagsModuleTest`), 0 fail/error/skip; autotest Linken **1093/1093**, Strange **1083/1083**, 0 fail/skip; шаг `check self nametag` проверяет `getDisplayName() == Hidden` при `StreamerMode.Custom Name=Hidden`, затем восстанавливает настройки. Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
3. **Commit**: `1ea9fd3` `feat(visuals): port SelfNametags toggle (v1.0.134, D110)`.
4. **Сборка**: `linkenvisuals-1.0.134.jar` — 11 110 581 байт, SHA-256 `FA2E0F083AFADAB816F4652BF860EB0E8723545AC489B6B21C4726D42F9DA0A3`; `linkenvisuals-strange-1.0.134.jar` — 11 110 577 байт, SHA-256 `CD7A2AA03352114298C0854DB5C8BDD5C39ACB81E482356728F6C66A2BE1CE2A`.

### Следующая задача
1. **UI** donor controls: colour/fade/blur/BoostFPS; сверить donor `UI` и наши Interface/Theme/HudStyle.
2. **Punchy-lite expansion** (swim/climb/elytra/fall/item idle); full Punchy remains open.
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.
4. Remaining donor visual modules: `HelperItemsHud` blocked, `DynamicIsland` skipped.

### Риски
- `SelfNametags` зависит от `StreamerMode` и `Custom Name`; это donor-семантика, не баг. Без них модуль визуально ничего не меняет.
- Санитизация своего label не проверена на реальном multiplayer/team-prefix (singleplayer autotest).

## Состояние на 2026-10-23 (D109 — DamageParticles + FortniteDamage) — v1.0.133

### Сделано
1. **D109/v1.0.133**: donor `DamageParticles` — `DamageParticlesModule` (Visual, default OFF): Color, Count 10–60 (30), Size 10–55 (30), Spawn On Attack/Throw, Mode Bloom/Star/Snow/Firefly/Dollar/Cube, Particle Size 0–1 (0.5). `DamageParticlesRenderer` на `END_MAIN`: legacy impact 30 частиц (2.1–2.8 с, bounce/friction/LOS, 3 слоя glow/core/sparkle) + decorative burst 35 на удар и 4/тик на стрелы/тризубцы/эндер-перлы (2 с, caps 420/480).
2. **FortniteDamage**: `FortniteDamageModule` (Visual, default OFF): Style Fortnite, Size 0.012–0.08 (0.032), Lifetime 300–1800 (850 мс), Rise 0–1.5 (0.42), Theme Color, Color, Crit Color. `FortniteDamageRenderer` ждёт изменение HP до 750 мс, при килле оценивает attack damage, рисует MSDF-цифры с 8-контурным outline; крит — #FCFE3C. Для стабильности используется **screen-space проекция через камеру и проверенный GUI MSDF pipeline**, а не отдельный world-text pipeline.
3. **Проверки**: **299 тестов** (+4 `DamageParticlesRulesTest`, +3 `FortniteDamageRulesTest`, +3 `DamageVisualModulesTest`), 0 fail/error/skip; autotest Linken **1087/1087**, Strange **1077/1077**, 0 fail/skip, 1920×1080; screenshots `damage-number` (debug probe) и `damage-visuals` (реальный удар). Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
4. **Commit**: `a9a99be` `feat(visuals): port DamageParticles and FortniteDamage (v1.0.133, D109)`.
5. **Сборка**: `linkenvisuals-1.0.133.jar` — 11 108 168 байт, SHA-256 `74AEB58B362FACAEBAA0DFEA5395D7D8FCF7C481D1B19FC66802DAE1AD82473E`; `linkenvisuals-strange-1.0.133.jar` — 11 108 167 байт, SHA-256 `A31C091673036A2ED815C8AB0F03FF3A1D1A8ABDD3C5B4F409A1F86E3089362B`.

### Следующая задача
1. **SelfNametags** line-by-line; full `UI` colour/fade/blur/BoostFPS.
2. **Punchy-lite expansion** (swim/climb/elytra/fall/item idle); full Punchy remains open.
3. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.
4. Remaining donor visual modules: `HelperItemsHud` blocked, `DynamicIsland` skipped.

### Риски
- FortniteDamage: donor 3D world billboard заменён screen-space проекцией; при серверной несинхронизации HP цифра может не появиться (donor имел то же ограничение).
- DamageParticles: donor-текстуры/3 слоя воспроизведены, но без точного pixel-diff; ambient `Particles` (чёрные квадраты) — отдельный существующий модуль.
- `FortniteDamage.Style` содержит единственный donor-вариант Fortnite.

## Состояние на 2026-10-23 (D108 — ItemRadius) — v1.0.132

### Сделано
1. **D108/v1.0.132**: donor `ItemRadius` — `ItemRadiusModule` (Visual, default OFF): Ender Eye/Sugar (14), Netherite Scrap box 2.5/3.5 (`Dragon Trap`), Dried Kelp (плита/стена), Snowball (12), Splash Potion (radius 1–30, default 4), `Proximity Colour` (magenta), individual item colours.
2. **Геометрия**: `END_MAIN` + `BlockOverlayPipelines.FLAT_NO_DEPTH` (donor line depth=false); кольца — `HitRangeGeometry` ribbon 96 сегментов, боксы — `CustomHitBoxGeometry`, стены/лесенка — `ItemRadiusGeometry` thick-line quads. Симуляция splash-зелья: 200 шагов, speed 0.5, pitch −20°, gravity 0.03, drag 0.8/0.99, player velocity, raycast; кэш на тик/yaw/pitch.
3. **Проверки**: **289 тестов** (+3 `ItemRadiusModuleTest`, +2 `ItemRadiusGeometryTest`), 0 fail/error/skip; autotest Linken **1028/1028**, Strange **1018/1018**, 0 fail/skip, 1920×1080; screenshot `item-radius` (Ender Eye ring, pitch вниз). Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
4. **Commit**: `00a9a73` `feat(world): port donor item radius (v1.0.132, D108)`.
5. **Сборка**: `linkenvisuals-1.0.132.jar` — 11 074 237 байт, SHA-256 `94F027953C620522080B7E66C848B1610005F6185679F06216B06C7D38D4765D`; `linkenvisuals-strange-1.0.132.jar` — 11 074 232 байта, SHA-256 `420CA80F65E17570A9830E23721D6A5AF3D8C780C768EBCB7EA5820AC2ACACF1`.

### Следующая задача
1. **DamageParticles** / **FortniteDamage** — donor particle features + damage numbers.
2. **SelfNametags** line-by-line; full `UI` colour/fade/blur/BoostFPS.
3. **Punchy-lite expansion** (swim/climb/elytra/fall/item idle); full Punchy remains open.
4. **Pixel GUI pass** 1280×720/1920×1080; human acceptance.

### Риски
- `ItemRadius` proximity `%` labels портированы в D112 (screen-space MSDF); donor world-billboard pixel-diff остаётся открытым.
- Dried-kelp wall — donor staircase geometry, но без точного texture/width pixel-diff.
- Splash impact — donor heuristic without bossbar; human check required.

## Состояние на 2026-10-23 (D107 — CustomVitals + Armor V2, hotbar-anchored HUD) — v1.0.131

### Сделано
1. **D107/v1.0.131** (`ecbbd64`): добавлен donor `CustomVitals` — `CustomVitalsModule` (Visual, default OFF) со стилями `Segments`/`Stack`/`Bars`, `Segments` 3–10, `Armor Row`. Рисуется hotbar-anchored полосой 182 px над хотбаром (`CustomVitalsHud`), заменяет vanilla health/food/armour/air/XP; в preview — при открытом чате/редакторе.
2. **ArmorHud V2**: `Armor HUD Mode` `V1`/`V2` в Interface; V2 — две donor-группы 49 px по бокам хотбара (boots/leggings слева, chest/helmet справа), 17 px ячейки, durability %, preview; V1 сохранён.
3. **Vanilla cancel**: `InGameHudMixin.renderStatusBars`/`renderMountHealth`, новые `ExperienceBarMixin`/`JumpBarMixin` отменяют vanilla-слои только при активном CustomVitals. Mixin-конфиг обновлён, `MixinRegistrationTest` зелёный.
4. **Проверки**: **284 теста** (0 fail/error/skip; +3 `CustomVitalsModuleTest`); autotest Linken **977/977**, Strange **967/967**, 0 fail/skip, 1920×1080; screenshots `custom-vitals`, `armor-hud-v2`. Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
5. **Сборка**: `linkenvisuals-1.0.131.jar` — 11 061 584 байта, SHA-256 `E8864BE73A9E2E4975628B28D724B8F632E665C5D0B300FC9A47D3880ED14E64`; `linkenvisuals-strange-1.0.131.jar` — 11 061 579 байт, SHA-256 `DD02DAC29461FB2F0480B63F09BC35C238A8E3510E35ED377B2A44321F78EE6F`.

### Следующая задача
1. **ItemRadius** — donor 333 строки: ender eye/sugar circles, netherite scrap box, dried-kelp staircase, snowball projection, splash potion impact/proximity labels.
2. **DamageParticles** / **FortniteDamage** — сначала проверить helper-классы; затем `SelfNametags` line-by-line и UI controls.
3. **Punchy-lite expansion** (swim/climb/elytra/fall/item idle) — полного Punchy по-прежнему нет.
4. **Pixel GUI pass** 1280×720/1920×1080 и human acceptance.

### Риски
- CustomVitals/Armor V2 — fixed hotbar anchors, не `HudElement` drag/snap; это соответствует donor, но не редактируется как карточки.
- `Armor HUD Mode` добавлен в Interface (Linken и Strange); V2 не тестировался на hotbar offhand clearance в живом бою.
- Полный Punchy, Lottie и pixel parity остаются открытыми.

## Состояние на 2026-10-23 (D106a — fake-player attack charge) — v1.0.130

### Сделано
1. **D106a/v1.0.130** (`8b0bc41`): `FakePlayer.damageOf` теперь берёт текущий `getAttackCooldownProgress(0.5f)`, а не время с предыдущего удара; раньше первый удар после `hitAt` всегда считался почти нулевым charge и наносил только 20% урона. Теперь полный charge-logic + 1.5× crit работают на первом и последующих ударах.
2. **Проверки**: **281 тест**, clean build и `-Pstrange` зелёные; autotest Linken **953/953** и Strange **943/943** на 1.0.130, 0 fail/skip.
3. **Сборка**: `linkenvisuals-1.0.130.jar` — 11 047 843 байта, SHA-256 `1FFE8177B1A4D5F2F454835A092AF4ECE86E0652AF4749D69B1F21464286968B`; `linkenvisuals-strange-1.0.130.jar` — 11 047 838 байт, SHA-256 `2B8ADEDA0BD6C5E544BA463A3D6DA4F47F9650C4DA7FC0C1328B2A05EA14C11C`.

### Следующая задача
См. D106 ниже: pixel GUI pass, screen-anchored HUD, Punchy-lite expansion.

## Состояние на 2026-10-23 (чат №60 — audit fixes: crits/Welcome/ESC/animations, D106) — v1.0.129

### Сделано
1. **D106/v1.0.129** (`385fa23`): детальный аудит `docs/VISUAL_AUDIT.md`; исправлены четыре замечания пользователя.
2. **FakePlayer crits**: `HitSoundModule.isCrit` теперь повторяет vanilla-гейт (full charge >0.9, fall, airborne, no water/climb/blind/vehicle/sprint); `FakePlayer.hit` считает crit один раз, применяет 1.5× урон, crit-звук/эффект и `lastCrit`; убраны двойные hit-звуки из mixin-путей. Тесты `FakePlayerTest` + `HitSoundModuleTest`; autotest реально бьёт dummy с crit-путём.
3. **Welcome**: `LinkenMainMenuScreen` получил первый welcome-экран (wordmark, `Welcome back, <nick>`, accent glow, progress rail, skip по клику/клавише, музыка после него). Lottie/Skija не портирован; сцена процедурная в том же визуальном языке. Autotest: `welcome` + `linken-mainmenu`.
4. **ESC**: `LinkenPauseScreen` переписан с широкого 1040 px hub на donor-панель 440 px (topbar 56, rows 44, gap 8, 2 колонки, divider, Save/Quit), локализован (`pause.*` en/ru/uk), иконки `icons2`, hover/open анимации, закрытие X/ESC. Autotest проверяет реальный route `GameMenuScreen -> LinkenPauseScreen` и снимает `pause`.
5. **Анимации**: в `Better Animations` добавлен `Hand Motion` (default true) — walk bob, sprint sway, вода, падение и лёгкий attack-recoil на donor-анкере `renderItem`; это Punchy-lite, не полный Punchy. Тест `HandMotionTest`.
6. **GUI fix**: в account-row иконка `icons2` больше не перекрывает `Current account · Licensed` (donor-координаты `tx` / `tx+10`).
7. **Проверки**: **281 тест** в обеих flavor-сборках (0 fail/error/skip). Autotest: Linken **953/953**, Strange **943/943**, 0 fail/skip, 1920×1080; screenshots `welcome`, `pause`, `linken-mainmenu`; новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
8. **Сборка**: `linkenvisuals-1.0.129.jar` — 11 047 876 байт, SHA-256 `6EFF4F8A7713D4EDB98FC7BF36D023EE7A6A956BD7389F063ABC2FC818D95FBC`; `linkenvisuals-strange-1.0.129.jar` — 11 047 871 байт, SHA-256 `8D03660CC18CF4E38E1E97F3A00CB718CC6D8907D9183AD3A9048AA705216063`.

### Следующая задача
1. **Пиксельный GUI-пасс**: 1280×720 и 1920×1080 для title, LinkGUI, pause, multiplayer, Select World, Alt Manager и sub-settings; исправить оставшиеся отличия от donor.
2. **Screen-anchored HUD layer**: разблокирует `ArmorHud V2` и donor `CustomVitals`; текущий `HudElement` center/fraction-based.
3. **Punchy-lite expansion**: swim/climb/elytra/fall poses и item-specific idle, если пользователь хочет ближе к donor Punchy. Полный Punchy (181 класс/2.7 MB) не портирован.
4. **Остальные модули**: `ItemRadius`, `DamageParticles`, `FortniteDamage`, `SelfNametags` line-by-line, full UI colour/fade/blur/BoostFPS.

### Риски
- Welcome — процедурная сцена, не 1:1 Lottie (`welcome.json` 493 кадра/60 fps, Skija).
- HandMotion — только видимый subset; полный Punchy и его item-анимации отсутствуют.
- Pause соответствует donor-геометрии, но pixel-parity не доказан; 1280×720 требует ручной проверки.
- GUI-пасс не закрыт: остаются мелкие отличия шрифтов/хверов/экранов.
- Полный parity не закрыт; см. `VISUAL_AUDIT.md` и `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №59 — donor ViewModel, D105) — v1.0.128

### Сделано
1. **D105/v1.0.128** (`aa6e107`): donor `ViewModel` — `ViewModelModule` (`viewmodel`, Visual, default OFF) с 12 настройками: `Main/Off X/Y/Z Offset` (−2…2, шаг 0.05, 0) и `Main/Off X/Y/Z Scale` (0…3, шаг 0.05, 1). Донорский `Animated`/Punchy-режим не выставлен (Punchy не портирован; мёртвый тумблер запрещён).
2. **Применение**: `HeldItemRendererMixin` инжектит `renderFirstPersonItem` на `INVOKE renderItem(LivingEntity, ItemStack, ItemDisplayContext, MatrixStack, OrderedRenderCommandQueue, I)`, `require=2` — donor-порядок поверх vanilla equip/swing и `HandPose`, до item-display transform. При активном `HandEditor` дельта отключается, чтобы принятые drag-хэндлы не расходились.
3. **Правила**: main-hand X инвертируется, off-hand X — нет, left-handed X зеркалится; при еде/зельях X/Z-смещения обнуляются, Y и scale остаются. Чистая `Transform.resolve` покрыта тестами.
4. **Конфиг-фикс**: `Modules.init()` перенесён до `ConfigManager.load()` — object-модули D95–D105 раньше регистрировались после загрузки профиля, поэтому их `enabled`/настройки не восстанавливались. Теперь сохраняются/загружаются все object-модули; legacy ModuleInfo-путь не менялся.
5. **Проверки**: **270 тестов** в обеих flavor-сборках (0 fail/error/skip; +5 `ViewModelModuleTest`). Autotest: Linken **938/938**, Strange **928/928**, 0 fail/skip, 1920×1080; новый `viewmodel` screenshot и проверка `lastAppliedVisible()` после реального кадра. Новых ERROR/exception/shader/sound нет; в Strange-логе 3 строки — только прежние offline-auth/Realms.
6. **Сборка**: `linkenvisuals-1.0.128.jar` — 11 034 570 байт, SHA-256 `37E19B641CBFD7F488D92874CDE596B34054A472C86170A9BB92C28F50FCE710`; `linkenvisuals-strange-1.0.128.jar` — 11 034 567 байт, SHA-256 `4277ED2DA154083FB40C12AD4985DA91ED3083A6ADBF4B3A8F2C1D3673216173`.

### Следующая задача
1. **Screen-anchored HUD слой** — разблокирует `ArmorHud V2` (hotbar-relative) и donor `CustomVitals` (полосы HP/голода/опыта над хотбаром). Сейчас `HudElement` center/fraction-based; нужен слой screen/hotbar-anchored без поломки drag/snap.
2. Если слой слишком велик — `CustomVitals` как прямой `InGameHudMixin`-overlay (donor `CustomVitalsBar`), без `HudManager`-элемента; затем `ItemRadius` (world-render круг/бокс/лесенка, donor 333 строки) или `DamageParticles`.
3. Human: живая еда/зелья и left-hand для ViewModel, числовые настройки vs HandEditor, оба flavor-jar, cooldown-тики, multiplayer custom tab.

### Риски
- `ViewModel` и `HandEditor` — два независимых слоя; при одновременном включении дельта применяется поверх HandPose, а в editor-режиме скрыта. Это осознанно, но требует human-проверки.
- `Modules.init()` до `ConfigManager.load()` впервые включает persistence для всех object-модулей D95–D105; старые профили теперь могут восстановить их state. Это исправление, но при жалобах смотреть порядок загрузки.
- Donor `Animated`/Punchy не портирован; parity `ViewModel` не полный.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №58 — visual KillStreak, D104) — v1.0.127

### Сделано
1. **D104/v1.0.127** (`fc775b6`): donor `KillStreak` visual card — модуль `KillStreakModule` (Visual/World, default ON) и HUD-элемент `KillStreakHud` (top-center), плюс Interface-тумблер `Kill Streak HUD` (default true).
2. **Трекинг**: свои damage-марки из attack-hook и `EntityDamageS2CPacket` (players always, mobs по `Count Mobs`), window 2–60 s (default 10), retention 4.5 s, health/death проверка. SoundsModule продолжает отвечать за голосовые streak-тиры, визуал их не дублирует.
3. **Карточка**: donor-геометрия в уменьшенном виде — tile 20, sword-иконка, overline `KILL STREAK`, 5 dots, tier title (`DOUBLE/TRIPLE/MEGA/MONSTER/UNSTOPPABLE`), `xN`, combo-timer 3 s; fade-out 350 ms. Материал — общий `HudSurface`/`HudStyle` (все 8 стилей/3 формы), без raw GL.
4. **i18n**: 6 ключей `linkenvisuals.hud.killstreak.*` в en/ru/uk; LangParityTest зелёный.
5. **Проверки**: **265 тестов** в обеих flavor-сборках (0 fail/error/skip). Autotest: Linken **881/881**, Strange **871/871**, 1920×1080, 0 fail/skip; скриншот `killstreak-hud` (TRIPLE KILL, x3, dots, timer) и реальная проверка `KillStreakModule.streak() >= 1` после attack-шагов. Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
6. **Сборка**: `linkenvisuals-1.0.127.jar` — 11 029 466 байт, SHA-256 `220BDDC75810BD46AA680898150DCCB2BE4DF434A8E583AD212A8E56D3EE4394`; `linkenvisuals-strange-1.0.127.jar` — 11 029 463 байта, SHA-256 `777B294B43E9777F71136F14B44CF5A40D6A9AF7DCA9A19A8E306B344D153E38`.

### Следующая задача
1. **full ViewModel/Hands** — donor view-model settings поверх существующего HandPose.
2. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
3. Human: KillStreak в живом бою (2–5 килов), RU/UA/AZERTY/QWERTZ, оба flavor-jar, cooldown-тики, multiplayer custom tab.

### Риски
- Карточка использует наш `HudStyle`, а не точный donor glass/glitch/conic/shine слой; donor-эффекты fx≥3 (glitch/conic) и voice не дублировались.
- `Count Mobs`/`Streak Window`/`Combo Timer`/`Theme Accent` работают; donor-настройки voice/volume оставлены SoundsModule.
- HUD-позиция top-center, 0.5/0.07; при 1280×720 карточка уже/ниже, чем donor 1920×1080.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №57 — donor Trajectories, D103) — v1.0.126

### Сделано
1. **D103/v1.0.126** (`90f9b60`): donor `Trajectories` (Visual/World, default OFF) — симуляция дуги 300 шагов, маркер попадания, опциональный explosive-radius 4 блока. Настройки `Explosive Circle` (true) и `Color`.
2. **Поддержка**: Bow (только при натяжении, donor power-curve), Crossbow с Multishot (три луча −10/0/+10°), Ender Pearl, Trident, Splash/Lingering Potion, Experience Bottle, Snowball, Egg. Скорости/углы/гравитация/water-drag из donor.
3. **Маркер**: вместо донорского `hit.png` — процедурное кольцо + 4 зубца в плоскости грани. Текстура `hit.png` через raw-sampler давала большой atlas-квадрат; ассет удалён из порта, форма кольца сохранена. Explosive radius — плоское кольцо `HitRangeGeometry` на `BlockOverlayPipelines.FLAT`.
4. **Проверки**: **265 тестов** в обеих flavor-сборках (0 fail/error/skip; +4 `TrajectoryRulesTest`). Autotest: Linken **862/862**, Strange **852/852**, 1920×1080, 0 fail/skip; скриншот `trajectories` чистый (BlockOverlay временно скрыт на preview). Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
5. **Сборка**: `linkenvisuals-1.0.126.jar` — 11 020 288 байт, SHA-256 `F83D8CA8EFC95FC7446FD16D4E90FC2857464D5041B1AEA463F873DCD72ABE84`; `linkenvisuals-strange-1.0.126.jar` — 11 020 285 байт, SHA-256 `38DA975FCB6C74E6CB5A32CE8F6958828A0F55FE3C7FE5471809052C6503344D`.

### Следующая задача
1. **visual KillStreak** — donor HUD/layer, звуковой streak уже есть.
2. Затем full **ViewModel/Hands**.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
4. Human: проверить Bow/Crossbow multishot/pearl/potions, explosive circle, маркер на разных гранях, оба flavor-jar.

### Риски
- Маркер процедурный (кольцо+зубцы), не 1:1 пиксели donor `hit.png`; текстура удалена из-за raw-sampler atlas-артефакта.
- Explosive Circle срабатывает по ключевым словам имени предмета/эффекта; donor-эвристика по bossbar (server helper) не портирована.
- Для Bow дуга появляется только при активном натяжении; Crossbow multishot требует enchantment component.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №56 — donor Particles, D102) — v1.0.125

### Сделано
1. **D102/v1.0.125** (`0d2d350`): donor `Particles` (Visual/World, default OFF) — 5 texture-режимов (`Bloom`, `Star`, `Snow`, `Firefly`, `Dollar`) + `Cube` wireframe; `Max Particles`, `Size`, `Color`, `Mode`, `Cube Glow`, `Cube Glow Size`, `Cube Rotate Speed`.
2. **Ассеты**: из donor скопированы `star/snow/firefly/dollar.png` в `textures/particles/`; Bloom использует существующий донорский `textures/esp/ghost_bloom.png` (SHA-256 совпадает с donor `client/xuynya/ghost_bloom.png`).
3. **Рендер**: billboard-квады через `POSITION_TEXTURE_COLOR`/`ParticlesPipelines.BILLBOARD` и `END_MAIN`; для raw-pass используются **TRIANGLES** (6 вершин на частицу), не QUADS — первый вариант с QUADS давал длинные артефактные линии. Cube-режим — 12 толстых рёбер двумя перекрещенными квадами на `BlockOverlayPipelines.FLAT`, glow-куб отдельным проходом.
4. **Поведение**: donor spawn rate 42/s, радиус 18, life 50–150 тиков, скорость/DRAG 0.99, лимит 36 спавнов/тик; LOS по центру/углам через `ParticleVisibilityHelper` с кэшем 2 тика; frustum, удаление за 60 блоками/из блоков/из игрока.
5. **Проверки**: **261 тест** в обеих flavor-сборках (0 fail/error/skip; +4 `ParticlesRulesTest`). Autotest: Linken **847/847**, Strange **837/837**, 1920×1080, 0 fail/skip; screenshot `particles` (billboard) и `particles-cube` (wireframe+glow) чистые. Новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
6. **Сборка**: `linkenvisuals-1.0.125.jar` — 11 005 507 байт, SHA-256 `7783DFDC314435E038ABCD52AD1BC179BBAC6BABE1C32858988D52E20C13855F`; `linkenvisuals-strange-1.0.125.jar` — 11 005 504 байта, SHA-256 `8071B3D52B43724D3EBA5F390107E616952A903B1E1AE4DF9AB9B2A160F830D4`.

### Следующая задача
1. **Trajectories** — donor projectile trajectories; отдельный world-render путь + настройки + тест/скриншот.
2. Затем visual **KillStreak**, full **ViewModel/Hands**.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
4. Human: проверить все 6 режимов Particles в живом мире, LOS/кубы/масштаб частиц, оба flavor-jar, cooldown-тики, раскладки, multiplayer custom tab.

### Риски
- Cube-настройки видны во всех режимах (в donor они скрывались предикатом); они работают только при `Mode=Cube`, это осознанное ограничение текущего setting-UI.
- Billboard preview использует временно увеличенный `Size` (1.2) и `Max Particles` (800), затем возвращает 0.25/600.
- Частицы рисуются только при открытом мире и не затрагивают NoRender/Optimization particle culling (наши custom renderers).
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №55 — donor Optimization, D101) — v1.0.124

### Сделано
1. **D101/v1.0.124** (`520bc13`): donor `Optimization` (Visual/World, default OFF) — Tier `Low`/`Medium`/`Ultra`, `Entity Culling`, render-distance cap `0/12/8`, particle cull radius `48/32/22` блоков, cloud culling на Ultra. `Limit Render Distance` и donor `fast transparency` не выставлены как мёртвые контролы: cap является частью tier-поведения, transparency-путь 1.21.4 больше не существует.
2. **OcclusionCuller**: порт donor DDA-рейкаста по центру + 8 углам hitbox, per-tier ray budget `16/24/32`, TTL hidden `150/120/90` мс и visible `450/350/250` мс, кэш по entity id, очистка при смене мира и 5-секундный sweep.
3. **Mixins**: `EntityRenderManager.shouldRender` (`@ModifyReturnValue`), `GameOptions.getClampedViewDistance`, `ParticleManager.addParticle(Particle)`, `WorldRenderer.render` (beginFrame) + `renderClouds` (Ultra cancel). Все четыре зарегистрированы в `linkenvisuals.mixins.json`; `MixinRegistrationTest` зелёный.
4. **Проверки**: **257 тестов** в обеих flavor-сборках (0 fail/error/skip; +4 `OptimizationRulesTest`). Autotest: Linken **805/805**, Strange **795/795**, 1920×1080, 0 fail/skip; mixin apply/injection ошибок нет, новых ERROR/exception/shader/sound нет (только offline-auth/Realms).
5. **Сборка**: `linkenvisuals-1.0.124.jar` — 10 823 585 байт, SHA-256 `65FEFE683ECC7924E2A3B5E00C7DFF2398192BD53626E181758214AB3108ACEE`; `linkenvisuals-strange-1.0.124.jar` — 10 823 582 байта, SHA-256 `355CCB64BBBF0D31B9EDB52D8FE0C625E507B8EA804640C32F0D84F4231BCE88`.

### Следующая задача
1. **Particles** — donor module + particle renderers, по одному слою; не смешивать с NoRender/Optimization culling.
2. Затем **Trajectories**, visual **KillStreak**, full **ViewModel/Hands**.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
4. Human: проверить Optimization в реальном мире (сущности за стенами, render-distance cap, частицы, облака Ultra), оба flavor-jar, cooldown-тики, раскладки, multiplayer custom tab.

### Риски
- Entity culling donor-поведением скрывает сущности за непрозрачными блоками; glowing entities и игрок исключены. Визуальная приёмка — за человеком.
- Render-distance cap при Medium/Ultra применяется всегда (donor-toggle `Limit Render Distance` убран, чтобы не было контрола без эффекта на Low).
- Transparency-путь 1.21.4 не портирован: в 1.21.11 `MinecraftClient.isFabulousGraphicsOrBetter` отсутствует; мёртвую настройку не добавляли.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №54 — compile-time Strange/Linken flavors, D100) — v1.0.123

### Сделано
1. **D100/v1.0.123**: добавлен compile-time flavor. Обычная сборка (`.\gradlew.bat build`) — **только Linken**: Linken main menu, Link GUI, 8 Linken HUD styles/3 shapes; Classic/Beta/Vanilla стили недоступны. Сборка `.\gradlew.bat build -Pstrange` — **только StrangeVisuals**: Vanilla/Beta menu (default Vanilla), Classic/Beta ClickGUI (default Classic), legacy Strange HUD; Linken-стиль недоступен.
2. **BuildFlavor**: `build.gradle` генерирует `dev.linkenvisuals.BuildFlavor` (`NAME`, `STRANGE`) в `build/generated/sources/buildFlavor/java`; jar-имена `linkenvisuals-1.0.123.jar` и `linkenvisuals-strange-1.0.123.jar`. Флаг также принимает `-Pstrange=true`; `-Pstrange=false` = обычная сборка.
3. **StyleFlavor**: единая таблица разрешённых GUI/menu/HUD-стилей. `ModuleRegistry.guiStyle/hudStyle/hudShape` строятся из неё; `setGuiStyle`, `cycleGuiStyle`, `StyleManager.setStyle/setStyleSilently`, загрузка `menuStyle` из конфига и кнопки редактора меню проходят sanitize. Сохранённый конфиг другого семейства при загрузке молча заменяется default-стилем этого jar.
4. **Strange HUD**: `HudStyle.Variant.LEGACY` восстанавливает pre-D83 `HudSurface` (glass + body `0x0E0F14` + NeonEdge). В Strange build `HudStyle.variant()` всегда LEGACY, `shape()` всегда ROUNDED; `HUD Style`/`HUD Shape` не выводятся в ClickGUI (нет мёртвых контролов).
5. **UI**: в Linken build кнопки style-toggle и menu editor в Video Settings скрыты; в Strange build они показываются, editor рисует только Beta/Vanilla. `TitleScreen` в Strange default — ванильный с edition-текстом `STRANGE VISUALS`; в Linken — LinkenMainMenuScreen.
6. **Проверки**: **253 теста** в каждой сборке (0 fail/error/skip; +3 `StyleFlavorTest`). Скрытые прогоны: Linken **797/797**, `Active Style: Linken`; Strange **787/787**, `Active Style: Vanilla`; 1920×1080, 0 fail/skip. Новых ERROR/exception/shader/sound нет, только прежние offline-auth/Realms.
7. **Сборка**: `linkenvisuals-1.0.123.jar` — 10 812 034 байта, SHA-256 `B5F5EC47870E9B7E7418D10BFB5810CA1CA9065ABBF664D409375A34D6A2D7BD`; `linkenvisuals-strange-1.0.123.jar` — 10 812 031 байт, SHA-256 `E220F083045F7BF99AAD82E42CCA99FDD94CE8649B8917DE5B31E2EDFE64F4FF`.

### Следующая задача
1. **Optimization** — donor Tier Low/Medium/Ultra, entity culling, render-distance cap, particle distance culling, transparency/cloud logic; только поведение, работающее на 1.21.11.
2. Затем Particles / Trajectories / visual KillStreak / full ViewModel/Hands.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
4. Human: проверить оба jar в реальном запуске (Linken-стиль и Strange Vanilla/Classic/legacy HUD), переключение конфигов между flavor, живые cooldown-тики, раскладки, multiplayer custom tab, pixel diff.

### Риски
- Strange HUD — восстановленный pre-D83 glass/neon слой; визуальная приёмка выполнена скриншотами 1920×1080, но пиксельный diff с оригинальным StrangeVisuals не заявлен.
- Strange build разрешает Beta как второй menu-стиль и Beta как второй ClickGUI; Linken полностью заблокирован. Если нужен строго один Vanilla/Classic, это отдельное решение.
- Оба jar имеют один mod id (`linkenvisuals`); одновременно в mods их ставить нельзя.
- Полный donor parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №53 — NoRender mixin registration fix, D99) — v1.0.122

### Сделано
1. **D99/v1.0.122** (`ed53c0a`): при аудите D98 обнаружено, что `BossBarHudMixin`, `InGameOverlayRendererMixin` и `ParticleManagerMixin` **не были зарегистрированы** в `linkenvisuals.mixins.json` — Boss Bar, Fire и Particles у NoRender были мёртвыми контролами. Все три добавлены в `client`.
2. **ParticleManagerMixin**: в 1.21.11 метода `renderParticles` больше нет; target исправлен на `addToBatch(SubmittableBatch, Frustum, Camera, float)` с `@At("HEAD")` cancel. Clean build больше не выдаёт remap-warning.
3. **InGameOverlayRendererMixin**: сигнатура исправлена на donor-метод `renderFireOverlay(MatrixStack, VertexConsumerProvider, Sprite)`; отменяется только fire-слой, underwater/in-wall остаются vanilla. `BossBarHudMixin.render(DrawContext)` уже совпадал.
4. **Регрессия**: `MixinRegistrationTest` читает `linkenvisuals.mixins.json` и требует, чтобы каждый `*Mixin.java` из `dev/linkenvisuals/mixin` был в `client`/`server`/`mixins`. Это ловит именно D97-класс ошибок.
5. **Проверки**: **250 тестов, 0 падений/ошибок/скипов**; clean build зелёный, remap-warning `renderParticles` исчез; скрытый autotest **799/799**, 0 fail/skip, 1920×1080; mixin apply/injection ошибок и новых ERROR/exception нет (только прежние offline-auth/Realms).
6. **Сборка**: jar `linkenvisuals-1.0.122.jar` (10 805 739 байт, SHA-256 `F1BFDBE9C4652017A2DE563CBE0B670C6CA560C4F95D015698A57809F5F4890A`).

### Следующая задача
1. **Optimization** — donor Tier Low/Medium/Ultra, entity culling, render-distance cap, particle distance culling, transparency/cloud logic; только поведение, работающее на 1.21.11.
2. Затем Particles / Trajectories / visual KillStreak / full ViewModel/Hands.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2.
4. Human: живой bossbar/fire/particles-прогон NoRender, красное состояние HitRange, LOS/self/items CustomHitBox, cooldown-тики, раскладки, multiplayer custom tab, pixel diff.

### Риски
- NoRender Particles отменяет весь vanilla `addToBatch`; кастомные world-render эффекты мода не затрагиваются.
- Fire-миксин отменяет только fire overlay; underwater/in-wall overlays не скрываются.
- Bossbar/fire/particles проверены apply-загрузкой и autotest-свипом настроек, но без отдельной боевой сцены с боссом/огнём/частицами — это human-проверка.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №52 — donor HitRange + CustomHitBox, D98) — v1.0.121

### Сделано
1. **D98/v1.0.121** (`acd46e7`): donor `HitRange` (Visual/World, default OFF) — вращающееся кольцо радиуса `3.32`, 72 сегмента, толщина/скорость/`Depth Test`, красный цвет при видимом игроке в радиусе; рисуется на `END_MAIN` как triangle-list лента через проверенный `BlockOverlayPipelines.FLAT` (не GL-линии), цвет — байтовый `POSITION_COLOR`. Отдельный `FLAT_NO_DEPTH` pipeline для выключенного depth-test, без глобальных RenderSystem-мутаций.
2. **D98/v1.0.121**: donor `CustomHitBox` (Visual/World, default OFF) — fill/outline боксы, `Fill`/`Fill Alpha`/`Line Width`/`Color`/`Show Self`/`Show Items`; дистанция `128`, frustum-отсечка, LOS-рейкасты по центру и 10 запасным точкам с кэшем `120 мс`; donor-геометрия 36 fill-вершин и 144 outline-вершин на сущность, camera-relative.
3. **Тесты**: `HitRangeGeometryTest` (6) — вершины на inner/outer окружности, ненулевая площадь каждого треугольника, wrap вращения, finite-радиус; `CustomHitBoxGeometryTest` (3) — 6 граней fill, 12 толстых рёбер, дистанция/alpha/thickness правила. Итого **249 тестов, 0 падений/ошибок/скипов**.
4. **Autotest**: **799/799**, 0 fail/skip, 1920×1080; скриншоты `lv-autotest-20-hitrange.png` (чистое кольцо) и `lv-autotest-21-custom-hitbox.png` (заполненный/обведённый бокс вокруг тестовой коровы). Новых ERROR/exception, shader-source и unknown-sound-event ошибок нет; в `latest.log` только прежние offline-auth/Realms ERROR.
5. **Сборка**: jar `linkenvisuals-1.0.121.jar` (10 805 529 байт, SHA-256 `CAC73CD84DD5B6F00E5A76899B0EB8D634E983D5FA2854463BCD4DE66C40B9F1`).

### Следующая задача
1. **Optimization** — donor Tier Low/Medium/Ultra, entity culling, render-distance cap, particle distance culling, transparency/cloud logic; только поведение, работающее на 1.21.11, без мёртвых настроек.
2. Затем Particles / Trajectories / visual KillStreak / full ViewModel/Hands — по одному модулю, с проверкой world-render пути и скриншотом.
3. HUD-остатки: HelperItems (blocked), DynamicIsland (скрыт Link style), Armor V2 (нужен screen-anchored слой).
4. Human: живые cooldown-тики, RU/UA/AZERTY/QWERTZ, low-armor в бою, multiplayer custom tab, pixel diff 1280×720/1920×1080; отдельно — красное состояние HitRange против второго игрока и LOS/self/items у CustomHitBox.

### Риски
- HitRange краснеет только от видимых non-spectator игроков; в singleplayer это состояние не воспроизводится, нужен второй игрок.
- CustomHitBox следует donor-набору сущностей (все живые, кроме XP-orbs; предметы — по toggle), а не только `LivingEntity`.
- Autotest-скриншот CustomHitBox использует временно повышенные `Fill Alpha`/`Line Width` и тестовую корову; после шага значения возвращаются к 90/1.5.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №51 — donor NoRender, D97) — v1.0.120

### Сделано
1. **D97/v1.0.120** (`5a6b48d`): donor `NoRender` module (Visual/World, default OFF) — Hurt Camera, Fire, Totem Pop, Scoreboard, Boss Bar, Particles, Armor, Inventory Background.
2. **Mixins**: hurt camera/totem, fire overlay, bossbar, particle render, armor layers, vanilla sidebar, HandledScreen background; `ScoreboardHud` скрывается при NoRender scoreboard.
3. **Проверки**: **240 тестов, 0 падений**; скрытый autotest **749/749**, 1920×1080, ошибок нет.
4. **Сборка**: jar `linkenvisuals-1.0.120.jar` (10 785 684 байта, SHA-256 `48CC40CE7C19456A4C10D9E18E243AFFC855EF50F234CE0BC9BB32ED29B056DC`).

### Следующая задача
- HitRange (renderer не принят из-за артефактов), CustomHitBox, Optimization, Particles, Trajectories, visual KillStreak, full ViewModel/Hands.
- HUD: HelperItems (blocked), DynamicIsland (donor скрывает в Link), Armor V2.
- Human: cooldown ticks, RU/UA/AZERTY/QWERTZ, low-armor, multiplayer tab, pixel diff.

### Риски
- NoRender armor скрывает все armor-слои, включая Rudy-исключение (как donor-toggle).
- Particles скрывает только vanilla `ParticleManager`, не наши custom renderers.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №50 — donor Aspect Ratio, D96) — v1.0.119

### Сделано
1. **D96/v1.0.119** (`9849613`): donor `Aspect Ratio` module (Visual/World, default OFF), slider 0.1–5.0 default 1.75; `GameRendererMixin.getBasicProjectionMatrix` заменяет `m00 = m11 / ratio`.
2. **ProjectionMath**: чистая функция + `ProjectionMathTest`; проверены invalid/null и сохранение вертикальных членов.
3. **CameraSettings**: не дублировал — smooth-F5 уже покрыт `Better Animations`/`Animations.perspective`.
4. **Проверки**: **239 тестов, 0 падений**; скрытый autotest **730/730**, 1920×1080, ошибок нет.
5. **Сборка**: jar `linkenvisuals-1.0.119.jar` (10 780 793 байта, SHA-256 `DA38FA327C6D7EC6186F8D508D2C496862987AABFAB7970942CA28D6501E6E71`).

### Следующая задача
- Visual: CustomHitBox, HitRange, NoRender, Optimization, Particles, Trajectories, KillStreak, ViewModel/Hands.
- HUD: HelperItems (blocked без donor helper-модулей), DynamicIsland (donor скрывает в Link), Armor V2.
- Human: cooldown ticks, RU/UA/AZERTY/QWERTZ, low-armor, multiplayer tab, pixel diff.

### Риски
- Aspect Ratio меняет только projection matrix; UI/HUD не масштабируются — как в donor.
- CameraSettings покрыт существующей анимацией, не отдельным модулем.
- Полный parity не закрыт; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-23 (чат №49 — HUD batch: Cooldowns/Armor/Inventory/LowArmor, D95) — v1.0.118

### Сделано
1. **D95/v1.0.118** (`ed4d241`): **`CooldownsHud`** — donor-список кулдаунов: заголовок + иконка, строки item/name/time, preview трёх предметов в edit-mode, цвета по progress/remaining. **`CooldownTracker`** читает 1.21.11 `ItemCooldownManager` (`Identifier → Entry(startTick,endTick)`) через reflection + record-accessors, при недоступности — скан main/offhand inventory; время округляется вверх до секунд.
2. **`ArmorHud` (V1)**: donor-карточка 100×39, четыре inset-ячейки 17 px, иконка + процент прочности под слотом, preview железного набора в edit-mode. Режим donor V2 (две группы у хотбара) **не выставлен**: текущий HUD-слой не умеет screen-anchored элементы.
3. **`InventoryHud`**: 3 ряда main inventory (27 слотов), опция `Inventory Hotbar` добавляет 4-й ряд и разделитель; слоты inset, item scale 0.9 + stack overlay.
4. **`LowArmorNotificationsHud`**: пороги 50%/25%, до 4 сообщений, 3.5 s, fade, preview в edit-mode; строки en/ru/uk, интеграция с `ArmorHud.durabilityPercent`.
5. **Настройки ClickGUI Interface**: `Cooldowns HUD` (true), `Armor HUD` (true), `Inventory HUD` (false), `Inventory Hotbar` (false), `Low Armor Notifications` (true). Все элементы в `HudManager.ELEMENTS` — drag/snap/edit-mode работают.
6. **Проверки**: **237 тестов** (+6: `CooldownsHudTest`, `ArmorHudTest`, `InventoryHudTest`, `LowArmorNotificationsHudTest`), 0 падений/ошибок/скипов; clean build зелёный; autotest **723/723**, 0 fail/skip; новых ERROR/exception/shader/sound ошибок нет.
7. **Сборка**: jar `linkenvisuals-1.0.118.jar` (10 779 551 байт, SHA-256 `F6A2482C353617B652864409E8FE1B1A43207A135A889C467391154F1E0CA1F5`).

### Следующая задача
1. **HelperItemsHud** — заблокирован: donor-модули `FtHelper/HwHelper/RwHelper` с их bind-настройками в порт не входят; делать HUD без реального источника данных нельзя. Вариант: сначала портировать helper-модули, затем HUD.
2. **DynamicIsland** — donor сам скрывает остров при `LinkHudStyle.active()`; в нашем style-lock он невидим, медиа уже покрыто `MediaPlayerHud`, lyrics — `LyricsHud`. Отдельный элемент не добавлять без смены style-lock.
3. **Armor V2** — только после screen-anchored HUD-слоя (hotbar-relative текущий `HudElement` не поддерживает).
4. Дальше visual-модули: `CameraSettings`, `AspectRatio`, `CustomHitBox`, `CustomVitals`, `DamageParticles`, `FortniteDamage`, `HitRange`, `ItemRadius`, `NoRender`, `Optimization`, `Particles`, `Trajectories`, `KillStreak`, full `ViewModel`/Hands.
5. Human-проверка: low-armor notifications в бою, реальные cooldown-тики (не только preview), раскладки RU/UA/AZERTY/QWERTZ.

### Риски
- Reflection-путь `CooldownTracker` проверен в autotest только без активных кулдаунов (preview/fallback); живой cooldown — human-проверка.
- `Inventory HUD` большой (192×72) и на GUI scale 3 занимает заметную часть справа; default off, двигается/масштабируется стилем.
- `LowArmorNotifications` работает только пока включён HUD-элемент; сообщения не дублируются при повторном входе в мир.
- Полный 100% pixel parity не заявлен.

## Состояние на 2026-10-22 (чат №48 — layout-aware text shortcuts, D94) — v1.0.117

### Сделано
1. **D94/v1.0.117** (`c72fd70`): новый `LayoutKeys` принимает **и физическую US-клавишу, и напечатанный символ** через `GLFW.glfwGetKeyName` + Cyrillic-алиасы (`а/с/м/ч/я/н` → `a/c/v/x/z/y`). Ctrl/Cmd-шорткаты работают на US, AZERTY, QWERTZ и кириллице.
2. **`TextInput`**: общий редактор для кастомных полей — caret/selection, Ctrl+A/C/V/X, Ctrl+Z/Y (+Shift+Z), Ctrl+Backspace/Delete, Left/Right/Home/End, Ctrl+Insert/Shift+Insert/Shift+Delete, undo/redo (64 шага), maxLength/filter.
3. **Vanilla `TextFieldWidget`**: mixin обрабатывает layout-aware Ctrl-шорткаты до vanilla, ведёт undo-историю на `write`/`erase`. Покрывает чат, наковальню, creative search, адрес сервера и все ванильные поля.
4. **LINKEN/ClickGUI**: multiplayer, Alt Manager, Language, Keybinds, Resource Packs, SubSettings, Select World, LinkGUI search/config/side input, ConfigPanel, Beta/Classic search переведены на `TextInput`.
5. **Проверки**: **231 тест** (8 новых), 0 падений/ошибок/скипов; clean build зелёный; autotest **697/697**, 0 fail/skip; mixin apply/shader/sound ошибок нет.
6. **Сборка**: jar `linkenvisuals-1.0.117.jar` (10 754 036 байт, SHA-256 `A8A17B389F80560AB9513DEBE4C913FD08B61500D3E4A6954AACCB01F4AD67DF`).

### Следующая задача
- Human-проверка шорткатов на RU/UA/AZERTY/QWERTZ в чате, creative search, multiplayer Add/Edit, Alt Manager, LinkGUI.
- Затем HUD: Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Selection в кастомных полях не подсвечивается (caret есть, visual selection — нет); функционально Ctrl+A/C/X работают.
- Cyrillic-алиасы покрывают только шесть shortcut-букв; остальные раскладки — physical US + printed Latin.
- Classic/Beta search синхронизируют `searchQuery` вручную; при новых полях использовать `TextInput`.
- Полный 100% pixel parity не заявлен.

## Состояние на 2026-10-22 (чат №47 — LINKEN splash вместо Mojang, D93) — v1.0.116

### Сделано
1. **D93/v1.0.116** (`45e6086`): `SplashOverlayMixin` теперь отменяет vanilla `SplashOverlay.render` с первого кадра и сам ведёт состояние reload (как donor): progress lerp, `reloadCompleteTime`, `exceptionHandler`, hand-off `setOverlay(null)`.
2. **Bootstrap**: до готовности shaders/атласов рисуется `LinkenLoadingScreen.renderBootstrap` только на vanilla `fill/fillGradient` — тёмный фон, accent progress, уголки. Vanilla-текст в bootstrap **удалён**, потому что `minecraft:core/rendertype_text` ещё не загружен и давал `Couldn't find source`/`Couldn't compile`.
3. **После readiness**: тот же overlay переключается на полный `LinkenLoadingScreen` (linken., VISUALS, стадии, прогресс, footer). Mojang-логотип больше не появляется в LINKEN-стиле.
4. **Runtime-проверка**: покадровый capture 1280×720 — сначала тёмный LINKEN progress, затем полный splash `linken.`; main menu достигается, `Couldn't find source`/`Couldn't compile` — 0.
5. **Проверки**: **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; autotest **697/697**, 0 fail/skip; новых ERROR/exception, shader/sound ошибок нет.
6. **Сборка**: jar `linkenvisuals-1.0.116.jar` (10 745 004 байт, SHA-256 `AED88ACD4B4CB9975AF9FEDECEF85F084B99CCEF606BF7F4C1E4F523C3832DDB`).

### Следующая задача
- Handwritten Lottie `welcome.json` из donor **не портирован** (нужен Skija; вне текущего scope) — при желании заменить лёгкой welcome-анимацией на наших шрифтах.
- Остаток title hover/press, real server rows/favicon; затем Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland HUD.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Первые ~0.2 с bootstrap показывает фон+progress без wordmark (vanilla font ещё не загружен); затем полный splash.
- `SplashOverlayMixin` вручную завершает reload; F3+T/resource-pack reload проверен только логикой, не отдельным human-прогоном.
- Donor Lottie welcome (рукописный «welcome») отсутствует; это осознанный остаток.
- Полный 100% pixel parity не заявлен.

## Состояние на 2026-10-22 (чат №46 — media/hatching/favicon pixel pass, D92) — v1.0.115

### Сделано
1. **D92/v1.0.115** (`b984fd1`): media-cover переведён на donor-градиент `0x37302A→0x12100E`; `by` рисуется `headingLight` 11 с +2 px baseline, как в donor.
2. **Server window**: добавлена donor-штриховка `repeating-linear-gradient(-45deg, white 0.014, transparent 1px 10px)` — 1 px линии через повёрнутые rounded-rect квады, шаг 14.142 design px.
3. **Favicon**: PNG декодируется один раз, CPU-маска скругляет углы (радиус 6/32) до upload; donor-подобный rounded favicon без отдельного шейдера.
4. **Проверки**: RU title/server скриншоты; **697/697**, 0 fail/skip; **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; новых ERROR/exception, shader/sound ошибок нет.
5. **Сборка**: jar `linkenvisuals-1.0.115.jar` (10 743 565 байт, SHA-256 `41553852CAF77D13BF3EE824890BB7C325D100815A696F8947BFCB46CEC35251`).

### Следующая задача
- Реальные server rows с MOTD/players/ping/favicon на пользовательском списке; hover/press title.
- Затем Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland HUD.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Pinned-сервера донора отсутствуют (network out of scope).
- Остальные LINKEN-экраны используют `wtmico`/`iconsfa`; main menu — `icons2`.
- Профиль по умолчанию `language: en_us`; donor UI — RU. Autotest 1920×1061 из-за border окна.
- Полный 100% pixel parity не заявлен.

## Состояние на 2026-10-22 (чат №45 — donor icons2 main-menu set, D91) — v1.0.114

### Сделано
1. **D91/v1.0.114** (`9883204`): скопирован donor-атлас `icons2` (`fonts/msdf/icons2.json/png/mcmeta`), добавлен `LinkenFonts.icons2()`; main-menu кнопки/account-meta/socials переведены с `wtmico`/`brands` на donor-глифы `MENU_ICON_*` (`A/B/C/D/E/F/H`). Social «code» теперь donor-иконка `H`, а не текст `</>`.
2. **Визуал**: RU title screenshot — иконки multiplayer (globe), singleplayer (person), settings (gear), quit (power), Telegram/Discord/LV/code совпадают с donor-набором; social-tiles сохраняют donor-градиент D90.
3. **Проверки**: **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; скрытый runAutotest (RU) **697/697**, 0 fail/skip, 1920×1080; новых ERROR/exception, shader/sound ошибок нет.
4. **Сборка**: jar `linkenvisuals-1.0.114.jar` (10 742 664 байт, SHA-256 `66217AE9EDDBE0192925AB4854C6EB52626015B0B9AB555644D42D4C943B5543`).

### Следующая задача
- Реальные server rows/favicons (MOTD/players/ping) на пользовательском списке; favicon rounded-clip.
- Остаток title media/hover/press-анимаций; затем Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland HUD.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- `icons2` применён только к main menu; остальные LINKEN-экраны пока используют `wtmico`/`iconsfa`.
- Donor pinned-сервера отсутствуют (network out of scope); favicon без rounded-clip, hatching окна нет.
- Профиль по умолчанию `language: en_us`; donor UI — RU. Скриншоты autotest 1920×1061 из-за border окна.
- Полный 100% pixel parity не заявлен.

## Состояние на 2026-10-22 (чат №44 — menu material/footer pixel pass, D90) — v1.0.113

### Сделано
1. **D90/v1.0.113** (`c1a9b7f`): secondary-кнопки и social-tiles переведены с плоской заливки на donor вертикальный градиент `0x181E1B→0x0F1311` (alpha 217), верхний highlight и border 23→61; account-avatar — donor-градиент `0x2A3830→0x101613`.
2. **Footer**: RU meta-строка приведена к donor lowercase (`текущий аккаунт · лицензия`); `DISCLAIMER_LINE_HEIGHT` 16.5→21.0, из-за чего social-tiles/disclaimer встали на donor-уровень (проверено crop’ом 1920×1080).
3. **Проверки**: RU-прогон (`lang:ru_ru`, hidden 1920×1080) — title/server/Add-modal скриншоты; **697/697**, 0 fail/skip; **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; новых ERROR/exception, shader/sound ошибок нет.
4. **Сборка**: jar `linkenvisuals-1.0.113.jar` (10 708 899 байт, SHA-256 `ACF84E88C101F902A7F182177573D5DD8BDFDF6191EB212FA35CF0D304FFA03B`).

### Следующая задача
- Дальше pixel-diff title/menu: иконки, media-card, hover/press-анимации; затем реальные server rows/favicons.
- После меню — Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland HUD.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Полный 100% pixel parity не заявлен: donor pinned-сервера отсутствуют (network out of scope), favicon без rounded-clip, hatching окна не портирован.
- Текущий профиль `linkenvisuals_config.json` = `language: en_us`; donor UI хардкодит RU. Для сверки ставить RU.
- Скриншоты autotest имеют высоту 1061 (window border), не 1080; fullscreen-сверка — за человеком.

## Состояние на 2026-10-22 (чат №43 — donor server browser, D89) — v1.0.112

### Сделано
1. **D89/v1.0.112** (`05111f2`): `LinkenMultiplayerScreen` переписан с 3×3-сетки на donor 4b list-layout: окно 1000×272 (topbar 68, rows 64, footer 76), `WIN_TOP=168`, padding 22, search 320 (+24 при фокусе), refresh 36×36, back-chip 38, account-pill под wordmark, clock/version/music chrome.
2. **Список**: row tile 38, favicon/letter tile, name 11.5 + version chip 9, MOTD 12, players mono 11, ping-bars 4×3 px, selected accent bar/фон, hover, smooth scroll, двойной клик = join; empty state и Add.
3. **Формы**: donor-размер 460, поля 36, labels 11/12.5, resource-pack segmented choice (Disabled/Prompt/Enabled), primary/cancel 44, `Modal.ADD/EDIT/DIRECT/DELETE`; сохранение policy в `ServerInfo`.
4. **i18n**: 29 новых ключей `linkenvisuals.multiplayer.*` в en/ru/uk (title/back/search/join/add/direct/empty/not_found/change/ms/forms/packs); LangParityTest зелёный.
5. **Autotest**: +5 шагов — open Linken multiplayer, screenshot `multiplayer.png`, open Add modal, screenshot `multiplayer-add.png`, close. Итог **697/697**, 0 fail/skip, 1920×1080.
6. **Проверки**: **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; новых ERROR/exception, shader-source/compile и unknown sound events — 0. `latest.log` содержит только прежние offline-auth/Realms ERROR.
7. **Сборка**: jar `linkenvisuals-1.0.112.jar` (10 708 865 байт, SHA-256 `B3819CEA7FF717FA79A9C8B5022D743855B6DC1E9D447F4A41C3CAC6EDA91994`).

### Следующая задача
- Доводка pixel-diff title/menu (тексты/иконки/footer) и server forms; donor backend pinned servers не портированы (network out of scope).
- Проверка списка с реальными серверами и favicon-ами; human visual acceptance.
- HUD-элементы дальше: Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Donor pinned-сервера приходят с бэкенда (`UserSyncManager`), у нас их нет; локальный список показывает пустое состояние.
- Favicon рисуется квадратом 32 внутри tile 38 без rounded-clip; hatching окна не портирован.
- Метки в скриншотах EN, потому что `linkenvisuals_config.json` = `language: en_us`; donor UI хардкодит RU. Для пиксельной сверки ставить RU.
- Полный donor parity не закрыт; открытые пункты в `FEATURE_PARITY.md`.

## Состояние на 2026-10-22 (чат №42 — donor CoordinatesHud, D88) — v1.0.111

### Сделано
1. **D88/v1.0.111** (`16be18e`): портирован donor `CoordinatesHud`. Карточка 17 px: compass-глиф `J` из `iconsnew` 9 px, integer `X Y Z` шрифтом 7.5, bullet 4 px, `BPS` 6.5, padding 5, animated width 200 мс; панель — `HudSurface`/`HudStyle` (все 8 стилей и border smoothness).
2. **Поведение**: координаты `floor(player.X/Y/Z)`, BPS с двумя знаками, self-preview в edit-mode/чате; настройка `Coordinates HUD` (BooleanSetting, default OFF) в Interface-группе; элемент в `HudManager.ELEMENTS`, drag/snap работают. Общий `HudTween` вынесен из TargetHud и переиспользован.
3. **BPS**: в 1.21.11 поля `prevX/prevZ` недоступны, поэтому скорость считается как `hypot(velocity.x, velocity.z) * 20` — эквивалент donor per-tick displacement.
4. **Autotest**: +5 шагов (`enable`, `open preview`, `check`, `screenshot coordinates-hud`, `close`); снят `coordinates-hud.png` (compass, `-130 67 617`, `0.00 BPS`). Дополнительно `summonMobs` расширен до 8 целей, чтобы 4 attack-шага не скипались после sweep/раннего kill.
5. **Проверки**: **223 теста, 0 падений/ошибок/скипов**; clean build зелёный; скрытый runAutotest **692/692**, 0 fail/skip, 1920×1080; новых ERROR/exception, shader-source/compile и unknown sound events — 0. `latest.log` содержит только прежние offline-auth/Realms ERROR.
6. **Сборка**: jar `linkenvisuals-1.0.111.jar` (10 699 109 байт, SHA-256 `3BED0F4CC5E7CD3BF1100FB1BDA444DDFBA4BAA1C4E2A3B05339DED2D4D0AFD2`).

### Следующая задача
- `CooldownsHud` — следующий HUD-элемент по donor `hud/impl/CooldownsHud.java`, затем Armor/Inventory/HelperItems/LowArmor/DynamicIsland.
- Visual-модули из `FEATURE_PARITY.md`; human sound/visual acceptance; multiplayer Custom Tab.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- CoordinatesHud проверен self-preview’ом и idle BPS; движение/смена знаков BPS — за человеком.
- Donor middle-click copy координат не портирован (нет видимого контрола и отдельного notify API).
- Default-позиция карточки — top-left под watermark, не donor center-spawn; выбрана, чтобы не пересекаться с TargetHud в нашем layout.
- Полный donor parity не закрыт; открытые пункты в `FEATURE_PARITY.md`.

## Состояние на 2026-10-22 (чат №41 — donor TargetHud, D87) — v1.0.110

### Сделано
1. **D87/v1.0.110** (`dc7abd0`): портирован donor `TargetHud` в нашу `HudElement`-архитектуру. Карточка сохраняет donor-геометрию 104×30 (+25 px ряд offhand/брони), head 20, padding/gap 4, item cells 15/4, name 7.6, HP 6.6, % 4.6; панель — `HudSurface`/`HudStyle` (все 8 стилей и border smoothness работают).
2. **Поведение**: цель под crosshair в радиусе 3.5 либо в aim-cone с hitbox margin 0.75 и LOS-рейкастом; удержание последней цели 1000 мс; фильтры invisibility и friends (LinkGUI friends); в edit-mode/чате — self-preview. HP/absorption с округлением 0.1 и donor-форматом K/M, animated health bar, hit flash, hurt-tint головы, durability %, streamer-санитизация имени, кэши строк/предметов/друзей.
3. **Настройка**: `Target HUD` (BooleanSetting, default ON) в Interface-группе LinkGUI; элемент добавлен в `HudManager.ELEMENTS`, drag/snap/edit-mode работают. `HudStyle.inset` получил публичный overload для donor-ячеек.
4. **Autotest**: добавлены 5 шагов (`enable target hud`, `open target hud preview`, `check target hud`, `screenshot target hud`, `close target hud preview`); снят `target-hud.png` с self-preview (имя, 20.0 HP, броня/проценты, бар).
5. **Проверки**: **220 тестов, 0 падений/ошибок/скипов**; clean build зелёный; скрытый runAutotest **685/685**, 1920×1080, 0 fail/skip; новых ERROR/exception, `Couldn't find source`/`Couldn't compile`, unknown sound events — 0. `latest.log` содержит только прежние offline-auth/Realms ERROR.
6. **Сборка**: jar `linkenvisuals-1.0.110.jar` (10 694 415 байт, SHA-256 `4254909F27C39A3ACF4E808B31CD93B48F29BB5DB499117B39297E111935C9BF`).

### Следующая задача
- `CoordinatesHud` — следующий HUD-элемент по donor `hud/impl/CoordinatesHud.java`, затем Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland.
- Visual-модули из `FEATURE_PARITY.md`; human sound/visual acceptance; multiplayer Custom Tab.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- TargetHud проверен self-preview’ом в edit-mode; живая цель (другой игрок) и multiplayer-поведение — за человеком.
- Donor middle-click copy ника не портирован (нет видимого контрола и отдельного notify API); friend-фильтр использует LinkGUI friends, не donor FriendManager.
- Hidden-run: minimize ставит singleplayer на паузу, autostart не срабатывает; нужен `pauseOnLostFocus:false` + окно за пределами desktop (`SetWindowPos`, не `SW_MINIMIZE`), затем восстановить `options.txt`.
- Высота карточки меняется при появлении/исчезновении брони; при первом кадре возможен сдвиг на половину ряда (25 px), далее стабильно.
- Полный donor parity не закрыт; открытые пункты в `FEATURE_PARITY.md`.

## Состояние на 2026-10-22 (чат №40 — donor border smoothness, D86) — v1.0.109

### Сделано
1. **D86/v1.0.109** (`376c532`): добавлен `rounded_rect_border` pipeline (POSITION_TEXTURE_COLOR_NORMAL), thickness/internal/external smoothness через normal, radius через Position.z; `HudStyle` использует donor-пары smoothness для всех 8 стилей.
2. **Исправление**: `external` — reserved word GLSL, переименовано; runtime shader compile error устранён.
3. **Проверки**: **218 тестов, 0 падений**; скрытый autotest **678/678**, 1920×1080, `Couldn't compile`/unknown sound events нет.
4. **Сборка**: jar `linkenvisuals-1.0.109.jar` (10 682 349 байт, SHA-256 `AAEEAA5874602EEC1B533F936787F322E921F839B6660B91CEC0C31F03A6F31A`).

### Следующая задача
- HUD-элементы: TargetHud → CoordinatesHud → CooldownsHud → ArmorHud → InventoryHud → HelperItemsHud → LowArmorNotificationsHud → DynamicIsland.
- Visual-модули из `FEATURE_PARITY.md`; human sound/visual acceptance; multiplayer Custom Tab.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Полный donor parity не закрыт; открытые пункты в `FEATURE_PARITY.md`.
- HUD Style применяется к `HudSurface`; внутренние радиусы отдельных элементов могут отличаться.
- Border smoothness теперь donor-подобный, но нормализация/масштаб проверены визуально на 1920×1080, не на всех GUI scale.

## Состояние на 2026-10-22 (чат №39 — all donor HUD styles, D85) — v1.0.108

### Сделано
1. **D85/v1.0.108** (`e5064b7`): все 8 donor HUD-стилей (Card, Minimal, Neon, Aurora, Glass, Solid, Gradient, Outline) переведены на donor-цвета/градиенты/радиусы/альфы. `RoundedRectPipeline` получил per-vertex vertical/horizontal gradients; Card/Minimal проверены crop’ами — HUD не пропадает.
2. **Autotest**: 8 отдельных скриншотов `hud-*` с задержкой 10 тиков; прогон **678/678**, новых ERROR/exception нет.
3. **Сборка**: **218 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.108.jar` (10 679 178 байт, SHA-256 `1A471D94BFE6AF27D8D55F0D7834FE988675F2974767AA58F70FA112940AB6F3`).

### Следующая задача
- Довести border smoothness до donor `drawBorder` (internal/external smoothness) — сейчас толщина/альфа совпадают, smoothness аппроксимирован.
- Затем HUD-элементы: Target/Coordinates/Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland.
- Human sound/visual acceptance, multiplayer Custom Tab.

### Риски
- Полный pixel-perfect border pass ещё не закрыт.
- HUD Style применяется к `HudSurface`; часть внутренних радиусов элементов остаётся собственной.
- Полный donor parity не заявлен; см. `FEATURE_PARITY.md`.

## Состояние на 2026-10-22 (чат №38 — donor HUD Minimal, D84) — v1.0.107

### Сделано
1. **D84/v1.0.107** (`fbf91af`): второй donor-стиль HUD **Minimal** приведён к 1:1-логике — low-alpha body `hudShade(0.02, 120α)`, radius cap 4, без border, вертикальный gradient rail 2.2 px primary→secondary с donor inset. Раньше был solid accent-bar + white outline.
2. **Autotest**: добавлен шаг Minimal HUD + `hud-minimal.png`; прогон **664/664**, 1920×1080, новых ERROR/exception нет.
3. **Сборка**: **218 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.107.jar` (10 677 372 байта, SHA-256 `61313B44441B9B99B16DE5F8B839E3D0182E7C8CB58D44438ECBDFCC115634FA`).

### Следующая задача
- Продолжать parity по `FEATURE_PARITY.md`: Target/Coordinates/Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland HUD, затем visual-модули.
- Проверить Custom Tab на multiplayer; human sound/visual acceptance.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Minimal применён к `HudSurface`; отдельные внутренние радиусы HUD-элементов остаются собственными.
- Полный donor parity не закрыт; открытые пункты перечислены в audit.
- Custom Tab визуально не подтверждён в singleplayer.

## Состояние на 2026-10-22 (чат №37 — donor HUD parity, D83) — v1.0.106

### Сделано
1. **D83/v1.0.106** (`7bfc584`): HUD Style (8) и HUD Shape (3) добавлены в Interface-группу LinkGUI и применены центрально через `HudStyle`/`HudSurface`; Interface-секция получила scroll/scrollbar.
2. **Custom Scoreboard**: новый `ScoreboardHud` + настройки Custom Scoreboard/Radius/Opacity; vanilla sidebar отменяется, пока custom включён; autotest создаёт временный objective и снимает `scoreboard.png`.
3. **Custom Tab**: настройки Custom Tab/Colour/Opacity + mixin background по захваченным границам; путь render/mixin проверен в runClient, визуальный multiplayer-скриншот — за человеком.
4. **Parity audit**: `docs/FEATURE_PARITY.md` перечисляет ported/covered/open/excluded; полный donor parity **не** заявлен.
5. **Проверки**: **218 тестов, 0 падений**; clean build зелёный; скрытый autotest **661/661**, 1920×1080, новых ERROR/exception, shader-source и unknown-sound-event ошибок нет. Jar `linkenvisuals-1.0.106.jar` (10 676 970 байт, SHA-256 `92A68738DC89F5F38066B5F86D8FCD216EAA49670443D9ABD8B41008EC5A8A14`).

### Следующая задача
- Выбрать следующий parity-срез из `FEATURE_PARITY.md`: TargetHud/Coordinates/Cooldowns/Armor/Inventory/HelperItems/LowArmor/DynamicIsland, CameraSettings/AspectRatio/CustomHitBox/Vitals/DamageParticles/HitRange/ItemRadius/NoRender/Optimization/Particles/Trajectories/KillStreak/ViewModel.
- Проверить Custom Tab на multiplayer-сервере (singleplayer без entries не рисует список).
- Человеческая приёмка звуков/визуала; точный donor pixel diff.

### Риски
- Custom Tab визуально не подтверждён в singleplayer; mixin/путь рендера проверен.
- Новые HUD-настройки сохраняются generic ConfigJson, отдельного schema bump не было.
- HUD Style/Shape применены к поверхностям `HudSurface`; часть мелких внутренних радиусов элементов остаётся собственной.
- Полный donor parity не закрыт; см. audit.

## Состояние на 2026-10-22 (чат №36 — pinned cards + verified shader readiness, D82) — v1.0.105

### Сделано
1. **D82/v1.0.105** (`6aeb1b3`): найдена причина shader-source race — кастомный `LinkenLoadingScreen` рисовал пайплайны до загрузки `linkenvisuals:core/*`. Readiness теперь выставляется reloader’ом после `ResourceReloaderKeys.Client.SHADERS` + tick-fallback; `Pipelines.warmUp`, splash, loading screen и title не рисуют до готовности. Проверено скрытым `runAutotest`: `Couldn't find source`/`Couldn't compile` больше нет.
2. **Pinned settings cards**: донорский подход `drawPinnedCard` — карточки закреплённых модулей слева под Profile (справа при закрытой Theme), header toggle/unpin, строки настроек, `...` при переполнении. Под style lock `GUI Style` из pinned card скрыт; переключение Classic/Beta из LinkGUI запрещено. Добавлен тест `pinnedCardsFitBelowProfileAndAboveDock`.
3. **Autotest**: добавлены LINKEN title, LinkGUI и pinned screenshot; скрытый прогон **625/625, 0 fail/skip**; сняты 1280×720 и 1920×1080, новых ERROR/exception нет.
4. **Сборка**: **217 тестов, 0 падений/ошибок/скипов**; clean build зелёный; jar `linkenvisuals-1.0.105.jar` (10 662 645 байт, SHA-256 `5DC8AF5AB6F1BA4180013D6D3EAF95343C6850F1C6F17058570DD5B88E072074`).

### Следующая задача
- **Слуховая приёмка человеком**: kill меч/стрела, streak 2–5, totem, death, low HP, HitSound Bell/Crime/Nya/Uwu/Only Crit, UI/ClickGUI. Лог уже чист от `Unable to play unknown soundEvent`.
- **Визуальная приёмка человеком**: title + LinkGUI + pinned cards на 1280×720/1920×1080; точные шрифты/отступы/hover.
- **Остаток strict 1:1**: донорские HUD style/shape и custom scoreboard/tab — либо портировать с backend, либо зафиксировать отклонение; мёртвые контролы не рисовать.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Звук проверен по логу/ассетам и боевому autotest, но не на слух; полный клик-свип UI не автоматизирован.
- HUD style/shape и scoreboard/tab отсутствуют; это открытый визуальный долг, не «сделано».
- Точная пиксельная сверка с донором на обоих разрешениях ещё не исчерпывающая.
- Пока shader sources не готовы, initial splash остаётся vanilla; кастомный splash включается после readiness.

## Состояние на 2026-10-22 (чат №35 — shader warm-up deferral, D81) — v1.0.104

### Сделано
1. **D81/v1.0.104** (`1713548`): `Pipelines.warmUp()` убран из `Fonts.ensure`/`LinkenFonts.ensure`; `LinkenMainMenuScreen.init` больше не прогревает пайплайны; `render` не рисует меню, пока `client.getOverlay() != null`, и вызывает `warmUp` только после снятия splash/resource-overlay. Кандидат-фикс shader-source race; в runClient **не проверен**.
2. **Strict 1:1**: зафиксированы отклонения — pinned settings cards (донорский `drawPinnedCard`) не портированы (пины только всплывают в сетке); HUD style/shape и custom scoreboard/tab отсутствуют в backend, мёртвые контролы не рисуются.
3. **Сборка**: **216 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.104.jar` (10 657 617 байт, SHA-256 `179E5F2623AA41B49171D5397CA99EC7F5730011CB3E724FC3399AE7FE04F4D2`). Minecraft не запускался.

### Следующая задача
- **Явно запросить разрешение на runClient** и провести:
  - приёмку боевых звуков D80 (kill меч/стрела, streak 2–5, totem, death, low HP, HitSound Bell/Crime/Nya/Uwu/Only Crit, UI/ClickGUI);
  - проверку `latest.log` на `Unable to play unknown soundEvent`, vanilla-звуки и shader-source race;
  - визуальную приёмку 1280×720/1920×1080 (pinned cards/HUD/scoreboard/tab и точные отступы).
- После приёмки — либо портировать pinned cards + HUD/scoreboard/tab, либо оставить зафиксированное отклонение.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Shader-фикс не проверен в игре; возможно, ошибки останутся или проявятся на другом этапе загрузки.
- Боевые cues и media-трек не проверены живьём в этом чате.
- Pinned cards / HUD style/shape / scoreboard/tab — открытый визуальный долг.
- Точная пиксельная сверка на 1920×1080 ещё не выполнена.

## Состояние на 2026-10-21 (чат №34 — donor sounds + menu track, D80) — v1.0.103

### Сделано
1. **D80/v1.0.103**: перенесены все донорские звуки — **35 событий** в `sounds.json`, включая `mainmenu/evilshit.ogg`, kill/streak/totem/low-HP/death cues, HitSound Bell/Crime/Nya/Uwu, cosmetic/pet. Все OGG проверены тестом; `ui.*` перенаправлены на донорские `clickgui_open`/`disable`/`enable`.
2. **Vanilla sounds removed**: все клики UI (ConfigPanel, LinkGuiScreen, CustomMultiplayer, MainMenu, MainMenuEditor, AltManager, Hub, Multiplayer, SelectWorld) используют `LinkenSounds.UI_CLICK`; `KillEffects` и `FakePlayer` используют донорские cues; в Java-исходниках мода не осталось `SoundEvents.*`.
3. **Sounds module**: `SoundsModule` (Player, default ON) — Volume 130 %, Kills/Kill Streak/Totem Pop/Death/Low Health/Low Health At 6 HP; melee + `EntityDamageS2CPacket` (включая свои стрелы), streak 2–5 за 9 с, totem pop, death, low HP.
4. **Main menu**: донорский трек `EVILSHIT` / `ARLEKIN 40 000` (131,3 с, volume 0.4) запускается один раз, продолжается между меню, останавливается при входе в мир, заменяет vanilla menu music; карточка рисует `EVILSHIT by ARLEKIN 40 000`, progress и play/pause.
5. **Сборка/тесты**: **216 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.103.jar` (10 657 799 байт, SHA-256 `64494B17AE61674037DD9588ED5EA4DC20DEDE941CB27808FF971B9B679F26F3`). Release-jar 1920×1080: title с донорским треком и версией 1.0.103, в логе нет `Unable to play unknown soundEvent`.

### Следующая задача
- Ручная проверка боевых cues на сервере/в мире: kill, streak 2–5, totem pop, low HP, death, HitSound Bell/Crime/Nya/Uwu.
- Остаток strict 1:1: pinned cards, donor-only Interface groups (HUD style/shape, scoreboard/tab), точная сверка шрифтов/отступов.
- Устранить shader-source race (`Couldn't find source` для `linkenvisuals:core/*` / `minecraft:core/position_color`).
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Аудиовоспроизведение проверено по состоянию/progress и отсутствию unknown-event, но не измерялось инструментально; боевые cues требуют живого прогона.
- `LinkenMenuMusic.pause()` использует `pauseAllExcept()` — пауза трека глушит все звуки, как в доноре.
- `SoundsModule` считает kill по исчезновению цели при HP ≤ 6 (мобы/игроки) и по damage-пакетам; скрытое сервером HP может дать пропуск, как и в доноре.
- `LinkenSelectWorldScreen` использует vanilla `WorldListWidget` через off-screen `renderWidget`; при изменении vanilla API это хрупко.
- Create/Edit/Re-Create открывают vanilla `CreateWorldScreen` — осознанный остаток.


## Состояние на 2026-10-21 (чат №32 — MSDF/icon pixelation fix, D78) — v1.0.101

### Сделано
1. **D78/v1.0.101**: найдена причина «пиксельности» — `core/icon.fsh` клампил `pixelRange` в 1.0, из-за чего MSDF-переход был почти бинарным; плюс категории/утилиты рисовались PNG 48×48 на 17 px с неверным UV 128×128. Исправлено: донорская формула `Range * inversesqrt((dFdx*W)^2 + (dFdy*H)^2)`, `Range=8`, `smoothstep(-0.5,0.5)`, baseline без округления, `aa` скруглений 0.6→1.0.
2. **Иконки**: добавлены донорские MSDF-атласы `iconsnew` и `iconsfa` (+mcmeta blur/clamp); категории P/S/R/V/H, утилиты FontAwesome (search, gear, users, map-pin, sliders, user, droplet). PNG-иконки 48×48 больше не используются в LinkGUI. TTF/шрифты не импортировались — только атласы.
3. **Сборка/тесты**: **212 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.101.jar` (7 585 612 байт, SHA-256 `FA4181AAD894515C6630D788B8E892E65E0C15D331A2FB9C969944C8FD34C5C8`).
4. **Визуал**: при client 1280×720 текст/иконки/dock/Theme/Profile гладкие; окно 1264×681 (нестандартный клиент) даёт дробный canvas-scale и субъективно усиливает пиксельность — это property canvas-подхода.

### Следующая задача
- Остаток strict 1:1: pinned settings cards, donor media track/audio, donor-only Interface groups (HUD style/shape, custom scoreboard/tab), точная сверка шрифтов/отступов на 1920×1080.
- Устранить shader-source race при старте.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Media strip без донорского трека показывает реальное состояние MediaWatcher.
- Theme-панель короче донорской: нет HUD style/shape, scoreboard/tab настроек.
- Pinned cards не отрисовываются.


## Состояние на 2026-10-21 (чат №31 — donor visual diff, D77) — v1.0.100

### Сделано
1. **D77/v1.0.100**: собран и запущен донорский release-jar `linkenvisuals-2.0.jar` на MC 1.21.4 (только запуск, исходники не менялись) и снят эталон 1280×720 (title + LinkGUI). Найдены и исправлены системные расхождения: canvas не центрировался (`offsetX/offsetY` — сдвиг ~40/24 px), Profile/Theme были закрыты по умолчанию, dock был неполным (5 категорий + 4 утилиты), 3D profile не рисовался.
2. **LinkGUI**: `LinkGuiLayout.Metrics.offsetX/offsetY`; матрица и мышь через `translate(ox,oy)+scale`; backdrop в screen-space. Profile/Theme открыты сразу. Dock 9 тайлов: Combat/Movement/Visual/Player/Misc + Configs/Friends/Waypoints/Elements. Friends/Waypoints — рабочие side-панели 340×600; Elements — HUD edit + чат (fallback).
3. **3D profile**: `InventoryScreen.drawEntity` получает screen-space (`offset + design*scale`) и размер `eh/3.2`; модель внутри viewer, 2D-голова — fallback.
4. **Текст**: title-case кнопки, локализованный `gui.header.all`, keycap `None`.
5. **Сборка/тесты**: **212 тестов, 0 падений**; clean build зелёный; jar `linkenvisuals-1.0.100.jar` (7 384 704 байт, SHA-256 `F32C5338B4F3B17F0624E4A97B0D6D2BED66E45D5ACA44942FF25EA7DC4159C0`). Визуально проверены title, LinkGUI с Profile/Theme, 3D profile, dock, смена темы.

### Следующая задача
- Остаток strict 1:1: pinned settings cards, donor media track/audio, donor-only Interface groups (HUD style/shape, custom scoreboard/tab), точная сверка шрифтов/отступов на 1920×1080.
- Устранить shader-source race при старте.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Media strip без донорского трека показывает реальное состояние MediaWatcher (часто «ничего не играет») — визуально отличается от донора.
- Theme-панель короче донорской: в моде нет HUD style/shape, scoreboard/tab настроек.
- Pinned cards не отрисовываются.


## Состояние на 2026-10-21 (чат №30 — main menu + LinkGUI re-base, D76) — v1.0.99

### Сделано
1. **D76/v1.0.99**: сверка с фактически используемым донорским `LinkGuiScreen` (а не неиспользуемыми `windows/*`) показала настоящую раскладку 1920×1080: main 1090×830, 3 колонки card 102, theme 340×920, profile 330×358, dock 52. `LinkGuiLayout`/`LinkGuiScreen` переписаны под неё; header 62 (wordmark, avatar 34, ALL, search, sliders, close), bottom dock с раскрывающимся активным табом, вызываемые Profile и Theme/Interface, карточки с иконкой 17/двумя строками описания/keycap/32×18 toggle/«···», media strip 76.
2. **Главное меню 1:1 donor**: центрированный `linken.` 23, часы PlexMono 44, welcome-колонка 56/300/340, кнопки **Multiplayer → Singleplayer → Settings + Quit**, inline-аккаунт с «сменить», 4 соц-плитки (TG/Discord/LV/Code), media 320 по центру внизу, footer 2×11. Убраны лишний Alt Manager, карточка аккаунта, левый wordmark и правая media-карточка.
3. **Настройки GUI**: добавлены `Interface Scale`, `GUI Transparency`, `Cursor Tracker`; theme-панель 2×4 chips 28 + Interface-секция со всеми настройками ClickGUI. `GUI Style` скрыт/заблокирован (D69).
4. **Сборка/тесты**: **211 тестов, 0 падений**; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.99.jar` (7 376 475 байт, SHA-256 `8E9F08D5FDD5AB41C4507723D7BA9845289A2B0DF81496BC7E18DCFBB4E8E8EE`).
5. **Визуал**: release-jar 1280×720 — title, LinkGUI, Theme, Profile проверены; при старте остаётся известный shader-source race, но UI/мир рисуются.

### Следующая задача
- Доводка strict 1:1 остатка: pinned settings cards (right-click pin сейчас без панели), 3D profile preview (2D-голова — зафиксированный fallback), donor media/volume, точная сверка шрифтов/отступов на 1920×1080.
- Устранить shader-source race при старте (отдельный дефект: `Couldn't find source` для `linkenvisuals:core/*` и `minecraft:core/position_color`).
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Pinned-карточки и 3D-профиль не 1:1; это записано в D76.
- Шрифтовые метрики MSDF могут давать субпиксельные отличия от donor `Render2D`.
- Донорский `donor2`/`Главная` отсутствуют; использован `C:\Users\gameg\Desktop\визуалы` (read-only).

## Состояние на 2026-10-21 (чат №29 — strict dock/theme, M8.9b-2a) — v1.0.98

### Сделано
1. **D75/v1.0.98** (`1c529bc`): dock donor ×1.5 — pad 9, contiguous tabs 54px, sliding indicator inset 3/radius 13.5, icons 22.5, active/hover lift 6.75, label 10.2 alpha; theme — donor 2×4 swatches 45px/gap 7.5/radius 12, preset backgrounds (Midnight…Frost), active outline 1.8, lower ACTIVE PALETTE info.
2. **Сборка/тесты**: **210 тестов, 0 падений**; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.98.jar` (7 374 430 байт, SHA-256 `E64C6D748DC3CE2698CF5762960EEFA3ADB0D33A8EDC5D7AB8B867B7A8755CE0`).
3. **Визуал**: dev-run ранее упал в shader-source race; release-jar через `MultiMC.exe --launch 1.21.111` не создал окно (процесс без MainWindowHandle, остановлен). Визуально dock/theme **не приняты**.

### Следующая задача
- **M8.9b-2b**: InterfaceWindow (interface scale/cursor tracker/transparency/Apply-Default), donor Theme Interface section, media strip typography/volume, theme button; volume/system volume — реальный контрол или зафиксировать отклонение; close overlay — docs-only (no-op).
- **Release-jar visual acceptance** 1280×720/4K в чистом профиле; dev shader-source race обходить.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Визуал не подтверждён: dev shader-source race и неудачный MultiMC launch.
- Interface/Theme donor section и media volume ещё не портированы.
- Донор `donor2` отсутствует; используется локальный донор с теми же 1280×720-константами.

## Состояние на 2026-10-21 (чат №28 — strict header/grid, M8.9b-1) — v1.0.97

### Сделано
1. **D74/v1.0.97** (`1d1e876`): donor header ×1.5 — logo chip 60, player head 42 на `+87`, `ALL` 51×42, search `+192`, close 36/right−60; grid side pad 63/126, media strip 120. Ник/счётчик категории убраны из шапки (в доноре их нет); добавлен `searchAll`/ALL.
2. **Сборка/тесты**: **209 тестов, 0 падений**; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.97.jar` (7 373 888 байт, SHA-256 `BECF12FB2DDB48D41B9C0BDF997AFB123F65E5D1E23CEA3A2B4E90103B6161DB`).
3. **Визуальный прогон**: пользователь дал разрешение; dev-клиент запустился, title screen отрисовался, но при входе в мир/загрузке возник shader-source resource race — `Couldn't find source` для `linkenvisuals:core/*` и `minecraft:core/position_color`, world start не завершился. Header/карточки визуально **не приняты**; это dev-артефакт, release-jar не проверялся.

### Следующая задача
- **M8.9b-2**: theme button, dock indicator slide, Interface/Profile/Theme spacing, media strip typography/volume; volume/system volume — реальный контрол или зафиксировать отклонение; close overlay (no-op) — docs.
- **Release-jar visual acceptance** 1280×720/4K: запускать не dev `runClient`, а собранный `linkenvisuals-1.0.97.jar` в чистом профиле, чтобы обойти shader-source race.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Dev-визуал заблокирован shader-source resource race; release-jar ещё не проверялся.
- Header/карточки не проверены глазами; возможны нюансы chip/head/All/search.
- Донор `donor2` на этом устройстве отсутствует; использован локальный старый донор с теми же 1280×720-константами.

## Состояние на 2026-10-21 (чат №27 — strict card 1:1, M8.9a) — v1.0.96

### Сделано
1. **D73/v1.0.96** (`a081296`): строгая геометрия `ModuleCard` из донора ×1.5 — badge 51/pad 18, title 14.1 на +81/+34.5, description 11.85 на +61.5, bind pill +90/h21, toggle 54×27 (right margin 15), radius 15, hover lift 4.5. Чистая `LinkGuiLayout.CardMetrics` + `cardContentUsesDonorScaledGeometry`.
2. **Сборка/тесты**: **208 тестов, 0 падений**; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.96.jar` (7 373 730 байт, SHA-256 `139A07F5B48329270D64D774F3D6C1CC7F1C34A3CF7F1ACB518DD4F6A0DC0BFB`). Minecraft не запускался.
3. **Донор**: `C:\StrangeVisuals\donor2\visualy` отсутствует на этом устройстве; использован локальный донор `C:\Users\gameg\Desktop\Главная` (`screen/linkgui`, те же 1280×720-константы). Донор не изменялся.

### Следующая задача
- **M8.9b**: main header (logo chip 60, head 42 на +87, All+search, close 36, theme), dock indicator slide, Interface/Profile/Theme spacing, media strip typography/volume; volume/system volume — только реальный контрол или зафиксировать отклонение.
- Close overlay: в доноре no-op, новый не выдумывать; зафиксировать в D-решении.
- Визуальная приёмка 1280×720/4K — только после явного разрешения пользователя.

### Риски
- Карточки не проверены глазами после изменения; нужна визуальная сверка.
- `pin` — наша M4-фича, не донорская; оставлена в нижнем правом углу.
- Строгий 1:1 остаётся неполным: header/dock/panels/media ещё не сверены.

## Состояние на 2026-10-21 (чат №26 — donor card scale + media controls, M8.8) — v1.0.95

### Сделано
1. **D72/v1.0.95** (`f76016d`): карточки приведены к донорскому масштабу — высота 129, gap 15, header 78, grid top +80, media 126; media strip 94. Добавлены `<<` / `>||` / `>>`, отправляющие системные media-клавиши через `java.awt.Robot` (`LinkGuiMediaKeys`), ошибки в debug.
2. **Сборка/тесты**: 207 тестов, 0 падений; `clean build --no-daemon` зелёный; Minecraft не запускался. Jar `linkenvisuals-1.0.95.jar` (7 371 851 байт, SHA-256 `886EF652F5FDEE6298178C0B15916412816CBEE780A8BE66C66D5D8480B65081`).
3. **M8**: layout, dock, Interface/Profile, draggable settings, 8 тем, player head, badges, media strip/controls, style lock, cursor trail.

### Следующая задача
- Остаток строгого 1:1: volume slider/system volume (донорский audio backend не портирован), close overlay (no-op в доноре), точная пиксельная сверка шрифтов/отступов.
- Визуальная приёмка 1280×720/4K — только с разрешения пользователя.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- System media keys через Robot могут не сработать на некоторых Windows/Java конфигурациях (debug-лог, без краша).
- Volume/prev/play/next и light/theme/cursor не проверены глазами.
- Масштаб карточек изменён по донорским константам, но визуальная сверка ещё нужна.

## Состояние на 2026-10-21 (чат №25 — cursor trail, M8.7) — v1.0.94

### Сделано
1. **D71/v1.0.94** (`166869a`): `LinkGuiCursorTrail` — 64 точки/780 мс/min-step 2.4, мягкие glow-орбы + белое ядро через `GlowPipeline`/`CirclePipeline`; очистка в `init`, рендер поверх GUI. Minecraft не запускался (только код/тесты).
2. **Сборка/тесты**: 207 тестов, 0 падений; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.94.jar` (7 370 108 байт, SHA-256 `FC4974DB090D25907B91B2FE055F8056EECA80F172975ACBA0B9AF6C64E48991`).
3. **M8 итог**: донорская компоновка, 8 тем (LIGHT/FROST), Interface/Profile, draggable settings window, player head, card badges, media strip, style lock, cursor trail.

### Следующая задача
- Строгий 1:1 остаток: управление музыкой/громкостью (донорский playback-бэкенд не портирован, strip read-only), close overlay (в доноре no-op), точная пиксельная сверка шрифтов/отступов с донором.
- Визуальная приёмка 1280×720/4K — только с разрешения пользователя; сейчас код-компиляция.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Cursor trail/light/drag-окно не проверены глазами.
- Read-only media strip и отсутствие volume/prev/play/next — осознанное отклонение без donor audio backend.
- Строгий пиксель-в-пиксель не подтверждён без визуального сравнения.

## Состояние на 2026-10-21 (чат №24 — draggable module settings, M8.6) — v1.0.93

### Сделано
1. **D70/v1.0.93** (`1d66355`): клик по карточке модуля (кроме switch/pin) открывает плавающее окно настроек 330×440; header перетаскивается, `X` закрывает, позиция клампится в canvas. Внутри — имя/описание, toggle, bind, прокручиваемые настройки. Клик по ClickGUI оставляет Interface/Profile.
2. **Сборка/тесты**: 207 тестов, 0 падений; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.93.jar` (7 366 303 байта, SHA-256 `B7B6F14481432FDF571D96CDFD3C7DE9E1A5ACAEF28C5111D0495B70569296C7`). Minecraft не запускался — только компиляция/тесты.
3. **Уже в M8**: донорская компоновка, 8 тем включая LIGHT/FROST, player head, card badges, media strip, Interface/Profile, style lock.

### Следующая задача
- Строгий 1:1 остаток: cursor trail (донорский `LinkGuiCursorTrail`), close overlay (в доноре no-op), управление музыкой/громкостью (read-only), точная визуальная сверка отступов/шрифтов с донором.
- Визуальная приёмка 1280×720/4K — после следующего code-среза; Minecraft не запускать без разрешения.
- Style/Editor в LINKEN Settings не добавлять; style lock сохранять.

### Риски
- Draggable-окно и split не проверены глазами; возможны пересечения при малом окне.
- Cursor trail/close overlay/music controls пока не перенесены.
- Строгий пиксель-в-пиксель без запуска клиента не подтверждён.

## Состояние на 2026-10-21 (чат №23 — LINKEN GUI style lock + Interface/Profile, M8.5) — v1.0.92

### Сделано
1. **D69/v1.0.92** (`5638ef7`): пока активен Linken GUI, стиль заблокирован — `ModuleRegistry.isGuiStyleLocked`, `cycleGuiStyle` no-op, `setGuiStyle` принимает только Linken, Ctrl+Left/Right не переключает, строка `GUI Style` скрыта в Interface-панели. +1 тест.
2. **Interface/Profile split**: левая колонка теперь `INTERFACE` 330×282 + `PROFILE` 330×360 (донорская компоновка). Верх — глобальные настройки ClickGUI, низ — активный профиль/список профилей либо настройки выбранного модуля. `LinkGuiLayoutTest` обновлён.
3. **Сборка/тесты**: **207 тестов, 0 падений**; `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.92.jar` (7 365 384 байта, SHA-256 `728C7BAA069D9BFE37F423E72EE4958DFAB5DFDCFDF054AE406F1368E350B5D0`). Minecraft по просьбе пользователя не запускался.

### Следующая задача
- Строгий 1:1 остаток: перетаскиваемые окна настроек модуля (donor `LinkGuiSettingsWindow`), cursor trail/close overlay, 3D `ClickGuiPlayer`, управление музыкой/громкостью, точная сверка отступов/шрифтов по скриншоту донора.
- Визуальная приёмка 1280×720/4K после следующего code-среза.
- Style/Editor в LINKEN Settings не добавлять (приказ пользователя); style lock сохранять.

### Риски
- Стиль заблокирован намертво: вернуть Classic/Beta можно только правкой конфига/кода — это требование пользователя.
- Light-темы/player head/interface split не проверены глазами; компиляция и тесты только.
- Строгий пиксель-в-пиксель не подтверждён без визуального сравнения.

## Состояние на 2026-10-21 (чат №22 — LINKEN ClickGUI: player/card/light, M8.3–M8.4) — v1.0.91

### Сделано
1. **D68/v1.0.91** (`fa107f1`): в шапке главного окна — 2D-голова активного скина (`PlayerSkinDrawer`, fallback), ник/категория; ModuleCard получил badge 28×28 с иконкой категории и переработанные отступы; добавлены донорские light-темы **LIGHT** и **FROST** (8 палитр), полный набор токенов (`TEXT/PANEL/ROW/HOVER/ACTIVE/FIELD/TRACK/BACKDROP/ON_DARK`), светлый backdrop, светлые панели/строки, тёмный текст; theme-панель показывает 8 карточек.
2. **Тесты/сборка**: `LinkGuiThemeTest` +2 — **206 тестов, 0 падений**; `clean build --no-daemon` зелёный, Java-ворнингов 0; jar `linkenvisuals-1.0.91.jar` (7 363 633 байта, SHA-256 `79DD285FA99E729BB64559C68B10033FA3960B34403979A2A961D56BD90573D9`). Minecraft по просьбе пользователя не запускался — только компиляция/тесты.
3. **Уже перенесено в M8**: донорская компоновка (main 1080×720, dock 540×72, левый inspector 330×600, правое theme-окно 360×855), 8 палитр, read-only media strip, player head, card badges, поиск/категории/настройки/конфиги/пины.

### Следующая задача
- **M8.5 (если нужен строгий 100%)**: глобальный `InterfaceWindow` (interface scale, GUI style, cursor tracker, Apply/Default), отдельные перетаскиваемые окна настроек модуля, cursor trail/close overlay, 3D player preview, управление музыкой.
- После этого — визуальная приёмка 1280×720/4K и финальная полировка. Style/Editor в LINKEN Settings не добавлять (приказ пользователя).

### Риски
- Light-темы не проверены глазами: часть secondary-элементов остаётся тёмной (`ON_DARK`), контраст на светлых панелях нужно проверить.
- Player head 2D вместо донорского 3D; card badge — иконка категории, не индивидуальная иконка модуля.
- Media strip read-only, без управления и громкости.
- Строгий 1:1 с донором ещё не закрыт (см. M8.5).

## Состояние на 2026-10-21 (чат №21 — LINKEN ClickGUI donor window layout, M8.2) — v1.0.90

### Сделано
1. **D67/v1.0.90 — донорская компоновка LinkGUI** (`ea26c9c`): 1280×720 композиция ×1.5 на 1920×1080 — главное окно 1080×720, горизонтальный док категорий 540×72, левый inspector 330×600, правое постоянное Theme-окно 360×855. `LinkGuiLayout` ужимает главное окно на узких virtual-width, чтобы панели не пересекались. `LinkGuiLayoutTest` обновлён.
2. **Визуал**: ник/категория/счётчик/поиск/close в шапке главного окна; док с иконками+подписями; левая панель модуля; правая панель 6 палитр (ACTIVE/CLICK TO APPLY) без модала; read-only media strip внизу (NOW PLAYING/MEDIA, title/artist/progress/equalizer). Модальный `LinkGuiThemeWindowLayout` удалён.
3. **Evidence**: 1280×720 visual — новое окно/док/панели читаются, пересечений нет; media strip добавлен после снимка, глазами не перепроверен. `clean build --no-daemon` — **204 теста, 0 падений**; jar `linkenvisuals-1.0.90.jar` (7 361 749 байт, SHA-256 `3531B024BED331B6496E931DD73231536BCD6D88AC79530E681D437526C015AE`).

### Следующая задача
- **M8.3** — раскрывающиеся `ModuleCard` (настройки в карточке), `ClickGuiPlayer` preview в шапке, донорский `MusicPlayerStrip` (read-only основа уже внизу главного окна), доводка левого inspector под ширину 330.
- **M8.4** — light-темы LIGHT/FROST и плавный переход палитр.
- Прогнать 4K-framebuffer после M8.2: проверить, что док/панели не обрезаны и media strip читается.
- Shader-source ERROR caveat и ручной keybind rebind — без изменений.

### Риски
- Левая панель 330 px тесная: длинные названия настроек/значения могут выглядеть плотно (добавлен ellipsis, но глазами проверить).
- Media strip не проверен глазами; права/анимация только от `MediaWatcher`, без управления.
- Полный M8 ещё не закрыт: карточки пока без inline-настроек, player preview отсутствует.

## Состояние на 2026-10-21 (чат №20 — LINKEN ClickGUI ThemeWindow, M8.1) — v1.0.89

### Сделано
1. **D66/v1.0.89 — первый срез переноса донорского LinkGUI** (`43031da`): `LinkGuiThemeWindowLayout` (чистая геометрия 568-панели, 3 карточки в ряд 160×110) + модальное THEME-окно в `LinkGuiScreen`. 6 текущих палитр, selected/hover, клик применяет и сохраняет тему; закрытие X/клик вне/Esc. Theme-chip теперь открывает окно вместо циклинга.
2. **Тесты**: `LinkGuiThemeWindowLayoutTest` (+3) — итого **207**, 0 падений. `clean build --no-daemon` зелёный; jar `linkenvisuals-1.0.89.jar` (7 363 796 байт, SHA-256 `BD03E44F5DB2E06F1F7906BA2271FA4525A52F1378FD67F523B70336E0C77C5F`).
3. **Визуальный прогон ThemeWindow не выполнен**: автоматическая отправка Right Shift в dev-окно не открыла ClickGUI (фокус/raw input). Разметка покрыта unit-тестами; глазами проверить при следующем runClient.

### Следующая задача
- **M8.2** — донорская компоновка плавающих окон: `MainModuleWindow` (720×480), `CategoryDockWindow` (нижний док), `InterfaceWindow` (левая панель), `ThemeWindow` справа; перенести геометрию 1280×720 + fit-scale, сохранить текущий ModuleRegistry/Setting.
- **M8.3** — `ModuleCard` с раскрытием настроек, `MusicPlayerStrip` (read-only через `MediaWatcher`), `ClickGuiPlayer` preview.
- **M8.4** — light-темы LIGHT/FROST из донора + плавный переход палитр.
- Style/Editor в LINKEN Settings не добавлять (приказ пользователя). Shader-source ERROR caveat и ручной keybind rebind — по-прежнему открыты.

### Риски
- ThemeWindow не проверен глазами; возможны визуальные нюансы карточек при 4K.
- Полный M8 — крупная переработка `LinkGuiScreen` (сейчас 74 КБ); дробить по окнам, не ломать Beta/Classic.

## Состояние на 2026-10-21 (чат №19 — LINKEN keybind refresh) — v1.0.88

### Сделано
1. **D65/v1.0.88 — keybind refresh fix** (`23ce1d0`): `LinkenKeybindsScreen` после rebind/right-click reset/RESET ALL теперь вызывает `KeyBinding.updateKeysByCode()` и `refresh()`. До фикса новая клавиша сохранялась в `options.txt` (проверено `key_key.forward:key.keyboard.b`), но строка показывала старую, а карта клавиш в сессии не обновлялась. Listening-состояние и запись в options подтверждены; ручной end-to-end UI rebind всё ещё рекомендуется. `clean build --no-daemon` — 204 теста, 0 падений; jar `linkenvisuals-1.0.88.jar` (7 358 704 байта, SHA-256 `3B7583E3CE18616DBE34E650BAD7B0C7172AFB0FBBDA512864495021DB779860`).

### Следующая задача
- Ручной end-to-end keybind rebind (новая клавиша сразу в строке и в игре).
- Shader-source ERROR caveat: в dev-прогонах при startup/reload логируются `Couldn't find source for … shader` (включая `minecraft:core/position_color`), экраны рендерятся; на релизном jar/реальном клиенте не перепроверено.
- `OPEN FOLDER`, соцссылки и медиа-трек — по желанию.
- Финальное «принято» M2–M7; Style/Editor в LINKEN Settings не добавлять (приказ пользователя).

### Риски
- Shader-source ERRORs в dev — вероятный resource race; не воспроизведён/не проверен на релизной сборке.
- Физического 4K нет: только framebuffer 3840×2160.

## Состояние на 2026-10-21 (чат №18 — M7b-4 acceptance + splash reload fix) — v1.0.87

### Сделано
1. **D63/v1.0.86 — Skin и Resource Packs** (`62cb4e1`): `LinkenSkinScreen` (превью головы, 7 model parts, main arm, account action) + `LinkenResourcePacksScreen` (AVAILABLE/SELECTED, поиск, enable/disable, приоритет, pinned/locked/incompatible, confirm-modal, open folder, apply/reload); роутинг из Settings + `MinecraftClientMixin`; i18n en/ru/uk; `LinkenPackSearchTest` (+5), итого 204 теста.
2. **D64/v1.0.87 — критический fix, найденный на приёмке** (`7f181cc`): `SplashOverlayMixin` отменял vanilla `render` до инициализации `reloadStartTime`; при ручном reload ресурсов оверлей зависал навсегда (чёрный экран после применения пака). Теперь mixin сам ставит `reloadStartTime`, если он `<= 0`; после `Enable Anyway`/закрытия паков возврат в Settings hub за ~3 с. 204 теста, 0 падений; jar `linkenvisuals-1.0.87.jar` (7 358 660 байт, SHA-256 `9A2E4D53378564D1B10CFB06CAA1FDA0A018126FD02CABE2391CB96D4D3769CC`).
3. **Визуальная приёмка M7b-4**: 1280×720/GUI 2 — Skin (превью, 7 частей, ON/OFF пишутся в `options.txt`, Main Arm Left/Right, account manager), Resource Packs (две колонки, поиск по `p`, enable/disable, порядок 01/02, incompatible-confirm, DONE/ESC → apply/reload → hub), Language (автопрокрутка к активному `English (US)`), Keybinds (60 биндов, conflict-маркеры). 4K-framebuffer F2 PNG **3840×2160**: `run/screenshots/2026-10-21_10.48.11.png` (Resource Packs), `2026-10-21_10.51.10.png` (Skin); обрезки/перекрытий нет.
4. **Локальный `options.txt` восстановлен** из `options.txt.lvaccept.bak` (resourcePacks `[]`, mainHand right, model parts true); тестовый `IncompatibleTest` пак удалён.

### Следующая задача
- **Ручная приёмка keybind rebind** (listening-состояние «PRESS A KEY / ESC CANCELS» подтверждено; фактический ввод клавиши автоматизация не подтвердила) и, при желании, `OPEN FOLDER`/соцссылки/медиа-трек.
- **Style/Editor в LINKEN Settings не добавлять** — приказ пользователя от 21.10.2026; переключение Beta/Vanilla и редактор остаются вне LINKEN Settings, это принято.
- **Проверить shader-source ERROR caveat**: в dev-прогонах при startup/reload логировались `Couldn't find source for … shader` (включая `minecraft:core/position_color`), экраны работали. Guard в `Pipelines.warmUp` не помог и откатан; перепроверить на релизном jar/реальном клиенте, при воспроизведении — отдельный фикс.
- Финальное «принято» M2–M7 — за пользователем; после него M7 закрыть и выбрать следующий объём.

### Риски
- Keybind rebind визуально не подтверждён (только listening-состояние); conflict-маркеры и экран отображаются корректно.
- Shader-source ERRORs при startup/reload в dev — возможен resource race; на релизной сборке не проверено.
- Style/Editor недоступны из LINKEN Settings по приказу пользователя — принято, не дефект.
- Физического 4K нет: только framebuffer 3840×2160.

## Состояние на 2026-10-21 (чат №17 — LINKEN M7b-4) — v1.0.86

### Сделано
1. **D63 — M7b-4: Skin и Resource Packs, v1.0.86, `62cb4e1`.** `LinkenSkinScreen`: LINKEN-окно 1060×740, живой 2D-превью головы из `PlayerSkinProvider`/`DefaultSkinHelper` (fallback `?`), все 7 `PlayerModelPart` как ON/OFF-строки, `Main Arm` Left/Right, `ACCOUNT & SKIN` → существующий `LinkenAltManagerScreen`. В 1.21.11 у `SkinOptionsScreen` нет отдельного skin chooser для offline-аккаунтов — это осознанное отклонение (см. D63), а не пропущенная кнопка.
2. **`LinkenResourcePacksScreen`**: две колонки AVAILABLE/SELECTED на `ResourcePackOrganizer`, поиск по имени/описанию/id, включение/выключение кликом, стрелки приоритета, `PINNED`/`LOCKED`/`INCOMPATIBLE`, подтверждение включения несовместимого пака, `OPEN FOLDER`, DONE/ESC. Применение — `GameOptions.refreshResourcePacks(manager)` (запись options + reload при изменении) и возврат в Settings. Иконки — LINKEN-монограммы, без vanilla texture-loader.
3. **Роутинг**: Settings → Skin/Resource Packs ведут в новые экраны; `MinecraftClientMixin` дополнительно перехватывает vanilla `SkinOptionsScreen`/`PackScreen` в LINKEN. Beta/Vanilla не перерисовываются.
4. **i18n**: en/ru/uk — skin (title/subtitle/preview/account/model parts/main arm/on/off) и packs (title/subtitle/available/selected/open folder/none/hint/locked/pinned/conflict/incompatible modal/cancel/enable anyway); `LangParityTest` зелёный.
5. **Тесты/сборка**: `LinkenPackSearchTest` (+5) — итого **204 теста, 0 падений/ошибок/скипов**; `clean build --no-daemon` зелёный, Java-ворнингов 0; jar `source/build/libs/linkenvisuals-1.0.86.jar` (7 358 636 байт).
6. **Визуальная приёмка не выполнялась**: в этом чате не было явного разрешения на `runClient`; 1280×720/4K, Skin-превью, Resource Packs enable/disable/move/apply/reload, incompatible-confirm и Open Folder — за следующим шагом.

### Следующая задача
- **Финальная визуальная приёмка M7** (нужно явное разрешение на `runClient`): 1280×720 и 4K-framebuffer — Skin (превью головы, все 7 частей, main arm, account action), Resource Packs (поиск, enable/disable, порядок, apply/reload, incompatible-confirm, open folder), Language (активная строка после автопрокрутки), keybind rebind, Beta/Vanilla regression. После «принято» — закрыть M7 и решить следующий объём.
- Открытые решения без изменений: LICENSE copyright, `MASTER_PROMPT.md` в публичном репо, UK-глифы MSDF, waypoints UX, StreamerMode nametag-санитизация.

### Риски
- Физического 4K нет: проверка только framebuffer 3840×2160; окно может уезжать на второй монитор/терять фокус (как в M6).
- Skin: offline-аккаунты не имеют in-game skin chooser в 1.21.11; показывается голова активного скина и переход в account manager — осознанное отклонение.
- Resource Packs: иконки — монограммы, не оригинальные pack.png; несовместимый пак можно включить только через подтверждение. `ResourcePackOrganizer` — vanilla API; поведение apply/reload нужно проверить в игре.
- Keybind rebind и Language активная строка всё ещё не подтверждены визуально после изменений M7b-2/M7b-3a.

## Состояние на 2026-10-21 (чат №16 — LINKEN M7a/M7b-1/M7b-2/M7b-3a) — v1.0.85

### Сделано
1. **M2 — splash/loading/connect, v1.0.77, D53, `bef9569`.** `SplashOverlayMixin`, `LinkenLoadingScreen`, `SplashProgress` + `SplashProgressTest`; LINKEN-ветки `LevelLoadingScreenMixin`, `ConnectScreenMixin`, `ProgressScreenMixin`, `MessageScreenMixin`; `LinkenFonts.retryIfUnavailable()`. Кастомный `SAVING WORLD` пойман, hand-off на title стабилен, чёрного застревания нет. Сам splash через F2 не снимается (ввод во время overlay не обрабатывается) — тайминги покрыты тестом.
2. **M3/M4 — Link GUI, v1.0.78/1.0.79, D54/D55, `21cd4d1`/`f639d69`.** `clickgui/link/` (shell, layout, theme, config), Classic/Beta/Linken и Ctrl+Left/Right, поиск/скролл, темы `MIDNIGHT/GRAPHITE`, пины, создание/удаление профилей, compact `Auto` 190. Темы/пины/конфиг вошли в M3 из-за compile-зависимости; M4 — полировка. Beta/Vanilla не затронуты.
3. **M5 — pause/settings hub, v1.0.80, D56, `ec52c1e`.** `LinkenHubScreen`, `LinkenPauseScreen`, `LinkenSettingsScreen`, `returnScreen`, blur-fix. **Критический hang `Save and Quit` исправлен:** вместо `disconnectWithSavingScreen()` вызывается `client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT)`, поэтому соединение закрывается, сервер пишет `Stopping server`/`All chunks are saved` и возвращает title. По приказу пользователя из Settings убрана кнопка `LinkenVisuals` — hub содержит только игровые опции (8 карточек).
4. **M6 — multiplayer, v1.0.81, D57, `c3a328c`.** `LinkenMultiplayerScreen` на `ServerList`/pinger/`ConnectScreen`: сетка 3×3, paging, статусы, Add/Edit/Delete/Direct modal, toolbar, double-click/Enter. Проверены Add/Edit/Delete, `LocalServer` → `INCOMPATIBLE`, пагинация `1 / 1`; 4K главное меню и Settings сняты. Direct Connect modal/actual connect — на финальную приёмку.
5. **Сборка/тесты:** `.\gradlew.bat test --console=plain` — **199 тестов, 0 падений/ошибок** на v1.0.85. Коммиты M2–M7b-3a созданы отдельно, версии `+1 patch`; рабочее дерево после docs-коммита должно быть чистым.
6. **Визуальные evidence (1280×720, GUI 2):** pause `20.44.54.png`; settings `20.45.11.png`; Link GUI `20.45.28.png`; config `20.46.22.png`; создание профиля `20.46.49.png`; Save/Quit title `20.59.04.png`; M6 main `20.59.22.png`; Add modal `20.59.43.png`; M6 add `21.00.08.png`; Edit `21.24.16.png`; Delete `21.30.11.png`; M6 after delete `21.31.27.png`; 4K menu `21.53.13.png`; 4K Settings `21.57.22.png`. Все — в `source/run/screenshots/`, не коммитить.
7. **Docs:** D53–D60 в `MERGE_DECISIONS.md`; ROADMAP/PORT_STATUS обновлены. Тестовое состояние (`linkenvisuals_linkgui.json` с `GRAPHITE`+`Shaders`, `servers.dat` с `LocalServer`/`M6Test`, `linkenvisuals_accounts.json`) очищено; `run/options.txt` вернуть в исходное (`ao:true`, если тестовый toggle его выключил).
8. **M7a — LINKEN account manager, v1.0.82, D59, `c04ae31`.** `LinkenAltManagerScreen`: 1920×1080 chrome, список аккаунтов с ACTIVE-бейджем, поиск, Add/Use/Delete и confirm-delete; backend — существующий `AccountStorage` + `MinecraftClientAccessor`, без IAS. Роутинг из main menu/account-chip и `MinecraftClientMixin`. Проверены пустое состояние, добавление `LinkenTest`, применение, поиск, удаление; `AltAccountFilterTest` (+3). PrintWindow evidence: `alt-state.png`, `alt-add.png`, `alt-added.png`, `alt-used.png`, `alt-search.png`, `alt-del.png`, `alt-deleted.png` (temp).
9. **M7b-1 — LINKEN settings sub-pages, v1.0.83, D60, `a129b8f`.** `LinkenSubSettingsScreen` (search, двухколоночные строки, toggle/slider/cycle, DONE/ESC), `LinkenSettingsOption`/`LinkenSettingsCatalog`/`LinkenSettingsFilter`; Video/Controls/Sound/Chat/Accessibility используют vanilla `SimpleOption` через LINKEN chrome. Проверены Video, toggle Smooth Lighting, Sound, Chat, поиск `opacity`, Controls; PrintWindow `video-settings.png`, `video-toggle.png`, `settings-hub-after-video.png`, `sound-settings2.png`, `chat-settings.png`, `chat-search.png`, `controls-settings.png`. Language/Skin/Packs и keybinds list — следующий срез.
10. **M7b-2 — LINKEN keybinds, v1.0.84, D61, `2d569c8`.** `LinkenKeybindsScreen`: категории, поиск, capture через `InputUtil.fromKeyCode`, conflict-маркеры, right-click reset, RESET ALL, `options.write()`. Controls → `Keybinds → Open` теперь LINKEN. Evidence `keybinds2.png`, capture-состояние `rebind-state.png`; автоматический ввод клавиши не сработал из-за фокуса окна на втором мониторе — сам rebind проверить руками.
11. **M7b-3a — LINKEN language list, v1.0.85, D62, `f9dbed3`.** `LinkenLanguageScreen`: 143 языка, поиск, ACTIVE, клавиатурная навигация, Apply через `LanguageManager`/`reloadResources`, автопрокрутка к текущему. Settings → Language больше не открывает vanilla. Evidence `language.png`; после добавления автопрокрутки визуально не перепроверено.

### Следующая задача
- **Финальное «принято» M2–M6 пользователем.** Повторить Direct Connect modal и actual connect (например, на localhost), проверить splash глазами при желании.
- **M7b-4:** Skin/Resource Packs list screens; затем визуальная приёмка M7 1280×720/4K. Singleplayer/Select World в доноре ванильный, отдельный экран не нужен; Alt Manager закрыт M7a, keybinds — M7b-2, Language — M7b-3a. Beta/Vanilla не перерисовывать.
- Открытые решения: LICENSE copyright, `MASTER_PROMPT.md` в публичном репо, UK-глифы MSDF, waypoints UX, StreamerMode nametag.

### Риски
- Физического 4K-дисплея нет: 4K проверен framebuffer 3840×2160; на реальном 4K/другом DPI возможны отличия.
- 4K-окно растягивалось на два 1920×1080-монитора и периодически минимизировалось/теряло фокус — automation F2 нестабильна; 4K Settings снят, 4K Multiplayer/Link GUI — нет.
- Splash визуально не пойман (F2 не работает во время overlay); логика покрыта тестом.
- Direct Connect и actual connect не перепроверены после modal-фиксов; код использует общий layout и `ConnectScreen`.
- Skin/Resource Packs пока используют vanilla chrome — это M7b-4, не регрессия M5/M7b-1.
- Keybind rebind-клик/ESC проверены визуально, но автоматическая отправка клавиши не сработала из-за фокуса 4K-окна на втором мониторе; ручной rebind и conflict-поведение — на приёмку.
- Account manager работает только с offline-именами (`AccountStorage`), без IAS/лицензионного login и без скинов; это осознанный backend проекта.

## Состояние на 2026-10-20 (чат №14 — LINKEN M1) — v1.0.76

### Сделано
1. **D52 — третий визуальный стиль LINKEN, M1: главное меню и роутинг.** Прочитан `MASTER_PROMPT_STYLE3.md`; M1 сделан одним фич-коммитом `feat(ui): add LINKEN main menu style (v1.0.76, D52)`.
2. **Меню** — `ui/linkmenu/`: `LinkenMainMenuScreen` (виртуальный холст 1920×1080, `scale = screenHeight/1080`), `LinkenMenuChrome` (фон `bg-tower` cover + parallax + scrim/виньетка, wordmark `linken.`/`VISUALS`, часы/дата, приветствие, аккаунт-чип, соцсети, живая медиа-карточка, футер, fit/ellipsis длинных строк), `LinkenMenuLayout` (чистая геометрия), `LinkenFonts` (MSDF-атласы Golos/Golos Semi/Unbounded/Unbounded Light/PlexMono + shared `wtmico`/`brands`). Акцент `#7EDFB4`; Esc не закрывает, Quit — повторный клик 3 с; кнопки Singleplayer/Multiplayer/Settings/Alt Manager/Quit и соцсети рабочие.
3. **Роутинг** — `MenuStyle.LINKEN` (алиасы `linken`/`link`), чистые правила `StyleRouting` (`nextStyle`, `shouldApplyPreset`, `usesVanillaChrome`), цикл Video Settings **Beta → Vanilla → Linken → Beta**; `TitleScreenMixin`/`MinecraftClientMixin` — явные `switch`; все прежние бинарные проверки переведены на `usesVanillaChrome`. LINKEN **не вызывает** `MainMenuConfig.applyPreset` — элементы Beta/Vanilla сохраняются, `menuStyle` переживает рестарт (схема/`configVersion` не менялись).
4. **Редактор** — третий пункт `Linken`, в LINKEN только переключатель стиля + `Save & Exit` + пояснение фиксированной раскладки; тулбар расширен до 840, Reset/Save не пересекаются.
5. **Fallback'и M1 (без мёртвых кликов)** — Multiplayer → ванильный `MultiplayerScreen` (M4/M6), Settings → существующий `OptionsScreen` (M5), Alt Manager → наш `AltManagerScreen`; unported-экраны — vanilla chrome; Link GUI остаётся Classic/Beta до M3.
6. **Ассеты/provenance** — copy: `textures/linkmenu/bg-tower.png` + MSDF-атласы `fonts/link/*` (upstream SIL OFL 1.1, только атласы, без TTF); reuse: `wtmico`/`brands`; skip: донорский `icons2`, Lottie `welcome.json`, донорские play/pause/next/previous и аудио; новых шейдеров нет (фон/скрим — существующие `GlowPipeline`/`RoundedRectPipeline`). Авторство донорских ассетов — D6.
7. **i18n** — en/ru/uk: приветствия, welcome/subtitle, account/licensed/offline, quit, media, disclaimers, `video.style_linken`, `editor.linken_fixed/help`; `LangParityTest` зелёный.
8. **Тесты/сборка** — `MenuStyleTest` (4), `StyleRoutingTest` (3), `LinkenMenuLayoutTest` (5: 1280×720/1920×1080/2560×1440/3840×2160, вложенность, отсутствие пересечений), `ConfigRoundTripTest` (4: LINKEN round-trip, сохранение Beta-элементов, Beta/Vanilla пресеты). `clean build --no-daemon` — **181 тест, 0 падений/ошибок/скипов, Java-ворнингов 0**; jar `source/build/libs/linkenvisuals-1.0.76.jar` (7 192 589 байт, SHA-256 `39702CC7587E7D1C60C290E1DED8E62288D690E547C66982A185CDAE0A241069`).
9. **Docs** — D52 в `MERGE_DECISIONS.md`; ROADMAP/PORT_STATUS/README/README_RU обновлены (LINKEN M1 + M2–M6).
10. **Визуальная приёмка M1 (завершена 20.10.2026)** — `runClient` разрешён пользователем; проверены 4K-framebuffer **3840×2160** (F2 PNG; физического 4K нет — 2×1920×1080, окно выведено за экран) и **1280×720/GUI 2**. LINKEN-меню без обрезки/перекрытий: wordmark, часы/дата, приветствие, аккаунт-чип, кнопки, соцсети, медиа-карточка, футер. Esc не закрывает (два 4K-снимка побайтно равны), Quit показывает `Confirm` и через 3 с возвращается к `Quit`, повторный клик штатно завершает игру. Singleplayer → Select World, Multiplayer → ванильный `MultiplayerScreen`, Settings → `OptionsScreen` → Video Settings, аккаунт-чип/Alt Manager → наш `AltManagerScreen`. Соцсети открывают браузер (Telegram и Discord). Медиа-карточка с реальным SMTC-треком `Linken Acceptance Track` / `LinkenVisuals QA`, прогресс `0:03 / 1:15`; read-only — осознанно.
11. **Редактор/стили/регрессия** — LINKEN-редактор 1280×720 и 3840×2160: тулбар `Beta/Vanilla/Linken` + `SAVE` без пересечений, fixed-notice и help-бар; на 4K тулбар уже экрана из-за клампа 840 — не дефект. SAVE вернул в Video Settings. Цикл **Linken → Beta → Vanilla → Linken** отработал; Beta-меню и Vanilla-редактор не перекрашены LINKEN-слоем. После рестарта с `menuStyle=LINKEN` сразу открылся LINKEN-титул (`Active Style: Linken`), после рестарта с `VANILLA` — ванильный титул без перекраски.
12. **Лог/локальный options.txt** — в сессиях 1–2 было 186× `Skipping bad option:` (WARN). Это не дефект мода: предупреждения шли из старого локального `source/run/options.txt`; после перезаписи файла игрой (186 валидных строк, UTF-8/CRLF) сессия 3 дала **0** таких строк. `ERROR`/`Exception` в логах — 0 (только известное `Realms ... SignedJWT: FabricMC` в INFO). Все три сессии завершились штатным `Stopping!`, `BUILD SUCCESSFUL`. Код после v1.0.76 не менялся — этот шаг docs-only, без бампа.

### Следующая задача
- **Финальное «принято» M1 — за пользователем.** Визуальная приёмка 1280×720/4K, кнопок, соцсетей, медиа, редактора, цикла стилей и логов выполнена; M2 не начинать до этого слова.
- После «принято» — **M2 (splash/loading/connect)**, ориентир v1.0.77/D53, затем M3 (Link GUI shell), M4 (themes/config), M5 (pause/settings), M6 (multiplayer/parity) по ROADMAP.
- Открытые решения без изменений: LICENSE copyright, `MASTER_PROMPT.md` в публичном репо, UK-глифы MSDF, waypoints UX, StreamerMode nametag-санитизация.

### Риски
- 4K проверен в framebuffer 3840×2160; физического 4K-дисплея нет — остаётся риск отличий на реальном 4K/другом DPI.
- Редактор: тулбар фиксированной ширины 840 логических единиц; при 1280×720/GUI 2 и 4K пересечений нет, но экстремально высокий GUI scale не перепроверялся.
- MSDF-атласы донора: UK-глифы `і/ї/є/ґ` по-прежнему без нативных глифов (общий известный риск, фолбэк).
- Медиа-карточка read-only (нет донорского playback-бэкенда) — это осознанное отклонение, не баг.
- M2 не стартует до финального «принято» пользователя.

## Состояние на 2026-10-20 (чат №13 — TargetEsp) — v1.0.75

### Сделано
1. **D51 — TargetEsp**: 4 режима донора (Original/Ghosts/Circles/Crystals) на `AFTER_ENTITIES`: выбор цели по прицелу + retention 1 с, радиус 30; рейкаст видимости (кэш 50 мс); NPC-фильтр (armor stand / ping 0 + нулевой UUID, `Show NPCs`); инвиз скрывается только без видимой экипировки. Все билборды depth-tested — сквозь стены не видно. Original — world-billboard с пересчётом донорского экранного размера в мировые единицы (единственное визуальное отклонение). Ассеты `textures/esp/{marker,ghost_bloom}.png`, пайплайны sprite/glow/body. +11 тестов правил (итого 165).
2. **Evidence**: `clean build` — **165 тестов, 0 падений**; hands-free `runAutotest -PautotestWorld=AutoTestWorld -PautotestQuit=true` — **583 шага, 580 ok, 0 fail, 3 skip** (зомби в Peaceful + нет прав на `/setblock`), новых `ERROR`/`Exception` — **0**, клиент закрылся сам; отчёт `run/logs/linkenvisuals-autotest-20261020-094258.log`, 3 скриншота. Первый прогон поймал реальный краш Crystals (`BufferBuilder: Not building!` — смена слоёв в одном кадре), исправлено раздельными проходами по слоям.
3. **Jar**: `source/build/libs/linkenvisuals-1.0.75.jar`.

### Следующая задача
- Игровая приёмка v1.0.75: включить Target ESP, навести прицел на игрока/моба — Original (вращающийся маркер), Ghosts, Circles, Crystals; уйти за стену — эффект должен пропасть; невидимый игрок без брони/предметов — эффекта нет, с предметом в руке/бронёй — есть; `Show NPCs` на стойках/NPC.
- Пачка этого чата: WayPoints-II (чат-события + квикбинд + 2D-маркеры) → v1.0.76, GPS (`/gps` + HUD-стрелка) → v1.0.77.
- Открытые решения без изменений: LICENSE copyright, `MASTER_PROMPT.md` в публичном репо, UK-глифы MSDF, waypoints UX, StreamerMode nametag-санитизация.

### Риски
- Original — world-billboard вместо 2D-прохода: экранный размер по формуле донора, но при экстремальном FOV возможны субпиксельные отличия — проверить глазами.
- NPC-фильтр опирается на ping 0 + нулевой UUID; на серверах с NPC-плагинами возможны ложные срабатывания (отключается `Show NPCs`).

## Состояние на 2026-10-19 (чат №12 — пачка визуальных фиксов по отчёту пользователя) — v1.0.74

### Сделано
1. **D46 — «фейковые скругления» («и так на всём»)**: glass-шейдеры считали радиус угла без GUI-масштаба (`1.2×level`, кап 12 px), а тела/обводки — `level×guiScale` → из-под скруглённых панелей торчали квадратные полупрозрачные углы стекла. Теперь UV несёт радиус в пикселях устройства, шейдеры трактуют его напрямую (формула 1:1 с `rounded_rect.fsh`). Файлы: `GlassPanelPipeline.java`, `GlassWindowPipeline.java`, `glass_panel.fsh`, `glass_window.fsh`.
2. **D47 — Trails/JumpCircles/BlockOverlay/Waypoints не работали**: их собственные encoder-пассы не биндили `DynamicTransforms` (в 1.21.11 `RenderSystem.bindDefaultUniforms` ставит только Projection/Fog/Globals/Lighting) → геометрия вырождалась молча. Двухфазный хелпер: `Pipelines.writeWorldTransforms()` **до** открытия пасса + `bindWorldUniforms(pass, slice)` внутри (write мапит UBO — внутри пасса краш, пойман первым прогоном `/lvtest`); заменено 6 вызовов, включая `ShaderSkyPipeline`. Сигнатура `write(...)` сверена javap по yarn build.4.
3. **D48 — HitSound**: RETURN-гейт `if (!doAttack().getReturnValue()) return;` давал звук на блоках и тишину на мобах. Звук перенесён в `ClientPlayerInteractionManagerMixin.attackEntity` (после реального удара) + ванильное крит-условие для `Only Crit`; фейк-игрок озвучен в cancel-ветке `doAttack`.
4. **D49 — бинды клавиш (Zoom и др.)**: `KeySetting` не имел UI-бинда (оба GUI рисуют бинды только для `KeybindSetting`) — удалён; Zoom/Freelook/HandSwap/ElytraSwap на `KeybindSetting` (`code()`; дефолты C/LALT/NONE/NONE). `ConfigJson` читает бинд только из строки — legacy-числа игнорируются; +1 тест (`keybindIgnoresLegacyNumericValues`).
5. **D50 — вкладка Configs**: шапка категории (иконка/заголовок/описание/подчёркивание), stagger при входе + hover-пружины карточек, пустое состояние, векторный «×» вместо глифа U+2715 (нет в атласах), статусы на двойных кавычках (апострофа U+0027 тоже нет), 4 новых i18n-ключа en/ru/uk. Поведение/клики не менялись.
6. **Jar**: `linkenvisuals-1.0.74.jar` (автодеплой в MultiMC). Evidence: `clean build` — **154 теста, 0 падений**; hands-free `/lvtest` — **562 шага, 562 ok, 0 fail, 0 skip, новых ERROR/исключений — 0**, клиент закрылся сам.

### Следующая задача
- Игровая приёмка пользователем v1.0.74: углы HUD/ClickGUI при масштабах GUI 2/3/4 (не должно быть полупрозрачных квадратов), Trails/JumpCircles/BlockOverlay/Waypoints/Shader Sky (должны быть видны), HitSound (моб/крит/«только крит»/фейк-игрок), бинд клавиш в modern+classic GUI (Zoom дефолт C), вкладка Configs (шапка, hover, «×», статусы).
- **Остаток заказа этой пачки** (следующие чаты): (а) прицелы — улучшить встроенные + пиксельный редактор собственных; (б) 3-й стиль GUI «LINK» из донора (донорский «ClickGUI 3a», ~6k строк + ассеты + несвободные шрифты) — крупная отдельная работа, перед стартом нужен скоуп/решение по шрифтам и лицензии.
- Открытые решения без изменений: LICENSE copyright, `MASTER_PROMPT.md` в публичном репо, UK-глифы MSDF (і/ї/є/ґ), waypoints UX, StreamerMode nametag-санитизация.
- Тег/релиз GitHub — только по явному слову пользователя.

### Риски
- Glass-радиус теперь зависит от GUI-масштаба и без потолка 12 px (кламп — по половине квада, как у `RoundedRectPipeline`): при масштабе 4 радиус до 40 px — выглядит одинаково со телами по построению, но глазами проверить все три масштаба.
- Тексты без апострофов в статусах Configs — осознанно (атлас без U+0027); если понадобятся апострофы — регенерация атласа (тот же пункт, что UK-глифы).

## Состояние на 2026-10-19 (чат №11 — лог-фикс + in-game автотест) — v1.0.73

### Сделано
1. **D44 — двойной blur ClickGUI**: по логу пользователя (1.0.72) единственное наше предупреждение — `Can only blur once per frame`; причина — `ClickGuiScreen.render` вызывал `context.applyBlur()` после ванильного `Screen#renderBackground`. Повторный вызов и поле `blurUnsupportedLogged` удалены. В прогоне 1.0.73 — 0 таких строк.
2. **D45 — автотест в игре**: `dev.linkenvisuals.autotest` (`AutoTestDriver`, `AutoTestCommand`, `AutoTestReport` + 4 теста). `/lvtest start|stop|status` (алиас `/lvautotest`), автостарт `-Dlinkenvisuals.autotest=true`, авто-выход `.quit=true`; Gradle `runAutotest`, `-PautotestWorld=<save>`, `-PautotestQuit=true`. Свип всех модулей/настроек (boolean/slider/mode/color; keybind/text не трогаются), modern+classic ClickGUI, инвентарь, 4 моба (2 cow/2 zombie, NoAI, 1 HP) и атаки, jump/sneak/sprint, use item, place/break в креативе, restore конфига/позиции/слота, скан новых ERROR в `latest.log`, отчёт `logs/linkenvisuals-autotest-*.log` + 3 скриншота.
3. **Evidence**: `clean build` — 153 теста, 0 падений. Hands-free прогон на копии мира (читы вкл.): **564 шага, 564 ok, 0 fail, 0 skip**, новых ERROR/Exception — 0, blur-warning — 0, клиент сам закрылся. Первый прогон (557/7 skip) выявил, что `Tags` не синхронизируются на клиент; детекция мобов — по снимку entity id (тег остался для `/kill`). Скриншоты: `run/screenshots/lv-autotest-{1-world,2-clickgui,3-final}.png`.
4. **Jar**: `source/build/libs/linkenvisuals-1.0.73.jar` (автодеплой в MultiMC).

### Следующая задача
- Визуальная приёмка пользователем: в мире с читами выполнить `/lvtest start`, дождаться чат-отчёта; проверить размытие ClickGUI и эффекты. Для dev-рана: `.\gradlew runAutotest` (автостарт после входа в мир) или `-PautotestWorld=<save> -PautotestQuit=true`.
- Открытые решения без изменений: LICENSE copyright, MASTER_PROMPT в публичном репо, UK MSDF (і/ї/є/ґ), waypoints UX, StreamerMode nametag-санитизация.
- Тег/релиз GitHub — только по явному слову пользователя.

### Риски
- Автотест не заменяет визуальную проверку: он ловит исключения/варнинги и проверяет жизненный цикл модулей. В мультиплеере без op мобы/`tp`/`setblock` пропускаются (skip), свип модулей выполняется.

## Состояние на 2026-10-19 (чат №10 — Фаза 6 закрыта) — v1.0.72

### Сделано
1. **Ф6.1 — аудит пер-кадровых аллокаций (D42).** `pet/PetUltimate`: горячий путь ULT-сцены переведён на примитивы — `lastBodyPoint: Vec3d` → `lastBodyX/Y/Z` (double), физика иллюзий и туман перьев по компонентам, `yawFromDir`/`pitchFromDir` — double-перегрузки (Vec3d-делегаты для возврата). Было ~10 Vec3d/птица/кадр + 588 Vec3d/кадр в тумане → стало 2 Vec3d/птица/кадр (неизменяемые `pos`/`vel`, принято) и 0 в тумане; порядок float-операций сохранён построчно. `render/VanillaStylePipeline`: `getIconForKey` больше не аллоцирует на виджет каждый кадр — `ICON_CACHE` + `resolveIcon`. Остальное проверено и принято осознанно (см. D42).
2. **Ф6.2 — аудит draw-calls.** 0 per-frame регистраций пайплайнов/слоёв: все `RenderPipelines.register`/`RenderLayer.of` — static final или одноразовый `Pipelines.warmUp()` (guard `warmed`; `HudManager` под `loaded`, `MainMenuScreen.init`). 29 сайтов `createBuffer` — lazy-guard (uniform/dummy) либо динамическая геометрия под try-with-resources; суммарно единицы GPU-буферов на кадр. Сабмиты ограничены константами (ULT ≤43 raven + 2 слоя билбордов ≤294 перьев/42 glow; BlockOverlay — только target; вэйпоинты/трейлы — по спискам).
3. **Ф6.3 — инвентарь ресурсов.** Скрипт-сверка в обе стороны (id-литералы ↔ файлы, sounds.json ↔ .ogg, обратный поиск): 0 битых ссылок; 5 «нерезолвленных» id — рантайм-текстуры (screen_capture, hud_glass_capture/sharp, media_art, custom_menu_bg.png). sounds.json ↔ .ogg — чисто. **Удалены 45 файлов без ссылок** (8 легаси-шейдеров blur/border/shadow/texture + 36 донорских png + `ico_avatar.png.mcmeta`); `characters/rudy.json` оставлен как носитель CC-BY-4.0-атрибуции. Джар: 538 записей / 3.5 МБ.
4. **Ф6.4 — README/доки.** Оба README: раздел «Cosmetics, Pets & Waypoints» (11 скинов, ULT, Rudy, `/way`), строка «Pet ULT» в таблице управления, примечание о клиентских командах, структура (`pet/`, `waypoint/`, `characters/`, `lang/`, `sounds/`), `linkenvisuals_waypoints.json` в конфигах; в RU поправлен уровень заголовка UI-раздела. ROADMAP/PORT_STATUS синхронизированы.
5. **Ф6.5 — релизная сборка.** `clean build` — **149 тестов, 0 падений**; `linkenvisuals-1.0.71.jar` задеплоен в MultiMC; dev-прогон: 1 «Reloading ResourceManager», «Sound engine started», 0 «Failed to load»/«Couldn't find»/«cannot add it to event»/mixin/shader/«deferred»; 2 ERROR — ожидаемый dev-шум. Миксин-скан не требуется (новых mixin-целей нет).
6. **Ф6.6 — DoD-свип (D43, v1.0.72).** 0 `TODO`/`FIXME`/`System.out`/javac-ворнингов по нашему коду. Аудит «проглоченных» catch: исправлено 3 (`PetUltimate` play/stop звука → `LOGGER.debug`; `LoadingOverlay` fallback панорамы → warn-один-раз), 8 оставлены осознанно (fallback-move, `Result.failures`, sentinel `-1`, `interrupt()`, UI-статусы). LICENSE (MIT) ↔ README-бейдж ↔ `fabric.mod.json` согласованы; строка копирайта «StrangeVisuals Team & Contributors» (старое имя) — в открытых вопросах.

### Следующая задача
Игровая приёмка за пользователем: v1.0.68 (Rudy/Trails), v1.0.69 (11 скинов), v1.0.70 (ULT), v1.0.71 (аудит-правки: картинка не менялась, ULT — тот же сценарий), v1.0.72 (только логи в catch + warn-один-раз — визуал не тронут); проверка, что ничего не сломалось. Тег/релиз GitHub — только по слову пользователя (проект к нему готов: сборка/доки/инвентарь/DoD закрыты).

### Проверенные факты (для следующего чата)
- Ф6 закрыта; код-правки аудита — только `PetUltimate.java` (примитивы) и `VanillaStylePipeline.java` (кэш иконок); изменения v1.0.71 — аудит + 45 удалённых ассетов + README/доки.
- Удалённые ассеты восстановимы из истории git (последний коммит с ними — v1.0.70, `8c7d297`).
- `source/tools/*` — repo-only артефакты (превью + исходник шрифта), в jar не попадают; релевантность подтверждена как «оставить».
- Инвентарь-скрипт (одноразовый, вне репо): id-литералы `LinkenVisuals.id(...)`/`Identifier.of("linkenvisuals", ...)` → файлы; sounds.json → .ogg; обратный поиск; динамические семейства — Category/ClassicCategory iconName, ClassicRenderTheme, lang/<code>, MSDF, `"core/" + name`.

### Открытые решения (ждём пользователя)
- Игровая приёмка v1.0.68/1.0.69/1.0.70/1.0.71 — за пользователем.
- LICENSE: копирайт-строка — держатель не менялся (правило «лицензию не менять» в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы MSDF (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- WayPoints: дополнительный UX (кейбинд/GUI) — нужен ли.
- StreamerMode: nametag-санитаризация — нужна ли отдельной задачей.

## Состояние на 2026-10-19 (чат №9, продолжение 2) — v1.0.70

### Сделано
1. **Фаза 5.6 (остаток): ULT-анимация воронов (D41, v1.0.70).** Донорская 19-с сцена — `pet/PetUltimate.java` (константы 1:1: 2.4 s подъём → 2.6 s стягивание → 9.2 s удержание → 2.8 s разлёт → 3.0 s дисперсия, обрезка на 19 s). Hero + 42 иллюзии — `PetRig.renderUltRaven` (`entityTranslucent(textures/pet/voron.png)` + ARGB); перья (7/птицу, `dens = 1.05+1.55·attach`) и свечение — билборды пайплайнами `position_tex_color` (перья TRANSLUCENT, свечение `BlendFunction.LIGHTNING`; `NO_DEPTH_TEST`, без depth-write/cull); возврат 850 мс (`snapFlyTo`). Триггер — бинд `Pet ULT` (Cosmetics, дефолт NONE) + GLFW edge-poll (`PetRig.frame`; гейты «экран закрыт» + «скин Raven»; мид-сцена — игнор, во время возврата — перезапуск). Настройки: `Pet Alpha` 82 %, `Pet Glow` 65 %, `Pet Glow Colour` 0xFF141414. Звук `linkenvisuals:pet/ult` → `sounds/pet/itachi.ogg`. `oblok.png` не импортирован (0 ссылок в доноре); остальные отклонения — в D41.
2. **Находка dev-прогона (исправлена до выдачи jar)**: звуковой путь резолвился как `sounds/pet/sounds/itachi.ogg` (двойной `sounds/`) — файл переложен в `sounds/pet/`, имя в `sounds.json` — `linkenvisuals:pet/itachi`; заодно ассеты питомцев приведены к общему layout `textures/pet/*`. Повторный прогон чистый.
3. **Тесты**: **149, 0 падений** (+7: `PetUltimateTest` — окна фаз/перекрытие дисперсии, attach/detach, альфа птиц, smoothstep; `KeyNamesTest` — round-trip, NONE/−1, «KEY n»). Build зелёный; автодеплой `linkenvisuals-1.0.70.jar` в MultiMC.
4. **Dev-прогон**: 1 «Reloading ResourceManager», 0 «Failed to load»/«Couldn't find source», 0 ошибок миксинов, 0 звуковых предупреждений; клиент дошёл до титульного экрана, закрыт штатно (2 ERROR в логе — дев-шум: user properties/Realms offline).

### Следующая задача
Игровая приёмка пользователем: v1.0.68 (Rudy — поза/крылья/хват; Trails), v1.0.69 (11 скинов), **v1.0.70 (ULT)**: в Cosmetics забиндить `Pet ULT`, выбрать скин Raven, нажать в мире — 19-с сцена (подъём → стягивание/удержание → разлёт, перья/свечение) со звуком темы Itachi, в конце возврат на плечо за 850 мс; повторное нажатие мид-сцены игнорируется (во время возврата — перезапуск). Затем Фаза 6 в утверждённом порядке: 1) аудит пер-кадровых аллокаций, 2) аудит draw-calls, 3) инвентарь ресурсов, 4) README/доки, 5) релизная сборка. Тег/релиз — только по слову пользователя.

### Проверенные факты (для следующего чата)
- **Yarn-кэш**: проект собирается против `yarn 1.21.11+build.4`; в кэше fabric-loom лежат лишние `build.3` и `build.6` — javap по ним **врёт**. Пример: `PositionedSoundInstance.master(SoundEvent, float)` в build.4 переименован в `ui(...)`.
- **Ресурсы**: id-путь текстур — точный путь под `assets/<ns>/` (префикс `textures/` не добавляется автоматически); у нас вся графика — `textures/…` и в id указывается `textures/…`. Для `sounds.json`: имя `[<ns>:]<path>` ↔ файл `assets/<ns>/sounds/<path>.ogg`.
- **ULT-рендер**: `Camera.getRotation(): Quaternionf`; `camera.getHorizontalPlane()/getVerticalPlane()` — билборды; `RenderPipelines.POSITION_TEX_COLOR_SNIPPET` + `RenderPipelines.register` + `RenderLayer.of(...RenderSetup.texture("Sampler0", id)...)` (паттерн `Material.java`); `RenderLayers.entityTranslucent(id)` + цвет-аргумент `submitModel(..., color)` — альфа без шейдера.
- **ULT-константы (locked тестами)**: 19000/2400/2600/9200/2800/3000 мс; 42 иллюзии (сид `now ^ 0x9E3779B97F4A7C15L`); спавн птицы `520 + 14·i` мс + фейд 860 мс; detach после break = 0.95; постоянный фейд `i·0.0085`.
- **KeybindSetting**: ярлык + код; `KeyNames.codeOf` (обратный маппинг); персистенция в `ConfigJson`.

### Открытые решения (ждём пользователя)
- Игровая приёмка v1.0.68 (Rudy, Trails), v1.0.69 (11 скинов) и v1.0.70 (ULT) — за пользователем.
- LICENSE: копирайт-строка — держатель не менялся (правило «лицензию не менять» в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы MSDF (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность при переносах Ф5.
- WayPoints: дополнительный UX (кейбинд/GUI) — нужен ли.
- StreamerMode: nametag-санитаризация — нужна ли отдельной задачей.

## Состояние на 2026-10-19 (чат №9, продолжение) — v1.0.69

### Сделано
1. **Фаза 5.6 (остаток): питомцы — 11 скинов (D40, v1.0.69).** Новый пакет `dev.linkenvisuals.pet`: `PetSkins` (None + Bat, Parrot, Raven, Fairy, Bee, Vex, Fox, Pig, Frog, Pufferfish, Slime), `PetGroundSampler`, `PetWanderController` (фикс-тик 20 Гц, инжектируемый `GroundProbe` — тестируется без мира), `PetRig` (рантайм + рендер). Рендер — `AFTER_ENTITIES` → camera-relative `manager.render` (паттерн KillEffects), поза/свет/варианты — патчи render state после `getAndUpdateRenderState`; raven — `submitModel` с `pet/voron.png`; без UserSyncManager/сети, Naruto не портирован. Настройки в Cosmetics: `Pet` + Radius/Lift/Speed/Size/First Person (дефолты донора, скин None). Весь кадр под защитным `catch` (warn с троттлингом 30 с + пауза 5 с).
2. **Тесты**: **142, 0 падений** (+7: `PetWanderControllerTest` — seed/догон/телепорт/лиши/reset; `PetSkinsTest` — round-trip меток). Build зелёный; автодеплой в MultiMC сработал (`linkenvisuals-1.0.69.jar`).
3. **Dev-прогон**: 1 «Reloading ResourceManager», 0 «Failed to load»/«Couldn't find source», 0 ошибок миксинов; dev-клиент закрыт штатно.

### Следующая задача
**ULT-анимация воронов (v1.0.70)** — донорский `Raven.java` 458–1008 (beginReturn/renderReturn/renderUltimate/renderMainAscendRaven/billboards/playUltSound) + импорт ассетов `pero.png`, `oblok.png`, `sounds/itachi.ogg` (одобрено) + бинд. Затем игровая приёмка пользователем (питомцы v1.0.69 + Rudy/Trails из v1.0.68). Затем Фаза 6 по порядку: перф-аудит аллокаций → draw-calls → инвентарь ресурсов → README/доки → релизная сборка. Тег/релиз — только по слову пользователя.

### Открытые решения (ждём пользователя)
- Игровая приёмка v1.0.68 (Rudy, Trails) и v1.0.69 (11 скинов) — за пользователем.
- LICENSE: копирайт-строка — держатель не менялся (правило «лицензию не менять» в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы MSDF (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность при переносах Ф5.
- WayPoints: дополнительный UX (кейбинд/GUI) — нужен ли.
- StreamerMode: nametag-санитаризация — нужна ли отдельной задачей.

### Проверенные факты (для следующего чата)
- **Питомцы**: `Entity.getEntityWorld()` (НЕ `getWorld()`) — в 1.21.11; `EntityDimensions.height()` — доступор; `ParrotEntity.lastFlapProgress/lastMaxWingDeviation` публичны (крылья считаются аналитически); `setVariant`/`setSleeping` НЕ публичны — варианты только через render state (`ParrotEntityRenderState.variant/parrotPose`, `FoxEntityRenderState.type/sitting/sleeping/sleepingDirection/walking`); `LightmapTextureManager.MAX_LIGHT_COORDINATE`, `OverlayTexture.DEFAULT_UV`, `RenderLayers.entityCutoutNoCull(id)`; `queue.submitModel(model, state, matrices, layer, light, overlay, color, null)` — 8-арг default-оверлоад; `manager.getAndUpdateRenderState(ent, pt)` + `manager.render(state, cameraState, 0,0,0, matrices, queue)`.
- **Dev-прогон обязателен** перед выдачей jar (см. секцию v1.0.68 ниже): reload ловит битые шейдеры/ресурсы; закрытие — kill `java.exe` с `devlaunchinjector` в CommandLine.

## Состояние на 2026-10-19 (чат №9) — v1.0.68

### Сделано
1. **Хотфикс D38 (v1.0.67): пайплайн Trails ронял загрузку шейдеров.** `TrailsRenderer` (с коммита b5bcf1e, «v1.0.62») ссылался на `linkenvisuals:core/position_color`, файлов не было → любая сборка 1.0.62–1.0.66 падала на reload (`Failed to load required shader programs` → сброс ресурспаков; каскадом терялись и ванильные шейдеры), т.к. пайплайн регистрируется на старте в `Pipelines.warmUp()`. Добавлены `assets/linkenvisuals/shaders/core/position_color.vsh/.fsh` (семантика ванильной пары 1.21.11). Evidence: повторный dev-запуск — 1 reload, 0 ошибок, `precompile deferred` = 0. У пользователя не проявлялся: последняя сессия MultiMC — v1.0.45 (лог 18.10).
2. **Фаза 5.6: Rudy — порт персонажа (D39, v1.0.68).** Ассеты `characters/rudy.*` (CC-BY-4.0) + чистое ядро `RudyMesh` (парсер, 8 тестов) + `RudyCharacter` (загрузка/текстуры/submit/эмиссия/поза) + 4 новых миксина и WrapOperation над `submitModel` в `LivingEntityRendererMixin`. Архитектура: `submitCustom` по одному на материал, `setAngles` перевызывается в draw-лямбде (поза на submit не финальна), пивоты/хват — из донора, броня скрыта, только локальный игрок. UI: Cosmetics → «Character» (Default/Rudy).
3. **Тесты**: 135, 0 падений (+8 RudyMeshTest); build зелёный; jar `linkenvisuals-1.0.68.jar`, автодеплой в MultiMC сработал (запись «путь 1.21.111 не существует» в разделе v1.0.66 неактуальна).
4. **Ремап-скан jar 1.0.68**: 53 миксин-класса (включая 4 новых), 0 yarn-имён в member-рефах — dev-запуск ремап НЕ проверяет (named-маппинги), скан обязателен.

### Следующая задача
**Питомцы (5.6c-остаток = CosmeticEffects-задача)**: локальная адаптация донорского `Raven` (≈2950 строк: симуляция, 11 скинов ванильных мобов, ULT-анимация воронов) + `PetWanderController`/`PetGroundSampler` — **без UserSyncManager** (п.3); перед стартом уточнить у пользователя состав (какие скины, ULT — да/нет). После — Ф6 по утверждённому порядку: 1) перф-аудит аллокаций, 2) draw-calls, 3) инвентарь ресурсов, 4) README/доки, 5) релизная сборка.

### Открытые решения (ждём пользователя)
- Питомцы: состав порта (скины, ULT-анимация воронов).
- Trails: был неработоспособен с 1.0.62 — в игровой приёмке посмотреть визуал (упрощённые billboard'ы).
- LICENSE: копирайт-строка — держатель не менялся (правило «лицензию не менять» в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы MSDF (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность при переносах Ф5.
- WayPoints: дополнительный UX (кейбинд/GUI) — нужен ли.
- StreamerMode: nametag-санитаризация — нужна ли отдельной задачей.

### Проверенные факты (для следующего чата)
- **Dev-прогон перед выдачей jar — обязателен** (после D38): `runClient` (окно), лог `source/run/logs/latest.log`, DEBUG — в `debug.log` (`precompilation deferred`); ресурсы — из `build/resources/main`; закрытие — kill `java.exe` с `devlaunchinjector` в CommandLine. Ловит битые шейдеры/ресурсы на reload; ремап целей — НЕ проверяет (named-маппинги).
- **Шейдеры в пайплайнах**: ванильные — `Identifier.of("minecraft", "core/…")` (пример: CirclePipeline); свои — файлы `assets/linkenvisuals/shaders/core/<name>.vsh|.fsh` (`#version 330` — как в большинстве; `#moj_import <minecraft:dynamictransforms.glsl>`/`projection.glsl`); сверка ссылок: `Select-String -Pattern '"core/([a-zA-Z_0-9]+)"'` по java × список файлов.
- **Rudy-факты**: draw-лямбда `submitCustom` обязана сама звать `model.setAngles(state)`; `matrices.peek().copy()` — снимок матриц для лямбды; «невидим для игрока» = `invisible && !invisibleToPlayer`; свои текстуры — `NativeImage.read` + `NativeImageBackedTexture(id::toString, image)` + `registerTexture`; флаги по render state — duck-интерфейс + миксин на state (`@Unique` поле; паттерн HitColor/Rudy).

## Состояние на 2026-10-19 (чат №8) — v1.0.66

### Сделано
1. **Пачка 3 Ф5.7 (D36, v1.0.65): StreamerMode — полный порт движка без god-класса.** Таблица → ресурс `assets/linkenvisuals/streamer/patterns.json` (донат/монеты/анархия/гриф/26 парольных команд); чистый движок `util/text/StreamerRules` (предфильтр одним regex, whole-token ник, пары «друг=ник», донат/монеты, номера 999/666, маска `/login` с сохранением пробелов, §/&-коды и hex сохраняются); `util/text/StreamerTextSanitizer` (обход Text-компонент со стилями, без аллокаций на «мимо»; маска поля ввода той же длины); `util/text/StreamerPatterns` (Gson-загрузчик, громкий фейл). Хуки (7): ChatHud.addMessage ×2, PlayerListHud.getPlayerName/setHeader/setFooter, InGameHud.setOverlayMessage, ScoreboardObjective.getDisplayName, ScoreboardEntry.name/display/formatted, TextFieldWidget (маска в существующем WrapOperation).
2. **Модуль**: `StreamerModeModule` — кеш `StreamerRules` с инвалидацией по настройкам/нику сессии, `rules()` (null при выключенном); мёртвый `shouldHideAnarchy()` удалён; FriendManager заменён строкой «real=nick» (отклонение, D36).
3. **Тесты**: 119, 0 падений (+15: StreamerRulesTest 12, StreamerPatternsTest 3). BUILD SUCCESSFUL, jar `source/build/libs/linkenvisuals-1.0.65.jar` (1 786 339 байт).
4. **Ремап-скан 1.0.65**: 6 затронутых миксинов — 0 yarn-остатков; все таргеты — методы по `minecraft-merged-intermediary`; `comp_2129()` — легальный метод-аксессор record-компонента (не дефект).
5. **WayPoints активирован (D37, v1.0.66)**: клиентская команда `/way` (алиас `/waypoint`) — `add <name> me`, `add <name> x y z`, `remove`, `list`, `clear` (донорские семантика и отказ на дубликат); персистентность `config/linkenvisuals_waypoints.json` (AtomicFiles + configVersion); `WaypointsRenderer` переведён на общий стор, тоггл модуля больше не стирает точки; тесты 127 (+8 `WaypointStoreTest`). **Ф5.7 закрыта.**

### Следующая задача
**Игровая приёмка пользователем** пачек 1–3 + WayPoints (jar 1.0.66; автодеплой в MultiMC недоступен — путь `D:\MultiMC\instances\1.21.111` на машине не существует, jar выдаётся вручную). Затем — решение по Naruto/CosmeticEffects (5.6) и Ф6 (перф-профилирование, инвентарь ресурсов, релизная сборка).

### Открытые решения (ждём пользователя)
- LICENSE: копирайт-строка — держатель не менялся (правило «лицензию не менять» в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы MSDF (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность при переносах Ф5.
- WayPoints: реализована команда `/way` (D37); нужен ли дополнительный UX (кейбинд/GUI для добавления) — скажи.
- Naruto/CosmeticEffects (5.6): пропустить или портировать (Naruto — энейблер настроек; CosmeticEffects — зонтичный модуль, ghost-bloom ветки)?
- Питомцы/Rudy (5.6c): D29 пропустил (только UserSyncManager-режим) — подтверди пропуск.
- StreamerMode-остаток: nametag-санитаризация (подписи игроков в мире) отложена — нужна ли отдельной задачей.

## Состояние на 2026-10-19 (чат №7) — v1.0.64

### Сделано
1. **Пачка 2 Ф5.7 (D35, v1.0.64)**: `ItemScroller` (drag по модификаторам: shift/alt+shift — стаки, alt — по совпадению, ctrl — по одному; колесо — переносы одного предмета/стаков/всего; хуки `HandledScreenMixin.mouseClicked/mouseDragged/mouseReleased/mouseScrolled` + `HandledScreenAccessor` из пачки 1; чистая логика `util/ItemScrollerMath` + MoveBudget, анти-кик пол 50 мс), `ElytraSwap` (фазовый своп через слот 6, 1 клик/тик; временный InventoryScreen; `util/InventoryMath.pickBest`; без Instant и `/fly` — D35), `TotemTracker` (хук `ClientPlayNetworkHandlerMixin.onEntityStatus` + чат-сообщение с меткой зачарованности; дедуп `util/PopDedupe`), HUD `TotemCounterHud`/`UseTrackerHud` в `HudManager.ELEMENTS` (видны в edit-mode).
2. **Гигиена catch закрыта**: не 6, а 11 проглоченных исключений (ещё 5 — в `CustomTextureLoader`) → LOGGER (warn / warn-once для пер-кадровых путей / debug для best-effort cleanup). TODO/FIXME/System.out/catch-ignored по `src/main/java` = 0.
3. **Тесты**: 104, 0 падений (+21: ItemScrollerMath 11, InventoryMath 5, PopDedupe 5; −1: `inventoryIndexToSlot` переехал в `InventoryMathTest`, `DragPath` — только сэмплирование).
4. **Ремап-скан** jar 1.0.64: 47 миксинов, 0 yarn-имён; аннотации новых инжектов ремапнуты (`method_25394`, `method_11113`; `getStatus` → `method_11470:()B`). Стабильные `rotation(float)`/`getString()`/`comp_*` валидированы по `minecraft-merged-intermediary` (не method_* — так и должно быть).

### Следующая задача
**StreamerMode** (остаток пачки 2 Ф5.7) — отдельной сессией: `StreamerModeUtils` (3048 строк) + `StreamerModePatterns`/`StreamerModeState` + FriendManager-зависимость; таблицу паттернов в ресурс; фильтры ChatHud/PlayerListHud/scoreboard. Перед стартом уточнить объём у пользователя (полный донорский фильтр vs минимальный). Затем `WayPoints/GPS` (нет команды добавления точек — открытый вопрос).

### Открытые решения (ждём пользователя)
- LICENSE: копирайт-строка «StrangeVisuals Team & Contributors» — обновлять ли держателя (правило «лицензию не менять» пока в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы: в MSDF-атласах нет нативных і/ї/є/ґ (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность скриптов при переносах Фазы 5.
- `WaypointsRenderer.clear()` намеренно ничего не делает (комментарий «Keep waypoints on disable») — решить: чистить при disable или нет.
- `WaypointsModule` — нет команды добавления точек (`addWaypoint` нигде не вызывается). Нужен ли `/wp add`?
- StreamerMode: объём порта (см. «Следующая задача»).

## Состояние на 2026-10-19 (чат №6) — v1.0.63

### Сделано
1. **Пачка 1 Ф5.7 закрыта (D34, v1.0.63)**: `SwingAnimation`, `HitColor`, `Freelook`, `ItemPhysics` — полные порты вместо заглушек D33. Хуки: `HeldItemRendererMixin.swingArm` (RETURN, guard main arm), `LivingEntityRenderStateMixin` + `LivingEntityRendererMixin` (`updateRenderState` TAIL, `getMixColor` RETURN), `MouseMixin.updateMouse` (HEAD/RETURN), `CameraMixin` (`@ModifyExpressionValue` на `Entity.getYaw/getPitch`), `ItemEntityRenderStateMixin` + `ItemEntityRendererMixin` (`updateRenderState` TAIL, `render` HEAD cancellable).
2. **Чистая логика вынесена и покрыта тестами**: `util/HitColorMath` (7), `util/ItemPhysicsMath` (6), `util/DragPath` (6), `SwingAnimationModule.curve/xAngle` (7). Тесты: 83, 0 падений (было 50).
3. **Конфиг-гэп закрыт**: `ConfigJson` теперь сохраняет/читает `KeySetting` и `StringSetting` (бинды Freelook/ElytraSwap/HandSwap/Zoom и ники StreamerMode раньше терялись); round-trip-тест в `ConfigJsonTest`.
4. **Убрана инертная настройка** `HitColorModule.colorArmor`: armor-тинт через `EquipmentRenderer` → `submitModel` (color — index 6) не реализован, настройка ничего не делала. Причина и путь возврата — D34.
5. **Ремап-скан**: 47 миксинов собранного jar, 0 yarn-имён в member-ref'ах (узкий и широкий проходы); `HandledScreenAccessor` зарегистрирован в `linkenvisuals.mixins.json`.
6. Мелочи: выравнивание отступа `@Inject` в `HeldItemRendererMixin`; `mod_version` 1.0.62 → 1.0.63.

### Следующая задача
**Пачка 2 Ф5.7** (D34-остаток): `ItemScroller` (drag-перемещение через `HandledScreenAccessor` + `DragPath`, `clickSlot`), `ElytraSwap` (клик по слоту 6), `StreamerMode` (паттерны в ресурс + фильтры ChatHud/PlayerListHud/scoreboard), `TotemTracker` (`ClientPlayNetworkHandler.onEntityStatus` + `EntityStatuses.USE_TOTEM_OF_UNDYING`), HUD-элементы `TotemCounter`/`UseTracker` в `HudManager.ELEMENTS`. Затем — 6 оставшихся `catch (... ignored)` → лог, `WayPoints/GPS`.

### Открытые решения (ждём пользователя)
- LICENSE: копирайт-строка «StrangeVisuals Team & Contributors» — обновлять ли держателя (правило «лицензию не менять» пока в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы: в MSDF-атласах нет нативных і/ї/є/ґ (фолбэк і/ї→i, є→э, ґ→г) — нужна ли регенерация атласа.
- `source/tools/*` — проверить актуальность скриптов при переносах Фазы 5.
- `WaypointsRenderer.clear()` намеренно ничего не делает (комментарий «Keep waypoints on disable») — решить: чистить при disable или нет.
- `WaypointsModule` — нет команды добавления точек (`addWaypoint` нигде не вызывается): модуль визуально инертен без команды. Нужен ли `/wp add`?

## Состояние на 2026-10-18 (чат №5, Muse)

### Сделано
1. **Архивы разобраны**: оба скачаны через Git LFS из ветки `стщеуте1018` (пользователь сам запушил: коммит `chore(archives)`), SHA256 сошлись, распакованы в `~/workspace/mcmerge/archives/`. `StrangeVisuals-1.21.11.zip` (253 МБ) — локальная копия на том же HEAD v1.0.44, реального дрейфа от main нет (только CRLF-шум в 9 ресурсных файлах + 2 пустые папки, `git status` чистый). `визу34алы.rar` (790 МБ) — донор LinkenVisuals 2.0: 779 java-файлов, punchy 181 файл (не трогаем), все цели переноса из PORT_STATUS на месте (MSDF, BlockOverlay, MotionBlurRenderer, Render2D/3D, ViewModel/Hands, косметика 74 файла, модули белого списка сверены точечно).
2. **Ф3.8 закрыта (v1.0.45, D17)** — решение пользователя: триггер edit-mode остаётся T (чат), но через явный флаг. `HudManager`: `isEditScreen`/`instanceof ChatScreen` удалены, новый `editing` + `setEditing`/`isEditing`; `ChatScreenMixin`: инъекции `init`→`setEditing(true)`, `removed`→`setEditing(false)`; тихий `catch (IllegalStateException ignored)` вокруг `applyBlur()` → `LOGGER.warn` один раз (`blurUnsupportedLogged`). Тест `HudManagerEditModeTest` (round-trip флага). Вне скоупа остались `instanceof ChatScreen` в `Animations.chat()`, `HandEditor:45`, `PotionSlot:31`.
3. **Игровой тест Ф3.8 (v1.0.45) подтверждён логом** (`D:\MultiMC\instances\1.21.111\.minecraft\logs\latest.log`, 22:06): запуск `linkenvisuals 1.0.45`, вход в мир «New World», выход; режим редактирования HUD (T / Esc), перетаскивание элементов — спама в логе нет, WARN/ERROR = 0. Ветка `f3.8-hud-edit-flag` смержена в `main` и запушена в `origin/main`.
4. **Ф3.9 закрыта (v1.0.46, D18)** — README-дрейф устранён (en/ru зеркально): категории ClickGUI → `Combat, Movement, Visual, Player, Misc, Config` (факт `Category.java`); конфиг → 8 реальных файлов в `.minecraft/config/` вместо одного; косметика → 9 носимых (`Embers, Halo, Kagune, Orbit, Runes, Seraph, Shards, Spires, Tendrils`), «Bandana» не существует в коде (`grep -rni bandana` = 0); попутно clone-URL → `KurumaOfficial`, jar → `linkenvisuals-<version>.jar`.
5. **Ф3.10 закрыта (v1.0.47, D19)** — аудит без изменения кода: секретов в `AccountStorage` нет (только ники + offline-UUID, токенов/паролей нет); миксины `Yggdrasil*` (2 шт.) — нормализация неподписанных текстур, обхода аутентификации нет, ToS-риск минимальный; Sodium/Iris — 0 упоминаний в коде/`fabric.mod.json`: с Sodium конфликтов архитектуры нет (пост-цепочка через `WorldRenderEvents.END_MAIN`, чанки не трогаются; не тестировалось), с шейдерами Iris пост-эффекты и кастомное небо в финальный кадр, скорее всего, не попадут. Workflow изменён по приказу пользователя: пачки коммитов прямо в `main`, без веток на фичу; тестирование — пачкой после.
6. **Фаза 4 закрыта (v1.0.49, D20+D21)** — каркас модулей: `module/Module` (id/info/enabled, `onEnable`/`onDisable`), `module/Modules` (фасад + `init()`), `ModuleRegistry.registerModule`/`module(id)`; legacy-статики не тронуты; тест `ModuleLifecycleTest` (3 кейса: регистрация, lifecycle на тоггле, молчание на `toggleSilently`).

### Следующая задача (старт нового чата)
**Фаза 5 (ROADMAP)** — переносы из донора по одной фиче на коммит; перед каждой — таблица «параметр → реализация на 1.21.11». Ближайшая: **5.5 Оптимизации** (LRU VoxelShape, int-ядро линий, scratch-буферы, batched dot-grid/hatch — только где есть циклы). Внимание: п.5.7 (модули) — пачками с подтверждением пользователя; скоуп переносов решает пользователь.
1. **Тест Ф3.1 (1.0.35) подтверждён логом** (`D:\MultiMC\instances\1.21.111\.minecraft\logs\latest.log`, 16:37): запуск `linkenvisuals 1.0.35`, вход в мир «New World», выход («Stopping singleplayer server as player logged out» → «Saving worlds»); `wrong thread` = **0** (было 9), всего WARN/ERROR = 0. Критерий закрыт — регрессии «Rendersystem called from wrong thread» на DISCONNECT-пути нет.
2. **Ф3.2 закрыта (v1.0.36, D8)**: config-окно `ClickGuiScreen` (бывшие строки 473–804) вынесено в новый `clickgui/ConfigPanel.java` (486 строк); `ClickGuiScreen` 1794 → 1394 строк; 16 RU-строк → ключи `gui.config.*` (en/ru/uk), резолв через `dev.linkenvisuals.i18n.I18n.get`; заодно локализован placeholder поиска (`gui.search.placeholder`). Поведение не менялось: статические поля остались статическими, скроллбар — колбэк, шрифты биндятся в `init()`.
3. Гигиена: lang-файлы нормализованы в LF (в git-блобах лежал CRLF с прежних коммитов) — CRLF-шум ушёл.
4. `build` зелёный (27 тестов, LangParityTest с новыми ключами), jar `linkenvisuals-1.0.36.jar` задеплоен в MultiMC (старый удалён деплоем).
5. **Ф3.3 закрыта (v1.0.37, D9)**: идентификация виджетов — только по ключам перевода (новый `util/TextKeys`); `TitleScreenMixin`/`GameMenuScreenMixin`/`VanillaStylePipeline` — ключи вместо `contains(...)`, 12 вызовов `getIconFor` → `getIconForKey`; `TextFieldWidgetMixin` и `CameraMixin.setRotation` переведены с ordinal на slice-анкеры по байткоду (javap yarn build.4: format@234/drawText@258/getWidth@269; yaw@258/setRotation@269/getScale@294); ремап целей подтверждён дампом аннотаций собранного jar (промежуточные `class_*`, refmap не нужен). Видимое изменение — семантические иконки кнопок под-экранов настроек (раньше из-за порядка проверок почти все получали шестерёнку) — ждёт игровой проверки вместе с визуалом 1.0.36.
6. **Ф3.4 (п.3) закрыта (v1.0.38, D10)**: 4 loading-миксина (645 стр.) сведены к общему `render/LoadingOverlay` (165 стр.); миксины 645 → 317 стр.; удалены 4 копии `withAlpha` (→ `ColorMath.argb`), 4 копии lazy-загрузки MSDF-шрифтов (теперь статический кэш `LoadingOverlay.titleFont()/textFont()` — один на все 4 экрана) и 4 копии beta/vanilla-сцен; `Screen#renderPanoramaBackground` (protected) передаётся через `@FunctionalInterface PanoramaDrawer`.
7. **Хотфикс краша 1.0.38 → 1.0.39 (D11)**: игра падала на лоаде (`CameraMixin`, «Invalid name: getScale:») — цель slice `to` осталась yarn-именем при ремапе (owner ремапнут, имя нет). Заменена на `Camera.clipToSpace(F)F` (класс, чьи рефы доказанно ремапятся); javap jar 1.0.39 — все цели intermediary; скан всех 40 mixin-классов jar — 0 yarn-имён в member-ref'ах аннотаций (контроль на 1.0.38 находит ровно `class_1309;getScale:()F`).
8. **Ф3.4 (п.4) закрыта (v1.0.40, D12)**: единый цветовой свод — 16 клонов `argb`/`withAlpha` удалены, 302 вызова → `ColorMath.withAlpha(rgb, 0..255)`; `mix`-клоны ClickGui/NameTags/WidgetStylePipeline → `ColorMath.mix` (WidgetStylePipeline.mix удалён, 4 внешних вызова обновлены), `MainMenuScreen.mix` — делегат с прежней непрозрачной семантикой; `centredTop` (ClickGui+ConfigPanel) ≡ `MsdfText.topFor` — 17 вызовов, методы/константы удалены; новый единый `render/Fonts` вместо копий MSDF-обвязки в `LoadingOverlay`/`WidgetStylePipeline`/`CustomMultiplayerScreen`/`MainMenuScreen`/`MainMenuEditorScreen` (+ ConnectScreenMixin); javap-скан jar 1.0.40 — 24 member-ref'а, 0 yarn-имён.
9. **Ф3.4 (п.4b) закрыта (v1.0.41, D13)**: шрифтовые константы `TEXT_*/BOLD_*` ещё в 13 файлах (12 HUD + `clickgui/classic/util/FontDraw`) → `Fonts`; 18 load-сайтов заменены (`text`/`regular` → `Fonts.textFont()`, `bold`/`menuFont` → `Fonts.titleFont()`), поля-алиасы сохранены; `FontDraw.getFont` — тернарник без try/catch; 2 тройные пустые строки-регрессии схлопнуты; миксины не тронуты (javap-скан не требовался). 
10. **Ф3.5 закрыта (v1.0.42, D14)**: `HudLayoutStorage`/`PotionLayoutMode` → атомарные записи; найдены и переведены 4 ручные копии tmp+ATOMIC_MOVE (AccountStorage, HandPose, MacroManager, MainMenuConfig) — теперь 7/7 конфиг-писателей через `util.AtomicFiles` (укреплён: tmp удаляется best-effort при сбое); `ConfigManager.CONFIG_VERSION = 1` пишется в профиль/глобальный/legacy-миграцию, при чтении — warn «файл новее сборки».
11. **Ф3.6 закрыта (v1.0.43, D15)**: пер-кадровые аллокации убраны — `KeybindPanel` пересобирал `entries` из `width()` каждый кадр → кэш по новому `ModuleInfo.revision()` (4 точки мутаций кейбиндов: arm/cancel/resolve/clear; uppercase-ключ строится один раз в `Entry`); `Cosmetics` 6 × `new CosmeticContext`/кадр → переиспользуемый `CosmeticContext.set()` (конструктор делегирует `set`, прочие 4 создающих сайта не тронуты); `HudManager` drag-путь: `double[1]`×2 + `float[2]`×2/кадр → статические `CURSOR_X/Y`/`SNAP_OUT`; `Pipelines.warmUp()` из `render()` → одноразовый `if (!loaded)` первого кадра. Миксины не тронуты (javap-скан не требовался).
12. **Ф3.7 закрыта (v1.0.44, D16)**: `MediaWatcher` — `-ExecutionPolicy Bypass` убран (доказано: политика не гейтит stdin — прогон под форс-`Restricted` дал 6 строк/8 c, как и без флага); SCRIPT переписан в 9 строк, каждая законченный statement (PS 5.1 может исполнять stdin построчно: старый многострочный скрипт на .NET-стенде молча умирал за 0.5 c/0 байт, новый — жил 9 c/6 строк; Java-репродукция мода зелёная во всех прогонах); не-Windows → один INFO «unsupported platform» (раньше — молча); внешний скрипт `config/linkenvisuals_media.cmd|.sh` заменяет встроенный (тот же TAB-формат, описан в javadoc); `parse` + `MediaWatcherTest` (5 тестов).

### Следующая задача (старт нового чата)
**Ф3.8 (ROADMAP п.8)** — `HudManager`: edit-mode не через «is a ChatScreen»; убрать тихий catch. Дальше по порядку: Ф3.9 README-дрейф → Ф3.10 секреты/Yggdrasil/Sodium-Iris.

### Открытые решения (ждём пользователя)
- LICENSE: копирайт-строка всё ещё «StrangeVisuals Team & Contributors» — обновлять ли держателя (правило «лицензию не менять» пока в силе).
- `MASTER_PROMPT.md` — убирать ли из публичного репо.
- UK-глифы: в MSDF-атласах нет нативных і/ї/є/ґ (работает фолбэк і/ї→i, є→э, ґ→г) — нужна ли отдельная задача регенерации атласа.
- `source/tools/*` — проверить актуальность скриптов при переносах Фазы 5.

### Проверенные факты (для следующего чата)
- Сборка: `cd source; $env:JAVA_HOME='C:\Program Files\Java\jdk-21'; cmd /c ".\gradlew.bat build --console=plain 2>&1" | Select-Object -Last 40`; бамп версии — `-PbumpVersion` на ОДНОМ вызове Gradle (не на каждом).
- `git push` работает (GIT_TERMINAL_PROMPT=0); деплой в MultiMC автоматический (`multiMcModsDir` из `%USERPROFILE%\.gradle\gradle.properties`), старые `linkenvisuals-*.jar` удаляются перед копированием.
- Логи для игровых проверок: `D:\MultiMC\instances\1.21.111\.minecraft\logs\latest.log` (+ ротация `*.log.gz` рядом).
- Кириллица в консоли: `chcp 65001`; в PS 5.1 `[System.IO.File]::ReadAllText` — только абсолютные пути; lang-файлы теперь LF.
- i18n: собственный движок `dev.linkenvisuals.i18n.I18n.get(key, args)` — встроенные бандлы en/ru/uk из classpath, фолбэк en → vanilla → сам ключ; `%s`-формат безопасен (исключения логируются).
- Миксины и ремап: refmap не используется (`linkenvisuals.mixins.json` без поля `refmap`) — loom ремапит цели `@At`/slice на `remapJar`; проверка после сборки — `javap -v -p` по классу из jar: цели должны быть в промежуточных именах (`class_*`/`method_*`/`field_*`).
- Ремап-скан ОБЯЗАТЕЛЕН на каждой сборке с новыми целями: yarn-имя, оставшееся в `target=` (не `method_`/`field_`/`class_`), = гарантированный краш при лоаде («Invalid name: …»), даже если билд зелёный. Regex для скана всех mixin-классов: `Lnet/minecraft/[A-Za-z0-9_$/]+;(?!method_|field_|class_)[A-Za-z_][A-Za-z0-9_]*(?=:|[(])` — должно быть 0 совпадений (контроль 1.0.38: ровно `class_1309;getScale:()F`). Пример: D11.
- Байткод-эталоны yarn build.4 для анкеров Ф3.3 лежат в `%TEMP%\sv_audit\` (`tfw.txt`, `cam.txt`, `title.txt`, `menu.txt`, `en_us.json` — ванильные lang-ключи 1.21.11). Именованные jar-ы: `%USERPROFILE%\.gradle\caches\fabric-loom\minecraftMaven\...\build.4-v2\*.jar` (javap без декомпиляции).
- Цвет/шрифты после D12/D13: единый источник — `util.ColorMath` (`withAlpha(rgb, byte)` clamp, `argb(rgb, float 0..1)`, `mix` дропает альфу) и `render.Fonts` (`attempted`-флаг; `GlyphAtlas.load` кэширует по Identifier — дублировалась только обвязка; весь MSDF-свод охватывает LoadingOverlay, 3 экрана, WidgetStylePipeline, 12 HUD-файлов и FontDraw). Массовые своды делать скриптом с пофайловыми count-проверками и абортом до записи (шаблон: `%TEMP%\opencode\refactor_34p4.ps1`); удаление методов — балансным сканом скобок, не regex'ом до первой `}`-строки (тела с try/catch ломают наивный regex); после сводов проверять тройные пустые строки (в этот раз 3 штуки, схлопнуты; 2 в MainMenuScreen — пре-существующие).
- Ф3.6-факты: `ModuleInfo.revision()` — static-счётчик мутаций кейбиндов (`++` в `armBinding`/`cancelBinding`/`resolveBinding`/`clearBinding`); кэш `KeybindPanel` привязан к нему — новые мутации кейбиндов вводить только через эти методы, иначе панель не обновится. `CosmeticContext` теперь мутабельный и переиспользуемый: ссылка, полученная из `Cosmetics.context(...)`, валидна только до следующего `set(...)` — не сохранять её; `Cosmetics` держит один статический `CONTEXT`, остальные 4 создающих сайта (TargetMarks/HitEffects/KillEffects/RainFall) по-прежнему создают свои экземпляры. `HudManager` drag-скретчи (`SNAP_OUT`, `CURSOR_X/Y`) — render-thread-only. Вызовы `Pipelines.warmUp()`: `Fonts.ensure` + `init()` MainMenuScreen/Editor + одноразовый блок первого HUD-кадра.
- Повторная сборка в той же фазе — БЕЗ `-PbumpVersion` (флаг бампает на каждом вызове; повтор даст лишний бамп).
- Ф3.7-факты: `MediaWatcher.SCRIPT` — каждая строка обязана быть законченным PowerShell-statement'ом: `-Command -` может исполняться построчно (зависит от хоста), многострочные конструкции в этом режиме молча убивают весь stdin-вход (exit 0, 0 байт) — не возвращать многострочность. Java-репродукция транспорта мода: `%TEMP%\opencode\MediaProbe.java` (режимы stdin / stdin-bypass / stdin-restricted / encoded), извлечение SCRIPT из java-файла — по маркерам text-block (скрипт-тела: `%TEMP%\opencode\media_script*.txt`). Внешний скрипт: `config/linkenvisuals_media.cmd` (Windows, `cmd /c`) или `config/linkenvisuals_media.sh` (прочие, `sh`); формат строки: title TAB artist TAB app TAB playing(0|1) TAB pos_ms TAB len_ms. `-ExecutionPolicy` не передаётся (политика не гейтит stdin-команды — проверено под `Restricted`).
