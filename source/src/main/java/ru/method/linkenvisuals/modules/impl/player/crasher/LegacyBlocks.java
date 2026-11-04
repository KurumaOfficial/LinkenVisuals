package ru.method.linkenvisuals.modules.impl.player.crasher;

import java.util.HashMap;
import java.util.Map;

/**
 * Числовые ID старого WorldEdit ({@code //set 35:14}) → современные имена блоков.
 * Покрыты все ходовые блоки и все цветные семейства.
 */
public final class LegacyBlocks {

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final Map<String, String> BY_ID = new HashMap<>();

    private LegacyBlocks() {
    }

    private static void put(int id, String name) {
        BY_ID.put(String.valueOf(id), name);
    }

    private static void put(int id, int data, String name) {
        BY_ID.put(id + ":" + data, name);
        if (data == 0) {
            BY_ID.put(String.valueOf(id), name);
        }
    }

    /** Цветное семейство: 16 вариантов через data-значение. */
    private static void colored(int id, String prefix, String suffix) {
        for (int i = 0; i < COLORS.length; i++) {
            put(id, i, prefix + COLORS[i] + suffix);
        }
    }

    static {
        put(0, "air");
        put(1, 0, "stone");
        put(1, 1, "granite");
        put(1, 2, "polished_granite");
        put(1, 3, "diorite");
        put(1, 4, "polished_diorite");
        put(1, 5, "andesite");
        put(1, 6, "polished_andesite");
        put(2, "grass_block");
        put(3, 0, "dirt");
        put(3, 1, "coarse_dirt");
        put(3, 2, "podzol");
        put(4, "cobblestone");
        put(5, 0, "oak_planks");
        put(5, 1, "spruce_planks");
        put(5, 2, "birch_planks");
        put(5, 3, "jungle_planks");
        put(5, 4, "acacia_planks");
        put(5, 5, "dark_oak_planks");
        put(6, "oak_sapling");
        put(7, "bedrock");
        put(8, "water");
        put(9, "water");
        put(10, "lava");
        put(11, "lava");
        put(12, 0, "sand");
        put(12, 1, "red_sand");
        put(13, "gravel");
        put(14, "gold_ore");
        put(15, "iron_ore");
        put(16, "coal_ore");
        put(17, 0, "oak_log");
        put(17, 1, "spruce_log");
        put(17, 2, "birch_log");
        put(17, 3, "jungle_log");
        put(18, "oak_leaves");
        put(19, "sponge");
        put(20, "glass");
        put(21, "lapis_ore");
        put(22, "lapis_block");
        put(23, "dispenser");
        put(24, 0, "sandstone");
        put(24, 1, "chiseled_sandstone");
        put(24, 2, "cut_sandstone");
        put(25, "note_block");
        put(26, "red_bed");
        put(27, "powered_rail");
        put(28, "detector_rail");
        put(29, "sticky_piston");
        put(30, "cobweb");
        put(31, "short_grass");
        put(32, "dead_bush");
        put(33, "piston");
        colored(35, "", "_wool");
        put(37, "dandelion");
        put(38, "poppy");
        put(39, "brown_mushroom");
        put(40, "red_mushroom");
        put(41, "gold_block");
        put(42, "iron_block");
        put(43, "smooth_stone");
        put(44, "smooth_stone_slab");
        put(45, "bricks");
        put(46, "tnt");
        put(47, "bookshelf");
        put(48, "mossy_cobblestone");
        put(49, "obsidian");
        put(50, "torch");
        put(51, "fire");
        put(52, "spawner");
        put(53, "oak_stairs");
        put(54, "chest");
        put(55, "redstone_wire");
        put(56, "diamond_ore");
        put(57, "diamond_block");
        put(58, "crafting_table");
        put(60, "farmland");
        put(61, "furnace");
        put(63, "oak_sign");
        put(64, "oak_door");
        put(65, "ladder");
        put(66, "rail");
        put(67, "cobblestone_stairs");
        put(68, "oak_wall_sign");
        put(69, "lever");
        put(70, "stone_pressure_plate");
        put(72, "oak_pressure_plate");
        put(73, "redstone_ore");
        put(74, "redstone_ore");
        put(76, "redstone_torch");
        put(77, "stone_button");
        put(78, "snow");
        put(79, "ice");
        put(80, "snow_block");
        put(81, "cactus");
        put(82, "clay");
        put(83, "sugar_cane");
        put(84, "jukebox");
        put(85, "oak_fence");
        put(86, "carved_pumpkin");
        put(87, "netherrack");
        put(88, "soul_sand");
        put(89, "glowstone");
        put(90, "nether_portal");
        put(91, "jack_o_lantern");
        put(92, "cake");
        colored(95, "", "_stained_glass");
        put(96, "oak_trapdoor");
        put(98, 0, "stone_bricks");
        put(98, 1, "mossy_stone_bricks");
        put(98, 2, "cracked_stone_bricks");
        put(98, 3, "chiseled_stone_bricks");
        put(99, "brown_mushroom_block");
        put(100, "red_mushroom_block");
        put(101, "iron_bars");
        put(102, "glass_pane");
        put(103, "melon");
        put(106, "vine");
        put(107, "oak_fence_gate");
        put(108, "brick_stairs");
        put(109, "stone_brick_stairs");
        put(110, "mycelium");
        put(111, "lily_pad");
        put(112, "nether_bricks");
        put(113, "nether_brick_fence");
        put(114, "nether_brick_stairs");
        put(116, "enchanting_table");
        put(117, "brewing_stand");
        put(120, "end_portal_frame");
        put(121, "end_stone");
        put(122, "dragon_egg");
        put(123, "redstone_lamp");
        put(124, "redstone_lamp");
        put(129, "emerald_ore");
        put(130, "ender_chest");
        put(133, "emerald_block");
        put(137, "command_block");
        put(138, "beacon");
        put(139, "cobblestone_wall");
        put(145, "anvil");
        put(146, "trapped_chest");
        put(152, "redstone_block");
        put(153, "nether_quartz_ore");
        put(154, "hopper");
        put(155, 0, "quartz_block");
        put(155, 1, "chiseled_quartz_block");
        put(155, 2, "quartz_pillar");
        put(156, "quartz_stairs");
        put(157, "activator_rail");
        put(158, "dropper");
        colored(159, "", "_terracotta");
        colored(160, "", "_stained_glass_pane");
        put(161, "acacia_leaves");
        put(162, 0, "acacia_log");
        put(162, 1, "dark_oak_log");
        put(163, "acacia_stairs");
        put(164, "dark_oak_stairs");
        put(165, "slime_block");
        put(166, "barrier");
        put(167, "iron_trapdoor");
        put(168, 0, "prismarine");
        put(168, 1, "prismarine_bricks");
        put(168, 2, "dark_prismarine");
        put(169, "sea_lantern");
        put(170, "hay_block");
        colored(171, "", "_carpet");
        put(172, "terracotta");
        put(173, "coal_block");
        put(174, "packed_ice");
        put(179, 0, "red_sandstone");
        put(179, 1, "chiseled_red_sandstone");
        put(179, 2, "cut_red_sandstone");
        put(180, "red_sandstone_stairs");
        put(183, "spruce_fence_gate");
        put(184, "birch_fence_gate");
        put(185, "jungle_fence_gate");
        put(186, "dark_oak_fence_gate");
        put(187, "acacia_fence_gate");
        put(188, "spruce_fence");
        put(189, "birch_fence");
        put(190, "jungle_fence");
        put(191, "dark_oak_fence");
        put(192, "acacia_fence");
        put(198, "end_rod");
        put(199, "chorus_plant");
        put(201, "purpur_block");
        put(202, "purpur_pillar");
        put(203, "purpur_stairs");
        put(206, "end_stone_bricks");
        put(208, "dirt_path");
        put(213, "magma_block");
        put(214, "nether_wart_block");
        put(215, "red_nether_bricks");
        put(216, "bone_block");
        put(219, "white_shulker_box");
        colored(251, "", "_concrete");
        colored(252, "", "_concrete_powder");
    }

    /**
     * @param raw "35" или "35:14"
     * @return имя блока без namespace, или null если такого легаси-ID нет
     */
    public static String resolve(String raw) {
        return BY_ID.get(raw);
    }
}
