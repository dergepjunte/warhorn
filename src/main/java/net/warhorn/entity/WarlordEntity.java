package net.warhorn.entity;

import net.warhorn.siege.Ruination;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.cinematic.SlowMotion;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.Hints;
import net.warhorn.siege.SiegeDirector;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WarlordEntity extends AbstractIllager {
	public enum Action {
		NONE(0), SLAM_WINDUP(26), SLAM(12), CHARGE_WINDUP(18), CHARGE(24), ROAR(34), STUNNED(56),
		SWING(16), THROW_WINDUP(18), UNARMED(160), CATCH(12),
		LEAP_WINDUP(22), LEAP(18), LEAP_RECOVER(34), BANNER(34), LAST_STAND(80);

		public final int ticks;

		Action(int ticks) {
			this.ticks = ticks;
		}
	}

	public static final int[] SWING_TICKS = {15, 14, 22};
	public static final int[] SWING_HIT = {8, 7, 13};
	public static final int LAST_STAND_BURST = 40;
	public static final int DEATH_TICKS = 56;
	private static final double REACH = 3.9;

	private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> COMBO = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LAST_STAND = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> POSE_HOLD = SynchedEntityData.defineId(WarlordEntity.class, EntityDataSerializers.INT);

	private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(),
		Component.translatable("warhorn.warlord.name"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private int actionTicks;
	private int cooldown = 60;
	private int swingRest;
	private Vec3 chargeDirection = Vec3.ZERO;
	private Vec3 leapFrom = Vec3.ZERO;
	private Vec3 leapTo = Vec3.ZERO;
	private Action lastAbility = Action.NONE;
	private boolean bannerRaised;
	private final Set<UUID> struck = new HashSet<>();
	private int siegeId = -1;

	public WarlordEntity(EntityType<? extends WarlordEntity> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		bossBar.setDarkenScreen(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 320.0)
			.add(Attributes.ARMOR, 14.0)
			.add(Attributes.ARMOR_TOUGHNESS, 6.0)
			.add(Attributes.ATTACK_DAMAGE, 13.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.4)
			.add(Attributes.MOVEMENT_SPEED, 0.31)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.SCALE, 1.4)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, (byte) 0);
		builder.define(ENRAGED, false);
		builder.define(COMBO, (byte) 0);
		builder.define(ARMED, true);
		builder.define(LAST_STAND, false);
		builder.define(POSE_HOLD, -1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ACTION.equals(key) || COMBO.equals(key)) actionTicks = 0;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new HoldForActionGoal());
		goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15, true));
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.7));
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
		targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	private final class HoldForActionGoal extends Goal {
		HoldForActionGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			Action a = action();
			return a != Action.NONE && a != Action.CHARGE && a != Action.LEAP;
		}

		@Override
		public void start() {
			getNavigation().stop();
		}
	}

	public void equip() {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
		setDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	public void bindToSiege(int siegeId, int defenders) {
		this.siegeId = siegeId;
		var health = getAttribute(Attributes.MAX_HEALTH);
		if (health != null) health.setBaseValue(320.0 + 160.0 * Math.max(0, defenders - 1));
		setHealth(getMaxHealth());
	}

	public int siegeId() {
		return siegeId;
	}

	public Action action() {
		byte id = entityData.get(ACTION);
		return id >= 0 && id < Action.values().length ? Action.values()[id] : Action.NONE;
	}

	public boolean enraged() {
		return entityData.get(ENRAGED);
	}

	public int combo() {
		return Mth.clamp(entityData.get(COMBO), 0, 2);
	}

	public boolean armed() {
		return entityData.get(ARMED);
	}

	public boolean lastStand() {
		return entityData.get(LAST_STAND);
	}

	public int actionTicks() {
		return actionTicks;
	}

	private int duration(Action a) {
		return a == Action.SWING ? SWING_TICKS[combo()] : a.ticks;
	}

	public float actionProgress(float partialTick) {
		Action a = action();
		if (a == Action.NONE) return 0;
		int hold = entityData.get(POSE_HOLD);
		if (hold >= 0) return Mth.clamp((float) hold / duration(a), 0, 1);
		return Mth.clamp((actionTicks + partialTick) / duration(a), 0, 1);
	}

	public void holdPose(Action a, int combo, int tick, boolean lastStand) {
		entityData.set(COMBO, (byte) combo);
		entityData.set(ACTION, (byte) a.ordinal());
		entityData.set(POSE_HOLD, tick);
		entityData.set(ARMED, a != Action.UNARMED);
		entityData.set(LAST_STAND, lastStand);
	}

	private void begin(Action next) {
		entityData.set(ACTION, (byte) next.ordinal());
		actionTicks = 0;
		struck.clear();
	}

	@Override
	public void tick() {
		super.tick();
		if (action() != Action.NONE) actionTicks++;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossBar.setProgress(getHealth() / getMaxHealth());
		if (isNoAi()) return;
		switch (action()) {
			case NONE -> {
				if (swingRest > 0) swingRest--;
				chooseAction(level);
			}
			case SLAM_WINDUP -> slamWindup(level);
			case SLAM -> slam(level);
			case CHARGE_WINDUP -> chargeWindup(level);
			case CHARGE -> charge(level);
			case ROAR -> roar(level);
			case STUNNED -> stunned(level);
			case SWING -> swing(level);
			case THROW_WINDUP -> throwWindup(level);
			case UNARMED -> unarmed(level);
			case CATCH -> {
				if (actionTicks >= Action.CATCH.ticks) finish();
			}
			case LEAP_WINDUP -> leapWindup(level);
			case LEAP -> leap(level);
			case LEAP_RECOVER -> leapRecover(level);
			case BANNER -> banner(level);
			case LAST_STAND -> lastStandTick(level);
		}
	}

	private void chooseAction(ServerLevel level) {
		if (!bannerRaised && getHealth() <= getMaxHealth() * 0.66f) {
			bannerRaised = true;
			begin(Action.BANNER);
			level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.6f, 0.55f);
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) return;
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		double d = distanceTo(target);
		boolean sight = hasLineOfSight(target);
		boolean grounded = target.onGround() || target.getY() - getY() < 2;
		Action[] options = {Action.SLAM_WINDUP, Action.LEAP_WINDUP, Action.CHARGE_WINDUP, Action.THROW_WINDUP};
		float[] weights = {
			d < 6.5 ? 4 : 0,
			d >= 6 && d <= 20 && sight && grounded ? (d < 12 ? 3 : 4) : 0,
			d >= 8 && d < 22 && sight ? 3 : 0,
			d >= 7 && d < 24 && sight && armed() ? (d > 14 ? 4 : 2.5f) : 0,
		};
		float total = 0;
		for (int i = 0; i < options.length; i++) {
			if (options[i] == lastAbility) weights[i] *= 0.25f;
			total += weights[i];
		}
		if (total <= 0) return;
		float roll = random.nextFloat() * total;
		Action pick = Action.NONE;
		for (int i = 0; i < options.length && pick == Action.NONE; i++) {
			roll -= weights[i];
			if (roll < 0 && weights[i] > 0) pick = options[i];
		}
		if (pick == Action.NONE) return;
		lastAbility = pick;
		switch (pick) {
			case SLAM_WINDUP -> {
				begin(Action.SLAM_WINDUP);
				Hints.offerNear(level, position(), 24, Hints.SLAM);
				level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6f, 0.55f);
			}
			case CHARGE_WINDUP -> {
				begin(Action.CHARGE_WINDUP);
				Hints.offerNear(level, position(), 32, Hints.CHARGE);
				chargeDirection = target.position().subtract(position()).multiply(1, 0, 1).normalize();
				level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1.4f, 0.6f);
			}
			case LEAP_WINDUP -> {
				begin(Action.LEAP_WINDUP);
				leapFrom = position();
				leapTo = landingFor(target);
				Hints.offerNear(level, position(), 32, Hints.LEAP);
				level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.75f);
			}
			case THROW_WINDUP -> {
				begin(Action.THROW_WINDUP);
				level.playSound(null, blockPosition(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.6f, 0.55f);
			}
			default -> {
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (action() == Action.NONE && armed() && swingRest == 0) startSwing(level, 0);
		return false;
	}

	private void startSwing(ServerLevel level, int step) {
		entityData.set(COMBO, (byte) step);
		begin(Action.SWING);
		level.playSound(null, getX(), getEyeY(), getZ(), WarhornSounds.WHOOSH, SoundSource.HOSTILE, 0.9f,
			step == 2 ? 0.6f : 0.85f + random.nextFloat() * 0.1f);
	}

	private void swing(ServerLevel level) {
		int step = combo();
		LivingEntity target = getTarget();
		if (target != null && actionTicks < SWING_HIT[step] - 2) faceTowards(target.position(), 40f);
		if (actionTicks == SWING_HIT[step]) strike(level, step);
		if (actionTicks < SWING_TICKS[step]) return;
		boolean follow = step < 2 && target != null && target.isAlive() && distanceTo(target) < REACH + 1.4;
		if (follow) {
			startSwing(level, step + 1);
		} else {
			begin(Action.NONE);
			swingRest = lastStand() ? 10 : enraged() ? 16 : 26;
		}
	}

	private void strike(ServerLevel level, int step) {
		Vec3 forward = Vec3.directionFromRotation(0, getYRot());
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		double arc = step == 2 ? 0.55 : 0.2;
		float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		float damage = base * (step == 2 ? 1.35f : step == 1 ? 0.9f : 1f);
		boolean landed = false;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH, 1.2, REACH), this::isFoe)) {
			Vec3 to = victim.position().subtract(position()).multiply(1, 0, 1);
			double d = to.length();
			if (d > REACH + victim.getBbWidth() * 0.5 || d > 0.01 && to.normalize().dot(forward) < arc) continue;
			if (!victim.hurtServer(level, damageSources().mobAttack(this), damage)) continue;
			landed = true;
			Vec3 push = step == 2 ? to.normalize().scale(1.1) : side.scale(step == 0 ? -0.9 : 0.9).add(forward.scale(0.4));
			victim.push(push.x, step == 2 ? 0.5 : 0.3, push.z);
			Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.25, 0.3, 0.25, 0.45);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
		}
		double height = getY() + (step == 1 ? 1.9 : 1.5) * getScale();
		for (int i = 0; i < 7; i++) {
			double t = i / 6.0;
			double angle = step == 2 ? 0 : Mth.lerp(t, step == 0 ? 1.2 : -1.2, step == 0 ? -1.2 : 1.2);
			Vec3 dir = forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
			double r = step == 2 ? 1.6 + t * 1.2 : 2.6;
			double y = step == 1 ? height - 0.9 + t * 1.4 : step == 2 ? getY() + 0.3 + (1 - t) * 2.8 : height;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + dir.x * r, y, getZ() + dir.z * r, 1, 0, 0, 0, 0);
		}
		level.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.55f);
		if (step == 2) {
			Vec3 hit = position().add(forward.scale(2.6));
			BlockState ground = level.getBlockState(BlockPos.containing(hit.x, getY() - 0.5, hit.z));
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), hit.x, getY() + 0.1, hit.z, 40, 0.6, 0.1, 0.6, 0.2);
				level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), hit.x, getY() + 0.1, hit.z, 18, 0.5, 0, 0.5, 0.3);
			}
			level.playSound(null, BlockPos.containing(hit), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.2f, 0.7f);
			Cues.impact(level, hit, landed ? 0.75f : 0.5f, 18);
		} else if (landed) {
			Cues.impact(level, position(), 0.4f, 12);
		}
		if (landed) level.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.3f, 0.6f);
	}

	private void faceTowards(Vec3 point, float maxTurn) {
		Vec3 d = point.subtract(position());
		if (d.horizontalDistanceSqr() < 1e-4) return;
		float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float yaw = Mth.approachDegrees(getYRot(), want, maxTurn);
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	private void slamWindup(ServerLevel level) {
		getNavigation().stop();
		if (getTarget() != null) getLookControl().setLookAt(getTarget());
		if (actionTicks % 4 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 6, 0.9, 0.1, 0.9, 0.01);
		}
		if (actionTicks >= Action.SLAM_WINDUP.ticks) {
			begin(Action.SLAM);
			level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.3f, 0.6f);
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f);
			Cues.impact(level, position(), 0.9f, 28);
		}
	}

	private void slam(ServerLevel level) {
		float radius = 1.2f + actionTicks * 0.75f;
		BlockState ground = level.getBlockState(blockPosition().below());
		int points = Mth.ceil(radius * 7);
		for (int i = 0; i < points; i++) {
			double angle = i * Mth.TWO_PI / points;
			double x = getX() + Math.cos(angle) * radius;
			double z = getZ() + Math.sin(angle) * radius;
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, getY() + 0.1, z, 2, 0.1, 0.05, 0.1, 0.15);
			}
			if (i % 3 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, getY() + 0.2, z, 0, 0, 0.04, 0, 1);
		}
		AABB area = getBoundingBox().inflate(radius + 1, 1.5, radius + 1);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, this::isFoe)) {
			double d = Math.sqrt(victim.distanceToSqr(getX(), victim.getY(), getZ()));
			if (d < radius - 0.9 || d > radius + 0.5 || !victim.onGround() || !struck.add(victim.getUUID())) continue;
			victim.hurtServer(level, damageSources().mobAttack(this), enraged() ? 13f : 10f);
			Vec3 away = victim.position().subtract(position()).multiply(1, 0, 1).normalize();
			victim.push(away.x * 0.8, 0.55, away.z * 0.8);
		}
		if (actionTicks >= Action.SLAM.ticks) finish();
	}

	private void chargeWindup(ServerLevel level) {
		getNavigation().stop();
		LivingEntity target = getTarget();
		if (target != null) {
			chargeDirection = target.position().subtract(position()).multiply(1, 0, 1).normalize();
			getLookControl().setLookAt(target);
		}
		setYRot((float) (Mth.atan2(chargeDirection.z, chargeDirection.x) * Mth.RAD_TO_DEG) - 90f);
		setYBodyRot(getYRot());
		if (actionTicks % 3 == 0) {
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 3, 0.4, 0.05, 0.4, 0.02);
		}
		if (actionTicks >= Action.CHARGE_WINDUP.ticks) {
			begin(Action.CHARGE);
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8f, 0.8f);
		}
	}

	private void charge(ServerLevel level) {
		double speed = lastStand() ? 1.05 : enraged() ? 0.95 : 0.8;
		setDeltaMovement(chargeDirection.x * speed, getDeltaMovement().y, chargeDirection.z * speed);
		setYRot((float) (Mth.atan2(chargeDirection.z, chargeDirection.x) * Mth.RAD_TO_DEG) - 90f);
		setYBodyRot(getYRot());
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.1, 0.3, 0.01);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), this::isFoe)) {
			if (!struck.add(victim.getUUID())) continue;
			victim.hurtServer(level, damageSources().mobAttack(this), enraged() ? 15f : 12f);
			victim.push(chargeDirection.x * 1.6, 0.6, chargeDirection.z * 1.6);
			Cues.impact(level, victim.position(), 0.7f, 18);
		}
		boolean breached = smashAhead(level);
		if (horizontalCollision && !breached && actionTicks > 3) {
			begin(Action.STUNNED);
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.4f);
			level.sendParticles(ParticleTypes.CRIT, getX(), getEyeY() + 0.6, getZ(), 24, 0.6, 0.3, 0.6, 0.2);
			Cues.impact(level, position(), 1.0f, 24);
			Cues.toast(level, position(), "warhorn.warlord.stunned");
			Hints.offerNear(level, position(), 32, Hints.STUNNED);
		} else if (actionTicks >= Action.CHARGE.ticks) {
			finish();
		}
	}

	private boolean smashAhead(ServerLevel level) {
		if (!Ruination.griefing(level)) return false;
		AABB box = getBoundingBox().move(chargeDirection.x * 0.9, 0, chargeDirection.z * 0.9).deflate(0.05);
		AABB ahead = new AABB(box.minX, getY() + 0.6, box.minZ, box.maxX, box.maxY, box.maxZ);
		boolean broke = false;
		Ruination.begin();
		try {
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY, ahead.minZ),
				BlockPos.containing(ahead.maxX, ahead.maxY, ahead.maxZ))) {
				BlockState state = level.getBlockState(pos);
				if (!yields(level, pos, state)) continue;
				level.destroyBlock(pos, true, this);
				broke = true;
			}
		} finally {
			Ruination.end();
		}
		if (broke) {
			level.playSound(null, blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.4f, 0.7f);
			Cues.impact(level, position(), 0.45f, 20);
		}
		return broke;
	}

	public static boolean yields(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
		if (state.getCollisionShape(level, pos).isEmpty()) return false;
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness > 3.5f) return false;
		return state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(ConventionalBlockTags.GLASS_BLOCKS)
			|| state.is(ConventionalBlockTags.GLASS_PANES);
	}

	private void stunned(ServerLevel level) {
		getNavigation().stop();
		if (actionTicks % 5 == 0) {
			level.sendParticles(ParticleTypes.CRIT, getX(), getEyeY() + 0.7, getZ(), 4, 0.4, 0.1, 0.4, 0.05);
		}
		if (actionTicks >= Action.STUNNED.ticks) finish();
	}

	public Vec3 handPosition() {
		Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		return position().add(0, 1.9 * getScale(), 0).add(right.scale(0.55 * getScale())).add(forward.scale(0.3));
	}

	private void throwWindup(ServerLevel level) {
		LivingEntity target = getTarget();
		if (target != null) faceTowards(target.position(), 30f);
		if (actionTicks % 3 == 0) {
			Vec3 hand = handPosition().add(0, 0.6, 0);
			level.sendParticles(ParticleTypes.SMALL_FLAME, hand.x, hand.y, hand.z, 2, 0.2, 0.3, 0.2, 0.01);
		}
		if (actionTicks < Action.THROW_WINDUP.ticks) return;
		if (target == null || !target.isAlive()) {
			finish();
			return;
		}
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).add(target.getDeltaMovement().multiply(7, 0, 7));
		Vec3 from = handPosition().add(0, 0.5, 0);
		Vec3 reach = aim.subtract(from);
		if (reach.length() > 26) aim = from.add(reach.normalize().scale(26));
		float damage = lastStand() ? 15f : enraged() ? 13f : 11f;
		WarlordAxeEntity.launch(level, this, from, aim, damage);
		entityData.set(ARMED, false);
		begin(Action.UNARMED);
		level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.6f, 0.5f);
		level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.5f);
		Hints.offerNear(level, position(), 40, Hints.AXE);
	}

	private void unarmed(ServerLevel level) {
		LivingEntity target = getTarget();
		if (target != null) getLookControl().setLookAt(target);
		if (actionTicks >= Action.UNARMED.ticks) catchAxe();
	}

	public void catchAxe() {
		if (armed()) return;
		entityData.set(ARMED, true);
		if (action() == Action.UNARMED) begin(Action.CATCH);
		if (level() instanceof ServerLevel level) {
			Vec3 hand = handPosition();
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.8f, 0.65f);
			level.sendParticles(ParticleTypes.CRIT, hand.x, hand.y, hand.z, 12, 0.2, 0.2, 0.2, 0.3);
		}
	}

	private Vec3 landingFor(LivingEntity target) {
		Vec3 aim = target.position().add(target.getDeltaMovement().multiply(10, 0, 10));
		Vec3 flat = aim.subtract(position()).multiply(1, 0, 1);
		if (flat.length() > 20) aim = position().add(flat.normalize().scale(20));
		return new Vec3(aim.x, target.getY(), aim.z);
	}

	private void leapWindup(ServerLevel level) {
		LivingEntity target = getTarget();
		if (target != null && actionTicks < 12) leapTo = landingFor(target);
		faceTowards(leapTo, 25f);
		markLanding(level, 1f - (float) actionTicks / (Action.LEAP_WINDUP.ticks + Action.LEAP.ticks));
		if (actionTicks % 4 == 0) {
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 4, 0.5, 0.05, 0.5, 0.03);
		}
		if (actionTicks < Action.LEAP_WINDUP.ticks) return;
		leapFrom = position();
		begin(Action.LEAP);
		BlockState ground = level.getBlockState(blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 30, 0.7, 0.1, 0.7, 0.2);
		}
		level.playSound(null, blockPosition(), WarhornSounds.WHOOSH, SoundSource.HOSTILE, 1.2f, 0.6f);
		level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.9f);
	}

	private void markLanding(ServerLevel level, float remaining) {
		if (actionTicks % 2 != 0) return;
		DustParticleOptions red = new DustParticleOptions(0xD22A1E, 1.6f);
		DustParticleOptions ember = new DustParticleOptions(0xFF8A2A, 1.1f);
		double outer = 4.2;
		double inner = 0.8 + 3.2 * Mth.clamp(remaining, 0, 1);
		for (int i = 0; i < 28; i++) {
			double a = i * Mth.TWO_PI / 28 + actionTicks * 0.05;
			level.sendParticles(red, leapTo.x + Math.cos(a) * outer, leapTo.y + 0.15, leapTo.z + Math.sin(a) * outer, 1, 0, 0, 0, 0);
			if (i % 2 == 0) {
				level.sendParticles(ember, leapTo.x + Math.cos(-a) * inner, leapTo.y + 0.15, leapTo.z + Math.sin(-a) * inner, 1, 0, 0, 0, 0);
			}
		}
	}

	private void leap(ServerLevel level) {
		int flight = Action.LEAP.ticks;
		double s = Math.min(1.0, (actionTicks + 1) / (double) flight);
		double span = leapTo.subtract(leapFrom).multiply(1, 0, 1).length();
		double apex = 3.5 + span * 0.18;
		Vec3 next = leapFrom.lerp(leapTo, s).add(0, 4 * apex * s * (1 - s), 0);
		setDeltaMovement(next.subtract(position()));
		faceTowards(leapTo, 30f);
		resetFallDistance();
		markLanding(level, 1f - (float) (Action.LEAP_WINDUP.ticks + actionTicks) / (Action.LEAP_WINDUP.ticks + flight));
		if (actionTicks % 2 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 2, 0.3, 0.4, 0.3, 0.01);
		if (actionTicks >= flight || actionTicks > 4 && onGround() && s > 0.6) land(level);
	}

	private void land(ServerLevel level) {
		setDeltaMovement(Vec3.ZERO);
		resetFallDistance();
		begin(Action.LEAP_RECOVER);
		Vec3 at = position();
		double radius = 4.6;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius), this::isFoe)) {
			Vec3 to = victim.position().subtract(at);
			double d = to.multiply(1, 0, 1).length();
			if (d > radius || Math.abs(to.y) > 2.2) continue;
			float damage = (float) Mth.lerp(d / radius, lastStand() ? 22 : 18, 7);
			victim.hurtServer(level, damageSources().mobAttack(this), damage);
			Vec3 away = d > 0.01 ? to.multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, getYRot());
			victim.push(away.x * 1.3, 0.7, away.z * 1.3);
		}
		BlockState ground = level.getBlockState(blockPosition().below());
		for (int ring = 1; ring <= 4; ring++) {
			int points = ring * 9;
			for (int i = 0; i < points; i++) {
				double a = i * Mth.TWO_PI / points;
				double x = at.x + Math.cos(a) * ring;
				double z = at.z + Math.sin(a) * ring;
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, at.y + 0.1, z, 3, 0.15, 0.1, 0.15, 0.25);
					if (i % 2 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), x, at.y + 0.1, z, 2, 0.1, 0, 0.1, 0.4);
				}
				if (i % 3 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, at.y + 0.2, z, 0, Math.cos(a) * 0.05, 0.03, Math.sin(a) * 0.05, 1);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.0, 0.2, 1.0, 0);
		level.playSound(null, blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.6f);
		level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.55f);
		level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.1f, 0.45f);
		Cues.impact(level, at, 1.0f, 34);
	}

	private void leapRecover(ServerLevel level) {
		getNavigation().stop();
		if (actionTicks == 2) Hints.offerNear(level, position(), 32, Hints.LEAP_RECOVER);
		if (actionTicks == 24) {
			level.playSound(null, blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.4f, 0.5f);
			BlockState ground = level.getBlockState(blockPosition().below());
			Vec3 axe = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.6));
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), axe.x, getY() + 0.2, axe.z, 16, 0.3, 0.2, 0.3, 0.15);
			}
		}
		if (actionTicks >= Action.LEAP_RECOVER.ticks) finish();
	}

	private void banner(ServerLevel level) {
		getNavigation().stop();
		if (actionTicks == 6) {
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.5f);
		}
		if (actionTicks == 20) {
			Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
			Vec3 left = new Vec3(forward.z, 0, -forward.x);
			Vec3 at = position().add(left.scale(1.7)).add(forward.scale(0.7));
			WarBannerEntity.plant(level, this, at);
			Cues.impact(level, at, 0.7f, 30);
			Cues.toast(level, at, "warhorn.banner.raised");
			Hints.offerNear(level, at, 48, Hints.BANNER);
		}
		if (actionTicks >= Action.BANNER.ticks) finish();
	}

	private void beginLastStand(ServerLevel level) {
		entityData.set(LAST_STAND, true);
		entityData.set(ARMED, true);
		setDeltaMovement(Vec3.ZERO);
		begin(Action.LAST_STAND);
		level.playSound(null, blockPosition(), SoundEvents.RAVAGER_HURT, SoundSource.HOSTILE, 2.0f, 0.45f);
		SiegeDirector.warlordLastStand(level, this);
	}

	private void lastStandTick(ServerLevel level) {
		getNavigation().stop();
		if (actionTicks % 4 == 0 && actionTicks < LAST_STAND_BURST) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.4 * getScale(), getZ(), 3, 0.4, 0.5, 0.4, 0.01);
		}
		if (actionTicks == LAST_STAND_BURST - 8) {
			level.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.6f);
		}
		if (actionTicks == LAST_STAND_BURST) {
			Vec3 chest = position().add(0, 1.6 * getScale(), 0);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.NETHERITE_SCRAP),
				chest.x, chest.y, chest.z, 50, 0.4, 0.6, 0.4, 0.35);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.IRON_INGOT),
				chest.x, chest.y, chest.z, 30, 0.4, 0.6, 0.4, 0.3);
			level.sendParticles(ParticleTypes.LAVA, chest.x, chest.y, chest.z, 24, 0.5, 0.6, 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, chest.x, chest.y, chest.z, 60, 0.3, 0.8, 0.3, 0.12);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 2.0f, 0.5f);
			level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.5f);
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.6f, 0.4f);
			Cues.impact(level, position(), 1.0f, 48);
			if (SiegeDirector.slowMotionFair(level, siegeId)) SlowMotion.start(level.getServer(), 7f, 1400);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), this::isFoe)) {
				Vec3 away = victim.position().subtract(position()).multiply(1, 0, 1).normalize();
				victim.push(away.x * 1.4, 0.6, away.z * 1.4);
			}
			setAttribute(Attributes.ARMOR, 4.0);
			setAttribute(Attributes.ARMOR_TOUGHNESS, 0.0);
			setAttribute(Attributes.MOVEMENT_SPEED, 0.4);
			setAttribute(Attributes.ATTACK_DAMAGE, 17.0);
			setAttribute(Attributes.KNOCKBACK_RESISTANCE, 1.0);
		}
		if (actionTicks >= Action.LAST_STAND.ticks) finish();
	}

	private void setAttribute(Holder<Attribute> attribute, double value) {
		var instance = getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private void roar(ServerLevel level) {
		getNavigation().stop();
		if (actionTicks == 8) {
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.6f, 0.45f);
			level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.4f, 0.6f);
			Cues.impact(level, position(), 1.0f, 40);
			for (Mob ally : level.getEntitiesOfClass(Raider.class, getBoundingBox().inflate(16), m -> m != this)) {
				ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
				ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
			}
		}
		if (actionTicks > 8 && actionTicks % 2 == 0) {
			level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(), 1, 0, 0, 0, 0);
		}
		if (actionTicks >= Action.ROAR.ticks) finish();
	}

	private void finish() {
		begin(Action.NONE);
		cooldown = lastStand() ? 25 + random.nextInt(20) : enraged() ? 40 + random.nextInt(30) : 70 + random.nextInt(50);
	}

	public boolean isFoe(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof Raider) && !(e instanceof Player p && (p.isSpectator() || p.isCreative()));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Action a = action();
		if (a == Action.ROAR || a == Action.LAST_STAND) return false;
		boolean opening = a == Action.STUNNED || a == Action.LEAP_RECOVER || !armed();
		if (a == Action.STUNNED) damage *= 1.6f;
		else if (a == Action.LEAP_RECOVER) damage *= 1.4f;
		else if (!armed()) damage *= 1.3f;
		boolean hurt = super.hurtServer(level, source, damage);
		if (!hurt || isDeadOrDying()) return hurt;
		if (opening) {
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.4 * getScale(), getZ(), 10, 0.4, 0.5, 0.4, 0.3);
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2f, 0.7f);
		}
		if (!lastStand() && getHealth() < getMaxHealth() * 0.2f) {
			beginLastStand(level);
		} else if (!enraged() && getHealth() < getMaxHealth() * 0.5f) {
			entityData.set(ENRAGED, true);
			setAttribute(Attributes.MOVEMENT_SPEED, 0.36);
			begin(Action.ROAR);
			Cues.card(level, position(), "warhorn.warlord.enraged", "warhorn.warlord.enraged.sub");
			Hints.offerNear(level, position(), 48, Hints.ENRAGED);
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel level && !isDeadOrDying()) {
			if (lastStand() && action() != Action.LAST_STAND) {
				if (tickCount % 2 == 0) {
					level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.3 * getScale(), getZ(), 1, 0.35, 0.7, 0.35, 0.01);
				}
				if (tickCount % 5 == 0) {
					level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.6 * getScale(), getZ(), 1, 0.3, 0.3, 0.3, 0);
				}
			} else if (enraged() && tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2 * getScale(), getZ(), 1, 0.35, 0.6, 0.35, 0.005);
			}
		}
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		if (!(level() instanceof ServerLevel level) || isRemoved()) return;
		if (deathTime == 1) {
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_DEATH, SoundSource.HOSTILE, 2.2f, 0.5f);
		}
		if (deathTime == 16) {
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9f, 0.4f);
			BlockState ground = level.getBlockState(blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 30, 0.8, 0.1, 0.8, 0.15);
			}
		}
		if (deathTime > 10 && deathTime % 3 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2 * getScale(), getZ(), 3, 0.4, 0.5, 0.4, 0.01);
			level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.0 * getScale(), getZ(), 1, 0.4, 0.5, 0.4, 0.01);
		}
		if (deathTime >= DEATH_TICKS) {
			Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 80, 0.4, 0.8, 0.4, 0.1);
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.04);
			level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.6f, 0.5f);
			level.broadcastEntityEvent(this, (byte) 60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		begin(Action.NONE);
		if (level() instanceof ServerLevel level) SiegeDirector.warlordSlain(level, this);
		bossBar.removeAllPlayers();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("WarhornSiege", siegeId);
		output.putBoolean("Enraged", enraged());
		output.putBoolean("LastStand", lastStand());
		output.putBoolean("BannerRaised", bannerRaised);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		siegeId = input.getIntOr("WarhornSiege", -1);
		entityData.set(ENRAGED, input.getBooleanOr("Enraged", false));
		entityData.set(LAST_STAND, input.getBooleanOr("LastStand", false));
		bannerRaised = input.getBooleanOr("BannerRaised", false);
	}

	@Override
	public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
	}

	@Override
	public SoundEvent getCelebrateSound() {
		return SoundEvents.VINDICATOR_CELEBRATE;
	}

	@Override
	public IllagerArmPose getArmPose() {
		return IllagerArmPose.NEUTRAL;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VINDICATOR_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return 0.62f;
	}
}
