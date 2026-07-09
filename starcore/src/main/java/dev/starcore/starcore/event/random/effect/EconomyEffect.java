package dev.starcore.starcore.event.random.effect;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.event.random.EventEffect;
import dev.starcore.starcore.event.random.RandomEventService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 经济效果
 * 影响经济系统 - 集成资源/税收/市场系统
 */
public class EconomyEffect implements EventEffect {

    private static final Logger LOGGER = Logger.getLogger(EconomyEffect.class.getName());
    private static EconomyService economyService;
    private static InternalEconomyService internalEconomyService;
    private static RandomEventService eventService;
    private static ResourceService resourceService;
    private static NationService nationService;
    private static TreasuryService treasuryService;
    private static TechnologyService technologyService;
    private static DiplomacyService diplomacyService;
    private static ServiceRegistry serviceRegistry;

    private final EffectType effectType;
    private final double value;
    private final String resourceType;
    private final int duration;
    private final String targetType;  // PLAYER, NATION, GLOBAL

    public EconomyEffect(EconomyService economyService, EffectType effectType, double value,
                        String resourceType, int duration) {
        this.economyService = economyService;
        this.effectType = effectType;
        this.value = value;
        this.resourceType = resourceType;
        this.duration = duration;
        this.targetType = "PLAYER";
    }

    /**
     * 便捷构造函数（无经济服务时使用）
     */
    public EconomyEffect(EffectType effectType, double value, String resourceType, int duration) {
        this(null, effectType, value, resourceType, duration);
    }

    /**
     * 设置经济服务
     */
    public static void setEconomyService(EconomyService service) {
        economyService = service;
    }

    /**
     * 设置事件服务
     */
    public static void setEventService(RandomEventService service) {
        eventService = service;
    }

    /**
     * 设置资源服务
     */
    public static void setResourceService(ResourceService service) {
        resourceService = service;
    }

    /**
     * 设置国家服务
     */
    public static void setNationService(NationService service) {
        nationService = service;
    }

    /**
     * 设置国库服务
     */
    public static void setTreasuryService(TreasuryService service) {
        treasuryService = service;
    }

    /**
     * 设置科技服务
     */
    public static void setTechnologyService(TechnologyService service) {
        technologyService = service;
    }

    /**
     * 设置外交服务
     */
    public static void setDiplomacyService(DiplomacyService service) {
        diplomacyService = service;
    }

    /**
     * 设置服务注册表
     */
    public static void setServiceRegistry(ServiceRegistry registry) {
        serviceRegistry = registry;
    }

    /**
     * 设置内部经济服务
     */
    public static void setInternalEconomyService(InternalEconomyService service) {
        internalEconomyService = service;
    }

    /**
     * 获取内部经济服务（带延迟解析）
     */
    public static InternalEconomyService getInternalEconomyService() {
        if (internalEconomyService != null) {
            return internalEconomyService;
        }
        // 尝试从服务注册表获取
        if (serviceRegistry != null) {
            return serviceRegistry.find(InternalEconomyService.class).orElse(null);
        }
        return null;
    }

    /**
     * 尝试设置所有服务（用于自动注入）
     */
    public static void injectServices(Object... services) {
        for (Object service : services) {
            if (service instanceof EconomyService) {
                economyService = (EconomyService) service;
            } else if (service instanceof InternalEconomyService) {
                internalEconomyService = (InternalEconomyService) service;
            } else if (service instanceof RandomEventService) {
                eventService = (RandomEventService) service;
            } else if (service instanceof ResourceService) {
                resourceService = (ResourceService) service;
            } else if (service instanceof NationService) {
                nationService = (NationService) service;
            } else if (service instanceof TreasuryService) {
                treasuryService = (TreasuryService) service;
            } else if (service instanceof TechnologyService) {
                technologyService = (TechnologyService) service;
            } else if (service instanceof DiplomacyService) {
                diplomacyService = (DiplomacyService) service;
            } else if (service instanceof ServiceRegistry) {
                serviceRegistry = (ServiceRegistry) service;
            }
        }
    }

