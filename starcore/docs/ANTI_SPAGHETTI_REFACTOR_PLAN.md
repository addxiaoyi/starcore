# STARCORE 反屎山重构计划

日期: 2026-06-09

## 当前风险结论

STARCORE 还没有烂掉，但已经出现两个需要马上控规模的热点:

- `MapModule.java`: 约 2280 行，仍混合 Web 路由、viewer 数据准备和真实地形采样。
- `StarCoreCommand.java`: 约 2536 行，仍混合 root 分发、Tab 补全、GUI、财政、外交、战争和部分管理命令；资源命令族已抽出。

这两个文件后续不能继续直接塞新功能。新增功能如果必须接触它们，优先抽出小服务或 handler。

## 立即规则

- 不再把分析/缓存产物放进 `src/main/java`。
- `graphify-out/`、`.codegraph/` 这类目录必须留在项目根或工具缓存目录，并通过 `.gitignore` 忽略。
- 财政/事件流水分类继续向单一服务收口，避免命令和网页分别维护匹配规则。
- 新增网页 API 时，先建 endpoint/support 类，再由 `MapModule` 做薄路由。
- 新增命令时，先建 command handler/support 类，再由 `StarCoreCommand` 做薄分发。

## 拆分优先级

### P0: 清理源码树污染

目标:

- 删除 `src/main/java/**/graphify-out/`
- `.gitignore` 忽略 `.codegraph/`、`graphify-out/`、`**/graphify-out/`

验证:

```text
rg --files src/main/java | rg "graphify-out|\\.json$" 应为空
mvn -q -DskipTests compile
```

### P1: 抽 LedgerCategoryService

目标:

- 从 `ConfigurationService`、`StarCoreCommand`、`MapModule` 抽出财政/事件流水分类服务。
- 统一处理:
  - 分类名归一化
  - 中文/英文别名
  - `event-types` 精确匹配
  - `prefixes` 前缀匹配
  - Tab 补全候选
  - 命令显示名

建议新文件:

```text
src/main/java/dev/starcore/starcore/module/event/LedgerCategoryService.java
src/main/java/dev/starcore/starcore/module/event/LedgerCategoryRules.java
src/test/java/dev/starcore/starcore/module/event/LedgerCategoryServiceTest.java
```

验证:

```text
mvn -q "-Dtest=LedgerCategoryServiceTest,StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心抽取。
- `StarCoreCommand` 的事件分类匹配、filter 归一化、显示名 suffix、Tab 补全候选已委托 `LedgerCategoryService`。
- `MapModule` 的网页财政 filter 归一化与匹配已委托 `LedgerCategoryService`。
- `ConfigurationService` 仍负责原始配置读取，后续如需继续瘦身，可把 ledger 默认规则完全迁到 `LedgerCategoryService`。

### P2: 拆 EventCommandHandler

目标:

- 把 `/sc ev list/audit/export/record/clear` 从 `StarCoreCommand` 搬到独立 handler。
- `StarCoreCommand` 只负责 root/subcommand 分发和共用工具。
- 导出 CSV/JSON 渲染可以继续拆成 `EventExportRenderer`。

建议新文件:

```text
src/main/java/dev/starcore/starcore/command/EventCommandHandler.java
src/main/java/dev/starcore/starcore/command/EventExportRenderer.java
```

验证:

```text
mvn -q "-Dtest=StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `EventCommandHandler`，负责 `/sc ev` 的执行、Tab 补全、事件导出 CSV/JSON、时间窗口解析、事件上下文格式化。
- `StarCoreCommand` 仅保留 root 分发并委托事件命令 handler，已清理事件拆分遗留导入和旧别名方法。
- `EventExportRenderer` 暂未单独拆出；如果后续事件导出字段继续增加，再作为 P2.1 从 handler 内抽出。
- 已通过命令定向测试、全量测试与打包验证。

### P3: 拆 MapFinanceEndpoint

目标:

- 把 `/api/map/finance/events` 从 `MapModule` 搬出。
- 保留 `MapModule` 注册路由，实际构建响应交给 endpoint/support。
- CSV/JSON 渲染独立出来，避免后续财政面板继续膨胀 `MapModule`。

建议新文件:

