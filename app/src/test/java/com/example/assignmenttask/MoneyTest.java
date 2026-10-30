package com.example.assignmenttask;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import org.junit.Test;

public class MoneyTest {

    @Test
    public void formatsAmountsWithSymbolAndTwoDecimals() {
        assertEquals(Money.SYMBOL + "35.00", Money.format("35"));
        assertEquals(Money.SYMBOL + "1,234.50", Money.format("1234.5"));
        assertEquals(Money.SYMBOL + "0.00", Money.format("not a number"));
        assertEquals(new BigDecimal("130.50"), Money.parse("100").add(Money.parse("30.50")));
    }
}
