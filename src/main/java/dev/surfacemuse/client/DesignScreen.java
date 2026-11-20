package dev.surfacemuse.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla-styled suggestion browser: classic gray beveled panel like container
 * screens, slotted item grids like the survival inventory, fake tooltip-free
 * rows with hover highlight, and real Minecraft button widgets.
 */
public class DesignScreen extends Screen {

    private static final int CELL = 18;
    private static final int VISIBLE_ROWS = 6;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int PANEL_DARK_SHADOW = 0xFF373737;
    private static final int PANEL_MID_SHADOW = 0xFF8B8B8B;
    private static final int PANEL_LIGHT_EDGE = 0xFF565656;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_HIGHLIGHT = 0xFFBBBBBB;

    private final BlockState baseState;
    private final DesignData.SurfaceKind kind;
    private final List<DesignData.Suggestion> suggestions;
    private final BlockPos anchor;
    private final Direction face;
    private final Map<Block, ItemStack> stackCache = new HashMap<>();

    private int selected = 0;
    private int listScroll = 0;
    private final List<Integer> rowTops = new ArrayList<>();
    private final List<Integer> rowBottoms = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW = 222;
    private int panelH = 186;
    private int listX;
    private int listW = 78;
    private int listTop;
    private int previewX;

    public DesignScreen(BlockState baseState, DesignData.SurfaceKind kind,
                        List<DesignData.Suggestion> suggestions, BlockPos anchor,
                        Direction face) {
        super(Component.literal("Surface Muse - "
                + kind.label.toLowerCase(java.util.Locale.ROOT)));
        this.baseState = baseState;
        this.kind = kind;
        this.suggestions = suggestions;
        this.anchor = anchor;
        this.face = face;
    }

