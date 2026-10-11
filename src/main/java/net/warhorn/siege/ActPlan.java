package net.warhorn.siege;

import java.util.List;
import java.util.Map;

public final class ActPlan {
	public record Wave(Map<Unit, Integer> units) {
	}

	public record Act(String key, List<Wave> waves) {
	}

	public static final List<Act> ACTS = List.of(
		new Act("vanguard", List.of(
			new Wave(Map.of(Unit.PILLAGER, 5, Unit.VINDICATOR, 3)),
			new Wave(Map.of(Unit.PILLAGER, 4, Unit.VINDICATOR, 3, Unit.SAPPER, 2, Unit.KIDNAPPER, 1)),
			new Wave(Map.of(Unit.PILLAGER, 5, Unit.VINDICATOR, 2, Unit.WITCH, 1, Unit.SAPPER, 1, Unit.RAM, 1, Unit.CATAPULT, 1)))),
		new Act("siege", List.of(
			new Wave(Map.of(Unit.VINDICATOR, 4, Unit.PILLAGER, 4, Unit.EVOKER, 1, Unit.SAPPER, 2, Unit.KIDNAPPER, 1)),
			new Wave(Map.of(Unit.RAVAGER_RIDER, 1, Unit.VINDICATOR, 2, Unit.PILLAGER, 2, Unit.RAM, 1, Unit.CATAPULT, 1)),
			new Wave(Map.of(Unit.PILLAGER, 6, Unit.VINDICATOR, 4, Unit.EVOKER, 1, Unit.WITCH, 1, Unit.KIDNAPPER, 2)))),
		new Act("storm", List.of(
			new Wave(Map.of(Unit.RAVAGER, 2, Unit.VINDICATOR, 5)),
			new Wave(Map.of(Unit.EVOKER, 2, Unit.VINDICATOR, 4, Unit.PILLAGER, 4, Unit.CATAPULT, 1)),
			new Wave(Map.of(Unit.RAVAGER_RIDER, 2, Unit.VINDICATOR, 6, Unit.PILLAGER, 3)))));

	public static final Wave WARLORD_GUARD = new Wave(Map.of(Unit.VINDICATOR, 4, Unit.PILLAGER, 2));

	public static final int INTERLUDE_TICKS = 20 * 75;
	public static final int WAVE_TIMEOUT_TICKS = 20 * 150;

	private ActPlan() {
	}

	public static int scaled(int base, int defenders, int omenLevel) {
		double multiplier = (1.0 + 0.5 * Math.max(0, defenders - 1)) * (1.0 + 0.15 * Math.max(0, omenLevel - 1));
		return Math.max(base > 0 ? 1 : 0, (int) Math.round(base * multiplier));
	}
}
