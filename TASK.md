# Gold Price Tracker 任务清单 (v1.3)

## 🎯 当前目标
清理项目残留，规范化代码注释，并发布 v1.3 版本。

## 📋 任务列表

### 1. 项目清理与初始化 [已完成]
- [x] 删除 `build/` 目录 (Python 遗留)
- [x] 删除 `gold-backend.spec` (Python 遗留)
- [x] 删除 `dependency-reduced-pom.xml` (Maven 遗留)
- [x] 创建 `chats/` 目录用于对话管理
- [x] 初始化 `TASK.md` 任务追踪

### 2. 版本管理 [已完成]
- [x] 将 `pom.xml` 中的版本号从 1.2.0 升级为 1.3.0
- [x] 在 `release/1.3/` 下打包发布可执行文件 (JAR & EXE)
- [x] 使用 `jpackage` 生成免安装 EXE 软件包
- [x] 确保 `.gitignore` 已忽略 `release/` 文件夹
- [x] 将所有更改提交并推送至 GitHub 远程仓库 (origin/main)

### 3. 代码规范化 (根据 CLAUDE.md) [已完成]
- [x] 为 `PriceService.java` 添加详细的中文步骤注释和 Javadoc
- [x] 为 `MainApp.java` 添加详细的中文步骤注释和 Javadoc
- [x] 在 `CLAUDE.md` 中新增 `INITIAL.md` 任务追踪规则
- [x] 在 `INITIAL.md` 中标识已完成的任务

### 4. 验证与发布 [已完成]
- [x] 运行 `mvn clean package` 并复制依赖到 `target/libs`
- [x] 引入 `module-info.java` 将项目模块化，解决 JavaFX 运行时冲突
- [x] 修复 JavaFX 渲染/事件导致的闪退问题 (移除 Shade 插件，改用原生模块打包)
- [x] 修复 FXML 布局不一致导致的启动闪退问题 (VBox -> HBox)
- [x] 再次修复布局问题：将价格恢复为上下垂直排列 (HBox -> VBox)
- [x] 增加自动停靠任务栏功能，默认开启
- [x] 优化拖拽逻辑：未锁定时允许手动拖拽，并自动解除任务栏停靠模式
- [x] 优化日志记录逻辑：日志文件现在自动生成在软件 EXE 同级目录下
- [x] 清理 release 目录中的旧版本文件
- [x] 使用 `jpackage` 结合 `--module-path` 和 `--add-opens` 彻底解决 `MouseEvent$Flags` 闪退
- [x] 验证 `release/1.3/gold-price-tracker-1.3.0.exe/` 运行正常
- [x] 运行 `mvn test` 验证单元测试通过

---
*创建日期: 2026-02-28*
*最后更新: 2026-02-28*
