---
id: maplibui-20261125-preserve-edits-layer-replacement
area: Синхронизация слоёв NextGIS Web
kind: user
at: '2026-11-25T12:38:00+04:00'
title: Заменять пересобранный слой с формой и сохранять очередь правок
why: При загрузке формы терялась позиция замены и в проекте оставалась копия слоя с устаревшей схемой.
covers:
  - src/main/java/com/nextgis/maplibui/GISApplication.java
  - src/main/java/com/nextgis/maplibui/service/LayerFillService.java
  - src/main/java/com/nextgis/maplibui/util/SchemaRebuildRetryGuard.java
  - src/main/res/values/strings.xml
  - src/main/res/values-ru/strings.xml
  - src/test/java/com/nextgis/maplibui/util/SchemaRebuildRetryGuardTest.java
  - agent-system/knowledge/documentation/schema-rebuild-deferred-render-callback-and-ordering-diagnosis.json
  - docs/README.md
  - update-reports/entries/maplibui-20261125-preserve-edits-layer-replacement.md
---

Загрузка формы передаёт позицию замены и настройку отложенной перерисовки дочерней задаче загрузки. Исходный слой и его очередь остаются доступны, пока приложение не подтвердит отправку или сверку. Удаление предыдущих копий требует резервных копий, повторной проверки очереди и успешного сохранения состава карты. Ранее исчерпанные попытки загрузки с потерянным признаком замены не блокируют исправленный сценарий.

На следующей синхронизации можно восстановить проекты, оставшиеся со старой SQLite или с двумя копиями. При одной очереди сохраняется копия с правками. При нескольких очередях сохраняются все копии до сверки.
