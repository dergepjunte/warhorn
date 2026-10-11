package net.warhorn.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import net.warhorn.client.SiegeClientState;
import net.warhorn.entity.WarlordEntity;
import net.warhorn.net.SiegeStatePayload;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.SiegeDirector;

public final class Score {
	private static final long BAR_NANOS = 2_000_000_000L;
	private static final int PULSE = 0, DRIVE = 1, TENSION = 2, THEME = 3, MIGHT = 4;

	private enum Cue {
		NONE(""), INTERLUDE("interlude"), BATTLE("battle"), STORM("storm"), WARLORD("warlord"), RAGE("rage"), LAST("last");

		final String key;

		Cue(String key) {
			this.key = key;
		}
	}

	private static int mood;
	private static float intensity;
	private static Cue cue = Cue.NONE;
	private static final Layer[] layers = new Layer[WarhornSounds.LAYERS.length];
	private static long epoch;
	private static long switchAt;
	private static long riserAt;
	private static long surgeUntil;
	private static float heat;
	private static int hostilesNear;
	private static long lastScan;
	private static int lastOmen;
	private static long omenHornAt;

	private Score() {
	}

	public static void setMood(int newMood, float newIntensity) {
		if (newMood < 0 || newMood > SiegeDirector.SCORE_HUSH) newMood = SiegeDirector.SCORE_SILENT;
		boolean changed = newMood != mood;
		if (mood == SiegeDirector.SCORE_HUSH && (newMood == SiegeDirector.SCORE_BATTLE || newMood == SiegeDirector.SCORE_WARLORD)) {
			surgeUntil = System.nanoTime() + 4 * BAR_NANOS;
		}
		mood = newMood;
		intensity = newIntensity;
		if (changed && mood != SiegeDirector.SCORE_SILENT) Minecraft.getInstance().getMusicManager().stopPlaying();
		if (changed && (mood == SiegeDirector.SCORE_VICTORY || mood == SiegeDirector.SCORE_DEFEAT)) {
			retireAll();
			cue = Cue.NONE;
			play(mood == SiegeDirector.SCORE_VICTORY ? WarhornSounds.STINGER_VICTORY : WarhornSounds.STINGER_DEFEAT, 1f, 1f);
		}
	}

	public static void stop() {
		mood = SiegeDirector.SCORE_SILENT;
		retireAll();
		cue = Cue.NONE;
		switchAt = 0;
		riserAt = 0;
		heat = 0;
	}

