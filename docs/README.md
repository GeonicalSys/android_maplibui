---
title: maplibui — GIS UI, layer fill и Collector orchestration
module_id: maplibui
last_verified: 2026-10-09
---

# maplibui — GIS UI, layer fill и Collector orchestration

## Назначение

TrackUploader обслуживает живой recorder и WorkManager через захваченный TrackLayer;
ошибка регистрации/сервера оставляет SQLite outbox для повтора. TrackWorker
владеет отдельными заданиями каждого workspace, не переключая активную карту.
Конфигурация, миграция default и ACK: consuming root
контракт доставки docs/architecture/ngw-sync-and-storage.md.

ChooseFeatureTypeDialog асинхронно загружает категории FieldStyleRule и возвращает
явное начальное состояние вместе с исходным способом создания (карандаш,
местоположение или обход); arguments/result сохраняют маршрут при пересоздании.
WalkSessionStore атомарно сохраняет typed walk_initial_values нового объекта
вместе с геометрией и владельцем записи до передачи в geometry/form draft.
FeatureTypeDefaults подбирает имена legacy пары
и стабильные keys всей цепочки lisa_form_dependencies; несколько допустимых
родителей показываются раздельно. При обходе смешанной формы нормализуются только
непустые привязки combobox/double_combobox: подписи, вкладки и отсутствие второго
поля у обычного списка не прерывают загрузку категорий. FeatureTypePreview рисует копию символа.
Индикатор загрузки и сообщение об ошибке живут в шапке списка, без отдельной
custom panel AlertDialog: длинный список прокручивается, «Отменить» остаётся
на экране. Header не превращается в выбранную категорию.
LayerUtil использует общую пару form/meta и сохраняет checkpoint до запуска
формы; GeometryEditDraftStore v1 сохраняет typed initial_values. Контракт:
consuming root docs/architecture/feature-type-creation.md.

Внешние NGFP-вкладки закреплены под toolbar; взмах по полям переключает соседнюю
вкладку без потери данных, включая взмах над обычным списком, флажком,
комментарием и пустой областью короткой страницы. FormScrollView оставляет
вертикальную прокрутку, выделение текста, подпись, фото и собственные
горизонтальные жесты их владельцам. При TalkBack работают штатные вкладки.
LayerUtil передаёт matching metadata и при автоматическом выборе default form.

`CascadingFormElements` разворачивает управляемые каскадом legacy
`double_combobox` в два отдельных обычных поля с подписями из alias слоя.
Это преобразование отображения в корне и внутри Tabs: исходный JSON,
имена полей и pin черновика сохраняются. `FormFieldLayout` задаёт спискам
минимальную высоту 56 dp и контейнер с отступом 20 dp до раннего выхода cascade init.

FormFieldLayout оформляет обычные и NGFP-поля: подпись 14sp сверху, значение
17sp в рамке, высота от 56dp и отступ 20dp; длинные названия в списке переносятся.
Нижний Save закреплён и использует общий gate. Обе темы сохраняют контраст.
Bundle и durable draft сохраняют зарегистрированные controls независимо от
FieldContainer; прямая группа Tabs дополнительно сохраняет активную страницу.

ConditionalRequiredController читает независимый `lisa_form_rules` v1/v2. V2
visible управляет field-контейнером или element/lisa_id, включая inactive/nested
Tabs и pinned header. Скрытые поля не блокируют required/cascade Save, но их
значения/черновик сохраняются. Видимые поля усиливают штатный required.
Ошибки идентификатора/цели блокируют Save; view-only тоже применяет visibility.
Условия закреплены атомарным SHA-256 снимком; observer не заменяет listeners и
не читает SQLite на кадрах. Подробнее: consuming root
`docs/architecture/conditional-form-rules.md` и пользовательское руководство.

Каскадные таблицы закрепляются атомарным SHA-256 snapshot в папке своего слоя.
Bundle/draft содержит короткую ссылку, ключи выбора, привязки и исходные значения;
большие списки не превышают Binder-лимит состояния Activity. Повреждение файла
блокирует Save и оставляет выбор восстанавливаемым.

