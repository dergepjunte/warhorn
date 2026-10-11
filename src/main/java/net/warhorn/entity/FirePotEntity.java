package net.warhorn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.Hints;
import net.minecraft.world.level.block.state.BlockState;
import net.warhorn.siege.Ruination;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;

public class FirePotEntity extends Entity {
	public static final double GRAVITY = 0.045;
	private static final int MAX_FLIGHT = 200;
	private static final float BURST_RADIUS = 3.4f;
	private static final int FIRES = 10;
	private static final int SMASHED = 5;

	private int siegeId = -1;

	public FirePotEntity(EntityType<? extends FirePotEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static FirePotEntity launch(ServerLevel level, Vec3 from, Vec3 to, int siegeId) {
		FirePotEntity pot = WarhornEntities.FIRE_POT.create(level, EntitySpawnReason.TRIGGERED);
		if (pot == null) return null;
		pot.siegeId = siegeId;
		pot.setPos(from);
		pot.setDeltaMovement(velocity(from, to));
		level.addFreshEntity(pot);
		level.playSound(null, pot, WarhornSounds.FIREPOT_FLIGHT, SoundSource.HOSTILE, 2.5f, 1.0f);
		return pot;
	}

	public static Vec3 velocity(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double flat = Math.sqrt(d.horizontalDistanceSqr());
		double ticks = Mth.clamp(flat * 0.9 + 18, 36, 80);
		return new Vec3(d.x / ticks, (d.y + 0.5 * GRAVITY * ticks * ticks) / ticks, d.z / ticks);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 from = position();
		Vec3 v = getDeltaMovement();
		Vec3 to = from.add(v);
		if (level().isClientSide()) {
			setPos(to);
			setDeltaMovement(v.add(0, -GRAVITY, 0));
			trail(from, to);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (tickCount > MAX_FLIGHT || getY() < level.getMinY()) {
			discard();
			return;
		}
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
		LivingEntity struck = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.4),
				e -> e.isAlive() && !(e instanceof Raider))
			.stream().filter(e -> e.getBoundingBox().inflate(0.3).clip(from, end).isPresent()).findFirst().orElse(null);
		if (struck != null) {
			burst(level, struck.position().add(0, struck.getBbHeight() * 0.6, 0), null);
			return;
		}
		if (hit.getType() != HitResult.Type.MISS) {
			if (!level.getFluidState(hit.getBlockPos()).isEmpty()) {
				fizzle(level, hit.getLocation());
				return;
			}
			burst(level, hit.getLocation(), hit);
			return;
		}
		setPos(to);
		setDeltaMovement(v.add(0, -GRAVITY, 0));
	}

	private void trail(Vec3 from, Vec3 to) {
		for (int i = 0; i < 3; i++) {
			Vec3 p = from.lerp(to, i / 3.0);
			level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.04, 0.02, (random.nextDouble() - 0.5) * 0.04);
		}
		level().addParticle(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 0, 0.01, 0);
		if (tickCount % 2 == 0) level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, from.x, from.y, from.z, 0, 0.015, 0);
	}

	private void burst(ServerLevel level, Vec3 at, BlockHitResult hit) {
		discard();
		level.playSound(null, at.x, at.y, at.z, WarhornSounds.FIREPOT_SHATTER, SoundSource.HOSTILE, 2.4f, 0.9f + random.nextFloat() * 0.2f);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 110, 1.8, 0.8, 1.8, 0.12);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 2, 0.5, 0.3, 0.5, 0);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 26, 1.2, 0.4, 1.2, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 24, 0.9, 0.6, 0.9, 0.04);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TERRACOTTA.defaultBlockState()),
			at.x, at.y + 0.3, at.z, 24, 0.4, 0.3, 0.4, 0.2);
		Cues.impact(level, at, 0.7f, 30);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e.isAlive() && !(e instanceof Raider))) {
			double d = e.position().distanceTo(at);
			if (d > BURST_RADIUS + 0.5) continue;
			e.hurtServer(level, damageSources().onFire(), (float) Mth.lerp(d / (BURST_RADIUS + 0.5), 6, 2));
			e.igniteForSeconds(4);
		}
		Hints.offerNear(level, at, 40, Hints.CATAPULT);
		Siege siege = siegeId >= 0 ? SiegeManager.byId(level, siegeId) : null;
		if (!Ruination.griefing(level) || siege != null && Ruination.exhausted(siege)) return;
		BlockPos centre = hit != null ? hit.getBlockPos().relative(hit.getDirection()) : BlockPos.containing(at);
		if (hit != null) {
			Ruination.begin();
			try {
				BlockPos struck = hit.getBlockPos();
				int smashed = 0;
				for (int attempt = 0; attempt < 20 && smashed < SMASHED; attempt++) {
					BlockPos p = attempt == 0 ? struck : struck.offset(random.nextInt(3) - 1, -random.nextInt(2), random.nextInt(3) - 1);
					BlockState state = level.getBlockState(p);
					if (!Ruination.isBuilding(state)) continue;
					float hardness = state.getDestroySpeed(level, p);
					if (hardness < 0 || hardness >= 10) continue;
					net.warhorn.siege.Debris.tearOut(level, p, state, at.add(0, 1.5, 0), 0.4, true);
					smashed++;
				}
			} finally {
				Ruination.end();
			}
		}
		int lit = 0;
		for (int attempt = 0; attempt < 48 && lit < FIRES; attempt++) {
			BlockPos p = attempt == 0 ? centre : centre.offset(random.nextInt(7) - 3, random.nextInt(4) - 2, random.nextInt(7) - 3);
			if (!level.getBlockState(p).isAir() || !BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) continue;
			level.setBlock(p, BaseFireBlock.getState(level, p), Block.UPDATE_ALL_IMMEDIATE);
			lit++;
		}
	}

	private void fizzle(ServerLevel level, Vec3 at) {
		discard();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.5f, 0.8f);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 20, 0.5, 0.3, 0.5, 0.05);
	}

	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("WarhornSiege", siegeId);
		Vec3 v = getDeltaMovement();
		output.putDouble("VX", v.x);
		output.putDouble("VY", v.y);
		output.putDouble("VZ", v.z);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		siegeId = input.getIntOr("WarhornSiege", -1);
		setDeltaMovement(input.getDoubleOr("VX", 0), input.getDoubleOr("VY", 0), input.getDoubleOr("VZ", 0));
	}
}
