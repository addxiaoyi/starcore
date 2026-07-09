package dev.starcore.starcore.module.nation.claimtool;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeClaimToolService implements ClaimToolService {
    private final ConfigurationService configuration;
    private final NationService nationService;
    private final MessageService messages;
    private final NamespacedKey toolKey;
    private final Map<UUID, SelectionState> selections = new ConcurrentHashMap<>();

    public NativeClaimToolService(Plugin plugin, ConfigurationService configuration, NationService nationService, MessageService messages) {
        this.configuration = configuration;
        this.nationService = nationService;
        this.messages = messages;
        this.toolKey = new NamespacedKey(plugin, "claim_tool");
    }

    @Override
    public ItemStack createTool() {
        ItemStack item = new ItemStack(configuration.claimToolMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(configuration.claimToolName(), NamedTextColor.GOLD));
        List<Component> lore = configuration.claimToolLore().stream()
            .map(line -> (Component) Component.text(line, NamedTextColor.GRAY))
            .toList();
        meta.lore(lore);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean isClaimTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    @Override
    public ClaimToolSelectionUpdate select(Player player, Block block, ClaimToolPoint point) {
        SelectionPoint selected = SelectionPoint.from(block);
        SelectionState updated = selections.compute(player.getUniqueId(), (ignored, current) -> {
            SelectionState state = current == null || !current.sameWorld(selected.world()) ? new SelectionState(null, null) : current;
            return point == ClaimToolPoint.FIRST ? new SelectionState(selected, state.second()) : new SelectionState(state.first(), selected);
        });
        Optional<ChunkClaimSelection> selection = selectionOf(updated);
        Optional<ClaimSelectionPreview> preview = selection.map(value -> nationService.previewClaimSelection(player.getUniqueId(), value));
        return new ClaimToolSelectionUpdate(point, selected.world(), selected.blockX(), selected.blockZ(), selected.chunkX(), selected.chunkZ(), selection, preview);
    }

    @Override
    public Optional<ClaimSelectionPreview> preview(UUID playerId) {
        return Optional.ofNullable(selections.get(playerId))
            .flatMap(this::selectionOf)
            .map(selection -> nationService.previewClaimSelection(playerId, selection));
    }

    @Override
    public ClaimSelectionResult confirm(UUID playerId) {
        ChunkClaimSelection selection = Optional.ofNullable(selections.get(playerId))
            .flatMap(this::selectionOf)
            .orElseThrow(() -> new IllegalStateException(messages.format("territory.tool.incomplete-selection")));
        ClaimSelectionResult result = nationService.claimSelection(playerId, selection);
        selections.remove(playerId);
        return result;
    }

    @Override
    public boolean clear(UUID playerId) {
        return selections.remove(playerId) != null;
    }

    private Optional<ChunkClaimSelection> selectionOf(SelectionState state) {
        if (state == null || state.first() == null || state.second() == null) {
            return Optional.empty();
        }
        if (!state.first().sameWorld(state.second().world())) {
            return Optional.empty();
        }
        return Optional.of(ChunkClaimSelection.fromBlockBounds(
            state.first().world(),
            state.first().blockX(),
            state.second().blockX(),
            state.first().blockZ(),
            state.second().blockZ()
        ));
    }

    private record SelectionState(SelectionPoint first, SelectionPoint second) {
        boolean sameWorld(String world) {
            return (first == null || first.sameWorld(world)) && (second == null || second.sameWorld(world));
        }
    }

    private record SelectionPoint(String world, int blockX, int blockZ, int chunkX, int chunkZ) {
        static SelectionPoint from(Block block) {
            return new SelectionPoint(block.getWorld().getName(), block.getX(), block.getZ(), block.getChunk().getX(), block.getChunk().getZ());
        }

        boolean sameWorld(String otherWorld) {
            return world.equals(otherWorld);
        }
    }
}
