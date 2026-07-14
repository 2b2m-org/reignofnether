package com.solegendary.reignofnether.player;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.matchstart.MatchEndClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// Sent to all clients once a match ends, carrying the final scoreboard so the
// end-of-match stats screen (MatchEndScreen) can be rendered. Scores otherwise
// only exist server-side, so this is the only way the client learns them.
public class MatchStatsClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MatchStatsClientboundPacket> TYPE =
        payloadType("match_stats_clientbound");
    public static final StreamCodec<FriendlyByteBuf, MatchStatsClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(MatchStatsClientboundPacket::encode, MatchStatsClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<MatchStatsClientboundPacket> type() {
        return TYPE;
    }

    // one results-table row per player that took part in the match
    public static class MatchStatRow {
        public final String ownerName;
        public final String displayName;
        public final Faction faction;
        public final boolean winner;
        public final int teamId; // startPosColorId - players sharing it are on the same team
        public final int[] scores; // ordered as RTSPlayerScoresEnum.values()

        public MatchStatRow(String ownerName, String displayName, Faction faction,
                            boolean winner, int teamId, int[] scores) {
            this.ownerName = ownerName;
            this.displayName = displayName;
            this.faction = faction;
            this.winner = winner;
            this.teamId = teamId;
            this.scores = scores;
        }
    }

    private final long gameDurationTicks;
    private final List<MatchStatRow> rows;

    public static void broadcast(long gameDurationTicks, List<MatchStatRow> rows) {
        PacketDistributor.sendToAllPlayers(new MatchStatsClientboundPacket(gameDurationTicks, rows));
    }

    public MatchStatsClientboundPacket(long gameDurationTicks, List<MatchStatRow> rows) {
        this.gameDurationTicks = gameDurationTicks;
        this.rows = rows;
    }

    public MatchStatsClientboundPacket(FriendlyByteBuf buffer) {
        this.gameDurationTicks = buffer.readLong();
        int n = buffer.readInt();
        if (n < 0)
            throw new DecoderException("Negative match stat row count: " + n);
        this.rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String ownerName = buffer.readUtf();
            String displayName = buffer.readUtf();
            Faction faction = buffer.readEnum(Faction.class);
            boolean winner = buffer.readBoolean();
            int teamId = buffer.readVarInt();
            int[] scores = buffer.readVarIntArray();
            this.rows.add(new MatchStatRow(
                    ownerName, displayName, faction, winner, teamId, scores));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(gameDurationTicks);
        buffer.writeInt(rows.size());
        for (MatchStatRow row : rows) {
            buffer.writeUtf(row.ownerName);
            buffer.writeUtf(row.displayName);
            buffer.writeEnum(row.faction);
            buffer.writeBoolean(row.winner);
            buffer.writeVarInt(row.teamId);
            buffer.writeVarIntArray(row.scores);
        }
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            {
                        MatchEndClientEvents.receive(gameDurationTicks, rows);
                    }
        });
    }
}
