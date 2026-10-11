package net.warhorn.cinematic;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.warhorn.net.CinematicPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CinematicService {
	private record Active(int id, long endTick, Set<UUID> viewers, Set<UUID> skipped) {
	}

	private static final Map<Integer, Active> BY_SIEGE = new HashMap<>();
	private static int nextId = 1;

	private CinematicService() {
	}

	public static int play(ServerLevel level, int siegeId, List<ServerPlayer> viewers, CinematicPayload shot) {
		int id = nextId++;
		CinematicPayload sent = new CinematicPayload(id, shot.durationTicks(), shot.trackEntity(), shot.keys(), shot.beats());
		Set<UUID> ids = new HashSet<>();
		for (ServerPlayer player : viewers) {
			ServerPlayNetworking.send(player, sent);
			ids.add(player.getUUID());
		}
		BY_SIEGE.put(siegeId, new Active(id, level.getGameTime() + shot.durationTicks(), ids, new HashSet<>()));
		return id;
	}

	public static boolean playing(ServerLevel level, int siegeId) {
		Active active = BY_SIEGE.get(siegeId);
		if (active == null) return false;
		boolean done = level.getGameTime() >= active.endTick
			|| (!active.viewers.isEmpty() && active.skipped.containsAll(active.viewers));
		if (done) BY_SIEGE.remove(siegeId);
		return !done;
	}

	public static boolean watching(ServerPlayer player) {
		for (Active active : BY_SIEGE.values()) {
			if (active.viewers.contains(player.getUUID()) && !active.skipped.contains(player.getUUID())) return true;
		}
		return false;
	}

	public static void skip(ServerPlayer player, int cinematicId) {
		for (Active active : BY_SIEGE.values()) {
			if (active.id == cinematicId && active.viewers.contains(player.getUUID())) active.skipped.add(player.getUUID());
		}
	}

	public static void clear(int siegeId) {
		BY_SIEGE.remove(siegeId);
	}

	public static void clearAll() {
		BY_SIEGE.clear();
	}
}
