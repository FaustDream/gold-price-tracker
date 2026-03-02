# 黄金价格追踪器 (Gold Price Tracker) · v1.6

一个极简风格的 Windows 桌面悬浮窗小工具，用于实时追踪国内和国际金价。

---

## ✨ 核心功能

*   **实时数据**：
    *   **国内金价**：直连上海黄金交易所 (Au T+D) 实时行情。
    *   **国际金价**：获取实时伦敦金价格。
    *   **智能联动**：休市期间自动根据国际金价和汇率估算国内价格，实现 24 小时动态更新。
*   **极简悬浮窗**：
    *   **无边框设计**：完美融入桌面环境。
    *   **透明背景**：默认透明，仅显示价格文字，不遮挡壁纸。
    *   **强制置顶**：顽强地驻留在屏幕最上层，防止被任务栏遮挡。
    *   **位置记忆**：自动保存窗口位置，重启后还原。
*   **个性化定制**：
    *   **外观设置**：可自定义字体颜色、背景颜色（支持透明度）、字体大小。
    *   **智能显隐**：可设置价格范围，仅在价格满足条件时显示。
*   **价格预警**：
    *   支持设置国内/国际金价的高低阈值。
    *   触发预警时弹出桌面通知（含防骚扰冷却机制）。
*   **安全稳定**：
    *   内置崩溃捕获与日志记录系统。
    *   绿色免安装，解压即用。

---

## � 版本与使用

* 最新版本：v1.6.0（release/1.6/gold-price-tracker/）
* 托盘菜单：置顶/锁定/穿透/贴边吸附、更多设置、均价计算器、退出
* 已移除“智能显隐”功能（窗口始终显示）

---

## 🚀 快速开始

### 用户使用
1. 解压后运行 gold-price-tracker 应用
2. 在任务栏托盘的金色图标上右键，使用中文菜单：
   - 始终置顶、锁定位置、鼠标穿透、贴边吸附、更多设置、均价计算器、退出

### 开发者入门
本项目提供了详细的入门文档，即使是 Java 小白也能轻松上手。

*   📄 **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)**: **强烈推荐阅读！** 包含环境搭建、核心原理解析、手把手复现教程等。

#### 环境要求
*   **JDK**: 17+ (推荐 Microsoft OpenJDK 21)
*   **Maven**: 3.8+
*   **IDE**: IntelliJ IDEA 或 VS Code

#### 开发/打包
* 开发运行（无需打包）：`powershell -ExecutionPolicy Bypass -File scripts/dev-run.ps1`
* 一键打包：`powershell -ExecutionPolicy Bypass -File scripts/package.ps1`

---

## 📂 项目结构

```
gold-price-tracker/
├── src/
│   ├── main/
│   │   ├── java/com/goldpricetracker/
│   │   │   ├── backend/          # 后端逻辑 (数据获取、计算、启动/样式/服务)
│   │   │   │   ├── PriceDataServer.java   # 本地 /price 与 /settings 服务
│   │   │   │   ├── WindowStyleHelper.java # 置顶与穿透的原生样式封装 (JNA)
│   │   │   ├── frontend/         # 前端界面 (控制器、设置窗口)
│   │   │   ├── Launcher.java     # 程序启动器 (错误捕获、日志)
│   │   │   └── MainApp.java      # JavaFX 入口 (窗口初始化)
│   │   └── resources/
│   │       └── fxml/             # 界面布局文件 (.fxml)
│   └── test/                     # 单元测试
├── scripts/                      # 开发运行与打包脚本
│   ├── dev-run.ps1               # 无需打包直接运行
│   └── package.ps1               # 一键打包
├── release/                      # 打包后的发布文件
├── pom.xml                       # Maven 配置文件
├── README.md                     # 项目说明文档
└── DEVELOPMENT_GUIDE.md          # 开发者入门指南
```

---

## ⚙️ 常见问题 (FAQ)

**Q: 托盘菜单中文为什么显示为方框？**
A: 旧版 AWT PopupMenu 在部分 Win11 环境下无法正确选择中文字体。v1.6 改用 Swing JPopupMenu 并动态选择可用中文字体，已修复。

**Q: 如何完全隐藏背景？**
A: 在“设置”中，将“背景颜色”设置为完全透明（Alpha通道为0），或使用默认的 1% 透明度（推荐，方便右键点击）。

**Q: 遇到闪退怎么办？**
A: 程序目录下会生成 `gold_price_tracker.log` 文件，请查看其中的报错信息。

---

## 🛠️ 技术栈

* 语言: Java 17
* UI 框架: JavaFX 21
* 网络库: OkHttp 4、Jackson
* 原生接口: JNA (置顶/穿透)
* 本地服务: jdk.httpserver (PriceDataServer)
* 构建工具: Maven
* 打包工具: jpackage
