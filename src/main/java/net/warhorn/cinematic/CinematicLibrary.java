package net.warhorn.cinematic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.warhorn.net.CinematicPayload;
import net.warhorn.net.CinematicPayload.Beat;
import net.warhorn.siege.Siege;

public final class CinematicLibrary {
	public static final int INTRO_TICKS = 360;
	public static final int INTRO_CHARGE = 300;
	public static final int WARLORD_TICKS = 300;
	public static final int WARLORD_ROAR_TICK = 178;
	public static final int UNIT_INTRO_TICKS = 190;
	public static final int WARLORD_CHARGE = 240;
	public static final int ENDING_TICKS = 220;
	public static final int LAST_STAND_TICKS = 80;
	public static final int MARCH_FROM = 100;
	public static final double MARCH_SPEED = 0.05;
	public static final double CHARGE_SPEED = 0.3;
	public static final int CHARGE_RAMP = 12;

	private CinematicLibrary() {
	}

	private record Frame(Vec3 center, Vec3 staging, Vec3 out, Vec3 side) {
		static Frame of(Siege siege) {
			Vec3 c = Vec3.atBottomCenterOf(siege.center());
			Vec3 s = Vec3.atBottomCenterOf(siege.staging());
			Vec3 out = s.subtract(c).multiply(1, 0, 1).normalize();
			return new Frame(c, s, out, new Vec3(-out.z, 0, out.x));
		}
	}

	private static final Vec3 UP = new Vec3(0, 1, 0);

	public static CinematicPayload intro(ServerLevel level, Siege siege, Entity captain) {
		Frame f = Frame.of(siege);
		Vec3 front = f.staging.add(f.out.scale(-1));
		Vec3 captainPos = captain != null ? captain.position() : front;
		Vec3 toVillage = f.out.scale(-1);
		ShotBuilder shot = new ShotBuilder(level)
			.key(0, f.center.subtract(f.out.scale(34)).add(UP.scale(30)), f.center.add(f.out.scale(26)), 68f)
			.key(105, f.center.subtract(f.out.scale(12)).add(UP.scale(22)), f.center.add(f.out.scale(34)), 62f)
			.beat(14, 82, Beat.LINE, "warhorn.intro.line1", "")
			.cut()
			.key(105, marched(front, toVillage, 105).add(toVillage.scale(9)).add(f.side.scale(15)).add(UP.scale(3.4)),
				marched(front, toVillage, 105).add(f.side.scale(6)).add(UP.scale(1.2)), 58f, 5f)
			.key(200, marched(front, toVillage, 200).add(toVillage.scale(8)).add(f.side.scale(-15)).add(UP.scale(3.8)),
				marched(front, toVillage, 200).add(f.side.scale(-6)).add(UP.scale(1.2)), 58f, -4f)
			.beat(122, 74, Beat.TITLE, "warhorn.intro.title", "warhorn.intro.subtitle")
			.cut()
			.key(200, marched(captainPos, toVillage, 200).add(toVillage.scale(7.5)).add(UP.scale(1.1)),
				marched(captainPos, toVillage, 200).add(UP.scale(2.1)), 54f)
			.key(INTRO_CHARGE, marched(captainPos, toVillage, 200).add(toVillage.scale(7.5)).add(UP.scale(1.3)),
				marched(captainPos, toVillage, INTRO_CHARGE).add(UP.scale(2.0)), 36f, 2f)
			.beat(218, 76, Beat.LINE, "warhorn.intro.line2", "");
		shot.cut();
		if (captain != null) {
			Vec3 side = f.side;
			Vec3 a = marched(captainPos, toVillage, INTRO_CHARGE), b = marched(captainPos, toVillage, INTRO_TICKS);
			int clearRight = (shot.sees(a.add(toVillage.scale(9)).add(side.scale(3.5)).add(UP.scale(1.0)), a.add(UP.scale(1.5))) ? 1 : 0)
				+ (shot.sees(b.add(toVillage.scale(1.2)).add(side.scale(5)).add(UP.scale(1.0)), b.add(UP.scale(1.5))) ? 1 : 0);
			int clearLeft = (shot.sees(a.add(toVillage.scale(9)).add(side.scale(-3.5)).add(UP.scale(1.0)), a.add(UP.scale(1.5))) ? 1 : 0)
				+ (shot.sees(b.add(toVillage.scale(1.2)).add(side.scale(-5)).add(UP.scale(1.0)), b.add(UP.scale(1.5))) ? 1 : 0);
			if (clearLeft > clearRight) side = side.scale(-1);
			shot.track(captain)
				.follow(INTRO_CHARGE, a, toVillage.scale(9).add(side.scale(3.5)).add(UP.scale(1.0)), UP.scale(1.5), 60f, -3f)
				.follow(INTRO_CHARGE + 36, marched(captainPos, toVillage, INTRO_CHARGE + 36),
					toVillage.scale(4.5).add(side.scale(4.2)).add(UP.scale(1.0)), UP.scale(1.4), 72f, 2f)
				.follow(INTRO_TICKS, b, toVillage.scale(1.2).add(side.scale(5.0)).add(UP.scale(1.1)),
					toVillage.scale(-6).add(UP.scale(1.2)), 80f, 4f);
		} else {
			shot.key(INTRO_CHARGE, marched(front, toVillage, INTRO_CHARGE).add(toVillage.scale(10)).add(UP.scale(0.8)),
					marched(front, toVillage, INTRO_CHARGE).add(UP.scale(1.5)), 62f)
				.key(INTRO_TICKS, marched(front, toVillage, INTRO_CHARGE).add(toVillage.scale(18)).add(f.side.scale(4)).add(UP.scale(0.8)),
					marched(front, toVillage, INTRO_TICKS).add(UP.scale(1.5)), 76f, 3f);
		}
		return shot.build(INTRO_TICKS);
	}

