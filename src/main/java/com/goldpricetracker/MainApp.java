package com.goldpricetracker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 加载 FXML
        URL fxmlUrl = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlUrl == null) {
            System.err.println("无法找到 Dashboard.fxml");
            System.exit(1);
        }
        
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        
        // 创建一个隐藏的 Utility Stage 作为 owner，以隐藏任务栏图标
        Stage utilityStage = new Stage(StageStyle.UTILITY);
        utilityStage.setOpacity(0);
        utilityStage.setWidth(0);
        utilityStage.setHeight(0);
        utilityStage.show();
        
        // 创建一个新的 Stage 作为主窗口，而不是使用 primaryStage
        // 因为 primaryStage 的 owner 无法被修改 (IllegalStateException)
        Stage mainStage = new Stage();
        mainStage.initOwner(utilityStage);
        mainStage.initStyle(StageStyle.TRANSPARENT);
        mainStage.setAlwaysOnTop(true);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT); // 场景透明
        
        // 获取 Controller 并传递 Stage
        com.goldpricetracker.frontend.DashboardController controller = loader.getController();
        controller.setStage(mainStage);
        
        mainStage.setScene(scene);
        mainStage.show();
        
        // 关闭主窗口时同时关闭 utilityStage 以便退出程序
        mainStage.setOnHidden(e -> utilityStage.close());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
