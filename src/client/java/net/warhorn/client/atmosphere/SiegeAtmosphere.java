package net.warhorn.client.atmosphere;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.AmbientParticle;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.warhorn.client.SiegeClientState;
import net.warhorn.client.audio.Score;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;

public final class SiegeAtmosphere {
	private static final Vector3fc SKY = new Vector3f(0.26f, 0.05f, 0.05f);
	private static final Vector3fc FOG = new Vector3f(0.17f, 0.06f, 0.06f);
	private static final Vector3fc LIGHT = new Vector3f(0.92f, 0.58f, 0.55f);
	private static final Vector4fc SUNSET = new Vector4f(0.80f, 0.16f, 0.07f, 0.65f);
	private static final Vector4fc CLOUDS = new Vector4f(0.13f, 0.07f, 0.07f, 0.95f);
	private static final Vector3fc DAWN_SKY = new Vector3f(0.98f, 0.72f, 0.45f);
	private static final Vector3fc DAWN_FOG = new Vector3f(1.0f, 0.80f, 0.58f);

	private SiegeAtmosphere() {
	}

	public static void addLayers(EnvironmentAttributeSystem.Builder builder) {
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (base, tick) ->
			dawnLerp(ARGB.srgbLerp(d(), base, SKY), DAWN_SKY, 0.55f));
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (base, tick) ->
			dawnLerp(ARGB.srgbLerp(d() * 0.9f, base, FOG), DAWN_FOG, 0.5f));
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (base, tick) -> Mth.lerp(d(), base, Math.min(base, 6f)));
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (base, tick) -> Mth.lerp(d() * 0.85f, base, Math.min(base, 72f)));
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (base, tick) -> ARGB.srgbLerp(d() * 0.8f, base, LIGHT));
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (base, tick) -> Mth.lerp(d() * 0.6f, base, Math.min(base, 0.5f)));
		builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (base, tick) -> lerp4(base, SUNSET, d()));
		builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (base, tick) -> lerp4(base, CLOUDS, d()));
		builder.addTimeBasedLayer(EnvironmentAttributes.MUSIC_VOLUME, (base, tick) ->
			Score.omen() > 0 || Score.scoring() || net.warhorn.client.cinematic.CinematicPlayer.active() ? 0f : base * (1f - Math.max(d(), SiegeClientState.dawn())));
		builder.addTimeBasedLayer(EnvironmentAttributes.AMBIENT_PARTICLES, (base, tick) -> {
			float d = d();
			if (d < 0.05f) return base;
			List<AmbientParticle> list = new ArrayList<>(base);
			list.add(new AmbientParticle(ParticleTypes.WHITE_ASH, 0.012f * d));
			list.add(new AmbientParticle(ParticleTypes.ASH, 0.006f * d));
			return list;
		});
	}

	private static float d() {
		return SiegeClientState.dread();
	}

	private static Vector3fc dawnLerp(Vector3fc color, Vector3fc dawn, float strength) {
		float w = SiegeClientState.dawn() * strength;
		return w <= 0 ? color : ARGB.srgbLerp(w, color, dawn);
	}

	private static Vector4fc lerp4(Vector4fc a, Vector4fc b, float t) {
		return t <= 0 ? a : ARGB.srgbLerp(t, a, b);
	}
}
