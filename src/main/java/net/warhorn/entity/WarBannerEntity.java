package net.warhorn.entity;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;
import net.warhorn.siege.SiegeSpawner;
import net.warhorn.siege.Unit;

public class WarBannerEntity extends Entity {
	public static final float MAX_HEALTH = 36f;
	private static final int CALL_EVERY = 200;
	private static final int MAX_CALLS = 6;
	private static final int CROWD_LIMIT = 8;

	private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(WarBannerEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LAST_HIT = SynchedEntityData.defineId(WarBannerEntity.class, EntityDataSerializers.INT);

	private int siegeId = -1;
	private int calls;
	public int age;

	public WarBannerEntity(EntityType<? extends WarBannerEntity> type, Level level) {
		super(type, level);
		blocksBuilding = true;
	}

	public static void plant(ServerLevel level, WarlordEntity warlord, Vec3 at) {
		WarBannerEntity banner = WarhornEntities.WAR_BANNER.create(level, EntitySpawnReason.TRIGGERED);
		if (banner == null) return;
		banner.siegeId = warlord.siegeId();
		banner.setPos(at.x, warlord.getY(), at.z);
		banner.setYRot(warlord.getYRot());
		banner.setGlowingTag(true);
		level.addFreshEntity(banner);
		level.playSound(null, banner.getX(), banner.getY(), banner.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.6f, 0.6f);
		level.playSound(null, banner.getX(), banner.getY(), banner.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f, 1.2f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(banner.blockPosition().below())),
			banner.getX(), banner.getY() + 0.1, banner.getZ(), 30, 0.5, 0.1, 0.5, 0.2);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, banner.getX(), banner.getY() + 0.3, banner.getZ(), 16, 0.6, 0.2, 0.6, 0.03);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HEALTH, MAX_HEALTH);
		builder.define(LAST_HIT, -100);
	}

	public float health() {
		return entityData.get(HEALTH);
	}

	public int sinceHit() {
		return age - entityData.get(LAST_HIT);
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (!(level() instanceof ServerLevel level)) {
			if (age % 3 == 0) {
				level().addParticle(ParticleTypes.SMALL_FLAME, getX() + (random.nextDouble() - 0.5) * 0.3, getY() + 3.25,
					getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
			}
			return;
		}
		if (age % 20 == 0) {
			Siege siege = SiegeManager.byId(level, siegeId);
			if (siegeId >= 0 && (siege == null || !siege.stage().fighting())) {
				fade(level);
				return;
			}
			rally(level);
		}
		if (age % CALL_EVERY == 0 && calls < MAX_CALLS) call(level);
	}

	private void rally(ServerLevel level) {
		for (Raider raider : level.getEntitiesOfClass(Raider.class, getBoundingBox().inflate(14), Entity::isAlive)) {
			if (raider instanceof WarlordEntity warlord) {
				if (!warlord.lastStand()) warlord.heal(2f);
				continue;
			}
			raider.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 50, 0, true, true));
			raider.addEffect(new MobEffectInstance(MobEffects.SPEED, 50, 0, true, true));
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3.0, getZ(), 6, 0.6, 0.3, 0.6, 0.01);
	}

	private void call(ServerLevel level) {
		Siege siege = SiegeManager.byId(level, siegeId);
		if (siege == null) return;
		int crowd = level.getEntitiesOfClass(Raider.class, getBoundingBox().inflate(24), e -> e.isAlive() && !(e instanceof WarlordEntity)).size();
		if (crowd >= CROWD_LIMIT) return;
		calls++;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.2f, 1.1f);
		for (int i = 0; i < 2; i++) {
			double a = random.nextDouble() * Mth.TWO_PI;
			Vec3 at = position().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
			Mob mob = SiegeSpawner.reinforce(level, siege, i == 0 ? Unit.VINDICATOR : Unit.PILLAGER, at, (float) (a * Mth.RAD_TO_DEG));
			if (mob == null) continue;
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 20, 0.3, 0.6, 0.3, 0.02);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 14, 0.4, 0.1, 0.4, 0.04);
		}
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return !(source.getEntity() instanceof Raider);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || isInvulnerableToBase(source) || source.getEntity() instanceof Raider) return false;
		float left = health() - Math.max(1f, damage);
		entityData.set(HEALTH, left);
		entityData.set(LAST_HIT, age);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1.2f, 0.7f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_HIT, SoundSource.HOSTILE, 1.0f, 0.8f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WOOL.red().defaultBlockState()),
			getX(), getY() + 2.2, getZ(), 12, 0.4, 0.5, 0.4, 0.1);
		if (left <= 0) fall(level);
		return true;
	}

	private void fade(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 30, 0.3, 1.0, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2.8, getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		discard();
	}

	private void fall(ServerLevel level) {
		Vec3 c = position().add(0, 1.6, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WOOL.red().defaultBlockState()), c.x, c.y + 0.6, c.z, 60, 0.5, 0.9, 0.5, 0.2);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.defaultBlockState()), c.x, c.y, c.z, 40, 0.2, 1.2, 0.2, 0.2);
		level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 1.2, c.z, 12, 0.4, 0.4, 0.4, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 24, 0.4, 0.9, 0.4, 0.03);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.6f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2f, 0.7f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
		Cues.impact(level, c, 0.6f, 24);
		Cues.toast(level, c, "warhorn.banner.fallen");
		discard();
	}

	public int siegeId() {
		return siegeId;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("WarhornSiege", siegeId);
		output.putFloat("Health", health());
		output.putInt("Calls", calls);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		siegeId = input.getIntOr("WarhornSiege", -1);
		entityData.set(HEALTH, input.getFloatOr("Health", MAX_HEALTH));
		calls = input.getIntOr("Calls", 0);
	}
}
