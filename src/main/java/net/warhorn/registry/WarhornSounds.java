package net.warhorn.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.warhorn.Warhorn;

import java.util.HashMap;
import java.util.Map;

public final class WarhornSounds {
	public static final SoundEvent WAR_HORN = register("war_horn");
	public static final SoundEvent IMPACT = register("impact");
	public static final SoundEvent STINGER_VICTORY = register("stinger_victory");
	public static final SoundEvent STINGER_DEFEAT = register("stinger_defeat");
	public static final SoundEvent WHOOSH = register("whoosh");
	public static final SoundEvent CINE_BRAAM = register("cine.braam");
	public static final SoundEvent CINE_HIT = register("cine.hit");
	public static final SoundEvent CINE_RISE = register("cine.rise");
	public static final SoundEvent TRAMP = register("tramp");
	public static final SoundEvent STAMPEDE = register("stampede");
	public static final SoundEvent FUSE = register("fuse");
	public static final SoundEvent CATAPULT_WINCH = register("winch");
	public static final SoundEvent CATAPULT_LOOSE = register("catapult_loose");
	public static final SoundEvent FIREPOT_FLIGHT = register("firepot_flight");
	public static final SoundEvent FIREPOT_SHATTER = register("firepot_shatter");

	public static final String[] LAYERS = {"pulse", "drive", "tension", "theme", "might"};
	public static final Map<String, SoundEvent> MUSIC = registerMusic();
	public static final SoundEvent RISER = register("music.riser");

	private WarhornSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Warhorn.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Map<String, SoundEvent> registerMusic() {
		Map<String, SoundEvent> music = new HashMap<>();
		for (String cue : new String[] {"battle", "storm", "warlord", "rage", "last", "interlude"}) {
			for (String layer : LAYERS) {
				if (cue.equals("interlude") && (layer.equals("drive") || layer.equals("might"))) continue;
				music.put(cue + "_" + layer, register("music." + cue + "." + layer));
			}
		}
		return Map.copyOf(music);
	}

	public static void register() {
	}
}
