---
title: maplibui — GIS UI, layer fill и Collector orchestration
module_id: maplibui
last_verified: 2026-07-24
---

# maplibui — GIS UI, layer fill и Collector orchestration

## Назначение

UI-библиотека и runtime orchestration: выбор NGW resources, создание/настройка
слоёв, batch fill, layer list/reorder, edit overlays, sync/account UI,
Collector workspaces и защитные backups.

## Основные сценарии

- импорт обычных NGW и Collector vector/style resources;
- импорт vector/raster NGW-ресурса по прямому URL через общий fill pipeline;
- вставка NGRc/raster/vector layers в правильном порядке;
- deferred reload карты после batch fill;
- изолированные Collector projects и переключение composition;
- schema rebuild/removal только после успешного backup;
- track/edit/form UI и foreground workers/services.

## Ограничения

- Нет compile dependency на `app`.
- LayerGroup index `0` — bottom; UI и MapLibre должны совпадать.
- Collector fill вставляет project-managed слои ниже «Мои треки» и применяет
  editable-флаг элемента проекта отдельно от общего mobile config.
- Activity и Dialog используют единый `CollectorProjectImportHelper`; initial
  import и composition sync создают штатные QGIS styles только через
  `CollectorRasterLayerHelper`, в общем порядке с vectors и всегда read-only.
- Backup failure блокирует destructive mutation.
- Collector project UID/map path не смешиваются между workspaces.
- Карта приложения переоткрывается потокобезопасно, ContentProvider следует активному workspace,
  а project switch запрещён до остановки записываемого трека.
- `SYNC_NONE` оценивается отдельно для feature data и поддерживаемой config logic.
- Успешная preprocessing-задача без собственного слоя не вставляет `null` в
  `LayerGroup`; отсутствие server `data.write` оставляет pull, но запрещает edit/push.
- Сравнение сохранённых строковых значений формы с typed controls выполняется по
  строковому представлению, чтобы число `42` не считалось ложной правкой к `"42"`.
- Успешная серверная авторизация не считается добавлением Веб ГИС, пока
  `AccountManager` не создал и не вернул variant-specific Android account; при
  локальном отказе форма остаётся открытой и пишет безопасную диагностику без credentials.
- Mobile/Collector behavior определяется `IGISApplication.isCollectorApplication()`,
  а не жёстким сравнением package name, чтобы suffix `.geonical`/`.debug` не менял UI сервисов.

## Диагностика

- Долгий/зависший fill: `LayerFillService`, notification/foreground lifecycle,
  SQLite transaction и deferred map reload.
- Неверный порядок: insertion index в model и последующий style reload.
- «Нет редактируемых слоёв»: проверить Collector item `editable`,
  `managed_by_project` и исходящее направление sync.
- Потеря слоя после composition: backup result и removal scheduling.
- Неверный проект после restart: registry JSON, active project и map path.
- Пустая/чужая история треков после switch: проверить active project preference, создание нового
  `MapDrawable` и перепривязку `LayerContentProvider` к тому же workspace.
- Валидный вход закрывается без аккаунта: проверить совпадение account type в
  runtime, authenticator и sync adapter, затем сообщения `NGW account add` в HyperLog.

## Проверки

Собрать `:maplibui:assembleDebug`, затем выполнить относящиеся device smoke IDs.
