package net.warhorn.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.warhorn.Warhorn;

public record SkipCinematicPayload(int id) implements CustomPacketPayload {
	public static final Type<SkipCinematicPayload> TYPE = new Type<>(Warhorn.id("skip_cinematic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkipCinematicPayload> CODEC =
		ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(SkipCinematicPayload::new, SkipCinematicPayload::id);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
