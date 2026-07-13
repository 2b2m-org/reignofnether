package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// disable spectator GUI entirely when in orthoview mode as it has cheaty functions like tp to player

@Mixin(Gui.class)
public class TopdownGuiMixin {

    @Inject(
            method = "renderHotbar",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderHotbar(
            GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        if (OrthoviewClientEvents.isEnabled())
            ci.cancel();
    }

    @Inject(
            method = "renderCrosshair",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderCrosshair(
            GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        if (OrthoviewClientEvents.isEnabled())
            ci.cancel();
    }
}
