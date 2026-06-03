# Frontend: Архитектура и структура

## Общая архитектура

- **Vue 3** — Composition API (`<script setup>`)
- **Vite** — сборщик и dev-сервер
- **Blockly** — визуальный редактор блоков (Google, v12)
- **Axios** — HTTP-запросы к backend
- **@blockly/field-multilineinput** — многострочное текстовое поле для Blockly
- **Локализация** — русская локаль Blockly (`blockly/msg/ru.js`)

## Структура проекта

```
src/
  blockly/
    locales/
      ru.js           — русская локаль Blockly (UMD)
      ru.mjs          — ESM-обёртка над ru.js
    customBlocks.js   — определения кастомных блоков
    toolbox.js        — конфигурация toolbox (категории и блоки)
    dataBlocks.js     — генерация динамических блоков для таблиц БД
  components/
    BlocklyWorkspace.vue — основной компонент редактора
    ResultPanel.vue      — отображение результата выполнения
    SaveProcedureDialog.vue — диалог сохранения/загрузки процедур
  api/
    procedures.js    — API-вызов execute
    auth.js          — аутентификация (Keycloak)
    tables.js        — API-вызовы getTables / getTableColumns
    savedProcedures.js — API-вызовы CRUD для сохранённых процедур
  App.vue            — корневой компонент (layout)
  main.js            — точка входа
```

### Зависимости (package.json)

- `vue` ^3.5.13
- `blockly` ^12.5.1
- `@blockly/field-multilineinput` — многострочное поле
- `axios` ^1.16.1

## Описание модулей

### blockly/locales/

Русская локаль Blockly. Файл `ru.js` — UMD-модуль, содержащий переводы всех встроенных блоков.
Загружается через `<script>` с установкой `window.Blockly = Blockly` для совместимости.

### blockly/customBlocks.js

Определения кастомных блоков, отсутствующих в Blockly:
- `logic_ternary` — тернарная операция
- `text_limited` — текстовый литерал с ограничением 128 символов
- `text_multiline` — многострочный текст (до 1024 символов, `@blockly/field-multilineinput`)
- `date_now` — текущая дата и время (ISO)
- `date_today` — текущая дата (YYYY-MM-DD)
- `date_time_now` — текущее время с выбором часового пояса
- `date_create` — конструктор даты и времени
- `date_get` — извлечение компонента даты
- `date_set` — установка компонента даты
- `date_add` — увеличить дату на N
- `date_subtract` — уменьшить дату на N
- `date_format` — форматировать дату
- `date_diff` — разница между датами
- `date_compare` — сравнить даты
- `action_execute` — выполнить сохранённую процедуру

Также содержит переопределение `text_print` — в GraalVM отсутствует `window.alert`,
поэтому блок генерирует возврат значения вместо вызова console.log/alert.

### blockly/toolbox.js

Конфигурация панели инструментов. Содержит все категории блоков
(Логика, Циклы, Математика, Текст, Списки, Переменные, Функции, Даты) в едином объекте `toolbox`.

### components/BlocklyWorkspace.vue

Основной Vue-компонент. Инициализирует Blockly, подключает русскую локаль,
кастомные блоки и toolbox. Генерирует JavaScript-код при изменении workspace.

### components/ResultPanel.vue

Компонент отображения результата выполнения скрипта.
Обрабатывает состояния: загрузка, ошибка, успех.

### api/procedures.js

Отправка POST-запроса на `/api/procedures/execute` с JSON-телом:
```json
{
    "clientType": "blockly-executor",
    "procedureName": "executeBlocklyScript",
    "parameters": {
        "script": "<generated JavaScript>",
        "parameters": {}
    }
}
```

### api/auth.js

Получение JWT-токена из Keycloak (Resource Owner Password Credentials grant).

---

## Категории блоков

### Логика

| Блок | Тип | Описание |
|------|-----|----------|
| controls_if | Встроенный | Если условие истинно, выполнить действие |
| controls_if (else) | Встроенный | Если...иначе |
| logic_compare | Встроенный | Сравнение (==, !=, <, >, <=, >=) |
| logic_operation | Встроенный | Логическое И / ИЛИ |
| logic_negate | Встроенный | Логическое НЕ |
| logic_boolean | Встроенный | Истина / Ложь |
| logic_null | Встроенный | Ничто (null) |
| logic_ternary | Кастомный | Тернарная операция (если → то, иначе) |

### Циклы

| Блок | Тип | Описание |
|------|-----|----------|
| controls_repeat_ext | Встроенный | Повторить действие N раз |
| controls_whileUntil | Встроенный | Повторять пока условие истинно (while) или до тех пор пока не станет истинным (until) |
| controls_for | Встроенный | Цикл со счётчиком (для i от 0 до N с шагом 1) |
| controls_forEach | Встроенный | Перебор элементов списка (для каждого элемента в списке) |
| controls_flow_statements | Встроенный | Выйти из цикла / перейти к следующей итерации |

