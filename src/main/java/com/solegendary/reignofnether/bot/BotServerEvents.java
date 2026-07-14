package com.solegendary.reignofnether.bot;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BotServerEvents {
    private static final int TICK_GRANULARITY = 10;
    private static final Map<String, BotController> CONTROLLERS = new HashMap<>();
    private static boolean survivalAlliancesReady;

    private BotServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rts-bot")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("villagers", "monsters", "piglins"), builder))
                                        .executes(context -> addBot(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "faction"),
                                                BotDifficulty.MEDIUM,
                                                BlockPos.containing(context.getSource().getPosition())
                                        ))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> addBot(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "faction"),
                                                        BotDifficulty.MEDIUM,
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos")
                                                )))
                                        .then(addDifficultyBranch(BotDifficulty.EASY))
                                        .then(addDifficultyBranch(BotDifficulty.MEDIUM))
                                        .then(addDifficultyBranch(BotDifficulty.HARD)))))
                .then(Commands.literal("list").executes(context -> listBots(context.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotServerEvents::suggestBotNames)
                                .executes(context -> removeBot(
                                        context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("difficulty")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotServerEvents::suggestBotNames)
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("easy", "medium", "hard"), builder))
                                        .executes(context -> setDifficulty(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "level")
                                        )))))
                .then(Commands.literal("personality")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotServerEvents::suggestBotNames)
                                .then(Commands.argument("style", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("steady", "rusher", "turtle"), builder))
                                        .executes(context -> setPersonality(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "style")
                                        )))))
                .then(Commands.literal("test-speed")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotServerEvents::suggestBotNames)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setSpeed(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "enabled")
                                        )))))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addDifficultyBranch(BotDifficulty difficulty) {
        String name = difficulty.name().toLowerCase(Locale.ROOT);
        return Commands.literal(name)
                .executes(context -> addBot(
                        context.getSource(),
                        StringArgumentType.getString(context, "name"),
                        StringArgumentType.getString(context, "faction"),
                        difficulty,
                        BlockPos.containing(context.getSource().getPosition())
                ))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> addBot(
                                context.getSource(),
                                StringArgumentType.getString(context, "name"),
                                StringArgumentType.getString(context, "faction"),
                                difficulty,
                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                        )));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % TICK_GRANULARITY != 0)
            return;
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null)
            return;
        if (SurvivalServerEvents.isEnabled()) {
            if (!survivalAlliancesReady) {
                AlliancesServerEvents.applyCoopAlliances();
                survivalAlliancesReady = true;
            }
        } else {
            survivalAlliancesReady = false;
        }

        List<RTSPlayer> players;
        synchronized (PlayerServerEvents.rtsPlayers) {
            players = new ArrayList<>(PlayerServerEvents.rtsPlayers);
        }

        Set<String> activeNames = new HashSet<>();
        for (RTSPlayer player : players) {
            if (!player.aiControlled || player.aiHomePos == null)
                continue;
            activeNames.add(player.name);
            CONTROLLERS.computeIfAbsent(player.name, BotController::new).tick(level);
        }
        CONTROLLERS.keySet().removeIf(name -> !activeNames.contains(name));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CONTROLLERS.clear();
        survivalAlliancesReady = false;
    }

    private static int addBot(CommandSourceStack source, String displayName, String factionName,
                              BotDifficulty difficulty,
                              BlockPos requestedHome) {
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("RTS bots can only be added from the Overworld."));
            return 0;
        }
        Faction faction = parseFaction(factionName);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction '" + factionName + "'. Use villagers, monsters, or piglins."));
            return 0;
        }
        if (displayName.length() > 32) {
            source.sendFailure(Component.literal("Bot names must be 32 characters or fewer."));
            return 0;
        }
        if (SurvivalServerEvents.ENEMY_OWNER_NAME.equalsIgnoreCase(displayName)) {
            source.sendFailure(Component.literal("'" + displayName + "' is reserved by Wave Survival."));
            return 0;
        }
        boolean nameInUse = PlayerServerEvents.rtsPlayers.stream()
                .anyMatch(player -> player.displayName.equalsIgnoreCase(displayName))
                || source.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(player -> player.getGameProfile().getName().equalsIgnoreCase(displayName));
        if (nameInUse) {
            source.sendFailure(Component.literal("An RTS player named '" + displayName + "' already exists."));
            return 0;
        }

        RTSPlayer bot = RTSPlayer.getNewAiBot(
                displayName, faction, requestedHome, difficulty, BotPersonality.STEADY);
        BotStrategy strategy = BotStrategy.forFaction(faction);
        var origin = BotBuildingPlanner.findPlacement(
                level, strategy.capitol(), requestedHome, bot.name, true);
        if (origin.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No flat, clear area was found for " + displayName + "'s capitol."));
            return 0;
        }
        BuildingPlacement preview = strategy.capitol().createBuildingPlacement(
                level, origin.get(), Rotation.NONE, bot.name);
        BlockPos home = BotBuildingPlanner.groundAt(level, preview.centrePos.getX(), preview.centrePos.getZ());
        bot.aiHomePos = home;

        PlayerServerEvents.rtsPlayers.add(bot);
        ResourcesServerEvents.assignResources(bot.name);
        ResourcesServerEvents.resetResources(bot.name);
        ResearchServerEvents.removeAllCheatsFor(bot.name);
        PlayerClientboundPacket.addRTSPlayer(bot);

        List<Entity> workers = spawnWorkers(level, bot, home);
        int[] workerIds = workers.stream().mapToInt(Entity::getId).toArray();
        BuildingPlacement capitol = BuildingServerEvents.placeBuilding(
                strategy.capitol(), origin.get(), Rotation.NONE, bot.name, workerIds, false, false);
        if (capitol == null || !BuildingServerEvents.getBuildings().contains(capitol)) {
            workers.forEach(Entity::discard);
            PlayerServerEvents.rtsPlayers.remove(bot);
            ResourcesServerEvents.resourcesList.removeIf(resources -> resources.ownerName.equals(bot.name));
            PlayerClientboundPacket.removeRTSPlayer(bot.name);
            source.sendFailure(Component.literal("Failed to place " + displayName + "'s capitol."));
            return 0;
        }

        if (SurvivalServerEvents.isEnabled()) {
            AlliancesServerEvents.applyCoopAlliances();
            survivalAlliancesReady = true;
        }
        CONTROLLERS.put(bot.name, new BotController(bot.name));
        PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.bot_added", true, displayName);
        PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.total_players", false, PlayerServerEvents.rtsPlayers.size());
        PlayerClientboundPacket.syncRtsGameTime(PlayerServerEvents.rtsGameTicks);
        PlayerServerEvents.saveRTSPlayers();

        ReignOfNether.LOGGER.info(
                "[Bot] added {} owner={} faction={} difficulty={} personality={} home={} capitol={}",
                displayName, bot.name, faction, difficulty, bot.aiPersonality, home, capitol.originPos);
        source.sendSuccess(() -> Component.literal("Added RTS bot " + displayName + " ("
                + factionName.toLowerCase(Locale.ROOT) + ", "
                + difficulty.name().toLowerCase(Locale.ROOT) + ", steady) at " + home.toShortString()), true);
        return 1;
    }

    private static List<Entity> spawnWorkers(ServerLevel level, RTSPlayer bot, BlockPos home) {
        EntityType<? extends Unit> workerType = switch (bot.faction) {
            case VILLAGERS -> EntityRegistrar.VILLAGER_UNIT.get();
            case MONSTERS -> EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get();
            case PIGLINS -> EntityRegistrar.GRUNT_UNIT.get();
            default -> throw new IllegalArgumentException("Unsupported bot faction: " + bot.faction);
        };

        List<Entity> workers = new ArrayList<>();
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            Entity entity = workerType.create(level);
            if (entity == null)
                continue;
            BlockPos feet = BotBuildingPlanner.groundAt(level, home.getX() + xOffset, home.getZ()).above();
            ((Unit) entity).setOwnerName(bot.name);
            entity.moveTo(feet, 0, 0);
            level.addFreshEntity(entity);
            workers.add(entity);
        }
        return workers;
    }

    private static int removeBot(CommandSourceStack source, String displayName) {
        RTSPlayer player = findAiBotByDisplayName(displayName);
        if (player == null) {
            source.sendFailure(Component.literal(
                    "No AI-controlled RTS bot named '" + displayName + "' exists."));
            return 0;
        }
        CONTROLLERS.remove(player.name);
        PlayerServerEvents.defeat(player.name, "removed by an operator");
        source.sendSuccess(() -> Component.literal("Removed RTS bot " + displayName), true);
        return 1;
    }

    private static int listBots(CommandSourceStack source) {
        List<BotController> controllers = CONTROLLERS.values().stream()
                .sorted((first, second) -> first.getDisplayName().compareToIgnoreCase(second.getDisplayName()))
                .toList();
        if (controllers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No AI-controlled RTS bots are active."), false);
            return 1;
        }
        for (BotController controller : controllers)
            source.sendSuccess(() -> Component.literal(controller.describe()), false);
        return controllers.size();
    }

    private static int setDifficulty(CommandSourceStack source, String displayName, String level) {
        RTSPlayer player = findAiBotByDisplayName(displayName);
        if (player == null) {
            source.sendFailure(Component.literal(
                    "No AI-controlled RTS bot named '" + displayName + "' exists."));
            return 0;
        }
        BotDifficulty difficulty = BotDifficulty.fromName(level).orElse(null);
        if (difficulty == null) {
            source.sendFailure(Component.literal("Unknown difficulty '" + level + "'. Use easy, medium, or hard."));
            return 0;
        }
        player.aiDifficulty = difficulty;
        PlayerServerEvents.saveRTSPlayers();
        source.sendSuccess(() -> Component.literal("Set " + displayName + " difficulty to "
                + difficulty.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }

    private static int setPersonality(CommandSourceStack source, String displayName, String style) {
        RTSPlayer player = findAiBotByDisplayName(displayName);
        if (player == null) {
            source.sendFailure(Component.literal(
                    "No AI-controlled RTS bot named '" + displayName + "' exists."));
            return 0;
        }
        BotPersonality personality = BotPersonality.fromName(style).orElse(null);
        if (personality == null) {
            source.sendFailure(Component.literal(
                    "Unknown personality '" + style + "'. Use steady, rusher, or turtle."));
            return 0;
        }
        player.aiPersonality = personality;
        PlayerServerEvents.saveRTSPlayers();
        source.sendSuccess(() -> Component.literal("Set " + displayName + " personality to "
                + personality.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }

    private static int setSpeed(CommandSourceStack source, String displayName, boolean enabled) {
        RTSPlayer player = findAiBotByDisplayName(displayName);
        if (player == null) {
            source.sendFailure(Component.literal(
                    "No AI-controlled RTS bot named '" + displayName + "' exists."));
            return 0;
        }

        for (String cheat : List.of("warpten", "operationcwal")) {
            if (enabled && !ResearchServerEvents.playerHasCheat(player.name, cheat)) {
                ResearchServerEvents.addCheat(player.name, cheat);
                ResearchClientboundPacket.addCheat(player.name, cheat);
            } else if (!enabled && ResearchServerEvents.playerHasCheat(player.name, cheat)) {
                ResearchServerEvents.removeCheat(player.name, cheat);
                ResearchClientboundPacket.removeCheat(player.name, cheat);
            }
        }
        source.sendSuccess(() -> Component.literal("Test-only speed cheats " + (enabled ? "enabled" : "disabled")
                + " for " + displayName), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBotNames(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        List<String> names = PlayerServerEvents.rtsPlayers.stream()
                .filter(player -> player.aiControlled)
                .map(player -> player.displayName)
                .toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }

    static RTSPlayer findAiBotByDisplayName(String displayName) {
        synchronized (PlayerServerEvents.rtsPlayers) {
            return PlayerServerEvents.rtsPlayers.stream()
                    .filter(player -> player.aiControlled)
                    .filter(player -> player.displayName.equalsIgnoreCase(displayName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Faction parseFaction(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "villager", "villagers" -> Faction.VILLAGERS;
            case "monster", "monsters" -> Faction.MONSTERS;
            case "piglin", "piglins" -> Faction.PIGLINS;
            default -> null;
        };
    }
}
