package com.solegendary.reignofnether.mixin.fogofwar;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FrozenChunk;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.*;


@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Final @Shadow private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Final @Shadow private Minecraft minecraft;
    @Final @Shadow private RenderBuffers renderBuffers;
    @Final @Shadow private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow private ViewArea viewArea;
    @Shadow private ClientLevel level;

    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection> lastVisibleSections = new ObjectArrayList<>();

    private List<Pair<BlockPos, Integer>> chunksToReDirty = new ArrayList<>();

    @Inject(
            method = "applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void applyFrustum(Frustum pFrustum, CallbackInfo ci) {
        if (!FogOfWarClientEvents.isEnabled())
            return;

        ci.cancel();

        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
        } else {
            this.minecraft.getProfiler().push("apply_frustum");
            this.visibleSections.clear();
            Set<ChunkPos> chunksToRefreshDone = new HashSet<>();

            for (SectionRenderDispatcher.RenderSection renderSection : this.viewArea.sections) {
                if (pFrustum.isVisible(renderSection.getBoundingBox())) {
                    this.visibleSections.add(renderSection);
                }
                if (minecraft.level != null) {
                    ChunkPos cpos = new ChunkPos(renderSection.getOrigin());
                    if (FogOfWarClientEvents.chunksToRefresh.contains(cpos)) {
                        renderSection.setDirty(true);
                        chunksToRefreshDone.add(cpos);
                    }
                }
            }

            FogOfWarClientEvents.chunksToRefresh.removeAll(chunksToRefreshDone);

            FogOfWarClientEvents.renderChunksInFrustum.clear();
            FogOfWarClientEvents.renderChunksInFrustum.addAll(visibleSections);

            this.minecraft.getProfiler().pop();
        }
    }

    @Inject(
            method = "compileSections(Lnet/minecraft/client/Camera;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void compileSections(Camera pCamera, CallbackInfo ci) {

        // hiding leaves around cursor
        if (OrthoviewClientEvents.hideLeavesMethod == OrthoviewClientEvents.LeafHideMethod.AROUND_UNITS_AND_CURSOR &&
                OrthoviewClientEvents.isEnabled()) {
            UnitClientEvents.windowUpdateTicks -= 1;
            if (UnitClientEvents.windowUpdateTicks <= 0) {
                UnitClientEvents.windowUpdateTicks = UnitClientEvents.WINDOW_UPDATE_TICKS_MAX;
                Vec3 centrePos = MiscUtil.getOrthoviewCentreWorldPos(Minecraft.getInstance());
                for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
                    BlockPos chunkCentreBp = renderSection.getOrigin().offset(8, 8, 8);

                    List<Pair<BlockPos, Integer>> newChunksToReDirty = new ArrayList<>();

                    // rerender each chunk a second time so we can unhide leaves as they go out of range
                    synchronized (UnitClientEvents.windowPositions) {
                        for (Pair<BlockPos, Integer> pair : chunksToReDirty) {
                            int times = pair.getSecond();
                            if (pair.getFirst().equals(renderSection.getOrigin())) {
                                renderSection.setDirty(true);
                                times -= 1;
                            }
                            if (times > 0)
                                newChunksToReDirty.add(new Pair<>(pair.getFirst(), times));
                        }
                        chunksToReDirty.clear();
                        chunksToReDirty.addAll(newChunksToReDirty);

                        UnitClientEvents.windowPositions.forEach(bp -> {
                            if (chunkCentreBp.distSqr(bp) < 625) {
                                renderSection.setDirty(true);
                                chunksToReDirty.add(new Pair<>(renderSection.getOrigin().immutable(), 10));
                            }
                        });
                    }
                }
            }
        }

        if (!isEnabled())
            return;

        ci.cancel();


        // determine which renderChunks are new - enforce frozenChunks on those
        ObjectArrayList<SectionRenderDispatcher.RenderSection> newVisibleSections = new ObjectArrayList<>();
        newVisibleSections.addAll(visibleSections);
        newVisibleSections.removeAll(lastVisibleSections);

        // load saved blocks into unexplored frozen chunks (don't repeat this for overlapping chunks)
        ArrayList<BlockPos> loadedFcOrigins = new ArrayList<>();
        for (FrozenChunk frozenChunk : frozenChunks) {
            if (frozenChunk.hasFakeBlocks) continue;
            for (SectionRenderDispatcher.RenderSection newRenderSection : newVisibleSections) {
                if (newRenderSection.getOrigin().equals(frozenChunk.origin) &&
                    !isInBrightChunk(frozenChunk.origin) &&
                    !loadedFcOrigins.contains(frozenChunk.origin)) {
                    if (!frozenChunk.unsaved) {
                        frozenChunk.loadBlocks();
                        loadedFcOrigins.add(frozenChunk.origin);
                    }
                }
            }
        }
        // rerun for any faked chunks, only run them if they were not already loaded
        for (FrozenChunk frozenChunk : frozenChunks) {
            if (!frozenChunk.hasFakeBlocks) continue;
            for (SectionRenderDispatcher.RenderSection newRenderSection : newVisibleSections) {
                if (newRenderSection.getOrigin().equals(frozenChunk.origin) &&
                    !isInBrightChunk(frozenChunk.origin) &&
                    !loadedFcOrigins.contains(frozenChunk.origin)) {
                    if (!frozenChunk.unsaved) {
                        //System.out.println("loaded (faked) frozen blocks at: " + frozenChunk.origin);
                        frozenChunk.loadBlocks();
                    }
                }
            }
        }

        this.minecraft.getProfiler().push("populate_chunks_to_compile");
        LevelLightEngine levellightengine = this.level.getLightEngine();
        RenderRegionCache renderregioncache = new RenderRegionCache();
        BlockPos blockpos = pCamera.getBlockPosition();
        List<SectionRenderDispatcher.RenderSection> sectionsToCompile = Lists.newArrayList();
        Set<ChunkPos> rerenderChunksToRemove = ConcurrentHashMap.newKeySet();
        Set<ChunkPos> enemyOccupiedChunks = FogOfWarClientEvents.getEnemyOccupiedChunks();
        outerLoop:
        for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {

            BlockPos originPos = renderSection.getOrigin();
            ChunkPos chunkPos = new ChunkPos(originPos);

            if (rerenderChunks.contains(chunkPos)) {
                FogOfWarClientEvents.updateChunkLighting(originPos);
                rerenderChunksToRemove.add(chunkPos);
            }
            else if (!isInBrightChunk(originPos)) {
                if (semiFrozenChunks.contains(originPos)) continue;
                for (FrozenChunk chunk : frozenChunks) {
                    if (!chunk.unsaved) continue;
                    if (chunk.origin.equals(originPos)) continue outerLoop;
                }
                if (OrthoviewClientEvents.isEnabled() || enemyOccupiedChunks.contains(chunkPos))
                    semiFrozenChunks.add(originPos);
            }
            SectionPos sectionpos = SectionPos.of(renderSection.getOrigin());
            if (renderSection.isDirty() && levellightengine.lightOnInSection(sectionpos)) {
                boolean flag = false;
                if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY) {
                    BlockPos blockpos1 = renderSection.getOrigin().offset(8, 8, 8);
                    flag = blockpos1.distSqr(blockpos) < 768.0D || renderSection.isDirtyFromPlayer();
                } else if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                    flag = renderSection.isDirtyFromPlayer();
                }

                if (flag) {
                    this.minecraft.getProfiler().push("build_near_sync");
                    this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderregioncache);
                    renderSection.setNotDirty();
                    this.minecraft.getProfiler().pop();
                } else {
                    sectionsToCompile.add(renderSection);
                }
            }
        }
        rerenderChunks.removeAll(rerenderChunksToRemove);

        this.minecraft.getProfiler().popPush("upload");
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        this.minecraft.getProfiler().popPush("schedule_async_compile");

        for (SectionRenderDispatcher.RenderSection renderSection : sectionsToCompile) {
            renderSection.rebuildSectionAsync(this.sectionRenderDispatcher, renderregioncache);
            renderSection.setNotDirty();
        }
        this.minecraft.getProfiler().pop();

        lastVisibleSections.clear();
        lastVisibleSections.addAll(visibleSections);
    }

    // always recheck chunks being in frustum - without this normally only checks when the camera moves
    @Redirect(
            method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"
            )
    )
    private boolean reignofnether$alwaysRefreshOrthoviewFrustum(SectionOcclusionGraph graph) {
        return graph.consumeFrustumUpdate() || isEnabled() && OrthoviewClientEvents.isEnabled();
    }

    // rerun blockDestroyProgress overlays but with range extended to between 32-256 blocks
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderBuffers;crumblingBufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;",
                    ordinal = 2
            )
    )
    private void renderDistantBlockBreaking(
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        PoseStack poseStack = new PoseStack();
        Vec3 cameraPosition = camera.getPosition();
        double cameraX = cameraPosition.x();
        double cameraY = cameraPosition.y();
        double cameraZ = cameraPosition.z();

        ObjectIterator<Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>>> entries =
                this.destructionProgress.long2ObjectEntrySet().iterator();
        while (entries.hasNext()) {
            Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> entry = entries.next();
            BlockPos pos = BlockPos.of(entry.getLongKey());
            double x = pos.getX() - cameraX;
            double y = pos.getY() - cameraY;
            double z = pos.getZ() - cameraZ;
            double distanceSquared = x * x + y * y + z * z;
            if (distanceSquared > 1024.0 && distanceSquared < 65536.0) {
                SortedSet<BlockDestructionProgress> progress = entry.getValue();
                if (progress != null && !progress.isEmpty()) {
                    int stage = progress.last().getProgress();
                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(
                            this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(stage)),
                            poseStack.last(),
                            1.0F
                    );
                    ModelData modelData = this.level.getModelData(pos);
                    this.minecraft.getBlockRenderer().renderBreakingTexture(
                            this.level.getBlockState(pos), pos, this.level, poseStack, vertexConsumer, modelData
                    );
                    poseStack.popPose();
                }
            }
        }
    }

    // increase render distance for particles
    @Shadow private ParticleStatus calculateParticleLevel(boolean pDecreased) { return null; }

    @Inject(
            method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void addParticleInternal(ParticleOptions pOptions, boolean pForce, boolean pDecreased, double pX, double pY, double pZ,
                                    double pXSpeed, double pYSpeed, double pZSpeed, CallbackInfoReturnable<Particle> cir) {
        if (!OrthoviewClientEvents.isEnabled())
            return;

        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        if (this.minecraft != null && camera.isInitialized() && this.minecraft.particleEngine != null) {
            ParticleStatus particlestatus = this.calculateParticleLevel(pDecreased);
            if (pForce) {
                cir.setReturnValue(this.minecraft.particleEngine.createParticle(pOptions, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed));
            } else if (camera.getPosition().distanceToSqr(pX, pY, pZ) > 4096) {
                cir.setReturnValue(null);
            } else {
                cir.setReturnValue(particlestatus == ParticleStatus.MINIMAL ? null : this.minecraft.particleEngine.createParticle(pOptions, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed));
            }
        } else {
            cir.setReturnValue(null);
        }
    }
}
