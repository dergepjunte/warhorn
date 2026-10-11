package net.warhorn.siege;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.warhorn.Warhorn;

import java.util.ArrayList;
import java.util.List;

public final class RebuildRegistry extends SavedData {
	public static final class Ruin {
		static final Codec<Ruin> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("center").forGetter(Ruin::center),
			DestructionLedger.CODEC.fieldOf("ledger").forGetter(Ruin::ledger),
			Codec.INT.optionalFieldOf("total", 0).forGetter(Ruin::total),
			Codec.INT.optionalFieldOf("milestone", 0).forGetter(r -> r.milestone)
		).apply(i, Ruin::new));

		private final BlockPos center;
		private final DestructionLedger ledger;
		private int total;
		int milestone;

		Ruin(BlockPos center, DestructionLedger ledger, int total, int milestone) {
			this.center = center;
			this.ledger = ledger;
			this.total = total > 0 ? total : ledger.size();
			this.milestone = milestone;
		}

		public BlockPos center() {
			return center;
		}

		public DestructionLedger ledger() {
			return ledger;
		}

		public int total() {
			return total;
		}

		void shrink() {
			total = Math.max(0, total - 1);
		}

		public int restored() {
			return total - ledger.size();
		}
	}

	public static final Codec<RebuildRegistry> CODEC = Ruin.CODEC.listOf().fieldOf("ruins").codec()
		.xmap(RebuildRegistry::new, r -> r.ruins);
	public static final SavedDataType<RebuildRegistry> TYPE = new SavedDataType<>(
		Warhorn.id("ruins"), RebuildRegistry::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);

	private final List<Ruin> ruins;

	public RebuildRegistry() {
		this(List.of());
	}

	private RebuildRegistry(List<Ruin> ruins) {
		this.ruins = new ArrayList<>(ruins);
	}

	public static RebuildRegistry get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	static void adopt(ServerLevel level, Siege siege) {
		if (siege.ledger.size() == 0) return;
		RebuildRegistry registry = get(level);
		registry.ruins.add(new Ruin(siege.center, siege.ledger, siege.ledger.size(), 0));
		registry.setDirty();
	}

	public List<Ruin> ruins() {
		return ruins;
	}
}
