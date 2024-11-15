package dev.enjarai.trickster.spell.trick;

import dev.enjarai.trickster.Trickster;
import dev.enjarai.trickster.cca.ModEntityComponents;
import dev.enjarai.trickster.item.component.FragmentComponent;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.Pattern;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.blunder.BlunderException;
import dev.enjarai.trickster.spell.blunder.CantEditBlockBlunder;
import dev.enjarai.trickster.spell.blunder.IncorrectFragmentBlunder;
import dev.enjarai.trickster.spell.blunder.MissingFragmentBlunder;
import dev.enjarai.trickster.spell.fragment.EntityFragment;
import dev.enjarai.trickster.spell.fragment.FragmentType;
import dev.enjarai.trickster.spell.fragment.ListFragment;
import dev.enjarai.trickster.spell.fragment.VectorFragment;
import dev.enjarai.trickster.util.Hamt;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

public abstract class Trick {
    public static final Identifier TRICK_RANDOM = Trickster.id("trick");

    protected final Pattern pattern;

    public Trick(Pattern pattern) {
        this.pattern = pattern;
    }

    public final Pattern getPattern() {
        return pattern;
    }

    public abstract Fragment activate(SpellContext ctx, List<Fragment> fragments) throws BlunderException;

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> T expectInput(List<Fragment> fragments, FragmentType<T> type, int index) throws BlunderException {
        if (fragments.size() <= index) {
            throw new MissingFragmentBlunder(this, index, type.getName());
        }
        var fragment = fragments.get(index);
        if (fragment.type() != type) {
            throw new IncorrectFragmentBlunder(this, index, type.getName(), fragment);
        }
        return (T) fragment;
    }

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> T expectType(Fragment fragment, FragmentType<T> type) throws BlunderException {
        if (fragment.type() != type) {
            throw new IncorrectFragmentBlunder(this, -1, type.getName(), fragment);
        }
        return (T) fragment;
    }

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> Optional<T> supposeInput(List<Fragment> fragments, FragmentType<T> type, int index) throws BlunderException {
        if (fragments.size() <= index) {
            return Optional.empty();
        }
        var fragment = fragments.get(index);
        if (fragment.type() != type) {
            throw new IncorrectFragmentBlunder(this, index, type.getName(), fragment);
        }
        return Optional.of((T) fragment);
    }

    protected Optional<Fragment> supposeInput(List<Fragment> fragments, int index) throws BlunderException {
        if (fragments.size() <= index) {
            return Optional.empty();
        }
        return Optional.of(fragments.get(index));
    }

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> Optional<T> supposeType(Fragment fragment, FragmentType<T> type) throws BlunderException {
        if (fragment.type() != type) {
            return Optional.empty();
        }
        return Optional.of((T) fragment);
    }

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> T expectInput(List<Fragment> fragments, Class<T> type, int index) throws BlunderException {
        if (fragments.size() <= index) {
            throw new MissingFragmentBlunder(this, index, Text.of(type.getSimpleName()));
        }
        var fragment = fragments.get(index);
        if (!type.isInstance(fragment)) {
            throw new IncorrectFragmentBlunder(this, index, Text.literal(type.getSimpleName()), fragment);
        }
        return (T) fragment;
    }

    @SuppressWarnings("unchecked")
	protected <T extends Fragment> T expectType(Fragment fragment, Class<T> type, int index) throws BlunderException {
        if (!type.isInstance(fragment)) {
            throw new IncorrectFragmentBlunder(this, index, Text.literal(type.getSimpleName()), fragment);
        }
        return (T) fragment;
    }

    protected Fragment expectInput(List<Fragment> fragments, int index) throws BlunderException {
        if (fragments.size() <= index) {
            throw new MissingFragmentBlunder(this, index, Text.of("any"));
        }
        return fragments.get(index);
    }

    protected void expectCanBuild(SpellContext ctx, BlockPos... positions) {
        if (ctx.source().getPlayer().isEmpty()) {
            return;
        }

        var player = ctx.source().getPlayer().get();

        if (player.interactionManager.getGameMode().isBlockBreakingRestricted()) {
            throw new CantEditBlockBlunder(this, positions[0]);
        }

        for (var pos : positions) {
            if (!player.canModifyAt(ctx.source().getWorld(), pos)) {
                throw new CantEditBlockBlunder(this, pos);
            }
        }
    }

    protected void tryWard(SpellContext triggerCtx, Entity target, List<Fragment> fragments) throws BlunderException {
        if (target instanceof ServerPlayerEntity player) {
            var triggerCaster = triggerCtx.source().getCaster();

            if (triggerCaster.map(c -> c.equals(player)).orElse(false)) {
                return;
            }

            var sourceFragment = triggerCaster
                    .<Fragment>map(EntityFragment::from)
                    .orElse(new VectorFragment(triggerCtx.source().getPos()));
            var charmMap = FragmentComponent.getUserMergedMap(player, "charm", Hamt::empty);
            var spell = charmMap.get(getPattern());
            var caster = ModEntityComponents.CASTER.get(player);
            spell.ifPresent(s -> caster.queueSpellAndCast(s, List.of(sourceFragment, new ListFragment(fragments)), Optional.empty()));
        }
    }

    public MutableText getName() {
        var id = Tricks.REGISTRY.getId(this);
        if (id == null) {
            return Text.literal("Unregistered");
        }
        return Text.literal("").append(
                Text.translatable(Trickster.MOD_ID + ".trick." + id.getNamespace() + "." + id.getPath())
                        .withColor(FragmentType.PATTERN.color().getAsInt()));
    }
}
