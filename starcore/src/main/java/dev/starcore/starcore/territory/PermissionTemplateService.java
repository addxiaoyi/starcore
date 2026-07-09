package dev.starcore.starcore.territory;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 权限模板服务
 * 管理权限模板的创建、应用和预设
 */
public class PermissionTemplateService {

    private final JavaPlugin plugin;

    // 模板存储 - ID -> Template
    private final Map<UUID, PermissionTemplate> templates = new ConcurrentHashMap<>();

    // 名称索引 - Name -> TemplateID
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    // 创建者索引 - CreatorID -> Set<TemplateID>
    private final Map<UUID, Set<UUID>> creatorIndex = new ConcurrentHashMap<>();

    // 预设模板
    private final Map<TemplatePreset, PermissionTemplate> presetTemplates = new EnumMap<>(TemplatePreset.class);

    public PermissionTemplateService(JavaPlugin plugin) {
        this.plugin = plugin;
        initializePresets();
    }

    // ==================== 初始化 ====================

    /**
     * 初始化预设模板
     */
    private void initializePresets() {
        for (TemplatePreset preset : TemplatePreset.values()) {
            PermissionTemplate template = preset.createTemplate();
            presetTemplates.put(preset, template);
            templates.put(template.getId(), template);
            nameIndex.put(template.getName().toLowerCase(), template.getId());
            plugin.getLogger().info("加载预设模板: " + preset.getDisplayName());
        }
    }

    // ==================== 创建和删除 ====================

    /**
     * 创建自定义模板
     */
    public PermissionTemplate createTemplate(String name, String description, UUID creatorId) {
        // 检查名称冲突
        if (nameIndex.containsKey(name.toLowerCase())) {
            plugin.getLogger().warning("模板名称已存在: " + name);
            return null;
        }

        PermissionTemplate template = new PermissionTemplate(name, description, creatorId, false);

        // 存储模板
        templates.put(template.getId(), template);
        nameIndex.put(name.toLowerCase(), template.getId());
        creatorIndex.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet())
            .add(template.getId());

