package net.warhorn.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.warhorn.Warhorn;
import net.warhorn.entity.FirePotEntity;

public class FirePotRenderer extends EntityRenderer<FirePotEntity, FirePotRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("fire_pot"), "main");
	private final EntityModel<State> model;

	public FirePotRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new EntityModel<>(context.bakeLayer(LAYER)) {
		};
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("pot", CubeListBuilder.create()
			.texOffs(60, 52).addBox(-2, -2, -2, 4, 4, 4)
			.texOffs(80, 52).addBox(-1, -2, -3, 2, 2, 1), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, 0.2, 0);
		pose.rotate(Axis.YP.rotationDegrees(state.age * 23f));
		pose.rotate(Axis.XP.rotationDegrees(state.age * 31f));
		pose.scale(1.6f, 1.6f, 1.6f);
		collector.submitModel(model, state, pose, CatapultRenderer.TEXTURE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FirePotEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.age = entity.tickCount + partialTicks;
	}

	public static class State extends EntityRenderState {
		public float age;
	}
}
