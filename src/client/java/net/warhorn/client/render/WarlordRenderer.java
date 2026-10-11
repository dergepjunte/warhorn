package net.warhorn.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.warhorn.Warhorn;
import net.warhorn.entity.WarlordEntity;

public class WarlordRenderer extends IllagerRenderer<WarlordEntity, WarlordRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warhorn.id("warlord"), "main");
	private static final Identifier TEXTURE = Warhorn.id("textures/entity/warlord.png");
	private static final RenderType EMBERS = RenderTypes.eyes(Warhorn.id("textures/entity/warlord_embers.png"));

	public WarlordRenderer(EntityRendererProvider.Context context) {
		super(context, new WarlordModel(context.bakeLayer(LAYER)), 0.9f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EMBERS;
			}
		});
		addLayer(new EyesLayer<>(this) {
			private final RenderType rage = RenderTypes.eyes(Warhorn.id("textures/entity/warlord_rage.png"));

			@Override
			public void submit(PoseStack pose, SubmitNodeCollector collector, int light, WarlordRenderState state,
				float yRot, float xRot) {
				if (state.enraged) super.submit(pose, collector, light, state, yRot, xRot);
			}

			@Override
			public RenderType renderType() {
				return rage;
			}
		});
	}

	@Override
	public void extractRenderState(WarlordEntity entity, WarlordRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.action = entity.action();
		state.actionProgress = entity.actionProgress(partialTicks);
		state.enraged = entity.enraged() || entity.lastStand();
		state.combo = entity.combo();
		state.armed = entity.armed();
		state.lastStand = entity.lastStand();
		if (state.deathTime > 6) state.hasRedOverlay = false;
	}

	@Override
	protected float getFlipDegrees() {
		return 0f;
	}

	@Override
	public Identifier getTextureLocation(WarlordRenderState state) {
		return TEXTURE;
	}

	@Override
	public WarlordRenderState createRenderState() {
		return new WarlordRenderState();
	}
}
