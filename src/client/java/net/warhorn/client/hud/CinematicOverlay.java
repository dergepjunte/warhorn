package net.warhorn.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.warhorn.client.cinematic.CameraEffects;
import net.warhorn.client.cinematic.CinematicPlayer;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Beat;

public final class CinematicOverlay {
	private CinematicOverlay() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth();
		int h = g.guiHeight();
		drawImpact(g, w, h);
		if (!CinematicPlayer.presenting()) return;
		CinematicPayload shot = CinematicPlayer.payload();
		float t = CinematicPlayer.ticks();
		float barTarget = h * 0.115f;
		float bars;
		if (shot != null) {
			float in = Mth.clamp(t / 14f, 0, 1);
			float out = Mth.clamp((shot.durationTicks() - t) / 10f, 0, 1);
			bars = barTarget * smooth(Math.min(in, out));
		} else {
			bars = barTarget * CinematicPlayer.blackout();
		}
		int bar = Math.round(bars);
		if (bar > 0) {
			g.fill(0, 0, w, bar, 0xFF050404);
			g.fill(0, h - bar, w, h, 0xFF050404);
		}
		float black = CinematicPlayer.blackout();
		if (black > 0.01f) g.fill(0, 0, w, h, Type.argb(black, 0x050404));
		if (shot == null) return;
		for (Beat beat : shot.beats()) drawBeat(g, mc.font, beat, t, w, h, bar);
		drawSkip(g, mc.font, w, h, bar);
	}

	private static void drawBeat(GuiGraphicsExtractor g, Font font, Beat beat, float t, int w, int h, int bar) {
		float local = t - beat.tick();
		float alpha = Type.envelope(local, beat.duration(), 12f, 14f);
		if (!Type.visible(alpha)) return;
		String title = beat.title().isEmpty() ? "" : Component.translatable(beat.title()).getString();
		String sub = beat.subtitle().isEmpty() ? "" : Component.translatable(beat.subtitle()).getString();
		switch (beat.style()) {
			case Beat.TITLE -> {
				float tracking = 1.2f + 1.6f * Mth.clamp(local / beat.duration(), 0, 1);
				float y = h * 0.36f - 8 * (1 - alpha);
				Type.tracked(g, font, title.toUpperCase(), w / 2f, y, 3.2f, tracking, 2.8f, Type.BONE, alpha, true);
				int half = Math.round(Math.min(w * 0.22f, 120) * smooth(Mth.clamp(local / 22f, 0, 1)));
				Type.rule(g, w / 2, Math.round(y + 32), half, Type.EMBER, alpha * 0.9f);
				Type.tracked(g, font, sub.toUpperCase(), w / 2f, y + 40, 1.15f, 1.6f, Type.EMBER, alpha, true);
			}
			case Beat.LINE -> {
				int y = h - bar - 22;
				Component line = Component.literal(title).withStyle(s -> s.withItalic(true));
				Type.scaled(g, font, line, w / 2f, y, 1.15f, Type.BONE, alpha * 0.95f, true);
			}
			case Beat.NAMEPLATE -> {
				float sweep = smooth(Mth.clamp(local / 10f, 0, 1));
				int x = Math.round(w * 0.09f);
				int y = Math.round(h * 0.60f);
				g.fill(x, y, x + Math.round(4 * sweep), y + 40, Type.argb(alpha, Type.BLOOD));
				g.fill(x + 6, y + 27, x + 6 + Math.round(170 * sweep), y + 28, Type.argb(alpha * 0.8f, Type.EMBER));
				float reveal = Mth.clamp((local - 4) / 16f, 0, 1);
				String shown = title.substring(0, Math.round(title.length() * reveal));
				Type.scaled(g, font, Component.literal(shown.toUpperCase()), x + 12, y + 2, 2.4f, Type.BONE, alpha, false);
				Type.scaled(g, font, Component.literal(sub), x + 12, y + 32, 1.0f, Type.ASH, alpha * Mth.clamp((local - 14) / 10f, 0, 1), false);
			}
			default -> {
			}
		}
	}

	private static void drawSkip(GuiGraphicsExtractor g, Font font, int w, int h, int bar) {
		float p = CinematicPlayer.skipProgress();
		float hintAlpha = Mth.clamp((CinematicPlayer.ticks() - 30) / 20f, 0, 0.55f) + p * 0.45f;
		Component hint = Component.translatable("warhorn.cinematic.skip", Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage());
		int tw = font.width(hint);
		int x = w - tw - 16;
		int y = h - Math.max(bar, 14) + (Math.max(bar, 14) - 8) / 2;
		g.text(font, hint, x, y, Type.argb(hintAlpha, Type.ASH), false);
		if (p > 0) {
			g.fill(x, y + 10, x + tw, y + 11, Type.argb(0.25f, Type.ASH));
			g.fill(x, y + 10, x + Math.round(tw * p), y + 11, Type.argb(0.9f, Type.EMBER));
		}
	}

	private static void drawImpact(GuiGraphicsExtractor g, int w, int h) {
		float flash = CameraEffects.flashAlpha();
		if (flash > 0.01f) g.fill(0, 0, w, h, Type.argb(flash * 0.75f, 0xFFF4E2));
		float impact = CameraEffects.impactAlpha();
		if (impact > 0.01f) {
			int bands = 10;
			for (int i = 0; i < bands; i++) {
				float a = impact * 0.32f * (1f - i / (float) bands);
				int inset = i * Math.max(2, Math.min(w, h) / 40);
				g.fill(inset, inset, w - inset, inset + Math.max(2, Math.min(w, h) / 40), Type.argb(a, 0x2A0604));
				g.fill(inset, h - inset - Math.max(2, Math.min(w, h) / 40), w - inset, h - inset, Type.argb(a, 0x2A0604));
			}
		}
	}

	private static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}
}
