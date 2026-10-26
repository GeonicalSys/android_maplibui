---
id: maplibui-20261026-offline-local-tile-transport
area: Карта и локальные слои
kind: user
at: 2026-10-26T16:35:00Z
title: Локальные векторные тайлы доступны без подключения к интернету
why: MapLibre Offline откладывал HTTP loopback вместе с внешними запросами
covers:
  - src/main/java/com/nextgis/maplibui/GISApplication.java
---

MOB-01/MVT-01/REL-03. После SDK initialization разрешён транспорт MapLibre,
чтобы запросы к LocalVectorTileServer выполнялись и без внешней сети.
Android NetworkUtil и ограничения WorkManager не меняются: отправка правок,
треков и проверка обновлений продолжают учитывать реальное подключение.
Внешние запросы карты в отсутствие сети могут завершаться обычной ошибкой
транспорта с backoff SDK; радио и настройки устройства не переключаются.
Загрузка геометрии, форматы тайлов и устройство стилей не переделываются.
