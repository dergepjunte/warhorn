package net.warhorn.siege;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.CinematicLibrary;
import net.warhorn.cinematic.CinematicService;
import net.warhorn.cinematic.Cues;
import net.warhorn.cinematic.SlowMotion;
import net.warhorn.entity.WarlordEntity;
import net.warhorn.net.CuePayload;
import net.warhorn.net.SiegeStatePayload;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class SiegeDirector {
	public static final int RADIUS = 112;
	static final int OMEN_TICKS = 220;
	static final int HOST_GATHERS_AT = 110;
	static final int VICTORY_SLOWMO_TICKS = 14;
	static final int ABANDON_TICKS = 20 * 90;
	static final int MISSING_FOR_GOOD = 20 * 60 * 4;
	static final int GATHER_HORN = 20 * 5;
	static final int GATHER_CRY = GATHER_HORN + 20 * 5;
	static final int GATHER_CHARGE = GATHER_HORN + 20 * 7;
	static final int INTERLUDE_FORM_UP = 20 * 9;
	static final int INTERLUDE_RUN = 20 * 2;

	public static final int SCORE_SILENT = 0, SCORE_OMEN = 1, SCORE_BATTLE = 2, SCORE_INTERLUDE = 3,
		SCORE_WARLORD = 4, SCORE_VICTORY = 5, SCORE_DEFEAT = 6;
	public static final int SCORE_HUSH = 7;

	private SiegeDirector() {
	}

	static void tick(ServerLevel level, Siege s) {
		if (!s.resumed) {
			s.resumed = true;
			if (s.stage == SiegeStage.ACT && s.act > 0) s.arsonsLeft = 1 + s.act;
			if (s.stage == SiegeStage.ACT || s.stage == SiegeStage.INTERLUDE) SiegeSpawner.release(level, s);
		}
		refreshParticipants(level, s);
		countForces(level, s);
		s.stageTicks++;
		if (s.stage.fighting() || s.stage == SiegeStage.INTERLUDE) {
			boolean empty = s.villagersAtStart > 0 && s.villagersAlive == 0 && villageLoaded(level, s);
			s.defeatTicks = empty ? s.defeatTicks + 1 : 0;
			if (s.defeatTicks > 60) {
				enter(level, s, SiegeStage.DEFEAT);
			} else if (s.participants.isEmpty()) {
				if (++s.emptyTicks > ABANDON_TICKS) enter(level, s, SiegeStage.DEFEAT);
			} else {
				s.emptyTicks = 0;
			}
		}
		switch (s.stage) {
			case OMEN -> omen(level, s);
			case INTRO -> intro(level, s);
			case ACT -> act(level, s);
			case INTERLUDE -> interlude(level, s);
			case WARLORD_INTRO -> warlordIntro(level, s);
			case WARLORD -> warlordFight(level, s);
			case VICTORY -> victory(level, s);
			case DEFEAT -> {
				if (s.stageTicks > CinematicLibrary.ENDING_TICKS + 20) enter(level, s, SiegeStage.FINISHED);
			}
			case FINISHED -> {
			}
		}
		if (s.chargeTicks > 0) chargeGoesOn(level, s);
		if (level.getGameTime() % 10 == 0) sync(level, s);
		flushCues(level, s);
	}

	private static void flushCues(ServerLevel level, Siege s) {
		if (s.pendingCues.isEmpty()) return;
		List<ServerPlayer> players = players(level, s);
		for (CuePayload cue : s.pendingCues) Cues.send(players, cue);
		s.pendingCues.clear();
	}

	private static void omen(ServerLevel level, Siege s) {
		Vec3 host = Vec3.atCenterOf(s.staging);
		if (s.stageTicks == 1) {
			score(s, SCORE_OMEN, 0.4f);
			horn(s, host, 0.6f);
		}
		if (s.stageTicks == 40) Hints.offerAll(players(level, s), Hints.OMEN);
		if (s.stageTicks == 80) horn(s, host, 0.8f);
		if (s.stageTicks == HOST_GATHERS_AT) {
			s.wave = 0;
			spawnWave(level, s, true);
			SiegeSpawner.spawnExtras(level, s, 4, 3);
		}
		if (s.stageTicks == 160) horn(s, host, 1.0f);
		if (s.stageTicks >= OMEN_TICKS) {
			Entity captain = s.raiders.stream().map(level::getEntity).filter(e -> e instanceof Mob m
				&& !m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()).findFirst().orElse(null);
			enter(level, s, SiegeStage.INTRO);
			CinematicService.play(level, s.id, players(level, s), CinematicLibrary.intro(level, s, captain));
			lockForCinematic(level, s, CinematicLibrary.INTRO_TICKS);
		}
	}

	private static void intro(ServerLevel level, Siege s) {
		int t = s.stageTicks;
		if (t == 1) score(s, SCORE_OMEN, 0.35f);
		if (t <= 61 && t % 20 == 1) {
			level.playSound(null, s.center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 0.82f);
		}
		if (t == 105) score(s, SCORE_OMEN, 0.72f);
		if (t == 122) sting(s, WarhornSounds.CINE_BRAAM, 1.0f);
		if (t == 200) score(s, SCORE_OMEN, 0.95f);
		if (t == CinematicLibrary.INTRO_CHARGE - 20) breath(s);
		if (t == CinematicLibrary.INTRO_CHARGE) chargeBlow(level, s, 1.0f);
		if (t >= CinematicLibrary.MARCH_FROM) {
			SiegeSpawner.march(level, s, CinematicLibrary.step(t, CinematicLibrary.MARCH_FROM, CinematicLibrary.INTRO_CHARGE));
		}
		if (!CinematicService.playing(level, s.id)) beginAct(level, s);
	}

	private static void breath(Siege s) {
		score(s, SCORE_HUSH, 0f);
		sting(s, WarhornSounds.CINE_RISE, 1.0f);
	}

	private static void chargeBlow(ServerLevel level, Siege s, float strength) {
		Vec3 host = hostCentre(level, s);
		sting(s, WarhornSounds.CINE_HIT, strength);
		horn(s, host, 1.0f);
		warCry(level, s);
		level.playSound(null, host.x, host.y, host.z, WarhornSounds.STAMPEDE, SoundSource.HOSTILE, 4.0f, 1.0f);
		for (ServerPlayer player : players(level, s)) {
			ServerPlayNetworking.send(player, CuePayload.of(CuePayload.SHAKE, 0.45f * strength, 20, host));
			ServerPlayNetworking.send(player, CuePayload.of(CuePayload.FOV_PUNCH, 0.5f * strength, 8, host));
		}
		score(s, s.stage == SiegeStage.WARLORD_INTRO ? SCORE_WARLORD : SCORE_BATTLE, 1f);
	}

	private static void chargeGoesOn(ServerLevel level, Siege s) {
		s.chargeTicks--;
		for (UUID uuid : s.raiders) {
			if (level.getEntity(uuid) instanceof Mob mob && mob.getDeltaMovement().horizontalDistanceSqr() > 0.012
				&& mob.getRandom().nextInt(3) == 0) {
				SiegeSpawner.kickUpEarth(level, mob);
			}
		}
		if (s.chargeTicks % 10 != 0) return;
		Vec3 host = hostCentre(level, s);
		float fade = s.chargeTicks / (float) SiegeSpawner.CHARGE_TICKS;
		for (ServerPlayer player : players(level, s)) {
			double d = player.position().distanceTo(host);
			if (d < 48) ServerPlayNetworking.send(player, CuePayload.of(CuePayload.SHAKE, 0.16f * fade * (float) (1 - d / 48), 12, host));
		}
	}

	private static Vec3 hostCentre(ServerLevel level, Siege s) {
		Vec3 sum = Vec3.ZERO;
		int n = 0;
		for (UUID uuid : s.raiders) {
			if (level.getEntity(uuid) instanceof LivingEntity e && e.isAlive()) {
				sum = sum.add(e.position());
				n++;
			}
		}
		return n == 0 ? Vec3.atCenterOf(s.staging) : sum.scale(1.0 / n);
	}

	private static void sting(Siege s, SoundEvent sound, float volume) {
		String id = BuiltInRegistries.SOUND_EVENT.getKey(sound).toString();
		s.pendingCues.add(new CuePayload(CuePayload.STING, volume, 0, Vec3.atCenterOf(s.center), id, ""));
	}

	private static void beginAct(ServerLevel level, Siege s) {
		SiegeSpawner.dismissExtras(level, s);
		enter(level, s, SiegeStage.ACT);
		SiegeSpawner.release(level, s);
		SiegeSpawner.charge(level, s);
		s.waveTicks = 0;
		s.actEnemiesTotal = Math.max(s.raidersAlive, 1);
		s.arsonsLeft = s.act == 0 ? 0 : 2 + 2 * s.act;
		score(s, SCORE_BATTLE, 0.55f + 0.2f * s.act);
		Cues.card(level, Vec3.atCenterOf(s.center), "warhorn.act." + (s.act + 1), "warhorn.act." + ActPlan.ACTS.get(s.act).key());
		if (s.act == 0) Hints.offerAll(players(level, s), Hints.FIRST_ACT);
	}

	private static void act(ServerLevel level, Siege s) {
		if (s.gatherTicks >= 0) {
			gather(level, s);
			return;
		}
		s.waveTicks++;
		ActPlan.Act act = ActPlan.ACTS.get(s.act);
		boolean moreWaves = s.wave + 1 < act.waves().size();
		if (s.raidersAlive > 0 && s.raidersAlive <= 3) revealStragglers(level, s);
		boolean cleared = s.raidersAlive == 0 || s.raidersAlive <= 2 && s.waveTicks > ActPlan.WAVE_TIMEOUT_TICKS;
		if (moreWaves && cleared) {
			s.wave++;
			s.gatherTicks = 0;
		} else if (!moreWaves && cleared) {
			Cues.card(level, Vec3.atCenterOf(s.center), "warhorn.act.cleared", "warhorn.act." + ActPlan.ACTS.get(s.act).key());
			enter(level, s, s.act + 1 < ActPlan.ACTS.size() ? SiegeStage.INTERLUDE : SiegeStage.WARLORD_INTRO);
		}
	}

	private static void gather(ServerLevel level, Siege s) {
		if (s.gatherTicks > GATHER_HORN && UnitIntros.next(level, s)) return;
		int t = s.gatherTicks++;
		if (t == 0) {
			s.staging = SiegeSpawner.findStaging(level, s.center, level.getRandom());
			score(s, SCORE_BATTLE, 0.2f);
		}
		if (t == GATHER_HORN) {
			UnitIntros.offer(level, s, spawnWave(level, s, true));
			horn(s, Vec3.atCenterOf(s.staging), 0.9f);
			Cues.card(level, Vec3.atCenterOf(s.center), "warhorn.wave.incoming", "warhorn.wave." + (s.wave + 1));
		}
		if (t > GATHER_HORN) SiegeSpawner.march(level, s, CinematicLibrary.step(t, GATHER_HORN, GATHER_CRY));
		if (t == GATHER_CRY - 20) breath(s);
		if (t == GATHER_CRY) chargeBlow(level, s, 0.85f);
		if (t >= GATHER_CHARGE) {
			SiegeSpawner.release(level, s);
			SiegeSpawner.charge(level, s);
			s.actEnemiesTotal = Math.max(s.raidersAlive, 1);
			s.waveTicks = 0;
			s.gatherTicks = -1;
		}
	}

	private static void warCry(ServerLevel level, Siege s) {
		for (UUID uuid : s.raiders) {
			if (level.getEntity(uuid) instanceof net.minecraft.world.entity.raid.Raider raider) {
				level.playSound(null, raider.blockPosition(), raider.getCelebrateSound(), SoundSource.HOSTILE,
					2.2f, 0.75f + raider.getRandom().nextFloat() * 0.3f);
			}
		}
	}

	private static void revealStragglers(ServerLevel level, Siege s) {
		if (s.stageTicks % 20 != 0) return;
		for (UUID uuid : s.raiders) {
			if (level.getEntity(uuid) instanceof LivingEntity soldier) {
				soldier.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
			}
		}
		Hints.offerAll(players(level, s), Hints.LAST_ENEMIES);
	}

	private static void interlude(ServerLevel level, Siege s) {
		if (s.stageTicks > ActPlan.INTERLUDE_TICKS - INTERLUDE_FORM_UP && UnitIntros.next(level, s)) {
			s.stageTicks--;
			return;
		}
		if (s.stageTicks == 1) score(s, SCORE_INTERLUDE, 0.3f);
		if (s.stageTicks == 60) Hints.offerAll(players(level, s), Hints.INTERLUDE);
		int left = ActPlan.INTERLUDE_TICKS - s.stageTicks;
		if (left == 20 * 20) {
			s.staging = SiegeSpawner.findStaging(level, s.center, level.getRandom());
			horn(s, Vec3.atCenterOf(s.staging), 0.8f);
			score(s, SCORE_INTERLUDE, 0.7f);
		}
		if (left == INTERLUDE_FORM_UP) {
			s.act++;
			s.wave = 0;
			UnitIntros.offer(level, s, spawnWave(level, s, true));
		}
		if (left < INTERLUDE_FORM_UP && left > 0) {
			int formedUp = ActPlan.INTERLUDE_TICKS - INTERLUDE_FORM_UP;
			SiegeSpawner.march(level, s, CinematicLibrary.step(s.stageTicks, formedUp, ActPlan.INTERLUDE_TICKS - INTERLUDE_RUN));
		}
		if (left == INTERLUDE_RUN + 20) breath(s);
		if (left == INTERLUDE_RUN) chargeBlow(level, s, 0.9f);
		if (left <= 0) beginAct(level, s);
	}

	private static void warlordIntro(ServerLevel level, Siege s) {
		if (s.stageTicks == 1) {
			s.staging = SiegeSpawner.findStaging(level, s.center, level.getRandom());
			score(s, SCORE_OMEN, 0.3f);
			WarlordEntity warlord = WarhornEntities.WARLORD.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (warlord == null) {
				enter(level, s, SiegeStage.VICTORY);
				return;
			}
			Vec3 at = Vec3.atBottomCenterOf(s.staging);
			Vec3 toVillage = Vec3.atCenterOf(s.center).subtract(at).multiply(1, 0, 1).normalize();
			float yaw = (float) Math.toDegrees(Math.atan2(toVillage.z, toVillage.x)) - 90f;
			SiegeSpawner.prepare(level, s, warlord, at, yaw, true);
			warlord.equip();
			warlord.bindToSiege(s.id, Math.max(1, s.participants.size()));
			level.addFreshEntity(warlord);
			s.warlord = warlord.getUUID();
			s.raiders.add(warlord.getUUID());
			SiegeSpawner.spawnWave(level, s, ActPlan.WARLORD_GUARD, Math.max(1, s.participants.size()), true, 2);
			SiegeSpawner.spawnExtras(level, s, 3, 3);
			CinematicService.play(level, s.id, players(level, s), CinematicLibrary.warlord(level, s, warlord));
			lockForCinematic(level, s, CinematicLibrary.WARLORD_TICKS);
		}
		int t = s.stageTicks;
		if (t == 2) horn(s, Vec3.atCenterOf(s.staging), 1.0f);
		if (t == 96) {
			sting(s, WarhornSounds.CINE_BRAAM, 1.0f);
			score(s, SCORE_OMEN, 0.6f);
		}
		if (t == CinematicLibrary.WARLORD_ROAR_TICK - 20) breath(s);
		if (t == CinematicLibrary.WARLORD_ROAR_TICK && s.warlord != null
			&& level.getEntity(s.warlord) instanceof WarlordEntity warlord) {
			level.playSound(null, warlord.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.42f);
			Cues.impact(level, warlord.position(), 1.0f, RADIUS);
			sting(s, WarhornSounds.CINE_HIT, 1.0f);
			score(s, SCORE_WARLORD, 1f);
		}
		if (t == CinematicLibrary.WARLORD_CHARGE) chargeBlow(level, s, 0.8f);
		if (t > CinematicLibrary.WARLORD_CHARGE) {
			SiegeSpawner.march(level, s, CinematicLibrary.step(t, CinematicLibrary.WARLORD_CHARGE, CinematicLibrary.WARLORD_CHARGE));
		}
		if (t > 3 && !CinematicService.playing(level, s.id)) {
			SiegeSpawner.dismissExtras(level, s);
			enter(level, s, SiegeStage.WARLORD);
			SiegeSpawner.release(level, s);
			SiegeSpawner.charge(level, s);
			s.actEnemiesTotal = Math.max(1, s.raidersAlive);
			score(s, SCORE_WARLORD, 1f);
		}
	}

	private static void warlordFight(ServerLevel level, Siege s) {
		Entity warlord = s.warlord == null ? null : level.getEntity(s.warlord);
		boolean slain = s.warlordSlain || warlord instanceof WarlordEntity w && w.isDeadOrDying();
		if (s.warlord == null || slain) enter(level, s, SiegeStage.VICTORY);
	}

	private static void victory(ServerLevel level, Siege s) {
		if (s.stageTicks == 1) {
			score(s, SCORE_SILENT, 0f);
			if (level.getServer().getPlayerList().getPlayers().stream().allMatch(p -> s.participants.contains(p.getUUID()))) {
				SlowMotion.start(level.getServer(), 5f, 1600);
			}
			Vec3 fall = s.lastWarlordPos != null ? s.lastWarlordPos : Vec3.atCenterOf(s.center);
			Cues.impact(level, fall, 1.0f, RADIUS);
			Cues.send(players(level, s), CuePayload.of(CuePayload.FLASH, 0.8f, 16, fall));
		}
		if (s.stageTicks == VICTORY_SLOWMO_TICKS) {
			Vec3 fall = s.lastWarlordPos != null ? s.lastWarlordPos : Vec3.atCenterOf(s.center);
			score(s, SCORE_VICTORY, 1f);
			CinematicService.play(level, s.id, players(level, s), CinematicLibrary.victory(level, s, fall));
			lockForCinematic(level, s, CinematicLibrary.ENDING_TICKS);
			reward(level, s);
		}
		if (s.stageTicks > VICTORY_SLOWMO_TICKS + 5 && !CinematicService.playing(level, s.id)) {
			enter(level, s, SiegeStage.FINISHED);
		}
	}

	public static void warlordSlain(ServerLevel level, WarlordEntity warlord) {
		Siege s = SiegeManager.byId(level, warlord.siegeId());
		if (s != null) s.warlordSlain = true;
	}

	public static void warlordLastStand(ServerLevel level, WarlordEntity warlord) {
		Siege s = SiegeManager.byId(level, warlord.siegeId());
		if (s == null) return;
		List<ServerPlayer> viewers = players(level, s);
		CinematicService.play(level, s.id, viewers, CinematicLibrary.lastStand(level, s, warlord));
		lockForCinematic(level, s, CinematicLibrary.LAST_STAND_TICKS);
		Hints.offerAll(viewers, Hints.LAST_STAND);
	}

	public static boolean slowMotionFair(ServerLevel level, int siegeId) {
		Siege s = SiegeManager.byId(level, siegeId);
		return s != null && level.getServer().getPlayerList().getPlayers().stream().allMatch(p -> s.participants.contains(p.getUUID()));
	}

	static void enter(ServerLevel level, Siege s, SiegeStage stage) {
		s.stage = stage;
		s.stageTicks = 0;
		if (stage == SiegeStage.DEFEAT) {
			score(s, SCORE_DEFEAT, 1f);
			CinematicService.play(level, s.id, players(level, s), CinematicLibrary.defeat(level, s));
			lockForCinematic(level, s, CinematicLibrary.ENDING_TICKS);
		}
		sync(level, s);
	}

	private static List<Mob> spawnWave(ServerLevel level, Siege s, boolean frozen) {
		ActPlan.Wave wave = ActPlan.ACTS.get(s.act).waves().get(s.wave);
		List<Mob> spawned = SiegeSpawner.spawnWave(level, s, wave, Math.max(1, s.participants.size()), frozen, 1 + s.act);
		countForces(level, s);
		return spawned;
	}

	private static void reward(ServerLevel level, Siege s) {
		for (ServerPlayer player : players(level, s)) {
			player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 40, Math.max(0, s.omenLevel - 1), false, false, true));
			player.giveExperiencePoints(120 + 40 * s.omenLevel);
		}
	}

	private static void lockForCinematic(ServerLevel level, Siege s, int ticks) {
		for (ServerPlayer player : players(level, s)) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks + 20, 4, false, false, false));
		}
	}

	private static void horn(Siege s, Vec3 from, float strength) {
		s.pendingCues.add(new CuePayload(CuePayload.HORN, strength, 0, from, "", ""));
	}

	private static void score(Siege s, int mood, float intensity) {
		s.mood = mood;
		s.moodIntensity = intensity;
		s.pendingCues.add(new CuePayload(CuePayload.SCORE, intensity, mood, Vec3.atCenterOf(s.center), "", ""));
	}

	static List<ServerPlayer> players(ServerLevel level, Siege s) {
		return s.participants.stream().map(level::getPlayerByUUID).filter(p -> p instanceof ServerPlayer)
			.map(p -> (ServerPlayer) p).toList();
	}

	private static void refreshParticipants(ServerLevel level, Siege s) {
		List<UUID> before = List.copyOf(s.participants);
		s.participants.clear();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && s.contains(player.blockPosition(), RADIUS)) {
				s.participants.add(player.getUUID());
				s.heroes.add(player.getUUID());
			}
		}
		for (UUID uuid : before) {
			if (!s.participants.contains(uuid) && level.getPlayerByUUID(uuid) instanceof ServerPlayer gone) {
				ServerPlayNetworking.send(gone, SiegeStatePayload.INACTIVE);
				ServerPlayNetworking.send(gone, new CuePayload(CuePayload.SCORE, 0, SCORE_SILENT, Vec3.ZERO, "", ""));
			}
		}
		for (UUID uuid : s.participants) {
			if (!before.contains(uuid) && level.getPlayerByUUID(uuid) instanceof ServerPlayer joined) {
				ServerPlayNetworking.send(joined, new CuePayload(CuePayload.SCORE, s.moodIntensity, s.mood, Vec3.atCenterOf(s.center), "", ""));
			}
		}
	}

	private static boolean villageLoaded(ServerLevel level, Siege s) {
		net.minecraft.world.level.ChunkPos c = net.minecraft.world.level.ChunkPos.containing(s.center);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(c.x() + dx, c.z() + dz))) return false;
			}
		}
		return true;
	}

	private static void countForces(ServerLevel level, Siege s) {
		int alive = 0;
		for (Iterator<UUID> it = s.raiders.iterator(); it.hasNext(); ) {
			UUID uuid = it.next();
			Entity e = level.getEntity(uuid);
			if (e instanceof LivingEntity living) {
				s.missingTicks.remove(uuid);
				if (!living.isAlive()) {
					it.remove();
					continue;
				}
				alive++;
				if (e instanceof WarlordEntity) s.lastWarlordPos = e.position();
			} else if (s.missingTicks.merge(uuid, 1, Integer::sum) > MISSING_FOR_GOOD) {
				s.missingTicks.remove(uuid);
				it.remove();
			} else {
				alive++;
			}
		}
		s.raidersAlive = alive;
		if ((level.getGameTime() % 20 == 0 || s.villagersAtStart == 0) && villageLoaded(level, s)) {
			BlockPos c = s.center;
			s.villagersAlive = level.getEntitiesOfClass(AbstractVillager.class,
				new AABB(c).inflate(80, 40, 80), LivingEntity::isAlive).size();
			if (s.villagersAtStart == 0) s.villagersAtStart = s.villagersAlive;
		}
	}

	static float dread(Siege s) {
		return switch (s.stage) {
			case OMEN -> Math.min(1f, s.stageTicks / (float) OMEN_TICKS);
			case INTRO, ACT, WARLORD_INTRO, WARLORD -> 1f;
			case INTERLUDE -> 0.7f;
			case VICTORY -> Math.max(0f, 1f - s.stageTicks / 120f);
			case DEFEAT -> 1f;
			case FINISHED -> 0f;
		};
	}

	static void sync(ServerLevel level, Siege s) {
		int timer = 0;
		int timerMax = 0;
		if (s.stage == SiegeStage.INTERLUDE) {
			timer = Math.max(0, ActPlan.INTERLUDE_TICKS - s.stageTicks);
			timerMax = ActPlan.INTERLUDE_TICKS;
		}
		int warlordId = -1;
		if (s.warlord != null && level.getEntity(s.warlord) instanceof WarlordEntity w) warlordId = w.getId();
		SiegeStatePayload state = s.stage == SiegeStage.FINISHED ? SiegeStatePayload.INACTIVE
			: new SiegeStatePayload(true, s.center, s.staging, s.stage.ordinal(), s.act, s.wave, s.raidersAlive,
				s.actEnemiesTotal, timer, timerMax, s.villagersAlive, s.villagersAtStart, dread(s), warlordId);
		for (ServerPlayer player : players(level, s)) ServerPlayNetworking.send(player, state);
	}
}
