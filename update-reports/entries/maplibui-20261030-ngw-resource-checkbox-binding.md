---
id: maplibui-20261030-ngw-resource-checkbox-binding
area: Выбор ресурсов мобильного приложения
kind: user
at: '2026-10-30T14:16:51.980922+00:00'
title: Галочки и подписи ресурсов не наследуют состояние соседней строки
why: Переиспользованный checkbox зависел от порядка чужих CheckState, а подпись raster могла оставаться
  от vector/Collector.
covers:
- src/main/java/com/nextgis/maplibui/dialog/NGWResourcesListAdapter.java
- src/main/java/com/nextgis/maplibui/util/NgwResourceSelectionState.java
- AGENTS.md
- docs/README.md
- docs/manifest.yaml
---

Исправлена отрисовка mode по ANY matching process-local CheckState.id:
bind снимает listener, устанавливает полное true/false ровно один раз, затем
возвращает listener, не меняя список выбора. False очищает requested mode у всех
matching записей и удаляет только bothfalse; true не добавляет положительный
duplicate при уже выбранном mode. Другой mode/ID/порядок/identity сохранены.
Recycled raster/WMS/webmap получают owned raster label заново; physical checkbox1
у vector без style/Collector по-прежнему вызывает semantic mode2. Auth guard прежний.

Owned compact JSON restore после каждого normal resolve и parse flags объединяет
только same newly-resolved localID OR flags в первой новой записи. Different
accounts/localIDs, first order, missing later resource и malformed later flag
сохраняют прежний исход. JSON schema, save filter, external setter/Parcel и import
loops не нормализуются; generic import dedup не заявлен. Refresh/tree policy и
дальнейшие неизвестные едины в Root `AUDIT-TODO-NGW-RESOURCE-BROWSER-REFRESH`. Contract `UI-NGW-RESOURCE-CHECKBOX-SELECTION` фиксирует полную
цепочку XML → binding → checks → оба import consumers → saved-state boundaries.

Состояние: actual37 App-owned API28 models PASS (22+6 new и9 protected browser),
baseline13assertFAIL/24controls →37GREEN. Source compile0/BUILD47s; native acceptance NOT_RUN. Invalid9f25 source review
отозван доC; corrected606 whole-class review не менял22+6 fixture expectations.
Исторические message89PASS/Belka59 имеют другой Adapter source и не принимают
этот patch. Никаких real account/socket/DB/import writes, device/rotation/largefont,
Service/APK/full-ecosystem проверок здесь нет. Wiki, F61 clock и library/App pins
не подгоняются. Public focused recipe и последующие факты — App `android-20261030-ngw-resource-checkbox-selection`.


Фактическая source-проверка37: Actual same37 API28 models PASS/0fail/errors/skips:22 checkbox/label +6 owned restore +9 protected browser messages,3XML6.846s,Gradle0/BUILD45s. Meaningful old-source baseline37 has13assertFAIL/24controls/0errors/skips,XML6.493s,Gradle1/BUILD57s. Same c683/f46 fixture bytes/method sets unchanged. Baseline134 raw events/133unique includes duplicate FAILED status; green133/133 exact reviewed SET with different order.81 protected inputs exact,all13 baseline/all14 green owned processes settled absent. First dry prefix-classifier false positive and first raw-event133 observer assumption remain explicit diagnostic history, not retroactive gate PASS; actual tests were not rerun for those observer corrections. Invalid9f25 source was held beforeC and replaced by reviewed606 without oracle changes. BelkaRelease source compile exit0/BUILD47s (49.136s observed),81 inputs exact,all10 owned processes settled absent. Actual100 task SET matches reviewed100 with different order; UIJava/AppJava+Kotlin executed, CoreJava UP-TO-DATE. Four Java JAR class counts28/535/670/45; no dex/so/APK/AAB/AAR. This is source compilation, not installed-app/native/Service/DB/server/import/layout-fit acceptance. Native/device/Activity lifecycle/rotation/realserver/DB/import writes/largefont/APK/refresh acceptance NOT_RUN; screenshot layout fit not covered. Historical full message89/Belka59 not rerun and used old Adapterb703.


Actual source-compile recipe: `& '.\gradlew.bat' '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:compileBelkaReleaseJavaWithJavac' ':app:compileBelkaReleaseKotlin'`
Unit exit проверяется до compile; других runtime stages команда не разрешает.

### Закрытие source-среза выбора ресурсов — 30 октября2026

Index --write/--check завершены0/CURRENT:5763 files/78874 edges, source SHA256
`147d131eb7b353470a58168bab7f5db06dc1c239fbb7f7f71cbafb8d5dbba8e8`.
Basis — четыре own working trees и два foreign committed HEAD; прежние188
observations побайтно сохранены. Полнота связей/geo.json/exclusions остаётся UNKNOWN.
После source37/compile неизменны80 из81 input; единственный намеренный delta —
reviewed UI catalogue44/177/44 (включая9 XML witnesses). Код/тесты не переписаны.

App docs-check26 policy tests PASS15.845s. Final local UI/docs validator оставляет
ровно прежний WF-SYNC-CLOCK-STORE; отдельные57 docs tests —53PASS/4fail того же
F61 clock contract. Первый docs проход дополнительно нашёл запрещённый placeholder
substring в новом maintained README: исправлено только указание на canonical risk
через consuming contract; исходные failed logs сохранены, checker не ослаблялся.
Root docs validator оставляет одну прежнюю missing-frontmatter ошибку excluded
Wiki backup REVERT.md. Wiki и clock expectations не менялись, full docs не PASS.
Report guards Root377/35 App130/21 Core57/20 UI38/13 пройдены отдельно.

Обычные процессы JVM завершились, новые temporary JVM dirs остаются на Q:
6,208,233 bytes в пяти owned paths. Native PowerShell cleanup отклонён executor
policy до запуска при full access/approval never; ничего не удалено и обходов
не было. Пять записей добавлены в общий pending-cleanup с сохранением прежних42.
Компактные source/XML/stop отчёты удерживаются до окончания аудита/PR review;
shared caches и пользовательские GPX/изображение не являются task garbage.

Это source Draft checkpoint. Отдельные новые сообщения владельца о невидимости
local/WebGIS слоёв и обрезанном caption ещё расследуются; source37 проверяет
selection/value binding, не реальную отрисовку/fit/native lifecycle или эти fixes.
Library pins F61/E66 и порядок Core/UI Merge Commit → App pin/semantic clock review
→ App/Root Squash остаются до отдельной команды на merge/release.
