package net.warhorn.siege;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.phys.Vec3;
import net.warhorn.Warhorn;
import net.warhorn.cinematic.CinematicService;
import net.warhorn.cinematic.SlowMotion;
import net.warhorn.mixin.MobAccessor;
import net.warhorn.net.CuePayload;
import net.warhorn.net.SiegeStatePayload;

import java.util.List;

public final class SiegeManager {
	private SiegeManager() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SiegeManager::tickLevel);
		ServerTickEvents.END_SERVER_TICK.register(SlowMotion::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RebuildService.clear();
			SlowMotion.stop(server);
			CinematicService.clearAll();
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> RebuildService.forget(handler.player.getUUID()));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(SiegeSpawner.EXTRA_TAG)) {
				Siege siege = find(level, entity.blockPosition(), SiegeDirector.RADIUS * 2);
				if (siege == null || !siege.extras.contains(entity.getUUID())) {
					level.getServer().execute(entity::discard);
				}
				return;
			}
			if (!(entity instanceof PathfinderMob mob) || !mob.entityTags().contains(SiegeSpawner.TAG)) return;
			Siege siege = find(level, mob.blockPosition(), SiegeDirector.RADIUS * 2);
			if (siege != null) {
				((MobAccessor) mob).warhorn$goalSelector().addGoal(5, new MarchOnVillageGoal(mob, siege.center()));
				if (mob.entityTags().contains(ArsonGoal.TAG)) {
					((MobAccessor) mob).warhorn$goalSelector().addGoal(2, new ArsonGoal(mob, siege.center()));
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
				|| reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;
			if (!entity.entityTags().contains(SiegeSpawner.TAG)) return;
			for (Siege siege : data(level).sieges) siege.raiders.remove(entity.getUUID());
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && CinematicService.watching(player)));
	}

	public static SiegeSavedData data(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(SiegeSavedData.TYPE);
	}

	private static void tickLevel(ServerLevel level) {
		RebuildService.tick(level);
		SiegeSavedData data = data(level);
		if (data.sieges.isEmpty()) return;
		for (Siege siege : List.copyOf(data.sieges)) {
			SiegeDirector.tick(level, siege);
			if (siege.stage == SiegeStage.FINISHED) {
				end(level, siege);
			}
		}
		data.setDirty();
	}

	public static Siege startFromOmen(ServerLevel level, ServerPlayer player, BlockPos omenPos, int omenLevel) {
		List<PoiRecord> homes = level.getPoiManager()
			.getInRange(p -> p.is(PoiTypeTags.VILLAGE), omenPos, 64, PoiManager.Occupancy.IS_OCCUPIED).toList();
		BlockPos center = omenPos;
		if (!homes.isEmpty()) {
			Vec3 sum = Vec3.ZERO;
			for (PoiRecord home : homes) sum = sum.add(Vec3.atCenterOf(home.getPos()));
			center = BlockPos.containing(sum.scale(1.0 / homes.size()));
		}
		return start(level, center, omenLevel);
	}

	public static Siege start(ServerLevel level, BlockPos center, int omenLevel) {
		Siege existing = find(level, center, 96);
		if (existing != null) return existing;
		SiegeSavedData data = data(level);
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
		Siege siege = new Siege(data.nextId(), ground, SiegeSpawner.findStaging(level, ground, level.getRandom()),
			Math.clamp(omenLevel, 1, 5));
		data.sieges.add(siege);
		data.setDirty();
		Warhorn.LOG.info("Siege {} begins at {} (omen {})", siege.id, siege.center, siege.omenLevel);
		return siege;
	}

	public static Siege find(ServerLevel level, BlockPos pos, int radius) {
		Siege nearest = null;
		double best = Double.MAX_VALUE;
		for (Siege siege : data(level).sieges) {
			if (siege.stage == SiegeStage.FINISHED || !siege.contains(pos, radius)) continue;
			double d = siege.center.distSqr(pos);
			if (d < best) {
				best = d;
				nearest = siege;
			}
		}
		return nearest;
	}

	public static Siege byId(ServerLevel level, int id) {
		for (Siege siege : data(level).sieges) {
			if (siege.id == id && siege.stage != SiegeStage.FINISHED) return siege;
		}
		return null;
	}

	public static void stop(ServerLevel level, Siege siege) {
		SiegeSpawner.dismissExtras(level, siege);
		for (var uuid : List.copyOf(siege.raiders)) {
			var entity = level.getEntity(uuid);
			if (entity != null) entity.discard();
		}
		siege.raiders.clear();
		end(level, siege);
	}

	private static void end(ServerLevel level, Siege siege) {
		for (ServerPlayer player : SiegeDirector.players(level, siege)) {
			ServerPlayNetworking.send(player, SiegeStatePayload.INACTIVE);
			ServerPlayNetworking.send(player, new CuePayload(CuePayload.SCORE, 0, SiegeDirector.SCORE_SILENT, Vec3.ZERO, "", ""));
		}
		CinematicService.clear(siege.id);
		SlowMotion.stop(level.getServer());
		RebuildRegistry.adopt(level, siege);
		data(level).sieges.remove(siege);
		data(level).setDirty();
	}

	public static void skipTo(ServerLevel level, Siege siege, SiegeStage stage, int act) {
		for (var uuid : List.copyOf(siege.raiders)) {
			var entity = level.getEntity(uuid);
			if (entity != null) entity.discard();
		}
		siege.raiders.clear();
		SiegeSpawner.dismissExtras(level, siege);
		CinematicService.clear(siege.id);
		siege.raidersAlive = 0;
		int target = Math.clamp(act, 0, ActPlan.ACTS.size() - 1);
		if (stage == SiegeStage.ACT) {
			siege.act = target - 1;
			SiegeDirector.enter(level, siege, SiegeStage.INTERLUDE);
			siege.stageTicks = ActPlan.INTERLUDE_TICKS - 1;
		} else {
			siege.act = target;
			siege.wave = 0;
			SiegeDirector.enter(level, siege, stage);
		}
	}

	public static boolean isUnderSiege(ServerLevel level, BlockPos pos) {
		Siege siege = find(level, pos, 96);
		return siege != null && (siege.stage.fighting() || siege.stage == SiegeStage.INTERLUDE);
	}
}
