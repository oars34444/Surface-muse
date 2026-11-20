package dev.surfacemuse.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a selected design onto the surface with geometry reasoning:
 *
 *  - Region discovery: from the clicked block it scans along the surface
 *    tangent (and vertically for walls down to the base) to find how far the
 *    surface actually extends, capping the region at 12x12.
 *  - Pattern anchoring: patterns are anchored to the surface's bottom-left of
 *    the discovered region so horizontal bands land as drawn (wainscot sits
 *    at the base) and tile modulo their 6-wide period, so repeated
 *    applications tile seamlessly instead of restarting at the click point.
 *  - Like-for-like safety: a cell is only painted when its current block is
 *    structural material of the same family as the base block. Air, glass,
 *    doors, chests, trapdoors, signs and other functional or fragile blocks
 *    are never touched, so windows and doorways simply appear as holes in
 *    the applied design rather than being destroyed.
 *  - Only the detail (s) and accent (a) glyph cells are written; base cells
 *    (p) are left exactly as the builder made them.
 *  - Survival fairness: the full material requirement is computed first and
 *    checked against the inventory; if anything is missing, nothing is
 *    placed and the shortage is reported. In survival, materials are
 *    consumed from the authoritative server-side inventory. Creative is
 *    free.
 *  - Singleplayer places through the integrated server's level so the world
 *    stays in sync; multiplayer creative attempts setblock commands and
 *    quietly no-op without permission.
 */
public final class SmartPlacer {

    public record Plan(BlockPos origin, Surface surface,
                       List<PlanEntry> entries, Map<Block, Integer> required) {

        public int size() {
            return entries.size();
        }
    }

    public record PlanEntry(BlockPos pos, BlockState state) {
    }

    public record Surface(Direction tangent, int width, int height, BlockPos bottomLeft) {
    }

    private SmartPlacer() {
    }

    private static final int MAX_SPAN = 12;

    private static final String[] NEVER_REPLACE_HINTS = {
            "glass", "door", "chest", "barrel", "trapdoor", "trap_door",
            "torch", "ladder", "sign", "button", "lever", "rail", "carpet",
            "fence", "wall", "stairs", "slab", "pane", "bars", "chain",
            "lantern", "soul_lantern", "hanging", "candle", "bed", "banner",
            "flower", "sapling", "vine", "mushroom", "snow", "water",
            "lava", "campfire", "bookshelf", "respawn", "beacon", "conduit",
            "path", "farmland", "piston", "observer", "hopper", "dispenser",
            "dropper", "spawner"
    };

    public static Surface discoverSurface(Level world, BlockPos anchor, Direction face) {
        Direction tangent = face == Direction.UP || face == Direction.DOWN
                ? Direction.EAST
                : (face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                anchor.getX(), anchor.getY(), anchor.getZ());
        while (surfaceCellValid(world, cursor)) {
            cursor.move(Direction.DOWN);
        }
        cursor.move(Direction.UP);
        BlockPos bottom = cursor.immutable();

        int height = 0;
        cursor.set(bottom.getX(), bottom.getY(), bottom.getZ());
        while (height < MAX_SPAN && surfaceCellValid(world, cursor)) {
            cursor.move(Direction.UP);
            height++;
        }

        int left = Math.min(scan(world, anchor, tangent.getOpposite(), 5), 5);
        int right = Math.min(scan(world, anchor, tangent, MAX_SPAN - 1), MAX_SPAN - 1);

        return new Surface(tangent, left + right + 1, height,
                bottom.relative(tangent, -left));
    }

