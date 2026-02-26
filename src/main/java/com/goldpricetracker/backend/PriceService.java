package com.goldpricetracker.backend;

import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceService {
    private static final Logger logger = LoggerFactory.getLogger(PriceService.class);
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    // API Endpoints
    // hf_XAU: 伦敦金
    // gds_AUTD: 上海金
    // USDCNY: 美元兑人民币汇率 (新浪源)
    private static final String SINA_API_URL = "http://hq.sinajs.cn/list=hf_XAU,gds_AUTD,USDCNY";
    private static final String BINANCE_API_URL = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT";
    private static final String COINBASE_API_URL = "https://api.coinbase.com/v2/prices/PAXG-USD/spot";

    // 缓存上一次的有效价格，用于防止瞬间网络波动显示 0
    private double lastInternational = 0.0;
    private double lastDomestic = 0.0;
    private double lastRate = 7.20; // 默认汇率兜底

    public PriceService() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    /**
     * 获取实时金价
     */
    public Map<String, Double> fetchPrices() {
        Map<String, Double> prices = new HashMap<>();
        
        // 1. 获取基础数据 (新浪接口同时提供伦敦金、上海金、汇率)
        fetchFromSina(prices);
        
        // 2. 如果新浪接口的国际金价无效，尝试 Binance/Coinbase
        if (prices.getOrDefault("international", 0.0) <= 0) {
            fetchFromBinanceOrCoinbase(prices);
        }
        
        // 3. 校验并修正国内金价
        validateAndFixDomesticPrice(prices);
        
        // 更新缓存
        if (prices.getOrDefault("international", 0.0) > 0) lastInternational = prices.get("international");
        if (prices.getOrDefault("domestic", 0.0) > 0) lastDomestic = prices.get("domestic");
        if (prices.getOrDefault("rate", 0.0) > 0) lastRate = prices.get("rate");
        
        return prices;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 判断当前是否为国内金休市时间
     * 交易时间：
     * 周一至周五：09:00-11:30, 13:30-15:30, 20:00-02:30(次日)
     * 周末休市
     */
    private boolean isMarketClosed() {
        // 使用注入的 Clock 获取当前时间，方便测试
        LocalDateTime now = LocalDateTime.now(clock);
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 周日全天休市
        if (day == DayOfWeek.SUNDAY) {
            return true;
        }

        // 周六 02:30 之后休市
        if (day == DayOfWeek.SATURDAY) {
            return time.isAfter(LocalTime.of(2, 30));
        }

        // 周一 09:00 之前休市 (周一凌晨无夜盘)
        if (day == DayOfWeek.MONDAY) {
            if (time.isBefore(LocalTime.of(9, 0))) {
                return true;
            }
        } else {
            // 周二至周五 02:30 - 09:00 休市
            if (time.isAfter(LocalTime.of(2, 30)) && time.isBefore(LocalTime.of(9, 0))) {
                return true;
            }
        }

        // 午间休市 11:30 - 13:30
        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 30))) {
            return true;
        }

        // 傍晚休市 15:30 - 20:00
        if (time.isAfter(LocalTime.of(15, 30)) && time.isBefore(LocalTime.of(20, 0))) {
            return true;
        }

        return false;
    }

    private void validateAndFixDomesticPrice(Map<String, Double> prices) {
        double international = prices.getOrDefault("international", lastInternational);
        double domestic = prices.getOrDefault("domestic", 0.0);
        
        // 使用获取到的实时汇率，如果获取失败则使用缓存
        double rate = prices.getOrDefault("rate", lastRate);
        if (rate <= 0) rate = 7.20; // 最后的硬兜底
        
        // 计算理论国内金价 (CNY/g) = (国际金价 USD/oz / 31.1034768) * 汇率
        double calculatedDomestic = 0.0;
        if (international > 0) {
            calculatedDomestic = (international / 31.1034768) * rate;
        }
        
        boolean shouldUseCalculation = false;
        
        // 判断是否休市，如果休市则强制使用计算值
        if (isMarketClosed()) {
            if (calculatedDomestic > 0) {
                shouldUseCalculation = true;
                logger.info("当前为休市时间，强制使用计算值: " + calculatedDomestic);
            }
        } else {
            // 非休市时间，执行原有校验逻辑
            if (domestic <= 0) {
                shouldUseCalculation = true;
            } else if (calculatedDomestic > 0) {
                double diffPercent = Math.abs(domestic - calculatedDomestic) / calculatedDomestic;
                if (diffPercent > 0.05) {
                    shouldUseCalculation = true;
                }
            }
        }
        
        if (shouldUseCalculation && calculatedDomestic > 0) {
            prices.put("domestic", calculatedDomestic);
        } else if (domestic <= 0 && lastDomestic > 0) {
            prices.put("domestic", lastDomestic);
        }
        
        if (prices.getOrDefault("international", 0.0) <= 0 && lastInternational > 0) {
            prices.put("international", lastInternational);
        }
    }

    private void fetchFromSina(Map<String, Double> prices) {
        Request request = new Request.Builder()
            .url(SINA_API_URL)
            .header("Referer", "https://finance.sina.com.cn/")
            .header("User-Agent", "Mozilla/5.0")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                parseSinaResponse(responseBody, prices);
            }
        } catch (Exception e) {
            logger.warn("新浪接口获取失败: " + e.getMessage());
        }
    }

    private void parseSinaResponse(String rawData, Map<String, Double> prices) {
        String[] lines = rawData.split("\n");
        for (String line : lines) {
            try {
                if (line.contains("hf_XAU")) {
                    prices.put("international", parsePrice(line));
                } else if (line.contains("gds_AUTD")) {
                    prices.put("domestic", parsePrice(line));
                } else if (line.contains("USDCNY")) {
                    prices.put("rate", parseExchangeRate(line));
                }
            } catch (Exception ignored) {}
        }
    }

    private double parseExchangeRate(String line) {
        // hq_str_USDCNY="16:47:15,6.8366,6.8376,6.8687,379,6.8687,6.8687,6.8308,6.8371,美元人民币,2026-02-26"
        // 格式可能为: Time, Open, PreClose, Current, ...
        // 或者旧格式: Name, Current, ...
        int start = line.indexOf("\"");
        int end = line.lastIndexOf("\"");
        if (start > 0 && end > start) {
            String data = line.substring(start + 1, end);
            String[] parts = data.split(",");
            if (parts.length > 3) {
                // 如果第1个字段包含冒号，说明是时间，取第4个字段(索引3)作为当前价格
                if (parts[0].contains(":")) {
                    return Double.parseDouble(parts[3]);
                }
                // 否则假设是旧格式，取第2个字段
                return Double.parseDouble(parts[1]);
            }
        }
        return 0.0;
    }

    private void fetchFromBinanceOrCoinbase(Map<String, Double> prices) {
        try {
            double binancePrice = fetchJsonPrice(BINANCE_API_URL, "price");
            if (binancePrice > 0) {
                prices.put("international", binancePrice);
                return;
            }
            double coinbasePrice = fetchJsonPrice(COINBASE_API_URL, "data", "amount");
            if (coinbasePrice > 0) {
                prices.put("international", coinbasePrice);
            }
        } catch (Exception ignored) {}
    }

    private double fetchJsonPrice(String url, String... paths) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode node = mapper.readTree(response.body().string());
                for (String path : paths) {
                    if (node.has(path)) node = node.get(path);
                    else return 0.0;
                }
                return node.asDouble();
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double parsePrice(String line) {
        int start = line.indexOf("\"");
        int end = line.lastIndexOf("\"");
        if (start > 0 && end > start) {
            String data = line.substring(start + 1, end);
            String[] parts = data.split(",");
            if (parts.length > 0) {
                return Double.parseDouble(parts[0]);
            }
        }
        return 0.0;
    }
}