    @Override
    protected void init() {
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX = panelX + 8;
        listTop = panelY + 32;
        previewX = listX + listW + 14;

        addRenderableWidget(Button.builder(
                        Component.literal("Place").withStyle(ChatFormatting.GREEN),
                        button -> applySelected())
                .bounds(panelX + panelW - 78, panelY + panelH - 26, 70, 20)
                .build());
        addRenderableWidget(Button.builder(
                        CommonComponents.GUI_DONE,
                        button -> onClose())
                .bounds(panelX + 8, panelY + panelH - 26, 70, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        rowTops.clear();
        rowBottoms.clear();

        drawPanel(guiGraphics, panelX, panelY, panelW, panelH);
        guiGraphics.drawCenteredString(this.font, this.title,
                this.width / 2, panelY + 6, 0x404040);

        guiGraphics.drawString(this.font,
                Component.literal("Base: "
                        + this.baseState.getBlock().getName().getString())
                        .withStyle(ChatFormatting.DARK_GRAY),
                panelX + 8, panelY + 17, 0xFFFFFF, false);

        int last = Math.min(listScroll + VISIBLE_ROWS, suggestions.size());
        for (int i = listScroll; i < last; i++) {
            DesignData.Suggestion suggestion = suggestions.get(i);
            int y = listTop + (i - listScroll) * 20;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= y && mouseY < y + 18;
            boolean isSel = i == selected;
            drawCellBackground(guiGraphics, listX, y, listW, 18, isSel || hovered);

            guiGraphics.renderItem(stackFor('s', suggestion), listX + 2, y + 1);
            guiGraphics.drawString(this.font,
                    Component.literal((isSel ? "> " : "  ")
                            + suggestion.design().title()),
                    listX + 21, y + 5, isSel ? 0xFFFFFF : 0x404040, false);
            rowTops.add(y);
            rowBottoms.add(y + 18);
        }

        if (!suggestions.isEmpty()) {
            DesignData.Suggestion sel = suggestions.get(selected);
            List<String> pattern = sel.design().pattern();
            int rows = Math.min(pattern.size(), 8);
            int previewTop = listTop;

            for (int row = 0; row < rows; row++) {
                String line = pattern.get(row);
                for (int col = 0; col < line.length(); col++) {
                    drawCellBackground(guiGraphics,
                            previewX + col * CELL, previewTop + row * CELL,
                            16, 16, false);
                    ItemStack stack = stackFor(line.charAt(col), sel);
                    if (stack != null) {
                        guiGraphics.renderItem(stack,
                                previewX + col * CELL + 1,
                                previewTop + row * CELL + 1);
                    }
                }
            }

            int textY = previewTop + rows * CELL + 8;
            guiGraphics.drawString(this.font,
                    Component.literal(sel.design().title())
                            .withStyle(ChatFormatting.DARK_GREEN),
                    previewX, textY, 0xFFFFFF, false);
            guiGraphics.drawString(this.font,
                    Component.literal("Detail: "
                            + sel.secondary().block().getName().getString()),
                    previewX, textY + 11, 0x404040, false);
            if (sel.accent() != null) {
                guiGraphics.drawString(this.font,
                        Component.literal("Accent: "
                                + sel.accent().block().getName().getString()),
                        previewX, textY + 22, 0x404040, false);
            }
            guiGraphics.drawString(this.font,
                    Component.literal(sel.secondary().explanation())
                            .withStyle(ChatFormatting.DARK_GRAY),
                    previewX, textY + (sel.accent() != null ? 33 : 22) + 10,
                    0xFFFFFF, false);
        }
    }

    /**
     * Classic beveled panel, matching the container-screen look: a dark outer
     * edge, a light left/top ridge, and a darker right/bottom trough.
     */
    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_FILL);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_DARK_SHADOW);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_FILL);
        g.fill(x + 2, y + 2, x + w - 3, y + 3, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + 3, y + h - 3, 0xFFFFFFFF);
        g.fill(x + w - 3, y + 3, x + w - 2, y + h - 2, PANEL_LIGHT_EDGE);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 2, PANEL_LIGHT_EDGE);
    }

    /** Inventory-slot look: recessed dark border on a gray cell body. */
    private void drawCellBackground(GuiGraphics g, int x, int y, int w, int h,
                                    boolean highlighted) {
        g.fill(x, y, x + w, y + h, PANEL_DARK_SHADOW);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1,
                highlighted ? SLOT_HIGHLIGHT : SLOT_FILL);
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
                int clicked = listScroll + j;
                if (clicked == selected) {
                    sendPaletteToChat();
                } else {
                    selected = clicked;
                }
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
        if (keyCode == GLFW.GLFW_KEY_P) {
            applySelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendPaletteToChat();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (this.minecraft == null || this.minecraft.player == null
                || suggestions.isEmpty()) {
            return;
        }
        DesignData.Suggestion sel = suggestions.get(selected);
        this.minecraft.player.displayClientMessage(
                Component.literal("[Surface Muse] " + kind.label + ": "
                        + sel.design().title()
                        + " | base: " + baseState.getBlock().getName().getString()
                        + " | detail: " + sel.secondary().block().getName().getString()
                        + " (" + sel.secondary().explanation() + ")"
                        + (sel.accent() != null
                                ? " | accent: "
                                + sel.accent().block().getName().getString()
                                : "")),
                false);
    }

    private void applySelected() {
        if (this.minecraft == null || this.minecraft.level == null
                || suggestions.isEmpty()) {
            return;
        }
        DesignData.Suggestion sel = suggestions.get(selected);
        SmartPlacer.Plan plan = SmartPlacer.plan(
                this.minecraft.level, anchor, face, baseState.getBlock(),
                sel.design(), sel.secondary(), sel.accent());
        int result = SmartPlacer.apply(this.minecraft, plan);
        if (result > 0 && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.literal("[Surface Muse] applied " + sel.design().title()
                            + " - " + result + " blocks placed ("
                            + plan.surface().width() + "x" + plan.surface().height()
                            + " region)"),
                    true);
            onClose();
        }
    }
}
