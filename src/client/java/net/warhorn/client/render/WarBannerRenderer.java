package net.warhorn.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.warhorn.Warhorn;
import net.warhorn.entity.WarBannerEntity;

public class WarBannerRenderer extends EntityRenderer<WarBannerEntity, WarBannerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("war_banner"), "main");
	private static final Identifier TEXTURE = Warhorn.id("textures/entity/war_banner.png");
	private final Model model;

	public WarBannerRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new Model(context.bakeLayer(LAYER));
		shadowRadius = 0.5f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition pole = root.addOrReplaceChild("pole", CubeListBuilder.create()
			.texOffs(44, 0).addBox(-1, -28, -1, 2, 52, 2)
			.texOffs(0, 40).addBox(-2, 20, -2, 4, 3, 4)
			.texOffs(36, 40).addBox(-1.5f, -2, -1.5f, 3, 2, 3)
			.texOffs(0, 34).addBox(-11, -26, -1, 22, 2, 2)
			.texOffs(16, 40).addBox(-1.5f, -32, -1.5f, 3, 4, 3), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String n = side < 0 ? "right" : "left";
			pole.addOrReplaceChild(n + "_horn", CubeListBuilder.create()
				.texOffs(28, 40).addBox(-0.5f, -4, -0.5f, 1, 4, 1),
				PartPose.offsetAndRotation(side * 1.4f, -30.5f, 0, -0.15f, 0, side * 0.75f));
			pole.addOrReplaceChild(n + "_spike", CubeListBuilder.create()
				.texOffs(28, 40).addBox(-0.5f, -4, -0.5f, 1, 4, 1),
				PartPose.offsetAndRotation(side * 11f, -25, 0, 0, 0, side * 0.4f));
			pole.addOrReplaceChild(n + "_tassel", CubeListBuilder.create()
				.texOffs(32, 40).addBox(-0.5f, 0, -0.5f, 1, 8, 1),
				PartPose.offset(side * 10.5f, -24, -0.6f));
		}
		PartDefinition top = pole.addOrReplaceChild("cloth_top", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-10, 0, -0.5f, 20, 10, 1), PartPose.offset(0, -24, -1.6f));
		PartDefinition mid = top.addOrReplaceChild("cloth_mid", CubeListBuilder.create()
			.texOffs(0, 11).addBox(-10, 0, -0.5f, 20, 10, 1), PartPose.offset(0, 10, 0));
		mid.addOrReplaceChild("cloth_low", CubeListBuilder.create()
			.texOffs(0, 22).addBox(-10, 0, -0.5f, 20, 10, 1), PartPose.offset(0, 10, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		float rise = Mth.clamp(state.age / 6f, 0, 1);
		pose.translate(0, -1.2 * (1 - rise * rise), 0);
		pose.rotate(Axis.YP.rotationDegrees(180f - state.yRot));
		pose.scale(-1, -1, 1);
		pose.translate(0, -1.501, 0);
		collector.submitModel(model, state, pose, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarBannerEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot();
		state.age = entity.age + partialTicks;
		state.sinceHit = entity.sinceHit() + partialTicks;
		state.health = entity.health() / WarBannerEntity.MAX_HEALTH;
	}

	public static class State extends EntityRenderState {
		public float yRot;
		public float age;
		public float sinceHit = 100;
		public float health = 1;
	}

	private static final class Model extends EntityModel<State> {
		private final ModelPart pole;
		private final ModelPart top;
		private final ModelPart mid;
		private final ModelPart low;
		private final ModelPart rightTassel;
		private final ModelPart leftTassel;

		Model(ModelPart root) {
			super(root);
			pole = root.getChild("pole");
			top = pole.getChild("cloth_top");
			mid = top.getChild("cloth_mid");
			low = mid.getChild("cloth_low");
			rightTassel = pole.getChild("right_tassel");
			leftTassel = pole.getChild("left_tassel");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.age * 0.13f;
			top.xRot = 0.05f + Mth.sin(t) * 0.05f;
			mid.xRot = Mth.sin(t - 0.8f) * 0.1f;
			low.xRot = Mth.sin(t - 1.6f) * 0.16f;
			top.zRot = Mth.sin(t * 0.7f) * 0.015f;
			low.zRot = Mth.sin(t * 0.9f - 1f) * 0.04f;
			rightTassel.xRot = Mth.sin(t * 1.3f) * 0.12f;
			leftTassel.xRot = Mth.sin(t * 1.3f + 1.2f) * 0.12f;
			float shudder = Math.max(0, 1 - state.sinceHit / 10f);
			pole.zRot = Mth.sin(state.sinceHit * 2.4f) * 0.07f * shudder;
			pole.xRot = Mth.cos(state.sinceHit * 2.1f) * 0.04f * shudder;
			low.xRot += 0.5f * shudder;
			rightTassel.visible = state.health > 0.75f;
			leftTassel.visible = state.health > 0.5f;
			low.visible = state.health > 0.3f;
		}
	}
}
