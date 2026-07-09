package dev.starcore.starcore.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.PartyService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 社交数据导入导出服务
 *
 * 支持导出格式：JSON
 * 支持完整数据导出（好友、公会、派对）
 */
public final class SocialImportExportService {

    private final JavaPlugin plugin;
    private final FriendService friendService;
    private final GuildService guildService;
    private final PartyService partyService;

    private static final String EXPORT_FOLDER = "social_exports";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    public SocialImportExportService(JavaPlugin plugin, FriendService friendService,
                                     GuildService guildService, PartyService partyService) {
        this.plugin = plugin;
        this.friendService = friendService;
        this.guildService = guildService;
        this.partyService = partyService;
    }

    /**
     * 导出所有社交数据
     *
     * @param fileName 自定义文件名（不含扩展名），为null则使用时间戳
     * @return 导出文件的路径，失败返回null
     */
    public String exportAll(String fileName) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("exportTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
            root.addProperty("version", "1.0");

            // 导出好友数据
            root.add("friends", exportFriends());

            // 导出公会数据
            root.add("guilds", exportGuilds());

            // 导出派对数据
            root.add("parties", exportParties());

            // 导出玩家在线状态
            root.add("playerStatus", exportPlayerStatus());

            // 保存文件
            String name = fileName != null ? fileName : "social_export_" + System.currentTimeMillis();
            Path exportDir = plugin.getDataFolder().toPath().resolve(EXPORT_FOLDER);
            Files.createDirectories(exportDir);
            Path file = exportDir.resolve(name + ".json");

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            plugin.getLogger().info("社交数据已导出到: " + file);
            return file.toString();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "导出社交数据失败", e);
            return null;
        }
    }

    /**
     * 导入所有社交数据
     *
     * @param filePath 导入文件路径
     * @return 成功导入返回true
     */
    public boolean importAll(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                plugin.getLogger().warning("导入文件不存在: " + filePath);
                return false;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(content, JsonObject.class);

            if (root == null) {
                plugin.getLogger().warning("导入文件格式错误: " + filePath);
                return false;
            }

            int friendsCount = 0;
            int guildsCount = 0;
            int partiesCount = 0;

            // 导入好友数据
            if (root.has("friends")) {
                friendsCount = importFriends(root.getAsJsonArray("friends"));
            }

            // 导入公会数据（需要特殊处理）
            if (root.has("guilds")) {
                guildsCount = importGuilds(root.getAsJsonArray("guilds"));
            }

            // 导入派对数据（需要特殊处理）
            if (root.has("parties")) {
                partiesCount = importParties(root.getAsJsonArray("parties"));
            }

            // 导入玩家状态
            if (root.has("playerStatus")) {
                importPlayerStatus(root.getAsJsonArray("playerStatus"));
            }

            // 保存到持久化
            saveAllData();

            plugin.getLogger().info(String.format(
                "社交数据导入完成: %d个好友关系, %d个公会, %d个派对",
                friendsCount, guildsCount, partiesCount
            ));
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "导入社交数据失败", e);
            return false;
        }
    }

    /**
     * 导出好友数据
     */
    private JsonArray exportFriends() {
        JsonArray array = new JsonArray();
        var friendships = friendService.exportFriendships();

        for (var entry : friendships.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("playerUuid", entry.getKey().toString());
            JsonArray friends = new JsonArray();
            for (UUID friendId : entry.getValue()) {
                friends.add(friendId.toString());
            }
            obj.add("friendUuids", friends);
            array.add(obj);
        }

        return array;
    }

    /**
     * 导入好友数据
     */
    private int importFriends(JsonArray array) {
        int count = 0;
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            String playerStr = obj.get("playerUuid").getAsString();
            UUID playerId = UUID.fromString(playerStr);

            if (obj.has("friendUuids")) {
                for (var e : obj.getAsJsonArray("friendUuids")) {
                    try {
                        UUID friendId = UUID.fromString(e.getAsString());
                        friendService.loadFriendship(playerId, friendId);
                        count++;
                    } catch (Exception ex) {
                        plugin.getLogger().warning("导入好友关系失败: " + ex.getMessage());
                    }
                }
            }
        }
        return count;
    }

    /**
     * 导出公会数据
     */
    private JsonArray exportGuilds() {
        JsonArray array = new JsonArray();

        for (var guild : guildService.getAllGuilds()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", guild.getId().toString());
            obj.addProperty("name", guild.getName());
            obj.addProperty("tag", guild.getTag());
            obj.addProperty("leaderUuid", guild.getLeader().toString());
            obj.addProperty("level", guild.getLevel());
            obj.addProperty("experience", guild.getExperience());
            obj.addProperty("createdAt", guild.getCreatedTime());
            if (guild.getDescription() != null) {
                obj.addProperty("description", guild.getDescription());
            }

            JsonArray members = new JsonArray();
            for (UUID memberId : guild.getMembers()) {
                members.add(memberId.toString());
            }
            obj.add("members", members);

            array.add(obj);
        }

        return array;
    }

    /**
     * 导入公会数据
     */
    private int importGuilds(JsonArray array) {
        int count = 0;
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            try {
                // 检查公会名称是否已存在
                String name = obj.get("name").getAsString();
                if (guildService.getGuildByName(name) != null) {
                    plugin.getLogger().warning("公会已存在，跳过: " + name);
                    continue;
                }

                // 创建公会
                UUID leaderId = UUID.fromString(obj.get("leaderUuid").getAsString());
                String tag = obj.has("tag") ? obj.get("tag").getAsString() : "";

                var guild = guildService.createGuild(leaderId, name, tag);

                // 设置等级和经验
                if (obj.has("level")) {
                    guild.setLevel(obj.get("level").getAsInt());
                }
                if (obj.has("experience")) {
                    guild.setExperience(obj.get("experience").getAsInt());
                }
                if (obj.has("description")) {
                    guild.setDescription(obj.get("description").getAsString());
                }

                // 添加其他成员
                if (obj.has("members")) {
                    for (var e : obj.getAsJsonArray("members")) {
                        try {
                            UUID memberId = UUID.fromString(e.getAsString());
                            if (!memberId.equals(leaderId)) {
                                guild.addMember(memberId);
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().warning("导入公会成员失败: " + ex.getMessage());
                        }
                    }
                }

                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("导入公会失败: " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * 导出派对数据
     */
    private JsonArray exportParties() {
        JsonArray array = new JsonArray();

        for (var party : partyService.getAllParties()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", party.getId().toString());
            obj.addProperty("leaderUuid", party.getLeader().toString());
            obj.addProperty("createdAt", party.getCreatedTime());
            obj.addProperty("friendlyFire", party.isFriendlyFire());
            obj.addProperty("expShare", party.isExpShare());
            obj.addProperty("maxMembers", party.getMaxMembers());

            JsonArray members = new JsonArray();
            for (UUID memberId : party.getMembers()) {
                members.add(memberId.toString());
            }
            obj.add("members", members);

            array.add(obj);
        }

        return array;
    }

    /**
     * 导入派对数据
     */
    private int importParties(JsonArray array) {
        int count = 0;
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            try {
                // 创建派对
                UUID leaderId = UUID.fromString(obj.get("leaderUuid").getAsString());
                var party = partyService.createParty(leaderId);

                // 设置属性
                if (obj.has("friendlyFire")) {
                    party.setFriendlyFire(obj.get("friendlyFire").getAsBoolean());
                }
                if (obj.has("expShare")) {
                    party.setExpShare(obj.get("expShare").getAsBoolean());
                }
                if (obj.has("maxMembers")) {
                    party.setMaxMembers(obj.get("maxMembers").getAsInt());
                }

                // 添加其他成员
                if (obj.has("members")) {
                    for (var e : obj.getAsJsonArray("members")) {
                        try {
                            UUID memberId = UUID.fromString(e.getAsString());
                            if (!memberId.equals(leaderId)) {
                                party.addMember(memberId);
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().warning("导入派对成员失败: " + ex.getMessage());
                        }
                    }
                }

                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("导入派对失败: " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * 导出玩家状态
     */
    private JsonArray exportPlayerStatus() {
        JsonArray array = new JsonArray();
        var status = friendService.getAllOnlineStatus();

        for (var entry : status.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("playerUuid", entry.getKey().toString());
            obj.addProperty("isOnline", entry.getValue());
            array.add(obj);
        }

        return array;
    }

    /**
     * 导入玩家状态
     */
    private void importPlayerStatus(JsonArray array) {
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            try {
                UUID playerId = UUID.fromString(obj.get("playerUuid").getAsString());
                boolean isOnline = obj.has("isOnline") && obj.get("isOnline").getAsBoolean();
                friendService.loadOnlineStatus(playerId, isOnline);
            } catch (Exception e) {
                plugin.getLogger().warning("导入玩家状态失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存所有数据到持久化
     */
    private void saveAllData() {
        friendService.saveAllToDatabase();
        guildService.saveGuilds();
        partyService.saveData();
    }

    /**
     * 获取导出目录路径
     */
    public String getExportDirectory() {
        return plugin.getDataFolder().toPath().resolve(EXPORT_FOLDER).toString();
    }

    /**
     * 列出所有导出文件
     */
    public java.util.List<String> listExports() {
        java.util.List<String> files = new java.util.ArrayList<>();
        try {
            Path exportDir = plugin.getDataFolder().toPath().resolve(EXPORT_FOLDER);
            if (Files.exists(exportDir)) {
                Files.list(exportDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> files.add(p.getFileName().toString()));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("列出导出文件失败: " + e.getMessage());
        }
        return files;
    }

    /**
     * 导出玩家数据（单个玩家）
     */
    public String exportPlayerData(UUID playerId) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("playerUuid", playerId.toString());
            root.addProperty("exportTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));

            // 好友
            JsonArray friends = new JsonArray();
            for (UUID friendId : friendService.getFriends(playerId)) {
                friends.add(friendId.toString());
            }
            root.add("friends", friends);

            // 好友请求
            JsonArray requests = new JsonArray();
            for (UUID requesterId : friendService.getFriendRequests(playerId)) {
                requests.add(requesterId.toString());
            }
            root.add("friendRequests", requests);

            // 黑名单
            JsonArray blacklist = new JsonArray();
            for (UUID blockedId : friendService.getBlacklist(playerId)) {
                blacklist.add(blockedId.toString());
            }
            root.add("blacklist", blacklist);

            // 保存文件
            Path exportDir = plugin.getDataFolder().toPath().resolve(EXPORT_FOLDER);
            Files.createDirectories(exportDir);
            Path file = exportDir.resolve("player_" + playerId + "_" + System.currentTimeMillis() + ".json");

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            return file.toString();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "导出玩家数据失败: " + playerId, e);
            return null;
        }
    }

    /**
     * 导入玩家数据
     */
    public boolean importPlayerData(String filePath, UUID playerId) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return false;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(content, JsonObject.class);

            if (root == null || !root.has("playerUuid")) {
                return false;
            }

            // 导入好友
            if (root.has("friends")) {
                for (var e : root.getAsJsonArray("friends")) {
                    try {
                        UUID friendId = UUID.fromString(e.getAsString());
                        friendService.loadFriendship(playerId, friendId);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("导入玩家好友数据失败: " + ex.getMessage());
                    }
                }
            }

            // 导入好友请求
            if (root.has("friendRequests")) {
                for (var e : root.getAsJsonArray("friendRequests")) {
                    try {
                        UUID requesterId = UUID.fromString(e.getAsString());
                        friendService.loadFriendRequest(playerId, requesterId);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("导入好友请求数据失败: " + ex.getMessage());
                    }
                }
            }

            // 导入黑名单
            if (root.has("blacklist")) {
                for (var e : root.getAsJsonArray("blacklist")) {
                    try {
                        UUID blockedId = UUID.fromString(e.getAsString());
                        friendService.loadBlacklistEntry(playerId, blockedId);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("导入黑名单数据失败: " + ex.getMessage());
                    }
                }
            }

            // 保存
            friendService.saveAllToDatabase();
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "导入玩家数据失败: " + playerId, e);
            return false;
        }
    }
}
