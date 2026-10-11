package net.warhorn.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.warhorn.Warhorn;

import java.util.List;

public record RuinPayload(BlockPos center, int restored, int total, List<BlockPos> positions, List<Integer> states,
	List<BlockPos> sites, List<Integer> siteMissing, List<Integer> siteLogs, List<Integer> siteStone) implements CustomPacketPayload {
	public static final RuinPayload NONE = new RuinPayload(BlockPos.ZERO, 0, 0, List.of(), List.of(), List.of(), List.of(),
		List.of(), List.of());
	public static final Type<RuinPayload> TYPE = new Type<>(Warhorn.id("ruin"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RuinPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, RuinPayload::center,
		ByteBufCodecs.VAR_INT, RuinPayload::restored,
		ByteBufCodecs.VAR_INT, RuinPayload::total,
		BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RuinPayload::positions,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RuinPayload::states,
		BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RuinPayload::sites,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RuinPayload::siteMissing,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RuinPayload::siteLogs,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RuinPayload::siteStone,
		RuinPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
