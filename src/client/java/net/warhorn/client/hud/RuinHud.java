package net.warhorn.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.warhorn.client.SiegeClientState;
import net.warhorn.client.cinematic.CinematicPlayer;
import net.warhorn.client.rebuild.RuinGhosts;
import net.warhorn.client.rebuild.Waymarks;

public final class RuinHud {
	private RuinHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !RuinGhosts.active() || SiegeClientState.active() || CinematicPlayer.active()) return;
		var ruin = RuinGhosts.ruin();
		int cx = g.guiWidth() / 2;
		int top = 6;
		float a = 0.85f;
		String label = Component.translatable("warhorn.rebuild.progress").getString().toUpperCase();
		Type.tracked(g, mc.font, label, cx, top, 1.0f, 1.4f, Type.EMBER, a, true);
		int half = 60;
		int barY = top + 12;
		float ratio = Mth.clamp(RuinGhosts.shownRestored() / (float) Math.max(1, ruin.total()), 0, 1);
		g.fill(cx - half - 1, barY - 1, cx + half + 1, barY + 3, Type.argb(0.5f, Type.INK));
		g.fill(cx - half, barY, cx - half + Math.round(half * 2 * ratio), barY + 2, Type.argb(a, Type.EMBER));
		Component count = Component.literal(ruin.restored() + " / " + ruin.total());
		Type.scaled(g, mc.font, count, cx, barY + 6, 0.8f, Type.ASH, a, true);
		compass(g, mc, delta, cx, barY + 17, a);
	}

	private static void compass(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker delta, int cx, int y, float a) {
		var ruin = RuinGhosts.ruin();
		int site = Waymarks.nearest(mc.player.position());
		if (site < 0) return;
		BlockPos target = ruin.sites().get(site);
		Component cost = net.warhorn.siege.RebuildService.costText(ruin.siteLogs().get(site), ruin.siteStone().get(site));
		double dx = target.getX() + 0.5 - mc.player.getX();
		double dz = target.getZ() + 0.5 - mc.player.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < Waymarks.ARRIVED) {
			Component here = Component.translatable("warhorn.rebuild.here", cost);
			Type.scaled(g, mc.font, here, cx, y, 0.85f, Type.EMBER, a, true);
			Component how = Component.translatable("warhorn.rebuild.how").withStyle(st -> st.withItalic(true));
			Type.scaled(g, mc.font, how, cx, y + 11, 0.8f, Type.ASH, a * 0.9f, true);
			return;
		}
		float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float turn = Mth.wrapDegrees(bearing - mc.player.getYRot(delta.getGameTimeDeltaPartialTick(false)));
		Component line = Component.translatable("warhorn.rebuild.next", Math.round(dist), cost);
		int width = Math.round(mc.font.width(line) * 0.85f);
		int size = 12;
		int left = cx - (width + size + 4) / 2;
		g.pose().pushMatrix();
		g.pose().translate(left + size / 2f, y + 3);
		g.pose().rotate((float) Math.toRadians(turn + 180));
		g.blit(RenderPipelines.GUI_TEXTURED, Waymarks.TEXTURE, -size / 2, -size / 2, 0, 0, size, size, size, size,
			Type.argb(a, 0xFFFFFF));
		g.pose().popMatrix();
		Type.scaled(g, mc.font, line, left + size + 4, y, 0.85f, Type.BONE, a, false);
	}
}
