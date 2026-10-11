package net.warhorn.siege;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.warhorn.Warhorn;

import java.util.ArrayList;
import java.util.List;

public final class SiegeSavedData extends SavedData {
	public static final Codec<SiegeSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Siege.CODEC.listOf().fieldOf("sieges").forGetter(d -> d.sieges),
		Codec.INT.fieldOf("next_id").forGetter(d -> d.nextId)
	).apply(i, SiegeSavedData::new));

	public static final SavedDataType<SiegeSavedData> TYPE = new SavedDataType<>(
		Warhorn.id("sieges"), SiegeSavedData::new, CODEC, DataFixTypes.SAVED_DATA_RAIDS);

	final List<Siege> sieges;
	private int nextId;

	public SiegeSavedData() {
		this(List.of(), 1);
	}

	private SiegeSavedData(List<Siege> sieges, int nextId) {
		this.sieges = new ArrayList<>(sieges);
		this.nextId = nextId;
	}

	int nextId() {
		setDirty();
		return nextId++;
	}

	public List<Siege> sieges() {
		return sieges;
	}
}
