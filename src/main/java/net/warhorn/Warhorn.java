package net.warhorn;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.warhorn.command.WarhornCommands;
import net.warhorn.net.WarhornNetworking;
import net.warhorn.registry.WarhornEntities;
import net.warhorn.registry.WarhornSounds;
import net.warhorn.siege.SiegeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Warhorn implements ModInitializer {
	public static final String MOD_ID = "warhorn";
	public static final Logger LOG = LoggerFactory.getLogger("Warhorn");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		WarhornSounds.register();
		WarhornEntities.register();
		WarhornNetworking.register();
		SiegeManager.register();
		WarhornCommands.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) net.warhorn.command.Showcase.register();
		net.warhorn.siege.RebuildScene.register();
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(net.warhorn.siege.RebuildService::use);
	}
}
