package com.invokermod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class InvokerMod implements ModInitializer {
    public static final String MOD_ID = "invoker";
    public static Item SPELLBOOK;
    public static Item SUPER_SPELLBOOK;

    @Override
    public void onInitialize() {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "spellbook"));
        SPELLBOOK = Registry.register(Registries.ITEM, key,
                new SpellbookItem(new Item.Settings().registryKey(key).maxCount(1), Spells.BOOK_COOLDOWN));

        RegistryKey<Item> superKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "super_spellbook"));
        SUPER_SPELLBOOK = Registry.register(Registries.ITEM, superKey,
                new SpellbookItem(new Item.Settings().registryKey(superKey).maxCount(1).rarity(Rarity.EPIC)
                        .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true), Spells.SUPER_BOOK_COOLDOWN));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(SPELLBOOK);
            entries.add(SUPER_SPELLBOOK);
        });

        PayloadTypeRegistry.playC2S().register(OrbPayload.ID, OrbPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OrbPayload.ID, (payload, context) -> {
            int t = payload.type();
            if (t >= 0 && t <= 2) {
                InvokerManager.addOrb(context.player(), t);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(InvokerManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> InvokerManager.clearAll());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> InvokerManager.sync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> InvokerManager.remove(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            InvokerManager.reset(newPlayer);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(InvokerManager::allowDamage);
    }
}
