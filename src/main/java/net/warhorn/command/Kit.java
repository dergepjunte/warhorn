package net.warhorn.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class Kit {
	public static final List<String> ITEMS = List.of(
		"minecraft:netherite_helmet[minecraft:enchantments={\"minecraft:protection\":4,\"minecraft:unbreaking\":3}]",
		"minecraft:netherite_chestplate[minecraft:enchantments={\"minecraft:protection\":4,\"minecraft:unbreaking\":3}]",
		"minecraft:netherite_leggings[minecraft:enchantments={\"minecraft:protection\":4,\"minecraft:unbreaking\":3}]",
		"minecraft:netherite_boots[minecraft:enchantments={\"minecraft:protection\":4,\"minecraft:feather_falling\":4,\"minecraft:unbreaking\":3}]",
		"minecraft:netherite_sword[minecraft:enchantments={\"minecraft:sharpness\":5,\"minecraft:sweeping_edge\":3,\"minecraft:unbreaking\":3}]",
		"minecraft:netherite_axe[minecraft:enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3}]",
		"minecraft:bow[minecraft:enchantments={\"minecraft:power\":5,\"minecraft:flame\":1,\"minecraft:infinity\":1,\"minecraft:unbreaking\":3}]",
		"minecraft:golden_apple 16",
		"minecraft:enchanted_golden_apple 2",
		"minecraft:splash_potion[minecraft:potion_contents={potion:\"minecraft:strong_healing\"}] 1",
		"minecraft:splash_potion[minecraft:potion_contents={potion:\"minecraft:strong_healing\"}] 1",
		"minecraft:potion[minecraft:potion_contents={potion:\"minecraft:strong_strength\"}] 1",
		"minecraft:cooked_beef 32",
		"minecraft:shield[minecraft:enchantments={\"minecraft:unbreaking\":3}]",
		"minecraft:arrow 1",
		"minecraft:totem_of_undying 1",
		"minecraft:splash_potion[minecraft:potion_contents={potion:\"minecraft:strong_healing\"}] 1",
		"minecraft:splash_potion[minecraft:potion_contents={potion:\"minecraft:strong_healing\"}] 1");
	private static final int ROW = 9;
	public static final int MAX = 64;

	private Kit() {
	}

	public static List<BlockPos> place(ServerLevel level, Vec3 eye, float yaw, int count) {
		Direction ahead = Direction.fromYRot(yaw);
		Direction side = ahead.getClockWise();
		BlockPos start = BlockPos.containing(eye).relative(ahead, 2);
		List<BlockPos> placed = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int row = i / ROW, inRow = Math.min(ROW, count - row * ROW);
			BlockPos column = start.relative(ahead, row).relative(side, i % ROW - (inRow - 1) / 2);
			BlockPos at = ground(level, column);
			level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, ahead.getOpposite()), Block.UPDATE_ALL);
			fill(level, at);
			placed.add(at);
		}
		return placed;
	}

	private static BlockPos ground(ServerLevel level, BlockPos column) {
		for (int d = 0; d <= 4; d++) {
			for (int dy : new int[] {-d, d}) {
				BlockPos at = column.above(dy);
				if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					return at;
				}
			}
		}
		return column;
	}

	private static void fill(ServerLevel level, BlockPos at) {
		CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(at))
			.withSuppressedOutput().withPermission(LevelBasedPermissionSet.OWNER);
		for (int slot = 0; slot < ITEMS.size(); slot++) {
			level.getServer().getCommands().performPrefixedCommand(source,
				"item replace block " + at.getX() + " " + at.getY() + " " + at.getZ() + " container." + slot + " with " + ITEMS.get(slot));
		}
	}
}