В строке слоя жёлтая точка означает наличие несинхронизированных изменений.
`LayersListAdapter.setPendingChanges` получает фоновый снимок layer ID от app;
row bind не читает SQLite и сбрасывает отметку при переиспользовании строки.
Изменения карты вызывают общий фоновый refresh, listener снимается при уничтожении
экрана. Точка на кнопке синхронизации использует тот же снимок.

UI-библиотека и runtime orchestration: выбор NGW resources, создание/настройка
слоёв, batch fill, layer list/reorder, edit overlays, sync/account UI,
Collector workspaces и защитные backups. MapLibre Android `13.0.2` подключён
через явный OpenGL-артефакт, согласованный с `app` и `maplib`.

При подготовке production `3.1.2.27` библиотека проверяется вместе с consuming
app и maplib; shared UI, account identities и OpenGL backend сохраняют прежний
контракт. APK version matrix выполняется после закрытия library PRs.

`ExternalGnssService` удерживает внешнюю сессию, а готовность BLE-подписки и
свежесть качества определяет maplib. Живой foreground service не подтверждает
наличие свежего фикса; Pigo использует профиль UART `3A20` и BESTPOSB.

## Основные сценарии

- NGFP `meta.json.lisa_form_dependencies` включает встроенную фильтрацию
  combobox/double_combobox по нескольким родителям с общей таблицей и без
  фиксированной глубины. Новый сеанс не выбирает первый вариант и не использует
  last. Смена родителя очищает всех потомков, Save пишет SQL NULL и проверяет
  принадлежность перед required. Определение/key/исходные значения закреплены
  в Bundle и durable draft. Исторические значения не заменяются автоматически.
  Подробнее: consuming root `docs/architecture/cascading-form-lists.md`.

- Формы NGFP и стандартные формы проверяют Field.isRequired() в общем пути
  Save, включая сохранение через Back. Отсутствующее значение, пустота/пробелы
  и строка «Нет значения» без учёта регистра блокируют окончательный Save;
  «не применимо», 0 и false допустимы. Необязательные поля не меняются.
  Подпись получает *, диалог перечисляет пропуски и открывает вкладку первого
  поля. Оба значения зависимого списка проверяются по собственным полям.
  До проверки сохраняется черновик, но запись и вложения ещё не изменяются.
  Значение существующего объекта, которого нет в форме, сохраняется; отсутствие
  такого обязательного поля у нового объекта требует исправления формы.
  Контракт и ограничения описаны в docs/architecture/ngw-sync-and-storage.md
  consuming root; RequiredFieldsTest проверяет реальную Android-форму и SQLite.

- `TrackerService` следит за Battery Saver и выбором источника GNSS во время
  записи. При screen-off ограничении системного GPS постоянное уведомление
  предупреждает о возможном разрыве и возвращается к обычному тексту после
  снятия ограничения. Экран получает VALUE_TRACK_POWER; запись и Stop сохраняются.

- `TrackerService` сохраняет выбранный в кнопке карты режим «Пешеход» или
  «Пешеход + машина». Track-only `TrackSpeedGate` пропускает скорость выше
  30 км/ч до sampling/queue, дописывает только разрешённый хвост и возобновляет
  запись новым сегментом. Без скорости приёмника используется соседняя GNSS-пара;
  без пригодной пары запись ждёт измерений. Stop работает и во время пропуска.
  Режим переживает restart/split, legacy default — смешанный. Heartbeat трека
  молчит во время пропуска; общие GNSS callbacks, курсор и обход не меняются.

- В списке слоёв карты значок включённой видимости зелёный; выключенное
  состояние сохраняет прежний значок и цвет в светлой и тёмной темах.
  Кольцо прогресса ручной NGW-синхронизации принадлежит app/`NgwSyncProgress`;
  диалог LayerFill не использует эту шкалу.
- импорт обычных NGW и Collector vector/style resources;
- Activity/Dialog выбора NGW сохраняют только account/server, пути remote ID и
  выбранные флаги; деревья ресурсов и credentials не входят в saved state или
  новые launch intents. Имена слоёв и групп в списке переносятся, а не
  обрезаются. `NgwResourceSelectionState` повторно получает учётные
  данные через AccountManager и восстанавливает путь/выбор в фоне. При ошибке
  сохранённый выбор остаётся для повтора, импорт заблокирован; закрытие экрана
  отменяет restore и не позволяет позднему результату открыть диалог.
  Начальный выбор аккаунта сохраняет тип действия/ID слоя вместо callback со
  старой Activity; после process recreation переход в импорт/экспорт остаётся
  доступен. Отсутствующая кнопка Add в account picker не разыменовывается.
