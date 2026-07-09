package dev.starcore.starcore.pet;

import java.util.*;

/**
 * 玩家宠物数据
 */
public class PlayerPets {
    private final UUID playerId;
    private final Map<UUID, Pet> pets;
    private UUID activePetId;
    private int maxPets;
    private long totalExp;

    public PlayerPets(UUID playerId) {
        this.playerId = playerId;
        this.pets = new HashMap<>();
        this.maxPets = 5; // 默认最大宠物数
        this.totalExp = 0;
    }

    /**
     * 添加宠物
     */
    public boolean addPet(Pet pet) {
        if (pets.size() >= maxPets) {
            return false;
        }
        pets.put(pet.getPetId(), pet);
        return true;
    }

    /**
     * 移除宠物
     */
    public Pet removePet(UUID petId) {
        if (activePetId != null && activePetId.equals(petId)) {
            activePetId = null;
        }
        return pets.remove(petId);
    }

    /**
     * 获取宠物
     */
    public Pet getPet(UUID petId) {
        return pets.get(petId);
    }

    /**
     * 获取当前召唤的宠物
     */
    public Pet getActivePet() {
        if (activePetId == null) {
            return null;
        }
        return pets.get(activePetId);
    }

    /**
     * 设置活动宠物
     */
    public void setActivePet(UUID petId) {
        if (petId == null || pets.containsKey(petId)) {
            this.activePetId = petId;
        }
    }

    /**
     * 获取当前活动宠物ID
     */
    public UUID getActivePetId() {
        return activePetId;
    }

    /**
     * 获取所有宠物列表
     */
    public Collection<Pet> getAllPets() {
        return pets.values();
    }

    /**
     * 获取宠物数量
     */
    public int getPetCount() {
        return pets.size();
    }

    /**
     * 获取玩家ID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 获取最大宠物数
     */
    public int getMaxPets() {
        return maxPets;
    }

    /**
     * 设置最大宠物数
     */
    public void setMaxPets(int maxPets) {
        this.maxPets = maxPets;
    }

    /**
     * 获取总经验
     */
    public long getTotalExp() {
        return totalExp;
    }

    /**
     * 增加总经验
     */
    public void addTotalExp(long exp) {
        this.totalExp += exp;
    }

    /**
     * 获取所有宠物的总战斗力
     */
    public double getTotalPower() {
        return pets.values().stream()
                .mapToDouble(Pet::getTotalPower)
                .sum();
    }

    /**
     * 获取指定类别的宠物
     */
    public List<Pet> getPetsByCategory(PetCategory category) {
        List<Pet> result = new ArrayList<>();
        for (Pet pet : pets.values()) {
            if (pet.getPetType().getCategory() == category) {
                result.add(pet);
            }
        }
        return result;
    }

    /**
     * 获取可骑乘的宠物
     */
    public List<Pet> getRideablePets() {
        List<Pet> result = new ArrayList<>();
        for (Pet pet : pets.values()) {
            if (pet.getPetType().canRide()) {
                result.add(pet);
            }
        }
        return result;
    }

    /**
     * 转为可存储的Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId.toString());
        data.put("activePetId", activePetId != null ? activePetId.toString() : null);
        data.put("maxPets", maxPets);
        data.put("totalExp", totalExp);

        List<Map<String, Object>> petList = new ArrayList<>();
        for (Pet pet : pets.values()) {
            petList.add(pet.toMap());
        }
        data.put("pets", petList);

        return data;
    }

    /**
     * 从Map加载数据
     */
    @SuppressWarnings("unchecked")
    public static PlayerPets fromMap(Map<String, Object> data) {
        UUID playerId = UUID.fromString((String) data.get("playerId"));
        PlayerPets playerPets = new PlayerPets(playerId);

        String activePetIdStr = (String) data.get("activePetId");
        if (activePetIdStr != null && !activePetIdStr.isEmpty()) {
            playerPets.activePetId = UUID.fromString(activePetIdStr);
        }

        playerPets.maxPets = ((Number) data.getOrDefault("maxPets", 5)).intValue();
        playerPets.totalExp = ((Number) data.getOrDefault("totalExp", 0L)).longValue();

        List<Map<String, Object>> petList = (List<Map<String, Object>>) data.get("pets");
        if (petList != null) {
            for (Map<String, Object> petData : petList) {
                Pet pet = Pet.fromMap(petData);
                playerPets.pets.put(pet.getPetId(), pet);
            }
        }

        return playerPets;
    }
}
