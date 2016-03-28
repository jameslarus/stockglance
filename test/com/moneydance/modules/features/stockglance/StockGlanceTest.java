package com.moneydance.modules.features.stockglance;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by larus on 3/28/16.
 */
public class StockGlanceTest {
    @Test
    public void testBackDays() {
        StockGlance sg = new StockGlance();
        int today = 20160328;
        assertTrue(sg.backDays(today, 0) == today);
        assertTrue(sg.backDays(today, 1) == 20160327);
        assertTrue(sg.backDays(today, 7) == 20160321);
        assertTrue(sg.backDays(today, 30) == 20160227);
        assertTrue(sg.backDays(today, 365) == 20150329);

        assertTrue(sg.backDays(20160301, 1) == 20160229);
        assertTrue(sg.backDays(20160301, 2) == 20160228);
        assertTrue(sg.backDays(20160301, 3) == 20160227);

        assertTrue(sg.backDays(20160101, 1) == 20151231);


    }
}