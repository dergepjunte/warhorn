package net.warhorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.Ruination;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;
import net.warhorn.siege.SiegeSpawner;
import net.warhorn.siege.SiegeStage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CatapultEntity extends Entity {
	public enum Phase {
		IDLE(0),
		WIND(80),
		LIGHT(24),
		LOOSE(16),
		REST(50);

		public final int ticks;

		Phase(int ticks) {
			this.ticks = ticks;
		}
	}

	public static final String CREW_TAG = "warhorn_catapult_crew";
	public static final int LAUNCH = 2;
	public static final float SCALE = 1.75f;
	private static final int MAX_HEALTH = 24;
	private static final double CREW_ALERT = 7;
	private static final double CREW_CALM = 16;
	private static final int TARGET_REACH = 30;
	private static final double[][] SLOTS = {{-1.45, -1.5}, {1.45, -1.5}};

	private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(CatapultEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> HEALTH = SynchedEntityData.defineId(CatapultEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SHAKE = SynchedEntityData.defineId(CatapultEntity.class, EntityDataSerializers.INT);

	private final List<UUID> crew = new ArrayList<>();
	private int siegeId = -1;
	private BlockPos village;
	private BlockPos target;
	private boolean fixedTarget;
	private int phaseTicks;
	private boolean held;
	private int calm;
	public int phaseClock;
	public int shakeClock = 100;

	public CatapultEntity(EntityType<? extends CatapultEntity> type, Level level) {
		super(type, level);
	}

	public static List<Mob> spawn(ServerLevel level, Siege siege, BlockPos village, Vec3 at, float yaw, boolean frozen) {
		List<Mob> men = new ArrayList<>();
		CatapultEntity catapult = WarhornEntities.CATAPULT.create(level, EntitySpawnReason.EVENT);
		if (catapult == null) return men;
		catapult.siegeId = siege != null ? siege.id() : -1;
		catapult.village = village;
		catapult.held = frozen;
		catapult.snapTo(at.x, at.y, at.z, yaw, 0);
		level.addFreshEntity(catapult);
		for (int i = 0; i < SLOTS.length; i++) {
			Mob man = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
			if (man == null) continue;
			if (siege != null) {
				SiegeSpawner.prepare(level, siege, man, catapult.slot(i), yaw, true);
			} else {
				man.snapTo(catapult.slot(i), yaw, 0);
				man.finalizeSpawn(level, level.getCurrentDifficultyAt(catapult.blockPosition()), EntitySpawnReason.EVENT, null);
				man.setPersistenceRequired();
				man.setNoAi(true);
			}
			man.addTag(CREW_TAG);
			man.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			man.setAggressive(true);
			level.addFreshEntity(man);
			if (siege != null) siege.enlist(man.getUUID());
			catapult.crew.add(man.getUUID());
			men.add(man);
		}
		return men;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PHASE, (byte) 0);
		builder.define(HEALTH, MAX_HEALTH);
		builder.define(SHAKE, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (PHASE.equals(key)) phaseClock = 0;
		if (SHAKE.equals(key)) shakeClock = 0;
	}

	public Phase phase() {
		return Phase.values()[Mth.clamp(entityData.get(PHASE), 0, Phase.values().length - 1)];
	}

	public float integrity() {
		return entityData.get(HEALTH) / (float) MAX_HEALTH;
	}

	public void release() {
		held = false;
	}

	public boolean belongsTo(int siege) {
		return siegeId == siege;
	}

	public List<UUID> crew() {
		return crew;
	}

	public void aimAt(BlockPos target) {
		this.target = target;
		this.fixedTarget = true;
	}

	private void enter(Phase p) {
		entityData.set(PHASE, (byte) p.ordinal());
		phaseTicks = 0;
	}

	private Vec3 forward() {
		return Vec3.directionFromRotation(0, getYRot());
	}

	private Vec3 slot(int i) {
		Vec3 f = forward();
		Vec3 side = new Vec3(-f.z, 0, f.x);
		return position().add(side.scale(SLOTS[i][0])).add(f.scale(SLOTS[i][1]));
	}

	public Vec3 cup() {
		return position().add(forward().scale(0.67 * SCALE)).add(0, 1.75 * SCALE, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			phaseClock++;
			shakeClock++;
			if (phase() == Phase.LIGHT) flames();
			Vec3 b = brazier();
			if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, b.x + (random.nextDouble() - 0.5) * 0.3, b.y, b.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.015, 0);
			if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.SMOKE, b.x, b.y + 0.2, b.z, 0, 0.03, 0);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		List<Mob> men = new ArrayList<>();
		for (UUID uuid : crew) {
			if (level.getEntity(uuid) instanceof Mob m && m.isAlive()) men.add(m);
		}
		boolean manned = !men.isEmpty() && !held && crewAtWork(level, men);
		phaseTicks++;
		Phase p = phase();
		if (!manned) {
			if (p != Phase.IDLE && p != Phase.WIND) enter(Phase.IDLE);
			return;
		}
		switch (p) {
			case IDLE -> {
				if (pickTarget(level)) enter(Phase.WIND);
			}
			case WIND -> {
				if (target != null) face(Vec3.atCenterOf(target).subtract(position()), 0.6f);
				if (phaseTicks % 8 == 4) {
					level.playSound(null, getX(), getY() + 1, getZ(), WarhornSounds.CATAPULT_WINCH, SoundSource.HOSTILE, 1.0f,
						0.85f + phaseTicks / (float) Phase.WIND.ticks * 0.3f);
				}
				if (phaseTicks >= p.ticks) enter(Phase.LIGHT);
			}
			case LIGHT -> {
				if (phaseTicks == 1) {
					Vec3 c = cupLoaded();
					level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 0.7f);
				}
				if (phaseTicks >= p.ticks) enter(Phase.LOOSE);
			}
			case LOOSE -> {
				if (phaseTicks == LAUNCH) loose(level);
				if (phaseTicks >= p.ticks) enter(Phase.REST);
			}
			case REST -> {
				if (phaseTicks >= p.ticks) enter(pickTarget(level) ? Phase.WIND : Phase.IDLE);
			}
		}
	}

	private Vec3 cupLoaded() {
		return position().subtract(forward().scale(1.2 * SCALE)).add(0, 1.12 * SCALE, 0);
	}

	public Vec3 brazier() {
		Vec3 f = forward();
		Vec3 left = new Vec3(f.z, 0, -f.x);
		return position().add(left.scale(10.0 / 16 * SCALE)).subtract(f.scale(21.0 / 16 * SCALE)).add(0, (24 - 14.0) / 16 * SCALE, 0);
	}

	private void flames() {
		Vec3 c = cupLoaded();
		if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.FLAME, c.x, c.y + 0.35, c.z, 0, 0.02, 0);
		if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, c.x, c.y + 0.5, c.z, 0, 0.04, 0);
	}

	private boolean crewAtWork(ServerLevel level, List<Mob> men) {
		boolean threat = false;
		for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(CREW_ALERT),
			p -> !p.isSpectator() && !p.isCreative() && p.isAlive())) {
			threat = true;
		}
		boolean hurt = men.stream().anyMatch(m -> m.getLastHurtByMob() instanceof Player && m.tickCount - m.getLastHurtByMobTimestamp() < 100);
		if (threat || hurt) {
			calm = 0;
			for (Mob m : men) {
				if (m.isNoAi()) {
					m.setNoAi(false);
					m.setAggressive(false);
					m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				}
			}
			return false;
		}
		boolean near = !level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(CREW_CALM),
			p -> !p.isSpectator() && !p.isCreative()).isEmpty();
		calm = near ? 0 : calm + 1;
		boolean allAtPosts = true;
		for (int i = 0; i < crew.size(); i++) {
			if (!(level.getEntity(crew.get(i)) instanceof Mob m) || !m.isAlive()) continue;
			if (!m.isNoAi() && calm < 60) {
				allAtPosts = false;
				continue;
			}
			Vec3 at = slot(i);
			if (!m.isNoAi() && m.position().distanceToSqr(at) > 1.2 * 1.2) {
				if (m.tickCount % 20 == 0) m.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
				allAtPosts = false;
				continue;
			}
			man(level, m, at);
		}
		return allAtPosts;
	}

	private void man(ServerLevel level, Mob m, Vec3 at) {
		if (!m.isNoAi()) {
			m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			m.setTarget(null);
		}
		m.setNoAi(true);
		m.setAggressive(true);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
		m.setPos(at.x, Math.abs(ground - getY()) < 2 ? ground : getY(), at.z);
		int i = crew.indexOf(m.getUUID());
		float yaw = getYRot() + (i >= 0 && SLOTS[i][0] < 0 ? 90f : -90f);
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.setXRot(phase() == Phase.WIND ? 25f + 10f * Mth.sin(phaseTicks * 0.4f) : 0f);
	}

	private void face(Vec3 dir, float maxTurn) {
		if (dir.horizontalDistanceSqr() < 1e-4) return;
		float want = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
		setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -maxTurn, maxTurn));
	}

	private boolean pickTarget(ServerLevel level) {
		Siege siege = siegeId >= 0 ? SiegeManager.byId(level, siegeId) : null;
		if (siegeId >= 0 && (siege == null || siege.stage() != SiegeStage.ACT)) return false;
		if (fixedTarget) return true;
		BlockPos center = village != null ? village : blockPosition().offset(BlockPos.containing(forward().scale(40)));
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 30; i++) {
			int x = center.getX() + random.nextInt(TARGET_REACH * 2 + 1) - TARGET_REACH;
			int z = center.getZ() + random.nextInt(TARGET_REACH * 2 + 1) - TARGET_REACH;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			BlockPos p = new BlockPos(x, top, z);
			BlockState state = level.getBlockState(p);
			if (!Ruination.isBuilding(state)) continue;
			int score = (state.ignitedByLava() ? 20 : 0) + Math.min(10, top - center.getY()) - (int) Math.sqrt(p.distSqr(center)) / 4;
			if (score > bestScore) {
				bestScore = score;
				best = p;
			}
		}
		if (best == null && target == null) return false;
		if (best != null) target = best;
		return true;
	}

	private void loose(ServerLevel level) {
		Vec3 from = cup();
		level.playSound(null, getX(), getY() + 1.5, getZ(), WarhornSounds.CATAPULT_LOOSE, SoundSource.HOSTILE, 2.2f,
			0.9f + random.nextFloat() * 0.15f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()),
			from.x, from.y - 0.4, from.z, 12, 0.5, 0.3, 0.5, 0.1);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 6, 0.3, 0.2, 0.3, 0.02);
		Cues.impact(level, position(), 0.35f, 16);
		if (target == null) return;
		Vec3 to = Vec3.atBottomCenterOf(target).add(0, 1, 0);
		FirePotEntity.launch(level, from, to, siegeId);
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return !(source.getEntity() instanceof Raider);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || !(source.getEntity() instanceof Player player)) return false;
		int left = entityData.get(HEALTH) - Math.max(1, Math.round(damage));
		entityData.set(HEALTH, left);
		entityData.set(SHAKE, entityData.get(SHAKE) + 1);
		Vec3 hit = position().add(0, 1.2, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()),
			hit.x, hit.y, hit.z, 14, 0.6, 0.5, 0.6, 0.15);
		level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.9f, 0.8f + random.nextFloat() * 0.2f);
		if (player instanceof ServerPlayer sp) net.warhorn.siege.Hints.offer(sp, net.warhorn.siege.Hints.CATAPULT_SMASH);
		if (left <= 0) wreck(level);
		return true;
	}

	private void wreck(ServerLevel level) {
		Vec3 c = position().add(0, 1, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()),
			c.x, c.y, c.z, 70, 1.4, 0.9, 1.4, 0.25);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_LOG.defaultBlockState()),
			c.x, c.y, c.z, 30, 1.0, 0.6, 1.0, 0.2);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 16, 1.0, 0.5, 1.0, 0.04);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.55f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.6f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, WarhornSounds.IMPACT, SoundSource.HOSTILE, 1.2f, 1.2f);
		Cues.impact(level, c, 0.6f, 24);
		Cues.toast(level, c, "warhorn.catapult.wrecked");
		for (UUID uuid : crew) {
			if (level.getEntity(uuid) instanceof Mob m && m.isAlive()) {
				m.setNoAi(false);
				m.setAggressive(false);
				m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			}
		}
		crew.clear();
		level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, c.x, c.y, c.z, new ItemStack(Items.STICK, 4)));
		discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("WarhornSiege", siegeId);
		output.store("Crew", UUIDUtil.CODEC.listOf(), crew);
		if (village != null) output.putLong("Village", village.asLong());
		output.putInt("Health", entityData.get(HEALTH));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		siegeId = input.getIntOr("WarhornSiege", -1);
		crew.clear();
		input.read("Crew", UUIDUtil.CODEC.listOf()).ifPresent(crew::addAll);
		input.getLong("Village").ifPresent(l -> village = BlockPos.of(l));
		entityData.set(HEALTH, input.getIntOr("Health", MAX_HEALTH));
		held = false;
	}
}
