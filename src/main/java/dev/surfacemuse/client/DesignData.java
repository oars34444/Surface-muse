package dev.surfacemuse.client;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class DesignData {

    public enum SurfaceKind {
        WALL("Wall"),
        FLOOR("Floor"),
        CEILING("Ceiling");

        public final String label;

        SurfaceKind(String label) {
            this.label = label;
        }
    }

    public record Design(String id, String title, String desc, List<String> pattern) {
    }

    public record Suggestion(Design design, Block secondary, Block accent) {
    }

    private DesignData() {
    }

    public static SurfaceKind kindOf(Direction side) {
        if (side == Direction.UP) {
            return SurfaceKind.FLOOR;
        }
        if (side == Direction.DOWN) {
            return SurfaceKind.CEILING;
        }
        return SurfaceKind.WALL;
    }

    public static List<Design> designsFor(SurfaceKind kind) {
        return switch (kind) {
            case WALL -> List.of(
                new Design("banding", "Banding",
                    "A horizontal two-tone band across the wall.",
                    List.of(
                        "ssssss",
                        "ssssss",
                        "pppppp",
                        "pppppp",
                        "aaaaaa",
                        "pppppp")),
                new Design("wainscot", "Wainscot",
                    "Lower panelling with a neat accent trim line.",
                    List.of(
                        "pppppp",
                        "pppppp",
                        "ssssss",
                        "ssssss",
                        "aaaaaa",
                        "aaaaaa")),
                new Design("timber", "Timber Frame",
                    "Vertical posts every few blocks, Half-Timber style.",
                    List.of(
                        "spppps",
                        "pppppp",
                        "spppps",
                        "pppppp",
                        "spppps",
                        "pppppp")),
                new Design("panel", "Paneled",
                    "Framed inset panels with a small accent core.",
                    List.of(
                        "ssssss",
                        "spppps",
                        "spapps",
                        "spapps",
                        "spppps",
                        "ssssss")),
                new Design("cornice", "Cornice",
                    "A moulded trim where the wall meets the ceiling.",
                    List.of(
                        "aaaaaa",
                        "ssssss",
                        "ssppps",
                        "pppppp",
                        "pppppp",
                        "pppppp")),
                new Design("column", "Pilaster",
                    "Flat vertical column strips to break up long walls.",
                    List.of(
                        "appsaa",
                        "ppsppp",
                        "ppsppp",
                        "ppsppp",
                        "ppsppp",
                        "appsaa")));
            case FLOOR -> List.of(
                new Design("rug", "Center Rug",
                    "A framed rug in the middle of the room.",
                    List.of(
                        "pppppp",
                        "pssssp",
                        "psppsp",
                        "psppsp",
                        "pssssp",
                        "pppppp")),
                new Design("checker", "Checker",
                    "Simple alternating two-block checkerboard.",
                    List.of(
                        "spspsp",
                        "pspsps",
                        "spspsp",
                        "pspsps",
                        "spspsp",
                        "pspsps")),
                new Design("grid", "Grid Inlay",
                    "Detail lines crossing like floor inlay tiling.",
                    List.of(
                        "aaapaa",
                        "pppppp",
                        "pppppp",
                        "aaapaa",
                        "pppppp",
                        "pppppp")),
                new Design("border", "Floor Border",
                    "A framed trim around the room's edge.",
                    List.of(
                        "ssssss",
                        "spppps",
                        "spppps",
                        "spppps",
                        "spppps",
                        "ssssss")),
                new Design("path", "Path Stones",
                    "Stepping-stone run across larger floors.",
                    List.of(
                        "pppppp",
                        "pspppp",
                        "sppspp",
                        "ppspps",
                        "pppspp",
                        "ppppsp")),
                new Design("hearth", "Hearth Tile",
                    "Accent square for a fireplace or seating corner.",
                    List.of(
                        "pppppp",
                        "pppapp",
                        "ppaaap",
                        "paaaap",
                        "ppaaap",
                        "pppppp")));
            case CEILING -> CEILING;
        };
    }

    private static final List<Design> CEILING = List.of(
        new Design("coffered", "Coffered",
            "Sunken grid of framed panels, classic for big halls.",
            List.of(
                "ssssss",
                "spppps",
                "spppps",
                "ssssss",
                "spppps",
                "spppps")),
        new Design("beams", "Beam Grid",
            "Exposed structural beams running across the ceiling.",
            List.of(
                "spppps",
                "pppppp",
                "pppppp",
                "spppps",
                "pppppp",
                "pppppp")),
        new Design("trim_edge", "Perimeter Trim",
            "Trim band all around the ceiling's edge.",
            List.of(
                "aaaaaa",
                "ssssss",
                "pppppp",
                "pppppp",
                "pppppp",
                "pppppp")),
        new Design("rosette", "Rosette",
            "Accent centerpiece for dining rooms and foyers.",
            List.of(
                "pppppp",
                "ppsspp",
                "ssppss",
                "ssppss",
                "ppsspp",
                "pppppp")),
        new Design("lantern_field", "Lantern Field",
            "Accent 'lantern' tiles scattered for light placement spots.",
            List.of(
                "papppp",
                "pppppa",
                "pppppp",
                "appppa",
                "pppppp",
                "ppappp")));

    public static List<Suggestion> suggest(BlockState state, Direction side) {
        List<Suggestion> out = new ArrayList<>();
        for (Design design : designsFor(kindOf(side))) {
            Block secondary = firstExisting(secondaryCandidates(state), state);
            Block accent = firstExisting(accentCandidates(state), state);
            if (secondary == null) {
                continue;
            }
            out.add(new Suggestion(design, secondary, accent));
        }
        return out;
    }

    private static Block firstExisting(List<String> ids, BlockState deny) {
        for (String id : ids) {
            Block block = BuiltInRegistries.BLOCK
                    .get(ResourceLocation.fromNamespaceAndPath("minecraft", id));
            if (block != null && (deny == null || block != deny.getBlock())) {
                return block;
            }
        }
        return null;
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private static List<String> secondaryCandidates(BlockState state) {
        String blockName = name(state.getBlock());
        if (blockName.endsWith("_planks")) {
            String wood = blockName.substring(0, blockName.length() - "_planks".length());
            return List.of(wood + "_log", "stripped_" + wood + "_log", wood + "_planks");
        }
        if (blockName.contains("brick")) {
            return List.of("chiseled_stone_bricks", "deepslate_tiles", "tuff_bricks",
                    "polished_blackstone_bricks", "mossy_stone_bricks", "cracked_stone_bricks");
        }
        if (blockName.contains("deepslate") || blockName.contains("tuff")
                || blockName.contains("blackstone")) {
            return List.of("polished_deepslate", "deepslate_bricks", "polished_tuff",
                    "polished_blackstone", "deepslate_tiles");
        }
        if (blockName.contains("sandstone") || blockName.contains("_terracotta")
                || blockName.contains("quartz")) {
            return List.of("chiseled_sandstone", "chiseled_quartz_block", "smooth_sandstone",
                    "smooth_quartz", "smooth_stone");
        }
        return List.of("stone_bricks", "chiseled_stone_bricks", "smooth_stone",
                "andesite", "tuff", "calcite");
    }

    private static List<String> accentCandidates(BlockState state) {
        String blockName = name(state.getBlock());
        if (blockName.endsWith("_planks")) {
            String wood = blockName.substring(0, blockName.length() - "_planks".length());
            return List.of(wood + "_fence", wood + "_trapdoor", "stripped_" + wood + "_wood");
        }
        return List.of("iron_bars", "chain", "lantern", "sea_lantern",
                "polished_andesite", "smooth_stone");
    }
}
