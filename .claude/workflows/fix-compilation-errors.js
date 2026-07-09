export const meta = {
  name: 'fix-compilation-errors',
  description: '并行修复 StarCore 项目编译错误',
  phases: ['Scan', 'Parallel Fix 1', 'Parallel Fix 2', 'Verify'],
}

// Phase 1: Scan and categorize errors
phase('Scan')
const compileOutput = await agent(`运行 'cd /d/qwq/项目/mapadd/starcore && mvn compile 2>&1' 获取完整编译错误列表，然后分析这些错误：

1. 统计总错误数量
2. 按类别分类（缺失包、缺失类、类型错误、重复定义等）
3. 列出每个类别的错误数量
4. 识别最简单的修复（添加 import、修复类型转换等）

返回 JSON 格式：
{
  "totalErrors": 数字,
  "categories": [{"type": "类别", "count": 数字, "files": ["文件1", "文件2"]}],
  "quickFixes": ["简单修复1", "简单修复2"]
}`, {label: 'scan-errors'})

// Phase 2: Parallel fixes - Batch 1
phase('Parallel Fix 1')
const fix1 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

请修复以下几类编译错误：

1. **Plugin 类型转换错误**
   - 修复 SocialModule.java: org.bukkit.plugin.Plugin 无法转换为 org.bukkit.plugin.java.JavaPlugin
   - 修复 QuestModule.java: 同样的问题
   - 方法：从 context.plugin() 获取 JavaPlugin 或添加类型检查

2. **RankingServiceImpl 重复方法**
   - 文件: ranking/RankingServiceImpl.java:573
   - 错误: getKDRatio(UUID) 方法已定义
   - 方法：删除重复的方法定义

3. **Event 包缺失**
   - 文件: social/simulation/SocialSimulationListener.java
   - 错误: 包 dev.starcore.starcore.event.player 不存在
   - 方法：
     a) 创建缺失的包和类，或
     b) 修改 import 语句使用存在的包

4. **Network 包缺失**
   - 文件: social/SocialSimulationModule.java
   - 错误: 包 dev.starcore.starcore.social.simulation.network 不存在
   - 方法：创建缺失的包或移除 import

请直接编辑修复这些文件，使用 Edit 或 Write 工具。`, {label: 'fix-batch1'})

const fix2 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

请修复以下几类编译错误：

1. **缺失的 Optional 导入**
   - 在以下文件中添加 import java.util.Optional;
   - 搜索所有使用 Optional 但没有导入的文件

2. **ReputationServiceImpl 符号错误**
   - 文件: social/simulation/ReputationServiceImpl.java
   - 错误行: 129, 196, 202, 208, 214
   - 检查这些行引用的类是否存在，如不存在则创建或修复

3. **SocialSimCommand 符号错误**
   - 文件: social/simulation/command/SocialSimCommand.java
   - 错误: 行 50, 407
   - 检查并修复引用的类

4. **CrossServerPlayerSync 类型错误**
   - 文件: crossserver/CrossServerPlayerSync.java
   - 错误: BigDecimal 无法转换为 double
   - 方法：使用 .doubleValue() 或调整类型

5. **StarCorePlugin 符号错误**
   - 文件: StarCorePlugin.java
   - 错误: 行 248, 687, 690
   - 检查引用的服务/类是否存在

请直接编辑修复这些问题。`, {label: 'fix-batch2'})

// Phase 3: Parallel fixes - Batch 2
phase('Parallel Fix 2')
const fix3 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

请修复以下编译错误：

1. **NewsPropagationService 符号错误**
   - 文件: social/simulation/NewsPropagationService.java:31
   - 检查并修复缺失的类引用

2. **SocialModule 其他错误**
   - 文件: social/SocialModule.java
   - 错误: 行 217, 271
   - 修复缺失的类引用

3. **RelationshipNetwork 类问题**
   - 搜索 social/simulation 目录中所有引用 RelationshipNetwork 的地方
   - 如果类不存在，创建它

4. **SocialSimulationListener 其他符号错误**
   - 行: 178, 231, 620
   - 检查并创建缺失的类

5. **SocialSimulationModule 错误**
   - 修复 social/SocialSimulationModule.java 中的 import 错误

请使用 Grep 查找缺失的类，然后在需要的地方创建简单的存根类。`, {label: 'fix-batch3'})

const fix4 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

请执行以下修复任务：

1. **批量修复所有文件的 Optional 导入**
   - 使用 grep 查找所有使用 Optional 但没有导入 java.util.Optional 的文件
   - 修复所有这些文件

2. **创建缺失的包/类**
   - event/player/ 包的类
   - social/simulation/network/ 包的类
   - 其他被引用但不存在的类

3. **修复类型转换错误**
   - 所有 Plugin 到 JavaPlugin 的转换问题

4. **删除重复方法定义**
   - 检查所有类中是否有重复的方法签名

请使用 parallel() 并行执行多个修复任务。`, {label: 'fix-batch4'})

// Phase 4: Verify
phase('Verify')
const verify = await agent(`运行 'cd /d/qwq/项目/mapadd/starcore && mvn compile 2>&1' 检查编译结果。

1. 运行编译命令
2. 统计剩余错误数量
3. 如果有新的简单错误（import 缺失等），直接修复
4. 返回最终错误列表（如果有）

返回 JSON 格式：
{
  "buildSuccess": true/false,
  "remainingErrors": 数字,
  "errorFiles": ["文件1:错误描述", ...]
}`, {label: 'verify'})

return {
  scanResult: compileOutput,
  fixes1: fix1,
  fixes2: fix2,
  fixes3: fix3,
  fixes4: fix4,
  verifyResult: verify
}
