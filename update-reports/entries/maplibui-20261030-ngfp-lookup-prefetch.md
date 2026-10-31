---
id: maplibui-20261030-ngfp-lookup-prefetch
area: Справочники форм NGFP
kind: user
at: '2026-10-30T05:30:49.581349Z'
title: Подготовка справочников учитывает вложенные вкладки формы
why: Root-only reader пропускал nested/orientation lookup и возвращал повторяющиеся ID
covers:
- src/main/java/com/nextgis/maplibui/util/LayerUtil.java
- src/main/java/com/nextgis/maplibui/GISApplication.java
- src/main/java/com/nextgis/maplibui/service/LayerFillService.java
- AGENTS.md
- docs/README.md
- docs/manifest.yaml
---

Новый prefetch-only reader передаёт двум прежним загрузчикам lookup bindings root
array, рекурсивных pages и обоих declared orientations. Android getLong нормализует
ID, первый порядок сохраняется без повторов; malformed reference/-1 sentinel прежние.
Старые17 строк public fillLookupTableIds и весь NGWVectorLayerUI/delete побайтово
сохранены: новая подготовка не расширяет destructive readers до nested ссылок.

Это не automatic missing-cache/hash repair, global uniqueness, безопасное удаление
аккаунта/shared lookup или persistence/retry гарантия. Account cast не менялся,
backup/owner policy отдельно открыта. Public recipe/env и source hashes сохранены
в новой App internal карточке android-20261030-ngfp-lookup-prefetch-regression,
а не только в Q evidence.

Actual SDK28 reader: baseline12/3 named AssertionFAIL/9PASS/0errors/skips; после A3
тот же test86e3 и12 names —12PASS/0errors/skips, XML2.149с, Gradle0/49с. LisaDebug
changed Java/Kotlin compile включён. BelkaRelease Java/Kotlin0/41с;87 actionable,
6executed/81up-to-date. Technical Q JSON без account/map/SQLite/NGW/device/native.
Старые cache9/clock19/journal22/storage21 и desktop1117/1192 не перезапускались.

Operational event остаётся NOT_PASS: Debug-only local JAR classifier отметил четыре
Release bundleLibCompileToJar, а PowerShell продолжил разрешённую компиляцию после
Python exit1 до corrected receipt. Post-readback whole100 task list exact approved;
cached AGP9.1.0/output archives подтвердили4 classes.jar (28/530/669/45 class files,
0dex/so/APK), а не product/native задачи. Это не retroactive prelaunch gate PASS.
Checked-stage wrapper проверяет LASTEXITCODE после каждого native call; controlled
exit7 →outer1, would-launch marker отсутствует/новый JVM не запускался. Rerun для
сокрытия события не было. Все owned процессы естественно stopped:
greenDry6/green12/compileDry5/compile7, technical JSON finally удалены.

Own-live source b645/11ee/d231 +test86e3 отдельно от committed App UI E66/maplib F61.
Library Merge Commit → App pointer после merge. Routine source/docs commit/push/Draft
разрешены; это не merge/APK/release/deploy/native acceptance. App production новую API
не вызывает; test reflection не принимает parent E66. UI39/142/19/F61 неизменён.
Новые metadata official docs/report checks после C-интеграции NOT_RUN, старый clock
drift не снят, общего validation/allgatesPASS нет.


### Проверки после интеграции metadata — 30 октября2026

Предыдущая NOT_RUN пометка относится к source freeze. UI report guard теперь
actual0/PASS34cards/3sourcepaths; App guard127/10 и policy26PASS18.312с. App full
docs NOT_PASS ровно preserved F61 clock guard;57 docs53PASS/4sameFAIL44.767с,
новых NGFP metadata errors нет. Root source index --check actual0/CURRENT5741,
fourOWN-live включая UI; semantic completeness UNKNOWN. Source3/test/legacy
delete/UI3981 byte-preserved; JVM12/Belka compile повторно не запускались.
