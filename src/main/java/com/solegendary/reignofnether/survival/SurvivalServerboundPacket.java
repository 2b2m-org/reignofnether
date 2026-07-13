package com.solegendary.reignofnether.survival;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.network.FriendlyByteBuf;


public class SurvivalServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SurvivalServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:survival_serverbound");
    public static final StreamCodec<FriendlyByteBuf, SurvivalServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(SurvivalServerboundPacket::encode, SurvivalServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<SurvivalServerboundPacket> type() {
        return TYPE;
    }

    public WaveDifficulty difficulty;
    public int waveNumber;

    // copies the gamemode to all other clients
    public static void startSurvivalMode(WaveDifficulty mode) {
        PacketDistributor.sendToServer(new SurvivalServerboundPacket(mode, 0));
    }

    // copies the gamemode to all other clients
    public static void setWaveNumber(int number) {
        if (number > 0)
            PacketDistributor.sendToServer(new SurvivalServerboundPacket(WaveDifficulty.BEGINNER, number));
    }

    public SurvivalServerboundPacket(WaveDifficulty gameMode, int waveNumber) {
        this.difficulty = gameMode;
        this.waveNumber = waveNumber;
    }

    public SurvivalServerboundPacket(FriendlyByteBuf buffer) {
        this.difficulty = buffer.readEnum(WaveDifficulty.class);
        this.waveNumber = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.difficulty);
        buffer.writeInt(this.waveNumber);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (this.waveNumber <= 0) {
                ReignOfNether.LOGGER.info("[Survival] Enabling survival mode with difficulty: {}", difficulty);
                SurvivalServerEvents.enable(difficulty);
            } else {
                ReignOfNether.LOGGER.info("[Survival] Setting wave number to: {}", waveNumber);
                SurvivalServerEvents.setWaveNumber(waveNumber);
            }
        });
    }
}