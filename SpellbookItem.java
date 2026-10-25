package com.invokermod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class SpellbookItem extends Item {
    private final int cooldown;

    public SpellbookItem(Settings settings, int cooldown) {
        super(settings);
        this.cooldown = cooldown;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
            if (InvokerManager.cast(sp, sw)) {
                // кулдаун на обе книги, чтобы нельзя было обойти его, меняя книги
                sp.getItemCooldownManager().set(new ItemStack(InvokerMod.SPELLBOOK), cooldown);
                sp.getItemCooldownManager().set(new ItemStack(InvokerMod.SUPER_SPELLBOOK), cooldown);
            }
        }
        return ActionResult.SUCCESS;
    }
}
