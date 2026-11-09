---
id: maplibui-20261109-layer-list-snapshot
area: Мобильное приложение
kind: internal
at: '2026-11-09T14:06:14+04:00'
title: Стабильные строки списка слоёв
why: Устранить падение списка при фоновом изменении карты
covers:
  - agent-system/knowledge/documentation/pending-changes-indicator-uses-one-shared-background-snapshot.json
  - src/main/java/com/nextgis/maplibui/fragment/LayersListAdapter.java
---

Количество, содержимое и идентификаторы строк читаются из одного снимка. Список получает снимок вместе с уведомлением. При перетаскивании передаются показанные слои. Требуется совместимое изменение Maplib. Регрессия GlitchTip №144 проверяет фоновую вставку, перетаскивание и удаление.
