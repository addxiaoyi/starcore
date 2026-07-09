package dev.starcore.starcore.event.random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件链管理器
 * 处理事件之间的链式触发关系，支持延迟触发
 */
public class EventChain {

    private final RandomEventService eventService;
    private final Map<UUID, ChainState> activeChains;
    private final Map<String, Long> chainSpecificCooldowns; // 用于链事件在 eventService 中的冷却标记
    private final Map<String, ChainTriggerTask> pendingTriggers;
    private JavaPlugin plugin;

    public EventChain(RandomEventService eventService) {
        this.eventService = eventService;
        this.activeChains = new ConcurrentHashMap<>();
        this.chainSpecificCooldowns = new HashMap<>();
        this.pendingTriggers = new ConcurrentHashMap<>();
    }

    /**
     * 设置插件引用（用于调度任务）
     */
    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动事件链
     *
     * @param eventId 初始事件ID
     * @param player 触发玩家
     * @param location 触发位置
     * @return 事件链ID
     */
    public UUID startChain(String eventId, Player player, Location location) {
        UUID chainId = UUID.randomUUID();
        ChainState state = new ChainState(chainId, eventId, player, location);
        activeChains.put(chainId, state);
        return chainId;
    }

    /**
     * 继续事件链 - 支持延迟触发
     *
     * @param chainId 事件链ID
     * @param nextEventId 下一个事件ID
     * @param delayTicks 延迟时间（tick），0表示立即触发
     * @return 如果成功安排触发返回true
     */
    public boolean continueChain(UUID chainId, String nextEventId, long delayTicks) {
        ChainState state = activeChains.get(chainId);
        if (state == null) {
            return false;
        }

        // 检查冷却时间
        RandomEvent nextEvent = eventService.getEvent(nextEventId);
        if (nextEvent == null) {
            return false;
        }

        if (!eventService.checkCooldown(nextEventId, nextEvent.getCooldown())) {
            return false;
        }

        String taskKey = chainId.toString() + "_" + nextEventId;

        // 立即设置冷却，防止重复触发
        eventService.setCooldown(nextEventId);

        if (delayTicks > 0 && plugin != null) {
            // 延迟触发
            ChainTriggerTask existingTask = pendingTriggers.get(taskKey);
            if (existingTask != null) {
                // 取消之前的任务
                existingTask.cancel();
            }

            ChainTriggerTask task = new ChainTriggerTask(chainId, nextEventId, nextEvent);
            pendingTriggers.put(taskKey, task);

            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                executeChainEvent(chainId, nextEventId, nextEvent);
                pendingTriggers.remove(taskKey);
            }, delayTicks).getTaskId();

