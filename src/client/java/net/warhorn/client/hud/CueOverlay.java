package net.warhorn.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.warhorn.client.cinematic.CinematicPlayer;

public final class CueOverlay {
	private record Card(String title, String subtitle, long start, int duration) {
	}

	private static Card card;
	private static Card toast;
	private static Card hint;
	private static Vec3 hornFrom;
	private static long hornAt;

	private CueOverlay() {
	}

	private static float elapsedTicks(long start) {
		return (System.nanoTime() - start) / 50_000_000f;
	}

	public static void card(String title, String subtitle, int duration) {
		card = new Card(title, subtitle, System.nanoTime(), duration);
	}

	public static void toast(String text, int duration) {
		toast = new Card(text, "", System.nanoTime(), duration);
	}

	public static void hint(String text, int duration) {
		hint = new Card(text, "", System.nanoTime(), duration);
	}

	public static void horn(Vec3 from) {
		hornFrom = from;
		hornAt = System.nanoTime();
	}

	public static void reset() {
		card = toast = hint = null;
		hornFrom = null;
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || CinematicPlayer.active()) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		if (card != null) {
			float t = elapsedTicks(card.start);
			float a = Type.envelope(t, card.duration, 14, 20);
			if (t > card.duration) card = null;
			else {
				float y = h * 0.27f;
				String sub = Component.translatable(card.title).getString().toUpperCase();
				String title = Component.translatable(card.subtitle).getString();
				Type.tracked(g, mc.font, sub, w / 2f, y, 1.0f, 2.2f, Type.EMBER, a, true);
				Type.tracked(g, mc.font, title, w / 2f, y + 13, 2.4f, 0.6f + 0.6f * Mth.clamp(t / card.duration, 0, 1), 1.2f, Type.BONE, a, true);
				int half = Math.round(90 * Type.envelope(t, card.duration, 26, 14));
				Type.rule(g, w / 2, Math.round(y + 39), half, Type.EMBER, a * 0.8f);
			}
		}
		if (toast != null) {
			float t = elapsedTicks(toast.start);
			float a = Type.envelope(t, toast.duration, 4, 12);
			if (t > toast.duration) toast = null;
			else {
				float pop = 1f + 0.25f * Math.max(0, 1 - t / 5f);
				Type.tracked(g, mc.font, Component.translatable(toast.title).getString().toUpperCase(), w / 2f,
					h * 0.42f, 1.6f * pop, 1.4f, Type.BONE, a, true);
			}
		}
		if (hint != null) {
			float t = elapsedTicks(hint.start);
			float a = Type.envelope(t, hint.duration, 16, 24) * 0.92f;
			if (t > hint.duration) hint = null;
			else {
				Component text = Component.translatable(hint.title).withStyle(s -> s.withItalic(true));
				int tw = mc.font.width(text);
				int y = Math.round(h * 0.68f);
				int x = w / 2;
				g.fill(x - tw / 2 - 10, y - 1, x - tw / 2 - 7, y + 2, Type.argb(a, Type.EMBER));
				g.fill(x - tw / 2 - 9, y - 2, x - tw / 2 - 8, y + 3, Type.argb(a, Type.EMBER));
				Type.scaled(g, mc.font, text, x, y - 3, 1.0f, Type.BONE, a, true);
			}
		}
		drawHornChevron(g, mc, w, h);
	}

	private static void drawHornChevron(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
		if (hornFrom == null) return;
		float t = elapsedTicks(hornAt);
		float a = Type.envelope(t, 160, 10, 50) * 0.85f;
		if (t > 160) {
			hornFrom = null;
			return;
		}
		Vec3 d = hornFrom.subtract(mc.player.position());
		double source = Math.atan2(d.z, d.x) - Math.PI / 2;
		double view = Math.toRadians(mc.player.getYRot());
		double rel = Mth.wrapDegrees(Math.toDegrees(source - view));
		if (Math.abs(rel) < 18) a *= Math.abs(rel) / 18f;
		double angle = Math.toRadians(rel);
		float radius = Math.min(w, h) * 0.18f;
		float cx = w / 2f + (float) Math.sin(angle) * radius;
		float cy = h / 2f - (float) Math.cos(angle) * radius;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) angle);
		int col = Type.argb(a, Type.EMBER);
		for (int i = 0; i < 5; i++) {
			g.fill(-i - 1, i, -i + 1, i + 1, col);
			g.fill(i - 1, i, i + 1, i + 1, col);
		}
		g.pose().popMatrix();
	}
}
