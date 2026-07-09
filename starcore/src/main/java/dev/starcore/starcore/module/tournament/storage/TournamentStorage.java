package dev.starcore.starcore.module.tournament.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentConfig;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 锦标赛数据持久化
 * 使用 YAML 格式存储比赛数据
 */
public class TournamentStorage {
    private static final String DATA_FOLDER = "tournaments";
    private static final String ACTIVE_FILE = "active_tournaments.yml";
    private static final String HISTORY_FILE = "history_tournaments.yml";
    private static final java.lang.reflect.Type STRING_OBJECT_MAP = new TypeToken<Map<String, Object>>() {}.getType();
    private static final java.lang.reflect.Type STRING_STRING_MAP = new TypeToken<Map<String, String>>() {}.getType();

    private final JavaPlugin plugin;
    private final Gson gson;
    private final Path dataPath;
    private final Path activeFile;
    private final Path historyFile;

    public TournamentStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = createGson();
        this.dataPath = plugin.getDataFolder().toPath().resolve(DATA_FOLDER);
        this.activeFile = dataPath.resolve(ACTIVE_FILE);
        this.historyFile = dataPath.resolve(HISTORY_FILE);
        ensureDataFolder();
    }

    private void ensureDataFolder() {
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create data folder", e);
        }
    }

    private Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    }

    // ==================== 存储操作 ====================

    /**
     * 保存所有活跃比赛
     */
    public void saveActiveTournaments(Map<String, TournamentData> tournaments) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("saved-at", Instant.now().toString());
            data.put("count", tournaments.size());

            Map<String, String> tournamentJson = new LinkedHashMap<>();
            for (Map.Entry<String, TournamentData> entry : tournaments.entrySet()) {
                tournamentJson.put(entry.getKey(), gson.toJson(entry.getValue()));
            }
            data.put("tournaments", tournamentJson);

            String content = gson.toJson(data);
            Files.writeString(activeFile, content, StandardCharsets.UTF_8);
            plugin.getLogger().info("Saved " + tournaments.size() + " active tournaments");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save active tournaments", e);
        }
    }

    /**
     * 加载所有活跃比赛
     */
    public Map<String, TournamentData> loadActiveTournaments() {
        if (!Files.exists(activeFile)) {
            return new HashMap<>();
        }

        try {
            String content = Files.readString(activeFile, StandardCharsets.UTF_8);
            Map<String, Object> data = gson.fromJson(content, STRING_OBJECT_MAP);

            if (data == null || !data.containsKey("tournaments")) {
                return new HashMap<>();
            }

            Map<String, String> tournamentJson = gson.fromJson(gson.toJson(data.get("tournaments")), STRING_STRING_MAP);
            Map<String, TournamentData> result = new HashMap<>();

            for (Map.Entry<String, String> entry : tournamentJson.entrySet()) {
                try {
                    TournamentData tournamentData = gson.fromJson(entry.getValue(), TournamentData.class);
                    result.put(entry.getKey(), tournamentData);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse tournament " + entry.getKey() + ": " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded " + result.size() + " active tournaments");
            return result;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active tournaments", e);
            return new HashMap<>();
        }
    }

    /**
     * 添加比赛到历史记录
     */
    public void addToHistory(Tournament tournament) {
        try {
            List<String> history = new ArrayList<>();
            if (Files.exists(historyFile)) {
                String content = Files.readString(historyFile, StandardCharsets.UTF_8);
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        history.add(line);
                    }
                }
            }

            // 添加新记录
            TournamentData data = TournamentData.fromTournament(tournament);
            history.add(gson.toJson(data));

            // 保留最近100条
            while (history.size() > 100) {
                history.remove(0);
            }

            Files.writeString(historyFile, String.join("\n", history), StandardCharsets.UTF_8);
            plugin.getLogger().info("Added tournament to history: " + tournament.getId());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add tournament to history", e);
        }
    }

    /**
     * 导出比赛数据到文件
     */
    public void exportTournament(Tournament tournament, String filename) {
        try {
            Path exportPath = dataPath.resolve("export_" + filename + ".json");
            TournamentData data = TournamentData.fromTournament(tournament);
            String content = gson.toJson(data);
            Files.writeString(exportPath, content, StandardCharsets.UTF_8);
            plugin.getLogger().info("Exported tournament to: " + exportPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to export tournament", e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 比赛数据容器（用于序列化）
     */
    public static class TournamentData {
        public String id;
        public String name;
        public String type;
        public String creatorId;
        public String status;
        public String createdAt;
        public String startTime;
        public String winner;
        public int currentRound;
        public int totalRounds;

        public List<String> participants;
        public List<String> aliveParticipants;
        public Map<String, Integer> kills;
        public Map<String, Long> completions;
        public Map<String, List<String>> teams;

        // 配置
        public String configDisplayName;
        public String configDescription;
        public int maxPlayers;
        public int minPlayers;
        public int waitTime;
        public int maxDuration;
        public List<String> tags;
        public double prizePool;

        // 观战位置
        public double spectatorX;
        public double spectatorY;
        public double spectatorZ;
        public String spectatorWorld;

        // 空的构造方法用于 Gson
        public TournamentData() {}

        /**
         * 从 Tournament 创建数据
         */
        public static TournamentData fromTournament(Tournament tournament) {
            TournamentData data = new TournamentData();
            data.id = tournament.getId();
            data.name = tournament.getName();
            data.type = tournament.getType().name();
            data.creatorId = tournament.getCreatorId().toString();
            data.status = tournament.getStatus().name();
            data.createdAt = tournament.getCreatedAt().toString();
            data.startTime = tournament.getStartTime() != null ? tournament.getStartTime().toString() : null;
            data.winner = tournament.getWinner() != null ? tournament.getWinner().toString() : null;
            data.currentRound = tournament.getCurrentRound();
            data.totalRounds = tournament.getTotalRounds();

            data.participants = tournament.getParticipants().stream().map(UUID::toString).toList();
            data.aliveParticipants = tournament.getAliveParticipants().stream().map(UUID::toString).toList();
            data.kills = new HashMap<>();
            tournament.getKills().forEach((k, v) -> data.kills.put(k.toString(), v));
            data.completions = new HashMap<>();
            tournament.getCompletions().forEach((k, v) -> data.completions.put(k.toString(), v));
            data.teams = new HashMap<>();
            tournament.getTeams().forEach((k, v) -> data.teams.put(k, v.stream().map(UUID::toString).toList()));

            data.configDisplayName = tournament.getConfig().displayName();
            data.configDescription = tournament.getConfig().description();
            data.maxPlayers = tournament.getConfig().maxPlayers();
            data.minPlayers = tournament.getConfig().minPlayers();
            data.waitTime = tournament.getConfig().waitTime();
            data.maxDuration = tournament.getConfig().maxDuration();
            data.tags = tournament.getConfig().tags();
            data.prizePool = tournament.getConfig().prizePool();

            // 保存观战位置
            Location specLoc = tournament.getConfig().spectatorLocation();
            if (specLoc != null) {
                data.spectatorX = specLoc.getX();
                data.spectatorY = specLoc.getY();
                data.spectatorZ = specLoc.getZ();
                data.spectatorWorld = specLoc.getWorld().getName();
            }

            return data;
        }

        /**
         * 转换为 Tournament 对象
         */
        public Tournament toTournament() {
            // 重建观战位置
            Location specLoc = null;
            if (spectatorWorld != null) {
                World world = Bukkit.getWorld(spectatorWorld);
                if (world != null) {
                    specLoc = new Location(world, spectatorX, spectatorY, spectatorZ);
                }
            }

            TournamentConfig config = new TournamentConfig(
                configDisplayName,
                configDescription,
                maxPlayers,
                minPlayers,
                waitTime,
                maxDuration,
                tags != null ? tags : List.of(),
                prizePool,
                specLoc,
                List.of()
            );

            Tournament tournament = new Tournament(
                id,
                name,
                TournamentType.valueOf(type),
                UUID.fromString(creatorId),
                config,
                Instant.parse(createdAt)
            );

            tournament.setStatus(TournamentStatus.valueOf(status));
            if (startTime != null) {
                tournament.setStartTime(Instant.parse(startTime));
            }
            if (winner != null) {
                tournament.setWinner(UUID.fromString(winner));
            }
            tournament.setCurrentRound(currentRound);
            tournament.setTotalRounds(totalRounds);

            if (participants != null) {
                participants.forEach(p -> tournament.addParticipant(UUID.fromString(p)));
            }
            if (aliveParticipants != null) {
                tournament.getAliveParticipants().clear();
                aliveParticipants.forEach(p -> tournament.getAliveParticipants().add(UUID.fromString(p)));
            }
            if (kills != null) {
                kills.forEach((k, v) -> tournament.getKills().put(UUID.fromString(k), v));
            }
            if (completions != null) {
                completions.forEach((k, v) -> tournament.getCompletions().put(UUID.fromString(k), v));
            }
            if (teams != null) {
                teams.forEach((k, v) -> tournament.getTeams().put(k, new HashSet<>(v.stream().map(UUID::fromString).toList())));
            }

            return tournament;
        }
    }

    // ==================== GSON 适配器 ====================

    private static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }

    private static class UUIDAdapter extends TypeAdapter<UUID> {
        @Override
        public void write(JsonWriter out, UUID value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public UUID read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return UUID.fromString(in.nextString());
        }
    }
}
