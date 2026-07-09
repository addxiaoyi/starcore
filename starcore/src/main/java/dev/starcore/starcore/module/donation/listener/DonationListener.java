package dev.starcore.starcore.module.donation.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.donation.DonationConfig;
import dev.starcore.starcore.module.donation.DonationService;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * 献金事件监听器
 * 处理玩家进出、献金公告等事件
 */
public final class DonationListener implements Listener {
    private final DonationService donationService;
    private final NationService nationService;
    private final MessageService messages;
    private final DonationConfig config;

    public DonationListener(
        DonationService donationService,
        NationService nationService,
        MessageService messages,
        DonationConfig config
    ) {
        this.donationService = donationService;
        this.nationService = nationService;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否有未领取的奖励
        checkUnclaimedRewards(player);

        // 显示献金信息（如果玩家有献金等级）
        showDonationInfoOnJoin(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 可以在这里添加保存数据的逻辑
    }

    private void checkUnclaimedRewards(Player player) {
        Optional<dev.starcore.starcore.module.nation.model.Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }

        var nation = nationOpt.get();
        var availableRewards = donationService.getAvailableRewards(player.getUniqueId(), nation.id());

        // 找出可领取的奖励
        long claimableCount = availableRewards.stream()
            .filter(r -> donationService.isRewardClaimable(player.getUniqueId(), nation.id(), r.id()))
            .count();

        if (claimableCount > 0) {
            player.sendMessage(Component.text(
                messages.format("donation.reward.unclaimed", claimableCount),
                NamedTextColor.YELLOW
            ));
        }
    }

    private void showDonationInfoOnJoin(Player player) {
        UUID playerId = player.getUniqueId();

        // 获取玩家献金等级
        DonationService.DonationTier tier = donationService.getPlayerTier(playerId);
        if (tier == null || tier.id().equals("none")) {
            return;
        }

        // 获取玩家总献金额
        java.math.BigDecimal totalDonations = donationService.getTotalDonations(playerId);
        if (totalDonations.signum() <= 0) {
            return;
        }

        // 显示献金欢迎信息
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.welcome.message",
                tier.name(),
                totalDonations.setScale(2, java.math.RoundingMode.DOWN).toPlainString()),
            NamedTextColor.GOLD
        ));
    }
}