package com.solegendary.reignofnether.hero;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.unit.HeroUnitSave;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class FallenHeroClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FallenHeroClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:fallen_hero_clientbound");
    public static final StreamCodec<FriendlyByteBuf, FallenHeroClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(FallenHeroClientboundPacket::encode, FallenHeroClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<FallenHeroClientboundPacket> type() {
        return TYPE;
    }

    public String uuid;
    public String name;
    public String ownerName;
    public int experience;
    public int skillPoints;
    public int ability1Rank;
    public int ability2Rank;
    public int ability3Rank;
    public int ability4Rank;

    public static void addFallenHero(HeroUnitSave heroUnitSave) {
        PacketDistributor.sendToAllPlayers(new FallenHeroClientboundPacket(heroUnitSave));
    }

    public FallenHeroClientboundPacket(HeroUnitSave heroUnitSave) {
        this.uuid = heroUnitSave.uuid;
        this.name = heroUnitSave.name;
        this.ownerName = heroUnitSave.ownerName;
        this.experience = heroUnitSave.experience;
        this.skillPoints = heroUnitSave.skillPoints;
        this.ability1Rank = heroUnitSave.ability1Rank;
        this.ability2Rank = heroUnitSave.ability2Rank;
        this.ability3Rank = heroUnitSave.ability3Rank;
        this.ability4Rank = heroUnitSave.ability4Rank;
    }

    public FallenHeroClientboundPacket(FriendlyByteBuf buffer) {
        this.uuid = buffer.readUtf();
        this.name = buffer.readUtf();
        this.ownerName = buffer.readUtf();
        this.experience = buffer.readInt();
        this.skillPoints = buffer.readInt();
        this.ability1Rank = buffer.readInt();
        this.ability2Rank = buffer.readInt();
        this.ability3Rank = buffer.readInt();
        this.ability4Rank = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.uuid);
        buffer.writeUtf(this.name);
        buffer.writeUtf(this.ownerName);
        buffer.writeInt(this.experience);
        buffer.writeInt(this.skillPoints);
        buffer.writeInt(this.ability1Rank);
        buffer.writeInt(this.ability2Rank);
        buffer.writeInt(this.ability3Rank);
        buffer.writeInt(this.ability4Rank);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                    HeroClientEvents.addFallenHero(new HeroUnitSave(
                        uuid,
                        name,
                        ownerName,
                        experience,
                        skillPoints,
                        0,
                        ability1Rank,
                        ability2Rank,
                        ability3Rank,
                        ability4Rank
                    ));
                }
        });
    }
}
