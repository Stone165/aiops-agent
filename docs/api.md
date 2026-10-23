# AIOps Agent API

基础地址：`http://localhost:8080`
请求/响应类型：`application/json`

## 约定

### 分页响应

`GET /api/incidents/page`、`/api/incidents/search`、`/api/diagnosis/records` 返回：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 0
}
```

- `pageNum` 从 1 开始。
- `pageSize` 最大 100。

### 错误响应

```json
{
  "status": 400,
  "message": "工单标题不能为空",
  "timestamp": "2026-10-22T14:00:00Z",
  "path": "/api/incidents"
}
```

| 状态码 | 场景 |
|---|---|
| 400 | 参数校验失败、非法分页、非法状态/严重程度 |
| 404 | 工单/诊断记录不存在 |
| 405 | 请求方法不支持 |
| 500 | 未预期服务器异常 |
| 502 | AI 上游返回错误或达到最大工具轮次 |
| 503 | 数据库不可用、未配置 AI Key、AI 余额不足/限流 |
| 504 | AI 服务连接超时 |

## 健康检查

### GET /api/health

```json
{
  "code": 200,
  "status": "UP",
  "message": "智能运维平台运行正常",
  "service": "aiops-agent",
  "time": "2026-10-22T22:00:00"
}
```

## 工单

### POST /api/incidents

请求：

```json
{
  "title": "业务接口超时",
  "description": "接口平均响应时间超过 5 秒",
  "deviceId": "server-001",
  "severity": "HIGH"
}
```

- `title`、`description` 必填。
- `severity` 可选：`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`，默认 `MEDIUM`。
- 创建后状态固定为 `OPEN`。

响应 `201`：

```json
{
  "id": 1,
  "title": "业务接口超时",
  "description": "接口平均响应时间超过 5 秒",
  "deviceId": "server-001",
  "severity": "HIGH",
  "status": "OPEN",
  "rootCause": null,
  "solution": null,
  "createdAt": "2026-10-22T22:00:00",
  "updatedAt": "2026-10-22T22:00:00"
}
```

### GET /api/incidents

返回全部工单，按创建时间倒序。兼容旧前端，数据量大时请使用分页接口。

### GET /api/incidents/{id}

返回工单详情；不存在时 `404`。

### GET /api/incidents/stats

```json
{
  "total": 12,
  "open": 4,
  "inProgress": 2,
  "resolved": 5,
  "closed": 1,
  "critical": 2,
  "high": 3
}
```

### GET /api/incidents/page

参数：`pageNum`（默认 1）、`pageSize`（默认 10，最大 100）。

### GET /api/incidents/search

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNum` | 否 | 默认 1 |
| `pageSize` | 否 | 默认 10，最大 100 |
| `status` | 否 | `OPEN`/`IN_PROGRESS`/`RESOLVED`/`CLOSED` |
| `severity` | 否 | `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` |
| `keyword` | 否 | 匹配标题、描述、设备编号，最长 100 |

### PUT /api/incidents/{id}/status

```json
{ "status": "IN_PROGRESS" }
```

响应：

```json
{
  "message": "工单状态更新成功",
  "incident": { "id": 1, "status": "IN_PROGRESS" }
}
```

### PUT /api/incidents/{id}/resolution

```json
{
  "rootCause": "数据库连接池耗尽",
  "solution": "扩容连接池并增加监控",
  "status": "RESOLVED"
}
```

至少提交 `rootCause`、`solution`、`status` 中的一个字段。

### DELETE /api/incidents/{id}

成功返回 `204 No Content`；不存在返回 `404`。

## 诊断

### POST /api/diagnosis/agent

AI 模式，调用 DeepSeek Function Calling，成功后自动归档。

请求：

```json
{
  "deviceId": "server-001",
  "description": "业务接口无法访问，怀疑服务停止运行",
  "snapshotMode": "LOCAL"
}
```

`snapshotMode` 可选值：

- `LOCAL`（默认）：诊断开始时采集本机实时快照，AI 工具返回本机指标。
- `TEXT`：不读取本机指标，只根据 `description` 文本分析。从工单进入诊断页时
  前端默认发送 `TEXT`。

响应（`LOCAL` 模式）：

```json
{
  "deviceId": "local-machine",
  "description": "本机运行状态检查",
  "answer": "根据本机快照，磁盘使用率偏高……",
  "findings": [],
  "suggestions": [],
  "toolCallCount": 1,
  "toolCalls": [
    {
      "tool": "check_system_metrics",
      "arguments": { "deviceId": "local-machine" },
      "result": {
        "tool": "system_metrics",
        "deviceId": "Ciallo",
        "cpuUsage": 3.5,
        "memoryUsage": 72.7,
        "diskUsage": 99.7,
        "cpuHigh": false,
        "memoryHigh": false,
        "diskHigh": true,
        "simulated": false
      }
    }
  ],
  "simulated": false,
  "recordId": 3,
  "createdAt": "2026-10-23T11:49:00",
  "monitorSnapshot": {
    "deviceId": "Ciallo",
    "metrics": {
      "cpuUsage": 3.5,
      "memoryUsage": 72.7,
      "diskUsage": 99.7
    },
    "network": {
      "targetHost": "www.baidu.com",
      "targetPort": 443,
      "reachable": true,
      "latencyMs": 40
    },
    "service": {
      "serviceName": "MySQL",
      "host": "127.0.0.1",
      "port": 3306,
      "running": true
    },
    "simulated": false,
    "collectedAt": "2026-10-23T11:49:00"
  }
}
```

