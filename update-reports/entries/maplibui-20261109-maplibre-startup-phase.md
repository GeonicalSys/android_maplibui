---
id: maplibui-20261109-maplibre-startup-phase
area: Мобильное приложение
kind: internal
at: '2026-11-09T15:14:16+04:00'
title: Измерение запуска MapLibre
why: Локализовать стадию в отчётах GlitchTip №135 и №146
covers:
  - agent-system/knowledge/documentation/fill-transaction-orphan-storage-and-runtime-dependency-diagnosis.json
  - src/main/java/com/nextgis/maplibui/GISApplication.java
---

Вызов инициализации MapLibre получает отдельную стадию в общем журнале старта. Потребитель использует новый API Maplib. Причина старых ANR остаётся неустановленной.
