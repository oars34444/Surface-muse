package dev.surfacemuse.client;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The candidate pool the ranking engine draws from. Vanilla-approximate and
 * resilient: every id resolves through the registry and quietly drops out
 * if Mojang renamed or removed it, so the mod never crashes on a missing
 * block. New 26.3 woods like poplar are included and degrade gracefully on
 * older versions.
 */
public final class PalettePool {

    private PalettePool() {
    }

    private static final String[] CANDIDATE_IDS = {
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
            "chiseled_stone_bricks", "smooth_stone", "stone", "andesite",
            "polished_andesite", "granite", "polished_granite", "diorite",
            "polished_diorite", "calcite", "tuff", "polished_tuff",
            "chiseled_tuff", "tuff_bricks", "tuff_slab", "tuff_stairs",
            "cobbled_deepslate", "polished_deepslate", "deepslate_tiles",
            "deepslate_bricks", "chiseled_deepslate", "polished_deepslate_slab",
            "polished_deepslate_stairs", "blackstone", "polished_blackstone",
            "polished_blackstone_bricks", "chiseled_polished_blackstone",
            "gilded_blackstone", "basalt", "polished_basalt", "smooth_basalt",
            "sandstone", "chiseled_sandstone", "cut_sandstone", "smooth_sandstone",
            "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone",
            "smooth_red_sandstone", "bricks", "mud_bricks", "packed_mud",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
            "acacia_planks", "dark_oak_planks", "mangrove_planks",
            "cherry_planks", "bamboo_planks", "pale_oak_planks", "poplar_planks",
            "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log",
            "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log",
            "poplar_log", "stripped_oak_log", "stripped_spruce_log",
            "stripped_birch_log", "stripped_cherry_log", "stripped_poplar_log",
            "oak_wood", "spruce_wood", "dark_oak_wood",
            "quartz_block", "chiseled_quartz_block", "smooth_quartz",
            "quartz_bricks", "quartz_pillar", "purpur_block", "purpur_pillar",
            "end_stone_bricks", "nether_bricks", "cracked_nether_bricks",
            "chiseled_nether_bricks", "red_nether_bricks", "prismarine",
            "prismarine_bricks", "dark_prismarine", "cut_copper",
            "exposed_cut_copper", "oxidized_cut_copper", "waxed_cut_copper",
            "copper_block", "chiseled_copper", "copper_grate",
            "crimson_planks", "warped_planks", "crimson_stem", "warped_stem",
            "wool", "cyan_wool", "light_blue_wool", "light_gray_wool",
            "white_wool", "orange_terracotta", "white_terracotta",
            "light_gray_terracotta", "white_concrete", "light_gray_concrete",
            "gray_concrete", "black_concrete", "brown_concrete",
            "glass", "tinted_glass", "smooth_stone_slab", "brick_slab",
            "stone_brick_slab", "stone_brick_stairs", "stone_brick_wall",
            "brick_stairs", "brick_wall", "cobblestone_wall", "mossy_cobblestone",
            "mossy_cobblestone_wall", "cobblestone", "iron_block",
            "iron_bars", "chain", "lantern", "soul_lantern", "sea_lantern",
            "shroomlight", "glowstone", "campfire", "crying_obsidian",
            "chiseled_bookshelf", "bookshelf", "barrel", "loom"
    };

    public static List<Block> resolve() {
        List<Block> pool = new ArrayList<>();
        for (String id : CANDIDATE_IDS) {
            Block block = Registries.BLOCK
                    .get(Identifier.of("minecraft", id));
            if (block != null && !pool.contains(block)) {
                pool.add(block);
            }
        }
        return pool;
    }
}
