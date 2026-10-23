# AIOps Agent 前端

Vue 3 + Vite 前端，提供运维总览、故障工单、AI 故障诊断和设备监控四个页面。

设备监控页每 3 秒轮询 `GET /api/monitor/local`，展示本机实时 CPU、内存、磁盘、
网络和本地服务状态。点击“发起故障诊断”会跳转到诊断页并自动开始规则/AI 分析，
后端会在诊断开始时捕获一份本机快照。

诊断页支持两种数据来源：

- **本机快照**：读取后端所在机器的实时指标，适用于监控页发起的诊断。
- **仅分析描述**：不读取本机指标，只分析工单描述中的 CPU/内存/磁盘/延迟等信息。
  从工单进入诊断页时默认使用该模式。

## 启动

```bash
npm install
npm run dev
```

默认地址：http://localhost:5173

开发环境下 `/api` 由 Vite 代理到 `http://localhost:8080`，配置见 `vite.config.js`。

## 构建

```bash
npm run build
npm run preview
```

如果前端独立部署并直连后端，复制 `.env.example` 为 `.env`，把 `VITE_API_BASE_URL`
改为后端地址（例如 `http://localhost:8080/api`），并确认后端 `CORS_ALLOWED_ORIGINS`
包含前端来源。

## 目录

```text
src/
  api/          后端接口封装
  components/   通用组件
  composables/  全局通知等组合式函数
  utils/        格式化工具
  views/        四个页面视图
  App.vue       应用壳层与导航
  style.css     全局样式
```
