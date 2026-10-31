# maplibui — инструкции для ИИ-агентов

Перед изменением прочитай `docs/README.md` и `docs/manifest.yaml`. В parent
workspace также прочитай `../docs/registry/change-impact.yaml`, invariants и
smoke registry. При standalone checkout сообщи, если central docs недоступны.

`maplibui` владеет UI и orchestration между `app` и `maplib`: layer fill,
reorder, sync UI, Collector registry/workspaces, backups и lifecycle services.
Не импортируй `app`; используй interfaces из `maplib`.

Изменения общих UI-компонентов проверять с русским текстом, в обеих темах,
portrait/landscape, fontScale 1.0/1.3/2.0 и крупным display size совместно.
Диалоги создавать через `FittedDialogs.nativeBuilder/compatBuilder`, сохраняя
исходный тип окна. С закрытой клавиатурой проверять полный текст и реальное
нажатие Close/Save/Cancel после вписывания. Текущий продуктовый UI проверять
на русском языке.
Вложенные adapter lists (`setView` с ListView/GridView/RecyclerView) сохраняют
штатную ограниченную высоту и прокрутку; их нельзя измерять через общий
FittedPanelLayout с неограниченной высотой. Проверять количество видимых строк,
достижимость последней строки, выбор каждого слоя и Cancel настоящими touches.
Порядок адаптации: перенос и гибкая компоновка, понятное сокращение, затем
локальное уменьшение при оставшемся переполнении. Не менять системные настройки
пользователя. Действия и их реальные области нажатия должны оставаться внутри
видимой области с учётом insets при закрытой клавиатуре. Одновременная
видимость всего интерфейса с открытой клавиатурой не требуется; штатное
поведение IME сохраняется, после закрытия клавиатуры все элементы доступны. Native consuming-app проверки
обязаны выявлять скрытые строки, ellipsis, перекрытия и выход кнопок за экран;
getText() и успешной сборки недостаточно. Панели карты вписываются без прокрутки.

Большие таблицы NGFP не помещать в Activity Bundle. `lisa_cascade_pin` — SHA-256
ссылка на атомарный snapshot внутри owning layer/form_dependencies; проверять
хеш при восстановлении. Отсутствие/повреждение файла блокирует Save, сохраняя
pin и прежние управляемые значения в durable draft. Не удалять старые snapshots
при обновлении формы: они могут принадлежать незавершённому черновику.

Индикаторы несинхронизированных изменений строк слоя получают общий фоновый
снимок от app через `LayersListAdapter.setPendingChanges`. Не читать SQLite
в row bind; сбрасывать отметку при переиспользовании строки. Снимать listener
обновлений при уничтожении экрана и отбрасывать ответы другой карты.

Любое destructive removal/schema rebuild проверяет backup gate. Изменение
`LayerFillService`, `GISApplication`, `CollectorProjectRegistry` или
`ReorderedLayerView` требует проверки layer order, project isolation и
deferred reload. Обновляй local pack и central docs по DoD.

Оба экрана начального Collector-импорта обязаны использовать общий
`CollectorProjectImportHelper`; raster-style creation для initial import и
composition sync проходит через `CollectorRasterLayerHelper`. Не дублируй эту
логику обратно в Activity/Dialog: vector и штатные QGIS style items должны
получать один смешанный project order, а style layer всегда остаётся read-only.

## Git-доставка

Запрос изменить/исправить/добавить содержимое репозитория разрешает агенту
создать `codex/*` ветку, выполнить проверки, scoped commits, push и открыть
Draft PR без отдельных подтверждений каждого шага. Read-only запросы этого не
разрешают. Direct push в `master`, force push, tag/release и merge запрещены без
явного намерения пользователя завершить выпуск. PR библиотеки сливается через
Merge Commit; связанный root PR обновляет submodule pointer после merge.

## Реестр обновлений для отчёта