	public static void horn(Vec3 from, float strength) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Vec3 ear = mc.player.getEyePosition();
		Vec3 dir = from.subtract(ear);
		double distance = dir.length();
		Vec3 at = distance > 40 ? ear.add(dir.normalize().scale(40)) : from;
		float volume = 3.5f * Mth.clamp(strength, 0.2f, 1f);
		mc.getSoundManager().play(new SimpleSoundInstance(WarhornSounds.WAR_HORN, SoundSource.HOSTILE, volume,
			0.96f + mc.player.getRandom().nextFloat() * 0.08f, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
		if (mood == SiegeDirector.SCORE_BATTLE && (cue == Cue.BATTLE || cue == Cue.STORM) && riserAt == 0) riserAt = nextBar(System.nanoTime());
	}

	public static void sting(String id, float volume) {
		net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
		SoundEvent sound = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(key);
		if (sound != null) play(sound, volume, 1f);
	}

	public static void whoosh() {
		play(WarhornSounds.WHOOSH, 0.55f, 0.9f + (float) Math.random() * 0.2f);
	}

	public static void impact(float strength) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.getSoundManager().play(new SimpleSoundInstance(WarhornSounds.IMPACT.location(), SoundSource.HOSTILE,
			Mth.clamp(strength, 0.2f, 1f), 0.9f + (float) Math.random() * 0.15f, SoundInstance.createUnseededRandom(),
			false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
	}

	public static void frame() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		long now = System.nanoTime();
		updateHeat(mc.player, now);
		int omen = omen();
		if (omen > 0 && lastOmen == 0 && mood == SiegeDirector.SCORE_SILENT) {
			mc.getMusicManager().stopPlaying();
			impact(0.75f);
		}
		lastOmen = omen;
		if (omen == 2 && mood == SiegeDirector.SCORE_SILENT) {
			if (omenHornAt == 0) omenHornAt = now + 2_000_000_000L;
			if (now >= omenHornAt) {
				double a = Math.random() * Math.PI * 2;
				Vec3 from = mc.player.position().add(Math.cos(a) * 90, 6, Math.sin(a) * 90);
				horn(from, 0.3f + (float) Math.random() * 0.2f);
				omenHornAt = now + 6_000_000_000L + (long) (Math.random() * 4_000_000_000L);
			}
		} else {
			omenHornAt = 0;
		}

		Cue wanted = wantedCue(mc);
		if (wanted != cue) {
			if (switchAt == 0) switchAt = cue == Cue.NONE ? now : nextBar(now);
			if (now >= switchAt) {
				retireAll();
				net.warhorn.Warhorn.LOG.debug("score: {} -> {}", cue, wanted);
				cue = wanted;
				switchAt = 0;
				riserAt = 0;
				if (cue != Cue.NONE) start(cue, now);
			}
		} else {
			switchAt = 0;
		}

		if (riserAt != 0 && now >= riserAt) {
			play(WarhornSounds.RISER, 1f, 1f);
			net.warhorn.Warhorn.LOG.debug("score: riser");
			surgeUntil = riserAt + 6 * BAR_NANOS;
			riserAt = 0;
		}

		float[] targets = targets(mc.player, now);
		for (int i = 0; i < layers.length; i++) {
			if (layers[i] != null) layers[i].target = targets[i];
		}
	}

	private static Cue wantedCue(Minecraft mc) {
		return switch (mood) {
			case SiegeDirector.SCORE_OMEN -> Cue.BATTLE;
			case SiegeDirector.SCORE_BATTLE -> SiegeClientState.get().act() >= 2 ? Cue.STORM : Cue.BATTLE;
			case SiegeDirector.SCORE_INTERLUDE -> Cue.INTERLUDE;
			case SiegeDirector.SCORE_WARLORD -> warlordCue(mc);
			case SiegeDirector.SCORE_HUSH -> cue;
			default -> omen() > 0 ? Cue.BATTLE : Cue.NONE;
		};
	}

	public static boolean scoring() {
		return mood != SiegeDirector.SCORE_SILENT;
	}

	public static int omen() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return 0;
		return p.hasEffect(MobEffects.RAID_OMEN) ? 2 : p.hasEffect(MobEffects.BAD_OMEN) ? 1 : 0;
	}

	private static Cue warlordCue(Minecraft mc) {
		int id = SiegeClientState.get().warlordId();
		if (id < 0 || !(mc.level.getEntity(id) instanceof WarlordEntity w)) return Cue.WARLORD;
		return w.lastStand() ? Cue.LAST : w.enraged() ? Cue.RAGE : Cue.WARLORD;
	}

