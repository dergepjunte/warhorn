package net.warhorn.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Debris {
	private Debris() {
	}

	public static boolean tearOut(ServerLevel level, BlockPos pos, BlockState state, Vec3 from, double power, boolean fling) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
			pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.2);
		if (!fling || !throwable(state)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			return false;
		}
		RandomSource random = level.getRandom();
		FallingBlockEntity piece = FallingBlockEntity.fall(level, pos, state);
		piece.disableDrop();
		piece.dropItem = false;
		Vec3 out = Vec3.atCenterOf(pos).subtract(from).multiply(1, 0.4, 1);
		if (out.lengthSqr() < 1e-3) out = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
		out = out.normalize().scale(power * (0.55 + random.nextDouble() * 0.7));
		piece.setDeltaMovement(out.x, 0.3 + random.nextDouble() * 0.45 * power, out.z);
		piece.needsSync = true;
		return true;
	}

	private static boolean throwable(BlockState state) {
		if (state.hasBlockEntity()) return false;
		Block block = state.getBlock();
		return state.isSolidRender() || block instanceof StairBlock || block instanceof SlabBlock;
	}
}
