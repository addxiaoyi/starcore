package dev.starcore.starcore.title;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.ranking.RankingService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 称号显示服务
 * 负责在Tab列表、玩家名字、聊天等位置显示称号
 */
public class TitleDisplayService {
    private final Plugin plugin;
    private final TitleService titleService;
    private final TitleDisplayConfig config;
    private final Logger logger;
    private NationService nationService;
    private RankingService rankingService;

    // 全息投影适配器
    private HologramAdapter hologramAdapter;

    // 玩家显示缓存
    private final Map<UUID, String> displayCache = new ConcurrentHashMap<>();

    public TitleDisplayService(Plugin plugin, TitleService titleService, TitleDisplayConfig config) {
        this.plugin = plugin;
        this.titleService = titleService;
        this.config = config;
        this.logger = plugin.getLogger();

        // 检测并初始化全息投影支持
        initializeHologramSupport();
    }

    /**
     * 设置国家服务（用于获取玩家所在国家名称）
     */
    public void setNationService(NationService nationService) {
        this.nationService = nationService;
    }

    /**
     * 设置排名服务（用于获取玩家排名）
     */
    public void setRankingService(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * 初始化全息投影支持
     */
    private void initializeHologramSupport() {
        if (Bukkit.getPluginManager().getPlugin("HolographicDisplays") != null) {
            logger.info("Detected HolographicDisplays, enabling hologram support");
            // hologramAdapter = new HolographicDisplaysAdapter(plugin);
        } else if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            logger.info("Detected DecentHolograms, enabling hologram support");
            // hologramAdapter = new DecentHologramsAdapter(plugin);
        } else {
            logger.info("No hologram plugin detected, hologram display disabled");
        }
    }

