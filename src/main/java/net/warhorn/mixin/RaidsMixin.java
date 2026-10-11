package net.warhorn.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.gamerules.GameRules;
import net.warhorn.siege.SiegeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raids.class)
public abstract class RaidsMixin {
	@Inject(method = "createOrExtendRaid", at = @At("HEAD"), cancellable = true)
	private void warhorn$startSiege(ServerPlayer player, BlockPos raidPosition, CallbackInfoReturnable<Raid> cir) {
		cir.setReturnValue(null);
		var level = player.level();
		if (player.isSpectator() || !level.getGameRules().get(GameRules.RAIDS)
			|| !level.environmentAttributes().getValue(EnvironmentAttributes.CAN_START_RAID, raidPosition)) {
			return;
		}
		var omen = player.getEffect(MobEffects.RAID_OMEN);
		int omenLevel = omen == null ? 1 : omen.getAmplifier() + 1;
		SiegeManager.startFromOmen(level, player, raidPosition, omenLevel);
	}
}
