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

public class SettingsController {

    @FXML private ColorPicker bgColorPicker;
    @FXML private ColorPicker domesticColorPicker;
    @FXML private ColorPicker internationalColorPicker;
    @FXML private TextField fontSizeField;
    
    @FXML private CheckBox smartVisibilityToggle;
    @FXML private TextField minDomesticField;
    @FXML private TextField maxDomesticField;
    @FXML private TextField minInternationalField;
    @FXML private TextField maxInternationalField;
    
    @FXML private TextField alertDomesticMaxField;
    @FXML private TextField alertDomesticMinField;
    @FXML private TextField alertInternationalMaxField;
    @FXML private TextField alertInternationalMinField;

    private DashboardController mainController;
    private Properties config;

    public void setMainController(DashboardController controller, Properties config) {
        this.mainController = controller;
        this.config = config;
        loadValues();
    }

    private void loadValues() {
        bgColorPicker.setValue(Color.web(config.getProperty("color.bg", "rgba(0,0,0,0.01)")));
        domesticColorPicker.setValue(Color.web(config.getProperty("color.domestic", "#FFD700")));
        internationalColorPicker.setValue(Color.web(config.getProperty("color.international", "#FFFFFF")));
        fontSizeField.setText(config.getProperty("font.size", "12"));
        
        smartVisibilityToggle.setSelected(Boolean.parseBoolean(config.getProperty("visibility.enabled", "false")));
        minDomesticField.setText(config.getProperty("threshold.domestic.min", "0"));
        maxDomesticField.setText(config.getProperty("threshold.domestic.max", "0"));
        minInternationalField.setText(config.getProperty("threshold.international.min", "0"));
        maxInternationalField.setText(config.getProperty("threshold.international.max", "0"));
        
        alertDomesticMaxField.setText(config.getProperty("alert.domestic.max", "0"));
        alertDomesticMinField.setText(config.getProperty("alert.domestic.min", "0"));
        alertInternationalMaxField.setText(config.getProperty("alert.international.max", "0"));
        alertInternationalMinField.setText(config.getProperty("alert.international.min", "0"));
    }

    @FXML
    private void handleSave() {
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

        // 保存到文件
        try (OutputStream output = new FileOutputStream("gold_tracker_config.properties")) {
            config.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 应用更改
        mainController.applySettings();
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) bgColorPicker.getScene().getWindow()).close();
    }

    private String toWeb(Color color) {
        return String.format("rgba(%d, %d, %d, %.2f)", 
            (int)(color.getRed() * 255), 
            (int)(color.getGreen() * 255), 
            (int)(color.getBlue() * 255), 
            color.getOpacity());
    }
}