Доработка, которую нужно объяснить в отчёте заказчику или во внутреннем
списке для оплаты, получает карточку `update-reports/entries/<id>.md` в том
же PR. Префикс id: `maplibui-`. Срезы живут в монорепозитории `lisa`
(`docs/update-reports/`). Граница — номера карточек, не дата мержа.

## Project scripts

Зависимая обязательность: читать `../docs/architecture/conditional-form-rules.md`.
`ConditionalRequiredController` использует общий `FormMetadataSnapshot`,
не заменяет listeners контролов и не читает SQLite при отрисовке. Обновлять
звёздочки идемпотентно; v2 visible управляет контейнером field или
element/lisa_id, включая подпись/ошибку/отступ и pinned header Tabs. Hidden
поля/потомки не блокируют required/cascade UI gate, значения и draft сохраняются.
Обычная inactive вкладка не hidden. Unknown/duplicate target блокирует Save.
FormFieldLayout — общий caption/value UI, FormChoiceAdapter переносит длинные
названия без потери значения/поиска. Нижняя кнопка использует общий Save gate.
Не удалять снимки `form_rules` и не отключать проверку при ошибке pin.
`LayerUtil` обязан передавать парные form/meta даже при default form.
`FormScrollView` перелистывает только внешние Tabs, сохраняя самостоятельные
жесты ввода, подписи, галереи и горизонтально прокручиваемых контролов.
Проверять свайпы поверх enabled Spinner/checkbox/короткого комментария и пустой
области, многократные переходы, vertical drag и выделение текста. Не отменять
взмах лишь из-за requestDisallowIntercept или небольшого начального дрейфа.
Каскадный legacy double_combobox разворачивать через CascadingFormElements в
два обычных controls на каждом уровне, сохраняя field names/pins. Selector height
и отступы применять до early return регистрации каскада через FormFieldLayout.

Зависимые списки: читать `../docs/architecture/cascading-form-lists.md`.
Controller управляет обычными и сдвоенными Spinner, а не создаёт отдельные
трёхуровневые элементы. Сохранять required, Back Save, draft pin и explicit NULL.

Владеет единым import hook, form controller, pin версии в Bundle/draft, debounce/stale guards, warning/block и before-save gate. Native GIS/формат принадлежит maplib; не дублировать интерпретатор и host functions в UI.

Перед доработкой читать `../docs/architecture/project-scripts.md` и пользовательское
руководство. Новый host API добавлять с capability/grants/типами/бюджетами/тестами
сначала в APK. Не поставлять arbitrary SQL, Java reflection, сеть или GIS-движок
внешним JS. Старые пакеты сохранять для pin черновиков. Cross-repo schema/API
обновлять одновременно с stand_project и central registries.

## Общая синхронизация проектов

Читать consuming root docs/architecture/ngw-sync-and-storage.md и
docs/guides/project-synchronization-user-guide.md. sync_all_projects по умолчанию true;
ручной и scheduled account запуск используют ProjectSyncRunner. Не переключать
mMap/active prefs ради фонового проекта. Владельца переносить через
SyncWorkspaceSession во все async callbacks, service tickets и provider URI;
untagged UI URI всегда относится к активной карте, expired token не имеет fallback.
Очередь держит глобальный lease до завершения дочерних работ и mutating HTTP.
Полный pending project/account план сохраняется до первого прохода; Collector
journals разделены по canonical map path. Проверять cancellation, equal layer/group
IDs, сохранность draft и реальный fill на изолированном эмуляторе.
Подписи разделённых double_combobox брать из meta.fields keyname/display_name,
затем layer alias; field key используется только при отсутствии обоих.


## Обязательный контроль реестра перед push

