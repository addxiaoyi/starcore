package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.battle.BattleCalculator;
import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 军事学说事件监听器
 * 处理学说变更事件和战斗加成应用
 */
public final class DoctrineListener implements Listener {
    private final DoctrineService doctrineService;
    private final NationService nationService;
    private final MessageService messages;

    public DoctrineListener(
        DoctrineService doctrineService,
        NationService nationService,
        MessageService messages
    ) {
        this.doctrineService = doctrineService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDoctrineChanged(DoctrineChangedEvent event) {
        UUID nationId = event.nationId();

        // 获取国家名称
        String nationName = nationService.nationById(new dev.starcore.starcore.module.nation.model.NationId(nationId))
            .map(Nation::name)
            .orElse("Unknown");

        // 广播学说变更消息
        if (event.isNewAdoption()) {
            broadcastDoctrineAdopted(nationId, nationName, event.newDoctrine());
        } else if (event.isSwitch()) {
            broadcastDoctrineSwitched(nationId, nationName, event.previousDoctrine(), event.newDoctrine());
        } else if (event.isClear()) {
            broadcastDoctrineCleared(nationId, nationName, event.previousDoctrine());
        }
    }

    /**
     * 广播新学说采用消息
     */
    private void broadcastDoctrineAdopted(UUID nationId, String nationName, DoctrineType doctrine) {
        String message = messages.format("doctrine.announce.adopted",
            nationName, doctrine.displayName());
        broadcastToNation(nationId, message);
    }

    /**
     * 广播学说切换消息
     */
    private void broadcastDoctrineSwitched(UUID nationId, String nationName,
            DoctrineType previous, DoctrineType current) {
        String message = messages.format("doctrine.announce.switched",
            nationName, previous.displayName(), current.displayName());
        broadcastToNation(nationId, message);
    }

    /**
     * 广播学说清除消息
     */
    private void broadcastDoctrineCleared(UUID nationId, String nationName, DoctrineType doctrine) {
        String message = messages.format("doctrine.announce.cleared",
            nationName, doctrine.displayName());
        broadcastToNation(nationId, message);
    }

    /**
     * 向国家所有在线成员广播消息
     */
    private void broadcastToNation(UUID nationId, String message) {
        Optional<Nation> nationOpt = nationService.nationById(
            new dev.starcore.starcore.module.nation.model.NationId(nationId));

        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        Component component = Component.text(message, NamedTextColor.YELLOW);

        for (NationMember member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(component);
            }
        }
    }
}