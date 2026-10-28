package app.geeklab.audiencelab.sdk.internal.util

import java.math.BigDecimal
import java.math.RoundingMode

internal object CurrencyValue {
    private const val SCALE = 8

    fun decimal(value: Double): BigDecimal {
        if (!value.isFinite()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
        }
        return normalize(BigDecimal.valueOf(value))
    }

    fun normalize(value: Double): Double = normalize(decimal(value)).toDouble()

    fun normalize(value: BigDecimal): BigDecimal {
        return value.setScale(SCALE, RoundingMode.HALF_UP)
    }

    fun add(left: Double, right: Double): Double {
        return normalize(decimal(left).add(decimal(right))).toDouble()
    }
}
