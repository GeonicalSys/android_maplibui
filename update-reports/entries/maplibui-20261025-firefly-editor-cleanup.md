---
id: maplibui-20261025-firefly-editor-cleanup
area: Мобильное приложение
kind: user
at: 2026-10-25T18:10:00+01:00
title: Удалена лишняя кнопка образца полевых точек
why: Применение бело-розового пресета выглядело как пустое действие при уже совпадающих параметрах
covers:
  - src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - src/main/res/layout/style_marker.xml
  - src/main/res/values/firefly_strings.xml
  - src/main/res/values-ru/firefly_strings.xml
---

Из редактора «Светлячка» удалены кнопка «Образец “Полевые точки”», её обработчик
и строки. Она применяла фиксированные цвета/размеры, а не открывала просмотр.
Обычный живой образец, выбор типа, ручные параметры ядра/свечения и Save/Cancel
сохранены; смена типа по-прежнему не перезаписывает пользовательские цвета.
