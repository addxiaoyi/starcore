# ===============================================
# STARCORE 压力测试完整指南
# ===============================================
# 本文档包含真实服务器压力测试的完整流程
# ===============================================

# ===============================================
# 第一部分：测试环境准备
# ===============================================

## 1.1 服务器要求

### 最低配置
- CPU: 4核心
- RAM: 4GB
- 网络: 100Mbps
- 硬盘: 20GB SSD

### 推荐配置
- CPU: 8核心
- RAM: 8GB
- 网络: 1Gbps
- 硬盘: 50GB NVMe SSD

## 1.2 软件要求

```bash
# 1. 安装 Java 21
sudo apt update
sudo apt install openjdk-21-jdk

# 2. 下载 Paper 服务器
wget https://api.papermc.io/v2/projects/paper/versions/1.21/builds/latest/downloads/paper-1.21-latest.jar

# 3. 创建启动脚本
cat > start.sh << 'EOF'
#!/bin/bash
java -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar paper-1.21-latest.jar --nogui
EOF

chmod +x start.sh
```

# ===============================================
# 第二部分：压力测试工具
# ===============================================

## 2.1 使用 mc-bots（推荐）

### 安装
```bash
# 1. 克隆仓库
git clone https://github.com/crpmax/mc-bots.git
cd mc-bots

# 2. 编译
./gradlew build

# 3. 配置 config.yml
cat > config.yml << 'EOF'
server:
  host: "localhost"
  port: 25565
  version: "1.21"

bots:
  count: 100          # 机器人数量
  spawn-delay: 100    # 生成延迟（毫秒）
  prefix: "Bot_"

behavior:
  move: true          # 是否移动
  chat: true          # 是否聊天
  attack: false       # 是否攻击
  break-blocks: false # 是否破坏方块
EOF

# 4. 运行
java -jar build/libs/mc-bots.jar
```

## 2.2 使用 StressTestBots 插件

### 安装
```bash
# 1. 下载插件
wget https://github.com/ShaneBeee/StressTestBots/releases/latest/download/StressTestBots.jar

# 2. 放入插件目录
mv StressTestBots.jar plugins/

# 3. 重启服务器
./start.sh
```

### 使用命令
```
/stresstest spawn <数量>     # 生成压力测试机器人
/stresstest remove all        # 移除所有机器人
/stresstest start             # 开始压力测试
/stresstest stop              # 停止压力测试
/stresstest status            # 查看状态
```

## 2.3 使用 Spark 性能分析

### 安装
```bash
# 1. 下载 Spark
wget https://ci.lucko.me/job/spark/lastSuccessfulBuild/artifact/spark-bukkit/build/libs/spark-bukkit.jar

# 2. 放入插件目录
mv spark-bukkit.jar plugins/

# 3. 重启服务器
```

### 使用命令
```
/spark profiler start        # 开始分析
/spark profiler stop         # 停止分析
/spark profiler open         # 查看报告
/spark health                # 查看健康状态
/spark tps                   # 查看TPS
/spark gc                    # 查看GC信息
/spark heapdump              # 生成堆转储
```

# ===============================================
# 第三部分：压力测试方案
# ===============================================

## 3.1 基础性能测试

### 测试目标
- 测试插件基础功能性能
- 确认没有明显性能问题
- 建立性能基线

### 测试步骤

```bash
# 1. 启动服务器
./start.sh

# 2. 启动 Spark 分析
/spark profiler start

# 3. 生成10个测试机器人
/stresstest spawn 10

# 4. 让机器人执行基础操作
- 移动
- 打开GUI
- 使用命令

# 5. 运行5分钟后停止
/spark profiler stop
/stresstest remove all

# 6. 查看报告
/spark profiler open
```

### 性能指标
- TPS: >18 (优秀: 20)
- MSPT: <50ms (优秀: <40ms)
- 内存使用: <50%
- GC频率: <5次/分钟

## 3.2 中等负载测试

### 测试目标
- 测试50-100玩家负载
- 验证缓存系统
- 验证异步系统

### 测试步骤

```bash
# 1. 生成50个机器人
/stresstest spawn 50

# 2. 执行操作
for i in {1..50}; do
  /sudo Bot_$i star spawn
  /sudo Bot_$i menu
  /sudo Bot_$i orbit Bot_$((i+1))
done

# 3. 监控性能
/spark tps
/spark health

# 4. 运行15分钟
sleep 900

# 5. 查看统计
/starcore performance
/starcore cache stats
/starcore pool stats
```

### 性能指标
- TPS: >16 (优秀: 19)
- MSPT: <70ms (优秀: <50ms)
- 内存使用: <60%
- 缓存命中率: >80%

## 3.3 高负载测试

### 测试目标
- 测试100-200玩家负载
- 压力测试数据库
- 压力测试缓存

### 测试步骤

```bash
# 1. 生成100个机器人
/stresstest spawn 100

# 2. 模拟真实玩家行为
- 50个机器人：移动和战斗
- 30个机器人：使用GUI
- 20个机器人：聊天和社交

# 3. 使用自定义压力测试脚本
./scripts/high-load-test.sh

# 4. 运行30分钟
sleep 1800

# 5. 分析结果
/spark profiler open
```

