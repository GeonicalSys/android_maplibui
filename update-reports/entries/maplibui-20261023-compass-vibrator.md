---
id: maplibui-20261023-compass-vibrator
area: Мобильное приложение
kind: user
at: 2026-10-23T12:32:23+02:00
title: Компас не падает на устройствах без вибрации
why: Поворот компаса должен работать и на планшете без сервиса или оборудования вибрации
covers:
  - src/main/java/com/nextgis/maplibui/fragment/CompassFragment.java
  - docs/manifest.yaml
---

GlitchTip 101: необязательный импульс при повороте компаса выполняется только
при наличии vibrator и hasVibrator. Touch rotation и пользовательская настройка
сохраняются. Регрессии API 26/36 и native consumer test находятся в app;
поставка требует library Merge Commit, app pin и согласованного APK выпуска.
