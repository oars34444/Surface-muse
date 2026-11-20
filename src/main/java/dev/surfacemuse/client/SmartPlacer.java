package dev.surfacemuse.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

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

    public static Surface discoverSurface(World world, BlockPos anchor, Direction face) {
        Direction tangent = face == Direction.UP || face == Direction.DOWN
                ? Direction.EAST
                : (face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST);

        BlockPos.MutableBlockPos cursor = new BlockPos.Mutable(
                anchor.getX(), anchor.getY(), anchor.getZ());
        while (surfaceCellValid(world, cursor)) {
            cursor.move(Direction.DOWN);
        }
        cursor.move(Direction.UP);
        BlockPos bottom = cursor.toImmutable();

        int height = 0;
        cursor.set(bottom.getX(), bottom.getY(), bottom.getZ());
        while (height < MAX_SPAN && surfaceCellValid(world, cursor)) {
            cursor.move(Direction.UP);
            height++;
        }

        int left = Math.min(scan(world, anchor, tangent.getOpposite(), 5), 5);
        int right = Math.min(scan(world, anchor, tangent, MAX_SPAN - 1), MAX_SPAN - 1);

        return new Surface(tangent, left + right + 1, height,
                bottom.offset(tangent, -left));
    }

    private static int scan(World world, BlockPos anchor, Direction dir, int cap) {
        int n = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.Mutable(
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

    private static boolean surfaceCellValid(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        for (String hint : NEVER_REPLACE_HINTS) {
            if (id.contains(hint)) {
                return false;
            }
        }
        return true;
    }

    private static boolean familyMatches(World world, BlockPos pos, String baseId,
                                         String baseClass) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        if (id.equals(baseId)) {
            return true;
        }
        String cellClass = SuggestionEngine.materialClass(SuggestionEngine.tokens(id));
        return cellClass.equals(baseClass);
    }

    public static Plan plan(World world, BlockPos anchor, Direction face, Block baseBlock,
                            DesignData.Design design,
                            SuggestionEngine.Scored detail,
                            SuggestionEngine.Scored accent) {
        Surface surface = discoverSurface(world, anchor, face);
        String baseId = Registries.BLOCK.getId(baseBlock).getPath();
        String baseClass = SuggestionEngine.materialClass(SuggestionEngine.tokens(baseId));

        List<PlanEntry> entries = new ArrayList<>();
        Map<Block, Integer> required = new HashMap<>();

        List<String> pattern = design.pattern();
        int rows = Math.min(surface.height(), MAX_SPAN);
        int cols = Math.min(surface.width(), MAX_SPAN);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                BlockPos pos = surface.bottomLeft()
                        .offset(surface.tangent(), j)
                        .offset(Direction.UP, i);
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

                entries.add(new PlanEntry(pos, target.getDefaultState()));
                required.merge(target, 1, Integer::sum);
            }
        }

        return new Plan(anchor, surface, entries, required);
    }

    public static boolean checkInventory(ServerPlayerEntity player, Plan plan,
                                         List<Text> shortages) {
        for (Map.Entry<Block, Integer> need : plan.required().entrySet()) {
            Item item = need.getId().asItem();
            int have = 0;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (!inventory.getStack(slot).isEmpty()
                        && inventory.getStack(slot).getItem() == item) {
                    have += inventory.getStack(slot).getCount();
                }
            }
            if (have < need.getValue()) {
                shortages.add(Text.literal(
                        need.getId().getName().getString()
                                + ": need " + (need.getValue() - have) + " more"));
            }
        }
        return shortages.isEmpty();
    }

    /**
     * Returns the number of blocks placed, -1 for "blocked, reason
     * reported", or -2 for "unsupported mode".
     */
    public static int apply(MinecraftClient client, Plan plan) {
        if (plan.size() == 0) {
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("[Surface Muse] nothing to place here - "
                                + "the surface scan found no matching cells."), false);
            }
            return 0;
        }

        if (client.getServer() != null && client.getServer() != null
                && client.player != null) {
            var server = client.getServer();
            ServerWorld level = server.getWorld(client.player.getWorld().getRegistryKey());
            if (level == null) {
                return -2;
            }
            ServerPlayerEntity serverPlayer =
                    server.getPlayerManager().getPlayer(client.player.getUUID());
            if (serverPlayer == null) {
                return -2;
            }

            if (!serverPlayer.isCreative()) {
                List<Text> shortages = new ArrayList<>();
                if (!checkInventory(serverPlayer, plan, shortages)) {
                    serverPlayer.sendMessage(
                            Text.literal("[Surface Muse] missing materials:"), false);
                    for (Text shortage : shortages) {
                        serverPlayer.sendMessage(
                                Text.literal("  - " + shortage.getString()), false);
                    }
                    return -1;
                }
            }

            int placed = 0;
            for (PlanEntry entry : plan.entries()) {
                level.setBlockState(entry.pos(), entry.state(), 3);
                placed++;
            }

            if (!serverPlayer.isCreative()) {
                for (Map.Entry<Block, Integer> need : plan.required().entrySet()) {
                    removeItems(serverPlayer, need.getId().asItem(), need.getValue());
                }
            }
            return placed;
        }

        if (client.player != null && client.player.isCreative()
                && client.getConnection() != null) {
            for (PlanEntry entry : plan.entries()) {
                String id = Registries.BLOCK.getId(entry.state().getBlock())
                        .toString();
                client.getNetworkHandler().sendCommand(
                        "setblock " + entry.pos().getX() + " " + entry.pos().getY()
                                + " " + entry.pos().getZ() + " " + id);
            }
            return plan.size();
        }

        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("[Surface Muse] smart placement needs "
                            + "singleplayer (or creative + setblock permission "
                            + "on servers)."), false);
        }
        return -2;
    }

    private static void removeItems(ServerPlayerEntity player, Item item, int count) {
        int left = count;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
    }
}
