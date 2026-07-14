package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BotIdentityCommandTest {
    @BeforeEach
    @AfterEach
    void clearPlayers() {
        PlayerServerEvents.rtsPlayers.clear();
        PlayerServerEvents.postGameRtsPlayers.clear();
    }

    @Test
    void displayResolverFindsOnlyAiPlayersCaseInsensitively() {
        RTSPlayer human = RTSPlayer.getNewPlayer("Collision", Faction.VILLAGERS, 12);
        RTSPlayer bot = RTSPlayer.getNewBot(
                "ron-ai-c3285108-a155-4cf6-a307-c31a39c7ca1f", Faction.MONSTERS);
        bot.displayName = "Collision";
        bot.aiControlled = true;
        PlayerServerEvents.rtsPlayers.add(human);
        PlayerServerEvents.rtsPlayers.add(bot);

        assertAll(
                () -> assertSame(bot, BotServerEvents.findAiBotByDisplayName("cOlLiSiOn")),
                () -> assertNull(BotServerEvents.findAiBotByDisplayName(bot.name)),
                () -> assertNull(BotServerEvents.findAiBotByDisplayName("missing"))
        );
    }
}
