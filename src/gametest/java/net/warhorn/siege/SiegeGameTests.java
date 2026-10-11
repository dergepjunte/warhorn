package net.warhorn.siege;

import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.phys.Vec3;
import net.warhorn.entity.BatteringRamEntity;
import net.warhorn.entity.PowderKegEntity;
import net.warhorn.entity.CatapultEntity;
import net.warhorn.entity.FirePotEntity;
import net.warhorn.entity.KidnapperEntity;
import net.warhorn.entity.SapperEntity;
import net.warhorn.entity.WarBannerEntity;
import net.warhorn.entity.WarlordAxeEntity;
import net.warhorn.entity.WarlordEntity;
import net.warhorn.registry.WarhornEntities;

public class SiegeGameTests {
	@GameTest
	public void ledgerRoundTripKeepsFirstState(GameTestHelper helper) {
		DestructionLedger ledger = new DestructionLedger();
		BlockPos a = new BlockPos(1, 2, 3);
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		ledger.record(a, Blocks.OAK_PLANKS.defaultBlockState());
		ledger.record(a, Blocks.FIRE.defaultBlockState());
		ledger.record(a.above(), door);
		ledger.record(a.below(), Blocks.AIR.defaultBlockState());
		DynamicOps<Tag> ops = NbtOps.INSTANCE;
		Tag tag = DestructionLedger.CODEC.encodeStart(ops, ledger).getOrThrow();
		DestructionLedger back = DestructionLedger.CODEC.parse(ops, tag).getOrThrow();
		helper.assertTrue(back.size() == 2, "ledger should hold two entries, has " + back.size());
		helper.assertTrue(back.get(a).original().is(Blocks.OAK_PLANKS), "first recorded state must win");
		helper.assertTrue(back.get(a.above()).original().equals(door), "door half must survive the round trip");
		helper.succeed();
	}

	@GameTest
	public void ruinSplitsIntoItsHouses(GameTestHelper helper) {
		DestructionLedger ledger = new DestructionLedger();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		for (int x = 0; x < 4; x++) for (int y = 64; y < 68; y++) ledger.record(new BlockPos(x, y, 0), planks);
		for (int z = 0; z < 3; z++) ledger.record(new BlockPos(30, 65, z), planks);
		ledger.record(new BlockPos(80, 64, 80), planks);
		var sites = RebuildService.sites(new RebuildRegistry.Ruin(BlockPos.ZERO, ledger, ledger.size(), 0));
		helper.assertTrue(sites.size() == 2, "two houses, the stray block left to the ghosts: " + sites);
		helper.assertTrue(sites.get(0).missing() == 16 && sites.get(1).missing() == 3, "biggest wound first: " + sites);
		helper.assertTrue(sites.get(0).marker().getY() == 68, "marker sits above the highest hole: " + sites.get(0));
		helper.assertTrue(sites.get(1).marker().getX() == 30, "second marker over the second house: " + sites.get(1));
		helper.succeed();
	}

