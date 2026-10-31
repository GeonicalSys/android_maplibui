---
id: maplibui-20261030-resource-caption-layout
area: Полный текст мобильного интерфейса
kind: user
at: '2026-10-31T01:39:20.452391+00:00'
title: Полные подписи и название ресурса при крупном шрифте
why: Способ импорта мог обрезаться, вытеснять название при сужении и оставлять конец пути за краем прокрутки.
covers:
- src/main/java/com/nextgis/maplibui/view/NgwResourceRowLayout.java
- src/main/res/layout/row_ngwlayer_check.xml
- src/main/res/layout/row_ngwlayer_doublecheck.xml
- src/main/res/layout/layout_resources.xml
- AGENTS.md
- docs/README.md
- docs/manifest.yaml
---

Три caption width=wrap_content сохраняют полный owned RU/EN текст. NgwResourceRowLayout сначала измеряет обычную горизонтальную строку; лишь фактический glyph clipping имени переводит две статические header/mode группы друг под друга. Перед обычным и full-width измерением локально forceLayout для каждого descendant обновляет getLayout до решения о размещении; parent requestLayout/reparent нет. При расширении та же строка возвращается в горизонтальную компоновку. Шрифт, IDs, порядок controls, listeners/CheckState/local-vs-remote identity/JSON/Parcel/import/refresh прежние. Shared breadcrumb layout переносит5dp child margin в5dp padding HorizontalScrollView: API28 clamp учитывает padding, а child margin не учитывает. Визуальный отступ сохранён, конец пути достижим в технической модели.

Фактически5 caption layout methods PASS/0 failures/errors/skips, XML10.06s. Ораклы и whole fixture SHA256 cc1fb961f2459d6b9e71097f403f5898b82aaa2425093b6157ee76edf03d06ac неизменны относительно предыдущего4PASS/1FAIL. Finalized Root receipt SHA256 68f7f6bdc072f9f25fd2af02e0584ac467a4dbb1a0c1b1e1959341b2126630ef. В этом запуске общий cohort10 НЕ PASS: 6 PASS/4 отдельные Panel assertion FAIL, Gradle exit1; 120 входов без drift, все13 owned processes settled absent, наблюдалось110.0625s. Это технические API28 CPU native Paint/text measurements, без Android device/OEM/GPU/display/GIS native/touch/rotation acceptance. Исторический cohort10 checkpoint предшествовал consumer compile. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.

Первый combined55 attempt: Core29 реально исполнился (15 assertion FAIL/14 controls), App0: fixture не скомпилировался из-за missing RobolectricTestRunner import. Старый Checkbox22 XML был STALE и исключён; предварительные51 copied cases не являются51 выполненной проверкой. V3 добавил ровно один импорт. Legacy App26 затем реально25 PASS/1 path FAIL; первые3 caption methods PASS на старом XML, поэтому это НЕ crop RED. NativeV4 class-only CPU glyph/font backend с setup calibration воспроизвёл4 assertion FAIL на старом XML. После3 caption width attrs Native4 дал2 PASS/2 assertion FAIL: имя Collector при280dp/font2RU и конец пути. Static responsive groups и перенос5dp margin→scroll padding дали4 PASS/1 transition FAIL в frozen5 fixture. Новая локальная инвалидация descendant measure cache проверяется теми же5: fixturecc1f, все assertions/setup неизменны. Core29 PASS относится к отдельной cache-проверке, combined55 PASS не заявляется. Исторические37/89 и их source pins не перезапускались.

Полная owned copy и CPU glyph bounds не принимают фактическое число доступных строк, touch targets/scroll/OEM/font/theme/rotation/display-size/installed APK/phone performance. font1.3 и реальные orientation/display combinations не исполнены. Единственный narrow Root risk AUDIT-TODO-MOBILE-RESOURCE-ROW-LAYOUT; broader UI audit продолжается, не all-screens PASS. F61 clock/Wiki/pointer blockers прежние.

Public source recipe: App android-20261030-resource-caption-layout-regression.
