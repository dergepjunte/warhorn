package net.warhorn.cinematic;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.warhorn.net.CuePayload;

public final class Cues {
	private Cues() {
	}

	public static void impact(ServerLevel level, Vec3 at, float strength, int radius) {
		for (ServerPlayer player : level.players()) {
			double d = player.position().distanceTo(at);
			if (d > radius) continue;
			float s = strength * (float) (1.0 - d / radius);
			ServerPlayNetworking.send(player, CuePayload.of(CuePayload.SHAKE, s, 14, at));
			if (s > 0.45f) ServerPlayNetworking.send(player, CuePayload.of(CuePayload.FOV_PUNCH, s, 8, at));
			if (s > 0.75f) ServerPlayNetworking.send(player, CuePayload.of(CuePayload.HITSTOP, s, 3, at));
		}
	}

	public static void card(ServerLevel level, Vec3 at, String title, String subtitle) {
		CuePayload cue = new CuePayload(CuePayload.ACT_CARD, 1, 90, at, title, subtitle);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(at) < 128) ServerPlayNetworking.send(player, cue);
		}
	}

	public static void toast(ServerLevel level, Vec3 at, String text) {
		CuePayload cue = new CuePayload(CuePayload.TOAST, 1, 50, at, text, "");
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(at) < 48) ServerPlayNetworking.send(player, cue);
		}
	}

	public static void send(Iterable<ServerPlayer> players, CuePayload cue) {
		for (ServerPlayer player : players) ServerPlayNetworking.send(player, cue);
	}
}
