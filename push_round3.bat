@echo off
cd /d "%~dp0"
echo ========== StarCore Git Push ==========
echo.
echo Staging all changes...
git add -A
echo.
echo Committing changes...
git commit -m "feat: 完成第三轮功能完善 (20个工作流)

## 第三轮完成功能

### 1. 每日/任务系统
- QuestMenu GUI主菜单/每日/列表/详情/委托/进度
- QuestMenuListener 事件监听
- 每日凌晨4点自动重置
- 8种任务分类/6种难度

### 2. 数据库迁移系统
- DatabaseMigrationService 增强迁移服务
- MigrationCommand /dbmigrate命令
- EnhancedDataValidator 数据验证
- V8__pet_module.sql / V9__ranking_and_audit.sql

### 3. 国际化多语言支持
- messages_en_us.yml 英文语言文件
- LanguageCommand /language命令
- I18nManager实现MessageService接口
- PAPI占位符支持

### 4. 安全加固
- SqlSocialStateStorage SQL注入修复
- InputValidator 输入验证框架
- ConfigEncryptionService AES-256-GCM加密
- HMAC-SHA256 API认证

### 5. 性能优化
- CacheManager LRU/TTL/内存压力感知
- StarCoreScheduler 独立I/O线程池
- StarCoreEventBus 优先级系统
- DatabaseService 慢查询检测

### 6. GUI动画效果
- GuiAnimationManager 动画管理器
- MenuTransitionAnimator 过渡效果
- ParticleEffectManager 40+粒子效果
- SoundFeedbackManager 40+音效类型

### 7. 地图标记系统
- MapMarkerService 地图标记核心
- MarkerPermissionService 15种权限
- CustomMapMarker/DynamicMapMarker 自定义/动态标记
- WebMap API /api/markers端点

### 8. 统计报告系统
- PeriodicReportService 小时/日/周/月报告
- VisualizationService ASCII图表
- StatsExportService JSON/CSV/HTML导出
- StatsDashboardService 仪表盘

### 9. 测试覆盖
- BukkitMockFactory/DatabaseMockFactory
- BaseTest/DatabaseIntegrationTest基类
- NationServiceIntegrationTest/ArmyUnitTest
- .github/workflows/ci.yml CI配置
- JaCoCo覆盖率配置

### 10. 文档系统
- README.md 完整重构
- docs/COMMAND_REFERENCE.md 27命令分类
- docs/API_REFERENCE.md 完整API文档
- docs/DEVELOPER_GUIDE.md 开发者教程

---

## 三轮总计完成

### 第一轮
- 排名系统/战争持久化/税收真实数据
- 社交GUI/法庭执行/随机事件
- 社交持久化/领地保护/委托任务/国家模型

### 第二轮
- 科技系统/交易所/邮件/API服务
- 跨服通信/宠物/成就/经济区
- 表情动作/配置管理

### 第三轮
- 每日任务/数据库迁移/国际化
- 安全加固/性能优化/GUI动画
- 地图标记/统计报告/测试/文档

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
echo.
echo Pushing to remote...
git push
echo.
echo ========== Done! ==========
pause
