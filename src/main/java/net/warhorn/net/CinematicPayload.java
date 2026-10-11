package net.warhorn.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.warhorn.Warhorn;

import java.util.List;

public record CinematicPayload(int id, int durationTicks, int trackEntity, List<Key> keys, List<Beat> beats)
	implements CustomPacketPayload {
	public record Key(int tick, Vec3 pos, Vec3 look, float fov, float roll, boolean cut, int mode) {
		public static final int FIXED = 0;
		public static final int AIM = 1;
		public static final int FOLLOW = 2;

		public static final StreamCodec<ByteBuf, Key> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Key::tick,
			Vec3.STREAM_CODEC, Key::pos,
			Vec3.STREAM_CODEC, Key::look,
			ByteBufCodecs.FLOAT, Key::fov,
			ByteBufCodecs.FLOAT, Key::roll,
			ByteBufCodecs.BOOL, Key::cut,
			ByteBufCodecs.VAR_INT, Key::mode,
			Key::new);
	}

	public record Beat(int tick, int duration, int style, String title, String subtitle) {
		public static final int TITLE = 0;
		public static final int LINE = 1;
		public static final int NAMEPLATE = 2;

		public static final StreamCodec<ByteBuf, Beat> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Beat::tick,
			ByteBufCodecs.VAR_INT, Beat::duration,
			ByteBufCodecs.VAR_INT, Beat::style,
			ByteBufCodecs.STRING_UTF8, Beat::title,
			ByteBufCodecs.STRING_UTF8, Beat::subtitle,
			Beat::new);
	}

	public static final Type<CinematicPayload> TYPE = new Type<>(Warhorn.id("cinematic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CinematicPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CinematicPayload::id,
		ByteBufCodecs.VAR_INT, CinematicPayload::durationTicks,
		ByteBufCodecs.VAR_INT, CinematicPayload::trackEntity,
		Key.CODEC.apply(ByteBufCodecs.list(64)), CinematicPayload::keys,
		Beat.CODEC.apply(ByteBufCodecs.list(16)), CinematicPayload::beats,
		CinematicPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