    @Override
    public boolean apply(Player player, Location location) {
        if (player == null) {
            return false;
        }

        try {
            switch (effectType) {
                case ADD_MONEY:
                    return applyAddMoney(player);

                case REMOVE_MONEY:
                    return applyRemoveMoney(player);

                case ADD_RESOURCE:
                    return applyAddResource(player);

                case REMOVE_RESOURCE:
                    return applyRemoveResource(player);

                case MULTIPLY_INCOME:
                    return applyMultiplyIncome(player);

                case TAX_INCREASE:
                    return applyTaxIncrease(player);

                case MARKET_CRASH:
                    return applyMarketCrash();

                case MARKET_BOOM:
                    return applyMarketBoom();

                case TRANSFER_TO_NATION:
                    return applyTransferToNation(player);

                case COLLECT_TAX:
                    return applyCollectTax(player);

                case GIVE_TREATMENT:
                    return applyGiveTreatment(player);

                case APPLY_INFLATION:
                    return applyInflation(player);

                case TECH_BOOM:
                    return applyTechBoom(player);

                case GRANT_TECHNOLOGY:
                    return applyGrantTechnology(player);

                case DIPLOMATIC_BONUS:
                    return applyDiplomaticBonus(player);

                default:
                    return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply economy effect", e);
            return false;
        }
    }

    /**
     * 增加金钱
     */
    private boolean applyAddMoney(Player player) {
        if (economyService != null) {
            economyService.deposit(player.getUniqueId(), BigDecimal.valueOf(value));
            player.sendMessage(String.format("§a你获得了 %.2f 金币！", value));
        } else {
            player.sendMessage(String.format("§a你获得了 %.2f 金币（经济系统未连接）", value));
        }
        return true;
    }

    /**
     * 减少金钱
     */
    private boolean applyRemoveMoney(Player player) {
        UUID playerId = player.getUniqueId();
        if (economyService != null) {
            if (economyService.has(playerId, BigDecimal.valueOf(value))) {
                economyService.withdraw(playerId, BigDecimal.valueOf(value));
                player.sendMessage(String.format("§c你支付了 %.2f 金币！", value));
                return true;
            } else {
                player.sendMessage("§c金币不足！");
                return false;
            }
        } else {
            player.sendMessage(String.format("§c你支付了 %.2f 金币（经济系统未连接）", value));
            return true;
        }
    }

    /**
     * 增加资源
     */
    private boolean applyAddResource(Player player) {
        int amount = (int) value;
        String resourceName = formatResourceName(resourceType);

        // 尝试使用资源服务
        if (resourceService != null && nationService != null) {
            nationService.nationOf(player.getUniqueId()).ifPresent(nation -> {
                boolean success = resourceService.grant(nation.id(), resourceType, amount);
                if (success) {
                    player.sendMessage(String.format("§a你的国家获得了 %d 单位 %s！", amount, resourceName));
                } else {
                    player.sendMessage(String.format("§e资源系统暂时无法添加 %s！", resourceName));
                }
            });
        } else {
            // 备用消息
            player.sendMessage(String.format("§a你获得了 %d 单位 %s（国家系统未连接）！", amount, resourceName));
        }

        // 如果有事件服务，记录持续效果
        if (eventService != null && duration > 0) {
            eventService.addPersistentEffect(player.getUniqueId(), "RESOURCE_BOOST",
                resourceType, value, duration);
        }

        return true;
    }

    /**
     * 减少资源
     */
    private boolean applyRemoveResource(Player player) {
        int amount = (int) value;
        String resourceName = formatResourceName(resourceType);

        // 尝试使用资源服务检查并消耗资源
        if (resourceService != null && nationService != null) {
            boolean success = nationService.nationOf(player.getUniqueId())
                .map(nation -> resourceService.consume(nation.id(), resourceType, amount))
                .orElse(false);

            if (!success) {
                player.sendMessage(String.format("§c%s 资源不足，无法消耗！", resourceName));
                return false;
            }
        }

        player.sendMessage(String.format("§c你失去了 %d 单位 %s！", amount, resourceName));
        return true;
    }

    /**
     * 收入倍增
     */
    private boolean applyMultiplyIncome(Player player) {
        String message;
        if (duration > 0) {
            message = String.format("§6你的收入增加了 %.0f%%，持续 %d 秒！", (value - 1) * 100, duration);
            // 注册持续效果
            if (eventService != null) {
                eventService.addPersistentEffect(player.getUniqueId(), "INCOME_MULTIPLIER",
                    resourceType, value, duration);
            }
        } else {
            message = String.format("§6你的收入增加了 %.0f%%！", (value - 1) * 100);
        }
        player.sendMessage(message);

        // 收入加成通过持续效果系统记录，由收入计算逻辑读取
        // 这里不需要修改 Nation 模型，事件系统会处理加成的应用
        return true;
    }

    /**
     * 税收增加
     */
    private boolean applyTaxIncrease(Player player) {
        String message;
        if (duration > 0) {
            message = String.format("§c税收增加了 %.0f%%，持续 %d 秒！", (value - 1) * 100, duration);
            // 注册持续效果
            if (eventService != null) {
                eventService.addPersistentEffect(player.getUniqueId(), "TAX_INCREASE",
                    resourceType, value, duration);
            }
        } else {
            message = String.format("§c税收增加了 %.0f%%！", (value - 1) * 100);
        }
        player.sendMessage(message);

        // 税收加成通过持续效果系统记录，由税收计算逻辑读取
        return true;
    }

    /**
     * 市场崩溃 - 降低物品价值
     */
    private boolean applyMarketCrash() {
        double dropPercent = (1 - value) * 100;
        Bukkit.broadcast(Component.text("§4市场崩溃！物价下降 " + String.format("%.0f", dropPercent) + "%！", NamedTextColor.DARK_RED));

        // 更新市场交易系数 - 存储到持久化服务
        if (eventService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                eventService.addPersistentEffect(player.getUniqueId(), "MARKET_CRASH",
                    "global", value, duration > 0 ? duration : 3600); // 默认1小时
            }
        }

        return true;
    }

