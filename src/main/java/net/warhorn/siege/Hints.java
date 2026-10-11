package net.warhorn.siege;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.warhorn.Warhorn;
import net.warhorn.net.CuePayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Hints extends SavedData {
	public static final String OMEN = "omen";
	public static final String FIRST_ACT = "first_act";
	public static final String INTERLUDE = "interlude";
	public static final String LAST_ENEMIES = "last_enemies";
	public static final String SLAM = "slam";
	public static final String CHARGE = "charge";
	public static final String STUNNED = "stunned";
	public static final String ENRAGED = "enraged";
	public static final String RUINS = "rebuild";
	public static final String ARSON = "arson";
	public static final String AXE = "axe";
	public static final String LEAP = "leap";
	public static final String LEAP_RECOVER = "leap_recover";
	public static final String BANNER = "banner";
	public static final String LAST_STAND = "last_stand";
	public static final String KEG = "keg";
	public static final String KIDNAP = "kidnap";
	public static final String CATAPULT = "catapult";
	public static final String CATAPULT_SMASH = "catapult_smash";

	private static final Codec<Map<UUID, Set<String>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC,
		Codec.STRING.listOf().xmap(l -> (Set<String>) new HashSet<>(l), List::copyOf));
	public static final Codec<Hints> CODEC = MAP_CODEC.xmap(Hints::new, h -> h.seen);
	public static final SavedDataType<Hints> TYPE = new SavedDataType<>(
		Warhorn.id("hints"), Hints::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);

	private final Map<UUID, Set<String>> seen;

	public Hints() {
		this(Map.of());
	}

	private Hints(Map<UUID, Set<String>> seen) {
		this.seen = new HashMap<>();
		seen.forEach((k, v) -> this.seen.put(k, new HashSet<>(v)));
	}

	private static Hints get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void offer(ServerPlayer player, String hint) {
		Hints hints = get(player.level());
		if (!hints.seen.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(hint)) return;
		hints.setDirty();
		ServerPlayNetworking.send(player, new CuePayload(CuePayload.HINT, 1, 110, Vec3.ZERO, "warhorn.hint." + hint, ""));
	}

	public static boolean firstTime(ServerPlayer player, String hint) {
		Hints hints = get(player.level());
		if (!hints.seen.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(hint)) return false;
		hints.setDirty();
		return true;
	}

	public static void offerAll(Iterable<ServerPlayer> players, String hint) {
		for (ServerPlayer player : players) offer(player, hint);
	}

	public static void offerNear(ServerLevel level, Vec3 at, double radius, String hint) {
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(at) <= radius && !player.isSpectator()) offer(player, hint);
		}
	}
}