- безопасное сообщение об ошибке подключения в выборе NGW-ресурсов без попытки
  открыть окно через application context или уничтоженную Activity;
- импорт vector/raster NGW-ресурса по прямому URL через общий fill pipeline
  (пункт меню скрыт, обработчик сохранён);
- локальный KML/GPX направляется в отдельную fill-задачу, которая создаёт один
  редактируемый точечный слой и удаляет его целиком при ошибке разбора/записи;
- вставка NGRc/raster/vector layers в правильном порядке;
- импорт `.mbtiles` и ZIP с `.mbtiles` через local-underlay pipeline; raster
  добавляется над OSM, получает обычный hot reload и сохраняет порядок;
  «Открыть локальный» классифицирует NGRc/MBTiles/ZIP так же, как «Новая
  подложка из файла»;
- «Загрузить проект» ищет группу Веб ГИС с ключом `lisa`, показывает проекты
  radio-списком с разделителями и прокруткой и импортирует выбранный
  Collector-проект после «Загрузить» тем же isolated-workspace pipeline;
- lease `UNDERLAY_MIGRATION` исключает одновременные switch/sync/fill операции,
  пока приложение потоково собирает подложки старого Debug в активном проекте;
- deferred reload карты после batch fill, который остаётся pending до фактического
  завершения MapLibre style/source apply;
- изолированные Web GIS/local projects, atomic registry/sidecar, switch/create/
  rename/delete и project-wide operation leases; до первого открытия карты
  создаётся начальный local workspace, а прежняя штатная standalone-карта один
  раз копируется в него без удаления оригинала; fill заранее резервирует
  workspace; scoped зависимый fill ждёт разрешения своей сессии перед доступом к SQLite;
- попытка импортировать новый Collector-проект, слой или подложку во время sync
  показывает предупреждение с возможностью прервать sync; действие продолжится
  после освобождения lease. Gate повторяется перед фактической подготовкой
  workspace, поэтому sync, начавшийся во время выбора ресурса, не обходит
  предупреждение. Успешная подготовка без разрыва переводит exclusive
  project-switch lease в layer-fill lease и передаёт его foreground service.
  Во время fill/rebuild остаётся модальное ожидание, а реестр до этого не
  изменяется;
- staged schema rebuild/removal только после успешного backup, с ограничением
  повторов неизменного mismatch fingerprint;
- backup сохраняет таблицы слоя и только файлы вложений, физически
  имеющиеся на этом устройстве; server-only payload не скачивается и не
  блокирует удаление;
- перед account sync одинаковые managed layers группируются по
  `account + project_uid + remote_id`: без pending changes лишние копии
  backup-гейтятся и удаляются одним map commit, с правками sync блокируется;
- staged replacement на main thread одним сохранением одновременно вставляет
  replacement и убирает старые ссылки; storage удаляется только после commit,
  а слой другого проекта или manual origin не затрагивается;
- toolbar Back в NGW resource tree поднимается к родительскому каталогу и
  закрывает экран только из корня;
- просмотр вкладок свойств NGW-слоя не меняет направление синхронизации;
  направление можно вернуть из «только с сервера» в двустороннее по политике
  владельца слоя, даже если generic mobile `is_editable` у Collector-слоя false;
- track/edit/form UI и foreground workers/services;
- фото-вложения по умолчанию получают видимый штамп координат из геометрии объекта;
  оба preference можно выключить в настройках карты;
  карта по умолчанию не гасит экран (`keep_screen_on=true`);
