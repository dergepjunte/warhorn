package net.warhorn.client.rebuild;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.warhorn.net.RuinPayload;

import java.util.ArrayList;
import java.util.List;

public final class RuinGhosts {
	private static final int FAINT = 0xE8DCC4;
	private static final int GOLD = 0xF2A33A;

	private static RuinPayload ruin = RuinPayload.NONE;
	private static final List<BlockState> states = new ArrayList<>();
	private static int shownRestored;

	private RuinGhosts() {
	}

	public static void accept(RuinPayload payload) {
		ruin = payload;
		states.clear();
		for (int id : payload.states()) states.add(Block.stateById(id));
	}

	public static void reset() {
		accept(RuinPayload.NONE);
	}

	public static boolean active() {
		return ruin.total() > 0;
	}

	public static RuinPayload ruin() {
		return ruin;
	}

	public static Block heldMissing() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !active()) return null;
		for (ItemStack stack : List.of(mc.player.getMainHandItem(), mc.player.getOffhandItem())) {
			if (stack.getItem() instanceof BlockItem item) {
				for (BlockState state : states) {
					if (state.is(item.getBlock())) return item.getBlock();
				}
			}
		}
		return null;
	}

	public static Block mostMissing() {
		Minecraft mc = Minecraft.getInstance();
		java.util.Map<Block, Integer> counts = new java.util.HashMap<>();
		for (int i = 0; i < states.size(); i++) {
			Block block = states.get(i).getBlock();
			if (!mc.level.getBlockState(ruin.positions().get(i)).is(block)) counts.merge(block, 1, Integer::sum);
		}
		return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
	}

	public static int placesFor(Block block) {
		Minecraft mc = Minecraft.getInstance();
		int n = 0;
		for (int i = 0; i < states.size(); i++) {
			if (states.get(i).is(block) && !mc.level.getBlockState(ruin.positions().get(i)).is(block)) n++;
		}
		return n;
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.level == null || mc.player == null || net.warhorn.client.cinematic.CinematicPlayer.active()) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		Block held = heldMissing();
		float time = (mc.level.getGameTime() % 1000 + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20f;
		float pulse = 0.65f + 0.35f * Mth.sin(time * 3.2f);
		int drawn = 0;
		for (int i = 0; i < states.size() && drawn < 600; i++) {
			BlockPos pos = ruin.positions().get(i);
			BlockState original = states.get(i);
			if (mc.level.getBlockState(pos).is(original.getBlock())) continue;
			double dist = pos.distToCenterSqr(camera);
			boolean match = held != null && original.is(held);
			if (dist > (match ? 40 * 40 : 18 * 18)) continue;
			float fade = 1f - (float) Math.sqrt(dist) / (match ? 48f : 20f);
			int color = match ? ARGB.color(Math.round(255 * pulse), GOLD) : ARGB.color(Math.round(70 * fade), FAINT);
			VoxelShape shape = original.getShape(mc.level, pos);
			if (shape.isEmpty()) shape = Shapes.box(0.3, 0, 0.3, 0.7, 0.6, 0.7);
			pose.pushPose();
			pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
			context.submitNodeCollector().submitShapeOutline(pose, shape, RenderTypes.linesTranslucent(), color,
				match ? 3.5f : 1.6f, true);
			pose.popPose();
			drawn++;
		}
	}

	public static int shownRestored() {
		shownRestored += Integer.signum(ruin.restored() - shownRestored);
		return shownRestored;
	}
}
