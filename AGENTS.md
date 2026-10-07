# maplibui — инструкции для ИИ-агентов

Перед изменением прочитай `docs/README.md` и `docs/manifest.yaml`. В parent
workspace также прочитай `../docs/registry/change-impact.yaml`, invariants и
smoke registry. При standalone checkout сообщи, если central docs недоступны.

`maplibui` владеет UI и orchestration между `app` и `maplib`: layer fill,
reorder, sync UI, Collector registry/workspaces, backups и lifecycle services.
Не импортируй `app`; используй interfaces из `maplib`.

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

## Project scripts

Зависимая обязательность: читать `../docs/architecture/conditional-form-rules.md`.
`ConditionalRequiredController` использует общий `FormMetadataSnapshot`,
не заменяет listeners контролов и не читает SQLite при отрисовке. Обновлять
звёздочки идемпотентно; Save проверяет эффективный required после checkpoint.
Не удалять снимки `form_rules` и не отключать проверку при ошибке pin.
`LayerUtil` обязан передавать парные form/meta даже при default form.
`FormScrollView` перелистывает только внешние Tabs, сохраняя самостоятельные
жесты ввода, подписи, галереи и горизонтально прокручиваемых контролов.

Зависимые списки: читать `../docs/architecture/cascading-form-lists.md`.
Controller управляет обычными и сдвоенными Spinner, а не создаёт отдельные
трёхуровневые элементы. Сохранять required, Back Save, draft pin и explicit NULL.

Владеет единым import hook, form controller, pin версии в Bundle/draft, debounce/stale guards, warning/block и before-save gate. Native GIS/формат принадлежит maplib; не дублировать интерпретатор и host functions в UI.

Перед доработкой читать `../docs/architecture/project-scripts.md` и пользовательское
руководство. Новый host API добавлять с capability/grants/типами/бюджетами/тестами
сначала в APK. Не поставлять arbitrary SQL, Java reflection, сеть или GIS-движок
внешним JS. Старые пакеты сохранять для pin черновиков. Cross-repo schema/API
обновлять одновременно с stand_project и central registries.
