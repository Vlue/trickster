package dev.enjarai.trickster.spell.trick.map;

import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.Pattern;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.fragment.MapFragment;
import dev.enjarai.trickster.spell.fragment.VoidFragment;
import dev.enjarai.trickster.spell.trick.Trick;
import dev.enjarai.trickster.spell.blunder.BlunderException;
import java.util.List;

public class MapGetTrick extends Trick {
    public MapGetTrick() {
        super(Pattern.of(0, 3, 6, 8, 5, 2, 4, 6, 7));
    }

    @Override
    public Fragment activate(SpellContext ctx, List<Fragment> fragments) throws BlunderException {
        return expectInput(fragments, MapFragment.class, 0).map().get(expectInput(fragments, 1)).getOrElse(VoidFragment.INSTANCE);
    }
}