```text
src/main/java/dev/starcore/starcore/module/map/finance/MapFinanceEndpoint.java
src/main/java/dev/starcore/starcore/module/map/finance/FinanceEventRenderer.java
```

验证:

```text
mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapFinanceEndpoint`，负责网页地图财政事件查询、分页、时间窗口、filter 归一化、CSV/JSON 导出和导出文件名。
- `MapModule` 保留 `/api/map/finance/events` 路由、访问鉴权、同步调度和原 `buildFinanceEventsResponse(MapViewerAccess, Map)` 反射测试入口，但业务实现已委托 endpoint。
- `MapModule` 仍保留 polygon/marker metadata 的财政汇总逻辑；后续如果继续增长，可在 P3.1 抽 `FinanceMetadataSupport`。
- 已通过地图契约测试、全量测试与打包验证。

### P4: 拆 TerrainTileService

目标:

- 把地形瓦片缓存、磁盘缓存、预热、PNG/二进制编码从 `MapModule` 搬出。
- `MapModule` 只保留 HTTP 参数解析和调用服务。

验证:

```text
mvn -q "-Dtest=MapModuleViewerSnapshotContractTest" test
node --check src/main/resources/web/map/js/map.js
```

状态:

- 2026-06-09 已完成 P4.1 编码层拆分。
- 新增 `TerrainTileRaster` 顶层包内模型，解除 terrain raster 对 `MapModule` 私有内部 record 的依赖。
- 新增 `TerrainTileCodec`，负责:
  - PNG 渲染编码
  - terrain-data JSON 编码
  - terrain-bin 二进制编码
  - 调色板与高度数组编码
- `MapModule` 的 `/api/map/terrain`、`/api/map/terrain-data`、`/api/map/terrain-bin` 仍保留路由和缓存调度，但输出格式编码已委托 `TerrainTileCodec`。
- 新增 `TerrainTileCodecTest` 锁住 JSON 字段、二进制头、PNG 魔数和基础调色板编码。
- 2026-06-09 已完成 P4.2 磁盘缓存层拆分。
- 新增 `TerrainTileKey` 顶层包内模型，解除 cache/prewarm/dirty tracking 对 `MapModule` 私有内部 key 的依赖。
- 新增 `TerrainTileDiskCache`，负责:
  - 磁盘缓存安全路径生成
  - PNG 缓存文件读取/写入
  - TTL 过期删除
  - 脏块失效删除
  - region 源文件更新时间失效
  - 记忆世界 region 目录
- 新增 `TerrainTileDiskCacheTest` 锁住路径安全、TTL、空文件、脏块失效、region 源文件更新和世界目录记忆。
- 2026-06-09 已完成 P4.3 内存缓存与渲染并发限制拆分。
- 新增 `TerrainTileMemoryCache`，负责:
  - PNG 内存缓存
  - terrain-bin 内存缓存
  - raster 内存缓存
  - TTL 过期
  - max entries 裁剪
  - 脏块/源文件失效回调
  - 三类缓存统一移除/清空
- 新增 `TerrainTileRenderLimiter`，负责地形 raster 渲染并发槽位 acquire/release/reset。
- 新增 `TerrainTileMemoryCacheTest` 与 `TerrainTileRenderLimiterTest` 锁住 TTL、max entries、invalidation、remove、并发限制和 release 兜底。
- 2026-06-09 已完成 P4.4 预热队列拆分。
- 新增 `TerrainPrewarmTile` 顶层包内模型，解除 prewarm 对 `MapModule` 私有内部 record 的依赖。
- 新增 `TerrainTilePrewarmService`，负责:
  - 按世界出生点生成候选瓦片
  - 按配置 tile size 顺序和距离排序
  - 按 max tiles 限量
  - poll/requeue/clear 队列操作
  - 忽略非法世界名和非法 tile size
- 新增 `TerrainTilePrewarmServiceTest` 锁住排序、限量、重建清空、非法输入跳过和 busy requeue 进入队尾。
- 2026-06-09 已完成 P4.5 脏块追踪与 terrain revision 广播拆分。
- 新增 `TerrainTileInvalidationService`，负责:
  - 方块变化映射到受影响的多级地形瓦片 key
  - 脏瓦片时间戳记录
  - 依据磁盘缓存 TTL 与最大条目数裁剪 dirty map
  - 内存/磁盘缓存失效判断
  - render 完成后的 dirty 清理
  - terrain revision 单调递增
  - revision broadcast 任务排队与取消
