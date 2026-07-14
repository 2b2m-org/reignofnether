package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotWorldViewTest {
    @Test
    void aggressiveNeutralThreatsFollowTheGamerule() {
        assertTrue(BotWorldView.isAggressiveNeutralThreat(true, "", true));
        assertFalse(BotWorldView.isAggressiveNeutralThreat(false, "", true));
        assertFalse(BotWorldView.isAggressiveNeutralThreat(true, "", false));
        assertFalse(BotWorldView.isAggressiveNeutralThreat(true, "player", true));
    }
}
