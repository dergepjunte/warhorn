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
import net.warhorn.entity.BatteringRamEntity;

public class BatteringRamRenderer extends EntityRenderer<BatteringRamEntity, BatteringRamRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("battering_ram"), "main");
	private static final Identifier TEXTURE = Warhorn.id("textures/entity/battering_ram.png");
	private final Model model;

	public BatteringRamRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new Model(context.bakeLayer(LAYER));
		shadowRadius = 0;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition ram = root.addOrReplaceChild("ram", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-3, -3, -22.4f, 6, 6, 48)
			.texOffs(0, 56).addBox(-4, -4, -28.4f, 8, 8, 6), PartPose.offset(0, 4.8f, 0));
		for (int side = -1; side <= 1; side += 2) {
			String n = side < 0 ? "right" : "left";
			PartDefinition a = ram.addOrReplaceChild(n + "_horn", CubeListBuilder.create()
				.texOffs(32, 56).addBox(side < 0 ? -3 : 0, -1.5f, -2, 3, 3, 4),
				PartPose.offsetAndRotation(side * 3.6f, -2.6f, -24.6f, 0.25f, side * -0.35f, side * -0.3f));
			PartDefinition b = a.addOrReplaceChild("curl", CubeListBuilder.create()
				.texOffs(48, 56).addBox(-1, -1.5f, -1.5f, 2, 3, 3),
				PartPose.offsetAndRotation(side * 2.4f, 0.6f, 2.0f, 1.1f, 0, 0));
			b.addOrReplaceChild("tip", CubeListBuilder.create()
				.texOffs(60, 56).addBox(-1, -1, -1, 2, 2, 2),
				PartPose.offsetAndRotation(0, 2.6f, 0.4f, 1.0f, 0, 0));
			for (int k = 0; k < 2; k++) {
				ram.addOrReplaceChild(n + "_sling" + k, CubeListBuilder.create()
					.texOffs(72, 56).addBox(0, -0.5f, -0.5f, 12, 1, 1),
					PartPose.offsetAndRotation(side * 3f, -1.5f, k == 0 ? 2.4f : 20f, 0, side < 0 ? Mth.PI : 0, side * -0.0f - 0.25f));
			}
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
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
	public void extractRenderState(BatteringRamEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.age = entity.tickCount + partialTicks;
		state.swing = entity.swingClock < 0 ? -1 : Math.max(0, entity.swingClock - 1 + partialTicks);
		double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
		state.stride = (float) Mth.clamp(Math.sqrt(dx * dx + dz * dz) / 0.085, 0, 1);
	}

	public static class State extends EntityRenderState {
		public float yRot;
		public float age;
		public float swing = -1;
		public float stride;
	}

	private static final class Model extends EntityModel<State> {
		private final ModelPart ram;

		Model(ModelPart root) {
			super(root);
			ram = root.getChild("ram");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			ram.y = 4.8f + Mth.abs(Mth.sin(state.age * 0.45f)) * 0.9f * state.stride;
			ram.zRot = Mth.sin(state.age * 0.225f) * 0.03f * state.stride;
			ram.z = 0;
			ram.xRot = 0;
			if (state.swing >= 0) {
				ram.z = (float) -BatteringRamEntity.swingCurve(state.swing) * 16f;
				ram.xRot = (float) BatteringRamEntity.swingCurve(state.swing) * -0.05f;
			}
		}
	}
}