    /**
     * 更新玩家Tab列表显示
     */
    public void updateTabDisplay(Player player) {
        if (!config.getTabConfig().isEnabled()) {
            return;
        }

        // 检查player是否仍然在线，避免异步操作时player已离线
        if (!player.isOnline()) {
            return;
        }

        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            // 在主线程中执行Scoreboard操作
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                String prefix = "";
                String suffix = "";

                // 获取装备的称号
                if (data.getEquippedTitle().isPresent()) {
                    String titleId = data.getEquippedTitle().get();
                    titleService.getTitle(titleId).ifPresent(title -> {
                        String format = config.getTabConfig().getPrefixFormat();
                        if (format != null && !format.isEmpty()) {
                            displayCache.put(player.getUniqueId(), title.getPlainText());
                        }
                    });
                }

                // 使用Scoreboard Team设置前缀
                Scoreboard scoreboard = player.getScoreboard();
                if (scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
                    scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
                    player.setScoreboard(scoreboard);
                }

                String teamName = "title_" + player.getName().substring(0, Math.min(player.getName().length(), 10));
                Team team = scoreboard.getTeam(teamName);
                if (team == null) {
                    team = scoreboard.registerNewTeam(teamName);
                }

                team.addEntry(player.getName());

                // 应用占位符
                TitleDisplayConfig.TitlePlaceholders placeholders = buildPlaceholders(player, data);
                prefix = config.applyPlaceholders(config.getTabConfig().getPrefixFormat(), placeholders);
                suffix = config.applyPlaceholders(config.getTabConfig().getSuffixFormat(), placeholders);

                // 限制长度（Minecraft限制）
                if (prefix.length() > 64) {
                    prefix = prefix.substring(0, 64);
                }
                if (suffix.length() > 64) {
                    suffix = suffix.substring(0, 64);
                }

                team.setPrefix(prefix);
                team.setSuffix(suffix);
            });
        });
    }

    /**
     * 更新玩家名字前缀
     */
    public void updateNameDisplay(Player player) {
        if (!config.getNamePrefixConfig().isEnabled()) {
            return;
        }

        // 检查player是否仍然在线
        if (!player.isOnline()) {
            return;
        }

        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            // 在主线程中执行setDisplayName操作
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                TitleDisplayConfig.TitlePlaceholders placeholders = buildPlaceholders(player, data);
                String format = config.applyPlaceholders(config.getNamePrefixConfig().getFormat(), placeholders);

                // 设置玩家显示名
                player.setDisplayName(format + player.getName());
            });
        });
    }

    /**
     * 更新玩家全息投影
     */
    public void updateHologramDisplay(Player player) {
        if (!config.getHologramConfig().isEnabled() || hologramAdapter == null) {
            return;
        }

        // 检查player是否仍然在线
        if (!player.isOnline()) {
            return;
        }

        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            // 在主线程中执行全息投影操作
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || hologramAdapter == null) {
                    return;
                }

                TitleDisplayConfig.TitlePlaceholders placeholders = buildPlaceholders(player, data);

                // 应用占位符到每一行
                String[] lines = config.getHologramConfig().getLines().stream()
                    .map(line -> config.applyPlaceholders(line, placeholders))
                    .toArray(String[]::new);

                // 使用全息投影适配器显示
                hologramAdapter.updateHologram(player, lines, config.getHologramConfig().getOffset());
            });
        });
    }

    /**
     * 移除玩家全息投影
     */
    public void removeHologramDisplay(Player player) {
        // 在主线程中移除全息投影
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hologramAdapter != null) {
                hologramAdapter.removeHologram(player);
            }
        });
    }

    /**
     * 更新所有显示
     */
    public void updateAllDisplays(Player player) {
        updateTabDisplay(player);
        updateNameDisplay(player);
        updateHologramDisplay(player);
    }

    /**
     * 刷新所有在线玩家的显示
     */
    public void refreshAllPlayers() {
        Bukkit.getOnlinePlayers().forEach(this::updateAllDisplays);
    }

    /**
     * 获取玩家的聊天前缀
     */
    public String getChatPrefix(Player player) {
        if (!config.getChatConfig().isEnabled()) {
            return "";
        }

        // 从缓存获取
        String cached = displayCache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }

        // 异步加载
        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            TitleDisplayConfig.TitlePlaceholders placeholders = buildPlaceholders(player, data);
            String prefix = config.applyPlaceholders(config.getChatConfig().getFormat(), placeholders);
            displayCache.put(player.getUniqueId(), prefix);
        });

        return "";
    }

    /**
     * 构建占位符数据
     */
    private TitleDisplayConfig.TitlePlaceholders buildPlaceholders(Player player, PlayerTitle data) {
        String titleText = "";
        String badgeText = "";

        // 获取称号文本
        if (data.getEquippedTitle().isPresent()) {
            String titleId = data.getEquippedTitle().get();
            titleText = titleService.getTitle(titleId)
                .map(Title::getPlainText)
                .orElse("");
        }

        // 获取徽章文本
        if (data.getEquippedBadge().isPresent()) {
            String badgeId = data.getEquippedBadge().get();
            badgeText = titleService.getBadge(badgeId)
                .map(Badge::getFormatted)
                .orElse("");
        }

        // 获取玩家所在国家名称
        String nationName = "无国家";
        if (nationService != null) {
            Optional<dev.starcore.starcore.module.nation.model.Nation> nation =
                nationService.nationOf(player.getUniqueId());
            if (nation.isPresent()) {
                nationName = nation.get().name();
            }
        }

        // 获取玩家排名
        String rankText = getPlayerRankText(player.getUniqueId());

        return new TitleDisplayConfig.TitlePlaceholders(
            titleText,
            badgeText,
            nationName,
            rankText,
            player.getName()
        );
    }

    /**
     * 获取玩家排名文本
     */
    private String getPlayerRankText(UUID playerId) {
        if (rankingService == null) {
            return "无排名";
        }

        try {
            int rank = rankingService.getKillRank(playerId, dev.starcore.starcore.ranking.RankPeriod.ALLTIME).join();
            if (rank > 0) {
                return "#" + rank;
            }
            return "无排名";
        } catch (Exception e) {
            logger.warning("Failed to get rank for player " + playerId + ": " + e.getMessage());
            return "无排名";
        }
    }

    /**
     * 清理玩家缓存
     */
    public void clearCache(UUID playerId) {
        displayCache.remove(playerId);
    }

    /**
     * 全息投影适配器接口
     */
    public interface HologramAdapter {
        void updateHologram(Player player, String[] lines, double offset);
        void removeHologram(Player player);
    }
}
