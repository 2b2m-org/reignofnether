package com.solegendary.reignofnether.unit.packets;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;


public class UnitSyncMobEffectsClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnitSyncMobEffectsClientboundPacket> TYPE =
        payloadType("unit_sync_mob_effects_clientbound");
    public static final StreamCodec<FriendlyByteBuf, UnitSyncMobEffectsClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(UnitSyncMobEffectsClientboundPacket::encode, UnitSyncMobEffectsClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<UnitSyncMobEffectsClientboundPacket> type() {
        return TYPE;
    }

    private final int entityId;
    private final int effectId;
    private final int amplifier;
    private final int duration;

    public static void addEffectClientside(LivingEntity entity, MobEffectInstance mei) {
        PacketDistributor.sendToAllPlayers(new UnitSyncMobEffectsClientboundPacket(entity.getId(), BuiltInRegistries.MOB_EFFECT.getId(mei.getEffect().value()), mei.getAmplifier(), mei.getDuration())
        );
    }

    public static void removeEffectClientside(LivingEntity entity, Holder<MobEffect> me) {
        PacketDistributor.sendToAllPlayers(new UnitSyncMobEffectsClientboundPacket(entity.getId(), BuiltInRegistries.MOB_EFFECT.getId(me.value()), 0, 0)
        );
    }

    // packet-handler functions
    public UnitSyncMobEffectsClientboundPacket(
        int entityId,
        int descriptionId,
        int amplifier,
        int duration
    ) {
        this.entityId = entityId;
        this.effectId = descriptionId;
        this.amplifier = amplifier;
        this.duration = duration;
    }

    public UnitSyncMobEffectsClientboundPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.effectId = buffer.readInt();
        this.amplifier = buffer.readInt();
        this.duration = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeInt(this.effectId);
        buffer.writeInt(this.amplifier);
        buffer.writeInt(this.duration);
    }

    // client-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                    UnitClientEvents.syncMobEffect(this.entityId, this.effectId, this.amplifier, this.duration);
                }
        });
    }
}
