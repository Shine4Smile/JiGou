# AI 模型监控看板说明

## 1. 依赖前提

1. 应用已集成 Spring Boot Actuator 与 Micrometer，并暴露了 Prometheus 指标端点（例如：`/actuator/prometheus`）。
2. Prometheus 已成功抓取到该应用的指标数据。
3. Grafana 中已配置数据源，且 **数据源名称必须命名为 `prometheus`**（因为 JSON 文件中的查询已默认硬编码了该名称）。

## 2. 导入方法

1. 登录 Grafana，点击左侧菜单 `Dashboards` -> `New` -> `Import`。
2. 将 `ai-model-monitoring.json` 文件内容粘贴到输入框，或者直接上传该文件。
3. 在弹出的页面中，确认数据源选择无误，点击 `Import` 即可。

## 3. 核心指标与标签说明

本看板依赖以下自定义指标进行展示：

- `ai_model_requests_total`：大模型请求总数（支持按 `status="success"` 筛选）
- `ai_model_tokens_total`：Token 消耗总量（区分 `token_type="input"`, `token_type="output"` 及 `token_type="total"`）
- `ai_model_response_duration_seconds`：模型响应耗时（基于 `_sum` 和 `_count` 计算平均值）
- `ai_model_errors_total`：调用错误总数

**看板筛选变量依赖以下标签：**

- `model_name`：模型名称
- `app_id`：应用 ID
- `user_id`：用户 ID

## 4. 看板模块概览

- **概览指标**：总请求数、总 Token、平均耗时、错误数。
- **Token 消耗分析**：累积趋势（堆叠图）、Token 类型分布（环形图）。
- **模型调用分析**：调用次数趋势（5分钟增量）、模型平均响应时间。
- **排行榜**：热门应用调用量/Token 消耗量、活跃用户调用量/Token 消耗量（表格内嵌进度条）。
- **错误分析**：错误率趋势、错误类型分布。