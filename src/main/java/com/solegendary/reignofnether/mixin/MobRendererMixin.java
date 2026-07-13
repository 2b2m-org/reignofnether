package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MobRenderer.class)
public abstract class MobRendererMixin<T extends Mob, M extends EntityModel<T>> extends LivingEntityRenderer<T, M> {

    public MobRendererMixin(EntityRendererProvider.Context pContext, M pModel, float pShadowRadius) {
        super(pContext, pModel, pShadowRadius);
    }

    @Override
    public void render(
        T pEntity,
        float pEntityYaw,
        float pPartialTicks,
        PoseStack pMatrixStack,
        MultiBufferSource pBuffer,
        int pPackedLight
    ) {
        try {
            super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
        } catch (ClassCastException e) {
            pMatrixStack.popPose();
            ReignOfNether.LOGGER.error("Caught ClassCastException while rendering mob portrait", e);
        }
    }
}
