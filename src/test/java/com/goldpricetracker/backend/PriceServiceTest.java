package com.goldpricetracker.backend;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class PriceServiceTest {

    // 辅助方法：创建固定时间的 Clock
    private Clock createFixedClock(String dateTimeStr) {
        LocalDateTime dt = LocalDateTime.parse(dateTimeStr);
        return Clock.fixed(dt.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void testMarketClosedLogic() throws Exception {
        PriceService service = new PriceService();
        Method isMarketClosed = PriceService.class.getDeclaredMethod("isMarketClosed");
        isMarketClosed.setAccessible(true);

        // 1. 周一上午 10:00 (交易中) -> False
        service.setClock(createFixedClock("2023-10-23T10:00:00")); // 2023-10-23 is Monday
        assertFalse((boolean) isMarketClosed.invoke(service), "周一上午 10:00 应该是交易时间");

        // 2. 周一凌晨 01:00 (休市，周一无夜盘) -> True
        service.setClock(createFixedClock("2023-10-23T01:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "周一凌晨 01:00 应该是休市时间");

        // 3. 周二凌晨 01:00 (交易中，夜盘) -> False
        service.setClock(createFixedClock("2023-10-24T01:00:00"));
        assertFalse((boolean) isMarketClosed.invoke(service), "周二凌晨 01:00 应该是交易时间");

        // 4. 周二凌晨 03:00 (休市) -> True
        service.setClock(createFixedClock("2023-10-24T03:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "周二凌晨 03:00 应该是休市时间");

        // 5. 周六凌晨 02:00 (交易中) -> False
        service.setClock(createFixedClock("2023-10-28T02:00:00")); // 2023-10-28 is Saturday
        assertFalse((boolean) isMarketClosed.invoke(service), "周六凌晨 02:00 应该是交易时间");

        // 6. 周六凌晨 03:00 (休市) -> True
        service.setClock(createFixedClock("2023-10-28T03:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "周六凌晨 03:00 应该是休市时间");

        // 7. 周日全天 (休市) -> True
        service.setClock(createFixedClock("2023-10-29T12:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "周日应该是休市时间");

        // 8. 午间休市 12:00 -> True
        service.setClock(createFixedClock("2023-10-23T12:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "午间 12:00 应该是休市时间");

        // 9. 傍晚休市 18:00 -> True
        service.setClock(createFixedClock("2023-10-23T18:00:00"));
        assertTrue((boolean) isMarketClosed.invoke(service), "傍晚 18:00 应该是休市时间");
    }

    @Test
    void testPriceCalculationDuringClosedMarket() throws Exception {
        PriceService service = new PriceService();
        
        // 设置为休市时间 (周日)
        service.setClock(createFixedClock("2023-10-29T12:00:00"));

        Map<String, Double> prices = new HashMap<>();
        prices.put("international", 2000.0);
        prices.put("rate", 7.3);
        prices.put("domestic", 0.0); // 模拟未获取到或需要覆盖

        // 调用私有方法 validateAndFixDomesticPrice
        Method validate = PriceService.class.getDeclaredMethod("validateAndFixDomesticPrice", Map.class);
        validate.setAccessible(true);
        validate.invoke(service, prices);

        // 预期计算值: (2000 / 31.1034768) * 7.3 ≈ 469.40
        double expected = (2000.0 / 31.1034768) * 7.3;
        assertEquals(expected, prices.get("domestic"), 0.01, "休市期间应强制使用计算值");
    }

    @Test
    void testPriceFetchIntegration() {
        // 简单的集成测试，确保无异常抛出
        PriceService service = new PriceService();
        Map<String, Double> prices = service.fetchPrices();
        assertNotNull(prices);
    }
}
