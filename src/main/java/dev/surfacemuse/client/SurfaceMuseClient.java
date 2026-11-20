package dev.surfacemuse.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public class SurfaceMuseClient implements ClientModInitializer {

    private static KeyMapping openDesigns;

    @Override
    public void onInitializeClient() {
        openDesigns = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.surfacemuse.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.category.surfacemuse"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDesigns.consumeClick()) {
                tryOpenScreen(client);
            }
        });
    }

    private static void tryOpenScreen(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            message(client, "surfacemuse.no_target");
            return;
        }
        BlockState base = client.level.getBlockState(blockHit.getBlockPos());
        var suggestions = DesignData.suggest(base, blockHit.getDirection());
        if (suggestions.isEmpty()) {
            message(client, "surfacemuse.unsupported");
            return;
        }
        var kind = DesignData.kindOf(blockHit.getDirection());
        client.setScreen(new DesignScreen(base, kind, suggestions));
    }

    private static void message(Minecraft client, String key) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
