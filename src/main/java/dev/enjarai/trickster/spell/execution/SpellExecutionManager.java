package dev.enjarai.trickster.spell.execution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.enjarai.trickster.advancement.criterion.ModCriteria;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.execution.source.SpellSource;
import dev.enjarai.trickster.spell.SpellPart;
import dev.enjarai.trickster.spell.mana.ManaPool;
import dev.enjarai.trickster.spell.trick.blunder.BlunderException;
import dev.enjarai.trickster.spell.trick.blunder.NaNBlunder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.text.Text;

import java.util.*;

public class SpellExecutionManager {
    public static final Codec<SpellExecutionManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("capacity", 5).forGetter(e -> e.capacity),
            Codec.unboundedMap(Codec.STRING.xmap(Integer::parseInt, Object::toString), SpellExecutor.CODEC)
                    .fieldOf("spells").forGetter((e) -> e.spells)
    ).apply(instance, SpellExecutionManager::new));

    // NOT final, we want to be able to change this perchance
    private int capacity;
    private SpellSource source;
    private final Int2ObjectMap<SpellExecutor> spells;

    private SpellExecutionManager(int initialCapacity, Map<Integer, SpellExecutor> spells) {
        this(initialCapacity);
        this.spells.putAll(spells);
    }

    public SpellExecutionManager(int initialCapacity) {
        this.spells = new Int2ObjectOpenHashMap<>(initialCapacity);
        this.capacity = initialCapacity;
    }

    public boolean queue(SpellPart spell, List<Fragment> arguments) {
        return queue(new SpellExecutor(spell, arguments));
    }

    public boolean queue(SpellPart spell, List<Fragment> arguments, ManaPool poolOverride) {
        return queue(new SpellExecutor(spell, new ExecutionState(arguments, poolOverride)));
    }

    public boolean queue(SpellExecutor executor) {
        for (int i = 0; i < capacity; i++) {
            if (spells.putIfAbsent(i, executor) == null) {
                return true;
            }
        }
        return false;
    }

    public void tick(ExecutorDoneCallback executorDoneCallback) {
        if (source == null)
            return;

        for (var iterator = spells.int2ObjectEntrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var spell = entry.getValue();

            try {
                if (spell.run(source).isEmpty()) {
                    executorDoneCallback.callTheBack(entry.getIntKey(), spell);
                } else {
                    iterator.remove();
                }
            } catch (BlunderException e) {
                if (e instanceof NaNBlunder)
                    source.getPlayer().ifPresent(ModCriteria.NAN_NUMBER::trigger);

                source.getPlayer().ifPresent(player -> player.sendMessage(e.createMessage().append(" (").append("spell.formatStackTrace()").append(")")));
            } catch (Exception e) {
                source.getPlayer().ifPresent(player -> player.sendMessage(Text.literal("Uncaught exception in spell: " + e.getMessage())));
            }
        }
    }

    public void setSource(SpellSource source) {
        this.source = source;
    }

    public void killAll() {
        spells.clear();
    }

    public void kill(int index) {
        spells.remove(index);
    }

    public interface ExecutorDoneCallback {
        void callTheBack(int index, SpellExecutor executor);
    }
}
