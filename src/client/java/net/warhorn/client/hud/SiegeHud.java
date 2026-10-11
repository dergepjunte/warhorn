package net.warhorn.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.warhorn.client.SiegeClientState;
import net.warhorn.client.cinematic.CinematicPlayer;
import net.warhorn.net.SiegeStatePayload;
import net.warhorn.siege.ActPlan;
import net.warhorn.siege.SiegeStage;

public final class SiegeHud {
	private static float shownRatio = 1f;
	private static float lossPulse;
	private static int lastVillagers = -1;

	private SiegeHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !SiegeClientState.active() || CinematicPlayer.active()) return;
		SiegeStatePayload s = SiegeClientState.get();
		SiegeStage stage = SiegeClientState.stage();
		if (stage == SiegeStage.FINISHED || stage == SiegeStage.VICTORY || stage == SiegeStage.DEFEAT) return;
		int w = g.guiWidth();
		int cx = w / 2;
		int top = stage == SiegeStage.WARLORD ? 34 : 6;
		float a = 0.92f;

		String chapter = switch (stage) {
			case OMEN, INTRO -> Component.translatable("warhorn.hud.omen").getString();
			case WARLORD_INTRO, WARLORD -> Component.translatable("warhorn.hud.warlord").getString();
			default -> Component.translatable("warhorn.act." + (Mth.clamp(s.act(), 0, 2) + 1)).getString() + "  ·  "
				+ Component.translatable("warhorn.act." + ActPlan.ACTS.get(Mth.clamp(s.act(), 0, 2)).key()).getString();
		};
		Type.tracked(g, mc.font, chapter.toUpperCase(), cx, top, 1.0f, 1.4f, Type.EMBER, a, true);

		int half = 74;
		int barY = top + 12;
		if (stage == SiegeStage.INTERLUDE) {
			float secs = SiegeClientState.timerSeconds();
			String clock = String.format("%d:%02d", (int) secs / 60, (int) secs % 60);
			Type.tracked(g, mc.font, clock, cx, barY - 1, 1.6f, 1.0f, Type.BONE, a, true);
			Component line = Component.translatable("warhorn.hud.interlude").withStyle(st -> st.withItalic(true));
			Type.scaled(g, mc.font, line, cx, barY + 15, 0.85f, Type.ASH, a * 0.9f, true);
			float progress = s.timerMax() == 0 ? 0 : 1f - secs * 20f / s.timerMax();
			g.fill(cx - half, barY + 26, cx + half, barY + 27, Type.argb(0.35f, Type.ASH));
			g.fill(cx - half, barY + 26, cx - half + Math.round(half * 2 * Mth.clamp(progress, 0, 1)), barY + 27,
				Type.argb(a, Type.EMBER));
		} else if (stage == SiegeStage.ACT || stage == SiegeStage.WARLORD) {
			float ratio = s.enemiesTotal() == 0 ? 0 : s.enemiesAlive() / (float) s.enemiesTotal();
			shownRatio += (Mth.clamp(ratio, 0, 1) - shownRatio) * 0.12f;
			g.fill(cx - half - 1, barY - 1, cx + half + 1, barY + 4, Type.argb(0.55f, Type.INK));
			g.fill(cx - half, barY, cx - half + Math.round(half * 2 * shownRatio), barY + 3, Type.argb(a, Type.BLOOD));
			g.fill(cx - half, barY, cx - half + Math.round(half * 2 * shownRatio), barY + 1, Type.argb(a * 0.6f, 0xD8573F));
			Component count = Component.translatable("warhorn.hud.enemies", s.enemiesAlive());
			Type.scaled(g, mc.font, count, cx, barY + 7, 0.85f, Type.ASH, a, true);
		}

		if (s.villagersTotal() > 0) {
			if (lastVillagers >= 0 && s.villagersAlive() < lastVillagers) lossPulse = 1f;
			lastVillagers = s.villagersAlive();
			lossPulse = Math.max(0, lossPulse - 0.02f);
			Component v = Component.translatable("warhorn.hud.villagers", s.villagersAlive(), s.villagersTotal());
			int col = lossPulse > 0 ? lerp(Type.ASH, 0xE0533D, lossPulse) : Type.ASH;
			Type.scaled(g, mc.font, v, cx + half + 10, barY - 1, 0.85f, col, a, false);
		}
	}

	private static int lerp(int c0, int c1, float t) {
		int r = Math.round(Mth.lerp(t, (c0 >> 16) & 255, (c1 >> 16) & 255));
		int gr = Math.round(Mth.lerp(t, (c0 >> 8) & 255, (c1 >> 8) & 255));
		int b = Math.round(Mth.lerp(t, c0 & 255, c1 & 255));
		return (r << 16) | (gr << 8) | b;
	}
}
