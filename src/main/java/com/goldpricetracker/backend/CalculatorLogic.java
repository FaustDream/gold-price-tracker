package com.goldpricetracker.backend;

/**
 * 计算逻辑工具类
 */
public class CalculatorLogic {

    /**
     * 计算新的持仓均价
     * 公式: (历史均价 * 历史克数 + 当前单价 * 当前克数) / (历史克数 + 当前克数)
     * 
     * @param historyGrams 历史持仓克数
     * @param historyPrice 历史持仓均价
     * @param currentGrams 当前买入克数
     * @param currentPrice 当前买入单价
     * @return 新的持仓均价
     * @throws IllegalArgumentException 如果输入值为负数或总克数为0
     */
    public static double calculateNewAverage(double historyGrams, double historyPrice, double currentGrams, double currentPrice) {
        if (historyGrams < 0 || historyPrice < 0 || currentGrams < 0 || currentPrice < 0) {
            throw new IllegalArgumentException("数值不能为负数");
        }
        
        double totalGrams = historyGrams + currentGrams;
        if (totalGrams == 0) {
             throw new IllegalArgumentException("总持仓不能为 0");
        }
        
        double totalCost = (historyPrice * historyGrams) + (currentPrice * currentGrams);
        return totalCost / totalGrams;
    }
}
