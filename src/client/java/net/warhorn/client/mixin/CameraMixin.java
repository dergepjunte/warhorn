package net.warhorn.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.warhorn.client.cinematic.CameraEffects;
import net.warhorn.client.cinematic.CinematicPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;
	@Shadow @Final private Quaternionf rotation;
	@Shadow @Final private Vector3f forwards;
	@Shadow @Final private Vector3f up;
	@Shadow @Final private Vector3f left;
	@Shadow @Final private static Vector3fc FORWARDS;
	@Shadow @Final private static Vector3fc UP;
	@Shadow @Final private static Vector3fc LEFT;
	@Shadow private int matrixPropertiesDirty;
	@Shadow private float yRot;
	@Shadow private float xRot;

	@Shadow protected abstract void setPosition(Vec3 position);

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void warhorn$direct(DeltaTracker delta, CallbackInfo ci) {
		CinematicPlayer.Pose pose = CinematicPlayer.pose();
		float roll = CameraEffects.rollOffset();
		if (pose != null) {
			setPosition(pose.pos());
			setRotation(pose.yaw() + CameraEffects.yawOffset() * 0.5f, pose.pitch() + CameraEffects.pitchOffset() * 0.5f);
			detached = true;
			roll += pose.roll();
		} else if (CameraEffects.yawOffset() != 0 || CameraEffects.pitchOffset() != 0) {
			setRotation(yRot + CameraEffects.yawOffset(), xRot + CameraEffects.pitchOffset());
		}
		if (roll != 0) {
			rotation.rotateZ(roll * ((float) Math.PI / 180f));
			FORWARDS.rotate(rotation, forwards);
			UP.rotate(rotation, up);
			LEFT.rotate(rotation, left);
			matrixPropertiesDirty |= 3;
		}
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void warhorn$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		CinematicPlayer.Pose pose = CinematicPlayer.lastPose();
		float fov = CinematicPlayer.active() && pose != null ? pose.fov() : cir.getReturnValueF();
		cir.setReturnValue(fov * CameraEffects.fovScale());
	}
}
