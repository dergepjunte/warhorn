package net.warhorn.client.cinematic;

import net.minecraft.util.Mth;

public final class CameraEffects {
	private static float trauma;
	private static float fovPunch;
	private static float flash;
	private static float impactFrame;
	private static double time;

	private CameraEffects() {
	}

	public static void addTrauma(float amount) {
		trauma = Math.min(1f, trauma + amount);
	}

	public static void punchFov(float amount) {
		fovPunch = Math.min(1f, fovPunch + amount);
	}

	public static void flash(float amount) {
		flash = Math.max(flash, Math.min(1f, amount));
	}

	public static void impactFrame(float amount) {
		impactFrame = Math.max(impactFrame, amount);
	}

	public static void frame(float seconds) {
		time += seconds;
		trauma = Math.max(0, trauma - seconds * 0.9f);
		fovPunch = Math.max(0, fovPunch - seconds * 3.2f);
		flash = Math.max(0, flash - seconds * 2.2f);
		impactFrame = Math.max(0, impactFrame - seconds * 6f);
	}

	private static float shake() {
		return trauma * trauma;
	}

	public static float yawOffset() {
		return shake() * 6.0f * noise(time * 23.0, 0.0);
	}

	public static float pitchOffset() {
		return shake() * 4.5f * noise(time * 23.0, 37.0);
	}

	public static float rollOffset() {
		return shake() * 5.0f * noise(time * 17.0, 71.0);
	}

	public static float fovScale() {
		float p = fovPunch;
		return 1f + 0.12f * p * p;
	}

	public static float flashAlpha() {
		return flash * flash;
	}

	public static float impactAlpha() {
		return impactFrame;
	}

	private static float noise(double x, double seed) {
		double xs = x + seed;
		int i = Mth.floor(xs);
		double f = xs - i;
		double u = f * f * (3 - 2 * f);
		return (float) Mth.lerp(u, hash(i), hash(i + 1));
	}

	private static double hash(int i) {
		long h = i * 0x9E3779B97F4A7C15L;
		h ^= (h >>> 31);
		h *= 0xBF58476D1CE4E5B9L;
		h ^= (h >>> 29);
		return ((h & 0xFFFF) / 32767.5) - 1.0;
	}
}
