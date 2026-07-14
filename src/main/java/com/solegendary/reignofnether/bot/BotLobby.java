package com.solegendary.reignofnether.bot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.startpos.StartPos;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class BotLobby {
    private BotLobby() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("lobby")
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("villagers", "monsters", "piglins"), builder))
                                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("easy", "medium", "hard"), builder))
                                                .then(Commands.argument("personality", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                List.of("steady", "rusher", "turtle"), builder))
                                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                                .executes(context -> add(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        StringArgumentType.getString(context, "faction"),
                                                                        StringArgumentType.getString(context, "difficulty"),
                                                                        StringArgumentType.getString(context, "personality"),
                                                                        BlockPosArgument.getBlockPos(context, "pos")
                                                                ))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotLobby::suggestNames)
                                .executes(context -> remove(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list").executes(context -> list(context.getSource())));
    }

    private static int add(CommandSourceStack source, String displayName,
                           String factionName, String difficultyName,
                           String personalityName, BlockPos pos) {
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("RTS lobby bots can only be added from the Overworld."));
            return 0;
        }
        if (PlayerServerEvents.isGameActive()) {
            source.sendFailure(Component.literal("RTS lobby bots cannot be changed during an active match."));
            return 0;
        }
        if (StartPosServerEvents.isStartingGame()) {
            source.sendFailure(Component.literal("RTS lobby bots cannot be changed during the countdown."));
            return 0;
        }

        Faction faction = BotServerEvents.parseFaction(factionName);
        if (faction == null) {
            source.sendFailure(Component.literal(
                    "Unknown faction '" + factionName + "'. Use villagers, monsters, or piglins."));
            return 0;
        }
        BotDifficulty difficulty = BotDifficulty.fromName(difficultyName).orElse(null);
        if (difficulty == null) {
            source.sendFailure(Component.literal(
                    "Unknown difficulty '" + difficultyName + "'. Use easy, medium, or hard."));
            return 0;
        }
        BotPersonality personality = BotPersonality.fromName(personalityName).orElse(null);
        if (personality == null) {
            source.sendFailure(Component.literal(
                    "Unknown personality '" + personalityName + "'. Use steady, rusher, or turtle."));
            return 0;
        }
        Optional<String> nameError = BotServerEvents.validateBotDisplayName(
                displayName, BotServerEvents.usedAiDisplayNames());
        if (nameError.isPresent()) {
            source.sendFailure(Component.literal(nameError.get()));
            return 0;
        }

        StartPos startPos = StartPosServerEvents.getStartPos(pos);
        if (startPos == null) {
            source.sendFailure(Component.literal("No RTS start position exists at " + pos.toShortString() + "."));
            return 0;
        }
        if (!startPos.enabled) {
            source.sendFailure(Component.literal("The RTS start position at " + pos.toShortString() + " is disabled."));
            return 0;
        }
        if (startPos.isOccupied()) {
            source.sendFailure(Component.literal("The RTS start position at " + pos.toShortString() + " is occupied."));
            return 0;
        }

        String ownerName = RTSPlayer.createAiOwnerName();
        if (BotServerEvents.planLobbyBotStart(
                level, ownerName, displayName, faction, difficulty, personality, startPos).isEmpty()) {
            source.sendFailure(Component.literal(
                    "The start position cannot be used for " + displayName + "'s capitol."));
            return 0;
        }

        startPos.reserveBot(ownerName, displayName, faction, difficulty, personality);
        StartPosServerEvents.updateCountdown(true);
        source.sendSuccess(() -> Component.literal("Added lobby bot " + displayName + " ("
                + faction.name().toLowerCase(Locale.ROOT) + ", "
                + difficulty.name().toLowerCase(Locale.ROOT) + ", "
                + personality.name().toLowerCase(Locale.ROOT) + ") at "
                + pos.toShortString()), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String displayName) {
        if (PlayerServerEvents.isGameActive()) {
            source.sendFailure(Component.literal("RTS lobby bots cannot be changed during an active match."));
            return 0;
        }
        StartPos startPos = findByDisplayName(displayName);
        if (startPos == null) {
            source.sendFailure(Component.literal("No pending lobby bot named '" + displayName + "' exists."));
            return 0;
        }

        boolean cancelCountdown = StartPosServerEvents.isStartingGame();
        startPos.reset();
        if (cancelCountdown)
            StartPosServerEvents.cancelStartGameCountdown(true);
        else
            StartPosServerEvents.updateCountdown(false);
        source.sendSuccess(() -> Component.literal("Removed lobby bot " + displayName), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<StartPos> bots = StartPosServerEvents.startPoses.stream()
                .filter(startPos -> startPos.aiControlled)
                .sorted((first, second) -> first.displayName.compareToIgnoreCase(second.displayName))
                .toList();
        if (bots.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No AI-controlled RTS bots are waiting in the lobby."), false);
            return 1;
        }
        for (StartPos bot : bots) {
            source.sendSuccess(() -> Component.literal(bot.displayName
                    + " state=lobby faction=" + bot.faction.name().toLowerCase(Locale.ROOT)
                    + " difficulty=" + bot.aiDifficulty.name().toLowerCase(Locale.ROOT)
                    + " personality=" + bot.aiPersonality.name().toLowerCase(Locale.ROOT)
                    + " pos=" + bot.pos.toShortString()
                    + " ready=" + bot.ready), false);
        }
        return bots.size();
    }

    private static StartPos findByDisplayName(String displayName) {
        return StartPosServerEvents.startPoses.stream()
                .filter(startPos -> startPos.aiControlled)
                .filter(startPos -> startPos.displayName.equalsIgnoreCase(displayName))
                .findFirst()
                .orElse(null);
    }

    private static CompletableFuture<Suggestions> suggestNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> names = StartPosServerEvents.startPoses.stream()
                .filter(startPos -> startPos.aiControlled)
                .map(startPos -> startPos.displayName)
                .toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    public static Optional<String> startBots(ServerLevel level, List<StartPos> startPoses) {
        List<BotServerEvents.BotStartPlan> plans = new ArrayList<>();
        List<BotBuildingPlanner.Footprint> plannedFootprints = new ArrayList<>();
        for (StartPos startPos : startPoses) {
            if (!startPos.aiControlled)
                continue;
            Optional<BotServerEvents.BotStartPlan> plan = BotServerEvents.planLobbyBotStart(
                    level, startPos.ownerName, startPos.displayName, startPos.faction,
                    startPos.aiDifficulty, startPos.aiPersonality, startPos);
            if (plan.isEmpty())
                return Optional.of(startPos.displayName);
            BotServerEvents.BotStartPlan botPlan = plan.get();
            if (plannedFootprints.stream().anyMatch(
                    footprint -> botPlan.footprint().overlaps(footprint, 0)))
                return Optional.of(startPos.displayName);
            plans.add(botPlan);
            plannedFootprints.add(botPlan.footprint());
        }
        if (plans.isEmpty())
            return Optional.empty();

        List<BotServerEvents.MaterializedBot> materialized = new ArrayList<>();
        for (BotServerEvents.BotStartPlan plan : plans) {
            BotServerEvents.MaterializedBot bot = BotServerEvents.materializeBot(level, plan);
            if (bot == null) {
                materialized.forEach(BotServerEvents::rollbackMaterializedBot);
                PlayerServerEvents.saveRTSPlayers();
                return Optional.of(plan.displayName());
            }
            materialized.add(bot);
        }
        PlayerClientboundPacket.syncRtsGameTime(PlayerServerEvents.rtsGameTicks);
        PlayerServerEvents.saveRTSPlayers();
        return Optional.empty();
    }
}