- 新增 `TerrainTileInvalidationServiceTest` 锁住负坐标 tile 对齐、邻居 tile、dirty 判断、render 清理、TTL/max entries 裁剪、revision 单调递增和非法配置跳过。
- `MapModule` 现在只保留 Bukkit 方块事件入口、缓存移除和实际地图刷新回调；dirty map、revision 和 broadcast task id 已不再堆在 `MapModule` 中。
- P4 地形瓦片主线已完成编码、磁盘缓存、内存缓存、渲染并发限制、预热队列、脏块追踪和 revision 广播拆分。
- 2026-06-09 已完成 P4.6 地形 dirty tracking 配置化。
- 新增中文注释配置:
  - `map.web.terrain-dirty-tile-sizes`
  - `map.web.terrain-dirty-max-entries`
- `MapModule` 不再硬编码 dirty tile 粒度和 dirty map 上限，启动时从 `ConfigurationService` 读取配置后创建 `TerrainTileInvalidationService`。
- 新增 `ConfigurationServiceTerrainConfigTest` 锁住 dirty tile size 默认值、非法值过滤、重复值去重和 max entries 上下限裁剪。
- 2026-06-09 已完成 P4.7 前后端地形瓦片像素尺寸配置化。
- 新增中文注释配置 `map.web.terrain-tile-pixels`，默认 `256`，允许 `64..512`。
- `MapModule` 的 raster 数组尺寸、采样循环、terrain JSON metadata 和二进制/JSON编码中的 tile size 都改为使用配置后的像素尺寸。
- 网页地图改为读取 snapshot `terrain.tileSize`，动态设置 Leaflet grid tileSize、canvas 尺寸和 terrain URL 的 worldSize 计算；tile size 变化时会重建地形图层。
- 旧的前端 `tileSize: 256`、canvas 256、`Math.round(256 / scale)` 硬编码已移除，只保留默认 fallback 256。
- 新增/扩展测试锁住配置裁剪与地图 metadata 输出。
- 2026-06-09 已完成 P4.8 `TerrainTileService` 外壳抽取。
- 新增 `TerrainTileService`，负责:
  - PNG / terrain-bin / raster 三类缓存编排
  - 内存缓存命中与写入
  - PNG 磁盘缓存读取/异步写入
  - raster render Future 合并，避免同一 tile 并发重复渲染
  - 渲染并发槽位 acquire/release
  - dirty tile 后统一移除三类缓存和正在渲染的 future
  - revision broadcast 委托
- 新增顶层 `TerrainTileBusyException`，供服务和 HTTP handler 共用。
- `MapModule` 现在只保留 HTTP 参数解析、Bukkit 主线程调度、真实世界 raster 取样和地形 metadata 输出；缓存/磁盘/并发/dirty 移除编排已委托 `TerrainTileService`。
- `TerrainTileDiskCache` 路径新增 tile pixels 分层，避免 `terrain-tile-pixels` 从 256 改到 128/512 后复用旧 PNG。
- 新增 `TerrainTileServiceTest`，并扩展 `TerrainTileDiskCacheTest`，锁住内存缓存、磁盘缓存复用、busy 异常、dirty 后重渲染、不同 tilePixels 磁盘缓存隔离。
- 2026-06-09 已完成 P4.9 `TerrainTileEndpoint` 抽取。
- 新增 `TerrainTileEndpoint`，负责:
  - `/api/map/terrain`、`/api/map/terrain-data`、`/api/map/terrain-bin` 的 `world/x/z/size` 参数解析
  - `size` 服务端裁剪到 `1..4096`
  - PNG / terrain-bin / terrain-data 三种响应体构建
  - `400 Missing/Invalid`、`404 World not found`、`503 renderer busy` 响应构建
  - content type 与 cache max-age 输出
