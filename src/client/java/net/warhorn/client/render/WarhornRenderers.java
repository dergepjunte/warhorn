package net.warhorn.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.warhorn.registry.WarhornEntities;

public final class WarhornRenderers {
	private WarhornRenderers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(WarlordRenderer.LAYER, WarlordModel::createBodyLayer);
		EntityRendererRegistry.register(WarhornEntities.WARLORD, WarlordRenderer::new);
		ModelLayerRegistry.registerModelLayer(WarlordAxeRenderer.LAYER, WarlordAxeRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.WARLORD_AXE, WarlordAxeRenderer::new);
		ModelLayerRegistry.registerModelLayer(WarBannerRenderer.LAYER, WarBannerRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.WAR_BANNER, WarBannerRenderer::new);
		ModelLayerRegistry.registerModelLayer(SapperRenderer.LAYER, SapperModel::createBodyLayer);
		EntityRendererRegistry.register(WarhornEntities.SAPPER, SapperRenderer::new);
		ModelLayerRegistry.registerModelLayer(KidnapperRenderer.LAYER, KidnapperModel::createBodyLayer);
		EntityRendererRegistry.register(WarhornEntities.KIDNAPPER, KidnapperRenderer::new);
		ModelLayerRegistry.registerModelLayer(CatapultRenderer.LAYER, CatapultRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.CATAPULT, CatapultRenderer::new);
		ModelLayerRegistry.registerModelLayer(FirePotRenderer.LAYER, FirePotRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.FIRE_POT, FirePotRenderer::new);
		ModelLayerRegistry.registerModelLayer(PowderKegRenderer.LAYER, PowderKegRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.POWDER_KEG, PowderKegRenderer::new);
		ModelLayerRegistry.registerModelLayer(BatteringRamRenderer.LAYER, BatteringRamRenderer::createLayer);
		EntityRendererRegistry.register(WarhornEntities.BATTERING_RAM, BatteringRamRenderer::new);
	}
}
