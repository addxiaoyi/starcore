package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * 降级菜单提供者（纯聊天式交互）
 * 当所有高级菜单框架都不可用时使用
 *
 * audit C-077/C-078: 此 fallback 实现使用纯 player.sendMessage，未构造 Inventory，
 *   不存在 setItem 调用或槽位越界可能。两条 audit 项在本实现中均不适用（无操作）。
 */
public class FallbackChestMenuProvider implements NationMenuProvider {
    private final NationModule nationModule;
    private final MessageService messages;

    public FallbackChestMenuProvider(NationModule nationModule, MessageService messages) {
        this.nationModule = nationModule;
        this.messages = messages;
    }

    @Override
    public void openMainMenu(Player player) {
        Optional<Nation> nationOpt = nationModule.getNationByMember(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            showVisitorInfo(player);
            return;
        }

        Nation nation = nationOpt.get();

        // 纯聊天式菜单
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
        player.sendMessage(Component.text("⚔ " + nation.name() + " 国家管理", NamedTextColor.GOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("可用命令：", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("• /sc nation info - 国家信息", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• /sc nation members - 成员管理", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• /sc nation claims - 领土管理", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• /sc treasury - 国库管理", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• /sc diplomacy - 外交关系", NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
    }

    @Override
    public void openSubMenu(Player player, Nation nation, String submenuId) {
        player.sendMessage(Component.text("⚠ 子菜单功能暂不可用，请使用命令操作", NamedTextColor.RED));
    }

    private void showVisitorInfo(Player player) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
        player.sendMessage(Component.text("⚔ 国家系统 - 访客模式", NamedTextColor.GRAY));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("你还没有加入任何国家", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("可用命令：", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• /sc nation create <名称> - 创建国家", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• /sc nation join <名称> - 加入国家", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• /sc nation list - 查看所有国家", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY));
    }

    @Override
    public String getProviderType() {
        return "Fallback (Chat)";
    }

    @Override
    public boolean isAvailable() {
        return true; // 降级方案始终可用
    }
}