- `MapModule` 的地形 HTTP handler 现在只负责 CORS、GET 检查、配置启用检查、访问鉴权、写出 `HttpExchange`。
- 新增 `TerrainTileEndpointTest`，锁住三种格式响应、缺参、非法参数、size 裁剪、空世界和 busy typed error。
- 2026-06-09 已完成 P4.10 `TerrainWorldMetadataService` 抽取。
- 新增 `TerrainWorldMetadataService`，负责:
  - 地图 snapshot 中 `terrain` metadata JSON 构建
  - Bukkit 世界解析后的 spawn/seaLevel 输出
  - 扫描世界 `region/*.mca` 计算已生成地图边界
  - 无 region 时按出生点回退 512x512 范围
  - 通过回调继续让 `TerrainTileService` 记忆世界目录
- `MapModule.appendTerrainMetadata(...)` 现在只保留薄委托，不再直接维护 region 文件扫描和地形 metadata JSON。
- 新增 `TerrainWorldMetadataServiceTest`，锁住已生成 region 边界、缺失世界跳过、无 region 回退、tileSize 裁剪和 remember 回调。

### P5: 拆 MapAvatarEndpoint

目标:

- 把 `/api/map/avatar` 的参数校验、头像回源、缓存命中、缓存降级和缓存清理从 `MapModule` 搬出。
- `MapModule` 只保留 CORS、GET 检查、settings 组装和 `HttpExchange` 写出。
- 后续玩家头像、RPG 地图玩家面板、离线头像源扩展不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapAvatarEndpointTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapAvatarEndpoint`，负责:
  - `/api/map/avatar?id=<uuid>` 的 `id` 校验
  - 按配置 upstream 顺序回源并支持 `{uuid}` / `{uuidNoDash}` 占位符
  - cache enabled 时读写 `cache/avatars/<uuid>.png`
  - 新下载头像输出 1h max-age，命中新鲜缓存输出 24h max-age
  - 上游不可用时回退已有旧缓存 5min max-age
  - 缓存清理删除超过 TTL 的头像文件
- `MapModule` 清理旧 `downloadAvatar*`、`isAvatarCacheFresh`、`avatarCacheFile` 和未使用的 `writeFile(...)` helper。
- 新增 `MapAvatarEndpointTest`，锁住缺参/非法 UUID、新鲜缓存、回源重试、禁用缓存不写入、旧缓存降级、502、cleanup 和 upstream URL 格式化。

### P6: 拆 MapClaimEndpoint

目标:

- 把 `/api/map/claim/preview` 与 `/api/map/claim/request` 的选择参数、预览 JSON、pending 请求、冷却和确认前校验从 `MapModule` 搬出。
- `MapModule` 只保留 CORS、POST/开关/访问鉴权、请求体解析、Bukkit 主线程同步、玩家通知和真实 `NationService` 调用。
- 后续地图圈地、RPG 服务器玩家面板和圈地工具扩展不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapClaimEndpointTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapClaimEndpoint`，负责:
  - `world/minX/maxX/minZ/maxZ` 选择参数校验并生成 `ChunkClaimSelection`
  - 选区过大时直接返回不可提交预览，不再调用 nation preview
  - 统一渲染网页圈地预览 JSON 与 pricing 明细
  - pricing 明细继续按 `price DESC -> distance DESC -> chunkX -> chunkZ` 排序
  - pending web claim 创建、过期清理、cooldown 记录和确认前 owner/过期校验
- `MapModule` 清理旧 `PendingWebClaim`、`pendingWebClaims`、`webClaimCooldowns`、`claimSelectionFromParams(...)`、`claimPreviewJson(...)` 和 pricing JSON helper。
- 新增 `MapClaimEndpointTest`，锁住参数校验、过大选区不调用 preview、pending/cooldown、确认 owner 校验与 pending 消耗。
- `MapModuleViewerSnapshotContractTest` 的价格排序护栏已改为直接测试 `MapClaimEndpoint.previewJson(...)`，不再反射 `MapModule` 私有方法。

### P7: 拆 MapResourceDistrictEndpoint

目标:

