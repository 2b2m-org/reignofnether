package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public class BeaconBlockEntityMixin extends BlockEntity {

    public BeaconBlockEntityMixin(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    @Inject(
            method = "getBeamSections()Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void getBeamSections(CallbackInfoReturnable<List<BeaconBlockEntity.BeaconBeamSection>> cir) {
        if (level == null || !level.isClientSide())
            return;

        BeaconPlacement beacon = BuildingUtils.getBeacon(level.isClientSide());

        if (beacon != null && beacon.getUpgradeLevel() > 0 && worldPosition.equals(beacon.beaconPos)) {
            if (beacon.isBeaconActive()) {
                int colour;

                if (beacon.getAuraEffect() == MobEffects.LUCK)
                    colour = FastColor.ARGB32.color(110, 255, 129); // pale green
                else if (beacon.getAuraEffect() == MobEffects.DIG_SPEED)
                    colour = FastColor.ARGB32.color(255, 232, 102); // pale yellow
                else if (beacon.getAuraEffect() == MobEffects.REGENERATION)
                    colour = FastColor.ARGB32.color(240, 91, 153); // pink
                else if (beacon.getAuraEffect() == MobEffects.DAMAGE_BOOST)
                    colour = FastColor.ARGB32.color(245, 170, 95); // bronze
                else if (beacon.getAuraEffect() == MobEffects.DAMAGE_RESISTANCE)
                    colour = FastColor.ARGB32.color(180, 180, 180); // silver
                else
                    colour = -1; // white

                BeaconBlockEntity.BeaconBeamSection beam = new BeaconBlockEntity.BeaconBeamSection(colour);
                cir.setReturnValue(List.of(beam));
            } else {
                cir.setReturnValue(List.of());
            }
        }
    }
}
