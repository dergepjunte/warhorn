package net.warhorn.siege;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.warhorn.cinematic.CinematicLibrary;
import net.warhorn.cinematic.CinematicService;
import net.warhorn.entity.BatteringRamEntity;
import net.warhorn.entity.SapperEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UnitIntros {
	private UnitIntros() {
	}

	public static String key(Mob mob) {
		if (mob instanceof SapperEntity) return "sapper";
		if (mob instanceof net.warhorn.entity.KidnapperEntity) return "kidnapper";
		if (mob.entityTags().contains(BatteringRamEntity.CREW_TAG)) return "ram";
		if (mob.entityTags().contains(net.warhorn.entity.CatapultEntity.CREW_TAG)) return "catapult";
		return null;
	}

	static net.minecraft.world.entity.Entity subject(Mob mob, String key) {
		Class<? extends net.minecraft.world.entity.Entity> engine = switch (key) {
			case "ram" -> BatteringRamEntity.class;
			case "catapult" -> net.warhorn.entity.CatapultEntity.class;
			default -> null;
		};
		if (engine == null) return mob;
		return mob.level().getEntitiesOfClass(engine, mob.getBoundingBox().inflate(4)).stream()
			.findFirst().map(r -> (net.minecraft.world.entity.Entity) r).orElse(mob);
	}

	private static final Map<Integer, Deque<Mob>> PENDING = new HashMap<>();

	static boolean offer(ServerLevel level, Siege s, List<Mob> spawned) {
		Deque<Mob> queue = new ArrayDeque<>();
		Set<String> kinds = new HashSet<>();
		for (Mob mob : spawned) {
			String key = key(mob);
			if (key != null && kinds.add(key)) queue.add(mob);
		}
		PENDING.put(s.id, queue);
		return next(level, s);
	}

	static boolean next(ServerLevel level, Siege s) {
		if (CinematicService.playing(level, s.id)) return true;
		Deque<Mob> queue = PENDING.get(s.id);
		while (queue != null && !queue.isEmpty()) {
			Mob mob = queue.poll();
			String key = key(mob);
			if (key == null || !mob.isAlive()) continue;
			List<ServerPlayer> viewers = SiegeDirector.players(level, s).stream()
				.filter(p -> Hints.firstTime(p, "unit_" + key)).toList();
			if (viewers.isEmpty()) continue;
			show(level, s.id, s.center, viewers, mob, key);
			return true;
		}
		PENDING.remove(s.id);
		return false;
	}

	public static void show(ServerLevel level, int siegeId, net.minecraft.core.BlockPos village, List<ServerPlayer> viewers,
		Mob unit, String key) {
		CinematicService.play(level, siegeId, viewers, CinematicLibrary.unitIntro(level, village, subject(unit, key), key));
		for (ServerPlayer player : viewers) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, CinematicLibrary.UNIT_INTRO_TICKS + 20, 4, false, false, false));
		}
	}
}
