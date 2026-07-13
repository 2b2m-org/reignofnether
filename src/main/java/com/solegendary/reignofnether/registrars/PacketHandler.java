package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AbilityServerboundPacket;
import com.solegendary.reignofnether.ability.BuildingAbilityClientboundPacket;
import com.solegendary.reignofnether.ability.BuildingAbilityServerboundPacket;
import com.solegendary.reignofnether.alliance.*;
import com.solegendary.reignofnether.attackwarnings.AttackWarningClientboundPacket;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingServerboundPacket;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientboundPacket;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerboundPacket;
import com.solegendary.reignofnether.config.ClientboundSyncResourceCostPacket;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerboundPacket;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;
import com.solegendary.reignofnether.fogofwar.FrozenChunkServerboundPacket;
import com.solegendary.reignofnether.gamemode.GameModeClientboundPacket;
import com.solegendary.reignofnether.gamemode.GameModeServerboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleServerboundPacket;
import com.solegendary.reignofnether.guiscreen.TopdownGuiServerboundPacket;
import com.solegendary.reignofnether.hero.FallenHeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroServerboundPacket;
import com.solegendary.reignofnether.minimap.MapMarkerClientboundPacket;
import com.solegendary.reignofnether.minimap.MapMarkerServerboundPacket;
import com.solegendary.reignofnether.player.MatchStatsClientboundPacket;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.research.ResearchServerboundPacket;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerboundPacket;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoClientboundPacket;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerboundPacket;
import com.solegendary.reignofnether.sandbox.SandboxServerboundPacket;
import com.solegendary.reignofnether.scenario.ScenarioClientboundPacket;
import com.solegendary.reignofnether.scenario.ScenarioServerboundPacket;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.startpos.StartPosClientboundPacket;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.survival.SurvivalClientboundPacket;
import com.solegendary.reignofnether.survival.SurvivalServerboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialClientboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialServerboundPacket;
import com.solegendary.reignofnether.unit.packets.*;
import com.solegendary.reignofnether.debug.RtsDebugChunksClientboundPacket;
import com.solegendary.reignofnether.debug.RtsDebugStatsClientboundPacket;


