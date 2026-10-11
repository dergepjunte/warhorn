package net.warhorn.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

public final class Ruination {
	public static final int CORE_RADIUS = 64;

	private static int depth;

	private Ruination() {
	}

	public static void begin() {
		depth++;
	}

	public static void end() {
		depth--;
	}

	public static int fireBudget(Siege s) {
		return 150 + 130 * s.act;
	}

	public static boolean exhausted(Siege s) {
		return s.ledger.size() >= fireBudget(s);
	}

	public static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	public static boolean isBuilding(BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof BaseFireBlock) return false;
		if (state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)) return false;
		if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
			|| state.is(BlockTags.REPLACEABLE_BY_TREES)) return false;
		Block block = state.getBlock();
		return !(block instanceof VegetationBlock || block instanceof FarmlandBlock || block instanceof SnowLayerBlock
			|| block instanceof GrassBlock || block == Blocks.GRAVEL || block == Blocks.STONE || block == Blocks.DEEPSLATE);
	}

	public static void beforeSet(Level level, BlockPos pos, BlockState next) {
		if (depth <= 0 || !(level instanceof ServerLevel server)) return;
		if (!next.isAir() && !(next.getBlock() instanceof BaseFireBlock)) return;
		BlockState old = level.getBlockState(pos);
		if (old == next || !worthRebuilding(server, pos, old)) return;
		Siege siege = SiegeManager.find(server, pos, CORE_RADIUS);
		if (siege != null && siege.stage.ravaging()) siege.ledger.record(pos, old);
	}

	private static boolean worthRebuilding(ServerLevel level, BlockPos pos, BlockState state) {
		if (!isBuilding(state)) return false;
		if (state.is(BlockTags.LOGS)) {
			for (Direction d : Direction.values()) {
				if (level.getBlockState(pos.relative(d)).is(BlockTags.LEAVES)) return false;
			}
		}
		return true;
	}
}
