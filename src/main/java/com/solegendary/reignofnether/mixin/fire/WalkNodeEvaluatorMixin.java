package com.solegendary.reignofnether.mixin.fire;

import com.solegendary.reignofnether.resources.BlockUtils;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {

    public WalkNodeEvaluatorMixin() {
    }

    @Inject(
            method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void getBlockPathType(PathfindingContext context, int pX, int pY, int pZ, CallbackInfoReturnable<PathType> cir) {
        if (!(this.mob instanceof Unit))
            return;

        BlockGetter pLevel = context.level();

        BlockState blockStateBelow = pLevel.getBlockState(new BlockPos(pX, pY, pZ).below());
        Block blockBelow = blockStateBelow.getBlock();
        Block block = pLevel.getBlockState(new BlockPos(pX, pY, pZ)).getBlock();

        // allow units to walk on fire and magma but not leaves (to prevent workers getting stuck in trees)
        if (block == Blocks.FIRE || blockBelow == Blocks.FIRE ||
            block == Blocks.MAGMA_BLOCK || blockBelow == Blocks.MAGMA_BLOCK)
            cir.setReturnValue(PathType.WALKABLE);
        else if (block == Blocks.POINTED_DRIPSTONE || blockBelow == Blocks.POINTED_DRIPSTONE)
            cir.setReturnValue(PathType.UNPASSABLE_RAIL);
        else if (BlockUtils.isLeafBlock(blockStateBelow))
            cir.setReturnValue(PathType.DAMAGE_FIRE);
        else {
            PathType bpt = getPathTypeStatic(context, new BlockPos.MutableBlockPos(pX, pY, pZ));
            cir.setReturnValue(bpt);
        }
    }
}
