package com.solegendary.reignofnether.tutorial;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.FriendlyByteBuf;


public class TutorialServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TutorialServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:tutorial_serverbound");
    public static final StreamCodec<FriendlyByteBuf, TutorialServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(TutorialServerboundPacket::encode, TutorialServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<TutorialServerboundPacket> type() {
        return TYPE;
    }

    TutorialAction action;
    TutorialStage stage;

    public static void doServerAction(TutorialAction action) {
        PacketDistributor.sendToServer(new TutorialServerboundPacket(action, TutorialStage.INTRO));
    }

    public static void saveStage(TutorialStage stage) {
        PacketDistributor.sendToServer(new TutorialServerboundPacket(TutorialAction.SAVE_STAGE, stage));
    }

    // packet-handler functions
    public TutorialServerboundPacket(TutorialAction action, TutorialStage stage) {
        this.action = action;
        this.stage = stage;
    }

    public TutorialServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(TutorialAction.class);
        this.stage = buffer.readEnum(TutorialStage.class);
    }

    public void encode(FriendlyByteBuf buffer)  {
        buffer.writeEnum(this.action);
        buffer.writeEnum(this.stage);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (action) {
                case SAVE_STAGE -> TutorialServerEvents.saveStage(stage);
                case SET_DAY_TIME -> TutorialServerEvents.setDayTime();
                case SET_NIGHT_TIME -> TutorialServerEvents.setNightTime();
                case SPAWN_ANIMALS -> TutorialServerEvents.spawnAnimals();
                case SPAWN_MONSTERS_A -> TutorialServerEvents.spawnMonstersA();
                case ATTACK_WITH_MONSTERS_A -> TutorialServerEvents.attackWithMonstersA();
                case SPAWN_MONSTERS_B -> TutorialServerEvents.spawnMonstersB();
                case ATTACK_WITH_MONSTERS_B -> TutorialServerEvents.attackWithMonstersB();
                case SPAWN_MONSTER_WORKERS -> TutorialServerEvents.spawnMonsterWorkers();
                case START_MONSTER_BASE -> TutorialServerEvents.startBuildingMonsterBase();
                case EXPAND_MONSTER_BASE_A -> TutorialServerEvents.expandMonsterBaseA();
                case EXPAND_MONSTER_BASE_B -> TutorialServerEvents.expandMonsterBaseB();
                case SPAWN_MONSTER_BASE_ARMY -> TutorialServerEvents.spawnMonsterBaseArmy();
                case SPAWN_FRIENDLY_ARMY -> TutorialServerEvents.spawnFriendlyArmy();
            }
        });
    }
}