- 把 `/api/map/resource-district/migrate` 的响应构建、状态码映射、迁移结果 JSON 和网页指挥字段从 `MapModule` 搬出。
- `MapModule` 只保留 HTTP 外壳、访问鉴权、请求体解析、同步调度、服务/玩家查找和刷新广播回调。
- 后续资源区块迁移、资源指挥面板、RPG 服务器资源玩法扩展不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapResourceDistrictEndpointTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapResourceDistrictEndpoint`，负责:
  - disabled / player-offline / success 等迁移响应构建
  - `district-not-found / leader-only / already-waiting / insufficient-balance / player-offline` 状态码映射
  - 迁移结果 JSON 输出
  - 国家概览、资源产能预报、迁移花费、viewer 状态、迁移阶段/下一步/限制等网页字段组装
- `MapModule` 清理旧 `resourceDistrictMigrationJson(...)` 和 `resourceDistrictMigrationStatus(...)`。
- 新增 `MapResourceDistrictEndpointTest`，锁住 disabled 不调用 migrator、离线 viewer 返回 409、成功路径输出运营字段并触发刷新回调。
- 现有 `MapModuleViewerSnapshotContractTest` 继续锁住真实 `buildResourceDistrictMigrationResponse(...)` 的 JSON 契约。

### P8: 抽 ResourceDistrictMapMetadataSupport

目标:

- 把资源区块 marker metadata 的基础字段、产能预报字段和 viewer 迁移命令字段从 `MapModule` 搬出。
- `MapModule` 只保留 nation/district 服务查找、关系计算、marker 坐标和 support 调用。
- 后续资源区块地图面板、RPG 玩家信息面板、迁移按钮状态扩展优先改 support，不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=ResourceDistrictMapMetadataSupportTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `ResourceDistrictMapMetadataSupport`，负责:
  - 国家身份/等级/领地上限 metadata
  - 资源区块 biome/richness/剩余资源/总经验 metadata
  - 资源预期产能、每小时产能、未来 3 轮预报 metadata
  - beacon、nextRefreshAt、forceMigrationAt、pendingTarget metadata
  - viewer 迁移花费、余额缺口、角色、在线状态、按钮状态、阶段/下一步/限制 metadata
- `MapModule.markerForResourceDistrict(...)` 和 `appendResourceDistrictViewerMetadata(...)` 现在只做数据准备与 support 委托。
- 新增 `ResourceDistrictMapMetadataSupportTest`，锁住基础资源区块 metadata 和 viewer command metadata 输出。
- 现有 `MapModuleViewerSnapshotContractTest` 继续锁住真实地图 layer 与 viewer relation 场景。
- 2026-06-09 20:25 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P9: 抽 NationMapMetadataSupport

目标:

- 把国家领地 polygon metadata 的基础国家字段、最近事件字段和财政摘要字段从 `MapModule` 搬出。
- `MapModule` 只保留 relation/color 计算、服务查找、事件 resource id 解析和 support 委托。
- 后续 RPG 地图国家面板、财政面板、事件面板扩展优先改 support，不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=NationMapMetadataSupportTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `NationMapMetadataSupport`，负责:
  - 国家身份、政体、claim、成员、创始人 metadata
  - 国家等级/经验、城邦数量、资源区块数量 metadata
  - 最近事件 `recentEvent*` metadata 和已解析资源区块 marker id 写入
  - 财政 `financeEvent*`、分类合计、净额、treasury balance metadata
- `MapModule.polygonFor(...)`、`appendRecentNationEvents(...)` 和 `appendFinanceSummary(...)` 现在只做数据准备与 support 委托。
- 新增 `NationMapMetadataSupportTest`，锁住基础国家 metadata、最近事件 resource id、财政合计和最近 3 条财政事件输出。
- `MapModule.java` 从 2909 行降到 2798 行。
- 2026-06-09 20:35 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P10: 抽 MapSnapshotJsonWriter

目标:

- 把地图 snapshot JSON 的顶层渲染、access、summary、worlds、terrain 插槽、diplomacy matrix、layers / territories / markers / metadata 字段输出从 `MapModule` 搬出。
- `MapModule` 只保留快照过滤、viewer 数据准备、terrain metadata 回调和 HTTP/SSE 外壳。
- 后续网页地图/RPG 玩家面板字段扩展优先改 writer 或专用 support，不再继续在 `MapModule` 里手写大段 JSON 拼接。

验证:

```text
mvn -q "-Dtest=MapSnapshotJsonWriterTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapSnapshotJsonWriter`，负责:
  - snapshot 顶层 JSON、access、summary、worlds metadata
  - terrain JSON 插槽调用
  - diplomacy relation matrix
  - layer / territory / marker / metadata 渲染
  - JSON 字符串转义与 access scope 渲染
