package com.goldpricetracker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 汇率服务类
 * 
 * 功能：
 * 提供美元兑人民币 (USD/CNY) 的汇率数据。
 * 
 * 目前状态：
 * 这是一个占位类或备用类。
 * 在当前的 PriceService 实现中，汇率是直接从新浪财经 API (USDCNY) 获取的，
 * 并没有使用这个类。
 * 
 * 为什么保留它？
 * 1. 架构完整性：如果将来需要接入专门的汇率 API (如 Fixer.io, OpenExchangeRates)，可以在这里实现。
 * 2. 兜底策略：如果所有网络接口都挂了，这里可以提供一个写死的默认汇率，保证程序不崩溃。
 */
public class ExchangeRateService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    // 固定汇率: 1 USD = 6.84 CNY
    // 注意：这个值是写死的，在实际生产环境中应该动态获取。
    // 这里仅作为最后的兜底值。
    private static final double FIXED_RATE = 6.84;

    /**
     * 获取汇率
     * @return 当前汇率
     */
    public double fetchUsdCnyRate() {
        return FIXED_RATE;
    }
}
