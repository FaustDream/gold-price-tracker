package com.goldpricetracker.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价格计算器工具类
 * 
 * 功能：
 * 负责纯粹的数学计算逻辑，将国际金价（美元/盎司）换算为国内金价（人民币/克）。
 * 
 * 为什么需要单独提取这个类？
 * 1. 职责分离：让 PriceService 专注于业务流程，PriceCalculator 专注于数值计算。
 * 2. 易于测试：纯数学函数非常容易编写单元测试，不需要模拟网络请求。
 */
public class PriceCalculator {

    /**
     * 计算国内金价 (CNY/g)
     * 
     * 换算公式详解:
     * 1. 国际金价单位是 "美元/盎司" (USD/oz)
     * 2. 国内金价单位是 "人民币/克" (CNY/g)
     * 3. 1 金衡盎司 (Troy Ounce) ≈ 31.1034768 克
     * 
     * 所以:
     * 单价(美元/克) = 国际金价 ÷ 31.1034768
     * 单价(人民币/克) = 单价(美元/克) × 汇率(USD/CNY)
     * 
     * @param internationalPriceUsd 国际金价 (美元/盎司)
     * @param usdCnyRate 美元兑人民币汇率
     * @return 国内金价 (人民币/克)，保留2位小数
     */
    public static double calculateDomesticPrice(double internationalPriceUsd, double usdCnyRate) {
        if (internationalPriceUsd <= 0 || usdCnyRate <= 0) {
            return 0.0;
        }
        
        // 换算系数：1 金衡盎司 = 31.1034768 克
        double OUNCE_TO_GRAM = 31.1034768;
        
        // 执行换算
        double priceInCnyPerGram = (internationalPriceUsd / OUNCE_TO_GRAM) * usdCnyRate;
        
        // 使用 BigDecimal 进行高精度四舍五入，保留两位小数
        BigDecimal bd = new BigDecimal(priceInCnyPerGram);
        return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
