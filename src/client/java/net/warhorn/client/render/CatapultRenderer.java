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
import net.warhorn.entity.CatapultEntity;
import net.warhorn.entity.CatapultEntity.Phase;

public class CatapultRenderer extends EntityRenderer<CatapultEntity, CatapultRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("catapult"), "main");
	static final Identifier TEXTURE = Warhorn.id("textures/entity/catapult.png");
	static final float REST = 0.45f;
	static final float LOADED = -1.3f;
	private static final float AXLE_Y = 14;
	private static final float AXLE_Z = -2;
	private static final float DRUM_Y = 14;
	private static final float DRUM_Z = 14;
	private final Model model;

	public CatapultRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new Model(context.bakeLayer(LAYER));
		shadowRadius = 1.4f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("frame", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-7, 17, -16, 2, 2, 34)
			.texOffs(0, 0).mirror().addBox(5, 17, -16, 2, 2, 34).mirror(false)
			.texOffs(0, 40).addBox(-5, 17, -15, 10, 2, 2)
			.texOffs(0, 40).addBox(-5, 17, 15, 10, 2, 2)
			.texOffs(32, 40).addBox(-7, 13, -3, 2, 4, 2)
			.texOffs(32, 40).mirror().addBox(5, 13, -3, 2, 4, 2).mirror(false)
			.texOffs(32, 40).addBox(-7, 13, 13, 2, 4, 2)
			.texOffs(32, 40).mirror().addBox(5, 13, 13, 2, 4, 2).mirror(false)
			.texOffs(0, 46).addBox(-6, 12.5f, -3.5f, 12, 3, 3)
			.texOffs(76, 0).addBox(-7, -1, -11, 2, 18, 2)
			.texOffs(76, 0).mirror().addBox(5, -1, -11, 2, 18, 2).mirror(false)
			.texOffs(0, 54).addBox(-7, -1.5f, -11.5f, 14, 3, 3), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String n = side < 0 ? "right" : "left";
			root.addOrReplaceChild(n + "_brace", CubeListBuilder.create()
				.texOffs(0, 70).addBox(-1, -1, 0, 2, 2, 21), PartPose.offsetAndRotation(side * 6, 16, 3, 2.224f, 0, 0));
			for (int end = -1; end <= 1; end += 2) {
				root.addOrReplaceChild(n + "_wheel" + (end < 0 ? "_front" : "_back"), CubeListBuilder.create()
					.texOffs(40, 40).addBox(-1, -3.5f, -3.5f, 2, 7, 7), PartPose.offset(side * 8, 20.5f, end * 11));
			}
		}
		root.addOrReplaceChild("deck", CubeListBuilder.create()
			.texOffs(48, 70).addBox(-5, 16.5f, 5, 10, 1, 11)
			.texOffs(60, 52).addBox(-4, 12.5f, 7, 4, 4, 4)
			.texOffs(60, 52).addBox(0.5f, 12.5f, 8.5f, 4, 4, 4)
			.texOffs(60, 52).addBox(-2, 8.5f, 7.8f, 4, 4, 4), PartPose.ZERO);
		root.addOrReplaceChild("brazier", CubeListBuilder.create()
			.texOffs(96, 52).addBox(-0.5f, 0, -0.5f, 1, 7, 1)
			.texOffs(100, 52).addBox(-2.5f, -3, -2.5f, 5, 3, 5), PartPose.offset(10, 17, 21));
		PartDefinition drum = root.addOrReplaceChild("drum", CubeListBuilder.create()
			.texOffs(0, 62).addBox(-5, -1.5f, -1.5f, 10, 3, 3), PartPose.offset(0, DRUM_Y, DRUM_Z));
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition crank = drum.addOrReplaceChild(side < 0 ? "right_crank" : "left_crank", CubeListBuilder.create()
				.texOffs(30, 62).addBox(-0.5f, 0, -0.5f, 1, 5, 1), PartPose.offset(side * 6.5f, 0, 0));
			crank.addOrReplaceChild("peg", CubeListBuilder.create()
				.texOffs(36, 62).addBox(side < 0 ? -2 : 0, 4, -0.5f, 2, 1, 1), PartPose.ZERO);
		}
		PartDefinition arm = root.addOrReplaceChild("arm", CubeListBuilder.create()
			.texOffs(88, 0).addBox(-1.5f, -22, -1.5f, 3, 22, 3), PartPose.offset(0, AXLE_Y, AXLE_Z));
		arm.addOrReplaceChild("cup", CubeListBuilder.create()
			.texOffs(60, 40).addBox(-3.5f, -3.5f, -3, 7, 7, 3), PartPose.offset(0, -20, -1.5f));
		arm.addOrReplaceChild("pot", CubeListBuilder.create()
			.texOffs(60, 52).addBox(-2, -2, -2, 4, 4, 4)
			.texOffs(80, 52).addBox(-1, -2, -3, 2, 2, 1), PartPose.offset(0, -20, -6.3f));
		root.addOrReplaceChild("rope", CubeListBuilder.create()
			.texOffs(44, 62).addBox(-0.5f, 0, -0.5f, 1, 1, 1), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.rotate(Axis.YP.rotationDegrees(180f - state.yRot));
		pose.scale(-CatapultEntity.SCALE, -CatapultEntity.SCALE, CatapultEntity.SCALE);
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
	public void extractRenderState(CatapultEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.phase = entity.phase();
		state.time = entity.phaseClock + partialTicks;
		state.shake = entity.shakeClock + partialTicks;
		state.integrity = entity.integrity();
	}

	public static class State extends EntityRenderState {
		public float yRot;
		public Phase phase = Phase.IDLE;
		public float time;
		public float shake = 100;
		public float integrity = 1;
	}

	static float wound(float t) {
		float p = Mth.clamp(t / Phase.WIND.ticks, 0, 1) * 10;
		int notch = Mth.floor(p);
		float f = p - notch;
		return Math.min(1, (notch + f * f * (3 - 2 * f)) / 10);
	}

	static float armAngle(Phase phase, float t) {
		return switch (phase) {
			case IDLE, REST -> REST;
			case WIND -> Mth.lerp(wound(t), REST, LOADED);
			case LIGHT -> LOADED + 0.01f * Mth.sin(t * 2.1f);
			case LOOSE -> {
				if (t < 3) yield Mth.lerp((t / 3) * (t / 3), LOADED, REST + 0.15f);
				yield REST + 0.15f * Mth.cos((t - 3) * 1.3f) * (float) Math.exp(-(t - 3) / 4);
			}
		};
	}

	private static final class Model extends EntityModel<State> {
		private final ModelPart root;
		private final ModelPart arm;
		private final ModelPart pot;
		private final ModelPart drum;
		private final ModelPart rope;

		Model(ModelPart root) {
			super(root);
			this.root = root;
			arm = root.getChild("arm");
			pot = arm.getChild("pot");
			drum = root.getChild("drum");
			rope = root.getChild("rope");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.time;
			arm.xRot = armAngle(state.phase, t);
			pot.visible = state.phase == Phase.LIGHT
				|| state.phase == Phase.WIND && t > Phase.WIND.ticks - 12
				|| state.phase == Phase.LOOSE && t < CatapultEntity.LAUNCH;
			pot.yRot = state.phase == Phase.LIGHT ? Mth.sin(t * 0.7f) * 0.05f : 0;
			float wind = switch (state.phase) {
				case WIND -> wound(t);
				case LIGHT -> 1;
				case LOOSE -> Math.max(0, 1 - t / 3);
				default -> 0;
			};
			drum.xRot = -wind * Mth.TWO_PI * 3;
			float jolt = state.shake < 8 ? (1 - state.shake / 8) : 0;
			root.xRot = jolt * 0.04f * Mth.sin(state.shake * 3.1f);
			root.zRot = jolt * 0.05f * Mth.sin(state.shake * 2.3f + 1) + (1 - state.integrity) * 0.03f;
			stretchRope();
		}

		private void stretchRope() {
			float l = 10;
			float ay = AXLE_Y - l * Mth.cos(arm.xRot);
			float az = AXLE_Z - l * Mth.sin(arm.xRot);
			float dy = (DRUM_Y - 1.5f) - ay;
			float dz = DRUM_Z - az;
			rope.x = 0;
			rope.y = ay;
			rope.z = az;
			rope.xRot = (float) Math.atan2(dz, dy);
			rope.yScale = (float) Math.sqrt(dy * dy + dz * dz);
		}
	}
}
