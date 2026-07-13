package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.registrars.BlockEntityRegistrar;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class RTSStructureBlockEntity extends StructureBlockEntity {

    public RTSStructureBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(pPos, pBlockState);
    }

    @Override
    public BlockEntityType<?> getType() {
        return BlockEntityRegistrar.RTS_STRUCTURE_BLOCK_ENTITY.get();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        updateBlockState();
    }

    private void updateBlockState() {
        if (this.level != null) {
            BlockPos $$0 = this.getBlockPos();
            BlockState $$1 = this.level.getBlockState($$0);
            if ($$1.is(BlockRegistrar.RTS_STRUCTURE_BLOCK.get())) {
                this.level.setBlock($$0, $$1.setValue(StructureBlock.MODE, super.getMode()), 2);
            }
        }
    }

    @Override
    public @NotNull StructureMode getMode() {
        StructureMode mode = super.getMode();
        if (mode == StructureMode.LOAD)
            return StructureMode.SAVE;
        return mode;
    }

    @Override
    public void setMode(@NotNull StructureMode pMode) {
        if (pMode == StructureMode.LOAD)
            pMode = StructureMode.CORNER; // don't allow loading by block, only by building placement menu
        super.setMode(pMode);
        updateBlockState();
    }

    private @NotNull Stream<BlockPos> getRelatedCorners(@NotNull BlockPos pMinPos, @NotNull BlockPos pMaxPos) {
        Objects.requireNonNull(level);
        Stream<BlockPos> stream = BlockPos.betweenClosedStream(pMinPos, pMaxPos).filter((p_272561_) ->
                level.getBlockState(p_272561_).is(BlockRegistrar.RTS_STRUCTURE_BLOCK.get()));
        return stream.map(level::getBlockEntity).filter((p_155802_) ->
                p_155802_ instanceof RTSStructureBlockEntity)
            .filter((p_155787_) ->
                ((RTSStructureBlockEntity) p_155787_).getMode() == StructureMode.CORNER &&
                    Objects.equals(this.getStructureName(), ((RTSStructureBlockEntity) p_155787_).getStructureName())
            ).map(BlockEntity::getBlockPos);
    }

    @Override
    public boolean detectSize() {
        if (super.getMode() != StructureMode.SAVE || level == null) {
            return false;
        }

        BlockPos blockPos = getBlockPos();
        BlockPos minPos = new BlockPos(blockPos.getX() - 80, level.getMinBuildHeight(), blockPos.getZ() - 80);
        BlockPos maxPos = new BlockPos(blockPos.getX() + 80, level.getMaxBuildHeight() - 1, blockPos.getZ() + 80);
        boolean result = calculateEnclosingBoundingBox(blockPos, getRelatedCorners(minPos, maxPos))
                .filter(box -> {
                    int sizeX = box.maxX() - box.minX();
                    int sizeY = box.maxY() - box.minY();
                    int sizeZ = box.maxZ() - box.minZ();
                    if (sizeX <= 1 || sizeY <= 1 || sizeZ <= 1) {
                        return false;
                    }
                    setStructurePos(new BlockPos(
                            box.minX() - blockPos.getX() + 1,
                            box.minY() - blockPos.getY() + 1,
                            box.minZ() - blockPos.getZ() + 1
                    ));
                    setStructureSize(new Vec3i(sizeX - 1, sizeY - 1, sizeZ - 1));
                    setChanged();
                    BlockState state = level.getBlockState(blockPos);
                    level.sendBlockUpdated(blockPos, state, state, 3);
                    return true;
                })
                .isPresent();

        if (result && level != null && level.getServer() != null) { // this function only runs serverside but we can only render clientside unless in singleplayer
            if (this.getShowBoundingBox() && (!level.isClientSide() && !level.getServer().isDedicatedServer())) {
                CustomBuildingClientEvents.rtsStructuresToRenderBB.add(this.getBlockPos());
            }
        }
        return result;
    }

    private static Optional<BoundingBox> calculateEnclosingBoundingBox(BlockPos pos, Stream<BlockPos> relatedCorners) {
        Iterator<BlockPos> iterator = relatedCorners.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        BoundingBox box = new BoundingBox(iterator.next());
        if (iterator.hasNext()) {
            iterator.forEachRemaining(box::encapsulate);
        } else {
            box.encapsulate(pos);
        }
        return Optional.of(box);
    }

    @Override
    public boolean saveStructure(boolean pWriteToDisk) {
        boolean result = super.saveStructure(pWriteToDisk);
        if (result && level != null) {
            if (!level.isClientSide()) {
                BlockPos pos = getBlockPos().offset(getStructurePos()).offset(0,-1,0);
                ResourceLocation structureLocation = ResourceLocation.tryParse(getStructureName());
                if (structureLocation != null) {
                    CustomBuildingServerEvents.createAndRegisterNewCustomBuilding(
                            structureLocation,
                            getStructureName(),
                            (ServerLevel) this.level,
                            pos,
                            getStructureSize()
                    );
                }
            }
        }
        return result;
    }
}
