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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.warhorn.Warhorn;
import net.warhorn.entity.PowderKegEntity;

public class PowderKegRenderer extends EntityRenderer<PowderKegEntity, PowderKegRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("powder_keg"), "main");
	private final Model model;

	public PowderKegRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new Model(context.bakeLayer(LAYER));
		shadowRadius = 0.45f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition keg = root.addOrReplaceChild("keg", SapperModel.kegCubes(), PartPose.offset(0, 18, 0));
		keg.addOrReplaceChild("fuse", CubeListBuilder.create()
			.texOffs(40, 64).addBox(-0.5f, -4, -0.5f, 1, 4, 1), PartPose.offsetAndRotation(1.5f, -6, 1.5f, 0.3f, 0, -0.25f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		float shake = (1 - state.fuse) * (1 - state.fuse) * 0.035f;
		pose.translate(Mth.sin(state.age * 3.1f) * shake, 0, Mth.cos(state.age * 2.7f) * shake);
		pose.rotate(Axis.YP.rotationDegrees(180f - state.yRot));
		pose.scale(-1.3f, -1.3f, 1.3f);
		pose.translate(0, -1.501, 0);
		float flash = state.fuseTicks < 20 && ((int) state.fuseTicks / 2) % 2 == 0 ? 0.8f : 0f;
		int light = flash > 0 ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
		collector.submitModel(model, state, pose, SapperRenderer.TEXTURE, light, OverlayTexture.pack(OverlayTexture.u(flash), 10),
			state.outlineColor);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PowderKegEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot();
		state.age = entity.tickCount + partialTicks;
		state.fuse = entity.fuseLeft(partialTicks);
		state.fuseTicks = state.fuse * (entity.loose() ? PowderKegEntity.LOOSE_FUSE : PowderKegEntity.FUSE);
	}

	public static class State extends EntityRenderState {
		public float yRot;
		public float age;
		public float fuse = 1;
		public float fuseTicks = 100;
	}

	private static final class Model extends EntityModel<State> {
		private final ModelPart fuse;

		Model(ModelPart root) {
			super(root);
			fuse = root.getChild("keg").getChild("fuse");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			fuse.yScale = Math.max(0.05f, state.fuse);
		}
	}
}
