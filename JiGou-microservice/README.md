# JiGou（即构）微服务版 · AI 零代码应用生成平台

> 用一句自然语言描述需求，即可生成可预览、可持续迭代、可一键部署的网页应用。

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-blue)
![Dubbo](https://img.shields.io/badge/Dubbo-3.3.6-2b6cb0)
![Nacos](https://img.shields.io/badge/Nacos-2.x-00a1ea)
![Higress](https://img.shields.io/badge/Higress-Gateway-6b46c1)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)

## 目录

- [项目简介](#项目简介)
- [模块划分](#模块划分)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [端口规划](#端口规划)
- [服务调用关系](#服务调用关系)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [接口文档](#接口文档)
- [构建与打包](#构建与打包)
- [与单体版本的关系](#与单体版本的关系)
- [常见问题](#常见问题)
- [声明](#声明)

## 项目简介

本目录是 JiGou 项目的**微服务版本**，由根目录的单体应用按业务边界拆分而来。单体版本已打基线 tag `v1.0.0-monolith`，目前处于 **legacy 冻结维护**状态，仅修复紧急问题；新功能全部在微服务版本中开发。

微服务版本采用 **Nacos 注册中心 + Dubbo（tri 协议）+ Higress 网关** 的架构：

- 用户、应用、截图三个业务服务独立部署，通过 Dubbo 暴露内部 RPC；
- 前端统一经由 Higress 网关访问 HTTP 接口，不直接访问具体服务；
- 用户服务与应用服务共享 Redis Session，保证登录态在多个服务之间互通。

> 当前处于迁移进行中：核心链路已跑通，部分功能仍在与单体版本对齐，接口行为以单体版本为参照。

## 模块划分

| 模块 | 职责 | 类型 | HTTP 端口 | Dubbo tri 端口 |
| --- | --- | --- | --- | --- |
| `jigou-common` | 公共配置、统一响应、异常、常量、COS 封装、工具类 | 库 | - | - |
| `jigou-model` | 实体、DTO、VO、枚举 | 库 | - | - |
| `jigou-client` | Dubbo 服务接口定义（`InnerUserService`、`InnerScreenshotService`） | 库 | - | - |
| `jigou-ai` | LangChain4j 能力封装：代码生成、应用命名、类型路由、工具调用 | 库 | - | - |
| `jigou-user` | 用户注册 / 登录 / 注销、用户管理 | 可运行 | 8184 | 50054 |
| `jigou-app` | 应用管理、对话生成、版本管理、部署、静态资源 | 可运行 | 8183 | 50053 |
| `jigou-screenshot` | 网页截图、COS 上传 | 可运行 | 8182 | 50052 |

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 基础框架 | JDK 21、Spring Boot 4.1.1、Spring MVC |
| 微服务 | Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0、Dubbo 3.3.6（tri） |
| 注册中心 | Nacos |
| 网关 | Higress |
| 数据访问 | MyBatis-Flex 1.11.8、MySQL 8、HikariCP |
| 缓存 / 会话 | Redis、Spring Session Data Redis、Redisson 3.50.0、Caffeine |
| AI | LangChain4j 1.19.0（OpenAI 兼容协议） |
| 工具 | Selenium 4.33 + WebDriverManager 6.1（截图）、Hutool 5.8.40、Lombok |
| 文档 / 监控 | Knife4j 5.7.9（OpenAPI 3）、Spring Boot Actuator、Micrometer + Prometheus |

## 系统架构

```text
┌─────────────────────────────────────────────┐
│ 浏览器（Vue 3 + Vite）                       │
│ http://localhost:5173                       │
└───────────────────┬─────────────────────────┘
                    │ /api（开发环境由 Vite 代理）
                    ▼
┌─────────────────────────────────────────────┐
│ Higress 网关  http://localhost:8880          │
└───────┬─────────────────┬───────────────────┘
        │                 │
        ▼                 ▼
┌───────────────┐  ┌───────────────┐  ┌──────────────────┐
│ jigou-user    │  │ jigou-app     │  │ jigou-screenshot │
│ :8184         │  │ :8183         │  │ :8182            │
└───────┬───────┘  └───────┬───────┘  └────────┬─────────┘
        │                  │                   │
        │        Dubbo tri（50054/50053/50052）│
        └──────────────────┼───────────────────┘
                           ▼
                ┌─────────────────────┐
                │ Nacos 注册中心 :8848 │
                └─────────────────────┘

        所有服务共享：MySQL 8 + Redis（Session）
```

## 目录结构

```text
JiGou-microservice/
├── pom.xml                       # 聚合工程（dependencyManagement + pluginManagement）
├── jigou-common/                 # 公共模块
├── jigou-model/                  # 实体 / DTO / VO / 枚举
├── jigou-client/                 # Dubbo 服务接口定义
├── jigou-ai/                     # LangChain4j 能力封装
├── jigou-user/                   # 用户服务
├── jigou-app/                    # 应用主服务
└── jigou-screenshot/             # 截图服务
```

每个可运行模块的资源目录结构一致：

```text
src/main/resources/
├── application.yml               # 公共配置（提交）
├── application-dev.yml           # 本地私有配置（已忽略，不提交）
├── application-dev.yml.example   # 私有配置模板（提交）
└── mapper/                       # MyBatis XML（如有）
```

## 端口规划

| 组件 | 端口 | 说明 |
| --- | --- | --- |
| 前端 Vite | 5173 | 开发服务器 |
| Higress | 8880 | 统一网关入口 |
| jigou-user | 8184 / 50054 | HTTP / Dubbo tri |
| jigou-app | 8183 / 50053 | HTTP / Dubbo tri |
| jigou-screenshot | 8182 / 50052 | HTTP / Dubbo tri |
| Nacos | 8848 | 注册中心 |
| 单体（legacy） | 8181 | 与微服务可同时启动，互不冲突 |

> **约定**：`dubbo.protocol.port` 在每个服务中必须唯一，已在各模块 `application.yml` 中固定。

## 服务调用关系

| 消费者 | 提供者 | 接口 | 用途 |
| --- | --- | --- | --- |
| `jigou-app` | `jigou-user` | `InnerUserService` | 获取当前登录用户、校验用户 |
| `jigou-app` | `jigou-screenshot` | `InnerScreenshotService` | 部署后生成应用封面 |

- 接口定义集中在 `jigou-client`，提供方使用 `@DubboService` 实现，消费方使用 `@DubboReference` 引用；
- `jigou-user`、`jigou-app` 均引入 `spring-boot-starter-session-data-redis`，共享 `jigou:session` 命名空间。

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 与单体版本一致 |
| Maven | 3.9+ | 微服务使用独立聚合工程，需在仓库根目录执行 `mvn -f` |
| MySQL | 8.x | 数据库初始化脚本见根目录 `sql/` |
| Redis | 6+ | Session、缓存、AI 对话记忆 |
| Nacos | 2.x | 注册中心，默认 `127.0.0.1:8848` |
| Higress | 最新稳定版 | 统一网关，默认 `localhost:8880` |
| Node.js | 20+ | 前端与 Vue 工程模式代码生成 |

## 快速开始

### 1. 启动基础设施

按顺序启动 Nacos、MySQL、Redis、Higress，并确认：

- Nacos 控制台可访问：<http://localhost:8848/nacos>
- MySQL 中已存在 `jigou` 库
- Redis 可连接（默认无密码）

### 2. 初始化数据库

```bash
mysql -u root -p < sql/create_table.sql
```

增量脚本位于 `sql/upgrade/`，按编号顺序执行。

### 3. 生成三份本地配置

三个可运行模块各复制一份模板（**不要提交** `application-dev.yml`）：

```powershell
# Windows
copy JiGou-microservice\jigou-user\src\main\resources\application-dev.yml.example JiGou-microservice\jigou-user\src\main\resources\application-dev.yml
copy JiGou-microservice\jigou-app\src\main\resources\application-dev.yml.example JiGou-microservice\jigou-app\src\main\resources\application-dev.yml
copy JiGou-microservice\jigou-screenshot\src\main\resources\application-dev.yml.example JiGou-microservice\jigou-screenshot\src\main\resources\application-dev.yml
```

```bash
# macOS / Linux
cp JiGou-microservice/jigou-user/src/main/resources/application-dev.yml.example JiGou-microservice/jigou-user/src/main/resources/application-dev.yml
cp JiGou-microservice/jigou-app/src/main/resources/application-dev.yml.example JiGou-microservice/jigou-app/src/main/resources/application-dev.yml
cp JiGou-microservice/jigou-screenshot/src/main/resources/application-dev.yml.example JiGou-microservice/jigou-screenshot/src/main/resources/application-dev.yml
```

然后将 `<your username>`、`<your password>`、`<Your API Key>` 等占位符替换为真实值。

### 4. 启动服务

启动顺序建议：**jigou-screenshot → jigou-user → jigou-app**（`jigou-app` 依赖另外两个服务的 Dubbo 接口）。

```bash
# 方式一：IDE 中分别运行三个启动类
JiGouScreenshotApplication / JiGouUserApplication / JiGouAppApplication

# 方式二：可执行 jar
java -jar JiGou-microservice/jigou-screenshot/target/jigou-screenshot-1.0-SNAPSHOT.jar
java -jar JiGou-microservice/jigou-user/target/jigou-user-1.0-SNAPSHOT.jar
java -jar JiGou-microservice/jigou-app/target/jigou-app-1.0-SNAPSHOT.jar
```

> `dubbo.consumer.check` 默认为 `false`，即使依赖服务尚未启动，当前服务也能启动。

### 5. 启动前端

```bash
cd JiGou-frontend
npm install
npm run dev:micro    # 经 Higress 8880 访问微服务
# npm run dev        # 默认 --mode mono，访问单体 8181
```

### 6. 验证

- Nacos 控制台 → 服务列表，应能看到三个服务实例；
- 访问任一服务的 Knife4j 文档，确认接口可用；
- 前端登录后刷新页面，确认 Session 在 user / app 之间共享。

## 配置说明

| 文件 | 是否提交 | 内容 |
| --- | --- | --- |
| `application.yml` | 提交 | 应用名、HTTP 端口、Session、**Dubbo 公共配置（application / protocol / consumer / provider）**、springdoc、knife4j |
| `application-dev.yml` | **不提交**（`.gitignore` 已忽略） | 数据源、Redis、Nacos 地址与账号密码、AI Key、COS 密钥 |
| `application-dev.yml.example` | 提交 | 上述私有项的占位模板 |

需要本地填写的敏感项：

| 配置项 | 说明 |
| --- | --- |
| `spring.datasource.username/password` | MySQL 账号密码 |
| `spring.data.redis.password` | Redis 密码（无密码留空） |
| `dubbo.registry.address` | Nacos 地址与账号密码 |
| `langchain4j.open-ai.*.api-key` | DeepSeek API Key（共 4 处，`jigou-app`） |
| `cos.client.secretId/secretKey` | 腾讯云 COS 密钥（`jigou-screenshot`） |

> 修改端口时，必须同步更新：前端 `.env.micro`、Higress 路由、本文档端口表。

## 接口文档

| 服务 | Knife4j 地址 |
| --- | --- |
| jigou-user | <http://localhost:8184/api/doc.html> |
| jigou-app | <http://localhost:8183/api/doc.html> |
| jigou-screenshot | <http://localhost:8182/api/doc.html> |

OpenAPI JSON：`http://localhost:{port}/api/v3/api-docs`。

## 构建与打包

```bash
# 在仓库根目录执行
mvn -f JiGou-microservice/pom.xml -DskipTests clean package
```

| 模块 | 产物 | 说明 |
| --- | --- | --- |
| jigou-user / jigou-app / jigou-screenshot | 可执行 jar（含 `BOOT-INF/`） | 配置了 `spring-boot-maven-plugin` 的 `repackage` |
| jigou-common / model / client / ai | 普通 jar | 父 pom 中 `skip=true` |

> **安全提示**：构建时 `application-dev.yml` 会被复制到 `target/classes` 并打进 jar。若要把 jar 分发或上传到服务器，请改用外部配置（`--spring.config.additional-location=file:./config/`）或环境变量注入，不要分发含真实密钥的 jar。

## 与单体版本的关系

| 阶段 | 仓库位置 | 状态 | 说明 |
| --- | --- | --- | --- |
| 单体版本 | 仓库根目录 `src/`、根 `pom.xml` | **legacy，冻结维护** | 基线 tag `v1.0.0-monolith`，仅修复紧急问题 |
| 微服务版本 | `JiGou-microservice/` | 迁移进行中 | 本目录，新功能在此开发 |
| 移除单体 | - | 待功能对齐后 | 单独提交，删除前打 tag `v1.x-final-monolith` |

两套体系**构建互相独立**：

```bash
# 单体
mvn -DskipTests clean package

# 微服务
mvn -f JiGou-microservice/pom.xml -DskipTests clean package
```

## 常见问题

**1. 启动时报 `Nacos registry address is invalid` 或连接超时**

确认 Nacos 已启动（`http://localhost:8848/nacos`），并检查 `application-dev.yml` 中 `dubbo.registry.address` 的地址与账号密码。

**2. 启动多个服务时报 `Port 22222 is already in use`**

Dubbo QoS 端口默认 22222。各模块 `application.yml` 已配置 `dubbo.application.qos-enable: false`，若自行改动请保持一致。

**3. 启动时报 Dubbo tri 端口冲突**

`jigou-user` / `jigou-app` / `jigou-screenshot` 的 `dubbo.protocol.port` 分别为 50054 / 50053 / 50052，新增服务时必须分配未占用的端口。

**4. 登录后在另一个服务接口上取不到用户**

检查三个服务的 `spring.session.data.redis.namespace` 是否都为 `jigou:session`、`spring.session.timeout` 是否一致（30 天），并确认连接的是同一个 Redis。

**5. 前端请求 404 或跨域**

- 微服务开发请用 `npm run dev:micro`，确认 `.env.micro` 指向 Higress（8880）；
- 单体开发用 `npm run dev`，确认 `.env.mono` 指向 8181；
- 检查 Higress 路由是否已把 `/api` 转发到正确的服务。

**6. AI 调用返回 401 / 鉴权失败**

`jigou-app` 的 `application-dev.yml` 中 `langchain4j.open-ai` 下有 **4 处** `api-key`，逐一替换为真实 DeepSeek Key。

**7. 启动报 `Could not resolve placeholder`**

没有生成本地配置。按[快速开始第 3 步](#3-生成三份本地配置)复制 `application-dev.yml.example` 为 `application-dev.yml` 并填写。

**8. 截图服务初始化 Chrome 失败**

本机未安装 Chrome，或 WebDriverManager 首次下载驱动失败（需要联网）。

**9. 单体与微服务可以同时启动吗？**

可以。单体使用 8181，微服务使用 8182 / 8183 / 8184，端口不冲突；但两者共享同一个 MySQL 库和 Redis，调试时注意数据互相影响。

## 声明

- 本项目为个人学习项目，配置模板中的 `<your-xxx>` 均为占位符，请勿将包含真实密钥的 `application-dev.yml` 提交到仓库；
- 如密钥曾外泄，请及时到 DeepSeek、腾讯云、Nacos 等平台重置；
- 代码仅供学习交流使用。
