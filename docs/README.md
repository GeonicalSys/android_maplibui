---
title: maplibui — GIS UI, layer fill и Collector orchestration
module_id: maplibui
last_verified: 2026-07-19
---

# maplibui — GIS UI, layer fill и Collector orchestration

## Назначение

UI-библиотека и runtime orchestration: выбор NGW resources, создание/настройка
слоёв, batch fill, layer list/reorder, edit overlays, sync/account UI,
Collector workspaces и защитные backups.

## Основные сценарии

- импорт обычных NGW и Collector resources;
- вставка NGRc/raster/vector layers в правильном порядке;
- deferred reload карты после batch fill;
- изолированные Collector projects и переключение composition;
- schema rebuild/removal только после успешного backup;
- track/edit/form UI и foreground workers/services.

## Ограничения

- Нет compile dependency на `app`.
- LayerGroup index `0` — bottom; UI и MapLibre должны совпадать.
- Backup failure блокирует destructive mutation.
- Collector project UID/map path не смешиваются между workspaces.
- `SYNC_NONE` оценивается отдельно для feature data и поддерживаемой config logic.

## Диагностика

- Долгий/зависший fill: `LayerFillService`, notification/foreground lifecycle,
  SQLite transaction и deferred map reload.
- Неверный порядок: insertion index в model и последующий style reload.
- Потеря слоя после composition: backup result и removal scheduling.
- Неверный проект после restart: registry JSON, active project и map path.

## Проверки

Собрать `:maplibui:assembleDebug`, затем выполнить относящиеся device smoke IDs.
