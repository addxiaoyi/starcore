# StarCore 现代化集成计划

## 目标
将以下17个参考插件的核心功能集成到StarCore，使菜单、玩法达到现代Minecraft插件水平。

## 需要集成的功能模块

### 1. 菜单系统 (优先级: 最高)
**参考**: Codex, DeluxeMenus, Towns-and-Nations, TownyMenus

需要实现:
- [ ] MenuRefreshService - 菜单自动刷新服务
- [ ] MenuConditionChecker - 条件显示系统(权限/状态/经济)
- [ ] UnifiedClickHandler - 统一点击事件处理
- [ ] BorderAnimator - 边框动画效果
- [ ] YAML配置增强 - 完整的占位符支持
- [ ] 分页迭代器统一化

### 2. 聊天系统 (优先级: 高)
**参考**: HuskChat, InteractiveChat, Gchat-towny

需要实现:
- [ ] ChannelManager - 聊天频道管理
- [ ] ChatFilter - 消息过滤(广告/刷屏/脏话)
- [ ] PrivateMessage - 私信系统
- [ ] SocialSpy - 社会观察
- [ ] ChatFormat - 格式系统(支持PlaceholderAPI)
- [ ] 点击链接/物品展示

### 3. 经济系统 (优先级: 高)
**参考**: CrazyAuctions, Treasury模块

需要实现:
- [ ] AuctionHouse - 拍卖行系统
- [ ] BidManager - 竞价管理
- [ ] Vault集成 - 统一经济API
- [ ] TransactionHistory - 交易历史

### 4. 边境系统 (优先级: 中)
**参考**: Town_Borders, TownyPlus

需要实现:
- [ ] BorderRenderer - 边境渲染(粒子/边界)
- [ ] BorderNotification - 进入边境通知
- [ ] ChunkOwnerDisplay - chunk所有者显示

### 5. 家/传送系统 (优先级: 中)
**参考**: HuskHomes

需要实现:
- [ ] HomeManager - 家管理(多个家)
- [ ] WarpManager - 公共传送点
- [ ] TeleportCooldown - 传送冷却
- [ ] PrivacySettings - 隐私设置

### 6. 房屋系统 (优先级: 中)
**参考**: Quarters

需要实现:
- [ ] PlotManager - 地块管理
- [ ] RentSystem - 租借系统
- [ ] PlotPermission - 权限控制

### 7. 地图集成 (优先级: 中)
**参考**: mc-xaero-map

需要实现:
- [ ] MapIntegration - 地图标记
- [ ] TownMarker - 城镇标记
- [ ] TerritoryHighlight - 领土高亮

### 8. 地标系统 (优先级: 低)
**参考**: TownyWaypoints

需要实现:
- [ ] WaypointManager - 导航点
- [ ] NavigatorGUI - 导航界面

### 9. Discord集成 (优先级: 低)
**参考**: TownsAndNations-DiscordSrv

需要实现:
- [ ] DiscordBot - Discord机器人
- [ ] ChannelSync - 频道同步
- [ ] NotificationBridge - 通知桥接

### 10. Geyser支持 (优先级: 高)
**参考**: Geyser API文档

需要实现:
- [ ] GeyserIntegration - Geyser API集成
- [ ] BedrockDetector - 检测基岩版玩家
- [ ] CustomItemSupport - 自定义物品支持
- [ ] FormSupport - 表单支持(基岩版特有)

## 现代化改进

### 依赖库升级
1. **数据库**: HikariCP 5.x (已有) → 保持
2. **JSON**: Jackson (添加)
3. **Discord**: JDA 5.x (添加)
4. **网络**: Netty (如需要)

### 代码改进
1. 异步数据库操作
2. 缓存层优化
3. 事件驱动架构
4. 配置热重载
5. 完整的日志记录

### 中文本地化
- 所有消息使用lang键值
- 完整的中文翻译文件
- 颜色代码标准化

## 实施顺序

1. **Phase 1**: 菜单系统核心改进
2. **Phase 2**: Geyser支持
3. **Phase 3**: 聊天系统
4. **Phase 4**: 经济系统
5. **Phase 5**: 边境/家/房屋
6. **Phase 6**: 地图/地标
7. **Phase 7**: Discord集成
8. **Phase 8**: 优化和完善

## 预期效果

- 菜单美观流畅，支持动态刷新
- 完整支持基岩版玩家
- 聊天功能丰富(频道/过滤/私信)
- 经济系统完整(拍卖/交易)
- 边境/家/房屋功能完善
- 地图集成
- Discord通知