### Математика

| Блок | Тип | Описание |
|------|-----|----------|
| math_number | Встроенный | Числовой литерал |
| math_arithmetic | Встроенный | Арифметические операции (+, -, ×, ÷, ^) |
| math_single | Встроенный | Унарные операции (√, |x|, -x, ln, log10, e^x, 10^x) |
| math_round | Встроенный | Округление (округлить, вверх, вниз) |
| math_trig | Встроенный | Тригонометрические функции (sin, cos, tan, cot, asin, acos, atan) |
| math_constant | Встроенный | Математические константы (π, e, φ, √2, √½, ∞) |
| math_number_property | Встроенный | Свойства числа (чётное, нечётное, простое, целое, положительное, отрицательное, делится на) |
| math_on_list | Встроенный | Операции над списком (сумма, минимум, максимум, среднее, медиана) |
| math_modulo | Встроенный | Остаток от деления |
| math_random_int | Встроенный | Случайное целое число от начального до конечного |
| math_random_float | Встроенный | Случайное вещественное число от 0 до 1 |

### Текст

| Блок | Тип | Описание |
|------|-----|----------|
| text_limited | Кастомный | Текстовый литерал (до 128 символов, валидация при вводе) |
| text_multiline | Кастомный | Многострочный текст (до 1024 символов, `@blockly/field-multilineinput`) |
| text_join | Встроенный | Объединение нескольких строк в одну |
| text_append | Встроенный | Добавить текст к переменной |
| text_length | Встроенный | Длина строки (количество символов) |
| text_isEmpty | Встроенный | Проверка на пустую строку (возвращает истину/ложь) |
| text_indexOf | Встроенный | Найти позицию первого/последнего вхождения текста |
| text_charAt | Встроенный | Взять символ по индексу |
| text_getSubstring | Встроенный | Взять подстроку |
| text_changeCase | Встроенный | Изменить регистр (заглавные, строчные, начальные) |
| text_trim | Встроенный | Удалить пробелы в начале и конце |
| text_print | Встроенный | Вывод текста (переопределён: возвращает значение вместо console.log) |
| text_reverse | Встроенный | Изменить порядок символов на обратный |

### Списки

| Блок | Тип | Описание |
|------|-----|----------|
| lists_create_empty | Встроенный | Создать пустой список |
| lists_create_with | Встроенный | Создать список из элементов (редактируемое количество) |
| lists_repeat | Встроенный | Создать список из N одинаковых элементов |
| lists_reverse | Встроенный | Изменить порядок элементов списка на обратный |
| lists_isEmpty | Встроенный | Проверка: пуст ли список (возвращает истину/ложь) |
| lists_length | Встроенный | Получить длину списка |
| lists_indexOf | Встроенный | Найти позицию первого/последнего вхождения элемента |
| lists_getIndex | Встроенный | Взять элемент по индексу (получить/удалить/получить и удалить) |
| lists_setIndex | Встроенный | Установить элемент по индексу |
| lists_getSublist | Встроенный | Взять подсписок |
| lists_split | Встроенный | Разделить текст в список / собрать текст из списка |
| lists_sort | Встроенный | Сортировать список (по возрастанию/убыванию) |

### Переменные

| Блок | Тип | Описание |
|------|-----|----------|
| variables_get | Встроенный | Получение значения переменной |
| variables_set | Встроенный | Установка значения переменной |
| variables_change | Встроенный | Изменение значения переменной на величину |
| variables_declare | Встроенный | Объявление переменной |

> Переменные управляются через встроенный `custom: 'VARIABLE'` — Blockly автоматически создаёт UI для создания, присваивания и изменения переменных. Поддерживаются локальные и глобальные переменные.

### Функции

| Блок | Тип | Описание |
|------|-----|----------|
| procedures_defnoreturn | Встроенный | Создать функцию (без возврата) |
| procedures_defreturn | Встроенный | Создать функцию с возвратом значения |
| procedures_callnoreturn | Встроенный | Выполнить функцию |
| procedures_callreturn | Встроенный | Выполнить функцию и получить результат |
| procedures_return | Встроенный | Вернуть значение из функции |

> Функции управляются через встроенный `custom: 'PROCEDURE'` — Blockly автоматически создаёт UI для создания, вызова и параметризации функций. Поддерживаются функции с параметрами и с возвратом значения.

### Даты