- `TrackerService`, `WalkEditService` и `ExternalGnssService` подписаны на общий GNSS-only поток
  Application. `ExternalGnssService` держит процесс при выбранном внешнем
  приёмнике без второй LocationManager-подписки. Tracker и Walk сохраняют отфильтрованные точки после прореживания с сохранением
  поворотов. Network используется картой только без свежего GPS. Mock внешнего
  GNSS и native NMEA пишутся без пешеходного smoother, не грубее 2 с / 1 м. Трек хранит номер сегмента и
  экспортирует разрывы через GPX `trkseg`; обход хранит `gps_paused` и ждёт
  явного «Продолжить и соединить». Перед Save UI получает финальный durable
  snapshot без raw GPS-хвоста. Звуковой контроль получает общий проверенный поток
  до прореживания, продолжает работать на стоянке и проверяет время измерения.
  Подробности и миграция: [GPS pipeline](../../docs/architecture/location-pipeline.md).
  Сервисы работают в основном процессе, сохраняют durable intent/черновики при
  неожиданном завершении и не запускают запрещённый location FGS без permission.
  Track и Walk запускаются только с foreground type `location`, без требований
  `connectedDevice`. Меню подтверждает активную запись после публикации
  запущенного сервиса, а не только после восстановления прежнего трека. Состояния
  Start/Starting/Recording/Error общие для меню и кнопки карты; обычный запуск
  больше не мигает ложным «Не пишется». Выключенная системная геопозиция
  блокирует Start до создания строки трека; ошибка запуска или сохранения точки
  предупреждает отдельно. Черновик обхода и уже записанные точки сохраняются
  для восстановления.
- сообщения результата сохранения мультиполигона: успешное исправление с числом
  частей либо возврат в редактор при невозможности получить валидную геометрию;
- LineString, Polygon и Multi-варианты используют tap-скетч с одним стартовым
  узлом, вычисленным через экранную проекцию актуального центра камеры; обратное
  преобразование экранных координат линий и полигонов также выполняется текущей
  MapLibre-проекцией, а не устаревающим legacy display. В панели нет overflow и
  дополнения касанием, у полигонов также нет добавления/удаления частей и отверстий.
  Перед тапом или запуском обхода MapLibre показывает выбранный узел красным,
  следующую вершину и сегмент внутри той же части/кольца — оранжевыми. Обход
  вставляет GPS после выбранного узла; отдельная панель завершает запись
  после подтверждения финального снимка сервисом. Undo/Redo хранит до 100 реальных
  изменений геометрии и сравнивает координатный WKT: выбор узла, повторный callback
  и тот же скетч с обновлённым CRS не занимают отдельный шаг истории;
  инструмент линейки показывает те же кнопки и записывает в эту историю каждое
  добавление или завершённый перенос измерительной точки, используя активную
  MapLibre-геометрию из `MapDrawable`, а не legacy `RulerOverlay`;
- durable crash journals: track recording resumes silently, while walk geometry,
  normal vertex/tap geometry and attribute forms use explicit Continue/Discard recovery;
  walk и point geometry могут сосуществовать с разными владельцами; один
  переданный скетч не открывается дважды;
- `BottomToolbar`: lean action menus (≤3 items) keep icons visible; identify
  attribute form gated by layer edit policy in `app`; «Поля → метка» сохраняет
  `feature_label_field` слоя;
- настройка стиля векторного слоя: простой и «По правилу», включая
  **«Стиль для прочих (по умолчанию)»** как базу новых категорий и источник
  незаданных опциональных параметров.

## Ограничения

- Нет compile dependency на `app`.
- MapLibre dependency совпадает с `app` и `maplib`:
  `org.maplibre.gl:android-sdk-opengl:13.0.2`; generic MapLibre 13 artifact
  использует Vulkan и не допускается в production dependency graph.
- UI не выбирает типы для topology repair: решение разрешено только app/maplib
  для точного `GTMultiPolygon`; Polygon и линии сохраняют прежнее поведение.
- LayerGroup index `0` — bottom; UI и MapLibre должны совпадать.
- Raster MBTiles и migrated underlay остаются manual local layers и не попадают
  под destructive Collector composition sync. Режим фона карты `light`
  возвращает сплошной белый bitmap (`#FFFFFF`).
- Collector fill вставляет project-managed слои ниже «Мои треки» и применяет
  editable-флаг элемента проекта отдельно от общего mobile config.
- Activity и Dialog используют единый `CollectorProjectImportHelper`; initial
  import и composition sync создают штатные QGIS styles только через
  `CollectorRasterLayerHelper`, в общем порядке с vectors и всегда read-only.