`TEXT` 模式响应示例：

```json
{
  "deviceId": "server-006",
  "description": "CPU 92%，内存 88%，服务未运行",
  "answer": "文本诊断结论：……",
  "findings": ["描述中 CPU 使用率为 92%"],
  "suggestions": ["检查高负载进程"],
  "toolCallCount": 0,
  "toolCalls": [],
  "simulated": false,
  "recordId": 9,
  "createdAt": "2026-10-23T14:55:00"
}
```

- `TEXT` 模式不调用本机工具，`toolCalls` 为空，响应中没有 `monitorSnapshot`。
- 未配置 `DEEPSEEK_API_KEY` 时返回 `503`。
- 数据库保存失败时仍返回结论，`recordId` 为 `null`。
- `LOCAL` 模式下模型只能调用白名单工具；工具数据都来自后端所在机器，不会访问请求中指定的其他设备。
- `LOCAL` 模式下诊断开始时自动采集一份快照；`monitorSnapshot` 即本次分析使用的快照。

### POST /api/diagnosis/rule

确定性规则诊断，不调用大模型，不需要 API Key。请求结构同 `/agent`。
响应额外包含 `findings` 和 `suggestions`。

- `LOCAL` 模式直接使用本机实时快照。
- `TEXT` 模式解析描述中的 CPU/内存/磁盘/延迟百分比和服务未运行关键词，
  返回 `toolCalls: []`、无 `monitorSnapshot` 的文本诊断结论。

### POST /api/diagnosis/run

兼容旧接口，等价于 `/api/diagnosis/rule`。

### GET /api/diagnosis/records

参数：`pageNum`、`pageSize`、`deviceId`（可选）。按创建时间倒序。

```json
{
  "records": [
    {
      "id": 3,
      "deviceId": "server-001",
      "description": "业务接口无法访问",
      "answer": "诊断结论……",
      "toolCallCount": 2,
      "toolCalls": [],
      "monitorSnapshot": {
        "deviceId": "Ciallo",
        "metrics": { "cpuUsage": 3.5, "memoryUsage": 72.7, "diskUsage": 99.7 },
        "network": { "reachable": true, "latencyMs": 40 },
        "service": { "serviceName": "MySQL", "running": true },
        "simulated": false,
        "collectedAt": "2026-10-23T11:49:00"
      },
      "simulated": false,
      "createdAt": "2026-10-23T11:49:00"
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 1
}
```

### GET /api/diagnosis/records/{id}

返回单条诊断记录；不存在时 `404`。

## 监控

### GET /api/monitor/local

返回后端所在机器的实时快照。前端每 3 秒轮询一次。

```json
{
  "deviceId": "Ciallo",
  "metrics": {
    "tool": "system_metrics",
    "deviceId": "Ciallo",
    "cpuUsage": 3.5,
    "memoryUsage": 72.7,
    "diskUsage": 99.7,
    "totalMemoryBytes": 17088585728,
    "freeMemoryBytes": 4669636608,
    "cpuHigh": false,
    "memoryHigh": false,
    "diskHigh": true,
    "availableProcessors": 16,
    "osName": "Windows 11",
    "osArch": "amd64",
    "simulated": false
  },
  "network": {
    "tool": "network_check",
    "deviceId": "Ciallo",
    "targetHost": "www.baidu.com",
    "targetPort": 443,
    "reachable": true,
    "latencyMs": 40,
    "simulated": false
  },
  "service": {
    "tool": "service_status",
    "deviceId": "Ciallo",
    "serviceName": "MySQL",
    "host": "127.0.0.1",
    "port": 3306,
    "running": true,
    "simulated": false
  },
  "simulated": false,
  "collectedAt": "2026-10-23T11:49:00"
}
```

数据来自本机 JDK 管理接口、磁盘文件系统和 TCP 探测。网络/本地服务目标可通过
`MONITOR_NETWORK_HOST`、`MONITOR_NETWORK_PORT`、`MONITOR_SERVICE_HOST`、
`MONITOR_SERVICE_PORT` 配置。旧路径 `GET /api/monitor/{deviceId}` 仍可用，但当前
始终返回本机快照。

## 示例

```bash
curl http://localhost:8080/api/health

curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"title":"接口超时","description":"响应超过 5 秒","deviceId":"server-001","severity":"HIGH"}'

curl -X POST http://localhost:8080/api/diagnosis/rule \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"server-001","description":"业务接口无法访问"}'
```
