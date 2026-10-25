---
id: maplibui-20261025-track-stop-confirmation
area: Мобильное приложение
kind: user
at: 2026-10-25T17:25:00+01:00
title: Подтверждение ручной остановки записи во всех входах
why: Одного случайного нажатия хватало для завершения записи трека
covers:
  - src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - src/main/java/com/nextgis/maplibui/util/TrackActions.java
  - src/main/java/com/nextgis/maplibui/activity/TrackStopConfirmationActivity.java
  - src/main/AndroidManifest.xml
  - src/main/res/values/track_stop_styles.xml
  - src/main/res/values/strings.xml
  - src/main/res/values-ru/strings.xml
---

Общий вписываемый диалог требует подтверждения Stop; открытие, Cancel и Back
не меняют durable recording intent и pending_stop. Уведомление открывает
неэкспортируемую Activity с таким же подтверждением. Сохранённый UUID сеанса
переживает перезапуск и midnight split: старое окно или PendingIntent не могут
остановить более новую запись. Положительный ответ сохраняет намерение до
дописывания очереди; отказ checkpoint не отправляет Stop. Журнал различает
источники map/menu/notification и подтверждение/отмену. Восстановление и
внутренний Stop/retry сохраняют прежний сценарий без повторного диалога.
