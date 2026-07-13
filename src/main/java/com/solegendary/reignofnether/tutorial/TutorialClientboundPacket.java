package com.solegendary.reignofnether.tutorial;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class TutorialClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TutorialClientboundPacket> TYPE =
        payloadType("tutorial_clientbound");
    public static final StreamCodec<FriendlyByteBuf, TutorialClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(TutorialClientboundPacket::encode, TutorialClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<TutorialClientboundPacket> type() {
        return TYPE;
    }

    private final TutorialAction action;
    private final TutorialStage stage;

    public static void enableTutorial() {
        PacketDistributor.sendToAllPlayers(new TutorialClientboundPacket(TutorialAction.ENABLE, TutorialStage.INTRO));
    }
    public static void disableTutorial() {
        PacketDistributor.sendToAllPlayers(new TutorialClientboundPacket(TutorialAction.DISABLE, TutorialStage.INTRO));
    }
    public static void loadTutorialStage(TutorialStage stage) {
        PacketDistributor.sendToAllPlayers(new TutorialClientboundPacket(TutorialAction.LOAD_STAGE, stage));
    }

    public TutorialClientboundPacket(TutorialAction action, TutorialStage stage) {
        this.action = action;
        this.stage = stage;
    }

    public TutorialClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(TutorialAction.class);
        this.stage = buffer.readEnum(TutorialStage.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeEnum(this.stage);
    }

    // client-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                    switch (action) {
                        case ENABLE -> TutorialClientEvents.setEnabled(true);
                        case DISABLE -> TutorialClientEvents.setEnabled(false);
                        case LOAD_STAGE -> TutorialClientEvents.loadStage(stage);
                    }
                }
        });
    }
}
