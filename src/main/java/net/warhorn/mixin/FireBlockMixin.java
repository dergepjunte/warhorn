package net.warhorn.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.warhorn.siege.Ruination;
import net.warhorn.siege.Siege;
import net.warhorn.siege.SiegeManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FireBlock.class)
abstract class FireBlockMixin {
	@WrapMethod(method = "tick")
	private void warhorn$siegeFire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
		Siege siege = SiegeManager.find(level, pos, Ruination.CORE_RADIUS);
		if (siege == null || !siege.stage().ravaging()) {
			original.call(state, level, pos, random);
			return;
		}
		if (Ruination.exhausted(siege)) {
			if (random.nextInt(3) == 0) level.removeBlock(pos, false);
			else level.scheduleTick(pos, (FireBlock) (Object) this, 10 + random.nextInt(10));
			return;
		}
		Ruination.begin();
		try {
			original.call(state, level, pos, random);
		} finally {
			Ruination.end();
		}
	}
}
