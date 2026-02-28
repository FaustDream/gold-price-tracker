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
     * 2. 如果新浪接口的国际金价无效 (<=0)，尝试备用源 (Binance/Coinbase)。
     * 3. 对国内金价进行校验和修正（处理休市、数据异常等情况）。
     * 4. 更新缓存并返回结果。
     * 
     * @return 包含 'domestic', 'international', 'rate' 等键值的 Map
     */
    public Map<String, Double> fetchPrices() {
        Map<String, Double> prices = new HashMap<>();
        
        // 1. 获取基础数据 (新浪接口同时提供伦敦金、上海金、汇率)
        fetchFromSina(prices);
        
        // 2. 如果新浪接口的国际金价无效 (<=0)，尝试从备用源获取
        if (prices.getOrDefault("international", 0.0) <= 0) {
            fetchFromBinanceOrCoinbase(prices);
        }
        
        // 3. 校验并修正国内金价
        validateAndFixDomesticPrice(prices);
        
        // 4. 更新缓存：只要获取到大于 0 的有效值，就更新缓存
        if (prices.getOrDefault("international", 0.0) > 0) lastInternational = prices.get("international");
        if (prices.getOrDefault("domestic", 0.0) > 0) lastDomestic = prices.get("domestic");
        if (prices.getOrDefault("rate", 0.0) > 0) lastRate = prices.get("rate");
        
        return prices;
    }

    /**
     * 允许外部注入 Clock 对象，主要用于单元测试时固定时间
     * @param clock 时间对象
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 从新浪财经 API 获取原始数据。
     * 
     * 步骤：
     * 1. 构造 HTTP 请求。
     * 2. 解析返回的 JS 变量格式数据。
     * 3. 提取伦敦金、上海金和美元汇率。
     *
     * @param prices 用于存储解析结果的 Map
     */
    private void fetchFromSina(Map<String, Double> prices) {
        Request request = new Request.Builder()
            .url(SINA_API_URL)
            .addHeader("Referer", "http://finance.sina.com.cn")
            .build();
            
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String content = response.body().string();
                parseSinaResponse(content, prices);
            }
        } catch (IOException e) {
            logger.error("从新浪获取数据失败: " + e.getMessage());
        }
    }

    /**
     * 解析新浪 API 返回的特殊格式字符串。
     * 示例格式: var hq_str_hf_XAU="...";
     */
    private void parseSinaResponse(String content, Map<String, Double> prices) {
        // 1. 处理国际金价 (hf_XAU)
        if (content.contains("hf_XAU")) {
            String[] parts = extractData(content, "hf_XAU");
            if (parts.length > 0) {
                try {
                    prices.put("international", Double.parseDouble(parts[0]));
                } catch (NumberFormatException e) {
                    logger.warn("国际金价格式错误: " + parts[0]);
                }
            }
        }

        // 2. 处理国内金价 (gds_AUTD)
        if (content.contains("gds_AUTD")) {
            String[] parts = extractData(content, "gds_AUTD");
            if (parts.length > 0) {
                try {
                    prices.put("domestic", Double.parseDouble(parts[0]));
                } catch (NumberFormatException e) {
                    logger.warn("国内金价格式错误: " + parts[0]);
                }
            }
        }

        // 3. 处理汇率 (USDCNY)
        if (content.contains("USDCNY")) {
            String[] parts = extractData(content, "USDCNY");
            if (parts.length > 1) {
                try {
                    // 新浪汇率接口通常在第2个位置是中间价
                    prices.put("rate", Double.parseDouble(parts[1]));
                } catch (NumberFormatException e) {
                    logger.warn("汇率格式错误: " + parts[1]);
                }
            }
        }
    }

    /**
     * 辅助方法：从 JS 字符串中提取数组内容
     */
    private String[] extractData(String content, String symbol) {
        int start = content.indexOf(symbol + "=\"") + symbol.length() + 2;
        int end = content.indexOf("\"", start);
        if (start > symbol.length() + 1 && end > start) {
            return content.substring(start, end).split(",");
        }
        return new String[0];
    }

    /**
     * 备用数据源：当新浪失效时，从币安或 Coinbase 获取 Pax Gold (锚定黄金的代币) 价格。
     * Pax Gold 价格极度接近国际现货金价，是极佳的备用参考。
     */
    private void fetchFromBinanceOrCoinbase(Map<String, Double> prices) {
        // 1. 优先尝试币安
        if (fetchFromBinance(prices)) return;

        // 2. 币安失败则尝试 Coinbase
        fetchFromCoinbase(prices);
    }

    private boolean fetchFromBinance(Map<String, Double> prices) {
        Request request = new Request.Builder().url(BINANCE_API_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode node = mapper.readTree(response.body().string());
                double price = node.get("price").asDouble();
                if (price > 0) {
                    prices.put("international", price);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("从币安获取数据失败: " + e.getMessage());
        }
        return false;
    }

    private void fetchFromCoinbase(Map<String, Double> prices) {
        Request request = new Request.Builder().url(COINBASE_API_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode node = mapper.readTree(response.body().string());
                double price = node.get("data").get("amount").asDouble();
                if (price > 0) {
                    prices.put("international", price);
                }
            }
        } catch (Exception e) {
            logger.error("从 Coinbase 获取数据失败: " + e.getMessage());
        }
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
     * 1. 检查市场状态：
     *    - 如果开市：直接使用 API 返回的实时国内金价。
     *    - 如果休市：停止使用 API 的国内金价（因为它是收盘价，不会变动），
     *      改为使用 "国际金价 * 汇率" 实时计算，以提供参考。
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
        
        // 标记市场状态，传给前端
        boolean marketClosed = isMarketClosed();
        prices.put("market_closed", marketClosed ? 1.0 : 0.0);
        
        if (marketClosed) {
            // 休市期间：强制使用计算值，实现 24 小时动态更新
            if (calculatedDomestic > 0) {
                prices.put("domestic", calculatedDomestic);
                logger.debug("休市中，使用计算值: " + calculatedDomestic);
            } else if (lastDomestic > 0) {
                // 如果计算值也无效（例如拿不到国际金价），就保持最后的有效值
                prices.put("domestic", lastDomestic);
            }
        } else {
            // 开市期间：优先使用 API 返回的国内金价
            if (domestic > 0) {
                // 正常情况，不做修改，使用 API 值
                // 但如果 API 值异常（例如为0），则用计算值兜底
            } else if (calculatedDomestic > 0) {
                prices.put("domestic", calculatedDomestic);
            } else if (lastDomestic > 0) {
                prices.put("domestic", lastDomestic);
            }
        }
        
        // 国际金价兜底
        if (prices.getOrDefault("international", 0.0) <= 0 && lastInternational > 0) {
            prices.put("international", lastInternational);
        }
    }
}
