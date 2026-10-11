package net.warhorn.siege;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DestructionLedger {
	public record Entry(BlockPos pos, BlockState original) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Entry::original)
		).apply(i, Entry::new));
	}

	public static final Codec<DestructionLedger> CODEC = Entry.CODEC.listOf()
		.xmap(DestructionLedger::new, l -> List.copyOf(l.entries.values()));

	private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();

	public DestructionLedger() {
	}

	private DestructionLedger(List<Entry> list) {
		for (Entry e : list) entries.put(e.pos(), e);
	}

	public void record(BlockPos pos, BlockState original) {
		if (original.isAir()) return;
		entries.putIfAbsent(pos.immutable(), new Entry(pos.immutable(), original));
	}

	public void forget(BlockPos pos) {
		entries.remove(pos);
	}

	public Entry get(BlockPos pos) {
		return entries.get(pos);
	}

	public int size() {
		return entries.size();
	}

	public Map<BlockPos, Entry> view() {
		return Collections.unmodifiableMap(entries);
	}
}
