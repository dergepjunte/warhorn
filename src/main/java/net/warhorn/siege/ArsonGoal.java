package net.warhorn.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class ArsonGoal extends Goal {
	public static final String TAG = "warhorn_torchbearer";
	private static final int GIVE_UP_TICKS = 20 * 25;

	private final PathfinderMob mob;
	private final BlockPos center;
	private BlockPos firePos;
	private int ticks;
	private int cooldown;

	public ArsonGoal(PathfinderMob mob, BlockPos center) {
		this.mob = mob;
		this.center = center;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (mob.isNoAi() || mob.isPassenger() || !(mob.level() instanceof ServerLevel level)) return false;
		if (!Ruination.griefing(level) || closeFoe()) return false;
		Siege siege = SiegeManager.find(level, center, 8);
		if (siege == null || siege.stage() != SiegeStage.ACT || siege.arsonsLeft <= 0 || Ruination.exhausted(siege)) return false;
		if (mob.getRandom().nextInt(4) != 0) return false;
		firePos = findKindling(level);
		if (firePos == null) cooldown = 40;
		return firePos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return firePos != null && ticks < GIVE_UP_TICKS && !closeFoe()
			&& mob.level() instanceof ServerLevel level && level.getBlockState(firePos).isAir();
	}

	@Override
	public void start() {
		ticks = 0;
		mob.setAggressive(true);
		mob.getNavigation().moveTo(firePos.getX() + 0.5, firePos.getY(), firePos.getZ() + 0.5, 1.15);
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.getNavigation().stop();
		firePos = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (firePos == null) return;
		ticks++;
		ServerLevel level = (ServerLevel) mob.level();
		mob.getLookControl().setLookAt(Vec3.atCenterOf(firePos));
		if (ticks % 4 == 0) {
			Vec3 hand = mob.position().add(mob.getLookAngle().scale(0.4)).add(0, mob.getBbHeight() * 0.62, 0);
			level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.005);
			level.sendParticles(ParticleTypes.SMOKE, hand.x, hand.y + 0.2, hand.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		if (mob.position().distanceToSqr(Vec3.atCenterOf(firePos)) < 3.2 * 3.2) {
			ignite(level);
			return;
		}
		if (ticks % 20 == 0 && mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(firePos.getX() + 0.5, firePos.getY(), firePos.getZ() + 0.5, 1.15);
		}
	}

	private void ignite(ServerLevel level) {
		Siege siege = SiegeManager.find(level, center, 8);
		if (siege != null && siege.arsonsLeft > 0 && BaseFireBlock.canBePlacedAt(level, firePos, Direction.UP)) {
			level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL_IMMEDIATE);
			level.playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0f, 0.8f);
			level.playSound(null, firePos, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.6f, 0.7f);
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
			siege.arsonsLeft--;
			Hints.offerNear(level, Vec3.atCenterOf(firePos), 40, Hints.ARSON);
		}
		firePos = null;
		cooldown = 20 * 12;
	}

	private boolean closeFoe() {
		LivingEntity target = mob.getTarget();
		return target != null && target.distanceToSqr(mob) < 5 * 5;
	}

	private BlockPos findKindling(ServerLevel level) {
		for (int attempt = 0; attempt < 24; attempt++) {
			int dx = mob.getRandom().nextInt(57) - 28;
			int dz = mob.getRandom().nextInt(57) - 28;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz);
			for (int y = top; y > top - 6; y--) {
				BlockPos solid = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
				BlockState state = level.getBlockState(solid);
				if (!state.ignitedByLava() || state.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos air = solid.relative(d);
					if (level.getBlockState(air).isAir() && level.getBlockState(air.below()).isFaceSturdy(level, air.below(), Direction.UP)
						&& Math.abs(air.getY() - mob.getBlockY()) < 8) {
						return air;
					}
				}
			}
		}
		return null;
	}
}