| Блок | Тип | Описание |
|------|-----|----------|
| date_now | Кастомный | Текущая дата и время в формате ISO |
| date_today | Кастомный | Только текущая дата (YYYY-MM-DD) |
| date_time_now | Кастомный | Текущее время с выбором часового пояса |
| date_create | Кастомный | Конструктор даты и времени из компонентов |
| date_get | Кастомный | Извлечь компонент даты (год, месяц, день, час, минута, секунда) |
| date_set | Кастомный | Установить компонент даты (год, месяц, день, час, минута, секунда) |
| date_add | Кастомный | Увеличить дату на N дней/месяцев/лет |
| date_subtract | Кастомный | Уменьшить дату на N дней/месяцев/лет |
| date_format | Кастомный | Форматировать дату (DD.MM.YYYY, YYYY-MM-DD, текст) |
| date_diff | Кастомный | Разница между двумя датами (в днях, месяцах, годах) |
| date_compare | Кастомный | Сравнить две даты (<, >, <=, >=, ==, !=) |

> Все даты хранятся в ISO формате (YYYY-MM-DDTHH:mm:ss.sssZ). Блоки date_add/date_subtract/date_set работают через IIFE и возвращают новую дату, не изменяя оригинал.

### Вложения (планы развития)

> Блоки вложений (attachment_create, attachment_create_csv, attachment_create_xlsx) запланированы как следующий этап развития платформы. Они будут создавать JSON-объекты с описанием файла, которые executor автоматически сохраняет в хранилище и возвращает URL для скачивания. Не входит в рамки данной работы.

### XML (планы развития)

> Блоки для создания XML-файлов запланированы как следующий этап развития платформы. Позволят генерировать структурированные XML-документы из Blockly-скриптов. Не входит в рамки данной работы.

### Данные (динамические блоки)

