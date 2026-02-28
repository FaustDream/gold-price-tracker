package com.goldpricetracker.frontend;

import com.goldpricetracker.backend.PriceService;
import com.goldpricetracker.backend.StartupManager;
import com.goldpricetracker.backend.TaskbarLocator;
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
import javafx.scene.layout.HBox;
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
 * 更新内容：
 * 1. 布局改为 HBox 以适应任务栏。
 * 2. 增加任务栏自动停靠功能 (Auto-Dock)。
 * 3. 增加价格趋势指示器 (▲/▼)。
 */
public class DashboardController {

    // FXML 绑定的 UI 控件
    @FXML private HBox rootBox; // 改为 HBox
    @FXML private Label domesticPriceLabel; 
    @FXML private Label internationalPriceLabel; 
    @FXML private Label domesticTrendLabel; // 新增趋势标签
    @FXML private Label internationalTrendLabel; // 新增趋势标签

    // 后端服务实例
    private final PriceService priceService = new PriceService();
    private final Timer timer = new Timer(true);
    private Properties config = new Properties();
    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isLocked = false;
    // 默认开启任务栏停靠模式
    private boolean isTaskbarMode = true; 
    
    private static final String CONFIG_FILE = "gold_tracker_config.properties";
    private Stage stage;
    
    private Map<String, Long> lastAlertTime = new HashMap<>();
    private static final long ALERT_COOLDOWN = 10 * 60 * 1000;
    
    // 记录上一次价格用于计算趋势
    private double prevDomestic = 0.0;
    private double prevInternational = 0.0;

