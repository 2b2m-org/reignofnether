package com.solegendary.reignofnether.mixin;

import com.google.common.collect.Lists;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoggedUnit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Projectile {

    protected AbstractArrowMixin(EntityType<? extends Projectile> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Shadow public abstract boolean isNoPhysics();
    @Shadow private int life;
    @Shadow protected EntityHitResult findHitEntity(Vec3 pStartVec, Vec3 pEndVec) {
        return null;
    }

    private boolean isInsideBuildingAndNotForeignEntity(BuildingPlacement building) {
        Vec3 vec32 = this.position();
        Vec3 vec33 = vec32.add(getDeltaMovement());
        EntityHitResult entityHitResult = this.findHitEntity(vec32, vec33);

        boolean insideForeignEntity = entityHitResult != null && entityHitResult.getEntity() != getOwner();
        if (entityHitResult != null && entityHitResult.getEntity() instanceof Unit unit1 && getOwner() instanceof Unit unit2 &&
            GarrisonableBuildingAddon.getGarrison(unit1) == GarrisonableBuildingAddon.getGarrison(unit2))
            insideForeignEntity = false;

        return building != null && building.isPosInsideBuilding(this.blockPosition()) && !insideForeignEntity;
    }

    // prevent arrows from colliding with the building that a garrisoned unit is inside of
    @Inject(
            method = "isNoPhysics",
            at = @At("HEAD"),
            cancellable = true
    )
    public void isNoPhysics(CallbackInfoReturnable<Boolean> cir) {
        if (this.getOwner() instanceof AttackerUnit aUnit) {
            // garrisoned unit -> ground
            BuildingPlacement building = GarrisonableBuildingAddon.getGarrison((Unit) aUnit);
            if (isInsideBuildingAndNotForeignEntity(building)) {
                cir.setReturnValue(true);
            }

            // ground -> garrisoned unit - allow through at 1/2 accuracy
            // can't use random() here or the nophysics status will change every tick
            /*
            if (this.getId() % 2 == 0 &&
                ((Unit) aUnit).getTargetGoal().getTarget() instanceof Unit tUnit) {
                if (GarrisonableBuilding.getGarrison(tUnit) instanceof Building building &&
                    isInsideTopOfBuilding(building)) {

                    // nophysics all the time means they wouldn't collide with the actual target
                    if (this.distanceToSqr((Entity) tUnit) > Math.pow(1.0d, 2))
                         cir.setReturnValue(true);
                }
            }
             */
        }
    }

    // correct angle of nophysics arrows
    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    public void tick(CallbackInfo ci) {
        if (this.isNoPhysics()) {
            Vec3 vec3 = this.getDeltaMovement();
            double d4 = vec3.horizontalDistance();
            double d6 = vec3.y;
            this.setXRot((float)(Mth.atan2(d6, d4) * 57.2957763671875));
            this.setXRot(lerpRotation(this.xRotO, this.getXRot()));
            this.setYRot(lerpRotation(this.yRotO, this.getYRot()));
        }
    }

    // reduce effective life
    @Inject(
            method = "tickDespawn",
            at = @At("TAIL")
    )
    protected void tickDespawn(CallbackInfo ci) {
        if (this.getOwner() instanceof Unit && this.life >= 200)
            this.discard();
    }

    @Shadow public byte getPierceLevel() { return 0; }
    @Shadow private SoundEvent soundEvent;
    @Shadow private IntOpenHashSet piercingIgnoreEntityIds;
    @Shadow private List<Entity> piercedAndKilledEntities;
    @Shadow private double baseDamage;
    @Shadow public boolean isCritArrow() { return false; }
    @Shadow public AbstractArrow.Pickup pickup;
    @Shadow protected abstract ItemStack getPickupItem();
    @Shadow public abstract ItemStack getWeaponItem();
    @Shadow protected abstract void doKnockback(LivingEntity target, DamageSource damageSource);
    @Shadow protected void doPostHurtEffects(LivingEntity pTarget) { }
    @Shadow public boolean shotFromCrossbow() { return false; }

    @Unique
    private boolean reignofnether$collidedWithUntargetedAlly(Entity entity) {
        boolean isAlliedOrOwned = false;
        if (this.getOwner() instanceof Unit unit1 &&
                entity instanceof Unit unit2) {
            String owner1 = unit1.getOwnerName();
            String owner2 = unit2.getOwnerName();
            if (entity.level().isClientSide())
                isAlliedOrOwned = owner1.equals(owner2) || AlliancesClient.isAllied(owner1, owner2);
            else
                isAlliedOrOwned = owner1.equals(owner2) || AlliancesServerEvents.isAllied(owner1, owner2);
        }
        return this.getOwner() instanceof Unit unit1 &&
                entity instanceof Unit unit2 &&
                isAlliedOrOwned &&
                (unit1.getTargetGoal().getTarget() == null ||
                        !unit1.getTargetGoal().getTarget().equals(unit2));
    }

    @Unique
    private boolean reignofnether$boggedArrowCollidedWithPoisonedEnemy(Entity entity) {
        return this.getOwner() instanceof BoggedUnit boggedUnit && entity instanceof LivingEntity le && le.hasEffect(MobEffects.POISON) &&
                !(boggedUnit.getTargetGoal().forced && boggedUnit.getTargetGoal().getTarget() == entity);
    }

    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) &&
                (this.piercingIgnoreEntityIds == null || !this.piercingIgnoreEntityIds.contains(entity.getId())) &&
                !reignofnether$collidedWithUntargetedAlly(entity) &&
                !reignofnether$boggedArrowCollidedWithPoisonedEnemy(entity);
    }

    // replace bounce logic (on hitting an enemy at the time as another arrow) with pierce logic instead
    @Inject(
            method = "onHitEntity",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void onHitEntity(EntityHitResult pResult, CallbackInfo ci) {
        ci.cancel();
        Entity entity = pResult.getEntity();

        super.onHitEntity(pResult);

        float speed = (float)this.getDeltaMovement().length();
        double damage = this.baseDamage;
        Entity owner = this.getOwner();
        AbstractArrow arrow = (AbstractArrow)(Object)this;
        DamageSource damageSource = this.damageSources().arrow(arrow, owner != null ? owner : arrow);
        if (this.getWeaponItem() != null && this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(
                    serverLevel, this.getWeaponItem(), entity, damageSource, (float)damage
            );
        }

        int damageAmount = Mth.ceil(Mth.clamp((double)speed * damage, 0.0, 2.147483647E9));
        if (this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }
            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }
            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.discard();
                return;
            }
            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.isCritArrow()) {
            long criticalBonus = this.random.nextInt(damageAmount / 2 + 2);
            damageAmount = (int)Math.min(criticalBonus + (long)damageAmount, 2147483647L);
        }

        if (owner instanceof LivingEntity livingOwner) {
            livingOwner.setLastHurtMob(entity);
        }

        boolean isEnderman = entity.getType() == EntityType.ENDERMAN;
        if (this.isOnFire() && !isEnderman) {
            entity.igniteForSeconds(5.0F);
        }

        if (entity.hurt(damageSource, (float)damageAmount)) {
            if (isEnderman) {
                return;
            }

            if (entity instanceof LivingEntity livingentity) {
                if (!this.level().isClientSide && this.getPierceLevel() <= 0) {
                    livingentity.setArrowCount(livingentity.getArrowCount() + 1);
                }

                this.doKnockback(livingentity, damageSource);
                if (this.level() instanceof ServerLevel serverLevel) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(
                            serverLevel, livingentity, damageSource, this.getWeaponItem()
                    );
                }

                this.doPostHurtEffects(livingentity);
                if (livingentity != owner && livingentity instanceof Player
                        && owner instanceof ServerPlayer serverPlayer && !this.isSilent()) {
                    serverPlayer.connection.send(
                            new ClientboundGameEventPacket(ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F)
                    );
                }

                if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
                    this.piercedAndKilledEntities.add(livingentity);
                }

                if (!this.level().isClientSide && owner instanceof ServerPlayer serverplayer) {
                    if (this.piercedAndKilledEntities != null && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(serverplayer, this.piercedAndKilledEntities);
                    } else if (!entity.isAlive() && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(serverplayer, Arrays.asList(entity));
                    }
                }
            }

            this.playSound(this.soundEvent, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.discard();
            }
        } else {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }
            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }
            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.discard();
                return;
            }
            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.getOwner() instanceof PillagerUnit pUnit &&
            !pUnit.level().isClientSide() && pUnit.isPassenger()) {
            pUnit.level().explode(this.getOwner(), damageSource, null,
                    pResult.getEntity().getEyePosition().x,
                    pResult.getEntity().getEyePosition().y,
                    pResult.getEntity().getEyePosition().z,
                    1f,
                    false,
                    Level.ExplosionInteraction.BLOCK);
        }
    }

    @Inject(
            method = "onHitBlock",
            at = @At("HEAD")
    )
    protected void onHitBlock(BlockHitResult pResult, CallbackInfo ci) {
        if (this.getOwner() instanceof PillagerUnit pUnit &&
                !pUnit.level().isClientSide() && pUnit.isPassenger()) {
            pUnit.level().explode(this.getOwner(), null, null,
                    pResult.getLocation().x,
                    pResult.getLocation().y,
                    pResult.getLocation().z,
                    1f,
                    false,
                    Level.ExplosionInteraction.TNT);
        }
    }
}
