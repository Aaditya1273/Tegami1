package com.tegami.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** A token amount in base units. Never a Double: sums and thresholds must be exact. */
data class Money(val units: Long, val decimals: Int, val symbol: String) : Comparable<Money> {
    init {
        require(units >= 0) { "negative amount" }
    }

    operator fun plus(other: Money): Money {
        require(other.decimals == decimals && other.symbol == symbol) { "mixed assets" }
        return copy(units = Math.addExact(units, other.units))
    }

    override fun compareTo(other: Money): Int {
        require(other.decimals == decimals && other.symbol == symbol) { "mixed assets" }
        return units.compareTo(other.units)
    }

    fun decimal(): BigDecimal = BigDecimal.valueOf(units, decimals)

    /** "1,250.00" (at least two decimals, more only when the amount has them). */
    fun formatNumber(locale: Locale = Locale.getDefault()): String {
        val f = DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale))
        f.maximumFractionDigits = decimals.coerceAtLeast(2)
        return f.format(decimal())
    }

    fun format(locale: Locale = Locale.getDefault()) = "${formatNumber(locale)} $symbol"

    companion object {
        fun zero(decimals: Int, symbol: String) = Money(0, decimals, symbol)

        /** Parses user input like "2000" or "2,000.50"; null if invalid, negative or too precise. */
        fun parse(text: String, decimals: Int, symbol: String): Money? {
            val clean = text.trim().replace(",", "")
            if (clean.isEmpty() || !clean.matches(Regex("""\d{1,12}(\.\d+)?"""))) return null
            val value = clean.toBigDecimal()
            if (value.scale() > decimals) return null
            return runCatching { Money(value.movePointRight(decimals).longValueExact(), decimals, symbol) }.getOrNull()
        }
    }
}
