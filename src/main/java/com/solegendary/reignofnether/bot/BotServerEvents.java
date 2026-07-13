package com.solegendary.reignofnether.bot;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.solegendary.reignofnether.ReignOfNether;
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
    private static final int DECISION_INTERVAL_TICKS = 20;
    private static final Map<String, BotController> CONTROLLERS = new HashMap<>();

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
                                                BlockPos.containing(context.getSource().getPosition())
                                        ))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> addBot(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "faction"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos")
                                                ))))))
                .then(Commands.literal("list").executes(context -> listBots(context.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(BotServerEvents::suggestBotNames)
                                .executes(context -> removeBot(
                                        context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("speed")
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % DECISION_INTERVAL_TICKS != 0)
            return;
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null)
            return;

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
    }

    private static int addBot(CommandSourceStack source, String name, String factionName, BlockPos requestedHome) {
        ServerLevel level = source.getLevel();
        Faction faction = parseFaction(factionName);
        if (faction == null) {
            source.sendFailure(Component.literal("Unknown faction '" + factionName + "'. Use villagers, monsters, or piglins."));
            return 0;
        }
        if (name.length() > 32) {
            source.sendFailure(Component.literal("Bot names must be 32 characters or fewer."));
            return 0;
        }
        if (PlayerServerEvents.isRTSPlayer(name)) {
            source.sendFailure(Component.literal("An RTS player named '" + name + "' already exists."));
            return 0;
        }

        BotStrategy strategy = BotStrategy.forFaction(faction);
        var origin = BotBuildingPlanner.findPlacement(level, strategy.capitol(), requestedHome, name, true);
        if (origin.isEmpty()) {
            source.sendFailure(Component.literal("No flat, clear area was found for " + name + "'s capitol."));
            return 0;
        }
        BuildingPlacement preview = strategy.capitol().createBuildingPlacement(level, origin.get(), Rotation.NONE, name);
        BlockPos home = BotBuildingPlanner.groundAt(level, preview.centrePos.getX(), preview.centrePos.getZ());

        RTSPlayer bot = RTSPlayer.getNewAiBot(name, faction, home);
        PlayerServerEvents.rtsPlayers.add(bot);
        ResourcesServerEvents.assignResources(name);
        ResourcesServerEvents.resetResources(name);
        ResearchServerEvents.removeAllCheatsFor(name);
        PlayerClientboundPacket.addRTSPlayer(name, faction, (long) bot.id, 0);

        List<Entity> workers = spawnWorkers(level, bot, home);
        int[] workerIds = workers.stream().mapToInt(Entity::getId).toArray();
        BuildingPlacement capitol = BuildingServerEvents.placeBuilding(
                strategy.capitol(), origin.get(), Rotation.NONE, name, workerIds, false, false);
        if (capitol == null || !BuildingServerEvents.getBuildings().contains(capitol)) {
            workers.forEach(Entity::discard);
            PlayerServerEvents.rtsPlayers.remove(bot);
            ResourcesServerEvents.resourcesList.removeIf(resources -> resources.ownerName.equals(name));
            PlayerClientboundPacket.removeRTSPlayer(name);
            source.sendFailure(Component.literal("Failed to place " + name + "'s capitol."));
            return 0;
        }

        level.setDayTime(500);
        CONTROLLERS.put(name, new BotController(name));
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.bot_added", true, name);
        PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.total_players", false, PlayerServerEvents.rtsPlayers.size());
        PlayerClientboundPacket.syncRtsGameTime(PlayerServerEvents.rtsGameTicks);
        PlayerServerEvents.saveRTSPlayers();

        ReignOfNether.LOGGER.info("[Bot] added {} faction={} home={} capitol={}", name, faction, home, capitol.originPos);
        source.sendSuccess(() -> Component.literal("Added RTS bot " + name + " (" + factionName.toLowerCase(Locale.ROOT)
                + ") at " + home.toShortString()), true);
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

    private static int removeBot(CommandSourceStack source, String name) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(name);
        if (player == null || !player.aiControlled) {
            source.sendFailure(Component.literal("No AI-controlled RTS bot named '" + name + "' exists."));
            return 0;
        }
        CONTROLLERS.remove(name);
        PlayerServerEvents.defeat(name, "removed by an operator");
        source.sendSuccess(() -> Component.literal("Removed RTS bot " + name), true);
        return 1;
    }

    private static int listBots(CommandSourceStack source) {
        List<BotController> controllers = CONTROLLERS.values().stream()
                .sorted((first, second) -> first.getOwnerName().compareToIgnoreCase(second.getOwnerName()))
                .toList();
        if (controllers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No AI-controlled RTS bots are active."), false);
            return 1;
        }
        for (BotController controller : controllers)
            source.sendSuccess(() -> Component.literal(controller.describe()), false);
        return controllers.size();
    }

    private static int setSpeed(CommandSourceStack source, String name, boolean enabled) {
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(name);
        if (player == null || !player.aiControlled) {
            source.sendFailure(Component.literal("No AI-controlled RTS bot named '" + name + "' exists."));
            return 0;
        }

        for (String cheat : List.of("warpten", "operationcwal")) {
            if (enabled && !ResearchServerEvents.playerHasCheat(name, cheat)) {
                ResearchServerEvents.addCheat(name, cheat);
                ResearchClientboundPacket.addCheat(name, cheat);
            } else if (!enabled && ResearchServerEvents.playerHasCheat(name, cheat)) {
                ResearchServerEvents.removeCheat(name, cheat);
                ResearchClientboundPacket.removeCheat(name, cheat);
            }
        }
        source.sendSuccess(() -> Component.literal("Accelerated bot timing " + (enabled ? "enabled" : "disabled")
                + " for " + name), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBotNames(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        List<String> names = PlayerServerEvents.rtsPlayers.stream()
                .filter(player -> player.aiControlled)
                .map(player -> player.name)
                .toList();
        return SharedSuggestionProvider.suggest(names, builder);
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
