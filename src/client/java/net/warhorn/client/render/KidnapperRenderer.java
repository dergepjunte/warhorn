package net.warhorn.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.warhorn.Warhorn;
import net.warhorn.entity.KidnapperEntity;

public class KidnapperRenderer extends IllagerRenderer<KidnapperEntity, KidnapperRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("kidnapper"), "main");
	static final Identifier TEXTURE = Warhorn.id("textures/entity/kidnapper.png");
	private static final RenderType GLOW = RenderTypes.eyes(Warhorn.id("textures/entity/kidnapper_glow.png"));

	public KidnapperRenderer(EntityRendererProvider.Context context) {
		super(context, new KidnapperModel(context.bakeLayer(LAYER)), 0.5f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public void extractRenderState(KidnapperEntity entity, KidnapperRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.state = entity.state();
		state.stateTime = entity.stateTicks() + partialTicks;
	}

	@Override
	public Identifier getTextureLocation(KidnapperRenderState state) {
		return TEXTURE;
	}

	@Override
	public KidnapperRenderState createRenderState() {
		return new KidnapperRenderState();
	}
}
