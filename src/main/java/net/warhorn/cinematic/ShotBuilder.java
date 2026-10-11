package net.warhorn.cinematic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Beat;
import net.warhorn.net.CinematicPayload.Key;

import java.util.ArrayList;
import java.util.List;

public final class ShotBuilder {
	private final ServerLevel level;
	private final List<Key> keys = new ArrayList<>();
	private final List<Beat> beats = new ArrayList<>();
	private int trackEntity = -1;
	private Vec3 trackOrigin;
	private boolean nextCut = true;

	public ShotBuilder(ServerLevel level) {
		this.level = level;
	}

	public ShotBuilder cut() {
		nextCut = true;
		return this;
	}

	public ShotBuilder key(int tick, Vec3 pos, Vec3 look, float fov) {
		return key(tick, pos, look, fov, 0f);
	}

	public ShotBuilder key(int tick, Vec3 pos, Vec3 look, float fov, float roll) {
		Vec3 lens = safe(pos);
		if (trackEntity < 0) lens = clearSight(lens, look);
		else if (trackOrigin != null) lens = clearSight(lens, trackOrigin.add(look));
		keys.add(new Key(tick, lens, look, fov, roll, nextCut, trackEntity < 0 ? Key.FIXED : Key.AIM));
		nextCut = false;
		return this;
	}

	public ShotBuilder follow(int tick, Vec3 subject, Vec3 offset, Vec3 look, float fov, float roll) {
		Vec3 lens = clearSight(safe(subject.add(offset)), subject.add(look), 5);
		keys.add(new Key(tick, lens.subtract(subject), look, fov, roll, nextCut, Key.FOLLOW));
		nextCut = false;
		return this;
	}

	public ShotBuilder track(int entityId) {
		trackEntity = entityId;
		return this;
	}

	public ShotBuilder track(net.minecraft.world.entity.Entity entity) {
		trackOrigin = entity.position();
		return track(entity.getId());
	}

	public ShotBuilder beat(int tick, int duration, int style, String title, String subtitle) {
		beats.add(new Beat(tick, duration, style, title, subtitle));
		return this;
	}

	public CinematicPayload build(int durationTicks) {
		return new CinematicPayload(0, durationTicks, trackEntity, List.copyOf(keys), List.copyOf(beats));
	}

	public boolean sees(Vec3 lens, Vec3 target) {
		BlockPos at = BlockPos.containing(lens);
		if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
		BlockHitResult hit = level.clip(new ClipContext(lens, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 2.5 * 2.5;
	}

	private Vec3 safe(Vec3 pos) {
		Vec3 p = pos;
		for (int i = 0; i < 24; i++) {
			BlockPos at = BlockPos.containing(p);
			if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return p;
			p = p.add(0, 1, 0);
		}
		return p;
	}

	private Vec3 clearSight(Vec3 pos, Vec3 target) {
		return clearSight(pos, target, 14);
	}

	private Vec3 clearSight(Vec3 pos, Vec3 target, int maxLift) {
		Vec3 p = pos;
		for (int i = 0; i < maxLift; i++) {
			BlockHitResult hit = level.clip(new ClipContext(p, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
				CollisionContext.empty()));
			if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 2.5 * 2.5) return p;
			p = safe(p.add(0, 1, 0));
		}
		return pos;
	}
}