- Backup failure блокирует destructive mutation; отсутствие локальной копии
  server-only вложения не является failure.
- Project UID/map path не смешиваются между workspaces; во время sync UI предлагает
  прервать sync перед switch/create/rename/delete или destructive project
  mutation. Несколько владельцев отмены регистрируются независимо; завершение
  отклонённого запуска не снимает handler активного worker. Пока подтверждается
  уже отправленная серверная правка, UI объясняет безопасное ожидание;
  fill/rebuild остаются взаимоисключающими с изменением проекта.
- Чистая установка до первого `MapDrawable` публикует active UID локального
  проекта; legacy migration копирует только map-owned layer paths и track DB,
  не захватывая соседние файлы или каталог остальных проектов.
- Карта приложения переоткрывается потокобезопасно, ContentProvider следует активному workspace,
  а project switch запрещён до остановки записываемого трека.
- `SYNC_NONE` оценивается отдельно для feature data и поддерживаемой config logic.
- Успешная preprocessing-задача без собственного слоя не вставляет `null` в
  `LayerGroup`; отсутствие server `data.write` оставляет pull, но запрещает edit/push.
- Каждая параллельная fill-задача получает заранее зарезервированный уникальный
  каталог. Ошибка первого SQL insert откатывает и удаляет неполный слой вместо
  продолжения партии по заведомо неверной таблице.
- Новый каталог fill получает marker незавершённой публикации до первого
  обращения к данным. После process death приложение удаляет только помеченные
  и не указанные в загруженной карте stages вместе с их таблицами; помеченный,
  но уже опубликованный слой сохраняется, а старые непомеченные каталоги никогда
  не считаются автоматически удаляемым мусором.
- Collector journal закреплён за project UID. Если после restart активен другой
  workspace, repair сохраняется и ждёт открытия целевого проекта; layer и все
  его SQLite-операции заранее привязываются к target group.
- KML/GPX fill не восстанавливает исходную геометрию или стиль: он сохраняет
  только упорядоченные точки и доступные name/time/elevation.
- Сравнение сохранённых строковых значений формы с typed controls выполняется по
  строковому представлению, чтобы число `42` не считалось ложной правкой к `"42"`.
- Successful form Save/Discard is terminal before `Activity.finish()`; its trailing
  `onPause()` must not recreate `feature_form_draft`. A successful Save result carries
  enough layer/feature/new-row identity for the app host to reload the persisted feature,
  terminate either creation or existing-feature editing and clear selection back to the
  normal map screen. Walk Finish stops the service after its acknowledgement but retains
  `walkedit_temp` until successful feature Save or explicit Discard. Normal
  vertex/tap editing keeps `geometry_edit_draft` until explicit Cancel, successful
  update, form handoff or recovery Discard.
- После cold Continue обхода пассивный source показывает контур независимо от
  редактора точки; Polygon получает заливку, LineString/MultiLineString — только
  линию. Панель ждёт подтверждение Finish перед переходом к ручным вершинам.
- Во время point session даже ранее открытое меню или старое уведомление не
  может завершить, удалить либо продолжить обход.

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
  SQLite transaction, project UID, `.layer-fill-partial` и deferred map reload.
- После прерывания появились лишние `layer_*`: автоматически удаляются только
  новые каталоги с `.layer-fill-partial`, которых нет в `LayerGroup`. Legacy
  каталоги без marker требуют отдельной диагностики и явного решения, поскольку
  среди них могут быть тяжёлые MBTiles или пользовательские данные.
- Crash `No Vulkan compatible GPU found` до появления карты означает, что в
  runtime dependency graph вернулся generic/Vulkan MapLibre artifact вместо
  согласованного `android-sdk-opengl`.
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
- Импорт во время sync показывает предупреждение с кнопкой прерывания; проверить
  `ProjectSyncInterruption`, отмену worker и то, что блокировка срабатывает до
  `ensureProject()`. При fill/rebuild сохраняется `PrepareWorkspaceResult.BUSY`.
  и открыть модальное сообщение.
- После успешного удаления показана ошибка: не открывать fallback-карту из
  фонового потока удаления; её открывает `MainActivity` после результата.
