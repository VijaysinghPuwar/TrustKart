package com.vijaysinghpuwar.trustkart.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money crosses the API boundary as a decimal string ("1799.99"), never a JSON number, so no client parses
 * it into a binary float. Amounts are always scale 2; HALF_EVEN is used wherever rounding is unavoidable.
 */
public final class MoneyWire {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyWire() {}

    public static String format(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING).toPlainString();
    }
}
