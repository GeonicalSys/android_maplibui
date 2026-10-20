---
id: maplibui-20261014-script-isolated-bindings
area: Мобильное приложение
kind: user
at: '2026-10-14T11:56:20+02:00'
title: Изоляция последовательных запусков скриптов
why: Выполненная доработка отсутствовала в реестре и могла не попасть в отчёт
covers:
- docs/manifest.yaml
- src/main/java/com/nextgis/maplibui/util/ProjectScriptClient.java
source_commits:
- d887cb99a604aa9b3e4fef560d354eab0ffc5282
---

Каждый запуск проектного скрипта получает собственное подключение к изолированному сервису на поддерживаемых Android. Завершение предыдущего сервиса больше не должно прерывать следующий запуск.
