package dev.surfacemuse.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesignScreen extends Screen {

    private static final int PATTERN_PREVIEW_COLS = 6;
    private static final int PATTERN_PREVIEW_ROWS = 6;
    private static final int CELL = 18;
    private static final int LIST_WIDTH = 130;
    private static final int VISIBLE_ROWS = 6;

    private final BlockState baseState;
    private final String kindLabel;
    private final DesignData.SurfaceKind kind;
    private final List<DesignData.Suggestion> suggestions;
    private final BlockPos anchor;
    private final Direction face;
    private final Map<Block, ItemStack> stackCache = new HashMap<>();

    private int selected = 0;
    private int listScroll = 0;
    private final List<Integer> rowTops = new ArrayList<>();
    private final List<Integer> rowBottoms = new ArrayList<>();

    public DesignScreen(BlockState baseState, DesignData.SurfaceKind kind,
                        List<DesignData.Suggestion> suggestions, BlockPos anchor,
                        Direction face) {
        super(Component.literal("Surface Muse - " + kind.label + " suggestions"));
        this.baseState = baseState;
        this.kind = kind;
        this.kindLabel = kind.label;
        this.suggestions = suggestions;
        this.anchor = anchor;
        this.face = face;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        rowTops.clear();
        rowBottoms.clear();

        int left = (this.width - (LIST_WIDTH + 20 + PATTERN_PREVIEW_COLS * CELL)) / 2;
        int top = 40;

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int listX = left;
        int previewX = left + LIST_WIDTH + 20;

        int last = Math.min(listScroll + VISIBLE_ROWS, suggestions.size());
        for (int i = listScroll; i < last; i++) {
            DesignData.Suggestion suggestion = suggestions.get(i);
            int y = top + (i - listScroll) * 24;
            int bg = i == selected ? 0xFF2E6E4F : 0xFF333333;
            guiGraphics.fill(listX, y - 2, listX + LIST_WIDTH, y + 20, bg);
            guiGraphics.drawString(this.font,
                    Component.literal(suggestion.design().title()), listX + 4, y + 5, 0xFFFFFF);
            rowTops.add(y - 2);
            rowBottoms.add(y + 20);
        }

        DesignData.Suggestion sel = suggestions.get(selected);
        for (int row = 0; row < Math.min(PATTERN_PREVIEW_ROWS, sel.design().pattern().size()); row++) {
            String line = sel.design().pattern().get(row);
            for (int col = 0; col < Math.min(PATTERN_PREVIEW_COLS, line.length()); col++) {
                ItemStack stack = stackFor(line.charAt(col), sel);
                if (stack != null) {
                    guiGraphics.renderItem(stack, previewX + col * CELL, top + row * CELL);
                }
            }
        }

        int textY = top + PATTERN_PREVIEW_ROWS * CELL + 8;
        guiGraphics.drawString(this.font,
                Component.translatable("surfacemuse.parent_block", baseState.getBlock().getName()),
                previewX, textY, 0xB0B0B0);
        guiGraphics.drawString(this.font,
                Component.translatable("surfacemuse.secondary", sel.secondary().block().getName()),
                previewX, textY + 12, 0xB0B0B0);
        if (sel.accent() != null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("surfacemuse.accent", sel.accent().block().getName()),
                    previewX, textY + 24, 0xB0B0B0);
        }

        guiGraphics.drawString(this.font,
                Component.literal(sel.secondary().explanation()),
                previewX, textY + 38, 0x8CBF9E);
        guiGraphics.drawString(this.font,
                Component.literal(sel.design().desc()),
                previewX, textY + 50, 0x909090);
        guiGraphics.drawString(this.font,
                Component.literal("Up/Down: browse - P: place on the surface - Click/Enter: send block list to chat - Esc: close"),
                previewX, textY + 64, 0x707070);
    }

    private ItemStack stackFor(char c, DesignData.Suggestion suggestion) {
        Block target = switch (c) {
            case 's' -> suggestion.secondary().block();
            case 'a' -> suggestion.accent().block();
            default -> baseState.getBlock();
        };
        if (target == null) {
            return null;
        }
        return stackCache.computeIfAbsent(target, b -> new ItemStack(b.asItem()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int j = 0; j < rowTops.size(); j++) {
            if (mouseY >= rowTops.get(j) && mouseY <= rowBottoms.get(j)) {
                selected = listScroll + j;
                sendPaletteToChat();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && selected < suggestions.size() - 1) {
            selected++;
        } else if (scrollY > 0 && selected > 0) {
            selected--;
        }
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN && selected < suggestions.size() - 1) {
            selected++;
            clampScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP && selected > 0) {
            selected--;
            clampScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendPaletteToChat();
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            applySelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applySelected() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        DesignData.Suggestion sel = suggestions.get(selected);
        SmartPlacer.Plan plan = SmartPlacer.plan(
                this.minecraft.level,
                anchor,
                face,
                baseState.getBlock(),
                sel.design(),
                sel.secondary(),
                sel.accent());
        int result = SmartPlacer.apply(this.minecraft, plan);
        if (result > 0 && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.literal("[Surface Muse] applied " + sel.design().title()
                            + " - " + result + " blocks placed ("
                            + plan.surface().width() + "x" + plan.surface().height()
                            + " region)"),
                    true);
            this.onClose();
        }
    }

    private void clampScroll() {
        if (selected < listScroll) {
            listScroll = selected;
        }
        if (selected >= listScroll + VISIBLE_ROWS) {
            listScroll = selected - VISIBLE_ROWS + 1;
        }
    }

    private void sendPaletteToChat() {
        if (this.minecraft != null && this.minecraft.player != null) {
            DesignData.Suggestion sel = suggestions.get(selected);
            this.minecraft.player.displayClientMessage(
                    Component.literal("[Surface Muse] " + kindLabel + ": "
                            + sel.design().title()
                            + " | base: " + baseState.getBlock().getName().getString()
                            + " | detail: " + sel.secondary().block().getName().getString()
                            + " (" + sel.secondary().explanation() + ")"
                            + (sel.accent() != null
                                    ? " | accent: " + sel.accent().block().getName().getString()
                                    : "")),
                    false);
        }
    }
}
