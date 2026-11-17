---
id: maplibui-20261117-ngid-login-diagnostics
area: Мобильное приложение — AUTH-01
kind: user
at: '2026-11-17T07:30:00+04:00'
title: Различать причины отказа NextGIS ID в обычном локальном журнале
why: Экспорт должен связывать попытку входа с этапом и исходным результатом без секретов
covers:
- src/main/java/com/nextgis/maplibui/util/NGIDLoginDiagnostics.java
- src/main/java/com/nextgis/maplibui/util/NGIDUtils.java
- src/test/java/com/nextgis/maplibui/util/NGIDLoginDiagnosticsTest.java
- update-reports/entries/maplibui-20261117-ngid-login-diagnostics.md
---

Интерактивная попытка получает случайный correlation ID и парные записи начала
и результата попытки/этапов token, profile, settings, support. Записываются
только безопасный hostname, монотонная длительность, исходный HTTP-статус
или unknown и тип ошибки. Нет login/password/token, URL query, headers,
request/response bodies или raw exception. Sink — существующий локальный
HyperLog; отказ самого журналирования не ломает вход. ThreadLocal очищается
в finally; прежняя нормализация identifier, пароль и callbacks сохранены.

7 новых unit и прежняя нормализация PASS в составе адресных 92 Java unit.
На смартфоне A56/API37 и CPU/API36 по 9 native cases PASS: успех, 401, 503,
DNS, TLS, настоящий 180-секундный read timeout, malformed token/profile и
старый login contract. Проверяется именно штатный экспорт HyperLog и
отсутствие уникальных тестовых секретов. Асинхронная готовность проверяется
неочищающим чтением, затем выполняется один обычный экспорт; существующее
поведение экспорта не менялось. Состояние проверочного аккаунта восстановлено.

Общий Full и реальный новый выпуск не выполнены; production APK не менялся.
Merge/публикация не выполнялись. Квитанции и точные bases — в retained
каталоге `Q:/Codex/Scratch/mobile-night-20261117-01a1e5ee`.
