package dev.surfacemuse.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The palette selection brain of Surface Muse.
 *
 * A suggestion is produced by a multi-signal ranking pipeline over a curated
 * pool of vanilla building blocks:
 *
 *  1. Token similarity  - block ids are split into words ("stone", "bricks",
 *     "chiseled", "oak"...). Jaccard overlap drives family recognition:
 *     "oak_planks" knows about "oak_log" before any hand-written rule.
 *  2. Color proximity   - each block's representative vanilla-approximate
 *     color (curated table) is compared perceptually in CIE-Lab (deltaE),
 *     so sand-adjacent bases stay warm and deepslate bases stay cold.
 *  3. Material class     - ids are classified (planks, bricks, polished,
 *     cut_copper, wool, glass, metal...). Same-class pairs and known
 *     complementary classes score higher; chaotic pairs score lower.
 *  4. Diversity forcing  - selections are re-ranked by "marginal relevance":
 *     each subsequent pick must add tokens and a new color band that earlier
 *     picks have not covered, so the suggestions stay visually distinct
 *     instead of six shades of the same brick.
 */
public final class SuggestionEngine {

    private static final int WOOD = 0x9C7F4E;
    private static final int NEUTRAL_STONE = 0x8F8F8F;
    private static final int LIGHT = 0xDBDBD8;

    public record Scored(Block block, List<String> tokens, String materialClass,
                         double tokenScore, double colorScore, double classScore,
                         double contrast, double total) {

        public String explanation() {
            String why;
            double guide = total - (tokenScore + colorScore + classScore + contrast);
            if (guide > 45) {
                why = "builder-guide proven pairing";
            } else if (tokenScore > 15) {
                why = "same block family";
            } else if (classScore > 12) {
                why = "matching texture style";
            } else if (contrast > 30) {
                why = "deliberate contrast";
            } else {
                why = "matching color band";
            }
            return why + String.format(Locale.ROOT, " - %.0f%% fit",
                    Math.min(100, total));
        }
    }

    private SuggestionEngine() {
    }

