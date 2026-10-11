package net.warhorn.cinematic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

public final class SlowMotion {
	private static long restoreAtNanos;
	private static float restoreRate = -1;

	private SlowMotion() {
	}

	public static void start(MinecraftServer server, float rate, long realMillis) {
		ServerTickRateManager manager = server.tickRateManager();
		if (restoreRate < 0) restoreRate = manager.tickrate();
		manager.setTickRate(rate);
		restoreAtNanos = System.nanoTime() + realMillis * 1_000_000L;
	}

	public static void tick(MinecraftServer server) {
		if (restoreRate >= 0 && System.nanoTime() >= restoreAtNanos) stop(server);
	}

	public static void stop(MinecraftServer server) {
		if (restoreRate < 0) return;
		server.tickRateManager().setTickRate(restoreRate);
		restoreRate = -1;
	}
}
