package net.warhorn.client.mixin;

import net.minecraft.client.Minecraft;
import net.warhorn.client.cinematic.CinematicPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void warhorn$holdKeybinds(CallbackInfo ci) {
		if (CinematicPlayer.active()) ci.cancel();
	}
}
