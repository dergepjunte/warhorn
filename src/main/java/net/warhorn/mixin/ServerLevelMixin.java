package net.warhorn.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.warhorn.siege.SiegeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "isRaided", at = @At("RETURN"), cancellable = true)
	private void warhorn$siegeIsRaid(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && SiegeManager.isUnderSiege((ServerLevel) (Object) this, pos)) {
			cir.setReturnValue(true);
		}
	}
}
