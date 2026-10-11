package net.warhorn.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.warhorn.client.atmosphere.SiegeAtmosphere;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
	private void warhorn$siegeAtmosphere(EnvironmentAttributeSystem.Builder builder,
		CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		SiegeAtmosphere.addLayers(cir.getReturnValue());
	}
}
