package dev.starcore.starcore.clan.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.clan.Clan;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Clan数据库
 * 使用JSON文件存储Clan数据
 */
public class ClanDatabase {

    private final Plugin plugin;
    private final Gson gson;
    private final File dataFolder;

    public ClanDatabase(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = new File(plugin.getDataFolder(), "clans");

        // 创建文件夹
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 保存Clan（异步）
     */
    public CompletableFuture<Boolean> saveClan(Clan clan) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, clan.getId() + ".json");
                ClanData data = convertToData(clan);

                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(data, writer);
                }

                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("保存Clan失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 加载Clan（异步）
     */
    public CompletableFuture<Clan> loadClan(UUID clanId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, clanId + ".json");
                if (!file.exists()) {
                    return null;
                }

                try (FileReader reader = new FileReader(file)) {
                    ClanData data = gson.fromJson(reader, ClanData.class);
                    return convertFromData(data);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("加载Clan失败: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * 删除Clan（异步）
     */
    public CompletableFuture<Boolean> deleteClan(UUID clanId) {
        return CompletableFuture.supplyAsync(() -> {
            File file = new File(dataFolder, clanId + ".json");
            return file.exists() && file.delete();
        });
    }

    /**
     * 加载所有Clan（异步）
     */
    public CompletableFuture<List<Clan>> loadAllClans() {
        return CompletableFuture.supplyAsync(() -> {
            List<Clan> clans = new ArrayList<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        ClanData data = gson.fromJson(reader, ClanData.class);
                        Clan clan = convertFromData(data);
                        if (clan != null) {
                            clans.add(clan);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("加载Clan失败: " + file.getName());
                    }
                }
            }

            return clans;
        });
    }

    /**
     * 保存所有Clan（异步）
     */
    public CompletableFuture<Integer> saveAllClans(Collection<Clan> clans) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (Clan clan : clans) {
                if (saveClan(clan).join()) {
                    saved++;
                }
            }
            return saved;
        });
    }

    // ==================== 数据转换 ====================

    private ClanData convertToData(Clan clan) {
        ClanData data = new ClanData();
        data.id = clan.getId().toString();
        data.tag = clan.getTag();
        data.name = clan.getName();
        data.leader = clan.getLeader().toString();
        data.nationId = clan.getNationId() != null ? clan.getNationId().toString() : null;

        data.members = clan.getMembers().stream()
            .map(UUID::toString)
            .toList();

        data.allies = clan.getAllies().stream()
            .map(UUID::toString)
            .toList();

        data.rivals = clan.getRivals().stream()
            .map(UUID::toString)
            .toList();

        data.kills = clan.getKills();
        data.deaths = clan.getDeaths();
        data.kdr = clan.getKDR();
        data.balance = clan.getBalance();
        data.friendlyFire = clan.isFriendlyFire();
        data.pvpEnabled = clan.isPvpEnabled();
        data.createdTime = clan.getCreatedTime();
        data.lastActiveTime = clan.getLastActiveTime();

        return data;
    }

    private Clan convertFromData(ClanData data) {
        try {
            UUID id = UUID.fromString(data.id);
            UUID leader = UUID.fromString(data.leader);

            Clan clan = new Clan(id, data.tag, data.name, leader);

            // 设置Nation
            if (data.nationId != null) {
                clan.setNationId(UUID.fromString(data.nationId));
            }

            // 添加成员
            for (String memberStr : data.members) {
                UUID memberId = UUID.fromString(memberStr);
                if (!memberId.equals(leader)) { // 领导者已自动添加
                    clan.getMembers().add(memberId);
                }
            }

            // 添加盟友
            for (String allyStr : data.allies) {
                clan.addAlly(UUID.fromString(allyStr));
            }

            // 添加敌对
            for (String rivalStr : data.rivals) {
                clan.addRival(UUID.fromString(rivalStr));
            }

            // 设置统计
            for (int i = 0; i < data.kills; i++) {
                clan.addKill();
            }
            for (int i = 0; i < data.deaths; i++) {
                clan.addDeath();
            }

            clan.deposit(data.balance);
            clan.setFriendlyFire(data.friendlyFire);
            clan.setPvpEnabled(data.pvpEnabled);

            return clan;
        } catch (Exception e) {
            plugin.getLogger().severe("转换Clan数据失败: " + e.getMessage());
            return null;
        }
    }

    // ==================== 数据类 ====================

    private static class ClanData {
        String id;
        String tag;
        String name;
        String leader;
        String nationId;
        List<String> members;
        List<String> allies;
        List<String> rivals;
        int kills;
        int deaths;
        double kdr;
        double balance;
        boolean friendlyFire;
        boolean pvpEnabled;
        long createdTime;
        long lastActiveTime;
    }
}
