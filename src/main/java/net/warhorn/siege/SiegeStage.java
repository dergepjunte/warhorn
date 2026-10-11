package net.warhorn.siege;

public enum SiegeStage {
	OMEN(true),
	INTRO(true),
	ACT(false),
	INTERLUDE(false),
	WARLORD_INTRO(true),
	WARLORD(false),
	VICTORY(true),
	DEFEAT(true),
	FINISHED(true);

	private final boolean scripted;

	SiegeStage(boolean scripted) {
		this.scripted = scripted;
	}

	public boolean scripted() {
		return scripted;
	}

	public boolean fighting() {
		return this == ACT || this == WARLORD;
	}

	public boolean ravaging() {
		return fighting() || this == INTERLUDE || this == WARLORD_INTRO;
	}
}
