---
id: maplibui-20261030-form-list-messages
area: Сообщения мобильного приложения
kind: user
at: '2026-10-30T12:45:34.020191+00:00'
title: Ошибки списка формы отображаются понятным текстом
why: Три Toast показывали буквальные имена R.string вместо объяснения.
covers:
- src/main/java/com/nextgis/maplibui/activity/FormBuilderModifyAttributesActivity.java
- src/main/res/values/legacy_form_error_strings.xml
- src/main/res/values-ru/legacy_form_error_strings.xml
---

Вместо R.string.error_invalid_column/error_invalid_row/error_empty_list подготовлены
русские и английские объяснения: список формы недоступен, ранее выбранная строка
больше недоступна либо в списке нет доступных строк. Изменены только3 Toast argument
literals и добавлены resource strings. Metadata, row reset, callback и Save прежние.

Actual App SDK28 same4 fixture baseline:3 meaningful comparison failures/1 valid-row
control,0errors/skips, XML3.180с, native1; все11 owned процессов завершились,
technical meta files удалены, source41 unchanged. Исправление применено в исходниках;
same4/6eb9 GREEN0.434с входит в actual89 PASS/0fail/errors/skips, а не89+4.
Первый общий85 (включая успешные literal4 XML2.721с) остаётся NOT_PASS81/4assert,
0errors/skips; последующее89 имеет отдельную source и oracle history. No-lifecycle
технические методы не заменяют реальную форму/keyboard/large-font/device acceptance.

Проверка: consuming App com.nextgis.maplibui.activity.FormLegacyListMessagesTest.
UI Merge Commit предшествует App pointer; Draft source delivery не является выпуском.

### Закрытие source-среза сообщений — 30 октября2026

После semantic review документов global dependency index проверен CURRENT:
5761 files / 78853 edges, source SHA256
`3742037ad3e46bdbb2183c026b451b4abfa69a0d81ccc6c9aa8fa4b6207d71c1`.
Basis: own Root/App/Core/UI working trees, foreign easypicker/publisher committed
HEAD snapshots; 188 historical observations сохранены побайтно. Полнота связей
остаётся UNKNOWN. Final impact сопоставляет все36 changed source/test/resource
paths; это план, не исполнение дополнительных сценариев.

App docs-check:26 policy tests PASS; local UI validator оставляет ровно прежний
WF-SYNC-CLOCK-STORE. Отдельные57 docs tests:53PASS/4fail того же F61 clock contract.
Его ожидание не подгонялось под live unmerged library. Root docs validator:
ровно одна прежняя ошибка frontmatter в excluded Wiki backup REVERT.md; Wiki
не менялась. Поэтому полные docs suites не объявляются PASS.

После metadata review из68 inputs выполненных89/compile проверок неизменны67
behavioral/source/runtime inputs; единственное намеренное изменение — reviewed
UI contract catalogue. Код/тесты после89PASS и Belka source compile не менялись.
Fresh fetch всех6 repositories успешен, owned branches ahead/behind0/0 перед
коммитом. App gitlinks F61/E66 сохраняются до library Merge Commit; source Draft
не является готовым APK. Report guards пройдены отдельно от docs limitations.
