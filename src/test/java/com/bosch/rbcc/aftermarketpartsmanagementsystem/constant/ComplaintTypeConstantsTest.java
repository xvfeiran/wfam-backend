package com.bosch.rbcc.aftermarketpartsmanagementsystem.constant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplaintTypeConstantsTest {

    @Test
    void ba40_isAftermarket_notZeroKm() {
        assertFalse(ComplaintTypeConstants.isZeroKm("BA40"));
    }

    @Test
    void ba41_isAftermarket_notZeroKm() {
        assertFalse(ComplaintTypeConstants.isZeroKm("BA41"));
    }

    @Test
    void ba20_isZeroKm() {
        assertTrue(ComplaintTypeConstants.isZeroKm("BA20"));
    }

    @Test
    void ba35_isZeroKm() {
        assertTrue(ComplaintTypeConstants.isZeroKm("BA35"));
    }

    @Test
    void ba42_isZeroKm() {
        assertTrue(ComplaintTypeConstants.isZeroKm("BA42"));
    }

    @Test
    void null_isNotZeroKm() {
        assertFalse(ComplaintTypeConstants.isZeroKm(null));
    }

    @Test
    void blank_isNotZeroKm() {
        assertFalse(ComplaintTypeConstants.isZeroKm(""));
    }

    @Test
    void whitespace_isNotZeroKm() {
        assertFalse(ComplaintTypeConstants.isZeroKm("   "));
    }
}
