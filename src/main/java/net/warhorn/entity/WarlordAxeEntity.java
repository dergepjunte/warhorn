package net.warhorn.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WarlordAxeEntity extends Entity {
	private static final int OUT_TICKS = 18;
	private static final int HANG_TICKS = 5;
	private static final int GIVE_UP_TICKS = 150;

	private UUID owner;
	private Vec3 from = Vec3.ZERO;
	private Vec3 bend = Vec3.ZERO;
	private Vec3 to = Vec3.ZERO;
	private float damage = 11f;
	private int age;
	private boolean returning;
	private final Set<UUID> struck = new HashSet<>();

	public WarlordAxeEntity(EntityType<? extends WarlordAxeEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static void launch(ServerLevel level, WarlordEntity owner, Vec3 from, Vec3 to, float damage) {
		WarlordAxeEntity axe = WarhornEntities.WARLORD_AXE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (axe == null) {
			owner.catchAxe();
			return;
		}
		axe.owner = owner.getUUID();
		axe.from = from;
		axe.to = to;
		axe.damage = damage;
		Vec3 line = to.subtract(from);
		Vec3 right = new Vec3(-line.z, 0, line.x).normalize();
		axe.bend = from.lerp(to, 0.5).add(right.scale(line.length() * 0.32)).add(0, 1.5, 0);
		axe.setPos(from);
		axe.face(line);
		level.addFreshEntity(axe);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			trail();
			return;
		}
		WarlordEntity warlord = owner != null && level.getEntity(owner) instanceof WarlordEntity w && w.isAlive() ? w : null;
		if (warlord == null) {
			level.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.02);
			discard();
			return;
		}
		age++;
		Vec3 next;
		if (age <= OUT_TICKS) {
			double u = age / (double) OUT_TICKS;
			double s = 1 - (1 - u) * (1 - u);
			next = bezier(s);
		} else if (age <= OUT_TICKS + HANG_TICKS) {
			next = position().add(0, -0.03, 0);
		} else {
			if (!returning) {
				returning = true;
				struck.clear();
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.6f, 0.55f);
			}
			Vec3 hand = warlord.handPosition();
			Vec3 toHand = hand.subtract(position());
			double speed = Math.min(1.5, 0.3 + (age - OUT_TICKS - HANG_TICKS) * 0.08);
			if (toHand.length() <= Math.max(1.0, speed) || age > GIVE_UP_TICKS) {
				warlord.catchAxe();
				discard();
				return;
			}
			next = position().add(toHand.normalize().scale(speed));
		}
		Vec3 motion = next.subtract(position());
		setDeltaMovement(motion);
		setPos(next);
		if (motion.lengthSqr() > 1e-4) face(motion);
		if (age % 5 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.9f, 0.45f + age % 3 * 0.05f);
		}
		cut(level, warlord, motion);
	}

	private void cut(ServerLevel level, WarlordEntity warlord, Vec3 motion) {
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5), warlord::isFoe)) {
			if (!struck.add(victim.getUUID())) continue;
			if (!victim.hurtServer(level, damageSources().mobProjectile(this, warlord), damage)) continue;
			Vec3 push = motion.lengthSqr() > 1e-4 ? motion.normalize() : Vec3.ZERO;
			victim.push(push.x * 0.9, 0.35, push.z * 0.9);
			Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.25, 0.3, 0.25, 0.5);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.3f, 0.6f);
			Cues.impact(level, at, 0.55f, 10);
		}
	}

	private void trail() {
		Level level = level();
		double spin = tickCount * 0.9;
		for (int i = 0; i < 2; i++) {
			double a = spin + i * Math.PI;
			level.addParticle(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.6, getY() + 0.5 + Math.sin(a) * 0.6, getZ(), 0, 0, 0);
		}
		if (tickCount % 2 == 0) level.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.01, 0);
	}

	private Vec3 bezier(double s) {
		Vec3 a = from.lerp(bend, s);
		Vec3 b = bend.lerp(to, s);
		return a.lerp(b, s);
	}

	private void face(Vec3 motion) {
		setYRot((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90f);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
