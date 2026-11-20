package dev.surfacemuse.client;

import net.minecraft.core.Direction;
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

    public record Suggestion(Design design, SuggestionEngine.Scored secondary,
                               SuggestionEngine.Scored accent) {
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

    /**
     * Runs the ranking engine and pairs each design with its own detail and
     * accent pick, both drawn from diversified ranked lists so every design
     * previews a distinct palette.
     */
    public static List<Suggestion> suggest(BlockState state, Direction side,
                                               Surroundings.Context context) {
        List<Design> designs = designsFor(kindOf(side));
        List<Block> pool = PalettePool.resolve();
        if (pool.isEmpty()) {
            return List.of();
        }

        int count = designs.size();
        List<SuggestionEngine.Scored> details = SuggestionEngine.diversify(
                SuggestionEngine.scoreAll(state, pool, context, false), count);
        List<SuggestionEngine.Scored> accents = SuggestionEngine.diversify(
                SuggestionEngine.scoreAll(state, pool, context, true), count);
        if (details.isEmpty() || accents.isEmpty()) {
            return List.of();
        }

        List<Suggestion> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SuggestionEngine.Scored detail = details.get(Math.min(i, details.size() - 1));
            SuggestionEngine.Scored accent = accents.get(Math.min(i, accents.size() - 1));
            out.add(new Suggestion(designs.get(i), detail, accent));
        }
        return out;
    }

}
