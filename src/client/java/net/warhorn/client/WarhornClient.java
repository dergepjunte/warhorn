package net.warhorn.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.warhorn.Warhorn;
import net.warhorn.client.audio.Score;
import net.warhorn.client.cinematic.CameraEffects;
import net.warhorn.client.cinematic.CinematicPlayer;
import net.warhorn.client.hud.CinematicOverlay;
import net.warhorn.client.hud.CueOverlay;
import net.warhorn.client.hud.SiegeHud;
import net.warhorn.client.qa.QaRunner;
import net.warhorn.client.render.WarhornRenderers;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CuePayload;
import net.warhorn.net.RuinPayload;
import net.warhorn.client.rebuild.RuinGhosts;
import net.warhorn.net.SiegeStatePayload;

import java.util.List;

public final class WarhornClient implements ClientModInitializer {
	private static final List<Identifier> HIDDEN_IN_CINEMATICS = List.of(
		VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
		VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
		VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR,
		VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE,
		VanillaHudElements.CHAT, VanillaHudElements.SUBTITLES);

	@Override
	public void onInitializeClient() {
		WarhornRenderers.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) QaRunner.register();

		ClientPlayNetworking.registerGlobalReceiver(CinematicPayload.TYPE, (payload, context) -> CinematicPlayer.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(SiegeStatePayload.TYPE, (payload, context) -> SiegeClientState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(CuePayload.TYPE, (payload, context) -> handleCue(payload));
		ClientPlayNetworking.registerGlobalReceiver(RuinPayload.TYPE, (payload, context) -> RuinGhosts.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RuinGhosts.reset();
			CinematicPlayer.stop(false);
			SiegeClientState.reset();
			CueOverlay.reset();
			Score.stop();
		});

		for (Identifier element : HIDDEN_IN_CINEMATICS) {
			HudElementRegistry.replaceElement(element, original -> (graphics, delta) -> {
				if (!CinematicPlayer.active()) original.extractRenderState(graphics, delta);
			});
		}
		HudElementRegistry.addLast(Warhorn.id("siege_hud"), SiegeHud::extract);
		HudElementRegistry.addLast(Warhorn.id("ruin_hud"), net.warhorn.client.hud.RuinHud::extract);
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(RuinGhosts::collect);
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(net.warhorn.client.rebuild.Waymarks::collect);
		HudElementRegistry.addLast(Warhorn.id("cues"), CueOverlay::extract);
		HudElementRegistry.addLast(Warhorn.id("cinematic"), CinematicOverlay::extract);

		ClientTickEvents.END_CLIENT_TICK.register(client -> SiegeClientState.tick());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || client.isPaused()) return;
			for (var e : client.level.entitiesForRendering()) {
				if (e instanceof net.warhorn.entity.BatteringRamEntity ram) ram.alignCrew();
			}
		});
		net.warhorn.siege.RebuildService.clientSite = net.warhorn.client.rebuild.Waymarks::houseAt;
	}

	private static void handleCue(CuePayload cue) {
		switch (cue.kind()) {
			case CuePayload.SHAKE -> CameraEffects.addTrauma(cue.strength() * 0.8f);
			case CuePayload.FOV_PUNCH -> CameraEffects.punchFov(cue.strength());
			case CuePayload.HITSTOP -> {
				CameraEffects.impactFrame(cue.strength());
				Score.impact(cue.strength());
			}
			case CuePayload.FLASH -> CameraEffects.flash(cue.strength());
			case CuePayload.ACT_CARD -> CueOverlay.card(cue.text(), cue.subtext(), cue.duration());
			case CuePayload.TOAST -> CueOverlay.toast(cue.text(), cue.duration());
			case CuePayload.HINT -> CueOverlay.hint(cue.text(), cue.duration());
			case CuePayload.HORN -> {
				Score.horn(cue.origin(), cue.strength());
				CueOverlay.horn(cue.origin());
			}
			case CuePayload.SCORE -> Score.setMood(cue.duration(), cue.strength());
			case CuePayload.STING -> Score.sting(cue.text(), cue.strength());
			default -> {
			}
		}
	}
}
