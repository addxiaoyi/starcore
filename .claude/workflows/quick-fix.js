export const meta = {
  name: 'quick-fix-batch',
  description: '快速批量修复编译错误',
  phases: ['Fix CrossServerWarSync', 'Fix Others', 'Verify'],
}

phase('Fix CrossServerWarSync')
const warFix = await agent(`你是一个 Java 编译器错误修复专家。

项目目录：D:\\qwq\\项目\\mapadd\\starcore

CrossServerWarSync.java 文件有很多编译错误，因为 WarService 接口与代码使用不匹配。

请读取该文件并修复以下问题：

1. 修复所有使用 WarService 的地方，使用正确的方法签名
2. 删除所有使用 StarCoreEventBus.publish() 的代码行
3. 删除所有使用 WarDeclaredEvent, WarEndedEvent, WarStartedEvent 的代码
4. 删除所有使用 PendingWarSync 构造函数的代码
5. 使用 String 作为战争标识符而不是 UUID

这是一个示例修复模式：

原始代码（有问题）:
for (WarSnapshot war : warService.activeWars()) {
    syncWarState(war.warId());  // warId() 不存在
}

修复后:
for (WarSnapshot war : warService.activeWars()) {
    syncWarState(war.left().toString() + "_" + war.right().toString());
}

请修复所有错误。`, {label: 'fix-war-sync'})

phase('Fix Others')
const otherFix = await parallel([
  () => agent(`修复以下编译错误：

项目目录：D:\\qwq\\项目\\mapadd\\starcore

1. CrossServerNationSync.java - 修复类型不匹配问题
2. CrossServerPlayerSync.java - 修复 BigDecimal 到 double 的转换（使用 .doubleValue()）
3. CrossServerTerritorySync.java - 修复缺失的符号

使用 Edit 工具修复这些问题。`, {label: 'fix-nation-sync'}),
  () => agent(`修复以下编译错误：

项目目录：D:\\qwq\\项目\\mapadd\\starcore

1. CrossServerWarSync.java 行的所有 @Override 注解错误
2. RedisTransport.java 和 RabbitMQTransport.java 的 @Override 问题

方法：删除所有 @Override 注解或修复方法签名以匹配接口。`, {label: 'fix-transports'})
])

phase('Verify')
const verify = await agent(`运行 'cd /d/qwq/项目/mapadd/starcore && mvn compile 2>&1' 检查编译结果。

返回：
1. 编译是否成功
2. 剩余错误数量
3. 如果有错误，列出前10个错误文件。`, {label: 'verify'})

return { warFix, otherFix, verify }
