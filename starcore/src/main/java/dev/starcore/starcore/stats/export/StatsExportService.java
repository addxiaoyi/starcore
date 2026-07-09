package dev.starcore.starcore.stats.export;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 统计数据导出服务
 *
 * 支持多种格式导出：
 * - JSON: 结构化数据，便于程序处理
 * - CSV: 表格数据，便于Excel处理
 * - HTML: 网页格式，便于浏览
 * - TXT: 纯文本格式，便于打印
 * - ZIP: 批量导出压缩包
 */
public class StatsExportService {

    private final Plugin plugin;
    private final RankingService rankingService;
    private final Path exportDirectory;

    // 导出配置
    private static final String EXPORT_FOLDER = "exports";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public StatsExportService(Plugin plugin, RankingService rankingService) {
        this.plugin = plugin;
        this.rankingService = rankingService;
        this.exportDirectory = plugin.getDataFolder().toPath().resolve(EXPORT_FOLDER);
        initExportDirectory();
    }

    private void initExportDirectory() {
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create export directory: " + e.getMessage());
        }
    }

    /**
     * 导出玩家统计为 JSON
     */
    public CompletableFuture<Path> exportToJson(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new LinkedHashMap<>();

            // 玩家基本信息
            data.put("playerId", playerId.toString());
            data.put("playerName", playerName);
            data.put("exportTime", LocalDateTime.now().toString());

            // 统计数据
            Map<String, Object> stats = new LinkedHashMap<>();
            for (RankPeriod period : RankPeriod.values()) {
                Map<String, Object> periodStats = new LinkedHashMap<>();
                periodStats.put("kills", rankingService.getKillCount(playerId, period).join());
                periodStats.put("deaths", rankingService.getDeathCount(playerId, period).join());
                periodStats.put("assists", rankingService.getAssistCount(playerId, period).join());
                periodStats.put("onlineTime", rankingService.getOnlineTime(playerId, period).join());
                periodStats.put("killRank", rankingService.getKillRank(playerId, period).join());
                periodStats.put("kdrRank", rankingService.getKDRatioRank(playerId, period).join());
                periodStats.put("kdr", rankingService.getKDRatio(playerId));
                periodStats.put("kda", rankingService.getKDARatio(playerId));
                stats.put(period.name(), periodStats);
            }
            data.put("statistics", stats);

            String json = toJson(data);
            return saveFile("json", playerName, json);
        });
    }

    /**
     * 导出排行榜为 JSON
     */
    public CompletableFuture<Path> exportLeaderboardToJson(String statType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new LinkedHashMap<>();

            data.put("statType", statType);
            data.put("limit", limit);
            data.put("exportTime", LocalDateTime.now().toString());

            List<Map<String, Object>> leaderboard = new ArrayList<>();
            for (RankPeriod period : RankPeriod.values()) {
                List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, limit, period);

                Map<String, Object> periodData = new LinkedHashMap<>();
                periodData.put("period", period.name());
                periodData.put("displayName", period.getDisplayName());

                List<Map<String, Object>> players = new ArrayList<>();
                for (RankingService.TopPlayerData player : topPlayers) {
                    Map<String, Object> playerData = new LinkedHashMap<>();
                    playerData.put("rank", player.position());
                    playerData.put("playerId", player.playerId().toString());
                    playerData.put("value", player.value());
                    players.add(playerData);
                }
                periodData.put("players", players);
                leaderboard.add(periodData);
            }
            data.put("leaderboard", leaderboard);

            String json = toJson(data);
            return saveFile("json", "leaderboard_" + statType, json);
        });
    }

    /**
     * 导出玩家统计为 CSV
     */
    public CompletableFuture<Path> exportToCsv(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder csv = new StringBuilder();

            // 表头
            csv.append("PlayerID,PlayerName,Period,Kills,Deaths,Assists,OnlineTime,KillRank,KDRRank\n");

            // 数据行
            for (RankPeriod period : RankPeriod.values()) {
                csv.append(playerId.toString()).append(",");
                csv.append(escapeCsv(playerName)).append(",");
                csv.append(period.name()).append(",");
                csv.append(rankingService.getKillCount(playerId, period).join()).append(",");
                csv.append(rankingService.getDeathCount(playerId, period).join()).append(",");
                csv.append(rankingService.getAssistCount(playerId, period).join()).append(",");
                csv.append(rankingService.getOnlineTime(playerId, period).join()).append(",");
                csv.append(rankingService.getKillRank(playerId, period).join()).append(",");
                csv.append(rankingService.getKDRatioRank(playerId, period).join()).append("\n");
            }

            return saveFile("csv", playerName, csv.toString());
        });
    }

    /**
     * 导出排行榜为 CSV
     */
    public CompletableFuture<Path> exportLeaderboardToCsv(String statType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder csv = new StringBuilder();

            // 表头
            csv.append("Period,Rank,PlayerID,Value\n");

            // 数据行
            for (RankPeriod period : RankPeriod.values()) {
                List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, limit, period);
                for (RankingService.TopPlayerData player : topPlayers) {
                    csv.append(period.name()).append(",");
                    csv.append(player.position()).append(",");
                    csv.append(player.playerId().toString()).append(",");
                    csv.append(player.value()).append("\n");
                }
            }

            return saveFile("csv", "leaderboard_" + statType, csv.toString());
        });
    }

    /**
     * 导出统计报告为 HTML
     */
    public CompletableFuture<Path> exportToHtml(String reportTitle, Map<String, Object> content) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder html = new StringBuilder();

            html.append("<!DOCTYPE html>\n");
            html.append("<html lang=\"zh-CN\">\n");
            html.append("<head>\n");
            html.append("  <meta charset=\"UTF-8\">\n");
            html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("  <title>").append(escapeHtml(reportTitle)).append("</title>\n");
            html.append("  <style>\n");
            html.append(getDefaultCss());
            html.append("  </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("  <div class=\"container\">\n");
            html.append("    <h1>").append(escapeHtml(reportTitle)).append("</h1>\n");
            html.append("    <p class=\"timestamp\">导出时间: ").append(LocalDateTime.now()).append("</p>\n");

            // 生成内容
            for (Map.Entry<String, Object> entry : content.entrySet()) {
                String section = entry.getKey();
                Object data = entry.getValue();

                html.append("    <div class=\"section\">\n");
                html.append("      <h2>").append(escapeHtml(section)).append("</h2>\n");

                if (data instanceof List<?> list) {
                    html.append("      <table>\n");
                    html.append("        <thead><tr><th>排名</th><th>玩家</th><th>数值</th></tr></thead>\n");
                    html.append("        <tbody>\n");
                    for (Object item : list) {
                        if (item instanceof RankingService.TopPlayerData player) {
                            html.append("          <tr>\n");
                            html.append("            <td>").append(player.position()).append("</td>\n");
                            html.append("            <td>").append(player.playerId().toString().substring(0, 8)).append("</td>\n");
                            html.append("            <td>").append(player.value()).append("</td>\n");
                            html.append("          </tr>\n");
                        }
                    }
                    html.append("        </tbody>\n");
                    html.append("      </table>\n");
                } else if (data instanceof Map<?, ?> map) {
                    html.append("      <dl>\n");
                    for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                        html.append("        <dt>").append(escapeHtml(String.valueOf(mapEntry.getKey()))).append("</dt>\n");
                        html.append("        <dd>").append(escapeHtml(String.valueOf(mapEntry.getValue()))).append("</dd>\n");
                    }
                    html.append("      </dl>\n");
                } else {
                    html.append("      <p>").append(escapeHtml(String.valueOf(data))).append("</p>\n");
                }

                html.append("    </div>\n");
            }

            html.append("  </div>\n");
            html.append("</body>\n");
            html.append("</html>\n");

            return saveFile("html", "report_" + System.currentTimeMillis(), html.toString());
        });
    }

    /**
     * 导出完整统计报告为 TXT
     */
    public CompletableFuture<Path> exportToTxt(String reportTitle, String content) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder txt = new StringBuilder();
            txt.append("=".repeat(60)).append("\n");
            txt.append(reportTitle).append("\n");
            txt.append("导出时间: ").append(LocalDateTime.now()).append("\n");
            txt.append("=".repeat(60)).append("\n\n");
            txt.append(content);
            txt.append("\n\n").append("=".repeat(60)).append("\n");
            txt.append("StarCore Statistics Export\n");

            return saveFile("txt", "report_" + System.currentTimeMillis(), txt.toString());
        });
    }

    /**
     * 批量导出为 ZIP
     */
    public CompletableFuture<Path> exportAllToZip() {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
            Path zipPath = exportDirectory.resolve("export_" + timestamp + ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                // 导出所有排行榜
                for (String statType : Arrays.asList("kills", "deaths", "kdratio", "playtime")) {
                    // JSON
                    String json = toJson(generateLeaderboardData(statType));
                    addToZip(zos, "leaderboard_" + statType + ".json", json);

                    // CSV
                    String csv = generateLeaderboardCsv(statType);
                    addToZip(zos, "leaderboard_" + statType + ".csv", csv);
                }

                // HTML 报告
                Map<String, Object> htmlContent = new LinkedHashMap<>();
                for (String statType : Arrays.asList("kills", "deaths", "kdratio", "playtime")) {
                    List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME);
                    htmlContent.put(statType + " Top 10", topPlayers);
                }
                String html = generateHtmlReport("StarCore 全局统计报告", htmlContent);
                addToZip(zos, "full_report.html", html);

            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create ZIP export: " + e.getMessage());
                throw new RuntimeException(e);
            }

            return zipPath;
        });
    }

    /**
     * 获取导出文件列表
     */
    public List<ExportFileInfo> listExports() {
        List<ExportFileInfo> exports = new ArrayList<>();

        try {
            Files.list(exportDirectory).forEach(path -> {
                String filename = path.getFileName().toString();
                long size;
                try {
                    size = Files.size(path);
                } catch (IOException e) {
                    size = 0;
                }

                exports.add(new ExportFileInfo(
                    filename,
                    path,
                    size,
                    filename.endsWith(".json") ? ExportFormat.JSON :
                    filename.endsWith(".csv") ? ExportFormat.CSV :
                    filename.endsWith(".html") ? ExportFormat.HTML :
                    filename.endsWith(".zip") ? ExportFormat.ZIP :
                    ExportFormat.TXT
                ));
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to list exports: " + e.getMessage());
        }

        // 按时间倒序
        exports.sort((a, b) -> -a.path().compareTo(b.path()));
        return exports;
    }

    /**
     * 删除导出文件
     */
    public boolean deleteExport(Path path) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete export: " + e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> generateLeaderboardData(String statType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statType", statType);
        data.put("exportTime", LocalDateTime.now().toString());

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (RankPeriod period : RankPeriod.values()) {
            List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, 100, period);
            Map<String, Object> periodData = new LinkedHashMap<>();
            periodData.put("period", period.name());
            periodData.put("displayName", period.getDisplayName());
            periodData.put("players", topPlayers);
            leaderboard.add(periodData);
        }
        data.put("leaderboard", leaderboard);
        return data;
    }

    private String generateLeaderboardCsv(String statType) {
        StringBuilder csv = new StringBuilder();
        csv.append("Period,Rank,PlayerID,Value\n");

        for (RankPeriod period : RankPeriod.values()) {
            List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, 100, period);
            for (RankingService.TopPlayerData player : topPlayers) {
                csv.append(period.name()).append(",");
                csv.append(player.position()).append(",");
                csv.append(player.playerId().toString()).append(",");
                csv.append(player.value()).append("\n");
            }
        }
        return csv.toString();
    }

    private String generateHtmlReport(String title, Map<String, Object> content) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        html.append("  <style>").append(getDefaultCss()).append("</style>\n");
        html.append("</head>\n<body>\n  <div class=\"container\">\n");
        html.append("    <h1>").append(escapeHtml(title)).append("</h1>\n");
        html.append("    <p class=\"timestamp\">导出时间: ").append(LocalDateTime.now()).append("</p>\n");

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            html.append("    <div class=\"section\">\n");
            html.append("      <h2>").append(escapeHtml(entry.getKey())).append("</h2>\n");
            if (entry.getValue() instanceof List<?> list) {
                html.append("      <table><thead><tr><th>排名</th><th>玩家</th><th>数值</th></tr></thead><tbody>\n");
                for (Object item : list) {
                    if (item instanceof RankingService.TopPlayerData p) {
                        html.append("<tr><td>").append(p.position()).append("</td>");
                        html.append("<td>").append(p.playerId().toString().substring(0, 8)).append("</td>");
                        html.append("<td>").append(p.value()).append("</td></tr>\n");
                    }
                }
                html.append("</tbody></table>\n");
            }
            html.append("    </div>\n");
        }
        html.append("  </div>\n</body>\n</html>");
        return html.toString();
    }

    private Path saveFile(String extension, String name, String content) {
        String safeName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = safeName + "_" + System.currentTimeMillis() + "." + extension;
        Path path = exportDirectory.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save export file: " + e.getMessage());
            throw new RuntimeException(e);
        }

        return path;
    }

    private void addToZip(ZipOutputStream zos, String entryName, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String toJson(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ > 0) sb.append(",\n");
                sb.append("  \"").append(entry.getKey()).append("\": ");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("\n}");
            return sb.toString();
        } else if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int count = 0;
            for (Object item : list) {
                if (count++ > 0) sb.append(",\n");
                sb.append(toJson(item));
            }
            sb.append("\n]");
            return sb.toString();
        } else if (obj instanceof String str) {
            return "\"" + escapeJson(str) + "\"";
        } else {
            return String.valueOf(obj);
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String escapeCsv(String str) {
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private String escapeHtml(String str) {
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }

    private String getDefaultCss() {
        return """
            body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
            .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            h1 { color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; }
            .timestamp { color: #666; font-size: 0.9em; }
            .section { margin: 20px 0; }
            h2 { color: #4CAF50; margin-top: 20px; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
            th { background: #4CAF50; color: white; }
            tr:hover { background: #f5f5f5; }
            dl { margin: 10px 0; }
            dt { font-weight: bold; color: #333; }
            dd { margin: 5px 0 10px 20px; }
            """;
    }

    // ==================== 内部类 ====================

    public enum ExportFormat {
        JSON, CSV, HTML, TXT, ZIP
    }

    public record ExportFileInfo(
        String filename,
        Path path,
        long sizeBytes,
        ExportFormat format
    ) {}
}
