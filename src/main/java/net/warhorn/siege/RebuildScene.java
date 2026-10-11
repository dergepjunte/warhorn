package net.warhorn.siege;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.warhorn.cinematic.CinematicService;
import net.warhorn.cinematic.Cues;
import net.warhorn.cinematic.ShotBuilder;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Beat;
import net.warhorn.net.CuePayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RebuildScene {
	static final int LENGTH = 360;
	private static final int ARRIVE = 40;
	private static final int SCAFFOLD_FROM = 20, SCAFFOLD_TO = 75;
	private static final int BUILD_FROM = 80, BUILD_TO = 270;
	private static final int STRIKE_FROM = 275, STRIKE_TO = 300;
	private static final float TIME_LAPSE = 30f;
	private static final String HELPER_TAG = "warhorn_rebuild_helper";

	private static final List<RebuildScene> RUNNING = new ArrayList<>();
	private static int nextChannel = -3000;

	private final ServerLevel level;
	private final RebuildService.House house;
	private final List<ServerPlayer> viewers;
	private final int channel = nextChannel--;
	private final List<BlockPos> order;
	private final List<BlockPos> scaffolds = new ArrayList<>();
	private final List<BlockPos> towers = new ArrayList<>();
	private final List<BlockPos> stations = new ArrayList<>();
	private final List<Worker> workers = new ArrayList<>();
	private final Vec3 middle;
	private final int floor, top;
	private final int x0, z0, x1, z1;
	private final Holder<WorldClock> clock;
	private float clockRate = 1f;
	private boolean lapsing;
	private int placed;
	private int scaffoldsUp;
	private int t;

	private RebuildScene(ServerLevel level, RebuildService.House house, List<ServerPlayer> viewers) {
		this.level = level;
		this.house = house;
		this.viewers = viewers;
		order = house.entries().stream()
			.sorted(Comparator.<DestructionLedger.Entry>comparingInt(e -> e.original().isSolidRender() ? 0 : 1)
				.thenComparingInt(e -> e.pos().getY())
				.thenComparingDouble(e -> e.pos().distSqr(house.marker())))
			.map(DestructionLedger.Entry::pos).toList();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (BlockPos p : order) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		floor = minY;
		top = maxY;
		x0 = minX;
		z0 = minZ;
		x1 = maxX;
		z1 = maxZ;
		middle = new Vec3((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
		clock = level.dimensionTypeRegistration().value().defaultClock().orElse(null);
		planScaffolds(minX - 1, minZ - 1, maxX + 1, maxZ + 1);
		planStations(minX - 3, minZ - 3, maxX + 3, maxZ + 3);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (RebuildScene scene : List.copyOf(RUNNING)) {
				if (!scene.step()) {
					scene.finish();
					RUNNING.remove(scene);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (RebuildScene scene : RUNNING) scene.finish();
			RUNNING.clear();
		});
	}

	public static boolean busy(BlockPos marker) {
		return RUNNING.stream().anyMatch(s -> s.house.marker().closerThan(marker, RebuildService.SITE_REACH));
	}

	public static void start(ServerLevel level, RebuildService.House house, ServerPlayer payer) {
		if (house.entries().isEmpty() || busy(house.marker())) return;
		List<ServerPlayer> viewers = new ArrayList<>();
		Vec3 at = Vec3.atCenterOf(house.marker());
		for (ServerPlayer p : level.players()) {
			if (p == payer || !p.isSpectator() && p.position().distanceTo(at) < 40 && !CinematicService.watching(p)) viewers.add(p);
		}
		RebuildScene scene = new RebuildScene(level, house, viewers);
		RUNNING.add(scene);
		scene.begin(payer);
	}

	private void planScaffolds(int x0, int z0, int x1, int z1) {
		List<int[]> ring = ring(x0, z0, x1, z1);
		for (int i = 0; i < ring.size(); i += 3) {
			int[] c = ring.get(i);
			BlockPos ground = ground(c[0], c[1]);
			if (ground == null) continue;
			towers.add(ground);
			for (int y = ground.getY(); y <= top + 1; y++) {
				BlockPos p = new BlockPos(c[0], y, c[1]);
				if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) break;
				scaffolds.add(p);
			}
		}
		scaffolds.sort(Comparator.comparingInt(BlockPos::getY));
	}

	private void planStations(int x0, int z0, int x1, int z1) {
		List<int[]> ring = ring(x0, z0, x1, z1);
		for (int i = 0; i < ring.size() && stations.size() < 2; i += Math.max(1, ring.size() / 7)) {
			int[] c = ring.get(i);
			BlockPos ground = ground(c[0], c[1]);
			if (ground == null || !level.getBlockState(ground.above()).isAir()) continue;
			if (stations.stream().anyMatch(s -> s.closerThan(ground, 3))) continue;
			stations.add(ground);
		}
	}

	private static List<int[]> ring(int x0, int z0, int x1, int z1) {
		List<int[]> ring = new ArrayList<>();
		for (int x = x0; x <= x1; x++) ring.add(new int[] {x, z0});
		for (int z = z0 + 1; z <= z1; z++) ring.add(new int[] {x1, z});
		for (int x = x1 - 1; x >= x0; x--) ring.add(new int[] {x, z1});
		for (int z = z1 - 1; z > z0; z--) ring.add(new int[] {x0, z});
		return ring;
	}

	private BlockPos ground(int x, int z) {
		for (int y = floor + 4; y >= floor - 5; y--) {
			BlockPos p = new BlockPos(x, y, z);
			if (!level.getBlockState(p).canBeReplaced() || !level.getBlockState(p.above()).canBeReplaced()) continue;
			if (!level.getFluidState(p).isEmpty()) continue;
			if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return p;
		}
		return null;
	}

	private void begin(ServerPlayer payer) {
		CinematicService.play(level, channel, viewers, shot(payer));
		score(SiegeDirector.SCORE_INTERLUDE, 0.7f);
		gatherWorkers();
		douse();
		level.playSound(null, BlockPos.containing(middle), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1f);
	}

	private CinematicPayload shot(ServerPlayer payer) {
		double radius = Math.max(8, Math.sqrt(order.stream().mapToDouble(p -> p.distToCenterSqr(middle)).max().orElse(25)) + 6);
		Vec3 from = payer != null ? payer.position().subtract(middle) : new Vec3(1, 0, 0);
		double start = Math.atan2(from.z, from.x);
		Vec3 look = middle.add(0, 0.5, 0);
		ShotBuilder b = new ShotBuilder(level);
		int steps = 9;
		for (int i = 0; i <= steps; i++) {
			float a = i / (float) steps;
			double angle = start + a * 2.4;
			double r = radius * (1.05 - 0.15 * a);
			double h = top - middle.y + 1.5 + 2.5 * Mth.sin(a * Mth.PI);
			b.key((int) (LENGTH * a), middle.add(Math.cos(angle) * r, h, Math.sin(angle) * r), look, 60f - 6f * a, 0f);
		}
		b.beat(STRIKE_TO - 10, 70, Beat.TITLE, "warhorn.rebuild.scene.title", "warhorn.rebuild.scene.subtitle");
		return b.build(LENGTH);
	}

	private void gatherWorkers() {
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(middle)).inflate(48),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
		near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(middle)));
		for (int i = 0; i < 4; i++) {
			Villager v;
			if (i < near.size()) {
				v = near.get(i);
			} else if (i < 3) {
				v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
				if (v == null) continue;
				BlockPos edge = stations.isEmpty() ? BlockPos.containing(middle) : stations.get(i % stations.size());
				Vec3 away = Vec3.atBottomCenterOf(edge).subtract(middle).multiply(1, 0, 1).normalize().scale(10);
				v.snapTo(Vec3.atBottomCenterOf(edge).add(away), 0, 0);
				v.addTag(HELPER_TAG);
				level.addFreshEntity(v);
			} else {
				break;
			}
			workers.add(new Worker(v, i));
		}
		for (Worker w : workers) w.walk(post(w), 0, ARRIVE);
	}

	private Vec3 post(Worker w) {
		if (w.role < 2 && w.role < stations.size()) {
			BlockPos s = stations.get(w.role);
			Vec3 out = Vec3.atBottomCenterOf(s).subtract(middle).multiply(1, 0, 1).normalize();
			return Vec3.atBottomCenterOf(s).add(out.scale(1.1));
		}
		BlockPos target = placed < order.size() ? order.get(placed) : BlockPos.containing(middle);
		return builderSpot(target, w.role);
	}

	private Vec3 builderSpot(BlockPos target, int role) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos s : towers) {
			double d = s.distSqr(target.atY(s.getY())) + (role % 2) * 9;
			if (d < bestDistance) {
				bestDistance = d;
				best = s;
			}
		}
		if (best == null) return outside(Vec3.atBottomCenterOf(target));
		int y = Mth.clamp(target.getY() - 1, best.getY(), scaffoldTop(best));
		return new Vec3(best.getX() + 0.5, y, best.getZ() + 0.5);
	}

	private Vec3 outside(Vec3 from) {
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		List<BlockPos> spots = new ArrayList<>(towers);
		for (BlockPos s : stations) spots.add(s.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()), 1));
		for (BlockPos s : spots) {
			if (!level.getBlockState(s).canBeReplaced() || !level.getBlockState(s.above()).canBeReplaced()) continue;
			double d = Vec3.atBottomCenterOf(s).distanceToSqr(from);
			if (d < bestDistance) {
				bestDistance = d;
				best = Vec3.atBottomCenterOf(s);
			}
		}
		return best != null ? best : from;
	}

	private int scaffoldTop(BlockPos base) {
		int y = base.getY();
		for (int i = 0; i < scaffoldsUp; i++) {
			BlockPos s = scaffolds.get(i);
			if (s.getX() == base.getX() && s.getZ() == base.getZ()) y = Math.max(y, s.getY());
		}
		return y;
	}

	private boolean step() {
		t++;
		if (t >= SCAFFOLD_FROM && t <= SCAFFOLD_TO) raiseScaffolds();
		if (t == 30) setUpStations();
		if (t == BUILD_FROM) timeLapse(true);
		if (t >= BUILD_FROM && t <= BUILD_TO) build();
		if (t % 10 == 0 && t < STRIKE_TO) douse();
		if (t > BUILD_FROM && t < BUILD_TO) work();
		if (t == BUILD_TO) timeLapse(false);
		if (t >= STRIKE_FROM && t <= STRIKE_TO) strikeScaffolds();
		if (t == STRIKE_TO) {
			clearStations();
			score(SiegeDirector.SCORE_VICTORY, 1f);
			level.playSound(null, BlockPos.containing(middle), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1.1f);
			for (Worker w : workers) w.walk(outside(w.villager.position()), t, 30);
		}
		for (Worker w : workers) w.tick(t);
		return t < LENGTH;
	}

	private void douse() {
		boolean hissed = false;
		for (BlockPos p : BlockPos.betweenClosed(x0 - 4, floor - 1, z0 - 4, x1 + 4, top + 3, z1 + 4)) {
			if (!(level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)) continue;
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.02);
			if (!hissed) {
				level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
				hissed = true;
			}
		}
	}

	private void raiseScaffolds() {
		int due = Math.round(scaffolds.size() * (t - SCAFFOLD_FROM + 1) / (float) (SCAFFOLD_TO - SCAFFOLD_FROM + 1));
		while (scaffoldsUp < due && scaffoldsUp < scaffolds.size()) {
			BlockPos p = scaffolds.get(scaffoldsUp++);
			if (!level.getBlockState(p).canBeReplaced()) continue;
			level.setBlock(p, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (scaffoldsUp % 4 == 0) level.playSound(null, p, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 0.7f, 1.1f);
		}
	}

	private void strikeScaffolds() {
		int due = Math.round(scaffolds.size() * (STRIKE_TO - t) / (float) (STRIKE_TO - STRIKE_FROM));
		while (scaffoldsUp > due && scaffoldsUp > 0) {
			BlockPos p = scaffolds.get(--scaffoldsUp);
			if (!level.getBlockState(p).is(Blocks.SCAFFOLDING)) continue;
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (scaffoldsUp % 4 == 0) level.playSound(null, p, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 0.7f, 1.0f);
		}
	}

	private void setUpStations() {
		for (int i = 0; i < stations.size(); i++) {
			BlockPos s = stations.get(i);
			level.setBlock(s, (i == 0 ? Blocks.CRAFTING_TABLE : Blocks.STONECUTTER).defaultBlockState(), Block.UPDATE_CLIENTS);
			level.sendParticles(ParticleTypes.POOF, s.getX() + 0.5, s.getY() + 0.6, s.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, s, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
		}
	}

	private void clearStations() {
		for (BlockPos s : stations) {
			if (level.getBlockState(s).is(Blocks.CRAFTING_TABLE) || level.getBlockState(s).is(Blocks.STONECUTTER)) {
				level.setBlock(s, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.sendParticles(ParticleTypes.POOF, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
			}
		}
	}

	private void build() {
		int due = Math.round(order.size() * (t - BUILD_FROM + 1) / (float) (BUILD_TO - BUILD_FROM + 1));
		while (placed < due && placed < order.size()) {
			BlockPos p = order.get(placed++);
			place(p, true);
		}
	}

	private void place(BlockPos p, boolean show) {
		DestructionLedger.Entry entry = house.ruin().ledger().get(p);
		if (entry == null) return;
		BlockState now = level.getBlockState(p);
		if (!now.canBeReplaced() && !now.is(Blocks.SCAFFOLDING)) return;
		level.setBlock(p, entry.original(), Block.UPDATE_ALL);
		if (!show) return;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, entry.original()), p.getX() + 0.5, p.getY() + 0.5,
			p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
		if (placed % 3 == 0) {
			level.playSound(null, p, entry.original().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	private void work() {
		if (t % 8 == 0) {
			for (int i = 0; i < stations.size(); i++) {
				BlockPos s = stations.get(i);
				ItemStack chips = new ItemStack(i == 0 ? Items.OAK_PLANKS : Items.COBBLESTONE);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, chips.getItem()), s.getX() + 0.5, s.getY() + 1.1,
					s.getZ() + 0.5, 5, 0.2, 0.1, 0.2, 0.08);
				SoundEvent sound = i == 0 ? SoundEvents.VILLAGER_WORK_FLETCHER : SoundEvents.VILLAGER_WORK_MASON;
				if (t % 24 == 0) level.playSound(null, s, sound, SoundSource.NEUTRAL, 0.8f, 1f);
			}
		}
		if (t % 16 == 0) {
			for (Worker w : workers) {
				if (w.role < 2 && w.role < stations.size()) continue;
				w.walk(post(w), t, 12);
				if (placed < order.size()) {
					BlockState next = house.ruin().ledger().get(order.get(placed)) != null
						? house.ruin().ledger().get(order.get(placed)).original() : Blocks.OAK_PLANKS.defaultBlockState();
					w.hold(new ItemStack(next.getBlock().asItem()));
				}
			}
		}
	}

	private void timeLapse(boolean on) {
		if (clock == null) return;
		var clocks = level.getServer().clockManager();
		var instance = clocks.getInstance(clock);
		if (instance.isPaused()) return;
		if (on && !lapsing) {
			clockRate = instance.rate();
			clocks.setRate(clock, clockRate * TIME_LAPSE);
			lapsing = true;
		} else if (!on && lapsing) {
			clocks.setRate(clock, clockRate);
			lapsing = false;
		}
	}

	private void score(int mood, float intensity) {
		Cues.send(viewers, new CuePayload(CuePayload.SCORE, intensity, mood, middle, "", ""));
	}

	private void finish() {
		while (placed < order.size()) place(order.get(placed++), false);
		while (scaffoldsUp > 0) {
			BlockPos p = scaffolds.get(--scaffoldsUp);
			if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		clearStations();
		timeLapse(false);
		for (Worker w : workers) w.release();
		CinematicService.clear(channel);
		score(SiegeDirector.SCORE_SILENT, 0f);
	}

	private final class Worker {
		final Villager villager;
		final int role;
		final Vec3 home;
		final boolean noAi;
		final boolean invulnerable;
		final ItemStack held;
		Vec3 from, to;
		int start, duration = 1;

		Worker(Villager villager, int role) {
			this.villager = villager;
			this.role = role;
			home = villager.position();
			noAi = villager.isNoAi();
			invulnerable = villager.isPermanentlyInvulnerable();
			held = villager.getItemBySlot(EquipmentSlot.MAINHAND).copy();
			villager.setNoAi(true);
			villager.setPermanentlyInvulnerable(true);
			villager.getNavigation().stop();
			from = to = home;
			hold(new ItemStack(role == 0 ? Items.OAK_PLANKS : role == 1 ? Items.STONE_BRICKS : Items.OAK_PLANKS));
		}

		void hold(ItemStack stack) {
			if (!stack.isEmpty()) villager.setItemSlot(EquipmentSlot.MAINHAND, stack);
		}

		void walk(Vec3 target, int now, int ticks) {
			from = villager.position();
			to = target;
			start = now;
			duration = Math.max(1, ticks);
		}

		void tick(int now) {
			if (!villager.isAlive()) return;
			float a = Mth.clamp((now - start) / (float) duration, 0, 1);
			float s = a * a * (3 - 2 * a);
			Vec3 p = from.lerp(to, s);
			Vec3 facing = a < 1 ? to.subtract(from) : middle.subtract(p);
			villager.setPos(p.x, p.y, p.z);
			villager.resetFallDistance();
			villager.clearFire();
			if (facing.horizontalDistanceSqr() > 1e-3) {
				float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90f;
				villager.setYRot(yaw);
				villager.setYHeadRot(yaw);
				villager.setYBodyRot(yaw);
			}
		}

		void release() {
			if (villager.entityTags().contains(HELPER_TAG)) {
				villager.discard();
				return;
			}
			villager.setItemSlot(EquipmentSlot.MAINHAND, held);
			villager.setNoAi(noAi);
			villager.setPermanentlyInvulnerable(invulnerable);
		}
	}
}
