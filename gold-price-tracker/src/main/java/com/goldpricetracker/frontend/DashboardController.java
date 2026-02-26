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

public class DashboardController {

    @FXML private VBox rootBox;
    @FXML private Label domesticPriceLabel;
    @FXML private Label internationalPriceLabel;

    private final PriceService priceService = new PriceService();
    private final Timer timer = new Timer(true);
    private Properties config = new Properties();
    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isLocked = false;
    private static final String CONFIG_FILE = "gold_tracker_config.properties";
    private Stage stage;
    
    private Map<String, Long> lastAlertTime = new HashMap<>();
    private static final long ALERT_COOLDOWN = 10 * 60 * 1000;

    @FXML
    public void initialize() {
        loadConfig();
        applySettings();
        
        // 样式设置
        rootBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 5; -fx-padding: 2;");
        String commonStyle = "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 11px; -fx-font-weight: bold;";
        domesticPriceLabel.setStyle(commonStyle + " -fx-text-fill: #FFD700;");
        internationalPriceLabel.setStyle(commonStyle + " -fx-text-fill: #FFFFFF;");
        
        setupWindowDragging();
        setupContextMenu();
        startDataPolling();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        loadWindowPosition();
        stage.setOnCloseRequest(event -> saveWindowPosition());
    }

    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
        } catch (IOException ignored) {}
    }

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
            // 立即触发一次更新以应用状态
            updatePrices();
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
            
            // 标记设置窗口打开状态
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

        checkAlerts(domestic, international);

        Platform.runLater(() -> {
            boolean isVisibilityEnabled = Boolean.parseBoolean(config.getProperty("visibility.enabled", "false"));
            boolean showContent = true;

            // 仅当开启了智能显隐，且不满足显示条件时，才隐藏内容
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
                // 伪隐藏：内容置空，保留极低透明度背景以便右键
                domesticPriceLabel.setText("");
                internationalPriceLabel.setText("");
                rootBox.setOpacity(0.01); 
            }
        });
    }

    // ... (中间代码保持不变: checkAlerts, triggerAlert, showNotification, parseDouble, shouldShow, startDataPolling, setupWindowDragging, saveWindowPosition, loadWindowPosition) ...
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
                
                // 激进的防遮挡逻辑
                Platform.runLater(() -> {
                    // 如果设置窗口打开，暂停强制置顶主窗口，防止抢夺焦点
                    if (stage != null && !isSettingsOpen) {
                        stage.toFront();
                        
                        if (!stage.isAlwaysOnTop()) {
                            stage.setAlwaysOnTop(true);
                        }
                    }
                });
            }
        }, 0, 2000);
    }

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
