package dev.starcore.starcore.module.army.mercenary;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * 雇佣兵合同编解码器
 * 用于持久化存储合同数据
 */
public final class MercenaryContractCodec {

    private MercenaryContractCodec() {
        // 工具类
    }

    /**
     * 编码合同对象为字符串
     */
    public static String encode(MercenaryContract contract) {
        StringBuilder sb = new StringBuilder();
        sb.append("contractId=").append(contract.contractId());
        sb.append("|mercenaryId=").append(contract.mercenaryId());
        sb.append("|employerId=").append(contract.employerId());
        sb.append("|nationId=").append(contract.employerNationId());
        sb.append("|type=").append(contract.type().key());
        sb.append("|rank=").append(contract.rank().key());
        sb.append("|exp=").append(contract.experience());
        sb.append("|kills=").append(contract.kills());
        sb.append("|deaths=").append(contract.deaths());
        sb.append("|missions=").append(contract.missionsCompleted());
        sb.append("|salary=").append(contract.salary());
        sb.append("|hiredAt=").append(contract.hiredAt().getEpochSecond());
        if (contract.contractExpiresAt() != null) {
            sb.append("|expiresAt=").append(contract.contractExpiresAt().getEpochSecond());
        }
        sb.append("|status=").append(contract.status().key());
        if (contract.lastLocation() != null) {
            sb.append("|loc=").append(locationToString(contract.lastLocation()));
        }
        sb.append("|lastActive=").append(contract.lastActiveAt().getEpochSecond());
        return sb.toString();
    }

    /**
     * 从字符串解码合同对象
     */
    public static MercenaryContract decode(String data) {
        UUID contractId = null;
        UUID mercenaryId = null;
        UUID employerId = null;
        UUID nationId = null;
        MercenaryType type = MercenaryType.INFANTRY;
        MercenaryRank rank = MercenaryRank.RECRUIT;
        int experience = 0;
        int kills = 0;
        int deaths = 0;
        int missions = 0;
        int salary = 0;
        Instant hiredAt = Instant.now();
        Instant expiresAt = null;
        ContractStatus status = ContractStatus.ACTIVE;
        Location lastLocation = null;
        Instant lastActive = Instant.now();

        String[] parts = data.split("\\|");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;

            String key = kv[0].trim();
            String value = kv[1].trim();

            try {
                switch (key) {
                    case "contractId" -> contractId = UUID.fromString(value);
                    case "mercenaryId" -> mercenaryId = UUID.fromString(value);
                    case "employerId" -> employerId = UUID.fromString(value);
                    case "nationId" -> nationId = UUID.fromString(value);
                    case "type" -> type = MercenaryType.fromKey(value);
                    case "rank" -> rank = MercenaryRank.fromKey(value);
                    case "exp" -> experience = Integer.parseInt(value);
                    case "kills" -> kills = Integer.parseInt(value);
                    case "deaths" -> deaths = Integer.parseInt(value);
                    case "missions" -> missions = Integer.parseInt(value);
                    case "salary" -> salary = Integer.parseInt(value);
                    case "hiredAt" -> hiredAt = Instant.ofEpochSecond(Long.parseLong(value));
                    case "expiresAt" -> expiresAt = Instant.ofEpochSecond(Long.parseLong(value));
                    case "status" -> {
                        for (ContractStatus cs : ContractStatus.values()) {
                            if (cs.key().equalsIgnoreCase(value)) {
                                status = cs;
                                break;
                            }
                        }
                    }
                    case "loc" -> lastLocation = stringToLocation(value);
                    case "lastActive" -> lastActive = Instant.ofEpochSecond(Long.parseLong(value));
                }
            } catch (Exception ignored) {
                // 忽略解析错误
            }
        }

        if (contractId == null || mercenaryId == null || employerId == null || nationId == null) {
            throw new IllegalArgumentException("Invalid contract data: missing required fields");
        }

        return new MercenaryContract(
            contractId, mercenaryId, employerId, nationId,
            type, rank, experience, kills, deaths, missions,
            salary, hiredAt, expiresAt, status, lastLocation, lastActive
        );
    }

    /**
     * 从数据库结果集解码合同对象
     */
    public static MercenaryContract fromResultSet(ResultSet rs) throws SQLException {
        return new MercenaryContract(
            UUID.fromString(rs.getString("contract_id")),
            UUID.fromString(rs.getString("mercenary_id")),
            UUID.fromString(rs.getString("employer_id")),
            UUID.fromString(rs.getString("nation_id")),
            MercenaryType.fromKey(rs.getString("type")),
            MercenaryRank.fromKey(rs.getString("rank")),
            rs.getInt("experience"),
            rs.getInt("kills"),
            rs.getInt("deaths"),
            rs.getInt("missions_completed"),
            rs.getInt("salary"),
            Instant.ofEpochSecond(rs.getLong("hired_at")),
            rs.getLong("expires_at") > 0 ? Instant.ofEpochSecond(rs.getLong("expires_at")) : null,
            ContractStatus.valueOf(rs.getString("status").toUpperCase()),
            stringToLocation(rs.getString("last_location")),
            Instant.ofEpochSecond(rs.getLong("last_active"))
        );
    }

    private static String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static Location stringToLocation(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            String[] parts = data.split(":");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    return new Location(world, x, y, z);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}