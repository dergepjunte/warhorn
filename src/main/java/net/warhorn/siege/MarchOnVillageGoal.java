package net.warhorn.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class MarchOnVillageGoal extends Goal {
	private final PathfinderMob mob;
	private final BlockPos center;

	public MarchOnVillageGoal(PathfinderMob mob, BlockPos center) {
		this.mob = mob;
		this.center = center;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return mob.getTarget() == null && !mob.isPassenger() && !mob.isNoAi();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse() && !mob.getNavigation().isDone();
	}

	@Override
	public void start() {
		Vec3 goal = Vec3.atBottomCenterOf(center);
		Vec3 step;
		if (mob.position().distanceToSqr(goal) > 18 * 18) {
			step = DefaultRandomPos.getPosTowards(mob, 15, 4, goal, Math.PI / 2);
		} else {
			step = DefaultRandomPos.getPos(mob, 12, 4);
		}
		if (step != null) mob.getNavigation().moveTo(step.x, step.y, step.z, 1.0);
	}
}
