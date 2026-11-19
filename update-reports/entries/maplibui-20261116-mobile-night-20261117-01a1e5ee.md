---
id: maplibui-20261116-mobile-night-20261117-01a1e5ee
area: Мобильное приложение
kind: internal
at: '2026-11-17T03:49:58.690070+00:00'
title: PERF-01, MVT-01, AUTH-01 and SCRIPT-UI-01 overnight implementation and targeted
  physical-device verification
why: Согласованные завершённые изменения рабочей сессии
covers:
- src/main/java/com/nextgis/maplibui/util/NGIDLoginDiagnostics.java
- src/main/java/com/nextgis/maplibui/util/NGIDUtils.java
- src/main/java/com/nextgis/maplibui/util/ProjectScriptFormController.java
- src/main/java/com/nextgis/maplibui/util/ScriptNoticeDialogs.java
- src/test/java/com/nextgis/maplibui/util/NGIDLoginDiagnosticsTest.java
- update-reports/entries/maplibui-20261116-mobile-night-20261117-01a1e5ee.md
- update-reports/entries/maplibui-20261117-ngid-login-diagnostics.md
- update-reports/entries/maplibui-20261117-script-notice-scroll.md
---

PERF-01, MVT-01, AUTH-01 and SCRIPT-UI-01 overnight implementation and targeted physical-device verification

Решения и зависимости:

- Implement the four authorized overnight roadmap items on the idle verified session; preserve all Desktop and shared AGENTS work
- MVT-01 native RED: committed insertion followed by SQLite-source attachment leaves a nonempty legacy Java feature collection on A56/API37. No stale-pixel diagnosis inferred.
- A committed DELETE must notify rendering even when the independent legacy spatial-cache entry is absent. A missing cache item does not mean the database row was not deleted.
- AUTH-01 and SCRIPT-UI-01 have passed targeted physical checks on the current source candidate; retain exact original failures and test-oracle corrections separately.
- Retain native findings, correct the asynchronous export oracle and host-only upload/setup transport, and keep broader acceptance boundaries explicit.
- Finish the mobile night stage at owner request; preserve implemented AUTH/SCRIPT/MVT, failed PERF findings, updated roadmap and exact pending device cleanup; leave shared Desktop session active.
- Correct only unsupported roadmap enum spellings before task end; preserve partial product scope and failed/unknown runtime acceptance.

Полный журнал, входные хэши и результаты проверок: сессия karniilp-01a1e5c4, задача mobile-night-20261117-01a1e5ee. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
