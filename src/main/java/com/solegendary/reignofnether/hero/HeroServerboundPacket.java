package com.solegendary.reignofnether.hero;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class HeroServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HeroServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:hero_serverbound");
    public static final StreamCodec<FriendlyByteBuf, HeroServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(HeroServerboundPacket::encode, HeroServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<HeroServerboundPacket> type() {
        return TYPE;
    }

    private final int unitId;
    private final HeroAction heroAction;

    public static void requestHeroSync(int unitId) {
        PacketDistributor.sendToServer(new HeroServerboundPacket(unitId, HeroAction.REQUEST_SYNC));
    }

    public HeroServerboundPacket(
            int unitId,
            HeroAction heroAction
    ) {
        this.unitId = unitId;
        this.heroAction = heroAction;
    }

    public HeroServerboundPacket(FriendlyByteBuf buffer) {
        this.unitId = buffer.readInt();
        this.heroAction = buffer.readEnum(HeroAction.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.unitId);
        buffer.writeEnum(this.heroAction);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (heroAction == HeroAction.REQUEST_SYNC) {
                for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                    if (entity.getId() == this.unitId && entity instanceof HeroUnit hero) {
                        hero.syncToClients();
                    }
                }
            }
        });
    }
}
