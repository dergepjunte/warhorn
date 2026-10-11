package net.warhorn.client.render;

import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.warhorn.entity.WarlordEntity;

public class WarlordRenderState extends IllagerRenderState {
	public WarlordEntity.Action action = WarlordEntity.Action.NONE;
	public float actionProgress;
	public boolean enraged;
	public int combo;
	public boolean armed = true;
	public boolean lastStand;
}
