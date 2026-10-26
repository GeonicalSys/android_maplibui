---
id: maplibui-20261025-track-controls
area: Мобильное приложение
kind: user
at: 2026-10-25T11:30:00+01:00
title: Переименование и быстрые действия выбранного трека
why: Нужно управлять конкретным треком из списка и с карты без риска остановить другую запись
covers:
  - src/main/java/com/nextgis/maplibui/activity/TracksActivity.java
  - src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - src/main/java/com/nextgis/maplibui/util/FittedDialogs.java
  - src/main/java/com/nextgis/maplibui/util/TrackActions.java
  - src/main/java/com/nextgis/maplibui/view/TrackSelectionPanel.java
  - src/main/res/menu/action_tracks.xml
  - src/main/res/values/ids.xml
  - src/main/res/values/strings.xml
  - src/main/res/values-ru/strings.xml
---

Одиночный трек в списке можно переименовать. Общие действия списка и нижней
панели карты изменяют название, цвет, видимость и удаляют после подтверждения.
Пустое имя не сохраняется; скрытый выбранный трек можно снова показать прямо
из панели. Скрытие не останавливает запись.

Удаление старого трека сохраняет другой текущий сеанс. При удалении самого
записываемого трека recorder сначала дописывает durable очередь, затем закрывает
и удаляет ровно этот ID транзакционно. При ошибке намерение сохраняется для
повтора. Диалоги и русская панель вписываются в крупный интерфейс.
