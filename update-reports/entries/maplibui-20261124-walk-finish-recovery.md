---
id: maplibui-20261124-walk-finish-recovery
area: Мобильное приложение
kind: user
at: '2026-11-24T14:40:27.492949+00:00'
title: Повторное завершение остановленного обхода
why: Оставить черновик доступным при незавершённой записи контрольной точки
covers:
- agent-system/knowledge/documentation/rel03-ordered-walk-journal-and-backup-budget.json
- docs/README.md
- src/main/java/com/nextgis/maplibui/service/WalkEditService.java
- update-reports/entries/maplibui-20261124-walk-finish-recovery.md
---

Команда FINISH для остановленного обхода сразу пытается сохранить финальное состояние. Пока writer удерживает контрольную точку, хранилище отклоняет переход и оставляет черновик доступным для повтора. Обход с активной записью сохраняет барьер FINISHING и порядок сохранения точек.

Проверка потребителя проходит через карту, штатную форму и запись SQLite. Адресные результаты и приёмка выпуска учитываются отдельно.