    private static int scan(Level world, BlockPos anchor, Direction dir, int cap) {
        int n = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                anchor.getX(), anchor.getY(), anchor.getZ());
        while (n < cap) {
            cursor.move(dir);
            if (!surfaceCellValid(world, cursor)) {
                break;
            }
            n++;
        }
        return n;
    }

    private static boolean surfaceCellValid(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        for (String hint : NEVER_REPLACE_HINTS) {
            if (id.contains(hint)) {
                return false;
            }
        }
        return true;
    }

    private static boolean familyMatches(Level world, BlockPos pos, String baseId,
                                         String baseClass) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.equals(baseId)) {
            return true;
        }
        String cellClass = SuggestionEngine.materialClass(SuggestionEngine.tokens(id));
        return cellClass.equals(baseClass);
    }

    public static Plan plan(Level world, BlockPos anchor, Direction face, Block baseBlock,
                            DesignData.Design design,
                            SuggestionEngine.Scored detail,
                            SuggestionEngine.Scored accent) {
        Surface surface = discoverSurface(world, anchor, face);
        String baseId = BuiltInRegistries.BLOCK.getKey(baseBlock).getPath();
        String baseClass = SuggestionEngine.materialClass(SuggestionEngine.tokens(baseId));

        List<PlanEntry> entries = new ArrayList<>();
        Map<Block, Integer> required = new HashMap<>();

        List<String> pattern = design.pattern();
        int rows = Math.min(surface.height(), MAX_SPAN);
        int cols = Math.min(surface.width(), MAX_SPAN);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                BlockPos pos = surface.bottomLeft()
                        .relative(surface.tangent(), j)
                        .relative(Direction.UP, i);
                if (!surfaceCellValid(world, pos)
                        || !familyMatches(world, pos, baseId, baseClass)) {
                    continue;
                }

                int patternRow = (rows - 1 - i) % pattern.size();
                String line = pattern.get(patternRow);
                char glyph = line.charAt(j % line.length());

                Block target = switch (glyph) {
                    case 's' -> detail.block();
                    case 'a' -> accent.block();
                    case 'p', '.' -> baseBlock;
                    default -> baseBlock;
                };
                if ((glyph != 's' && glyph != 'a') || target == baseBlock) {
                    continue;
                }

                entries.add(new PlanEntry(pos, target.defaultBlockState()));
                required.merge(target, 1, Integer::sum);
            }
        }

        return new Plan(anchor, surface, entries, required);
    }

    public static boolean checkInventory(ServerPlayer player, Plan plan,
                                         List<Component> shortages) {
        for (Map.Entry<Block, Integer> need : plan.required().entrySet()) {
            Item item = need.getKey().asItem();
            int have = player.getInventory().countItem(item);
            if (have < need.getValue()) {
                shortages.add(Component.literal(
                        need.getKey().getName().getString()
                                + ": need " + (need.getValue() - have) + " more"));
            }
        }
        return shortages.isEmpty();
    }

    /**
     * Returns the number of blocks placed, -1 for "blocked, reason
     * reported", or -2 for "unsupported mode".
     */
    public static int apply(net.minecraft.client.Minecraft client, Plan plan) {
        if (plan.size() == 0) {
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("[Surface Muse] nothing to place here - "
                                + "the surface scan found no matching cells."), false);
            }
            return 0;
        }

        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null
                && client.player != null) {
            var server = client.getSingleplayerServer();
            ServerLevel level = server.getLevel(client.player.level().dimension());
            if (level == null) {
                return -2;
            }
            ServerPlayer serverPlayer =
                    server.getPlayerList().getPlayer(client.player.getUUID());
            if (serverPlayer == null) {
                return -2;
            }

            if (!serverPlayer.isCreative()) {
                List<Component> shortages = new ArrayList<>();
                if (!checkInventory(serverPlayer, plan, shortages)) {
                    serverPlayer.displayClientMessage(
                            Component.literal("[Surface Muse] missing materials:"), false);
                    for (Component shortage : shortages) {
                        serverPlayer.displayClientMessage(
                                Component.literal("  - " + shortage.getString()), false);
                    }
                    return -1;
                }
            }

            int placed = 0;
            for (PlanEntry entry : plan.entries()) {
                level.setBlock(entry.pos(), entry.state(), 3);
                placed++;
            }

            if (!serverPlayer.isCreative()) {
                for (Map.Entry<Block, Integer> need : plan.required().entrySet()) {
                    removeItems(serverPlayer, need.getKey().asItem(), need.getValue());
                }
            }
            return placed;
        }

        if (client.player != null && client.player.isCreative()
                && client.getConnection() != null) {
            for (PlanEntry entry : plan.entries()) {
                String id = BuiltInRegistries.BLOCK.getKey(entry.state().getBlock())
                        .toString();
                client.getConnection().sendCommand(
                        "setblock " + entry.pos().getX() + " " + entry.pos().getY()
                                + " " + entry.pos().getZ() + " " + id);
            }
            return plan.size();
        }

        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("[Surface Muse] smart placement needs "
                            + "singleplayer (or creative + setblock permission "
                            + "on servers)."), false);
        }
        return -2;
    }

    private static void removeItems(ServerPlayer player, Item item, int count) {
        int left = count;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }
}
