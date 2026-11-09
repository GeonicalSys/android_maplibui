---
id: maplibui-20261109-qms-dialog-lifecycle
area: Мобильное приложение
kind: internal
at: '2026-11-09T14:06:14+04:00'
title: Ответ QMS после закрытия диалога
why: Устранить падение при завершении фоновой загрузки каталога
covers:
  - agent-system/knowledge/documentation/direct-url-local-point-import-and-raster-ordering.json
  - src/main/java/com/nextgis/maplibui/dialog/CreateFromQMSLayerDialog.java
---

Результат каталога применяется к действующему диалогу от его текущего запроса. Адаптер и сообщения используют контекст этого диалога. Регрессия GlitchTip №143 моделирует закрытие во время запроса и последовательную смену запросов.
