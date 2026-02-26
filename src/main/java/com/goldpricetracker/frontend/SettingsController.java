package com.goldpricetracker.frontend;

import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 设置界面控制器
 * 
 * 功能：
 * 负责处理 Settings.fxml 界面中的用户交互，包括：
 * 1. 颜色设置 (背景色、字体颜色)
 * 2. 字体大小设置
 * 3. 智能显隐阈值配置
 * 4. 价格预警阈值配置
 * 5. 将配置保存到文件并立即应用到主界面
 */
public class SettingsController {

    // --- UI 控件绑定 (与 Settings.fxml 对应) ---
    
    // 外观设置
    @FXML private ColorPicker bgColorPicker; // 背景颜色选择器
    @FXML private ColorPicker domesticColorPicker; // 国内金价颜色
    @FXML private ColorPicker internationalColorPicker; // 国际金价颜色
    @FXML private TextField fontSizeField; // 字体大小输入框
    
    // 智能显隐设置
    @FXML private CheckBox smartVisibilityToggle; // 智能显隐开关
    @FXML private TextField minDomesticField; // 国内金价显示下限
    @FXML private TextField maxDomesticField; // 国内金价显示上限
    @FXML private TextField minInternationalField; // 国际金价显示下限
    @FXML private TextField maxInternationalField; // 国际金价显示上限
    
    // 价格预警设置
    @FXML private TextField alertDomesticMaxField; // 国内金价报警上限
    @FXML private TextField alertDomesticMinField; // 国内金价报警下限
    @FXML private TextField alertInternationalMaxField; // 国际金价报警上限
    @FXML private TextField alertInternationalMinField; // 国际金价报警下限

    // 持有主控制器引用，以便应用设置时回调
    private DashboardController mainController;
    // 配置对象
    private Properties config;

    /**
     * 初始化设置数据
     * 由 DashboardController 打开设置窗口时调用
     */
    public void setMainController(DashboardController controller, Properties config) {
        this.mainController = controller;
        this.config = config;
        loadValues(); // 将配置填入界面控件
    }

    /**
     * 将 Properties 中的配置加载到 UI 控件中
     */
    private void loadValues() {
        // 加载颜色 (默认值: 透明背景, 金色国内价, 白色国际价)
        bgColorPicker.setValue(Color.web(config.getProperty("color.bg", "rgba(0,0,0,0.01)")));
        domesticColorPicker.setValue(Color.web(config.getProperty("color.domestic", "#FFD700")));
        internationalColorPicker.setValue(Color.web(config.getProperty("color.international", "#FFFFFF")));
        fontSizeField.setText(config.getProperty("font.size", "12"));
        
        // 加载智能显隐设置
        smartVisibilityToggle.setSelected(Boolean.parseBoolean(config.getProperty("visibility.enabled", "false")));
        minDomesticField.setText(config.getProperty("threshold.domestic.min", "0"));
        maxDomesticField.setText(config.getProperty("threshold.domestic.max", "0"));
        minInternationalField.setText(config.getProperty("threshold.international.min", "0"));
        maxInternationalField.setText(config.getProperty("threshold.international.max", "0"));
        
        // 加载预警设置
        alertDomesticMaxField.setText(config.getProperty("alert.domestic.max", "0"));
        alertDomesticMinField.setText(config.getProperty("alert.domestic.min", "0"));
        alertInternationalMaxField.setText(config.getProperty("alert.international.max", "0"));
        alertInternationalMinField.setText(config.getProperty("alert.international.min", "0"));
    }

    /**
     * 保存按钮点击事件
     */
    @FXML
    private void handleSave() {
        // 1. 将 UI 控件的值写入 Properties 对象
        config.setProperty("color.bg", toWeb(bgColorPicker.getValue()));
        config.setProperty("color.domestic", toWeb(domesticColorPicker.getValue()));
        config.setProperty("color.international", toWeb(internationalColorPicker.getValue()));
        config.setProperty("font.size", fontSizeField.getText());
        
        config.setProperty("visibility.enabled", String.valueOf(smartVisibilityToggle.isSelected()));
        config.setProperty("threshold.domestic.min", minDomesticField.getText());
        config.setProperty("threshold.domestic.max", maxDomesticField.getText());
        config.setProperty("threshold.international.min", minInternationalField.getText());
        config.setProperty("threshold.international.max", maxInternationalField.getText());
        
        config.setProperty("alert.domestic.max", alertDomesticMaxField.getText());
        config.setProperty("alert.domestic.min", alertDomesticMinField.getText());
        config.setProperty("alert.international.max", alertInternationalMaxField.getText());
        config.setProperty("alert.international.min", alertInternationalMinField.getText());

        // 2. 将 Properties 持久化到磁盘文件
        try (OutputStream output = new FileOutputStream("gold_tracker_config.properties")) {
            config.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3. 通知主界面应用新设置
        mainController.applySettings();
        
        // 4. 关闭设置窗口
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) bgColorPicker.getScene().getWindow()).close();
    }

    /**
     * 将 JavaFX Color 对象转换为 Web RGBA 字符串格式
     * 例如: rgba(255, 215, 0, 1.00)
     */
    private String toWeb(Color color) {
        return String.format("rgba(%d, %d, %d, %.2f)", 
            (int)(color.getRed() * 255), 
            (int)(color.getGreen() * 255), 
            (int)(color.getBlue() * 255), 
            color.getOpacity());
    }
}
