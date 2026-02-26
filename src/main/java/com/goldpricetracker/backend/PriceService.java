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

/**
 * 核心服务类：负责获取和处理黄金价格数据。
 * 功能包括：
 * 1. 从多个数据源（新浪财经、Binance、Coinbase）获取实时金价和汇率。
 * 2. 判断当前是否为交易休市时间。
 * 3. 校验国内金价数据的有效性，必要时通过国际金价和汇率进行估算。
 */
public class PriceService {
    private static final Logger logger = LoggerFactory.getLogger(PriceService.class);
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    // 使用 Clock 对象获取时间，便于单元测试时模拟特定时间
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    // API 数据源地址
    // hf_XAU: 伦敦金 (国际金价)
    // gds_AUTD: 上海金 (国内金价)
    // USDCNY: 美元兑人民币汇率 (用于换算)
    private static final String SINA_API_URL = "http://hq.sinajs.cn/list=hf_XAU,gds_AUTD,USDCNY";
    private static final String BINANCE_API_URL = "https://api.binance.com/api/v3/ticker/price?symbol=PAXGUSDT";
    private static final String COINBASE_API_URL = "https://api.coinbase.com/v2/prices/PAXG-USD/spot";

    // 缓存字段：用于保存上一次获取到的有效数据
    // 作用：当网络请求失败或数据源返回 0 时，使用缓存值避免界面显示 0.00
    private double lastInternational = 0.0;
    private double lastDomestic = 0.0;
    private double lastRate = 7.20; // 默认兜底汇率，防止首次启动无数据时计算异常

