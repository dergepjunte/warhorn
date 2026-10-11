package net.warhorn.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.util.Mth;
import net.warhorn.entity.KidnapperEntity;

public class KidnapperModel extends IllagerModel<KidnapperRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart cloak;
	private final ModelPart sack;
	private final ModelPart emptySack;
	private final ModelPart hood;

	public KidnapperModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		cloak = body.getChild("cloak");
		sack = root.getChild("sack");
		emptySack = leftArm.getChild("empty_sack");
		hood = head.getChild("hat");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create()
			.texOffs(32, 0).addBox(-4, -10, -4, 8, 12, 8, new CubeDeformation(0.5f)), PartPose.ZERO);
		head.addOrReplaceChild("nose", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-1, -1, -6, 2, 4, 2), PartPose.offset(0, -2, 0));
		head.addOrReplaceChild("mask", CubeListBuilder.create()
			.texOffs(64, 0).addBox(-4.5f, -3.6f, -4.6f, 9, 4, 1), PartPose.ZERO);

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6)
			.texOffs(0, 38).addBox(-4, 0, -3, 8, 14, 6, new CubeDeformation(0.45f)), PartPose.ZERO);
		body.addOrReplaceChild("cloak", CubeListBuilder.create()
			.texOffs(64, 8).addBox(-4.5f, 0, 0, 9, 17, 1), PartPose.offsetAndRotation(0, 0.2f, 3.4f, 0.08f, 0, 0));
		body.addOrReplaceChild("coil", CubeListBuilder.create()
			.texOffs(88, 0).addBox(-1.5f, -1.5f, -1, 3, 3, 2), PartPose.offsetAndRotation(4.7f, 8.5f, 0.5f, 0, 1.5708f, 0));

		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(40, 46).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(40, 46).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		leftArm.addOrReplaceChild("empty_sack", CubeListBuilder.create()
			.texOffs(88, 8).addBox(-3, 0, -1, 6, 9, 2), PartPose.offsetAndRotation(1, 9, 0, 0, 0, 0));
		PartDefinition arms = root.addOrReplaceChild("arms", CubeListBuilder.create()
			.texOffs(44, 22).addBox(-8, -2, -2, 4, 8, 4)
			.texOffs(40, 38).addBox(-4, 2, -2, 8, 4, 4), PartPose.offsetAndRotation(0, 3, -1, -0.75f, 0, 0));
		arms.addOrReplaceChild("left_shoulder", CubeListBuilder.create()
			.texOffs(44, 22).mirror().addBox(4, -2, -2, 4, 8, 4), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 22).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-2, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(0, 22).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));

		PartDefinition sack = root.addOrReplaceChild("sack", CubeListBuilder.create()
			.texOffs(0, 64).addBox(-6, 0, -5.5f, 12, 16, 11), PartPose.ZERO);
		sack.addOrReplaceChild("belly", CubeListBuilder.create()
			.texOffs(0, 92).addBox(-6.5f, 5, -6, 13, 9, 12), PartPose.ZERO);
		sack.addOrReplaceChild("knot", CubeListBuilder.create()
			.texOffs(48, 64).addBox(-2, -3, -2, 4, 3, 4), PartPose.offset(0, 0.5f, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(KidnapperRenderState state) {
		super.setupAnim(state);
		hood.visible = true;
		float t = state.stateTime;
		float walk = state.walkAnimationPos * 0.6662f;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		body.xRot = 0;
		body.y = 0;
		head.y = 0;
		rightArm.y = 2;
		leftArm.y = 2;
		cloak.xRot = 0.08f + 0.5f * speed;
		resetSack();
		emptySack.visible = false;
		emptySack.xRot = 0;
		emptySack.zRot = 0;
		emptySack.xScale = emptySack.yScale = emptySack.zScale = 1;
		if (state.deathTime > 0) {
			emptySack.visible = true;
			return;
		}
		switch (state.state) {
			case HUNT -> hunt(t, walk, speed);
			case GRAB -> grab(t);
			case CARRY -> carry(t, walk, speed);
		}
	}

	private void resetSack() {
		sack.visible = false;
		sack.x = 0;
		sack.y = 0;
		sack.z = 0;
		sack.xRot = 0;
		sack.yRot = 0;
		sack.zRot = 0;
		sack.xScale = sack.yScale = sack.zScale = 1;
	}

	private void hunt(float t, float walk, float speed) {
		emptySack.visible = true;
		float lean = 0.28f + 0.14f * speed;
		body.xRot = lean;
		body.y = 1.2f;
		head.y = 1.2f;
		rightArm.y = 3.2f;
		leftArm.y = 3.2f;
		head.xRot = Math.min(head.xRot, 0.25f) - lean * 0.4f;
		rightArm.xRot = -0.35f - 0.25f * speed + Mth.cos(walk) * 0.5f * speed;
		leftArm.xRot = 0.25f + Mth.cos(walk + Mth.PI) * 0.4f * speed;
		leftArm.zRot = -0.1f;
		emptySack.xRot = 0.25f + 0.35f * speed + Mth.sin(walk) * 0.2f * speed;
		emptySack.zRot = Mth.sin(t * 0.13f) * 0.06f;
		if (speed < 0.15f) {
			head.yRot += Mth.sin(t * 0.07f) * 0.6f;
		}
	}

	private void grab(float t) {
		int bag = KidnapperEntity.BAGGED;
		if (t < bag) {
			emptySack.visible = true;
			float up = ease(Mth.clamp(t / 6f, 0, 1));
			float down = ease(Mth.clamp((t - 6) / (bag - 6f), 0, 1));
			float arms = -2.9f * up + 1.9f * down;
			rightArm.xRot = arms;
			leftArm.xRot = arms;
			rightArm.zRot = 0.25f * up;
			leftArm.zRot = -0.25f * up;
			body.xRot = -0.15f * up + 0.55f * down;
			head.xRot = -0.2f * up + 0.4f * down;
			float open = 1 + 0.7f * up;
			emptySack.xScale = emptySack.zScale = open;
			emptySack.yScale = 1 + 0.3f * up;
			emptySack.xRot = Mth.PI * up * (1 - down * 0.4f);
			emptySack.zRot = -0.4f * up;
			return;
		}
		float s = ease(Mth.clamp((t - bag) / (KidnapperEntity.State.GRAB.ticks - bag), 0, 1));
		sack.visible = true;
		sack.x = Mth.lerp(s, 0, CARRY_X);
		sack.y = Mth.lerp(s, 8.5f, CARRY_Y);
		sack.z = Mth.lerp(s, -9, CARRY_Z);
		sack.xRot = Mth.lerp(s, 0, CARRY_XROT);
		sack.zRot = Mth.lerp(s, 0, CARRY_ZROT);
		body.xRot = Mth.lerp(s, 0.55f, 0.18f) - 0.25f * Mth.sin(s * Mth.PI);
		head.xRot = Mth.lerp(s, 0.4f, 0);
		rightArm.xRot = Mth.lerp(s, -1.0f, CARRY_ARM);
		leftArm.xRot = Mth.lerp(s, -1.0f, -0.4f);
		rightArm.zRot = Mth.lerp(s, 0, 0.2f);
		sack.xScale = sack.zScale = 1 + 0.08f * Mth.sin(s * Mth.PI * 3) * (1 - s);
	}

	private static final float CARRY_X = -5.5f;
	private static final float CARRY_Y = -5.5f;
	private static final float CARRY_Z = 3.5f;
	private static final float CARRY_XROT = 0.55f;
	private static final float CARRY_ZROT = -0.3f;
	private static final float CARRY_ARM = -3.25f;

	private void carry(float t, float walk, float speed) {
		sack.visible = true;
		float bounce = Math.abs(Mth.sin(walk)) * speed;
		float lean = 0.18f + 0.22f * speed;
		body.xRot = lean;
		head.xRot = Math.min(head.xRot, 0.2f) - lean * 0.3f;
		sack.x = CARRY_X;
		sack.y = CARRY_Y - bounce * 1.3f;
		sack.z = CARRY_Z;
		sack.xRot = CARRY_XROT + lean * 0.5f + Mth.sin(walk) * 0.05f * speed;
		sack.zRot = CARRY_ZROT + Mth.sin(walk * 0.5f) * 0.06f * speed;
		rightArm.xRot = CARRY_ARM + bounce * 0.1f;
		rightArm.yRot = 0.1f;
		rightArm.zRot = 0.2f;
		leftArm.xRot = Mth.cos(walk) * 1.2f * Math.max(speed, 0.3f) - 0.1f;
		leftArm.zRot = -0.15f;
		float k = t % 44;
		if (k < 9) {
			float pulse = Mth.sin(k / 9f * Mth.PI);
			sack.zRot += Mth.sin(t * 2.3f) * 0.16f * pulse;
			sack.xRot += Mth.sin(t * 1.7f + 1) * 0.1f * pulse;
			sack.xScale = sack.zScale = 1 + 0.07f * pulse * Math.abs(Mth.sin(t * 2.9f));
			body.zRot = Mth.sin(t * 2.3f) * 0.04f * pulse;
		}
		float glance = Mth.clamp(Mth.sin(t * 0.09f) * 2.4f - 1.6f, 0, 1);
		head.yRot += 0.9f * glance;
	}

	private static float ease(float x) {
		return x * x * (3 - 2 * x);
	}
}
