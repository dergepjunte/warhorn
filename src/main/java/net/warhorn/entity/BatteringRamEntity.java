package net.warhorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.siege.Ruination;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;
import net.warhorn.siege.SiegeSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BatteringRamEntity extends Entity {
	public static final String CREW_TAG = "warhorn_ram_crew";
	public static final int SWING_TICKS = 52;
	public static final int STRIKE = 34;
	private static final double RUN_UP = 2.3;
	private static final int BLOWS_TO_BREACH = 3;
	private static final int MAX_BREACHES = 4;
	private static final double SPEED = 0.085;
	private static final int STUCK_TICKS = 100;
	private static final int SEARCH_TICKS = 900;
	private static final double[][] SLOTS = {{-1.05, -0.15}, {1.05, -0.15}, {-1.05, -1.25}, {1.05, -1.25}};
	public static final double HEAD = 1.78;
	private static final double STOP_BACK = 1.15;

	private static final EntityDataAccessor<Integer> SWING = SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> BEARERS = SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<Integer>[] BEARER_IDS = new EntityDataAccessor[] {
		SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT),
		SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT),
		SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT),
		SynchedEntityData.defineId(BatteringRamEntity.class, EntityDataSerializers.INT)};

	private final List<UUID> crew = new ArrayList<>();
	private final java.util.Set<Long> refused = new java.util.HashSet<>();
	private final List<BlockPos> broken = new ArrayList<>();
	private int siegeId = -1;
	private BlockPos village;
	private BlockPos wall;
	private BlockPos standAt;
	private int blows;
	private int breaches;
	private int stuck;
	private int searching;
	private int dodge = 1;
	private double closest = Double.MAX_VALUE;
	private int idle;
	private boolean held;
	public int swingClock = -1;

	public BatteringRamEntity(EntityType<? extends BatteringRamEntity> type, Level level) {
		super(type, level);
	}

	public static List<Mob> spawn(ServerLevel level, Siege siege, Vec3 at, float yaw, boolean frozen) {
		return spawn(level, siege, siege.center(), at, yaw, frozen);
	}

	public static List<Mob> spawn(ServerLevel level, Siege siege, BlockPos village, Vec3 at, float yaw, boolean frozen) {
		List<Mob> bearers = new ArrayList<>();
		BatteringRamEntity ram = WarhornEntities.BATTERING_RAM.create(level, EntitySpawnReason.EVENT);
		if (ram == null) return bearers;
		ram.siegeId = siege != null ? siege.id() : -1;
		ram.village = village;
		ram.held = frozen;
		ram.snapTo(at.x, at.y, at.z, yaw, 0);
		level.addFreshEntity(ram);
		for (int i = 0; i < SLOTS.length; i++) {
			Mob bearer = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
			if (bearer == null) continue;
			if (siege != null) {
				SiegeSpawner.prepare(level, siege, bearer, ram.slot(i), yaw, true);
			} else {
				bearer.snapTo(ram.slot(i), yaw, 0);
				bearer.finalizeSpawn(level, level.getCurrentDifficultyAt(ram.blockPosition()), EntitySpawnReason.EVENT, null);
				bearer.setPersistenceRequired();
				bearer.setNoAi(true);
			}
			bearer.addTag(CREW_TAG);
			bearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
			bearer.setAggressive(true);
			level.addFreshEntity(bearer);
			if (siege != null) siege.enlist(bearer.getUUID());
			ram.crew.add(bearer.getUUID());
			bearers.add(bearer);
		}
		ram.entityData.set(BEARERS, bearers.size());
		return bearers;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SWING, -1);
		builder.define(BEARERS, 4);
		for (var id : BEARER_IDS) builder.define(id, -1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (SWING.equals(key)) swingClock = entityData.get(SWING);
	}

	public void aimAt(BlockPos wall, BlockPos stand) {
		this.wall = wall;
		this.standAt = stand;
		blows = 0;
	}

	public int breaches() {
		return breaches;
	}

	public boolean swinging() {
		return entityData.get(SWING) >= 0;
	}

	public int bearers() {
		return entityData.get(BEARERS);
	}

	public void targetWall(BlockPos wall, BlockPos stand) {
		this.wall = wall;
		this.standAt = stand;
	}

	public List<UUID> crew() {
		return crew;
	}

	public void release() {
		held = false;
	}

	public boolean held() {
		return held;
	}

	public void marchTo(double x, double y, double z) {
		setPos(x, y, z);
	}

	public boolean belongsTo(int siege) {
		return siegeId == siege;
	}

	private Vec3 forward() {
		return Vec3.directionFromRotation(0, getYRot());
	}

	private Vec3 slot(int i) {
		return slotAt(position(), getYRot(), swingOffset(), i);
	}

	private static Vec3 slotAt(Vec3 base, float yaw, double offset, int i) {
		Vec3 f = Vec3.directionFromRotation(0, yaw);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		return base.add(side.scale(SLOTS[i][0])).add(f.scale(SLOTS[i][1] + offset));
	}

	public void alignCrew() {
		double now = swingClock < 0 ? 0 : swingCurve(swingClock);
		double before = swingClock <= 0 ? now : swingCurve(swingClock - 1);
		Vec3 base = position(), baseO = new Vec3(xo, yo, zo);
		for (int i = 0; i < BEARER_IDS.length; i++) {
			if (!(level().getEntity(entityData.get(BEARER_IDS[i])) instanceof LivingEntity man) || !man.isAlive()) continue;
			Vec3 at = slotAt(base, getYRot(), now, i);
			Vec3 was = slotAt(baseO, yRotO, before, i);
			man.setPos(at.x, man.getY(), at.z);
			man.xo = was.x;
			man.zo = was.z;
			man.setYRot(getYRot());
			man.yRotO = yRotO;
			man.yBodyRot = getYRot();
			man.yBodyRotO = yRotO;
			man.yHeadRot = getYRot();
			man.yHeadRotO = yRotO;
		}
	}

	public double swingOffset() {
		int t = entityData.get(SWING);
		if (t < 0) return 0;
		return swingCurve(t);
	}

	public static double swingCurve(float t) {
		if (t < 20) {
			float a = t / 20f;
			return -RUN_UP * a * a * (3 - 2 * a);
		}
		if (t < 28) return -RUN_UP - 0.25 * Mth.sin((t - 20) / 8f * Mth.HALF_PI);
		if (t < STRIKE) {
			float a = (t - 28) / (float) (STRIKE - 28);
			return Mth.lerp(a * a * a, -RUN_UP - 0.25, 0.6);
		}
		if (t < STRIKE + 6) return Mth.lerp((t - STRIKE) / 6f, 0.6f, -0.55f);
		float a = Math.min(1, (t - STRIKE - 6) / (float) (SWING_TICKS - STRIKE - 6));
		return -0.55 * (1 - a * a * (3 - 2 * a));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (swingClock >= 0) swingClock++;
			return;
		}
		ServerLevel level = (ServerLevel) level();
		List<Mob> alive = new ArrayList<>();
		for (UUID uuid : crew) {
			if (level.getEntity(uuid) instanceof Mob m && m.isAlive()) alive.add(m);
		}
		if (alive.size() != bearers()) entityData.set(BEARERS, alive.size());
		for (int i = 0; i < BEARER_IDS.length; i++) {
			int id = i < crew.size() && level.getEntity(crew.get(i)) instanceof Mob m && m.isAlive() ? m.getId() : -1;
			if (entityData.get(BEARER_IDS[i]) != id) entityData.set(BEARER_IDS[i], id);
		}
		if (alive.isEmpty()) {
			fall(level);
			return;
		}
		if (!held) work(level, alive.size());
		for (int i = 0; i < crew.size(); i++) {
			if (level.getEntity(crew.get(i)) instanceof Mob bearer && bearer.isAlive()) carry(level, bearer, i);
		}
	}

	private void work(ServerLevel level, int bearers) {
		int swing = entityData.get(SWING);
		if (swing >= 0) {
			swing++;
			entityData.set(SWING, swing);
			if (swing == 22) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.6f, 0.65f);
			}
			if (swing > 28 && swing < STRIKE && swing % 2 == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.HOSTILE, 1.3f, 0.7f);
			}
			if (swing == STRIKE) strike(level);
			if (swing >= SWING_TICKS) entityData.set(SWING, wall != null ? 0 : -1);
			return;
		}
		if (wall == null || !Ruination.isBuilding(level.getBlockState(wall))) {
			wall = null;
			if (breaches >= MAX_BREACHES) {
				disband(level);
				return;
			}
			if (searching % 20 != 0 || !retarget(level) && !retargetAnywhere(level)) {
				if (++searching > SEARCH_TICKS || !marchOnVillage(level, bearers)) disband(level);
				return;
			}
			searching = 0;
		}
		Vec3 inward = Vec3.atCenterOf(wall).subtract(Vec3.atCenterOf(standAt)).multiply(1, 0, 1).normalize();
		Vec3 spot = Vec3.atBottomCenterOf(standAt).subtract(inward.scale(STOP_BACK));
		Vec3 to = spot.subtract(position()).multiply(1, 0, 1);
		if (to.length() < 0.6) {
			face(Vec3.atCenterOf(wall).subtract(position()), 10f);
			entityData.set(SWING, 0);
			return;
		}
		face(to, 4f);
		double speed = SPEED * Math.max(0.35, bearers / 4.0);
		if (to.length() < closest - 0.5) {
			closest = to.length();
			idle = 0;
		}
		if (!stepToward(level, to, speed) || ++idle > STUCK_TICKS * 2) {
			if (++stuck > STUCK_TICKS || idle > STUCK_TICKS * 2) {
				if (wall != null) refused.add(wall.asLong());
				wall = null;
				stuck = 0;
			}
			return;
		}
		stuck = 0;
	}

	private boolean marchOnVillage(ServerLevel level, int bearers) {
		if (village == null) return false;
		Vec3 to = Vec3.atBottomCenterOf(village).subtract(position()).multiply(1, 0, 1);
		if (to.length() < 4) return false;
		face(to, 4f);
		double speed = SPEED * Math.max(0.35, bearers / 4.0);
		if (!stepToward(level, to, speed)) return ++stuck <= STUCK_TICKS;
		stuck = 0;
		return true;
	}

	private boolean stepToward(ServerLevel level, Vec3 to, double speed) {
		Vec3 dir = to.normalize().scale(Math.min(speed, to.length()));
		Vec3[] tries = {dir, new Vec3(dir.x, 0, 0), new Vec3(0, 0, dir.z), dir.yRot(0.6f), dir.yRot(-0.6f), dir.yRot(1.2f),
			dir.yRot(-1.2f), dir.yRot(1.57f), dir.yRot(-1.57f)};
		if (dodge < 0) {
			for (int i = 3; i < tries.length; i += 2) {
				Vec3 t = tries[i];
				tries[i] = tries[i + 1];
				tries[i + 1] = t;
			}
		}
		for (int i = 0; i < tries.length; i++) {
			Vec3 step = tries[i];
			if (step.lengthSqr() < 1e-6) continue;
			Double ground = footing(level, getX() + step.x, getZ() + step.z);
			if (ground == null) continue;
			if (i >= 3 && i % 2 == 0) dodge = -dodge;
			setPos(getX() + step.x, getY() + Mth.clamp(ground - getY(), -0.5, 0.5), getZ() + step.z);
			if (tickCount % 12 == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.HOSTILE, 1.0f, 0.6f);
			}
			return true;
		}
		return false;
	}

	static Double groundNear(ServerLevel level, double x, double z, double y) {
		int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y + 0.01);
		for (int dy = 1; dy >= -3; dy--) {
			BlockPos feet = new BlockPos(bx, by + dy, bz);
			BlockState under = level.getBlockState(feet.below());
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				if (under.isFaceSturdy(level, feet.below(), Direction.UP)) return (double) feet.getY();
				var shape = under.getCollisionShape(level, feet.below());
				if (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.8 && shape.max(Direction.Axis.Y) <= 1.0) {
					return feet.getY() - 1 + shape.max(Direction.Axis.Y);
				}
			}
		}
		return null;
	}

	private void face(Vec3 dir, float maxTurn) {
		if (dir.lengthSqr() < 1e-4) return;
		float want = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
		setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -maxTurn, maxTurn));
	}

	private void strike(ServerLevel level) {
		Vec3 head = position().add(forward().scale(HEAD + 0.4)).add(0, 1.2, 0);
		BlockState struck = wall != null ? level.getBlockState(wall) : Blocks.OAK_PLANKS.defaultBlockState();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, struck), head.x, head.y, head.z, 30, 0.5, 0.6, 0.5, 0.25);
		level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 8, 0.4, 0.4, 0.4, 0.05);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.55f);
		level.playSound(null, head.x, head.y, head.z, net.warhorn.registry.WarhornSounds.IMPACT, SoundSource.HOSTILE, 1.4f, 0.9f);
		Cues.impact(level, head, 0.7f, 28);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
			new net.minecraft.world.phys.AABB(head, head).inflate(1.0), e -> e.isAlive() && !(e instanceof Raider))) {
			if (victim.hurtServer(level, damageSources().generic(), 8f)) {
				Vec3 push = forward().scale(1.4);
				victim.push(push.x, 0.45, push.z);
			}
		}
		if (wall == null) return;
		blows++;
		level.destroyBlockProgress(getId(), wall, Math.min(9, blows * 3));
		if (blows >= BLOWS_TO_BREACH) {
			breach(level);
		} else {
			crack(level);
		}
	}

	private void crack(ServerLevel level) {
		if (!Ruination.griefing(level)) return;
		Vec3 f = forward();
		Direction along = Direction.getApproximateNearest(f.x, 0, f.z);
		Direction across = along.getClockWise();
		Vec3 from = position().add(0, 1.0, 0);
		Ruination.begin();
		try {
			int knocked = 0;
			for (int attempt = 0; attempt < 12 && knocked < 1 + blows; attempt++) {
				BlockPos p = wall.relative(across, random.nextInt(3) - 1).above(random.nextInt(3) - 1);
				BlockState state = level.getBlockState(p);
				if (p.equals(wall) || !Ruination.isBuilding(state)) continue;
				float hardness = state.getDestroySpeed(level, p);
				if (hardness < 0 || hardness >= 10) continue;
				net.warhorn.siege.Debris.tearOut(level, p, state, from, 0.6, true);
				knocked++;
			}
		} finally {
			Ruination.end();
		}
	}

	private boolean retargetAnywhere(ServerLevel level) {
		BlockPos here = blockPosition();
		BlockPos centre = village != null ? village : here;
		BlockPos[] best = null;
		double bestDistance = Double.MAX_VALUE;
		int reach = 34;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > reach * reach) continue;
				for (int dy = -2; dy <= 3; dy++) {
					BlockPos pos = here.offset(dx, dy, dz);
					if (pos.distSqr(centre) > 56 * 56 || refused.contains(pos.asLong())) continue;
					BlockState state = level.getBlockState(pos);
					if (!Ruination.isBuilding(state) || !state.isSolidRender()) continue;
					float hardness = state.getDestroySpeed(level, pos);
					if (hardness < 0 || hardness >= 10) continue;
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos stand = pos.relative(d);
						Double ground = groundNear(level, stand.getX() + 0.5, stand.getZ() + 0.5, stand.getY());
						if (ground == null || pos.getY() - ground < 0 || pos.getY() - ground > 1.5) continue;
						double distance = Vec3.atCenterOf(stand).distanceToSqr(position());
						if (distance >= bestDistance) continue;
						if (SapperEntity.houseBlocks(level, pos) < 8 || !worthAttacking(level, pos, stand)) continue;
						BlockPos foot = BlockPos.containing(stand.getX(), ground, stand.getZ());
						if (!reachable(level, Vec3.atBottomCenterOf(foot).add(Vec3.atLowerCornerOf(d.getUnitVec3i()).scale(STOP_BACK)))) continue;
						bestDistance = distance;
						best = new BlockPos[] {pos, foot};
					}
				}
			}
		}
		if (best == null) return false;
		wall = best[0];
		standAt = best[1];
		blows = 0;
		closest = Double.MAX_VALUE;
		idle = 0;
		return true;
	}

	private boolean worthAttacking(ServerLevel level, BlockPos wall, BlockPos stand) {
		for (long l : refused) {
			if (BlockPos.of(l).distSqr(wall) < 4 * 4) return false;
		}
		for (BlockPos b : broken) {
			if (b.distSqr(wall) < 10 * 10) return false;
		}
		int dx = Integer.signum(stand.getX() - wall.getX()), dz = Integer.signum(stand.getZ() - wall.getZ());
		BlockPos out = stand.offset(dx * 2, 1, dz * 2);
		return level.canSeeSky(out) || level.canSeeSky(out.offset(dx, 0, dz));
	}

	private Double footing(ServerLevel level, double x, double z) {
		Double ground = groundNear(level, x, z, getY());
		return ground != null && Math.abs(ground - getY()) <= 1.01 ? ground : null;
	}

	private boolean reachable(ServerLevel level, Vec3 spot) {
		Vec3 to = spot.subtract(position()).multiply(1, 0, 1);
		double length = to.length();
		if (length < 0.25) return Math.abs(getY() - spot.y) <= 1;
		Vec3 step = to.scale(0.25 / length);
		double x = getX(), y = getY(), z = getZ();
		for (double done = 0; done < length; done += 0.25) {
			x += step.x;
			z += step.z;
			Double ground = groundNear(level, x, z, y);
			if (ground == null || Math.abs(ground - y) > 1) return false;
			y = ground;
		}
		return Math.abs(y - spot.y) <= 1;
	}

	private void breach(ServerLevel level) {
		level.destroyBlockProgress(getId(), wall, -1);
		Vec3 f = forward();
		Direction along = Direction.getApproximateNearest(f.x, 0, f.z);
		Direction across = along.getClockWise();
		Ruination.begin();
		try {
			Vec3 from = position().subtract(f.scale(2)).add(0, 1.0, 0);
			for (int a = 0; a <= 1; a++) {
				for (int s = -2; s <= 2; s++) {
					for (int dy = 0; dy <= 3; dy++) {
						BlockPos p = standAt.relative(along, 1 + a).relative(across, s).above(dy);
						int edge = (Math.abs(s) == 2 ? 1 : 0) + (dy == 3 ? 1 : 0);
						if (edge == 2 && random.nextFloat() < 0.75 || edge == 1 && random.nextFloat() < 0.3) continue;
						BlockState state = level.getBlockState(p);
						if (!Ruination.isBuilding(state)) continue;
						float hardness = state.getDestroySpeed(level, p);
						if (hardness < 0 || hardness >= 10) continue;
						net.warhorn.siege.Debris.tearOut(level, p, state, from, 0.85, random.nextFloat() < 0.7);
					}
				}
			}
		} finally {
			Ruination.end();
		}
		Vec3 hole = Vec3.atCenterOf(wall);
		level.playSound(null, hole.x, hole.y, hole.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.2f, 0.6f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, hole.x, hole.y, hole.z, 45, 1.4, 1.2, 1.4, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, hole.x, hole.y, hole.z, 3, 0.8, 0.6, 0.8, 0);
		Cues.impact(level, hole, 1.0f, 44);
		Cues.toast(level, hole, "warhorn.ram.breach");
		for (UUID uuid : crew) {
			if (level.getEntity(uuid) instanceof Raider r) {
				level.playSound(null, r.blockPosition(), r.getCelebrateSound(), SoundSource.HOSTILE, 1.6f, 0.9f);
			}
		}
		breaches++;
		broken.add(wall.immutable());
		blows = 0;
		wall = null;
		entityData.set(SWING, -1);
	}

	private boolean retarget(ServerLevel level) {
		BlockPos center = village != null ? village : blockPosition();
		BlockPos[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < 6; i++) {
			BlockPos[] found = SapperEntity.findWall(level, center, blockPosition(), random, 28);
			if (found == null || !worthAttacking(level, found[0], found[1])) continue;
			Vec3 out = Vec3.atCenterOf(found[1]).subtract(Vec3.atCenterOf(found[0])).multiply(1, 0, 1).normalize();
			if (!reachable(level, Vec3.atBottomCenterOf(found[1]).add(out.scale(STOP_BACK)))) continue;
			double d = found[1].distSqr(blockPosition());
			if (d < bestDistance) {
				bestDistance = d;
				best = found;
			}
		}
		if (best == null) return false;
		wall = best[0];
		standAt = best[1];
		blows = 0;
		closest = Double.MAX_VALUE;
		idle = 0;
		return true;
	}

	private void carry(ServerLevel level, Mob bearer, int i) {
		Vec3 at = slot(i);
		Double ground = groundNear(level, at.x, at.z, getY());
		double y = ground == null ? getY() : Math.max(ground, bearer.getY() - 0.5);
		bearer.setNoAi(true);
		bearer.setPos(at.x, y, at.z);
		bearer.setYRot(getYRot());
		bearer.setYHeadRot(getYRot());
		bearer.setYBodyRot(getYRot());
	}

	private void disband(ServerLevel level) {
		for (UUID uuid : crew) {
			if (level.getEntity(uuid) instanceof Mob bearer && bearer.isAlive()) {
				bearer.setNoAi(false);
				bearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_AXE));
			}
		}
		crew.clear();
		fall(level);
	}

	private void fall(ServerLevel level) {
		if (wall != null) level.destroyBlockProgress(getId(), wall, -1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_LOG.defaultBlockState()),
			getX(), getY() + 0.8, getZ(), 40, 1.2, 0.3, 1.2, 0.15);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 0.5f);
		discard();
	}

	public Siege siege(ServerLevel level) {
		return SiegeManager.byId(level, siegeId);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("WarhornSiege", siegeId);
		output.store("Crew", UUIDUtil.CODEC.listOf(), crew);
		if (village != null) output.putLong("Village", village.asLong());
		output.putInt("Breaches", breaches);
		output.putBoolean("Held", held);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		siegeId = input.getIntOr("WarhornSiege", -1);
		crew.clear();
		input.read("Crew", UUIDUtil.CODEC.listOf()).ifPresent(crew::addAll);
		input.getLong("Village").ifPresent(l -> village = BlockPos.of(l));
		breaches = input.getIntOr("Breaches", 0);
		held = false;
	}
}
