# Git Push Script for StarCore
cd "D:\qwq\项目\mapadd"

# Add all changes
git add -A

# Commit with message
git commit -m "feat: 完成第三轮功能完善 (社会模拟/成就/跨服/地图标记/统计报告)

## 主要更新

### 社会模拟系统
- 声望服务 (ReputationService)
- 关系网络 (RelationshipNetwork)
- 影响力服务 (SocialInfluenceService)
- 文化服务 (CultureService)
- 八卦系统 (GossipService)
- 活动服务 (SocialActivityService)

### 成就系统
- AchievementModule 成就模块
- AchievementService 成就服务
- 15个成就分类

### 跨服通信
- CrossServerChatSync 跨服聊天
- CrossServerNationSync 国家同步
- CrossServerWarSync 战争同步

### 地图标记系统
- MapMarkerService 标记核心
- CustomMapMarker 自定义标记

### GUI动画系统
- GuiAnimationManager 动画管理
- SoundFeedbackManager 音效反馈

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"

# Push to remote
git push

# Show result
echo "Push completed!"
