### May 3, 2026. Коммит c9780b3 - CQRS

Реализована базовая CQRS-архитектура. Внедрено разделение на чтение и запись на уровне DataSource:
- Добавлены два отдельных DataSource (master для записи, replica для чтения)
- Настроены application.yml с двумя JDBC-URL: spring.datasource.write и spring.datasource.read
- Write-пул -> postgres:5432 (мастер)
- Read-пул -> postgres-replica:5432 (реплика)



### May 4, 2026. Коммит 9364b75 - фикс HikariCP

- Починил конфигурацию HikariCP
- Были конфликты из-за двух DataSource в Spring Boot.
- Устранены проблемы с автоконфигурацией при двух DataSource



### May 8, 2026. Коммит d2df2e1 - разделение DAO

Разбил TenantAwareDao на два класса:
- CqrsTenantAwareReadDao - операции findById, findAll, where, count
- CqrsTenantAwareWriteDao - операции create, update, delete
  Добавил фасад CqrsTenantAwareDao, чтобы GraalVM не сломался



### May 12, 2026. Коммит 9be649d - оптимизация инфраструктуры

- Прикрутил pgBouncer между приложением и PostgreSQL:
    - Write-пул переключил на pgbouncer-write:6432 (session mode)
    - Read-пул переключил на pgbouncer-read:6432 (transaction mode)
- Увеличил партиции Kafka до 10(dev), concurrency=10(dev)
- Настроил PostgreSQL для работы с pgBouncer: max_connections=200, shared_buffers=256MB, effective_cache_size=1GB
- Увеличил пулы HikariCP: write max=10/min=2, read max=20/min=5



### May 15, 2026. Коммит 4028f93 - оптимизация GraalVM и фикс UPDATE

- GraalVM: заменил создание Engine на каждый запрос (50-200 мс) на кешированный синглтон. Теперь 5-15 мс на запрос
- Фикс типизации: GraalVM передавал параметры как org.graalvm.polyglot.Value, JDBC не понимал. Написал convertMap() и convertValue() для конвертации org.graalvm.polyglot.Value в Java-примитивы - устранена BadSqlGrammarException
- Env-переменные: пофиксил ${POSTGRES_DB} внутри SPRING_DATASOURCE_URL - убрал вложенные плейсхолдеры
- Тюнинг пулов: увеличил pgBouncer default_pool_size до 30
- Провел тесты производительности k6

  #### Запуск тестов

    ```
    ./gradlew.bat clean build
    docker-compose up -d
    k6 run load-test-update-only.js
    ```

  #### Результаты нагрузки (k6, 50 VU)

  Сценарий - Ошибки - p(95)
  READ findAll() - 0% - 301 мс
  UPDATE одиночный - 0% -928 мс
  Смешанная нагрузка - 0% - 417 мс
  Batch UPDATE 20 шт - 0% - 6.09 с

  Batch-операции тормозят, потому что каждый update() - отдельный SQL-запрос.TODO: Для массовых обновлений нужен метод updateAll().

