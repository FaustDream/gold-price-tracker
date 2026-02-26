package com.goldpricetracker.frontend;

import com.goldpricetracker.backend.PriceService;
import com.goldpricetracker.backend.StartupManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 主界面控制器：负责处理 UI 逻辑、数据更新和用户交互。
 * 
 * 主要职责：
 * 1. 初始化界面样式（透明背景、字体颜色）。
 * 2. 定时调用 PriceService 获取最新价格并更新到界面。
 * 3. 处理窗口拖动、右键菜单、设置窗口弹出。
 * 4. 实现价格预警和智能显隐功能。
 */
public class DashboardController {

    // FXML 绑定的 UI 控件
    @FXML private VBox rootBox; // 根容器，用于设置背景和拖动事件
    @FXML private Label domesticPriceLabel; // 显示国内金价
    @FXML private Label internationalPriceLabel; // 显示国际金价

    // 后端服务实例
    private final PriceService priceService = new PriceService();
    // 定时器，用于周期性刷新数据
    private final Timer timer = new Timer(true);
    // 配置属性，用于存储用户设置 (颜色、阈值等)
    private Properties config = new Properties();
    
    // 窗口拖动相关的坐标偏移量
    private double xOffset = 0;
    private double yOffset = 0;
    // 窗口位置锁定标志
    private boolean isLocked = false;
    // 配置文件路径
    private static final String CONFIG_FILE = "gold_tracker_config.properties";
    // 当前窗口的 Stage 对象
    private Stage stage;
    
    // 预警通知的冷却时间记录，防止频繁弹窗
    private Map<String, Long> lastAlertTime = new HashMap<>();
    private static final long ALERT_COOLDOWN = 10 * 60 * 1000; // 10分钟

    /**
     * 初始化方法：FXML 加载完成后自动调用
     */
    @FXML
    public void initialize() {
        // 1. 加载配置文件
        loadConfig();
        // 2. 应用用户设置的样式
        applySettings();
        
        // 3. 设置默认样式 (半透明黑色背景)
        rootBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 5; -fx-padding: 2;");
        String commonStyle = "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 11px; -fx-font-weight: bold;";
        domesticPriceLabel.setStyle(commonStyle + " -fx-text-fill: #FFD700;"); // 金色
        internationalPriceLabel.setStyle(commonStyle + " -fx-text-fill: #FFFFFF;"); // 白色
        
        // 4. 初始化功能模块
        setupWindowDragging(); // 允许窗口拖动
        setupContextMenu();    // 设置右键菜单
        startDataPolling();    // 开始定时获取数据
    }