Новая самостоятельная доработка получает новый ID карточки и `covers` с
точными путями всех её изменённых исходников/тестов/скриптов. Старый ID
не дописывать новым поведением. Для служебного исключения нужен новый
`exemptions/*.yaml` с точными путями и конкретной причиной.
Перед commit и push выполнить `py -3.13 -B tools/check_update_reports.py`.
В начале каждой задачи агент сам проверяет, подключён ли `.githooks/pre-push`
в этом клоне. Если hook ещё не подключён, агент без отдельного запроса
пользователю выполняет `py -3.13 tools/check_update_reports.py --install-hook`.
Если выбранному Python не хватает PyYAML, агент сам устанавливает зависимость
из `tools/requirements-update-reports.txt` и повторяет подключение. Штатный
installer сохраняет чужую конфигурацию hooks; её нельзя перезаписывать вслепую.
Не перекладывать эту настройку на пользователя или агента другого компьютера.
Обычный push блокируется hook при пропуске или ошибке. Запрещено обходить
его `--no-verify`, менять политику либо выдавать фиктивное исключение ради
зелёной проверки. Проверка путей не заменяет содержательный review карточек.


## Запрошенные пользователем отчёты

Каждый запрошенный отчёт, включая черновик, аудит и сверку, агент сохраняет
в Git и отправляет в свою ветку/PR в рамках той же задачи. Результат не должен
оставаться только в чате, `outputs/` или `.local/`. Отчёты о разработках
экосистемы и их приложения хранятся в `lisa/docs/update-reports/drafts/<id>/`
с оглавлением; прочие отчёты — в репозитории-владельце по его установленному
формату. В финале обязательны прямые ссылки на файлы в Git и статус PR.
Сохранение черновика не закрывает ID карточек: `reported/` и `letters/`
создаются отдельно по команде «зафиксируй отчёт». Правила источников и секретов
применяются и к приложениям отчёта.


## NGFP: prefetch отдельно от удаления

Читать consuming docs/architecture/ngw-sync-and-storage.md и conditional-form-rules.md.
Только GISApplication и LayerFillService используют новый LayerUtil.fillLookupTableIdsForPrefetch:
root array, оба declared orientation layouts, рекурсивные tabs/pages/elements и
first-seen normalized dedup. Сохранять Android getLong/-1 и malformed-present errors.
Старый public fillLookupTableIds и оба NGWVectorLayerUI delete reader сохраняют
прежние bytes/duplicates/errors; не переключать destructive readers на новый walker.
Missing-cache/hash-retry, межзадачные дубли, shared deletion и backup/account policy
вести в центральном контракте. Двенадцать consuming App JVM не проверяют actual
import/cache availability, account deletion, disk durability или native Save.


## Сообщения загрузки и выбора ресурсов

Проверять причину → owning task → progress/dialog/toast по исходникам.
NGWResourcesListAdapter принимает per-invocation ChildrenLoadResult, отличая
failed request от successful empty; legacy void callers и parcel layout прежние.
Сохранять live Activity gate. Для known server failure брать общий Core RU/EN
error_server_temporarily_unavailable, отдельно сохранять login/rights/offline.

LayerFillService передаёт исключения через LayerFillErrorMessage: только typed
HTTP provenance либо owned human resources. Raw Throwable text, URL, SQL/column
details и parser dumps остаются в диагностике. Сравнение owned текста — allowlist
содержимого, не способ угадывать HTTP status. No value for fields не доказывает403.
POINTZ marker/flag/особый dialog route сохраняются. FormBuilder list errors используют
resource IDs, а row reset, callback и Save остаются прежними. Native fit/lifecycle,
реальный server/import и device acceptance выполняются отдельно от source/JVM.


## Флажки NGW resource selector

Перед изменением NGWResourcesListAdapter/NgwResourceSelectionState/CheckState
читать `UI-NGW-RESOURCE-CHECKBOX-SELECTION`, invariant `INV-NGW-RESOURCE-CHECKBOX-SELECTION` и central ngw-sync-and-storage.md. Physical
checkbox1 не равен raster mode1: vector без style/Collector передают checkNo2.
Bind снимает listener, устанавливает ANY requested mode по matching localID,
затем возвращает listener, не меняя owner list. False очищает requested flag
у всех matching записей, true не размножает уже positive mode; другие flags/
IDs/order/object identity сохраняются. Recycled label устанавливается явно.
Owned JSON restore OR-merges same resolved localID firstorder лишь после каждого
прежнего resolve/boolean parse; failure не скрывать. Не переносить эту нормализацию
на public setter/Parcel и не обещать generic import dedup. Refresh policy —
единый Root `AUDIT-TODO-NGW-RESOURCE-BROWSER-REFRESH`. App fixtures22+6 и protected browser9 прошли actual37 source checks;
исторические message89 не принимают изменённый Adapter, F61 clock row не repin.


