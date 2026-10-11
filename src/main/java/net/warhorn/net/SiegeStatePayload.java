package net.warhorn.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.warhorn.Warhorn;

public record SiegeStatePayload(boolean active, BlockPos center, BlockPos staging, int stage, int act, int wave,
	int enemiesAlive, int enemiesTotal, int timer, int timerMax, int villagersAlive, int villagersTotal,
	float dread, int warlordId) implements CustomPacketPayload {
	public static final SiegeStatePayload INACTIVE =
		new SiegeStatePayload(false, BlockPos.ZERO, BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1);

	private record Head(boolean active, BlockPos center, BlockPos staging, int stage, int act, int wave) {
		static final StreamCodec<RegistryFriendlyByteBuf, Head> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Head::active,
			BlockPos.STREAM_CODEC, Head::center,
			BlockPos.STREAM_CODEC, Head::staging,
			ByteBufCodecs.VAR_INT, Head::stage,
			ByteBufCodecs.VAR_INT, Head::act,
			ByteBufCodecs.VAR_INT, Head::wave,
			Head::new);
	}

	private record Tail(int enemiesAlive, int enemiesTotal, int timer, int timerMax, int villagersAlive,
		int villagersTotal, float dread, int warlordId) {
		static final StreamCodec<RegistryFriendlyByteBuf, Tail> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Tail::enemiesAlive,
			ByteBufCodecs.VAR_INT, Tail::enemiesTotal,
			ByteBufCodecs.VAR_INT, Tail::timer,
			ByteBufCodecs.VAR_INT, Tail::timerMax,
			ByteBufCodecs.VAR_INT, Tail::villagersAlive,
			ByteBufCodecs.VAR_INT, Tail::villagersTotal,
			ByteBufCodecs.FLOAT, Tail::dread,
			ByteBufCodecs.VAR_INT, Tail::warlordId,
			Tail::new);
	}

	public static final Type<SiegeStatePayload> TYPE = new Type<>(Warhorn.id("siege_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SiegeStatePayload> CODEC = StreamCodec.composite(
		Head.CODEC, p -> new Head(p.active, p.center, p.staging, p.stage, p.act, p.wave),
		Tail.CODEC, p -> new Tail(p.enemiesAlive, p.enemiesTotal, p.timer, p.timerMax, p.villagersAlive,
			p.villagersTotal, p.dread, p.warlordId),
		(h, t) -> new SiegeStatePayload(h.active, h.center, h.staging, h.stage, h.act, h.wave, t.enemiesAlive,
			t.enemiesTotal, t.timer, t.timerMax, t.villagersAlive, t.villagersTotal, t.dread, t.warlordId));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
