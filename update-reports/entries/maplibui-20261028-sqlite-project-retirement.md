---
id: maplibui-20261028-sqlite-project-retirement
area: Владение проектом и жизненный цикл карты
kind: internal
at: 2026-10-28T04:32:00+01:00
title: Отмена native SQLite чтения до замены активной карты
why: closeMapObj/resetMap обнуляли карту без отмены нового прямого SQLite tile provider, который продолжал принадлежать старой базе
covers:
  - src/main/java/com/nextgis/maplibui/GISApplication.java
  - docs/README.md
  - docs/manifest.yaml
---

MOB-04. GISApplication вызывает terminal any-thread
MapDrawable.retireSqliteVectorSources до обнуления mMap в resetMap и closeMapObj.
Нижняя библиотека немедленно отменяет только render-source coroutine work,
закрывает providers и запрещает новые source generations. SDK/native методы,
ожидание Main, дополнительный getMap или изменение прав/recording sessions
в этой границе не добавлены. Existing getMap в resetMap сохраняет прежнюю
создающую семантику; sync-workspace routing и project preferences не меняются.

Полный contract/test implementation принадлежит maplib, карточка
maplib-20261028-sqlite-project-retirement. Он отличается от nonterminal закрытия
источников при Activity recreation и сохраняет layer order/deferred reload.
CollectorProjectRegistry activate/delete получают отмену через прежний
closeMapObj без нового parallel API или изменения saved-state/schema.
Обратные producers NGW/desktop не меняются. Parent task обновляет central app
registry и submodule delivery: maplib, затем maplibui, затем app pointers.

Перед edits: preflight Fetch OK, maplibui master clean, ahead0/behind0; actor
geoglyth, открытых PR нет, .githooks уже подключён. Создана собственная matching
codex/mobile-crash-224. sqlite-retirement-dependency-plan01.json выполнил
whole-contour discovery: UNKNOWN, REFRESHED_IN_MEMORY; отсутствие полного
покрытия не названо PASS. Чужой desktop checkout/registries не редактировались.

На момент source handoff builds/tests/ADB NOT RUN. Нижние pure tests покрывают
cancel ownership race и неблокирующую отмену при занятом Main, но не удостоверяют
GISApplication native project switch/delete/recreate. Эта приёмка остаётся
отдельной. Commit/push/merge/release/publication не выполнялись. Новых bulk
данных нет;33MiB discovery plan наQ удерживается в общем memory incident.
