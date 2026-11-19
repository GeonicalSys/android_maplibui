---
id: maplibui-20261117-script-notice-scroll
area: Мобильные формы — SCRIPT-UI-01
kind: user
at: '2026-11-17T07:30:00+04:00'
title: Читать длинные сообщения скриптов обычным шрифтом с прокруткой
why: Уменьшение всего окна делало историю аудита нечитаемой
covers:
- src/main/java/com/nextgis/maplibui/util/ProjectScriptFormController.java
- src/main/java/com/nextgis/maplibui/util/ScriptNoticeDialogs.java
- update-reports/entries/maplibui-20261117-script-notice-scroll.md
---

Только сообщения ProjectScriptFormController используют ScriptNoticeDialogs.
Область длинного текста ограничена оставшимся местом между неподвижными
заголовком и действиями и прокручивается с видимой полосой. Сохраняются обычный
размер текста, системный font scale и естественная высота короткого сообщения.
При font>=150% в landscape снимается необязательный верхний отступ заголовка,
чтобы короткое сообщение помещалось и на узком окне. Глобальный FittedDialogs
и API внешнего пакета scripts не изменены. Прежние listeners/views/actions
переиспользованы; warn/block, ключи ack, back/cancel/close сохраняют поведение.

По 4 native cases PASS на A56/API37, T40/API30 и CPU/API36. Каждая матрица
содержит 24 локальных профиля: две темы/ориентации, font100/200%, ширина
360/320/280 dp, плотность420/540/620 dpi. Проверены максимальные сообщения,
длинное слово/emoji, последняя строка, stationary title/actions, клавиатура,
accessibility actions, поведение контроллера и прежний защищённый путь формы.
Системные font/density настройки устройств не менялись. Промежуточные
отрицательные screenshots и исправления независимого pixel/layout oracle
сохранены; пороги не ослаблялись ради успешного результата.

Ручное чтение TalkBack, общий Full, merge и выпуск остаются открытыми.
Внешний пакет аудита и Desktop-эталон не менялись. Квитанции и bases:
`Q:/Codex/Scratch/mobile-night-20261117-01a1e5ee/retained`.
