package com.goldpricetracker.frontend;

import com.goldpricetracker.backend.PriceService;
import com.goldpricetracker.backend.StartupManager;
import com.goldpricetracker.backend.TaskbarLocator;
import com.goldpricetracker.backend.WindowStyleHelper;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
// use fully-qualified java.awt.MenuItem in code to avoid clash
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Font;
import java.awt.CheckboxMenuItem;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JWindow;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 主界面控制器：负责处理 UI 逻辑、数据更新和用户交互。
 */
public class DashboardController {

    // FXML 绑定的 UI 控件
    @FXML private VBox rootBox; // v1.5：上下方向显示
    @FXML private Label domesticPriceLabel; 
    @FXML private Label internationalPriceLabel; 
    @FXML private Label domesticTrendLabel; 
    @FXML private Label internationalTrendLabel; 

    // 后端服务实例
    private final PriceService priceService = new PriceService();
    private final Timer timer = new Timer(true);
    private Properties config = new Properties();
    
    private boolean isLocked = false;
    private boolean alwaysOnTop = false;
    private boolean clickThrough = false;
    private boolean snapToEdges = false;
    
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
        
        // 设置默认样式：透明背景，圆角
        rootBox.setStyle("-fx-background-color: transparent; -fx-padding: 2 8;");
        
        // 给文字添加阴影，确保在浅色背景下也能看清
        DropShadow dropShadow = new DropShadow();
        dropShadow.setRadius(2.0);
        dropShadow.setOffsetX(1.0);
        dropShadow.setOffsetY(1.0);
        dropShadow.setColor(Color.color(0, 0, 0, 0.8));
        
        domesticPriceLabel.setEffect(dropShadow);
        internationalPriceLabel.setEffect(dropShadow);
        domesticTrendLabel.setEffect(dropShadow);
        internationalTrendLabel.setEffect(dropShadow);
        
