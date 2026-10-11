package net.warhorn.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.Hints;
import net.warhorn.siege.Ruination;

import java.util.ArrayList;
import java.util.List;

public class PowderKegEntity extends Entity {
	public static final int FUSE = 20 * 6;
	public static final int LOOSE_FUSE = 30;
	private static final float BLAST_RADIUS = 4.3f;
	private static final int THROWN = 22;

	private static final EntityDataAccessor<Integer> FUSE_LEFT = SynchedEntityData.defineId(PowderKegEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FUSE_TOTAL = SynchedEntityData.defineId(PowderKegEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> LOOSE = SynchedEntityData.defineId(PowderKegEntity.class, EntityDataSerializers.BOOLEAN);

	public PowderKegEntity(EntityType<? extends PowderKegEntity> type, Level level) {
		super(type, level);
		blocksBuilding = true;
	}

	public static PowderKegEntity plant(ServerLevel level, Vec3 at, float yaw, boolean loose) {
		PowderKegEntity keg = WarhornEntities.POWDER_KEG.create(level, EntitySpawnReason.TRIGGERED);
		if (keg == null) return null;
		keg.setPos(at.x, at.y, at.z);
		keg.setYRot(yaw);
		int fuse = loose ? LOOSE_FUSE : FUSE;
		keg.entityData.set(FUSE_LEFT, fuse);
		keg.entityData.set(FUSE_TOTAL, fuse);
		keg.entityData.set(LOOSE, loose);
		level.addFreshEntity(keg);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, loose ? 1.3f : 0.8f);
		if (!loose) Hints.offerNear(level, at, 40, Hints.KEG);
		return keg;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FUSE_LEFT, FUSE);
		builder.define(FUSE_TOTAL, FUSE);
		builder.define(LOOSE, false);
	}

	public float fuseLeft(float partial) {
		return Mth.clamp((entityData.get(FUSE_LEFT) - partial) / Math.max(1f, entityData.get(FUSE_TOTAL)), 0, 1);
	}

	public boolean loose() {
		return entityData.get(LOOSE);
	}

	public Vec3 fuseTip() {
		return position().add(0, 0.78 + 0.32 * fuseLeft(0), 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!onGround()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.04, 0).scale(0.98));
			move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
		}
		int left = entityData.get(FUSE_LEFT);
		if (level().isClientSide()) {
			sparks(left);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		int every = left > FUSE / 2 ? 20 : left > 30 ? 10 : 5;
		if (left % every == 0) {
			level.playSound(null, getX(), getY(), getZ(), WarhornSounds.FUSE, SoundSource.HOSTILE, 1.1f,
				0.9f + 0.4f * (1 - fuseLeft(0)));
		}
		if (left <= 0) {
			explode(level);
			return;
		}
		entityData.set(FUSE_LEFT, left - 1);
	}

	private void sparks(int left) {
		Vec3 tip = fuseTip();
		int n = left < 30 ? 3 : 1;
		for (int i = 0; i < n; i++) {
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
				(random.nextDouble() - 0.5) * 0.15, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.15);
		}
		if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, tip.x, tip.y + 0.05, tip.z, 0, 0.03, 0);
		if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, 0, 0.01, 0);
	}

	private void explode(ServerLevel level) {
		Vec3 c = position().add(0, 0.5, 0);
		boolean wrecks = !loose() && Ruination.griefing(level);
		List<BlockState> debris = new ArrayList<>();
		if (wrecks) {
			Ruination.begin();
			try {
				int r = Mth.ceil(BLAST_RADIUS);
				int thrown = 0;
				BlockPos origin = BlockPos.containing(c);
				for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -1, -r), origin.offset(r, r + 1, r))) {
					double d = Vec3.atCenterOf(p).distanceTo(c);
					if (d > BLAST_RADIUS || d > BLAST_RADIUS - 0.8 && random.nextFloat() < 0.5) continue;
					BlockState state = level.getBlockState(p);
					if (!Ruination.isBuilding(state)) continue;
					float hardness = state.getDestroySpeed(level, p);
					if (hardness < 0 || hardness >= 10) continue;
					if (debris.size() < 12) debris.add(state);
					net.warhorn.siege.Debris.tearOut(level, p.immutable(), state, c.add(0, -0.6, 0), 0.75, random.nextFloat() < 0.6 && thrown++ < THROWN);
				}
			} finally {
				Ruination.end();
			}
		}
		discard();
		level.explode(this, c.x, c.y, c.z, loose() ? 3.4f : 4.6f, false, Level.ExplosionInteraction.NONE);
		for (BlockState state : debris) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y + 0.5, c.z, 14, 0.9, 0.9, 0.9, 0.35);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.6, c.z, 80, 1.8, 1.8, 1.8, 0.08);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.8, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1.0, c.z, 12, 0.7, 0.8, 0.7, 0.02);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.4, c.z, 70, 1.4, 1.0, 1.4, 0.18);
		level.playSound(null, c.x, c.y, c.z, WarhornSounds.IMPACT, SoundSource.HOSTILE, 2.5f, 0.8f);
		if (wrecks) level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5f, 0.6f);
		Cues.impact(level, c, 1.0f, 56);
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
		if (isRemoved() || source.getEntity() instanceof Raider || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
		if (!(source.getEntity() instanceof Player)) return false;
		defuse(level);
		return true;
	}

	private void defuse(ServerLevel level) {
		Vec3 tip = fuseTip();
		level.sendParticles(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 10, 0.15, 0.1, 0.15, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0.01);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()),
			getX(), getY() + 0.5, getZ(), 20, 0.3, 0.4, 0.3, 0.15);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0f, 1.0f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.0f, 0.8f);
		level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.4, getZ(), new ItemStack(Items.GUNPOWDER, 2)));
		if (!loose()) Cues.toast(level, position(), "warhorn.keg.defused");
		discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Fuse", entityData.get(FUSE_LEFT));
		output.putInt("FuseTotal", entityData.get(FUSE_TOTAL));
		output.putBoolean("Loose", loose());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		entityData.set(FUSE_LEFT, input.getIntOr("Fuse", FUSE));
		entityData.set(FUSE_TOTAL, input.getIntOr("FuseTotal", FUSE));
		entityData.set(LOOSE, input.getBooleanOr("Loose", false));
	}
}
