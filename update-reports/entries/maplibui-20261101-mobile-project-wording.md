---
id: maplibui-20261101-mobile-project-wording
area: Мобильные проекты
kind: user
at: '2026-11-01T14:27:27+00:00'
title: Проекты нашего Mobile больше не путаются с отдельным Collector
why: Пользователь работает в нашем Mobile; прежние подписи выглядели требованием другого приложения.
covers:
- src/main/res/values/strings.xml
- src/main/res/values-ru/strings.xml
- docs/README.md
- docs/manifest.yaml
- AGENTS.md
---

В названиях и сообщениях RU/EN используется «Мобильный проект» / «Mobile project».
Наше приложение само читает серверный NGW `collector_project` и загружает его
через свой workspace/import pipeline. Отдельное приложение Collector для этого
маршрута не требуется.

Изменены только восемь значений на язык: тип ресурса, ошибки состава/поддержанных
слоёв, пустой список проектов, повторная загрузка и read-only пояснение. Resource
keys, placeholders, идентичности, callbacks, права и работа импорта сохранены.
Технические имена протокола и прежние API не переименованы.

Source bindings сопоставлены с actual callers. XML parsing и точный обратный
readback подтверждают сохранение всех остальных узлов/атрибутов/порядка и `%d`.
Новые literal-only тесты не добавлялись. Consuming resource build и общие docs
checks выполняются отдельно при закрытии задачи; native/GUI/device не запускались.
Library source delivery не означает App gitlink adoption или готовый APK.
