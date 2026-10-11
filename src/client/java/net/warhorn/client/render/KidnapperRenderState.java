package net.warhorn.client.render;

import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.warhorn.entity.KidnapperEntity;

public class KidnapperRenderState extends IllagerRenderState {
	public KidnapperEntity.State state = KidnapperEntity.State.HUNT;
	public float stateTime;
}
