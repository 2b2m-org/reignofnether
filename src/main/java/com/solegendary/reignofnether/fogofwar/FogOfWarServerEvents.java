package com.solegendary.reignofnether.fogofwar;

import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;

import static com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers;

public class FogOfWarServerEvents {

    private static boolean enabled = false; // enforced for all clients
    private static ServerLevel serverLevel = null;

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        syncClientFog();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (enabled)
            sendMessageToAllPlayers("server.reignofnether.enabled_fog_of_war", true);
        else
            sendMessageToAllPlayers("server.reignofnether.disabled_fog_of_war", true);

        syncClientFog();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post evt) {
        if (evt.getLevel().isClientSide() || evt.getLevel().dimension() != Level.OVERWORLD)
            return;

        serverLevel = (ServerLevel) evt.getLevel();
    }

    // register here too for command blocks
    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-fog").then(Commands.literal("enable")
                .executes((command) -> {
                    setEnabled(true);
                    return 1;
                })));
        evt.getDispatcher().register(Commands.literal("rts-fog").then(Commands.literal("disable")
                .executes((command) -> {
                    setEnabled(false);
                    return 1;
                })));
    }

    // sets the fog to match what all
    private static void syncClientFog() {
        FogOfWarClientboundPacket.setEnabled(enabled);
    }

    // updates all blocks in the renderchunk to force all clients to match the server
    public static void syncClientBlocks(BlockPos renderChunkOrigin) {
        if (serverLevel == null)
            return;

        ArrayList<Pair<BlockPos, BlockState>> plants = new ArrayList<>();

        // The 16³ region lies entirely in one chunk — fetch it once instead of looking it up per read.
        LevelChunk chunk = serverLevel.getChunkAt(renderChunkOrigin);

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos bp = renderChunkOrigin.offset(x,y,z);
                    BlockState bs = chunk.getBlockState(bp);
                    if (bs.is(BlockTags.REPLACEABLE_BY_TREES) || bs.is(BlockTags.REPLACEABLE)) {
                        plants.add(new Pair<>(bp, bs));
                    }
                }
            }
        }
        for (Pair<BlockPos, BlockState> plant : plants)
            serverLevel.setBlockAndUpdate(plant.getFirst(), Blocks.AIR.defaultBlockState());

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos bp = renderChunkOrigin.offset(x,y,z);
                    BlockState bs = chunk.getBlockState(bp);
                    serverLevel.setBlockAndUpdate(bp, Blocks.BEDROCK.defaultBlockState());
                    serverLevel.setBlockAndUpdate(bp, bs);
                }
            }
        }
        plants.sort(Comparator.comparing(p -> ((Pair<BlockPos, BlockState>) p).getFirst().getY()).reversed());
        for (Pair<BlockPos, BlockState> plant : plants)
            serverLevel.setBlockAndUpdate(plant.getFirst(), plant.getSecond());

        FrozenChunkClientboundPacket.unmuteChunks();
    }
}
