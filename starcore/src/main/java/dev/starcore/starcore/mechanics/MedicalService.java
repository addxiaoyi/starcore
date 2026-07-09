package dev.starcore.starcore.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医疗服务
 * 管理治疗流程和医院系统
 */
public class MedicalService {

    private final Plugin plugin;
    private final Map<UUID, Hospital> hospitals;
    private final Map<UUID, Treatment> activeTreatments; // 正在进行的治疗

    private boolean enabled = true;

    public MedicalService(Plugin plugin) {
        this.plugin = plugin;
        this.hospitals = new ConcurrentHashMap<>();
        this.activeTreatments = new ConcurrentHashMap<>();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        // 定时检查治疗进度
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkTreatments, 1200L, 1200L);

        plugin.getLogger().info("医疗服务已启用");
    }

    /**
     * 开始治疗
     */
    public boolean startTreatment(Player player, Disease disease) {
        if (!enabled) return false;

        // 检查是否已在治疗中
        if (activeTreatments.containsKey(player.getUniqueId())) {
            player.sendMessage("§c你已经在接受治疗了！");
            return false;
        }

        // 创建治疗方案
        Treatment treatment = new Treatment(disease.getType());

        // 检查是否有所需物品
        if (!treatment.hasRequiredItems(player)) {
            player.sendMessage("§c你缺少治疗所需的物品！");
            player.sendMessage(treatment.getDescription());
            return false;
        }

        // 查找最近的医院
        Hospital hospital = findNearestHospital(player.getLocation());
        double efficiency = 1.0;

        if (hospital != null && hospital.isInRange(player.getLocation())) {
            // 在医院内治疗，效率更高
            if (hospital.hasAvailableBed()) {
                hospital.admit(player.getUniqueId());
                efficiency = hospital.getTreatmentEfficiency();
                player.sendMessage("§a你已在 " + hospital.getName() + " 住院治疗");
            } else {
                player.sendMessage("§e医院床位已满，在院外接受治疗");
            }
        } else {
            player.sendMessage("§e你不在医院范围内，治疗效果可能较差");
            efficiency = 0.7; // 院外治疗效率降低
        }

        // 消耗物品
        treatment.consumeItems(player);

        // 开始治疗
        activeTreatments.put(player.getUniqueId(), treatment);
        disease.setTreated(true);

        player.sendMessage("§a开始治疗 " + disease.getType().getColoredName());
        player.sendMessage("§7预计持续时间: §f" + treatment.getDuration() + " 分钟");
        player.sendMessage("§7治疗效率: §f" + String.format("%.0f", efficiency * 100) + "%");

        // 定时治疗效果
        final double finalEfficiency = efficiency;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            completeTreatment(player.getUniqueId(), disease, treatment, finalEfficiency);
        }, treatment.getDuration() * 60 * 20L); // 转换为ticks

        return true;
    }

    /**
     * 完成治疗
     */
    private void completeTreatment(UUID playerId, Disease disease, Treatment treatment, double efficiency) {
        activeTreatments.remove(playerId);

        // 应用治疗效果
        int reduction = (int) (treatment.applyTreatment(disease) * efficiency);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            // 出院
            for (Hospital hospital : hospitals.values()) {
                if (hospital.isPatient(playerId)) {
                    hospital.discharge(playerId);
                    break;
                }
            }

            if (disease.isCured()) {
                player.sendMessage("§a§l恭喜！你已完全康复！");
                disease.removeEffects(player);
            } else {
                player.sendMessage("§e治疗完成，病情好转");
                player.sendMessage("§7严重程度降低: §a-" + reduction);
                player.sendMessage("§7当前严重程度: §f" + disease.getSeverity() + "/100");

                if (disease.getSeverity() > 50) {
                    player.sendMessage("§c建议继续接受治疗");
                }
            }
        }
    }

    /**
     * 检查正在进行的治疗
     */
    private void checkTreatments() {
        // 清理离线玩家的治疗
        activeTreatments.keySet().removeIf(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });
    }

    /**
     * 创建医院
     */
    public Hospital createHospital(String name, org.bukkit.Location location, UUID ownerId) {
        UUID id = UUID.randomUUID();
        Hospital hospital = new Hospital(id, name, location, ownerId);
        hospitals.put(id, hospital);
        return hospital;
    }

    /**
     * 删除医院
     */
    public void removeHospital(UUID hospitalId) {
        Hospital hospital = hospitals.remove(hospitalId);
        if (hospital != null) {
            // 让所有患者出院
            for (UUID patientId : hospital.getPatients().keySet()) {
                Player player = Bukkit.getPlayer(patientId);
                if (player != null) {
                    player.sendMessage("§c医院已关闭，你已被强制出院");
                }
            }
        }
    }

    /**
     * 查找最近的医院
     */
    public Hospital findNearestHospital(org.bukkit.Location location) {
        Hospital nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Hospital hospital : hospitals.values()) {
            if (!hospital.getLocation().getWorld().equals(location.getWorld())) {
                continue;
            }

            double distance = hospital.getLocation().distanceSquared(location);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = hospital;
            }
        }

        return nearest;
    }

    /**
     * 获取医院
     */
    public Hospital getHospital(UUID hospitalId) {
        return hospitals.get(hospitalId);
    }

    /**
     * 获取所有医院
     */
    public Collection<Hospital> getAllHospitals() {
        return new ArrayList<>(hospitals.values());
    }

    /**
     * 检查玩家是否在治疗中
     */
    public boolean isTreating(UUID playerId) {
        return activeTreatments.containsKey(playerId);
    }

    /**
     * 取消治疗
     */
    public void cancelTreatment(UUID playerId) {
        activeTreatments.remove(playerId);

        // 出院
        for (Hospital hospital : hospitals.values()) {
            if (hospital.isPatient(playerId)) {
                hospital.discharge(playerId);
                break;
            }
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§c治疗已取消");
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 清理所有治疗
        for (UUID playerId : activeTreatments.keySet()) {
            cancelTreatment(playerId);
        }

        activeTreatments.clear();
        hospitals.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
