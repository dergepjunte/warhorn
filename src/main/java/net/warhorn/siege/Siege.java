package net.warhorn.siege;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;
import net.warhorn.net.CuePayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Siege {
	public static final Codec<Siege> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(s -> s.id),
		BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
		BlockPos.CODEC.fieldOf("staging").forGetter(s -> s.staging),
		Codec.INT.fieldOf("omen").forGetter(s -> s.omenLevel),
		Codec.STRING.fieldOf("stage").forGetter(s -> s.stage.name()),
		Codec.INT.fieldOf("stage_ticks").forGetter(s -> s.stageTicks),
		Codec.INT.fieldOf("act").forGetter(s -> s.act),
		Codec.INT.fieldOf("wave").forGetter(s -> s.wave),
		Codec.INT.fieldOf("wave_ticks").forGetter(s -> s.waveTicks),
		UUIDUtil.CODEC.listOf().fieldOf("raiders").forGetter(s -> List.copyOf(s.raiders)),
		UUIDUtil.CODEC.optionalFieldOf("warlord").forGetter(s -> Optional.ofNullable(s.warlord)),
		Codec.INT.fieldOf("act_total").forGetter(s -> s.actEnemiesTotal),
		Codec.INT.fieldOf("villagers_start").forGetter(s -> s.villagersAtStart),
		UUIDUtil.CODEC.listOf().fieldOf("heroes").forGetter(s -> List.copyOf(s.heroes)),
		DestructionLedger.CODEC.fieldOf("ledger").forGetter(s -> s.ledger)
	).apply(i, Siege::new));

	final int id;
	final BlockPos center;
	BlockPos staging;
	final int omenLevel;
	SiegeStage stage;
	int stageTicks;
	int act;
	int wave;
	int waveTicks;
	final Set<UUID> raiders;
	UUID warlord;
	int actEnemiesTotal;
	int villagersAtStart;
	final Set<UUID> heroes;
	final DestructionLedger ledger;

	transient int raidersAlive;
	transient int villagersAlive;
	transient final List<UUID> participants = new ArrayList<>();
	transient Vec3 lastWarlordPos;
	transient int emptyTicks;
	transient int defeatTicks;
	transient int arsonsLeft;
	transient boolean resumed;
	transient boolean warlordSlain;
	transient int gatherTicks = -1;
	transient int chargeTicks;
	transient final java.util.Map<UUID, Integer> missingTicks = new java.util.HashMap<>();
	transient final List<UUID> extras = new ArrayList<>();
	transient int mood;
	transient float moodIntensity;
	transient final List<CuePayload> pendingCues = new ArrayList<>();

	Siege(int id, BlockPos center, BlockPos staging, int omenLevel) {
		this(id, center, staging, omenLevel, SiegeStage.OMEN.name(), 0, 0, 0, 0, List.of(), Optional.empty(), 0, 0,
			List.of(), new DestructionLedger());
	}

	private Siege(int id, BlockPos center, BlockPos staging, int omenLevel, String stage, int stageTicks, int act,
		int wave, int waveTicks, List<UUID> raiders, Optional<UUID> warlord, int actEnemiesTotal, int villagersAtStart,
		List<UUID> heroes, DestructionLedger ledger) {
		this.id = id;
		this.center = center;
		this.staging = staging;
		this.omenLevel = omenLevel;
		this.stage = parseStage(stage);
		this.stageTicks = stageTicks;
		this.act = act;
		this.wave = wave;
		this.waveTicks = waveTicks;
		this.raiders = new HashSet<>(raiders);
		this.warlord = warlord.orElse(null);
		this.actEnemiesTotal = actEnemiesTotal;
		this.villagersAtStart = villagersAtStart;
		this.heroes = new HashSet<>(heroes);
		this.ledger = ledger;
	}

	private static SiegeStage parseStage(String name) {
		try {
			return SiegeStage.valueOf(name);
		} catch (IllegalArgumentException e) {
			return SiegeStage.FINISHED;
		}
	}

	public int id() { return id; }
	public BlockPos center() { return center; }
	public BlockPos staging() { return staging; }
	public int omenLevel() { return omenLevel; }
	public SiegeStage stage() { return stage; }
	public int stageTicks() { return stageTicks; }
	public int act() { return act; }
	public int wave() { return wave; }
	public int raidersAlive() { return raidersAlive; }
	public int villagersAlive() { return villagersAlive; }
	public int villagersAtStart() { return villagersAtStart; }
	public int actEnemiesTotal() { return actEnemiesTotal; }
	public List<UUID> participants() { return participants; }
	public DestructionLedger ledger() { return ledger; }
	public UUID warlord() { return warlord; }
	public int arsonsLeft() { return arsonsLeft; }

	public boolean contains(BlockPos pos, int radius) {
		return center.distSqr(pos) <= (double) radius * radius;
	}

	public void enlist(UUID soldier) {
		raiders.add(soldier);
	}
}