Категории генерируются автоматически из схемы БД при загрузке приложения.
Архитектура вдохновлена конструктором процедур [Appliner](https://appliner.pro/kb/конструктор-процедур/компоненты-кпр/).

#### Структура категории

```
Данные
  ├── Условия (универсальные condition-блоки)
  ├── {table_name} (динамическая подкатегория)
  ├── {table_name2} ...
```

#### Блоки условий (универсальные, не привязаны к таблице)

| Блок | Тип | Описание |
|------|-----|----------|
| data_condition | expression → Boolean | Колонка + оператор (=, ≠, >, <, ≥, ≤, like, ilike, ~, ~*, !~, !~*) + значение |
| data_condition_between | expression → Boolean | Колонка BETWEEN x AND y |
| data_condition_null | expression → Boolean | Колонка IS NULL |
| data_condition_not_null | expression → Boolean | Колонка IS NOT NULL |
| data_condition_and | expression → Boolean | Логическое И |
| data_condition_or | expression → Boolean | Логическое ИЛИ |
| data_condition_not | expression → Boolean | Логическое НЕ |
| data_condition_in | expression → Boolean | Колонка IN (список) |
| data_where_params | expression → Boolean | Условия + лимит (1-1000) + смещение для пагинации |
| data_column | expression → String | Ввод имени колонки (произвольный текст, используется как fallback) |
| data_value | expression → String | Ввод текстового значения |

#### Поддерживаемые операторы

=, ≠, >, <, ≥, ≤, like (ILIKE), ~ (regex), ~* (regex регистронезависимый), !~ (не regex), !~* (не regex регистронезависимый), between, is_null, is_not_null, in, not_in

#### Блоки для каждой таблицы

| Блок | Тип | Генерация JS | Описание |
|------|-----|-------------|----------|
| все записи из {table} | expression → Array | `DB.table('x').findAll()` | Все записи (LIMIT 100) |
| все записи из {table} где | expression → Array | `DB.table('x').where(condition)` | Записи по условию (LIMIT 100 по умолчанию, настраивается через `data_where_params`) |
| найденная запись из {table} id | expression → Object | `DB.table('x').findById(id)` | Поиск по ID |
| найденная запись из {table} где | expression → Object | `DB.table('x').findOne(condition)` | Одна запись по условию |
| из записи поле | expression | `record.column_name` | Получить поле из записи |
| создать запись в {table} | statement | `DB.table('x').create(data)` | INSERT |
| обновить запись id данные в {table} | statement | `DB.table('x').update(id, data)` | UPDATE |
| удалить запись id из {table} | statement | `DB.table('x').delete(id)` | DELETE |
| колонка {column} из {table} | expression → String | `'column_name'` | Имя колонки (выпадающий список) |

#### Формат условий для DAO

```javascript
// Простое равенство (обратно совместимо)
{name: "test"}                              // WHERE name = 'test'

// Оператор
{name: {op: "like", value: "%test%"}}       // WHERE name ILIKE '%test%'
{price: {op: ">", value: 100}}              // WHERE price > 100
{date: {op: "between", from: "2024-01-01", to: "2024-12-31"}}
{col: {op: "is_null"}}                      // WHERE col IS NULL
{status: {op: "in", value: ["a", "b"]}}     // WHERE status IN ('a','b')

// Логические операторы
{__and: [{name: "test"}, {price: {op: ">", value: 100}}]}
{__or: [{name: "a"}, {name: "b"}]}
{__not: {name: "test"}}

// Пагинация (через блок data_where_params)
{name: {op: ">", value: 100}, __limit: 50, __offset: 0}   // первые 50
{name: {op: ">", value: 100}, __limit: 50, __offset: 50}  // вторая страница
{name: {op: ">", value: 100}, __limit: 50, __offset: 100} // третья страница

// Формат генерации блока data_where_params:
// { ...(conditions), __limit: N, __offset: M }
// Где N = 1..1000 (по умолчанию 100), M = 0..10000 (по умолчанию 0)
```

#### Цвет категорий

Цвет вычисляется по хешу имени таблицы: `hash(tableName) % 360`. Каждая таблица — уникальный цвет.

#### Файлы

| Файл | Описание |
|------|----------|
| `src/blockly/dataBlocks.js` | Генерация блоков условий и категорий таблиц |
| `src/api/tables.js` | API-вызовы getTables / getTableColumns |
| `src/components/BlocklyWorkspace.vue` | Загрузка таблиц при монтировании |

### api/savedProcedures.js

API-модуль для CRUD-операций с сохранёнными процедурами. Все вызовы идут через Kafka (procedure components в executor):

```javascript
fetchSavedProcedures()   // → getSavedProcedures  — список процедур тенанта
getSavedProcedure(name)  // → getSavedProcedureByName — получить процедуру по имени
saveProcedure({...})     // → saveProcedure — создать/обновить процедуру
deleteSavedProcedure(name) // → deleteSavedProcedure — удалить процедуру
```

Формат запроса — тот же `POST /api/procedures/execute` с `procedureName` и `parameters`.
Tenant определяется через `X-Organization-Id` заголовок.

### components/SaveProcedureDialog.vue

Vue-компонент диалога сохранения/загрузки процедур. Два режима:

- **save** — ввод названия и описания, сохранение текущего workspace (XML + сгенерированный JS) в БД
- **list** — список сохранённых процедур с кнопками «Загрузить» и «Удалить»

Открывается из панели инструментов App.vue.

### App.vue — сохранение и загрузка процедур

В панели инструментов добавлены кнопки:
- **«Сохранить»** — сериализует текущий Blockly workspace в XML, открывает диалог сохранения
- **«Мои процедуры»** — открывает список сохранённых процедур

При загрузке процедуры XML восстанавливается в workspace через `Blockly.Xml.clearWorkspaceAndLoadFromXml`.

### customBlocks.js — динамический dropdown в action_execute

Блок `action_execute` загружает список сохранённых процедур при монтировании workspace
(функция `refreshSavedProceduresCache`). Dropdown обновляется динамически —
при сохранении/удалении процедуры кэш обновляется, все блоки `action_execute`
на workspace получают актуальный список.

### action_execute — выполнение сохранённой процедуры

Генерирует JavaScript:
```javascript
executeProcedure('procedure_name');
```

На бэкенде executor ищет процедуру по имени в таблице `saved_procedures`,
загружает её `generated_js` и выполняет в GraalVM-контексте текущего пользователя.

### Бэкенд: сохранённые процедуры

#### Таблица `saved_procedures` (schema: public)

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | SERIAL PK | Идентификатор |
| tenant_id | VARCHAR(64) | ID тенанта |
| name | VARCHAR(255) | Уникальное имя в рамках тенанта |
| description | TEXT | Описание |
| blockly_xml | TEXT | Сериализованный Blockly workspace |
| generated_js | TEXT | Сгенерированный JavaScript-код |
| created_at | TIMESTAMP | Дата создания |
| updated_at | TIMESTAMP | Дата обновления |

UNIQUE(tenant_id, name) — имя процедуры уникально в рамках тенанта.

#### Procedure components в executor

| Компонент | Описание |
|-----------|----------|
| `@Component("getSavedProcedures")` | Список процедур тенанта (без XML/JS для оптимизации) |
| `@Component("getSavedProcedureByName")` | Получение процедуры по имени (с XML и JS) |
| `@Component("saveProcedure")` | Создание/обновление (UPSERT по tenant_id + name) |
| `@Component("deleteSavedProcedure")` | Удаление по имени |

Все компоненты реализуют `ProcedureExecutor<Map<String, Object>, Object>`,
вызываются через Kafka topic `blockly-executor-procedures` как и другие procedure components.

#### SavedProcedureService.java

Сервисный слой с JdbcTemplate. Использует `readJdbcTemplate` для чтения
и `writeJdbcTemplate` для записи. Метод `listByTenant` возвращает список
без `blockly_xml`/`generated_js` (оптимизация для списка), `getByName` —
с полными данными.
