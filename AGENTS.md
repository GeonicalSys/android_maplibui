# maplibui — инструкции для ИИ-агентов

Перед изменением прочитай `docs/README.md` и `docs/manifest.yaml`. В parent
workspace также прочитай `../docs/registry/change-impact.yaml`, invariants и
smoke registry. При standalone checkout сообщи, если central docs недоступны.

`maplibui` владеет UI и orchestration между `app` и `maplib`: layer fill,
reorder, sync UI, Collector registry/workspaces, backups и lifecycle services.
Не импортируй `app`; используй interfaces из `maplib`.

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
