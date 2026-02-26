package com.goldpricetracker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExchangeRateService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    // 固定汇率: 1 USD = 6.84 CNY
    private static final double FIXED_RATE = 6.84;

    public double fetchUsdCnyRate() {
        return FIXED_RATE;
    }
}
