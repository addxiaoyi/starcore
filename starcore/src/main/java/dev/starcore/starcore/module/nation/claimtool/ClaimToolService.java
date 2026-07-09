package dev.starcore.starcore.module.nation.claimtool;

import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public interface ClaimToolService {
    ItemStack createTool();

    boolean isClaimTool(ItemStack item);

    ClaimToolSelectionUpdate select(Player player, Block block, ClaimToolPoint point);

    Optional<ClaimSelectionPreview> preview(UUID playerId);

    ClaimSelectionResult confirm(UUID playerId);

    boolean clear(UUID playerId);
}
