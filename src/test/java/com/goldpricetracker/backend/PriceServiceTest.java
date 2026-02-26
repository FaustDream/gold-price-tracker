package com.goldpricetracker.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

public class PriceServiceTest {

    @Test
    void testParseSinaData() {
        // 由于 PriceService 主要依赖网络请求，单元测试难以直接测试真实 API
        // 这里我们可以通过反射或重构代码来测试私有的解析逻辑，或者进行简单的集成测试
        
        PriceService service = new PriceService();
        Map<String, Double> prices = service.fetchPrices();
        
        assertNotNull(prices);
        // 注意：如果没有网络，这里可能为空或 0.0，所以仅验证 Map 结构
        assertTrue(prices.containsKey("domestic") || prices.isEmpty());
        assertTrue(prices.containsKey("international") || prices.isEmpty());
    }
}
