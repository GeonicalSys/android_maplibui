---
id: maplibui-20261023-feature-transform
area: Мобильное приложение
kind: user
at: 2026-10-23T19:23:46+02:00
title: Панель сдвига и поворота с сохранением отмены
why: Редактировать объект целиком и иметь доступ ко всем командам даже на узком экране
covers:
  - src/main/java/com/nextgis/maplibui/fragment/BottomToolbar.java
  - src/main/java/com/nextgis/maplibui/overlay/EditLayerOverlay.java
  - src/main/java/com/nextgis/maplibui/overlay/UndoRedoOverlay.java
  - src/test/java/com/nextgis/maplibui/overlay/UndoRedoOverlayTest.java
  - src/main/res/drawable/ic_action_transform.xml
  - src/main/res/menu/edit_transform.xml
  - src/main/res/values/geometry_transform_strings.xml
  - src/main/res/values-ru/geometry_transform_strings.xml
---

Кнопка добавлена во все стандартные редакторы геометрии. Панель вписывает все icon targets по density/insets без overflow. Undo/Redo сохраняет container/ring CRS: MultiPolygon после отмены/повтора остаётся пригодным к штатному Save. Требуется maplib #59; consumer app #77.
