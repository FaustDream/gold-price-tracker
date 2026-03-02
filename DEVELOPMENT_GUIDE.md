# GoldPriceTracker 开发指南 · v1.6

本文档旨在帮助编程新手快速理解、运行并复刻 `GoldPriceTracker` (黄金价格追踪器) 项目。

---

## 1. 项目简介（最新版本特性）

这是一个基于 **JavaFX** 开发的 Windows 桌面小工具。
核心功能：实时显示国际金价（伦敦金）和国内金价（上海金），支持窗口置顶、透明背景、托盘操控（置顶/锁定/穿透/吸附），以及价格预警。

### 技术栈 (Tech Stack)
*   **语言**: Java 17+
*   **UI 框架**: JavaFX
*   **构建工具**: Maven
*   **网络请求**: OkHttp3
*   **JSON 解析**: Jackson
*   **原生接口**: JNA（置顶与穿透）
*   **本地服务**: jdk.httpserver（PriceDataServer）

---

## 2. 快速开始（如何运行）

### 环境准备
1.  **安装 JDK 17+**: 推荐下载 Microsoft OpenJDK 或 Oracle JDK。
2.  **安装 Maven**: 下载 Apache Maven 并配置环境变量。
3.  **IDE**: 推荐使用 IntelliJ IDEA 或 VS Code (安装 Java 插件包)。

### 运行步骤
1.  **开发运行（无需打包）**:
    ```powershell
    powershell -ExecutionPolicy Bypass -File scripts/dev-run.ps1
    ```
2.  **一键打包**:
    ```powershell
    powershell -ExecutionPolicy Bypass -File scripts/package.ps1
    ```

---

## 3. 核心功能实现原理 (小白必读)

### 3.1 怎么获取金价？(网络请求)
我们通过 **HTTP 请求** 访问新浪财经的公开接口。这就像你在浏览器地址栏输入网址一样，只不过代码帮我们自动做了。

*   **代码位置**: `src/main/java/com/goldpricetracker/backend/PriceService.java`
*   **关键代码**:
    ```java
    // 构造请求
    Request request = new Request.Builder()
        .url("http://hq.sinajs.cn/list=hf_XAU,gds_AUTD,USDCNY")
        // 必须带上 Referer 头，告诉服务器我是从新浪网页来的，否则会被拒绝访问 (403 Forbidden)
        .header("Referer", "https://finance.sina.com.cn/") 
        .build();
    ```

### 3.2 界面是怎么画出来的？(JavaFX FXML)
我们使用 **FXML** 文件来描述界面长什么样，就像写 HTML 网页一样。

*   **布局文件**: `src/main/resources/fxml/Dashboard.fxml`
*   **结构**:
    *   `VBox`: 一个垂直排列的盒子，作为最外层容器。
    *   `Label`: 用来显示文字的标签（比如“国内金价：450.50”）。

### 3.3 透明窗口与托盘菜单（中文不乱码）
*   透明窗口：`StageStyle.TRANSPARENT` + `Scene.setFill(Color.TRANSPARENT)`。
*   托盘菜单：使用 **Swing JPopupMenu** 替代 AWT PopupMenu，动态选择中文字体（优先 Microsoft YaHei UI/微软雅黑/宋体/SimSun 等），避免中文显示为方框。

### 3.4 为什么休市了还能更新价格？（业务逻辑）
国内黄金交易所（上海金）在晚上和周末会休市，价格不动。为了让你时刻看到参考价，我们做了一个自动切换逻辑：

*   **开市时**: 直接显示交易所的报价。
*   **休市时**: 自动切换为“计算模式”。
    *   **公式**: `(国际金价 USD/oz ÷ 31.1034768) × 汇率 USD/CNY`
    *   **代码位置**: `PriceService.java` 中的 `validateAndFixDomesticPrice` 方法。

### 3.5 置顶与穿透如何实现？（原生样式）
*   置顶：`Stage.setAlwaysOnTop(true)` + `SetWindowPos(HWND_TOPMOST | SWP_NOACTIVATE)`
*   穿透：`WS_EX_TRANSPARENT | WS_EX_LAYERED`（JNA 设置扩展样式）+ `Node.setMouseTransparent(true)` 回退
*   封装位置：`com.goldpricetracker.backend.WindowStyleHelper`

## 4. 配置文件如何工作？

项目使用标准的 Java `.properties` 文件来保存设置（如颜色、字体大小）。

*   **文件位置**: 运行目录下生成的 `gold_tracker_config.properties`。
*   **键位（v1.6）**：
    - `window.x` / `window.y`：位置保存
    - `window.always_on_top` / `window.locked` / `window.click_through`
    - `snap_to_edges` / `font.size` / `lang` / `autostart`
*   **读取配置**:
    ```java
    Properties config = new Properties();
    config.load(new FileInputStream("config.properties"));
    String color = config.getProperty("color.bg", "rgba(0,0,0,0.5)"); // 第二个参数是默认值
    ```
*   **保存配置**:
    ```java
    config.setProperty("color.bg", "rgba(255,0,0,1)");
    config.store(new FileOutputStream("config.properties"), null);
    ```

---

## 5. 如何复现与举一反三

### 5.1 复现步骤 (手把手)
1.  **新建 Maven 项目**: 在 IDE 中新建一个 Maven 项目。
2.  **添加依赖**: 在 `pom.xml` 中添加 `javafx-controls`, `javafx-fxml`, `okhttp`, `jackson` 等依赖（直接复制本项目的 pom.xml 即可）。
3.  **创建目录结构**: 按照 `src/main/java/com/yourname/...` 创建包。
4.  **复制 FXML**: 将 `Dashboard.fxml` 放入 `src/main/resources/fxml/`。
5.  **编写 Controller**: 创建 `DashboardController.java`，把界面控件和代码绑定起来。
6.  **编写 Main**: 创建 `MainApp.java` 启动 JavaFX。

### 5.2 举一反三 (做点别的)
学会了这个框架，你可以轻松制作其他桌面小工具：

*   **股票盯盘助手**: 把 API 地址换成股票接口 (如新浪股票 `list=sh600519`)，解析逻辑改一下，界面逻辑保持不变。
*   **加密货币监视器**: 把 API 换成 Binance 或 CoinGecko 的 API，解析 JSON 数据。
*   **天气小组件**: 找一个免费的天气 API，显示当前温度和天气图标。

---

## 6. 常见问题（FAQ）

*   Q: 托盘中文菜单显示为方框？
    *   A: v1.6 改用 Swing JPopupMenu 并动态选择中文字体，确保中文正常显示。
*   Q: 置顶或穿透不起作用？
    *   A: 确认托盘菜单的“始终置顶”“鼠标穿透”已勾选；日志中查看 WindowStyleHelper 操作结果。
*   Q: 打包后双击没反应？
    *   A: 查看 `gold_price_tracker.log` 日志信息；确保使用 `scripts/package.ps1` 生成的目录运行。

---

希望这份指南能帮你快速入门！Coding is fun! 🚀
