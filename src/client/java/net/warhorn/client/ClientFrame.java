package net.warhorn.client;

import net.warhorn.client.audio.Score;
import net.warhorn.client.cinematic.CameraEffects;
import net.warhorn.client.cinematic.CinematicPlayer;

public final class ClientFrame {
	private static long lastFrame = System.nanoTime();

	private ClientFrame() {
	}

	public static void run() {
		long now = System.nanoTime();
		float seconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		CameraEffects.frame(seconds);
		CinematicPlayer.frame(seconds);
		Score.frame();
	}
}