	private static Vec3 marched(Vec3 start, Vec3 toVillage, int tick) {
		return start.add(toVillage.scale(introAdvance(tick)));
	}

	public static double introAdvance(int tick) {
		return advance(tick, MARCH_FROM, INTRO_CHARGE);
	}

	public static double advance(int tick, int walkFrom, int chargeFrom) {
		double walked = MARCH_SPEED * Mth.clamp(tick - walkFrom, 0, Math.max(0, chargeFrom - walkFrom));
		int c = tick - chargeFrom;
		if (c <= 0) return walked;
		double ramp = Math.min(c, CHARGE_RAMP);
		return walked + MARCH_SPEED * ramp + (CHARGE_SPEED - MARCH_SPEED) * ramp * ramp / (2.0 * CHARGE_RAMP)
			+ CHARGE_SPEED * Math.max(0, c - CHARGE_RAMP);
	}

	public static double step(int tick, int walkFrom, int chargeFrom) {
		return advance(tick, walkFrom, chargeFrom) - advance(tick - 1, walkFrom, chargeFrom);
	}

	public static CinematicPayload warlord(ServerLevel level, Siege siege, Entity warlord) {
		Frame f = Frame.of(siege);
		Vec3 w = warlord.position();
		Vec3 toVillage = Vec3.atBottomCenterOf(siege.center()).subtract(w).multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-toVillage.z, 0, toVillage.x);
		double distance = Math.max(8, w.distanceTo(f.center) - 22);
		return new ShotBuilder(level)
			.track(warlord)
			.key(0, w.add(toVillage.scale(distance)).add(UP.scale(6)), UP.scale(2.0), 34f)
			.key(80, w.add(toVillage.scale(distance - 3)).add(UP.scale(4.5)), UP.scale(2.2), 28f)
			.cut()
			.key(80, w.add(toVillage.scale(6.5)).add(side.scale(4.5)).add(UP.scale(1.2)), UP.scale(2.8), 62f, -7f)
			.key(172, w.add(toVillage.scale(5.5)).add(side.scale(-3.2)).add(UP.scale(1.5)), UP.scale(2.6), 52f, 0f)
			.beat(96, 92, Beat.NAMEPLATE, "warhorn.warlord.name", "warhorn.warlord.title")
			.cut()
			.key(172, w.add(toVillage.scale(3.4)).add(UP.scale(2.3)), UP.scale(2.75), 46f)
			.key(WARLORD_CHARGE, w.add(toVillage.scale(2.3)).add(UP.scale(2.45)), UP.scale(2.75), 30f, 3f)
			.cut()
			.follow(WARLORD_CHARGE, w, toVillage.scale(9).add(side.scale(2.5)).add(UP.scale(0.8)), UP.scale(2.2), 58f, -2f)
			.follow(WARLORD_TICKS, w.add(toVillage.scale(advance(WARLORD_TICKS, WARLORD_CHARGE, WARLORD_CHARGE))),
				toVillage.scale(4.2).add(side.scale(3.2)).add(UP.scale(1.1)), UP.scale(2.4), 74f, 3f)
			.build(WARLORD_TICKS);
	}

	public static CinematicPayload unitIntro(ServerLevel level, net.minecraft.core.BlockPos village, Entity unit, String key) {
		Vec3 u = unit.position();
		Vec3 toVillage = Vec3.atBottomCenterOf(village).subtract(u).multiply(1, 0, 1).normalize();
		double r = unit instanceof net.warhorn.entity.CatapultEntity ? 2.0 : 1.0;
		Vec3 side = new Vec3(-toVillage.z, 0, toVillage.x).scale(r);
		toVillage = toVillage.scale(r);
		Vec3 up = UP.scale(r);
		String base = "warhorn.unit." + key;
		return new ShotBuilder(level)
			.track(unit)
			.key(0, u.add(toVillage.scale(4.2)).add(side.scale(1.8)).add(up.scale(0.9)), up.scale(1.5), 56f, -3f)
			.key(78, u.add(toVillage.scale(2.6)).add(side.scale(1.0)).add(up.scale(1.4)), up.scale(1.55), 44f, 0f)
			.beat(10, 64, Beat.NAMEPLATE, base + ".name", base + ".title")
			.cut()
			.key(78, u.subtract(toVillage.scale(4.2)).add(side.scale(-1.9)).add(up.scale(2.6)), toVillage.scale(14).add(up.scale(0.5)), 62f, 0f)
			.key(UNIT_INTRO_TICKS, u.subtract(toVillage.scale(3.2)).add(side.scale(-1.5)).add(up.scale(2.4)), toVillage.scale(18).add(up.scale(0.5)), 54f, 0f)
			.beat(84, 50, Beat.LINE, base + ".line1", "")
			.beat(136, 52, Beat.LINE, base + ".line2", "")
			.build(UNIT_INTRO_TICKS);
	}

	public static CinematicPayload lastStand(ServerLevel level, Siege siege, Entity warlord) {
		Vec3 w = warlord.position();
		Vec3 fwd = Vec3.directionFromRotation(0, warlord.getYRot());
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		return new ShotBuilder(level)
			.track(warlord)
			.key(0, w.add(fwd.scale(4.0)).add(side.scale(2.0)).add(UP.scale(3.0)), UP.scale(1.5), 48f, -4f)
			.key(LAST_STAND_TICKS / 2, w.add(fwd.scale(3.0)).add(side.scale(1.4)).add(UP.scale(2.6)), UP.scale(1.8), 40f, -2f)
			.cut()
			.key(LAST_STAND_TICKS / 2, w.add(fwd.scale(5.0)).add(side.scale(-2.4)).add(UP.scale(2.4)), UP.scale(3.0), 58f, 2f)
			.key(LAST_STAND_TICKS, w.add(fwd.scale(7.5)).add(side.scale(-3.6)).add(UP.scale(3.6)), UP.scale(3.0), 64f, 0f)
			.beat(LAST_STAND_TICKS / 2 + 4, 50, Beat.TITLE, "warhorn.warlord.laststand", "warhorn.warlord.laststand.sub")
			.build(LAST_STAND_TICKS);
	}

	public static CinematicPayload victory(ServerLevel level, Siege siege, Vec3 fallAt) {
		Frame f = Frame.of(siege);
		Vec3 toVillage = f.center.subtract(fallAt).multiply(1, 0, 1).normalize();
		return new ShotBuilder(level)
			.key(0, fallAt.add(toVillage.scale(-4)).add(UP.scale(1.6)), fallAt.add(UP.scale(0.6)), 50f, 4f)
			.key(90, fallAt.add(toVillage.scale(-8)).add(UP.scale(9)), f.center, 62f)
			.key(ENDING_TICKS, f.center.subtract(toVillage.scale(26)).add(UP.scale(28)), f.center, 72f)
			.beat(46, 150, Beat.TITLE, "warhorn.victory.title", "warhorn.victory.subtitle")
			.build(ENDING_TICKS);
	}

	public static CinematicPayload defeat(ServerLevel level, Siege siege) {
		Frame f = Frame.of(siege);
		return new ShotBuilder(level)
			.key(0, f.center.add(f.out.scale(6)).add(UP.scale(4)), f.center, 60f, -3f)
			.key(ENDING_TICKS, f.center.add(f.out.scale(30)).add(UP.scale(34)), f.center, 74f, 0f)
			.beat(30, 160, Beat.TITLE, "warhorn.defeat.title", "warhorn.defeat.subtitle")
			.build(ENDING_TICKS);
	}
}
