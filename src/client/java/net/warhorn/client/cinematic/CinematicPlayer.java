package net.warhorn.client.cinematic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Key;
import net.warhorn.net.SkipCinematicPayload;
import net.warhorn.client.audio.Score;

import java.util.ArrayList;
import java.util.List;

public final class CinematicPlayer {
	public record Pose(Vec3 pos, float yaw, float pitch, float fov, float roll) {
	}

	private static final long TICK_NANOS = 50_000_000L;
	public static final float SKIP_HOLD_SECONDS = 1.1f;
	public static final float FADE_TICKS = 10f;
	private static final float OUTRO_TICKS = 12f;

	private static CinematicPayload active;
	private static List<List<Key>> shots = List.of();
	private static long startNanos;
	private static long outroStart = -1;
	private static float skipHeld;
	private static Pose lastPose;
	private static int lastShot = -1;

	private CinematicPlayer() {
	}

	public static void play(CinematicPayload payload) {
		if (payload.keys().isEmpty()) return;
		active = payload;
		lastBase = null;
		lift = 0;
		startNanos = System.nanoTime();
		outroStart = -1;
		skipHeld = 0;
		List<List<Key>> grouped = new ArrayList<>();
		for (Key key : payload.keys()) {
			if (key.cut() || grouped.isEmpty()) grouped.add(new ArrayList<>());
			grouped.getLast().add(key);
		}
		shots = grouped;
		lastShot = -1;
	}

	public static boolean active() {
		return active != null;
	}

	public static boolean presenting() {
		return active != null || outroStart >= 0;
	}

	public static float ticks() {
		return active == null ? 0 : (System.nanoTime() - startNanos) / (float) TICK_NANOS;
	}

	public static CinematicPayload payload() {
		return active;
	}

	public static float skipProgress() {
		return Mth.clamp(skipHeld / SKIP_HOLD_SECONDS, 0, 1);
	}

	public static float blackout() {
		if (active != null) {
			float t = ticks();
			float in = 1f - Mth.clamp(t / FADE_TICKS, 0, 1);
			float out = Mth.clamp((t - (active.durationTicks() - FADE_TICKS)) / FADE_TICKS, 0, 1);
			return Math.max(in, out);
		}
		if (outroStart >= 0) {
			float t = (System.nanoTime() - outroStart) / (float) TICK_NANOS;
			return 1f - Mth.clamp(t / OUTRO_TICKS, 0, 1);
		}
		return 0;
	}

	public static void frame(float realSeconds) {
		Minecraft mc = Minecraft.getInstance();
		if (outroStart >= 0 && (System.nanoTime() - outroStart) / (float) TICK_NANOS > OUTRO_TICKS) outroStart = -1;
		if (active == null) return;
		if (mc.level == null || mc.player == null) {
			stop(false);
			return;
		}
		if (mc.options.keyShift.isDown() && mc.gui.screen() == null) {
			skipHeld += realSeconds;
			if (skipHeld >= SKIP_HOLD_SECONDS) {
				ClientPlayNetworking.send(new SkipCinematicPayload(active.id()));
				stop(true);
				return;
			}
		} else {
			skipHeld = Math.max(0, skipHeld - realSeconds * 2);
		}
		if (ticks() >= active.durationTicks()) stop(true);
	}

	public static void stop(boolean fadeBack) {
		active = null;
		shots = List.of();
		outroStart = fadeBack ? System.nanoTime() : -1;
	}

	public static Pose pose() {
		if (active == null) return null;
		float t = ticks();
		int index = 0;
		for (int i = 0; i < shots.size(); i++) {
			if (shots.get(i).getFirst().tick() <= t) index = i;
		}
		List<Key> shot = shots.get(index);
		if (index != lastShot) {
			lastShot = index;
			Score.whoosh();
		}
		Pose pose = evaluate(shot, t);
		lastPose = pose;
		return pose;
	}

	private static Vec3 lastBase;

	public static Pose lastPose() {
		return lastPose;
	}

	private static Pose evaluate(List<Key> shot, float t) {
		Key first = shot.getFirst();
		Key last = shot.getLast();
		Vec3 trackBase = trackedBase();
		if (shot.size() == 1 || last.tick() <= first.tick()) return toPose(lens(first, trackBase), look(first, trackBase), first.fov(), first.roll());
		float span = last.tick() - first.tick();
		float u = Mth.clamp((t - first.tick()) / span, 0, 1);
		float eased = easeInOut(u);
		float tt = first.tick() + eased * span;
		int i = 0;
		while (i < shot.size() - 2 && shot.get(i + 1).tick() < tt) i++;
		Key k1 = shot.get(i);
		Key k2 = shot.get(i + 1);
		Key k0 = shot.get(Math.max(0, i - 1));
		Key k3 = shot.get(Math.min(shot.size() - 1, i + 2));
		float local = k2.tick() == k1.tick() ? 1 : Mth.clamp((tt - k1.tick()) / (k2.tick() - k1.tick()), 0, 1);
		Vec3 pos = catmullRom(lens(k0, trackBase), lens(k1, trackBase), lens(k2, trackBase), lens(k3, trackBase), local);
		Vec3 look = catmullRom(look(k0, trackBase), look(k1, trackBase), look(k2, trackBase), look(k3, trackBase), local);
		float fov = Mth.lerp(local, k1.fov(), k2.fov());
		float roll = Mth.lerp(local, k1.roll(), k2.roll());
		if (k1.mode() == Key.FOLLOW) pos = surfaced(pos);
		return toPose(pos, look, fov, roll);
	}

	private static Vec3 trackedBase() {
		if (active == null || active.trackEntity() < 0) return null;
		Minecraft mc = Minecraft.getInstance();
		Entity e = mc.level == null ? null : mc.level.getEntity(active.trackEntity());
		if (e == null) return lastBase;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		lastBase = e.getPosition(partial);
		return lastBase;
	}

	private static float lift;

	private static Vec3 surfaced(Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();
		float wanted = 0;
		if (mc.level != null) {
			while (wanted < 4 && !open(mc, pos.add(0, wanted, 0))) wanted += 0.5f;
		}
		lift += (wanted - lift) * (wanted > lift ? 0.35f : 0.08f);
		return pos.add(0, lift, 0);
	}

	private static boolean open(Minecraft mc, Vec3 at) {
		net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(at);
		return mc.level.getBlockState(p).isAir() && mc.level.getFluidState(p).isEmpty();
	}

	private static Vec3 lens(Key key, Vec3 trackBase) {
		if (key.mode() != Key.FOLLOW) return key.pos();
		return trackBase == null ? key.pos() : trackBase.add(key.pos());
	}

	private static Vec3 look(Key key, Vec3 trackBase) {
		if (key.mode() == Key.FIXED) return key.look();
		if (trackBase == null) return key.mode() == Key.FOLLOW ? key.pos().add(key.look()) : key.pos().add(key.look());
		return trackBase.add(key.look());
	}

	private static Pose toPose(Vec3 pos, Vec3 look, float fov, float roll) {
		Vec3 d = look.subtract(pos);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);
		return new Pose(pos, yaw, pitch, fov, roll);
	}

	static float easeInOut(float x) {
		return (float) (-(Math.cos(Math.PI * x) - 1) / 2);
	}

	static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return new Vec3(
			0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
			0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
			0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
	}
}
