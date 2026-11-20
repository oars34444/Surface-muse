package dev.surfacemuse.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the build context around the surface the player is looking at so
 * suggestions match what is already there.
 *
 * A weighted neighborhood scan (flat 17x13x17 box around the clicked block,
 * closer blocks counting more) tallies every decorative block that is not the
 * base block and not terrain noise (dirt, grass, stone, water...). The top
 * survivors become the room's existing palette, and the temperature that
 * dominates them becomes the room's temperature. The engine then boosts
 * candidates that are already part of that palette, that proven pair with its
 * members, and whose warm/cool character agrees with it.
 */
public final class Surroundings {

    public record Context(Map<String, Double> top, String temperature) {
    }

    private static final int RADIUS_H = 8;
    private static final int RADIUS_V = 6;

    private static final String[] TERRAIN_NOISE = {
            "dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt",
            "mud", "sand", "red_sand", "gravel", "clay", "water", "lava",
            "netherrack", "bedrock", "stone", "deepslate", "snow",
            "snow_block", "ice", "moss_block", "cobweb", "short_grass",
            "tall_grass", "seagrass"
    };

    private Surroundings() {
    }

    public static Context scan(World world, BlockPos anchor, Direction face) {
        String baseId = SuggestionEngine.name(world.getBlockState(anchor).getBlock());
        Map<String, Double> weights = new LinkedHashMap<>();

        for (int dx = -RADIUS_H; dx <= RADIUS_H; dx++) {
            for (int dz = -RADIUS_H; dz <= RADIUS_H; dz++) {
                for (int dy = -RADIUS_V; dy <= RADIUS_V; dy++) {
                    BlockPos pos = new BlockPos(
                            anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    var block = world.getBlockState(pos).getBlock();
                    if (block.getDefaultState().isAir()) {
                        continue;
                    }
                    String id = SuggestionEngine.name(block);
                    if (id.equals(baseId) || isNoise(id)) {
                        continue;
                    }
                    double dist = Math.sqrt(dx * dx + 1.5 * dy * dy + dz * dz);
                    weights.merge(id, 1.0 / (1.0 + dist * 0.35), Double::sum);
                }
            }
        }

        Map<String, Double> top = new LinkedHashMap<>();
        weights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(4)
                .forEach(e -> top.put(e.getKey(), e.getValue()));

        double max = top.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) {
            max = 1;
        }
        top.replaceAll((id, w) -> w / max);

        return new Context(top, dominantTemperature(top));
    }

    private static boolean isNoise(String id) {
        for (String noise : TERRAIN_NOISE) {
            if (id.equals(noise)) {
                return true;
            }
        }
        return false;
    }

    private static String dominantTemperature(Map<String, Double> top) {
        Map<String, Double> votes = new LinkedHashMap<>();
        for (var entry : top.entrySet()) {
            String temp = StyleKnowledge.get().temperatureOf(entry.getKey());
            if (temp != null) {
                votes.merge(temp, entry.getValue(), Double::sum);
            }
        }
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static String summary(Context context) {
        if (context == null || context.top().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String id : context.top().keySet()) {
            if (i >= 2) {
                break;
            }
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pretty(id));
            i++;
        }
        return sb.toString();
    }

    private static String pretty(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