    public PriceService() {
        // 初始化 HTTP 客户端，设置超时时间为 10 秒
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    /**
     * 主方法：获取最新的价格数据
     * 流程：
     * 1. 尝试从新浪财经获取所有数据（国内金、国际金、汇率）。
     * 2. 如果新浪的国际金价失效，尝试从备用源（Binance/Coinbase）获取。
     * 3. 对国内金价进行校验和修正（处理休市、数据异常等情况）。
     * 4. 更新缓存并返回结果。
     * 
     * @return 包含 'domestic', 'international', 'rate' 等键值的 Map
     */
    public Map<String, Double> fetchPrices() {
        Map<String, Double> prices = new HashMap<>();
        
        // 1. 获取基础数据 (新浪接口同时提供伦敦金、上海金、汇率)
        fetchFromSina(prices);
        
        // 2. 如果新浪接口的国际金价无效 (<=0)，尝试 Binance/Coinbase
        if (prices.getOrDefault("international", 0.0) <= 0) {
            fetchFromBinanceOrCoinbase(prices);
        }
        
        // 3. 校验并修正国内金价
        validateAndFixDomesticPrice(prices);
        
        // 更新缓存：只要获取到大于 0 的有效值，就更新缓存
        if (prices.getOrDefault("international", 0.0) > 0) lastInternational = prices.get("international");
        if (prices.getOrDefault("domestic", 0.0) > 0) lastDomestic = prices.get("domestic");
        if (prices.getOrDefault("rate", 0.0) > 0) lastRate = prices.get("rate");
        
        return prices;
    }

    /**
     * 允许外部注入 Clock 对象，主要用于单元测试时固定时间
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 判断当前是否为国内黄金交易所 (上海黄金交易所) 的休市时间。
     * 
     * 为什么要判断休市？
     * 因为在休市期间，交易所接口返回的国内金价是收盘价，不再变动。
     * 而国际金价（伦敦金）是 24 小时交易的。
     * 为了让用户在休市期间也能看到参考的国内金价变动，我们需要在休市期间
     * 强制使用 "国际金价 * 汇率" 的公式来估算国内金价。
     * 
     * 交易时间规则：
     * 周一至周五：09:00-11:30, 13:30-15:30, 20:00-02:30(次日)
     * 周末休市
     */
    private boolean isMarketClosed() {
        // 使用注入的 Clock 获取当前时间 (默认为 Asia/Shanghai)
        LocalDateTime now = LocalDateTime.now(clock);
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 周日全天休市
        if (day == DayOfWeek.SUNDAY) {
            return true;
        }

        // 周六 02:30 之后休市 (周五夜盘结束)
        if (day == DayOfWeek.SATURDAY) {
            return time.isAfter(LocalTime.of(2, 30));
        }

        // 周一 09:00 之前休市 (周一凌晨无夜盘)
        if (day == DayOfWeek.MONDAY) {
            if (time.isBefore(LocalTime.of(9, 0))) {
                return true;
            }
        } else {
            // 周二至周五 02:30 - 09:00 休市 (夜盘结束到早盘开始之间)
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

    /**
     * 核心校验逻辑：决定最终显示的国内金价
     * 
     * 逻辑如下：
     * 1. 计算理论值：根据当前国际金价和汇率算出理论上的国内金价 (CNY/g)。
     *    公式：(国际金价 USD/oz / 31.1034768) * 汇率 USD/CNY
     * 2. 如果当前是休市时间 -> 强制使用理论计算值 (实现 24 小时动态更新)。
     * 3. 如果是非休市时间：
     *    - 如果接口返回的国内金价无效 (<=0) -> 使用计算值。
     *    - 如果接口值与计算值偏差过大 (>5%) -> 认为是接口数据异常，使用计算值。
     * 4. 兜底：如果以上都无效，尝试使用上次缓存的有效值。
     */
    private void validateAndFixDomesticPrice(Map<String, Double> prices) {
        double international = prices.getOrDefault("international", lastInternational);
        double domestic = prices.getOrDefault("domestic", 0.0);
        
        // 获取汇率，若失效则使用缓存或默认值
        double rate = prices.getOrDefault("rate", lastRate);
        if (rate <= 0) rate = 7.20; 
        
        // 计算理论国内金价
        double calculatedDomestic = 0.0;
        if (international > 0) {
            calculatedDomestic = (international / 31.1034768) * rate;
        }
        
        boolean shouldUseCalculation = false;
        
        // 判断是否休市
        if (isMarketClosed()) {
            if (calculatedDomestic > 0) {
                shouldUseCalculation = true;
                logger.info("当前为休市时间，强制使用计算值: " + calculatedDomestic);
            }
        } else {
            // 非休市时间，正常校验
            if (domestic <= 0) {
                shouldUseCalculation = true;
            } else if (calculatedDomestic > 0) {
                // 偏差校验：防止接口数据错乱
                double diffPercent = Math.abs(domestic - calculatedDomestic) / calculatedDomestic;
                if (diffPercent > 0.05) { // 偏差超过 5%
                    shouldUseCalculation = true;
                }
            }
        }
        
        // 应用决策结果
        if (shouldUseCalculation && calculatedDomestic > 0) {
            prices.put("domestic", calculatedDomestic);
        } else if (domestic <= 0 && lastDomestic > 0) {
            prices.put("domestic", lastDomestic);
        }
        
        // 国际金价兜底
        if (prices.getOrDefault("international", 0.0) <= 0 && lastInternational > 0) {
            prices.put("international", lastInternational);
        }
    }

    /**
     * 从新浪财经 API 获取数据
     * 注意：必须带上 Referer 头，否则可能被反爬虫拦截返回 403
     */
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

    /**
     * 解析新浪返回的 JavaScript 格式数据
     * 格式示例：var hq_str_hf_XAU="..."; var hq_str_gds_AUTD="...";
     */
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

    /**
     * 解析汇率字段
     * 针对新浪接口格式变化做了兼容处理
     */
    private double parseExchangeRate(String line) {
        // 新格式示例: hq_str_USDCNY="16:47:15,6.8366,6.8376,6.8687,379,..."
        // 旧格式示例: hq_str_USDCNY="美元人民币,6.8366,..."
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
                // 否则假设是旧格式，取第2个字段(索引1)
                return Double.parseDouble(parts[1]);
            }
        }
        return 0.0;
    }

    /**
     * 备用数据源：从 Binance 或 Coinbase 获取国际金价 (PAXG 币对)
     * 当新浪接口挂掉时使用
     */
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

    // 通用的 JSON 解析辅助方法
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

    // 解析常规价格字段 (取第1个字段)
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