    @FXML
    public void initialize() {
        loadConfig();
        
        // 设置默认样式：深色背景，模仿任务栏
        rootBox.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-background-radius: 5; -fx-padding: 0 10;");
        
        applySettings();
        
        setupWindowDragging(); 
        setupContextMenu();    
        startDataPolling();    
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        loadWindowPosition();
        
        // 如果是任务栏模式，初始时进行一次定位
        if (isTaskbarMode) {
            Platform.runLater(this::dockToTaskbar);
        }
        
        stage.setOnCloseRequest(event -> saveWindowPosition());
    }

    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
        } catch (IOException ignored) {}
    }

    // 应用配置中的样式设置 (背景色、字体大小等)
    public void applySettings() {
        String bg = config.getProperty("color.bg", "transparent"); // 默认透明
        String domesticColor = config.getProperty("color.domestic", "#FFD700");
        String internationalColor = config.getProperty("color.international", "#FFFFFF");
        String fontSize = config.getProperty("font.size", "12");

        rootBox.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 5; -fx-padding: 0 10;", bg));
        
        String fontStyle = String.format("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: %spx; -fx-font-weight: bold;", fontSize);
        domesticPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + domesticColor + ";");
        internationalPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + internationalColor + ";");
        
        // 趋势标签样式
        String trendStyle = "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 10px;";
        domesticTrendLabel.setStyle(trendStyle);
        internationalTrendLabel.setStyle(trendStyle);
        
        rootBox.setMinHeight(30); // 适应任务栏高度
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        CheckMenuItem startupItem = new CheckMenuItem("开机自启");
        startupItem.setSelected(StartupManager.isStartupEnabled());
        startupItem.setOnAction(e -> StartupManager.setStartup(startupItem.isSelected()));
        
        // 任务栏停靠开关
        CheckMenuItem taskbarModeItem = new CheckMenuItem("停靠任务栏");
        taskbarModeItem.setSelected(isTaskbarMode);
        taskbarModeItem.setOnAction(e -> {
            isTaskbarMode = taskbarModeItem.isSelected();
            if (isTaskbarMode) {
                dockToTaskbar();
            }
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
        
        contextMenu.getItems().addAll(startupItem, taskbarModeItem, settingsItem, toggleLockItem, exitItem);
        
        rootBox.setOnContextMenuRequested(event -> 
            contextMenu.show(rootBox, event.getScreenX(), event.getScreenY())
        );
    }
    
    private void setupWindowDragging() {
        rootBox.setOnMousePressed(event -> {
            // 只有非锁定且非任务栏模式下才允许拖动
            if (!isLocked && !isTaskbarMode && event.getButton() == MouseButton.PRIMARY) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        rootBox.setOnMouseDragged(event -> {
            if (!isLocked && !isTaskbarMode && event.getButton() == MouseButton.PRIMARY && stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
        rootBox.setOnMouseReleased(event -> {
            if (!isLocked && !isTaskbarMode && event.getButton() == MouseButton.PRIMARY) saveWindowPosition();
        });
    }

    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            config.setProperty("window.taskbar_mode", String.valueOf(isTaskbarMode));
            config.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isSettingsOpen = false;

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

    private void updatePrices() {
        Map<String, Double> prices = priceService.fetchPrices();
        double domestic = prices.getOrDefault("domestic", 0.0);
        double international = prices.getOrDefault("international", 0.0);
        boolean isMarketClosed = prices.getOrDefault("market_closed", 0.0) > 0.5;

        checkAlerts(domestic, international);

        Platform.runLater(() -> {
            boolean isVisibilityEnabled = Boolean.parseBoolean(config.getProperty("visibility.enabled", "false"));
            boolean showContent = true;

            if (isVisibilityEnabled && !shouldShow(domestic, international)) {
                showContent = false;
            }

            if (showContent) {
                rootBox.setOpacity(1.0);
                if (domestic > 0) {
                    domesticPriceLabel.setText(String.format("%.2f", domestic));
                    internationalPriceLabel.setText(String.format("%.2f", international));
                    
                    // 样式调整：休市时国内金价颜色变暗，提示用户这是计算值/收盘值
                    if (isMarketClosed) {
                        domesticPriceLabel.setStyle("-fx-text-fill: #AAA; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        // 开市：恢复亮金色
                        String domesticColor = config.getProperty("color.domestic", "#FFD700");
                        domesticPriceLabel.setStyle("-fx-text-fill: " + domesticColor + "; -fx-font-weight: bold; -fx-font-size: 11px;");
                    }
                    
                    // 更新趋势
                    updateTrend(domestic, prevDomestic, domesticTrendLabel);
                    updateTrend(international, prevInternational, internationalTrendLabel);
                    
                    prevDomestic = domestic;
                    prevInternational = international;
                } else {
                    domesticPriceLabel.setText("--");
                    internationalPriceLabel.setText("--");
                }
            } else {
                domesticPriceLabel.setText("");
                internationalPriceLabel.setText("");
                rootBox.setOpacity(0.01); 
            }
        });
    }

    // 更新趋势箭头和颜色
    private void updateTrend(double current, double prev, Label label) {
        if (prev <= 0) {
            label.setText("");
            return;
        }
        if (current > prev) {
            label.setText("▲");
            label.setStyle("-fx-text-fill: #FF4444; -fx-font-size: 10px;"); // 红涨
        } else if (current < prev) {
            label.setText("▼");
            label.setStyle("-fx-text-fill: #44FF44; -fx-font-size: 10px;"); // 绿跌
        } else {
            label.setText("");
        }
    }

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

    private void triggerAlert(String key, String title, String message) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime.getOrDefault(key, 0L) > ALERT_COOLDOWN) {
            lastAlertTime.put(key, now);
            Platform.runLater(() -> showNotification(title, message));
        }
    }

    private void showNotification(String title, String message) {
        Stage toastStage = new Stage();
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);

        HBox root = new HBox(10); // 通知也改为横向布局
        root.setStyle("-fx-background-color: rgba(30, 30, 30, 0.95); -fx-background-radius: 5; -fx-padding: 10; -fx-border-color: #FFD700; -fx-border-width: 1; -fx-border-radius: 5;");
        root.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 12px; -fx-font-weight: bold;");
        
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        root.getChildren().addAll(titleLabel, msgLabel);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        toastStage.setScene(scene);

        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        toastStage.setX(screenBounds.getMaxX() - 300);
        toastStage.setY(screenBounds.getMaxY() - 80);
        
        toastStage.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(e -> toastStage.close());
        delay.play();
    }

    private double parseDouble(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }

    private boolean shouldShow(double domestic, double international) {
        double minD = parseDouble(config.getProperty("threshold.domestic.min", "0"));
        double maxD = parseDouble(config.getProperty("threshold.domestic.max", "0"));
        double minI = parseDouble(config.getProperty("threshold.international.min", "0"));
        double maxI = parseDouble(config.getProperty("threshold.international.max", "0"));

        boolean showD = (minD == 0 && maxD == 0) || (domestic >= minD && domestic <= maxD);
        boolean showI = (minI == 0 && maxI == 0) || (international >= minI && international <= maxI);

        return showD && showI;
    }

    private void startDataPolling() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updatePrices();
                
                Platform.runLater(() -> {
                    if (stage != null && !isSettingsOpen) {
                        stage.toFront();
                        if (!stage.isAlwaysOnTop()) {
                            stage.setAlwaysOnTop(true);
                        }
                        // 如果开启了任务栏停靠，定期检查位置 (防止任务栏移动或分辨率改变)
                        if (isTaskbarMode) {
                            dockToTaskbar();
                        }
                    }
                });
            }
        }, 0, 2000); 
    }
    
    /**
     * 将窗口停靠到任务栏托盘区旁边
     */
    private void dockToTaskbar() {
        if (stage == null || !stage.isShowing()) return;
        
        // 获取窗口尺寸 (如果尚未显示可能为 NaN，使用 rootBox 的尺寸)
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (Double.isNaN(w)) w = rootBox.getWidth();
        if (Double.isNaN(h)) h = rootBox.getHeight();
        
        // 调用 JNA 工具类获取最佳位置
        TaskbarLocator.Point loc = TaskbarLocator.getDockLocation(w, h);
        
        // 平滑移动 (如果位置差异不大，就不动，避免抖动)
        if (Math.abs(stage.getX() - loc.x) > 1 || Math.abs(stage.getY() - loc.y) > 1) {
            stage.setX(loc.x);
            stage.setY(loc.y);
        }
    }

    // 保存窗口位置到配置文件
    private void saveWindowPosition() {
        if (stage == null) return;
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            if (!Double.isNaN(stage.getX())) {
                config.setProperty("window.x", String.valueOf(stage.getX()));
                config.setProperty("window.y", String.valueOf(stage.getY()));
                config.setProperty("window.locked", String.valueOf(isLocked));
                config.setProperty("window.taskbar_mode", String.valueOf(isTaskbarMode));
                config.store(output, null);
            }
        } catch (IOException ignored) {}
    }

    private void loadWindowPosition() {
        String x = config.getProperty("window.x");
        String y = config.getProperty("window.y");
        String locked = config.getProperty("window.locked");
        String tbMode = config.getProperty("window.taskbar_mode");
        
        if (stage != null) {
            if (x != null && y != null) {
                stage.setX(Double.parseDouble(x));
                stage.setY(Double.parseDouble(y));
            }
            if (locked != null) isLocked = Boolean.parseBoolean(locked);
            if (tbMode != null) isTaskbarMode = Boolean.parseBoolean(tbMode);
        }
    }
}
