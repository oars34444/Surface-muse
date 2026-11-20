package dev.surfacemuse.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import org.lwjgl.glfw.GLFW;

public class SurfaceMuseClient implements ClientModInitializer {

    private static KeyBinding openDesigns;

    @Override
    public void onInitializeClient() {
        openDesigns = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.surfacemuse.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.category.surfacemuse"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDesigns.wasPressed()) {
                tryOpenScreen(client);
            }
        });
    }

    private static void tryOpenScreen(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            message(client, "surfacemuse.no_target");
            return;
        }
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getSide();
        BlockState base = client.world.getBlockState(anchor);
        var suggestions = DesignData.suggest(base, face);
        if (suggestions.isEmpty()) {
            message(client, "surfacemuse.unsupported");
            return;
        }
        var kind = DesignData.kindOf(face);
        client.setScreen(new DesignScreen(base, kind, suggestions, anchor, face));
    }

    private static void message(MinecraftClient client, String key) {
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(key), true);
        }
    }
}
