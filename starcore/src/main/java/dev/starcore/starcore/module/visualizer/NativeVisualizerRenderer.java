package dev.starcore.starcore.module.visualizer;

import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Beacon;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Campfire;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Furnace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

final class NativeVisualizerRenderer implements VisualizerRenderer {
    @Override
    public Optional<VisualizerRenderSnapshot> renderBlock(Block block, VisualizerEntry entry, Player viewer, VisualizerConfig config) {
        if (block == null || entry == null) {
            return Optional.empty();
        }
        BlockState state = block.getState(false);
        List<String> lines = switch (entry) {
            case CHEST, DOUBLE_CHEST, BARREL, SHULKER_BOX, DISPENSER, DROPPER, HOPPER -> containerLines(entry, state);
            case FURNACE, BLAST_FURNACE, SMOKER -> furnaceLines(entry, state);
            case BREWING_STAND -> brewingLines(state);
            case CAMPFIRE, SOUL_CAMPFIRE -> campfireLines(entry, state);
            case BEACON -> beaconLines(state);
            case BEE_NEST, BEEHIVE -> beehiveLines(entry, block, state);
            case LECTERN -> lecternLines(state);
            case JUKEBOX -> jukeboxLines(state);
            case NOTE_BLOCK -> noteBlockLines(block);
            case SPAWNER -> spawnerLines(state);
            case CONDUIT -> conduitLines(block);
            case BANNER -> bannerLines(state);
            case BOOKSHELF -> bookshelfLines(block, state);
            default -> simpleInteractionLines(entry);
        };
        List<ItemStack> items = previewItems(entry, state);
        return snapshot(entry, lines, items);
    }

    @Override
    public Optional<VisualizerRenderSnapshot> renderEntity(Entity entity, VisualizerEntry entry, Player viewer, VisualizerConfig config) {
        if (entity instanceof Item item && entry == VisualizerEntry.ITEM) {
            ItemStack stack = item.getItemStack();
            List<String> lines = List.of(
                "&e" + VisualizerText.itemName(stack),
                stack.getAmount() > 1 ? "&bx" + stack.getAmount() : "&7single item"
            );
            return snapshot(entry, lines, List.of(stack.clone()));
        }
        if (entity instanceof Villager villager && entry == VisualizerEntry.VILLAGER) {
            List<String> lines = List.of(
                "&eVillager",
                "&7" + prettyEnum(villager.getProfession().name()) + " &8Lv." + villager.getVillagerLevel(),
                "&7Restocks: &f" + villager.getRestocksToday()
            );
            return snapshot(entry, lines, List.of());
        }
        return Optional.empty();
    }

    private List<String> containerLines(VisualizerEntry entry, BlockState state) {
        if (!(state instanceof Container container)) {
            return simpleInteractionLines(entry);
        }
        Inventory inventory = container.getInventory();
        int used = 0;
        int total = 0;
        ItemStack top = null;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            used++;
            total += item.getAmount();
            if (top == null || item.getAmount() > top.getAmount()) {
                top = item;
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("&e" + entry.displayName());
        lines.add("&7Slots: &f" + used + "&8/&f" + inventory.getSize());
        lines.add("&7Items: &f" + total);
        if (top != null) {
            lines.add("&7Top: &f" + VisualizerText.itemName(top) + " x" + top.getAmount());
        }
        return lines;
    }

    private List<String> furnaceLines(VisualizerEntry entry, BlockState state) {
        if (!(state instanceof Furnace furnace)) {
            return simpleInteractionLines(entry);
        }
        int total = Math.max(1, furnace.getCookTimeTotal());
        double progress = Math.max(0, furnace.getCookTime()) / (double) total;
        boolean burning = furnace.getBurnTime() > 0;
        List<String> lines = new ArrayList<>();
        lines.add("&e" + entry.displayName());
        lines.add("&7Cook " + VisualizerText.progressBar(progress, 10, "&e", "&7", "|"));
        lines.add(burning ? "&6Fuel active" : "&cNo fuel");
        ItemStack input = furnace.getInventory().getSmelting();
        if (input != null && !input.getType().isAir()) {
            lines.add("&7Input: &f" + VisualizerText.itemName(input) + " x" + input.getAmount());
        }
        return lines;
    }

    private List<String> brewingLines(BlockState state) {
        if (!(state instanceof BrewingStand brewing)) {
            return simpleInteractionLines(VisualizerEntry.BREWING_STAND);
        }
        int total = Math.max(1, brewing.getRecipeBrewTime());
        double progress = brewing.getBrewingTime() <= 0 ? 0.0D : 1.0D - (brewing.getBrewingTime() / (double) total);
        return List.of(
            "&eBrewing Stand",
            "&7Brew " + VisualizerText.progressBar(progress, 10, "&6", "&7", "|"),
            brewing.getFuelLevel() > 0 ? "&6Fuel: &f" + brewing.getFuelLevel() : "&cNo fuel"
        );
    }

    private List<String> campfireLines(VisualizerEntry entry, BlockState state) {
        if (!(state instanceof Campfire campfire)) {
            return simpleInteractionLines(entry);
        }
        List<String> lines = new ArrayList<>();
        lines.add("&e" + entry.displayName());
        int occupied = 0;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            occupied++;
            int total = Math.max(1, campfire.getCookTimeTotal(slot));
            double progress = campfire.getCookTime(slot) / (double) total;
            lines.add("&7" + VisualizerText.itemName(item) + " " + VisualizerText.progressBar(progress, 10, "&e", "&7", "|"));
        }
        if (occupied == 0) {
            lines.add("&7No cooking items");
        }
        return lines;
    }

