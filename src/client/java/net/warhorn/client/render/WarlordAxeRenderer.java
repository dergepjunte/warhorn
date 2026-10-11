package net.warhorn.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
import net.warhorn.entity.WarlordAxeEntity;

public class WarlordAxeRenderer extends EntityRenderer<WarlordAxeEntity, WarlordAxeRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("warlord_axe"), "main");
	private static final Identifier TEXTURE = Warhorn.id("textures/entity/warlord.png");
	private final Model model;

	public WarlordAxeRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new Model(context.bakeLayer(LAYER));
		shadowRadius = 0.4f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition axe = root.addOrReplaceChild("greataxe", CubeListBuilder.create()
			.texOffs(0, 64).addBox(-0.5f, -0.5f, -17, 1, 1, 24)
			.texOffs(52, 64).addBox(-0.5f, -6.5f, -16.5f, 1, 13, 6)
			.texOffs(52, 84).addBox(-0.5f, -7.5f, -11, 1, 15, 1)
			.texOffs(0, 90).addBox(-1, -1, 6.5f, 2, 2, 2)
			.texOffs(0, 95).addBox(-1, -1, -18.5f, 2, 2, 2), PartPose.offset(0, 0, 9));
		axe.addOrReplaceChild("wrap", CubeListBuilder.create()
			.texOffs(10, 90).addBox(-0.5f, -0.5f, -2, 1, 1, 6, new CubeDeformation(0.15f)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, 0.6, 0);
		pose.rotate(Axis.YP.rotationDegrees(180f - state.yRot));
		pose.rotate(Axis.XP.rotationDegrees(-16f));
		pose.rotate(Axis.YP.rotationDegrees(state.ageInTicks * 52f));
		pose.rotate(Axis.ZP.rotationDegrees(90f));
		pose.scale(-1.4f, -1.4f, 1.4f);
		collector.submitModel(model, state, pose, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarlordAxeEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
	}

	public static class State extends EntityRenderState {
		public float yRot;
	}

	private static final class Model extends EntityModel<State> {
		Model(ModelPart root) {
			super(root);
		}
	}
}
