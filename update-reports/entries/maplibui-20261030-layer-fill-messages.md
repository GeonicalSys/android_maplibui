---
id: maplibui-20261030-layer-fill-messages
area: Сообщения мобильного приложения
kind: user
at: '2026-10-30T12:45:34.022353+00:00'
title: Загрузка слоя показывает безопасное объяснение ошибки
why: В progress и Toast попадали raw IOException/SQLite/JSON/NGException; отсутствующее поле JSON ошибочно объявлялось403.
covers:
- src/main/java/com/nextgis/maplibui/service/LayerFillService.java
- src/main/java/com/nextgis/maplibui/util/LayerFillErrorMessage.java
- src/main/res/values/layer_fill_error_strings.xml
- src/main/res/values-ru/layer_fill_error_strings.xml
- AGENTS.md
- docs/README.md
- docs/manifest.yaml
---

Подготовлена единая безопасная подача caught failures в progress/toast. Известная
typed HTTP server cause даёт общий Core текст о возможных техработах/перегрузке;
owned offline/auth/rights/CRS/bad-file/geometry explanations сохраняются. Остальной
technical exception получает понятный fallback, а детали остаются в прежних логах.
No value for fields больше не считается свидетельством отсутствия прав.

Typed Core initial feature IOException и metadata NGException сохраняют старые
diagnostic/localized message и разные retry/catch boundaries. Owned text equality
используется только как content allowlist, не HTTP classifier. Numeric-only owned
count-mismatch explanation сохраняется без arbitrary suffix. POINTZ flag/особый
dialog/no-toast path и coordinate cancellation gate прежние; source import/SQL/
rollback/saved data policy не меняются.

App presenter10/Core typed5+5 fixtures прошли в итоговых89 source checks:
Core46/App43,0fail/errors/skips, XML10.995с/Gradle0/50с. Core IO typed5 — plain JUnit,
response typed5/App10 — API28 models; это не native Service/import. Первый85
остаётся NOT_PASS81/4assert: в том числе App presenter подтвердил408+externalDB
false-maintenance. Исправлен central422-only classifier, не safe UI ожидание;
один unrelated Producer stale mask8→12 явно приведён к исходному mixed контракту.
Belka source compile0/59с; source68 unchanged во время проверок, unit15/compile13
процессов завершились. Resource/service bindings reviewed by source, actual
Service/native import не исполнялся.
Core/UI Merge Commit до App pin, затем обычные device/GUI/release gates отдельно.
Draft source delivery не является публикацией исправления установленным клиентам.

### Закрытие source-среза сообщений — 30 октября2026

После semantic review документов global dependency index проверен CURRENT:
5761 files / 78853 edges, source SHA256
`3742037ad3e46bdbb2183c026b451b4abfa69a0d81ccc6c9aa8fa4b6207d71c1`.
Basis: own Root/App/Core/UI working trees, foreign easypicker/publisher committed
HEAD snapshots; 188 historical observations сохранены побайтно. Полнота связей
остаётся UNKNOWN. Final impact сопоставляет все36 changed source/test/resource
paths; это план, не исполнение дополнительных сценариев.

App docs-check:26 policy tests PASS; local UI validator оставляет ровно прежний
WF-SYNC-CLOCK-STORE. Отдельные57 docs tests:53PASS/4fail того же F61 clock contract.
Его ожидание не подгонялось под live unmerged library. Root docs validator:
ровно одна прежняя ошибка frontmatter в excluded Wiki backup REVERT.md; Wiki
не менялась. Поэтому полные docs suites не объявляются PASS.

После metadata review из68 inputs выполненных89/compile проверок неизменны67
behavioral/source/runtime inputs; единственное намеренное изменение — reviewed
UI contract catalogue. Код/тесты после89PASS и Belka source compile не менялись.
Fresh fetch всех6 repositories успешен, owned branches ahead/behind0/0 перед
коммитом. App gitlinks F61/E66 сохраняются до library Merge Commit; source Draft
не является готовым APK. Report guards пройдены отдельно от docs limitations.