- Пустая/чужая история треков после switch: проверить active project preference, создание нового
  `MapDrawable` и перепривязку `LayerContentProvider` к тому же workspace.
- На скорости перестал расти трек или обход: проверить provider-qualified
  `LocationTrackFilter` и итоговые filter stats. Каскад `drop:speed_dist` при
  реальном движении до 160 км/ч является регрессией. Network не входит в запись;
  дополнительно проверить gps_paused у обхода и счётчики общего источника.
- Валидный вход закрывается без аккаунта: проверить совпадение account type в
  runtime, authenticator и sync adapter, затем сообщения `NGW account add` в HyperLog.
- Сервер NGW отвечает `5xx`, а приложение падает с `BadTokenException`: проверить,
  что `NGWResourceAsyncTask.onPostExecute()` не передаёт application context в
  `AlertDialog` и пропускает UI после уничтожения Activity.
- Для отмены одной вершины требуется несколько нажатий: проверить, что
  `UndoRedoOverlay` отбрасывает подряд идущие снимки с одинаковым координатным
  WKT, даже если callback обновил CRS, и не сдвигает курсор при недоступном Undo/Redo.

## Проверки

Собрать `:maplibui:assembleDebug`, затем выполнить относящиеся device smoke IDs,
включая `SMOKE-NGW-CONNECTION-FAILURE` для недоступного сервера.

## GPS: фон и уточнение стоянок

GPS-подписка записи сохраняется при скрытии/возврате карты. Источник удерживает
partial wake lock, пока активен хотя бы один recorder, независимо от звука.
Акселерометр 25 Гц дополняет GNSS-проверку стоянок; при отсутствии свежих сенсорных
событий используется состояние «неизвестно». Согласованное движение автомобиля
может опровергнуть неподвижность телефона в держателе. Уточнение стоянки через
  `takeStationaryCorrection` изменяет последнюю свою вершину, а не дописывает линию.
Диагностика `GPS health` позволяет сравнить сырые интервалы и accuracy со включённым
и выключенным экраном. Курсор текущей позиции не пишет каждую GPS-точку и autopan
в HyperLog: это остаётся только в Logcat при `DEBUG_MODE`. См. [контракт GPS](../../docs/architecture/location-pipeline.md).
Шаринг GPX идёт через FileProvider: Intent `application/gpx+xml`, URI MIME
`text/xml`, чтобы получатель не склеивал `.bin` или `.null`. MAX может показать
`.gpx.xml`; QGIS такой файл открывает.

Трек и обход используют общий протокол подтверждения движения: неподтверждённый
буфер не рисуется и не выгружается при Stop или потере GPS. Подтверждённое начало
сохраняется с исходными временами, не создавая фиктивного разрыва получения GPS.
Явный Stop передаёт фактическую константу ACTION_STOP, закрывает строку трека
и очищает намерение восстановления; onDestroy сохраняет прежнюю семантику восстановления.

Панель обхода содержит только название слоя и три значка в одной строке 48 dp.
Save, Pause/Resume и Cancel вызывают host confirmation; tooltip и TalkBack
объясняют значки, все touch targets 48 dp. Цвета берутся из темы бренда,
длинное название сокращается, сообщения point lock/persistence failure остаются.
PAUSE — session-owned команда: flush принятого хвоста, существующий gps_paused,
без завершения владельца; новые callbacks не добавляют вершины до RESUME.
Дискета после подтверждения FINISHED и проверки вершин сразу открывает атрибуты.

## Независимый обход и начало движения

Обход владеет геометрией в `WalkSessionStore`, а карта показывает отдельный
`walk-preview-source`, восстановленный после загрузки style. Приватная копия
сохраняет CRS перед переводом метров в широту/долготу. Обычные меню доступны;
панель обхода не занимает foreground-редактор точки. От начала выбора слоя точки
до Save/Cancel заблокированы все команды обхода в UI и сервисе. Начальный черновик
формы сохраняется до её запуска; ошибка, камера и перезапуск не снимают блокировку.
После Finish сервис подтверждает финальный снимок, который можно проверить и
сохранить обычным редактором. Панель компактная, без точности GPS и меню действий;
Дискета, пауза/продолжение и крестик требуют подтверждения. Дискета после
FINISHED сразу открывает форму атрибутов. Линии требуют две разные точки, кольца — три;
пустые части или коллекции не допускаются. Недостаточный финальный снимок закрывает
обход без создания объекта или формы, с понятным сообщением.
Общий `ControlHelper` изолирует drawable перед изменением alpha/tint и поддерживает
пункты без иконки, чтобы состояние соседней кнопки не делало активную кнопку серой.
См. [восстановление](../../docs/architecture/crash-recovery.md).

