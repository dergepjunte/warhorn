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
import net.warhorn.entity.SapperEntity;
import net.warhorn.entity.SapperEntity.State;

public class SapperModel extends IllagerModel<SapperRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart keg;
	private final ModelPart match;

	public SapperModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		keg = root.getChild("keg");
		match = rightArm.getChild("match");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create()
			.texOffs(32, 0).addBox(-4, -10, -4, 8, 12, 8, new CubeDeformation(0.45f)), PartPose.ZERO);
		head.addOrReplaceChild("nose", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-1, -1, -6, 2, 4, 2), PartPose.offset(0, -2, 0));
		head.addOrReplaceChild("cap", CubeListBuilder.create()
			.texOffs(64, 0).addBox(-4.5f, -10.6f, -4.5f, 9, 4, 9), PartPose.ZERO);
		head.addOrReplaceChild("goggles", CubeListBuilder.create()
			.texOffs(64, 14).addBox(-4.5f, -9.6f, -4.9f, 9, 2, 1), PartPose.rotation(-0.1f, 0, 0));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6)
			.texOffs(0, 38).addBox(-4, 0, -3, 8, 14, 6, new CubeDeformation(0.4f)), PartPose.ZERO);
		body.addOrReplaceChild("bandolier", CubeListBuilder.create()
			.texOffs(64, 20).addBox(-0.5f, -7.5f, -3.5f, 1, 15, 7, new CubeDeformation(0.25f)),
			PartPose.offsetAndRotation(0, 6, 0, 0, 0, -0.62f));
		body.addOrReplaceChild("pouch", CubeListBuilder.create()
			.texOffs(84, 20).addBox(-1, 0, -0.5f, 2, 3, 1), PartPose.offset(2.6f, 9.4f, -3.9f));
		body.addOrReplaceChild("pouch2", CubeListBuilder.create()
			.texOffs(84, 20).addBox(-1, 0, -0.5f, 2, 3, 1), PartPose.offset(-1.2f, 3.2f, -4.0f));
		body.addOrReplaceChild("coil", CubeListBuilder.create()
			.texOffs(92, 20).addBox(-1.5f, -1.5f, -1, 3, 3, 2), PartPose.offsetAndRotation(-4.6f, 10, 0, 0, 1.5708f, 0));

		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(40, 46).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		rightArm.addOrReplaceChild("match", CubeListBuilder.create()
			.texOffs(104, 0).addBox(-0.5f, -0.5f, -5, 1, 1, 5), PartPose.offsetAndRotation(-1, 9.5f, -0.5f, 0.35f, 0, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(40, 46).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		PartDefinition arms = root.addOrReplaceChild("arms", CubeListBuilder.create()
			.texOffs(44, 22).addBox(-8, -2, -2, 4, 8, 4)
			.texOffs(40, 38).addBox(-4, 2, -2, 8, 4, 4), PartPose.offsetAndRotation(0, 3, -1, -0.75f, 0, 0));
		arms.addOrReplaceChild("left_shoulder", CubeListBuilder.create()
			.texOffs(44, 22).mirror().addBox(4, -2, -2, 4, 8, 4), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 22).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-2, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(0, 22).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));

		PartDefinition keg = root.addOrReplaceChild("keg", kegCubes(), PartPose.offset(0, 9, -7.5f));
		keg.addOrReplaceChild("fuse", CubeListBuilder.create()
			.texOffs(40, 64).addBox(-0.5f, -4, -0.5f, 1, 4, 1), PartPose.offsetAndRotation(1.5f, -6, 1.5f, 0.3f, 0, -0.25f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	static CubeListBuilder kegCubes() {
		return CubeListBuilder.create().texOffs(0, 64).addBox(-5, -6, -5, 10, 12, 10);
	}

	@Override
	public void setupAnim(SapperRenderState state) {
		super.setupAnim(state);
		float t = state.stateTime;
		float walk = state.walkAnimationPos * 0.6662f;
		float speed = Math.min(1f, state.walkAnimationSpeed);
		body.xRot = 0;
		body.y = 0;
		head.y = 0;
		rightArm.y = 2;
		leftArm.y = 2;
		rightLeg.y = 12;
		leftLeg.y = 12;
		rightLeg.z = 0;
		leftLeg.z = 0;
		keg.x = 0;
		keg.y = 9;
		keg.z = -7.5f;
		keg.xRot = 0;
		keg.zRot = 0;
		keg.xScale = keg.yScale = keg.zScale = 0.78f;
		keg.visible = false;
		match.visible = false;
		if (state.deathTime > 0) return;

		switch (state.state) {
			case CARRY -> carry(t, walk, speed);
			case PLANT -> plant(t);
			case FLEE -> flee(t, walk, speed);
			case BARE -> {
			}
		}
	}

	private void carry(float t, float walk, float speed) {
		keg.visible = true;
		match.visible = true;
		hug(-0.62f);
		float lean = 0.12f + 0.2f * speed;
		body.xRot = lean;
		head.xRot = Math.min(head.xRot, 0.2f) - lean * 0.5f;
		float jolt = Math.abs(Mth.sin(walk)) * speed;
		keg.y = 11.5f - jolt * 1.1f + lean * 2;
		keg.z = -6.5f - lean * 3;
		keg.xRot = lean * 0.8f + Mth.sin(walk) * 0.06f * speed;
		keg.zRot = Mth.sin(walk * 0.5f) * 0.05f * speed;
		rightArm.xRot += Mth.sin(walk) * 0.05f * speed;
		leftArm.xRot -= Mth.sin(walk) * 0.05f * speed;
	}

	private void hug(float xRot) {
		rightArm.xRot = xRot;
		leftArm.xRot = xRot;
		rightArm.yRot = -0.18f;
		leftArm.yRot = 0.18f;
		rightArm.zRot = 0.12f;
		leftArm.zRot = -0.12f;
	}

	private void plant(float t) {
		float down = ease(Mth.clamp(t / 8f, 0, 1));
		float up = ease(Mth.clamp((t - 24) / 6f, 0, 1));
		float k = down * (1 - up);
		float drop = 6 * k;
		head.y = drop;
		body.y = drop;
		rightArm.y = 2 + drop;
		leftArm.y = 2 + drop;
		rightLeg.y = 12 + drop;
		leftLeg.y = 12 + drop;
		rightLeg.xRot = -1.5f * k;
		leftLeg.xRot = 0.95f * k;
		leftLeg.z = 1.5f * k;
		body.xRot = 0.42f * k;
		head.xRot = 0.35f * k;
		boolean held = t < SapperEntity.LIGHT;
		keg.visible = held;
		float set = ease(Mth.clamp(t / SapperEntity.SET_DOWN, 0, 1));
		keg.y = Mth.lerp(set, 11.5f, 19.3f);
		keg.z = Mth.lerp(set, -6.5f, -11);
		hug(Mth.lerp(set, -0.62f, -1.25f));
		match.visible = t < SapperEntity.LIGHT + 2;
		if (t >= SapperEntity.SET_DOWN && t < SapperEntity.LIGHT + 2) {
			float s = (t - SapperEntity.SET_DOWN) / (SapperEntity.LIGHT + 2 - SapperEntity.SET_DOWN);
			rightArm.xRot = -1.45f + 0.35f * Mth.abs(Mth.sin(s * Mth.PI * 2));
			rightArm.yRot = 0.1f;
			leftArm.xRot = -1.15f;
		}
		if (t >= SapperEntity.LIGHT) {
			float back = ease(Mth.clamp((t - SapperEntity.LIGHT) / 4f, 0, 1));
			rightArm.xRot = Mth.lerp(back, rightArm.xRot, -0.2f);
			leftArm.xRot = Mth.lerp(back, leftArm.xRot, -0.3f);
			rightArm.zRot = 0.4f * back;
			leftArm.zRot = -0.4f * back;
			head.yRot += 0.5f * back;
		}
	}

	private void flee(float t, float walk, float speed) {
		float s = Math.max(speed, 0.6f);
		rightArm.xRot = Mth.cos(walk + Mth.PI) * 1.5f * s;
		leftArm.xRot = Mth.cos(walk) * 1.5f * s;
		rightArm.zRot = 0.35f;
		leftArm.zRot = -0.35f;
		rightLeg.xRot = Mth.cos(walk) * 1.3f * s;
		leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.3f * s;
		body.xRot = 0.32f;
		head.xRot = -0.15f;
		float glance = Mth.clamp(Mth.sin(t * 0.18f) * 2.2f - 1.2f, 0, 1);
		head.yRot += 1.1f * glance;
	}

	private static float ease(float x) {
		return x * x * (3 - 2 * x);
	}
}