- `MapModule.toJson(...)` 现在只做数据准备与 writer 委托。
- 新增 `MapSnapshotJsonWriterTest`，锁住 public/private worlds、viewer/terrain 插槽、summary、diplomacy、layer/territory/marker/metadata 输出。
- `MapModule.java` 从 2798 行降到 2633 行。
- 2026-06-09 22:57 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P11: 抽 MapViewerJsonWriter

目标:

- 把访问玩家 viewer JSON 的余额、国家、等级/经验、上限、在线坐标和角色字段输出从 `MapModule` 搬出。
- `MapModule` 只保留 Bukkit 在线玩家、玩家档案、经济和 NationService 的数据准备。
- 后续网页地图“访问玩家详细信息”扩展优先改 writer/support，不再继续在 `MapModule` 里拼字段。

验证:

```text
mvn -q "-Dtest=MapViewerJsonWriterTest,MapSnapshotJsonWriterTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-09 已完成核心拆分。
- 新增 `MapViewerJsonWriter`，负责:
  - null viewer 输出
  - playerId/playerName/balance 输出
  - nationId/nationName/nationKind/founderName/government/role 输出
  - 国家等级/经验、claim、城邦、资源区块上限输出
  - 在线状态、world/x/y/z、founder 输出
  - JSON 字符串转义与金额字符串输出
- 新增 `MapViewerJsonWriter.ViewerDetails`，避免 writer 依赖 Bukkit、经济或 NationService。
- `MapModule.viewerJson(...)` 现在只做 `ViewerDetails` 数据准备与 writer 委托。
- 新增 `MapViewerJsonWriterTest`，锁住余额、国家角色、等级进度、资源区块上限、在线坐标、founder 和字符串转义。
- `MapModule.java` 从 2633 行降到 2583 行。
- 2026-06-09 23:31 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P12: 抽 MapSseBroadcaster

目标:

- 把网页地图 SSE client 列表、ready/snapshot 事件写入、过期访问关闭、写入失败移除和 closeAll 清理从 `MapModule` 搬出。
- `MapModule` 只保留 SSE HTTP 入口的 CORS、开关、访问鉴权、响应头和初始 snapshot 生成。
- 后续心跳、重连策略、客户端统计和广播性能优化优先改 broadcaster，不再继续撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapSseBroadcasterTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `MapSseBroadcaster`，负责:
  - SSE client 注册
  - `ready` 和初始 `snapshot` 事件写入
  - snapshot 广播
  - 过期访问关闭
  - 写入失败移除
  - closeAll 清理
  - clientCount / isEmpty 状态输出
- `MapModule.handleSse(...)`、`refreshCacheAndBroadcast(...)`、`disable(...)` 和 `buildHealthJson(...)` 现在只做 HTTP/生命周期外壳与 broadcaster 委托。
- 新增 `MapSseBroadcasterTest`，锁住注册 ready/snapshot、过期客户端关闭、换行清理、写入失败移除和 closeAll。
- `MapModule.java` 从 2583 行降到 2531 行。
- 2026-06-10 07:17 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P13: 抽 MapStaticFileHandler

目标:

- 把网页地图静态文件服务从 `MapModule` 内部 `StaticHandler` 搬出。
- `MapModule` 只保留根路由注册和网页导出目录解析。
- 静态资源路径安全、content type、cache-control 和 404 响应由独立 handler 维护，后续 Web 资源缓存/安全策略扩展不再撑大 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapStaticFileHandlerTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `MapStaticFileHandler`，负责:
  - 根路径 `index.html` 解析
  - 静态文件读取
  - HTML / JS / JSON / CSS / 图片 / 未知扩展名 content type
  - HTML no-store 与静态资源短缓存 cache-control
  - 缺失文件、目录请求、缺失首页和路径穿越 404
- handler 使用绝对归一化 root/target 校验，阻止 `../` 与反斜杠路径穿越。
- `MapModule` 清理内部 `StaticHandler` 和 `HttpHandler` 导入，根路由现在委托 `MapStaticFileHandler`。
- 新增 `MapStaticFileHandlerTest`，锁住首页、JS content type/cache、缺失文件、目录请求、缺失首页、路径穿越和未知扩展名。
- `MapModule.java` 从 2531 行降到 2477 行。
- 2026-06-10 07:32 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P14: 抽 MapHttpResponses

目标:

- 把网页地图 HTTP 响应写出、JSON/text/bytes 输出、附件响应、CORS header 和 OPTIONS preflight 从 `MapModule` 搬出。
- `MapModule` 只保留短期薄委托，后续可继续清理委托层或把路由 handler 直接调用 support。
- 后续新增地图 Web API 时优先复用 `MapHttpResponses`，不要在 endpoint 或 `MapModule` 里重复手写 content type、cache-control、CORS 和 `sendResponseHeaders`。

验证:

```text
mvn -q "-Dtest=MapHttpResponsesTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `MapHttpResponses`，负责:
  - JSON 响应 content type、no-store cache 和 CORS
  - text/bytes 响应 content type、短缓存或 no-store cache
  - 导出/附件响应 content type、`Content-Disposition` filename 清理和 no-store cache
  - OPTIONS preflight 的 204、允许方法/请求头、no-store cache 和 close
  - wildcard / 精确 origin / `Vary: Origin` CORS 输出
