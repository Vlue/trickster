package dev.enjarai.trickster.spell.trick.block;

import dev.enjarai.trickster.Trickster;
import dev.enjarai.trickster.block.ModBlocks;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.Pattern;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.fragment.FragmentType;
import dev.enjarai.trickster.spell.trick.Trick;
import dev.enjarai.trickster.spell.blunder.BlockTooHardBlunder;
import dev.enjarai.trickster.spell.blunder.BlockUnoccupiedBlunder;
import dev.enjarai.trickster.spell.blunder.BlunderException;

import java.util.List;

public class BreakBlockTrick extends Trick {
    public BreakBlockTrick() {
        super(Pattern.of(1, 5, 8, 6, 4, 1, 0, 3, 6));
    }

    @Override
    public Fragment activate(SpellContext ctx, List<Fragment> fragments) throws BlunderException {
        var pos = expectInput(fragments, FragmentType.VECTOR, 0);
        var blockPos = pos.toBlockPos();
        var world = ctx.source().getWorld();
        var state = world.getBlockState(blockPos);

        if (state.isAir()) {
            throw new BlockUnoccupiedBlunder(this, pos);
        }

        float hardness = state.getBlock().getHardness();

        if (!state.isIn(ModBlocks.UNBREAKABLE) && hardness >= 0 && hardness < Trickster.CONFIG.maxBlockBreakingHardness()) {
            ctx.useMana(this, Math.max(hardness, 8));
            ctx.source().getCaster().ifPresentOrElse(c -> world.breakBlock(blockPos, true, c), () -> world.breakBlock(blockPos, true));
        } else {
            throw new BlockTooHardBlunder(this);
        }

        return pos;
    }
}
