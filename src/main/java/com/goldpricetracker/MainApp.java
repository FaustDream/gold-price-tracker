package com.goldpricetracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.net.URL;

/**
 * 应用程序入口类 (JavaFX)
 * 继承自 Application，负责启动 JavaFX 运行时环境。
 * 
 * 主要功能：
 * 1. 加载主界面的 FXML 文件。
 * 2. 配置主窗口 (Stage) 的属性：透明、无边框、置顶。
 * 3. 实现"隐藏任务栏图标"的技巧 (通过 Utility Stage)。
 */
public class MainApp extends Application {

    /**
     * JavaFX 程序的启动入口
     * @param primaryStage 系统自动创建的主舞台 (这里我们不直接使用它显示内容)
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. 加载 FXML 布局文件
        // FXML 是一种 XML 格式，用于定义界面结构 (类似 HTML)
        URL fxmlUrl = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlUrl == null) {
            System.err.println("无法找到 Dashboard.fxml");
            System.exit(1);
        }
        
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        
        // 2. 隐藏任务栏图标的黑科技
        // 创建一个隐藏的 Utility 类型 Stage 作为 owner
        // StageStyle.UTILITY 类型的窗口通常不会在任务栏显示图标
        Stage utilityStage = new Stage(StageStyle.UTILITY);
        utilityStage.setOpacity(0); // 完全透明
        utilityStage.setWidth(0);   // 宽度为 0
        utilityStage.setHeight(0);  // 高度为 0
        utilityStage.show();
        
        // 3. 创建真正的主窗口
        // 不使用传入的 primaryStage，因为它的 owner 属性一旦初始化就很难修改
        Stage mainStage = new Stage();
        mainStage.initOwner(utilityStage); // 设置归属关系
        mainStage.initStyle(StageStyle.TRANSPARENT); // 设置为透明风格 (无标题栏、无边框)
        mainStage.setAlwaysOnTop(true); // 默认置顶
        
        // 4. 配置场景 (Scene)
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT); // 设置场景背景透明，配合 FXML 中的样式实现圆角效果
        
        // 5. 获取控制器并传递 Stage 对象
        // 这样控制器内部就可以操作窗口 (如拖动、最小化等)
        com.goldpricetracker.frontend.DashboardController controller = loader.getController();
        controller.setStage(mainStage);
        
        // 6. 显示窗口
        mainStage.setScene(scene);
        mainStage.show();
        
        // 7. 退出逻辑
        // 当主窗口关闭时，同时关闭 utilityStage，确保程序完全退出
        mainStage.setOnHidden(e -> utilityStage.close());
    }

    /**
     * 标准 Java 程序入口
     * 调用 launch() 启动 JavaFX 生命周期
     */
    public static void main(String[] args) {
        launch(args);
    }
}
