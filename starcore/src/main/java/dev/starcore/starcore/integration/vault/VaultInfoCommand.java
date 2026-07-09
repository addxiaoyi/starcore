package dev.starcore.starcore.integration.vault;

import dev.starcore.starcore.foundation.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Vault 信息命令
 * /sc vault info - 显示 Vault 集成状态
 */
public final class VaultInfoCommand implements CommandExecutor, TabCompleter {
    private final VaultIntegration vaultIntegration;
    private final MessageService messages;

    public VaultInfoCommand(VaultIntegration vaultIntegration, MessageService messages) {
        this.vaultIntegration = vaultIntegration;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Vault 集成状态 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(""));

        // Vault 可用性
        boolean vaultAvailable = vaultIntegration.isRegistered();

        if (vaultAvailable) {
            sender.sendMessage(Component.text("✅ Vault: 已安装", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("❌ Vault: 未安装", NamedTextColor.RED));
        }

        // 注册状态
        if (vaultIntegration.isRegistered()) {
            sender.sendMessage(Component.text("✅ Economy Provider: 已注册（优先级：High）", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("✅ 其他插件可以使用 STARCORE 的经济系统", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("❌ Economy Provider: 未注册", NamedTextColor.RED));
            sender.sendMessage(Component.text("⚠️  经济功能仅限 STARCORE 内部使用", NamedTextColor.YELLOW));
        }

        sender.sendMessage(Component.text(""));

        // 活跃的 Economy Provider（如果有）
        vaultIntegration.getEconomyProvider().ifPresent(provider -> {
            sender.sendMessage(Component.text("当前活跃的 Economy Provider:", NamedTextColor.GRAY));
            sender.sendMessage(Component.text(
                "  Provider 名称: " + provider.getName(),
                NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                "  货币名称: " + provider.currencyNamePlural(),
                NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                "  小数位数: " + provider.fractionalDigits(),
                NamedTextColor.GRAY
            ));
        });

        sender.sendMessage(Component.text(""));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return List.of();
    }
}