### 性能指标
- TPS: >15 (优秀: 18)
- MSPT: <90ms (优秀: <60ms)
- 内存使用: <70%
- 数据库响应: <10ms

## 3.4 极限压力测试

### 测试目标
- 找出性能瓶颈
- 测试系统极限
- 验证崩溃恢复

### 测试步骤

```bash
# 1. 逐步增加机器人数量
/stresstest spawn 50
# 等待5分钟
/stresstest spawn 50
# 等待5分钟
/stresstest spawn 50
# 等待5分钟
/stresstest spawn 50
# 等待5分钟

# 2. 所有机器人同时执行操作
for i in {1..200}; do
  /sudo Bot_$i orbit Bot_$((i+1))
done

# 3. 监控崩溃
tail -f logs/latest.log

# 4. 记录崩溃点
```

### 性能指标
- 找出TPS降到10以下的临界点
- 找出内存溢出的临界点
- 找出数据库连接耗尽的临界点

# ===============================================
# 第四部分：自动化测试脚本
# ===============================================

## 4.1 基础压力测试脚本

```bash
#!/bin/bash
# basic-stress-test.sh

echo "开始基础压力测试..."

# 启动性能分析
screen -S minecraft -p 0 -X stuff "spark profiler start^M"

# 生成机器人
screen -S minecraft -p 0 -X stuff "stresstest spawn 10^M"

# 等待5分钟
echo "等待5分钟..."
sleep 300

# 停止分析
screen -S minecraft -p 0 -X stuff "spark profiler stop^M"
screen -S minecraft -p 0 -X stuff "spark profiler open^M"

# 移除机器人
screen -S minecraft -p 0 -X stuff "stresstest remove all^M"

echo "基础压力测试完成！"
```

## 4.2 完整压力测试脚本

```bash
#!/bin/bash
# full-stress-test.sh

TEST_LEVELS=(10 25 50 75 100)
DURATION=300  # 5分钟

echo "开始完整压力测试..."

for BOTS in "${TEST_LEVELS[@]}"; do
  echo "测试 $BOTS 个机器人..."

  # 启动分析
  screen -S minecraft -p 0 -X stuff "spark profiler start^M"

  # 生成机器人
  screen -S minecraft -p 0 -X stuff "stresstest spawn $BOTS^M"

  # 等待
  echo "等待 $DURATION 秒..."
  sleep $DURATION

  # 获取TPS
  screen -S minecraft -p 0 -X stuff "spark tps^M"

  # 获取性能统计
  screen -S minecraft -p 0 -X stuff "starcore performance^M"

  # 停止分析
  screen -S minecraft -p 0 -X stuff "spark profiler stop^M"

  # 保存报告
  screen -S minecraft -p 0 -X stuff "spark profiler open^M"

  # 移除机器人
  screen -S minecraft -p 0 -X stuff "stresstest remove all^M"

  # 等待系统恢复
  echo "等待系统恢复..."
  sleep 60
done

echo "完整压力测试完成！"
```

## 4.3 持续压力测试脚本

```bash
#!/bin/bash
# continuous-stress-test.sh

DURATION=$((24 * 60 * 60))  # 24小时
BOTS=50

echo "开始持续压力测试（24小时）..."

# 生成机器人
screen -S minecraft -p 0 -X stuff "stresstest spawn $BOTS^M"

# 每小时记录一次性能
for i in {1..24}; do
  echo "小时 $i / 24"

  # 记录TPS
  screen -S minecraft -p 0 -X stuff "spark tps > logs/tps-hour-$i.log^M"

  # 记录性能
  screen -S minecraft -p 0 -X stuff "starcore performance > logs/perf-hour-$i.log^M"

  # 等待1小时
  sleep 3600
done

# 移除机器人
screen -S minecraft -p 0 -X stuff "stresstest remove all^M"

echo "持续压力测试完成！"
```

# ===============================================
# 第五部分：性能指标收集
# ===============================================

## 5.1 TPS监控

```bash
#!/bin/bash
# monitor-tps.sh

OUTPUT="tps-log.txt"

while true; do
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
  TPS=$(screen -S minecraft -p 0 -X stuff "spark tps^M" | grep "TPS" | awk '{print $2}')

  echo "$TIMESTAMP - TPS: $TPS" >> $OUTPUT

  sleep 10
done
```

## 5.2 内存监控

```bash
#!/bin/bash
# monitor-memory.sh

OUTPUT="memory-log.txt"

while true; do
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

  # 获取Java进程内存
  PID=$(pgrep -f "paper.*jar")
  MEM=$(ps -p $PID -o rss= | awk '{print $1/1024 " MB"}')

  echo "$TIMESTAMP - Memory: $MEM" >> $OUTPUT

  sleep 30
done
```

## 5.3 数据库监控