    /**
     * 市场繁荣 - 提高物品价值
     */
    private boolean applyMarketBoom() {
        double risePercent = (value - 1) * 100;
        Bukkit.broadcast(Component.text("§a市场繁荣！物价上涨 " + String.format("%.0f", risePercent) + "%！", NamedTextColor.GREEN));

        // 更新市场交易系数 - 存储到持久化服务
        if (eventService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                eventService.addPersistentEffect(player.getUniqueId(), "MARKET_BOOM",
                    "global", value, duration > 0 ? duration : 3600);
            }
        }

        return true;
    }

    /**
     * 转移资金到国家
     */
    private boolean applyTransferToNation(Player player) {
        double amount = value;
        String nationId = resourceType; // 使用 resourceType 作为国家ID

        if (nationService != null && treasuryService != null && economyService != null) {
            try {
                NationId targetNationId = new NationId(UUID.fromString(nationId));
                nationService.nationById(targetNationId).ifPresentOrElse(nation -> {
                    economyService.withdraw(player.getUniqueId(), BigDecimal.valueOf(amount));
                    treasuryService.deposit(targetNationId, BigDecimal.valueOf(amount));
                    player.sendMessage(String.format("§a你向国家 %s 捐赠了 %.2f 金币！", nation.name(), amount));
                }, () -> {
                    player.sendMessage("§c未找到指定的国家！");
                });
                return true;
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的国家ID格式！");
                return false;
            }
        }

        player.sendMessage(String.format("§6你向国家 %s 捐赠了 %.2f 金币（国家系统未连接）！", nationId, amount));
        return true;
    }

    /**
     * 收取税款
     */
    private boolean applyCollectTax(Player player) {
        double taxRate = value; // value 表示税率
        double taxAmount = 0;

        if (economyService != null) {
            double playerBalance = economyService.getBalance(player.getUniqueId()).doubleValue();
            taxAmount = playerBalance * taxRate;

            if (economyService.has(player.getUniqueId(), BigDecimal.valueOf(taxAmount))) {
                economyService.withdraw(player.getUniqueId(), BigDecimal.valueOf(taxAmount));
                player.sendMessage(String.format("§c税款收取: %.2f 金币（税率 %.0f%%）", taxAmount, taxRate * 100));
            } else {
                player.sendMessage("§c余额不足，无法支付税款！");
                return false;
            }
        } else {
            player.sendMessage(String.format("§c税款收取: %.0f%% 收入（经济系统未连接）", taxRate * 100));
            return false;
        }

        // 将税款转入国家国库
        if (nationService != null && treasuryService != null && taxAmount > 0) {
            var nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                treasuryService.deposit(nationOpt.get().id(), BigDecimal.valueOf(taxAmount));
            }
        }

        return true;
    }

    /**
     * 全服发放补贴
     */
    private boolean applyGiveTreatment(Player player) {
        double amount = value;

        if (economyService != null) {
            // 给所有在线玩家发放补贴
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                economyService.deposit(onlinePlayer.getUniqueId(), BigDecimal.valueOf(amount));
            }
            Bukkit.broadcast(Component.text("§a全服发放补贴: 每位玩家获得 " + String.format("%.2f", amount) + " 金币！", NamedTextColor.GREEN));
        } else {
            Bukkit.broadcast(Component.text("§a全服发放补贴: 每位玩家获得 " + String.format("%.2f", amount) + " 金币（经济系统未连接）", NamedTextColor.YELLOW));
        }

        return true;
    }

    /**
     * 通货膨胀
     */
    private boolean applyInflation(Player player) {
        double inflationRate = value; // 1.0 = 100% 基准

        if (duration > 0) {
            Bukkit.broadcast(Component.text("§e通货膨胀来临！物价上涨 " + String.format("%.0f", (inflationRate - 1) * 100) + "%，持续 " + duration + " 秒！", NamedTextColor.YELLOW));

            // 注册持续效果
            if (eventService != null) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    eventService.addPersistentEffect(onlinePlayer.getUniqueId(), "INFLATION",
                        "global", inflationRate, duration);
                }
            }
        } else {
            Bukkit.broadcast(Component.text("§e通货膨胀来临！物价上涨 " + String.format("%.0f", (inflationRate - 1) * 100) + "%！", NamedTextColor.YELLOW));
        }

        return true;
    }

    /**
     * 科技繁荣 - 提升研发速度/降低科技成本
     */
    private boolean applyTechBoom(Player player) {
        double boostPercent = (value - 1) * 100;
        Bukkit.broadcast(Component.text("§b科技繁荣来临！研发速度提升 " + String.format("%.0f", boostPercent) + "%！", NamedTextColor.AQUA));

        // 注册持续效果
        if (eventService != null) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                eventService.addPersistentEffect(onlinePlayer.getUniqueId(), "TECH_BOOM",
                    resourceType != null ? resourceType : "global", value, duration > 0 ? duration : 7200);
            }
        }

        return true;
    }

    /**
     * 解锁科技 - 直接为玩家所在国家解锁指定科技
     */
    private boolean applyGrantTechnology(Player player) {
        if (nationService == null || technologyService == null) {
            player.sendMessage("§c科技系统未连接，无法解锁科技！");
            return false;
        }

        String techKey = resourceType; // 使用 resourceType 存储科技键
        if (techKey == null || techKey.isEmpty()) {
            player.sendMessage("§c未指定要解锁的科技！");
            return false;
        }

        return nationService.nationOf(player.getUniqueId())
            .map(nation -> {
                boolean success = technologyService.unlock(nation.id(), techKey);
                if (success) {
                    player.sendMessage(String.format("§b你的国家已解锁科技：%s！", techKey));
                } else {
                    player.sendMessage(String.format("§e科技 %s 解锁失败（可能已解锁或无效）！", techKey));
                }
                return success;
            })
            .orElseGet(() -> {
                player.sendMessage("§c你未加入任何国家，无法解锁科技！");
                return false;
            });
    }

    /**
     * 外交红利 - 改善与所有邻国/联盟的关系
     */
    private boolean applyDiplomaticBonus(Player player) {
        if (nationService == null || diplomacyService == null) {
            player.sendMessage("§c外交系统未连接！");
            return false;
        }

        return nationService.nationOf(player.getUniqueId())
            .map(nation -> {
                // 获取所有关系并改善
                var relations = diplomacyService.relationsOf(nation.id());
                int improvedCount = 0;
                for (var snapshot : relations) {
                    // 改善关系（如果是敌对或中立）
                    if (snapshot.relation() != DiplomacyRelation.ALLIED) {
                        diplomacyService.setRelation(nation.id(), snapshot.target(),
                            DiplomacyRelation.NEUTRAL);
                        improvedCount++;
                    }
                }

                String message = improvedCount > 0
                    ? String.format("§a外交红利！你的国家与 %d 个国家的关系得到改善！", improvedCount)
                    : "§a外交红利！但你的国家没有需要改善的关系！";
                player.sendMessage(message);
                return true;
            })
            .orElseGet(() -> {
                player.sendMessage("§c你未加入任何国家！");
                return false;
            });
    }

    /**
     * 格式化资源名称
     */
    private String formatResourceName(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            return "资源";
        }
        String[] parts = resourceType.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    public String getType() {
        return "ECONOMY";
    }

    @Override
    public String getDescription() {
        String typeDesc = switch (effectType) {
            case ADD_MONEY -> "增加金钱";
            case REMOVE_MONEY -> "减少金钱";
            case ADD_RESOURCE -> "增加资源";
            case REMOVE_RESOURCE -> "减少资源";
            case MULTIPLY_INCOME -> "收入倍增";
            case TAX_INCREASE -> "税收增加";
            case MARKET_CRASH -> "市场崩溃";
            case MARKET_BOOM -> "市场繁荣";
            case TRANSFER_TO_NATION -> "转移至国库";
            case COLLECT_TAX -> "收取税款";
            case GIVE_TREATMENT -> "全服补贴";
            case APPLY_INFLATION -> "通货膨胀";
            case TECH_BOOM -> "科技繁荣";
            case GRANT_TECHNOLOGY -> "解锁科技";
            case DIPLOMATIC_BONUS -> "外交红利";
        };

        String resourceInfo = (resourceType != null && !resourceType.isEmpty()) ?
            ", 资源=" + resourceType : "";

        String durationInfo = (duration > 0) ?
            ", 持续=" + duration + "秒" : "";

        return String.format("经济效果 [%s, 值=%.2f%s%s]", typeDesc, value, resourceInfo, durationInfo);
    }

    /**
     * 获取效果类型
     */
    public EffectType getEffectType() {
        return effectType;
    }

    /**
     * 获取效果值
     */
    public double getValue() {
        return value;
    }

    /**
     * 获取资源类型
     */
    public String getResourceType() {
        return resourceType;
    }

    public enum EffectType {
        ADD_MONEY,          // 增加金钱
        REMOVE_MONEY,       // 减少金钱
        ADD_RESOURCE,       // 增加资源
        REMOVE_RESOURCE,    // 减少资源
        MULTIPLY_INCOME,    // 收入倍增
        TAX_INCREASE,       // 税收增加
        MARKET_CRASH,       // 市场崩溃
        MARKET_BOOM,        // 市场繁荣
        TRANSFER_TO_NATION, // 转移至国库
        COLLECT_TAX,        // 收取税款
        GIVE_TREATMENT,     // 全服补贴
        APPLY_INFLATION,    // 通货膨胀
        TECH_BOOM,          // 科技繁荣
        GRANT_TECHNOLOGY,   // 解锁科技
        DIPLOMATIC_BONUS    // 外交红利
    }
}
