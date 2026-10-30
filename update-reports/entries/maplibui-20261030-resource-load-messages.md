---
id: maplibui-20261030-resource-load-messages
area: Сообщения мобильного приложения
kind: user
at: '2026-10-30T12:45:34.021262+00:00'
title: Сбой загрузки списка ресурсов больше не выглядит пустым списком
why: loadChildren молча возвращал после HTTP failure, а async adapter показывал успешное завершение.
covers:
- src/main/java/com/nextgis/maplibui/dialog/NGWResourcesListAdapter.java
- src/main/res/values/resource_list_error_strings.xml
- src/main/res/values-ru/resource_list_error_strings.xml
---

Подготовлено сообщение после failed child-list request: known server outage даёт
общий Core текст о возможных техработах/перегрузке и повторе позже,401/403 — прежние
разные login/rights, неизвестный IO/JSON — human retry copy. Successful empty[]
остаётся штатным пустым списком. Raw status/body/URL/exception в UI не передаются.

Core per-invocation ChildrenLoadResult и Connection delegate поставляются в Core63.
Legacy void API, sort/cache/parcel и post-execute live Activity window guard прежние.
Connection.connect(false) сохраняет общий connect-failed outcome; partial child
parse/retry остаётся отдельным вопросом. Не заявлено покрытие всех запросов NGW.

App actual resource/background fixture9 исполнен PASS внутри итоговых89 source
checks (Core46/App43,0fail/errors/skips, XML10.995с/Gradle0/50с). Technical transport
shadow подтверждён этими actual methods, не socket/server acceptance. Первый85
остаётся NOT_PASS81/4assert; после central408 guard и одной manual Producer oracle
correction8→12 повтор89 принят отдельно. Cached result200 — local success sentinel
без нового GET, не observed response. Setup/compile failure не RED.
No live server/socket/map/SQLite/GUI/device/native acceptance. Core/UI Merge Commit
до App pin; Draft source изменения ещё не являются опубликованным приложением.

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
