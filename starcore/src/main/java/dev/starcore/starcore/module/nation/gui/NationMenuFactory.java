package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.plugin.Plugin;

/**
 * 国家管理菜单工厂
 * 根据配置和可用性自动选择最佳菜单提供者
 *
 * 支持的提供者：
 * 1. TriumphNationMenu - TriumphGUI 箱子 + PacketEvents Anvil（推荐，完整功能）
 * 2. ProtocolLibAnvilMenuProvider - ProtocolLib Anvil（备用）
 * 3. TriumphChestMenuProvider - TriumphGUI 纯箱子（基础功能）
 * 4. Fallback - 纯聊天菜单（降级方案）
 */
public class NationMenuFactory {
    private final NationModule nationModule;
    private final MessageService messages;
    private final Plugin plugin;
    private final TreasuryService treasuryService;
    private final DiplomacyService diplomacyService;
    private final TechnologyService technologyService;
    private final GovernmentService governmentService;
    private final EconomyService economyService;
    private final PolicyService policyService;

    // 共享 Anvil 提供者实例
    private final PacketEventsAnvilProvider anvilProvider;

    public NationMenuFactory(NationModule nationModule, MessageService messages,
                            Plugin plugin, TreasuryService treasuryService,
                            DiplomacyService diplomacyService,
                            TechnologyService technologyService,
                            GovernmentService governmentService,
                            EconomyService economyService,
                            PolicyService policyService) {
        this.nationModule = nationModule;
        this.messages = messages;
        this.plugin = plugin;
        this.treasuryService = treasuryService;
        this.diplomacyService = diplomacyService;
        this.technologyService = technologyService;
        this.governmentService = governmentService;
        this.economyService = economyService;
        this.policyService = policyService;
        this.anvilProvider = new PacketEventsAnvilProvider(plugin);
    }

    /**
     * 创建菜单提供者
     * @param preferredType 首选类型 (auto/triumph/fallback)
     * @return 菜单提供者实例
     */
    public NationMenuProvider createProvider(String preferredType) {
        String type = preferredType == null ? "auto" : preferredType.toLowerCase();

        // 如果指定了具体类型，尝试创建该类型
        if (!type.equals("auto")) {
            NationMenuProvider provider = createSpecificProvider(type);
            if (provider != null && provider.isAvailable()) {
                plugin.getLogger().info("Using configured menu provider: " + provider.getProviderType());
                return provider;
            } else {
                plugin.getLogger().warning("Configured menu provider '" + type + "' is not available, falling back to auto-detection");
            }
        }

        // 自动检测：TriumphGUI + PacketEvents Anvil > ProtocolLib Anvil > Fallback
        NationMenuProvider triumph = tryCreateTriumph();
        if (triumph != null && triumph.isAvailable()) {
            plugin.getLogger().info("Auto-detected menu provider: " + triumph.getProviderType());
            return triumph;
        }

        // ProtocolLib Anvil 备用
        NationMenuProvider protocolLib = tryCreateProtocolLib();
        if (protocolLib != null && protocolLib.isAvailable()) {
            plugin.getLogger().info("Using ProtocolLib menu provider: " + protocolLib.getProviderType());
            return protocolLib;
        }

        // 降级到 Fallback
        plugin.getLogger().warning("No GUI providers available, using fallback chat menu");
        return new FallbackChestMenuProvider(nationModule, messages);
    }

    private NationMenuProvider createSpecificProvider(String type) {
        switch (type) {
            case "triumph", "chest", "auto" -> {
                return tryCreateTriumph();
            }
            case "fallback", "chat" -> {
                return new FallbackChestMenuProvider(nationModule, messages);
            }
            default -> {
                plugin.getLogger().warning("Unknown menu provider type: " + type);
                return null;
            }
        }
    }

    /**
     * 尝试创建 TriumphNationMenu 提供者（带 Anvil 输入）
     */
    private NationMenuProvider tryCreateTriumph() {
        try {
            Class.forName("dev.triumphteam.gui.guis.Gui");
            return new TriumphNationMenu(
                nationModule,
                messages,
                anvilProvider,
                treasuryService,
                diplomacyService,
                technologyService,
                governmentService,
                economyService,
                policyService,
                plugin
            );
        } catch (ClassNotFoundException e) {
            // TriumphGUI not available
        }
        return null;
    }

    /**
     * 尝试创建 ProtocolLib Anvil 提供者（备用）
     */
    private NationMenuProvider tryCreateProtocolLib() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            org.bukkit.plugin.Plugin protocolLib = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
            if (protocolLib == null || !protocolLib.isEnabled()) {
                return null;
            }
            return new ProtocolLibAnvilMenuProvider(nationModule, messages, plugin);
        } catch (ClassNotFoundException e) {
            // ProtocolLib not available
        }
        return null;
    }

    /**
     * 获取共享的 Anvil 提供者（用于测试）
     */
    public PacketEventsAnvilProvider getAnvilProvider() {
        return anvilProvider;
    }

    /**
     * 关闭工厂（清理资源）
     */
    public void shutdown() {
        if (anvilProvider != null) {
            anvilProvider.unregister();
        }
    }
}
