package net.warhorn.client.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.warhorn.client.ClientFrame;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.warhorn.client.cinematic.CinematicPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "update", at = @At("HEAD"))
	private void warhorn$frame(DeltaTracker delta, CallbackInfo ci) {
		ClientFrame.run();
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void warhorn$hideHand(CameraRenderState camera, PlayerRenderState player, GpuTextureView depth, CallbackInfo ci) {
		if (CinematicPlayer.active()) ci.cancel();
	}
}
