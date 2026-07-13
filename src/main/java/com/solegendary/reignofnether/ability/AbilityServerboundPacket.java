package com.solegendary.reignofnether.ability;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;


public class AbilityServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityServerboundPacket> TYPE =
        payloadType("ability_serverbound");
    public static final StreamCodec<FriendlyByteBuf, AbilityServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(AbilityServerboundPacket::encode, AbilityServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<AbilityServerboundPacket> type() {
        return TYPE;
    }

    private final int unitId;
    private final UnitAction unitAction;

    public static void rankUpAbility(int unitId, UnitAction abilityAction) {
        PacketDistributor.sendToServer(new AbilityServerboundPacket(unitId, abilityAction));
    }

    public AbilityServerboundPacket(
        int unitId,
        UnitAction unitAction
    ) {
        this.unitId = unitId;
        this.unitAction = unitAction;
    }

    public AbilityServerboundPacket(FriendlyByteBuf buffer) {
        this.unitId = buffer.readInt();
        this.unitAction = buffer.readEnum(UnitAction.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.unitId);
        buffer.writeEnum(this.unitAction);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {

            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                ReignOfNether.LOGGER.warn("AbilityServerboundPacket: Sender was null");
                return;
            }
            for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                if (entity.getId() == this.unitId && entity instanceof Unit unit) {

                    if (!player.getName().getString().equals(unit.getOwnerName())) {
                        ReignOfNether.LOGGER.warn("AbilityServerboundPacket: Tried to process packet from " + player.getName() + " for: " + unit.getOwnerName());
                        return;
                    }

                    for (Ability ability : unit.getAbilities().get()) {
                        if (ability.action == this.unitAction && ability instanceof HeroAbility heroAbility && unit instanceof HeroUnit hero) {
                            ReignOfNether.LOGGER.info("[Ability] {} ranked up ability {} on unit {}", player.getName(), this.unitAction, this.unitId);
                            heroAbility.rankUp(hero);
                        }
                    }
                }
            }
        });
    }
}
