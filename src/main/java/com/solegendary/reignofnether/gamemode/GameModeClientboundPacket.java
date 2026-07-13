package com.solegendary.reignofnether.gamemode;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.startpos.StartPosClientEvents;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class GameModeClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameModeClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:game_mode_clientbound");
    public static final StreamCodec<FriendlyByteBuf, GameModeClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(GameModeClientboundPacket::encode, GameModeClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<GameModeClientboundPacket> type() {
        return TYPE;
    }

    public GameMode gameMode;

    // sets the gamemode of all players
    // unlocked and reset back to
    public static void setAndLockAllClientGameModes(GameMode mode) {
        PacketDistributor.sendToAllPlayers(new GameModeClientboundPacket(mode));
    }

    public GameModeClientboundPacket(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public GameModeClientboundPacket(FriendlyByteBuf buffer) {
        this.gameMode = buffer.readEnum(GameMode.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.gameMode);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        if (gameMode != GameMode.NONE) {
                            ClientGameModeHelper.gameModeLocked = true;
                            ClientGameModeHelper.gameMode = this.gameMode;
                            if (gameMode != GameMode.CLASSIC && StartPosClientEvents.hasReservedPos()) {
                                StartPosClientEvents.selectedFaction = Faction.NONE;
                                StartPosServerboundPacket.unreservePos(StartPosClientEvents.getPos().pos);
                            }
                        } else {
                            ClientGameModeHelper.gameModeLocked = false;
                        }
                    }
        });
    }
}
