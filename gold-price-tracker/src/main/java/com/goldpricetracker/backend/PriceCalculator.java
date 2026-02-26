package com.goldpricetracker.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceCalculator {

    /**
     * 计算国内金价 (CNY/g)
     * 公式: 国际金价 (USD/oz) ÷ 31.1034768 × 汇率 (USD/CNY)
     * @param internationalPriceUsd 国际金价
     * @param usdCnyRate 汇率
     * @return 国内金价 (保留2位小数)
     */
    public static double calculateDomesticPrice(double internationalPriceUsd, double usdCnyRate) {
        if (internationalPriceUsd <= 0 || usdCnyRate <= 0) {
            return 0.0;
        }
        
        // 1 盎司 = 31.1034768 克
        double OUNCE_TO_GRAM = 31.1034768;
        
        double priceInCnyPerGram = (internationalPriceUsd / OUNCE_TO_GRAM) * usdCnyRate;
        
        // 四舍五入保留两位小数
        BigDecimal bd = new BigDecimal(priceInCnyPerGram);
        return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