            task.setTaskId(taskId);
            state.addPendingEvent(nextEventId, taskId);
            return true;
        } else {
            // 立即触发
            return executeChainEvent(chainId, nextEventId, nextEvent);
        }
    }

    /**
     * 重载 continueChain 以保持向后兼容
     */
    public boolean continueChain(UUID chainId, String nextEventId) {
        return continueChain(chainId, nextEventId, 0);
    }

    /**
     * 执行链事件
     */
    private boolean executeChainEvent(UUID chainId, String nextEventId, RandomEvent nextEvent) {
        ChainState state = activeChains.get(chainId);
        if (state == null) {
            return false;
        }

        state.addEvent(nextEventId);

        // 触发下一个事件（冷却已在 continueChain 中设置）
        eventService.triggerEvent(nextEvent, state.getPlayer(), state.getLocation(), false);

        // 处理该事件的链事件
        if (!nextEvent.getChainEvents().isEmpty()) {
            for (int i = 0; i < nextEvent.getChainEvents().size(); i++) {
                String chainEventId = nextEvent.getChainEvents().get(i);
                // 每个后续事件延迟 1 分钟
                continueChain(chainId, chainEventId, 20L * 60 * (i + 1));
            }
        }

        return true;
    }

    /**
     * 结束事件链
     *
     * @param chainId 事件链ID
     */
    public void endChain(UUID chainId) {
        ChainState state = activeChains.remove(chainId);
        if (state != null) {
            // 取消所有待处理的任务
            for (Map.Entry<String, Integer> entry : state.getPendingTasks().entrySet()) {
                String taskKey = chainId.toString() + "_" + entry.getKey();
                ChainTriggerTask task = pendingTriggers.remove(taskKey);
                if (task != null) {
                    Bukkit.getScheduler().cancelTask(entry.getValue());
                }
            }
        }
    }

    /**
     * 获取事件链状态
     *
     * @param chainId 事件链ID
     * @return 事件链状态，如果不存在返回null
     */
    public ChainState getChainState(UUID chainId) {
        return activeChains.get(chainId);
    }

    /**
     * 清理过期的事件链
     *
     * @param maxAge 最大存活时间（秒）
     */
    public void cleanupExpiredChains(long maxAge) {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, ChainState> entry : activeChains.entrySet()) {
            long age = (now - entry.getValue().getStartTime()) / 1000;
            if (age > maxAge) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID chainId : toRemove) {
            endChain(chainId);
        }
    }

    /**
     * 获取活跃事件链数量
     *
     * @return 活跃事件链数量
     */
    public int getActiveChainCount() {
        return activeChains.size();
    }

    /**
     * 获取待触发任务数量
     */
    public int getPendingTriggerCount() {
        return pendingTriggers.size();
    }

    /**
     * 链触发任务类
     */
    private static class ChainTriggerTask {
        private final UUID chainId;
        private final String eventId;
        private final RandomEvent event;
        private int taskId;

        public ChainTriggerTask(UUID chainId, String eventId, RandomEvent event) {
            this.chainId = chainId;
            this.eventId = eventId;
            this.event = event;
        }

        public void setTaskId(int taskId) {
            this.taskId = taskId;
        }

        public int getTaskId() {
            return taskId;
        }

        public void cancel() {
            if (taskId > 0) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
        }
    }

    /**
     * 事件链状态类
     */
    public static class ChainState {
        private final UUID chainId;
        private final String initialEventId;
        private final Player player;
        private final Location location;
        private final List<String> eventHistory;
        private final long startTime;
        private final Map<String, Object> context;
        private final Map<String, Integer> pendingTasks;

        public ChainState(UUID chainId, String initialEventId, Player player, Location location) {
            this.chainId = chainId;
            this.initialEventId = initialEventId;
            this.player = player;
            this.location = location;
            this.eventHistory = new ArrayList<>();
            this.eventHistory.add(initialEventId);
            this.startTime = System.currentTimeMillis();
            this.context = new HashMap<>();
            this.pendingTasks = new HashMap<>();
        }

        /**
         * 添加事件到历史记录
         *
         * @param eventId 事件ID
         */
        public void addEvent(String eventId) {
            eventHistory.add(eventId);
        }

        /**
         * 添加待处理的任务
         *
         * @param eventId 事件ID
         * @param taskId 任务ID
         */
        public void addPendingEvent(String eventId, int taskId) {
            pendingTasks.put(eventId, taskId);
        }

        /**
         * 获取待处理的任务
         */
        public Map<String, Integer> getPendingTasks() {
            return new HashMap<>(pendingTasks);
        }

        /**
         * 检查事件是否已经在链中触发过
         *
         * @param eventId 事件ID
         * @return 如果已触发返回true
         */
        public boolean hasTriggered(String eventId) {
            return eventHistory.contains(eventId);
        }

        /**
         * 设置上下文数据
         *
         * @param key 键
         * @param value 值
         */
        public void setContext(String key, Object value) {
            context.put(key, value);
        }

        /**
         * 获取上下文数据
         *
         * @param key 键
         * @return 值，如果不存在返回null
         */
        public Object getContext(String key) {
            return context.get(key);
        }

        // Getters
        public UUID getChainId() {
            return chainId;
        }

        public String getInitialEventId() {
            return initialEventId;
        }

        public Player getPlayer() {
            return player;
        }

        public Location getLocation() {
            return location;
        }

        public List<String> getEventHistory() {
            return new ArrayList<>(eventHistory);
        }

        public long getStartTime() {
            return startTime;
        }

        public int getChainLength() {
            return eventHistory.size();
        }

        public Map<String, Object> getContext() {
            return new HashMap<>(context);
        }
    }
}