## Полная строка выбора ресурса и повторное измерение

Читать `UI-NGW-RESOURCE-CHECKBOX-SELECTION`/`INV-NGW-RESOURCE-ROW-TEXT-FIT` в consuming App. Три caption width=wrap_content сохраняют полный owned RU/EN текст. NgwResourceRowLayout сначала измеряет обычную горизонтальную строку; лишь фактический glyph clipping имени переводит две статические header/mode группы друг под друга. Перед обычным и full-width измерением локально forceLayout для каждого descendant обновляет getLayout до решения о размещении; parent requestLayout/reparent нет. При расширении та же строка возвращается в горизонтальную компоновку. Шрифт, IDs, порядок controls, listeners/CheckState/local-vs-remote identity/JSON/Parcel/import/refresh прежние. Shared breadcrumb layout переносит5dp child margin в5dp padding HorizontalScrollView: API28 clamp учитывает padding, а child margin не учитывает. Визуальный отступ сохранён, конец пути достижим в технической модели.
Измерять полные line ends/ellipsis/height/glyph bounds и неперекрытие имени/caption/checkbox;
getText и XML hash этого не доказывают. Не переключать parent/list viewport на unbounded fitting.
Фактически5 caption layout methods PASS/0 failures/errors/skips, XML10.06s. Ораклы и whole fixture SHA256 cc1fb961f2459d6b9e71097f403f5898b82aaa2425093b6157ee76edf03d06ac неизменны относительно предыдущего4PASS/1FAIL. Finalized Root receipt SHA256 68f7f6bdc072f9f25fd2af02e0584ac467a4dbb1a0c1b1e1959341b2126630ef. В этом запуске общий cohort10 НЕ PASS: 6 PASS/4 отдельные Panel assertion FAIL, Gradle exit1; 120 входов без drift, все13 owned processes settled absent, наблюдалось110.0625s. Это технические API28 CPU native Paint/text measurements, без Android device/OEM/GPU/display/GIS native/touch/rotation acceptance. Исторический cohort10 checkpoint предшествовал consumer compile. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c. Полная owned copy и CPU glyph bounds не принимают фактическое число доступных строк, touch targets/scroll/OEM/font/theme/rotation/display-size/installed APK/phone performance. font1.3 и реальные orientation/display combinations не исполнены. Единственный narrow Root risk AUDIT-TODO-MOBILE-RESOURCE-ROW-LAYOUT; broader UI audit продолжается, не all-screens PASS. F61 clock/Wiki/pointer blockers прежние. User card `maplibui-20261030-resource-caption-layout`, App recipe `android-20261030-resource-caption-layout-regression`.


Общие документы действия «Поделиться» находятся в корне Android-репозитория:
`docs/architecture/track-controls.md` и `docs/architecture/shared-tracks.md`.
Из каталогов `app/` и `maplibui/` к ним ведёт `../docs/architecture/`.
Использовать LayerUtil.shareTrackAsGPX, захватив ровно выбранный shared track ID;
не подменять его feature FID, map-layer ID, Hub UID или recording session.
Сохранять текущие Activity/view/selection/mode launch guards, multiselect списка,
GPX metadata/segment order, FileProvider roots/MIME/read grants и отсутствие Stop.
CPU-font JVM не заменяет whole-window/device/touch/recipient acceptance.
Неразрешённые cache/snapshot/concurrency/lifetime вопросы — в единой Root очереди;
не создавать второй exporter или локальный параллельный список рисков.