- `MapModule` 的 `writeJson`、`writeText`、`writeBytes`、`writeClaimResponse`、`handleCorsPreflight` 和 `applyCorsHeaders` 已改为薄委托。
- 新增 `MapHttpResponsesTest`，锁住 JSON no-store/CORS、bytes 短缓存、attachment filename 清理、OPTIONS preflight 和非 OPTIONS 不拦截。
- `MapModule.java` 从 2477 行降到 2435 行。
- 2026-06-10 08:30 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P15: 抽 MapHttpRequestParser

目标:

- 把网页地图 query、form body、JSON body 和请求参数合并逻辑从 `MapModule` 搬出。
- `MapModule` 不再维护低层 `URLDecoder`、form split、扁平 JSON 字符串解析和 JSON 小 record。
- 后续新增地图 Web API 时优先复用 `MapHttpRequestParser`，避免 claim/resource/finance/avatar 各自解析请求。

验证:

```text
mvn -q "-Dtest=MapHttpRequestParserTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `MapHttpRequestParser`，负责:
  - URI query 解码和无效片段跳过
  - query + POST body 参数合并
  - form body 解码且不覆盖 query 参数
  - JSON body 解码且覆盖 query 参数
  - 扁平 JSON 字符串转义和 primitive 值文本化
  - 空 body / 坏 JSON 兜底
- `MapModule` 清理 `requestParams(...)`、`parseQuery(...)`、`parseFormBody(...)`、`parseFlatJsonObject(...)`、`skipJsonWhitespace*`、`readJson*` 和 JSON 小 record。
- 新增 `MapHttpRequestParserTest`，锁住 query 解码、form 合并、JSON 覆盖、转义、primitive 值、空 body 和坏 JSON 兜底。
- `MapModule.java` 从 2435 行降到 2285 行。
- 2026-06-10 08:41 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P16: 抽 MapWebServer

目标:

- 把网页地图 `HttpServer` 生命周期、executor 创建/关闭和路由注册从 `MapModule` 搬出。
- `MapModule` 只负责按配置组装启动参数、传入 handler 方法引用、记录启动日志和 access-secret 警告。
- 后续新增网页地图路由时优先扩展 `MapWebServer.Routes`，不要把 `HttpServer.create(...)`、`createContext(...)` 和 executor 清理逻辑继续塞回 `MapModule`。

验证:

```text
mvn -q "-Dtest=MapWebServerTest,MapModuleViewerSnapshotContractTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `MapWebServer`，负责:
  - map web 启用判断
  - `HttpServer` 创建
  - cached thread pool 创建
  - `/api/map/*` 与 `/` 静态文件 route context 注册
  - executor 绑定和 server start
  - 启动失败时 stop server / shutdown executor 回滚
  - stop 时停止 server、关闭 executor 并清空引用
