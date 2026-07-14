package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.faction.Faction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RTSPlayerIdentityTest {
    @BeforeEach
    @AfterEach
    void clearPlayers() {
        PlayerServerEvents.rtsPlayers.clear();
        PlayerServerEvents.postGameRtsPlayers.clear();
    }

    @Test
    void generatedAiOwnerKeysExceedMinecraftUsernameLengthAndAreUnique() {
        RTSPlayer first = newBot(RTSPlayer.createAiOwnerName(), "First");
        PlayerServerEvents.rtsPlayers.add(first);
        RTSPlayer second = newBot(RTSPlayer.createAiOwnerName(), "Second");

        assertAll(
                () -> assertTrue(first.name.length() > 16),
                () -> assertTrue(second.name.length() > 16),
                () -> assertNotEquals(first.name, second.name),
                () -> assertTrue(first.id < 0),
                () -> assertTrue(second.id < first.id)
        );
    }

    @Test
    void humanAndBotCanShareADisplayNameWithoutSharingOwnership() {
        RTSPlayer bot = newBot(RTSPlayer.createAiOwnerName(), "Collision");
        PlayerServerEvents.rtsPlayers.add(bot);
        RTSPlayer human = RTSPlayer.getNewPlayer("Collision", Faction.VILLAGERS, 42);
        PlayerServerEvents.rtsPlayers.add(human);

        assertAll(
                () -> assertEquals(human.displayName, bot.displayName),
                () -> assertNotEquals(human.name, bot.name),
                () -> assertSame(human, PlayerServerEvents.getRTSPlayer("Collision")),
                () -> assertSame(bot, PlayerServerEvents.getRTSPlayer(bot.name)),
                () -> assertEquals("Collision", PlayerServerEvents.getPlayerDisplayName(bot.name))
        );
    }

    private static RTSPlayer newBot(String ownerName, String displayName) {
        RTSPlayer bot = RTSPlayer.getNewBot(ownerName, Faction.MONSTERS);
        bot.displayName = displayName;
        bot.aiControlled = true;
        return bot;
    }
}
