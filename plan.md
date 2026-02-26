# Gold Price Tracker v2.0 - 重构计划

## 1. 项目简介
本项目是一个基于 Tauri v2 (Rust) + Vue 3 + Python (FastAPI) 构建的跨平台桌面应用，用于实时监控国际金价并换算国内人民币价格。项目遵循极简设计原则，**不包含任何本地数据持久化功能**（无 SQLite、无 CSV/Excel 导出），专注于提供实时、准确的市场数据。

## 2. 核心架构变更
*   **语言规范**：全中文文档与注释。
*   **去存储化**：彻底移除 SQLite 数据库、Pandas/Excel 依赖及相关代码。
*   **数据源重构**：
    *   **移除**：原有国内金价接口（Sina/Kitco 国内版等）。
    *   **保留**：国际金价接口（WebSocket/轮询，1秒/次）。
    *   **新增**：实时汇率接口（央行/Open-Exchange-Rates）。
    *   **计算逻辑**：前端/后端实时计算 `国内金价 = 国际金价 / 31.1034768 * 汇率`。

## 3. 技术栈
*   **Shell**: Tauri v2 (Rust) - 负责系统托盘、窗口管理及 Sidecar 进程管理。
*   **Frontend**: Vue 3 + TypeScript + Naive UI - 负责数据展示、图表绘制（ECharts）及设置交互。
*   **Backend (Sidecar)**: Python 3.10+ + FastAPI - 作为数据代理层，负责与第三方 API 通信并处理 WebSocket 连接。

## 4. 目录结构
```
gold-price-tracker/
├── backend-python/       # Python FastAPI 后端
│   ├── app/
│   │   ├── api/          # API 路由
│   │   ├── core/         # 配置与日志
│   │   ├── models/       # Pydantic 模型（仅用于数据传输）
│   │   ├── services/     # 价格与汇率服务（无存储）
│   │   └── main.py       # 入口文件
│   ├── requirements.txt  # 依赖列表（已精简）
│   └── tests/            # 单元测试
├── frontend-vue/         # Vue 3 前端
│   ├── src/
│   │   ├── components/   # UI 组件
│   │   ├── utils/        # 计算工具函数
│   │   └── App.vue
│   └── scripts/          # 环境检测脚本
├── src-tauri/            # Tauri 配置 (Rust)
│   ├── src/
│   │   └── main.rs       # 托盘逻辑
│   └── tauri.conf.json
└── docs/                 # 文档
    └── packaging/        # 打包教程
```

## 5. 开发流程
1.  **清理代码**：移除旧版存储逻辑与冗余依赖。
2.  **后端实现**：编写 `PriceService` (国际金价) 和 `ExchangeRateService` (汇率)。
3.  **核心逻辑**：实现 `calculateCNYPrice` 并通过单元测试。
4.  **前端对接**：更新 UI，展示实时计算结果，移除导出功能。
5.  **环境检测**：编写 `scripts/check-env.js`。
6.  **CI/CD**：配置 GitHub Actions 自动打包。
7.  **文档编写**：完成用户手册与打包教程。