    private List<String> beaconLines(BlockState state) {
        if (!(state instanceof Beacon beacon)) {
            return simpleInteractionLines(VisualizerEntry.BEACON);
        }
        PotionEffect primary = beacon.getPrimaryEffect();
        PotionEffect secondary = beacon.getSecondaryEffect();
        List<String> lines = new ArrayList<>();
        lines.add("&bBeacon Tier &f" + beacon.getTier());
        lines.add("&7Range: &f" + Math.round(beacon.getEffectRange()) + "m");
        if (primary != null) {
            lines.add("&7Primary: &f" + prettyEnum(primary.getType().getKey().getKey()));
        }
        if (secondary != null) {
            lines.add("&7Secondary: &f" + prettyEnum(secondary.getType().getKey().getKey()));
        }
        return lines;
    }

    private List<String> beehiveLines(VisualizerEntry entry, Block block, BlockState state) {
        int honey = 0;
        int maxHoney = 5;
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Beehive hiveData) {
            honey = hiveData.getHoneyLevel();
            maxHoney = hiveData.getMaximumHoneyLevel();
        }
        int bees = state instanceof Beehive hive ? hive.getEntityCount() : 0;
        return List.of(
            "&e" + entry.displayName(),
            "&7Honey: &f" + honey + "&8/&f" + maxHoney,
            "&7Bees: &f" + bees
        );
    }

    private List<String> lecternLines(BlockState state) {
        if (!(state instanceof Lectern lectern)) {
            return simpleInteractionLines(VisualizerEntry.LECTERN);
        }
        ItemStack book = lectern.getInventory().getItem(0);
        if (book != null && book.getItemMeta() instanceof BookMeta bookMeta) {
            String title = bookMeta.hasTitle() ? bookMeta.getTitle() : "Book";
            String author = bookMeta.hasAuthor() ? bookMeta.getAuthor() : "Unknown";
            return List.of("&e" + title, "&7- " + author, "&7Page: &f" + (lectern.getPage() + 1));
        }
        return List.of("&eLectern", "&7Empty");
    }

    private List<String> jukeboxLines(BlockState state) {
        if (!(state instanceof Jukebox jukebox) || !jukebox.hasRecord()) {
            return List.of("&eJukebox", "&7No disc");
        }
        return List.of("&eJukebox", "&7Playing: &f" + VisualizerText.itemName(jukebox.getRecord()));
    }

    private List<String> noteBlockLines(Block block) {
        if (block.getBlockData() instanceof NoteBlock noteBlock) {
            return List.of(
                "&eNote Block",
                "&7" + prettyEnum(noteBlock.getInstrument().name()),
                "&7Note: &f" + noteBlock.getNote().getId()
            );
        }
        return simpleInteractionLines(VisualizerEntry.NOTE_BLOCK);
    }

    private List<String> spawnerLines(BlockState state) {
        if (!(state instanceof CreatureSpawner spawner)) {
            return simpleInteractionLines(VisualizerEntry.SPAWNER);
        }
        int maxDelay = Math.max(1, spawner.getMaxSpawnDelay());
        double progress = 1.0D - Math.max(0, spawner.getDelay()) / (double) maxDelay;
        return List.of(
            "&eSpawner",
            "&7Mob: &f" + prettyEnum(spawner.getSpawnedType().name()),
            "&7Spawn " + VisualizerText.progressBar(progress, 10, "&a", "&c", "|"),
            "&7Range: &f" + spawner.getSpawnRange() + "m"
        );
    }

    private List<String> conduitLines(Block block) {
        int power = block.getBlockData() instanceof AnaloguePowerable powerable ? powerable.getPower() : 0;
        return List.of("&bConduit", power > 0 ? "&aActive" : "&7Inactive");
    }

    private List<String> bannerLines(BlockState state) {
        if (!(state instanceof Banner banner)) {
            return simpleInteractionLines(VisualizerEntry.BANNER);
        }
        return List.of(
            "&eBanner",
            "&7Base: &f" + prettyEnum(banner.getBaseColor().name()),
            "&7Patterns: &f" + banner.numberOfPatterns()
        );
    }

    private List<String> bookshelfLines(Block block, BlockState state) {
        if (state instanceof ChiseledBookshelf bookshelf) {
            int books = 0;
            for (ItemStack item : bookshelf.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    books++;
                }
            }
            return List.of("&eChiseled Bookshelf", "&7Books: &f" + books + "&8/&f6", "&7Last slot: &f" + bookshelf.getLastInteractedSlot());
        }
        return List.of("&eBookshelf", "&7Decorative shelf");
    }

    private List<String> simpleInteractionLines(VisualizerEntry entry) {
        return List.of("&e" + entry.displayName(), "&7Interaction visualizer ready");
    }

    private List<ItemStack> previewItems(VisualizerEntry entry, BlockState state) {
        if (state instanceof Furnace furnace) {
            return nonAir(furnace.getInventory().getSmelting(), furnace.getInventory().getResult(), furnace.getInventory().getFuel());
        }
        if (state instanceof BrewingStand brewing) {
            return nonAir(brewing.getInventory().getIngredient(), brewing.getInventory().getFuel(), brewing.getInventory().getItem(0), brewing.getInventory().getItem(1), brewing.getInventory().getItem(2));
        }
        if (state instanceof Campfire campfire) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < campfire.getSize(); i++) {
                ItemStack item = campfire.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    items.add(item.clone());
                }
            }
            return items.stream().limit(4).toList();
        }
        if (state instanceof Jukebox jukebox && jukebox.hasRecord()) {
            return nonAir(jukebox.getRecord());
        }
        if (state instanceof Lectern lectern) {
            return nonAir(lectern.getInventory().getItem(0));
        }
        if (state instanceof Container container) {
            return topItems(container.getInventory());
        }
        if (entry == VisualizerEntry.BEACON) {
            return List.of(new ItemStack(Material.BEACON));
        }
        return List.of();
    }

    private List<ItemStack> topItems(Inventory inventory) {
        return java.util.Arrays.stream(inventory.getContents())
            .filter(Objects::nonNull)
            .filter(item -> !item.getType().isAir())
            .sorted(Comparator.comparingInt(ItemStack::getAmount).reversed())
            .limit(4)
            .map(ItemStack::clone)
            .collect(Collectors.toList());
    }

    private List<ItemStack> nonAir(ItemStack... items) {
        List<ItemStack> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                result.add(item.clone());
            }
        }
        return result;
    }

    private Optional<VisualizerRenderSnapshot> snapshot(VisualizerEntry entry, List<String> lines, List<ItemStack> items) {
        List<String> colored = lines.stream().map(VisualizerText::color).toList();
        String hash = entry.key() + "|" + String.join("|", colored) + "|" + items.stream()
            .map(item -> item.getType().name() + ":" + item.getAmount())
            .collect(Collectors.joining(","));
        return Optional.of(new VisualizerRenderSnapshot(entry, colored, items, hash));
    }

    private String prettyEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String value = raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder(value.length());
        boolean upper = true;
        for (char c : value.toCharArray()) {
            if (upper && Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
                upper = false;
            } else {
                builder.append(c);
            }
            if (c == ' ') {
                upper = true;
            }
        }
        return builder.toString();
    }
}
