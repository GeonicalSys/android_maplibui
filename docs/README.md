---
title: maplibui — GIS UI, layer fill и Collector orchestration
module_id: maplibui
last_verified: 2026-08-21
---

# maplibui — GIS UI, layer fill и Collector orchestration

## Назначение

UI-библиотека и runtime orchestration: выбор NGW resources, создание/настройка
слоёв, batch fill, layer list/reorder, edit overlays, sync/account UI,
Collector workspaces и защитные backups.

## Основные сценарии

- импорт обычных NGW и Collector vector/style resources;
- безопасное сообщение об ошибке подключения в выборе NGW-ресурсов без попытки
  открыть окно через application context или уничтоженную Activity;
- импорт vector/raster NGW-ресурса по прямому URL через общий fill pipeline;
- локальный KML/GPX направляется в отдельную fill-задачу, которая создаёт один
  редактируемый точечный слой и удаляет его целиком при ошибке разбора/записи;
- вставка NGRc/raster/vector layers в правильном порядке;
- deferred reload карты после batch fill, который остаётся pending до фактического
  завершения MapLibre style/source apply;
- изолированные Web GIS/local projects, atomic registry/sidecar, switch/create/
  rename/delete и project-wide operation leases; fill заранее резервирует workspace,
  но ждёт завершения sync перед доступом к SQLite;
- попытка импортировать новый Collector-проект во время sync/fill не изменяет
  реестр и показывает отдельное окно с просьбой дождаться завершения операции;
- staged schema rebuild/removal только после успешного backup, с ограничением
  повторов неизменного mismatch fingerprint;
- toolbar Back в NGW resource tree поднимается к родительскому каталогу и
  закрывает экран только из корня;
- просмотр вкладок свойств NGW-слоя не меняет направление синхронизации;
  направление можно вернуть из «только с сервера» в двустороннее по политике
  владельца слоя, даже если generic mobile `is_editable` у Collector-слоя false;
- track/edit/form UI и foreground workers/services;
- фото-вложения по умолчанию получают видимый штамп координат из геометрии объекта;
  оба preference можно выключить в настройках карты;
- `TrackerService` и `WalkEditService` записывают GPS через общий фильтр до
  160 км/ч, выгружают последние буферизированные точки при остановке и публикуют
  безопасные счётчики причин отбрасывания; при двух разрешённых источниках свежий
  пригодный GPS подавляет Network на 12 секунд, после чего Network снова работает
  как резерв;
- сообщения результата сохранения мультиполигона: успешное исправление с числом
  частей либо возврат в редактор при невозможности получить валидную геометрию;
- Polygon и MultiPolygon используют ту же компактную панель вершин, что LineString:
  без добавления/удаления частей и отверстий, с режимами обхода и касания;
- durable crash journals: track recording resumes silently, while walk geometry,
  normal vertex/touch geometry and attribute forms use explicit Continue/Discard recovery;
- `BottomToolbar`: lean action menus (≤3 items) keep icons visible; identify
  attribute form gated by layer edit policy in `app`; «Поля → метка» сохраняет
  `feature_label_field` слоя;
- настройка стиля векторного слоя: простой и «По правилу», включая
  **«Стиль для прочих (по умолчанию)»** как базу новых категорий и источник
  незаданных опциональных параметров.

## Ограничения

- Нет compile dependency на `app`.
- UI не выбирает типы для topology repair: решение разрешено только app/maplib
  для точного `GTMultiPolygon`; Polygon и линии сохраняют прежнее поведение.
- LayerGroup index `0` — bottom; UI и MapLibre должны совпадать.
- Collector fill вставляет project-managed слои ниже «Мои треки» и применяет
  editable-флаг элемента проекта отдельно от общего mobile config.
- Activity и Dialog используют единый `CollectorProjectImportHelper`; initial
  import и composition sync создают штатные QGIS styles только через
  `CollectorRasterLayerHelper`, в общем порядке с vectors и всегда read-only.
- Backup failure блокирует destructive mutation.
- Project UID/map path не смешиваются между workspaces; switch и destructive
  project mutation запрещены во время sync/fill/rebuild.
- Карта приложения переоткрывается потокобезопасно, ContentProvider следует активному workspace,
  а project switch запрещён до остановки записываемого трека.