        applySettings();
        setupContextMenu();
        startDataPolling(); // 仅用于数据更新，不再维护窗口位置
        setupSystemTray();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        System.out.println("DashboardController: setStage called.");
        // 诊断：仅记录一次坐标计算，帮助分析为何到不了任务栏（不移动窗口）
        Platform.runLater(() -> debugDockComputationOnce());
        // v1.5 适配：根据任务栏高度自动压缩上下布局，并一次性放置到任务栏内部
        Platform.runLater(() -> adaptHeightAndPlaceIntoTaskbarOnce());
    }

    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
            System.out.println("DashboardController: Config loaded.");
            alwaysOnTop = Boolean.parseBoolean(config.getProperty("window.always_on_top", "false"));
            isLocked = Boolean.parseBoolean(config.getProperty("window.locked", "false"));
            clickThrough = Boolean.parseBoolean(config.getProperty("window.click_through", "false"));
            snapToEdges = Boolean.parseBoolean(config.getProperty("snap_to_edges", "false"));
        } catch (IOException ignored) {
            System.out.println("DashboardController: Config file not found or error loading.");
        }
    }

    // 应用配置中的样式设置 (背景色、字体大小等)
    public void applySettings() {
        // 背景色强制透明，忽略配置中的 color.bg
        // String bg = config.getProperty("color.bg", "rgba(30, 30, 30, 0.85)"); 
        String domesticColor = config.getProperty("color.domestic", "#FFD700");
        String internationalColor = config.getProperty("color.international", "#FFFFFF");
        String fontSize = config.getProperty("font.size", "14"); // 默认字体大小改为 14px

        // 保持透明背景
        rootBox.setStyle("-fx-background-color: transparent; -fx-padding: 2 8;");
        
        // 增加字体粗细和内部留白，避免趋势符号遮挡数字
        String fontStyle = String.format("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: %spx; -fx-font-weight: bold;", fontSize);
        domesticPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + domesticColor + "; -fx-padding: 0 6 0 0;");
        internationalPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + internationalColor + "; -fx-padding: 0 6 0 0;");
        
        // 趋势标签样式
        String trendStyle = "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-padding: 0 0 0 2;";
        domesticTrendLabel.setStyle(trendStyle);
        internationalTrendLabel.setStyle(trendStyle);
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        CheckMenuItem startupItem = new CheckMenuItem("开机自启");
        startupItem.setSelected(StartupManager.isStartupEnabled());
        startupItem.setOnAction(e -> StartupManager.setStartup(startupItem.isSelected()));
        
        // v1.5：移除“自动嵌入任务栏”“锁定位置”功能

        MenuItem avgCalcItem = new MenuItem("均价计算器");
        avgCalcItem.setOnAction(e -> openAverageCalculator());

        MenuItem settingsItem = new MenuItem("更多设置");
        settingsItem.setOnAction(e -> openSettings());
        
        MenuItem exitItem = new MenuItem("退出程序");
        exitItem.setOnAction(e -> {
            saveWindowPosition();
            System.exit(0);
        });
        
        contextMenu.getItems().addAll(startupItem, avgCalcItem, settingsItem, exitItem);
        
        rootBox.setOnContextMenuRequested(event -> 
            contextMenu.show(rootBox, event.getScreenX(), event.getScreenY())
        );
    }
    
    // v1.5：移除窗口拖动逻辑（不再在桌面自由移动，也不再维护任务栏位置）

    private void openAverageCalculator() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AverageCalculator.fxml"));
            Parent root = loader.load();
            
            AverageCalculatorController controller = loader.getController();
            
            Stage calcStage = new Stage();
            controller.setStage(calcStage);
            
            calcStage.setTitle("均价计算器");
            calcStage.setScene(new Scene(root));
            calcStage.initStyle(StageStyle.UTILITY); // 只有关闭按钮
            calcStage.setAlwaysOnTop(true);
            calcStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                        domesticPriceLabel.setStyle("-fx-text-fill: #AAA; -fx-font-weight: bold; -fx-font-size: 14px;");
                    } else {
                        // 开市：恢复亮金色
                        String domesticColor = config.getProperty("color.domestic", "#FFD700");
                        domesticPriceLabel.setStyle("-fx-text-fill: " + domesticColor + "; -fx-font-weight: bold; -fx-font-size: 14px;");
                    }
                    // 国际金：保持 14px 与配置颜色
                    String internationalColor = config.getProperty("color.international", "#FFFFFF");
                    internationalPriceLabel.setStyle("-fx-text-fill: " + internationalColor + "; -fx-font-weight: bold; -fx-font-size: 14px;");
                    
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
            }
        }, 0, 2000); 
    }
    
    // 诊断：仅输出一次坐标计算日志
    private boolean dockLogged = false;
    private void debugDockComputationOnce() {
        if (dockLogged || stage == null || !stage.isShowing()) return;
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (Double.isNaN(w) || w <= 1 || Double.isNaN(h) || h <= 1) {
            rootBox.applyCss();
            rootBox.layout();
            w = rootBox.prefWidth(-1);
            h = rootBox.prefHeight(-1);
        }
        System.out.println("Diagnostics: Window size used for dock calc: " + w + "x" + h);
        TaskbarLocator.Point loc = TaskbarLocator.getDockLocation(w, h);
        System.out.println("Diagnostics: Computed dock location: " + loc.x + ", " + loc.y);
        System.out.println("Diagnostics: Current stage location: " + stage.getX() + ", " + stage.getY());
        dockLogged = true;
    }

    // 一次性：根据任务栏高度压缩上下布局并放置到任务栏内部
    private boolean placedOnce = false;
    private void adaptHeightAndPlaceIntoTaskbarOnce() {
        if (placedOnce || stage == null || !stage.isShowing()) return;
        try {
            Rectangle2D tb = TaskbarLocator.getTaskbarBounds();
            if (tb == null) {
                System.out.println("AdaptPlace: Taskbar bounds not available, skip.");
                return;
            }
            // 读取配置的期望字体大小
            int targetFont = 14;
            try { targetFont = Integer.parseInt(config.getProperty("font.size", "14")); } catch (Exception ignored) {}
            // 自顶向下尝试，直到上下布局高度 <= 任务栏高度 - 2
            int font = targetFont;
            for (; font >= 10; font--) {
                String domesticColor = config.getProperty("color.domestic", "#FFD700");
                String internationalColor = config.getProperty("color.international", "#FFFFFF");
                String fontStyle = String.format("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: %dpx; -fx-font-weight: bold;", font);
                domesticPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + domesticColor + "; -fx-padding: 0 4 0 0;");
                internationalPriceLabel.setStyle(fontStyle + " -fx-text-fill: " + internationalColor + "; -fx-padding: 0 4 0 0;");
                String trendStyle = String.format("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: %dpx; -fx-padding: 0 0 0 2;", Math.max(10, font - 2));
                domesticTrendLabel.setStyle(trendStyle);
                internationalTrendLabel.setStyle(trendStyle);
                // 更新容器 padding 与 spacing，进一步压缩高度
                rootBox.setStyle("-fx-background-color: transparent; -fx-padding: 2 6;"); // 较小的上下 padding
                // 强制布局并测量高度
                rootBox.applyCss();
                rootBox.layout();
                double h = rootBox.prefHeight(-1);
                if (h <= tb.getHeight() - 2) {
                    break; // 找到可适配的字体大小
                }
            }
            // 放置到任务栏内部：一次性设置坐标
            rootBox.applyCss();
            rootBox.layout();
            double w = rootBox.prefWidth(-1);
            double h = rootBox.prefHeight(-1);
            TaskbarLocator.Point loc = TaskbarLocator.getDockLocation(w, h);
            stage.setX(loc.x);
            stage.setY(loc.y);
            System.out.println("AdaptPlace: Final size " + w + "x" + h + ", placed at " + loc.x + ", " + loc.y);
            placedOnce = true;
            applyWindowStyles();
        } catch (Throwable t) {
            System.err.println("AdaptPlace: " + t.getMessage());
        }
    }
    private void applyWindowStyles() {
        if (stage != null) stage.setAlwaysOnTop(alwaysOnTop);
        WindowStyleHelper.setTopMost("GoldPriceTracker_Main", alwaysOnTop);
        WindowStyleHelper.setClickThrough("GoldPriceTracker_Main", clickThrough);
        rootBox.setMouseTransparent(clickThrough);
    }
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private void setupDragging() {
        rootBox.setOnMousePressed(e -> {
            if (isLocked || stage == null) return;
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        rootBox.setOnMouseDragged(e -> {
            if (isLocked || stage == null) return;
            double nx = e.getScreenX() - dragOffsetX;
            double ny = e.getScreenY() - dragOffsetY;
            if (snapToEdges) {
                javafx.geometry.Rectangle2D sb = javafx.stage.Screen.getPrimary().getBounds();
                double threshold = 8;
                if (Math.abs(nx - sb.getMinX()) < threshold) nx = sb.getMinX();
                if (Math.abs((nx + stage.getWidth()) - sb.getMaxX()) < threshold) nx = sb.getMaxX() - stage.getWidth();
                if (Math.abs(ny - sb.getMinY()) < threshold) ny = sb.getMinY();
                if (Math.abs((ny + stage.getHeight()) - sb.getMaxY()) < threshold) ny = sb.getMaxY() - stage.getHeight();
            }
            stage.setX(nx);
            stage.setY(ny);
        });
        rootBox.setOnMouseReleased(e -> {
            saveWindowPosition();
            saveConfig();
        });
    }
    private void setupSystemTray() {
        try {
            if (!SystemTray.isSupported()) {
                setupDragging();
                applyWindowStyles();
                return;
            }
            SystemTray tray = SystemTray.getSystemTray();
            // 创建简易图标：金色圆点配白色边框，避免缺图导致图标不可见
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(255, 215, 0)); // 金色
            g.fillOval(2, 2, 12, 12);
            g.setColor(java.awt.Color.WHITE);
            g.drawOval(2, 2, 12, 12);
            g.dispose();
            TrayIcon icon = new TrayIcon(img, "Gold Price Tracker");
            icon.setImageAutoSize(true);
            // 使用 Swing JPopupMenu，实现中文稳定显示
            JPopupMenu swingMenu = buildSwingTrayMenu();
            JWindow popupWindow = new JWindow();
            JPanel invoker = new JPanel();
            popupWindow.setAlwaysOnTop(true);
            popupWindow.add(invoker);
            icon.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger() || e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        SwingUtilities.invokeLater(() -> {
                            popupWindow.setLocation(e.getX(), e.getY());
                            popupWindow.setSize(1, 1);
                            popupWindow.setVisible(true);
                            swingMenu.show(invoker, 0, 0);
                        });
                    }
                }
            });
            tray.add(icon);
            setupDragging();
            applyWindowStyles();
        } catch (Exception ignored) {
            setupDragging();
            applyWindowStyles();
        }
    }
    private JPopupMenu buildSwingTrayMenu() {
        JPopupMenu menu = new JPopupMenu();
        Font f = chooseBestFont();
        JCheckBoxMenuItem top = new JCheckBoxMenuItem("始终置顶", alwaysOnTop);
        top.setFont(f);
        top.addActionListener(a -> {
            alwaysOnTop = top.isSelected();
            config.setProperty("window.always_on_top", String.valueOf(alwaysOnTop));
            applyWindowStyles();
            saveConfig();
        });
        JCheckBoxMenuItem lock = new JCheckBoxMenuItem("锁定位置", isLocked);
        lock.setFont(f);
        lock.addActionListener(a -> {
            isLocked = lock.isSelected();
            config.setProperty("window.locked", String.valueOf(isLocked));
            saveConfig();
        });
        JCheckBoxMenuItem click = new JCheckBoxMenuItem("鼠标穿透", clickThrough);
        click.setFont(f);
        click.addActionListener(a -> {
            clickThrough = click.isSelected();
            config.setProperty("window.click_through", String.valueOf(clickThrough));
            applyWindowStyles();
            saveConfig();
        });
        JCheckBoxMenuItem snap = new JCheckBoxMenuItem("贴边吸附", snapToEdges);
        snap.setFont(f);
        snap.addActionListener(a -> {
            snapToEdges = snap.isSelected();
            config.setProperty("snap_to_edges", String.valueOf(snapToEdges));
            saveConfig();
        });
        JMenuItem settings = new JMenuItem("更多设置");
        settings.setFont(f);
        settings.addActionListener(a -> Platform.runLater(this::openSettings));
        JMenuItem avg = new JMenuItem("均价计算器");
        avg.setFont(f);
        avg.addActionListener(a -> Platform.runLater(this::openAverageCalculator));
        JMenuItem exit = new JMenuItem("退出");
        exit.setFont(f);
        exit.addActionListener(a -> {
            saveWindowPosition();
            System.exit(0);
        });
        menu.add(top);
        menu.add(lock);
        menu.add(click);
        menu.add(snap);
        menu.addSeparator();
        menu.add(settings);
        menu.add(avg);
        menu.add(exit);
        return menu;
    }
    private Font chooseBestFont() {
        try {
            String[] candidates = new String[] { "Microsoft YaHei UI", "微软雅黑", "SimSun", "宋体", "Segoe UI", "MS Shell Dlg 2", "Dialog" };
            java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String name : candidates) {
                if (available.contains(name)) {
                    return new Font(name, Font.PLAIN, 12);
                }
            }
        } catch (Exception ignored) {}
        return new Font("Dialog", Font.PLAIN, 12);
    }
    // 保存窗口位置到配置文件
    private void saveWindowPosition() {
        if (stage == null) return;
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            if (!Double.isNaN(stage.getX())) {
                config.setProperty("window.x", String.valueOf(stage.getX()));
                config.setProperty("window.y", String.valueOf(stage.getY()));
                config.store(output, null);
            }
        } catch (IOException ignored) {}
    }

    private void loadWindowPosition() {
        try {
            String x = config.getProperty("window.x");
            String y = config.getProperty("window.y");
            
            if (stage != null) {
                if (x != null && y != null) {
                    stage.setX(Double.parseDouble(x));
                    stage.setY(Double.parseDouble(y));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load window position: " + e.getMessage());
        }
    }
}
