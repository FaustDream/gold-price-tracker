# PRPs 合集（按版本）

## v1.3
- 清理与重构、崩溃修复、打包流程基线、日志路径与模块化修正。
- 参考：v1.3-cleanup-and-refactor.md、v1.3-* 系列。

## v1.4
- 功能更新与优化：透明背景、任务栏停靠/桌面模式的移动与锁定、均价计算器、单行横向模式、字体优化。
- 稳定性修复：Z-Order 置顶、任务栏覆盖问题、位置微调与日志增强。
- 参考：v1.4-feature-implementation.md、v1.4-* 系列。

## v1.5
- 路线调整：取消物理嵌入（SetParent）方案，避免 Win11 下坐标/焦点冲突。
- 方案蓝图：
  - v1.5-blueprint：稳定定位、上下布局、无 toFront 抢焦点。
  - v1.5-final-blueprint：最终建议采用原生 AppBar（C#/.NET）事件驱动驻留，Java 仅提供数据与设置服务。

## v1.6
- 蓝图：自由驻留、锁定/置顶/穿透、托盘操控，保持上下布局与动态适配。
- 已完成（实现要点）：
  - 自由移动与锁定、贴边吸附。
  - 始终置顶（JavaFX + 原生 SetWindowPos）、鼠标穿透（原生 WS_EX_TRANSPARENT|WS_EX_LAYERED + JavaFX mouseTransparent）。
  - 托盘图标与中文菜单（Swing JPopupMenu），开关即时生效并持久化。
  - 上下布局的动态压缩，保障任务栏高度受限时不遮挡。
- 待办：
  - 托盘菜单新增“字体大小/语言”子菜单，联动 /settings。
  - 原生 AppBar 评估与骨架搭建（Win11 稳定驻留）。
