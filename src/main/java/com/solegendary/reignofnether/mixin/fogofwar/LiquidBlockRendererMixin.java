package com.solegendary.reignofnether.mixin.fogofwar;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {

    @Redirect(
            method = "tesselate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/BlockAndTintGetter;getShade(Lnet/minecraft/core/Direction;Z)F"
            )
    )
    private float reignofnether$applyFogBrightness(
            BlockAndTintGetter level,
            Direction direction,
            boolean shade,
            BlockAndTintGetter methodLevel,
            BlockPos pos,
            VertexConsumer buffer,
            BlockState blockState,
            FluidState fluidState
    ) {
        return level.getShade(direction, shade) * FogOfWarClientEvents.getPosBrightness(pos);
    }
}