import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Registers all play-phase client/server payloads.
public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    private PacketHandler() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(TopdownGuiServerboundPacket.TYPE, TopdownGuiServerboundPacket.STREAM_CODEC, TopdownGuiServerboundPacket::handle);
        registrar.playToServer(UnitActionServerboundPacket.TYPE, UnitActionServerboundPacket.STREAM_CODEC, UnitActionServerboundPacket::handle);
        registrar.playToClient(BeaconSyncClientboundPacket.TYPE, BeaconSyncClientboundPacket.STREAM_CODEC, BeaconSyncClientboundPacket::handle);
        registrar.playToClient(UnitConvertClientboundPacket.TYPE, UnitConvertClientboundPacket.STREAM_CODEC, UnitConvertClientboundPacket::handle);
        registrar.playToClient(UnitSyncClientboundPacket.TYPE, UnitSyncClientboundPacket.STREAM_CODEC, UnitSyncClientboundPacket::handle);
        registrar.playToClient(UnitSyncWorkerClientBoundPacket.TYPE, UnitSyncWorkerClientBoundPacket.STREAM_CODEC, UnitSyncWorkerClientBoundPacket::handle);
        registrar.playToClient(UnitSyncAbilityClientboundPacket.TYPE, UnitSyncAbilityClientboundPacket.STREAM_CODEC, UnitSyncAbilityClientboundPacket::handle);
        registrar.playToServer(UnitSyncServerboundPacket.TYPE, UnitSyncServerboundPacket.STREAM_CODEC, UnitSyncServerboundPacket::handle);
        registrar.playToClient(UnitAnimationClientboundPacket.TYPE, UnitAnimationClientboundPacket.STREAM_CODEC, UnitAnimationClientboundPacket::handle);
        registrar.playToClient(UnitPathClientboundPacket.TYPE, UnitPathClientboundPacket.STREAM_CODEC, UnitPathClientboundPacket::handle);
        registrar.playToClient(RtsDebugStatsClientboundPacket.TYPE, RtsDebugStatsClientboundPacket.STREAM_CODEC, RtsDebugStatsClientboundPacket::handle);
        registrar.playToClient(RtsDebugChunksClientboundPacket.TYPE, RtsDebugChunksClientboundPacket.STREAM_CODEC, RtsDebugChunksClientboundPacket::handle);
        registrar.playToClient(UnitIdleWorkerClientBoundPacket.TYPE, UnitIdleWorkerClientBoundPacket.STREAM_CODEC, UnitIdleWorkerClientBoundPacket::handle);
        registrar.playToClient(ResearchClientboundPacket.TYPE, ResearchClientboundPacket.STREAM_CODEC, ResearchClientboundPacket::handle);
        registrar.playToServer(ResearchServerboundPacket.TYPE, ResearchServerboundPacket.STREAM_CODEC, ResearchServerboundPacket::handle);
        registrar.playToServer(PlayerServerboundPacket.TYPE, PlayerServerboundPacket.STREAM_CODEC, PlayerServerboundPacket::handle);
        registrar.playToClient(PlayerClientboundPacket.TYPE, PlayerClientboundPacket.STREAM_CODEC, PlayerClientboundPacket::handle);
        registrar.playToClient(FogOfWarClientboundPacket.TYPE, FogOfWarClientboundPacket.STREAM_CODEC, FogOfWarClientboundPacket::handle);
        registrar.playToServer(FogOfWarServerboundPacket.TYPE, FogOfWarServerboundPacket.STREAM_CODEC, FogOfWarServerboundPacket::handle);
        registrar.playToServer(FrozenChunkServerboundPacket.TYPE, FrozenChunkServerboundPacket.STREAM_CODEC, FrozenChunkServerboundPacket::handle);
        registrar.playToClient(FrozenChunkClientboundPacket.TYPE, FrozenChunkClientboundPacket.STREAM_CODEC, FrozenChunkClientboundPacket::handle);
        registrar.playToServer(BuildingServerboundPacket.TYPE, BuildingServerboundPacket.STREAM_CODEC, BuildingServerboundPacket::handle);
        registrar.playToClient(BuildingClientboundPacket.TYPE, BuildingClientboundPacket.STREAM_CODEC, BuildingClientboundPacket::handle);
        registrar.playToClient(ResourcesClientboundPacket.TYPE, ResourcesClientboundPacket.STREAM_CODEC, ResourcesClientboundPacket::handle);
        registrar.playToServer(ResourcesServerboundPacket.TYPE, ResourcesServerboundPacket.STREAM_CODEC, ResourcesServerboundPacket::handle);
        registrar.playToClient(AbilityClientboundPacket.TYPE, AbilityClientboundPacket.STREAM_CODEC, AbilityClientboundPacket::handle);
        registrar.playToServer(AbilityServerboundPacket.TYPE, AbilityServerboundPacket.STREAM_CODEC, AbilityServerboundPacket::handle);
        registrar.playToServer(BuildingAbilityServerboundPacket.TYPE, BuildingAbilityServerboundPacket.STREAM_CODEC, BuildingAbilityServerboundPacket::handle);
        registrar.playToClient(BuildingAbilityClientboundPacket.TYPE, BuildingAbilityClientboundPacket.STREAM_CODEC, BuildingAbilityClientboundPacket::handle);
        registrar.playToClient(AttackWarningClientboundPacket.TYPE, AttackWarningClientboundPacket.STREAM_CODEC, AttackWarningClientboundPacket::handle);
        registrar.playToClient(SoundClientboundPacket.TYPE, SoundClientboundPacket.STREAM_CODEC, SoundClientboundPacket::handle);
        registrar.playToClient(TutorialClientboundPacket.TYPE, TutorialClientboundPacket.STREAM_CODEC, TutorialClientboundPacket::handle);
        registrar.playToServer(TutorialServerboundPacket.TYPE, TutorialServerboundPacket.STREAM_CODEC, TutorialServerboundPacket::handle);
        registrar.playToClient(AllianceClientboundPacket.TYPE, AllianceClientboundPacket.STREAM_CODEC, AllianceClientboundPacket::handle);
        registrar.playToServer(AllianceServerboundPacket.TYPE, AllianceServerboundPacket.STREAM_CODEC, AllianceServerboundPacket::handle);
        registrar.playToServer(GameModeServerboundPacket.TYPE, GameModeServerboundPacket.STREAM_CODEC, GameModeServerboundPacket::handle);
        registrar.playToClient(GameModeClientboundPacket.TYPE, GameModeClientboundPacket.STREAM_CODEC, GameModeClientboundPacket::handle);
        registrar.playToServer(SurvivalServerboundPacket.TYPE, SurvivalServerboundPacket.STREAM_CODEC, SurvivalServerboundPacket::handle);
        registrar.playToClient(SurvivalClientboundPacket.TYPE, SurvivalClientboundPacket.STREAM_CODEC, SurvivalClientboundPacket::handle);
        registrar.playToClient(ClientboundSyncResourceCostPacket.TYPE, ClientboundSyncResourceCostPacket.STREAM_CODEC, ClientboundSyncResourceCostPacket::handle);
        registrar.playToServer(SandboxServerboundPacket.TYPE, SandboxServerboundPacket.STREAM_CODEC, SandboxServerboundPacket::handle);
        registrar.playToServer(GameruleServerboundPacket.TYPE, GameruleServerboundPacket.STREAM_CODEC, GameruleServerboundPacket::handle);
        registrar.playToClient(GameruleClientboundPacket.TYPE, GameruleClientboundPacket.STREAM_CODEC, GameruleClientboundPacket::handle);
        registrar.playToServer(StartPosServerboundPacket.TYPE, StartPosServerboundPacket.STREAM_CODEC, StartPosServerboundPacket::handle);
        registrar.playToClient(StartPosClientboundPacket.TYPE, StartPosClientboundPacket.STREAM_CODEC, StartPosClientboundPacket::handle);
        registrar.playToClient(HeroClientboundPacket.TYPE, HeroClientboundPacket.STREAM_CODEC, HeroClientboundPacket::handle);
        registrar.playToServer(HeroServerboundPacket.TYPE, HeroServerboundPacket.STREAM_CODEC, HeroServerboundPacket::handle);
        registrar.playToClient(FallenHeroClientboundPacket.TYPE, FallenHeroClientboundPacket.STREAM_CODEC, FallenHeroClientboundPacket::handle);
        registrar.playToClient(CustomBuildingClientboundPacket.TYPE, CustomBuildingClientboundPacket.STREAM_CODEC, CustomBuildingClientboundPacket::handle);
        registrar.playToServer(CustomBuildingServerboundPacket.TYPE, CustomBuildingServerboundPacket.STREAM_CODEC, CustomBuildingServerboundPacket::handle);
        registrar.playToClient(UnitSyncMobEffectsClientboundPacket.TYPE, UnitSyncMobEffectsClientboundPacket.STREAM_CODEC, UnitSyncMobEffectsClientboundPacket::handle);
        registrar.playToServer(MapMarkerServerboundPacket.TYPE, MapMarkerServerboundPacket.STREAM_CODEC, MapMarkerServerboundPacket::handle);
        registrar.playToClient(MapMarkerClientboundPacket.TYPE, MapMarkerClientboundPacket.STREAM_CODEC, MapMarkerClientboundPacket::handle);
        registrar.playToServer(ScenarioServerboundPacket.TYPE, ScenarioServerboundPacket.STREAM_CODEC, ScenarioServerboundPacket::handle);
        registrar.playToClient(ScenarioClientboundPacket.TYPE, ScenarioClientboundPacket.STREAM_CODEC, ScenarioClientboundPacket::handle);
        registrar.playToClient(MatchStatsClientboundPacket.TYPE, MatchStatsClientboundPacket.STREAM_CODEC, MatchStatsClientboundPacket::handle);
        registrar.playToClient(RTSMapInfoClientboundPacket.TYPE, RTSMapInfoClientboundPacket.STREAM_CODEC, RTSMapInfoClientboundPacket::handle);
        registrar.playToServer(RTSMapInfoServerboundPacket.TYPE, RTSMapInfoServerboundPacket.STREAM_CODEC, RTSMapInfoServerboundPacket::handle);
    }
}
