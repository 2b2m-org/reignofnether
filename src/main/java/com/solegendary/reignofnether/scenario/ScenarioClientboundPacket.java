package com.solegendary.reignofnether.scenario;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;


public class ScenarioClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScenarioClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:scenario_clientbound");
    public static final StreamCodec<FriendlyByteBuf, ScenarioClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(ScenarioClientboundPacket::encode, ScenarioClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<ScenarioClientboundPacket> type() {
        return TYPE;
    }

    public ScenarioAction action;
    public CompoundTag roleNbt;

    public ScenarioClientboundPacket(ScenarioAction action, CompoundTag roleNbt) {
        this.action = action;
        this.roleNbt = roleNbt;
    }

    public ScenarioClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(ScenarioAction.class);
        this.roleNbt = buffer.readNbt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeNbt(this.roleNbt);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            {

                switch (this.action) {
                    case LOAD_SCENARIO_ROLE -> {
                        int index = roleNbt.getInt("index");
                        for (int i = 0; i < ScenarioClientEvents.scenarioRoles.size(); i++) {
                            if (ScenarioClientEvents.scenarioRoles.get(i).index == index) {
                                ScenarioClientEvents.scenarioRoles.get(i).nbt = roleNbt;
                                ScenarioClientEvents.scenarioRoles.get(i).unpackNbt();
                                break;
                            }
                        }
                    }
                }
            }
        });
    }
}