    /**
     * 注入 Stage 对象，用于控制窗口位置和置顶状态
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        loadWindowPosition(); // 恢复上次关闭时的位置
        // 窗口关闭时保存位置
        stage.setOnCloseRequest(event -> saveWindowPosition());
    }

    // 从文件加载配置
    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
        } catch (IOException ignored) {}
    }

    // 应用配置中的样式设置 (背景色、字体大小等)
    public void applySettings() {
        String bg = config.getProperty("color.bg", "rgba(0,0,0,0.01)");
        String domesticColor = config.getProperty("color.domestic", "#FFD700");
        String internationalColor = config.getProperty("color.international", "#FFFFFF");
        String fontSize = config.getProperty("font.size", "12");

        rootBox.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 5; -fx-padding: 2;", bg));
        
        String fontStyle = String.format("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: %spx; -fx-font-weight: bold;", fontSize);
        domesticPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + domesticColor + ";");
        internationalPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + internationalColor + ";");
        
        rootBox.setMinSize(60, 30);
    }

    // 设置右键菜单 (设置、锁定、退出等)
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        // 开机自启开关
        CheckMenuItem startupItem = new CheckMenuItem("开机自启");
        startupItem.setSelected(StartupManager.isStartupEnabled());
        startupItem.setOnAction(e -> StartupManager.setStartup(startupItem.isSelected()));
        
        // 智能显隐开关
        CheckMenuItem visibilityItem = new CheckMenuItem("开启智能显隐");
        visibilityItem.setSelected(Boolean.parseBoolean(config.getProperty("visibility.enabled", "false")));
        visibilityItem.setOnAction(e -> {
            config.setProperty("visibility.enabled", String.valueOf(visibilityItem.isSelected()));
            saveConfig();
            updatePrices(); // 立即刷新状态
        });

        MenuItem settingsItem = new MenuItem("设置");
        settingsItem.setOnAction(e -> openSettings());

        MenuItem toggleLockItem = new MenuItem("锁定位置");
        toggleLockItem.setOnAction(e -> {
            isLocked = !isLocked;
            toggleLockItem.setText(isLocked ? "解锁位置" : "锁定位置");
        });
        
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> {
            saveWindowPosition();
            System.exit(0);
        });
        
        contextMenu.getItems().addAll(startupItem, visibilityItem, settingsItem, toggleLockItem, exitItem);
        
        // 绑定右键事件
        rootBox.setOnContextMenuRequested(event -> 
            contextMenu.show(rootBox, event.getScreenX(), event.getScreenY())
        );
    }
    
    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            config.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isSettingsOpen = false;

    // 打开设置窗口
    private void openSettings() {
        if (isSettingsOpen) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Settings.fxml"));
            Parent root = loader.load();
            
            SettingsController controller = loader.getController();
            controller.setMainController(this, config);
            
            Stage settingsStage = new Stage();
            settingsStage.setTitle("设置");
            settingsStage.setScene(new Scene(root));
            settingsStage.initStyle(StageStyle.UTILITY);
            settingsStage.setAlwaysOnTop(true);
            
            isSettingsOpen = true;
            settingsStage.setOnHidden(e -> isSettingsOpen = false);
            
            settingsStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心更新逻辑：获取价格并更新 UI
     */
    private void updatePrices() {
        // 1. 调用 Backend 获取数据
        Map<String, Double> prices = priceService.fetchPrices();
        double domestic = prices.getOrDefault("domestic", 0.0);
        double international = prices.getOrDefault("international", 0.0);

        // 2. 检查是否需要触发价格预警
        checkAlerts(domestic, international);

        // 3. 在 JavaFX UI 线程更新界面
        Platform.runLater(() -> {
            boolean isVisibilityEnabled = Boolean.parseBoolean(config.getProperty("visibility.enabled", "false"));
            boolean showContent = true;

            // 智能显隐逻辑：如果开启且不在设定范围内，则隐藏
            if (isVisibilityEnabled && !shouldShow(domestic, international)) {
                showContent = false;
            }

            if (showContent) {
                rootBox.setOpacity(1.0);
                if (domestic > 0) {
                    domesticPriceLabel.setText(String.format("%.2f", domestic));
                    internationalPriceLabel.setText(String.format("%.2f", international));
                } else {
                    domesticPriceLabel.setText("--");
                    internationalPriceLabel.setText("--");
                }
            } else {
                // 伪隐藏：清空文字，透明度设为极低 (0.01)，保留右键交互能力
                domesticPriceLabel.setText("");
                internationalPriceLabel.setText("");
                rootBox.setOpacity(0.01); 
            }
        });
    }

    // 检查并触发预警
    private void checkAlerts(double domestic, double international) {
        if (domestic <= 0 && international <= 0) return;

        double dMax = parseDouble(config.getProperty("alert.domestic.max", "0"));
        double dMin = parseDouble(config.getProperty("alert.domestic.min", "0"));
        double iMax = parseDouble(config.getProperty("alert.international.max", "0"));
        double iMin = parseDouble(config.getProperty("alert.international.min", "0"));

        if (dMax > 0 && domestic >= dMax) triggerAlert("domestic_max", "国内金价预警", "当前价格: " + domestic + " (高于 " + dMax + ")");
        if (dMin > 0 && domestic > 0 && domestic <= dMin) triggerAlert("domestic_min", "国内金价预警", "当前价格: " + domestic + " (低于 " + dMin + ")");
        
        if (iMax > 0 && international >= iMax) triggerAlert("intl_max", "国际金价预警", "当前价格: " + international + " (高于 " + iMax + ")");
        if (iMin > 0 && international > 0 && international <= iMin) triggerAlert("intl_min", "国际金价预警", "当前价格: " + international + " (低于 " + iMin + ")");
    }

