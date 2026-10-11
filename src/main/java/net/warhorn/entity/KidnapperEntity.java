package net.warhorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.siege.Hints;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;

import java.util.EnumSet;
import java.util.UUID;

public class KidnapperEntity extends AbstractIllager {
	public enum State {
		HUNT(0), GRAB(20), CARRY(0);

		public final int ticks;

		State(int ticks) {
			this.ticks = ticks;
		}
	}

	public static final String CARRIED_TAG = "warhorn_kidnapped";
	public static final int BAGGED = 11;
	public static final double ESCAPE_RADIUS = 54;
	private static final double BURDENED = 0.82;

	private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(KidnapperEntity.class, EntityDataSerializers.BYTE);

	private int stateTicks;
	private BlockPos village;
	private BlockPos forest;
	private UUID victim;
	private boolean bagged;

	public KidnapperEntity(EntityType<? extends KidnapperEntity> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.34)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
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
		goalSelector.addGoal(1, new GrabGoal());
		goalSelector.addGoal(2, new RunForTheTreesGoal());
		goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5f, 1.0, 1.2) {
			@Override
			public boolean canUse() {
				return state() == State.HUNT && super.canUse();
			}
		});
		goalSelector.addGoal(4, new HuntGoal());
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8));
	}

	public void sendAgainst(BlockPos village, BlockPos forest) {
		this.village = village;
		this.forest = forest;
	}

	public State state() {
		return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
	}

	public int stateTicks() {
		return stateTicks;
	}

	public boolean loaded() {
		State s = state();
		return s == State.CARRY || s == State.GRAB && stateTicks > BAGGED;
	}

	private void enter(State s) {
		entityData.set(STATE, (byte) s.ordinal());
		stateTicks = 0;
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(null);
	}

	@Override
	public void tick() {
		super.tick();
		stateTicks++;
		if (level().isClientSide()) return;
		ServerLevel level = (ServerLevel) level();
		if (village == null) {
			Siege siege = SiegeManager.find(level, blockPosition(), 140);
			village = siege != null ? siege.center() : blockPosition();
			forest = siege != null ? siege.staging() : null;
		}
		if (state() == State.GRAB) {
			getNavigation().stop();
			if (stateTicks == BAGGED && !bag(level, carried(level))) {
				victim = null;
				enter(State.HUNT);
			} else if (stateTicks >= State.GRAB.ticks) {
				enter(State.CARRY);
			}
		}
		if (!bagged) return;
		AbstractVillager carried = carried(level);
		if (carried == null) {
			victim = null;
			bagged = false;
			enter(State.HUNT);
			return;
		}
		holdInSack(carried);
		if (tickCount % 45 == 0) {
			level.playSound(null, getX(), getY() + 1.5, getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 0.6f);
		}
		if (tickCount % 3 == 0) {
			Vec3 sack = position().add(Vec3.directionFromRotation(0, yBodyRot - 90).scale(0.35)).add(0, 1.9, 0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.HAY_BLOCK.defaultBlockState()),
				sack.x, sack.y, sack.z, 1, 0.25, 0.2, 0.25, 0);
		}
		if (state() == State.CARRY && horizontalDistanceTo(village) > ESCAPE_RADIUS) escape(level, carried);
	}

	private double horizontalDistanceTo(BlockPos pos) {
		return Math.sqrt(Mth.square(getX() - pos.getX() - 0.5) + Mth.square(getZ() - pos.getZ() - 0.5));
	}

	private AbstractVillager carried(ServerLevel level) {
		return victim != null && level.getEntity(victim) instanceof AbstractVillager v && v.isAlive() ? v : null;
	}

	private boolean bag(ServerLevel level, AbstractVillager villager) {
		if (villager == null || villager.distanceTo(this) > 3 || villager.entityTags().contains(CARRIED_TAG)) return false;
		villager.addTag(CARRIED_TAG);
		villager.setInvisible(true);
		villager.setPermanentlyInvulnerable(true);
		villager.setNoAi(true);
		villager.setNoGravity(true);
		villager.setSilent(true);
		villager.noPhysics = true;
		villager.stopRiding();
		bagged = true;
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.9, villager.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()),
			villager.getX(), villager.getY() + 0.8, villager.getZ(), 16, 0.3, 0.4, 0.3, 0.1);
		holdInSack(villager);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0f, 0.8f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 1.2f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.2f, 1.35f);
		Hints.offerNear(level, position(), 48, Hints.KIDNAP);
		return true;
	}

	private void holdInSack(AbstractVillager villager) {
		villager.setPos(getX(), getY() - 2.5, getZ());
		villager.setDeltaMovement(Vec3.ZERO);
		villager.fallDistance = 0;
	}

	private void free(ServerLevel level, AbstractVillager villager) {
		villager.removeTag(CARRIED_TAG);
		villager.setInvisible(false);
		villager.setPermanentlyInvulnerable(false);
		villager.setNoAi(false);
		villager.setNoGravity(false);
		villager.setSilent(false);
		villager.noPhysics = false;
		villager.setPos(getX(), getY(), getZ());
		villager.push(0, 0.3, 0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 14, 0.4, 0.5, 0.4, 0);
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.2, getZ(), 12, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1.0f);
		Cues.toast(level, villager.position(), "warhorn.kidnap.freed");
		victim = null;
		bagged = false;
	}

	private void escape(ServerLevel level, AbstractVillager villager) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_DEATH, SoundSource.NEUTRAL, 1.0f, 0.6f);
		Cues.toast(level, Vec3.atCenterOf(village), "warhorn.kidnap.lost");
		villager.discard();
		discard();
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level) {
			AbstractVillager carried = bagged ? carried(level) : null;
			if (carried != null) free(level, carried);
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (bagged && reason.shouldDestroy() && level() instanceof ServerLevel level && carried(level) instanceof AbstractVillager v) {
			free(level, v);
		}
		super.remove(reason);
	}

	private final class HuntGoal extends Goal {
		private AbstractVillager prey;

		HuntGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (state() != State.HUNT || isNoAi() || random.nextInt(4) != 0) return false;
			prey = level().getEntitiesOfClass(AbstractVillager.class, getBoundingBox().inflate(40),
					v -> v.isAlive() && !v.entityTags().contains(CARRIED_TAG))
				.stream().min(java.util.Comparator.comparingDouble(KidnapperEntity.this::distanceToSqr)).orElse(null);
			return prey != null;
		}

		@Override
		public boolean canContinueToUse() {
			return state() == State.HUNT && prey != null && prey.isAlive() && !prey.entityTags().contains(CARRIED_TAG);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (prey == null || state() != State.HUNT) return;
			getLookControl().setLookAt(prey);
			if (distanceToSqr(prey) < 1.7 * 1.7) {
				victim = prey.getUUID();
				prey.getNavigation().stop();
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.PILLAGER_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.7f);
				enter(State.GRAB);
				prey = null;
				return;
			}
			if (tickCount % 8 == 0) getNavigation().moveTo(prey, 1.15);
		}
	}

	private final class GrabGoal extends Goal {
		GrabGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return state() == State.GRAB;
		}
	}

	private final class RunForTheTreesGoal extends Goal {
		RunForTheTreesGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return state() == State.CARRY && !isNoAi();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (stateTicks % 10 != 1 && !getNavigation().isDone()) return;
			Vec3 from = Vec3.atBottomCenterOf(village);
			Vec3 away = forest != null ? Vec3.atBottomCenterOf(forest).subtract(from) : position().subtract(from);
			if (away.horizontalDistanceSqr() < 1e-3) away = Vec3.directionFromRotation(0, getYRot());
			Vec3 goal = from.add(away.multiply(1, 0, 1).normalize().scale(ESCAPE_RADIUS + 8));
			Vec3 leg = position().add(goal.subtract(position()).multiply(1, 0, 1).normalize().scale(12));
			if (!getNavigation().moveTo(leg.x, leg.y, leg.z, BURDENED)) {
				Vec3 any = LandRandomPos.getPosTowards(KidnapperEntity.this, 14, 6, goal);
				if (any != null) getNavigation().moveTo(any.x, any.y, any.z, BURDENED);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (village != null) output.putLong("KidnapVillage", village.asLong());
		if (forest != null) output.putLong("KidnapForest", forest.asLong());
		if (victim != null && bagged) output.putString("KidnapVictim", victim.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getLong("KidnapVillage").ifPresent(l -> village = BlockPos.of(l));
		input.getLong("KidnapForest").ifPresent(l -> forest = BlockPos.of(l));
		input.getString("KidnapVictim").ifPresent(s -> {
			victim = UUID.fromString(s);
			bagged = true;
			entityData.set(STATE, (byte) State.CARRY.ordinal());
		});
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
		return IllagerArmPose.NEUTRAL;
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
		return 0.82f + (random.nextFloat() - 0.5f) * 0.08f;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
