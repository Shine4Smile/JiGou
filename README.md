# JiGou（即构）· AI 零代码应用生成平台

> 用一句自然语言描述需求，即可生成可预览、可持续迭代、可一键部署的网页应用。

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)
![Vite](https://img.shields.io/badge/Vite-7-646cff)
![MySQL](https://img.shields.io/badge/MySQL-8-4479a1)
![Redis](https://img.shields.io/badge/Redis-6%2B-dc382d)

## 目录

- [项目简介](#项目简介)
- [核心能力](#核心能力)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
  - [1. 克隆项目](#1-克隆项目)
  - [2. 初始化数据库](#2-初始化数据库)
  - [3. 生成并填写本地配置](#3-生成并填写本地配置)
  - [4. 启动后端](#4-启动后端)
  - [5. 启动前端](#5-启动前端)
  - [6. 创建管理员账号](#6-创建管理员账号)
- [配置说明](#配置说明)
- [Nginx 配置（部署 / 截图 / 架构图）](#nginx-配置部署--截图--架构图)
- [接口文档](#接口文档)
- [工作流模式（LangGraph4j，实验特性）](#工作流模式langgraph4j实验特性)
- [监控与可观测性](#监控与可观测性)
- [测试](#测试)
- [常见问题](#常见问题)
- [声明](#声明)

## 项目简介

JiGou 是一个 AI 零代码应用生成平台。用户输入一句需求后，后端调用大模型以 **SSE 流式**方式生成代码，前端实时展示生成过程并在 iframe 中预览效果；生成的应用可以继续对话迭代、提交版本快照、回滚历史版本、部署上线、下载源码。

平台同时提供用户与管理员体系、应用广场、对话历史、接口限流，以及基于 Prometheus + Grafana 的 AI 调用监控。

项目采用**前后端分离**结构：后端是单体 Spring Boot 应用（本仓库根目录），前端是独立的 Vue 3 工程（`JiGou-frontend/`）。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| AI 应用生成 | 支持 **原生 HTML 单文件**、**原生多文件**、**Vue 工程** 三种生成模式，由 AI 智能路由自动选择 |
| 流式对话 | SSE 推送生成内容与工具调用结果，生成完成后发送 `done` 事件，异常时发送 `error` 事件 |
| 实时预览 | 生成产物通过 `/api/static/{预览目录}/` 直接访问，前端 iframe 中注入可视化编辑脚本 |
| AI 应用命名 | 根据初始需求自动生成应用名称，失败时按需求文本截断兜底 |
| 版本管理 | 工作区代码 → 提交版本快照（默认最多保留 20 个，当前版本与线上部署版本受保护）、版本详情与差异对比、历史版本回滚 |
| 一键部署 | 将代码复制到部署目录并生成 6 位 `deployKey`，支持部署工作区最新代码或指定历史版本，也支持取消部署 |
| 自动封面 | 部署后用 Selenium 无头 Chrome 截图，压缩后写入应用封面字段 |
| 应用广场 | 精选应用、公开应用列表、可见性（`private` / `public`）控制、深分页保护 |
| 源码下载 | 将应用目录打包为 ZIP 下载 |
| 用户体系 | 注册 / 登录 / 注销、Spring Session 存 Redis、`user` / `admin` 角色、`@AuthCheck` 注解鉴权 |
| 接口限流 | 基于 Redisson 的分布式限流（AI 对话默认 5 次 / 60 秒） |
| 对话历史 | 用户侧与管理端分别分页查询 |
| 可观测性 | Actuator + Micrometer 输出 AI 请求数、Token 数、响应耗时、错误数，附 Grafana 看板 |
| 工作流模式（实验） | 基于 LangGraph4j 的多节点工作流，已跑通并有集成测试，**但尚未接入业务主链路** |

## 技术栈

### 后端

| 分类 | 技术 |
| --- | --- |
| 基础框架 | JDK 21、Spring Boot 4.1.1、Spring MVC |
| 数据访问 | MyBatis-Flex 1.11.8、MySQL 8、HikariCP |
| 缓存 / 会话 | Redis、Spring Session Data Redis、Redisson 3.50.0、Caffeine |
| AI | LangChain4j 1.19.0（OpenAI 兼容协议）、LangGraph4j 1.6.0-rc2、阿里云 DashScope SDK |
| 工具 | Selenium 4.33 + WebDriverManager 6.1（截图）、Hutool 5.8.40、Lombok |
| 文档 / 监控 | Knife4j 4.4.0（OpenAPI 3）、Spring Boot Actuator、Micrometer + Prometheus |

### 前端

| 分类 | 技术 |
| --- | --- |
| 基础框架 | Vue 3.5、Vite 7、TypeScript 5.8 |
| UI / 状态 | Ant Design Vue 4.2、Pinia 3、Vue Router 4.5 |
| 请求 / 渲染 | Axios、marked、DOMPurify、highlight.js |
| 工程化 | ESLint、Prettier、vue-tsc、@umijs/openapi（openapi2ts） |

### 外部服务

| 服务 | 用途 |
| --- | --- |
| DeepSeek（OpenAI 兼容接口） | 对话生成代码、应用命名、类型路由、推理 |
| 阿里云百炼 DashScope | `wan2.2-t2i-flash` 生成 Logo |
| Pexels API | 按关键词搜索配图 |
| undraw | 搜索插画素材 |
| Mermaid CLI（`mmdc`） | 将 Mermaid 代码渲染为 SVG 架构图 |

## 系统架构

```text
┌──────────────────────────────────────┐
│ 浏览器（Vue 3 + Vite）                │
│ http://localhost:5173                │
└──────────────────┬───────────────────┘
                   │ /api（开发环境由 Vite 代理，生产环境由 Nginx 反向代理）
                   ▼
┌──────────────────────────────────────────────────────────────┐
│ Spring Boot 单体应用  http://localhost:8181/api               │
│ 用户 / 应用 / 版本 / 对话 / 静态资源 / 工作流 Controller        │
│ AppService ──► LangChain4j（SSE 流式生成）                    │
│            └─► LangGraph4j（多节点工作流，实验特性）            │
└──────┬────────────────┬───────────────────────┬──────────────┘
       │                │                       │
       ▼                ▼                       ▼
   MySQL 8          Redis                 DeepSeek / DashScope
   (jigou)      Session / 缓存 / 限流       / Pexels / undraw
                     / AI 对话记忆
       │
       ▼
本地文件系统 <项目根目录>/tmp
├── code_output      应用工作区：最新代码，预览基于该目录
├── code_versions    历史版本快照：{codeGenType}_{appId}/v{n}
├── code_deploy      部署产物：{deployKey}/，由 Nginx 托管
├── screenshots      应用封面截图，由 Nginx 托管
└── MermaidDiagram   Mermaid 架构图 SVG，由 Nginx 托管
```

## 目录结构

```text
JiGou/
├── src/main/java/com/simple/jigou/
│   ├── ai/              # LangChain4j AI Service、输入/输出护轨、工具调用、流式消息模型
│   ├── annotation/      # @AuthCheck 等自定义注解
│   ├── aop/             # 鉴权拦截切面
│   ├── common/          # 统一响应、分页请求等通用对象
│   ├── config/          # 数据源、Redis、Session、Redisson、CORS、AI 模型等配置
│   ├── constant/        # 应用、用户、对话相关常量（含 tmp 目录约定）
│   ├── controller/      # 用户、应用、版本、对话、静态资源、工作流接口
│   ├── core/            # 代码解析、文件保存、Vue 工程构建、SSE 消息处理
│   ├── exception/       # 业务异常、错误码、全局异常处理
│   ├── generator/       # MyBatis-Flex 代码生成器
│   ├── langgraph4j/     # 工作流定义、节点、状态与图片工具（实验特性）
│   ├── mapper/          # MyBatis-Flex Mapper
│   ├── model/           # entity / dto / vo / enums
│   ├── monitor/         # AI 模型调用指标采集（Micrometer）
│   ├── ratelimit/       # @RateLimit 注解与 Redisson 限流切面
│   ├── service/         # 业务服务接口与实现
│   └── utils/           # 缓存 Key、Spring 上下文、网页截图等工具
├── src/main/resources/
│   ├── application.yml              # 公共配置：端口、Session、MyBatis、Knife4j、Actuator
│   ├── application-dev.yml.example  # 本地开发配置模板（复制为 application-dev.yml 后填写）
│   ├── mapper/                      # MyBatis XML
│   └── prompt/                      # 各场景系统提示词
├── src/test/java/                   # 单元测试与集成测试
├── JiGou-frontend/                  # Vue 3 前端工程（独立 npm 项目）
├── sql/
│   ├── create_table.sql             # 建库建表脚本
│   └── upgrade/                     # 增量升级脚本（按序号执行）
├── grafana/                         # Grafana 看板 JSON 与导入说明
├── prometheus.yml                   # Prometheus 抓取配置
└── tmp/                             # 运行时生成的代码、版本、部署产物与图片（不入库）
```

## 环境要求

| 依赖 | 版本 / 说明 | 是否必需 |
| --- | --- | --- |
| JDK | 21 | 必需 |
| Maven | 3.9+（项目未提交 `mvnw`，需本地安装） | 必需 |
| MySQL | 8.x | 必需 |
| Redis | 6+（用于 Session、缓存、分布式限流、AI 对话记忆） | 必需 |
| Node.js | 20.19+ 或 22.12+（Vite 7 要求） | 必需（前端） |
| npm | 10+ | 必需（前端） |
| Google Chrome | 部署后自动截图生成封面 | 使用部署/封面功能时必需 |
| Mermaid CLI | `npm i -g @mermaid-js/mermaid-cli`，提供 `mmdc` 命令 | 仅工作流模式生成架构图时需要 |
| Nginx | 托管部署产物、封面截图与 Mermaid 架构图 | 使用部署 / 封面 / 架构图功能时必需 |

> 说明：Selenium 通过 WebDriverManager 自动下载 ChromeDriver，首次使用需要联网。

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd JiGou
```

### 2. 初始化数据库

先确保 MySQL 已启动，然后在**项目根目录**执行（脚本会创建 `jigou` 库以及 `user`、`app`、`chat_history` 三张表）：

```bash
# Windows cmd / macOS / Linux
mysql -u root -p --default-character-set=utf8mb4 < sql/create_table.sql
mysql -u root -p --default-character-set=utf8mb4 jigou < sql/upgrade/01_add_app_version.sql
mysql -u root -p --default-character-set=utf8mb4 jigou < sql/upgrade/02_add_app_visibility.sql
mysql -u root -p --default-character-set=utf8mb4 jigou < sql/upgrade/03_add_app_deployed_version.sql
```

```powershell
# Windows PowerShell 不支持 "<" 输入重定向，改用管道
Get-Content .\sql\create_table.sql -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4
Get-Content .\sql\upgrade\01_add_app_version.sql -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 jigou
Get-Content .\sql\upgrade\02_add_app_visibility.sql -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 jigou
Get-Content .\sql\upgrade\03_add_app_deployed_version.sql -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 jigou
```

> `sql/upgrade/` 下的脚本是历史增量变更，全新安装时也需要按序号依次执行。

### 3. 生成并填写本地配置

`application-dev.yml` 含数据库密码与各类 API Key，已被 `.gitignore` 忽略，需要从模板生成：

```bash
# Windows cmd
copy src\main\resources\application-dev.yml.example src\main\resources\application-dev.yml

# Windows PowerShell
Copy-Item .\src\main\resources\application-dev.yml.example .\src\main\resources\application-dev.yml

# macOS / Linux
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
```

然后修改 `src/main/resources/application-dev.yml` 中的占位符（详见 [配置说明](#配置说明)）：

| 占位符 | 替换为 |
| --- | --- |
| `<your-mysql-username>` / `<your-mysql-password>` | 本地 MySQL 账号密码 |
| `<your-pexels-api-key>` | Pexels API Key（[申请地址](https://www.pexels.com/api/)） |
| `<your-deepseek-api-key>` | DeepSeek API Key（[开放平台](https://platform.deepseek.com/)），4 处都要替换 |
| `<your-dashscope-api-key>` | 阿里云百炼 DashScope API Key（[控制台](https://bailian.console.aliyun.com/)） |

### 4. 启动后端

```bash
# 方式一：开发模式直接运行
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/JiGou-0.0.1-SNAPSHOT.jar
```

启动成功后：

| 地址 | 说明 |
| --- | --- |
| `http://localhost:8181/api/health/` | 健康检查，返回 `succeed` |
| `http://localhost:8181/api/doc.html` | Knife4j 接口文档 |
| `http://localhost:8181/api/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8181/api/actuator/prometheus` | Prometheus 指标 |

> 端口与路径由 `src/main/resources/application.yml` 中的 `server.port`（8181）和 `server.servlet.context-path`（`/api`）决定，修改后前端代理地址需同步调整。

### 5. 启动前端

```bash
cd JiGou-frontend
npm install
npm run dev
```

浏览器访问 **http://localhost:5173**。

- 开发环境下 `vite.config.ts` 已把 `/api` 代理到 `http://localhost:8181`，因此接口、SSE 流式对话与 iframe 预览都与前端同源，无需额外配置跨域。
- 其他脚本：`npm run build`（类型检查 + 生产构建，产物在 `dist/`）、`npm run lint`、`npm run format`、`npm run openapi2ts`。

### 6. 创建管理员账号

管理员没有预置账号，先在前端注册一个普通用户，然后在数据库中提升为管理员：

```sql
update `user` set `userRole` = 'admin' where `userAccount` = 'your-account';
```

重新登录后即可访问 `/admin/userManage`、`/admin/appManage`、`/admin/chatHistoryManage` 管理端页面。

## 配置说明

### 公共配置 `application.yml`（已提交）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.application.name` | `JiGou` | 应用名，同时作为 Prometheus 指标标签的来源之一 |
| `spring.profiles.active` | `dev` | 默认激活开发环境配置 |
| `spring.session.timeout` | `2592000` | Session 过期时间（秒），默认 30 天，存储于 Redis |
| `server.port` | `8181` | 后端端口 |
| `server.servlet.context-path` | `/api` | 所有接口统一前缀，前端代理按此转发 |
| `mybatis.configuration.log-impl` | `StdOutImpl` | 控制台打印 SQL，生产环境建议关闭 |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | 暴露的 Actuator 端点 |
| `springdoc.group-configs` / `knife4j` | — | 接口文档分组与中文界面 |

### 本地配置 `application-dev.yml`（不提交）

从 `application-dev.yml.example` 复制而来，各配置项含义如下：

| 配置项 | 是否必填 | 说明 |
| --- | --- | --- |
| `spring.datasource.url` | 是 | 默认 `jdbc:mysql://localhost:3306/jigou`，库名需与建表脚本一致 |
| `spring.datasource.username` / `password` | 是 | 本地 MySQL 账号密码 |
| `spring.data.redis.host` / `port` / `database` | 是 | 默认 `localhost:6379`、`db 0` |
| `spring.data.redis.password` | 否 | 无密码时留空 |
| `spring.data.redis.ttl` | 否 | 缓存过期时间（秒），默认 3600 |
| `pexels.api-key` | 是 | 图片搜索工具使用，配图生成依赖 |
| `langchain4j.open-ai.chat-model.*` | 是 | 同步模型：应用命名、生成类型路由 |
| `langchain4j.open-ai.streaming-chat-model.*` | 是 | 流式模型：**业务主链路的代码生成** |
| `langchain4j.open-ai.reasoning-streaming-chat-model.*` | 是 | 推理模型：复杂推理任务 |
| `langchain4j.open-ai.routing-chat-model.*` | 是 | 轻量路由模型：简单分类任务 |
| `dashscope.api-key` | 是 | AI 生成 Logo 使用 |
| `dashscope.image-model` | 否 | 默认 `wan2.2-t2i-flash` |

> 所有模型都走 OpenAI 兼容协议。若要换成其他兼容服务，修改 `base-url` 与 `model-name` 即可（`api-key` 同步替换）。

## Nginx 配置（部署 / 截图 / 架构图）

后端的这三类产物保存在本地磁盘，且代码中生成的访问地址是 `http://localhost/...`，因此需要 Nginx 把它们映射为 HTTP 可访问的静态资源：

| 用途 | 后端写入目录 | 对外访问地址 | 相关代码 |
| --- | --- | --- | --- |
| 应用部署产物 | `<项目根>/tmp/code_deploy/{deployKey}/` | `http://localhost/{deployKey}/` | `AppConstant.CODE_DEPLOY_ROOT_DIR`、`CODE_DEPLOY_HOST` |
| 应用封面截图 | `<项目根>/tmp/screenshots/{随机目录}/*_compressed.jpg` | `http://localhost/screenshots/...` | `WebScreenshotUtils`、`AppServiceImpl.convertToHttpUrl` |
| Mermaid 架构图 | `<项目根>/tmp/MermaidDiagram/{随机目录}/*.svg` | `http://localhost/MermaidDiagram/...` | `MermaidDiagramTool.convertMermaidUrlToHttpUrl` |

在 Nginx 的 `conf/nginx.conf` 中增加以下配置（**把 `D:/path/to/JiGou` 替换为你本机项目所在的绝对路径**）：

```nginx
server {
    listen       80;
    server_name  localhost;
    charset      utf-8;
    charset_types text/css application/javascript text/plain text/xml application/json;

    # 项目部署根目录：<项目根>/tmp/code_deploy
    root         D:/path/to/JiGou/tmp/code_deploy;

    # 已部署应用：http://localhost/{deployKey}/ -> tmp/code_deploy/{deployKey}/index.html
    location ~ ^/([^/]+)/(.*)$ {
        try_files /$1/$2 /$1/index.html =404;
    }

    location / {
        root   html;
        index  index.html index.htm;
    }

    # 应用封面截图：<项目根>/tmp/screenshots（^~ 阻止被上面的正则 location 抢占）
    location ^~ /screenshots/ {
        alias D:/path/to/JiGou/tmp/screenshots/;
        add_header Access-Control-Allow-Origin *;
    }

    # Mermaid 架构图：<项目根>/tmp/MermaidDiagram
    location ^~ /MermaidDiagram/ {
        alias D:/path/to/JiGou/tmp/MermaidDiagram/;
        add_header Access-Control-Allow-Origin *;
    }

    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   html;
    }
}
```

注意事项：

1. Windows 下 Nginx 配置同样使用正斜杠 `/`；`alias` 末尾的 `/` 不能省略，否则会拼出错误路径。
2. `^~` 前缀匹配优先级高于正则 `location`，缺少它时 `/screenshots/...`、`/MermaidDiagram/...` 可能被 `location ~ ^/([^/]+)/(.*)$` 截获而返回 404。
3. 修改配置后先 `nginx -t` 校验，再 `nginx -s reload` 生效；建议启动后端并完成一次部署后再验证封面与部署地址。
4. **不配置 Nginx 时**：代码生成、对话、预览（走 `/api/static/...`）、版本管理、源码下载均正常；只有「部署后的访问地址」「部署后生成的封面」「工作流生成的 Mermaid 架构图」无法访问或显示为裂图。
5. 若要把部署地址换成自定义域名 / 端口，除了改 Nginx，还需要同步修改三处硬编码：`AppConstant.CODE_DEPLOY_HOST`、`AppServiceImpl.convertToHttpUrl()`、`MermaidDiagramTool.convertMermaidUrlToHttpUrl()`。
6. 该方案为个人学习项目的简化实现（本地存储 + Nginx），生产环境可替换为对象存储（如 COS / OSS）；同时建议限制部署目录的访问范围，避免用户生成内容被任意引用。

## 接口文档

启动后端后访问 **Knife4j：<http://localhost:8181/api/doc.html>**，或直接获取 OpenAPI JSON：`http://localhost:8181/api/v3/api-docs`（前端 `npm run openapi2ts` 也指向该地址）。

以下接口均以 `/api` 为前缀：

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 用户 | POST | `/user/register` | 用户注册 |
| 用户 | POST | `/user/login` | 用户登录（Session 写入 Redis） |
| 用户 | GET | `/user/get/login` | 获取当前登录用户 |
| 用户 | POST | `/user/logout` | 退出登录 |
| 用户 | POST | `/user/edit` | 编辑个人信息 |
| 用户 | POST | `/user/add` | 新增用户（管理员） |
| 用户 | GET | `/user/get` | 根据 id 获取用户（管理员） |
| 用户 | GET | `/user/get/vo` | 获取脱敏用户信息（管理员） |
| 用户 | POST | `/user/delete` | 删除用户（管理员） |
| 用户 | POST | `/user/update` | 更新用户（管理员） |
| 用户 | POST | `/user/list/page/vo` | 分页查询用户（管理员） |
| 应用 | POST | `/app/add` | 创建应用（未填名称时由 AI 生成） |
| 应用 | POST | `/app/edit` | 编辑应用 |
| 应用 | POST | `/app/delete` | 删除应用 |
| 应用 | GET | `/app/get/vo` | 获取应用详情 |
| 应用 | POST | `/app/list/page/vo` | 我的应用分页 |
| 应用 | POST | `/app/list/featured/vo` | 精选应用列表 |
| 应用 | POST | `/app/list/public/vo` | 公开应用广场 |
| 应用 | GET | `/app/chat/gen/code` | AI 对话生成代码（SSE，限流 5 次/60 秒） |
| 应用 | POST | `/app/deploy` | 部署应用（可指定历史版本） |
| 应用 | POST | `/app/undeploy` | 取消部署 |
| 应用 | GET | `/app/download/{appId}` | 下载应用源码 ZIP |
| 应用 | POST | `/app/admin/delete` | 删除应用（管理员） |
| 应用 | POST | `/app/admin/update` | 更新应用（管理员） |
| 应用 | POST | `/app/admin/list/page/vo` | 分页查询应用（管理员） |
| 应用 | GET | `/app/admin/get` | 获取应用详情（管理员） |
| 版本 | POST | `/app/version/commit` | 提交当前工作区为新版本 |
| 版本 | GET | `/app/version/list` | 版本列表 |
| 版本 | GET | `/app/version/detail` | 版本详情（含代码） |
| 版本 | GET | `/app/version/workspace` | 当前工作区代码 |
| 版本 | POST | `/app/version/rollback` | 回滚到指定版本 |
| 对话 | POST | `/chatHistory/app/list/page/vo` | 我的对话历史分页 |
| 对话 | POST | `/chatHistory/admin/list/page/vo` | 全部对话历史分页（管理员） |
| 静态 | GET | `/static/{deployKey}/**` | 预览 / 部署产物静态资源（私有应用会校验权限） |
| 健康 | GET | `/health/` | 健康检查 |
| 工作流 | POST | `/workflow/execute` | 同步执行工作流（演示接口） |
| 工作流 | GET | `/workflow/execute-flux` | SSE 执行工作流（演示接口） |

> 除健康检查、工作流演示接口与公开的静态资源外，其余接口均需要登录；管理员接口还会校验 `admin` 角色。

## 工作流模式（LangGraph4j，实验特性）

除流式输出外，项目还用 LangGraph4j 实现了一套多节点工作流，链路如下：

```text
START
  └─► image_collector   收集配图：Pexels 搜图 / undraw 插画 / Mermaid 架构图 / DashScope Logo
        └─► prompt_enhancer   结合素材增强提示词
              └─► router          智能路由选择生成类型（HTML / 多文件 / Vue 工程）
                    └─► code_generator   生成代码并保存到应用工作区
                          └─► code_quality_check  质量检查
                                ├─ build      ──► project_builder（构建 Vue 工程）──► END
                                ├─ skip_build ──► END
                                └─ fail       ──► 回到 code_generator 重新生成
```

- 入口类：`langgraph4j/CodeGenWorkflow.java`，节点实现位于 `langgraph4j/node/`。
- 演示接口（**未接入鉴权、限流与落库，请勿在生产环境直接暴露**）：
  - `POST http://localhost:8181/api/workflow/execute?prompt=创建一个技术博客网站`
  - `GET http://localhost:8181/api/workflow/execute-flux?prompt=创建一个技术博客网站`（SSE）
- 测试：`CodeGenWorkflowTest` 覆盖技术博客、企业官网、Vue 项目、简单个人主页 4 个场景，测试可通过。
- **该模式目前未接入业务主链路**：前端对话仍然走 `AppController#chatToGenCode` → `AppServiceImpl#chatToGenCode` 的流式输出。如需使用工作流生成应用，可参考 `AppServiceImpl#chatToGenCode` 的参数校验、用户校验、对话历史记录、SSE 推送方式，把工作流执行结果接入；注意工作流会真实写入 `tmp/code_output` 并调用 `npm` 构建 Vue 工程。

## 监控与可观测性

后端已集成 Spring Boot Actuator + Micrometer，并暴露 Prometheus 端点：`http://localhost:8181/api/actuator/prometheus`。

自定义指标（`monitor/AiModelMetricsCollector.java`）：

| 指标 | 类型 | 标签 | 说明 |
| --- | --- | --- | --- |
| `ai_model_requests_total` | Counter | `user_id`、`app_id`、`model_name`、`status` | AI 模型请求次数 |
| `ai_model_errors_total` | Counter | `user_id`、`app_id`、`model_name`、`error_message` | AI 调用错误次数 |
| `ai_model_tokens_total` | Counter | `user_id`、`app_id`、`model_name`、`token_type` | Token 消耗（`input` / `output` / `total`） |
| `ai_model_response_duration_seconds` | Timer | `user_id`、`app_id`、`model_name` | 模型响应耗时 |

使用步骤：

1. 启动后端，确认指标端点可访问。
2. 使用仓库根目录的 `prometheus.yml` 启动 Prometheus：
   ```bash
   prometheus --config.file=prometheus.yml
   ```
   其中抓取任务 `JiGou` 的 `metrics_path` 为 `/api/actuator/prometheus`，`targets` 默认为 `host.docker.internal:8181`（Prometheus 跑在 Docker 中时使用）；若 Prometheus 直接跑在本机，请改为 `localhost:8181`。
3. Grafana 中添加 Prometheus 数据源，**数据源名称必须是 `prometheus`**（看板 JSON 中已硬编码）。
4. `Dashboards` → `New` → `Import`，粘贴 `grafana/grafana_config.json` 内容并导入；详细说明见 `grafana/README.md`。

看板包含概览指标、Token 消耗趋势与类型分布、模型调用分析、热门应用 / 活跃用户排行与错误分析。

## 测试

```bash
# 全量测试（会启动 Spring 上下文，读取 application-dev.yml）
mvn test

# 只跑不依赖外部服务的单元测试
mvn test -Dtest=CodeParserTest,ToolCallMessagePairerTest
```

注意：

- `src/test` 下多数测试是**集成测试**（`@SpringBootTest`），会真实调用大模型、读写 `tmp` 目录、使用 Chrome 截图或执行工作流，运行前必须完成配置并启动 MySQL / Redis。
- `CodeGenWorkflowTest` 会真实生成代码并构建 Vue 工程，耗时较长，且需要本机具备 `npm` 与 `mmdc`。
- 仅验证工程能否编译：`mvn -q -DskipTests compile`。

## 常见问题

**1. 启动时报 `Could not resolve placeholder ...` 或数据源连接失败**

没有生成本地配置。请按[第 3 步](#3-生成并填写本地配置)把 `application-dev.yml.example` 复制为 `application-dev.yml`，并填好数据库账号密码。

**2. AI 调用返回 401 / 鉴权失败**

`application-dev.yml` 里的 `<your-deepseek-api-key>` 等占位符没有替换，特别注意 `langchain4j.open-ai` 下的 **4 处** `api-key`。

**3. 部署时报「初始化 Chrome 浏览器失败」**

本机未安装 Chrome，或 WebDriverManager 首次下载驱动失败（需要联网）。安装 Chrome 后重试；Linux 服务器还需安装 Chrome 运行所需依赖。

**4. 封面图裂图、部署地址 404、Mermaid 架构图打不开**

这三类资源由 Nginx 托管，请检查：
- Nginx 是否已按 [Nginx 配置](#nginx-配置部署--截图--架构图) 配置并 `reload`；
- `root` / `alias` 是否指向本项目 `tmp` 下的真实目录，`alias` 末尾是否带 `/`；
- 部署地址形如 `http://localhost/{deployKey}/`，需要先完成一次部署。

**5. Vue 工程模式构建失败**

后端所在机器需要安装 Node.js 与 npm（`VueProjectBuilder` 会执行 `npm install` 与 `npm run build`，超时分别为 5 分钟和 3 分钟）。

**6. 工作流生成架构图失败，日志提示 `Mermaid CLI 执行失败`**

安装 Mermaid CLI：`npm install -g @mermaid-js/mermaid-cli`，确认终端可直接执行 `mmdc -h`。

**7. 后端接口 401 或前端请求跨域**

请通过 `npm run dev` 访问前端（使用 Vite 代理），不要直接打开 `dist/index.html`；生产环境用 Nginx 把 `/api` 与前端页面放到同源下反向代理。

**8. 端口被占用**

后端默认 `8181`（`application.yml`），前端默认 `5173`（Vite）。修改后端端口后，记得同步修改 `JiGou-frontend/vite.config.ts` 中的代理目标。

**9. `tmp` 目录生成到了别的位置**

`tmp` 基于 `System.getProperty("user.dir")` 生成，请在**项目根目录**启动后端（`java -jar` 或 IDE 运行配置的工作目录都应为项目根目录）。

**10. 版本数量超过 20 个后旧版本消失**

版本管理默认最多保留 20 个快照，超出后淘汰最旧版本；当前版本与线上正在部署的版本不会被淘汰（`AppConstant.MAX_VERSION_COUNT`）。

## 声明

- 本项目为个人学习项目，Nginx + 本地文件系统是简化后的存储方案，可按需替换为对象存储服务（如 COS / OSS）。
- `/api/workflow/*` 为演示接口，未接入鉴权与限流，请勿在生产环境直接暴露。
- 请勿将包含真实密钥的 `application-dev.yml` 提交到仓库；如密钥曾外泄，请及时到对应平台重置。
- 代码仅供学习交流使用。