    // 触发桌面通知 (带冷却时间)
    private void triggerAlert(String key, String title, String message) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime.getOrDefault(key, 0L) > ALERT_COOLDOWN) {
            lastAlertTime.put(key, now);
            Platform.runLater(() -> showNotification(title, message));
        }
    }

    // 显示自定义的 Toast 通知
    private void showNotification(String title, String message) {
        Stage toastStage = new Stage();
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);

        VBox root = new VBox(5);
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-background-radius: 10; -fx-padding: 15; -fx-border-color: #FFD700; -fx-border-width: 1; -fx-border-radius: 10;");
        root.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        root.getChildren().addAll(titleLabel, msgLabel);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        toastStage.setScene(scene);

        // 显示在屏幕右下角
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        toastStage.setX(screenBounds.getMaxX() - 250);
        toastStage.setY(screenBounds.getMaxY() - 100);
        
        toastStage.show();

        // 5秒后自动关闭
        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(e -> toastStage.close());
        delay.play();
    }

    private double parseDouble(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }

    // 判断当前价格是否在"智能显隐"的显示范围内
    private boolean shouldShow(double domestic, double international) {
        double minD = parseDouble(config.getProperty("threshold.domestic.min", "0"));
        double maxD = parseDouble(config.getProperty("threshold.domestic.max", "0"));
        double minI = parseDouble(config.getProperty("threshold.international.min", "0"));
        double maxI = parseDouble(config.getProperty("threshold.international.max", "0"));

        boolean showD = (minD == 0 && maxD == 0) || (domestic >= minD && domestic <= maxD);
        boolean showI = (minI == 0 && maxI == 0) || (international >= minI && international <= maxI);

        return showD && showI;
    }

    // 启动定时轮询任务
    private void startDataPolling() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updatePrices();
                
                // 激进的防遮挡逻辑：每 2 秒强制置顶一次
                Platform.runLater(() -> {
                    // 如果设置窗口打开，暂停强制置顶主窗口，防止抢夺焦点导致无法输入
                    if (stage != null && !isSettingsOpen) {
                        stage.toFront();
                        
                        if (!stage.isAlwaysOnTop()) {
                            stage.setAlwaysOnTop(true);
                        }
                    }
                });
            }
        }, 0, 2000); // 间隔 2000 毫秒 (2秒)
    }

    // 设置窗口拖动逻辑
    private void setupWindowDragging() {
        rootBox.setOnMousePressed(event -> {
            if (!isLocked && event.getButton() == MouseButton.PRIMARY) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        rootBox.setOnMouseDragged(event -> {
            if (!isLocked && event.getButton() == MouseButton.PRIMARY && stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
        rootBox.setOnMouseReleased(event -> {
            if (!isLocked && event.getButton() == MouseButton.PRIMARY) saveWindowPosition();
        });
    }

    // 保存窗口位置到配置文件
    private void saveWindowPosition() {
        if (stage == null) return;
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            if (!Double.isNaN(stage.getX())) {
                config.setProperty("window.x", String.valueOf(stage.getX()));
                config.setProperty("window.y", String.valueOf(stage.getY()));
                config.setProperty("window.locked", String.valueOf(isLocked));
                config.store(output, null);
            }
        } catch (IOException ignored) {}
    }

    // 加载窗口位置
    private void loadWindowPosition() {
        String x = config.getProperty("window.x");
        String y = config.getProperty("window.y");
        String locked = config.getProperty("window.locked");
        
        if (stage != null) {
            if (x != null && y != null) {
                stage.setX(Double.parseDouble(x));
                stage.setY(Double.parseDouble(y));
            }
            if (locked != null) isLocked = Boolean.parseBoolean(locked);
        }
    }
}