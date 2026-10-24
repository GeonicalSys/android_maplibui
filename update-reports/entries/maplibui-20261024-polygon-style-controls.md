---
id: maplibui-20261024-polygon-style-controls
area: Мобильное приложение
kind: user
at: 2026-10-24T13:27:26+02:00
title: В редактор добавлены настройки штриховки, контура и живой образец
why: Изменения стиля приходилось сохранять перед оценкой результата на карте
covers:
  - src/main/res/values/polygon_style_controls.xml
  - src/main/res/layout/style_geometry_scale.xml
  - src/main/res/layout/style_text.xml
  - src/main/java/com/nextgis/maplibui/util/StylePreviewView.java
  - src/main/java/com/nextgis/maplibui/util/PolygonStyleControls.java
  - src/main/java/com/nextgis/maplibui/util/FeatureTypePreview.java
  - src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - src/main/res/layout/style_polygon.xml
  - src/main/res/layout/style_polygon_options.xml
  - src/main/res/values/ids.xml
  - src/main/res/values/polygon_style_strings.xml
  - src/main/res/values-ru/polygon_style_strings.xml
---

Настройки штриховки и расширенного контура сгруппированы в отдельные включаемые разделы. Образец точек, линий и полигонов обновляется сразу через тот же MapLibre и те же фабрики, что на карте, без сети. Он остаётся над прокручиваемыми настройками. Прозрачность фона, штриховки и обводки меняется независимо; неверные новые числовые параметры блокируют сохранение.

Чекбоксы редактора получили явный native style: в overlay-теме они сохраняют индикатор состояния и реагируют на первое настоящее касание.

Содержимое редактора использует полную тему элементов управления, соответствующую цветам окна: подписи читаются и в тёмной теме поверх светлой Activity. Длинные подписи выбора цвета отделены от компактного действия.
