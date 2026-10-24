---
id: maplibui-20261024-maplibre-13-7-1
area: Мобильное приложение
kind: internal
at: 2026-10-24T18:06:00+02:00
title: UI-библиотека согласована с MapLibre 13.7.1 OpenGL
why: Редактор и живой предпросмотр должны использовать тот же SDK, что карта
covers:
  - build.gradle
---

Зависимость обновлена до `org.maplibre.gl:android-sdk-opengl:13.7.1`.
Экраны редактора, сохранение параметров, Cancel и native предпросмотр сохраняют
прежнюю реализацию. Проверки выполняются совместно с app и maplib; квитанция
находится в consuming `docs/reference/maplibre-13-7-1-verification-2026-10-24.md`.
