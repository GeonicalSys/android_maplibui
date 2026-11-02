---
id: maplibui-20261102-git-eol
area: Сопровождение
kind: internal
at: '2026-11-02T12:00:00+04:00'
title: Общие правила переносов строк Git
why: Сделать текстовые checkout и экспорт независимыми от Windows core.autocrlf
covers:
  - .gitattributes
---

Общий блок LF и CRLF для Windows-скриптов согласован с остальными
репозиториями ЛИСА. Изображения, шрифты и архивы остаются binary. UI/API
не меняются; дополнительных текстовых blob для нормализации нет.
Связанный app PR MOB-DEV-01; публикации и native-прогона нет.
