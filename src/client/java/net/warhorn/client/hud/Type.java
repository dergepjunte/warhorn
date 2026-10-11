package net.warhorn.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class Type {
	static final int BONE = 0xEDE3D1;
	static final int EMBER = 0xD9A44A;
	static final int BLOOD = 0x9E2418;
	static final int ASH = 0xB9AE9E;
	static final int INK = 0x0B0807;

	private Type() {
	}

	static int argb(float alpha, int rgb) {
		int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	static boolean visible(float alpha) {
		return alpha > 0.03f;
	}

	static float width(Font font, String text, float scale, float tracking) {
		float w = 0;
		for (int i = 0; i < text.length(); i++) {
			w += font.width(String.valueOf(text.charAt(i))) + tracking;
		}
		return (w - tracking) * scale;
	}

	static void tracked(GuiGraphicsExtractor g, Font font, String text, float cx, float y, float scale, float tracking,
		int rgb, float alpha, boolean shadow) {
		tracked(g, font, text, cx, y, scale, tracking, tracking, rgb, alpha, shadow);
	}

	static void tracked(GuiGraphicsExtractor g, Font font, String text, float cx, float y, float scale, float tracking,
		float widestTracking, int rgb, float alpha, boolean shadow) {
		if (!visible(alpha)) return;
		float natural = width(font, text, scale, Math.max(tracking, widestTracking));
		float limit = g.guiWidth() * 0.9f;
		if (natural > limit) scale *= limit / natural;
		float x = cx - width(font, text, scale, tracking) / 2f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		for (int i = 0; i < text.length(); i++) {
			String glyph = String.valueOf(text.charAt(i));
			if (shadow) g.text(font, glyph, 1, 1, argb(alpha * 0.55f, INK), false);
			g.text(font, glyph, 0, 0, argb(alpha, rgb), false);
			g.pose().translate(font.width(glyph) + tracking, 0);
		}
		g.pose().popMatrix();
	}

	static void scaled(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, int rgb,
		float alpha, boolean centered) {
		if (!visible(alpha)) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		int w = font.width(text);
		int dx = centered ? -w / 2 : 0;
		g.text(font, text, dx + 1, 1, argb(alpha * 0.5f, INK), false);
		g.text(font, text, dx, 0, argb(alpha, rgb), false);
		g.pose().popMatrix();
	}

	static void rule(GuiGraphicsExtractor g, int cx, int y, int halfWidth, int rgb, float alpha) {
		if (!visible(alpha) || halfWidth <= 0) return;
		g.fill(cx - halfWidth, y, cx + halfWidth, y + 1, argb(alpha, rgb));
		g.fill(cx - halfWidth - 3, y - 1, cx - halfWidth - 1, y + 2, argb(alpha, rgb));
		g.fill(cx + halfWidth + 1, y - 1, cx + halfWidth + 3, y + 2, argb(alpha, rgb));
	}

	static float envelope(float t, float duration, float in, float out) {
		if (t < 0 || t > duration) return 0;
		float a = Mth.clamp(t / in, 0, 1);
		float b = Mth.clamp((duration - t) / out, 0, 1);
		float e = Math.min(a, b);
		return e * e * (3 - 2 * e);
	}
}
