package net.warhorn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.warhorn.net.SiegeStatePayload;
import net.warhorn.siege.SiegeStage;

public final class SiegeClientState {
	private static SiegeStatePayload state = SiegeStatePayload.INACTIVE;
	private static long receivedAt;
	private static float dread;
	private static float dawn;

	private SiegeClientState() {
	}

	public static void accept(SiegeStatePayload payload) {
		state = payload;
		receivedAt = System.nanoTime();
	}

	public static void reset() {
		state = SiegeStatePayload.INACTIVE;
		dread = 0;
		dawn = 0;
	}

	public static SiegeStatePayload get() {
		return state;
	}

	public static boolean active() {
		return state.active();
	}

	public static SiegeStage stage() {
		SiegeStage[] stages = SiegeStage.values();
		return state.stage() >= 0 && state.stage() < stages.length ? stages[state.stage()] : SiegeStage.FINISHED;
	}

	public static float timerSeconds() {
		float elapsed = (System.nanoTime() - receivedAt) / 1_000_000_000f;
		return Math.max(0, state.timer() / 20f - elapsed);
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		float target = 0;
		float dawnTarget = 0;
		if (state.active() && mc.player != null) {
			double d = mc.player.position().distanceTo(Vec3.atCenterOf(state.center()));
			float falloff = 1f - Mth.clamp((float) ((d - 96) / 64), 0, 1);
			target = state.dread() * falloff;
			if (stage() == SiegeStage.VICTORY) dawnTarget = falloff;
		}
		dread += (target - dread) * 0.04f;
		dawn += (dawnTarget - dawn) * 0.03f;
		if (Math.abs(dread - target) < 0.001f) dread = target;
	}

	public static float dread() {
		return dread;
	}

	public static float dawn() {
		return dawn;
	}
}
