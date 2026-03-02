package com.goldpricetracker.frontend;

import com.goldpricetracker.backend.CalculatorLogic;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * 均价计算器控制器
 * 负责处理均价计算逻辑和 UI 交互
 */
public class AverageCalculatorController {

    @FXML private TextField historyGramsField;
    @FXML private TextField historyPriceField;
    @FXML private TextField currentGramsField;
    @FXML private TextField currentPriceField;
    @FXML private Label resultGramsLabel;
    @FXML private Label resultPriceLabel;
    @FXML private Label errorLabel;

    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;

    /**
     * 初始化方法，在 FXML 加载后自动调用
     */
    @FXML
    public void initialize() {
        // 可以在这里添加一些初始化逻辑，例如限制输入框只能输入数字
        setupNumericValidation(historyGramsField);
        setupNumericValidation(historyPriceField);
        setupNumericValidation(currentGramsField);
        setupNumericValidation(currentPriceField);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        setupWindowDragging();
    }

    /**
     * 计算新的均价
     */
    @FXML
    private void handleCalculate() {
        try {
            errorLabel.setText(""); // 清除错误信息

            double historyGrams = parseDouble(historyGramsField.getText(), "历史持仓");
            double historyPrice = parseDouble(historyPriceField.getText(), "历史均价");
            double currentGrams = parseDouble(currentGramsField.getText(), "当前买入");
            double currentPrice = parseDouble(currentPriceField.getText(), "当前单价");

            double newAveragePrice = CalculatorLogic.calculateNewAverage(historyGrams, historyPrice, currentGrams, currentPrice);
            double totalGrams = historyGrams + currentGrams;

            resultGramsLabel.setText(String.format("%.2f 克", totalGrams));
            resultPriceLabel.setText(String.format("%.2f 元/克", newAveragePrice));

        } catch (NumberFormatException e) {
            errorLabel.setText("请输入有效的数字");
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private double parseDouble(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0; // 允许为空，默认为 0
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 格式不正确");
        }
    }

    private void setupNumericValidation(TextField textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) {
                textField.setText(oldValue);
            }
        });
    }

    private void setupWindowDragging() {
        if (stage == null || stage.getScene() == null) return;
        
        // 允许拖动窗口
        stage.getScene().setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        stage.getScene().setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }
}
