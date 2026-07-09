package dev.starcore.starcore.module.visualizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum VisualizerEntry {
    CRAFTING_TABLE("interactionvisualizer:crafting_table", "Crafting Table"),
    CRAFTER("interactionvisualizer:crafter", "Crafter"),
    LOOM("interactionvisualizer:loom", "Loom"),
    ENCHANTMENT_TABLE("interactionvisualizer:enchantment_table", "Enchantment Table"),
    CARTOGRAPHY_TABLE("interactionvisualizer:cartography_table", "Cartography Table"),
    ANVIL("interactionvisualizer:anvil", "Anvil"),
    GRINDSTONE("interactionvisualizer:grindstone", "Grindstone"),
    STONECUTTER("interactionvisualizer:stonecutter", "Stonecutter"),
    BREWING_STAND("interactionvisualizer:brewing_stand", "Brewing Stand"),
    CHEST("interactionvisualizer:chest", "Chest"),
    DOUBLE_CHEST("interactionvisualizer:double_chest", "Double Chest"),
    BARREL("interactionvisualizer:barrel", "Barrel"),
    FURNACE("interactionvisualizer:furnace", "Furnace"),
    BLAST_FURNACE("interactionvisualizer:blast_furnace", "Blast Furnace"),
    SMOKER("interactionvisualizer:smoker", "Smoker"),
    ENDER_CHEST("interactionvisualizer:ender_chest", "Ender Chest"),
    SHULKER_BOX("interactionvisualizer:shulker_box", "Shulker Box"),
    DISPENSER("interactionvisualizer:dispenser", "Dispenser"),
    DROPPER("interactionvisualizer:dropper", "Dropper"),
    HOPPER("interactionvisualizer:hopper", "Hopper"),
    BEACON("interactionvisualizer:beacon", "Beacon"),
    NOTE_BLOCK("interactionvisualizer:note_block", "Note Block"),
    JUKEBOX("interactionvisualizer:jukebox", "Jukebox"),
    SMITHING_TABLE("interactionvisualizer:smithing_table", "Smithing Table"),
    BEE_NEST("interactionvisualizer:bee_nest", "Bee Nest"),
    BEEHIVE("interactionvisualizer:beehive", "Beehive"),
    LECTERN("interactionvisualizer:lectern", "Lectern"),
    CAMPFIRE("interactionvisualizer:campfire", "Campfire"),
    SOUL_CAMPFIRE("interactionvisualizer:soul_campfire", "Soul Campfire"),
    SPAWNER("interactionvisualizer:spawner", "Spawner"),
    CONDUIT("interactionvisualizer:conduit", "Conduit"),
    BANNER("interactionvisualizer:banner", "Banner"),
    ITEM("interactionvisualizer:item", "Item"),
    VILLAGER("interactionvisualizer:villager", "Villager"),
    BOOKSHELF("bookshelf:bookshelf", "Bookshelf");

    private final String key;
    private final String displayName;

    VisualizerEntry(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<VisualizerEntry> fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(raw);
        return Arrays.stream(values())
            .filter(entry -> normalize(entry.key).equals(normalized)
                || normalize(entry.name()).equals(normalized)
                || normalize(entry.displayName).equals(normalized))
            .findFirst();
    }

    public static Optional<VisualizerEntry> fromBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        Material material = block.getType();
        String name = material.name();
        return switch (material) {
            case CRAFTING_TABLE -> Optional.of(CRAFTING_TABLE);
            case CRAFTER -> Optional.of(CRAFTER);
            case LOOM -> Optional.of(LOOM);
            case ENCHANTING_TABLE -> Optional.of(ENCHANTMENT_TABLE);
            case CARTOGRAPHY_TABLE -> Optional.of(CARTOGRAPHY_TABLE);
            case GRINDSTONE -> Optional.of(GRINDSTONE);
            case STONECUTTER -> Optional.of(STONECUTTER);
            case BREWING_STAND -> Optional.of(BREWING_STAND);
            case BARREL -> Optional.of(BARREL);
            case FURNACE -> Optional.of(FURNACE);
            case BLAST_FURNACE -> Optional.of(BLAST_FURNACE);
            case SMOKER -> Optional.of(SMOKER);
            case ENDER_CHEST -> Optional.of(ENDER_CHEST);
            case DISPENSER -> Optional.of(DISPENSER);
            case DROPPER -> Optional.of(DROPPER);
            case HOPPER -> Optional.of(HOPPER);
            case BEACON -> Optional.of(BEACON);
            case NOTE_BLOCK -> Optional.of(NOTE_BLOCK);
            case JUKEBOX -> Optional.of(JUKEBOX);
            case SMITHING_TABLE -> Optional.of(SMITHING_TABLE);
            case BEE_NEST -> Optional.of(BEE_NEST);
            case BEEHIVE -> Optional.of(BEEHIVE);
            case LECTERN -> Optional.of(LECTERN);
            case CAMPFIRE -> Optional.of(CAMPFIRE);
            case SOUL_CAMPFIRE -> Optional.of(SOUL_CAMPFIRE);
            case SPAWNER -> Optional.of(SPAWNER);
            case CONDUIT -> Optional.of(CONDUIT);
            case CHISELED_BOOKSHELF, BOOKSHELF -> Optional.of(BOOKSHELF);
            case CHEST, TRAPPED_CHEST -> chestEntry(block);
            default -> {
                if (name.endsWith("ANVIL")) {
                    yield Optional.of(ANVIL);
                }
                if (name.endsWith("SHULKER_BOX")) {
                    yield Optional.of(SHULKER_BOX);
                }
                if (name.endsWith("BANNER")) {
                    yield Optional.of(BANNER);
                }
                yield Optional.empty();
            }
        };
    }

    private static Optional<VisualizerEntry> chestEntry(Block block) {
        BlockState state = block.getState(false);
        if (state instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest) {
            return Optional.of(DOUBLE_CHEST);
        }
        return Optional.of(CHEST);
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT)
            .replace("interactionvisualizer:", "")
            .replace("bookshelf:", "")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "");
    }
}

