package net.warhorn.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.warhorn.entity.WarlordEntity;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;
import net.warhorn.siege.SiegeStage;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class WarhornCommands {
	private WarhornCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
			literal("warhorn")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("start")
					.executes(c -> start(c, 1))
					.then(argument("omen", IntegerArgumentType.integer(1, 5))
						.executes(c -> start(c, IntegerArgumentType.getInteger(c, "omen")))))
				.then(literal("stop").executes(WarhornCommands::stop))
				.then(literal("act")
					.then(argument("act", IntegerArgumentType.integer(1, 3))
						.executes(c -> jump(c, SiegeStage.ACT, IntegerArgumentType.getInteger(c, "act") - 1))))
				.then(literal("warlord").executes(c -> jump(c, SiegeStage.WARLORD_INTRO, 2)))
				.then(literal("victory").executes(c -> jump(c, SiegeStage.VICTORY, 2)))
				.then(literal("defeat").executes(c -> jump(c, SiegeStage.DEFEAT, 2)))
				.then(literal("status").executes(WarhornCommands::status))
				.then(literal("village").executes(WarhornCommands::toVillage))
				.then(literal("kit")
					.executes(c -> kit(c, 1))
					.then(argument("count", IntegerArgumentType.integer(1, Kit.MAX))
						.executes(c -> kit(c, IntegerArgumentType.getInteger(c, "count")))))
				.then(literal("pose")
					.then(argument("action", StringArgumentType.word())
						.then(argument("tick", IntegerArgumentType.integer(0, 400))
							.executes(c -> pose(c, false))
							.then(literal("laststand").executes(c -> pose(c, true))))))
				.then(literal("spawn")
					.then(literal("sapper").executes(WarhornCommands::spawnSapper))
					.then(literal("ram").executes(WarhornCommands::spawnRam))
					.then(literal("kidnapper").executes(WarhornCommands::spawnKidnapper))
					.then(literal("catapult").executes(WarhornCommands::spawnCatapult)))
				.then(literal("intro")
					.then(literal("sapper").executes(c -> intro(c, "sapper")))
					.then(literal("ram").executes(c -> intro(c, "ram")))
					.then(literal("kidnapper").executes(c -> intro(c, "kidnapper")))
					.then(literal("catapult").executes(c -> intro(c, "catapult"))))
				.then(literal("rebuild").executes(WarhornCommands::rebuild))
				.then(literal("house").executes(WarhornCommands::toHouse))
				.then(literal("showcase").executes(c -> Showcase.start(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(literal("mend")
					.executes(c -> mend(c, Integer.MAX_VALUE))
					.then(argument("count", IntegerArgumentType.integer(1))
						.executes(c -> mend(c, IntegerArgumentType.getInteger(c, "count")))))));
	}

	private static int pose(CommandContext<CommandSourceStack> c, boolean lastStand) {
		String name = StringArgumentType.getString(c, "action").toUpperCase(java.util.Locale.ROOT);
		int combo = 0;
		if (name.matches("SWING[0-2]")) {
			combo = name.charAt(5) - '0';
			name = "SWING";
		}
		WarlordEntity.Action action;
		try {
			action = WarlordEntity.Action.valueOf(name);
		} catch (IllegalArgumentException e) {
			c.getSource().sendFailure(Component.literal("Unknown action " + name));
			return 0;
		}
		var warlords = c.getSource().getLevel().getEntitiesOfClass(WarlordEntity.class,
			new net.minecraft.world.phys.AABB(BlockPos.containing(c.getSource().getPosition())).inflate(48));
		int tick = IntegerArgumentType.getInteger(c, "tick");
		for (WarlordEntity w : warlords) w.holdPose(action, combo, tick, lastStand);
		return warlords.size();
	}

	private static int start(CommandContext<CommandSourceStack> c, int omen) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		Siege siege = SiegeManager.start(level, at, omen);
		c.getSource().sendSuccess(() -> Component.translatable("warhorn.command.started", siege.id(),
			siege.staging().toShortString()), true);
		return siege.id();
	}

	private static Siege nearest(CommandContext<CommandSourceStack> c) {
		return SiegeManager.find(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 160);
	}

	private static int stop(CommandContext<CommandSourceStack> c) {
		Siege siege = nearest(c);
		if (siege == null) return none(c);
		SiegeManager.stop(c.getSource().getLevel(), siege);
		c.getSource().sendSuccess(() -> Component.translatable("warhorn.command.stopped", siege.id()), true);
		return 1;
	}

	private static int jump(CommandContext<CommandSourceStack> c, SiegeStage stage, int act) {
		Siege siege = nearest(c);
		if (siege == null) return none(c);
		SiegeManager.skipTo(c.getSource().getLevel(), siege, stage, act);
		c.getSource().sendSuccess(() -> Component.translatable("warhorn.command.jumped", stage.name()), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		Siege siege = nearest(c);
		if (siege == null) return none(c);
		String line = "Siege #" + siege.id() + " " + siege.stage()
			+ " t=" + siege.stageTicks() + " act=" + (siege.act() + 1) + " wave=" + (siege.wave() + 1)
			+ " raiders=" + siege.raidersAlive() + " villagers=" + siege.villagersAlive() + "/" + siege.villagersAtStart()
			+ " ruined=" + siege.ledger().size() + " arsons_left=" + siege.arsonsLeft();
		net.warhorn.Warhorn.LOG.info("status: {}", line);
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int rebuild(CommandContext<CommandSourceStack> c) {
		int placed = net.warhorn.siege.RebuildService.rebuildHouse(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()),
			c.getSource().getPlayer());
		if (placed < 0) return none(c);
		c.getSource().sendSuccess(() -> Component.translatable("warhorn.command.rebuilt", placed), false);
		return placed;
	}

	private static int toHouse(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		var player = c.getSource().getPlayerOrException();
		var house = net.warhorn.siege.RebuildService.house(level, player.blockPosition(), 160);
		if (house == null || house.entries().isEmpty()) return none(c);
		var hole = house.entries().stream().min(java.util.Comparator.comparingInt(e -> e.pos().getY())).orElseThrow().pos();
		net.minecraft.world.phys.Vec3 mid = net.minecraft.world.phys.Vec3.atCenterOf(house.marker()).multiply(1, 0, 1);
		net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(hole).multiply(1, 0, 1).subtract(mid);
		if (from.lengthSqr() < 1e-3) from = new net.minecraft.world.phys.Vec3(1, 0, 0);
		net.minecraft.world.phys.Vec3 at = mid.add(from.normalize().scale(from.length() + 4));
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(at.x, ground.getY() + 1.62, at.z);
		net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.atCenterOf(hole.below()).subtract(eye);
		float yaw = (float) (Math.atan2(look.z, look.x) * 180 / Math.PI) - 90f;
		float pitch = (float) -(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * 180 / Math.PI);
		player.teleportTo(level, at.x, ground.getY(), at.z, java.util.Set.of(), yaw, pitch, true);
		c.getSource().sendSuccess(() -> Component.literal("House at " + house.marker().toShortString() + ": " + house.entries().size()
			+ " missing, " + house.logs() + " logs, " + house.stone() + " cobblestone"), false);
		return 1;
	}

	private static int mend(CommandContext<CommandSourceStack> c, int count) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		var ruin = net.warhorn.siege.RebuildRegistry.get(level).ruins().stream()
			.filter(r -> r.center().distSqr(at) < 160 * 160)
			.min(java.util.Comparator.comparingDouble(r -> r.center().distSqr(at))).orElse(null);
		if (ruin == null) return none(c);
		int placed = 0;
		for (var entry : java.util.List.copyOf(ruin.ledger().view().values())) {
			if (placed >= count) break;
			level.setBlock(entry.pos(), entry.original(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			placed++;
		}
		int n = placed;
		net.warhorn.Warhorn.LOG.info("mend: placed {} of {} lost blocks", n, ruin.total());
		c.getSource().sendSuccess(() -> Component.literal("Mended " + n + " of " + ruin.total()), false);
		return n;
	}

	private static int toVillage(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		var player = c.getSource().getPlayerOrException();
		var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
			net.minecraft.resources.Identifier.withDefaultNamespace("village"));
		BlockPos village = level.findNearestMapStructure(tag, player.blockPosition(), 100, false);
		if (village == null) return none(c);
		level.getChunk(village);
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village);
		player.teleportTo(ground.getX() + 0.5, ground.getY() + 0.1, ground.getZ() + 0.5);
		c.getSource().sendSuccess(() -> Component.literal("Village at " + ground.toShortString()), false);
		return 1;
	}

	private static int kit(CommandContext<CommandSourceStack> c, int count) {
		Kit.place(c.getSource().getLevel(), c.getSource().getPosition(), c.getSource().getRotation().y, count);
		c.getSource().sendSuccess(() -> Component.translatable(count == 1 ? "warhorn.command.kit" : "warhorn.command.kits", count), false);
		return count;
	}

	private static int spawnSapper(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		Vec3 eye = c.getSource().getPosition();
		Vec3 look = Vec3.directionFromRotation(0, c.getSource().getRotation().y);
		BlockPos column = BlockPos.containing(eye.add(look.scale(10)));
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		var sapper = net.warhorn.registry.WarhornEntities.SAPPER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (sapper == null) return 0;
		sapper.snapTo(Vec3.atBottomCenterOf(ground), c.getSource().getRotation().y + 180, 0);
		sapper.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
		Siege siege = nearest(c);
		sapper.aimAt(siege != null ? siege.center() : BlockPos.containing(eye));
		level.addFreshEntity(sapper);
		return 1;
	}

	private static int spawnKidnapper(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		Vec3 eye = c.getSource().getPosition();
		Vec3 look = Vec3.directionFromRotation(0, c.getSource().getRotation().y);
		BlockPos column = BlockPos.containing(eye.add(look.scale(10)));
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		var kidnapper = net.warhorn.registry.WarhornEntities.KIDNAPPER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (kidnapper == null) return 0;
		kidnapper.snapTo(Vec3.atBottomCenterOf(ground), c.getSource().getRotation().y + 180, 0);
		kidnapper.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
		Siege siege = nearest(c);
		kidnapper.sendAgainst(siege != null ? siege.center() : BlockPos.containing(eye), siege != null ? siege.staging() : null);
		level.addFreshEntity(kidnapper);
		return 1;
	}

	private static int spawnCatapult(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		Vec3 eye = c.getSource().getPosition();
		float yaw = c.getSource().getRotation().y;
		BlockPos column = BlockPos.containing(eye.add(Vec3.directionFromRotation(0, yaw).scale(16)));
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		Siege siege = nearest(c);
		var crew = net.warhorn.entity.CatapultEntity.spawn(level, null, siege != null ? siege.center() : BlockPos.containing(eye),
			Vec3.atBottomCenterOf(ground), yaw + 180, false);
		return crew.size();
	}

	private static int spawnRam(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		Vec3 eye = c.getSource().getPosition();
		float yaw = c.getSource().getRotation().y;
		BlockPos ground = null;
		for (int i = 0; i < 40 && ground == null; i++) {
			int distance = 12 + i / 8 * 4;
			float turn = (i % 8 + 1) / 2 * 25f * (i % 2 == 0 ? 1 : -1);
			BlockPos column = BlockPos.containing(eye.add(Vec3.directionFromRotation(0, yaw + turn).scale(distance)));
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			var under = level.getBlockState(top.below());
			if (!net.warhorn.siege.Ruination.isBuilding(under) || under.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) ground = top;
		}
		if (ground == null) {
			ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(eye.add(Vec3.directionFromRotation(0, yaw).scale(12))));
		}
		Siege siege = nearest(c);
		var crew = net.warhorn.entity.BatteringRamEntity.spawn(level, siege, siege != null ? siege.center() : BlockPos.containing(eye),
			Vec3.atBottomCenterOf(ground), yaw + 180, false);
		return crew.size();
	}

	private static int intro(CommandContext<CommandSourceStack> c, String key) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		var player = c.getSource().getPlayerOrException();
		var unit = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(96),
				m -> key.equals(net.warhorn.siege.UnitIntros.key(m)))
			.stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		if (unit == null) return none(c);
		Siege siege = nearest(c);
		net.warhorn.siege.UnitIntros.show(level, siege != null ? siege.id() : -1000, siege != null ? siege.center() : player.blockPosition(),
			java.util.List.of(player), unit, key);
		return 1;
	}

	private static int none(CommandContext<CommandSourceStack> c) {
		c.getSource().sendFailure(Component.translatable("warhorn.command.none"));
		return 0;
	}
}
