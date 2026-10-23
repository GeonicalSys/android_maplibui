---
id: maplibui-20261023-feature-copy
area: Мобильное приложение
kind: user
at: 2026-10-23T19:23:46+02:00
title: Форма новой копии сохраняет совпадающие поля
why: Переносить атрибуты без потери скрытых полей и пустых значений
covers:
  - src/main/java/com/nextgis/maplibui/util/FeatureCopyDefaults.java
  - src/main/java/com/nextgis/maplibui/activity/ModifyAttributesActivity.java
  - src/main/java/com/nextgis/maplibui/control/DateTime.java
  - src/main/java/com/nextgis/maplibui/formcontrol/DateTime.java
  - src/main/java/com/nextgis/maplibui/dialog/NGDialog.java
---

Typed defaults и raw unbound scalars проходят обычные Save/scripts gates и durable drafts. Категория назначения имеет приоритет, в том числе для checkbox/counter; int64 не проходит через double. SQL NULL/пустая строка сохраняются до изменения control; nullable даты остаются пустыми. Тема диалога слоя восстанавливается до создания themed context. Требуется maplib #59; consumer app #77.
