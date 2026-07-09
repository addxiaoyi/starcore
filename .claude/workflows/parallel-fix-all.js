export const meta = {
  name: 'parallel-fix-all',
  description: '并行修复所有编译错误',
  phases: ['Analyze', 'Fix Batch 1', 'Fix Batch 2', 'Fix Batch 3', 'Final Verify'],
}

phase('Analyze')
const analysis = await agent(`运行 'cd /d/qwq/项目/mapadd/starcore && mvn compile 2>&1' 获取当前编译错误列表。

分类统计错误：
1. 按文件分组统计
2. 找出最简单的修复（只需要添加 import 的文件）
3. 找出需要创建缺失类的文件
4. 找出需要修复方法签名的文件

返回 JSON 格式：
{
  "totalErrors": 数字,
  "byFile": [{"file": "路径", "count": 数字, "errorType": "类型"}],
  "quickFixes": ["修复1", "修复2"],
  "needsNewClass": ["类1", "类2"]
}`, {label: 'analyze'})

phase('Fix Batch 1')
const batch1 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

修复以下文件的编译错误：

1. DatabaseMigrationService.java - 18个错误
2. ParticleEffectManager.java - 16个错误
3. QuestMenu.java - 14个错误
4. CourtExecutionService.java - 12个错误
5. AchievementStorage.java - 12个错误

常见修复方法：
- 添加缺失的 import
- 修复 API 不匹配（检查 Paper 1.21.11 API）
- 修复类型转换问题

请读取每个文件，找出错误原因，然后修复。`, {label: 'batch1'})

phase('Fix Batch 2')
const batch2 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

修复以下文件的编译错误：

1. TerritoryEndpoint.java - 10个错误
2. AchievementCommand.java - 10个错误
3. EmoteMenu.java - 8个错误
4. GuiAnimationManager.java - 8个错误
5. NationManagementMenuListener.java - 6个错误

常见修复方法：
- 添加缺失的 import (如 java.util.Optional)
- 修复 API 方法名
- 修复类型转换

请读取每个文件，修复错误。`, {label: 'batch2'})

phase('Fix Batch 3')
const batch3 = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

修复以下文件的编译错误：

1. MenuTransitionAnimator.java - 6个错误
2. RestApiServer.java - 6个错误
3. SqlSocialStateStorage.java - 4个错误
4. StatsEndpoint.java - 4个错误
5. ApiAuthService.java - 4个错误
6. AchievementGui.java - 4个错误
7. AchievementModule.java - 4个错误
8. SocialImportExportService.java - 2个错误
9. CommissionCommand.java - 2个错误

请读取并修复这些文件。`, {label: 'batch3'})

phase('Final Verify')
const result = await agent(`运行 'cd /d/qwq/项目/mapadd/starcore && mvn compile 2>&1' 检查最终编译结果。

1. 统计最终错误数量
2. 如果编译成功，返回 "SUCCESS"
3. 如果有错误但少于50个，列出所有错误
4. 如果有超过50个错误，只列出错误最多的10个文件

返回格式：
{
  "success": true/false,
  "errorCount": 数字,
  "topErrors": ["文件1:错误数", ...]
}`, {label: 'final-verify'})

return {
  analysis,
  batch1,
  batch2,
  batch3,
  result
}