```bash
#!/bin/bash
# monitor-database.sh

OUTPUT="db-log.txt"

while true; do
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

  # MySQL连接数
  CONN=$(mysql -u root -p'password' -e "SHOW STATUS LIKE 'Threads_connected';" | tail -1 | awk '{print $2}')

  # 查询数量
  QUERIES=$(mysql -u root -p'password' -e "SHOW STATUS LIKE 'Queries';" | tail -1 | awk '{print $2}')

  echo "$TIMESTAMP - Connections: $CONN, Queries: $QUERIES" >> $OUTPUT

  sleep 60
done
```

# ===============================================
# 第六部分：性能问题诊断
# ===============================================

## 6.1 TPS下降诊断

### 问题症状
- TPS < 15
- 玩家感觉卡顿
- 操作响应慢

### 诊断步骤

```bash
# 1. 查看CPU使用
top -p $(pgrep -f "paper.*jar")

# 2. 查看线程
jstack $(pgrep -f "paper.*jar") > thread-dump.txt

# 3. 使用Spark分析
/spark profiler start
# 等待5分钟
/spark profiler stop
/spark profiler open

# 4. 查看慢查询
/spark profiler start --only-ticks-over 50
```

### 常见原因
1. 事件监听器性能问题
2. 数据库查询慢
3. 大量实体
4. 区块加载慢
5. 插件冲突

## 6.2 内存泄漏诊断

### 问题症状
- 内存持续增长
- 频繁GC
- OutOfMemoryError

### 诊断步骤

```bash
# 1. 生成堆转储
/spark heapdump

# 2. 使用 VisualVM 分析
# 下载: https://visualvm.github.io/

# 3. 查找大对象
jmap -histo $(pgrep -f "paper.*jar") | head -20

# 4. 分析GC
/spark gc

# 5. 查看线程
jcmd $(pgrep -f "paper.*jar") Thread.print > threads.txt
```

### 常见原因
1. 缓存未清理
2. 事件监听器未注销
3. 静态集合持有对象
4. 玩家对象未释放

## 6.3 数据库性能问题

### 问题症状
- 数据库连接超时
- 查询响应慢
- 连接池耗尽

### 诊断步骤

```sql
-- 1. 查看慢查询
SHOW FULL PROCESSLIST;

-- 2. 查看表锁
SHOW OPEN TABLES WHERE In_use > 0;

-- 3. 查看连接数
SHOW STATUS LIKE 'Threads_connected';

-- 4. 分析查询
EXPLAIN SELECT * FROM players WHERE uuid = '...';

-- 5. 查看索引
SHOW INDEX FROM players;
```

### 常见原因
1. 缺少索引
2. 连接池配置太小
3. 同步查询太多
4. 未使用批量操作

# ===============================================
# 第七部分：性能优化建议
# ===============================================

## 7.1 根据测试结果优化

### 如果TPS < 18
```yaml
performance:
  # 增加线程池
  thread-pool-size: 16

  # 增加缓存
  cache:
    player-data-size: 2000
    pvp-stats-size: 1000

  # 优化数据库
  database:
    max-pool-size: 20
    batch-size: 100
```

### 如果内存使用 > 70%
```yaml
performance:
  # 减小缓存
  cache:
    player-data-size: 500
    expire-after: 5m

  # 启用对象池
  object-pool:
    enabled: true
    max-size: 100

  # 增加GC频率
  gc-interval: 300
```

### 如果数据库慢
```yaml
database:
  # 增加连接池
  max-pool-size: 30
  min-idle: 15

  # 启用批量操作
  batch-enabled: true
  batch-size: 100

  # 启用缓存
  query-cache: true
```

## 7.2 JVM参数优化

### 小内存优化（4GB）
```bash
java -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=8M \
  -jar paper.jar
```

### 大内存优化（8GB+）
```bash
java -Xms8G -Xmx8G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=130 \
  -XX:G1HeapRegionSize=16M \
  -XX:+UnlockExperimentalVMOptions \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -jar paper.jar
```

# ===============================================
# 第八部分：测试报告模板
# ===============================================

## 8.1 性能测试报告

```markdown
# STARCORE 性能测试报告

## 测试环境
- 服务器: [CPU/内存/硬盘]
- Paper版本: [版本]
- STARCORE版本: [版本]
- 测试日期: [日期]

## 测试结果

### 基础性能测试（10 bots）
- TPS: [数值]
- MSPT: [数值]
- 内存: [数值]
- 缓存命中率: [数值]

### 中等负载测试（50 bots）
- TPS: [数值]
- MSPT: [数值]
- 内存: [数值]
- 数据库响应: [数值]

### 高负载测试（100 bots）
- TPS: [数值]
- MSPT: [数值]
- 内存: [数值]
- 系统稳定性: [评价]

### 极限压力测试（200 bots）
- 最大负载: [数值] bots
- 崩溃点: [数值] bots
- 瓶颈: [描述]

## 性能瓶颈
1. [问题1]
2. [问题2]

## 优化建议
1. [建议1]
2. [建议2]

## 结论
[总体评价]
```

# ===============================================
# 配置文件结束
# ===============================================
# 按照本指南进行压力测试，全面评估插件性能！
# ===============================================
