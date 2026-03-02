# 任务列表 (v1.6)

## 待办任务 (Todo)


## 进行中 (In Progress)

## 已完成 (Done)

- [x] 自由移动与锁定（DashboardController.setupDragging，实现拖拽与锁定、位置保存）
- [x] 贴边吸附（阈值 8px，靠近屏幕边缘自动贴边）
- [x] 始终置顶开关（JavaFX setAlwaysOnTop + 原生 SetWindowPos(HWND_TOPMOST|SWP_NOACTIVATE)）
- [x] 鼠标穿透开关（原生 WS_EX_TRANSPARENT|WS_EX_LAYERED + JavaFX mouseTransparent）
- [x] 托盘图标与菜单（Swing JPopupMenu 中文正常显示，包含置顶/锁定/穿透/吸附/设置/均价/退出）
- [x] 动态压缩上下布局高度（任务栏高度受限时自动压缩字体与间距）
- [x] 配置持久化（window.x/y、always_on_top、locked、click_through、snap_to_edges、font.size、lang）
- [x] 打包与开发运行脚本（package.ps1、dev-run.ps1）

## 历史版本已完成（摘要）
- v1.4：均价计算器、透明背景、单行横向模式、字体优化、停靠逻辑与打包
- v1.5：取消任务栏物理嵌入，改为上下布局与稳定定位（后续转向 1.6 方案）

- [x] **3. 增强窗口移动与锁定逻辑**
    - [x] **TaskbarLocator 升级**：获取任务栏的具体矩形区域 (Bounds)。
    - [x] **DashboardController 改造**：
        - [x] 实现 `Taskbar Mode + Locked`：固定在托盘旁，禁止拖动。
        - [x] 实现 `Taskbar Mode + Unlocked`：限制拖动范围在任务栏区域内。
        - [x] 实现 `Desktop Mode + Locked`：禁止拖动。
        - [x] 实现 `Desktop Mode + Unlocked`：全屏自由拖动。
    - [x] 确保自动停靠时不遮挡系统托盘（需要精确计算偏移量）。

- [x] **4. 版本发布准备**
    - [x] 更新 `pom.xml` 版本号至 `1.4.0`。
    - [x] 运行所有单元测试 (`mvn test`)。
    - [x] 执行打包脚本 (`scripts/package.ps1`)。
    - [x] 验证生成的 EXE 文件。
    - [x] 更新 `INITIAL.md` 和 `README.md`。
