package dev.enjarai.trickster.spell.fragment;

import dev.enjarai.trickster.EndecTomfoolery;
import dev.enjarai.trickster.Trickster;
import dev.enjarai.trickster.item.component.FragmentComponent;
import dev.enjarai.trickster.item.component.ModComponents;
import dev.enjarai.trickster.pond.SlotHolderDuck;
import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.blunder.*;
import dev.enjarai.trickster.spell.trick.Trick;
import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.owo.serialization.endec.EitherEndec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

import com.mojang.datafixers.util.Either;

public record SlotFragment(int slot, Optional<Either<BlockPos, UUID>> source) implements Fragment {
    public static final StructEndec<SlotFragment> ENDEC = StructEndecBuilder.of(
            Endec.INT.fieldOf("slot", SlotFragment::slot),
            EndecTomfoolery.safeOptionalOf(new EitherEndec<>(EndecTomfoolery.ALWAYS_READABLE_BLOCK_POS, EndecTomfoolery.UUID, true)).fieldOf("source", SlotFragment::source),
            SlotFragment::new
    );

    @Override
    public FragmentType<?> type() {
        return FragmentType.SLOT;
    }

    @Override
    public Text asText() {
        return Text.literal("slot %d at %s".formatted(slot,
                source.map(either -> {
                    var mapped = either
                        .mapLeft(blockPos -> "(%d, %d, %d)".formatted(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                        .mapRight(uuid -> uuid.toString());
                    return mapped.right().orElseGet(() -> mapped.left().get());
                }).orElse("caster")));
    }

    @Override
    public boolean asBoolean() {
        return true;
    }

    @Override
    public int getWeight() {
        return 64;
    }

    public void setStack(ItemStack itemStack, Trick trick, SpellContext ctx) {
        var inventory = getInventory(trick, ctx);
        inventory.trickster$slot_holder$setStack(slot, itemStack);
    }

    public void writeSpell(Fragment fragment, boolean closed, Optional<Text> name, Optional<ServerPlayerEntity> player, Trick trick, SpellContext ctx) throws BlunderException {
        var inventory = getInventory(trick, ctx);
        var stack = inventory.trickster$slot_holder$getStack(slot);
        var updated = FragmentComponent.writeSpell(stack, fragment, closed, player, name);
        if (updated.isEmpty()) throw new ImmutableItemBlunder(trick);
        inventory.trickster$slot_holder$setStack(slot, updated.get());
    }

    public void clearSpell(Trick trick, SpellContext ctx) throws BlunderException {
        var inventory = getInventory(trick, ctx);
        ItemStack stack = inventory.trickster$slot_holder$getStack(slot);
        var updated = FragmentComponent.clearSpell(stack);
        if (updated.isEmpty()) throw new ImmutableItemBlunder(trick);
        inventory.trickster$slot_holder$setStack(slot, updated.get());
    }

    public void swapWith(Trick trickSource, SpellContext ctx, SlotFragment other) throws BlunderException {
        var otherInv = other.getInventory(trickSource, ctx);
        var inv = getInventory(trickSource, ctx);

        if (equals(other)) {
            var stack = inv.trickster$slot_holder$takeFromSlot(slot, getStack(trickSource, ctx).getCount());
            inv.trickster$slot_holder$setStack(slot, stack);
        } else {
            var otherStack = other.getStack(trickSource, ctx);
            var stack = getStack(trickSource, ctx);

            var movedOtherStack = other.move(trickSource, ctx, otherStack.getCount(), getSourcePos(trickSource, ctx));
            ItemStack movedStack;

            try {
                movedStack = move(trickSource, ctx, stack.getCount(), other.getSourcePos(trickSource, ctx));
            } catch (Exception e) {
                ctx.source().offerOrDropItem(movedOtherStack);
                throw e;
            }

            try {
                if (!inv.trickster$slot_holder$setStack(slot, movedOtherStack))
                    throw new ItemInvalidBlunder(trickSource);
            } catch (Exception e) {
                ctx.source().offerOrDropItem(movedOtherStack);
                ctx.source().offerOrDropItem(movedStack);
                throw e;
            }

            try {
                if(!otherInv.trickster$slot_holder$setStack(other.slot(), movedStack))
                    throw new ItemInvalidBlunder(trickSource);
            } catch (UnsupportedOperationException e) {
                throw new ItemInvalidBlunder(trickSource);
            } catch (Exception e) {
                ctx.source().offerOrDropItem(movedStack);
                throw e;
            }
        }
    }

    public ItemStack move(Trick trickSource, SpellContext ctx) throws BlunderException {
        return move(trickSource, ctx, 1);
    }

    public ItemStack move(Trick trickSource, SpellContext ctx, int amount) {
        return move(trickSource, ctx, amount, ctx.source().getBlockPos());
    }

    public ItemStack move(Trick trickSource, SpellContext ctx, int amount, BlockPos pos) throws BlunderException {
        var stack = getStack(trickSource, ctx);

        if (stack.getCount() < amount)
            throw new MissingItemBlunder(trickSource);

        ctx.useMana(trickSource, getMoveCost(trickSource, ctx, pos, amount));
        return takeFromSlot(trickSource, ctx, amount);
    }

    /**
     * Instead of taking items from the slot, directly reference the stored stack to modify it.
     */
    public ItemStack reference(Trick trickSource, SpellContext ctx) {
        var stack = getStack(trickSource, ctx);

        if (stack.isEmpty())
            throw new MissingItemBlunder(trickSource);

        return stack;
    }

    public Item getItem(Trick trickSource, SpellContext ctx) throws BlunderException {
        return getStack(trickSource, ctx).getItem();
    }

    private ItemStack getStack(Trick trickSource, SpellContext ctx) throws BlunderException {
        SlotHolderDuck inventory = getInventory(trickSource, ctx);

        if (slot < 0 || slot >= inventory.trickster$slot_holder$size())
            throw new NoSuchSlotBlunder(trickSource);

        return inventory.trickster$slot_holder$getStack(slot);
    }

    private ItemStack takeFromSlot(Trick trickSource, SpellContext ctx, int amount) throws BlunderException {
        SlotHolderDuck inventory = getInventory(trickSource, ctx);

        if (slot < 0 || slot >= inventory.trickster$slot_holder$size())
            throw new NoSuchSlotBlunder(trickSource);

        return inventory.trickster$slot_holder$takeFromSlot(slot, amount);
    }

    private SlotHolderDuck getInventory(Trick trickSource, SpellContext ctx) throws BlunderException {
        return source.map(s -> {
            if (s.left().isPresent()) {
                var e = ctx.source().getWorld().getBlockEntity(s.left().get());
                if (e instanceof SlotHolderDuck holder)
                    return holder;
                else if (e instanceof Inventory inv)
                    return new BridgedSlotHolder(inv);
                else throw new BlockInvalidBlunder(trickSource);
            } else {
                var e = ctx.source().getWorld().getEntity(s.right().get());
                if (e instanceof SlotHolderDuck holder)
                    return holder;
                else if (e instanceof Inventory inv)
                    return new BridgedSlotHolder(inv);
                else throw new EntityInvalidBlunder(trickSource);
            }
        }).orElseGet(() -> ctx.source().getPlayer()
            .map(player -> new BridgedSlotHolder(player.getInventory()))
            .orElseThrow(() -> new NoPlayerBlunder(trickSource)));
    }

    private float getMoveCost(Trick trickSource, SpellContext ctx, BlockPos pos, int amount) throws BlunderException {
        return source.map(s -> {
            if (s.left().isPresent()) {
                return s.left().get().toCenterPos();
            } else {
                if (ctx.source().getWorld().getEntity(s.right().get()) instanceof Entity entity)
                    return entity.getPos();
                else throw new EntityInvalidBlunder(trickSource);
            }
        }).map(blockPos -> 8 + (float) (pos.toCenterPos().distanceTo(blockPos) * amount * 0.5)).orElse(0f);
    }

    private class BridgedSlotHolder implements SlotHolderDuck {
        private Inventory inv;

        public BridgedSlotHolder(Inventory inv) {
            this.inv = inv;
        }

        public int trickster$slot_holder$size() {
            return inv.size();
        }

        public ItemStack trickster$slot_holder$getStack(int slot) {
            return inv.getStack(slot);
        }

        public ItemStack trickster$slot_holder$takeFromSlot(int slot, int amount) {
            var stack = inv.getStack(slot);
            var result = stack.copyWithCount(amount);
            stack.decrement(amount);
            return result;
        }
    }
}
