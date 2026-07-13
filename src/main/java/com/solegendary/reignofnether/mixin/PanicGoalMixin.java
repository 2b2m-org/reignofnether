package com.solegendary.reignofnether.mixin;

import net.minecraft.world.entity.ai.goal.PanicGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PanicGoal.class)
public class PanicGoalMixin {

    @Unique private static final double SPEED_MULTIPLIER_CAP = 1.2d;

    @ModifyArg(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(DDDD)Z"
            ),
            index = 3
    )
    private double capSpeedModifier(double speedModifier) {
        return Math.min(speedModifier, SPEED_MULTIPLIER_CAP);
    }
}
