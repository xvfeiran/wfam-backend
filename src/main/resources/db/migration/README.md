## 一、Flyway 的核心概念

1. **Migration（迁移）**

    * 每一个 SQL 文件或 Java 类都是一次迁移（schema 或数据）
    * 文件名里带版本号：`V1__create_part_table.sql`

2. **Version（版本号）**

    * Flyway 会记录数据库中执行过的版本（默认表 `flyway_schema_history`）
    * 避免重复执行

3. **Repeatable Migration（可重复执行）**

    * 用于初始化数据或者视图，名字用 `R__init_data.sql`

4. **数据库连接 + 自动执行**

    * Flyway 本身不提供数据库，它直接通过 JDBC 连接你的数据库
    * Spring Boot 启动时自动执行迁移

---

## 二、Spring Boot 项目中使用 Flyway（零服务部署）

### 1️⃣ 添加依赖

**Maven**

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

**Gradle**

```gradle
implementation 'org.flywaydb:flyway-core'
```

---

### 2️⃣ 创建迁移文件目录

```
src/main/resources/db/migration/
├─ V1__create_part_table.sql
├─ V2__create_supplier_table.sql
├─ V3__insert_initial_data.sql
```

规则：

* `V{version}__{description}.sql`
* Flyway 按版本号执行
* 放在 `classpath:db/migration/` 下（Spring Boot 默认路径）

---

### 3️⃣ 配置 application.yml

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:ORCL
    username: local_user
    password: local_pwd
    driver-class-name: oracle.jdbc.OracleDriver

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

解释：

* `enabled: true` → 启用 Flyway
* `locations` → SQL 文件目录
* `baseline-on-migrate: true` → 如果数据库已经有表，Flyway 可以从现有状态开始迁移

---

### 4️⃣ Spring Boot 启动流程

1. Spring Boot 初始化 DataSource
2. Flyway 读取 `db/migration` 目录
3. 检查 `flyway_schema_history` 表（如果不存在，会自动创建）
4. 执行未执行过的 migration 文件
5. 启动应用

> 不需要单独启动 Flyway 服务，一切都是嵌入在 Spring Boot 内部执行的

---

### 5️⃣ 可选：命令行或 Maven 执行

* Maven：

```bash
mvn flyway:migrate
```

* CLI 或 Docker 也能使用 Flyway，但在 Spring Boot 场景下一般不需要

---

## 三、企业级实践建议

1. **只管理 DDL / 初始数据**

    * CRUD 查询不用 Flyway，交给 JPA / REST Repositories
2. **严格版本化**

    * 每张表一个版本
    * 数据初始化用 repeatable migration
3. **多环境区分**

    * local / test / prod 都可以使用同一套 Flyway 配置，只需换数据库连接
4. **不依赖 OSIV**

    * Flyway 在应用启动阶段就执行，保证数据库结构完整
