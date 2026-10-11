package net.warhorn.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.warhorn.Warhorn;
import net.warhorn.entity.SapperEntity;

public class SapperRenderer extends IllagerRenderer<SapperEntity, SapperRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("sapper"), "main");
	static final Identifier TEXTURE = Warhorn.id("textures/entity/sapper.png");
	private static final RenderType GLOW = RenderTypes.eyes(Warhorn.id("textures/entity/sapper_glow.png"));

	public SapperRenderer(EntityRendererProvider.Context context) {
		super(context, new SapperModel(context.bakeLayer(LAYER)), 0.5f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public void extractRenderState(SapperEntity entity, SapperRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.state = entity.state();
		state.stateTime = entity.stateTicks() + partialTicks;
	}

	@Override
	public Identifier getTextureLocation(SapperRenderState state) {
		return TEXTURE;
	}

	@Override
	public SapperRenderState createRenderState() {
		return new SapperRenderState();
	}
}
