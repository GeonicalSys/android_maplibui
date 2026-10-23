---
id: maplibui-20261024-point-firefly
area: Мобильное приложение
kind: user
at: 2026-10-24T00:06:05+02:00
title: В редакторе точек появился «Светлячок» и образец полевой точки
why: Ядро и внешнее свечение нужно настраивать отдельно и видеть в списке категорий
covers:
  - src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - src/main/java/com/nextgis/maplibui/util/FeatureTypePreview.java
  - src/main/res/layout/style_marker.xml
  - src/main/res/values/arrays.xml
  - src/main/res/values/firefly_strings.xml
  - src/main/res/values-ru/firefly_strings.xml
---

Выбор «Светлячка» открывает понятные настройки ядра и свечения, сохраняя
текущие параметры. Отдельная кнопка применяет образец с белым ядром
и розовым свечением по полевым точкам. В списке категорий виден весь ореол.
