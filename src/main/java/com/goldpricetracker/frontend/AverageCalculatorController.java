package com.goldpricetracker.frontend;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * 黄金均价计算器控制器
 * 
 * 功能：
 * 根据用户输入的当前持仓（克数、均价）和新增买入（克数、单价），
 * 计算并显示加仓后的总克数和新的平均成本。
 */
public class AverageCalculatorController {

    @FXML private TextField currentWeightField;
    @FXML private TextField currentPriceField;
    @FXML private TextField newWeightField;
    @FXML private TextField newPriceField;
    
    @FXML private Label resultTotalWeightLabel;
    @FXML private Label resultAvgPriceLabel;

    @FXML
    private void calculate() {
        try {
            double currentWeight = parseDouble(currentWeightField.getText());
            double currentPrice = parseDouble(currentPriceField.getText());
            double newWeight = parseDouble(newWeightField.getText());
            double newPrice = parseDouble(newPriceField.getText());

            double totalWeight = currentWeight + newWeight;
            
            if (totalWeight <= 0) {
                resultTotalWeightLabel.setText("总克数: 0.00 g");
                resultAvgPriceLabel.setText("新均价: 0.00 元/g");
                return;
            }

            double totalCost = (currentWeight * currentPrice) + (newWeight * newPrice);
            double newAvgPrice = totalCost / totalWeight;

            resultTotalWeightLabel.setText(String.format("总克数: %.2f g", totalWeight));
            resultAvgPriceLabel.setText(String.format("新均价: %.2f 元/g", newAvgPrice));

        } catch (NumberFormatException e) {
            resultAvgPriceLabel.setText("输入格式错误");
        }
    }

    private double parseDouble(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        return Double.parseDouble(val.trim());
    }
}
