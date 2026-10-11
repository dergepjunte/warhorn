package net.warhorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.siege.Ruination;
import net.warhorn.siege.SiegeManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class SapperEntity extends AbstractIllager {
	public enum State {
		CARRY(0),
		PLANT(30),
		FLEE(90),
		BARE(0);

		public final int ticks;

		State(int ticks) {
			this.ticks = ticks;
		}
	}

	public static final int SET_DOWN = 12;
	public static final int LIGHT = 22;
	private static final int GIVE_UP_TICKS = 20 * 30;
	private static final int NEAR_SCAN = 10;

	private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SapperEntity.class, EntityDataSerializers.BYTE);

	private int stateTicks;
	private BlockPos center;
	private BlockPos wall;
	private BlockPos standAt;
	private Vec3 fleeFrom = Vec3.ZERO;

	public SapperEntity(EntityType<? extends SapperEntity> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 18.0)
			.add(Attributes.MOVEMENT_SPEED, 0.37)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(STATE, (byte) 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (STATE.equals(key)) stateTicks = 0;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PlantGoal());
		goalSelector.addGoal(2, new FleeGoal());
		goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false) {
			@Override
			public boolean canUse() {
				return state() == State.BARE && super.canUse();
			}
		});
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.7));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public void targetWall(BlockPos wall, BlockPos stand) {
		this.wall = wall;
		this.standAt = stand;
		if (center == null) center = wall;
	}

	public void aimAt(BlockPos village) {
		this.center = village;
	}

	public State state() {
		return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
	}

	public boolean carrying() {
		State s = state();
		return s == State.CARRY || s == State.PLANT && stateTicks < LIGHT;
	}

	public float stateProgress(float partial) {
		State s = state();
		return s.ticks <= 0 ? 0 : Mth.clamp((stateTicks + partial) / s.ticks, 0, 1);
	}

	public int stateTicks() {
		return stateTicks;
	}

	private void enter(State s) {
		entityData.set(STATE, (byte) s.ordinal());
		stateTicks = 0;
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(state() == State.BARE ? target : null);
	}

	@Override
	public void tick() {
		super.tick();
		stateTicks++;
		if (level().isClientSide()) {
			if (carrying() && tickCount % 3 == 0) {
				Vec3 hand = handPosition();
				level().addParticle(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 0, 0.02, 0);
				if (random.nextInt(4) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, hand.x, hand.y, hand.z, 0, 0.01, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (state() == State.PLANT) {
			getNavigation().stop();
			if (standAt != null && wall != null) getLookControl().setLookAt(Vec3.atCenterOf(wall));
			if (stateTicks == SET_DOWN) {
				level.playSound(null, blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.HOSTILE, 1.2f, 0.7f);
			}
			if (stateTicks == LIGHT) plantKeg(level);
			if (stateTicks >= State.PLANT.ticks) enter(State.FLEE);
		} else if (state() == State.FLEE && stateTicks >= State.FLEE.ticks) {
			enter(State.BARE);
		}
	}

	public Vec3 handPosition() {
		Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		return position().add(forward.scale(0.55)).add(right.scale(0.42)).add(0, 1.05, 0);
	}

	private void plantKeg(ServerLevel level) {
		Vec3 at = standAt != null ? Vec3.atBottomCenterOf(standAt) : position().add(Vec3.directionFromRotation(0, yBodyRot).scale(0.7));
		Vec3 face = wall != null ? Vec3.atCenterOf(wall).subtract(at).multiply(1, 0, 1) : Vec3.directionFromRotation(0, yBodyRot);
		Vec3 keg = wall != null ? at.add(face.normalize().scale(0.18)) : at;
		PowderKegEntity.plant(level, keg, (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f, false);
		level.playSound(null, blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.2f, 0.9f);
		fleeFrom = keg;
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && carrying()) {
			PowderKegEntity.plant(level, position().add(0, 0.2, 0), getYRot(), true);
		}
		super.die(source);
	}

	public static BlockPos[] findWall(ServerLevel level, BlockPos center, BlockPos near, net.minecraft.util.RandomSource random, int reach) {
		BlockPos[] best = null;
		int bestScore = 0;
		List<int[]> columns = new ArrayList<>();
		for (int attempt = 0; attempt < 40; attempt++) {
			columns.add(new int[] {center.getX() + random.nextInt(2 * reach + 1) - reach, center.getZ() + random.nextInt(2 * reach + 1) - reach});
		}
		for (int dx = -NEAR_SCAN; dx <= NEAR_SCAN; dx++) {
			for (int dz = -NEAR_SCAN; dz <= NEAR_SCAN; dz++) columns.add(new int[] {near.getX() + dx, near.getZ() + dz});
		}
		for (int[] column : columns) {
			int x = column[0];
			int z = column[1];
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			for (int y = top; y > top - 10; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(pos);
				if (!Ruination.isBuilding(state) || !state.isSolidRender()) continue;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos stand = pos.relative(d);
					if (!level.getBlockState(stand).isAir() || !level.getBlockState(stand.above()).isAir()) continue;
					if (!level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP)) continue;
					if (!openAbove(level, stand)) continue;
					int score = houseBlocks(level, pos);
					if (score < 8) continue;
					if (Math.abs(stand.getY() - near.getY()) > 6) score -= 6;
					score -= (int) Math.sqrt(stand.distSqr(near)) / 3;
					if (score > bestScore) {
						bestScore = score;
						best = new BlockPos[] {pos, stand};
					}
				}
			}
		}
		return best;
	}

	private static boolean openAbove(ServerLevel level, BlockPos stand) {
		for (int dy = 2; dy <= 4; dy++) {
			if (!level.getBlockState(stand.above(dy)).isAir()) return false;
		}
		return true;
	}

	static int houseBlocks(ServerLevel level, BlockPos pos) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
			if (Ruination.isBuilding(level.getBlockState(p))) n++;
		}
		return n;
	}

	private final class PlantGoal extends Goal {
		private int ticks;

		PlantGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (state() != State.CARRY || isNoAi() || !(level() instanceof ServerLevel level)) return false;
			if (center == null) {
				var siege = SiegeManager.find(level, blockPosition(), 140);
				center = siege != null ? siege.center() : blockPosition();
			}
			if (wall == null || tickCount % 40 == 0 && !Ruination.isBuilding(level.getBlockState(wall))) {
				BlockPos[] found = findWall(level, center, blockPosition(), getRandom(), 28);
				if (found == null) return false;
				wall = found[0];
				standAt = found[1];
			}
			return true;
		}

		@Override
		public void start() {
			ticks = 0;
			move();
		}

		@Override
		public boolean canContinueToUse() {
			return state() == State.CARRY && wall != null && ticks < GIVE_UP_TICKS;
		}

		@Override
		public void stop() {
			if (ticks >= GIVE_UP_TICKS) wall = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ticks++;
			Vec3 spot = Vec3.atBottomCenterOf(standAt);
			if (position().multiply(1, 0, 1).distanceTo(spot.multiply(1, 0, 1)) < 1.3 && Math.abs(getY() - spot.y) < 1.5) {
				getNavigation().stop();
				enter(State.PLANT);
				return;
			}
			if (ticks % 20 == 0 || getNavigation().isDone()) move();
		}

		private void move() {
			getNavigation().moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 1.2);
		}
	}

	private final class FleeGoal extends Goal {
		FleeGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return state() == State.FLEE && !isNoAi();
		}

		@Override
		public void tick() {
			if (stateTicks % 10 != 1) return;
			Vec3 away = position().subtract(fleeFrom).multiply(1, 0, 1);
			if (away.lengthSqr() < 1e-3) away = Vec3.directionFromRotation(0, getYRot() + 180);
			Vec3 to = position().add(away.normalize().scale(10));
			getNavigation().moveTo(to.x, to.y, to.z, 1.35);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("SapperState", state().ordinal());
		if (center != null) output.putLong("SapperVillage", center.asLong());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int s = input.getIntOr("SapperState", 0);
		entityData.set(STATE, (byte) (s == State.PLANT.ordinal() ? State.CARRY.ordinal() : s));
		input.getLong("SapperVillage").ifPresent(l -> center = BlockPos.of(l));
	}

	@Override
	public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
	}

	@Override
	public SoundEvent getCelebrateSound() {
		return SoundEvents.PILLAGER_CELEBRATE;
	}

	@Override
	public IllagerArmPose getArmPose() {
		return state() == State.BARE && isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PILLAGER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.25f + (random.nextFloat() - 0.5f) * 0.1f;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
