package com.solegendary.reignofnether.alliance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliancesServerEventsTest {

    @Test
    void botsDeclineCaptureFromAllies() {
        assertAll(
                () -> assertFalse(AlliancesServerEvents.isEligibleCaptureOwner(true, true)),
                () -> assertTrue(AlliancesServerEvents.isEligibleCaptureOwner(true, false)),
                () -> assertTrue(AlliancesServerEvents.isEligibleCaptureOwner(false, true)),
                () -> assertTrue(AlliancesServerEvents.isEligibleCaptureOwner(false, false))
        );
    }
}
