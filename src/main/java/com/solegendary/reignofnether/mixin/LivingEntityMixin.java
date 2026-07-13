package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnitProfession;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    public void tick(CallbackInfo ci) {
        if (this.level().isClientSide())
            if (this.hasEffect(MobEffects.LEVITATION))
                MiscUtil.spawnFlyingCloudParticles(this);
    }

    @Inject(
            method = "onChangedBlock",
            at = @At("TAIL"),
            cancellable = true
    )
    protected void onChangedBlock(BlockPos pPos, CallbackInfo ci) {
        Entity entity = this.level().getEntity(this.getId());

        if (!this.level().isClientSide() && entity instanceof Unit unit)
            if (SurvivalServerEvents.isEnabled() && SurvivalServerEvents.ENEMY_OWNER_NAME.equals(unit.getOwnerName())) {
                ci.cancel();
                FrostWalkerOnEntityMoved((LivingEntity) entity, this.level(), pPos, 1);
            }
    }

    // copied from FrostWalkerEnchantment.onEntityMoved
    private void FrostWalkerOnEntityMoved(LivingEntity pLiving, Level pLevel, BlockPos pPos, int pLevelConflicting) {
        if (pLiving.onGround()) {

            float f = (float)Math.min(16, 2 + pLevelConflicting);
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            Iterator var7 = BlockPos.betweenClosed(pPos.offset((int) -f, (int) -1.0, (int) -f), pPos.offset((int) f, (int) -1.0, (int) f)).iterator();

            while(true) {
                BlockPos blockpos;
                BlockState blockstate1;
                do {
                    do {
                        if (!var7.hasNext()) {
                            return;
                        }
                        blockpos = (BlockPos)var7.next();
                    } while(!blockpos.closerToCenterThan(pLiving.position(), f));

                    blockpos$mutableblockpos.set(blockpos.getX(), blockpos.getY() + 1, blockpos.getZ());
                    blockstate1 = pLevel.getBlockState(blockpos$mutableblockpos);
                } while(!blockstate1.isAir());

                BlockState blockstate2 = pLevel.getBlockState(blockpos);
                boolean isFull = blockstate2.getBlock() == Blocks.WATER && blockstate2.getValue(LiquidBlock.LEVEL) == 0;

                BlockState iceState = Blocks.FROSTED_ICE.defaultBlockState();
                if (blockstate2.getFluidState().is(FluidTags.WATER) && isFull && iceState.canSurvive(pLevel, blockpos) &&
                        pLevel.isUnobstructed(iceState, blockpos, CollisionContext.empty()) &&
                        !EventHooks.onBlockPlace(pLiving, BlockSnapshot.create(pLevel.dimension(), pLevel, blockpos), Direction.UP)) {

                    pLevel.setBlockAndUpdate(blockpos, iceState);
                    pLevel.scheduleTick(blockpos, Blocks.FROSTED_ICE, Mth.nextInt(pLiving.getRandom(), 60, 120));
                }

                isFull = blockstate2.getBlock() == Blocks.LAVA && blockstate2.getValue(LiquidBlock.LEVEL) == 0;
                BlockState magmaState = Blocks.NETHERRACK.defaultBlockState();
                if (blockstate2.getFluidState().is(FluidTags.LAVA) && isFull && magmaState.canSurvive(pLevel, blockpos) &&
                        pLevel.isUnobstructed(magmaState, blockpos, CollisionContext.empty()) &&
                        !EventHooks.onBlockPlace(pLiving, BlockSnapshot.create(pLevel.dimension(), pLevel, blockpos), Direction.UP)) {

                    pLevel.setBlockAndUpdate(blockpos, magmaState);
                    pLevel.scheduleTick(blockpos, Blocks.NETHERRACK, Mth.nextInt(pLiving.getRandom(), 60, 120));
                }
            }
        }
    }

    @Shadow public boolean hasEffect(net.minecraft.core.Holder<MobEffect> pEffect) { return true; }
    @Shadow public MobEffectInstance getEffect(net.minecraft.core.Holder<MobEffect> pEffect) { return null; }

    @Inject(
            method = "baseTick",
            at = @At("TAIL")
    )
    public void baseTick(CallbackInfo ci) {
        if (!this.level().isClientSide && this.remainingFireTicks > 0 && !fireImmune() && hasEffect(MobEffectRegistrar.INTENSE_HEAT)) {
            int amp = Math.min(39, getEffect(MobEffectRegistrar.INTENSE_HEAT).getAmplifier());
            int fireTicks = (this.remainingFireTicks + 10);
            if (fireTicks % (80 - (amp * 2)) == 0) {
                this.hurt(this.damageSources().onFire(), 1.0F);
            }
        }
    }
}
