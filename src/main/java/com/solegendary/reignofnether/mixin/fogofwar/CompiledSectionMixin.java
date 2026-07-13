package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(SectionRenderDispatcher.CompiledSection.class)
public abstract class CompiledSectionMixin {

    @Shadow @Final private List<BlockEntity> renderableBlockEntities;

    @Inject(method = "getRenderableBlockEntities", at = @At("HEAD"), cancellable = true)
    private void reignofnether$hideBlockEntitiesInFog(CallbackInfoReturnable<List<BlockEntity>> cir) {
        if (!FogOfWarClientEvents.isEnabled()) {
            return;
        }

        List<BlockEntity> visibleBlockEntities = new ArrayList<>();
        for (BlockEntity blockEntity : renderableBlockEntities) {
            if (FogOfWarClientEvents.isInBrightChunk(blockEntity.getBlockPos())) {
                visibleBlockEntities.add(blockEntity);
            }
        }
        cir.setReturnValue(visibleBlockEntities);
    }
}