    public static List<String> tokens(String blockId) {
        List<String> out = new ArrayList<>();
        for (String part : blockId.split("[_ ]")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    public static String materialClass(List<String> toks) {
        String id = String.join("_", toks);
        if (id.contains("chiseled")) {
            return "chiseled";
        }
        if (id.contains("polished")) {
            return "polished";
        }
        if (id.contains("smooth")) {
            return "smooth";
        }
        if (id.contains("cut")) {
            return "cut";
        }
        if (id.contains("brick")) {
            return "bricks";
        }
        if (id.contains("tiles")) {
            return "tiles";
        }
        if (id.contains("planks")) {
            return "planks";
        }
        if (id.contains("log") || id.contains("wood")) {
            return "log";
        }
        if (id.contains("copper")) {
            return "copper";
        }
        if (id.contains("deepslate")) {
            return "deepslate";
        }
        if (id.contains("tuff")) {
            return "tuff";
        }
        if (id.contains("blackstone")) {
            return "blackstone";
        }
        if (id.contains("sandstone")) {
            return "sandstone";
        }
        if (id.contains("quartz")) {
            return "quartz";
        }
        if (id.contains("terracotta")) {
            return "terracotta";
        }
        if (id.contains("concrete")) {
            return "concrete";
        }
        if (id.contains("wool") || id.contains("carpet")) {
            return "wool";
        }
        if (id.contains("glass")) {
            return "glass";
        }
        if (id.contains("lantern") || id.contains("lamp") || id.contains("shroomlight")
                || id.contains("torch") || id.contains("campfire")
                || id.contains("glowstone")) {
            return "light";
        }
        if (id.contains("purpur")) {
            return "purpur";
        }
        if (id.contains("prismarine")) {
            return "prismarine";
        }
        if (id.contains("iron") || id.contains("chain") || id.contains("bars")) {
            return "metal";
        }
        return "stone";
    }

    public static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long shared = a.stream().filter(b::contains).count();
        int union = a.size() + b.size() - (int) shared;
        return union == 0 ? 0 : (double) shared / union;
    }

    public static double classAffinity(String baseClass, String candidateClass) {
        if (baseClass.equals(candidateClass)) {
            return 34.0;
        }
        if (complements(baseClass, candidateClass) || complements(candidateClass, baseClass)) {
            return 26.0;
        }
        if (baseClass.equals("stone") || candidateClass.equals("stone")) {
            return 10.0;
        }
        return 4.0;
    }

    private static boolean complements(String a, String b) {
        return switch (a) {
            case "bricks" -> Set.of("chiseled", "smooth", "polished", "deepslate",
                    "tuff", "blackstone", "sandstone").contains(b);
            case "planks" -> Set.of("log").contains(b);
            case "log" -> Set.of("planks", "chiseled").contains(b);
            case "deepslate", "blackstone", "tuff" -> Set.of("polished", "bricks",
                    "smooth", "tiles").contains(b);
            case "sandstone", "quartz", "terracotta" -> Set.of("smooth", "chiseled",
                    "bricks").contains(b);
            case "polished", "smooth", "chiseled" -> Set.of("bricks", "tiles").contains(b);
            case "wool", "concrete" -> Set.of("wool", "concrete", "glass").contains(b);
            default -> false;
        };
    }

    private record Features(List<String> tokens, String materialClass, double[] lab) {
    }

    private static Features features(Block block) {
        String id = name(block);
        List<String> toks = tokens(id);
        return new Features(toks, materialClass(toks), labFor(id));
    }

    /**
     * Scores every pool block against the base block. In detail mode the
     * engine hunts for like-for-like texture mates; in accent mode it hunts
     * for deliberate contrast (metals, lights, glass, wool).
     */
    public static List<Scored> scoreAll(BlockState base, List<Block> pool,
                                         Surroundings.Context context,
                                         boolean accentRole) {
        Block baseBlock = base.getBlock();
        String baseId = name(baseBlock);
        List<String> baseTokens = tokens(baseId);
        String baseClass = materialClass(baseTokens);
        double[] baseLab = labFor(baseId);

        List<Scored> scored = new ArrayList<>();
        for (Block candidate : pool) {
            if (candidate == baseBlock) {
                continue;
            }
            Features f = features(candidate);

            double tokenScore;
            double colorScore = 0;
            double classScore;
            double contrast = 0;

            String candidateId = name(candidate);
            double guideScore = StyleKnowledge.get()
                    .provenBonus(baseId, candidateId, accentRole)
                    + StyleKnowledge.get().textureFit(baseId, candidateId, accentRole)
                    + contextScore(candidateId, context, accentRole);

            if (accentRole) {
                tokenScore = 14.0 * jaccard(baseTokens, f.tokens());
                classScore = ACCENT_CLASSES.contains(f.materialClass()) ? 28.0 : 0.0;
                contrast = 46.0 * accentContrast(baseLab, f.lab());
            } else {
                tokenScore = 42.0 * jaccard(baseTokens, f.tokens());
                classScore = classAffinity(baseClass, f.materialClass());
                colorScore = 40.0 * colorCloseness(baseLab, f.lab());
            }

            scored.add(new Scored(candidate, f.tokens(), f.materialClass(),
                    tokenScore, colorScore, classScore, contrast,
                    tokenScore + colorScore + classScore + contrast + guideScore));
        }
        scored.sort((a, b) -> Double.compare(b.total(), a.total()));
        return scored;
    }

    /**
     * Context harmony: candidates that already appear nearby (existing theme
     * members), that proven-pair with the room's standing palette, or whose
     * warm/cool character agrees with it, float upward. Capped so context can
     * tilt the ranking, never swamp it.
     */
    public static double contextScore(String candidateId, Surroundings.Context context,
                                       boolean accentRole) {
        if (context == null || candidateId == null) {
            return 0;
        }
        double score = 0;
        for (var entry : context.top().entrySet()) {
            String ctxId = entry.getKey();
            double weight = entry.getValue();
            if (candidateId.equals(ctxId)) {
                score += 26.0 * weight;
            }
            score += 0.5 * StyleKnowledge.get()
                    .provenBonus(ctxId, candidateId, accentRole) * weight;
        }
        if (!accentRole) {
            String candTemp = StyleKnowledge.get().temperatureOf(candidateId);
            if (candTemp != null && context.temperature() != null) {
                if (candTemp.equals(context.temperature())) {
                    score += 6.0;
                } else if ("neutral".equals(candTemp)
                        || "neutral".equals(context.temperature())) {
                    score += 2.0;
                } else {
                    score -= 6.0;
                }
            }
        }
        return Math.min(Math.max(score, -12.0), 45.0);
    }

    private static final Set<String> ACCENT_CLASSES =
            Set.of("metal", "light", "glass", "wool", "copper");

    /**
     * Marginal-relevance re-ranking: walk the ranked list and keep a pick only
     * if it adds a token or a finish style that earlier picks have not covered
     * yet. This is what keeps the suggestion spread wide instead of
     * near-duplicates.
     */
    public static List<Scored> diversify(List<Scored> ranked, int count) {
        List<Scored> out = new ArrayList<>();
        List<String> covered = new ArrayList<>();
        for (Scored candidate : ranked) {
            if (out.size() >= count) {
                break;
            }
            String id = String.join("_", candidate.tokens());
            boolean addsNovelty = id.contains("smooth") || id.contains("polished")
                    || id.contains("chiseled") || id.contains("cut");
            for (String token : candidate.tokens()) {
                if (!covered.contains(token)) {
                    addsNovelty = true;
                    break;
                }
            }
            if (out.isEmpty() || addsNovelty || candidate.total() > 75) {
                out.add(candidate);
                for (String token : candidate.tokens()) {
                    if (!covered.contains(token)) {
                        covered.add(token);
                    }
                }
            }
        }
        for (Scored candidate : ranked) {
            if (out.size() >= count) {
                break;
            }
            if (!out.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    public static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /**
     * Representative vanilla-approximate color per block id. Coarse by design:
     * the ranking needs a color band, not pixel fidelity.
     */
    public static int colorOf(String id) {
        if (id.contains("glowstone") || id.contains("shroomlight") || id.contains("sea_lantern")
                || id.contains("jack") || id.contains("lamp")) {
            return 0xF9DFB4;
        }
        if (id.contains("deepslate") && id.contains("brick")) {
            return 0x2C2F31;
        }
        if (id.contains("deepslate")) {
            return 0x37393B;
        }
        if (id.contains("blackstone")) {
            return 0x16181C;
        }
        if (id.contains("tuff")) {
            return 0x64685B;
        }
        if (id.contains("nether") && !id.contains("sprouts")) {
            return 0x442227;
        }
        if (id.contains("quartz")) {
            return 0xECE9E2;
        }
        if (id.contains("sandstone") || id.contains("calcite") || id.contains("dripstone")) {
            return 0xDBD3A0;
        }
        if (id.contains("iron") || id.contains("chain") || id.contains("bars")
                || id.contains("lantern")) {
            return 0xD8D8D8;
        }
        if (id.contains("oxidized")) {
            return 0x60A48C;
        }
        if (id.contains("exposed")) {
            return 0x94AFA8;
        }
        if (id.contains("copper")) {
            return 0xC16E4E;
        }
        if (id.contains("gold")) {
            return 0xF9EC4C;
        }
        if (id.contains("diamond") && id.contains("_block")) {
            return 0x67F2E1;
        }
        if (id.contains("emerald") && id.contains("_block")) {
            return 0x41F384;
        }
        if (id.contains("lapis") && id.contains("_block")) {
            return 0x205EA8;
        }
        if (id.contains("redstone") && id.contains("_block")) {
            return 0xB02121;
        }
        if (id.contains("prismarine")) {
            return 0x6C8C8D;
        }
        if (id.contains("purpur")) {
            return 0xA678B2;
        }
        int woodColor = woodColor(id);
        if (woodColor != -1) {
            return woodColor;
        }
        if (id.contains("crimson")) {
            return 0x6A2943;
        }
        if (id.contains("warped")) {
            return 0x2B6D63;
        }
        if (id.contains("planks") || id.contains("log") || id.contains("wood")) {
            return WOOD;
        }
        if (id.contains("brick")) {
            return 0x96625A;
        }
        if (id.contains("granite")) {
            return 0x9A6A5A;
        }
        if (id.contains("diorite")) {
            return 0xBFBFC2;
        }
        if (id.contains("andesite")) {
            return 0x8B8B8B;
        }
        if (id.contains("basalt") || id.contains("cobbled_deepslate")) {
            return 0x4C4C51;
        }
        if (id.contains("dirt") || id.contains("mud")) {
            return 0x8A6244;
        }
        if (id.contains("snow") || id.contains("ice")) {
            return 0xDCEFF7;
        }
        if (id.contains("obsidian")) {
            return 0x14121D;
        }
        if (id.contains("amethyst")) {
            return 0x8852C4;
        }
        if (id.contains("glass")) {
            return 0xD9EFEF;
        }
        if (id.contains("polished")) {
            return 0xA8A8A8;
        }
        if (id.contains("smooth")) {
            return LIGHT;
        }
        if (id.contains("chiseled") || id.contains("carved")) {
            return NEUTRAL_STONE;
        }
        if (id.contains("wool") || id.contains("carpet")) {
            return 0xA0A7A7;
        }
        if (id.contains("concrete")) {
            return 0x9AA4A4;
        }
        if (id.contains("terracotta")) {
            return 0xA3593D;
        }
        if (id.contains("lava") || id.contains("magma")) {
            return 0xD45A12;
        }
        return NEUTRAL_STONE;
    }

    private static int woodColor(String id) {
        if (id.contains("spruce")) {
            return 0x58452B;
        }
        if (id.contains("dark_oak")) {
            return 0x4A3218;
        }
        if (id.contains("birch")) {
            return 0xD7C185;
        }
        if (id.contains("cherry")) {
            return 0xE7B5AC;
        }
        if (id.contains("mangrove")) {
            return 0x773934;
        }
        if (id.contains("bamboo")) {
            return 0xC8AF58;
        }
        if (id.contains("jungle")) {
            return 0x9A7046;
        }
        if (id.contains("acacia")) {
            return 0xBA6337;
        }
        if (id.contains("pale")) {
            return 0xE4D9CC;
        }
        if (id.contains("poplar")) {
            return 0xB9956B;
        }
        if (id.contains("oak")) {
            return WOOD;
        }
        return -1;
    }

    private static final Map<String, double[]> LAB_CACHE = new HashMap<>();

    private static double[] labFor(String id) {
        return LAB_CACHE.computeIfAbsent(id, key -> mapColorToLab(colorOf(key)));
    }

    private static double[] mapColorToLab(int packed) {
        int r = (packed >> 16) & 0xFF;
        int g = (packed >> 8) & 0xFF;
        int b = packed & 0xFF;
        double lr = srgbToLinear(r / 255.0);
        double lg = srgbToLinear(g / 255.0);
        double lb = srgbToLinear(b / 255.0);

        double x = 0.4124 * lr + 0.3576 * lg + 0.1805 * lb;
        double y = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
        double z = 0.0193 * lr + 0.1192 * lg + 0.9505 * lb;

        double fx = pivot(x / 0.95047);
        double fy = pivot(y);
        double fz = pivot(z / 1.08883);

        return new double[] {
                116.0 * fy - 16.0,
                500.0 * (fx - fy),
                200.0 * (fy - fz)
        };
    }

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double pivot(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (903.3 * t + 16.0) / 116.0;
    }

    public static double deltaE(double[] labA, double[] labB) {
        double dl = labA[0] - labB[0];
        double da = labA[1] - labB[1];
        double db = labA[2] - labB[2];
        return Math.sqrt(dl * dl + 0.9 * da * da + 0.9 * db * db);
    }

    /**
     * Closeness for detail blocks: near-identical color scores best; a soft
     * plateau avoids letting exact matches dominate every other signal.
     */
    public static double colorCloseness(double[] labA, double[] labB) {
        if (labA == null || labB == null) {
            return 0.5;
        }
        double d = deltaE(labA, labB);
        if (d < 6) {
            return 1.0;
        }
        if (d > 48) {
            return 0.0;
        }
        return 1.0 - (d - 6) / 42.0;
    }

    /**
     * Contrast scoring for accents: accents should pop, not vanish. Mid-range
     * perceptual distance peaks; huge shifts are dampened.
     */
    public static double accentContrast(double[] labA, double[] labB) {
        if (labA == null || labB == null) {
            return 0.3;
        }
        double d = deltaE(labA, labB);
        if (d < 10) {
            return d / 40.0;
        }
        if (d > 44) {
            return 0.35;
        }
        return 0.72 - Math.abs(d - 24) / 20.0 * 0.28;
    }
}
