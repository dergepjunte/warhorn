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
import net.warhorn.entity.WarlordEntity;
import net.warhorn.entity.WarlordEntity.Action;

public class WarlordModel extends IllagerModel<WarlordRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart cape;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart axe;
	private final ModelPart[] armour;

	public WarlordModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		cape = body.getChild("cape");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
		axe = rightArm.getChild("greataxe");
		armour = new ModelPart[] {rightArm.getChild("pauldron"), leftArm.getChild("pauldron"),
			body.getChild("cuirass"), body.getChild("tassets"), head.getChild("crest")};
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
		head.addOrReplaceChild("helm", CubeListBuilder.create()
			.texOffs(64, 0).addBox(-4.5f, -10.8f, -4.5f, 9, 6, 9)
			.texOffs(64, 15).addBox(-5, -5.6f, -5.1f, 10, 1, 3), PartPose.ZERO);
		head.addOrReplaceChild("crest", CubeListBuilder.create()
			.texOffs(100, 0).addBox(-0.5f, -3, -4, 1, 3, 9), PartPose.offset(0, -10.6f, 0));
		for (int side = -1; side <= 1; side += 2) {
			String n = side < 0 ? "right" : "left";
			PartDefinition base = head.addOrReplaceChild(n + "_horn", CubeListBuilder.create()
				.texOffs(64, 20).addBox(side < 0 ? -3 : 0, -1.5f, -1.5f, 3, 3, 3),
				PartPose.offsetAndRotation(side * 4.2f, -8.6f, 0, 0, 0, side * -0.35f));
			PartDefinition mid = base.addOrReplaceChild("mid", CubeListBuilder.create()
				.texOffs(76, 20).addBox(-1, -4, -1, 2, 4, 2),
				PartPose.offsetAndRotation(side * 2.4f, -0.5f, 0, -0.25f, 0, side * 0.55f));
			mid.addOrReplaceChild("tip", CubeListBuilder.create()
				.texOffs(84, 20).addBox(-0.5f, -3.5f, -0.5f, 1, 4, 1),
				PartPose.offsetAndRotation(0, -3.6f, 0, -0.35f, 0, side * 0.45f));
		}

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6)
			.texOffs(0, 38).addBox(-4, 0, -3, 8, 20, 6, new CubeDeformation(0.5f)), PartPose.ZERO);
		body.addOrReplaceChild("cuirass", CubeListBuilder.create()
			.texOffs(64, 28).addBox(-4.5f, -0.4f, -3.6f, 9, 9, 7, new CubeDeformation(0.25f))
			.texOffs(96, 28).addBox(-4.6f, 8.6f, -3.7f, 9, 2, 7, new CubeDeformation(0.3f))
			.texOffs(96, 38).addBox(-1.5f, 8.2f, -4.4f, 3, 3, 1), PartPose.ZERO);
		body.addOrReplaceChild("tassets", CubeListBuilder.create()
			.texOffs(64, 45).addBox(-4.2f, 0, -0.5f, 4, 7, 1)
			.texOffs(74, 45).addBox(0.2f, 0, -0.5f, 4, 7, 1), PartPose.offsetAndRotation(0, 10.8f, -3.9f, -0.08f, 0, 0));
		body.addOrReplaceChild("cape", CubeListBuilder.create()
			.texOffs(88, 64).addBox(-5, 0, 0, 10, 21, 1), PartPose.offsetAndRotation(0, 0.4f, 3.4f, 0.1f, 0, 0));

		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(40, 46).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		rightArm.addOrReplaceChild("pauldron", CubeListBuilder.create()
			.texOffs(64, 54).addBox(-4.4f, -3.6f, -3.2f, 6, 4, 6)
			.texOffs(88, 54).addBox(-4.8f, -1.2f, -3.4f, 4, 2, 7), PartPose.rotation(0, 0, 0.18f));
		rightArm.addOrReplaceChild("bracer", CubeListBuilder.create()
			.texOffs(88, 45).addBox(-3.4f, 5, -2.4f, 5, 4, 5), PartPose.ZERO);
		PartDefinition axe = rightArm.addOrReplaceChild("greataxe", CubeListBuilder.create()
			.texOffs(0, 64).addBox(-0.5f, -0.5f, -17, 1, 1, 24)
			.texOffs(52, 64).addBox(-0.5f, -6.5f, -16.5f, 1, 13, 6)
			.texOffs(52, 84).addBox(-0.5f, -7.5f, -11, 1, 15, 1)
			.texOffs(0, 90).addBox(-1, -1, 6.5f, 2, 2, 2)
			.texOffs(0, 95).addBox(-1, -1, -18.5f, 2, 2, 2), PartPose.offset(-1, 9, 0));
		axe.addOrReplaceChild("wrap", CubeListBuilder.create()
			.texOffs(10, 90).addBox(-0.5f, -0.5f, -2, 1, 1, 6, new CubeDeformation(0.15f)), PartPose.ZERO);

		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(40, 46).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create()
			.texOffs(64, 54).mirror().addBox(-1.6f, -3.6f, -3.2f, 6, 4, 6)
			.texOffs(88, 54).mirror().addBox(0.8f, -1.2f, -3.4f, 4, 2, 7), PartPose.rotation(0, 0, -0.18f));
		leftArm.addOrReplaceChild("bracer", CubeListBuilder.create()
			.texOffs(88, 45).mirror().addBox(-1.6f, 5, -2.4f, 5, 4, 5), PartPose.ZERO);

		PartDefinition arms = root.addOrReplaceChild("arms", CubeListBuilder.create()
			.texOffs(44, 22).addBox(-8, -2, -2, 4, 8, 4)
			.texOffs(40, 38).addBox(-4, 2, -2, 8, 4, 4), PartPose.offsetAndRotation(0, 3, -1, -0.75f, 0, 0));
		arms.addOrReplaceChild("left_shoulder", CubeListBuilder.create()
			.texOffs(44, 22).mirror().addBox(4, -2, -2, 4, 8, 4), PartPose.ZERO);

		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 22).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-2, 12, 0));
		rightLeg.addOrReplaceChild("greave", CubeListBuilder.create()
			.texOffs(108, 45).addBox(-2.4f, 6.5f, -2.5f, 5, 5, 5), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(0, 22).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));
		leftLeg.addOrReplaceChild("greave", CubeListBuilder.create()
			.texOffs(108, 45).mirror().addBox(-2.6f, 6.5f, -2.5f, 5, 5, 5), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(WarlordRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float p = state.actionProgress;
		axe.xRot = -0.35f;
		axe.yRot = 0;
		axe.visible = state.armed;
		cape.xRot = 0.1f + state.walkAnimationSpeed * 0.75f + Mth.sin(age * 0.11f) * 0.045f;
		cape.zRot = Mth.sin(age * 0.07f) * 0.02f;
		body.xRot = 0;
		boolean stripped = state.lastStand && !(state.action == Action.LAST_STAND && p < 0.5f);
		for (ModelPart plate : armour) plate.visible = !stripped;

		if (state.deathTime > 0) {
			dying(state.deathTime, age);
			return;
		}

		switch (state.action) {
			case SLAM_WINDUP -> {
				float raise = ease(Math.min(1, p * 1.4f));
				rightArm.xRot = Mth.lerp(raise, rightArm.xRot, -3.05f);
				leftArm.xRot = Mth.lerp(raise, leftArm.xRot, -2.9f);
				rightArm.yRot = 0.25f * raise;
				leftArm.yRot = -0.45f * raise;
				rightArm.zRot = 0;
				leftArm.zRot = 0;
				axe.xRot = Mth.lerp(raise, -0.35f, 0.4f);
				head.xRot = Mth.lerp(raise, head.xRot, -0.35f);
				body.xRot = -0.12f * raise;
				if (p > 0.8f) rightArm.xRot += Mth.sin(age * 3.1f) * 0.04f;
			}
			case SLAM -> {
				float down = ease(Math.min(1, p * 4f));
				rightArm.xRot = Mth.lerp(down, -3.05f, -0.55f);
				leftArm.xRot = Mth.lerp(down, -2.9f, -0.6f);
				rightArm.yRot = 0.25f;
				leftArm.yRot = -0.45f;
				axe.xRot = Mth.lerp(down, 0.4f, -1.35f);
				head.xRot = 0.3f;
				body.xRot = 0.28f * down;
			}
			case CHARGE_WINDUP -> {
				float coil = ease(p);
				rightArm.xRot = 0.6f * coil;
				leftArm.xRot = 0.5f * coil;
				rightArm.zRot = 0.15f * coil;
				leftArm.zRot = -0.15f * coil;
				axe.xRot = -0.35f + 0.9f * coil;
				head.xRot = 0.25f * coil;
				body.xRot = 0.22f * coil;
				rightLeg.xRot = -0.35f * coil;
				leftLeg.xRot = 0.45f * coil;
			}
			case CHARGE -> {
				float run = age * 1.1f;
				rightLeg.xRot = Mth.cos(run) * 1.15f;
				leftLeg.xRot = Mth.cos(run + Mth.PI) * 1.15f;
				rightArm.xRot = 0.7f;
				leftArm.xRot = 0.6f;
				axe.xRot = 0.55f;
				head.xRot = 0.3f;
				body.xRot = 0.32f;
			}
			case ROAR -> {
				float spread = ease(Math.min(1, p * 2.5f));
				rightArm.xRot = -0.55f * spread;
				leftArm.xRot = -0.55f * spread;
				rightArm.zRot = 1.05f * spread;
				leftArm.zRot = -1.05f * spread;
				rightArm.yRot = 0;
				leftArm.yRot = 0;
				head.xRot = -0.65f * spread + Mth.sin(age * 2.3f) * 0.03f * spread;
				body.xRot = -0.14f * spread;
				cape.xRot += 0.35f * spread;
			}
			case STUNNED -> {
				head.xRot = 0.65f;
				head.zRot = Mth.sin(age * 0.35f) * 0.18f;
				rightArm.xRot = 0.2f + Mth.sin(age * 0.2f) * 0.05f;
				leftArm.xRot = 0.15f;
				rightArm.zRot = 0.1f;
				leftArm.zRot = -0.1f;
				axe.xRot = 0.9f;
				body.xRot = 0.35f;
			}
			case SWING -> swing(state.combo, p);
			case THROW_WINDUP -> {
				float cock = ease(Math.min(1, p / 0.8f));
				float snap = p < 0.8f ? 0 : ease((p - 0.8f) / 0.2f);
				rightArm.xRot = Mth.lerp(snap, Mth.lerp(cock, -0.3f, -2.95f), -1.3f);
				rightArm.yRot = Mth.lerp(snap, 0.35f * cock, -0.2f);
				rightArm.zRot = 0.1f * cock;
				axe.xRot = Mth.lerp(snap, Mth.lerp(cock, 0.78f, 0.5f), 1.4f);
				leftArm.xRot = Mth.lerp(cock, 0, -1.45f);
				leftArm.yRot = 0.15f * cock;
				body.yRot = Mth.lerp(snap, 0.45f * cock, -0.3f);
				body.xRot = Mth.lerp(snap, -0.1f * cock, 0.2f);
				head.yRot -= body.yRot * 0.6f;
				rightLeg.xRot = Mth.lerp(snap, 0.25f * cock, -0.35f);
				leftLeg.xRot = Mth.lerp(snap, -0.3f * cock, 0.3f);
			}
			case UNARMED -> {
				float in = ease(Math.min(1, p * 8));
				rightArm.xRot = Mth.lerp(in, -1.3f, -1.45f) + Mth.sin(age * 0.2f) * 0.04f;
				rightArm.yRot = 0.15f;
				rightArm.zRot = 0.2f;
				leftArm.xRot = -0.55f;
				leftArm.zRot = -0.35f;
				body.xRot = 0.1f;
				head.xRot = -0.1f;
			}
			case CATCH -> {
				float k = ease(p);
				float recoil = Mth.sin(Math.min(1, p * 2.2f) * Mth.PI);
				rightArm.xRot = Mth.lerp(k, -1.45f, -0.22f) + 0.7f * recoil;
				rightArm.yRot = 0.15f * (1 - k) + 0.3f * recoil;
				body.yRot = 0.35f * recoil;
				axe.xRot = Mth.lerp(k, 1.3f, 0.78f);
				leftArm.xRot = -0.55f * (1 - k);
			}
			case LEAP_WINDUP -> {
				float crouch = ease(Math.min(1, p * 1.6f));
				lower(3.5f * crouch);
				body.xRot = 0.45f * crouch;
				head.xRot = -0.35f * crouch;
				rightArm.xRot = 0.9f * crouch;
				leftArm.xRot = 0.8f * crouch;
				rightArm.zRot = 0.25f * crouch;
				leftArm.zRot = -0.25f * crouch;
				axe.xRot = Mth.lerp(crouch, 0.78f, 0.1f);
				rightLeg.xRot = -0.55f * crouch;
				leftLeg.xRot = 0.35f * crouch;
				if (p > 0.75f) body.xRot += Mth.sin(age * 2.7f) * 0.03f;
			}
			case LEAP -> {
				float up = ease(Math.min(1, p * 2.5f));
				float down = p < 0.55f ? 0 : ease((p - 0.55f) / 0.45f);
				rightArm.xRot = Mth.lerp(down, Mth.lerp(up, 0.9f, -3.0f), -1.6f);
				leftArm.xRot = Mth.lerp(down, Mth.lerp(up, 0.8f, -2.85f), -1.5f);
				rightArm.yRot = 0.2f;
				leftArm.yRot = -0.4f;
				axe.xRot = Mth.lerp(down, 0.35f, -0.6f);
				body.xRot = Mth.lerp(down, -0.25f * up, 0.3f);
				head.xRot = Mth.lerp(down, -0.3f, 0.15f);
				rightLeg.xRot = -0.9f * up;
				leftLeg.xRot = -0.35f * up;
				cape.xRot += 0.6f * up;
			}
			case LEAP_RECOVER -> {
				float yank = p < 0.66f ? 0 : ease(Math.min(1, (p - 0.66f) / 0.12f));
				float settle = p < 0.8f ? 0 : ease((p - 0.8f) / 0.2f);
				float strain = (1 - yank) * Mth.sin(age * 1.7f) * 0.03f;
				rightArm.xRot = Mth.lerp(settle, Mth.lerp(yank, -0.55f, -1.9f), -0.22f) + strain;
				leftArm.xRot = Mth.lerp(settle, Mth.lerp(yank, -0.6f, -1.7f), 0) + strain;
				rightArm.yRot = 0.25f * (1 - settle);
				leftArm.yRot = -0.45f * (1 - settle);
				axe.xRot = Mth.lerp(settle, Mth.lerp(yank, -1.35f, 0.2f), 0.78f);
				body.xRot = Mth.lerp(settle, Mth.lerp(yank, 0.35f, -0.1f), 0);
				head.xRot = Mth.lerp(settle, 0.25f, 0);
				lower(2f * (1 - yank));
			}
			case BANNER -> {
				float reach = ease(Math.min(1, p / 0.35f));
				float plant = p < 0.35f ? 0 : ease(Math.min(1, (p - 0.35f) / 0.24f));
				float hail = p < 0.66f ? 0 : ease(Math.min(1, (p - 0.66f) / 0.2f));
				leftArm.xRot = Mth.lerp(plant, Mth.lerp(reach, 0, -2.9f), -0.75f);
				leftArm.zRot = Mth.lerp(plant, -0.35f * reach, -0.45f);
				body.yRot = Mth.lerp(plant, 0.2f * reach, -0.3f) * (1 - hail);
				body.xRot = 0.25f * plant * (1 - hail) - 0.12f * hail;
				lower(1.5f * plant * (1 - hail));
				rightArm.xRot = Mth.lerp(hail, -0.22f, -2.85f);
				rightArm.zRot = 0.25f * hail;
				axe.xRot = Mth.lerp(hail, 0.78f, 0.25f);
				head.xRot = Mth.lerp(hail, 0.2f * plant, -0.55f);
			}
			case LAST_STAND -> lastStand(p, age);
			case NONE -> {
				head.zRot = 0;
				float stride = Mth.cos(state.walkAnimationPos * 0.6662f) * state.walkAnimationSpeed;
				float swing = Mth.sin(state.swingAnimation * Mth.PI);
				rightArm.xRot = -0.22f - stride * 0.25f - swing * 1.9f;
				rightArm.yRot = 0.08f;
				rightArm.zRot = 0.06f;
				leftArm.xRot = stride * 0.7f + Mth.sin(age * 0.06f) * 0.03f;
				leftArm.zRot = -0.06f;
				axe.xRot = Mth.lerp(swing, 0.78f, -0.4f);
				if (!state.armed) {
					rightArm.xRot = -0.3f - stride * 0.4f;
				}
				if (state.lastStand) {
					body.xRot = 0.14f + Mth.sin(age * 0.25f) * 0.025f;
					head.xRot = Math.min(head.xRot, 0.1f) - 0.1f;
					leftArm.zRot = -0.25f;
				}
			}
		}
	}

	private void swing(int combo, float p) {
		float hit = (float) WarlordEntity.SWING_HIT[combo] / WarlordEntity.SWING_TICKS[combo];
		float windEnd = hit - 0.16f;
		float wind = ease(Mth.clamp(p / windEnd, 0, 1));
		float cut = Mth.clamp((p - windEnd) / 0.2f, 0, 1);
		cut = cut * cut * (3 - 2 * cut);
		float back = ease(Mth.clamp((p - (hit + 0.2f)) / (0.8f - hit), 0, 1));
		float[][] pose = switch (combo) {
			case 0 -> new float[][] {
				{-0.22f, -1.45f, -1.35f},
				{0.08f, 1.15f, -1.05f},
				{0f, 0.5f, -0.55f},
				{0.78f, 1.25f, 1.15f},
				{0f, -0.7f, -0.25f},
				{-0.06f, -0.55f, -0.2f},
			};
			case 1 -> new float[][] {
				{-0.22f, -0.75f, -2.45f},
				{0.08f, -0.95f, 0.75f},
				{0f, -0.5f, 0.4f},
				{0.78f, 1.35f, 1.0f},
				{0f, -0.2f, -0.9f},
				{-0.06f, -0.15f, -0.6f},
			};
			default -> new float[][] {
				{-0.22f, -3.05f, -0.5f},
				{0.08f, 0.25f, 0.2f},
				{0f, 0.1f, 0f},
				{0.78f, 0.25f, -1.4f},
				{0f, -2.9f, -0.55f},
				{-0.06f, 0f, 0f},
			};
		};
		rightArm.xRot = blend(pose[0], wind, cut, back);
		rightArm.yRot = blend(pose[1], wind, cut, back);
		body.yRot = blend(pose[2], wind, cut, back);
		axe.xRot = blend(pose[3], wind, cut, back);
		leftArm.xRot = blend(pose[4], wind, cut, back);
		leftArm.zRot = blend(pose[5], wind, cut, back);
		leftArm.yRot = combo == 2 ? -0.45f * (1 - back) : 0;
		float lean = combo == 2 ? blend(new float[] {0, -0.18f, 0.38f}, wind, cut, back) : 0.12f * cut * (1 - back);
		body.xRot = lean;
		head.yRot -= body.yRot * 0.7f;
		rightLeg.xRot = -0.35f * cut * (1 - back);
		leftLeg.xRot = 0.3f * cut * (1 - back);
		if (combo == 2) lower(2.2f * cut * (1 - back));
	}

	private static float blend(float[] k, float wind, float cut, float back) {
		float v = Mth.lerp(wind, k[0], k[1]);
		v = Mth.lerp(cut, v, k[2]);
		return Mth.lerp(back, v, k[0]);
	}

	private void lastStand(float p, float age) {
		float burst = (float) WarlordEntity.LAST_STAND_BURST / WarlordEntity.Action.LAST_STAND.ticks;
		float kneel = ease(Math.min(1, p / 0.18f)) * (1 - ease(Mth.clamp((p - 0.36f) / 0.12f, 0, 1)));
		float grip = ease(Mth.clamp((p - 0.36f) / (burst - 0.36f), 0, 1)) * (p < burst ? 1 : 0);
		float roar = p < burst ? 0 : 1 - 0.5f * ease(Mth.clamp((p - 0.85f) / 0.15f, 0, 1));
		float breath = Mth.sin(age * 0.35f) * 0.04f;

		lower(5.5f * kneel);
		rightLeg.xRot = -1.35f * kneel;
		leftLeg.xRot = 1.25f * kneel;
		body.xRot = (0.5f + breath) * kneel - 0.3f * roar + 0.15f * grip;
		head.xRot = 0.55f * kneel - 0.75f * roar;
		rightArm.xRot = -0.65f * kneel - 1.55f * grip - 0.5f * roar;
		leftArm.xRot = -0.8f * kneel - 1.55f * grip - 0.5f * roar;
		rightArm.yRot = -0.65f * grip;
		leftArm.yRot = 0.65f * grip;
		rightArm.zRot = 1.35f * roar;
		leftArm.zRot = -1.35f * roar;
		axe.xRot = -1.2f * kneel + 0.78f * (1 - kneel);
		cape.xRot += 0.5f * roar;
		if (grip > 0 && grip < 1) {
			rightArm.xRot += Mth.sin(age * 2.9f) * 0.05f;
			leftArm.xRot -= Mth.sin(age * 2.9f) * 0.05f;
		}
	}

	private void dying(float deathTime, float age) {
		float kneel = ease(Mth.clamp(deathTime / 16f, 0, 1));
		float slump = ease(Mth.clamp((deathTime - 26f) / 24f, 0, 1));
		lower(9f * kneel);
		rightLeg.xRot = 1.35f * kneel;
		leftLeg.xRot = 1.35f * kneel;
		rightLeg.yRot = 0.1f * kneel;
		leftLeg.yRot = -0.1f * kneel;
		body.xRot = 0.32f * kneel + 0.3f * slump;
		head.xRot = 0.45f * kneel + 0.45f * slump;
		head.zRot = 0.12f * slump;
		rightArm.xRot = -0.75f * kneel + 0.35f * slump;
		rightArm.yRot = 0.1f;
		axe.xRot = Mth.lerp(kneel, 0.78f, -1.3f);
		leftArm.xRot = -0.35f * kneel + 0.5f * slump + Mth.sin(age * 0.1f) * 0.02f * slump;
		leftArm.zRot = 0.08f;
		cape.xRot = 0.05f;
	}

	private void lower(float px) {
		head.y += px;
		body.y += px;
		rightArm.y += px;
		leftArm.y += px;
		rightLeg.y += px;
		leftLeg.y += px;
	}

	private static float ease(float x) {
		return x * x * (3 - 2 * x);
	}
}