	@GameTest
	public void siegeFireRecordsTheCascade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
		BlockPos doorPos = floor.above();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.OAK_PLANKS);

		Siege siege = new Siege(-1, floor, floor.east(50), 1);
		siege.stage = SiegeStage.ACT;
		SiegeSavedData data = SiegeManager.data(level);
		data.sieges.add(siege);
		try {
			Ruination.begin();
			try {
				level.removeBlock(doorPos, false);
				level.removeBlock(helper.absolutePos(new BlockPos(2, 1, 1)), false);
			} finally {
				Ruination.end();
			}
			helper.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_PLANKS);
			level.removeBlock(helper.absolutePos(new BlockPos(3, 1, 1)), false);

			helper.assertTrue(siege.ledger.size() == 3, "door (both halves) and planks expected, ledger has " + siege.ledger.size());
			helper.assertTrue(siege.ledger.get(doorPos.above()) != null, "the upper door half fell with the lower one and must be recorded");
		} finally {
			data.sieges.remove(siege);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 1600)
	public void siegeFireIsRecorded(GameTestHelper helper) {
		Siege siege = fireSetup(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(siege.ledger.size() > 0, "fire inside a siege should have consumed and recorded planks");
			SiegeManager.data(helper.getLevel()).sieges.remove(siege);
		});
	}

	@GameTest(maxTicks = 400)
	public void exhaustedBudgetStarvesTheFire(GameTestHelper helper) {
		Siege siege = fireSetup(helper);
		for (int i = 0; i < Ruination.fireBudget(siege); i++) {
			siege.ledger.record(new BlockPos(0, -60, i), Blocks.OAK_PLANKS.defaultBlockState());
		}
		BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.runAfterDelay(380, () -> {
			SiegeManager.data(helper.getLevel()).sieges.remove(siege);
			long burnt = siege.ledger.view().keySet().stream().filter(p -> p.distSqr(center) < 9).count();
			helper.assertTrue(burnt == 0, "nothing may burn once the budget is spent");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, 1, 1));
			helper.succeed();
		});
	}

	private static Siege fireSetup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, level.getServer());
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
			}
		}
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.FIRE);
		BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
		Siege siege = new Siege(-2, center, center.east(50), 1);
		siege.stage = SiegeStage.ACT;
		siege.act = 1;
		siege.villagersAtStart = -1;
		SiegeManager.data(level).sieges.add(siege);
		return siege;
	}

	@GameTest
	public void chargeBreaksWoodButNotEarthOrStone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		java.util.Map<net.minecraft.world.level.block.Block, Boolean> expected = new java.util.LinkedHashMap<>();
		expected.put(Blocks.OAK_PLANKS, true);
		expected.put(Blocks.OAK_DOOR, true);
		expected.put(Blocks.GLASS, true);
		expected.put(Blocks.GLASS_PANE, true);
		expected.put(Blocks.STONE, false);
		expected.put(Blocks.COBBLESTONE, false);
		expected.put(Blocks.DIRT, false);
		expected.put(Blocks.GRASS_BLOCK, false);
		expected.put(Blocks.DIRT_PATH, false);
		expected.put(Blocks.SAND, false);
		int x = 0;
		for (var e : expected.entrySet()) {
			BlockPos rel = new BlockPos(x % 3, 1 + x / 3, 0);
			helper.setBlock(rel, e.getKey());
			BlockPos abs = helper.absolutePos(rel);
			boolean yields = net.warhorn.entity.WarlordEntity.yields(level, abs, level.getBlockState(abs));
			helper.assertTrue(yields == e.getValue(), e.getKey() + " should " + (e.getValue() ? "" : "not ") + "give way");
			helper.setBlock(rel, Blocks.AIR);
			x++;
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void thrownAxeComesBackToHisHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		WarlordEntity warlord = helper.spawn(WarhornEntities.WARLORD, new BlockPos(1, 1, 1));
		warlord.setNoAi(true);
		warlord.holdPose(WarlordEntity.Action.UNARMED, 0, 0, false);
		Vec3 hand = warlord.handPosition();
		WarlordAxeEntity.launch(level, warlord, hand, hand.add(4, 0, 3), 1f);
		helper.assertFalse(warlord.armed(), "he must be empty-handed while the axe flies");
		helper.succeedWhen(() -> {
			helper.assertTrue(warlord.armed(), "the axe has not come back yet");
			helper.assertTrue(level.getEntitiesOfClass(WarlordAxeEntity.class, warlord.getBoundingBox().inflate(40)).isEmpty(),
				"the axe must be gone once caught");
		});
	}

	private static final int WALL = 40;

	private static void wall(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		for (int x = 0; x < 8; x++) for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, 4), Blocks.OAK_PLANKS);
	}

	private static int planks(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x < 8; x++) for (int y = 1; y <= 5; y++) if (helper.getBlockState(new BlockPos(x, y, 4)).is(Blocks.OAK_PLANKS)) n++;
		return n;
	}

	@GameTest(maxTicks = 220)
	public void kegBlowsAHoleInTheWallButNotTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		PowderKegEntity.plant(level, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))), 0f, false);
		helper.runAfterDelay(PowderKegEntity.FUSE + 10, () -> {
			int left = planks(helper);
			helper.assertTrue(left < WALL - 12, "the blast must tear a hole in the wall, " + left + " of " + WALL + " planks left");
			helper.assertTrue(left > 0, "a keg makes a hole, not a crater: " + left + " planks left");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 3)).is(Blocks.STONE), "the ground is not a building");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void oneBlowCutsTheFuse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		PowderKegEntity keg = PowderKegEntity.plant(level, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))), 0f, false);
		Vindicator ally = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(6, 1, 1));
		keg.hurtServer(level, level.damageSources().mobAttack(ally), 5f);
		helper.assertFalse(keg.isRemoved(), "illagers must not put out their own fuse");
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		keg.hurtServer(level, level.damageSources().playerAttack(player), 1f);
		helper.assertTrue(keg.isRemoved(), "one blow from a defender must cut the fuse");
		helper.runAfterDelay(PowderKegEntity.FUSE + 10, () -> {
			helper.assertTrue(planks(helper) == WALL, "a cut fuse must leave the wall standing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void sapperRunsHisKegToTheWallAndLightsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		BlockPos[] found = SapperEntity.findWall(level, helper.absolutePos(new BlockPos(3, 2, 4)), helper.absolutePos(new BlockPos(3, 1, 1)),
			level.getRandom(), 3);
		helper.assertTrue(found != null, "the plank wall must count as a house wall");
		SapperEntity sapper = helper.spawn(WarhornEntities.SAPPER, new BlockPos(3, 1, 0));
		sapper.targetWall(found[0], found[1]);
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getEntitiesOfClass(PowderKegEntity.class, sapper.getBoundingBox().inflate(10)).isEmpty(),
				"no keg planted yet");
			helper.assertTrue(sapper.state() != SapperEntity.State.CARRY, "he must have put it down");
		});
	}

	@GameTest(maxTicks = 300)
	public void kidnapperBagsAVillagerAndKillingHimSetsThemFree(GameTestHelper helper) {
		var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 5));
		var kidnapper = helper.spawn(WarhornEntities.KIDNAPPER, new BlockPos(3, 1, 1));
		kidnapper.sendAgainst(helper.absolutePos(new BlockPos(3, 1, 3)), null);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.entityTags().contains(KidnapperEntity.CARRIED_TAG), "the villager is not in the sack yet");
			helper.assertTrue(villager.isInvisible() && villager.isNoAi(), "a villager in the sack is hidden and still");
			helper.assertTrue(kidnapper.state() == KidnapperEntity.State.CARRY, "he must be carrying them off");
			kidnapper.kill(helper.getLevel());
			helper.assertTrue(!villager.entityTags().contains(KidnapperEntity.CARRIED_TAG) && !villager.isInvisible()
				&& !villager.isNoAi() && villager.isAlive(), "killing him must let the villager out");
		});
	}

	@GameTest(maxTicks = 300)
	public void kidnapperWhoReachesTheTreesTakesTheVillagerWithHim(GameTestHelper helper) {
		var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 4));
		var kidnapper = helper.spawn(WarhornEntities.KIDNAPPER, new BlockPos(3, 1, 1));
		kidnapper.sendAgainst(helper.absolutePos(new BlockPos(3, 1, 3)).offset(-80, 0, 0), null);
		helper.succeedWhen(() -> {
			helper.assertTrue(kidnapper.isRemoved(), "he should be gone into the forest");
			helper.assertTrue(villager.isRemoved(), "and the villager with him");
		});
	}

	@GameTest(maxTicks = 300)
	public void catapultWindsLoosesAndWindsAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		var crew = CatapultEntity.spawn(level, null, helper.absolutePos(new BlockPos(3, 1, 4)),
			Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 0))), 0f, false);
		helper.assertTrue(crew.size() == 2, "two crewmen");
		var catapult = level.getEntitiesOfClass(CatapultEntity.class, crew.getFirst().getBoundingBox().inflate(5)).getFirst();
		catapult.aimAt(helper.absolutePos(new BlockPos(3, 2, 4)));
		boolean[] loosed = {false};
		helper.onEachTick(() -> {
			if (catapult.phase() == CatapultEntity.Phase.LOOSE) loosed[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(loosed[0], "it has not let fly yet");
			helper.assertTrue(catapult.phase() == CatapultEntity.Phase.REST || catapult.phase() == CatapultEntity.Phase.WIND,
				"after the throw it should rest and wind again, not " + catapult.phase());
		});
	}

	@GameTest(maxTicks = 200)
	public void catapultFallsSilentWhenItsCrewIsDead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var crew = CatapultEntity.spawn(level, null, helper.absolutePos(new BlockPos(3, 1, 6)),
			Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 1))), 0f, false);
		var catapult = level.getEntitiesOfClass(CatapultEntity.class, crew.getFirst().getBoundingBox().inflate(5)).getFirst();
		catapult.aimAt(helper.absolutePos(new BlockPos(3, 1, 6)));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(catapult.phase() == CatapultEntity.Phase.WIND, "the crew should be winding, not " + catapult.phase());
			for (var man : crew) man.kill(level);
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(catapult.phase() == CatapultEntity.Phase.WIND || catapult.phase() == CatapultEntity.Phase.IDLE,
				"with the crew dead it must stand still, not " + catapult.phase());
			helper.assertTrue(level.getEntitiesOfClass(FirePotEntity.class, catapult.getBoundingBox().inflate(40)).isEmpty(),
				"and throw nothing");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void ramBreaksThroughTheWall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		var crew = BatteringRamEntity.spawn(level, null, helper.absolutePos(new BlockPos(3, 1, 4)),
			Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 1))), 0f, false);
		helper.assertTrue(crew.size() == 4, "four bearers");
		var ram = level.getEntitiesOfClass(BatteringRamEntity.class, crew.getFirst().getBoundingBox().inflate(4)).getFirst();
		ram.targetWall(helper.absolutePos(new BlockPos(3, 1, 4)), helper.absolutePos(new BlockPos(3, 1, 3)));
		helper.succeedWhen(() -> helper.assertTrue(planks(helper) <= WALL - 4, "the wall still stands: " + planks(helper) + " planks"));
	}

	@GameTest
	public void ramFallsWhenItsBearersDie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wall(helper);
		var crew = BatteringRamEntity.spawn(level, null, helper.absolutePos(new BlockPos(3, 1, 4)),
			Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 1))), 0f, false);
		var ram = level.getEntitiesOfClass(BatteringRamEntity.class, crew.getFirst().getBoundingBox().inflate(4)).getFirst();
		for (var bearer : crew) bearer.kill(level);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(ram.isRemoved(), "with every bearer dead the ram must fall");
			helper.succeed();
		});
	}

	@GameTest
	public void bannerFallsToBlowsButNotToItsOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		WarBannerEntity banner = helper.spawn(WarhornEntities.WAR_BANNER, new BlockPos(1, 1, 1));
		Vindicator ally = helper.spawn(EntityTypes.VINDICATOR, new BlockPos(3, 1, 1));
		banner.hurtServer(level, level.damageSources().mobAttack(ally), 50f);
		helper.assertTrue(!banner.isRemoved() && banner.health() == WarBannerEntity.MAX_HEALTH, "illagers must not hurt their own banner");
		banner.hurtServer(level, level.damageSources().generic(), 20f);
		helper.assertTrue(!banner.isRemoved(), "one blow must not be enough");
		banner.hurtServer(level, level.damageSources().generic(), 20f);
		helper.assertTrue(banner.isRemoved(), "the banner must fall once its health is spent");
		helper.succeed();
	}

	@GameTest(maxTicks = RebuildScene.LENGTH + 40)
	public void villagersRebuildAPaidHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		DestructionLedger ledger = new DestructionLedger();
		for (int x = 2; x <= 5; x++) {
			for (int y = 1; y <= 4; y++) {
				ledger.record(helper.absolutePos(new BlockPos(x, y, 3)),
					(y == 4 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).defaultBlockState());
			}
		}
		RebuildRegistry.Ruin ruin = new RebuildRegistry.Ruin(helper.absolutePos(new BlockPos(3, 1, 3)), ledger, 0, 0);
		RebuildRegistry.get(level).ruins().add(ruin);
		RebuildService.House house = RebuildService.house(level, helper.absolutePos(new BlockPos(3, 1, 3)), 9, true);
		helper.assertTrue(house != null, "the wall must be found as a damaged house");
		helper.assertTrue(house.entries().size() == 16, "all of the wall belongs to it: " + house.entries().size());
		helper.assertTrue(house.logs() == 3 && house.stone() == 2, "12 planks cost 3 logs, 4 cobblestone cost 2: " + house.logs() + "/" + house.stone());
		RebuildScene.start(level, house, null);
		helper.assertTrue(RebuildScene.busy(house.marker()), "the villagers must be at work");
		helper.runAfterDelay(RebuildScene.LENGTH + 5, () -> {
			RebuildRegistry.get(level).ruins().remove(ruin);
			for (int x = 2; x <= 5; x++) {
				for (int y = 1; y <= 4; y++) {
					helper.assertBlockPresent(y == 4 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS, new BlockPos(x, y, 3));
				}
			}
			for (BlockPos p : BlockPos.betweenClosed(-2, 0, -2, 9, 7, 9)) {
				helper.assertFalse(helper.getBlockState(p).is(Blocks.SCAFFOLDING), "the scaffolding must come down again");
			}
			helper.assertFalse(RebuildScene.busy(house.marker()), "the work must be over");
			helper.succeed();
		});
	}

	@GameTest
	public void kitSetsDownFilledChests(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 eye = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 1)));
		var chests = net.warhorn.command.Kit.place(level, eye, 0f, 3);
		helper.assertTrue(chests.size() == 3, "three chests asked for, three set down");
		for (BlockPos at : chests) {
			helper.assertTrue(level.getBlockState(at).is(Blocks.CHEST), "a chest must stand at " + at);
			var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(at);
			helper.assertTrue(chest.getItem(0).is(net.minecraft.world.item.Items.NETHERITE_HELMET), "the kit starts with the helmet");
			helper.assertTrue(chest.getItem(4).is(net.minecraft.world.item.Items.NETHERITE_SWORD), "the sword must be in the chest");
			helper.assertTrue(!chest.getItem(net.warhorn.command.Kit.ITEMS.size() - 1).isEmpty(), "every item of the kit must be in the chest");
		}
		helper.succeed();
	}
}
