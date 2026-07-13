package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal;
import com.solegendary.reignofnether.unit.goals.RangedAttackGroundGoal;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(CrossbowItem.class)
public class CrossbowMixin {

    @Inject(
            method = "getChargeDuration",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void getChargeDuration(ItemStack crossbowStack, LivingEntity shooter, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue((int) (EnchantmentHelper.modifyCrossbowChargingTime(crossbowStack, shooter, 1.75F) * 20));
    }

    @Inject(method = "shootProjectile", at = @At("HEAD"), cancellable = true)
    private void aimPillagerAtBuilding(
            LivingEntity shooter,
            Projectile projectile,
            int index,
            float velocity,
            float inaccuracy,
            float angle,
            @Nullable LivingEntity target,
            CallbackInfo ci
    ) {
        if (!(shooter instanceof PillagerUnit pillager) || target != null) {
            return;
        }

        Vec3 direction = null;
        RangedAttackGroundGoal<?> groundGoal = pillager.getRangedAttackGroundGoal();
        if (pillager.getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?> buildingGoal
                && buildingGoal.getBuildingTarget() != null) {
            direction = new Vec3(
                    buildingGoal.getBuildingTarget().centrePos.getX() - shooter.getX() + 0.5,
                    75,
                    buildingGoal.getBuildingTarget().centrePos.getZ() - shooter.getZ() + 0.5
            );
        } else if (groundGoal != null && groundGoal.getGroundTarget() != null) {
            direction = new Vec3(
                    groundGoal.getGroundTarget().getX() - shooter.getX() + 0.5,
                    75,
                    groundGoal.getGroundTarget().getZ() - shooter.getZ() + 0.5
            );
        }

        if (direction == null) {
            return;
        }

        Vector3f shot = getProjectileShotVector(shooter, direction, angle);
        projectile.shoot(shot.x(), shot.y(), shot.z(), velocity, inaccuracy);
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.CROSSBOW_SHOOT, shooter.getSoundSource(), 1.0F,
                1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
        ci.cancel();
    }

    private static Vector3f getProjectileShotVector(LivingEntity shooter, Vec3 direction, float angle) {
        Vector3f normalized = direction.toVector3f().normalize();
        Vector3f perpendicular = new Vector3f(normalized).cross(new Vector3f(0.0F, 1.0F, 0.0F));
        if (perpendicular.lengthSquared() <= 1.0E-7) {
            perpendicular = new Vector3f(normalized).cross(shooter.getUpVector(1.0F).toVector3f());
        }
        Vector3f rotationAxis = new Vector3f(normalized).rotateAxis((float) (Math.PI / 2),
                perpendicular.x, perpendicular.y, perpendicular.z);
        return new Vector3f(normalized).rotateAxis(angle * (float) (Math.PI / 180),
                rotationAxis.x, rotationAxis.y, rotationAxis.z);
    }
}
