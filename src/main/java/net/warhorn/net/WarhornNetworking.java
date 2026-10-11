package net.warhorn.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.warhorn.cinematic.CinematicService;

public final class WarhornNetworking {
	private WarhornNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(CinematicPayload.TYPE, CinematicPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CuePayload.TYPE, CuePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SiegeStatePayload.TYPE, SiegeStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RuinPayload.TYPE, RuinPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SkipCinematicPayload.TYPE, SkipCinematicPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SkipCinematicPayload.TYPE,
			(payload, context) -> CinematicService.skip(context.player(), payload.id()));
	}
}
