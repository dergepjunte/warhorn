package net.warhorn.client.qa;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelSettings.DifficultySettings;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.warhorn.Warhorn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

public final class QaRunner {
	private static final String WORLD = System.getProperty("warhorn.qa.world", "WarhornQA");
	private static final Deque<String> SCRIPT = new ArrayDeque<>();
	private static boolean launched;
	private static boolean entered;
	private static int waitTicks;

	private QaRunner() {
	}

	public static void register() {
		String script = System.getProperty("warhorn.qa");
		if (script == null) return;
		try {
			SCRIPT.addAll(Files.readAllLines(Path.of(script)));
		} catch (IOException e) {
			Warhorn.LOG.error("QA script unreadable: {}", script, e);
			ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
				if (screen instanceof TitleScreen) client.execute(client::stop);
			});
			return;
		}
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof TitleScreen)) return;
			if (!launched) {
				launched = true;
				client.execute(() -> openWorld(client));
			} else if (!entered) {
				Warhorn.LOG.error("QA world {} could not be opened; quitting", WORLD);
				client.execute(client::stop);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(QaRunner::tick);
	}

	private static void openWorld(Minecraft mc) {
		if (mc.getLevelSource().levelExists(WORLD)) {
			mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.gui.setScreen(new TitleScreen()));
			return;
		}
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
			new DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
		long seed = WORLD.equals("WarhornQA") ? 20261008L : new java.util.Random().nextLong();
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(seed, true, false),
			WorldPresets::createNormalWorldDimensions, new TitleScreen());
	}

	private static void tick(Minecraft mc) {
		if (mc.player != null) entered = true;
		if (mc.player == null || mc.getConnection() == null || SCRIPT.isEmpty()) return;
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		String line = SCRIPT.poll().trim();
		if (line.isEmpty() || line.startsWith("#")) return;
		String arg = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : "";
		switch (line.split(" ")[0]) {
			case "wait" -> waitTicks = Integer.parseInt(arg);
			case "cmd" -> mc.getConnection().sendCommand(arg);
			case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.gameRenderer.mainRenderTarget(), 1,
				msg -> Warhorn.LOG.info("QA shot {}", arg));
			case "hud" -> {
				if (mc.gui.hud.isHidden() == arg.equals("on")) mc.gui.hud.toggle();
			}
			case "use" -> {
				if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
					mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				} else {
					Warhorn.LOG.warn("QA: use with no block under the crosshair");
				}
			}
			case "quit" -> mc.stop();
			default -> Warhorn.LOG.warn("QA: unknown line {}", line);
		}
	}
}
