package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.gamemode.ClientGameModeHelper;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.hud.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.matchstart.MatchStartClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public class StartPosClientEvents {

    // client player is considered to have reserved a spot if selectedFaction != NONE && startPosIndex >= 0
    public static ArrayList<StartPos> startPoses = new ArrayList<>();
    public static Faction selectedFaction = Faction.NONE;
    public static boolean isStarting = false; // game is counting down to start

    public static boolean isEnabled() {
        return ClientGameModeHelper.gameMode == GameMode.CLASSIC && !startPoses.isEmpty();
    }

    static void applySnapshot(List<StartPosClientboundPacket.PositionState> states,
                              int countdownTicks, boolean announceReadyChanges) {
        Map<BlockPos, StartPos> previous = new HashMap<>();
        for (StartPos startPos : startPoses)
            previous.put(startPos.pos, startPos);

        ArrayList<StartPos> updated = new ArrayList<>(states.size());
        for (StartPosClientboundPacket.PositionState state : states) {
            StartPos startPos = new StartPos(
                    state.pos(), state.faction(), state.ownerName(), state.displayName(),
                    state.aiControlled(), state.aiDifficulty(), state.aiPersonality(), state.colorId());
            startPos.enabled = state.enabled();
            startPos.ready = state.ready();
            updated.add(startPos);

            StartPos old = previous.get(state.pos());
            if (announceReadyChanges && old != null
                    && old.ownerName.equals(state.ownerName())
                    && !state.ownerName().isBlank()
                    && old.ready != state.ready()) {
                announceReadyChange(state.displayName(), state.ready(), states);
            }
        }

        startPoses.clear();
        startPoses.addAll(updated);
        syncSelectedFaction();
        MatchStartClientEvents.syncCountdown(countdownTicks);
    }

    private static void announceReadyChange(String playerName, boolean ready,
                                            List<StartPosClientboundPacket.PositionState> states) {
        if (MC.player == null)
            return;
        if (ready) {
            int readyPlayers = 0;
            int enabledPositions = 0;
            for (StartPosClientboundPacket.PositionState state : states) {
                if (state.enabled())
                    enabledPositions++;
                if (state.ready() && !state.ownerName().isBlank() && state.faction() != Faction.NONE)
                    readyPlayers++;
            }
            MC.player.sendSystemMessage(Component.translatable(
                    "startpos.reignofnether.player_ready", playerName, readyPlayers, enabledPositions));
        } else {
            MC.player.sendSystemMessage(Component.translatable(
                    "startpos.reignofnether.player_not_ready", playerName));
        }
    }

    private static void syncSelectedFaction() {
        StartPos localPos = getPos();
        selectedFaction = localPos == null ? Faction.NONE : localPos.faction;
    }

    public static boolean hasReservedPos() {
        return getPos() != null;
    }

    private static boolean isReady() {
        for (StartPos startPos : startPoses) {
            if (MC.player != null && startPos.isOwnedBy(MC.player.getName().getString()) && startPos.ready) {
                return true;
            }
        }
        return false;
    }

    public static Button getReadyButton() {
        return new Button("Ready",
                14,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/cross.png"),
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png"),
                null,
                () -> false,
                () -> !isEnabled() || isStarting || isReady(),
                () -> hasReservedPos() && selectedFaction != Faction.NONE,
                () -> {
                    if (MC.player != null)
                        StartPosServerboundPacket.readyPlayer();
                },
                null,
                getReadyButtonTooltip()
        );
    }

    private static List<FormattedCharSequence> getReadyButtonTooltip() {
        ArrayList<FormattedCharSequence> fcsList = new ArrayList<>();
        fcsList.add(fcs(I18n.get("startpos.reignofnether.ready_button.ready"), true));
        if (!hasReservedPos())
            fcsList.add(fcs(I18n.get("startpos.reignofnether.start_button.no_reserved_pos")));
        if (selectedFaction == Faction.NONE)
            fcsList.add(fcs(I18n.get("startpos.reignofnether.start_button.no_faction")));
        return fcsList;
    }


    public static Button getUnreadyButton() {
        return new Button("Unready",
                14,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/tick.png"),
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png"),
                null,
                () -> false,
                () -> !isEnabled() || !isReady(),
                () -> true,
                () -> {
                    if (MC.player != null)
                        StartPosServerboundPacket.unreadyPlayer();
                },
                null,
                List.of(
                        fcs(I18n.get("startpos.reignofnether.ready_button.unready"), true)
                )
        );
    }

    public static StartPos getPos() {
        for (StartPos startPos : startPoses)
            if (MC.player != null && startPos.isOwnedBy(MC.player.getName().getString()))
                return startPos;
        return null;
    }

    private static ResourceLocation getIcon() {
        if (getPos() == null)
            return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/block/rts_start_block_white.png");
        else
            return getPos().getIcon();
    }

    @SubscribeEvent
    public static void onChangeGamemode(PlayerEvent.PlayerChangeGameModeEvent evt) {
        StartPos startPos = getPos();
        if (evt.getEntity() == MC.player && startPos != null && MC.player != null) {
            selectedFaction = Faction.NONE;
            StartPosServerboundPacket.unreservePos(startPos.pos);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (!OrthoviewClientEvents.isEnabled() || MC.player == null)
            return;

        for (StartPos startPos : startPoses) {
            if (startPos.faction != Faction.NONE) {
                switch (startPos.faction) {
                    case VILLAGERS -> BuildingClientEvents.setBuildingToPlace(Buildings.TOWN_CENTRE);
                    case MONSTERS -> BuildingClientEvents.setBuildingToPlace(Buildings.MAUSOLEUM);
                    case PIGLINS -> BuildingClientEvents.setBuildingToPlace(Buildings.CENTRAL_PORTAL);
                }
                int forceColour = 2;
                if (startPos.isOwnedBy(MC.player.getName().getString()))
                    forceColour = 1;
                BuildingClientEvents.drawBuildingToPlace(evt.getPoseStack(), BuildingClientEvents.getBuildingOriginPos(startPos.pos), forceColour);
                BuildingClientEvents.setBuildingToPlace(null);
            }
        }
    }

    private static final Minecraft MC = Minecraft.getInstance();

    public static void resetAll() {
        selectedFaction = Faction.NONE;
        isStarting = false;
        for (StartPos startPos : startPoses)
            startPos.reset();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        // LOG OUT FROM SERVER WORLD ONLY
        if (MC.player != null && evt.getPlayer() != null && evt.getPlayer().getId() == MC.player.getId()) {
            resetAll();
            startPoses.clear();
        }
    }

    @SubscribeEvent
    public static void onPlayerLogoutEvent(PlayerEvent.PlayerLoggedOutEvent evt) {
        // LOG OUT FROM SINGLEPLAYER WORLD ONLY
        if (MC.player != null && evt.getEntity().getId() == MC.player.getId()) {
            resetAll();
            startPoses.clear();
        }
    }
}
