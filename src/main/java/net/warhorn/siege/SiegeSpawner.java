package net.warhorn.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.warhorn.mixin.MobAccessor;
import net.warhorn.registry.WarhornSounds;

import java.util.ArrayList;
import java.util.List;

public final class SiegeSpawner {
	public static final String TAG = "warhorn_soldier";
	public static final String EXTRA_TAG = "warhorn_extra";
	static final double HALT_RADIUS = 26;
	public static final int CHARGE_TICKS = 20 * 5;

	private SiegeSpawner() {
	}

	public static BlockPos findStaging(ServerLevel level, BlockPos center, RandomSource random) {
		BlockPos best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		float offset = random.nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < 16; i++) {
			float angle = offset + i * Mth.TWO_PI / 16;
			int distance = 48 + random.nextInt(17);
			BlockPos column = center.offset(Mth.floor(Mth.cos(angle) * distance), 0, Mth.floor(Mth.sin(angle) * distance));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.below()).isEmpty()) continue;
			double score = -Math.abs(ground.getY() - center.getY()) * 2.0;
			for (int dx = -12; dx <= 12; dx += 4) {
				for (int dz = -12; dz <= 12; dz += 4) {
					int x = ground.getX() + dx;
					int z = ground.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) score -= Math.abs(y - ground.getY());
					if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > y + 1) score -= 2.5;
					else if (y > ground.getY() + 3) score -= 1.0;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = ground;
			}
		}
		if (best == null) {
			best = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(56, 0, 0));
		}
		return best;
	}

	public static List<Mob> spawnWave(ServerLevel level, Siege siege, ActPlan.Wave wave, int defenders,
		boolean frozen, int bannerCarriers) {
		List<Mob> spawned = new ArrayList<>();
		if (frozen) trample(level, siege.staging(), 16);
		Vec3 toVillage = Vec3.atCenterOf(siege.center()).subtract(Vec3.atCenterOf(siege.staging())).multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-toVillage.z, 0, toVillage.x);
		float yaw = (float) (Mth.atan2(toVillage.z, toVillage.x) * Mth.RAD_TO_DEG) - 90f;
		List<Unit> order = new ArrayList<>();
		for (Unit unit : List.of(Unit.CATAPULT, Unit.RAM, Unit.RAVAGER_RIDER, Unit.RAVAGER, Unit.VINDICATOR, Unit.SAPPER, Unit.KIDNAPPER, Unit.EVOKER, Unit.WITCH, Unit.PILLAGER)) {
			Integer base = wave.units().get(unit);
			if (base == null) continue;
			int count = ActPlan.scaled(base, defenders, siege.omenLevel());
			for (int i = 0; i < count; i++) order.add(unit);
		}
		int catapults = 0;
		for (java.util.Iterator<Unit> it = order.iterator(); it.hasNext(); ) {
			if (it.next() != Unit.CATAPULT) continue;
			it.remove();
			int sign = catapults % 2 == 0 ? 1 : -1;
			Vec3 at = Vec3.atBottomCenterOf(siege.staging()).add(side.scale(sign * (13 + 7 * (catapults / 2)))).subtract(toVillage.scale(3));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
			spawned.addAll(net.warhorn.entity.CatapultEntity.spawn(level, siege, siege.center(), new Vec3(at.x, ground.getY(), at.z), yaw, frozen));
			catapults++;
		}
		int perRank = 6;
		int rams = 0;
		while (!order.isEmpty() && order.getFirst() == Unit.RAM) {
			order.removeFirst();
			Vec3 at = Vec3.atBottomCenterOf(siege.staging()).add(toVillage.scale(4.5)).add(side.scale((rams + 1) / 2 * 5.0 * (rams % 2 == 0 ? 1 : -1)));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
			spawned.addAll(net.warhorn.entity.BatteringRamEntity.spawn(level, siege, new Vec3(at.x, ground.getY(), at.z), yaw, frozen));
			rams++;
		}
		for (int i = 0; i < order.size(); i++) {
			int rank = i / perRank;
			int file = i % perRank - perRank / 2;
			Vec3 at = Vec3.atBottomCenterOf(siege.staging())
				.add(side.scale(file * 1.8 + (rank % 2) * 0.9))
				.subtract(toVillage.scale(rank * 2.1));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
			Mob mob = spawnUnit(level, siege, order.get(i), new Vec3(at.x, ground.getY(), at.z), yaw, frozen,
				spawned.size() < bannerCarriers);
			if (mob != null) spawned.add(mob);
		}
		int torches = siege.stage() == SiegeStage.OMEN ? 0 : Math.min(siege.act, 2);
		for (Mob mob : spawned) {
			if (torches <= 0) break;
			if (mob instanceof PathfinderMob pathfinder && mob.getType() == EntityTypes.VINDICATOR
				&& !mob.entityTags().contains(net.warhorn.entity.BatteringRamEntity.CREW_TAG)) {
				makeTorchbearer(pathfinder, siege.center());
				torches--;
			}
		}
		return spawned;
	}

	private static void trample(ServerLevel level, BlockPos staging, int radius) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) continue;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, staging.getX() + dx, staging.getZ() + dz);
				for (int dy = 0; dy <= 1; dy++) {
					at.set(staging.getX() + dx, ground + dy, staging.getZ() + dz);
					BlockState state = level.getBlockState(at);
					if (state.isAir() || !state.canBeReplaced() || !state.getFluidState().isEmpty()
						|| state.getBlock() instanceof SnowLayerBlock) continue;
					level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
	}

	private static Mob spawnUnit(ServerLevel level, Siege siege, Unit unit, Vec3 pos, float yaw, boolean frozen,
		boolean banner) {
		Mob mob = switch (unit) {
			case PILLAGER -> EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
			case VINDICATOR -> EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
			case EVOKER -> EntityTypes.EVOKER.create(level, EntitySpawnReason.EVENT);
			case WITCH -> EntityTypes.WITCH.create(level, EntitySpawnReason.EVENT);
			case RAVAGER, RAVAGER_RIDER -> EntityTypes.RAVAGER.create(level, EntitySpawnReason.EVENT);
			case SAPPER -> net.warhorn.registry.WarhornEntities.SAPPER.create(level, EntitySpawnReason.EVENT);
			case KIDNAPPER -> net.warhorn.registry.WarhornEntities.KIDNAPPER.create(level, EntitySpawnReason.EVENT);
			case RAM, CATAPULT -> null;
		};
		if (mob instanceof net.warhorn.entity.SapperEntity sapper) sapper.aimAt(siege.center());
		if (mob instanceof net.warhorn.entity.KidnapperEntity kidnapper) kidnapper.sendAgainst(siege.center(), siege.staging());
		if (mob == null) return null;
		prepare(level, siege, mob, pos, yaw, frozen);
		if (banner && mob instanceof Raider) {
			mob.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			mob.setDropChance(EquipmentSlot.HEAD, 0.0f);
		}
		level.addFreshEntityWithPassengers(mob);
		siege.raiders.add(mob.getUUID());
		if (unit == Unit.RAVAGER_RIDER) {
			Mob rider = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
			if (rider != null) {
				prepare(level, siege, rider, pos, yaw, frozen);
				level.addFreshEntity(rider);
				rider.startRiding(mob, true, false);
				siege.raiders.add(rider.getUUID());
			}
		}
		return mob;
	}

	public static Mob reinforce(ServerLevel level, Siege siege, Unit unit, Vec3 pos, float yaw) {
		return spawnUnit(level, siege, unit, pos, yaw, false, false);
	}

	public static void makeTorchbearer(PathfinderMob mob, BlockPos center) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));
		mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
		mob.addTag(ArsonGoal.TAG);
		((MobAccessor) mob).warhorn$goalSelector().addGoal(2, new ArsonGoal(mob, center));
	}

	public static void prepare(ServerLevel level, Siege siege, Mob mob, Vec3 pos, float yaw, boolean frozen) {
		mob.snapTo(pos, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), EntitySpawnReason.EVENT, null);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (mob instanceof Raider raider) raider.setCanJoinRaid(false);
		var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (followRange != null) followRange.setBaseValue(Math.max(followRange.getBaseValue(), 48.0));
		if (mob instanceof PathfinderMob pathfinder) {
			((MobAccessor) mob).warhorn$goalSelector().addGoal(5, new MarchOnVillageGoal(pathfinder, siege.center()));
		}
		mob.setNoAi(frozen);
	}

	public static void spawnExtras(ServerLevel level, Siege siege, int ranks, int behind) {
		Vec3 toVillage = Vec3.atCenterOf(siege.center()).subtract(Vec3.atCenterOf(siege.staging())).multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-toVillage.z, 0, toVillage.x);
		float yaw = (float) (Mth.atan2(toVillage.z, toVillage.x) * Mth.RAD_TO_DEG) - 90f;
		RandomSource random = level.getRandom();
		for (int rank = 0; rank < ranks; rank++) {
			int files = 7 + rank % 2;
			for (int file = 0; file < files; file++) {
				Vec3 at = Vec3.atBottomCenterOf(siege.staging())
					.add(side.scale((file - files / 2.0) * 1.8 + random.nextFloat() * 0.5))
					.subtract(toVillage.scale((behind + rank) * 2.1 + random.nextFloat() * 0.4));
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
				Mob extra = (random.nextFloat() < 0.6f ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR).create(level, EntitySpawnReason.EVENT);
				if (extra == null) continue;
				extra.snapTo(new Vec3(at.x, ground.getY(), at.z), yaw + random.nextFloat() * 6 - 3, 0f);
				extra.setYHeadRot(extra.getYRot());
				extra.setYBodyRot(extra.getYRot());
				extra.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
				extra.setNoAi(true);
				extra.setPermanentlyInvulnerable(true);
				extra.setSilent(true);
				extra.addTag(EXTRA_TAG);
				if (file % 3 == 1) {
					extra.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
					extra.setDropChance(EquipmentSlot.HEAD, 0f);
				}
				if (extra instanceof Raider raider) raider.setCanJoinRaid(false);
				level.addFreshEntity(extra);
				siege.extras.add(extra.getUUID());
			}
		}
	}

	public static void dismissExtras(ServerLevel level, Siege siege) {
		for (var uuid : siege.extras) {
			var e = level.getEntity(uuid);
			if (e != null) e.discard();
		}
		siege.extras.clear();
	}

	public static void release(ServerLevel level, Siege siege) {
		for (var uuid : siege.raiders) {
			if (level.getEntity(uuid) instanceof Mob mob) mob.setNoAi(false);
		}
		for (var ram : rams(level, siege)) ram.release();
		for (var catapult : level.getEntitiesOfClass(net.warhorn.entity.CatapultEntity.class,
			new net.minecraft.world.phys.AABB(siege.center()).inflate(112, 48, 112), c -> c.belongsTo(siege.id()))) {
			catapult.release();
		}
	}

	public static List<net.warhorn.entity.BatteringRamEntity> rams(ServerLevel level, Siege siege) {
		return level.getEntitiesOfClass(net.warhorn.entity.BatteringRamEntity.class,
			new net.minecraft.world.phys.AABB(siege.center()).inflate(112, 48, 112), r -> r.belongsTo(siege.id()));
	}

	public static void march(ServerLevel level, Siege siege, double step) {
		Vec3 center = Vec3.atBottomCenterOf(siege.center());
		Vec3 toVillage = center.subtract(Vec3.atCenterOf(siege.staging())).multiply(1, 0, 1).normalize();
		boolean running = step > 0.12;
		List<java.util.UUID> all = new ArrayList<>(siege.raiders);
		all.addAll(siege.extras);
		Vec3 sum = Vec3.ZERO;
		int moved = 0;
		for (var uuid : all) {
			if (!(level.getEntity(uuid) instanceof Mob mob) || !mob.isNoAi() || mob.isPassenger() || !mob.isAlive()) continue;
			if (mob.entityTags().contains(net.warhorn.entity.CatapultEntity.CREW_TAG)) continue;
			double fromCenter = Math.sqrt(Mth.square(mob.getX() - center.x) + Mth.square(mob.getZ() - center.z));
			if (fromCenter < HALT_RADIUS) continue;
			double x = mob.getX() + toVillage.x * step;
			double z = mob.getZ() + toVillage.z * step;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			double y = Math.max(ground, mob.getY() - 0.5);
			mob.setPos(x, y, z);
			sum = sum.add(mob.position());
			moved++;
			if (running && mob.getRandom().nextInt(3) == 0) kickUpEarth(level, mob);
		}
		for (var ram : rams(level, siege)) {
			if (!ram.held()) continue;
			double fromCenter = Math.sqrt(Mth.square(ram.getX() - center.x) + Mth.square(ram.getZ() - center.z));
			if (fromCenter < HALT_RADIUS) continue;
			double x = ram.getX() + toVillage.x * step;
			double z = ram.getZ() + toVillage.z * step;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			ram.marchTo(x, Math.max(ground, ram.getY() - 0.5), z);
		}
		if (moved == 0) return;
		Vec3 host = sum.scale(1.0 / moved);
		if (!running && step > 0 && level.getGameTime() % 10 == 0) {
			level.playSound(null, host.x, host.y, host.z, WarhornSounds.TRAMP, SoundSource.HOSTILE,
				Math.min(3.0f, 1.2f + moved * 0.08f), 0.95f + level.getRandom().nextFloat() * 0.1f);
		}
	}

	public static void kickUpEarth(ServerLevel level, Mob mob) {
		BlockState under = level.getBlockState(mob.blockPosition().below());
		if (under.isAir()) return;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), mob.getX(), mob.getY() + 0.1, mob.getZ(),
			3, 0.25, 0.05, 0.25, 0.15);
		if (mob.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mob.getX(), mob.getY() + 0.2, mob.getZ(), 1, 0.3, 0.05, 0.3, 0.005);
		}
	}

	public static void charge(ServerLevel level, Siege siege) {
		for (var uuid : siege.raiders) {
			if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive() && !(mob instanceof net.warhorn.entity.WarlordEntity)) {
				mob.addEffect(new MobEffectInstance(MobEffects.SPEED, CHARGE_TICKS, 1, false, false));
			}
		}
		siege.chargeTicks = CHARGE_TICKS;
	}
}