	private static void updateHeat(LocalPlayer player, long now) {
		if (now - lastScan > 250_000_000L) {
			lastScan = now;
			int n = 0;
			for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(20),
				e -> e.isAlive() && (e instanceof Raider || e instanceof WarlordEntity))) {
				n += e instanceof WarlordEntity ? 4 : 1;
			}
			hostilesNear = n;
		}
		boolean fighting = player.hurtTime > 0
			|| player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 80;
		float target = Mth.clamp(hostilesNear * 0.14f + (fighting ? 0.45f : 0f), 0f, 1f);
		heat += (target - heat) * (target > heat ? 0.05f : 0.006f);
	}

	private static float[] targets(LocalPlayer player, long now) {
		float[] t = new float[layers.length];
		SiegeStatePayload s = SiegeClientState.get();
		double distance = s.active() ? player.position().distanceTo(Vec3.atCenterOf(s.center())) : 0;
		float presence = 1f - Mth.clamp((float) ((distance - 96) / 64), 0f, 1f);
		float danger = Mth.clamp(1f - player.getHealth() / player.getMaxHealth() / 0.45f, 0f, 1f);
		int omen = omen();
		if (mood == SiegeDirector.SCORE_HUSH) {
			return t;
		} else if (mood == SiegeDirector.SCORE_OMEN || mood == SiegeDirector.SCORE_SILENT && omen > 0) {
			float k = mood == SiegeDirector.SCORE_OMEN ? intensity : omen == 2 ? 0.75f : 0.3f;
			t[PULSE] = 0.35f + 0.4f * k;
			t[TENSION] = 0.5f + 0.2f * k;
			t[DRIVE] = 0.85f * smooth(0.5f, 0.8f, k);
			t[THEME] = 0.6f * smooth(0.8f, 1f, k);
			t[MIGHT] = 0.7f * smooth(0.88f, 1f, k);
			if (omen == 2 && mood == SiegeDirector.SCORE_SILENT) t[DRIVE] = 0.3f;
		} else if (cue == Cue.INTERLUDE) {
			t[PULSE] = 0.9f;
			t[TENSION] = 1f;
			t[THEME] = 0.8f;
		} else {
			float floor = switch (cue) {
				case STORM -> 0.3f;
				case WARLORD -> 0.6f;
				case RAGE -> 0.85f;
				case LAST -> 0.97f;
				default -> 0.1f;
			};
			float h = Math.max(heat, Math.max(floor, intensity * 0.35f));
			if (now < surgeUntil) h = Math.max(h, 0.95f);
			t[PULSE] = 1f;
			t[TENSION] = 0.45f + 0.55f * Math.max(h, danger);
			t[DRIVE] = smooth(0.12f, 0.4f, h);
			t[THEME] = smooth(0.3f, 0.65f, h);
			t[MIGHT] = smooth(0.55f, 0.9f, h);
		}
		for (int i = 0; i < t.length; i++) t[i] *= presence;
		return t;
	}

	private static float smooth(float from, float to, float x) {
		float k = Mth.clamp((x - from) / (to - from), 0f, 1f);
		return k * k * (3 - 2 * k);
	}

	private static long nextBar(long now) {
		if (cue == Cue.NONE) return now;
		long bars = (now - epoch) / BAR_NANOS + 1;
		return epoch + bars * BAR_NANOS;
	}

	private static void start(Cue next, long now) {
		Minecraft mc = Minecraft.getInstance();
		epoch = now;
		for (int i = 0; i < layers.length; i++) {
			SoundEvent event = WarhornSounds.MUSIC.get(next.key + "_" + WarhornSounds.LAYERS[i]);
			if (event == null) {
				layers[i] = null;
				continue;
			}
			layers[i] = new Layer(event);
			mc.getSoundManager().play(layers[i]);
		}
	}

	private static void retireAll() {
		for (int i = 0; i < layers.length; i++) {
			if (layers[i] != null) layers[i].retire();
			layers[i] = null;
		}
	}

	private static void play(SoundEvent sound, float volume, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.MUSIC, Mth.clamp(volume, 0, 1),
			pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
	}

	private static final class Layer extends AbstractTickableSoundInstance {
		float target;
		private boolean retiring;

		Layer(SoundEvent event) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			relative = true;
			attenuation = Attenuation.NONE;
			volume = 0.0001f;
		}

		void retire() {
			retiring = true;
			target = 0;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			float rate = retiring ? 0.1f : mood == SiegeDirector.SCORE_HUSH ? 0.35f : target > volume ? 0.06f : 0.035f;
			volume += (target - volume) * rate;
			if (retiring && volume < 0.004f) stop();
		}
	}
}
