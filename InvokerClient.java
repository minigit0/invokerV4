package com.invokermod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class InvokerClient implements ClientModInitializer {
    private static final int[] orbs = {-1, -1};
    private static final int[] cds = new int[6];

    private static final int[] COLORS = {0xFFFF5A1E, 0xFF2E7BFF, 0xFFE6F7FF};

    private KeyBinding[] keys;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(InvokerMod.MOD_ID, "main"));
        keys = new KeyBinding[]{
                KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invoker.fire", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category)),
                KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invoker.water", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, category)),
                KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invoker.air", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, category))
        };

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> {
            List<Integer> d = payload.data();
            orbs[0] = d.get(0);
            orbs[1] = d.get(1);
            for (int i = 0; i < 6; i++) cds[i] = d.get(2 + i);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int i = 0; i < 6; i++) if (cds[i] > 0) cds[i]--;
            if (client.player == null) return;
            for (int i = 0; i < keys.length; i++) {
                while (keys[i].wasPressed()) {
                    ClientPlayNetworking.send(new OrbPayload(i));
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderHud(drawContext));
    }

    private static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int y = h - 58;
        int x0 = w / 2 - 14;

        for (int i = 0; i < 2; i++) {
            int x = x0 + i * 16;
            ctx.fill(x - 1, y - 1, x + 13, y + 13, 0xFF000000);
            int c = orbs[i] >= 0 ? COLORS[orbs[i]] : 0xFF444444;
            ctx.fill(x, y, x + 12, y + 12, c);
        }

        int idx = Spells.index(orbs[0], orbs[1]);
        if (idx >= 0) {
            String text = Spells.NAMES[idx] + (cds[idx] > 0 ? " " + ((cds[idx] + 19) / 20) + " с" : " (готов)");
            ctx.drawText(mc.textRenderer, text, w / 2 + 20, y + 2, cds[idx] > 0 ? 0xFFFF7777 : 0xFFFFFFFF, true);
        }
    }
}
