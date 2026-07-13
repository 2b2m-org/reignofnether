package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean detached;

    @ModifyVariable(method = "move(FFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float reignofnether$extendOrthoviewCameraDistance(float zoom) {
        return OrthoviewClientEvents.isEnabled() ? zoom - 20.0F : zoom;
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void reignofnether$detachOrthoviewCamera(
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        this.detached |= OrthoviewClientEvents.isEnabled();
    }
}