- `SYNC_NONE` оценивается отдельно для feature data и поддерживаемой config logic.
- Успешная preprocessing-задача без собственного слоя не вставляет `null` в
  `LayerGroup`; отсутствие server `data.write` оставляет pull, но запрещает edit/push.
- Каждая параллельная fill-задача получает заранее зарезервированный уникальный
  каталог. Ошибка первого SQL insert откатывает и удаляет неполный слой вместо
  продолжения партии по заведомо неверной таблице.
- KML/GPX fill не восстанавливает исходную геометрию или стиль: он сохраняет
  только упорядоченные точки и доступные name/time/elevation.
- Сравнение сохранённых строковых значений формы с typed controls выполняется по
  строковому представлению, чтобы число `42` не считалось ложной правкой к `"42"`.
- Successful form Save/Discard is terminal before `Activity.finish()`; its trailing
  `onPause()` must not recreate `feature_form_draft`. Walk Save/Cancel stops the
  service and clears `walkedit_temp`, while an unexpected stop retains it. Normal
  vertex/touch editing keeps `geometry_edit_draft` until explicit Cancel, successful
  update, form handoff or recovery Discard.
- Успешная серверная авторизация не считается добавлением Веб ГИС, пока
  `AccountManager` не создал и не вернул variant-specific Android account; при
  локальном отказе форма остаётся открытой и пишет безопасную диагностику без credentials.
- Mobile/Collector behavior определяется `IGISApplication.isCollectorApplication()`,
  а не жёстким сравнением package name, чтобы suffix `.geonical`/`.debug` не менял UI сервисов.
- Источник трека принадлежит только `tracks_location_source`, источник обхода —
  только обычному `location_source`; настройки не включают providers друг другу.
- Фоновая загрузка дерева NGW может использовать application context для сети и
  строковых ресурсов, но диалог ошибки показывается только через живую Activity;
  после её закрытия результат не должен создавать новое окно.

## Диагностика

- Долгий/зависший fill: `LayerFillService`, notification/foreground lifecycle,
  SQLite transaction и deferred map reload.
- Повторяется rebuild тяжёлого слоя: проверить mismatch fingerprint в
  `SchemaRebuildRetryGuard`, staged replacement и число остановленных слоёв в
  настройках проекта; не удалять старый слой до успешного fill.
- Fill закончен, identify видит объекты, но слой не отрисован: pending reload
  снимается только callback после проверки MapLibre source/style layer; проверить
  `MapLibre post-load verification`, а не перезапускать приложение как штатный путь.
- Неверный порядок: insertion index в model и последующий style reload.
- «Нет редактируемых слоёв»: проверить Collector item `editable`,
  `managed_by_project` и исходящее направление sync.
- После просмотра «Синхронизация», «Поля» или «Общие» слой стал read-only:
  проверить no-op guard начального события `Spinner` и доступность направления
  через `NGWVectorLayer.isSyncDirectionConfigurable()`.
- Потеря слоя после composition: backup result и removal scheduling.
- Неверный проект после restart: registry JSON, active project и map path.
- Импорт во время sync показывает общую «Ошибку»: проверить статус
  `PrepareWorkspaceResult.BUSY`; блокировка должна сработать до `ensureProject()`
  и открыть модальное сообщение.
- После успешного удаления показана ошибка: не открывать fallback-карту из
  фонового потока удаления; её открывает `MainActivity` после результата.
- Пустая/чужая история треков после switch: проверить active project preference, создание нового
  `MapDrawable` и перепривязку `LayerContentProvider` к тому же workspace.
- На скорости перестал расти трек или обход: проверить provider-qualified
  `LocationTrackFilter` и итоговые filter stats. Каскад `drop:speed_dist` при
  реальном движении до 160 км/ч является регрессией; `networkSuppressed` показывает
  только ожидаемые Network-фиксы, перекрытые свежим пригодным GPS.
- Валидный вход закрывается без аккаунта: проверить совпадение account type в
  runtime, authenticator и sync adapter, затем сообщения `NGW account add` в HyperLog.
- Сервер NGW отвечает `5xx`, а приложение падает с `BadTokenException`: проверить,
  что `NGWResourceAsyncTask.onPostExecute()` не передаёт application context в
  `AlertDialog` и пропускает UI после уничтожения Activity.

## Проверки

Собрать `:maplibui:assembleDebug`, затем выполнить относящиеся device smoke IDs,
включая `SMOKE-NGW-CONNECTION-FAILURE` для недоступного сервера.
