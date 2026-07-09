package dev.starcore.starcore.monitoring;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.StarCorePlugin;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务器压力测试工具
 * 命令: /stresstest <类型> [参数]
 *
 * 支持的压力测试类型:
 * - entity <数量> - 模拟大量实体生成
 * - chunk <数量> - 模拟大量区块加载
 * - player <数量> - 模拟多玩家在线
 * - economy <次数> - 模拟经济交易
 * - combat <次数> - 模拟战斗事件
 * - database <次数> - 模拟数据库操作
 *
 * 权限: starcore.admin
 */
public class StressTestCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final StarCorePlugin plugin;
    private final InternalEconomyService economyService;
    private final WarService warService;

    // 测试结果记录
    private final Map<String, StressTestResult> lastResults = new ConcurrentHashMap<>();

    public StressTestCommand(StarCorePlugin plugin, InternalEconomyService economyService, WarService warService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.warService = warService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text("你没有权限使用此命令").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender, label);
            return true;
        }

        String testType = args[0].toLowerCase();
        int amount = args.length > 1 ? parseIntOrDefault(args[1], 100) : 100;

        // 验证数量
        if (amount <= 0) {
            amount = 100;
        }
        if (amount > 10000) {
            sender.sendMessage(Component.text("警告: 测试数量超过10000，将分批执行").color(NamedTextColor.YELLOW));
        }

        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("服务器压力测试").color(NamedTextColor.GOLD).append(Component.text(" - " + testType).color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("参数: " + amount).color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));

        long startTime = System.currentTimeMillis();
        String startTimeStr = LocalDateTime.now().format(TIME_FORMAT);
        sender.sendMessage(Component.text("[" + startTimeStr + "] 测试开始...").color(NamedTextColor.YELLOW));

        switch (testType) {
            case "entity" -> runEntityTest(sender, amount, startTime);
            case "chunk" -> runChunkTest(sender, amount, startTime);
            case "player" -> runPlayerTest(sender, amount, startTime);
            case "economy" -> runEconomyTest(sender, amount, startTime);
            case "combat" -> runCombatTest(sender, amount, startTime);
            case "database", "db" -> runDatabaseTest(sender, amount, startTime);
            case "all" -> runAllTests(sender, amount, startTime);
            default -> {
                sender.sendMessage(Component.text("未知测试类型: " + testType).color(NamedTextColor.RED));
                sendHelp(sender, label);
            }
        }

        return true;
    }

    private void runEntityTest(CommandSender sender, int count, long startTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        Player targetPlayer = sender instanceof Player ? (Player) sender : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);

        if (targetPlayer == null) {
            sender.sendMessage(Component.text("没有在线玩家，无法测试实体生成").color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("正在生成 " + count + " 个实体...").color(NamedTextColor.AQUA));

        int batchSize = Math.min(100, count);
        int totalBatches = (count + batchSize - 1) / batchSize;
        AtomicInteger completedBatches = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int batch = completedBatches.getAndIncrement();
                if (batch >= totalBatches) {
                    this.cancel();
                    finishTest(sender, "实体生成", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();
                int start = batch * batchSize;
                int end = Math.min(start + batchSize, count);

                try {
                    for (int i = start; i < end; i++) {
                        // 在玩家附近生成各类实体
                        EntityType[] types = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN};
                        EntityType type = types[i % types.length];

                        targetPlayer.getWorld().spawnEntity(targetPlayer.getLocation(), type);
                        success.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.addAndGet(end - start);
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);

                // 清理已生成的实体（每批后清理）
                if (batch % 10 == 0) {
                    targetPlayer.getWorld().getEntitiesByClasses(
                        EntityType.PIG.getEntityClass(),
                        EntityType.COW.getEntityClass(),
                        EntityType.SHEEP.getEntityClass(),
                        EntityType.CHICKEN.getEntityClass()
                    ).forEach(e -> e.remove());
                }

                sender.sendMessage(Component.text("  批次 " + (batch + 1) + "/" + totalBatches + " 完成 (" + end + "/" + count + ")")
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void runChunkTest(CommandSender sender, int count, long startTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        Player targetPlayer = sender instanceof Player ? (Player) sender : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);

        if (targetPlayer == null) {
            sender.sendMessage(Component.text("没有在线玩家，无法测试区块加载").color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("正在加载 " + count + " 个区块...").color(NamedTextColor.AQUA));

        int batchSize = Math.min(50, count);
        int totalBatches = (count + batchSize - 1) / batchSize;
        AtomicInteger completedBatches = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int batch = completedBatches.getAndIncrement();
                if (batch >= totalBatches) {
                    this.cancel();
                    finishTest(sender, "区块加载", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();
                int start = batch * batchSize;
                int end = Math.min(start + batchSize, count);

                try {
                    int centerX = targetPlayer.getLocation().getChunk().getX();
                    int centerZ = targetPlayer.getLocation().getChunk().getZ();
                    int radius = (int) Math.ceil(Math.sqrt(count));

                    for (int i = start; i < end; i++) {
                        int dx = (i % (radius * 2)) - radius;
                        int dz = (i / (radius * 2)) % radius;
                        if (dx * dx + dz * dz <= radius * radius) {
                            Chunk chunk = targetPlayer.getWorld().getChunkAt(centerX + dx, centerZ + dz);
                            if (chunk != null && chunk.isLoaded()) {
                                success.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    failed.addAndGet(end - start);
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);

                sender.sendMessage(Component.text("  批次 " + (batch + 1) + "/" + totalBatches + " 完成 (" + end + "/" + count + ")")
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private void runPlayerTest(CommandSender sender, int count, long startTime) {
        // 玩家测试：模拟大量玩家数据查询
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        sender.sendMessage(Component.text("正在模拟 " + count + " 次玩家数据查询...").color(NamedTextColor.AQUA));

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            sender.sendMessage(Component.text("没有在线玩家，使用虚拟UUID进行测试").color(NamedTextColor.YELLOW));
        }

        int batchSize = Math.min(500, count);
        AtomicInteger processed = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int current = processed.get();
                if (current >= count) {
                    this.cancel();
                    finishTest(sender, "玩家数据查询", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();

                for (int i = 0; i < batchSize && current + i < count; i++) {
                    try {
                        // 模拟玩家数据查询操作
                        UUID testUuid = UUID.randomUUID();

                        // 查询玩家余额（如果经济服务可用）
                        if (economyService != null) {
                            economyService.getBalance(testUuid);
                        }

                        // 模拟其他玩家数据查询
                        String playerName = "Player_" + testUuid.hashCode();

                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);
                processed.addAndGet(batchSize);

                sender.sendMessage(Component.text("  进度: " + Math.min(current + batchSize, count) + "/" + count)
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void runEconomyTest(CommandSender sender, int count, long startTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        sender.sendMessage(Component.text("正在执行 " + count + " 次经济交易...").color(NamedTextColor.AQUA));

        if (economyService == null) {
            sender.sendMessage(Component.text("经济服务不可用，执行模拟测试").color(NamedTextColor.YELLOW));
        }

        int batchSize = Math.min(500, count);
        AtomicInteger processed = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int current = processed.get();
                if (current >= count) {
                    this.cancel();
                    finishTest(sender, "经济交易", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();

                for (int i = 0; i < batchSize && current + i < count; i++) {
                    long opStart = System.nanoTime();
                    try {
                        UUID playerUuid = UUID.randomUUID();
                        BigDecimal amount = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble() * 1000);

                        if (economyService != null) {
                            // 真实经济操作
                            economyService.deposit(playerUuid, amount);
                            economyService.withdraw(playerUuid, amount);
                        } else {
                            // 模拟操作
                            simulateOperation(5);
                        }

                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                    durations.add((System.nanoTime() - opStart) / 1_000_000);
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);
                processed.addAndGet(batchSize);

                sender.sendMessage(Component.text("  进度: " + Math.min(current + batchSize, count) + "/" + count)
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void runCombatTest(CommandSender sender, int count, long startTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        sender.sendMessage(Component.text("正在模拟 " + count + " 次战斗事件...").color(NamedTextColor.AQUA));

        int batchSize = Math.min(500, count);
        AtomicInteger processed = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int current = processed.get();
                if (current >= count) {
                    this.cancel();
                    finishTest(sender, "战斗事件", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();

                for (int i = 0; i < batchSize && current + i < count; i++) {
                    long opStart = System.nanoTime();
                    try {
                        // 模拟战斗事件计算
                        UUID attacker = UUID.randomUUID();
                        UUID defender = UUID.randomUUID();

                        // 模拟伤害计算
                        double damage = ThreadLocalRandom.current().nextDouble() * 100;
                        double defense = ThreadLocalRandom.current().nextDouble() * 50;
                        double finalDamage = Math.max(0, damage - defense);

                        // 模拟暴击判定
                        boolean critical = ThreadLocalRandom.current().nextDouble() < 0.1;
                        if (critical) {
                            finalDamage *= 1.5;
                        }

                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                    durations.add((System.nanoTime() - opStart) / 1_000_000);
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);
                processed.addAndGet(batchSize);

                sender.sendMessage(Component.text("  进度: " + Math.min(current + batchSize, count) + "/" + count)
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void runDatabaseTest(CommandSender sender, int count, long startTime) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<Long> durations = new CopyOnWriteArrayList<>();

        sender.sendMessage(Component.text("正在执行 " + count + " 次数据库操作...").color(NamedTextColor.AQUA));

        int batchSize = Math.min(200, count);
        AtomicInteger processed = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                int current = processed.get();
                if (current >= count) {
                    this.cancel();
                    finishTest(sender, "数据库操作", startTime, success.get(), failed.get(), durations);
                    return;
                }

                long batchStart = System.nanoTime();

                for (int i = 0; i < batchSize && current + i < count; i++) {
                    long opStart = System.nanoTime();
                    try {
                        // 模拟数据库读写操作
                        simulateDatabaseOperation();
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                    durations.add((System.nanoTime() - opStart) / 1_000_000);
                }

                durations.add((System.nanoTime() - batchStart) / 1_000_000);
                processed.addAndGet(batchSize);

                sender.sendMessage(Component.text("  进度: " + Math.min(current + batchSize, count) + "/" + count)
                    .color(NamedTextColor.GRAY));
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void runAllTests(CommandSender sender, int baseCount, long startTime) {
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("执行综合压力测试（所有测试类型）").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("每个测试执行 " + baseCount + " 次").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));

        // 依次执行每个测试
        int testIndex = 0;
        String[] tests = {"entity", "chunk", "player", "economy", "combat", "database"};

        StressTestCommand self = this;

        new BukkitRunnable() {
            int currentTest = 0;

            @Override
            public void run() {
                if (currentTest >= tests.length) {
                    this.cancel();
                    sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
                    sender.sendMessage(Component.text("所有压力测试完成！").color(NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("总耗时: " + (System.currentTimeMillis() - startTime) + "ms").color(NamedTextColor.WHITE));
                    sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
                    return;
                }

                String testName = tests[currentTest];
                sender.sendMessage(Component.text("").color(NamedTextColor.BLACK));
                sender.sendMessage(Component.text(">>> 执行测试: " + testName).color(NamedTextColor.AQUA));

                long testStart = System.currentTimeMillis();
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                List<Long> testDurations = new CopyOnWriteArrayList<>();

                // 执行单个测试
                runTestSync(testName, baseCount, successCount, failedCount, testDurations);

                long testDuration = System.currentTimeMillis() - testStart;
                LocalDateTime endTime = LocalDateTime.now();
                LocalDateTime startTime = endTime.minusNanos(testDuration * 1_000_000);
                StressTestResult result = new StressTestResult(
                    testName,
                    startTime.format(TIME_FORMAT),
                    endTime.format(TIME_FORMAT),
                    testDuration,
                    successCount.get(),
                    failedCount.get(),
                    calculateAvgTime(testDurations),
                    calculateSuccessRate(successCount.get(), failedCount.get())
                );

                lastResults.put(testName, result);
                displayTestResult(sender, result);

                currentTest++;
            }

            private void runTestSync(String testName, int count, AtomicInteger success, AtomicInteger failed, List<Long> durations) {
                switch (testName) {
                    case "entity" -> {
                        for (int i = 0; i < count; i++) {
                            long start = System.nanoTime();
                            try {
                                Player p = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                                if (p != null) {
                                    EntityType type = EntityType.values()[i % EntityType.values().length];
                                    p.getWorld().spawnEntity(p.getLocation(), type);
                                    success.incrementAndGet();
                                }
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                            durations.add((System.nanoTime() - start) / 1_000_000);
                        }
                    }
                    case "chunk" -> {
                        Player p = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                        if (p != null) {
                            int cx = p.getLocation().getChunk().getX();
                            int cz = p.getLocation().getChunk().getZ();
                            for (int i = 0; i < count; i++) {
                                long start = System.nanoTime();
                                try {
                                    Chunk chunk = p.getWorld().getChunkAt(cx + i % 10, cz + i / 10);
                                    if (chunk.isLoaded()) success.incrementAndGet();
                                } catch (Exception e) {
                                    failed.incrementAndGet();
                                }
                                durations.add((System.nanoTime() - start) / 1_000_000);
                            }
                        }
                    }
                    case "player" -> {
                        for (int i = 0; i < count; i++) {
                            long start = System.nanoTime();
                            try {
                                UUID uuid = UUID.randomUUID();
                                if (economyService != null) economyService.getBalance(uuid);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                            durations.add((System.nanoTime() - start) / 1_000_000);
                        }
                    }
                    case "economy" -> {
                        for (int i = 0; i < count; i++) {
                            long start = System.nanoTime();
                            try {
                                UUID uuid = UUID.randomUUID();
                                BigDecimal amount = BigDecimal.valueOf(100);
                                if (economyService != null) {
                                    economyService.deposit(uuid, amount);
                                    economyService.withdraw(uuid, amount);
                                }
                                success.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                            durations.add((System.nanoTime() - start) / 1_000_000);
                        }
                    }
                    case "combat" -> {
                        for (int i = 0; i < count; i++) {
                            long start = System.nanoTime();
                            try {
                                double damage = ThreadLocalRandom.current().nextDouble() * 100;
                                double defense = ThreadLocalRandom.current().nextDouble() * 50;
                                Math.max(0, damage - defense);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                            durations.add((System.nanoTime() - start) / 1_000_000);
                        }
                    }
                    case "database" -> {
                        for (int i = 0; i < count; i++) {
                            long start = System.nanoTime();
                            try {
                                simulateDatabaseOperation();
                                success.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                            durations.add((System.nanoTime() - start) / 1_000_000);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20); // 每秒执行一个测试
    }

    private void finishTest(CommandSender sender, String testName, long startTime,
                           int success, int failed, List<Long> durations) {
        long totalDuration = System.currentTimeMillis() - startTime;
        String endTime = LocalDateTime.now().format(TIME_FORMAT);

        double avgTime = calculateAvgTime(durations);
        double successRate = calculateSuccessRate(success, failed);

        LocalDateTime endDateTime = LocalDateTime.now();
        LocalDateTime startDateTime = endDateTime.minusNanos(totalDuration * 1_000_000);
        StressTestResult result = new StressTestResult(
            testName,
            startDateTime.format(TIME_FORMAT),
            endDateTime.format(TIME_FORMAT),
            totalDuration,
            success,
            failed,
            avgTime,
            successRate
        );

        lastResults.put(testName, result);

        sender.sendMessage(Component.text("").color(NamedTextColor.BLACK));
        displayTestResult(sender, result);
    }

    private void displayTestResult(CommandSender sender, StressTestResult result) {
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("测试结果: " + result.testName()).color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("----------------------------------------").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  开始时间: " + result.startTime()).color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  结束时间: " + result.endTime()).color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  持续时长: " + result.durationMs() + " ms").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("----------------------------------------").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  总操作数: " + (result.successCount() + result.failedCount())).color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  成功次数: " + result.successCount()).color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  失败次数: " + result.failedCount()).color(result.failedCount() > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  平均耗时: " + String.format("%.2f", result.avgTimeMs()) + " ms/操作").color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  成功率: " + String.format("%.2f%%", result.successRate())).color(
            result.successRate() >= 99 ? NamedTextColor.GREEN :
            result.successRate() >= 90 ? NamedTextColor.YELLOW : NamedTextColor.RED
        ));
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
    }

    private double calculateAvgTime(List<Long> durations) {
        if (durations.isEmpty()) return 0;
        return durations.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private double calculateSuccessRate(int success, int failed) {
        int total = success + failed;
        if (total == 0) return 0;
        return (double) success / total * 100;
    }

    private void simulateOperation(int iterations) {
        // 模拟一些CPU计算
        double result = 0;
        for (int i = 0; i < iterations; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
    }

    private void simulateDatabaseOperation() {
        // 模拟数据库操作的延迟
        simulateOperation(10);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("服务器压力测试工具").color(NamedTextColor.GREEN).append(Component.text(" - 帮助").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("用法: " + label + " <类型> [数量]").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("----------------------------------------").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  entity <数量>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟实体生成测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  chunk <数量>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟区块加载测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  player <数量>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟玩家数据查询").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  economy <次数>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟经济交易测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  combat <次数>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟战斗事件测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  database <次数>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 模拟数据库操作测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  all <数量>").color(NamedTextColor.AQUA)
            .append(Component.text(" - 执行所有测试").color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("----------------------------------------").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("默认数量: 100").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("权限: starcore.admin").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("========================================").color(NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("entity", "chunk", "player", "economy", "combat", "database", "all")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2) {
            return Arrays.asList("100", "500", "1000", "5000", "10000")
                .stream()
                .filter(s -> s.startsWith(args[1]))
                .toList();
        }
        return Collections.emptyList();
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 压力测试结果记录
     */
    public record StressTestResult(
        String testName,
        String startTime,
        String endTime,
        long durationMs,
        int successCount,
        int failedCount,
        double avgTimeMs,
        double successRate
    ) {}

    /**
     * 获取上次测试结果
     */
    public Optional<StressTestResult> getLastResult(String testName) {
        return Optional.ofNullable(lastResults.get(testName));
    }

    /**
     * 获取所有测试结果
     */
    public Map<String, StressTestResult> getAllResults() {
        return Collections.unmodifiableMap(lastResults);
    }
}
