package com.goldpricetracker.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PriceCalculatorTest {

    @Test
    void testCalculateDomesticPrice() {
        // 国际金价 2000 USD/oz, 汇率 7.0
        // (2000 / 31.1034768) * 7.0 = 450.11
        double international = 2000.0;
        double rate = 7.0;
        
        double result = PriceCalculator.calculateDomesticPrice(international, rate);
        
        assertEquals(450.11, result, 0.01, "计算结果应约为 450.11");
    }

    @Test
    void testZeroInput() {
        assertEquals(0.0, PriceCalculator.calculateDomesticPrice(0, 7.0));
        assertEquals(0.0, PriceCalculator.calculateDomesticPrice(2000, 0));
    }
}
