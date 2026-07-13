package com.solegendary.reignofnether.gamemode;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.network.FriendlyByteBuf;


public class GameModeServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameModeServerboundPacket> TYPE =
        payloadType("game_mode_serverbound");
    public static final StreamCodec<FriendlyByteBuf, GameModeServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(GameModeServerboundPacket::encode, GameModeServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<GameModeServerboundPacket> type() {
        return TYPE;
    }

    public GameMode gameMode;

    // copies the gamemode to all other clients
    public static void setAndLockAllClientGameModes(GameMode mode) {
        PacketDistributor.sendToServer(new GameModeServerboundPacket(mode));
    }

    public GameModeServerboundPacket(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public GameModeServerboundPacket(FriendlyByteBuf buffer) {
        this.gameMode = buffer.readEnum(GameMode.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.gameMode);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ReignOfNether.LOGGER.info("[GameMode] Setting game mode to: {}", this.gameMode);
            GameModeClientboundPacket.setAndLockAllClientGameModes(this.gameMode);
        });
    }
}