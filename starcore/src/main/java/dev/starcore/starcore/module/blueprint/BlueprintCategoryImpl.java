package dev.starcore.starcore.module.blueprint;

import java.io.Serializable;
import java.util.*;

/**
 * 蓝图分类实现
 */
public class BlueprintCategoryImpl implements BlueprintCategory, Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private String parentId;
    private int sortOrder;
    private boolean isPublic;
    private final long createdTime;

    private final List<String> blueprintIds = new ArrayList<>();
    private final List<String> childIds = new ArrayList<>();
    private final List<UUID> allowedPlayers = new ArrayList<>();
    private final List<String> allowedNations = new ArrayList<>();

    public BlueprintCategoryImpl(String id, String name, String description, String icon, String color) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon != null ? icon : "CHEST";
        this.color = color != null ? color : "WHITE";
        this.createdTime = System.currentTimeMillis();
        this.isPublic = true;
        this.sortOrder = 0;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getIcon() {
        return icon;
    }

    @Override
    public String getColor() {
        return color;
    }

    @Override
    public List<String> getBlueprintIds() {
        return Collections.unmodifiableList(blueprintIds);
    }

    @Override
    public int getBlueprintCount() {
        return blueprintIds.size();
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public List<String> getChildIds() {
        return Collections.unmodifiableList(childIds);
    }

    public void addChildId(String childId) {
        if (!childIds.contains(childId)) {
            childIds.add(childId);
        }
    }

    public void removeChildId(String childId) {
        childIds.remove(childId);
    }

    @Override
    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    @Override
    public List<UUID> getAllowedPlayers() {
        return Collections.unmodifiableList(allowedPlayers);
    }

    public void addAllowedPlayer(UUID playerId) {
        if (!allowedPlayers.contains(playerId)) {
            allowedPlayers.add(playerId);
        }
    }

    public void removeAllowedPlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
    }

    @Override
    public List<String> getAllowedNations() {
        return Collections.unmodifiableList(allowedNations);
    }

    public void addAllowedNation(String nationId) {
        if (!allowedNations.contains(nationId)) {
            allowedNations.add(nationId);
        }
    }

    public void removeAllowedNation(String nationId) {
        allowedNations.remove(nationId);
    }

    @Override
    public long getCreatedTime() {
        return createdTime;
    }

    @Override
    public boolean containsBlueprint(String blueprintId) {
        return blueprintIds.contains(blueprintId);
    }

    @Override
    public void addBlueprint(String blueprintId) {
        if (!blueprintIds.contains(blueprintId)) {
            blueprintIds.add(blueprintId);
        }
    }

    @Override
    public void removeBlueprint(String blueprintId) {
        blueprintIds.remove(blueprintId);
    }

    @Override
    public String toString() {
        return "BlueprintCategoryImpl{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", blueprints=" + blueprintIds.size() +
               '}';
    }
}
