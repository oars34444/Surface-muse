package dev.surfacemuse.client;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real-world builder knowledge, shipped as data and loaded once.
 *
 * The knowledge file distills published builder guides and community-palette
 * research into machine-usable signals:
 *
 *  - proven pairings: base block -> (secondary blocks, accent blocks) taken
 *    from worked palettes and tested combinations in build guides
 *  - texture noise class: flat / mid / loud, per guide taxonomy
 *  - color temperature: warm / cool / neutral
 *  - primary-quality lists: which blocks survive as a main wall material and
 *    which break builds when used in bulk
 *
 * Loading is fail-soft: a missing or malformed file leaves every lookup
 * returning "no signal" and the engine keeps running on its other scores.
 */
public final class StyleKnowledge {

    public static class Roles {
        public List<String> secondary = List.of();
        public List<String> accent = List.of();
    }

    public static class Data {
        public Map<String, Roles> pairings = new HashMap<>();
        public Map<String, String> noise = new HashMap<>();
        public Map<String, String> temperature = new HashMap<>();
        public List<String> goodPrimaries = List.of();
        public List<String> badPrimaries = List.of();
    }

    private static final StyleKnowledge INSTANCE = new StyleKnowledge();

    private final Data data;

    private StyleKnowledge() {
        Data loaded;
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/surfacemuse/knowledge.json")) {
            if (stream == null) {
                loaded = new Data();
            } else {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                loaded = new Gson().fromJson(reader, Data.class);
                if (loaded == null) {
                    loaded = new Data();
                }
            }
        } catch (Exception e) {
            loaded = new Data();
        }
        this.data = loaded;
    }

    public static StyleKnowledge get() {
        return INSTANCE;
    }

    /** Bonus when the guides have this exact pairing on record. */
    public double provenBonus(String baseId, String candidateId, boolean accentRole) {
        if (baseId == null || candidateId == null) {
            return 0;
        }
        var roles = data.pairings.get(baseId);
        if (roles == null) {
            return 0;
        }
        List<String> list = accentRole ? roles.accent : roles.secondary;
        return list != null && list.contains(candidateId)
                ? (accentRole ? 60.0 : 50.0)
                : 0;
    }

    /**
     * Texture and temperature fit penalty/bonus for the detail block role.
     * Two loud textures touching cancel each other out (guides' "one loud
     * block per palette" rule), a loud base prefers a calm detail block, and
     * a bad-as-primary base gets steered toward proven calm primaries.
     */
    public double textureFit(String baseId, String candidateId, boolean accentRole) {
        if (baseId == null || candidateId == null || accentRole) {
            return 0;
        }
        String baseNoise = data.noise.get(baseId);
        String candNoise = data.noise.get(candidateId);
        double score = 0;

        if ("loud".equals(baseNoise)) {
            if ("loud".equals(candNoise)) {
                score -= 30.0;
            } else if ("flat".equals(candNoise)) {
                score += 8.0;
            }
            if (data.goodPrimaries.contains(candidateId)) {
                score += 6.0;
            }
        }

        if (data.badPrimaries.contains(baseId)
                && data.goodPrimaries.contains(candidateId)) {
            score += 14.0;
        }

        String baseTemp = data.temperature.get(baseId);
        String candTemp = data.temperature.get(candidateId);
        if (baseTemp != null && candTemp != null) {
            if (baseTemp.equals(candTemp)) {
                score += 7.0;
            } else if ("neutral".equals(baseTemp) || "neutral".equals(candTemp)) {
                score += 2.0;
            } else {
                score -= 8.0;
            }
        }
        return score;
    }

    public boolean hasPairingFor(String baseId) {
        return baseId != null && data.pairings.containsKey(baseId);
    }

    public Set<String> knownBaseBlocks() {
        return new HashSet<>(data.pairings.keySet());
    }
}