        plugin.getLogger().info("创建模板: " + name + " (创建者: " + creatorId + ")");
        return template;
    }

    /**
     * 删除模板
     */
    public boolean deleteTemplate(UUID templateId) {
        PermissionTemplate template = templates.get(templateId);
        if (template == null) {
            return false;
        }

        // 不能删除预设模板
        if (template.isPreset()) {
            plugin.getLogger().warning("不能删除预设模板: " + template.getName());
            return false;
        }

        // 移除模板
        templates.remove(templateId);
        nameIndex.remove(template.getName().toLowerCase());

        // 移除索引
        if (template.getCreatorId() != null) {
            Set<UUID> creatorTemplates = creatorIndex.get(template.getCreatorId());
            if (creatorTemplates != null) {
                creatorTemplates.remove(templateId);
            }
        }

        plugin.getLogger().info("删除模板: " + template.getName());
        return true;
    }

    // ==================== 查询方法 ====================

    /**
     * 根据ID获取模板
     */
    public PermissionTemplate getTemplate(UUID id) {
        return templates.get(id);
    }

    /**
     * 根据名称获取模板
     */
    public PermissionTemplate getTemplateByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase());
        return id != null ? templates.get(id) : null;
    }

    /**
     * 获取预设模板
     */
    public PermissionTemplate getPresetTemplate(TemplatePreset preset) {
        return presetTemplates.get(preset);
    }

    /**
     * 获取创建者的所有模板
     */
    public List<PermissionTemplate> getTemplatesByCreator(UUID creatorId) {
        Set<UUID> templateIds = creatorIndex.get(creatorId);
        if (templateIds == null) {
            return Collections.emptyList();
        }

        return templateIds.stream()
            .map(templates::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有模板
     */
    public Collection<PermissionTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * 获取所有预设模板
     */
    public Collection<PermissionTemplate> getPresetTemplates() {
        return Collections.unmodifiableCollection(presetTemplates.values());
    }

    /**
     * 获取所有自定义模板
     */
    public List<PermissionTemplate> getCustomTemplates() {
        return templates.values().stream()
            .filter(t -> !t.isPreset())
            .collect(Collectors.toList());
    }

    // ==================== 应用模板 ====================

    /**
     * 应用模板到领土
     */
    public void applyTemplate(UUID templateId, Territory territory) {
        PermissionTemplate template = templates.get(templateId);
        if (template == null) {
            plugin.getLogger().warning("模板不存在: " + templateId);
            return;
        }

        template.applyToTerritory(territory);
        plugin.getLogger().info("应用模板 " + template.getName() + " 到领土 " + territory.getName());
    }

    /**
     * 应用模板到领土（通过名称）
     */
    public void applyTemplate(String templateName, Territory territory) {
        PermissionTemplate template = getTemplateByName(templateName);
        if (template == null) {
            plugin.getLogger().warning("模板不存在: " + templateName);
            return;
        }

        template.applyToTerritory(territory);
        plugin.getLogger().info("应用模板 " + template.getName() + " 到领土 " + territory.getName());
    }

    /**
     * 应用预设模板到领土
     */
    public void applyPreset(TemplatePreset preset, Territory territory) {
        PermissionTemplate template = presetTemplates.get(preset);
        if (template == null) {
            plugin.getLogger().warning("预设模板不存在: " + preset);
            return;
        }

        template.applyToTerritory(territory);
        plugin.getLogger().info("应用预设模板 " + preset.getDisplayName() + " 到领土 " + territory.getName());
    }

    /**
     * 应用模板到子区域
     */
    public void applyTemplate(UUID templateId, SubRegion subRegion) {
        PermissionTemplate template = templates.get(templateId);
        if (template == null) {
            plugin.getLogger().warning("模板不存在: " + templateId);
            return;
        }

        template.applyToSubRegion(subRegion);
        plugin.getLogger().info("应用模板 " + template.getName() + " 到子区域 " + subRegion.getName());
    }

    /**
     * 应用预设模板到子区域
     */
    public void applyPreset(TemplatePreset preset, SubRegion subRegion) {
        PermissionTemplate template = presetTemplates.get(preset);
        if (template == null) {
            plugin.getLogger().warning("预设模板不存在: " + preset);
            return;
        }

        template.applyToSubRegion(subRegion);
        plugin.getLogger().info("应用预设模板 " + preset.getDisplayName() + " 到子区域 " + subRegion.getName());
    }

    // ==================== 从现有配置创建模板 ====================

    /**
     * 从领土创建模板
     */
    public PermissionTemplate createFromTerritory(Territory territory, String name,
                                                  String description, UUID creatorId) {
        // 检查名称冲突
        if (nameIndex.containsKey(name.toLowerCase())) {
            plugin.getLogger().warning("模板名称已存在: " + name);
            return null;
        }

        PermissionTemplate template = PermissionTemplate.fromTerritory(
            territory, name, description, creatorId
        );

        // 存储模板
        templates.put(template.getId(), template);
        nameIndex.put(name.toLowerCase(), template.getId());
        creatorIndex.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet())
            .add(template.getId());

        plugin.getLogger().info("从领土 " + territory.getName() + " 创建模板: " + name);
        return template;
    }

    /**
     * 从子区域创建模板
     */
    public PermissionTemplate createFromSubRegion(SubRegion subRegion, String name,
                                                  String description, UUID creatorId) {
        // 检查名称冲突
        if (nameIndex.containsKey(name.toLowerCase())) {
            plugin.getLogger().warning("模板名称已存在: " + name);
            return null;
        }

        PermissionTemplate template = PermissionTemplate.fromSubRegion(
            subRegion, name, description, creatorId
        );

        // 存储模板
        templates.put(template.getId(), template);
        nameIndex.put(name.toLowerCase(), template.getId());
        creatorIndex.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet())
            .add(template.getId());

        plugin.getLogger().info("从子区域 " + subRegion.getName() + " 创建模板: " + name);
        return template;
    }

    // ==================== 克隆和重命名 ====================

    /**
     * 克隆模板
     */
    public PermissionTemplate cloneTemplate(UUID templateId, String newName, UUID creatorId) {
        PermissionTemplate original = templates.get(templateId);
        if (original == null) {
            plugin.getLogger().warning("模板不存在: " + templateId);
            return null;
        }

        // 检查名称冲突
        if (nameIndex.containsKey(newName.toLowerCase())) {
            plugin.getLogger().warning("模板名称已存在: " + newName);
            return null;
        }

        PermissionTemplate clone = original.clone(newName, creatorId);

        // 存储克隆
        templates.put(clone.getId(), clone);
        nameIndex.put(newName.toLowerCase(), clone.getId());
        creatorIndex.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet())
            .add(clone.getId());

        plugin.getLogger().info("克隆模板 " + original.getName() + " 为 " + newName);
        return clone;
    }

    /**
     * 重命名模板
     */
    public boolean renameTemplate(UUID templateId, String newName) {
        PermissionTemplate template = templates.get(templateId);
        if (template == null) {
            return false;
        }

        if (template.isPreset()) {
            plugin.getLogger().warning("不能重命名预设模板");
            return false;
        }

        // 检查名称冲突
        if (nameIndex.containsKey(newName.toLowerCase())) {
            plugin.getLogger().warning("模板名称已存在: " + newName);
            return false;
        }

        // 移除旧名称索引
        nameIndex.remove(template.getName().toLowerCase());

        // 更新模板名称
        String oldName = template.getName();
        template.setName(newName);

        // 添加新名称索引
        nameIndex.put(newName.toLowerCase(), templateId);

        plugin.getLogger().info("重命名模板: " + oldName + " -> " + newName);
        return true;
    }

    // ==================== 搜索 ====================

    /**
     * 搜索模板
     */
    public List<PermissionTemplate> searchTemplates(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return templates.values().stream()
            .filter(t -> t.getName().toLowerCase().contains(lowerKeyword) ||
                        (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerKeyword)))
            .collect(Collectors.toList());
    }

    // ==================== 统计方法 ====================

    /**
     * 获取模板总数
     */
    public int getTemplateCount() {
        return templates.size();
    }

    /**
     * 获取自定义模板数量
     */
    public int getCustomTemplateCount() {
        return (int) templates.values().stream()
            .filter(t -> !t.isPreset())
            .count();
    }

    /**
     * 获取预设模板数量
     */
    public int getPresetTemplateCount() {
        return presetTemplates.size();
    }

    // ==================== 数据管理 ====================

    /**
     * 清空所有自定义模板
     */
    public void clearCustomTemplates() {
        List<UUID> toRemove = templates.values().stream()
            .filter(t -> !t.isPreset())
            .map(PermissionTemplate::getId)
            .toList();

        for (UUID id : toRemove) {
            deleteTemplate(id);
        }

        plugin.getLogger().info("清空所有自定义模板");
    }

    /**
     * 重建索引
     */
    public void rebuildIndexes() {
        nameIndex.clear();
        creatorIndex.clear();

        for (PermissionTemplate template : templates.values()) {
            nameIndex.put(template.getName().toLowerCase(), template.getId());

            if (template.getCreatorId() != null) {
                creatorIndex.computeIfAbsent(template.getCreatorId(),
                    k -> ConcurrentHashMap.newKeySet()).add(template.getId());
            }
        }

        plugin.getLogger().info("重建模板索引完成");
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_templates", templates.size());
        stats.put("preset_templates", getPresetTemplateCount());
        stats.put("custom_templates", getCustomTemplateCount());
        stats.put("creators", creatorIndex.size());
        return stats;
    }
}
