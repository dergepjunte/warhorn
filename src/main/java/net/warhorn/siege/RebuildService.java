package net.warhorn.siege;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.Cues;
import net.warhorn.net.RuinPayload;
import net.warhorn.registry.WarhornSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RebuildService {
	static final int NEAR = 72;
	static final int SEND_RADIUS = 48;
	static final int MAX_SENT = 1024;
	static final float DONE_AT = 0.95f;
	static final int SITE_REACH = 7;
	static final int MIN_SITE = 3;
	static final int MAX_SITES = 24;

	public static java.util.function.Predicate<BlockPos> clientSite = pos -> false;

	private static final Map<UUID, BlockPos> showing = new HashMap<>();

	private RebuildService() {
	}

	static void tick(ServerLevel level) {
		if (level.getGameTime() % 20 != 0) return;
		RebuildRegistry registry = RebuildRegistry.get(level);
		Map<UUID, BlockPos> seen = new HashMap<>();
		for (RebuildRegistry.Ruin ruin : List.copyOf(registry.ruins())) {
			if (SiegeManager.find(level, ruin.center(), 96) != null) continue;
			Vec3 center = Vec3.atCenterOf(ruin.center());
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.position().distanceTo(center) < NEAR) near.add(player);
			}
			if (near.isEmpty()) continue;
			if (purgeNature(ruin)) registry.setDirty();
			Hints.offerAll(near, Hints.RUINS);

			int fixed = restore(level, ruin);
			boolean done = ruin.restored() >= ruin.total() * DONE_AT;
			if (fixed > 0) {
				registry.setDirty();
				if (!done) milestones(level, ruin, near);
			}
			if (done) {
				complete(level, ruin, near);
				registry.ruins().remove(ruin);
				registry.setDirty();
			}
			for (ServerPlayer player : near) {
				seen.put(player.getUUID(), ruin.center());
				boolean fresh = !ruin.center().equals(showing.get(player.getUUID()));
				if (done) {
					ServerPlayNetworking.send(player, RuinPayload.NONE);
				} else if (fresh || fixed > 0 || level.getGameTime() % 200 == 0) {
					ServerPlayNetworking.send(player, payloadFor(ruin, player));
				}
			}
		}
		for (ServerPlayer player : level.players()) {
			UUID uuid = player.getUUID();
			if (!seen.containsKey(uuid) && showing.remove(uuid) != null) {
				ServerPlayNetworking.send(player, RuinPayload.NONE);
			}
		}
		showing.putAll(seen);
	}

	static void forget(UUID player) {
		showing.remove(player);
	}

	static void clear() {
		showing.clear();
	}

	private static boolean purgeNature(RebuildRegistry.Ruin ruin) {
		boolean changed = false;
		for (DestructionLedger.Entry entry : List.copyOf(ruin.ledger().view().values())) {
			if (Ruination.isBuilding(entry.original())) continue;
			ruin.ledger().forget(entry.pos());
			ruin.shrink();
			changed = true;
		}
		return changed;
	}

	private static int restore(ServerLevel level, RebuildRegistry.Ruin ruin) {
		int fixed = 0;
		BlockPos last = null;
		for (DestructionLedger.Entry entry : List.copyOf(ruin.ledger().view().values())) {
			BlockPos pos = entry.pos();
			if (!level.isLoaded(pos)) continue;
			if (!level.getBlockState(pos).is(entry.original().getBlock())) continue;
			ruin.ledger().forget(pos);
			fixed++;
			last = pos;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.35, 0.35, 0.35, 0);
		}
		if (last != null) {
			level.playSound(null, last, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9f, 1.2f + level.getRandom().nextFloat() * 0.3f);
			ExperienceOrb.award(level, Vec3.atCenterOf(last), fixed * 2);
		}
		return fixed;
	}

	private static void milestones(ServerLevel level, RebuildRegistry.Ruin ruin, List<ServerPlayer> near) {
		int quarter = Math.min(3, ruin.restored() * 4 / Math.max(1, ruin.total()));
		while (ruin.milestone < quarter) {
			ruin.milestone++;
			for (ServerPlayer player : near) give(player, new ItemStack(Items.EMERALD, 2 + 2 * ruin.milestone));
			Cues.toast(level, Vec3.atCenterOf(ruin.center()), "warhorn.rebuild.milestone." + ruin.milestone);
			level.playSound(null, ruin.center(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.9f);
		}
	}

	private static void complete(ServerLevel level, RebuildRegistry.Ruin ruin, List<ServerPlayer> near) {
		Vec3 center = Vec3.atCenterOf(ruin.center());
		Cues.card(level, center, "warhorn.rebuild.done.kicker", "warhorn.rebuild.done");
		level.playSound(null, ruin.center(), WarhornSounds.STINGER_VICTORY, SoundSource.RECORDS, 1.0f, 1.0f);
		for (ServerPlayer player : near) {
			give(player, new ItemStack(Items.EMERALD, 12 + ruin.total() / 12));
			player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 20, 1, false, false, true));
			player.giveExperiencePoints(150);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 2, center.z, 80, 8, 3, 8, 0);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
		}
	}

	private static RuinPayload payloadFor(RebuildRegistry.Ruin ruin, ServerPlayer player) {
		List<BlockPos> positions = new ArrayList<>();
		List<Integer> states = new ArrayList<>();
		for (DestructionLedger.Entry entry : ruin.ledger().view().values()) {
			if (positions.size() >= MAX_SENT) break;
			if (entry.pos().distSqr(player.blockPosition()) > SEND_RADIUS * SEND_RADIUS) continue;
			positions.add(entry.pos());
			states.add(Block.getId(entry.original()));
		}
		List<Site> sites = sites(ruin);
		List<Integer> logs = new ArrayList<>();
		List<Integer> stone = new ArrayList<>();
		for (Site site : sites) {
			House house = new House(ruin, site.marker(), entriesOf(ruin, site.marker()));
			logs.add(house.logs());
			stone.add(house.stone());
		}
		return new RuinPayload(ruin.center(), ruin.restored(), ruin.total(), positions, states,
			sites.stream().map(Site::marker).toList(), sites.stream().map(Site::missing).toList(), logs, stone);
	}

	public record House(RebuildRegistry.Ruin ruin, BlockPos marker, List<DestructionLedger.Entry> entries) {
		public int logs() {
			int wood = (int) entries.stream().filter(e -> !isStone(e.original())).count();
			return (wood + 3) / 4;
		}

		public int stone() {
			int stone = (int) entries.stream().filter(e -> isStone(e.original())).count();
			return (stone + 1) / 2;
		}
	}

	public static boolean isLog(ItemStack stack) {
		return stack.is(net.minecraft.tags.ItemTags.LOGS);
	}

	public static boolean isRubble(ItemStack stack) {
		return stack.is(net.minecraft.tags.ItemTags.STONE_CRAFTING_MATERIALS);
	}

	public static net.minecraft.world.InteractionResult use(net.minecraft.world.entity.player.Player player,
		net.minecraft.world.level.Level world, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!isLog(held) && !isRubble(held) || player.isSpectator()) return net.minecraft.world.InteractionResult.PASS;
		if (world.isClientSide()) return clientSite.test(hit.getBlockPos()) ? net.minecraft.world.InteractionResult.SUCCESS
			: net.minecraft.world.InteractionResult.PASS;
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
		House house = house(level, hit.getBlockPos(), SITE_REACH + 2);
		if (house == null || house.entries().isEmpty()) return net.minecraft.world.InteractionResult.PASS;
		if (RebuildScene.busy(house.marker())) {
			sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("warhorn.rebuild.busy"));
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		int logs = house.logs(), stone = house.stone();
		if (!sp.isCreative()) {
			int haveLogs = count(sp, true), haveStone = count(sp, false);
			if (haveLogs < logs || haveStone < stone) {
				sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("warhorn.rebuild.short",
					costText(Math.max(0, logs - haveLogs), Math.max(0, stone - haveStone))));
				level.playSound(null, hit.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			take(sp, true, logs);
			take(sp, false, stone);
		}
		level.playSound(null, hit.getBlockPos(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		RebuildScene.start(level, house, sp);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	public static net.minecraft.network.chat.Component costText(int logs, int stone) {
		var text = net.minecraft.network.chat.Component.empty();
		if (logs > 0) text.append(net.minecraft.network.chat.Component.translatable("warhorn.rebuild.logs", logs));
		if (logs > 0 && stone > 0) text.append(" · ");
		if (stone > 0) text.append(net.minecraft.network.chat.Component.translatable("warhorn.rebuild.stone", stone));
		return text;
	}

	private static int count(ServerPlayer player, boolean logs) {
		int n = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (logs ? isLog(stack) : isRubble(stack)) n += stack.getCount();
		}
		return n;
	}

	private static void take(ServerPlayer player, boolean logs, int amount) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!(logs ? isLog(stack) : isRubble(stack))) continue;
			int n = Math.min(amount, stack.getCount());
			stack.shrink(n);
			amount -= n;
		}
		inventory.setChanged();
	}

	public static boolean isStone(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE);
	}

	public static House house(ServerLevel level, BlockPos near, double reach) {
		return house(level, near, reach, false);
	}

	static House house(ServerLevel level, BlockPos near, double reach, boolean evenUnderSiege) {
		House best = null;
		double bestDistance = reach * reach;
		for (RebuildRegistry.Ruin ruin : RebuildRegistry.get(level).ruins()) {
			if (ruin.center().distSqr(near) > 160 * 160) continue;
			if (!evenUnderSiege && SiegeManager.find(level, ruin.center(), 96) != null) continue;
			for (Site site : sites(ruin)) {
				double dx = site.marker().getX() - near.getX(), dz = site.marker().getZ() - near.getZ();
				double d = dx * dx + dz * dz;
				if (d <= bestDistance) {
					bestDistance = d;
					best = new House(ruin, site.marker(), null);
				}
			}
		}
		return best == null ? null : new House(best.ruin(), best.marker(), entriesOf(best.ruin(), best.marker()));
	}

	static List<DestructionLedger.Entry> entriesOf(RebuildRegistry.Ruin ruin, BlockPos marker) {
		List<DestructionLedger.Entry> entries = new ArrayList<>();
		for (DestructionLedger.Entry entry : ruin.ledger().view().values()) {
			BlockPos p = entry.pos();
			double dx = p.getX() - marker.getX(), dz = p.getZ() - marker.getZ();
			if (dx * dx + dz * dz <= (SITE_REACH + 1) * (SITE_REACH + 1)) entries.add(entry);
		}
		return entries;
	}

	public static int rebuildHouse(ServerLevel level, BlockPos near, ServerPlayer viewer) {
		House house = house(level, near, 160);
		if (house == null) return -1;
		RebuildScene.start(level, house, viewer);
		return house.entries().size();
	}

	record Site(BlockPos marker, int missing) {
	}

	static List<Site> sites(RebuildRegistry.Ruin ruin) {
		List<double[]> groups = new ArrayList<>();
		for (DestructionLedger.Entry entry : ruin.ledger().view().values()) {
			BlockPos pos = entry.pos();
			double[] home = null;
			double best = SITE_REACH * SITE_REACH;
			for (double[] g : groups) {
				double dx = g[0] / g[2] - pos.getX(), dz = g[1] / g[2] - pos.getZ();
				double d = dx * dx + dz * dz;
				if (d <= best) {
					best = d;
					home = g;
				}
			}
			if (home == null) {
				groups.add(new double[] {pos.getX(), pos.getZ(), 1, pos.getY()});
			} else {
				home[0] += pos.getX();
				home[1] += pos.getZ();
				home[2]++;
				home[3] = Math.max(home[3], pos.getY());
			}
		}
		return groups.stream()
			.filter(g -> g[2] >= MIN_SITE)
			.sorted((a, b) -> Double.compare(b[2], a[2]))
			.limit(MAX_SITES)
			.map(g -> new Site(BlockPos.containing(g[0] / g[2], g[3] + 1, g[1] / g[2]), (int) g[2]))
			.toList();
	}
}