- `MapModule.startWebServer()` 现在只做 `Settings` / `Routes` 组装、启动日志和 access-secret 警告。
- `MapModule.disable(...)` 现在清理模块状态后委托 `webServer.stop()`。
- `MapModule.terrainTileSettings()` 继续通过 `webServer.executor()` 复用 Web executor，保持旧的地形瓦片异步写盘行为。
- 新增 `MapWebServerTest`，锁住禁用不创建 server、全部路由注册、executor 暴露、二次启动忽略、stop 清理和启动失败回滚。
- `MapModule.java` 从 2285 行降到 2278 行。
- 2026-06-10 09:20 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P17: 抽 StarCoreCommandAliases

目标:

- 把 `/sc` 根命令候选、中英文短别名和各命令族 subcommand 归一化从 `StarCoreCommand` 搬出。
- `StarCoreCommand` 只保留同名薄委托，避免后续加中文一字命令、英文短命令或 RPG 服习惯别名时继续拉长主命令类。
- 后续命令别名统一改 `StarCoreCommandAliases`，Tab 补全和真实分发必须共享同一套归一化入口。

验证:

```text
mvn -q "-Dtest=StarCoreCommandAliasesTest,StarCoreCommandTabCompletionTest,StarCoreCommandShortAliasDispatchTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `StarCoreCommandAliases`，负责:
  - root 补全候选
  - root 命令归一化
  - nation / economy / resolution / government / map / diplomacy / treasury / policy / resource / technology / war / officer 子命令归一化
  - shared simple alias 归一化
  - token trim + lowercase 归一化
- `StarCoreCommand` 顶部 root 候选常量已移除，Tab 补全改用 `StarCoreCommandAliases.rootSuggestions()`。
- `StarCoreCommand` 底部大段 alias switch 已移除，保留薄委托减少调用点改动面。
- 新增 `StarCoreCommandAliasesTest`，锁住 root 候选不可变、中英文 root/subcommand 归一化和 token 归一化。
- `StarCoreCommandTabCompletionTest` 与 `StarCoreCommandShortAliasDispatchTest` 继续锁住现有中文短命令补全和真实分发。
- `StarCoreCommand.java` 从 3026 行降到 2917 行。
- 2026-06-10 10:02 +08:00 已补全 `mvn -q test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

### P18: 抽 ResourceCommandHandler

目标:

- 把 `/sc rsc status/districts/inspect/migrate/grant/consume` 从 `StarCoreCommand` 搬到独立 handler。
- `StarCoreCommand` 只负责 root 分发和资源命令补全委托。
- 资源命令继续复用统一 alias support，避免 Tab 补全和真实分发各维护一套中文/短命令规则。
- 资源区块详情、迁移状态和收益预测继续复用现有 support/view model，避免命令、GUI、网页地图情报面漂移。

验证:

```text
mvn -q "-Dtest=ResourceCommandHandlerTest,StarCoreCommandShortAliasDispatchTest,StarCoreCommandTabCompletionTest,StarCoreCommandAliasesTest" test
```

状态:

- 2026-06-10 已完成核心拆分。
- 新增 `ResourceCommandHandler`，负责:
  - 资源命令执行入口
  - 资源命令 Tab 补全
  - 资源库存状态
  - 资源区块列表、查看和迁移
  - 管理员资源 grant/consume
  - 资源 grant/consume ledger context 和事件记录
- `StarCoreCommand` 的 `resource` root 分发现在委托 `resourceCommands.handle(...)`。
- `StarCoreCommand` 的资源补全现在委托 `resourceCommands.complete(...)`。
- 新增 `ResourceCommandHandlerTest`，锁住服务缺失、补全、grant 记账和 inspect 输出。
- `StarCoreCommand.java` 从 2917 行降到 2536 行。
- 2026-06-10 10:46 +08:00 已补全干净 `mvn -q clean test`、`mvn -q -DskipTests package` 和 jar SHA256 验证，详见 `LATEST_VERIFICATION_SUMMARY.md`。

## 判断标准

每轮重构结束后至少满足:

- `mvn -q -DskipTests compile` 通过。
- 改到命令就跑命令测试。
- 改到地图就跑地图契约测试。
- 不把新业务逻辑继续放进 `MapModule` 或 `StarCoreCommand`。
- 长任务状态写回 `docs/CONTINUATION_PLAN_2026-06-05.md`。
