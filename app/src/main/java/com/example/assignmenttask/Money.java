package com.example.assignmenttask;

import java.math.BigDecimal;
import java.util.Locale;

/** Currency formatting for the Expenses feature. Change SYMBOL to switch currency everywhere. */
final class Money {

    static final String SYMBOL = "£";

    private Money() { }

    /** Reads an amount such as "12.50". Anything unreadable counts as zero. */
    static BigDecimal parse(String amount) {
        try {
            return new BigDecimal(amount == null ? "0" : amount.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** Formats an amount with the currency symbol and two decimals, for example "£1,234.50". */
    static String format(BigDecimal amount) {
        return SYMBOL + String.format(Locale.ROOT, "%,.2f", amount);
    }

    /** Formats an amount that is stored as text. */
    static String format(String amount) {
        return format(parse(amount));
    }
}