`WalkSessionRecoveryPolicy` на старте отделяет пустой initial owner без живого
сервиса от уже подтверждённого черновика. Пустой ghost удаляется автоматически;
реальный stopped draft получает Continue/Discard, а owner другой карты — явный
аварийный сброс. Сброс обходит только map-identity guard по точному UUID сессии,
останавливает соответствующий сервис и не трогает проекты или слои.

Курсор показывает текущую сглаженную позицию независимо от удержания записанной
стоянки. При хорошем сигнале начало линии подтверждается коротким окном, при
обычной уличной точности — медианными частями 12-секундного окна с допуском
поворота; начало пути сохраняется из буфера. Диагностика `stationary`/`departureMs`
различает ожидание фильтра и отсутствие GNSS. См. [GPS](../../docs/architecture/location-pipeline.md).


## Подложки между проектами

SharedUnderlayProjects координирует migration, attach, usage и подтверждённое глобальное удаление через UNDERLAY_MIGRATION lease. LayerFillService не добавляет второй shared ID в один проект и не подменяет имя NGRc-подложки SAF-идентификатором документа. Старые подложки защищаются перед удалением проекта; список слоёв снимает только проектную ссылку. Контракт: [shared-underlays](../../docs/architecture/shared-underlays.md).

## Восстановление и безопасное сохранение — 2026-10-03

Форма собирает значения контролов в UI-потоке, пишет данные в worker и остаётся
открытой при ошибке фотографии/подписи. Черновик сохраняется периодически,
перед записью и после назначения id; UUID позволяет повторить insert без второго
объекта. Подпись хранит штрихи и закрывает файловые потоки. AndroidX Back
сохраняет подтверждение выхода и блокирует уход во время Save.

TrackerService сохраняет очередь точек и намерение Stop до успешной выгрузки
хвоста. WalkEditService показывает ошибку checkpoint в панели/уведомлении и
снимает её после успешной записи. Внутренние службы не экспортированы; dataSync
службы обрабатывают timeout отменой и быстрым stopSelf.

Backup format2 публикуется уникальным ZIP после проверки содержимого; ручной
backup выполняется вне UI-потока, окончательное удаление повторно проверяет
карту, резервирование и поколение данных. Текущая невозможность редактировать
слой не отключает защиту уже существующих локальных данных.
NextGIS ID просит почту или логин, приводит только идентификатор к нижнему
регистру; Web GIS folder/up icons привязываются при каждом повторном использовании
строки. См. [хранение](../../docs/architecture/ngw-sync-and-storage.md),
[восстановление](../../docs/architecture/crash-recovery.md) и
[результаты аудита](../../docs/reference/mobile-reliability-audit.md).

## Project scripts

Владеет единым import hook, form controller, pin версии в Bundle/draft, debounce/stale guards, warning/block и before-save gate. Native GIS/формат принадлежит maplib; не дублировать интерпретатор и host functions в UI.

[Архитектура](../../docs/architecture/project-scripts.md),
[руководство](../../docs/guides/project-scripts-user-guide.md).

## Изоляция общей синхронизации

Настройка sync_all_projects включена по умолчанию. ProjectSyncRunner сериализует
project/account passes, а SyncWorkspaceSession связывает owning map с каждым
callback, provider URI и service ticket до фактического завершения. Закрытая карта
не активируется; открытая форма/черновик и preferences остаются прежними.
Полный контракт: consuming root docs/architecture/ngw-sync-and-storage.md;
пользовательская инструкция: docs/guides/project-synchronization-user-guide.md.
Перед изменениями читать оба документа. Нельзя заменить изоляцию временным
переключением глобальной карты или prefs, либо закрыть БД по timeout при живом child.
