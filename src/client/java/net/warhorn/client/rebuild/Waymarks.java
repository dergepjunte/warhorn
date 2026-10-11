package net.warhorn.client.rebuild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.warhorn.Warhorn;
import net.warhorn.net.RuinPayload;

public final class Waymarks {
	public static final Identifier TEXTURE = Warhorn.id("textures/gui/waymark.png");
	private static final int GOLD = 0xF2A33A;
	public static final double ARRIVED = 7.0;
	private static final double FADE_IN = 12.0;
	private static final int BEAM_HEIGHT = 36;

	private Waymarks() {
	}

	public static Vec3 anchor(BlockPos site) {
		Minecraft mc = Minecraft.getInstance();
		int roof = mc.level == null ? site.getY() : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX(), site.getZ());
		return new Vec3(site.getX() + 0.5, Math.max(site.getY(), roof) + 2.2, site.getZ() + 0.5);
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		RuinPayload ruin = RuinGhosts.ruin();
		if (!RuinGhosts.active() || mc.level == null || ruin.sites().isEmpty() || net.warhorn.client.cinematic.CinematicPlayer.active()) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		float time = (mc.level.getGameTime() % 24000 + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20f;
		for (int i = 0; i < ruin.sites().size(); i++) {
			BlockPos site = ruin.sites().get(i);
			Vec3 at = anchor(site);
			double flat = Math.sqrt(Mth.square(at.x - camera.x) + Mth.square(at.z - camera.z));
			float alpha = (float) Mth.clamp((flat - ARRIVED) / (FADE_IN - ARRIVED), 0, 1);
			if (alpha <= 0.02f) continue;
			float phase = time * 2.4f + i * 1.7f;
			double bob = Mth.sin(phase) * 0.22;
			float scale = (float) Mth.clamp(0.9 + flat / 40.0, 0.9, 2.2);

			pose.pushPose();
			pose.translate(at.x - camera.x, at.y - camera.y + bob, at.z - camera.z);
			beam(context, pose, time, alpha, scale);
			arrow(context, pose, camera, at, alpha, scale);
			pose.popPose();
		}
	}

	private static void beam(LevelRenderContext context, PoseStack pose, float time, float alpha, float scale) {
		pose.pushPose();
		pose.translate(-0.5, 0.9 * scale, -0.5);
		int color = ARGB.color(Math.round(200 * alpha), GOLD);
		BeaconRenderer.submitBeaconBeam(pose, context.submitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0f, time,
			0, BEAM_HEIGHT, color, 0.045f * scale, 0.11f * scale);
		pose.popPose();
	}

	private static void arrow(LevelRenderContext context, PoseStack pose, Vec3 camera, Vec3 at, float alpha, float scale) {
		pose.pushPose();
		float yaw = (float) Mth.atan2(camera.x - at.x, camera.z - at.z);
		pose.rotate(Axis.YP.rotation(yaw));
		pose.scale(scale, scale, scale);
		int ghost = ARGB.color(Math.round(90 * alpha), 0xFFFFFF);
		int solid = ARGB.color(Math.round(255 * alpha), 0xFFFFFF);
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.textSeeThrough(TEXTURE), (p, buffer) -> quad(p, buffer, ghost));
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.text(TEXTURE), (p, buffer) -> quad(p, buffer, solid));
		pose.popPose();
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color) {
		float h = 0.5f;
		int light = LightCoordsUtil.FULL_BRIGHT;
		buffer.addVertex(pose, -h, h * 2, 0).setColor(color).setUv(0, 0).setLight(light);
		buffer.addVertex(pose, -h, 0, 0).setColor(color).setUv(0, 1).setLight(light);
		buffer.addVertex(pose, h, 0, 0).setColor(color).setUv(1, 1).setLight(light);
		buffer.addVertex(pose, h, h * 2, 0).setColor(color).setUv(1, 0).setLight(light);
	}

	public static boolean houseAt(BlockPos pos) {
		if (!RuinGhosts.active()) return false;
		int site = nearest(Vec3.atCenterOf(pos));
		if (site < 0) return false;
		BlockPos marker = RuinGhosts.ruin().sites().get(site);
		return Mth.square(marker.getX() - pos.getX()) + Mth.square(marker.getZ() - pos.getZ()) <= Mth.square(9);
	}

	public static int nearest(Vec3 from) {
		RuinPayload ruin = RuinGhosts.ruin();
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < ruin.sites().size(); i++) {
			BlockPos site = ruin.sites().get(i);
			double d = Mth.square(site.getX() + 0.5 - from.x) + Mth.square(site.getZ() + 0.5 - from.z);
			if (d < bestDist) {
				bestDist = d;
				best = i;
			}
		}
		return best;
	}
}
