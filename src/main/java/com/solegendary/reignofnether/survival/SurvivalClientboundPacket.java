package com.solegendary.reignofnether.survival;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class SurvivalClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SurvivalClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:survival_clientbound");
    public static final StreamCodec<FriendlyByteBuf, SurvivalClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(SurvivalClientboundPacket::encode, SurvivalClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<SurvivalClientboundPacket> type() {
        return TYPE;
    }

    SurvivalSyncAction action;
    WaveDifficulty difficulty;
    long value;

    public static void enableAndSetDifficulty(WaveDifficulty diff) {
        PacketDistributor.sendToAllPlayers(new SurvivalClientboundPacket(SurvivalSyncAction.ENABLE_AND_SET_DIFFICULTY, diff, 0, 0L));
    }

    public static void setWaveNumber(long waveNumber) {
        PacketDistributor.sendToAllPlayers(new SurvivalClientboundPacket(SurvivalSyncAction.SET_WAVE_NUMBER, WaveDifficulty.EASY, waveNumber, 0L));
    }

    public static void setWaveRandomSeed(long seed) {
        PacketDistributor.sendToAllPlayers(new SurvivalClientboundPacket(SurvivalSyncAction.SET_WAVE_RANDOM_SEED, WaveDifficulty.EASY, seed, 0L));
    }

    public SurvivalClientboundPacket(SurvivalSyncAction action, WaveDifficulty difficulty, long value, long bonusTicks) {
        this.action = action;
        this.difficulty = difficulty;
        this.value = value;
    }

    public SurvivalClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(SurvivalSyncAction.class);
        this.difficulty = buffer.readEnum(WaveDifficulty.class);
        this.value = buffer.readLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeEnum(this.difficulty);
        buffer.writeLong(this.value);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        switch (action) {
                            case ENABLE_AND_SET_DIFFICULTY -> SurvivalClientEvents.enable(difficulty);
                            case SET_WAVE_NUMBER -> SurvivalClientEvents.setWaveNumber(value);
                            case SET_WAVE_RANDOM_SEED -> SurvivalClientEvents.setRandomSeed(value);
                        }
                    }
        });
    }
}
