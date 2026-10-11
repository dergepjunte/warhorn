package net.warhorn.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.warhorn.Warhorn;

public record CuePayload(int kind, float strength, int duration, Vec3 origin, String text, String subtext)
	implements CustomPacketPayload {
	public static final int SHAKE = 0;
	public static final int HITSTOP = 1;
	public static final int FOV_PUNCH = 2;
	public static final int FLASH = 3;
	public static final int ACT_CARD = 4;
	public static final int HORN = 5;
	public static final int SCORE = 6;
	public static final int TOAST = 7;
	public static final int HINT = 8;
	public static final int STING = 9;

	public static final Type<CuePayload> TYPE = new Type<>(Warhorn.id("cue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CuePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CuePayload::kind,
		ByteBufCodecs.FLOAT, CuePayload::strength,
		ByteBufCodecs.VAR_INT, CuePayload::duration,
		Vec3.STREAM_CODEC, CuePayload::origin,
		ByteBufCodecs.STRING_UTF8, CuePayload::text,
		ByteBufCodecs.STRING_UTF8, CuePayload::subtext,
		CuePayload::new);

	public static CuePayload of(int kind, float strength, int duration, Vec3 origin) {
		return new CuePayload(kind, strength, duration, origin, "", "");
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
