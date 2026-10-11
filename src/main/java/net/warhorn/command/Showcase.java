package net.warhorn.command;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.clock.WorldClock;
import net.warhorn.Warhorn;
import net.warhorn.cinematic.CinematicLibrary;
import net.warhorn.cinematic.CinematicService;
import net.warhorn.cinematic.Cues;
import net.warhorn.cinematic.ShotBuilder;
import net.warhorn.entity.BatteringRamEntity;
import net.warhorn.entity.CatapultEntity;
import net.warhorn.entity.FirePotEntity;
import net.warhorn.entity.KidnapperEntity;
import net.warhorn.entity.PowderKegEntity;
import net.warhorn.entity.SapperEntity;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Beat;
import net.warhorn.net.CuePayload;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.SiegeDirector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Showcase {
	private static final int RADIUS = 58;
	private static final int STAGE_Y = 250;
	private static final int HEADROOM = 30;
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final long LIGHT = 4500;

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static int nextChannel = -2000;

	private Showcase() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Showcase::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Run run = RUNS.remove(handler.player.getUUID());
			if (run != null) run.finish(handler.player, false);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RUNS.forEach((uuid, run) -> run.finish(server.getPlayerList().getPlayer(uuid), false));
			RUNS.clear();
		});
	}

	public static boolean running(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	public static boolean start(ServerPlayer player) {
		if (running(player)) return false;
		ServerLevel level = player.level();
		int y = Math.min(STAGE_Y, level.getMaxY() - HEADROOM);
		BlockPos centre = new BlockPos(player.getBlockX(), y, player.getBlockZ());
		if (!clear(level, centre)) {
			player.sendSystemMessage(Component.translatable("warhorn.showcase.blocked"));
			return false;
		}
		Run run = new Run(player, level, centre, nextChannel--);
		RUNS.put(player.getUUID(), run);
		run.build(player);
		return true;
	}

	private static boolean clear(ServerLevel level, BlockPos centre) {
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS, -1, -RADIUS), centre.offset(RADIUS, HEADROOM - 2, RADIUS))) {
			if (!level.getBlockState(p).isAir()) return false;
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (var it = RUNS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Run run = entry.getValue();
			if (!run.step(player)) {
				it.remove();
				run.finish(player, true);
			}
		}
	}

	private enum Phase {
		SETTLE, OPENING,
		SAPPER_INTRO, SAPPER_RUN, KEG,
		RAM_INTRO, RAM_MARCH, RAM_STRIKE,
		KIDNAP_INTRO, KIDNAP_HUNT, KIDNAP_CARRY, FREED,
		CATAPULT_INTRO, CATAPULT_WIND, POT, IMPACT, WRECK,
		FINALE, DONE
	}

	private static final class Run {
		private final ServerLevel level;
		private final BlockPos centre;
		private final Vec3 c;
		private final int channel;
		private final double x, y, z;
		private final float yaw, pitch;
		private final GameType mode;
		private final boolean griefing;
		private final Holder<WorldClock> clock;
		private long clockTicks;
		private boolean clockPaused;
		private final int clearTime, rainTime, thunderTime;
		private final boolean raining, thundering;
		private Phase phase = Phase.SETTLE;
		private int t;
		private Entity subject;
		private Vec3 mark;
		private boolean flag;

		Run(ServerPlayer player, ServerLevel level, BlockPos centre, int channel) {
			this.level = level;
			this.centre = centre;
			this.c = Vec3.atBottomCenterOf(centre);
			this.channel = channel;
			x = player.getX();
			y = player.getY();
			z = player.getZ();
			yaw = player.getYRot();
			pitch = player.getXRot();
			mode = player.gameMode();
			griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
			clock = level.dimensionTypeRegistration().value().defaultClock().orElse(null);
			var weather = level.getServer().getWeatherData();
			clearTime = weather.getClearWeatherTime();
			rainTime = weather.getRainTime();
			thunderTime = weather.getThunderTime();
			raining = weather.isRaining();
			thundering = weather.isThundering();
		}

		void build(ServerPlayer player) {
			level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
			if (clock != null) {
				var clocks = level.getServer().clockManager();
				var instance = clocks.getInstance(clock);
				clockTicks = instance.totalTicks();
				clockPaused = instance.isPaused();
				clocks.setTotalTicks(clock, clockTicks - Math.floorMod(clockTicks, 24000L) + LIGHT);
				clocks.setPaused(clock, true);
			}
			level.getServer().setWeatherParameters(24000, 0, false, false);
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					level.setBlock(centre.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			player.setGameMode(GameType.SPECTATOR);
			player.teleportTo(level, c.x, c.y + 6, c.z + 24, Set.of(), 180f, 15f, true);
			String[] houses = {"plains_small_house_1", "plains_medium_house_1", "plains_small_house_5", "plains_big_house_1"};
			int[][] at = {{6, 4}, {-14, 3}, {5, -15}, {-14, -16}};
			for (int i = 0; i < houses.length; i++) {
				BlockPos p = centre.offset(at[i][0], 0, at[i][1]);
				run(player, "place template minecraft:village/plains/houses/" + houses[i] + " " + p.getX() + " " + p.getY() + " " + p.getZ());
			}
			settleJigsaws();
			for (int i = -RADIUS + 3; i <= RADIUS - 3; i += 6) {
				BlockPos tree = centre.offset(i + (i * 7 % 3), 0, -RADIUS + 4 + (i * 5 % 4));
				run(player, "place feature minecraft:spruce " + tree.getX() + " " + tree.getY() + " " + tree.getZ());
			}
			for (int i = 0; i < 6; i++) {
				run(player, "summon villager " + (centre.getX() + (i % 3) * 4 - 4) + " " + centre.getY() + " " + (centre.getZ() + (i / 3) * 4 - 2));
			}
			player.sendSystemMessage(Component.translatable("warhorn.showcase.start"));
		}

		private int settleJigsaws() {
			int settled = 0;
			var blocks = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
			for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS, -1, -RADIUS), centre.offset(RADIUS, 16, RADIUS))) {
				if (!level.getBlockState(p).is(Blocks.JIGSAW)) continue;
				var state = Blocks.AIR.defaultBlockState();
				if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.JigsawBlockEntity jigsaw) {
					try {
						state = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(blocks, jigsaw.getFinalState(), false).blockState();
					} catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
					}
				}
				level.setBlock(p.immutable(), state, Block.UPDATE_CLIENTS);
				settled++;
			}
			return settled;
		}

		boolean step(ServerPlayer player) {
			t++;
			List<ServerPlayer> me = List.of(player);
			switch (phase) {
				case SETTLE -> {
					if (t == 10) {
						int late = settleJigsaws();
						if (late > 0) Warhorn.LOG.info("Showcase: {} late jigsaw blocks settled", late);
					}
					if (t >= 30) {
						score(me, SiegeDirector.SCORE_OMEN, 0.4f);
						play(me, opening());
						next(Phase.OPENING);
					}
				}
				case OPENING -> {
					if (t == 120) score(me, SiegeDirector.SCORE_OMEN, 0.7f);
					if (shotOver()) {
						subject = spawnSapper();
						introduce(me, "sapper");
						next(Phase.SAPPER_INTRO);
					}
				}
				case SAPPER_INTRO -> {
					if (shotOver() || gone()) {
						release();
						action(me);
						play(me, chase(subject, 600, 1.6f));
						next(Phase.SAPPER_RUN);
					}
				}
				case SAPPER_RUN -> {
					PowderKegEntity keg = level.getEntitiesOfClass(PowderKegEntity.class, stageBox(), k -> !k.loose()).stream()
						.findFirst().orElse(null);
					if (keg != null) {
						subject = keg;
						score(me, SiegeDirector.SCORE_HUSH, 0f);
						play(me, fuse(keg));
						next(Phase.KEG);
					} else if (t > 600 || gone()) {
						startRam(me);
					}
				}
				case KEG -> {
					if (t == PowderKegEntity.FUSE) {
						sting(me, WarhornSounds.CINE_HIT, 1f);
						score(me, SiegeDirector.SCORE_BATTLE, 0.7f);
					}
					if (shotOver()) {
						discardAll(SapperEntity.class);
						startRam(me);
					}
				}
				case RAM_INTRO -> {
					if (shotOver() || gone()) {
						release();
						action(me);
						play(me, escort(subject, 500));
						next(Phase.RAM_MARCH);
					}
				}
				case RAM_MARCH -> {
					if (subject instanceof BatteringRamEntity ram && ram.swinging()) {
						play(me, strike(ram));
						next(Phase.RAM_STRIKE);
					} else if (t > 500 || gone()) {
						startKidnapper(me);
					}
				}
				case RAM_STRIKE -> {
					if (!flag && subject instanceof BatteringRamEntity ram && ram.breaches() > 0) {
						flag = true;
						sting(me, WarhornSounds.CINE_HIT, 0.9f);
					}
					if (shotOver()) startKidnapper(me);
				}
				case KIDNAP_INTRO -> {
					if (shotOver() || gone()) {
						release();
						action(me);
						play(me, overwatch(subject, 500));
						next(Phase.KIDNAP_HUNT);
					}
				}
				case KIDNAP_HUNT -> {
					if (subject instanceof KidnapperEntity k && k.state() == KidnapperEntity.State.CARRY) {
						k.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 2, false, false));
						score(me, SiegeDirector.SCORE_BATTLE, 0.9f);
						play(me, flight(k));
						next(Phase.KIDNAP_CARRY);
					} else if (t > 500 || gone()) {
						startCatapult(me);
					}
				}
				case KIDNAP_CARRY -> {
					if (t >= 120 || gone()) {
						mark = subject.position();
						if (subject instanceof Mob m && m.isAlive()) m.kill(level);
						sting(me, WarhornSounds.CINE_HIT, 0.8f);
						play(me, freed(mark));
						next(Phase.FREED);
					}
				}
				case FREED -> {
					if (shotOver()) startCatapult(me);
				}
				case CATAPULT_INTRO -> {
					if (shotOver() || gone()) {
						action(me);
						play(me, winding((CatapultEntity) subject));
						next(Phase.CATAPULT_WIND);
					}
				}
				case CATAPULT_WIND -> {
					CatapultEntity cat = (CatapultEntity) subject;
					if (!flag && cat.phase() == CatapultEntity.Phase.LIGHT) {
						flag = true;
						sting(me, WarhornSounds.CINE_RISE, 0.9f);
					}
					FirePotEntity pot = level.getEntitiesOfClass(FirePotEntity.class, cat.getBoundingBox().inflate(8)).stream()
						.findFirst().orElse(null);
					if (pot != null) {
						subject = pot;
						mark = pot.position();
						play(me, pot(pot));
						next(Phase.POT);
					} else if (t > 420 || gone()) {
						finale(me);
					}
				}
				case POT -> {
					if (subject.isRemoved() || t > 140) {
						sting(me, WarhornSounds.CINE_HIT, 1f);
						play(me, impact(mark));
						next(Phase.IMPACT);
					} else {
						mark = subject.position();
					}
				}
				case IMPACT -> {
					if (shotOver()) {
						CatapultEntity cat = level.getEntitiesOfClass(CatapultEntity.class, stageBox()).stream().findFirst().orElse(null);
						if (cat == null) {
							finale(me);
						} else {
							subject = cat;
							play(me, wreck(cat));
							next(Phase.WRECK);
						}
					}
				}
				case WRECK -> {
					if ((t == 40 || t == 70 || t == 100) && !subject.isRemoved()) {
						subject.hurtServer(level, level.damageSources().playerAttack(player), 9f);
					}
					if (t == 101) sting(me, WarhornSounds.CINE_HIT, 1f);
					if (shotOver()) finale(me);
				}
				case FINALE -> {
					if (shotOver()) {
						score(me, SiegeDirector.SCORE_SILENT, 0f);
						next(Phase.DONE);
					}
				}
				case DONE -> {
					return t < 20;
				}
			}
			return true;
		}

		private void next(Phase p) {
			Warhorn.LOG.debug("Showcase {} -> {} after {} ticks (subject {})", phase, p, t,
				subject == null ? "none" : subject.getType().toShortString() + (subject.isRemoved() ? " removed" : ""));
			phase = p;
			t = 0;
			flag = false;
		}

		private boolean shotOver() {
			return t > 2 && !CinematicService.playing(level, channel);
		}

		private boolean gone() {
			return subject == null || subject.isRemoved();
		}

		private void release() {
			if (subject instanceof Mob m) m.setNoAi(false);
			if (subject instanceof BatteringRamEntity ram) ram.release();
		}

		private void action(List<ServerPlayer> me) {
			sting(me, WarhornSounds.CINE_HIT, 0.8f);
			score(me, SiegeDirector.SCORE_BATTLE, 0.6f);
		}

		private void introduce(List<ServerPlayer> me, String key) {
			score(me, SiegeDirector.SCORE_OMEN, 0.5f);
			sting(me, WarhornSounds.CINE_BRAAM, 0.9f);
			if (subject != null) play(me, CinematicLibrary.unitIntro(level, centre, subject, key));
		}

		private void startRam(List<ServerPlayer> me) {
			subject = spawnRam();
			introduce(me, "ram");
			next(Phase.RAM_INTRO);
		}

		private void startKidnapper(List<ServerPlayer> me) {
			discardAll(BatteringRamEntity.class);
			for (Mob m : level.getEntitiesOfClass(Mob.class, stageBox(), m -> m.entityTags().contains(BatteringRamEntity.CREW_TAG))) m.discard();
			subject = spawnKidnapper();
			introduce(me, "kidnapper");
			next(Phase.KIDNAP_INTRO);
		}

		private void startCatapult(List<ServerPlayer> me) {
			discardAll(KidnapperEntity.class);
			subject = spawnCatapult();
			introduce(me, "catapult");
			next(Phase.CATAPULT_INTRO);
		}

		private void finale(List<ServerPlayer> me) {
			score(me, SiegeDirector.SCORE_VICTORY, 1f);
			play(me, closing());
			next(Phase.FINALE);
		}

		private SapperEntity spawnSapper() {
			SapperEntity sapper = WarhornEntities.SAPPER.create(level, EntitySpawnReason.COMMAND);
			if (sapper == null) return null;
			sapper.snapTo(c.add(8, 0, -32), 0f, 0f);
			sapper.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), EntitySpawnReason.COMMAND, null);
			sapper.aimAt(centre);
			BlockPos wall = wallAhead(8);
			if (wall != null) sapper.targetWall(wall, wall.offset(0, -1, -1));
			sapper.setNoAi(true);
			level.addFreshEntity(sapper);
			return sapper;
		}

		private BatteringRamEntity spawnRam() {
			int x = -8;
			BlockPos wall = null;
			for (int lane = -6; lane >= -12 && wall == null; lane--) {
				wall = wallAhead(lane);
				x = lane;
			}
			BatteringRamEntity.spawn(level, null, centre, c.add(x, 0, -34), 0f, true);
			BatteringRamEntity ram = level.getEntitiesOfClass(BatteringRamEntity.class, stageBox()).stream().findFirst().orElse(null);
			if (ram != null && wall != null) ram.aimAt(wall, new BlockPos(wall.getX(), centre.getY() + 1, wall.getZ() - 1));
			return ram;
		}

		private BlockPos wallAhead(int dx) {
			for (int dz = -33; dz < 0; dz++) {
				BlockPos p = centre.offset(dx, 1, dz);
				if (net.warhorn.siege.Ruination.isBuilding(level.getBlockState(p))) return p;
			}
			return null;
		}

		private KidnapperEntity spawnKidnapper() {
			KidnapperEntity k = WarhornEntities.KIDNAPPER.create(level, EntitySpawnReason.COMMAND);
			if (k == null) return null;
			k.snapTo(c.add(9, 0, -28), 0f, 0f);
			k.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), EntitySpawnReason.COMMAND, null);
			k.sendAgainst(centre, centre.offset(0, 0, -RADIUS));
			k.setNoAi(true);
			level.addFreshEntity(k);
			return k;
		}

		private CatapultEntity spawnCatapult() {
			CatapultEntity.spawn(level, null, centre, c.add(-2, 0, 34), 180f, false);
			return level.getEntitiesOfClass(CatapultEntity.class, stageBox()).stream().findFirst().orElse(null);
		}

		private CinematicPayload opening() {
			Vec3 look = c.add(0, 2, -6);
			return new ShotBuilder(level)
				.key(0, c.add(46, 30, 52), look, 64f, -4f)
				.key(110, c.add(28, 16, 38), look, 58f, -2f)
				.key(200, c.add(12, 7, 26), look.add(0, -1, -4), 52f, 0f)
				.beat(30, 140, Beat.TITLE, "warhorn.showcase.title", "warhorn.showcase.subtitle")
				.build(200);
		}

		private CinematicPayload chase(Entity e, int ticks, float lookUp) {
			Vec3 at = e.position();
			Vec3 toVillage = c.subtract(at).multiply(1, 0, 1).normalize();
			Vec3 side = new Vec3(-toVillage.z, 0, toVillage.x);
			Vec3 look = UP.scale(lookUp);
			ShotBuilder b = new ShotBuilder(level).track(e);
			int steps = 6;
			for (int i = 0; i <= steps; i++) {
				float a = i / (float) steps;
				double angle = Mth.lerp(a, 0.35, 1.45);
				Vec3 offset = toVillage.scale(Math.cos(angle) * 5.2).add(side.scale(Math.sin(angle) * 5.2)).add(0, 1.8 - 0.4 * a, 0);
				b.follow((int) (ticks * a), at, offset, look, 58f - 6f * a, (1 - a) * -2f);
			}
			return b.build(ticks);
		}

		private CinematicPayload overwatch(Entity e, int ticks) {
			ShotBuilder b = new ShotBuilder(level).track(e);
			Vec3 from = e.position().subtract(c).multiply(1, 0, 1);
			double start = Math.atan2(from.z, from.x);
			int steps = 8;
			for (int i = 0; i <= steps; i++) {
				float a = i / (float) steps;
				double angle = start + a * 1.2;
				Vec3 lens = c.add(Math.cos(angle) * (19 - 5 * a), 9 - 3 * a, Math.sin(angle) * (19 - 5 * a));
				b.key((int) (ticks * a), lens, UP.scale(1.2), 50f - 6f * a, 0f);
			}
			return b.build(ticks);
		}

		private CinematicPayload escort(Entity e, int ticks) {
			Vec3 at = e.position();
			Vec3 fwd = Vec3.directionFromRotation(0, e.getYRot());
			Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 look = fwd.scale(3).add(0, 1.2, 0);
			return new ShotBuilder(level).track(e)
				.follow(0, at, fwd.scale(-6).add(side.scale(2.5)).add(0, 3.2, 0), look, 56f, 0f)
				.follow(ticks / 2, at, fwd.scale(-5).add(side.scale(3.5)).add(0, 2.6, 0), look, 54f, 0f)
				.follow(ticks, at, fwd.scale(-4.5).add(side.scale(4.2)).add(0, 2.2, 0), look, 52f, 0f)
				.build(ticks);
		}

		private CinematicPayload fuse(PowderKegEntity keg) {
			Vec3 k = keg.position();
			Vec3 out = k.subtract(c).multiply(1, 0, 1);
			out = out.lengthSqr() < 1e-3 ? new Vec3(0, 0, -1) : out.normalize();
			Vec3 side = new Vec3(-out.z, 0, out.x);
			Vec3 at = k.add(0, 0.9, 0);
			return new ShotBuilder(level)
				.key(0, k.add(out.scale(3.2)).add(side.scale(1.6)).add(0, 1.2, 0), at, 50f, -2f)
				.key(PowderKegEntity.FUSE - 4, k.add(out.scale(1.8)).add(side.scale(0.9)).add(0, 0.9, 0), at, 36f, 0f)
				.cut()
				.key(PowderKegEntity.FUSE - 2, k.add(out.scale(7)).add(side.scale(-3)).add(0, 3.5, 0), at, 62f, 3f)
				.key(PowderKegEntity.FUSE + 60, k.add(out.scale(11)).add(side.scale(-5)).add(0, 6, 0), at, 66f, 0f)
				.build(PowderKegEntity.FUSE + 60);
		}

		private CinematicPayload strike(BatteringRamEntity ram) {
			Vec3 r = ram.position();
			Vec3 fwd = Vec3.directionFromRotation(0, ram.getYRot());
			Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 head = r.add(fwd.scale(BatteringRamEntity.HEAD)).add(0, 1.2, 0);
			int split = BatteringRamEntity.SWING_TICKS * 2;
			return new ShotBuilder(level)
				.key(0, head.add(side.scale(6.5)).add(fwd.scale(-2.6)).add(0, 0.6, 0), head.add(fwd.scale(-1.2)), 60f, -3f)
				.key(split, head.add(side.scale(5.6)).add(fwd.scale(-1.6)).add(0, 0.4, 0), head.add(fwd.scale(-1.0)), 54f, 0f)
				.cut()
				.key(split + 1, r.add(fwd.scale(-7)).add(side.scale(-3)).add(0, 3.2, 0), head, 60f, 0f)
				.key(260, r.add(fwd.scale(-9)).add(side.scale(-4)).add(0, 4.5, 0), head, 64f, 0f)
				.build(260);
		}

		private CinematicPayload flight(KidnapperEntity k) {
			Vec3 at = k.position();
			Vec3 away = at.subtract(c).multiply(1, 0, 1);
			Vec3 dir = away.lengthSqr() < 1e-3 ? new Vec3(0, 0, -1) : away.normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			return new ShotBuilder(level).track(k)
				.follow(0, at, dir.scale(4.5).add(side.scale(1.2)).add(0, 1.5, 0), UP.scale(1.5), 50f, 2f)
				.follow(70, at, dir.scale(3.4).add(side.scale(2.2)).add(0, 1.3, 0), UP.scale(1.6), 46f, 0f)
				.follow(130, at, side.scale(3.6).add(dir.scale(0.5)).add(0, 1.4, 0), UP.scale(1.6), 50f, -2f)
				.build(130);
		}

		private CinematicPayload freed(Vec3 at) {
			Vec3 out = at.subtract(c).multiply(1, 0, 1);
			Vec3 dir = out.lengthSqr() < 1e-3 ? new Vec3(0, 0, -1) : out.normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 look = at.add(0, 1, 0);
			return new ShotBuilder(level)
				.key(0, at.add(dir.scale(3)).add(side.scale(2)).add(0, 1.6, 0), look, 50f, 0f)
				.key(100, at.add(dir.scale(5)).add(side.scale(3.5)).add(0, 2.6, 0), look, 56f, 0f)
				.build(100);
		}

		private CinematicPayload winding(CatapultEntity cat) {
			Vec3 p = cat.position();
			Vec3 fwd = Vec3.directionFromRotation(0, cat.getYRot());
			Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 look = UP.scale(1.6);
			return new ShotBuilder(level).track(cat)
				.key(0, p.add(side.scale(6)).add(fwd.scale(-3)).add(0, 1.8, 0), look, 56f, -2f)
				.key(140, p.add(side.scale(5)).add(fwd.scale(2.5)).add(0, 2.2, 0), look, 50f, 0f)
				.key(260, p.add(side.scale(2.5)).add(fwd.scale(-6)).add(0, 3.6, 0), UP.scale(2.5), 58f, 1f)
				.key(420, p.add(side.scale(1.5)).add(fwd.scale(-7)).add(0, 4.2, 0), UP.scale(2.5).add(fwd.scale(6)), 60f, 0f)
				.build(420);
		}

		private CinematicPayload pot(FirePotEntity pot) {
			Vec3 at = pot.position();
			Vec3 v = pot.getDeltaMovement().multiply(1, 0, 1);
			Vec3 dir = v.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : v.normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			return new ShotBuilder(level).track(pot)
				.follow(0, at, dir.scale(-4).add(side.scale(1.6)).add(0, 1.4, 0), dir.scale(2), 60f, 0f)
				.follow(40, at, dir.scale(-5.5).add(side.scale(2.2)).add(0, 2.4, 0), dir.scale(3).add(0, -1, 0), 66f, -3f)
				.follow(90, at, dir.scale(-6.5).add(side.scale(2.5)).add(0, 3.4, 0), dir.scale(3).add(0, -1.5, 0), 68f, 0f)
				.build(140);
		}

		private CinematicPayload impact(Vec3 at) {
			Vec3 out = at.subtract(c).multiply(1, 0, 1);
			Vec3 dir = out.lengthSqr() < 1e-3 ? new Vec3(0, 0, 1) : out.normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			return new ShotBuilder(level)
				.key(0, at.add(dir.scale(9)).add(side.scale(4)).add(0, 4, 0), at, 60f, 0f)
				.key(100, at.add(dir.scale(6.5)).add(side.scale(3)).add(0, 3, 0), at, 52f, 0f)
				.build(100);
		}

		private CinematicPayload wreck(CatapultEntity cat) {
			Vec3 p = cat.position();
			Vec3 fwd = Vec3.directionFromRotation(0, cat.getYRot());
			Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 look = p.add(0, 1.4, 0);
			return new ShotBuilder(level)
				.key(0, p.add(fwd.scale(5)).add(side.scale(-4)).add(0, 2.4, 0), look, 54f, 2f)
				.key(125, p.add(fwd.scale(3.6)).add(side.scale(-2.8)).add(0, 1.9, 0), look, 46f, 0f)
				.build(125);
		}

		private CinematicPayload closing() {
			Vec3 look = c.add(0, 1, -4);
			return new ShotBuilder(level)
				.key(0, c.add(-14, 6, 24), look, 56f, 0f)
				.key(220, c.add(-40, 34, 50), look, 70f, 3f)
				.beat(50, 150, Beat.TITLE, "warhorn.showcase.end_title", "warhorn.showcase.end_subtitle")
				.build(220);
		}

		private void play(List<ServerPlayer> me, CinematicPayload shot) {
			CinematicService.play(level, channel, me, shot);
		}

		private void score(List<ServerPlayer> me, int mood, float intensity) {
			Cues.send(me, new CuePayload(CuePayload.SCORE, intensity, mood, c, "", ""));
		}

		private void sting(List<ServerPlayer> me, SoundEvent sound, float volume) {
			Cues.send(me, new CuePayload(CuePayload.STING, volume, 0, c, BuiltInRegistries.SOUND_EVENT.getKey(sound).toString(), ""));
		}

		private AABB stageBox() {
			return new AABB(centre).inflate(RADIUS + 8, HEADROOM + 16, RADIUS + 8).move(0, HEADROOM / 2.0, 0);
		}

		private void discardAll(Class<? extends Entity> type) {
			for (Entity e : level.getEntitiesOfClass(type, stageBox())) e.discard();
		}

		void finish(ServerPlayer player, boolean online) {
			CinematicService.clear(channel);
			for (Entity e : level.getEntitiesOfClass(Entity.class, stageBox(), e -> !(e instanceof Player))) e.discard();
			for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS - 6, -1, -RADIUS - 6), centre.offset(RADIUS + 6, HEADROOM - 2, RADIUS + 6))) {
				if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
			var weather = level.getServer().getWeatherData();
			weather.setClearWeatherTime(clearTime);
			weather.setRainTime(rainTime);
			weather.setThunderTime(thunderTime);
			weather.setRaining(raining);
			weather.setThundering(thundering);
			weather.setDirty();
			if (clock != null) {
				level.getServer().clockManager().setTotalTicks(clock, clockTicks);
				level.getServer().clockManager().setPaused(clock, clockPaused);
			}
			if (player == null) return;
			if (online) score(List.of(player), SiegeDirector.SCORE_SILENT, 0f);
			player.setGameMode(mode);
			player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
			if (online) player.sendSystemMessage(Component.translatable("warhorn.showcase.end"));
		}

		private void run(ServerPlayer player, String cmd) {
			level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), cmd);
		}
	}
}
