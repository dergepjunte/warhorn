package net.warhorn.client.render;

import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.warhorn.entity.SapperEntity;

public class SapperRenderState extends IllagerRenderState {
	public SapperEntity.State state = SapperEntity.State.CARRY;
	public float stateTime;
}
