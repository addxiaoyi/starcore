// STARCORE Strategic Map Viewer

const MapConfig = {
    chunkSize: 16,
    defaultCenter: [0, 0],
    defaultZoom: 4,
    minZoom: 1,
    maxZoom: 10,
    snapshotScriptUrl: './snapshot.js',
    snapshotJsonUrl: './snapshot.json',
    dataUrl: './api/map/snapshot',
    streamUrl: './api/map/stream',
    healthUrl: './api/map/health',
    avatarUrl: './api/map/avatar?id=',
    terrainUrl: './api/map/terrain',
    terrainDataUrl: './api/map/terrain-data',
    terrainBinaryUrl: './api/map/terrain-bin',
    claimPreviewUrl: './api/map/claim/preview',
    claimRequestUrl: './api/map/claim/request',
    eventLogUrl: './api/map/events',
    financeEventsUrl: './api/map/finance/events',
    liveMapUrl: 'http://127.0.0.1:8716/',
    transparentTile: 'data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==',
    terrainRetryDelays: [350, 900, 1800],
    refreshInterval: 5000,
};

const EVENT_LEDGER_CONTEXT_FIELDS = {
    actor: new Set(['actor', 'operator', 'player', 'viewer', 'member', 'target', 'targetname']),
    reason: new Set(['reason', 'cause', 'operation', 'action', 'policy', 'technology', 'relation', 'warid']),
    query: new Set(['source', 'resource', 'resourceid', 'resource_id', 'resourcedistrictid', 'resource-district-id', 'district', 'districtid', 'district_id']),
    facts: new Set(['amount', 'balance', 'members', 'claims', 'taxedmembers', 'skippedmembers', 'fixed', 'percent', 'minimumbalance']),
};

const STRINGS = {
    zh: {
        brand: 'STARCORE MAP',
        title: '战略地图',
        intro: '查看领地、国家关系与在线成员。',
        sectionOverview: '概览',
        sectionIntel: '态势',
        sectionWorld: '世界',
        sectionNations: '国家',
        sectionPlayers: '在线玩家',
        sectionResources: '资源区块',
        sectionLayers: '图层',
        nationDetailTitle: '国家运营详情',
        nationDetailIdle: '选择一个国家查看运营详情与相关资源区块状态。',
        nationDetailClaims: '领地区块',
        nationDetailResources: '资源区块',
        nationDetailActiveDistrict: '当前选中资源区块',
        nationDetailNoDistrict: '暂无选中资源区块',
        nationDetailCycleYield: '单轮收益',
        nationDetailRefresh: '刷新周期',
        nationDetailMigration: '迁移状态',
        nationDetailTarget: '当前目标',
        nationDetailFocusNation: '聚焦国家',
        nationDetailFocusResource: '定位资源区块',
        nationDetailSelectResource: '选中资源区块',
        nationDetailTotalYield: '国家总单轮收益',
        nationDetailManagedDistricts: '本国资源区块',
        nationDetailPendingMigrations: '待处理迁移',
        nationDetailPriorityQueue: '运营优先区块',
        nationDetailPriorityEmpty: '当前没有可展示的资源区块。',
        nationDetailPriorityReady: '可立即迁移',
        nationDetailPriorityAwaitingTarget: '等待放置目标',
        nationDetailPriorityWaitingDepletion: '等待旧区枯竭',
        nationDetailPriorityInsufficientBalance: '资金不足',
        nationDetailPriorityPlayerOffline: '领袖需上线',
        nationDetailPriorityLeaderOnly: '需国家领袖',
        nationDetailPriorityExternal: '只读观察',
        nationDetailPriorityUnknown: '状态待确认',
        districtExplanationLabel: '迁移说明',
        districtReasonLabel: '原因',
        districtExplanationSummaryLabel: '说明',
        districtExplanationShortfallLabel: '缺口',
        nationDetailFilterAll: '全部',
        nationDetailFilterReady: '可迁移',
        nationDetailFilterAwaitingTarget: '等选点',
        nationDetailFilterInsufficientBalance: '缺资金',
        nationDetailFilterWaitingDepletion: '等枯竭',
        nationDetailFilterPlayerOffline: '需上线',
        nationDetailFilterLeaderOnly: '仅领袖',
        nationDetailFilterEmpty: '当前筛选下没有资源区块。',
        nationDetailFilteredCount: '筛选命中',
        nationDetailFilteredYield: '筛选总收益',
        nationDetailTopPriority: '当前最高优先级',
        nationDetailOperationsSummary: '运营概览',
        nationDetailOperationsFocus: '当前操作焦点',
        nationDetailOperationsForecast: '运营预报',
        nationDetailOperationsRecommendations: '处理建议',
        nationDetailRecentLog: '最近操作日志',
        nationDetailRecentLogEmpty: '近期没有记录到国家操作事件。',
        nationDetailRecentLogType: '事件类型',
        nationDetailRecentLogCategory: '事件分类',
        nationDetailRecentLogAt: '发生时间',
        nationDetailRecentLogFiltered: '筛选命中',
        nationDetailEventLedger: '完整事件日志',
        nationDetailEventLedgerOpen: '查看完整日志',
        nationDetailEventLedgerRefresh: '刷新日志',
        nationDetailEventLedgerLoading: '正在读取事件日志...',
        nationDetailEventLedgerError: '事件日志读取失败。',
        nationDetailEventLedgerEmpty: '当前筛选下没有事件日志。',
        nationDetailEventLedgerPage: '第 {page}/{pages} 页',
        nationDetailEventLedgerTotal: '共 {count} 条',
        nationDetailEventLedgerPrevious: '上一页',
        nationDetailEventLedgerNext: '下一页',
        nationDetailEventLedgerResourceOnly: '仅当前资源',
        nationDetailEventLedgerAllResources: '全部资源',
        nationDetailEventLedgerExportCsv: '导出CSV',
        nationDetailEventLedgerExportJson: '导出JSON',
        nationDetailEventLedgerSearch: '上下文搜索',
        nationDetailEventLedgerSearchPlaceholder: '操作人 / 原因 / 战争 / 财政',
        nationDetailEventLedgerSearchApply: '搜索',
        nationDetailEventLedgerSearchClear: '清空',
        nationDetailEventLedgerFilterActor: '操作人',
        nationDetailEventLedgerFilterReason: '原因',
        nationDetailEventLedgerFilterContext: '上下文',
        nationDetailEventLedgerJumpScope: '跨事件定位',
        nationDetailEventLedgerOperationLink: '运营联动',
        nationDetailEventLedgerOperationState: '处理状态',
        nationDetailEventLedgerOperationAuth: '处理授权',
        nationDetailEventLedgerOperationOpen: '处理同类问题',
        nationDetailEventLedgerOperationContext: '事件上下文',
        nationDetailEventLedgerOperationFinance: '财政处理',
        nationDetailEventLedgerOperationWar: '战争处理',
        nationDetailEventLedgerOperationOfficer: '官员处理',
        nationDetailEventLedgerOperationDiplomacy: '外交处理',
        nationDetailEventLedgerOperationStrategy: '策略处理',
        nationDetailEventLedgerOperationTerritory: '领地处理',
        nationDetailEventLedgerOperationNation: '国家治理',
        nationDetailEventLedgerOperationFounder: '国家领袖 / 管理员',
        nationDetailEventLedgerOperationCommandGuard: '命令权限',
        nationDetailRecentFilterAll: '全部',
        nationDetailRecentFilterResource: '资源',
        nationDetailRecentFilterFinance: '财政',
        nationDetailRecentFilterOfficer: '官员',
        nationDetailRecentFilterDiplomacy: '外交',
        nationDetailRecentFilterWar: '战争',
        nationDetailRecentFilterStrategy: '策略',
        nationDetailRecentFilterTerritory: '领地',
        nationDetailRecentFilterNation: '国家',
        nationDetailRecentFilterOther: '其他',
        nationDetailOutlook: '当前态势',
        nationDetailFocusEmpty: '当前筛选下暂无可推进焦点。',
        nationDetailCurrentFilter: '当前分组',
        nationDetailFocusWindow: '关键时间',
        nationDetailFocusHint: '已锁定当前筛选下的焦点资源区块，可在本组内前后切换。',
        nationDetailFocusPosition: '焦点序号',
        nationDetailFocusPrevious: '上一条',
        nationDetailFocusNext: '下一条',
        nationDetailActionable: '可立刻处理',
        nationDetailWaiting: '流程等待',
        nationDetailBlocked: '受阻区块',
        nationDetailFilterCoverage: '筛选覆盖',
        nationDetailHourlyYield: '小时折算资源',
        nationDetailHourlyExperience: '小时折算经验',
        nationDetailLevelEta: '升级预计',
        nationDetailShortfallTotal: '总资金缺口',
        nationDetailShortfallPeak: '最高缺口',
        nationDetailEtaUnknown: '需更多经验来源',
        nationDetailNextRefreshWindow: '下次刷新',
        nationDetailNextForcedMigration: '下次强迁',
        nationDetailForecastRefreshWave: '1小时内刷新',
        nationDetailForecastForceWave: '6小时内强迁',
        nationDetailForecast6h: '6小时预估',
        nationDetailForecast12h: '12小时预估',
        nationDetailForecastTreasury: '预计国库',
        nextThreeCyclesLabel: '未来3轮',
        forecastWindowLabel: '预计时长',
        nationDetailLevelCycles: '升级预计轮次',
        nationDetailOperationsTimeline: '运营时间线',
        nationDetailTimelineEmpty: '近期没有需要排程的区块事件。',
        nationDetailTimelineRefresh: '资源刷新',
        nationDetailTimelineForcedMigration: '强制迁移',
        nationDetailTimelineTargetPlacement: '等待选点',
        nationDetailTimelineRestriction: '当前限制',
        nationDetailTimelineNextStep: '下一步',
        nationDetailTimelineDistrict: '资源区块',
        nationDetailTimelineInspect: '查看此区块',
        nationDetailTimelineFocus: '定位此区块',
        nationDetailTimelineHandle: '处理这一类',
        nationDetailRecommendationLead: '首要建议',
        nationDetailRecommendationCount: '影响区块',
        nationDetailRecommendationAction: '建议动作',
        nationDetailRecommendationPrimary: '优先处理',
        nationDetailRecommendationPrimaryReady: '打开可迁移区块',
        nationDetailRecommendationPrimaryAwaitingTarget: '去选迁移目标',
        nationDetailRecommendationPrimaryInsufficientBalance: '查看资金缺口',
        nationDetailRecommendationPrimaryPlayerOffline: '查看需上线区块',
        nationDetailRecommendationPrimaryLeaderOnly: '查看领袖专属区块',
        nationDetailRecommendationPrimaryWaitingDepletion: '查看枯竭进度',
        nationDetailRecommendationPrimaryDefault: '打开建议区块',
        nationDetailRecommendationHintReady: '优先处理这一组已满足条件且收益更高的区块。',
        nationDetailRecommendationHintAwaitingTarget: '先为迁移核心选定新的落点区块，让流程继续推进。',
        nationDetailRecommendationHintInsufficientBalance: '先补足迁移资金，再回来处理这一组区块。',
        nationDetailRecommendationHintPlayerOffline: '通知国家领袖上线后，再继续这一批迁移流程。',
        nationDetailRecommendationHintLeaderOnly: '这组区块只能由国家领袖亲自处理。',
        nationDetailRecommendationHintWaitingDepletion: '观察旧区是否采空，或等待强制迁移窗口到来。',
        nationDetailRecommendationHintDefault: '先查看这一组区块的共同限制，再决定下一步。',
        nationDetailRecommendationShortfall: '建议补足',
        nationDetailRecommendationTarget: '建议目标',
        nationDetailRecommendationLeader: '负责人',
        nationDetailRecommendationTargetUnset: '尚未设置',
        nationDetailForecastNoEvent: '暂无',
        nationDetailForecastReadyNow: '现在可触发',
        nationDetailForecastUpcoming: '即将到来',
        nationDetailForecastStable: '近期无强制迁移压力',
        nationDetailForecastRefreshLine: '1小时内预计 {count} 个区块刷新',
        nationDetailForecastForceLine: '6小时内预计 {count} 个区块进入强制迁移窗口',
        nationDetailForecastLead: '首个区块',
        nationDetailForecastAbsolute: '绝对时间',
        nationDetailOutlookAwaitingTarget: '优先补放目标区块',
        nationDetailOutlookActionable: '可继续推进迁移',
        nationDetailOutlookNeedFunds: '优先补足迁移资金',
        nationDetailOutlookLeaderOffline: '需领袖上线处理',
        nationDetailOutlookWaitingDepletion: '等待旧区枯竭',
        nationDetailOutlookStable: '产能稳定运行',
        nationDetailOutlookNoData: '暂无可评估区块',
        nationDetailOperationsActions: '运营快捷入口',
        nationDetailOpenGroup: '打开此组',
        nationDetailGroupCount: '区块数',
        nationDetailGroupLead: '首要区块',
        nationDetailOfficerAuthorizations: '官员授权',
        nationDetailOfficerResourceMigration: '资源迁移',
        nationDetailOfficerTreasuryWithdraw: '国库支出',
        nationDetailOfficerDiplomacySet: '外交调整',
        nationDetailOfficerWarDeclare: '宣战',
        nationDetailOfficerWarEnd: '停战',
        nationDetailOfficerPolicySet: '启用政策',
        nationDetailOfficerPolicyClear: '清除政策',
        nationDetailOfficerTechnologyUnlock: '解锁科技',
        nationDetailOfficerTechnologyRevoke: '撤销科技',
        nationDetailOfficerStatusFounder: '可操作 · 领袖',
        nationDetailOfficerStatusOfficer: '可操作 · 官职 {role}',
        nationDetailOfficerStatusNeedAppointment: '需任命',
        nationDetailOfficerStatusExternal: '非本国',
        nationDetailOfficerStatusAnonymous: '未认证',
        nationDetailOfficerStatusNoRole: '未配置',
        nationDetailOfficerStatusUnknown: '状态未知',
        nationDetailFinanceSummary: '财政概览',
        nationDetailTreasuryBalance: '金库余额',
        nationDetailFinanceNet: '近期净额',
        nationDetailFinanceIncome: '日常收入',
        nationDetailFinanceResourceIncome: '资源产出',
        nationDetailFinanceReward: '任务奖励',
        nationDetailFinanceTax: '玩家税收',
        nationDetailFinanceDeposit: '管理员存入',
        nationDetailFinanceWithdraw: '金库支出',
        nationDetailFinanceEvents: '财政流水',
        nationDetailFinanceEmpty: '近期没有财政流水。',
        nationDetailFinanceLedger: '完整流水',
        nationDetailFinanceLedgerOpen: '查看全部流水',
        nationDetailFinanceLedgerRefresh: '刷新流水',
        nationDetailFinanceLedgerLoading: '正在读取财政流水...',
        nationDetailFinanceLedgerError: '财政流水读取失败。',
        nationDetailFinanceLedgerPage: '第 {page}/{pages} 页',
        nationDetailFinanceLedgerTotal: '共 {count} 条',
        nationDetailFinanceLedgerPrevious: '上一页',
        nationDetailFinanceLedgerNext: '下一页',
        nationDetailFinanceLedgerFilterAll: '全部',
        nationDetailFinanceLedgerFilterResource: '资源产出',
        nationDetailFinanceLedgerFilterIncome: '日常收入',
        nationDetailFinanceLedgerFilterReward: '任务奖励',
        nationDetailFinanceLedgerFilterTax: '玩家税收',
        nationDetailFinanceLedgerFilterDeposit: '管理员存入',
        nationDetailFinanceLedgerFilterWithdraw: '金库支出',
        nationDetailFinanceLedgerRangeAll: '全部时间',
        nationDetailFinanceLedgerRange1h: '最近1小时',
        nationDetailFinanceLedgerRange24h: '最近24小时',
        nationDetailFinanceLedgerRange7d: '最近7天',
        nationDetailFinanceLedgerRangeCustom: '自定义',
        nationDetailFinanceLedgerRangeFrom: '开始时间',
        nationDetailFinanceLedgerRangeTo: '结束时间',
        nationDetailFinanceLedgerRangeApply: '应用时间',
        nationDetailFinanceLedgerExportCsv: '导出CSV',
        nationDetailFinanceLedgerExportJson: '导出JSON',
        nationDetailFinanceLedgerActor: '操作人',
        nationDetailFinanceLedgerReason: '原因',
        nationDetailFinanceLedgerBalance: '余额',
        displayScope: '显示范围',
        terrainLayer: '地形底图',
        territoryFootprint: '领地图层',
        memberPresence: '成员图层',
        resourceDistrictLayer: '资源区块',
        sidebarExpand: '展开情报面板',
        sidebarCollapse: '收起情报面板',
        claimTitle: '网页圈地',
        claimModeOff: '开始选区',
        claimModeOn: '退出选区',
        claimChunkCount: '区块数',
        claimPrice: '价格',
        claimBalance: '余额',
        claimSubmit: '提交确认',
        claimNeedAuth: '使用个人地图链接后可拖框圈地。',
        claimReady: '按住鼠标左键拖出矩形选区。',
        claimDragging: '松开鼠标完成选区。',
        claimPreviewing: '正在计算选区价格。',
        claimNoWorld: '请先选择一个具体世界。',
        claimTooSmall: '请拖出一个有效选区。',
        claimSubmitted: '已发送游戏内确认，请回到服务器输入确认命令。',
        claimSubmitFailed: '提交失败，请重新选择。',
        claimDisabled: '当前视图不能圈地。',
        claimDistance: '距离',
        claimRichness: '资源',
        claimCoverage: '领地容量',
        claimOverlap: '重叠区块',
        claimBaseUnitPrice: '基础单价',
        claimPricePerChunk: '均价',
        claimExplanation: '圈地解释',
        claimTopChunks: '高价区块',
        claimDistanceFactor: '距离系数',
        claimBiomeFactor: '生物群系系数',
        claimDetailMore: '其余 {count} 个区块已省略',
        claimCapacitySummary: '当前 {used}/{limit}，预览后 {next}/{limit}',
        footerSurface: 'STARCORE Strategic Map',
        heroKicker: 'LIVE MAP',
        heroCaption: '领地、关系与在线玩家一屏查看。',
        legendTerritory: '领地',
        legendAllied: '成员',
        legendNeutral: '中立',
        legendResource: '资源',
        legendRealtime: '实时',
        zoomIn: '放大',
        zoomOut: '缩小',
        resetView: '重置视图',
        languageSwitch: '语言切换',
        worldFilter: '世界筛选',
        allWorlds: '全部世界',
        summaryGenerated: '生成时间',
        summaryWorlds: '世界数',
        summaryNations: '国家数',
        summaryClaims: '领地数',
        summaryPlayers: '在线数',
        summaryResources: '资源点',
        intelMode: '数据模式',
        intelDominantNation: '主要国家',
        intelDensity: '在线密度',
        intelHealth: '服务状态',
        intelDominantNationDesc: '当前视图中领地最多的国家。',
        intelDensityDesc: '当前可见领地与在线成员的比值。',
        intelHealthDesc: '头像缓存{cache}，当前跟踪 {players} 名在线玩家。',
        intelHealthSockets: '{count} 个实时连接',
        viewerCard: '访问者',
        viewerAnonymous: '未识别身份',
        viewerAnonymousDesc: '使用个人地图链接后显示余额、国家等级和领地上限。',
        viewerBalanceLabel: '余额',
        viewerNationLabel: '国家',
        viewerLevelLabel: '等级',
        viewerClaimsLabel: '领地',
        viewerResourcesLabel: '资源区块',
        viewerGovernmentLabel: '政体',
        viewerExperienceLabel: '国家经验',
        viewerRoleLabel: '身份',
        viewerFounderLabel: '领袖',
        viewerCityStatesLabel: '城邦',
        viewerProgressLabel: '升级进度',
        viewerNextLevelLabel: '距下一级',
        viewerMaxLevel: '已满级',
        nationKindLabel: '国家类型',
        viewerPositionLabel: '坐标',
        viewerWorldLabel: '世界',
        viewerOnlineLabel: '在线状态',
        viewerOnlineYes: '在线',
        viewerOnlineNo: '离线',
        viewerRoleFounder: '国家领袖',
        viewerRoleMember: '国家成员',
        viewerRoleIndependent: '独立玩家',
        unlimited: '无限',
        modeRealtime: '实时',
        modeStatic: '静态',
        modePolling: '轮询',
        statusLoading: '载入中',
        statusConnected: '已连接',
        statusOffline: '离线',
        statusStaticExport: '静态导出',
        statusLiveStream: '实时流',
        statusLiveWeb: '实时接口',
        statusReconnecting: '重连中',
        statusAccessExpired: '访问已过期',
        statusWaitingData: '等待地图数据',
        statusPulseLabel: 'LIVE FEED',
        statusFeedLoading: '地图情报注入中',
        statusFeedConnected: '实时情报稳定',
        statusFeedPolling: 'HTTP 快照轮询中',
        statusFeedStatic: '静态快照已挂载',
        statusFeedReconnecting: '实时信号恢复中',
        statusFeedOffline: '情报流已中断',
        statusFeedExpired: '访问凭证已过期',
        detailStreaming: '实时连接 {clients} 个客户端，头像缓存{cache}。',
        detailStaticExport: '当前读取本地导出的静态快照。',
        detailPolling: '当前通过 HTTP 快照接口刷新数据。',
        detailReconnecting: '实时连接中断，正在尝试恢复。',
        detailAccessExpired: '当前个人地图链接已过期，请回到游戏内重新执行命令获取新链接。',
        detailOffline: '暂时无法获取实时数据，请检查地图接口或刷新页面。',
        detailFileMode: '实时地图需要通过 http://127.0.0.1:8716/ 打开，不能直接打开本地 index.html 文件。',
        detailFileRedirect: '正在打开实时地图服务。',
        detailRenderError: '地图数据已获取，但前端渲染失败，请刷新页面或查看浏览器控制台。',
        accessIp: 'IP 自动识别',
        accessSigned: '个人链接',
        accessPublic: '公共视图',
        accessFull: '管理员视图',
        accessAllied: '友军可见',
        accessUnauthenticated: '未识别身份',
        accessExpires: '到期 {time}',
        cacheEnabled: '已启用',
        cacheDisabled: '未启用',
        noTerritory: '暂无领地',
        unknown: '未知',
        worldLabel: '世界',
        governmentLabel: '政体',
        statusLabel: '关系',
        claimsLabel: '领地',
        membersLabel: '成员',
        resourcesLabel: '资源',
        sidebarSearchPlaceholder: '快速筛选国家 / 玩家 / 资源',
        sidebarQuickJump: '快速跳转',
        richnessLabel: '丰富度',
        experienceLabel: '经验',
        cycleResourcesLabel: '理论单轮资源',
        cycleExperienceLabel: '理论单轮经验',
        refreshCycleLabel: '理论刷新周期',
        migrationLabel: '迁移',
        migrationCostLabel: '迁移花费',
        migrationNone: '无',
        migrationAwaitingTarget: '等待选择目标',
        migrationWaitingDepletion: '等待旧资源枯竭',
        refreshLabel: '刷新',
        beaconLabel: '信标',
        targetLabel: '目标区块',
        forcedAtLabel: '强制迁移',
        biomeLabel: '生物群系',
        nationLabel: '国家',
        positionLabel: '坐标',
        districtStageLabel: '当前阶段',
        districtNextStepLabel: '下一步',
        districtRestrictionLabel: '当前限制',
        districtShortfallLabel: '资金缺口',
        noNation: '无所属国家',
        noNations: '暂无国家',
        noPlayersOnline: '暂无在线玩家',
        noResourceDistricts: '暂无资源区块',
        independent: '独立',
        member: '成员',
        allied: '盟友',
        friendly: '友好',
        hostile: '敌对',
        war: '交战',
        vassal: '附庸',
        neutral: '中立',
        claimUnit: '块',
        generatedUnknown: '未知',
        sidebarNoMatch: '没有匹配的情报项',
        governmentMonarchy: '君主制',
        governmentRepublic: '共和国',
        governmentDictatorship: '独裁制',
        governmentDemocracy: '民主制',
        governmentTheocracy: '神权制',
        governmentFederation: '联邦制',
        governmentEmpire: '帝国制',
        governmentOligarchy: '寡头制',
        governmentCouncil: '议会制',
        governmentUnknown: '未知',
        nationKindNation: '国家',
        nationKindCityState: '城邦',
        nationKindIndependent: '独立',
    },
    en: {
        brand: 'STARCORE MAP',
        title: 'Strategic Map',
        intro: 'View territories, diplomacy, and online members.',
        sectionOverview: 'Overview',
        sectionIntel: 'Intel',
        sectionWorld: 'Worlds',
        sectionNations: 'Nations',
        sectionPlayers: 'Players Online',
        sectionResources: 'Resource Districts',
        sectionLayers: 'Layers',
        nationDetailTitle: 'Nation Operations',
        nationDetailIdle: 'Select a nation to inspect its operational overview and linked resource districts.',
        nationDetailClaims: 'Claim Chunks',
        nationDetailResources: 'Resource Districts',
        nationDetailActiveDistrict: 'Selected Resource District',
        nationDetailNoDistrict: 'No resource district selected',
        nationDetailCycleYield: 'Cycle Yield',
        nationDetailRefresh: 'Refresh Cycle',
        nationDetailMigration: 'Migration State',
        nationDetailTarget: 'Active Target',
        nationDetailFocusNation: 'Focus Nation',
        nationDetailFocusResource: 'Focus District',
        nationDetailSelectResource: 'Select District',
        nationDetailTotalYield: 'Nation Cycle Yield',
        nationDetailManagedDistricts: 'Managed Districts',
        nationDetailPendingMigrations: 'Pending Migrations',
        nationDetailPriorityQueue: 'Priority District Queue',
        nationDetailPriorityEmpty: 'No resource districts are available for this nation.',
        nationDetailPriorityReady: 'Ready to Migrate',
        nationDetailPriorityAwaitingTarget: 'Awaiting Target Placement',
        nationDetailPriorityWaitingDepletion: 'Waiting for Depletion',
        nationDetailPriorityInsufficientBalance: 'Needs Funds',
        nationDetailPriorityPlayerOffline: 'Leader Must Be Online',
        nationDetailPriorityLeaderOnly: 'Leader Only',
        nationDetailPriorityExternal: 'Read-Only View',
        nationDetailPriorityUnknown: 'State Unknown',
        districtExplanationLabel: 'Migration Note',
        districtReasonLabel: 'Reason',
        districtExplanationSummaryLabel: 'Summary',
        districtExplanationShortfallLabel: 'Shortfall',
        nationDetailFilterAll: 'All',
        nationDetailFilterReady: 'Ready',
        nationDetailFilterAwaitingTarget: 'Awaiting Target',
        nationDetailFilterInsufficientBalance: 'Needs Funds',
        nationDetailFilterWaitingDepletion: 'Waiting',
        nationDetailFilterPlayerOffline: 'Leader Online',
        nationDetailFilterLeaderOnly: 'Leader Only',
        nationDetailFilterEmpty: 'No resource districts match the current filter.',
        nationDetailFilteredCount: 'Matching Districts',
        nationDetailFilteredYield: 'Filtered Yield',
        nationDetailTopPriority: 'Top Priority',
        nationDetailOperationsSummary: 'Operations Summary',
        nationDetailOperationsFocus: 'Operations Focus',
        nationDetailOperationsForecast: 'Operations Forecast',
        nationDetailOperationsRecommendations: 'Recommendations',
        nationDetailRecentLog: 'Recent Operations',
        nationDetailRecentLogEmpty: 'No recent nation operation events were recorded.',
        nationDetailRecentLogType: 'Event Type',
        nationDetailRecentLogCategory: 'Event Category',
        nationDetailRecentLogAt: 'Occurred At',
        nationDetailRecentLogFiltered: 'Filtered Events',
        nationDetailEventLedger: 'Full Event Log',
        nationDetailEventLedgerOpen: 'Open Full Log',
        nationDetailEventLedgerRefresh: 'Refresh Log',
        nationDetailEventLedgerLoading: 'Loading event log...',
        nationDetailEventLedgerError: 'Unable to load event log.',
        nationDetailEventLedgerEmpty: 'No events match the current filter.',
        nationDetailEventLedgerPage: 'Page {page}/{pages}',
        nationDetailEventLedgerTotal: '{count} total',
        nationDetailEventLedgerPrevious: 'Previous',
        nationDetailEventLedgerNext: 'Next',
        nationDetailEventLedgerResourceOnly: 'Current Resource Only',
        nationDetailEventLedgerAllResources: 'All Resources',
        nationDetailEventLedgerExportCsv: 'Export CSV',
        nationDetailEventLedgerExportJson: 'Export JSON',
        nationDetailEventLedgerSearch: 'Context Search',
        nationDetailEventLedgerSearchPlaceholder: 'Actor / reason / war / finance',
        nationDetailEventLedgerSearchApply: 'Search',
        nationDetailEventLedgerSearchClear: 'Clear',
        nationDetailEventLedgerFilterActor: 'Actor',
        nationDetailEventLedgerFilterReason: 'Reason',
        nationDetailEventLedgerFilterContext: 'Context',
        nationDetailEventLedgerJumpScope: 'Search All Event Types',
        nationDetailEventLedgerOperationLink: 'Operation Link',
        nationDetailEventLedgerOperationState: 'Operation State',
        nationDetailEventLedgerOperationAuth: 'Required Role',
        nationDetailEventLedgerOperationOpen: 'Handle Similar',
        nationDetailEventLedgerOperationContext: 'Event Context',
        nationDetailEventLedgerOperationFinance: 'Finance Ops',
        nationDetailEventLedgerOperationWar: 'War Ops',
        nationDetailEventLedgerOperationOfficer: 'Officer Ops',
        nationDetailEventLedgerOperationDiplomacy: 'Diplomacy Ops',
        nationDetailEventLedgerOperationStrategy: 'Strategy Ops',
        nationDetailEventLedgerOperationTerritory: 'Territory Ops',
        nationDetailEventLedgerOperationNation: 'Nation Governance',
        nationDetailEventLedgerOperationFounder: 'Nation Founder / Admin',
        nationDetailEventLedgerOperationCommandGuard: 'Command Gate',
        nationDetailRecentFilterAll: 'All',
        nationDetailRecentFilterResource: 'Resources',
        nationDetailRecentFilterFinance: 'Finance',
        nationDetailRecentFilterOfficer: 'Officers',
        nationDetailRecentFilterDiplomacy: 'Diplomacy',
        nationDetailRecentFilterWar: 'War',
        nationDetailRecentFilterStrategy: 'Strategy',
        nationDetailRecentFilterTerritory: 'Territory',
        nationDetailRecentFilterNation: 'Nation',
        nationDetailRecentFilterOther: 'Other',
        nationDetailOutlook: 'Current Outlook',
        nationDetailFocusEmpty: 'No actionable focus district is available in the current filter.',
        nationDetailCurrentFilter: 'Current Filter',
        nationDetailFocusWindow: 'Key Window',
        nationDetailFocusHint: 'Locked to the active focus district in the current filter, with previous and next navigation.',
        nationDetailFocusPosition: 'Focus Position',
        nationDetailFocusPrevious: 'Previous',
        nationDetailFocusNext: 'Next',
        nationDetailActionable: 'Ready Actions',
        nationDetailWaiting: 'Workflow Waiting',
        nationDetailBlocked: 'Blocked Districts',
        nationDetailFilterCoverage: 'Filter Coverage',
        nationDetailHourlyYield: 'Hourly Resource Yield',
        nationDetailHourlyExperience: 'Hourly XP Yield',
        nationDetailLevelEta: 'Level ETA',
        nationDetailShortfallTotal: 'Total Shortfall',
        nationDetailShortfallPeak: 'Peak Shortfall',
        nationDetailEtaUnknown: 'Need more XP sources',
        nationDetailNextRefreshWindow: 'Next Refresh',
        nationDetailNextForcedMigration: 'Next Forced Migration',
        nationDetailForecastRefreshWave: 'Refreshes in 1h',
        nationDetailForecastForceWave: 'Forced in 6h',
        nationDetailForecast6h: '6h Forecast',
        nationDetailForecast12h: '12h Forecast',
        nationDetailForecastTreasury: 'Treasury Forecast',
        nextThreeCyclesLabel: 'Next 3 Cycles',
        forecastWindowLabel: 'Forecast Window',
        nationDetailLevelCycles: 'Level-up Cycles',
        nationDetailOperationsTimeline: 'Operations Timeline',
        nationDetailTimelineEmpty: 'No district events need scheduling soon.',
        nationDetailTimelineRefresh: 'Resource Refresh',
        nationDetailTimelineForcedMigration: 'Forced Migration',
        nationDetailTimelineTargetPlacement: 'Awaiting Target',
        nationDetailTimelineRestriction: 'Restriction',
        nationDetailTimelineNextStep: 'Next Step',
        nationDetailTimelineDistrict: 'District',
        nationDetailTimelineInspect: 'Inspect District',
        nationDetailTimelineFocus: 'Focus District',
        nationDetailTimelineHandle: 'Handle This Type',
        nationDetailRecommendationLead: 'Lead Recommendation',
        nationDetailRecommendationCount: 'Affected Districts',
        nationDetailRecommendationAction: 'Suggested Action',
        nationDetailRecommendationPrimary: 'Handle First',
        nationDetailRecommendationPrimaryReady: 'Open Ready Districts',
        nationDetailRecommendationPrimaryAwaitingTarget: 'Choose Migration Target',
        nationDetailRecommendationPrimaryInsufficientBalance: 'Review Funding Gap',
        nationDetailRecommendationPrimaryPlayerOffline: 'Review Offline-Leader Districts',
        nationDetailRecommendationPrimaryLeaderOnly: 'Review Leader-Only Districts',
        nationDetailRecommendationPrimaryWaitingDepletion: 'Track Depletion Progress',
        nationDetailRecommendationPrimaryDefault: 'Open Recommendation',
        nationDetailRecommendationHintReady: 'Handle the highest-yield districts that are already ready to move.',
        nationDetailRecommendationHintAwaitingTarget: 'Choose a new landing district first so migration can continue.',
        nationDetailRecommendationHintInsufficientBalance: 'Top up migration funds before returning to this group.',
        nationDetailRecommendationHintPlayerOffline: 'Bring the nation leader online before continuing this batch.',
        nationDetailRecommendationHintLeaderOnly: 'Only the nation leader can complete this set of actions.',
        nationDetailRecommendationHintWaitingDepletion: 'Watch the old district empty out or wait for the forced window.',
        nationDetailRecommendationHintDefault: 'Review the shared restriction on this group before deciding the next step.',
        nationDetailRecommendationShortfall: 'Suggested Funding',
        nationDetailRecommendationTarget: 'Suggested Target',
        nationDetailRecommendationLeader: 'Owner',
        nationDetailRecommendationTargetUnset: 'Not set yet',
        nationDetailForecastNoEvent: 'None',
        nationDetailForecastReadyNow: 'Ready now',
        nationDetailForecastUpcoming: 'Soon',
        nationDetailForecastStable: 'No forced migration pressure soon',
        nationDetailForecastRefreshLine: '{count} district(s) expected to refresh within 1 hour',
        nationDetailForecastForceLine: '{count} district(s) expected to hit forced migration within 6 hours',
        nationDetailForecastLead: 'Lead District',
        nationDetailForecastAbsolute: 'Absolute Time',
        nationDetailOutlookAwaitingTarget: 'Target placement should be handled first',
        nationDetailOutlookActionable: 'Migration can keep moving',
        nationDetailOutlookNeedFunds: 'Treasury needs more migration funds',
        nationDetailOutlookLeaderOffline: 'Leader must come online',
        nationDetailOutlookWaitingDepletion: 'Waiting for depletion progress',
        nationDetailOutlookStable: 'Output is currently stable',
        nationDetailOutlookNoData: 'No districts available to evaluate',
        nationDetailOperationsActions: 'Operational Shortcuts',
        nationDetailOpenGroup: 'Open Group',
        nationDetailGroupCount: 'Districts',
        nationDetailGroupLead: 'Lead District',
        nationDetailOfficerAuthorizations: 'Officer Authorizations',
        nationDetailOfficerResourceMigration: 'Resource Migration',
        nationDetailOfficerTreasuryWithdraw: 'Treasury Spending',
        nationDetailOfficerDiplomacySet: 'Diplomacy Updates',
        nationDetailOfficerWarDeclare: 'Declare War',
        nationDetailOfficerWarEnd: 'End War',
        nationDetailOfficerPolicySet: 'Activate Policy',
        nationDetailOfficerPolicyClear: 'Clear Policy',
        nationDetailOfficerTechnologyUnlock: 'Unlock Tech',
        nationDetailOfficerTechnologyRevoke: 'Revoke Tech',
        nationDetailOfficerStatusFounder: 'Allowed · Leader',
        nationDetailOfficerStatusOfficer: 'Allowed · {role}',
        nationDetailOfficerStatusNeedAppointment: 'Needs Appointment',
        nationDetailOfficerStatusExternal: 'Other Nation',
        nationDetailOfficerStatusAnonymous: 'Sign In Required',
        nationDetailOfficerStatusNoRole: 'No Roles Configured',
        nationDetailOfficerStatusUnknown: 'Status Unknown',
        nationDetailFinanceSummary: 'Finance Summary',
        nationDetailTreasuryBalance: 'Treasury Balance',
        nationDetailFinanceNet: 'Recent Net',
        nationDetailFinanceIncome: 'Daily Income',
        nationDetailFinanceResourceIncome: 'Resource Income',
        nationDetailFinanceReward: 'RPG Rewards',
        nationDetailFinanceTax: 'Player Tax',
        nationDetailFinanceDeposit: 'Admin Deposits',
        nationDetailFinanceWithdraw: 'Treasury Spending',
        nationDetailFinanceEvents: 'Finance Events',
        nationDetailFinanceEmpty: 'No recent finance ledger entries.',
        nationDetailFinanceLedger: 'Full Ledger',
        nationDetailFinanceLedgerOpen: 'View Full Ledger',
        nationDetailFinanceLedgerRefresh: 'Refresh Ledger',
        nationDetailFinanceLedgerLoading: 'Loading finance ledger...',
        nationDetailFinanceLedgerError: 'Finance ledger failed to load.',
        nationDetailFinanceLedgerPage: 'Page {page}/{pages}',
        nationDetailFinanceLedgerTotal: '{count} entries',
        nationDetailFinanceLedgerPrevious: 'Previous',
        nationDetailFinanceLedgerNext: 'Next',
        nationDetailFinanceLedgerFilterAll: 'All',
        nationDetailFinanceLedgerFilterResource: 'Resources',
        nationDetailFinanceLedgerFilterIncome: 'Daily',
        nationDetailFinanceLedgerFilterReward: 'Rewards',
        nationDetailFinanceLedgerFilterTax: 'Tax',
        nationDetailFinanceLedgerFilterDeposit: 'Deposits',
        nationDetailFinanceLedgerFilterWithdraw: 'Spending',
        nationDetailFinanceLedgerRangeAll: 'All Time',
        nationDetailFinanceLedgerRange1h: 'Last 1h',
        nationDetailFinanceLedgerRange24h: 'Last 24h',
        nationDetailFinanceLedgerRange7d: 'Last 7d',
        nationDetailFinanceLedgerRangeCustom: 'Custom',
        nationDetailFinanceLedgerRangeFrom: 'From',
        nationDetailFinanceLedgerRangeTo: 'To',
        nationDetailFinanceLedgerRangeApply: 'Apply Time',
        nationDetailFinanceLedgerExportCsv: 'Export CSV',
        nationDetailFinanceLedgerExportJson: 'Export JSON',
        nationDetailFinanceLedgerActor: 'Actor',
        nationDetailFinanceLedgerReason: 'Reason',
        nationDetailFinanceLedgerBalance: 'Balance',
        displayScope: 'Display Scope',
        terrainLayer: 'Terrain',
        territoryFootprint: 'Territory Layer',
        memberPresence: 'Player Layer',
        resourceDistrictLayer: 'Resources',
        sidebarExpand: 'Open Intel',
        sidebarCollapse: 'Hide Intel',
        claimTitle: 'Web Claim',
        claimModeOff: 'Select Area',
        claimModeOn: 'Exit Select',
        claimChunkCount: 'Chunks',
        claimPrice: 'Price',
        claimBalance: 'Balance',
        claimSubmit: 'Submit',
        claimNeedAuth: 'Open your personal map link to claim land.',
        claimReady: 'Drag on the map to select a rectangle.',
        claimDragging: 'Release to finish the selection.',
        claimPreviewing: 'Calculating selection price.',
        claimNoWorld: 'Select one world first.',
        claimTooSmall: 'Drag a valid selection.',
        claimSubmitted: 'Confirmation sent in-game. Run the confirm command on the server.',
        claimSubmitFailed: 'Submit failed. Select again.',
        claimDisabled: 'Claiming is unavailable in this view.',
        claimDistance: 'Distance',
        claimRichness: 'Richness',
        claimCoverage: 'Claim Capacity',
        claimOverlap: 'Overlaps',
        claimBaseUnitPrice: 'Base Unit Price',
        claimPricePerChunk: 'Avg Per Chunk',
        claimExplanation: 'Claim Reasons',
        claimTopChunks: 'Top Chunks',
        claimDistanceFactor: 'Distance Factor',
        claimBiomeFactor: 'Biome Factor',
        claimDetailMore: '{count} more chunks omitted',
        claimCapacitySummary: 'Current {used}/{limit}, preview {next}/{limit}',
        footerSurface: 'STARCORE Strategic Map',
        heroKicker: 'LIVE MAP',
        heroCaption: 'Territories, relations, and online players in one view.',
        legendTerritory: 'Territory',
        legendAllied: 'Members',
        legendNeutral: 'Neutral',
        legendResource: 'Resources',
        legendRealtime: 'Realtime',
        zoomIn: 'Zoom in',
        zoomOut: 'Zoom out',
        resetView: 'Reset view',
        languageSwitch: 'Language switch',
        worldFilter: 'World filter',
        allWorlds: 'All Worlds',
        summaryGenerated: 'Generated',
        summaryWorlds: 'Worlds',
        summaryNations: 'Nations',
        summaryClaims: 'Claims',
        summaryPlayers: 'Players',
        summaryResources: 'Resources',
        intelMode: 'Mode',
        intelDominantNation: 'Dominant Nation',
        intelDensity: 'Density',
        intelHealth: 'Server Health',
        intelDominantNationDesc: 'Nation with the largest visible claim count.',
        intelDensityDesc: 'Visible online members per visible territorial claim.',
        intelHealthDesc: 'Avatar cache {cache}, currently tracking {players} online players.',
        intelHealthSockets: '{count} live sockets',
        viewerCard: 'Viewer',
        viewerAnonymous: 'Unauthenticated',
        viewerAnonymousDesc: 'Open a personal map link to show balance, nation level, and claim limits.',
        viewerBalanceLabel: 'Balance',
        viewerNationLabel: 'Nation',
        viewerLevelLabel: 'Level',
        viewerClaimsLabel: 'Claims',
        viewerResourcesLabel: 'Resources',
        viewerGovernmentLabel: 'Government',
        viewerExperienceLabel: 'Nation XP',
        viewerRoleLabel: 'Role',
        viewerFounderLabel: 'Leader',
        viewerCityStatesLabel: 'City-States',
        viewerProgressLabel: 'Progress',
        viewerNextLevelLabel: 'To Next Level',
        viewerMaxLevel: 'Max Level',
        nationKindLabel: 'Nation Type',
        viewerPositionLabel: 'Position',
        viewerWorldLabel: 'World',
        viewerOnlineLabel: 'Status',
        viewerOnlineYes: 'Online',
        viewerOnlineNo: 'Offline',
        viewerRoleFounder: 'Nation Leader',
        viewerRoleMember: 'Nation Member',
        viewerRoleIndependent: 'Independent',
        unlimited: 'unlimited',
        modeRealtime: 'Realtime',
        modeStatic: 'Static',
        modePolling: 'Polling',
        statusLoading: 'Loading',
        statusConnected: 'Connected',
        statusOffline: 'Offline',
        statusStaticExport: 'Static Export',
        statusLiveStream: 'Live Stream',
        statusLiveWeb: 'Live API',
        statusReconnecting: 'Reconnecting',
        statusAccessExpired: 'Access Expired',
        statusWaitingData: 'Waiting for map data',
        statusPulseLabel: 'LIVE FEED',
        statusFeedLoading: 'Injecting map intel',
        statusFeedConnected: 'Realtime feed stabilized',
        statusFeedPolling: 'Polling snapshot endpoint',
        statusFeedStatic: 'Static snapshot mounted',
        statusFeedReconnecting: 'Restoring live signal',
        statusFeedOffline: 'Feed interrupted',
        statusFeedExpired: 'Access token expired',
        detailStreaming: 'Streaming to {clients} clients, avatar cache {cache}.',
        detailStaticExport: 'Reading the exported local snapshot.',
        detailPolling: 'Refreshing data through the snapshot endpoint.',
        detailReconnecting: 'Realtime connection lost, retrying now.',
        detailAccessExpired: 'This personal map link has expired. Run the in-game command again to get a fresh link.',
        detailOffline: 'Realtime data is unavailable. Check the map API or refresh the page.',
        detailFileMode: 'Open the live map through http://127.0.0.1:8716/. Do not open the local index.html file directly.',
        detailFileRedirect: 'Opening the live map service.',
        detailRenderError: 'Map data was loaded, but the frontend failed to render it. Refresh the page or check the browser console.',
        accessIp: 'IP auto-detected',
        accessSigned: 'Personal link',
        accessPublic: 'Public view',
        accessFull: 'Admin view',
        accessAllied: 'Allies visible',
        accessUnauthenticated: 'Identity not detected',
        accessExpires: 'expires {time}',
        cacheEnabled: 'enabled',
        cacheDisabled: 'disabled',
        noTerritory: 'No territory',
        unknown: 'Unknown',
        worldLabel: 'World',
        governmentLabel: 'Government',
        statusLabel: 'Status',
        claimsLabel: 'Claims',
        membersLabel: 'Members',
        resourcesLabel: 'Resources',
        sidebarSearchPlaceholder: 'Filter nations / players / resources',
        sidebarQuickJump: 'Quick jump',
        richnessLabel: 'Richness',
        experienceLabel: 'Experience',
        cycleResourcesLabel: 'Expected Resources',
        cycleExperienceLabel: 'Expected Experience',
        refreshCycleLabel: 'Expected Refresh',
        migrationLabel: 'Migration',
        migrationCostLabel: 'Migration Cost',
        migrationNone: 'None',
        migrationAwaitingTarget: 'Awaiting target',
        migrationWaitingDepletion: 'Waiting for depletion',
        refreshLabel: 'Refresh',
        beaconLabel: 'Beacon',
        targetLabel: 'Target Chunk',
        forcedAtLabel: 'Forced Move',
        biomeLabel: 'Biome',
        nationLabel: 'Nation',
        positionLabel: 'Position',
        districtStageLabel: 'Stage',
        districtNextStepLabel: 'Next Step',
        districtRestrictionLabel: 'Restriction',
        districtShortfallLabel: 'Shortfall',
        noNation: 'No nation',
        noNations: 'No nations',
        noPlayersOnline: 'No players online',
        noResourceDistricts: 'No resource districts',
        independent: 'Independent',
        member: 'Member',
        allied: 'Allied',
        friendly: 'Friendly',
        hostile: 'Hostile',
        war: 'At War',
        vassal: 'Vassal',
        neutral: 'Neutral',
        claimUnit: 'c',
        generatedUnknown: 'Unknown',
        sidebarNoMatch: 'No matching intel',
        governmentMonarchy: 'Monarchy',
        governmentRepublic: 'Republic',
        governmentDictatorship: 'Dictatorship',
        governmentDemocracy: 'Democracy',
        governmentTheocracy: 'Theocracy',
        governmentFederation: 'Federation',
        governmentEmpire: 'Empire',
        governmentOligarchy: 'Oligarchy',
        governmentCouncil: 'Council',
        governmentUnknown: 'Unknown',
        nationKindNation: 'Nation',
        nationKindCityState: 'City-State',
        nationKindIndependent: 'Independent',
    },
};

class StrategicMap {
    constructor() {
        this.language = this.resolveLanguage();
        this.viewerAccess = this.resolveViewerAccess();
        this.map = null;
        this.terrainLayer = null;
        this.terrainWorld = '';
        this.terrainRevision = '';
        this.terrainTileSize = 256;
        this.territoryLayer = L.layerGroup();
        this.resourceLayer = L.layerGroup();
        this.playerLayer = L.layerGroup();
        this.nationColors = new Map();
        this.nationData = [];
        this.resourceMarkers = [];
        this.playerMarkers = [];
        this.refreshTimer = null;
        this.eventSource = null;
        this.streamActive = false;
        this.streamRetryTimer = null;
        this.streamReconnectDelay = 1500;
        this.accessExpired = false;
        this.apiUnavailable = false;
        this._localSnapshotAvailable = false;
        this._demoDataLoaded = false;
        this.currentWorld = 'all';
        this.selectedNationId = null;
        this.nationPriorityFilter = 'all';
        this.nationRecentLogFilter = 'all';
        this.snapshot = null;
        this.health = null;
        this.viewerInfo = null;
        this.financeLedgerState = {
            nationId: '',
            page: 1,
            size: 10,
            filter: 'all',
            range: 'all',
            from: '',
            to: '',
            loading: false,
            error: '',
            data: null,
        };
        this.eventLedgerState = {
            nationId: '',
            page: 1,
            size: 10,
            filter: 'all',
            range: 'all',
            from: '',
            to: '',
            resourceId: '',
            query: '',
            actor: '',
            reason: '',
            loading: false,
            error: '',
            data: null,
        };
        this.lastRenderKey = '';
        this.nationBounds = new Map();
        this.hasFittedView = false;
        this.pageAnimationPlayed = false;
        this.dataRevealQueued = false;
        this.sidebarCollapsed = this.resolveInitialSidebarState();
        this.playersCollapsed = true;
        this.showTerrainLayer = this.resolveInitialTerrainLayerVisibility();
        this.demoMode = this.resolveDemoMode();
        this.showTerritoryLayer = true;
        this.showResourceLayer = true;
        this.showPlayerLayer = true;
        this.claimMode = false;
        this.claimDragging = false;
        this.claimStart = null;
        this.claimSelection = null;
        this.claimPreview = null;
        this.claimRectangle = null;
        this.claimAuthenticated = false;
        this.claimSubmitting = false;
        this.selectedResourceId = '';
        this.selectedPlayerId = '';
        this.statusPulseKey = '';
        // 新控件状态
        this.radarMarkers = [];
        this.radarAnimFrame = null;
        this.searchPanelVisible = false;
        this.searchQuery = '';
        this.searchResults = [];
        this.navInterval = null;
        this.playerPosition = null; // 玩家当前位置

        this.setSidebarCollapsed(this.sidebarCollapsed, { animate: false });
        this.applyStaticI18n();
        this.initMap();
        this.bindEvents();
        this.initHudAnimations();
        this.initRadar();
        this.initQuickActions();
        this.loadData();
        this.connectStream();
        this.startAutoRefresh();
        this.refreshConnectionStatus();
    }

    resolveLanguage() {
        const stored = this.getStoredPreference('starcore-map-language');
        if (stored === 'zh' || stored === 'en') {
            return stored;
        }
        const browser = (navigator.language || '').toLowerCase();
        return browser.startsWith('zh') ? 'zh' : 'en';
    }

    getStoredPreference(key) {
        try {
            return window.localStorage?.getItem(key) || null;
        } catch (_err) {
            return null;
        }
    }

    setStoredPreference(key, value) {
        try {
            window.localStorage?.setItem(key, value);
        } catch (_err) {
            // Storage can be disabled in embedded or privacy-restricted browsers.
        }
    }

    resolveViewerAccess() {
        const params = new URLSearchParams(window.location.search || '');
        const viewer = params.get('viewer') || '';
        const access = params.get('access') || '';
        const exp = params.get('exp') || '';
        const sig = params.get('sig') || '';
        return { viewer, access, exp, sig };
    }

    resolveInitialTerrainLayerVisibility() {
        const value = String(new URLSearchParams(window.location.search || '').get('terrain') || '').toLowerCase();
        return value !== 'off' && value !== 'false' && value !== '0' && value !== 'none';
    }

    resolveInitialSidebarState() {
        const params = new URLSearchParams(window.location.search || '');
        const explicit = String(params.get('sidebar') || params.get('show_sidebar') || '').toLowerCase();
        if (explicit === 'open' || explicit === 'expanded' || explicit === 'true' || explicit === '1') {
            return false;
        }
        if (explicit === 'closed' || explicit === 'collapsed' || explicit === 'false' || explicit === '0') {
            return true;
        }
        return true;
    }

    resolveDemoMode() {
        const value = String(new URLSearchParams(window.location.search || '').get('demo') || '').toLowerCase();
        return value === '1' || value === 'true' || value === 'yes' || value === 'on';
    }

    isCompactLayout() {
        return Boolean(window.matchMedia && window.matchMedia('(max-width: 920px)').matches);
    }

    maybeCollapseSidebarAfterMapAction() {
        if (!this.isCompactLayout() || this.sidebarCollapsed || this.claimMode) {
            return;
        }
        window.setTimeout(() => this.setSidebarCollapsed(true), 120);
    }

    maybeDismissSidebarFromMapClick(event) {
        if (!this.isCompactLayout() || this.sidebarCollapsed || this.claimMode) {
            return;
        }
        const target = event && event.originalEvent ? event.originalEvent.target : null;
        if (target && typeof target.closest === 'function' && target.closest('.leaflet-interactive, .leaflet-marker-icon, .leaflet-tooltip, .leaflet-popup, .leaflet-control')) {
            return;
        }
        this.setSidebarCollapsed(true);
    }

    buildApiUrl(baseUrl) {
        const query = new URLSearchParams();
        if (this.viewerAccess.viewer) {
            query.set('viewer', this.viewerAccess.viewer);
        }
        if (this.viewerAccess.access) {
            query.set('access', this.viewerAccess.access);
        }
        if (this.viewerAccess.exp) {
            query.set('exp', this.viewerAccess.exp);
        }
        if (this.viewerAccess.sig) {
            query.set('sig', this.viewerAccess.sig);
        }
        const queryString = query.toString();
        if (!queryString) {
            return baseUrl;
        }
        return `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}${queryString}`;
    }

    t(key, vars = {}) {
        const bundle = STRINGS[this.language] || STRINGS.zh;
        const fallback = STRINGS.zh[key] ?? key;
        const template = bundle[key] ?? fallback;
        return String(template).replace(/\{(\w+)\}/g, (_match, name) => {
            return vars[name] == null ? '' : String(vars[name]);
        });
    }

    setLanguage(language) {
        if (!STRINGS[language] || this.language === language) {
            return;
        }
        this.language = language;
        this.setStoredPreference('starcore-map-language', language);
        this.applyStaticI18n();
        if (this.snapshot) {
            this.renderSnapshot(this.snapshot, { forceRender: true, allowPulse: false, skipFit: true });
        } else {
            this.populateWorldFilter({ worlds: [] });
        }
        this.refreshConnectionStatus();
    }

    applyStaticI18n() {
        document.documentElement.lang = this.language === 'zh' ? 'zh-CN' : 'en';
        document.querySelectorAll('[data-i18n]').forEach(node => {
            node.textContent = this.t(node.dataset.i18n);
        });
        document.querySelectorAll('[data-i18n-title]').forEach(node => {
            const value = this.t(node.dataset.i18nTitle);
            node.setAttribute('title', value);
            node.setAttribute('aria-label', value);
        });
        document.querySelectorAll('[data-i18n-aria]').forEach(node => {
            node.setAttribute('aria-label', this.t(node.dataset.i18nAria));
        });
        document.querySelectorAll('[data-i18n-placeholder]').forEach(node => {
            node.setAttribute('placeholder', this.t(node.dataset.i18nPlaceholder));
            if (node.getAttribute('aria-label') == null || node.getAttribute('aria-label') === '') {
                node.setAttribute('aria-label', this.t(node.dataset.i18nPlaceholder));
            }
        });
        document.querySelectorAll('.lang-button').forEach(button => {
            button.classList.toggle('is-active', button.dataset.lang === this.language);
            button.setAttribute('aria-pressed', String(button.dataset.lang === this.language));
        });
        this.updateWorldDisplay();
        this.updateLayerControls();
        this.updateSidebarToggle();
        this.updateHudIdentity();
        this.updateClaimPanel();
    }

    initMap() {
        this.map = L.map('map', {
            crs: L.CRS.Simple,
            center: MapConfig.defaultCenter,
            zoom: MapConfig.defaultZoom,
            minZoom: MapConfig.minZoom,
            maxZoom: MapConfig.maxZoom,
            attributionControl: false,
            zoomControl: false,
        });

        this.terrainLayer = this.createTerrainLayer();
        if (this.showTerrainLayer) {
            this.terrainLayer.addTo(this.map);
        }
        this.territoryLayer.addTo(this.map);
        this.resourceLayer.addTo(this.map);
        this.playerLayer.addTo(this.map);

        this.map.on('mousemove', (e) => {
            const latlng = e.latlng;
            const x = Math.round(latlng.lng);
            const z = Math.round(-latlng.lat);
            document.getElementById('coords').textContent = `${x}, ${z}`;
            this.updateClaimDrag(e);
        });
        this.map.on('mousedown', (e) => this.beginClaimDrag(e));
        this.map.on('mouseup', (e) => this.finishClaimDrag(e));
        this.map.on('click', (e) => this.maybeDismissSidebarFromMapClick(e));
    }

    bindEvents() {
        document.getElementById('zoom-in').addEventListener('click', () => this.map.zoomIn());
        document.getElementById('zoom-out').addEventListener('click', () => this.map.zoomOut());
        document.getElementById('zoom-reset').addEventListener('click', () => {
            this.hasFittedView = false;
            if (this.snapshot && this.fitToTerrainMetadata(this.snapshot)) {
                this.refreshMapViewport();
            } else {
                this.map.setView(MapConfig.defaultCenter, MapConfig.defaultZoom);
            }
            this.selectedPlayerId = '';
            this.selectedResourceId = '';
            this.setSelectedNation(null, { preserveView: true });
        });

        const claimToggle = document.getElementById('claim-toggle');
        if (claimToggle) {
            claimToggle.addEventListener('click', () => this.toggleClaimMode());
        }
        const claimSubmit = document.getElementById('claim-submit');
        if (claimSubmit) {
            claimSubmit.addEventListener('click', () => this.submitClaimRequest());
        }
        const sidebarToggle = document.getElementById('sidebar-toggle');
        if (sidebarToggle) {
            sidebarToggle.addEventListener('click', () => this.setSidebarCollapsed(!this.sidebarCollapsed));
        }
        const sidebarDismiss = document.getElementById('sidebar-dismiss');
        if (sidebarDismiss) {
            sidebarDismiss.addEventListener('click', () => this.setSidebarCollapsed(true));
        }
        const playersToggle = document.getElementById('players-toggle');
        if (playersToggle) {
            playersToggle.addEventListener('click', () => this.setPlayersCollapsed(!this.playersCollapsed));
        }
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !this.sidebarCollapsed) {
                this.setSidebarCollapsed(true);
            }
        });

        document.getElementById('toggle-territory').addEventListener('click', () => {
            this.showTerritoryLayer = !this.showTerritoryLayer;
            this.applyLayerVisibility();
            this.updateLayerControls();
        });

        document.getElementById('toggle-terrain').addEventListener('click', () => {
            this.showTerrainLayer = !this.showTerrainLayer;
            this.applyLayerVisibility();
            this.updateLayerControls();
        });

        document.getElementById('toggle-players').addEventListener('click', () => {
            this.showPlayerLayer = !this.showPlayerLayer;
            this.applyLayerVisibility();
            this.updateLayerControls();
        });

        document.getElementById('toggle-resources').addEventListener('click', () => {
            this.showResourceLayer = !this.showResourceLayer;
            this.applyLayerVisibility();
            this.updateLayerControls();
        });

        document.querySelectorAll('.lang-button').forEach(button => {
            button.addEventListener('click', () => this.setLanguage(button.dataset.lang));
        });

        const worldFilter = document.getElementById('world-filter');
        worldFilter.addEventListener('click', (event) => {
            const button = event.target.closest('button[data-world]');
            if (!button) {
                return;
            }
            this.currentWorld = button.dataset.world;
            this.hasFittedView = false;
            this.clearClaimSelection();
            this.updateWorldDisplay();
            if (this.snapshot) {
                this.renderSnapshot(this.snapshot, { forceRender: true, allowPulse: true });
            }
            this.maybeCollapseSidebarAfterMapAction();
        });

        // 初始化导航功能
        this.initNavigation();
    }

    setSidebarCollapsed(collapsed, options = {}) {
        this.sidebarCollapsed = Boolean(collapsed);
        document.body.classList.toggle('sidebar-collapsed', this.sidebarCollapsed);
        document.body.classList.toggle('sidebar-expanded', !this.sidebarCollapsed);
        this.syncSidebarRenderState();
        this.setStoredPreference('starcore-map-sidebar', this.sidebarCollapsed ? 'collapsed' : 'expanded');
        this.updateSidebarToggle();

        if (options.animate !== false && typeof window.gsap !== 'undefined') {
            const toggle = document.getElementById('sidebar-toggle');
            const identity = document.getElementById('hud-identity');
            const legend = document.querySelector('.map-legend');
            const claim = document.getElementById('claim-panel');
            const targets = [toggle, identity, legend, claim].filter(Boolean);
            window.gsap.killTweensOf(targets);
            window.gsap.timeline({
                defaults: { duration: 0.42, ease: 'power3.out', overwrite: 'auto' },
                onComplete: () => this.refreshMapViewport(),
            })
                .fromTo(toggle, { x: this.sidebarCollapsed ? -8 : 8, scale: 0.98 }, { x: 0, scale: 1, clearProps: 'transform' }, 0)
                .fromTo([identity, legend, claim].filter(Boolean), { x: this.sidebarCollapsed ? -6 : 6, scale: 0.992 }, { x: 0, scale: 1, stagger: 0.035, clearProps: 'transform' }, 0.04);
        } else {
            this.refreshMapViewport();
        }
    }

    syncSidebarRenderState() {
        const sidebar = document.getElementById('sidebar');
        if (!sidebar) {
            return;
        }
        if (typeof window.gsap !== 'undefined') {
            window.gsap.killTweensOf(sidebar);
        }
        sidebar.style.removeProperty('opacity');
        sidebar.style.removeProperty('transform');
        sidebar.style.removeProperty('visibility');
    }

    updateSidebarToggle() {
        const button = document.getElementById('sidebar-toggle');
        if (!button) {
            return;
        }
        const labelKey = this.sidebarCollapsed ? 'sidebarExpand' : 'sidebarCollapse';
        const label = this.t(labelKey);
        button.setAttribute('aria-expanded', String(!this.sidebarCollapsed));
        button.setAttribute('title', label);
        button.setAttribute('aria-label', label);
    }

    updateLayerControls() {
        this.updateLayerButton('toggle-terrain', this.showTerrainLayer);
        this.updateLayerButton('toggle-territory', this.showTerritoryLayer);
        this.updateLayerButton('toggle-players', this.showPlayerLayer);
        this.updateLayerButton('toggle-resources', this.showResourceLayer);
    }

    setPlayersCollapsed(collapsed) {
        this.playersCollapsed = Boolean(collapsed);
        const section = document.getElementById('section-players');
        const toggle = document.getElementById('players-toggle');
        if (section) {
            section.classList.toggle('is-collapsed', this.playersCollapsed);
        }
        if (toggle) {
            toggle.setAttribute('aria-expanded', String(!this.playersCollapsed));
        }
    }

    updateLayerButton(id, active) {
        const button = document.getElementById(id);
        if (!button) {
            return;
        }
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-pressed', String(active));
        if (typeof window.gsap !== 'undefined') {
            window.gsap.fromTo(button,
                { x: active ? -3 : 3, scale: 0.98 },
                { x: 0, scale: 1, duration: 0.22, ease: 'power2.out', overwrite: 'auto', clearProps: 'transform' }
            );
        }
    }

    initHudAnimations() {
        this.bindMotionButtons();
    }

    bindMotionButtons() {
        if (typeof window.gsap === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }
        document.querySelectorAll('.motion-button').forEach((button, index) => {
            if (button.dataset.motionBound === 'true') {
                return;
            }
            button.dataset.motionBound = 'true';
            const drift = index % 2 === 0 ? 2 : -2;
            button.addEventListener('mouseenter', () => {
                window.gsap.to(button, { x: drift, y: -2, scale: 1.025, duration: 0.22, ease: 'power2.out', overwrite: 'auto' });
            });
            button.addEventListener('mouseleave', () => {
                window.gsap.to(button, { x: 0, y: 0, scale: 1, duration: 0.28, ease: 'power2.out', overwrite: 'auto', clearProps: 'transform' });
            });
            button.addEventListener('pointerdown', () => {
                window.gsap.to(button, { x: 0, y: 1, scale: 0.965, duration: 0.12, ease: 'power1.out', overwrite: 'auto' });
            });
            button.addEventListener('pointerup', () => {
                window.gsap.to(button, { x: drift, y: -2, scale: 1.025, duration: 0.18, ease: 'power2.out', overwrite: 'auto' });
            });
        });
    }

    /* ========== 雷达功能 ========== */
    initRadar() {
        const container = document.getElementById('radar-container');
        const canvas = document.getElementById('radar-canvas');
        if (!container || !canvas) return;

        // 添加雷达装饰元素
        const sweep = document.createElement('div');
        sweep.className = 'radar-sweep';
        container.appendChild(sweep);

        const crosshair = document.createElement('div');
        crosshair.className = 'radar-crosshair';
        container.appendChild(crosshair);

        const ring1 = document.createElement('div');
        ring1.className = 'radar-ring radar-ring-1';
        container.appendChild(ring1);

        const ring2 = document.createElement('div');
        ring2.className = 'radar-ring radar-ring-2';
        container.appendChild(ring2);

        this.radarCanvas = canvas;
        this.radarCtx = canvas.getContext('2d');
        this.radarContainer = container;

        // 地图移动时更新雷达
        if (this.map) {
            this.map.on('move', () => this.updateRadar());
            this.map.on('zoom', () => this.updateRadar());
            this.map.on('moveend', () => this.updateRadar());
        }

        this.updateRadar();
    }

    updateRadar() {
        if (!this.map || !this.radarCanvas) return;

        const canvas = this.radarCanvas;
        const ctx = this.radarCtx;
        const size = canvas.width;
        const center = size / 2;

        // 清除画布
        ctx.clearRect(0, 0, size, size);

        // 获取当前视图范围
        const bounds = this.map.getBounds();
        const viewWidth = bounds.getEast() - bounds.getWest();
        const viewHeight = bounds.getNorth() - bounds.getSouth();
        const viewCenter = this.map.getCenter();

        // 绘制领地标记
        if (this.snapshot && this.snapshot.claims) {
            this.snapshot.claims.forEach(claim => {
                const marker = claim.marker;
                if (!marker) return;

                const dx = (marker.lng - viewCenter.lng) / viewWidth;
                const dz = (marker.lat + viewCenter.lat) / viewHeight;

                const radarX = center + dx * center * 0.85;
                const radarY = center - dz * center * 0.85;

                // 只显示在雷达范围内的标记
                if (radarX > 5 && radarX < size - 5 && radarY > 5 && radarY < size - 5) {
                    ctx.beginPath();
                    ctx.arc(radarX, radarY, 2.5, 0, Math.PI * 2);
                    ctx.fillStyle = claim.nationColor || 'rgba(180, 180, 180, 0.7)';
                    ctx.fill();
                }
            });
        }

        // 绘制玩家标记
        if (this.playerMarkers) {
            this.playerMarkers.forEach(marker => {
                const latlng = marker.getLatLng();
                const dx = (latlng.lng - viewCenter.lng) / viewWidth;
                const dz = (latlng.lat - viewCenter.lat) / viewHeight;

                const radarX = center + dx * center * 0.85;
                const radarY = center - dz * center * 0.85;

                if (radarX > 5 && radarX < size - 5 && radarY > 5 && radarY < size - 5) {
                    ctx.beginPath();
                    ctx.arc(radarX, radarY, 3, 0, Math.PI * 2);
                    ctx.fillStyle = '#7c9486';
                    ctx.fill();
                }
            });
        }

        // 更新雷达游标位置（显示当前地图中心）
        const cursor = document.getElementById('radar-cursor');
        if (cursor) {
            cursor.style.transform = 'translate(-50%, -50%)';
        }
    }

    /* ========== 导航功能 ========== */
    initNavigation() {
        const navBtns = document.querySelectorAll('.nav-btn[data-dir]');
        const navHome = document.getElementById('nav-home');

        navBtns.forEach(btn => {
            const dir = btn.dataset.dir;
            let interval = null;

            const startNav = () => {
                this.navInterval = setInterval(() => this.panMap(dir), 50);
            };

            const stopNav = () => {
                if (this.navInterval) {
                    clearInterval(this.navInterval);
                    this.navInterval = null;
                }
            };

            btn.addEventListener('pointerdown', startNav);
            btn.addEventListener('pointerup', stopNav);
            btn.addEventListener('pointerleave', stopNav);
            btn.addEventListener('pointercancel', stopNav);
        });

        if (navHome) {
            navHome.addEventListener('click', () => this.goToPlayerPosition());
        }
    }

    panMap(direction) {
        if (!this.map) return;
        const offset = 200;
        const center = this.map.getCenter();
        let lat = center.lat;
        let lng = center.lng;

        switch (direction) {
            case 'north': lat += 0.05; break;
            case 'south': lat -= 0.05; break;
            case 'east': lng += 0.05; break;
            case 'west': lng -= 0.05; break;
        }

        this.map.setView([lat, lng], this.map.getZoom());
    }

    goToPlayerPosition() {
        if (this.playerPosition) {
            this.map.setView([this.playerPosition.lat, this.playerPosition.lng], this.map.getZoom());
        } else if (this.viewerInfo && this.viewerInfo.position) {
            const pos = this.viewerInfo.position;
            this.map.setView([pos.z, pos.x], 8);
        } else {
            // 如果没有玩家位置，回到视图中心
            this.hasFittedView = false;
            if (this.snapshot && this.fitToTerrainMetadata(this.snapshot)) {
                this.refreshMapViewport();
            }
        }
    }

    /* ========== 快捷按钮功能 ========== */
    initQuickActions() {
        const qaLayers = document.getElementById('qa-layers');
        const qaSearch = document.getElementById('qa-search');
        const qaMarker = document.getElementById('qa-marker');
        const qaNation = document.getElementById('qa-nation');
        const qaFullscreen = document.getElementById('qa-fullscreen');

        if (qaLayers) {
            qaLayers.addEventListener('click', () => this.toggleSidebarSection('section-layers'));
        }

        if (qaSearch) {
            qaSearch.addEventListener('click', () => this.toggleSearchPanel());
        }

        if (qaMarker) {
            qaMarker.addEventListener('click', () => this.addMarker());
        }

        if (qaNation) {
            qaNation.addEventListener('click', () => this.focusViewerNation());
        }

        if (qaFullscreen) {
            qaFullscreen.addEventListener('click', () => this.toggleFullscreen());
        }

        // 搜索面板
        const searchInput = document.getElementById('search-input');
        const searchClose = document.getElementById('search-close');

        if (searchInput) {
            searchInput.addEventListener('input', (e) => this.handleSearch(e.target.value));
            searchInput.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') this.toggleSearchPanel(false);
                if (e.key === 'Enter') this.selectFirstSearchResult();
            });
        }

        if (searchClose) {
            searchClose.addEventListener('click', () => this.toggleSearchPanel(false));
        }
    }

    toggleSidebarSection(sectionId) {
        const section = document.getElementById(sectionId);
        if (!section) return;

        // 展开侧边栏
        if (this.sidebarCollapsed) {
            this.setSidebarCollapsed(false);
        }

        // 滚动到指定区块
        section.scrollIntoView({ behavior: 'smooth', block: 'start' });

        // 高亮效果
        section.classList.add('highlight-flash');
        setTimeout(() => section.classList.remove('highlight-flash'), 800);
    }

    toggleSearchPanel(visible) {
        const panel = document.getElementById('search-panel');
        if (!panel) return;

        const isVisible = visible !== undefined ? visible : !this.searchPanelVisible;
        this.searchPanelVisible = isVisible;

        if (isVisible) {
            panel.hidden = false;
            panel.style.opacity = '0';
            panel.style.transform = 'translateX(-50%) translateY(-10px)';
            requestAnimationFrame(() => {
                panel.style.transition = 'opacity 0.2s, transform 0.2s';
                panel.style.opacity = '1';
                panel.style.transform = 'translateX(-50%) translateY(0)';
            });
            const input = document.getElementById('search-input');
            if (input) setTimeout(() => input.focus(), 100);
        } else {
            panel.style.opacity = '0';
            panel.style.transform = 'translateX(-50%) translateY(-10px)';
            setTimeout(() => { panel.hidden = true; }, 200);
        }

        // 更新按钮状态
        const btn = document.getElementById('qa-search');
        if (btn) btn.classList.toggle('is-active', isVisible);
    }

    handleSearch(query) {
        this.searchQuery = query.trim().toLowerCase();
        const resultsContainer = document.getElementById('search-results');
        if (!resultsContainer) return;

        if (!this.searchQuery) {
            resultsContainer.innerHTML = '';
            return;
        }

        const results = [];

        // 搜索国家
        if (this.snapshot && this.snapshot.nations) {
            this.snapshot.nations.forEach(nation => {
                if (nation.name.toLowerCase().includes(this.searchQuery)) {
                    results.push({
                        type: 'nation',
                        id: nation.id,
                        name: nation.name,
                        meta: `${nation.claims} 领地 · ${nation.members} 成员`
                    });
                }
            });
        }

        // 搜索玩家
        if (this.snapshot && this.snapshot.players) {
            this.snapshot.players.forEach(player => {
                if (player.name.toLowerCase().includes(this.searchQuery)) {
                    results.push({
                        type: 'player',
                        id: player.id,
                        name: player.name,
                        meta: player.nation ? `国家: ${player.nation}` : '独立玩家'
                    });
                }
            });
        }

        // 搜索资源区块
        if (this.snapshot && this.snapshot.resources) {
            this.snapshot.resources.forEach(resource => {
                if (resource.name.toLowerCase().includes(this.searchQuery) ||
                    (resource.biome && resource.biome.toLowerCase().includes(this.searchQuery))) {
                    results.push({
                        type: 'resource',
                        id: resource.id,
                        name: resource.name,
                        meta: `${resource.biome || '未知生物群系'} · ${resource.richness || ''}`
                    });
                }
            });
        }

        this.searchResults = results;
        this.renderSearchResults(results);
    }

    renderSearchResults(results) {
        const container = document.getElementById('search-results');
        if (!container) return;

        if (results.length === 0) {
            container.innerHTML = `<div class="search-no-results">${this.t('sidebarNoMatch') || '没有匹配结果'}</div>`;
            return;
        }

        const icons = {
            nation: '⚑',
            player: '●',
            resource: '◆'
        };

        container.innerHTML = results.slice(0, 20).map(result => `
            <div class="search-result-item" data-type="${result.type}" data-id="${result.id}">
                <div class="search-result-icon">${icons[result.type] || '?'}</div>
                <div class="search-result-info">
                    <div class="search-result-name">${this.escapeHtml(result.name)}</div>
                    <div class="search-result-meta">${this.escapeHtml(result.meta)}</div>
                </div>
            </div>
        `).join('');

        container.querySelectorAll('.search-result-item').forEach(item => {
            item.addEventListener('click', () => {
                const type = item.dataset.type;
                const id = item.dataset.id;
                this.selectSearchResult(type, id);
            });
        });
    }

    selectSearchResult(type, id) {
        this.toggleSearchPanel(false);

        switch (type) {
            case 'nation':
                this.setSelectedNation(id, { preserveView: false });
                break;
            case 'player':
                this.focusPlayer(id);
                break;
            case 'resource':
                this.focusResource(id);
                break;
        }
    }

    selectFirstSearchResult() {
        if (this.searchResults.length > 0) {
            const first = this.searchResults[0];
            this.selectSearchResult(first.type, first.id);
        }
    }

    focusPlayer(playerId) {
        const marker = this.playerMarkers.find(m => m.options.playerId === playerId);
        if (marker) {
            this.map.setView(marker.getLatLng(), Math.max(this.map.getZoom(), 6));
        }
    }

    focusResource(resourceId) {
        const marker = this.resourceMarkers.find(m => m.options.resourceId === resourceId);
        if (marker) {
            this.map.setView(marker.getLatLng(), Math.max(this.map.getZoom(), 5));
        }
    }

    addMarker() {
        const center = this.map.getCenter();
        const lng = Math.round(center.lng);
        const lat = Math.round(-center.lat);

        // 创建自定义标记
        const markerIcon = L.divIcon({
            className: 'custom-marker',
            html: `<div style="
                width: 16px;
                height: 16px;
                background: var(--warning, #b39a69);
                border: 2px solid white;
                border-radius: 50%;
                cursor: pointer;
                box-shadow: 0 2px 8px rgba(0,0,0,0.4);
            "></div>`,
            iconSize: [16, 16],
            iconAnchor: [8, 8]
        });

        const marker = L.marker(center, { icon: markerIcon }).addTo(this.map);
        marker.bindPopup(`<div style="text-align: center;">
            <strong>自定义标记</strong><br>
            <small>坐标: ${lng}, ${lat}</small><br>
            <button onclick="this.closest('.leaflet-popup').remove()">删除</button>
        </div>`);
    }

    focusViewerNation() {
        if (this.viewerInfo && this.viewerInfo.nation) {
            const nationId = this.viewerInfo.nation;
            this.setSelectedNation(nationId, { preserveView: false });
        } else {
            this.toggleSearchPanel(true);
        }
    }

    toggleFullscreen() {
        const btn = document.getElementById('qa-fullscreen');
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(() => {});
            if (btn) btn.classList.add('is-active');
        } else {
            document.exitFullscreen().catch(() => {});
            if (btn) btn.classList.remove('is-active');
        }
    }

    escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    applyLayerVisibility() {
        if (!this.map) {
            return;
        }
        if (this.terrainLayer) {
            if (this.showTerrainLayer) {
                this.terrainLayer.addTo(this.map);
            } else {
                this.map.removeLayer(this.terrainLayer);
            }
        }
        if (this.showTerritoryLayer) {
            this.territoryLayer.addTo(this.map);
        } else {
            this.map.removeLayer(this.territoryLayer);
        }
        if (this.showResourceLayer) {
            this.resourceLayer.addTo(this.map);
        } else {
            this.map.removeLayer(this.resourceLayer);
        }
        if (this.showPlayerLayer) {
            this.playerLayer.addTo(this.map);
        } else {
            this.map.removeLayer(this.playerLayer);
        }
    }

    async loadData() {
        if (this.demoMode) {
            this.loadDemoData();
            this.setStatus('connected', this.t('statusStaticExport'), this.t('detailStaticExport'));
            return;
        }
        if (this.hasExpiredViewerAccess()) {
            this.markAccessExpired();
            return;
        }
        let snapshot = null;
        let health = null;
        try {
            [snapshot, health] = await Promise.all([
                this.resolveSnapshot(),
                this.loadHealth(),
            ]);
        } catch (err) {
            if (this.accessExpired) {
                return;
            }
            console.warn('STARCORE map data load failed', err);
            this.playReveal({ allowPulse: false });
            this.setStatus('loading', this.t('statusOffline'), this.offlineDetail());
            return;
        }
        if (this.accessExpired || !snapshot) {
            return;
        }
        this.health = health;
        this.snapshot = snapshot;
        try {
            this.renderSnapshot(snapshot, { allowPulse: false });
            this.refreshConnectionStatus();
        } catch (err) {
            console.error('STARCORE map render failed', err);
            this.playReveal({ allowPulse: false });
            this.setStatus('error', this.t('statusOffline'), this.t('detailRenderError'));
        }
    }

    async loadHealth() {
        if (this.apiUnavailable) {
            return null;
        }
        try {
            const resp = await fetch(this.buildApiUrl(MapConfig.healthUrl), { cache: 'no-store' });
            if (resp.ok) {
                this.apiUnavailable = false;
                return await resp.json();
            }
            if (await this.handleAccessDenied(resp)) {
                return null;
            }
            if (resp.status === 404) {
                this.apiUnavailable = true;
            }
        } catch (_ignored) {
        }
        return null;
    }

    offlineDetail() {
        return window.location.protocol === 'file:' ? this.t('detailFileMode') : this.t('detailOffline');
    }

    createTerrainLayer() {
        const tileSize = this.terrainTileSize || 256;
        const layer = L.gridLayer({
            tileSize,
            opacity: 1,
            zIndex: 1,
        });
        layer.createTile = (coords, done) => {
            const tile = document.createElement('canvas');
            tile.width = tileSize;
            tile.height = tileSize;
            tile.style.width = `${tileSize}px`;
            tile.style.height = `${tileSize}px`;
            tile.style.display = 'block';
            tile.setAttribute('role', 'presentation');
            this.loadTerrainTile(tile, coords, done);
            return tile;
        };
        return layer;
    }

    async loadTerrainTile(tile, coords, done = () => {}) {
        const binaryUrl = this.terrainTileBinaryUrl(coords);
        const dataUrl = this.terrainTileDataUrl(coords);
        const imageUrl = this.terrainTileUrl(coords);
        tile.dataset.terrainUrl = binaryUrl;
        let completed = false;
        const complete = (error = null) => {
            if (completed) {
                return;
            }
            completed = true;
            done(error, tile);
        };
        if (binaryUrl === MapConfig.transparentTile) {
            this.clearTerrainCanvas(tile);
            complete();
            return;
        }
        try {
            const data = await this.fetchTerrainTileBinary(binaryUrl, 0);
            if (tile.dataset.terrainUrl !== binaryUrl) {
                complete();
                return;
            }
            this.paintTerrainTileData(tile, data);
            complete();
            return;
        } catch (_binaryError) {
            try {
                await this.paintTerrainTileImageFallback(tile, binaryUrl, imageUrl);
                complete();
            } catch (imageError) {
                try {
                    const data = await this.fetchTerrainTileData(dataUrl, 0);
                    if (tile.dataset.terrainUrl !== binaryUrl) {
                        complete();
                        return;
                    }
                    this.paintTerrainTileData(tile, data);
                    complete();
                    return;
                } catch (dataError) {
                    if (tile.dataset.terrainUrl === binaryUrl) {
                        this.clearTerrainCanvas(tile);
                    }
                    complete(dataError || imageError);
                    return;
                }
            }
        }
    }

    async fetchTerrainTileBinary(url, attempt) {
        const resp = await fetch(url, { cache: 'force-cache' });
        if (resp.ok) {
            const buffer = await resp.arrayBuffer();
            return this.decodeTerrainTileBinary(buffer);
        }
        if (await this.handleAccessDenied(resp)) {
            throw new Error('Terrain tile access denied');
        }
        if (this.shouldRetryTerrainTile(resp.status, attempt)) {
            await this.delay(MapConfig.terrainRetryDelays[attempt]);
            return this.fetchTerrainTileBinary(url, attempt + 1);
        }
        throw new Error(`Terrain tile binary failed with ${resp.status}`);
    }

    decodeTerrainTileBinary(buffer) {
        const view = new DataView(buffer);
        const bytes = new Uint8Array(buffer);
        if (bytes.length < 36 || bytes[0] !== 83 || bytes[1] !== 67 || bytes[2] !== 84 || bytes[3] !== 66) {
            throw new Error('Terrain tile binary magic mismatch');
        }
        const version = bytes[4];
        const paletteBits = bytes[5];
        if (version !== 1 || paletteBits !== 16) {
            throw new Error('Unsupported terrain tile binary version');
        }
        const tileSize = view.getInt32(8, false);
        const worldSize = view.getInt32(12, false);
        const x = view.getInt32(16, false);
        const z = view.getInt32(20, false);
        const heightMin = view.getInt32(24, false);
        const heightMax = view.getInt32(28, false);
        const paletteCount = view.getInt32(32, false);
        if (tileSize <= 0 || paletteCount <= 0) {
            throw new Error('Invalid terrain tile binary header');
        }
        const paletteOffset = 36;
        const pixelOffset = paletteOffset + paletteCount * 3;
        const pixelBytes = tileSize * tileSize * 2;
        if (bytes.length < pixelOffset + pixelBytes) {
            throw new Error('Terrain tile binary buffer is incomplete');
        }
        const palette = new Array(paletteCount);
        for (let i = 0; i < paletteCount; i += 1) {
            const offset = paletteOffset + i * 3;
            palette[i] = (bytes[offset] << 16) | (bytes[offset + 1] << 8) | bytes[offset + 2];
        }
        return {
            format: 'starcore-terrain-binary-v1',
            tileSize,
            worldSize,
            x,
            z,
            heightMin,
            heightMax,
            paletteBits,
            palette,
            pixels: bytes.subarray(pixelOffset, pixelOffset + pixelBytes),
        };
    }

    async fetchTerrainTileData(url, attempt) {
        const resp = await fetch(url, { cache: 'force-cache' });
        if (resp.ok) {
            const data = await resp.json();
            if (!data || data.format !== 'starcore-terrain-raster-v1' || !Array.isArray(data.palette) || typeof data.pixels !== 'string') {
                throw new Error('Terrain tile response is not structured raster data');
            }
            return data;
        }
        if (await this.handleAccessDenied(resp)) {
            throw new Error('Terrain tile access denied');
        }
        if (this.shouldRetryTerrainTile(resp.status, attempt)) {
            await this.delay(MapConfig.terrainRetryDelays[attempt]);
            return this.fetchTerrainTileData(url, attempt + 1);
        }
        throw new Error(`Terrain tile data failed with ${resp.status}`);
    }

    paintTerrainTileData(tile, data) {
        const size = Number(data.tileSize) || 256;
        const pixels = data.pixels instanceof Uint8Array ? data.pixels : this.decodeBase64Bytes(data.pixels);
        const expectedPixels = size * size;
        if (pixels.length < expectedPixels * 2) {
            throw new Error('Terrain tile pixel buffer is incomplete');
        }
        if (tile.width !== size || tile.height !== size) {
            tile.width = size;
            tile.height = size;
        }
        const ctx = tile.getContext('2d', { alpha: false });
        const image = ctx.createImageData(size, size);
        for (let i = 0; i < expectedPixels; i += 1) {
            const paletteIndex = (pixels[i * 2] << 8) | pixels[i * 2 + 1];
            const color = data.palette[paletteIndex] ?? 0;
            const offset = i * 4;
            image.data[offset] = (color >> 16) & 255;
            image.data[offset + 1] = (color >> 8) & 255;
            image.data[offset + 2] = color & 255;
            image.data[offset + 3] = 255;
        }
        ctx.putImageData(image, 0, 0);
    }

    async paintTerrainTileImageFallback(tile, expectedDataUrl, imageUrl) {
        if (imageUrl === MapConfig.transparentTile) {
            throw new Error('No terrain image fallback URL');
        }
        const objectUrl = await this.fetchTerrainTileObjectUrl(imageUrl, 0);
        try {
            if (tile.dataset.terrainUrl !== expectedDataUrl) {
                return;
            }
            const image = await this.loadImageObjectUrl(objectUrl);
            if (tile.dataset.terrainUrl !== expectedDataUrl) {
                return;
            }
            const ctx = tile.getContext('2d', { alpha: false });
            ctx.clearRect(0, 0, tile.width, tile.height);
            ctx.drawImage(image, 0, 0, tile.width, tile.height);
        } finally {
            URL.revokeObjectURL(objectUrl);
        }
    }

    loadImageObjectUrl(objectUrl) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.decoding = 'async';
            image.onload = () => resolve(image);
            image.onerror = () => reject(new Error('Terrain image fallback failed'));
            image.src = objectUrl;
        });
    }

    decodeBase64Bytes(value) {
        const binary = atob(value);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes;
    }

    clearTerrainCanvas(tile) {
        const ctx = tile.getContext('2d', { alpha: true });
        ctx.clearRect(0, 0, tile.width, tile.height);
    }

    async fetchTerrainTileObjectUrl(url, attempt) {
        const resp = await fetch(url, { cache: 'force-cache' });
        if (resp.ok) {
            const blob = await resp.blob();
            if (!blob.type.startsWith('image/')) {
                throw new Error('Terrain tile response is not an image');
            }
            return URL.createObjectURL(blob);
        }
        if (await this.handleAccessDenied(resp)) {
            throw new Error('Terrain tile access denied');
        }
        if (this.shouldRetryTerrainTile(resp.status, attempt)) {
            await this.delay(MapConfig.terrainRetryDelays[attempt]);
            return this.fetchTerrainTileObjectUrl(url, attempt + 1);
        }
        throw new Error(`Terrain tile failed with ${resp.status}`);
    }

    shouldRetryTerrainTile(status, attempt) {
        return (status === 429 || status === 503) && attempt < MapConfig.terrainRetryDelays.length;
    }

    delay(ms) {
        return new Promise(resolve => window.setTimeout(resolve, ms));
    }

    terrainTileUrl(coords) {
        if (!this.terrainWorld) {
            return MapConfig.transparentTile;
        }
        const scale = Math.pow(2, coords.z);
        const worldSize = Math.max(1, Math.round(this.terrainTileSize / scale));
        const x = Math.floor(coords.x * worldSize);
        const z = Math.floor(coords.y * worldSize);
        const revision = encodeURIComponent(this.terrainRevision || '0');
        const url = `${MapConfig.terrainUrl}?world=${encodeURIComponent(this.terrainWorld)}&x=${x}&z=${z}&size=${worldSize}&rev=${revision}`;
        return this.buildApiUrl(url);
    }

    terrainTileDataUrl(coords) {
        if (!this.terrainWorld) {
            return MapConfig.transparentTile;
        }
        const scale = Math.pow(2, coords.z);
        const worldSize = Math.max(1, Math.round(this.terrainTileSize / scale));
        const x = Math.floor(coords.x * worldSize);
        const z = Math.floor(coords.y * worldSize);
        const revision = encodeURIComponent(this.terrainRevision || '0');
        const url = `${MapConfig.terrainDataUrl}?world=${encodeURIComponent(this.terrainWorld)}&x=${x}&z=${z}&size=${worldSize}&rev=${revision}`;
        return this.buildApiUrl(url);
    }

    terrainTileBinaryUrl(coords) {
        if (!this.terrainWorld) {
            return MapConfig.transparentTile;
        }
        const scale = Math.pow(2, coords.z);
        const worldSize = Math.max(1, Math.round(this.terrainTileSize / scale));
        const x = Math.floor(coords.x * worldSize);
        const z = Math.floor(coords.y * worldSize);
        const revision = encodeURIComponent(this.terrainRevision || '0');
        const url = `${MapConfig.terrainBinaryUrl}?world=${encodeURIComponent(this.terrainWorld)}&x=${x}&z=${z}&size=${worldSize}&rev=${revision}`;
        return this.buildApiUrl(url);
    }

    updateTerrainLayer(snapshot) {
        if (!this.terrainLayer) {
            return;
        }
        const nextWorld = this.terrainWorldFor(snapshot);
        const nextRevision = this.terrainRevisionFor(snapshot);
        const nextTileSize = this.terrainTileSizeFor(snapshot);
        const tileSizeChanged = nextTileSize !== this.terrainTileSize;
        if (nextWorld === this.terrainWorld && nextRevision === this.terrainRevision && !tileSizeChanged) {
            return;
        }
        this.terrainWorld = nextWorld;
        this.terrainRevision = nextRevision;
        this.terrainTileSize = nextTileSize;
        if (tileSizeChanged) {
            this.rebuildTerrainLayer();
            return;
        }
        this.terrainLayer.redraw();
    }

    terrainRevisionFor(snapshot) {
        return String(((snapshot || {}).terrain || {}).revision ?? '0');
    }

    terrainTileSizeFor(snapshot) {
        const tileSize = Number(((snapshot || {}).terrain || {}).tileSize);
        if (!Number.isFinite(tileSize)) {
            return 256;
        }
        return Math.min(512, Math.max(64, Math.round(tileSize)));
    }

    rebuildTerrainLayer() {
        if (!this.map) {
            return;
        }
        if (this.terrainLayer) {
            this.map.removeLayer(this.terrainLayer);
        }
        this.terrainLayer = this.createTerrainLayer();
        if (this.showTerrainLayer) {
            this.terrainLayer.addTo(this.map);
        }
    }

    terrainWorldFor(snapshot) {
        const worlds = snapshot.worlds || this.inferWorlds(snapshot);
        if (this.currentWorld !== 'all') {
            return this.currentWorld;
        }
        return worlds[0] || '';
    }

    async resolveSnapshot() {
        if (!this.apiUnavailable) {
            try {
                const resp = await fetch(this.buildApiUrl(MapConfig.dataUrl), { cache: 'no-store' });
                if (resp.ok) {
                    this.apiUnavailable = false;
                    this._localSnapshotAvailable = false;
                    return await resp.json();
                }
                if (await this.handleAccessDenied(resp)) {
                    return null;
                }
                if (resp.status === 404) {
                    this.apiUnavailable = true;
                }
            } catch (_ignored) {
            }
        }

        try {
            const localResp = await fetch(MapConfig.snapshotJsonUrl, { cache: 'no-store' });
            if (localResp.ok) {
                this._localSnapshotAvailable = true;
                return await localResp.json();
            }
        } catch (_ignored) {
        }

        if (window.STARCORE_SNAPSHOT) {
            this._localSnapshotAvailable = true;
            return window.STARCORE_SNAPSHOT;
        }

        const scriptLoaded = await this.tryLoadSnapshotScript();
        if (scriptLoaded && window.STARCORE_SNAPSHOT) {
            this._localSnapshotAvailable = true;
            return window.STARCORE_SNAPSHOT;
        }

        throw new Error('No live or static snapshot available');
    }

    connectStream() {
        if (this.demoMode) {
            return;
        }
        if (this.apiUnavailable) {
            return;
        }
        if (typeof window.EventSource === 'undefined') {
            return;
        }
        if (this.hasExpiredViewerAccess()) {
            this.markAccessExpired();
            return;
        }
        if (this.eventSource || this.streamRetryTimer) {
            return;
        }
        try {
            this.eventSource = new EventSource(this.buildApiUrl(MapConfig.streamUrl));
            this.eventSource.addEventListener('ready', () => {
                this.streamActive = true;
                this.streamReconnectDelay = 1500;
                this.refreshConnectionStatus();
            });
            this.eventSource.addEventListener('snapshot', async (event) => {
                this.streamActive = true;
                this._localSnapshotAvailable = false;
                this.snapshot = JSON.parse(event.data);
                this.health = await this.loadHealth();
                this.renderSnapshot(this.snapshot, { allowPulse: true });
                this.refreshConnectionStatus();
            });
            this.eventSource.onerror = () => {
                this.streamActive = false;
                this.disposeStream();
                if (this.hasExpiredViewerAccess()) {
                    this.markAccessExpired();
                    return;
                }
                this.scheduleStreamReconnect();
            };
        } catch (_ignored) {
            this.scheduleStreamReconnect();
        }
    }

    disposeStream() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
    }

    scheduleStreamReconnect() {
        if (this.hasExpiredViewerAccess()) {
            this.markAccessExpired();
            return;
        }
        if (this.apiUnavailable && this._localSnapshotAvailable) {
            this.setStatus('connected', this.t('statusStaticExport'), this.t('detailStaticExport'));
            return;
        }
        if (this.streamRetryTimer) {
            return;
        }
        if (this._localSnapshotAvailable) {
            this.setStatus('connected', this.t('statusStaticExport'), this.t('detailStaticExport'));
        } else {
            this.setStatus('loading', this.t('statusReconnecting'), this.t('detailReconnecting'));
        }
        this.streamRetryTimer = window.setTimeout(() => {
            this.streamRetryTimer = null;
            this.connectStream();
        }, this.streamReconnectDelay);
        this.streamReconnectDelay = Math.min(this.streamReconnectDelay * 2, 15000);
    }

    tryLoadSnapshotScript() {
        return new Promise((resolve) => {
            const existing = document.querySelector('script[data-starcore-snapshot="true"]');
            if (existing) {
                resolve(Boolean(window.STARCORE_SNAPSHOT));
                return;
            }

            const script = document.createElement('script');
            script.src = `${MapConfig.snapshotScriptUrl}?t=${Date.now()}`;
            script.dataset.starcoreSnapshot = 'true';
            script.onload = () => resolve(true);
            script.onerror = () => resolve(false);
            document.head.appendChild(script);
        });
    }

    setStatus(cls, text, detail = '') {
        this.updateStatusFlavor(cls, text);
        const pulseKey = `${cls}:${text}:${detail}`;
        if (pulseKey !== this.statusPulseKey) {
            this.statusPulseKey = pulseKey;
        }
    }

    updateStatusFlavor(cls, text) {
        void cls;
        void text;
    }

    statusFlavorKeyFor(cls, text) {
        if (this.accessExpired || text === this.t('statusAccessExpired')) {
            return 'statusFeedExpired';
        }
        if (text === this.t('statusReconnecting')) {
            return 'statusFeedReconnecting';
        }
        if (cls === 'error' || text === this.t('statusOffline')) {
            return 'statusFeedOffline';
        }
        if (this.streamActive) {
            return 'statusFeedConnected';
        }
        if (this._localSnapshotAvailable) {
            return 'statusFeedStatic';
        }
        if (this.snapshot) {
            return 'statusFeedPolling';
        }
        return 'statusFeedLoading';
    }

    statusToneClassFor(cls, text) {
        if (this.accessExpired || cls === 'error' || text === this.t('statusOffline') || text === this.t('statusAccessExpired')) {
            return 'is-danger';
        }
        if (this.streamActive || this._localSnapshotAvailable) {
            return 'is-success';
        }
        return 'is-warning';
    }

    refreshConnectionStatus() {
        if (this.streamActive) {
            this.setStatus('connected', this.t('statusLiveStream'), this.describeStatusDetail());
            return;
        }
        if (this._localSnapshotAvailable) {
            this.setStatus('connected', this.t('statusStaticExport'), this.t('detailStaticExport'));
            return;
        }
        if (this.snapshot) {
            this.setStatus('loading', this.t('statusLiveWeb'), this.t('detailPolling'));
            return;
        }
        this.setStatus('loading', this.t('statusLoading'), this.t('statusWaitingData'));
    }

    describeStatusDetail() {
        const accessDetail = this.describeAccessDetail();
        if (this.streamActive && this.health) {
            const streamDetail = this.t('detailStreaming', {
                clients: this.health.sseClients || 0,
                cache: this.health.avatarCacheEnabled ? this.t('cacheEnabled') : this.t('cacheDisabled'),
            });
            return accessDetail ? `${streamDetail} · ${accessDetail}` : streamDetail;
        }
        if (this._localSnapshotAvailable) {
            return this.t('detailStaticExport');
        }
        return accessDetail ? `${this.t('detailPolling')} · ${accessDetail}` : this.t('detailPolling');
    }

    describeAccessDetail() {
        const access = (this.snapshot && this.snapshot.access) || (this.health && this.health.access) || null;
        if (!access) {
            return '';
        }
        const mode = access.mode === 'ip'
            ? this.t('accessIp')
            : (access.mode === 'signed' ? this.t('accessSigned') : this.t('accessPublic'));
        const scope = access.authenticated
            ? (access.scope === 'full' ? this.t('accessFull') : this.t('accessAllied'))
            : this.t('accessUnauthenticated');
        const expires = access.expiresAt ? this.t('accessExpires', { time: this.formatTimestamp(access.expiresAt) }) : '';
        return [mode, scope, expires].filter(Boolean).join(' · ');
    }

    startAutoRefresh() {
        if (this.demoMode) {
            return;
        }
        this.refreshTimer = setInterval(() => {
            if (!this.streamActive && !this.accessExpired) {
                this.loadData();
            }
        }, MapConfig.refreshInterval);
    }

    hasExpiredViewerAccess() {
        const exp = Number(this.viewerAccess.exp || 0);
        return Number.isFinite(exp) && exp > 0 && (Date.now() / 1000) >= exp;
    }

    async handleAccessDenied(resp) {
        if (!resp || resp.status !== 403) {
            return false;
        }
        let message = '';
        try {
            message = await resp.text();
        } catch (_ignored) {
        }
        if (message.toLowerCase().includes('expired')) {
            this.markAccessExpired();
            return true;
        }
        return false;
    }

    markAccessExpired() {
        this.accessExpired = true;
        this.streamActive = false;
        this.disposeStream();
        if (this.streamRetryTimer) {
            window.clearTimeout(this.streamRetryTimer);
            this.streamRetryTimer = null;
        }
        this.setStatus('error', this.t('statusAccessExpired'), this.t('detailAccessExpired'));
    }

    renderSnapshot(snapshot, options = {}) {
        const renderKey = this.renderKeyFor(snapshot);
        const snapshotChanged = renderKey !== this.lastRenderKey;
        const forceRender = options.forceRender === true;
        if (!snapshotChanged && !forceRender) {
            return;
        }
        this.lastRenderKey = renderKey;
        const allowPulse = options.allowPulse !== false;

        this.clearLayers();
        this.populateWorldFilter(snapshot);
        this.updateTerrainLayer(snapshot);

        const layers = snapshot.layers || [];
        const territoryLayer = layers.find(layer => layer.type === 'TERRITORY') || { territories: [] };
        const resourceLayer = layers.find(layer => layer.type === 'RESOURCE_DISTRICTS') || { markers: [] };
        const playerLayer = layers.find(layer => layer.type === 'PLAYER_MARKERS') || { markers: [] };

        const territories = (territoryLayer.territories || []).filter(item => this.acceptWorld(item.world));
        const resources = (resourceLayer.markers || []).filter(item => this.acceptWorld(item.world));
        const markers = (playerLayer.markers || []).filter(item => this.acceptWorld(item.world));
        const visibleNationIds = new Set(territories.map(item => String(item.ownerId || '')));
        const visibleResourceIds = new Set(resources.map(item => String(item.id || '')));
        const visiblePlayerIds = new Set(markers.map(item => String(item.id || '')));

        if (this.selectedNationId && !visibleNationIds.has(String(this.selectedNationId))) {
            this.selectedNationId = null;
        }
        if (this.selectedResourceId && !visibleResourceIds.has(String(this.selectedResourceId))) {
            this.selectedResourceId = '';
        }
        if (this.selectedPlayerId && !visiblePlayerIds.has(String(this.selectedPlayerId))) {
            this.selectedPlayerId = '';
        }

        this.viewerInfo = snapshot.viewer || null;
        this.updateHudIdentity();
        this.renderTerritories(territories);
        this.renderResourceDistricts(resources);
        this.renderPlayers(markers);
        this.applyLayerVisibility();
        this.updateSummary(snapshot, territories, markers, resources);
        this.updateIntel(snapshot, territories, markers);
        this.updateSidebar();
        this.updateWorldDisplay();
        this.claimAuthenticated = Boolean((snapshot.access || {}).authenticated);
        this.updateClaimPanel();
        if (!this.hasFittedView && !options.skipFit) {
            this.fitToVisibleData(snapshot, territories, markers, resources);
            this.hasFittedView = true;
        }
        this.playReveal(snapshotChanged && allowPulse);
    }

    acceptWorld(world) {
        return this.currentWorld === 'all' || this.currentWorld === world;
    }

    toggleClaimMode() {
        if (!this.claimAuthenticated) {
            this.updateClaimPanel(this.t('claimNeedAuth'));
            return;
        }
        if (!this.activeClaimWorld()) {
            this.updateClaimPanel(this.t('claimNoWorld'));
            return;
        }
        this.claimMode = !this.claimMode;
        this.claimDragging = false;
        if (this.claimMode) {
            this.map.dragging.disable();
        } else {
            this.map.dragging.enable();
            this.clearClaimSelection();
        }
        this.map.getContainer().classList.toggle('claim-mode', this.claimMode);
        this.updateClaimPanel();
    }

    activeClaimWorld() {
        if (this.currentWorld && this.currentWorld !== 'all') {
            return this.currentWorld;
        }
        const worlds = ((this.snapshot || {}).worlds || this.inferWorlds(this.snapshot || {})).filter(Boolean);
        return worlds.length === 1 ? worlds[0] : '';
    }

    beginClaimDrag(event) {
        if (!this.claimMode || !this.claimAuthenticated) {
            return;
        }
        if (!this.activeClaimWorld()) {
            this.updateClaimPanel(this.t('claimNoWorld'));
            return;
        }
        this.claimDragging = true;
        this.claimStart = event.latlng;
        this.claimPreview = null;
        this.claimSelection = null;
        this.drawClaimRectangle(this.rawSelectionBounds(event.latlng, event.latlng), false);
        this.updateClaimPanel(this.t('claimDragging'));
        if (event.originalEvent) {
            event.originalEvent.preventDefault();
        }
    }

    updateClaimDrag(event) {
        if (!this.claimMode || !this.claimDragging || !this.claimStart) {
            return;
        }
        this.drawClaimRectangle(this.rawSelectionBounds(this.claimStart, event.latlng), false);
    }

    async finishClaimDrag(event) {
        if (!this.claimMode || !this.claimDragging || !this.claimStart) {
            return;
        }
        this.claimDragging = false;
        const selection = this.selectionFromLatLngs(this.claimStart, event.latlng);
        this.claimStart = null;
        if (!selection) {
            this.updateClaimPanel(this.t('claimTooSmall'));
            return;
        }
        this.claimSelection = selection;
        this.updateClaimPanel(this.t('claimPreviewing'));
        try {
            const preview = await this.previewClaimSelection(selection);
            this.claimPreview = preview;
            this.applyServerClaimSelection(preview);
            this.drawClaimRectangle(this.claimPreviewBounds(preview), Boolean(preview.canSubmit));
            this.updateClaimPanel(preview.message || this.t('claimReady'));
        } catch (error) {
            console.warn('STARCORE claim preview failed', error);
            this.claimPreview = null;
            this.updateClaimPanel(error.message || this.t('claimSubmitFailed'));
        }
    }

    latLngToBlock(latlng) {
        return {
            x: Math.floor(latlng.lng),
            z: Math.floor(-latlng.lat),
        };
    }

    selectionFromLatLngs(start, end) {
        const world = this.activeClaimWorld();
        if (!world) {
            return null;
        }
        const first = this.latLngToBlock(start);
        const second = this.latLngToBlock(end);
        return {
            world,
            minX: Math.min(first.x, second.x),
            maxX: Math.max(first.x, second.x),
            minZ: Math.min(first.z, second.z),
            maxZ: Math.max(first.z, second.z),
        };
    }

    rawSelectionBounds(start, end) {
        const first = this.latLngToBlock(start);
        const second = this.latLngToBlock(end);
        const minX = Math.min(first.x, second.x);
        const maxX = Math.max(first.x, second.x);
        const minZ = Math.min(first.z, second.z);
        const maxZ = Math.max(first.z, second.z);
        return L.latLngBounds([[-maxZ, minX], [-minZ, maxX]]);
    }

    claimPreviewBounds(preview) {
        const minChunkX = Number(preview.minChunkX);
        const maxChunkX = Number(preview.maxChunkX);
        const minChunkZ = Number(preview.minChunkZ);
        const maxChunkZ = Number(preview.maxChunkZ);
        if ([minChunkX, maxChunkX, minChunkZ, maxChunkZ].every(Number.isFinite)) {
            const size = MapConfig.chunkSize;
            return L.latLngBounds([
                [-(maxChunkZ + 1) * size, minChunkX * size],
                [-minChunkZ * size, (maxChunkX + 1) * size],
            ]);
        }
        return this.rawSelectionBounds(this.claimStart || this.map.getCenter(), this.map.getCenter());
    }

    drawClaimRectangle(bounds, accepted) {
        const style = {
            color: accepted ? '#2f7a47' : '#c58a32',
            weight: 2,
            opacity: 0.95,
            fillColor: accepted ? '#2f7a47' : '#c58a32',
            fillOpacity: 0.18,
            dashArray: accepted ? null : '6 5',
            interactive: false,
        };
        if (!this.claimRectangle) {
            this.claimRectangle = L.rectangle(bounds, style).addTo(this.map);
            return;
        }
        this.claimRectangle.setBounds(bounds);
        this.claimRectangle.setStyle(style);
    }

    async previewClaimSelection(selection) {
        const resp = await fetch(this.claimApiUrl(MapConfig.claimPreviewUrl, selection), {
            method: 'POST',
            cache: 'no-store',
        });
        const data = await this.readJsonResponse(resp);
        if (!resp.ok) {
            throw new Error(data.message || this.t('claimSubmitFailed'));
        }
        return data;
    }

    async submitClaimRequest() {
        if (!this.claimSelection || !this.claimPreview || !this.claimPreview.canSubmit || this.claimSubmitting) {
            return;
        }
        this.claimSubmitting = true;
        let keepSubmitMessage = false;
        this.updateClaimPanel(this.t('claimPreviewing'));
        try {
            const resp = await fetch(this.claimApiUrl(MapConfig.claimRequestUrl, this.claimSelection), {
                method: 'POST',
                cache: 'no-store',
            });
            const data = await this.readJsonResponse(resp);
            this.claimPreview = data;
            if (!resp.ok || !data.requestSubmitted) {
                this.updateClaimPanel(data.message || this.t('claimSubmitFailed'));
                return;
            }
            const suffix = data.pendingId ? ` (${data.pendingId})` : '';
            this.claimPreview = { ...data, canSubmit: false };
            keepSubmitMessage = true;
            this.updateClaimPanel(`${this.t('claimSubmitted')}${suffix}`);
        } catch (error) {
            console.warn('STARCORE claim request failed', error);
            this.updateClaimPanel(error.message || this.t('claimSubmitFailed'));
        } finally {
            this.claimSubmitting = false;
            if (!keepSubmitMessage) {
                this.updateClaimPanel();
            }
        }
    }

    async readJsonResponse(resp) {
        try {
            return await resp.json();
        } catch (_ignored) {
            return { ok: false, canSubmit: false, message: this.t('claimSubmitFailed') };
        }
    }

    claimApiUrl(baseUrl, selection) {
        const params = new URLSearchParams();
        params.set('world', selection.world);
        params.set('minX', String(selection.minX));
        params.set('maxX', String(selection.maxX));
        params.set('minZ', String(selection.minZ));
        params.set('maxZ', String(selection.maxZ));
        return this.buildApiUrl(`${baseUrl}?${params.toString()}`);
    }

    applyServerClaimSelection(preview) {
        if (!preview || !this.claimSelection) {
            return;
        }
        this.claimSelection = {
            world: preview.world || this.claimSelection.world,
            minX: Number(preview.minX ?? this.claimSelection.minX),
            maxX: Number(preview.maxX ?? this.claimSelection.maxX),
            minZ: Number(preview.minZ ?? this.claimSelection.minZ),
            maxZ: Number(preview.maxZ ?? this.claimSelection.maxZ),
        };
    }

    clearClaimSelection() {
        this.claimDragging = false;
        this.claimStart = null;
        this.claimSelection = null;
        this.claimPreview = null;
        if (this.claimRectangle) {
            this.map.removeLayer(this.claimRectangle);
            this.claimRectangle = null;
        }
        this.updateClaimPanel();
    }

    updateClaimPanel(message = '') {
        const toggle = document.getElementById('claim-toggle');
        const submit = document.getElementById('claim-submit');
        const chunks = document.getElementById('claim-chunks');
        const price = document.getElementById('claim-price');
        const balance = document.getElementById('claim-balance');
        const status = document.getElementById('claim-status');
        const details = document.getElementById('claim-details');
        if (!toggle || !submit || !chunks || !price || !balance || !status || !details) {
            return;
        }
        const panel = document.getElementById('claim-panel');
        const worldReady = Boolean(this.activeClaimWorld());
        const canUse = this.claimAuthenticated && worldReady;
        const shouldExpand = Boolean(this.claimMode || this.claimSelection || this.claimPreview || this.claimSubmitting || message);
        if (panel) {
            panel.classList.toggle('is-available', canUse);
            panel.classList.toggle('is-active', shouldExpand);
        }
        toggle.textContent = this.t(this.claimMode ? 'claimModeOn' : 'claimModeOff');
        toggle.disabled = !canUse;
        chunks.textContent = String((this.claimPreview && this.claimPreview.chunkCount) || 0);
        price.textContent = this.formatMoney((this.claimPreview && this.claimPreview.price) || '0.00');
        balance.textContent = this.formatMoney((this.claimPreview && this.claimPreview.balance) || (this.viewerInfo && this.viewerInfo.balance) || '0.00');
        this.renderClaimPriceDetails(details);
        const canSubmit = Boolean(this.claimPreview && this.claimPreview.canSubmit && this.claimSelection && !this.claimSubmitting);
        submit.disabled = !canSubmit;
        if (message) {
            status.textContent = message;
        } else if (!this.claimAuthenticated) {
            status.textContent = this.t('claimNeedAuth');
        } else if (!worldReady) {
            status.textContent = this.t('claimNoWorld');
        } else if (this.claimMode) {
            status.textContent = (this.claimPreview && this.claimPreview.message) || this.t('claimReady');
        } else {
            status.textContent = this.t('claimDisabled');
        }
    }

    renderClaimPriceDetails(container) {
        const preview = this.claimPreview || null;
        if (!preview) {
            container.innerHTML = '';
            return;
        }
        const pricing = preview.pricing || {};
        const chunks = Array.isArray(pricing.chunks) ? pricing.chunks : [];
        const chunkCount = Number(preview.chunkCount || pricing.chunkCount || 0);
        const currentClaimCount = Number(preview.currentClaimCount || 0);
        const maxClaims = Number(preview.maxClaims || 0);
        const overlapCount = Number(preview.overlapCount || 0);
        const totalPrice = Number(preview.price || pricing.totalPrice || 0);
        const baseChunkPrice = this.formatMoney(pricing.baseChunkPrice || '0.00');
        const averagePrice = chunkCount > 0 ? this.formatMoney(totalPrice / chunkCount) : this.formatMoney('0.00');
        const detailLimit = Number(pricing.detailLimit || chunks.length || 0);
        const explanation = preview.explanation || {};
        const reasons = Array.isArray(explanation.reasons) ? explanation.reasons : [];
        const summaryRows = [
            [
                `${this.t('claimCoverage')} ${this.t('claimCapacitySummary', {
                    used: currentClaimCount,
                    next: currentClaimCount + Math.max(0, chunkCount - overlapCount),
                    limit: maxClaims > 0 ? maxClaims : this.t('unlimited'),
                })}`,
                `${this.t('claimOverlap')} ${this.formatWholeNumber(overlapCount)}`
            ],
            [
                `${this.t('claimBaseUnitPrice')} ${baseChunkPrice}`,
                `${this.t('claimPricePerChunk')} ${averagePrice}`
            ],
        ];
        const reasonRows = reasons.slice(0, 6).map(reason => {
            const main = String(reason && reason.message ? reason.message : reason && reason.code ? reason.code : '');
            const detailsText = this.formatClaimReasonDetails(reason && reason.details ? reason.details : {});
            return `<div class="claim-detail-row claim-detail-row-summary claim-detail-row-reason">
                <div>
                    <div class="claim-detail-main">${this.escape(main)}</div>
                    <div class="claim-detail-sub">${this.escape(detailsText)}</div>
                </div>
            </div>`;
        });
        const chunkRows = chunks.map(chunk => {
            const biome = String(chunk.biome || 'unknown').replaceAll('_', ' ');
            const distance = Number(chunk.distanceBlocks || 0);
            const richness = Number(chunk.biomeRichness || 0);
            const distanceMultiplier = Number(chunk.distanceMultiplier || 0);
            const biomeMultiplier = Number(chunk.biomeMultiplier || 0);
            return `<div class="claim-detail-row claim-detail-row-emphasis">
                <div>
                    <div class="claim-detail-main">${this.escape(chunk.chunkX)}, ${this.escape(chunk.chunkZ)} · ${this.escape(biome)}</div>
                    <div class="claim-detail-sub">${this.escape(this.t('claimDistance'))} ${this.escape(distance)} · ${this.escape(this.t('claimRichness'))} ${this.escape(richness.toFixed(2))} · ${this.escape(this.t('claimDistanceFactor'))} ${this.escape(distanceMultiplier.toFixed(3))} · ${this.escape(this.t('claimBiomeFactor'))} ${this.escape(biomeMultiplier.toFixed(3))}</div>
                </div>
                <div class="claim-detail-price">${this.escape(this.formatMoney(chunk.price || '0.00'))}</div>
            </div>`;
        });
        const moreCount = Math.max(0, chunkCount - Math.min(detailLimit, chunks.length));
        if (moreCount > 0) {
            chunkRows.push(`<div class="claim-detail-row claim-detail-row-summary"><div><div class="claim-detail-sub">${this.escape(this.t('claimDetailMore', { count: moreCount }))}</div></div></div>`);
        }
        const fallbackSummaryHtml = summaryRows.map(([main, sub]) => `<div class="claim-detail-row claim-detail-row-summary"><div><div class="claim-detail-main">${this.escape(main)}</div><div class="claim-detail-sub">${this.escape(sub || '')}</div></div></div>`).join('');
        const explanationHeading = reasonRows.length > 0 ? `<div class="claim-detail-heading">${this.escape(this.t('claimExplanation'))}</div>` : '';
        const summaryHtml = reasonRows.length > 0 ? `${explanationHeading}${reasonRows.join('')}` : fallbackSummaryHtml;
        const heading = chunks.length > 0 ? `<div class="claim-detail-heading">${this.escape(this.t('claimTopChunks'))}</div>` : '';
        container.innerHTML = `${summaryHtml}${heading}${chunkRows.join('')}`;
    }

    formatClaimReasonDetails(details) {
        if (!details || typeof details !== 'object') {
            return '';
        }
        const preferred = ['shortfall', 'projectedClaims', 'maxClaims', 'provider', 'coordinate', 'distanceMultiplier', 'biomeMultiplier'];
        const parts = [];
        preferred.forEach(key => {
            if (details[key] !== undefined && details[key] !== null && String(details[key]).trim() !== '') {
                parts.push(`${key}=${details[key]}`);
            }
        });
        if (parts.length === 0) {
            Object.keys(details).slice(0, 3).forEach(key => {
                parts.push(`${key}=${details[key]}`);
            });
        }
        return parts.slice(0, 3).join(' · ');
    }

    formatMoney(value) {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return String(value || '0.00');
        }
        return numeric.toFixed(2);
    }

    parseBooleanFlag(value) {
        if (typeof value === 'boolean') {
            return value;
        }
        const normalized = String(value == null ? '' : value).trim().toLowerCase();
        return normalized === 'true' || normalized === '1' || normalized === 'yes' || normalized === 'on';
    }

    metadataBoolean(meta, key, fallback = false) {
        if (meta && Object.prototype.hasOwnProperty.call(meta, key)) {
            return this.parseBooleanFlag(meta[key]);
        }
        return fallback;
    }

    resolveResourceMarkerVisualState(resource) {
        const source = resource && resource.data ? resource.data : resource;
        const meta = (source || {}).metadata || {};
        const relation = this.relationForNation(meta.nationId, meta);
        const style = this.relationStyleFor(relation, this.selectedNationId !== null);
        const color = meta.displayColor || this.nationColors.get(meta.nationId) || 'var(--warning)';
        const operationState = this.normalizeResourceActionState(meta);
        const resourceId = String((resource || {}).id || (source || {}).id || '');
        const selectedFilter = String(this.nationPriorityFilter || 'all');
        const matchesFocusedNation = this.selectedNationId && String(meta.nationId || '') === String(this.selectedNationId);
        const matchesPriorityFilter = matchesFocusedNation
            && selectedFilter !== 'all'
            && operationState === selectedFilter;
        const isSelectedResource = Boolean(this.selectedResourceId)
            && String(this.selectedResourceId) === resourceId;
        return {
            relation,
            style,
            color,
            operationState,
            matchesPriorityFilter,
            isSelectedResource,
            className: `resource-marker ${this.relationClassFor(relation)}${matchesPriorityFilter ? ' is-priority-match' : ''}${isSelectedResource ? ' is-selected' : ''}`,
        };
    }

    updateResourceMarkerVisualStates() {
        this.resourceMarkers.forEach((resource) => {
            const marker = resource.marker;
            if (!marker) {
                return;
            }
            const visualState = this.resolveResourceMarkerVisualState(resource);
            if (typeof marker.setZIndexOffset === 'function') {
                const priorityOffset = visualState.matchesPriorityFilter ? 120 : 0;
                const selectedOffset = visualState.isSelectedResource ? 240 : 0;
                marker.setZIndexOffset(80 + visualState.style.zIndexOffset + priorityOffset + selectedOffset);
            }
            const markerElement = typeof marker.getElement === 'function' ? marker.getElement() : null;
            const markerBody = markerElement && typeof markerElement.querySelector === 'function'
                ? markerElement.querySelector('.resource-marker')
                : null;
            if (!markerBody) {
                return;
            }
            markerBody.className = visualState.className;
            markerBody.style.borderColor = visualState.color;
            markerBody.style.opacity = String(Math.max(0.68, visualState.style.opacity));
        });
    }

    resolvePlayerMarkerVisualState(player) {
        const source = player && player.data ? player.data : player;
        const meta = (source || {}).metadata || {};
        const relation = this.relationForNation(meta.nationId, meta);
        const normalizedRelation = relation.toLowerCase();
        const style = this.relationStyleFor(relation, this.selectedNationId !== null);
        const allied = normalizedRelation === 'member' || normalizedRelation === 'allied' || normalizedRelation === 'friendly';
        const borderColor = allied
            ? (meta.displayColor || 'var(--success)')
            : (normalizedRelation === 'hostile' || normalizedRelation === 'war' ? 'var(--danger)' : 'var(--ink-muted)');
        const playerId = String((player || {}).id || (source || {}).id || '');
        const isSelectedPlayer = Boolean(this.selectedPlayerId) && String(this.selectedPlayerId) === playerId;
        return {
            borderColor,
            opacity: Math.max(0.62, style.opacity),
            zIndexOffset: 100 + style.zIndexOffset + (isSelectedPlayer ? 240 : 0),
            className: `player-marker ${this.relationClassFor(relation)}${allied ? ' allied' : ''}${isSelectedPlayer ? ' is-selected' : ''}`,
        };
    }

    updatePlayerMarkerVisualStates() {
        this.playerMarkers.forEach((player) => {
            const marker = player.marker;
            if (!marker) {
                return;
            }
            const visualState = this.resolvePlayerMarkerVisualState(player);
            if (typeof marker.setZIndexOffset === 'function') {
                marker.setZIndexOffset(visualState.zIndexOffset);
            }
            const markerElement = typeof marker.getElement === 'function' ? marker.getElement() : null;
            const markerBody = markerElement && typeof markerElement.querySelector === 'function'
                ? markerElement.querySelector('.player-marker')
                : null;
            if (!markerBody) {
                return;
            }
            markerBody.className = visualState.className;
            markerBody.style.borderColor = visualState.borderColor;
            markerBody.style.opacity = String(visualState.opacity);
        });
    }

    setSelectedNation(nationId, options = {}) {
        const nextId = nationId || null;
        if (this.selectedNationId === nextId) {
            return;
        }
        this.selectedNationId = nextId;
        this.nationRecentLogFilter = 'all';
        this.resetFinanceLedgerState(nextId || '');
        this.resetEventLedgerState(nextId || '');
        if (this.snapshot) {
            this.renderSnapshot(this.snapshot, {
                allowPulse: false,
                skipFit: options.preserveView === true,
                forceRender: true,
            });
        }
    }

    relationForNation(nationId, metadata = {}) {
        if (!this.selectedNationId) {
            return metadata.relation || 'neutral';
        }
        if (nationId && nationId === this.selectedNationId) {
            return 'member';
        }
        const relations = ((this.snapshot || {}).diplomacy || {}).relations || {};
        const focusedRelations = relations[this.selectedNationId] || {};
        if (nationId && focusedRelations[nationId]) {
            return focusedRelations[nationId];
        }
        return metadata.relation || 'neutral';
    }

    relationStyleFor(relation, focused = false) {
        const normalized = (relation || 'neutral').toLowerCase();
        if (!focused) {
            return { opacity: 0.82, fillOpacity: 0.34, weight: 1, dashArray: null, zIndexOffset: 0 };
        }
        switch (normalized) {
            case 'member':
                return { opacity: 1, fillOpacity: 0.56, weight: 2, dashArray: null, zIndexOffset: 500 };
            case 'allied':
            case 'friendly':
            case 'vassal':
                return { opacity: 0.92, fillOpacity: 0.38, weight: 1.5, dashArray: null, zIndexOffset: 300 };
            case 'hostile':
            case 'war':
                return { opacity: 0.96, fillOpacity: 0.26, weight: 2, dashArray: '7 5', zIndexOffset: 400 };
            default:
                return { opacity: 0.46, fillOpacity: 0.12, weight: 1, dashArray: '4 6', zIndexOffset: 100 };
        }
    }

    relationPriorityFor(relation) {
        switch ((relation || 'neutral').toLowerCase()) {
            case 'member':
                return 0;
            case 'war':
            case 'hostile':
                return 1;
            case 'allied':
            case 'friendly':
            case 'vassal':
                return 2;
            default:
                return 3;
        }
    }

    populateWorldFilter(snapshot) {
        const group = document.getElementById('world-filter');
        if (!group) {
            return;
        }
        const worlds = snapshot.worlds || this.inferWorlds(snapshot);
        const previous = this.currentWorld;
        this.currentWorld = worlds.includes(previous) || previous === 'all' ? previous : 'all';
        group.innerHTML = ['all', ...worlds].map(world => {
            const active = this.currentWorld === world;
            const label = world === 'all' ? this.t('allWorlds') : world;
            return `<button type="button" class="chip-button motion-button${active ? ' is-active' : ''}" data-world="${this.escape(world)}" aria-pressed="${active}">${this.escape(label)}</button>`;
        }).join('');
        this.bindMotionButtons();
    }

    updateWorldDisplay() {
        const worldName = document.getElementById('world-name');
        if (!worldName) {
            return;
        }
        worldName.textContent = this.currentWorld === 'all' ? this.t('allWorlds') : this.currentWorld;
    }

    inferWorlds(snapshot) {
        const worlds = new Set();
        for (const layer of snapshot.layers || []) {
            for (const territory of layer.territories || []) {
                worlds.add(territory.world);
            }
            for (const marker of layer.markers || []) {
                worlds.add(marker.world);
            }
        }
        return Array.from(worlds);
    }

    updateSummary(snapshot, territories, markers, resources = []) {
        const summaryData = snapshot.summary || {};
        const nationCount = summaryData.nationCount ?? new Set(territories.map(item => item.ownerId)).size;
        const worldCount = summaryData.worldCount ?? (snapshot.worlds || this.inferWorlds(snapshot)).length;
        const claimCount = summaryData.claimCount ?? territories.length;
        const playerCount = summaryData.onlinePlayers ?? markers.length;
        const resourceCount = summaryData.resourceDistricts ?? resources.length;
        const generatedAt = snapshot.generatedAt ? this.formatTimestamp(snapshot.generatedAt) : this.t('generatedUnknown');
        const summary = document.getElementById('map-summary');
        if (!summary) {
            return;
        }
        summary.innerHTML = `
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryGenerated'))}</span>
                <strong>${this.escape(generatedAt)}</strong>
            </div>
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryWorlds'))}</span>
                <strong>${worldCount}</strong>
            </div>
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryNations'))}</span>
                <strong>${nationCount}</strong>
            </div>
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryClaims'))}</span>
                <strong>${claimCount}</strong>
            </div>
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryPlayers'))}</span>
                <strong>${playerCount}</strong>
            </div>
            <div class="summary-card">
                <span class="summary-label">${this.escape(this.t('summaryResources'))}</span>
                <strong>${resourceCount}</strong>
            </div>
        `;
    }

    updateIntel(snapshot, territories, markers) {
        const intel = document.getElementById('intel-grid');
        if (!intel) {
            return;
        }
        const dominantNation = this.dominantNationName(territories);
        const playerDensity = territories.length > 0 ? (markers.length / territories.length).toFixed(2) : '0.00';
        const health = this.health || {};
        const mode = this.streamActive ? this.t('modeRealtime') : (this._localSnapshotAvailable ? this.t('modeStatic') : this.t('modePolling'));
        intel.innerHTML = `
            <div class="intel-card">
                <span class="intel-label">${this.escape(this.t('intelMode'))}</span>
                <strong>${this.escape(mode)}</strong>
                <p>${this.escape(this.describeStatusDetail())}</p>
            </div>
            ${this.buildViewerIntelCard(snapshot.viewer)}
            <div class="intel-card">
                <span class="intel-label">${this.escape(this.t('intelDominantNation'))}</span>
                <strong>${this.escape(dominantNation)}</strong>
                <p>${this.escape(this.t('intelDominantNationDesc'))}</p>
            </div>
            <div class="intel-card">
                <span class="intel-label">${this.escape(this.t('intelDensity'))}</span>
                <strong>${this.escape(playerDensity)}</strong>
                <p>${this.escape(this.t('intelDensityDesc'))}</p>
            </div>
            <div class="intel-card">
                <span class="intel-label">${this.escape(this.t('intelHealth'))}</span>
                <strong>${this.escape(this.t('intelHealthSockets', { count: String(health.sseClients ?? 0) }))}</strong>
                <p>${this.escape(this.t('intelHealthDesc', {
                    cache: health.avatarCacheEnabled ? this.t('cacheEnabled') : this.t('cacheDisabled'),
                    players: health.onlinePlayers ?? 0,
                }))}</p>
            </div>
        `;
    }

    buildViewerIntelCard(viewer) {
        if (!viewer) {
            return `<div class="intel-card">
                <span class="intel-label">${this.escape(this.t('viewerCard'))}</span>
                <strong>${this.escape(this.t('viewerAnonymous'))}</strong>
                <p>${this.escape(this.t('viewerAnonymousDesc'))}</p>
            </div>`;
        }
        const name = viewer.playerName || this.t('unknown');
        const nation = viewer.nationName || this.t('noNation');
        const role = this.formatViewerRole(viewer.role || 'independent');
        const onlineText = viewer.online ? this.t('viewerOnlineYes') : this.t('viewerOnlineNo');
        const positionText = viewer.online && viewer.world
            ? `${viewer.world} · ${Number(viewer.x || 0)}, ${Number(viewer.y || 0)}, ${Number(viewer.z || 0)}`
            : this.t('viewerOnlineNo');
        const metrics = [
            [this.t('viewerBalanceLabel'), this.formatMoney(viewer.balance || '0.00')],
            [this.t('viewerRoleLabel'), role],
            [this.t('viewerNationLabel'), nation],
            [this.t('nationKindLabel'), this.formatNationKind(viewer.nationKind || 'independent')],
            [this.t('viewerOnlineLabel'), onlineText],
            [this.t('viewerPositionLabel'), positionText],
        ];
        const details = [];
        if (viewer.nationName) {
            const claims = `${Number(viewer.claimCount || 0)}/${this.formatLimit(viewer.claimLimit)}`;
            const resources = `${Number(viewer.resourceDistrictCount || 0)}/${this.formatLimit(viewer.resourceDistrictLimit)}`;
            const cityStates = `${Number(viewer.cityStateCount || 0)}/${this.formatLimit(viewer.cityStateLimit)}`;
            const levelProgress = this.formatExperienceProgress(
                viewer.nationExperienceProgress,
                viewer.nationNextLevelExperience,
                viewer.nationMaxLevelReached
            );
            metrics.push(
                [this.t('viewerLevelLabel'), String(Number(viewer.nationLevel || 1))],
                [this.t('viewerClaimsLabel'), claims],
                [this.t('viewerCityStatesLabel'), cityStates],
                [this.t('viewerResourcesLabel'), resources],
            );
            details.push(
                `${this.t('viewerFounderLabel')} ${viewer.founderName || this.t('unknown')}`,
                `${this.t('viewerGovernmentLabel')} ${this.formatGovernment(viewer.government || 'UNKNOWN')}`,
                `${this.t('viewerExperienceLabel')} ${Number(viewer.nationExperience || 0)}`,
                `${this.t('viewerProgressLabel')} ${levelProgress}`,
                `${this.t('viewerNextLevelLabel')} ${this.formatExperienceRemaining(viewer.nationExperienceRemaining, viewer.nationMaxLevelReached)}`,
            );
        }
        return `<div class="intel-card viewer-intel-card">
            <span class="intel-label">${this.escape(this.t('viewerCard'))}</span>
            <strong>${this.escape(name)}</strong>
            <p>${this.escape(`${role} · ${onlineText}`)}</p>
            <div class="viewer-intel-grid">
                ${metrics.map(([label, value]) => `<div class="viewer-intel-metric"><span>${this.escape(label)}</span><strong>${this.escape(value)}</strong></div>`).join('')}
            </div>
            ${details.length ? `<div class="intel-detail-list">${details.map(detail => `<span>${this.escape(detail)}</span>`).join('')}</div>` : ''}
        </div>`;
    }

    updateHudIdentity() {
        const root = document.getElementById('hud-identity');
        const avatar = document.getElementById('hud-identity-avatar');
        const name = document.getElementById('hud-identity-name');
        if (!root || !avatar || !name) {
            return;
        }

        const viewer = this.viewerInfo;
        if (!viewer || !viewer.playerName) {
            root.hidden = true;
            root.classList.remove('has-avatar');
            avatar.classList.remove('fallback');
            avatar.style.removeProperty('background-image');
            avatar.replaceChildren();
            name.textContent = '';
            return;
        }

        const viewerName = viewer.playerName || this.t('unknown');
        const avatarUrl = viewer.avatarUrl || this.avatarUrlFromId(viewer.playerId);
        root.hidden = false;
        root.setAttribute('aria-label', viewerName);
        name.textContent = viewerName;
        avatar.classList.toggle('fallback', !avatarUrl);
        avatar.style.removeProperty('background-image');
        avatar.replaceChildren();

        if (avatarUrl) {
            const img = document.createElement('img');
            img.src = avatarUrl;
            img.alt = '';
            img.loading = 'lazy';
            img.referrerPolicy = 'no-referrer';
            img.addEventListener('error', () => {
                avatar.replaceChildren();
                avatar.classList.add('fallback');
                avatar.textContent = this.initialsForName(viewerName);
            }, { once: true });
            avatar.appendChild(img);
        } else {
            avatar.textContent = this.initialsForName(viewerName);
        }
    }

    initialsForName(name) {
        const clean = String(name || '').trim();
        if (!clean) {
            return '?';
        }
        return clean.replace(/[^A-Za-z0-9\u4E00-\u9FFF]/g, '').slice(0, 2).toUpperCase() || clean.slice(0, 1).toUpperCase();
    }

    formatViewerRole(role) {
        switch ((role || 'independent').toLowerCase()) {
            case 'founder':
                return this.t('viewerRoleFounder');
            case 'member':
                return this.t('viewerRoleMember');
            default:
                return this.t('viewerRoleIndependent');
        }
    }

    formatNationKind(kind) {
        switch ((kind || 'independent').toLowerCase()) {
            case 'nation':
                return this.t('nationKindNation');
            case 'city_state':
                return this.t('nationKindCityState');
            default:
                return this.t('nationKindIndependent');
        }
    }

    formatLimit(value) {
        const number = Number(value);
        if (!Number.isFinite(number) || number < 0) {
            return this.t('unlimited');
        }
        return String(number);
    }

    formatCapacity(current, limit) {
        const count = Number(current);
        const normalized = Number.isFinite(count) && count >= 0 ? count : 0;
        return `${normalized}/${this.formatLimit(limit)}`;
    }

    formatWholeNumber(value, fallback = '0') {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return fallback;
        }
        return String(Math.max(0, Math.round(numeric)));
    }

    formatExperienceProgress(current, total, maxLevelReached = false) {
        if (this.parseBooleanFlag(maxLevelReached)) {
            return this.t('viewerMaxLevel');
        }
        return `${this.formatWholeNumber(current)}/${this.formatWholeNumber(total)}`;
    }

    formatExperienceRemaining(remaining, maxLevelReached = false) {
        return this.parseBooleanFlag(maxLevelReached)
            ? this.t('viewerMaxLevel')
            : this.formatWholeNumber(remaining);
    }

    formatViewerOnlineLabel(value) {
        return this.parseBooleanFlag(value)
            ? this.t('viewerOnlineYes')
            : this.t('viewerOnlineNo');
    }

    formatDecimal(value, digits = 1, fallback = '0.0') {
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            return fallback;
        }
        return numeric.toFixed(digits);
    }

    estimateNationLevelEtaHours(experienceRemaining, hourlyExperienceYield, maxLevelReached = false) {
        if (this.parseBooleanFlag(maxLevelReached)) {
            return this.t('viewerMaxLevel');
        }
        const remaining = Number(experienceRemaining || 0);
        const hourlyYield = Number(hourlyExperienceYield || 0);
        if (!Number.isFinite(remaining) || remaining <= 0) {
            return this.language === 'zh' ? '0小时' : '0h';
        }
        if (!Number.isFinite(hourlyYield) || hourlyYield <= 0) {
            return this.t('nationDetailEtaUnknown');
        }
        const hours = remaining / hourlyYield;
        if (hours >= 24) {
            const days = hours / 24;
            return this.language === 'zh'
                ? `${this.formatDecimal(days, 1, '0.0')}天`
                : `${this.formatDecimal(days, 1, '0.0')}d`;
        }
        return this.language === 'zh'
            ? `${this.formatDecimal(hours, 1, '0.0')}小时`
            : `${this.formatDecimal(hours, 1, '0.0')}h`;
    }

    parseIsoTimestamp(value) {
        if (!value) {
            return null;
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    formatRelativeTimeFromNow(value) {
        const date = value instanceof Date ? value : this.parseIsoTimestamp(value);
        if (!date) {
            return this.t('nationDetailForecastNoEvent');
        }
        const diffMs = date.getTime() - Date.now();
        if (diffMs <= 0) {
            return this.t('nationDetailForecastReadyNow');
        }
        const totalMinutes = Math.ceil(diffMs / 60000);
        if (totalMinutes < 60) {
            return this.language === 'zh'
                ? `${totalMinutes}分钟后`
                : `in ${totalMinutes}m`;
        }
        const totalHours = diffMs / 3600000;
        if (totalHours < 24) {
            return this.language === 'zh'
                ? `${this.formatDecimal(totalHours, 1, '0.0')}小时后`
                : `in ${this.formatDecimal(totalHours, 1, '0.0')}h`;
        }
        const totalDays = totalHours / 24;
        return this.language === 'zh'
            ? `${this.formatDecimal(totalDays, 1, '0.0')}天后`
            : `in ${this.formatDecimal(totalDays, 1, '0.0')}d`;
    }

    formatRelativeMoment(value) {
        const date = value instanceof Date ? value : this.parseIsoTimestamp(value);
        if (!date) {
            return this.t('generatedUnknown');
        }
        const diffMs = date.getTime() - Date.now();
        const past = diffMs < 0;
        const absMs = Math.abs(diffMs);
        const totalMinutes = Math.max(1, Math.ceil(absMs / 60000));
        if (totalMinutes < 60) {
            return this.language === 'zh'
                ? `${totalMinutes}分钟${past ? '前' : '后'}`
                : (past ? `${totalMinutes}m ago` : `in ${totalMinutes}m`);
        }
        const totalHours = absMs / 3600000;
        if (totalHours < 24) {
            const text = this.formatDecimal(totalHours, 1, '0.0');
            return this.language === 'zh'
                ? `${text}小时${past ? '前' : '后'}`
                : (past ? `${text}h ago` : `in ${text}h`);
        }
        const totalDays = totalHours / 24;
        const text = this.formatDecimal(totalDays, 1, '0.0');
        return this.language === 'zh'
            ? `${text}天${past ? '前' : '后'}`
            : (past ? `${text}d ago` : `in ${text}d`);
    }

    formatRecommendationWindow(value) {
        const date = value instanceof Date ? value : this.parseIsoTimestamp(value);
        if (!date) {
            return this.t('nationDetailForecastNoEvent');
        }
        return `${this.formatRelativeTimeFromNow(date)} · ${this.formatTimestamp(date)}`;
    }

    summarizeForecastEvent(resources, key, horizonMs = 0) {
        const source = Array.isArray(resources) ? resources : [];
        const now = Date.now();
        const dated = source
            .map(({ resource, meta }) => {
                const date = this.parseIsoTimestamp(meta[key]);
                if (!date) {
                    return null;
                }
                const diffMs = date.getTime() - now;
                return {
                    resource,
                    meta,
                    date,
                    diffMs,
                };
            })
            .filter(Boolean)
            .sort((left, right) => left.date.getTime() - right.date.getTime());
        const upcoming = dated.filter(entry => entry.diffMs >= 0);
        const lead = upcoming[0] || dated[0] || null;
        const withinHorizon = horizonMs > 0
            ? upcoming.filter(entry => entry.diffMs <= horizonMs)
            : [];
        return {
            count: dated.length,
            upcomingCount: upcoming.length,
            withinHorizonCount: withinHorizon.length,
            lead,
        };
    }

    estimateNationLevelCycles(experienceRemaining, cycleExperienceYield, maxLevelReached = false) {
        if (this.parseBooleanFlag(maxLevelReached)) {
            return this.t('viewerMaxLevel');
        }
        const remaining = Number(experienceRemaining || 0);
        const cycleYield = Number(cycleExperienceYield || 0);
        if (!Number.isFinite(remaining) || remaining <= 0) {
            return this.language === 'zh' ? '0轮' : '0 cycles';
        }
        if (!Number.isFinite(cycleYield) || cycleYield <= 0) {
            return this.t('nationDetailEtaUnknown');
        }
        const cycles = remaining / cycleYield;
        return this.language === 'zh'
            ? `${this.formatDecimal(cycles, 1, '0.0')}轮`
            : `${this.formatDecimal(cycles, 1, '0.0')} cycles`;
    }

    resourceHourlyEstimate(meta = {}, yieldKey, hourlyKey) {
        const direct = Number(meta[hourlyKey] || 0);
        if (Number.isFinite(direct) && direct > 0) {
            return direct;
        }
        const cooldownMinutes = Number(meta.refreshCooldownMinutes || 0);
        const cycleYield = Number(meta[yieldKey] || 0);
        if (!Number.isFinite(cooldownMinutes) || cooldownMinutes <= 0 || !Number.isFinite(cycleYield) || cycleYield <= 0) {
            return 0;
        }
        return cycleYield * (60 / cooldownMinutes);
    }

    resourceForecastMetrics(meta = {}) {
        return {
            hourlyResource: this.resourceHourlyEstimate(meta, 'expectedResourceYield', 'expectedResourceYieldPerHour'),
            hourlyExperience: this.resourceHourlyEstimate(meta, 'expectedExperienceYield', 'expectedExperienceYieldPerHour'),
            hourlyTreasury: this.resourceHourlyEstimate(meta, 'expectedTreasuryIncomeYield', 'expectedTreasuryIncomeYieldPerHour'),
            next3Resource: Math.max(0, Number(meta.forecastResourceYieldNext3Cycles || 0)),
            next3Experience: Math.max(0, Number(meta.forecastExperienceYieldNext3Cycles || 0)),
            next3Treasury: Math.max(0, Number(meta.forecastTreasuryIncomeNext3Cycles || 0)),
            next3WindowMinutes: Math.max(0, Number(meta.forecastWindowMinutesNext3Cycles || 0)),
        };
    }

    nationOperationsForecast(resources, meta = {}) {
        const allResources = Array.isArray(resources) ? resources : [];
        const refreshEvent = this.summarizeForecastEvent(allResources, 'nextRefreshAt', 60 * 60 * 1000);
        const forcedMigrationEvent = this.summarizeForecastEvent(allResources, 'forceMigrationAt', 6 * 60 * 60 * 1000);
        const totalResourceYieldPerHour = allResources.reduce(
            (sum, entry) => sum + this.resourceHourlyEstimate(entry.meta || {}, 'expectedResourceYield', 'expectedResourceYieldPerHour'),
            0
        );
        const totalExperienceYieldPerHour = allResources.reduce(
            (sum, entry) => sum + this.resourceHourlyEstimate(entry.meta || {}, 'expectedExperienceYield', 'expectedExperienceYieldPerHour'),
            0
        );
        const totalTreasuryYieldPerHour = allResources.reduce(
            (sum, entry) => sum + this.resourceHourlyEstimate(entry.meta || {}, 'expectedTreasuryIncomeYield', 'expectedTreasuryIncomeYieldPerHour'),
            0
        );
        return {
            refreshEvent,
            forcedMigrationEvent,
            resourceYield6h: totalResourceYieldPerHour * 6,
            resourceYield12h: totalResourceYieldPerHour * 12,
            experienceYield6h: totalExperienceYieldPerHour * 6,
            experienceYield12h: totalExperienceYieldPerHour * 12,
            treasuryYield6h: totalTreasuryYieldPerHour * 6,
            treasuryYield12h: totalTreasuryYieldPerHour * 12,
            levelCycles: this.estimateNationLevelCycles(
                meta.nationExperienceRemaining,
                totalExperienceYieldPerHour,
                meta.nationMaxLevelReached
            ),
        };
    }

    nationOperationsTimeline(resources) {
        const source = Array.isArray(resources) ? resources : [];
        const now = Date.now();
        const events = [];
        source.forEach(({ resource, meta }) => {
            const summary = this.resourceOperationSummary(meta);
            const actionState = this.normalizeResourceActionState(meta);
            const nextStep = String(meta.migrationNextStep || summary.defaultHint || this.t('unknown'));
            const restriction = String(meta.migrationRestrictionDetail || meta.migrationRestriction || this.t('unknown'));
            const base = {
                resource,
                meta,
                summary,
                actionState,
                nextStep,
                restriction,
            };
            const refreshDate = this.parseIsoTimestamp(meta.nextRefreshAt);
            if (refreshDate) {
                events.push({
                    ...base,
                    type: 'refresh',
                    label: this.t('nationDetailTimelineRefresh'),
                    date: refreshDate,
                    diffMs: refreshDate.getTime() - now,
                });
            }
            const forcedDate = this.parseIsoTimestamp(meta.forceMigrationAt);
            if (forcedDate) {
                events.push({
                    ...base,
                    type: 'forced-migration',
                    label: this.t('nationDetailTimelineForcedMigration'),
                    date: forcedDate,
                    diffMs: forcedDate.getTime() - now,
                });
            }
            if (summary.actionState === 'awaiting-target') {
                events.push({
                    ...base,
                    type: 'awaiting-target',
                    label: this.t('nationDetailTimelineTargetPlacement'),
                    date: null,
                    diffMs: -1,
                });
            }
        });
        return events
            .sort((left, right) => {
                const leftImmediate = left.date ? 1 : 0;
                const rightImmediate = right.date ? 1 : 0;
                if (leftImmediate !== rightImmediate) {
                    return leftImmediate - rightImmediate;
                }
                if (!left.date && !right.date) {
                    return right.summary.rank - left.summary.rank;
                }
                if (left.diffMs < 0 && right.diffMs >= 0) {
                    return -1;
                }
                if (left.diffMs >= 0 && right.diffMs < 0) {
                    return 1;
                }
                if (left.date && right.date && left.date.getTime() !== right.date.getTime()) {
                    return left.date.getTime() - right.date.getTime();
                }
                return right.summary.rank - left.summary.rank;
            })
            .slice(0, 6);
    }

    nationOperationsOverview(resources, filteredResources, meta = {}) {
        const allResources = Array.isArray(resources) ? resources : [];
        const visibleResources = Array.isArray(filteredResources) ? filteredResources : [];
        const counts = {
            actionable: 0,
            waiting: 0,
            blocked: 0,
            insufficientBalance: 0,
            awaitingTarget: 0,
            playerOffline: 0,
            waitingDepletion: 0,
            leaderOnly: 0,
        };
        let totalResourceYieldPerHour = 0;
        let totalExperienceYieldPerHour = 0;
        let totalShortfall = 0;
        let peakShortfall = 0;

        allResources.forEach(({ meta: resourceMeta }) => {
            const actionState = this.normalizeResourceActionState(resourceMeta);
            const resourceYieldPerHour = this.resourceHourlyEstimate(resourceMeta || {}, 'expectedResourceYield', 'expectedResourceYieldPerHour');
            const experienceYieldPerHour = this.resourceHourlyEstimate(resourceMeta || {}, 'expectedExperienceYield', 'expectedExperienceYieldPerHour');
            const shortfall = Math.max(0, Number(resourceMeta.migrationBalanceShortfall || 0));
            totalResourceYieldPerHour += resourceYieldPerHour;
            totalExperienceYieldPerHour += experienceYieldPerHour;
            totalShortfall += shortfall;
            peakShortfall = Math.max(peakShortfall, shortfall);

            switch (actionState) {
                case 'ready':
                    counts.actionable += 1;
                    break;
                case 'awaiting-target':
                    counts.waiting += 1;
                    counts.awaitingTarget += 1;
                    break;
                case 'waiting-depletion':
                    counts.waiting += 1;
                    counts.waitingDepletion += 1;
                    break;
                case 'insufficient-balance':
                    counts.blocked += 1;
                    counts.insufficientBalance += 1;
                    break;
                case 'player-offline':
                    counts.blocked += 1;
                    counts.playerOffline += 1;
                    break;
                case 'leader-only':
                    counts.blocked += 1;
                    counts.leaderOnly += 1;
                    break;
                default:
                    break;
            }
        });

        const coverageRatio = allResources.length > 0 ? visibleResources.length / allResources.length : 0;
        let outlookKey = 'nationDetailOutlookNoData';
        if (counts.awaitingTarget > 0) {
            outlookKey = 'nationDetailOutlookAwaitingTarget';
        } else if (counts.actionable > 0) {
            outlookKey = 'nationDetailOutlookActionable';
        } else if (counts.insufficientBalance > 0) {
            outlookKey = 'nationDetailOutlookNeedFunds';
        } else if (counts.playerOffline > 0 || counts.leaderOnly > 0) {
            outlookKey = 'nationDetailOutlookLeaderOffline';
        } else if (counts.waitingDepletion > 0) {
            outlookKey = 'nationDetailOutlookWaitingDepletion';
        } else if (allResources.length > 0) {
            outlookKey = 'nationDetailOutlookStable';
        }

        return {
            counts,
            coverageRatio,
            totalResourceYieldPerHour,
            totalExperienceYieldPerHour,
            totalShortfall,
            peakShortfall,
            levelEta: this.estimateNationLevelEtaHours(
                meta.nationExperienceRemaining,
                totalExperienceYieldPerHour,
                meta.nationMaxLevelReached
            ),
            outlookLabel: this.t(outlookKey),
        };
    }

    nationOperationGroups(resources) {
        const source = Array.isArray(resources) ? resources : [];
        const definitions = [
            ['ready', 'nationDetailPriorityReady'],
            ['awaiting-target', 'nationDetailPriorityAwaitingTarget'],
            ['insufficient-balance', 'nationDetailPriorityInsufficientBalance'],
            ['player-offline', 'nationDetailPriorityPlayerOffline'],
            ['waiting-depletion', 'nationDetailPriorityWaitingDepletion'],
            ['leader-only', 'nationDetailPriorityLeaderOnly'],
        ];
        return definitions
            .map(([state, labelKey]) => {
                const entries = source.filter(({ meta }) => this.normalizeResourceActionState(meta) === state);
                if (!entries.length) {
                    return null;
                }
                return {
                    state,
                    label: this.t(labelKey),
                    count: entries.length,
                    lead: entries[0],
                };
            })
            .filter(Boolean);
    }

    nationOperationRecommendations(groups) {
        const source = Array.isArray(groups) ? groups : [];
        return source
            .map((group, index) => this.buildNationOperationRecommendation(group, index))
            .filter(Boolean)
            .slice(0, 3);
    }

    selectedNationOperationResources() {
        const nationId = String(this.selectedNationId || '');
        if (!nationId) {
            return {
                nationResources: [],
                rankedNationResources: [],
                filteredNationResources: [],
            };
        }
        const nationResources = this.resourceMarkers
            .filter(resource => String((((resource || {}).data || {}).metadata || {}).nationId || '') === nationId)
            .map(resource => ({ resource, meta: (((resource || {}).data || {}).metadata || {}) }));
        const rankedNationResources = this.rankNationResourcesForOperations(nationResources);
        const filteredNationResources = this.filterNationResourcesForOperations(rankedNationResources);
        return {
            nationResources,
            rankedNationResources,
            filteredNationResources,
        };
    }

    nationRecentEvents(meta = {}) {
        const count = Number(meta.recentEventCount || 0);
        if (!Number.isFinite(count) || count <= 0) {
            return [];
        }
        const events = [];
        for (let index = 0; index < count; index += 1) {
            const message = String(meta[`recentEvent${index}Message`] || '').trim();
            const type = String(meta[`recentEvent${index}Type`] || '').trim();
            const category = this.normalizeNationRecentEventCategory(meta[`recentEvent${index}Category`] || this.nationRecentEventCategory(type));
            const at = String(meta[`recentEvent${index}At`] || '').trim();
            const resourceId = String(meta[`recentEvent${index}ResourceId`] || '').trim();
            if (!message) {
                continue;
            }
            events.push({
                type,
                category,
                message,
                at,
                resourceId,
                date: this.parseIsoTimestamp(at),
            });
        }
        return events;
    }

    nationFinanceEvents(meta = {}) {
        const count = Math.min(3, Number(meta.financeEventCount || 0));
        if (!Number.isFinite(count) || count <= 0) {
            return [];
        }
        const events = [];
        for (let index = 0; index < count; index += 1) {
            const message = String(meta[`financeEvent${index}Message`] || '').trim();
            const type = String(meta[`financeEvent${index}Type`] || '').trim();
            const at = String(meta[`financeEvent${index}At`] || '').trim();
            const amount = String(meta[`financeEvent${index}Amount`] || '0.00').trim();
            if (!message) {
                continue;
            }
            events.push({
                type,
                message,
                at,
                amount,
                date: this.parseIsoTimestamp(at),
            });
        }
        return events;
    }

    resolveNationOperationsFocusEntry(resources) {
        const source = Array.isArray(resources) ? resources : [];
        if (!source.length) {
            return null;
        }
        const selectedResourceId = String(this.selectedResourceId || '');
        const selectedIndex = selectedResourceId
            ? source.findIndex(({ resource }) => String((resource || {}).id || '') === selectedResourceId)
            : -1;
        const index = selectedIndex >= 0 ? selectedIndex : 0;
        return {
            lead: source[index] || null,
            index,
            count: source.length,
            hasPrevious: index > 0,
            hasNext: index < source.length - 1,
        };
    }

    nationOperationsFocus(resources) {
        const focusEntry = this.resolveNationOperationsFocusEntry(resources);
        if (!focusEntry || !focusEntry.lead) {
            return null;
        }
        const lead = focusEntry.lead;
        const resource = lead.resource || {};
        const meta = lead.meta || {};
        const summary = this.resourceOperationSummary(meta);
        const recommendation = this.buildNationOperationRecommendation({
            state: summary.actionState || '',
            label: summary.label,
            count: focusEntry.count,
            lead,
        }, 0);
        const explanation = this.resourceMigrationExplanation(meta, summary);
        const stage = String(meta.migrationStage || this.formatResourceMigrationState(meta) || summary.label || this.t('unknown'));
        const nextStep = String(meta.migrationNextStep || summary.defaultHint || this.t('unknown'));
        const restriction = String(meta.migrationRestrictionDetail || meta.migrationRestriction || this.t('unknown'));
        const shortfall = this.formatMoney(meta.migrationBalanceShortfall || '0.00');
        const cycleYield = `${this.formatWholeNumber(meta.expectedResourceYield, '0')} · XP ${this.formatWholeNumber(meta.expectedExperienceYield, '0')}`;
        const cycleOrTargetLabel = meta.pendingTarget ? this.t('targetLabel') : this.t('nationDetailCycleYield');
        const cycleOrTargetValue = meta.pendingTarget ? String(meta.pendingTarget) : cycleYield;
        const windowCandidates = summary.actionState === 'waiting-depletion'
            ? [['forceMigrationAt', 'forcedAtLabel'], ['nextRefreshAt', 'refreshLabel']]
            : [['nextRefreshAt', 'refreshLabel'], ['forceMigrationAt', 'forcedAtLabel']];
        let windowLabel = this.t('nationDetailFocusWindow');
        let windowValue = this.t('nationDetailForecastNoEvent');
        for (const [field, labelKey] of windowCandidates) {
            if (meta[field]) {
                windowLabel = this.t(labelKey);
                windowValue = this.formatRecommendationWindow(meta[field]);
                break;
            }
        }
        return {
            ...(recommendation || {}),
            resourceId: String(resource.id || ''),
            resourceName: String(resource.label || meta.nation || this.t('unknown')),
            index: focusEntry.index,
            count: focusEntry.count,
            hasPrevious: focusEntry.hasPrevious,
            hasNext: focusEntry.hasNext,
            positionLabel: `${focusEntry.index + 1} / ${focusEntry.count}`,
            filterLabel: this.priorityFilterLabel(this.nationPriorityFilter),
            stage,
            nextStep,
            restriction,
            shortfall,
            windowLabel,
            windowValue,
            cycleOrTargetLabel,
            cycleOrTargetValue,
            explanation,
        };
    }

    applyNationOperationFilterSelection(filterValue, preferredResourceId = '') {
        this.nationPriorityFilter = String(filterValue || 'all');
        const { filteredNationResources } = this.selectedNationOperationResources();
        const filteredResourceIds = new Set(
            filteredNationResources.map(({ resource }) => String((resource || {}).id || '')).filter(Boolean)
        );
        const currentResourceId = String(this.selectedResourceId || '');
        const preferredId = String(preferredResourceId || '');
        let nextResourceId = '';
        if (preferredId && filteredResourceIds.has(preferredId)) {
            nextResourceId = preferredId;
        } else if (currentResourceId && filteredResourceIds.has(currentResourceId)) {
            nextResourceId = currentResourceId;
        } else if (filteredNationResources[0]) {
            nextResourceId = String((((filteredNationResources[0] || {}).resource || {}).id || ''));
        }
        if (nextResourceId) {
            if (nextResourceId === currentResourceId) {
                this.updateNationDetailPanel();
                this.updateResourceList();
                this.bindResourceListEvents();
                this.updateResourceMarkerVisualStates();
            } else {
                this.setSelectedResource(nextResourceId);
            }
            return nextResourceId;
        }
        this.updateNationDetailPanel();
        this.updateResourceList();
        this.bindResourceListEvents();
        this.updateResourceMarkerVisualStates();
        return '';
    }

    navigateNationOperationsFocus(offset) {
        const direction = Number(offset);
        if (!Number.isFinite(direction) || direction === 0) {
            return false;
        }
        const { filteredNationResources } = this.selectedNationOperationResources();
        const focusEntry = this.resolveNationOperationsFocusEntry(filteredNationResources);
        if (!focusEntry) {
            return false;
        }
        const nextIndex = Math.max(0, Math.min(
            focusEntry.count - 1,
            focusEntry.index + (direction < 0 ? -1 : 1)
        ));
        if (nextIndex === focusEntry.index) {
            return false;
        }
        const nextResourceId = String((((filteredNationResources[nextIndex] || {}).resource || {}).id || ''));
        if (!nextResourceId) {
            return false;
        }
        this.setSelectedResource(nextResourceId);
        return true;
    }

    buildNationOperationRecommendation(group, priority = 0) {
        if (!group) {
            return null;
        }
        const lead = group.lead || {};
        const leadResource = lead.resource || {};
        const leadMeta = lead.meta || {};
        const summary = this.resourceOperationSummary(leadMeta);
        const explanation = this.resourceMigrationExplanation(leadMeta, summary);
        const resourceName = String(leadResource.label || leadMeta.nation || this.t('unknown'));
        const nextStep = String(leadMeta.migrationNextStep || summary.defaultHint || this.t('unknown'));
        const restriction = String(leadMeta.migrationRestrictionDetail || leadMeta.migrationRestriction || this.t('unknown'));
        const shortfall = this.formatMoney(leadMeta.migrationBalanceShortfall || '0.00');
        const pendingTarget = String(leadMeta.pendingTarget || this.t('nationDetailRecommendationTargetUnset'));
        const founderName = String(leadMeta.founderName || this.t('unknown'));
        const yieldText = `${this.formatWholeNumber(leadMeta.expectedResourceYield, '0')} · XP ${this.formatWholeNumber(leadMeta.expectedExperienceYield, '0')}`;
        const forceWindow = this.formatRecommendationWindow(leadMeta.forceMigrationAt);
        const refreshWindow = this.formatRecommendationWindow(leadMeta.nextRefreshAt);
        const onlineLabel = this.formatViewerOnlineLabel(leadMeta.viewerOnline);
        const recommendation = {
            priority,
            state: group.state,
            label: group.label,
            count: group.count,
            resourceId: String(leadResource.id || ''),
            resourceName,
            nextStep,
            restriction,
            shortfall,
            pendingTarget,
            founderName,
            primaryLabel: this.t('nationDetailRecommendationPrimaryDefault'),
            hint: this.t('nationDetailRecommendationHintDefault'),
            emphasisLabel: this.t('nationDetailRecommendationLead'),
            emphasisValue: resourceName,
            supportLabel: this.t('nationDetailTimelineNextStep'),
            supportValue: nextStep,
            trailingLabel: this.t('nationDetailTimelineRestriction'),
            trailingValue: restriction,
            explanation,
        };
        switch (group.state) {
            case 'ready':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryReady');
                recommendation.hint = this.t('nationDetailRecommendationHintReady');
                recommendation.emphasisLabel = this.t('nationDetailCycleYield');
                recommendation.emphasisValue = yieldText;
                recommendation.supportLabel = this.t('nationDetailRecommendationLead');
                recommendation.supportValue = resourceName;
                recommendation.trailingLabel = this.t('districtNextStepLabel');
                recommendation.trailingValue = nextStep;
                break;
            case 'awaiting-target':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryAwaitingTarget');
                recommendation.hint = this.t('nationDetailRecommendationHintAwaitingTarget');
                recommendation.emphasisLabel = this.t('nationDetailRecommendationTarget');
                recommendation.emphasisValue = pendingTarget;
                recommendation.supportLabel = this.t('nationDetailRecommendationLead');
                recommendation.supportValue = resourceName;
                recommendation.trailingLabel = this.t('districtNextStepLabel');
                recommendation.trailingValue = nextStep;
                break;
            case 'insufficient-balance':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryInsufficientBalance');
                recommendation.hint = this.t('nationDetailRecommendationHintInsufficientBalance');
                recommendation.emphasisLabel = this.t('nationDetailRecommendationShortfall');
                recommendation.emphasisValue = shortfall;
                recommendation.supportLabel = this.t('nationDetailRecommendationLead');
                recommendation.supportValue = resourceName;
                recommendation.trailingLabel = this.t('nationDetailRecommendationLeader');
                recommendation.trailingValue = founderName;
                break;
            case 'player-offline':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryPlayerOffline');
                recommendation.hint = this.t('nationDetailRecommendationHintPlayerOffline');
                recommendation.emphasisLabel = this.t('viewerOnlineLabel');
                recommendation.emphasisValue = onlineLabel;
                recommendation.supportLabel = this.t('nationDetailRecommendationLeader');
                recommendation.supportValue = founderName;
                recommendation.trailingLabel = this.t('nationDetailTimelineRestriction');
                recommendation.trailingValue = restriction;
                break;
            case 'leader-only':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryLeaderOnly');
                recommendation.hint = this.t('nationDetailRecommendationHintLeaderOnly');
                recommendation.emphasisLabel = this.t('nationDetailRecommendationLeader');
                recommendation.emphasisValue = founderName;
                recommendation.supportLabel = this.t('nationDetailRecommendationLead');
                recommendation.supportValue = resourceName;
                recommendation.trailingLabel = this.t('nationDetailTimelineRestriction');
                recommendation.trailingValue = restriction;
                break;
            case 'waiting-depletion':
                recommendation.primaryLabel = this.t('nationDetailRecommendationPrimaryWaitingDepletion');
                recommendation.hint = this.t('nationDetailRecommendationHintWaitingDepletion');
                recommendation.emphasisLabel = this.t('forcedAtLabel');
                recommendation.emphasisValue = forceWindow;
                recommendation.supportLabel = this.t('resourcesLabel');
                recommendation.supportValue = this.formatWholeNumber(leadMeta.remainingResources, '0');
                recommendation.trailingLabel = this.t('refreshLabel');
                recommendation.trailingValue = refreshWindow;
                break;
            default:
                break;
        }
        return recommendation;
    }

    activateNationOperationGroup(filterValue, resourceId = '') {
        const nextFilter = String(filterValue || 'all');
        const selectedResourceId = this.applyNationOperationFilterSelection(nextFilter, resourceId);
        document.querySelectorAll('.nation-operation-group[data-operation-group]').forEach((node) => {
            node.classList.toggle('is-active', String(node.dataset.operationGroup || '') === nextFilter);
        });
        if (resourceId && selectedResourceId) {
            this.flyToResourceDistrict(selectedResourceId);
        }
    }

    dominantNationName(territories) {
        const counts = new Map();
        for (const territory of territories) {
            const key = territory.ownerName || this.t('unknown');
            counts.set(key, (counts.get(key) || 0) + 1);
        }
        let bestName = this.t('noTerritory');
        let bestCount = -1;
        for (const [name, count] of counts.entries()) {
            if (count > bestCount) {
                bestName = name;
                bestCount = count;
            }
        }
        return bestName;
    }

    renderTerritories(territories) {
        const byOwner = new Map();
        for (const t of territories) {
            if (!byOwner.has(t.ownerId)) {
                byOwner.set(t.ownerId, []);
            }
            byOwner.get(t.ownerId).push(t);
        }

        this.nationData = [];
        this.nationBounds.clear();
        this.nationColors.clear();

        const sortedOwners = [...byOwner.entries()].sort((left, right) => {
            const leftRelation = this.relationForNation(left[0], (left[1][0] || {}).metadata || {});
            const rightRelation = this.relationForNation(right[0], (right[1][0] || {}).metadata || {});
            return this.relationPriorityFor(rightRelation) - this.relationPriorityFor(leftRelation);
        });

        for (const [ownerId, claims] of sortedOwners) {
            const first = claims[0];
            const color = first.fillColor || this.generateColor(ownerId);
            const relation = this.relationForNation(ownerId, first.metadata || {});
            const style = this.relationStyleFor(relation, this.selectedNationId !== null);
            this.nationColors.set(ownerId, color);
            this.nationBounds.set(ownerId, this.boundsForClaims(claims));

            this.nationData.push({
                id: ownerId,
                name: first.ownerName,
                claims: claims.length,
                metadata: first.metadata || {},
                relation,
                world: first.world,
            });

            const polygons = this.buildChunkPolygons(claims, color, style);
            for (const poly of polygons) {
                const popup = L.tooltip({
                    className: 'nation-tooltip',
                    permanent: false,
                    direction: 'top',
                    offset: [0, -8],
                }).setContent(this.buildNationTooltip(first, claims.length));

                poly.bindTooltip(popup);
                poly.on('click', () => {
                    const ownerKey = String(ownerId || '');
                    if (!ownerKey) {
                        return;
                    }
                    if (this.claimMode) {
                        return;
                    }
                    if (String(this.selectedNationId || '') === ownerKey) {
                        this.setSelectedNation(null, { preserveView: true });
                        return;
                    }
                    this.setSelectedNation(ownerKey, { preserveView: true });
                    this.maybeCollapseSidebarAfterMapAction();
                });
                poly.addTo(this.territoryLayer);
                if (style.zIndexOffset > 250) {
                    poly.eachLayer(layer => layer.bringToFront());
                }
            }
        }

        this.updateNationList();
    }

    buildChunkPolygons(claims, color, style) {
        const chunkSet = new Set(claims.map(c => `${c.chunkX}:${c.chunkZ}`));
        const visited = new Set();
        const results = [];

        for (const chunk of claims) {
            const key = `${chunk.chunkX}:${chunk.chunkZ}`;
            if (visited.has(key)) continue;

            const region = this.floodFill(chunk.chunkX, chunk.chunkZ, chunkSet, visited);
            if (region.chunks.length > 0) {
                results.push(this.createPolygon(region.chunks, color, style));
            }
        }

        return results;
    }

    floodFill(startX, startZ, chunkSet, visited) {
        const chunks = [];
        const queue = [[startX, startZ]];

        while (queue.length > 0) {
            const [cx, cz] = queue.shift();
            const key = `${cx}:${cz}`;
            if (visited.has(key) || !chunkSet.has(key)) continue;

            visited.add(key);
            chunks.push([cx, cz]);
            queue.push([cx + 1, cz]);
            queue.push([cx - 1, cz]);
            queue.push([cx, cz + 1]);
            queue.push([cx, cz - 1]);
        }

        return { chunks };
    }

    createPolygon(chunks, color, style = this.relationStyleFor('neutral', false)) {
        const size = MapConfig.chunkSize;
        const group = L.featureGroup();

        for (const [cx, cz] of chunks) {
            const x1 = cx * size;
            const z1 = cz * size;
            const x2 = x1 + size;
            const z2 = z1 + size;

            const rect = L.rectangle(
                [[-z2, x1], [-z1, x2]],
                {
                    color,
                    weight: style.weight,
                    opacity: style.opacity,
                    fillColor: color,
                    fillOpacity: style.fillOpacity,
                    dashArray: style.dashArray,
                    interactive: true,
                }
            );
            group.addLayer(rect);
        }

        return group;
    }

    renderResourceDistricts(resources) {
        this.resourceMarkers = [];
        const sortedResources = [...resources].sort((left, right) => {
            const leftMeta = left.metadata || {};
            const rightMeta = right.metadata || {};
            const relationDelta = this.relationPriorityFor(this.relationForNation(rightMeta.nationId, rightMeta))
                - this.relationPriorityFor(this.relationForNation(leftMeta.nationId, leftMeta));
            if (relationDelta !== 0) {
                return relationDelta;
            }
            return String(left.label || '').localeCompare(String(right.label || ''));
        });

        for (const resource of sortedResources) {
            const meta = resource.metadata || {};
            const visualState = this.resolveResourceMarkerVisualState(resource);
            const el = document.createElement('div');
            el.className = visualState.className;
            el.style.borderColor = visualState.color;
            el.style.opacity = String(Math.max(0.68, visualState.style.opacity));
            el.innerHTML = '<span></span>';

            const marker = L.marker(
                [-resource.z, resource.x],
                {
                    icon: L.divIcon({
                        html: el.outerHTML,
                        className: 'resource-marker-container',
                        iconSize: [28, 28],
                        iconAnchor: [14, 14],
                    }),
                    interactive: true,
                    zIndexOffset: 80 + visualState.style.zIndexOffset,
                }
            );

            marker.bindTooltip(this.buildResourceTooltip(resource), {
                className: 'nation-tooltip',
                direction: 'top',
                offset: [0, -12],
            });
            marker.on('click', () => {
                this.setSelectedResource(resource.id);
                marker.openTooltip();
                this.maybeCollapseSidebarAfterMapAction();
            });
            marker.addTo(this.resourceLayer);
            this.resourceMarkers.push({ id: resource.id, label: resource.label, marker, data: resource });
        }

        this.updateResourceMarkerVisualStates();
        this.updateResourceList();
    }

    buildResourceTooltip(resource) {
        const meta = resource.metadata || {};
        const relation = this.relationForNation(meta.nationId, meta);
        const position = `${Math.round(resource.x)}, ${Math.round(resource.z)}`;
        const forecast = this.resourceForecastMetrics(meta);
        const claimCapacity = this.formatCapacity(meta.claimCount, meta.claimLimit);
        const cityStateCapacity = this.formatCapacity(meta.cityStateCount, meta.cityStateLimit);
        const resourceCapacity = this.formatCapacity(meta.resourceDistrictCount, meta.resourceDistrictLimit);
        const experienceProgress = this.formatExperienceProgress(
            meta.nationExperienceProgress,
            meta.nationNextLevelExperience,
            meta.nationMaxLevelReached
        );
        const detailLines = [
            `<div class="detail">${this.escape(this.t('nationLabel'))}: ${this.escape(meta.nation || this.t('unknown'))}</div>`,
            `<div class="detail">${this.escape(this.t('viewerFounderLabel'))}: ${this.escape(meta.founderName || this.t('unknown'))}</div>`,
            `<div class="detail">${this.escape(this.t('governmentLabel'))}: ${this.escape(this.formatGovernment(meta.government || 'UNKNOWN'))}</div>`,
            `<div class="detail">${this.escape(this.t('nationKindLabel'))}: ${this.escape(this.formatNationKind(meta.nationKind || 'nation'))}</div>`,
            `<div class="detail">${this.escape(this.t('viewerLevelLabel'))}: ${this.escape(meta.nationLevel || '1')}</div>`,
            `<div class="detail">${this.escape(this.t('viewerProgressLabel'))}: ${this.escape(experienceProgress)}</div>`,
            `<div class="detail">${this.escape(this.t('membersLabel'))}: ${this.escape(meta.memberCount || '0')}</div>`,
            `<div class="detail">${this.escape(this.t('claimsLabel'))}: ${this.escape(claimCapacity)}</div>`,
            `<div class="detail">${this.escape(this.t('viewerCityStatesLabel'))}: ${this.escape(cityStateCapacity)}</div>`,
            `<div class="detail">${this.escape(this.t('viewerResourcesLabel'))}: ${this.escape(resourceCapacity)}</div>`,
            `<div class="detail">${this.escape(this.t('positionLabel'))}: ${this.escape(position)}</div>`,
            `<div class="detail">${this.escape(this.t('biomeLabel'))}: ${this.escape(this.formatBiomeName(meta.biome))}</div>`,
            `<div class="detail">${this.escape(this.t('richnessLabel'))}: ${this.escape(meta.richness || '0.00')}</div>`,
            `<div class="detail">${this.escape(this.t('resourcesLabel'))}: ${this.escape(meta.remainingResources || '0')}</div>`,
            `<div class="detail">${this.escape(this.t('experienceLabel'))}: ${this.escape(meta.totalExperience || '0')}</div>`,
            `<div class="detail">${this.escape(this.t('cycleResourcesLabel'))}: ${this.escape(meta.expectedResourceYield || '0')}</div>`,
            `<div class="detail">${this.escape(this.t('cycleExperienceLabel'))}: ${this.escape(meta.expectedExperienceYield || '0')}</div>`,
            `<div class="detail">${this.escape(this.t('migrationLabel'))}: ${this.escape(this.formatResourceMigrationState(meta))}</div>`,
            `<div class="detail">${this.escape(this.t('statusLabel'))}: ${this.escape(this.formatRelation(relation))}</div>`,
        ];
        if (meta.refreshCooldownMinutes) {
            detailLines.push(`<div class="detail">${this.escape(this.t('refreshCycleLabel'))}: ${this.escape(this.formatDurationMinutes(meta.refreshCooldownMinutes))}</div>`);
        }
        if (forecast.hourlyResource > 0 || forecast.hourlyExperience > 0) {
            detailLines.push(
                `<div class="detail">${this.escape(this.t('nationDetailHourlyYield'))}: ${this.escape(this.formatDecimal(forecast.hourlyResource, 1, '0.0'))} · ${this.escape(this.t('nationDetailHourlyExperience'))}: ${this.escape(this.formatDecimal(forecast.hourlyExperience, 1, '0.0'))}</div>`
            );
        }
        if (forecast.next3Resource > 0 || forecast.next3Experience > 0) {
            detailLines.push(
                `<div class="detail">${this.escape(this.t('nextThreeCyclesLabel'))}: ${this.escape(this.formatWholeNumber(forecast.next3Resource, '0'))} · XP ${this.escape(this.formatWholeNumber(forecast.next3Experience, '0'))} · ${this.escape(this.t('nationDetailForecastTreasury'))} ${this.escape(this.formatMoney(forecast.next3Treasury))}${forecast.next3WindowMinutes > 0 ? ` · ${this.escape(this.t('forecastWindowLabel'))}: ${this.escape(this.formatDurationMinutes(forecast.next3WindowMinutes))}` : ''}</div>`
            );
        }
        if (meta.pendingTarget) {
            detailLines.push(`<div class="detail">${this.escape(this.t('targetLabel'))}: ${this.escape(meta.pendingTarget)}</div>`);
        }
        if (meta.nextRefreshAt) {
            detailLines.push(`<div class="detail">${this.escape(this.t('refreshLabel'))}: ${this.escape(this.formatTimestamp(meta.nextRefreshAt))}</div>`);
        }
        if (meta.forceMigrationAt) {
            detailLines.push(`<div class="detail">${this.escape(this.t('forcedAtLabel'))}: ${this.escape(this.formatTimestamp(meta.forceMigrationAt))}</div>`);
        }
        if (meta.beaconPosition) {
            detailLines.push(`<div class="detail">${this.escape(this.t('beaconLabel'))}: ${this.escape(meta.beaconPosition)}</div>`);
        }
        return `<h3>${this.escape(resource.label || this.t('sectionResources'))}</h3>
                ${detailLines.join('')}`;
    }

    renderPlayers(markers) {
        this.playerMarkers = [];

        const sortedMarkers = [...markers].sort((left, right) => {
            const leftMeta = left.metadata || {};
            const rightMeta = right.metadata || {};
            const leftRelation = this.relationForNation(leftMeta.nationId, leftMeta);
            const rightRelation = this.relationForNation(rightMeta.nationId, rightMeta);
            return this.relationPriorityFor(rightRelation) - this.relationPriorityFor(leftRelation);
        });

        for (const m of sortedMarkers) {
            const meta = m.metadata || {};
            const visualState = this.resolvePlayerMarkerVisualState({ id: m.id, data: m });
            const avatar = (meta.avatarHint || m.label || '?').slice(0, 2).toUpperCase();
            const avatarUrl = meta.avatarUrl || this.avatarUrlFromId(meta.playerId);
            const el = document.createElement('div');
            el.className = visualState.className;
            el.style.borderColor = visualState.borderColor;
            el.style.opacity = String(visualState.opacity);
            el.innerHTML = avatarUrl
                ? `<img src="${this.escape(avatarUrl)}" alt="${this.escape(m.label)}" loading="lazy" referrerpolicy="no-referrer" onerror="const holder=this.parentElement; this.remove(); if(holder){holder.classList.add('fallback'); holder.innerHTML='<span>${this.escape(avatar)}</span>'; }">`
                : `<span>${this.escape(avatar)}</span>`;

            const marker = L.marker(
                [-m.z, m.x],
                {
                    icon: L.divIcon({
                        html: el.outerHTML,
                        className: 'player-marker-container',
                        iconSize: [30, 30],
                        iconAnchor: [15, 15],
                    }),
                    interactive: true,
                    zIndexOffset: visualState.zIndexOffset,
                }
            );

            marker.bindTooltip(this.buildPlayerTooltip(m), {
                className: 'nation-tooltip',
                direction: 'top',
                offset: [0, -12],
            });
            marker.on('click', () => {
                this.setSelectedPlayer(m.id);
                marker.openTooltip();
                this.maybeCollapseSidebarAfterMapAction();
            });

            marker.addTo(this.playerLayer);
            this.playerMarkers.push({ id: m.id, label: m.label, marker, data: m });
        }

        this.updatePlayerMarkerVisualStates();
        this.updatePlayerList();
    }

    buildNationTooltip(t, claims) {
        const metadata = t.metadata || {};
        const gov = this.formatGovernment(metadata.government || 'UNKNOWN');
        const memberCount = metadata.memberCount || '0';
        const relation = this.formatRelation(this.relationForNation(t.ownerId, metadata));
        return `<h3>${this.escape(t.ownerName)}</h3>
                <div class="detail">${this.escape(this.t('worldLabel'))}: ${this.escape(t.world)}</div>
                <div class="detail">${this.escape(this.t('governmentLabel'))}: ${this.escape(gov)}</div>
                <div class="detail">${this.escape(this.t('statusLabel'))}: ${this.escape(relation)}</div>
                <div class="detail">${this.escape(this.t('claimsLabel'))}: ${claims}</div>
                <div class="detail">${this.escape(this.t('membersLabel'))}: ${this.escape(memberCount)}</div>`;
    }

    buildPlayerTooltip(m) {
        const meta = m.metadata || {};
        const relation = this.relationForNation(meta.nationId, meta);
        const position = `${Math.round(m.x)}, ${Math.round(m.z)}`;
        const avatar = meta.avatarUrl || this.avatarUrlFromId(meta.playerId);
        const portrait = avatar
            ? `<div class="tooltip-avatar"><img src="${this.escape(avatar)}" alt="${this.escape(m.label)}" loading="lazy" referrerpolicy="no-referrer"></div>`
            : '';
        if (meta.nation) {
            return `${portrait}<h3>${this.escape(m.label)}</h3>
                    <div class="detail">${this.escape(this.t('nationLabel'))}: ${this.escape(meta.nation)}</div>
                    <div class="detail">${this.escape(this.t('governmentLabel'))}: ${this.escape(this.formatGovernment(meta.government || 'UNKNOWN'))}</div>
                    <div class="detail">${this.escape(this.t('statusLabel'))}: ${this.escape(this.formatRelation(relation))}</div>
                    <div class="detail">${this.escape(this.t('positionLabel'))}: ${this.escape(position)}</div>`;
        }
        return `${portrait}<h3>${this.escape(m.label)}</h3>
                <div class="detail">${this.escape(this.t('noNation'))}</div>
                <div class="detail">${this.escape(this.t('positionLabel'))}: ${this.escape(position)}</div>`;
    }

    avatarUrlFromId(playerId) {
        return playerId ? `${MapConfig.avatarUrl}${playerId}` : '';
    }

    escape(s) {
        const d = document.createElement('div');
        d.textContent = s == null ? '' : s;
        return d.innerHTML;
    }

    clearLayers() {
        this.territoryLayer.clearLayers();
        this.resourceLayer.clearLayers();
        this.playerLayer.clearLayers();
    }

    updateNationList() {
        const list = document.getElementById('nation-list');
        if (this.nationData.length === 0) {
            list.innerHTML = `<li class="empty-state">${this.escape(this.t('noNations'))}</li>`;
            return;
        }

        list.innerHTML = this.nationData.map(n => {
            const color = this.nationColors.get(n.id) || '#888';
            const relation = this.formatRelation(n.relation || (n.metadata || {}).relation || 'neutral');
            const selectedClass = n.id === this.selectedNationId ? ' is-selected' : '';
            const searchText = [n.name, n.world, relation, n.claims].filter(Boolean).join(' ');
            return `<li class="${selectedClass.trim()}" data-nation="${this.escape(n.id)}" data-search-text="${this.escape(searchText)}">
                        <span class="nation-color" style="background:${color}"></span>
                        <span class="item-name">${this.escape(n.name)}</span>
                        <span class="item-meta">${this.escape(n.world)} · ${n.claims}${this.escape(this.t('claimUnit'))} · ${this.escape(relation)}</span>
                    </li>`;
        }).join('');
    }

    updateNationDetailPanel() {
        const panel = document.getElementById('nation-detail-panel');
        if (!panel) {
            return;
        }
        const nation = this.nationData.find(entry => entry.id === this.selectedNationId) || null;
        if (!nation) {
            panel.hidden = true;
            panel.innerHTML = '';
            return;
        }
        const meta = nation.metadata || {};
        const viewer = this.viewerInfo || {};
        const selectedResource = this.selectedResource();
        const selectedResourceMeta = (((selectedResource || {}).data || {}).metadata || {});
        const sameNationResource = selectedResourceMeta.nationId && selectedResourceMeta.nationId === nation.id ? selectedResourceMeta : null;
        const {
            nationResources,
            rankedNationResources,
            filteredNationResources,
        } = this.selectedNationOperationResources();
        const nationResourceCount = nationResources.length;
        const pendingMigrations = nationResources.filter(entry => String(entry.meta.migrationState || '').toLowerCase() !== 'none').length;
        const totalResourceYield = nationResources.reduce((sum, entry) => sum + Number(entry.meta.expectedResourceYield || 0), 0);
        const totalExperienceYield = nationResources.reduce((sum, entry) => sum + Number(entry.meta.expectedExperienceYield || 0), 0);
        const filteredResourceYield = filteredNationResources.reduce((sum, entry) => sum + Number(entry.meta.expectedResourceYield || 0), 0);
        const filteredExperienceYield = filteredNationResources.reduce((sum, entry) => sum + Number(entry.meta.expectedExperienceYield || 0), 0);
        const topPrioritySummary = filteredNationResources[0]
            ? this.resourceOperationSummary(filteredNationResources[0].meta)
            : null;
        const operationsOverview = this.nationOperationsOverview(nationResources, filteredNationResources, meta);
        const operationsForecast = this.nationOperationsForecast(nationResources, meta);
        const operationsTimeline = this.nationOperationsTimeline(rankedNationResources);
        const recentEvents = this.nationRecentEvents(meta);
        const filteredRecentEvents = this.filteredNationRecentEvents(recentEvents);
        const recentLogFilters = this.nationRecentLogFilters(recentEvents);
        const eventLedger = this.eventLedgerState || {};
        const eventLedgerData = eventLedger.data || {};
        const eventLedgerEvents = Array.isArray(eventLedgerData.events) ? eventLedgerData.events : [];
        const eventLedgerActiveForNation = String(eventLedger.nationId || '') === String(nation.id || '');
        const financeEvents = this.nationFinanceEvents(meta);
        const operationGroups = this.nationOperationGroups(rankedNationResources);
        const operationRecommendations = this.nationOperationRecommendations(operationGroups);
        const operationsFocus = this.nationOperationsFocus(filteredNationResources);
        const sameNationForecast = sameNationResource ? this.resourceForecastMetrics(sameNationResource) : null;
        const focusResourceCandidate = sameNationResource
            ? selectedResource
            : (filteredNationResources[0] ? filteredNationResources[0].resource : (rankedNationResources[0] ? rankedNationResources[0].resource : null));
        const detailLines = [
            `${this.t('viewerFounderLabel')} ${meta.founderName || this.t('unknown')}`,
            `${this.t('governmentLabel')} ${this.formatGovernment(meta.government || 'UNKNOWN')}`,
            `${this.t('nationKindLabel')} ${this.formatNationKind(meta.nationKind || 'nation')}`,
            `${this.t('statusLabel')} ${this.formatRelation(nation.relation || meta.relation || 'neutral')}`,
            `${this.t('viewerLevelLabel')} ${this.formatWholeNumber(meta.nationLevel, '1')}`,
            `${this.t('viewerExperienceLabel')} ${this.formatWholeNumber(meta.nationExperience, '0')}`,
            `${this.t('viewerProgressLabel')} ${this.formatExperienceProgress(meta.nationExperienceProgress, meta.nationNextLevelExperience, meta.nationMaxLevelReached)}`,
            `${this.t('viewerNextLevelLabel')} ${this.formatExperienceRemaining(meta.nationExperienceRemaining, meta.nationMaxLevelReached)}`,
            `${this.t('membersLabel')} ${this.formatWholeNumber(meta.memberCount, '0')}`,
            `${this.t('nationDetailClaims')} ${this.formatCapacity(meta.claimCount ?? nation.claims, meta.claimLimit)}`,
            `${this.t('viewerCityStatesLabel')} ${this.formatCapacity(meta.cityStateCount, meta.cityStateLimit)}`,
            `${this.t('nationDetailResources')} ${this.formatCapacity(meta.resourceDistrictCount, meta.resourceDistrictLimit)}`,
            `${this.t('nationDetailManagedDistricts')} ${this.formatWholeNumber(nationResourceCount, '0')}`,
            `${this.t('nationDetailPendingMigrations')} ${this.formatWholeNumber(pendingMigrations, '0')}`,
            `${this.t('nationDetailTotalYield')} ${this.formatWholeNumber(totalResourceYield, '0')} · XP ${this.formatWholeNumber(totalExperienceYield, '0')}`,
        ];
        if (viewer.nationId && viewer.nationId === nation.id) {
            detailLines.push(
                `${this.t('viewerBalanceLabel')} ${this.formatMoney(viewer.balance || '0.00')}`,
                `${this.t('viewerRoleLabel')} ${this.formatViewerRole(viewer.role || 'member')}`
            );
        }

        const actionButtons = [
            `<button type="button" class="motion-button nation-detail-action" data-action="focus-nation" data-nation-id="${this.escape(nation.id)}">${this.escape(this.t('nationDetailFocusNation'))}</button>`
        ];
        if (focusResourceCandidate) {
            actionButtons.push(
                `<button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(focusResourceCandidate.id)}">${this.escape(this.t('nationDetailSelectResource'))}</button>`,
                `<button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(focusResourceCandidate.id)}">${this.escape(this.t('nationDetailFocusResource'))}</button>`
            );
        }
        const officerAuthorizationRows = this.nationOfficerAuthorizationRows(meta);
        const officerAuthorizationBlock = officerAuthorizationRows.length > 0
            ? `<div class="nation-detail-block nation-detail-block-wide" data-officer-authorization="true" data-officer-authorization-count="${this.escape(String(officerAuthorizationRows.length))}">
                    <span class="intel-label">${this.escape(this.t('nationDetailOfficerAuthorizations'))}</span>
                    <strong>${this.escape(this.formatWholeNumber(officerAuthorizationRows.length, '0'))}</strong>
                    <div class="nation-ops-grid officer-authorization-grid">
                        ${officerAuthorizationRows.map(row => `
                            <div class="nation-ops-metric officer-authorization-metric${row.canOperate ? ' is-authorized' : ' is-blocked'}" data-officer-authorization-key="${this.escape(row.key)}" data-officer-authorization-roles="${this.escape(row.rawRoles)}" data-officer-authorization-can="${this.escape(String(row.canOperate))}" data-officer-authorization-status="${this.escape(row.status)}"${row.matchedRole ? ` data-officer-authorization-matched-role="${this.escape(row.matchedRole)}"` : ''}>
                                <span class="nation-ops-label">${this.escape(row.label)}</span>
                                <strong>${this.escape(row.roles)}</strong>
                                <span class="officer-authorization-status">${this.escape(row.statusLabel)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>`
            : '';

        const resourceBlock = sameNationResource
            ? `<div class="nation-detail-block">
                    <span class="intel-label">${this.escape(this.t('nationDetailActiveDistrict'))}</span>
                    <strong>${this.escape(selectedResource.label || sameNationResource.nation || this.t('sectionResources'))}</strong>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('biomeLabel'))} ${this.escape(this.formatBiomeName(sameNationResource.biome))}</span>
                        <span>${this.escape(this.t('nationDetailCycleYield'))} ${this.escape(this.formatWholeNumber(sameNationResource.expectedResourceYield, '0'))} · XP ${this.escape(this.formatWholeNumber(sameNationResource.expectedExperienceYield, '0'))}</span>
                        <span>${this.escape(this.t('nationDetailRefresh'))} ${this.escape(this.formatDurationMinutes(sameNationResource.refreshCooldownMinutes))}</span>
                        <span>${this.escape(this.t('nationDetailHourlyYield'))} ${this.escape(this.formatDecimal(sameNationForecast ? sameNationForecast.hourlyResource : 0, 1, '0.0'))} · ${this.escape(this.t('nationDetailHourlyExperience'))} ${this.escape(this.formatDecimal(sameNationForecast ? sameNationForecast.hourlyExperience : 0, 1, '0.0'))}</span>
                        <span>${this.escape(this.t('nextThreeCyclesLabel'))} ${this.escape(this.formatWholeNumber(sameNationForecast ? sameNationForecast.next3Resource : 0, '0'))} · XP ${this.escape(this.formatWholeNumber(sameNationForecast ? sameNationForecast.next3Experience : 0, '0'))}${sameNationForecast && sameNationForecast.next3WindowMinutes > 0 ? ` · ${this.escape(this.t('forecastWindowLabel'))} ${this.escape(this.formatDurationMinutes(sameNationForecast.next3WindowMinutes))}` : ''}</span>
                        <span>${this.escape(this.t('nationDetailMigration'))} ${this.escape(this.formatResourceMigrationState(sameNationResource))}</span>
                        <span>${this.escape(this.t('districtShortfallLabel'))} ${this.escape(this.formatMoney(sameNationResource.migrationBalanceShortfall || '0.00'))}</span>
                        <span>${this.escape(this.t('nationDetailTarget'))} ${this.escape(sameNationResource.pendingTarget || this.t('unknown'))}</span>
                    </div>
                    ${this.renderResourceMigrationExplanation(this.resourceMigrationExplanation(sameNationResource))}
                </div>`
            : `<div class="nation-detail-block">
                    <span class="intel-label">${this.escape(this.t('nationDetailActiveDistrict'))}</span>
                    <strong>${this.escape(this.t('nationDetailNoDistrict'))}</strong>
                    <p>${this.escape(this.t('nationDetailIdle'))}</p>
                </div>`;
        const priorityQueueBlock = `<div class="nation-detail-block">
                <span class="intel-label">${this.escape(this.t('nationDetailPriorityQueue'))}</span>
                <strong>${this.escape(this.formatWholeNumber(nationResourceCount, '0'))}</strong>
                <div id="nation-detail-priority-filters" class="nation-priority-filters">
                    ${this.renderNationPriorityFilters()}
                </div>
                <div id="nation-detail-priority-summary" class="nation-priority-summary">
                    <span>${this.escape(this.t('nationDetailFilteredCount'))} ${this.escape(this.formatWholeNumber(filteredNationResources.length, '0'))}</span>
                    <span>${this.escape(this.t('nationDetailFilteredYield'))} ${this.escape(this.formatWholeNumber(filteredResourceYield, '0'))} · XP ${this.escape(this.formatWholeNumber(filteredExperienceYield, '0'))}</span>
                    <span>${this.escape(this.t('nationDetailTopPriority'))} ${this.escape(topPrioritySummary ? topPrioritySummary.label : this.t('unknown'))}</span>
                </div>
                <div id="nation-detail-priority-list" class="nation-priority-list">
                    ${this.renderNationPriorityQueue(filteredNationResources)}
                </div>
            </div>`;
        const financeSummaryBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailFinanceSummary'))}</span>
                <strong>${this.escape(this.formatMoney(meta.treasuryBalance || '0.00'))}</strong>
                <div class="nation-ops-grid">
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailTreasuryBalance'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.treasuryBalance || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceNet'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeNetTotal || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceIncome'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeIncomeTotal || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceResourceIncome'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeResourceIncomeTotal || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceReward'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeRewardTotal || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceTax'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeTaxTotal || '0.00'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFinanceWithdraw'))}</span>
                        <strong>${this.escape(this.formatMoney(meta.financeWithdrawTotal || '0.00'))}</strong>
                    </div>
                </div>
                <div class="nation-ops-inline">
                    <span>${this.escape(this.t('nationDetailFinanceDeposit'))} ${this.escape(this.formatMoney(meta.financeDepositTotal || '0.00'))}</span>
                    <span>${this.escape(this.t('nationDetailFinanceEvents'))} ${this.escape(this.formatWholeNumber(meta.financeEventCount, '0'))}</span>
                </div>
                <div class="nation-priority-actions">
                    <button type="button" class="motion-button nation-detail-action" data-action="load-finance-ledger" data-nation-id="${this.escape(nation.id)}">${this.escape(this.financeLedgerState.data && this.financeLedgerState.nationId === nation.id ? this.t('nationDetailFinanceLedgerRefresh') : this.t('nationDetailFinanceLedgerOpen'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="finance-ledger-prev" data-nation-id="${this.escape(nation.id)}"${this.financeLedgerCanPage(nation.id, -1) ? '' : ' disabled'}>${this.escape(this.t('nationDetailFinanceLedgerPrevious'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="finance-ledger-next" data-nation-id="${this.escape(nation.id)}"${this.financeLedgerCanPage(nation.id, 1) ? '' : ' disabled'}>${this.escape(this.t('nationDetailFinanceLedgerNext'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="finance-ledger-export" data-format="csv" data-nation-id="${this.escape(nation.id)}">${this.escape(this.t('nationDetailFinanceLedgerExportCsv'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="finance-ledger-export" data-format="json" data-nation-id="${this.escape(nation.id)}">${this.escape(this.t('nationDetailFinanceLedgerExportJson'))}</button>
                </div>
                <div class="nation-finance-ledger-filters">
                    ${this.renderFinanceLedgerFilters(nation.id)}
                </div>
                <div class="nation-finance-ledger-ranges">
                    ${this.renderFinanceLedgerRangeControls(nation.id)}
                </div>
                <div id="nation-finance-ledger" class="nation-finance-ledger">
                    ${this.renderNationFinanceLedger(nation.id)}
                </div>
                <div class="nation-operations-timeline">
                    ${this.renderNationFinanceEvents(financeEvents)}
                </div>
            </div>`;
        const operationsSummaryBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailOperationsSummary'))}</span>
                <strong>${this.escape(operationsOverview.outlookLabel)}</strong>
                <div class="nation-ops-grid">
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailActionable'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsOverview.counts.actionable, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailWaiting'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsOverview.counts.waiting, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailBlocked'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsOverview.counts.blocked, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailFilterCoverage'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsOverview.coverageRatio * 100, '0'))}%</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailHourlyYield'))}</span>
                        <strong>${this.escape(this.formatDecimal(operationsOverview.totalResourceYieldPerHour, 1, '0.0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailHourlyExperience'))}</span>
                        <strong>${this.escape(this.formatDecimal(operationsOverview.totalExperienceYieldPerHour, 1, '0.0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailLevelEta'))}</span>
                        <strong>${this.escape(operationsOverview.levelEta)}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailShortfallTotal'))}</span>
                        <strong>${this.escape(this.formatMoney(operationsOverview.totalShortfall || '0.00'))}</strong>
                    </div>
                </div>
                <div class="nation-ops-inline">
                    <span>${this.escape(this.t('nationDetailOutlook'))} ${this.escape(operationsOverview.outlookLabel)}</span>
                    <span>${this.escape(this.t('nationDetailTopPriority'))} ${this.escape(topPrioritySummary ? topPrioritySummary.label : this.t('unknown'))}</span>
                    <span>${this.escape(this.t('nationDetailShortfallPeak'))} ${this.escape(this.formatMoney(operationsOverview.peakShortfall || '0.00'))}</span>
                </div>
            </div>`;
        const operationsFocusBlock = operationsFocus
            ? `<div id="nation-operations-focus" class="nation-detail-block nation-detail-block-wide" data-focus-index="${this.escape(String(operationsFocus.index))}" data-focus-count="${this.escape(String(operationsFocus.count))}" data-focus-resource-id="${this.escape(operationsFocus.resourceId)}">
                    <span class="intel-label">${this.escape(this.t('nationDetailOperationsFocus'))}</span>
                    <strong>${this.escape(operationsFocus.resourceName)}</strong>
                    <p class="nation-operation-note">${this.escape(this.t('nationDetailFocusHint'))}</p>
                    <div class="nation-ops-grid">
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('nationDetailFocusPosition'))}</span>
                            <strong>${this.escape(operationsFocus.positionLabel)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('nationDetailCurrentFilter'))}</span>
                            <strong>${this.escape(operationsFocus.filterLabel)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('districtStageLabel'))}</span>
                            <strong>${this.escape(operationsFocus.stage)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('districtNextStepLabel'))}</span>
                            <strong>${this.escape(operationsFocus.nextStep)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('districtRestrictionLabel'))}</span>
                            <strong>${this.escape(operationsFocus.restriction)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(this.t('districtShortfallLabel'))}</span>
                            <strong>${this.escape(operationsFocus.shortfall)}</strong>
                        </div>
                        <div class="nation-ops-metric">
                            <span class="nation-ops-label">${this.escape(operationsFocus.cycleOrTargetLabel)}</span>
                            <strong>${this.escape(operationsFocus.cycleOrTargetValue)}</strong>
                        </div>
                    </div>
                    <div class="nation-ops-inline">
                        <span>${this.escape(operationsFocus.windowLabel)} ${this.escape(operationsFocus.windowValue)}</span>
                        <span>${this.escape(this.t('nationDetailRecommendationAction'))} ${this.escape(operationsFocus.primaryLabel || this.t('unknown'))}</span>
                        <span>${this.escape(this.t('nationDetailRecommendationLeader'))} ${this.escape(operationsFocus.founderName || this.t('unknown'))}</span>
                    </div>
                    ${this.renderResourceMigrationExplanation(operationsFocus.explanation)}
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-prev-resource"${operationsFocus.hasPrevious ? '' : ' disabled'}>${this.escape(this.t('nationDetailFocusPrevious'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-next-resource"${operationsFocus.hasNext ? '' : ' disabled'}>${this.escape(this.t('nationDetailFocusNext'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(operationsFocus.resourceId)}">${this.escape(this.t('nationDetailSelectResource'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(operationsFocus.resourceId)}">${this.escape(this.t('nationDetailFocusResource'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="open-operation-group" data-priority-filter="${this.escape(operationsFocus.state || 'all')}" data-resource-id="${this.escape(operationsFocus.resourceId)}">${this.escape(operationsFocus.primaryLabel || this.t('nationDetailOpenGroup'))}</button>
                    </div>
                </div>`
            : `<div id="nation-operations-focus" class="nation-detail-block nation-detail-block-wide" data-focus-index="-1" data-focus-count="0" data-focus-resource-id="">
                    <span class="intel-label">${this.escape(this.t('nationDetailOperationsFocus'))}</span>
                    <strong>${this.escape(this.t('nationDetailFocusEmpty'))}</strong>
                    <p>${this.escape(this.t('nationDetailFocusEmpty'))}</p>
                </div>`;
        const operationsActionsBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailOperationsActions'))}</span>
                <strong>${this.escape(this.formatWholeNumber(operationGroups.length, '0'))}</strong>
                <div class="nation-operation-groups">
                    ${this.renderNationOperationGroups(operationGroups)}
                </div>
            </div>`;
        const operationsForecastBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailOperationsForecast'))}</span>
                <strong>${this.escape(operationsForecast.forcedMigrationEvent.withinHorizonCount > 0 ? this.t('nationDetailForecastUpcoming') : this.t('nationDetailForecastStable'))}</strong>
                <div class="nation-ops-grid">
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailNextRefreshWindow'))}</span>
                        <strong>${this.escape(operationsForecast.refreshEvent.lead ? this.formatRelativeTimeFromNow(operationsForecast.refreshEvent.lead.date) : this.t('nationDetailForecastNoEvent'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailNextForcedMigration'))}</span>
                        <strong>${this.escape(operationsForecast.forcedMigrationEvent.lead ? this.formatRelativeTimeFromNow(operationsForecast.forcedMigrationEvent.lead.date) : this.t('nationDetailForecastNoEvent'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecastRefreshWave'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsForecast.refreshEvent.withinHorizonCount, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecastForceWave'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsForecast.forcedMigrationEvent.withinHorizonCount, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecast6h'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsForecast.resourceYield6h, '0'))} · XP ${this.escape(this.formatWholeNumber(operationsForecast.experienceYield6h, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecast12h'))}</span>
                        <strong>${this.escape(this.formatWholeNumber(operationsForecast.resourceYield12h, '0'))} · XP ${this.escape(this.formatWholeNumber(operationsForecast.experienceYield12h, '0'))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecastTreasury'))}</span>
                        <strong>${this.escape(this.formatMoney(operationsForecast.treasuryYield6h))} / ${this.escape(this.formatMoney(operationsForecast.treasuryYield12h))}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailLevelCycles'))}</span>
                        <strong>${this.escape(operationsForecast.levelCycles)}</strong>
                    </div>
                    <div class="nation-ops-metric">
                        <span class="nation-ops-label">${this.escape(this.t('nationDetailForecastAbsolute'))}</span>
                        <strong>${this.escape(operationsForecast.refreshEvent.lead ? this.formatTimestamp(operationsForecast.refreshEvent.lead.date) : this.t('nationDetailForecastNoEvent'))}</strong>
                    </div>
                </div>
                <div class="nation-ops-inline">
                    <span>${this.escape(this.t('nationDetailForecastRefreshLine', { count: this.formatWholeNumber(operationsForecast.refreshEvent.withinHorizonCount, '0') }))}</span>
                    <span>${this.escape(this.t('nationDetailForecastForceLine', { count: this.formatWholeNumber(operationsForecast.forcedMigrationEvent.withinHorizonCount, '0') }))}</span>
                    <span>${this.escape(this.t('nationDetailForecastLead'))} ${this.escape(operationsForecast.forcedMigrationEvent.lead ? String((operationsForecast.forcedMigrationEvent.lead.resource || {}).label || ((operationsForecast.forcedMigrationEvent.lead.meta || {}).nation || this.t('unknown'))) : this.t('nationDetailForecastNoEvent'))}</span>
                </div>
            </div>`;
        const operationsTimelineBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailOperationsTimeline'))}</span>
                <strong>${this.escape(this.formatWholeNumber(operationsTimeline.length, '0'))}</strong>
                <div id="nation-operations-timeline" class="nation-operations-timeline">
                    ${this.renderNationOperationsTimeline(operationsTimeline)}
                </div>
            </div>`;
        const recentOperationsBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailRecentLog'))}</span>
                <strong>${this.escape(this.formatWholeNumber(filteredRecentEvents.length, '0'))} / ${this.escape(this.formatWholeNumber(recentEvents.length, '0'))}</strong>
                ${this.renderNationRecentLogFilters(recentLogFilters)}
                <div id="nation-recent-log" class="nation-operations-timeline" data-recent-log-filter="${this.escape(this.normalizeNationRecentLogFilter(this.nationRecentLogFilter))}" data-recent-log-count="${this.escape(String(filteredRecentEvents.length))}" data-recent-log-total="${this.escape(String(recentEvents.length))}">
                    ${this.renderNationRecentEvents(filteredRecentEvents)}
                </div>
                <div class="nation-priority-actions">
                    <button type="button" class="motion-button nation-detail-action" data-action="load-event-ledger" data-nation-id="${this.escape(nation.id)}">${this.escape(this.eventLedgerState.data && this.eventLedgerState.nationId === nation.id ? this.t('nationDetailEventLedgerRefresh') : this.t('nationDetailEventLedgerOpen'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-prev" data-nation-id="${this.escape(nation.id)}"${this.eventLedgerCanPage(nation.id, -1) ? '' : ' disabled'}>${this.escape(this.t('nationDetailEventLedgerPrevious'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-next" data-nation-id="${this.escape(nation.id)}"${this.eventLedgerCanPage(nation.id, 1) ? '' : ' disabled'}>${this.escape(this.t('nationDetailEventLedgerNext'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-export" data-format="csv" data-nation-id="${this.escape(nation.id)}">${this.escape(this.t('nationDetailEventLedgerExportCsv'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-export" data-format="json" data-nation-id="${this.escape(nation.id)}">${this.escape(this.t('nationDetailEventLedgerExportJson'))}</button>
                </div>
                <div class="nation-event-ledger-filters">
                    ${this.renderEventLedgerFilters(nation.id)}
                </div>
                <div class="nation-event-ledger-ranges">
                    ${this.renderEventLedgerRangeControls(nation.id)}
                </div>
                ${this.renderEventLedgerSearchControls(nation.id)}
                ${this.renderEventLedgerResourceControls(nation.id, this.selectedResourceId)}
                <div id="nation-event-ledger" class="nation-event-ledger" data-event-ledger-filter="${this.escape(this.normalizeNationRecentLogFilter(eventLedger.filter || 'all'))}" data-event-ledger-range="${this.escape(this.normalizeFinanceLedgerRange(eventLedger.range || 'all'))}" data-event-ledger-resource-id="${this.escape(this.normalizeEventLedgerResourceId(eventLedger.resourceId || ''))}" data-event-ledger-query="${this.escape(this.normalizeEventLedgerQuery(eventLedger.query || ''))}" data-event-ledger-actor="${this.escape(this.normalizeEventLedgerQuery(eventLedger.actor || ''))}" data-event-ledger-reason="${this.escape(this.normalizeEventLedgerQuery(eventLedger.reason || ''))}" data-event-ledger-page="${this.escape(String(eventLedgerActiveForNation ? (eventLedgerData.page || eventLedger.page || 1) : 1))}" data-event-ledger-pages="${this.escape(String(eventLedgerActiveForNation ? (eventLedgerData.totalPages || 0) : 0))}" data-event-ledger-count="${this.escape(String(eventLedgerActiveForNation ? eventLedgerEvents.length : 0))}" data-event-ledger-total="${this.escape(String(eventLedgerActiveForNation ? (eventLedgerData.total || 0) : 0))}">
                    ${this.renderNationEventLedger(nation.id)}
                </div>
            </div>`;
        const operationsRecommendationBlock = `<div class="nation-detail-block nation-detail-block-wide">
                <span class="intel-label">${this.escape(this.t('nationDetailOperationsRecommendations'))}</span>
                <strong>${this.escape(this.formatWholeNumber(operationRecommendations.length, '0'))}</strong>
                <div class="nation-operation-groups">
                    ${this.renderNationOperationRecommendations(operationRecommendations)}
                </div>
            </div>`;

        panel.hidden = false;
        panel.innerHTML = `
            <div class="nation-detail-block">
                <span class="intel-label">${this.escape(this.t('nationDetailTitle'))}</span>
                <strong>${this.escape(nation.name || this.t('unknown'))}</strong>
                <div class="intel-detail-list">${detailLines.map(detail => `<span>${this.escape(detail)}</span>`).join('')}</div>
                <div class="nation-detail-actions">${actionButtons.join('')}</div>
            </div>
            ${officerAuthorizationBlock}
            ${financeSummaryBlock}
            ${operationsSummaryBlock}
            ${operationsFocusBlock}
            ${operationsRecommendationBlock}
            ${operationsForecastBlock}
            ${operationsTimelineBlock}
            ${recentOperationsBlock}
            ${operationsActionsBlock}
            ${resourceBlock}
            ${priorityQueueBlock}
        `;
        this.bindNationDetailPanelEvents();
        this.bindMotionButtons();
    }

    nationOfficerAuthorizationRows(meta = {}) {
        const definitions = [
            ['resource-migration', 'ResourceMigration', 'nationDetailOfficerResourceMigration', 'officerRoleResourceMigration'],
            ['treasury-withdraw', 'TreasuryWithdraw', 'nationDetailOfficerTreasuryWithdraw', 'officerRoleTreasuryWithdraw'],
            ['diplomacy-set', 'DiplomacySet', 'nationDetailOfficerDiplomacySet', 'officerRoleDiplomacySet'],
            ['war-declare', 'WarDeclare', 'nationDetailOfficerWarDeclare', 'officerRoleWarDeclare'],
            ['war-end', 'WarEnd', 'nationDetailOfficerWarEnd', 'officerRoleWarEnd'],
            ['policy-set', 'PolicySet', 'nationDetailOfficerPolicySet', 'officerRolePolicySet'],
            ['policy-clear', 'PolicyClear', 'nationDetailOfficerPolicyClear', 'officerRolePolicyClear'],
            ['technology-unlock', 'TechnologyUnlock', 'nationDetailOfficerTechnologyUnlock', 'officerRoleTechnologyUnlock'],
            ['technology-revoke', 'TechnologyRevoke', 'nationDetailOfficerTechnologyRevoke', 'officerRoleTechnologyRevoke'],
        ];
        return definitions
            .map(([key, suffix, labelKey, metadataKey]) => {
                const rawRoles = String(meta[metadataKey] || '').trim();
                const canOperate = this.parseBooleanFlag(meta[`viewerCanOfficer${suffix}`]);
                const status = String(meta[`viewerOfficerStatus${suffix}`] || '').trim();
                const matchedRole = String(meta[`viewerOfficerMatchedRole${suffix}`] || '').trim();
                const row = {
                    key,
                    suffix,
                    rawRoles,
                    canOperate,
                    status,
                    matchedRole,
                    label: this.t(labelKey),
                    roles: this.formatOfficerRoles(rawRoles),
                };
                row.statusLabel = this.formatOfficerAuthorizationStatus(row);
                return {
                    ...row,
                };
            })
            .filter(row => row.rawRoles.length > 0);
    }

    formatOfficerAuthorizationStatus(row = {}) {
        switch (String(row.status || '').toLowerCase()) {
            case 'founder':
                return this.t('nationDetailOfficerStatusFounder');
            case 'officer':
                return this.t('nationDetailOfficerStatusOfficer', { role: row.matchedRole || this.t('unknown') });
            case 'needs-appointment':
                return this.t('nationDetailOfficerStatusNeedAppointment');
            case 'external-nation':
                return this.t('nationDetailOfficerStatusExternal');
            case 'anonymous':
                return this.t('nationDetailOfficerStatusAnonymous');
            case 'no-role-config':
                return this.t('nationDetailOfficerStatusNoRole');
            default:
                return row.canOperate ? this.t('nationDetailOfficerStatusOfficer', { role: row.matchedRole || this.t('unknown') }) : this.t('nationDetailOfficerStatusUnknown');
        }
    }

    formatOfficerRoles(value) {
        const roles = String(value || '')
            .split(',')
            .map(role => role.trim())
            .filter(Boolean);
        return roles.length > 0 ? roles.join(', ') : this.t('unknown');
    }

    resourceIdLookupKeys(resourceId) {
        const raw = String(resourceId || '').trim();
        if (!raw) {
            return new Set();
        }
        const normalized = this.normalizeEventLedgerResourceId(raw);
        const unprefixed = normalized.startsWith('resource:') ? normalized.slice('resource:'.length) : normalized;
        return new Set([raw, normalized, unprefixed].filter(Boolean));
    }

    findResourceDistrictEntry(resourceId) {
        const lookup = this.resourceIdLookupKeys(resourceId);
        if (lookup.size === 0) {
            return null;
        }
        const resource = this.resourceMarkers.find(candidate => {
            const candidateId = String((candidate || {}).id || '');
            const candidateLookup = this.resourceIdLookupKeys(candidateId);
            return Array.from(candidateLookup).some(key => lookup.has(key));
        }) || null;
        if (!resource) {
            return null;
        }
        return {
            resource,
            meta: (((resource || {}).data || {}).metadata || {}),
        };
    }

    fallbackResourceDistrictEntryForEvent() {
        const selected = this.selectedResource();
        const selectedMeta = (((selected || {}).data || {}).metadata || {});
        if (selected && String(selectedMeta.nationId || '') === String(this.selectedNationId || '')) {
            return {
                resource: selected,
                meta: selectedMeta,
            };
        }
        const {
            rankedNationResources,
            filteredNationResources,
        } = this.selectedNationOperationResources();
        return filteredNationResources[0] || rankedNationResources[0] || null;
    }

    officerAuthorizationRowForKey(key) {
        const nation = this.nationData.find(entry => String(entry.id || '') === String(this.selectedNationId || '')) || null;
        const rows = this.nationOfficerAuthorizationRows((nation || {}).metadata || {});
        return rows.find(row => String(row.key || '') === String(key || '')) || null;
    }

    eventLedgerOperationDefinitions() {
        return [
            {
                category: 'finance',
                action: 'finance-review',
                labelKey: 'nationDetailEventLedgerOperationFinance',
                prefixes: ['treasury.'],
                authorizationByPrefix: [
                    ['treasury.withdraw', 'treasury-withdraw'],
                ],
                contextKeys: ['amount', 'balance', 'target', 'reason', 'actor'],
                fallbackAuthLabelKey: 'nationDetailEventLedgerOperationCommandGuard',
            },
            {
                category: 'war',
                action: 'war-review',
                labelKey: 'nationDetailEventLedgerOperationWar',
                prefixes: ['war.'],
                authorizationByPrefix: [
                    ['war.ended', 'war-end'],
                    ['war.declared', 'war-declare'],
                ],
                contextKeys: ['warId', 'target', 'reason', 'actor', 'amount', 'balance'],
                fallbackAuthLabelKey: 'nationDetailEventLedgerOperationFounder',
            },
            {
                category: 'officer',
                action: 'officer-management',
                labelKey: 'nationDetailEventLedgerOperationOfficer',
                prefixes: ['officer.'],
                contextKeys: ['target', 'member', 'role', 'reason', 'actor'],
                fallbackAuthLabelKey: 'nationDetailEventLedgerOperationFounder',
            },
            {
                category: 'diplomacy',
                action: 'diplomacy-set',
                labelKey: 'nationDetailEventLedgerOperationDiplomacy',
                prefixes: ['diplomacy.'],
                authorizationByPrefix: [['diplomacy.', 'diplomacy-set']],
                contextKeys: ['relation', 'target', 'reason', 'actor'],
            },
            {
                category: 'strategy',
                action: 'strategy-review',
                labelKey: 'nationDetailEventLedgerOperationStrategy',
                prefixes: ['policy.', 'technology.', 'government.'],
                authorizationByPrefix: [
                    ['policy.clear', 'policy-clear'],
                    ['policy.', 'policy-set'],
                    ['technology.revoke', 'technology-revoke'],
                    ['technology.unlock', 'technology-unlock'],
                    ['technology.', 'technology-unlock'],
                ],
                contextKeys: ['policy', 'technology', 'target', 'reason', 'actor'],
            },
            {
                category: 'territory',
                action: 'territory-review',
                labelKey: 'nationDetailEventLedgerOperationTerritory',
                prefixes: ['territory.', 'claim.'],
                contextKeys: ['target', 'claims', 'reason', 'actor'],
                fallbackAuthLabelKey: 'nationDetailEventLedgerOperationFounder',
            },
            {
                category: 'nation',
                action: 'nation-governance',
                labelKey: 'nationDetailEventLedgerOperationNation',
                prefixes: ['nation.', 'city.', 'resolution.'],
                contextKeys: ['target', 'members', 'claims', 'reason', 'actor'],
                fallbackAuthLabelKey: 'nationDetailEventLedgerOperationFounder',
            },
        ];
    }

    eventLedgerOperationAuthorizationKey(definition = {}, type = '') {
        const normalizedType = String(type || '').trim().toLowerCase();
        const candidates = Array.isArray(definition.authorizationByPrefix)
            ? definition.authorizationByPrefix
            : [];
        const match = candidates.find(([prefix]) => normalizedType.startsWith(String(prefix || '').toLowerCase()));
        return match ? String(match[1] || '').trim() : '';
    }

    eventLedgerOperationDetailValue(details = {}, key = '') {
        const normalizedKey = String(key || '').trim().toLowerCase();
        if (!normalizedKey || !details || typeof details !== 'object') {
            return null;
        }
        const entry = Object.entries(details).find(([candidateKey]) =>
            String(candidateKey || '').trim().toLowerCase() === normalizedKey
        );
        if (!entry) {
            return null;
        }
        const value = this.normalizeEventLedgerQuery(entry[1]);
        return value ? { key: entry[0], value } : null;
    }

    eventLedgerOperationContextRows(definition = {}, details = {}) {
        const rows = [];
        const seen = new Set();
        (definition.contextKeys || []).forEach(key => {
            const row = this.eventLedgerOperationDetailValue(details, key);
            if (!row) {
                return;
            }
            const normalizedKey = String(row.key || '').trim().toLowerCase();
            if (seen.has(normalizedKey)) {
                return;
            }
            seen.add(normalizedKey);
            rows.push(row);
        });
        return rows.slice(0, 4);
    }

    eventLedgerOperationScope(definition = {}, details = {}) {
        const reason = this.eventLedgerOperationDetailValue(details, 'reason')
            || this.eventLedgerOperationDetailValue(details, 'cause');
        if (reason) {
            return { field: 'reason', value: reason.value };
        }
        const contextRows = this.eventLedgerOperationContextRows(definition, details);
        const searchRow = contextRows.find(row => !['actor', 'operator', 'player', 'viewer'].includes(String(row.key || '').toLowerCase()))
            || contextRows[0]
            || null;
        return searchRow ? { field: 'query', value: searchRow.value } : { field: 'query', value: '' };
    }

    eventLedgerOperationForEvent(event = {}, category = '', details = {}) {
        const type = String((event || {}).type || '').trim();
        const normalizedType = type.toLowerCase();
        const normalizedCategory = this.normalizeNationRecentEventCategory(category || (event || {}).category || this.nationRecentEventCategory(type));
        const definition = this.eventLedgerOperationDefinitions().find(candidate =>
            candidate.category === normalizedCategory
                && (!Array.isArray(candidate.prefixes)
                    || candidate.prefixes.some(prefix => normalizedType.startsWith(String(prefix || '').toLowerCase())))
        );
        if (!definition) {
            return null;
        }
        const authorizationKey = this.eventLedgerOperationAuthorizationKey(definition, type);
        const authorization = authorizationKey ? this.officerAuthorizationRowForKey(authorizationKey) : null;
        const action = authorizationKey || definition.action || normalizedCategory;
        const scope = this.eventLedgerOperationScope(definition, details);
        const contextRows = this.eventLedgerOperationContextRows(definition, details);
        const fallbackAuthLabel = definition.fallbackAuthLabelKey
            ? this.t(definition.fallbackAuthLabelKey)
            : this.t('nationDetailEventLedgerOperationCommandGuard');
        return {
            ...definition,
            action,
            authorizationKey,
            authorization,
            category: normalizedCategory,
            type,
            label: authorization ? authorization.label : this.t(definition.labelKey),
            contextRows,
            scope,
            fallbackAuthLabel,
        };
    }

    renderEventLedgerGenericOperationLink(operation = {}, event = {}) {
        const authorization = operation.authorization || null;
        const authRoles = authorization ? authorization.roles : operation.fallbackAuthLabel || this.t('unknown');
        const authRawRoles = authorization ? authorization.rawRoles : '';
        const authStatus = authorization ? authorization.status : 'command-gate';
        const authStatusLabel = authorization ? authorization.statusLabel : this.t('nationDetailEventLedgerOperationCommandGuard');
        const authCanOperate = authorization ? authorization.canOperate : false;
        const scope = operation.scope || {};
        const scopeField = ['actor', 'reason', 'query'].includes(scope.field || '') ? scope.field : 'query';
        const scopeValue = this.normalizeEventLedgerQuery(scope.value || '');
        const scopeQuery = scopeField === 'query' ? scopeValue : '';
        const scopeActor = scopeField === 'actor' ? scopeValue : '';
        const scopeReason = scopeField === 'reason' ? scopeValue : '';
        const contextRows = Array.isArray(operation.contextRows) ? operation.contextRows : [];
        const contextLines = contextRows.map(row => `
            <span>${this.escape(this.t('nationDetailEventLedgerOperationContext'))} ${this.escape(this.formatEventLedgerContextKey(row.key))} ${this.escape(row.value)}</span>
        `).join('');
        return `
            <div class="event-ledger-operation-link" data-event-operation-link="true" data-event-operation-family="${this.escape(operation.category || '')}" data-event-operation-action="${this.escape(operation.action || '')}" data-event-operation-auth-key="${this.escape(operation.authorizationKey || '')}" data-event-operation-filter="${this.escape(operation.category || '')}" data-event-operation-reason="${this.escape(scopeReason)}" data-event-operation-query="${this.escape(scopeQuery)}" data-event-operation-actor="${this.escape(scopeActor)}" data-event-operation-roles="${this.escape(authRawRoles)}" data-event-operation-can="${this.escape(String(authCanOperate))}" data-event-operation-status="${this.escape(authStatus)}">
                <div class="nation-priority-head">
                    <strong>${this.escape(this.t('nationDetailEventLedgerOperationLink'))}</strong>
                    <span class="nation-priority-badge">${this.escape(operation.label || this.formatNationRecentEventCategory(operation.category))}</span>
                </div>
                <div class="intel-detail-list">
                    <span>${this.escape(this.t('nationDetailEventLedgerOperationState'))} ${this.escape(this.formatNationRecentEventType(operation.type || event.type || ''))}</span>
                    <span>${this.escape(this.t('nationDetailEventLedgerOperationAuth'))} ${this.escape(authRoles)} · ${this.escape(authStatusLabel)}</span>
                    ${contextLines}
                </div>
                <div class="nation-priority-actions">
                    <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-operation-scope" data-nation-id="${this.escape(this.selectedNationId || '')}" data-event-operation-action="${this.escape(operation.action || '')}" data-operation-filter="${this.escape(operation.category || 'all')}" data-operation-query="${this.escape(scopeQuery)}" data-operation-actor="${this.escape(scopeActor)}" data-operation-reason="${this.escape(scopeReason)}">${this.escape(this.t('nationDetailEventLedgerOperationOpen'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="focus-nation" data-nation-id="${this.escape(this.selectedNationId || '')}">${this.escape(this.t('nationDetailFocusNation'))}</button>
                </div>
            </div>
        `;
    }

    renderEventLedgerOperationLink(eventOrResourceId, category = '', details = {}) {
        if (eventOrResourceId && typeof eventOrResourceId === 'object') {
            const event = eventOrResourceId;
            const eventCategory = this.normalizeNationRecentEventCategory(category || event.category || this.nationRecentEventCategory(event.type));
            const resourceId = String(event.resourceId || '').trim();
            if (resourceId) {
                return this.renderResourceEventLedgerOperationLink(resourceId);
            }
            const operation = this.eventLedgerOperationForEvent(event, eventCategory, details);
            return operation ? this.renderEventLedgerGenericOperationLink(operation, event) : '';
        }
        return this.renderResourceEventLedgerOperationLink(eventOrResourceId);
    }

    renderResourceEventLedgerOperationLink(resourceId) {
        let entry = this.findResourceDistrictEntry(resourceId);
        let matchMode = 'exact';
        if (!entry) {
            entry = this.fallbackResourceDistrictEntryForEvent();
            matchMode = entry ? 'fallback' : 'missing';
        }
        if (!entry) {
            return '';
        }
        const resource = entry.resource || {};
        const meta = entry.meta || {};
        const summary = this.resourceOperationSummary(meta);
        const explanation = this.resourceMigrationExplanation(meta, summary);
        const authorization = this.officerAuthorizationRowForKey('resource-migration');
        const actionState = summary.actionState || 'unknown';
        const operationFilter = actionState && actionState !== 'unknown' ? actionState : 'all';
        const resourceKey = String(resource.id || resourceId || '');
        const authRoles = authorization ? authorization.roles : this.t('unknown');
        const authRawRoles = authorization ? authorization.rawRoles : '';
        const authStatus = authorization ? authorization.status : '';
        const authStatusLabel = authorization ? authorization.statusLabel : this.t('nationDetailOfficerStatusUnknown');
        const authCanOperate = authorization ? authorization.canOperate : false;
        return `
            <div class="event-ledger-operation-link" data-event-operation-link="true" data-event-operation-family="resource" data-event-operation-action="resource-migration" data-event-operation-auth-key="resource-migration" data-event-operation-event-resource-id="${this.escape(resourceId)}" data-event-operation-resource-id="${this.escape(resourceKey)}" data-event-operation-match="${this.escape(matchMode)}" data-event-operation-state="${this.escape(actionState)}" data-event-operation-roles="${this.escape(authRawRoles)}" data-event-operation-can="${this.escape(String(authCanOperate))}" data-event-operation-status="${this.escape(authStatus)}">
                <div class="nation-priority-head">
                    <strong>${this.escape(this.t('nationDetailEventLedgerOperationLink'))}</strong>
                    <span class="nation-priority-badge">${this.escape(summary.label)}</span>
                </div>
                <div class="intel-detail-list">
                    <span>${this.escape(this.t('nationDetailEventLedgerOperationState'))} ${this.escape(summary.label)}</span>
                    <span>${this.escape(this.t('nationDetailEventLedgerOperationAuth'))} ${this.escape(authRoles)} · ${this.escape(authStatusLabel)}</span>
                </div>
                ${this.renderResourceMigrationExplanation(explanation, true)}
                <div class="nation-priority-actions">
                    <button type="button" class="motion-button nation-detail-action" data-action="open-operation-group" data-priority-filter="${this.escape(operationFilter)}" data-resource-id="${this.escape(resourceKey)}" data-event-operation-action="resource-migration">${this.escape(this.t('nationDetailEventLedgerOperationOpen'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(resourceKey)}">${this.escape(this.t('nationDetailTimelineInspect'))}</button>
                    <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(resourceKey)}">${this.escape(this.t('nationDetailTimelineFocus'))}</button>
                </div>
            </div>
        `;
    }

    renderNationOperationRecommendations(recommendations) {
        if (!Array.isArray(recommendations) || recommendations.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailOutlookNoData'))}</p>`;
        }
        return recommendations.map((recommendation) => {
            const activeClass = this.nationPriorityFilter === recommendation.state ? ' is-active' : '';
            return `
            <article class="nation-operation-group${activeClass}" data-operation-group="${this.escape(recommendation.state)}" data-resource-id="${this.escape(recommendation.resourceId)}">
                <div class="nation-priority-head">
                    <strong>${this.escape(recommendation.label)}</strong>
                    <span class="nation-priority-badge">${this.escape(this.t('nationDetailRecommendationCount'))} ${this.escape(this.formatWholeNumber(recommendation.count, '0'))}</span>
                </div>
                <p class="nation-operation-note">${this.escape(recommendation.hint)}</p>
                <div class="intel-detail-list">
                    <span>${this.escape(this.t('nationDetailRecommendationAction'))} ${this.escape(recommendation.primaryLabel)}</span>
                    <span>${this.escape(this.t('nationDetailRecommendationLead'))} ${this.escape(recommendation.resourceName)}</span>
                    <span>${this.escape(recommendation.emphasisLabel)} ${this.escape(recommendation.emphasisValue)}</span>
                    <span>${this.escape(recommendation.supportLabel)} ${this.escape(recommendation.supportValue)}</span>
                    <span>${this.escape(recommendation.trailingLabel)} ${this.escape(recommendation.trailingValue)}</span>
                </div>
                ${this.renderResourceMigrationExplanation(recommendation.explanation, true)}
                <div class="nation-priority-actions">
                    <button type="button" class="motion-button nation-detail-action" data-action="open-operation-group" data-priority-filter="${this.escape(recommendation.state)}" data-resource-id="${this.escape(recommendation.resourceId)}">${this.escape(recommendation.primaryLabel)}</button>
                </div>
            </article>
        `;
        }).join('');
    }

    renderNationOperationsTimeline(events) {
        if (!Array.isArray(events) || events.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailTimelineEmpty'))}</p>`;
        }
        return events.map((event) => {
            const resource = event.resource || {};
            const meta = event.meta || {};
            const resourceId = String(resource.id || '');
            const operationFilter = String(event.actionState || 'all');
            const whenText = event.date
                ? `${this.formatRelativeTimeFromNow(event.date)} · ${this.formatTimestamp(event.date)}`
                : this.t('nationDetailForecastReadyNow');
            return `
                <article class="nation-timeline-item" data-resource-id="${this.escape(resourceId)}" data-timeline-type="${this.escape(event.type)}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(event.label)}</strong>
                        <span class="nation-priority-badge">${this.escape(event.summary.label)}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailTimelineDistrict'))} ${this.escape(String(resource.label || meta.nation || this.t('unknown')))}</span>
                        <span>${this.escape(whenText)}</span>
                        <span>${this.escape(this.t('nationDetailTimelineNextStep'))} ${this.escape(event.nextStep)}</span>
                        <span>${this.escape(this.t('nationDetailTimelineRestriction'))} ${this.escape(event.restriction)}</span>
                    </div>
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="open-operation-group" data-priority-filter="${this.escape(operationFilter || 'all')}" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineHandle'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineInspect'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineFocus'))}</button>
                    </div>
                </article>
            `;
        }).join('');
    }

    resetFinanceLedgerState(nationId = '') {
        this.financeLedgerState = {
            nationId: String(nationId || ''),
            page: 1,
            size: 10,
            filter: 'all',
            range: 'all',
            from: '',
            to: '',
            loading: false,
            error: '',
            data: null,
        };
    }

    financeLedgerCanPage(nationId, direction) {
        const state = this.financeLedgerState || {};
        if (state.loading || !state.data || String(state.nationId || '') !== String(nationId || '')) {
            return false;
        }
        const page = Number(state.data.page || state.page || 1);
        const totalPages = Number(state.data.totalPages || 0);
        return direction < 0 ? page > 1 : totalPages > 0 && page < totalPages;
    }

    financeLedgerApiUrl(nationId, page = 1, size = 10, filter = 'all', range = 'all', from = '', to = '') {
        const params = new URLSearchParams();
        params.set('nation', nationId);
        params.set('page', String(Math.max(1, Number(page) || 1)));
        params.set('size', String(Math.max(1, Number(size) || 10)));
        params.set('filter', this.normalizeFinanceLedgerFilter(filter));
        params.set('range', this.normalizeFinanceLedgerRange(range));
        if (from) {
            params.set('from', from);
        }
        if (to) {
            params.set('to', to);
        }
        return this.buildApiUrl(`${MapConfig.financeEventsUrl}?${params.toString()}`);
    }

    financeLedgerExportUrl(nationId, format = 'csv') {
        const state = this.financeLedgerState || {};
        const params = new URLSearchParams();
        params.set('nation', nationId);
        params.set('filter', this.normalizeFinanceLedgerFilter(state.filter || 'all'));
        params.set('range', this.normalizeFinanceLedgerRange(state.range || 'all'));
        params.set('format', format === 'json' ? 'json' : 'csv');
        if (state.range === 'custom' && state.from) {
            params.set('from', state.from);
        }
        if (state.range === 'custom' && state.to) {
            params.set('to', state.to);
        }
        return this.buildApiUrl(`${MapConfig.financeEventsUrl}?${params.toString()}`);
    }

    async loadFinanceLedger(nationId, page = 1, filter = null, range = null, from = null, to = null) {
        const targetNationId = String(nationId || '');
        if (!targetNationId) {
            return;
        }
        const nextPage = Math.max(1, Number(page) || 1);
        const nextFilter = this.normalizeFinanceLedgerFilter(filter || (this.financeLedgerState || {}).filter || 'all');
        const nextRange = this.normalizeFinanceLedgerRange(range || (this.financeLedgerState || {}).range || 'all');
        const nextFrom = nextRange === 'custom' ? String(from ?? (this.financeLedgerState || {}).from ?? '') : '';
        const nextTo = nextRange === 'custom' ? String(to ?? (this.financeLedgerState || {}).to ?? '') : '';
        this.financeLedgerState = {
            ...(this.financeLedgerState || {}),
            nationId: targetNationId,
            page: nextPage,
            size: Number((this.financeLedgerState || {}).size || 10),
            filter: nextFilter,
            range: nextRange,
            from: nextFrom,
            to: nextTo,
            loading: true,
            error: '',
        };
        this.updateNationDetailPanel();
        try {
            const resp = await fetch(this.financeLedgerApiUrl(targetNationId, nextPage, this.financeLedgerState.size, nextFilter, nextRange, nextFrom, nextTo), { cache: 'no-store' });
            const data = await this.readJsonResponse(resp);
            if (!resp.ok || data.ok === false) {
                throw new Error(data.message || this.t('nationDetailFinanceLedgerError'));
            }
            this.financeLedgerState = {
                nationId: targetNationId,
                page: Number(data.page || nextPage),
                size: Number(data.size || this.financeLedgerState.size || 10),
                filter: this.normalizeFinanceLedgerFilter(data.filter || nextFilter),
                range: this.normalizeFinanceLedgerRange(data.range || nextRange),
                from: String(data.from || nextFrom || ''),
                to: String(data.to || nextTo || ''),
                loading: false,
                error: '',
                data,
            };
        } catch (error) {
            console.warn('STARCORE finance ledger failed', error);
            this.financeLedgerState = {
                ...(this.financeLedgerState || {}),
                nationId: targetNationId,
                page: nextPage,
                filter: nextFilter,
                range: nextRange,
                from: nextFrom,
                to: nextTo,
                loading: false,
                error: error.message || this.t('nationDetailFinanceLedgerError'),
                data: (this.financeLedgerState || {}).data || null,
            };
        }
        this.updateNationDetailPanel();
    }

    normalizeFinanceLedgerFilter(filter) {
        const value = String(filter || '').trim().toLowerCase().replaceAll('_', '-');
        if (['resource', 'income', 'reward', 'tax', 'deposit', 'withdraw'].includes(value)) {
            return value;
        }
        return 'all';
    }

    normalizeFinanceLedgerRange(range) {
        const value = String(range || '').trim().toLowerCase().replaceAll('_', '-');
        if (['1h', '24h', '7d', 'custom'].includes(value)) {
            return value;
        }
        return 'all';
    }

    renderFinanceLedgerFilters(nationId) {
        const activeFilter = this.normalizeFinanceLedgerFilter((this.financeLedgerState || {}).filter || 'all');
        return [
            ['all', 'nationDetailFinanceLedgerFilterAll'],
            ['resource', 'nationDetailFinanceLedgerFilterResource'],
            ['income', 'nationDetailFinanceLedgerFilterIncome'],
            ['reward', 'nationDetailFinanceLedgerFilterReward'],
            ['tax', 'nationDetailFinanceLedgerFilterTax'],
            ['deposit', 'nationDetailFinanceLedgerFilterDeposit'],
            ['withdraw', 'nationDetailFinanceLedgerFilterWithdraw'],
        ].map(([value, key]) => {
            const active = activeFilter === value;
            return `<button type="button" class="chip-button finance-ledger-filter${active ? ' is-active' : ''}" data-action="finance-ledger-filter" data-nation-id="${this.escape(nationId)}" data-filter="${this.escape(value)}" aria-pressed="${active}">${this.escape(this.t(key))}</button>`;
        }).join('');
    }

    renderFinanceLedgerRangeControls(nationId) {
        const state = this.financeLedgerState || {};
        const activeRange = this.normalizeFinanceLedgerRange(state.range || 'all');
        const buttons = [
            ['all', 'nationDetailFinanceLedgerRangeAll'],
            ['1h', 'nationDetailFinanceLedgerRange1h'],
            ['24h', 'nationDetailFinanceLedgerRange24h'],
            ['7d', 'nationDetailFinanceLedgerRange7d'],
            ['custom', 'nationDetailFinanceLedgerRangeCustom'],
        ].map(([value, key]) => {
            const active = activeRange === value;
            return `<button type="button" class="chip-button finance-ledger-range${active ? ' is-active' : ''}" data-action="finance-ledger-range" data-nation-id="${this.escape(nationId)}" data-range="${this.escape(value)}" aria-pressed="${active}">${this.escape(this.t(key))}</button>`;
        }).join('');
        const fromValue = this.isoToDateTimeLocal(state.from || '');
        const toValue = this.isoToDateTimeLocal(state.to || '');
        return `
            <div class="nation-finance-ledger-range-buttons">${buttons}</div>
            <div class="nation-finance-ledger-custom-range">
                <label><span>${this.escape(this.t('nationDetailFinanceLedgerRangeFrom'))}</span><input type="datetime-local" data-finance-range-field="from" value="${this.escape(fromValue)}"></label>
                <label><span>${this.escape(this.t('nationDetailFinanceLedgerRangeTo'))}</span><input type="datetime-local" data-finance-range-field="to" value="${this.escape(toValue)}"></label>
                <button type="button" class="motion-button nation-detail-action" data-action="finance-ledger-apply-range" data-nation-id="${this.escape(nationId)}">${this.escape(this.t('nationDetailFinanceLedgerRangeApply'))}</button>
            </div>
        `;
    }

    isoToDateTimeLocal(value) {
        const raw = String(value || '');
        if (!raw) {
            return '';
        }
        const date = new Date(raw);
        if (Number.isNaN(date.getTime())) {
            return '';
        }
        const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
        return local.toISOString().slice(0, 16);
    }

    dateTimeLocalToIso(value) {
        const raw = String(value || '').trim();
        if (!raw) {
            return '';
        }
        const date = new Date(raw);
        return Number.isNaN(date.getTime()) ? '' : date.toISOString();
    }

    renderNationFinanceLedger(nationId) {
        const state = this.financeLedgerState || {};
        if (String(state.nationId || '') !== String(nationId || '')) {
            return '';
        }
        if (state.loading) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailFinanceLedgerLoading'))}</p>`;
        }
        if (state.error) {
            return `<p class="nation-priority-empty">${this.escape(state.error)}</p>`;
        }
        const data = state.data;
        if (!data) {
            return '';
        }
        const events = Array.isArray(data.events) ? data.events : [];
        const page = Number(data.page || state.page || 1);
        const totalPages = Number(data.totalPages || 0);
        const total = Number(data.total || events.length || 0);
        return `
            <div class="nation-finance-ledger-head">
                <span>${this.escape(this.t('nationDetailFinanceLedgerPage', { page: String(page), pages: String(totalPages || 1) }))}</span>
                <span>${this.escape(this.t('nationDetailFinanceLedgerTotal', { count: this.formatWholeNumber(total, '0') }))}</span>
            </div>
            <div class="nation-operations-timeline">
                ${this.renderNationFinanceLedgerEvents(events)}
            </div>
        `;
    }

    renderNationFinanceLedgerEvents(events) {
        if (!Array.isArray(events) || events.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailFinanceEmpty'))}</p>`;
        }
        return events.map((event) => {
            const date = this.parseIsoTimestamp(event.occurredAt);
            const whenText = date
                ? `${this.formatRelativeMoment(date)} · ${this.formatTimestamp(date)}`
                : this.t('generatedUnknown');
            const actor = String(event.actor || '').trim() || this.t('unknown');
            const reason = String(event.reason || '').trim() || this.t('unknown');
            const balance = String(event.balance || '').trim();
            return `
                <article class="nation-timeline-item" data-event-type="${this.escape(event.type || '')}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(event.message || this.t('unknown'))}</strong>
                        <span class="nation-priority-badge">${this.escape(this.formatMoney(event.amount || '0.00'))}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailRecentLogType'))} ${this.escape(this.formatNationRecentEventType(event.type))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogAt'))} ${this.escape(whenText)}</span>
                        <span>${this.escape(this.t('nationDetailFinanceLedgerActor'))} ${this.escape(actor)}</span>
                        <span>${this.escape(this.t('nationDetailFinanceLedgerReason'))} ${this.escape(reason)}</span>
                        ${balance ? `<span>${this.escape(this.t('nationDetailFinanceLedgerBalance'))} ${this.escape(this.formatMoney(balance))}</span>` : ''}
                    </div>
                </article>
            `;
        }).join('');
    }

    resetEventLedgerState(nationId = '') {
        this.eventLedgerState = {
            nationId: String(nationId || ''),
            page: 1,
            size: 10,
            filter: 'all',
            range: 'all',
            from: '',
            to: '',
            resourceId: '',
            query: '',
            actor: '',
            reason: '',
            loading: false,
            error: '',
            data: null,
        };
    }

    eventLedgerCanPage(nationId, direction) {
        const state = this.eventLedgerState || {};
        if (state.loading || !state.data || String(state.nationId || '') !== String(nationId || '')) {
            return false;
        }
        const page = Number(state.data.page || state.page || 1);
        const totalPages = Number(state.data.totalPages || 0);
        return direction < 0 ? page > 1 : totalPages > 0 && page < totalPages;
    }

    eventLedgerApiUrl(nationId, page = 1, size = 10, filter = 'all', range = 'all', from = '', to = '', resourceId = '', query = '', actor = '', reason = '') {
        const params = new URLSearchParams();
        params.set('nation', nationId);
        params.set('page', String(Math.max(1, Number(page) || 1)));
        params.set('size', String(Math.max(1, Number(size) || 10)));
        params.set('filter', this.normalizeNationRecentLogFilter(filter));
        params.set('range', this.normalizeFinanceLedgerRange(range));
        if (from) {
            params.set('from', from);
        }
        if (to) {
            params.set('to', to);
        }
        if (resourceId) {
            params.set('resourceId', resourceId);
        }
        const normalizedQuery = this.normalizeEventLedgerQuery(query);
        if (normalizedQuery) {
            params.set('query', normalizedQuery);
        }
        const normalizedActor = this.normalizeEventLedgerQuery(actor);
        if (normalizedActor) {
            params.set('actor', normalizedActor);
        }
        const normalizedReason = this.normalizeEventLedgerQuery(reason);
        if (normalizedReason) {
            params.set('reason', normalizedReason);
        }
        return this.buildApiUrl(`${MapConfig.eventLogUrl}?${params.toString()}`);
    }

    eventLedgerExportUrl(nationId, format = 'csv') {
        const state = this.eventLedgerState || {};
        const params = new URLSearchParams();
        params.set('nation', nationId);
        params.set('filter', this.normalizeNationRecentLogFilter(state.filter || this.nationRecentLogFilter || 'all'));
        params.set('range', this.normalizeFinanceLedgerRange(state.range || 'all'));
        params.set('format', format === 'json' ? 'json' : 'csv');
        const resourceId = this.normalizeEventLedgerResourceId(state.resourceId || '');
        if (resourceId) {
            params.set('resourceId', resourceId);
        }
        const query = this.normalizeEventLedgerQuery(state.query || '');
        if (query) {
            params.set('query', query);
        }
        const actor = this.normalizeEventLedgerQuery(state.actor || '');
        if (actor) {
            params.set('actor', actor);
        }
        const reason = this.normalizeEventLedgerQuery(state.reason || '');
        if (reason) {
            params.set('reason', reason);
        }
        if (state.range === 'custom' && state.from) {
            params.set('from', state.from);
        }
        if (state.range === 'custom' && state.to) {
            params.set('to', state.to);
        }
        return this.buildApiUrl(`${MapConfig.eventLogUrl}?${params.toString()}`);
    }

    async loadEventLedger(nationId, page = 1, filter = null, range = null, from = null, to = null, resourceId = null, query = null, actor = null, reason = null) {
        const targetNationId = String(nationId || '');
        if (!targetNationId) {
            return;
        }
        const current = this.eventLedgerState || {};
        const nextPage = Math.max(1, Number(page) || 1);
        const nextFilter = this.normalizeNationRecentLogFilter(filter || current.filter || this.nationRecentLogFilter || 'all');
        const nextRange = this.normalizeFinanceLedgerRange(range || current.range || 'all');
        const nextFrom = nextRange === 'custom' ? String(from ?? current.from ?? '') : '';
        const nextTo = nextRange === 'custom' ? String(to ?? current.to ?? '') : '';
        const nextResourceId = this.normalizeEventLedgerResourceId(resourceId ?? current.resourceId ?? '');
        const nextQuery = this.normalizeEventLedgerQuery(query ?? current.query ?? '');
        const nextActor = this.normalizeEventLedgerQuery(actor ?? current.actor ?? '');
        const nextReason = this.normalizeEventLedgerQuery(reason ?? current.reason ?? '');
        this.eventLedgerState = {
            ...current,
            nationId: targetNationId,
            page: nextPage,
            size: Number(current.size || 10),
            filter: nextFilter,
            range: nextRange,
            from: nextFrom,
            to: nextTo,
            resourceId: nextResourceId,
            query: nextQuery,
            actor: nextActor,
            reason: nextReason,
            loading: true,
            error: '',
        };
        this.updateNationDetailPanel();
        try {
            const resp = await fetch(this.eventLedgerApiUrl(targetNationId, nextPage, this.eventLedgerState.size, nextFilter, nextRange, nextFrom, nextTo, nextResourceId, nextQuery, nextActor, nextReason), { cache: 'no-store' });
            const data = await this.readJsonResponse(resp);
            if (!resp.ok || data.ok === false) {
                throw new Error(data.message || this.t('nationDetailEventLedgerError'));
            }
            this.eventLedgerState = {
                nationId: targetNationId,
                page: Number(data.page || nextPage),
                size: Number(data.size || this.eventLedgerState.size || 10),
                filter: this.normalizeNationRecentLogFilter(data.filter || nextFilter),
                range: this.normalizeFinanceLedgerRange(data.range || nextRange),
                from: String(data.from || nextFrom || ''),
                to: String(data.to || nextTo || ''),
                resourceId: this.normalizeEventLedgerResourceId(data.resourceId || nextResourceId || ''),
                query: this.normalizeEventLedgerQuery(data.query || nextQuery || ''),
                actor: this.normalizeEventLedgerQuery(data.actor || nextActor || ''),
                reason: this.normalizeEventLedgerQuery(data.reason || nextReason || ''),
                loading: false,
                error: '',
                data,
            };
        } catch (error) {
            console.warn('STARCORE event ledger failed', error);
            this.eventLedgerState = {
                ...(this.eventLedgerState || {}),
                nationId: targetNationId,
                page: nextPage,
                filter: nextFilter,
                range: nextRange,
                from: nextFrom,
                to: nextTo,
                resourceId: nextResourceId,
                query: nextQuery,
                actor: nextActor,
                reason: nextReason,
                loading: false,
                error: error.message || this.t('nationDetailEventLedgerError'),
                data: (this.eventLedgerState || {}).data || null,
            };
        }
        this.updateNationDetailPanel();
    }

    normalizeEventLedgerResourceId(resourceId) {
        const raw = String(resourceId || '').trim();
        if (!raw) {
            return '';
        }
        return raw.startsWith('resource:') ? raw : `resource:${raw}`;
    }

    normalizeEventLedgerQuery(query) {
        return String(query || '').trim().replace(/\s+/g, ' ').slice(0, 80);
    }

    eventLedgerContextFieldMeta(key) {
        const normalized = String(key || '').trim().toLowerCase();
        if (EVENT_LEDGER_CONTEXT_FIELDS.actor.has(normalized)) {
            return { field: 'actor', group: 'actor', searchable: true };
        }
        if (EVENT_LEDGER_CONTEXT_FIELDS.reason.has(normalized)) {
            return { field: 'reason', group: 'reason', searchable: true };
        }
        if (EVENT_LEDGER_CONTEXT_FIELDS.query.has(normalized)) {
            return { field: 'query', group: 'context', searchable: true };
        }
        if (EVENT_LEDGER_CONTEXT_FIELDS.facts.has(normalized)) {
            return { field: 'readonly', group: 'facts', searchable: false };
        }
        return { field: 'hidden', group: 'other', searchable: false };
    }

    eventLedgerContextSearchField(key) {
        const meta = this.eventLedgerContextFieldMeta(key);
        return meta.searchable ? meta.field : 'query';
    }

    eventLedgerContextFilterLabel(field, key = '') {
        if (field === 'actor') {
            return this.t('nationDetailEventLedgerFilterActor');
        }
        if (field === 'reason') {
            return this.t('nationDetailEventLedgerFilterReason');
        }
        const label = String(key || '').trim();
        return label ? this.formatEventLedgerContextKey(label) : this.t('nationDetailEventLedgerFilterContext');
    }

    formatEventLedgerContextKey(key) {
        const raw = String(key || '').trim();
        if (!raw) {
            return this.t('nationDetailEventLedgerFilterContext');
        }
        return raw
            .replace(/[_-]+/g, ' ')
            .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
            .replace(/\s+/g, ' ')
            .trim();
    }

    eventLedgerContextChipLabel(field, key) {
        const normalized = String(key || '').trim().toLowerCase();
        const genericActorKeys = new Set(['actor', 'operator', 'player', 'viewer', 'member']);
        const genericReasonKeys = new Set(['reason', 'cause']);
        if ((field === 'actor' && !genericActorKeys.has(normalized))
            || (field === 'reason' && !genericReasonKeys.has(normalized))) {
            return this.formatEventLedgerContextKey(key);
        }
        return this.eventLedgerContextFilterLabel(field, key);
    }

    eventLedgerContextChips(details, nationId) {
        if (!details || typeof details !== 'object') {
            return '';
        }
        const seen = new Set();
        const chips = [];
        Object.entries(details).forEach(([key, rawValue]) => {
            const value = this.normalizeEventLedgerQuery(rawValue);
            if (!key || !value) {
                return;
            }
            const meta = this.eventLedgerContextFieldMeta(key);
            if (!meta.searchable) {
                return;
            }
            const field = meta.field;
            const dedupeKey = `${field}:${value.toLowerCase()}`;
            if (seen.has(dedupeKey)) {
                return;
            }
            seen.add(dedupeKey);
            const label = this.eventLedgerContextChipLabel(field, key);
            chips.push(`
                <button type="button" class="chip-button event-ledger-context-chip event-ledger-context-chip-${this.escape(meta.group)}" data-action="event-ledger-context-search" data-event-ledger-context-chip="true" data-event-ledger-context-group="${this.escape(meta.group)}" data-nation-id="${this.escape(nationId)}" data-search-field="${this.escape(field)}" data-search-key="${this.escape(key)}" data-search-value="${this.escape(value)}" title="${this.escape(label)} ${this.escape(value)}">
                    <span class="event-ledger-context-key">${this.escape(label)}</span>
                    <span class="event-ledger-context-value">${this.escape(value)}</span>
                </button>
            `);
        });
        return chips.length ? `<div class="event-ledger-context-chips">${chips.join('')}</div>` : '';
    }

    eventLedgerContextReadonlyFields(details) {
        if (!details || typeof details !== 'object') {
            return '';
        }
        const seen = new Set();
        const facts = [];
        Object.entries(details).forEach(([key, rawValue]) => {
            const value = this.normalizeEventLedgerQuery(rawValue);
            if (!key || !value) {
                return;
            }
            const meta = this.eventLedgerContextFieldMeta(key);
            if (meta.group !== 'facts') {
                return;
            }
            const normalizedKey = String(key).trim().toLowerCase();
            if (seen.has(normalizedKey)) {
                return;
            }
            seen.add(normalizedKey);
            const label = this.formatEventLedgerContextKey(key);
            facts.push(`
                <span class="event-ledger-context-readonly-pill" data-event-ledger-context-readonly="true" data-event-ledger-context-group="facts" data-context-key="${this.escape(key)}" data-context-value="${this.escape(value)}" title="${this.escape(label)} ${this.escape(value)}">
                    <span class="event-ledger-context-key">${this.escape(label)}</span>
                    <span class="event-ledger-context-value">${this.escape(value)}</span>
                </span>
            `);
        });
        return facts.length ? `<div class="event-ledger-context-readonly">${facts.join('')}</div>` : '';
    }

    renderActiveEventLedgerContextFilters() {
        const state = this.eventLedgerState || {};
        const scoped = this.normalizeNationRecentLogFilter(state.filter || 'all') !== 'all'
            || Boolean(this.normalizeEventLedgerResourceId(state.resourceId || ''));
        const filters = [
            ['query', this.t('nationDetailEventLedgerFilterContext'), this.normalizeEventLedgerQuery(state.query || '')],
            ['actor', this.t('nationDetailEventLedgerFilterActor'), this.normalizeEventLedgerQuery(state.actor || '')],
            ['reason', this.t('nationDetailEventLedgerFilterReason'), this.normalizeEventLedgerQuery(state.reason || '')],
        ].filter(([, , value]) => value);
        if (filters.length === 0) {
            return '';
        }
        return `<div class="event-ledger-active-context" data-event-ledger-active-context="true">
            ${filters.map(([field, label, value]) => `
                <button type="button" class="chip-button event-ledger-context-active" data-action="event-ledger-clear-search" data-search-field="${this.escape(field)}" aria-pressed="true">
                    <span class="event-ledger-context-key">${this.escape(label)}</span>
                    <span class="event-ledger-context-value">${this.escape(value)}</span>
                </button>
                ${scoped ? `<button type="button" class="chip-button event-ledger-context-jump" data-action="event-ledger-context-jump" data-search-field="${this.escape(field)}" data-search-value="${this.escape(value)}" title="${this.escape(this.t('nationDetailEventLedgerJumpScope'))}: ${this.escape(label)} ${this.escape(value)}" aria-label="${this.escape(this.t('nationDetailEventLedgerJumpScope'))}: ${this.escape(label)} ${this.escape(value)}">&#8644;</button>` : ''}
            `).join('')}
        </div>`;
    }

    renderEventLedgerFilters(nationId) {
        const activeFilter = this.normalizeNationRecentLogFilter((this.eventLedgerState || {}).filter || this.nationRecentLogFilter || 'all');
        return [
            ['all', 'nationDetailRecentFilterAll'],
            ['resource', 'nationDetailRecentFilterResource'],
            ['finance', 'nationDetailRecentFilterFinance'],
            ['officer', 'nationDetailRecentFilterOfficer'],
            ['diplomacy', 'nationDetailRecentFilterDiplomacy'],
            ['war', 'nationDetailRecentFilterWar'],
            ['strategy', 'nationDetailRecentFilterStrategy'],
            ['territory', 'nationDetailRecentFilterTerritory'],
            ['nation', 'nationDetailRecentFilterNation'],
            ['other', 'nationDetailRecentFilterOther'],
        ].map(([value, key]) => {
            const active = activeFilter === value;
            return `<button type="button" class="chip-button event-ledger-filter${active ? ' is-active' : ''}" data-action="event-ledger-filter" data-nation-id="${this.escape(nationId)}" data-filter="${this.escape(value)}" aria-pressed="${active}">${this.escape(this.t(key))}</button>`;
        }).join('');
    }

    renderEventLedgerRangeControls(nationId) {
        const state = this.eventLedgerState || {};
        const activeRange = this.normalizeFinanceLedgerRange(state.range || 'all');
        const buttons = [
            ['all', 'nationDetailFinanceLedgerRangeAll'],
            ['1h', 'nationDetailFinanceLedgerRange1h'],
            ['24h', 'nationDetailFinanceLedgerRange24h'],
            ['7d', 'nationDetailFinanceLedgerRange7d'],
            ['custom', 'nationDetailFinanceLedgerRangeCustom'],
        ].map(([value, key]) => {
            const active = activeRange === value;
            return `<button type="button" class="chip-button event-ledger-range${active ? ' is-active' : ''}" data-action="event-ledger-range" data-nation-id="${this.escape(nationId)}" data-range="${this.escape(value)}" aria-pressed="${active}">${this.escape(this.t(key))}</button>`;
        }).join('');
        const fromValue = this.isoToDateTimeLocal(state.from || '');
        const toValue = this.isoToDateTimeLocal(state.to || '');
        return `
            <div class="nation-event-ledger-range-buttons">${buttons}</div>
            <div class="nation-event-ledger-custom-range">
                <label><span>${this.escape(this.t('nationDetailFinanceLedgerRangeFrom'))}</span><input type="datetime-local" data-event-range-field="from" value="${this.escape(fromValue)}"></label>
                <label><span>${this.escape(this.t('nationDetailFinanceLedgerRangeTo'))}</span><input type="datetime-local" data-event-range-field="to" value="${this.escape(toValue)}"></label>
                <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-apply-range" data-nation-id="${this.escape(nationId)}">${this.escape(this.t('nationDetailFinanceLedgerRangeApply'))}</button>
            </div>
        `;
    }

    renderEventLedgerResourceControls(nationId, selectedResourceId = '') {
        const state = this.eventLedgerState || {};
        const normalizedSelected = this.normalizeEventLedgerResourceId(selectedResourceId);
        const activeResource = this.normalizeEventLedgerResourceId(state.resourceId || '');
        if (!normalizedSelected) {
            return '';
        }
        return `<div class="nation-event-ledger-resource-controls">
            <button type="button" class="chip-button event-ledger-resource${activeResource ? '' : ' is-active'}" data-action="event-ledger-resource" data-nation-id="${this.escape(nationId)}" data-resource-id="" aria-pressed="${!activeResource}">${this.escape(this.t('nationDetailEventLedgerAllResources'))}</button>
            <button type="button" class="chip-button event-ledger-resource${activeResource === normalizedSelected ? ' is-active' : ''}" data-action="event-ledger-resource" data-nation-id="${this.escape(nationId)}" data-resource-id="${this.escape(normalizedSelected)}" aria-pressed="${activeResource === normalizedSelected}">${this.escape(this.t('nationDetailEventLedgerResourceOnly'))}</button>
        </div>`;
    }

    renderEventLedgerSearchControls(nationId) {
        const state = this.eventLedgerState || {};
        const query = this.normalizeEventLedgerQuery(state.query || '');
        const hasContextSearch = Boolean(query || this.normalizeEventLedgerQuery(state.actor || '') || this.normalizeEventLedgerQuery(state.reason || ''));
        return `<div class="nation-event-ledger-search">
            <label>
                <span>${this.escape(this.t('nationDetailEventLedgerSearch'))}</span>
                <input type="search" data-event-ledger-search="query" value="${this.escape(query)}" placeholder="${this.escape(this.t('nationDetailEventLedgerSearchPlaceholder'))}">
            </label>
            <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-search" data-nation-id="${this.escape(nationId)}">${this.escape(this.t('nationDetailEventLedgerSearchApply'))}</button>
            <button type="button" class="motion-button nation-detail-action" data-action="event-ledger-clear-search" data-nation-id="${this.escape(nationId)}"${hasContextSearch ? '' : ' disabled'}>${this.escape(this.t('nationDetailEventLedgerSearchClear'))}</button>
            ${this.renderActiveEventLedgerContextFilters()}
        </div>`;
    }

    renderNationEventLedger(nationId) {
        const state = this.eventLedgerState || {};
        if (String(state.nationId || '') !== String(nationId || '')) {
            return '';
        }
        if (state.loading) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailEventLedgerLoading'))}</p>`;
        }
        if (state.error) {
            return `<p class="nation-priority-empty">${this.escape(state.error)}</p>`;
        }
        const data = state.data;
        if (!data) {
            return '';
        }
        const events = Array.isArray(data.events) ? data.events : [];
        const page = Number(data.page || state.page || 1);
        const totalPages = Number(data.totalPages || 0);
        const total = Number(data.total || events.length || 0);
        return `
            <div class="nation-finance-ledger-head">
                <span>${this.escape(this.t('nationDetailEventLedgerPage', { page: String(page), pages: String(totalPages || 1) }))}</span>
                <span>${this.escape(this.t('nationDetailEventLedgerTotal', { count: this.formatWholeNumber(total, '0') }))}</span>
            </div>
            <div class="nation-operations-timeline">
                ${this.renderNationEventLedgerEvents(events, nationId)}
            </div>
        `;
    }

    renderNationEventLedgerEvents(events, nationId = '') {
        if (!Array.isArray(events) || events.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailEventLedgerEmpty'))}</p>`;
        }
        return events.map((event) => {
            const date = this.parseIsoTimestamp(event.occurredAt);
            const whenText = date
                ? `${this.formatRelativeMoment(date)} · ${this.formatTimestamp(date)}`
                : this.t('generatedUnknown');
            const category = this.normalizeNationRecentEventCategory(event.category);
            const resourceId = String(event.resourceId || '');
            const details = event && typeof event.details === 'object' && event.details !== null ? event.details : {};
            const actor = String(details.actor || details.operator || details.player || '').trim();
            const reason = String(details.reason || details.cause || '').trim();
            const contextChips = this.eventLedgerContextChips(details, nationId || this.selectedNationId || '');
            const contextFacts = this.eventLedgerContextReadonlyFields(details);
            const operationLink = this.renderEventLedgerOperationLink(event, category, details);
            const actionButtons = resourceId && !operationLink
                ? `
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineInspect'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineFocus'))}</button>
                    </div>
                `
                : '';
            return `
                <article class="nation-timeline-item" data-event-ledger-item="true" data-event-type="${this.escape(event.type || '')}" data-event-category="${this.escape(category)}" data-resource-id="${this.escape(resourceId)}" data-event-actor="${this.escape(actor)}" data-event-reason="${this.escape(reason)}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(event.message || this.t('unknown'))}</strong>
                        <span class="nation-priority-badge">${this.escape(this.formatNationRecentEventCategory(category))}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailRecentLogCategory'))} ${this.escape(this.formatNationRecentEventCategory(category))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogType'))} ${this.escape(this.formatNationRecentEventType(event.type))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogAt'))} ${this.escape(whenText)}</span>
                        ${actor ? `<span>${this.escape(this.t('nationDetailFinanceLedgerActor'))} ${this.escape(actor)}</span>` : ''}
                        ${reason ? `<span>${this.escape(this.t('nationDetailFinanceLedgerReason'))} ${this.escape(reason)}</span>` : ''}
                    </div>
                    ${contextChips}
                    ${contextFacts}
                    ${operationLink}
                    ${actionButtons}
                </article>
            `;
        }).join('');
    }

    renderNationFinanceEvents(events) {
        if (!Array.isArray(events) || events.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailFinanceEmpty'))}</p>`;
        }
        return events.map((event) => {
            const whenText = event.date
                ? `${this.formatRelativeMoment(event.date)} · ${this.formatTimestamp(event.date)}`
                : this.t('generatedUnknown');
            const amount = this.formatMoney(event.amount || '0.00');
            return `
                <article class="nation-timeline-item" data-event-type="${this.escape(event.type || '')}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(event.message || this.t('unknown'))}</strong>
                        <span class="nation-priority-badge">${this.escape(amount)}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailRecentLogType'))} ${this.escape(this.formatNationRecentEventType(event.type))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogAt'))} ${this.escape(whenText)}</span>
                    </div>
                </article>
            `;
        }).join('');
    }

    renderNationRecentLogFilters(filters) {
        if (!Array.isArray(filters) || filters.length <= 1) {
            return '';
        }
        const activeFilter = this.normalizeNationRecentLogFilter(this.nationRecentLogFilter);
        return `<div id="nation-recent-log-filters" class="nation-recent-log-filters">
            ${filters.map(filter => {
                const value = this.normalizeNationRecentLogFilter(filter.value);
                const active = value === activeFilter;
                return `<button type="button" class="chip-button recent-log-filter${active ? ' is-active' : ''}" data-action="recent-log-filter" data-recent-log-filter="${this.escape(value)}" aria-pressed="${active}">${this.escape(filter.label)} <span>${this.escape(this.formatWholeNumber(filter.count, '0'))}</span></button>`;
            }).join('')}
        </div>`;
    }

    renderNationRecentEvents(events) {
        if (!Array.isArray(events) || events.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailRecentLogEmpty'))}</p>`;
        }
        return events.map((event) => {
            const whenText = event.date
                ? `${this.formatRelativeMoment(event.date)} · ${this.formatTimestamp(event.date)}`
                : this.t('generatedUnknown');
            const resourceId = String(event.resourceId || '');
            const actionButtons = resourceId
                ? `
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineInspect'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailTimelineFocus'))}</button>
                    </div>
                `
                : '';
            const category = this.normalizeNationRecentEventCategory(event.category);
            return `
                <article class="nation-timeline-item" data-event-type="${this.escape(event.type || '')}" data-event-category="${this.escape(category)}" data-resource-id="${this.escape(resourceId)}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(event.message || this.t('unknown'))}</strong>
                        <span class="nation-priority-badge">${this.escape(this.formatNationRecentEventCategory(category))}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailRecentLogCategory'))} ${this.escape(this.formatNationRecentEventCategory(category))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogType'))} ${this.escape(this.formatNationRecentEventType(event.type))}</span>
                        <span>${this.escape(this.t('nationDetailRecentLogAt'))} ${this.escape(whenText)}</span>
                    </div>
                    ${actionButtons}
                </article>
            `;
        }).join('');
    }

    renderNationOperationGroups(groups) {
        if (!Array.isArray(groups) || groups.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t('nationDetailOutlookNoData'))}</p>`;
        }
        return groups.map((group) => {
            const lead = group.lead || {};
            const leadResource = lead.resource || {};
            const leadMeta = lead.meta || {};
            const resourceId = String(leadResource.id || '');
            const summary = this.resourceOperationSummary(leadMeta);
            const explanation = this.resourceMigrationExplanation(leadMeta, summary);
            const activeClass = this.nationPriorityFilter === group.state ? ' is-active' : '';
            return `
                <article class="nation-operation-group${activeClass}" data-operation-group="${this.escape(group.state)}" data-resource-id="${this.escape(resourceId)}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(group.label)}</strong>
                        <span class="nation-priority-badge">${this.escape(this.t('nationDetailGroupCount'))} ${this.escape(this.formatWholeNumber(group.count, '0'))}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailGroupLead'))} ${this.escape(String(leadResource.label || leadMeta.nation || this.t('unknown')))}</span>
                        <span>${this.escape(this.t('nationDetailTopPriority'))} ${this.escape(summary.label)}</span>
                        <span>${this.escape(this.t('nationDetailCycleYield'))} ${this.escape(this.formatWholeNumber(leadMeta.expectedResourceYield, '0'))} · XP ${this.escape(this.formatWholeNumber(leadMeta.expectedExperienceYield, '0'))}</span>
                    </div>
                    ${this.renderResourceMigrationExplanation(explanation, true)}
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="open-operation-group" data-priority-filter="${this.escape(group.state)}" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailOpenGroup'))}</button>
                    </div>
                </article>
            `;
        }).join('');
    }

    renderNationPriorityQueue(resources) {
        if (!Array.isArray(resources) || resources.length === 0) {
            return `<p class="nation-priority-empty">${this.escape(this.t(this.nationPriorityFilter === 'all' ? 'nationDetailPriorityEmpty' : 'nationDetailFilterEmpty'))}</p>`;
        }
        return resources.map(({ resource, meta }) => {
            const resourceId = String(resource.id || '');
            const selectedClass = resourceId === this.selectedResourceId ? ' is-selected' : '';
            const summary = this.resourceOperationSummary(meta);
            const explanation = this.resourceMigrationExplanation(meta, summary);
            const forecast = this.resourceForecastMetrics(meta);
            const nextStep = String(meta.migrationNextStep || summary.defaultHint || this.t('unknown'));
            const targetText = meta.pendingTarget || this.t('unknown');
            const shortfall = this.formatMoney(meta.migrationBalanceShortfall || '0.00');
            const yieldText = `${this.t('cycleResourcesLabel')} ${this.formatWholeNumber(meta.expectedResourceYield, '0')} · XP ${this.formatWholeNumber(meta.expectedExperienceYield, '0')}`;
            const refreshText = `${this.t('refreshCycleLabel')} ${this.formatDurationMinutes(meta.refreshCooldownMinutes)}`;
            const forecastText = `${this.t('nextThreeCyclesLabel')} ${this.formatWholeNumber(forecast.next3Resource, '0')} · XP ${this.formatWholeNumber(forecast.next3Experience, '0')}${forecast.next3WindowMinutes > 0 ? ` · ${this.t('forecastWindowLabel')} ${this.formatDurationMinutes(forecast.next3WindowMinutes)}` : ''}`;
            const targetLine = meta.pendingTarget
                ? `${this.t('nationDetailTarget')} ${targetText}`
                : `${this.t('districtShortfallLabel')} ${shortfall}`;
            return `
                <article class="nation-priority-item${selectedClass}" data-resource-id="${this.escape(resourceId)}" data-priority-state="${this.escape(summary.actionState)}">
                    <div class="nation-priority-head">
                        <strong>${this.escape(resource.label || meta.nation || this.t('sectionResources'))}</strong>
                        <span class="nation-priority-badge">${this.escape(summary.label)}</span>
                    </div>
                    <div class="intel-detail-list">
                        <span>${this.escape(this.t('nationDetailMigration'))} ${this.escape(this.formatResourceMigrationState(meta))}</span>
                        <span>${this.escape(yieldText)}</span>
                        <span>${this.escape(refreshText)}</span>
                        <span>${this.escape(forecastText)}</span>
                        <span>${this.escape(this.t('districtNextStepLabel'))} ${this.escape(nextStep)}</span>
                        <span>${this.escape(targetLine)}</span>
                    </div>
                    ${this.renderResourceMigrationExplanation(explanation, true)}
                    <div class="nation-priority-actions">
                        <button type="button" class="motion-button nation-detail-action" data-action="select-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailSelectResource'))}</button>
                        <button type="button" class="motion-button nation-detail-action" data-action="focus-resource" data-resource-id="${this.escape(resourceId)}">${this.escape(this.t('nationDetailFocusResource'))}</button>
                    </div>
                </article>
            `;
        }).join('');
    }

    resourceMigrationExplanation(meta = {}, summary = null) {
        const operationSummary = summary || this.resourceOperationSummary(meta);
        const state = String(meta.migrationExplanationState || operationSummary.actionState || this.normalizeResourceActionState(meta) || 'unknown').trim().toLowerCase();
        const severity = String(meta.migrationExplanationSeverity || (operationSummary.actionState === 'ready' ? 'success' : 'info')).trim().toLowerCase();
        const nextStep = String(meta.migrationNextStep || operationSummary.defaultHint || '');
        const restriction = String(meta.migrationRestrictionDetail || meta.migrationRestriction || '');
        const primaryReason = String(meta.migrationExplanationPrimaryReason || restriction || nextStep || operationSummary.label || '').trim();
        const summaryText = String(meta.migrationExplanationSummary || (nextStep ? `${operationSummary.label} - ${nextStep}` : operationSummary.label) || '').trim();
        const reasonCodes = String(meta.migrationExplanationReasonCodes || state)
            .split(',')
            .map(code => code.trim())
            .filter(Boolean);
        const shortfall = this.formatMoney(meta.migrationBalanceShortfall || '0.00');
        return {
            state,
            severity,
            summary: summaryText,
            primaryReason,
            nextStep,
            restriction,
            shortfall,
            reasonCodes,
            hasShortfall: Number(meta.migrationBalanceShortfall || 0) > 0,
        };
    }

    renderResourceMigrationExplanation(explanation, compact = false) {
        if (!explanation || (!explanation.summary && !explanation.primaryReason && !explanation.nextStep)) {
            return '';
        }
        const state = String(explanation.state || 'unknown');
        const severity = String(explanation.severity || 'info');
        const reasonCodes = Array.isArray(explanation.reasonCodes) && explanation.reasonCodes.length > 0
            ? explanation.reasonCodes.join(',')
            : state;
        const rows = [];
        if (explanation.summary) {
            rows.push(`<span>${this.escape(this.t('districtExplanationSummaryLabel'))} ${this.escape(explanation.summary)}</span>`);
        }
        if (explanation.primaryReason && explanation.primaryReason !== explanation.summary) {
            rows.push(`<span>${this.escape(this.t('districtReasonLabel'))} ${this.escape(explanation.primaryReason)}</span>`);
        }
        if (!compact && explanation.nextStep && explanation.nextStep !== explanation.primaryReason) {
            rows.push(`<span>${this.escape(this.t('districtNextStepLabel'))} ${this.escape(explanation.nextStep)}</span>`);
        }
        if (explanation.hasShortfall) {
            rows.push(`<span>${this.escape(this.t('districtExplanationShortfallLabel'))} ${this.escape(explanation.shortfall)}</span>`);
        }
        return `
            <div class="resource-explanation" data-resource-explanation-state="${this.escape(state)}" data-resource-explanation-severity="${this.escape(severity)}" data-resource-explanation-codes="${this.escape(reasonCodes)}">
                <span class="resource-explanation-label">${this.escape(this.t('districtExplanationLabel'))}</span>
                <div class="resource-explanation-lines">
                    ${rows.join('')}
                </div>
            </div>
        `;
    }

    renderNationPriorityFilters() {
        const filters = [
            ['all', 'nationDetailFilterAll'],
            ['ready', 'nationDetailFilterReady'],
            ['awaiting-target', 'nationDetailFilterAwaitingTarget'],
            ['insufficient-balance', 'nationDetailFilterInsufficientBalance'],
            ['waiting-depletion', 'nationDetailFilterWaitingDepletion'],
            ['player-offline', 'nationDetailFilterPlayerOffline'],
            ['leader-only', 'nationDetailFilterLeaderOnly'],
        ];
        return filters.map(([value, key]) => {
            const active = this.nationPriorityFilter === value;
            return `<button type="button" class="chip-button nation-priority-filter${active ? ' is-active' : ''}" data-priority-filter="${this.escape(value)}" aria-pressed="${active}">${this.escape(this.t(key))}</button>`;
        }).join('');
    }

    priorityFilterLabel(value = 'all') {
        switch (String(value || 'all')) {
            case 'ready':
                return this.t('nationDetailFilterReady');
            case 'awaiting-target':
                return this.t('nationDetailFilterAwaitingTarget');
            case 'insufficient-balance':
                return this.t('nationDetailFilterInsufficientBalance');
            case 'waiting-depletion':
                return this.t('nationDetailFilterWaitingDepletion');
            case 'player-offline':
                return this.t('nationDetailFilterPlayerOffline');
            case 'leader-only':
                return this.t('nationDetailFilterLeaderOnly');
            default:
                return this.t('nationDetailFilterAll');
        }
    }

    filterNationResourcesForOperations(resources) {
        const currentFilter = String(this.nationPriorityFilter || 'all');
        if (currentFilter === 'all') {
            return resources;
        }
        return (resources || []).filter(({ meta }) => this.normalizeResourceActionState(meta) === currentFilter);
    }

    rankNationResourcesForOperations(resources) {
        return [...(resources || [])].sort((left, right) => {
            const leftSummary = this.resourceOperationSummary(left.meta);
            const rightSummary = this.resourceOperationSummary(right.meta);
            if (leftSummary.rank !== rightSummary.rank) {
                return rightSummary.rank - leftSummary.rank;
            }
            const leftYield = Number(left.meta.expectedResourceYield || 0);
            const rightYield = Number(right.meta.expectedResourceYield || 0);
            if (leftYield !== rightYield) {
                return rightYield - leftYield;
            }
            const leftShortfall = Number(left.meta.migrationBalanceShortfall || 0);
            const rightShortfall = Number(right.meta.migrationBalanceShortfall || 0);
            if (leftShortfall !== rightShortfall) {
                return rightShortfall - leftShortfall;
            }
            return String(left.resource.label || '').localeCompare(String(right.resource.label || ''), this.language === 'zh' ? 'zh-CN' : 'en');
        });
    }

    resourceOperationSummary(meta = {}) {
        const actionState = this.normalizeResourceActionState(meta);
        switch (actionState) {
            case 'awaiting-target':
                return { actionState, rank: 700, label: this.t('nationDetailPriorityAwaitingTarget'), defaultHint: String(meta.migrationNextStep || meta.migrationRestrictionDetail || this.t('nationDetailPriorityAwaitingTarget')) };
            case 'waiting-depletion':
                return { actionState, rank: 650, label: this.t('nationDetailPriorityWaitingDepletion'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
            case 'ready':
                return { actionState, rank: 600, label: this.t('nationDetailPriorityReady'), defaultHint: String(meta.migrationNextStep || meta.migrationRestrictionDetail || this.t('nationDetailPriorityReady')) };
            case 'insufficient-balance':
                return { actionState, rank: 550, label: this.t('nationDetailPriorityInsufficientBalance'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
            case 'player-offline':
                return { actionState, rank: 500, label: this.t('nationDetailPriorityPlayerOffline'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
            case 'leader-only':
                return { actionState, rank: 450, label: this.t('nationDetailPriorityLeaderOnly'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
            case 'not-own-nation':
                return { actionState, rank: 400, label: this.t('nationDetailPriorityExternal'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
            default:
                return { actionState, rank: 300, label: this.t('nationDetailPriorityUnknown'), defaultHint: String(meta.migrationRestrictionDetail || meta.migrationNextStep || '') };
        }
    }

    normalizeResourceActionState(meta = {}) {
        const actionState = String(meta.migrationActionState || '').trim().toLowerCase();
        if (actionState) {
            return actionState;
        }
        switch (String(meta.migrationState || '').trim().toLowerCase()) {
            case 'awaiting_target':
                return 'awaiting-target';
            case 'waiting_depletion':
                return 'waiting-depletion';
            case 'none':
                return 'ready';
            default:
                return '';
        }
    }

    formatNationRecentEventType(type) {
        const normalized = String(type || '').trim();
        if (!normalized) {
            return this.t('unknown');
        }
        return normalized
            .replaceAll('.', ' / ')
            .replaceAll('-', ' ');
    }

    normalizeNationRecentEventCategory(category) {
        const normalized = String(category || '').trim().toLowerCase().replaceAll('_', '-');
        const allowed = new Set(['resource', 'finance', 'officer', 'diplomacy', 'war', 'strategy', 'territory', 'nation', 'other']);
        return allowed.has(normalized) ? normalized : 'other';
    }

    nationRecentEventCategory(type) {
        const normalized = String(type || '').trim().toLowerCase();
        if (normalized.startsWith('resource.')) {
            return 'resource';
        }
        if (normalized.startsWith('treasury.')) {
            return 'finance';
        }
        if (normalized.startsWith('officer.')) {
            return 'officer';
        }
        if (normalized.startsWith('diplomacy.')) {
            return 'diplomacy';
        }
        if (normalized.startsWith('war.')) {
            return 'war';
        }
        if (normalized.startsWith('policy.') || normalized.startsWith('technology.') || normalized.startsWith('government.')) {
            return 'strategy';
        }
        if (normalized.startsWith('territory.') || normalized.startsWith('claim.')) {
            return 'territory';
        }
        if (normalized.startsWith('nation.') || normalized.startsWith('city.') || normalized.startsWith('resolution.')) {
            return 'nation';
        }
        return 'other';
    }

    formatNationRecentEventCategory(category) {
        const normalized = this.normalizeNationRecentEventCategory(category);
        const key = `nationDetailRecentFilter${normalized.charAt(0).toUpperCase()}${normalized.slice(1)}`;
        return this.t(key);
    }

    filteredNationRecentEvents(events) {
        const filter = this.normalizeNationRecentLogFilter(this.nationRecentLogFilter);
        const source = Array.isArray(events) ? events : [];
        if (filter === 'all') {
            return source;
        }
        return source.filter(event => this.normalizeNationRecentEventCategory(event.category) === filter);
    }

    normalizeNationRecentLogFilter(filter) {
        const normalized = String(filter || '').trim().toLowerCase().replaceAll('_', '-');
        if (normalized === 'all') {
            return 'all';
        }
        return this.normalizeNationRecentEventCategory(normalized);
    }

    nationRecentLogFilters(events) {
        const source = Array.isArray(events) ? events : [];
        if (!source.length) {
            return [];
        }
        const counts = new Map();
        source.forEach(event => {
            const category = this.normalizeNationRecentEventCategory(event.category);
            counts.set(category, (counts.get(category) || 0) + 1);
        });
        const ordered = ['resource', 'finance', 'officer', 'diplomacy', 'war', 'strategy', 'territory', 'nation', 'other'];
        const filters = [{
            value: 'all',
            label: this.t('nationDetailRecentFilterAll'),
            count: source.length,
        }];
        ordered.forEach(category => {
            const count = counts.get(category) || 0;
            if (count > 0) {
                filters.push({
                    value: category,
                    label: this.formatNationRecentEventCategory(category),
                    count,
                });
            }
        });
        return filters;
    }

    bindNationDetailPanelEvents() {
        document.querySelectorAll('#nation-detail-panel [data-action]').forEach(button => {
            button.addEventListener('click', () => {
                const action = button.dataset.action || '';
                if (action === 'focus-nation') {
                    const nationId = button.dataset.nationId || '';
                    if (nationId) {
                        this.flyToNation(nationId);
                        this.maybeCollapseSidebarAfterMapAction();
                    }
                    return;
                }
                if (action === 'open-operation-group') {
                    const filterValue = button.dataset.priorityFilter || 'all';
                    const resourceId = button.dataset.resourceId || '';
                    this.activateNationOperationGroup(filterValue, resourceId);
                    return;
                }
                if (action === 'focus-prev-resource') {
                    this.navigateNationOperationsFocus(-1);
                    return;
                }
                if (action === 'focus-next-resource') {
                    this.navigateNationOperationsFocus(1);
                    return;
                }
                if (action === 'recent-log-filter') {
                    const nextFilter = this.normalizeNationRecentLogFilter(button.dataset.recentLogFilter || 'all');
                    if (this.nationRecentLogFilter !== nextFilter) {
                        this.nationRecentLogFilter = nextFilter;
                        this.updateNationDetailPanel();
                    }
                    return;
                }
                if (action === 'load-event-ledger') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const filter = state.filter || this.nationRecentLogFilter || 'all';
                    this.loadEventLedger(nationId, 1, filter, state.range || 'all', state.from || '', state.to || '', state.resourceId || '', state.query || '', state.actor || '', state.reason || '');
                    return;
                }
                if (action === 'event-ledger-filter') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    this.loadEventLedger(nationId, 1, button.dataset.filter || 'all', state.range || 'all', state.from || '', state.to || '', state.resourceId || '', state.query || '', state.actor || '', state.reason || '');
                    return;
                }
                if (action === 'event-ledger-range') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const nextRange = button.dataset.range || 'all';
                    this.loadEventLedger(nationId, 1, state.filter || 'all', nextRange, state.from || '', state.to || '', state.resourceId || '', state.query || '', state.actor || '', state.reason || '');
                    return;
                }
                if (action === 'event-ledger-apply-range') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const fromInput = document.querySelector('#nation-detail-panel [data-event-range-field="from"]');
                    const toInput = document.querySelector('#nation-detail-panel [data-event-range-field="to"]');
                    this.loadEventLedger(
                        nationId,
                        1,
                        state.filter || 'all',
                        'custom',
                        this.dateTimeLocalToIso(fromInput ? fromInput.value : ''),
                        this.dateTimeLocalToIso(toInput ? toInput.value : ''),
                        state.resourceId || '',
                        state.query || '',
                        state.actor || '',
                        state.reason || ''
                    );
                    return;
                }
                if (action === 'event-ledger-resource') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    this.loadEventLedger(nationId, 1, state.filter || 'all', state.range || 'all', state.from || '', state.to || '', button.dataset.resourceId || '', state.query || '', state.actor || '', state.reason || '');
                    return;
                }
                if (action === 'event-ledger-search' || action === 'event-ledger-clear-search') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const input = document.querySelector('#nation-detail-panel [data-event-ledger-search="query"]');
                    const query = action === 'event-ledger-clear-search'
                        ? ''
                        : this.normalizeEventLedgerQuery(input ? input.value : state.query || '');
                    this.loadEventLedger(nationId, 1, state.filter || 'all', state.range || 'all', state.from || '', state.to || '', state.resourceId || '', query, '', '');
                    return;
                }
                if (action === 'event-ledger-context-search') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const field = ['actor', 'reason', 'query'].includes(button.dataset.searchField || '')
                        ? button.dataset.searchField
                        : this.eventLedgerContextSearchField(button.dataset.searchKey || '');
                    const value = this.normalizeEventLedgerQuery(button.dataset.searchValue || '');
                    if (!value) {
                        return;
                    }
                    this.loadEventLedger(
                        nationId,
                        1,
                        state.filter || 'all',
                        state.range || 'all',
                        state.from || '',
                        state.to || '',
                        state.resourceId || '',
                        field === 'query' ? value : '',
                        field === 'actor' ? value : '',
                        field === 'reason' ? value : ''
                    );
                    return;
                }
                if (action === 'event-ledger-context-jump') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const field = ['actor', 'reason', 'query'].includes(button.dataset.searchField || '')
                        ? button.dataset.searchField
                        : 'query';
                    const value = this.normalizeEventLedgerQuery(button.dataset.searchValue
                        || (field === 'actor' ? state.actor : field === 'reason' ? state.reason : state.query)
                        || '');
                    if (!value) {
                        return;
                    }
                    this.loadEventLedger(
                        nationId,
                        1,
                        'all',
                        state.range || 'all',
                        state.from || '',
                        state.to || '',
                        '',
                        field === 'query' ? value : '',
                        field === 'actor' ? value : '',
                        field === 'reason' ? value : ''
                    );
                    return;
                }
                if (action === 'event-ledger-operation-scope') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const filter = this.normalizeNationRecentLogFilter(button.dataset.operationFilter || button.dataset.eventOperationFamily || state.filter || 'all');
                    const query = this.normalizeEventLedgerQuery(button.dataset.operationQuery || '');
                    const actor = this.normalizeEventLedgerQuery(button.dataset.operationActor || '');
                    const reason = this.normalizeEventLedgerQuery(button.dataset.operationReason || '');
                    this.loadEventLedger(
                        nationId,
                        1,
                        filter,
                        state.range || '24h',
                        state.from || '',
                        state.to || '',
                        '',
                        query,
                        actor,
                        reason
                    );
                    return;
                }
                if (action === 'event-ledger-export') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const format = button.dataset.format || 'csv';
                    if (nationId) {
                        window.open(this.eventLedgerExportUrl(nationId, format), '_blank', 'noopener');
                    }
                    return;
                }
                if (action === 'event-ledger-prev' || action === 'event-ledger-next') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.eventLedgerState || {};
                    const currentPage = Number((this.eventLedgerState && this.eventLedgerState.page) || 1);
                    this.loadEventLedger(nationId, currentPage + (action === 'event-ledger-prev' ? -1 : 1), state.filter || 'all', state.range || 'all', state.from || '', state.to || '', state.resourceId || '', state.query || '', state.actor || '', state.reason || '');
                    return;
                }
                if (action === 'load-finance-ledger') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.financeLedgerState || {};
                    this.loadFinanceLedger(nationId, 1, state.filter || 'all', state.range || 'all', state.from || '', state.to || '');
                    return;
                }
                if (action === 'finance-ledger-filter') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.financeLedgerState || {};
                    this.loadFinanceLedger(nationId, 1, button.dataset.filter || 'all', state.range || 'all', state.from || '', state.to || '');
                    return;
                }
                if (action === 'finance-ledger-range') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.financeLedgerState || {};
                    const nextRange = button.dataset.range || 'all';
                    this.loadFinanceLedger(nationId, 1, state.filter || 'all', nextRange, state.from || '', state.to || '');
                    return;
                }
                if (action === 'finance-ledger-apply-range') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.financeLedgerState || {};
                    const fromInput = document.querySelector('#nation-detail-panel [data-finance-range-field="from"]');
                    const toInput = document.querySelector('#nation-detail-panel [data-finance-range-field="to"]');
                    this.loadFinanceLedger(
                        nationId,
                        1,
                        state.filter || 'all',
                        'custom',
                        this.dateTimeLocalToIso(fromInput ? fromInput.value : ''),
                        this.dateTimeLocalToIso(toInput ? toInput.value : '')
                    );
                    return;
                }
                if (action === 'finance-ledger-export') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const format = button.dataset.format || 'csv';
                    if (nationId) {
                        window.open(this.financeLedgerExportUrl(nationId, format), '_blank', 'noopener');
                    }
                    return;
                }
                if (action === 'finance-ledger-prev' || action === 'finance-ledger-next') {
                    const nationId = button.dataset.nationId || this.selectedNationId || '';
                    const state = this.financeLedgerState || {};
                    const currentPage = Number((this.financeLedgerState && this.financeLedgerState.page) || 1);
                    this.loadFinanceLedger(nationId, currentPage + (action === 'finance-ledger-prev' ? -1 : 1), state.filter || 'all', state.range || 'all', state.from || '', state.to || '');
                    return;
                }
                const resourceId = button.dataset.resourceId || '';
                if (!resourceId) {
                    return;
                }
                if (action === 'select-resource') {
                    this.setSelectedResource(resourceId);
                    return;
                }
                if (action === 'focus-resource') {
                    this.setSelectedResource(resourceId);
                    this.flyToResourceDistrict(resourceId);
                    this.maybeCollapseSidebarAfterMapAction();
                    return;
                }
            });
        });
        document.querySelectorAll('#nation-detail-priority-filters [data-priority-filter]').forEach(button => {
            button.addEventListener('click', () => {
                const nextFilter = String(button.dataset.priorityFilter || 'all');
                if (this.nationPriorityFilter === nextFilter) {
                    return;
                }
                this.applyNationOperationFilterSelection(nextFilter);
            });
        });
        document.querySelectorAll('#nation-detail-priority-list .nation-priority-item').forEach(item => {
            item.addEventListener('click', (event) => {
                if (event.target && typeof event.target.closest === 'function' && event.target.closest('button')) {
                    return;
                }
                const resourceId = item.dataset.resourceId || '';
                if (resourceId) {
                    this.setSelectedResource(resourceId);
                    this.flyToResourceDistrict(resourceId);
                    this.maybeCollapseSidebarAfterMapAction();
                }
            });
        });
    }

    updatePlayerList() {
        const list = document.getElementById('player-list');
        const toggleMeta = document.getElementById('players-toggle-meta');
        if (toggleMeta) {
            toggleMeta.textContent = this.playerMarkers.length > 0
                ? `${this.playerMarkers.length} ${this.t('sectionPlayers')}`
                : this.t('noPlayersOnline');
        }
        if (this.playerMarkers.length === 0) {
            list.innerHTML = `<li class="empty-state">${this.escape(this.t('noPlayersOnline'))}</li>`;
            return;
        }

        list.innerHTML = this.playerMarkers.map(p => {
            const meta = p.data.metadata || {};
            const nation = meta.nation || this.t('independent');
            const relation = this.relationForNation(meta.nationId, meta);
            const relationClass = this.relationClassFor(relation);
            const selectedClass = p.id === this.selectedPlayerId ? ' is-selected' : '';
            const initials = (meta.avatarHint || p.label || '?').slice(0, 2).toUpperCase();
            const avatarUrl = meta.avatarUrl || this.avatarUrlFromId(meta.playerId);
            const avatarNode = avatarUrl
                ? `<span class="player-face ${relationClass}"><img src="${this.escape(avatarUrl)}" alt="${this.escape(p.label)}" loading="lazy" referrerpolicy="no-referrer" onerror="const holder=this.parentElement; this.remove(); if(holder){holder.classList.add('fallback'); holder.textContent='${this.escape(initials)}';}"></span>`
                : `<span class="player-face ${relationClass} fallback">${this.escape(initials)}</span>`;
            const searchText = [p.label, nation, this.formatRelation(relation)].filter(Boolean).join(' ');
            return `<li class="${selectedClass.trim()}" data-player="${this.escape(p.id)}" data-search-text="${this.escape(searchText)}">
                        ${avatarNode}
                        <span class="item-name">${this.escape(p.label)}</span>
                        <span class="item-meta">${this.escape(nation)} · ${this.escape(this.formatRelation(relation))}</span>
                    </li>`;
        }).join('');
    }

    selectedPlayer() {
        return this.playerMarkers.find(player => player.id === this.selectedPlayerId) || null;
    }

    setSelectedPlayer(playerId) {
        const nextId = String(playerId || '');
        if (this.selectedPlayerId === nextId) {
            return;
        }
        this.selectedPlayerId = nextId;
        this.updatePlayerList();
        this.bindPlayerListEvents();
        this.updatePlayerMarkerVisualStates();
    }

    updateResourceList() {
        const list = document.getElementById('resource-list');
        if (!list) {
            return;
        }
        if (this.resourceMarkers.length === 0) {
            list.innerHTML = `<li class="empty-state">${this.escape(this.t('noResourceDistricts'))}</li>`;
            return;
        }

        list.innerHTML = this.resourceMarkers.map(resource => {
            const meta = resource.data.metadata || {};
            const relation = this.relationForNation(meta.nationId, meta);
            const relationClass = this.relationClassFor(relation);
            const migrationState = this.formatResourceMigrationState(meta);
            const selectedClass = resource.id === this.selectedResourceId ? ' is-selected' : '';
            const operationState = this.normalizeResourceActionState(meta);
            const inFocusedNation = this.selectedNationId && String(meta.nationId || '') === String(this.selectedNationId);
            const emphasizedByFilter = inFocusedNation
                && this.nationPriorityFilter !== 'all'
                && operationState === this.nationPriorityFilter;
            const emphasizedClass = emphasizedByFilter ? ' is-emphasized' : '';
            const searchText = [
                meta.nation || resource.label,
                meta.biome || this.t('unknown'),
                meta.remainingResources || '0',
                meta.totalExperience || '0',
                migrationState,
                meta.pendingTarget || '',
            ].join(' ');
            const metaLines = [
                `${this.escape(this.formatBiomeName(meta.biome))} · ${this.escape(meta.remainingResources || '0')} ${this.escape(this.t('resourcesLabel'))} · XP ${this.escape(meta.totalExperience || '0')}`,
                `${this.escape(this.t('migrationLabel'))}: ${this.escape(migrationState)}${meta.pendingTarget ? ` · ${this.escape(this.t('targetLabel'))}: ${this.escape(meta.pendingTarget)}` : ''}`,
            ];
            const forecast = this.resourceForecastMetrics(meta);
            if (meta.expectedResourceYield || meta.expectedExperienceYield || meta.refreshCooldownMinutes) {
                metaLines.push(
                    `${this.escape(this.t('cycleResourcesLabel'))}: ${this.escape(meta.expectedResourceYield || '0')} · XP ${this.escape(meta.expectedExperienceYield || '0')} · ${this.escape(this.t('refreshCycleLabel'))}: ${this.escape(this.formatDurationMinutes(meta.refreshCooldownMinutes))}`
                );
            }
            if (forecast.next3Resource > 0 || forecast.next3Experience > 0) {
                metaLines.push(
                    `${this.escape(this.t('nextThreeCyclesLabel'))}: ${this.escape(this.formatWholeNumber(forecast.next3Resource, '0'))} · XP ${this.escape(this.formatWholeNumber(forecast.next3Experience, '0'))}${forecast.next3WindowMinutes > 0 ? ` · ${this.escape(this.t('forecastWindowLabel'))}: ${this.escape(this.formatDurationMinutes(forecast.next3WindowMinutes))}` : ''}`
                );
            }
            if (meta.nextRefreshAt || meta.forceMigrationAt) {
                const timeBits = [];
                if (meta.nextRefreshAt) {
                    timeBits.push(`${this.escape(this.t('refreshLabel'))}: ${this.escape(this.formatTimestamp(meta.nextRefreshAt))}`);
                }
                if (meta.forceMigrationAt) {
                    timeBits.push(`${this.escape(this.t('forcedAtLabel'))}: ${this.escape(this.formatTimestamp(meta.forceMigrationAt))}`);
                }
                metaLines.push(timeBits.join(' · '));
            }
            return `<li class="${`${selectedClass}${emphasizedClass}`.trim()}" data-resource="${this.escape(resource.id)}" data-search-text="${this.escape(searchText)}" data-operation-state="${this.escape(operationState)}">
                        <span class="resource-face ${relationClass}"></span>
                        <span class="item-name">${this.escape(meta.nation || resource.label)}</span>
                        <span class="item-meta-stack">${metaLines.map(line => `<span class="resource-meta-line">${line}</span>`).join('')}</span>
                    </li>`;
        }).join('');
    }

    selectedResource() {
        return this.resourceMarkers.find(resource => resource.id === this.selectedResourceId) || null;
    }

    setSelectedResource(resourceId) {
        this.selectedResourceId = String(resourceId || '');
        this.updateResourceList();
        this.bindResourceListEvents();
        this.updateResourceMarkerVisualStates();
        this.updateNationDetailPanel();
    }

    relationClassFor(relation) {
        switch ((relation || 'neutral').toLowerCase()) {
            case 'member':
            case 'allied':
            case 'friendly':
                return 'is-allied';
            case 'hostile':
            case 'war':
                return 'is-hostile';
            case 'vassal':
                return 'is-vassal';
            default:
                return 'is-neutral';
        }
    }

    formatRelation(relation) {
        switch ((relation || 'neutral').toLowerCase()) {
            case 'member':
                return this.t('member');
            case 'allied':
                return this.t('allied');
            case 'friendly':
                return this.t('friendly');
            case 'hostile':
                return this.t('hostile');
            case 'war':
                return this.t('war');
            case 'vassal':
                return this.t('vassal');
            default:
                return this.t('neutral');
        }
    }

    formatGovernment(government) {
        switch ((government || 'UNKNOWN').toUpperCase()) {
            case 'MONARCHY':
                return this.t('governmentMonarchy');
            case 'REPUBLIC':
                return this.t('governmentRepublic');
            case 'DICTATORSHIP':
                return this.t('governmentDictatorship');
            case 'DEMOCRACY':
                return this.t('governmentDemocracy');
            case 'THEOCRACY':
                return this.t('governmentTheocracy');
            case 'FEDERATION':
                return this.t('governmentFederation');
            case 'EMPIRE':
                return this.t('governmentEmpire');
            case 'OLIGARCHY':
                return this.t('governmentOligarchy');
            case 'COUNCIL':
                return this.t('governmentCouncil');
            default:
                return government || this.t('governmentUnknown');
        }
    }

    formatTimestamp(value) {
        if (!value) {
            return this.t('generatedUnknown');
        }
        try {
            const date = new Date(value);
            if (Number.isNaN(date.getTime())) {
                return this.t('generatedUnknown');
            }
            return date.toLocaleString(this.language === 'zh' ? 'zh-CN' : 'en-US');
        } catch (_ignored) {
            return this.t('generatedUnknown');
        }
    }

    formatBiomeName(biome) {
        if (!biome) {
            return this.t('unknown');
        }
        return String(biome).replaceAll('_', ' ');
    }

    formatResourceMigrationState(meta = {}) {
        switch (String(meta.migrationState || 'none').toLowerCase()) {
            case 'none':
                return this.t('migrationNone');
            case 'awaiting_target':
                return this.t('migrationAwaitingTarget');
            case 'waiting_depletion':
                return this.t('migrationWaitingDepletion');
            default:
                return meta.migrationLabel || this.t('migrationNone');
        }
    }

    formatDurationMinutes(value) {
        const totalMinutes = Number(value || 0);
        if (!Number.isFinite(totalMinutes) || totalMinutes <= 0) {
            return this.language === 'zh' ? '0分钟' : '0m';
        }
        let minutes = Math.round(totalMinutes);
        const days = Math.floor(minutes / 1440);
        minutes -= days * 1440;
        const hours = Math.floor(minutes / 60);
        minutes -= hours * 60;
        const parts = [];
        if (days > 0) {
            parts.push(this.language === 'zh' ? `${days}天` : `${days}d`);
        }
        if (hours > 0) {
            parts.push(this.language === 'zh' ? `${hours}小时` : `${hours}h`);
        }
        if (minutes > 0 || parts.length === 0) {
            parts.push(this.language === 'zh' ? `${minutes}分钟` : `${minutes}m`);
        }
        return parts.join(this.language === 'zh' ? ' ' : ' ');
    }

    updateSidebar() {
        this.updateNationList();
        this.updatePlayerList();
        this.updateResourceList();
        this.updatePlayerMarkerVisualStates();
        this.updateResourceMarkerVisualStates();
        this.updateNationDetailPanel();
        this.setPlayersCollapsed(this.playersCollapsed);
        this.bindNationListEvents();
        this.bindPlayerListEvents();
        this.bindResourceListEvents();
    }

    bindNationListEvents() {
        document.querySelectorAll('li[data-nation]').forEach(li => {
            li.addEventListener('click', () => {
                const nationId = li.dataset.nation;
                if (nationId === this.selectedNationId) {
                    this.setSelectedNation(null, { preserveView: true });
                    return;
                }
                this.setSelectedNation(nationId, { preserveView: true });
                this.flyToNation(nationId);
                this.maybeCollapseSidebarAfterMapAction();
            });
        });
    }

    bindPlayerListEvents() {
        document.querySelectorAll('li[data-player]').forEach(li => {
            li.addEventListener('click', () => {
                const playerId = li.dataset.player;
                this.setSelectedPlayer(playerId);
                this.flyToPlayer(playerId);
                this.maybeCollapseSidebarAfterMapAction();
            });
        });
    }

    bindResourceListEvents() {
        document.querySelectorAll('li[data-resource]').forEach(li => {
            li.addEventListener('click', () => {
                const resourceId = li.dataset.resource;
                this.setSelectedResource(resourceId);
                this.flyToResourceDistrict(resourceId);
                this.maybeCollapseSidebarAfterMapAction();
            });
        });
    }

    flyToNation(nationId) {
        const bounds = this.nationBounds.get(nationId);
        if (bounds && bounds.isValid()) {
            this.hasFittedView = true;
            this.map.flyToBounds(bounds, { padding: [40, 40], maxZoom: 7 });
        }
    }

    boundsForClaims(claims) {
        const bounds = L.latLngBounds([]);
        for (const claim of claims) {
            const x1 = claim.chunkX * MapConfig.chunkSize;
            const z1 = claim.chunkZ * MapConfig.chunkSize;
            const x2 = x1 + MapConfig.chunkSize;
            const z2 = z1 + MapConfig.chunkSize;
            bounds.extend([-z2, x1]);
            bounds.extend([-z1, x2]);
        }
        return bounds;
    }

    flyToPlayer(playerId) {
        const found = this.playerMarkers.find(p => p.id === playerId);
        if (found) {
            this.hasFittedView = true;
            this.map.flyTo(found.marker.getLatLng(), 6);
            found.marker.openTooltip();
        }
    }

    flyToResourceDistrict(resourceId) {
        const found = this.resourceMarkers.find(resource => resource.id === resourceId);
        if (found) {
            this.hasFittedView = true;
            this.map.flyTo(found.marker.getLatLng(), 7);
            found.marker.openTooltip();
        }
    }

    fitToVisibleData(snapshot, territories, markers, resources = []) {
        const bounds = L.latLngBounds([]);
        territories.forEach(t => {
            const x1 = t.chunkX * MapConfig.chunkSize;
            const z1 = t.chunkZ * MapConfig.chunkSize;
            const x2 = x1 + MapConfig.chunkSize;
            const z2 = z1 + MapConfig.chunkSize;
            bounds.extend([-z2, x1]);
            bounds.extend([-z1, x2]);
        });
        markers.forEach(m => bounds.extend([-m.z, m.x]));
        resources.forEach(resource => bounds.extend([-resource.z, resource.x]));
        if (bounds.isValid()) {
            this.map.fitBounds(bounds.pad(0.2));
            return;
        }
        this.fitToTerrainMetadata(snapshot);
    }

    fitToTerrainMetadata(snapshot) {
        const metadata = this.terrainMetadataFor(snapshot);
        if (!metadata) {
            this.map.setView(MapConfig.defaultCenter, MapConfig.defaultZoom);
            return false;
        }
        const spawnX = Number(metadata.spawnX);
        const spawnZ = Number(metadata.spawnZ);
        if (metadata.generated === true && Number.isFinite(spawnX) && Number.isFinite(spawnZ)) {
            this.map.setView([-spawnZ, spawnX], MapConfig.defaultZoom);
            return true;
        }
        const minX = Number(metadata.minX);
        const minZ = Number(metadata.minZ);
        const maxX = Number(metadata.maxX);
        const maxZ = Number(metadata.maxZ);
        if ([minX, minZ, maxX, maxZ].every(Number.isFinite) && maxX > minX && maxZ > minZ) {
            const width = maxX - minX;
            const depth = maxZ - minZ;
            const focusSize = Math.min(Math.max(width, depth), 1024);
            const centerX = Number.isFinite(Number(metadata.spawnX)) ? Number(metadata.spawnX) : (minX + maxX) / 2;
            const centerZ = Number.isFinite(Number(metadata.spawnZ)) ? Number(metadata.spawnZ) : (minZ + maxZ) / 2;
            const half = Math.max(128, focusSize / 2);
            const focusedBounds = L.latLngBounds([
                [-(centerZ + half), centerX - half],
                [-(centerZ - half), centerX + half],
            ]);
            this.map.fitBounds(focusedBounds, { maxZoom: 5 });
            return true;
        }
        if (Number.isFinite(spawnX) && Number.isFinite(spawnZ)) {
            this.map.setView([-spawnZ, spawnX], MapConfig.defaultZoom);
            return true;
        }
        return false;
    }

    terrainMetadataFor(snapshot) {
        const worlds = ((snapshot || {}).terrain || {}).worlds || {};
        const worldName = this.terrainWorldFor(snapshot || {});
        return worlds[worldName] || null;
    }

    playReveal(options = true) {
        const allowPulse = typeof options === 'object' ? options.allowPulse !== false : options !== false;
        if (typeof window.gsap === 'undefined') {
            document.body.classList.add('page-ready');
            this.setSidebarCollapsed(this.sidebarCollapsed, { animate: false });
            this.refreshMapViewport();
            return;
        }

        if (!this.pageAnimationPlayed) {
            document.body.classList.add('page-ready');
            this.setSidebarCollapsed(this.sidebarCollapsed, { animate: false });
            const tl = window.gsap.timeline({
                defaults: { duration: 0.78, ease: 'power3.out' },
            });

            tl.addLabel('boot', 0)
                .to('#map-shell', { opacity: 1, y: 0 }, 'boot+=0.08')
                .to('.reveal-panel', { opacity: 1, y: 0, stagger: 0.08, duration: 0.6 }, 'boot+=0.26')
                .to('.reveal-footer', { opacity: 1, y: 0, duration: 0.52 }, 'boot+=0.44')
                .to('.reveal-map-stage', { opacity: 1, y: 0, scale: 1, filter: 'blur(0px)', duration: 1.05 }, 'boot+=0.14')
                .to('.reveal-floating', { opacity: 1, y: 0, stagger: 0.06, duration: 0.5 }, 'boot+=0.5')
                .add(() => this.refreshMapViewport(), '>-0.2')
                .add(() => this.playDataPulse(), '>-0.12');

            this.pageAnimationPlayed = true;
            return;
        }

        if (allowPulse) {
            this.playDataPulse();
        }
    }

    refreshMapViewport() {
        window.requestAnimationFrame(() => {
            if (!this.map) {
                return;
            }
            this.map.invalidateSize();
            if (this.terrainLayer) {
                this.terrainLayer.redraw();
            }
        });
    }

    playDataPulse() {
        if (typeof window.gsap === 'undefined' || this.dataRevealQueued) {
            return;
        }
        const summaryCards = Array.from(document.querySelectorAll('.summary-card, .intel-card'));
        const listItems = Array.from(document.querySelectorAll('#nation-list li, #player-list li, #resource-list li'));
        const playerMarkers = Array.from(document.querySelectorAll('.player-marker-container'));
        if (!summaryCards.length && !listItems.length && !playerMarkers.length) {
            return;
        }
        this.dataRevealQueued = true;
        const tl = window.gsap.timeline({
            defaults: { duration: 0.42, ease: 'power2.out' },
            onComplete: () => {
                this.dataRevealQueued = false;
            },
        });

        if (summaryCards.length) {
            tl.fromTo(summaryCards,
                { opacity: 0.55, y: 10 },
                { opacity: 1, y: 0, stagger: 0.045 },
                0
            );
        }
        if (listItems.length) {
            tl.fromTo(listItems,
                { opacity: 0, y: 10 },
                { opacity: 1, y: 0, stagger: 0.024, duration: 0.34 },
                0.04
            );
        }
        if (playerMarkers.length) {
            tl.fromTo(playerMarkers,
                { opacity: 0, scale: 0.86 },
                { opacity: 1, scale: 1, stagger: 0.018, duration: 0.28 },
                0.12
            );
        }
    }

    renderKeyFor(snapshot) {
        const parts = [this.currentWorld, this.selectedNationId || '', this.language, this.showTerritoryLayer ? 't1' : 't0', this.showPlayerLayer ? 'p1' : 'p0'];
        parts.push(JSON.stringify(snapshot.diplomacy || {}));
        parts.push(JSON.stringify(snapshot.terrain || {}));
        parts.push(JSON.stringify(snapshot.viewer || {}));
        for (const layer of snapshot.layers || []) {
            parts.push(layer.type || '');
            for (const territory of layer.territories || []) {
                const territoryMeta = territory.metadata || {};
                parts.push([
                    territory.ownerId || '',
                    territory.world || '',
                    territory.chunkX ?? '',
                    territory.chunkZ ?? '',
                    territory.fillColor || '',
                    territoryMeta.relation || '',
                    territoryMeta.recentEventCount || '',
                    territoryMeta.recentEvent0Type || '',
                    territoryMeta.recentEvent0At || '',
                    territoryMeta.recentEvent0Message || '',
                    territoryMeta.officerRoleResourceMigration || '',
                    territoryMeta.officerRoleTreasuryWithdraw || '',
                    territoryMeta.officerRoleDiplomacySet || '',
                    territoryMeta.officerRoleWarDeclare || '',
                    territoryMeta.officerRoleWarEnd || '',
                    territoryMeta.officerRolePolicySet || '',
                    territoryMeta.officerRolePolicyClear || '',
                    territoryMeta.officerRoleTechnologyUnlock || '',
                    territoryMeta.officerRoleTechnologyRevoke || '',
                    territoryMeta.viewerOfficerNationScope || '',
                    territoryMeta.viewerOfficerStatusResourceMigration || '',
                    territoryMeta.viewerOfficerStatusTreasuryWithdraw || '',
                    territoryMeta.viewerOfficerStatusDiplomacySet || '',
                    territoryMeta.viewerOfficerStatusWarDeclare || '',
                    territoryMeta.viewerOfficerStatusWarEnd || '',
                    territoryMeta.viewerOfficerStatusPolicySet || '',
                    territoryMeta.viewerOfficerStatusPolicyClear || '',
                    territoryMeta.viewerOfficerStatusTechnologyUnlock || '',
                    territoryMeta.viewerOfficerStatusTechnologyRevoke || '',
                    territoryMeta.viewerCanOfficerResourceMigration || '',
                    territoryMeta.viewerCanOfficerTreasuryWithdraw || '',
                    territoryMeta.viewerCanOfficerDiplomacySet || '',
                    territoryMeta.viewerCanOfficerWarDeclare || '',
                    territoryMeta.viewerCanOfficerWarEnd || '',
                    territoryMeta.viewerCanOfficerPolicySet || '',
                    territoryMeta.viewerCanOfficerPolicyClear || '',
                    territoryMeta.viewerCanOfficerTechnologyUnlock || '',
                    territoryMeta.viewerCanOfficerTechnologyRevoke || '',
                ].join(':'));
            }
            for (const marker of layer.markers || []) {
                const metadata = marker.metadata || {};
                parts.push([
                    marker.id || '',
                    marker.world || '',
                    Math.round(marker.x || 0),
                    Math.round(marker.z || 0),
                    metadata.nationId || '',
                    metadata.relation || '',
                    metadata.migrationState || '',
                    metadata.pendingTarget || '',
                    metadata.remainingResources || '',
                    metadata.totalExperience || '',
                    metadata.expectedResourceYield || '',
                    metadata.expectedExperienceYield || '',
                    metadata.refreshCooldownMinutes || '',
                    metadata.expectedResourceYieldPerHour || '',
                    metadata.expectedExperienceYieldPerHour || '',
                    metadata.forecastResourceYieldNext3Cycles || '',
                    metadata.forecastExperienceYieldNext3Cycles || '',
                    metadata.forecastWindowMinutesNext3Cycles || '',
                    metadata.forceMigrationAt || '',
                ].join(':'));
            }
        }
        return parts.join('|');
    }

    generateColor(id) {
        let hash = 0;
        for (let i = 0; i < id.length; i++) {
            hash = id.charCodeAt(i) + ((hash << 5) - hash);
        }
        const r = (hash >> 16) & 0xFF;
        const g = (hash >> 8) & 0xFF;
        const b = hash & 0xFF;
        return `#${this.hex(r)}${this.hex(g)}${this.hex(b)}`;
    }

    hex(n) {
        return Math.min(255, Math.max(0, Math.round(n)))
            .toString(16)
            .padStart(2, '0')
            .toUpperCase();
    }

    loadDemoData() {
        const demo = {
            generatedAt: new Date().toISOString(),
            summary: {
                nationCount: 3,
                claimCount: 6,
                onlinePlayers: 3,
                resourceDistricts: 2,
                worldCount: 2,
            },
            worlds: ['world', 'world_nether'],
            diplomacy: {
                relations: {
                    'nation-001': { 'nation-001': 'member', 'nation-002': 'friendly', 'nation-003': 'war' },
                    'nation-002': { 'nation-001': 'friendly', 'nation-002': 'member', 'nation-003': 'hostile' },
                    'nation-003': { 'nation-001': 'war', 'nation-002': 'hostile', 'nation-003': 'member' },
                },
            },
            layers: [
                {
                    type: 'TERRITORY',
                    territories: [
                        { ownerId: 'nation-001', ownerName: 'Aether Dominion', world: 'world', chunkX: -3, chunkZ: -2, fillColor: '#1E1E1E', metadata: { nationId: 'nation-001', government: 'MONARCHY', claims: '4', displayColor: '#1E1E1E', memberCount: '8' } },
                        { ownerId: 'nation-001', ownerName: 'Aether Dominion', world: 'world', chunkX: -2, chunkZ: -2, fillColor: '#1E1E1E', metadata: { nationId: 'nation-001', government: 'MONARCHY', claims: '4', displayColor: '#1E1E1E', memberCount: '8' } },
                        { ownerId: 'nation-002', ownerName: 'Verdant Republic', world: 'world', chunkX: 3, chunkZ: 1, fillColor: '#406B43', metadata: { nationId: 'nation-002', government: 'REPUBLIC', claims: '3', displayColor: '#406B43', memberCount: '11' } },
                        { ownerId: 'nation-003', ownerName: 'Obsidian Compact', world: 'world_nether', chunkX: 1, chunkZ: -1, fillColor: '#8B5E3C', metadata: { nationId: 'nation-003', government: 'DICTATORSHIP', claims: '2', displayColor: '#8B5E3C', memberCount: '5' } },
                    ],
                    markers: [],
                },
                {
                    type: 'PLAYER_MARKERS',
                    territories: [],
                    markers: [
                        { id: 'player:a1', label: 'Emperor_X', world: 'world', x: -40, z: -24, icon: 'allied-avatar', metadata: { player: 'Emperor_X', playerId: '8667ba71-b85a-4004-af54-457a9734eed7', world: 'world', nation: 'Aether Dominion', nationId: 'nation-001', government: 'MONARCHY', relation: 'member', avatarHint: 'EX', avatarUrl: `${MapConfig.avatarUrl}8667ba71-b85a-4004-af54-457a9734eed7`, displayColor: '#1E1E1E' } },
                        { id: 'player:b2', label: 'GreenLeader', world: 'world', x: 56, z: 24, icon: 'allied-avatar', metadata: { player: 'GreenLeader', playerId: 'ec70bcaf-702f-4bb8-b48d-276fa52a780c', world: 'world', nation: 'Verdant Republic', nationId: 'nation-002', government: 'REPUBLIC', relation: 'member', avatarHint: 'GL', avatarUrl: `${MapConfig.avatarUrl}ec70bcaf-702f-4bb8-b48d-276fa52a780c`, displayColor: '#406B43' } },
                        { id: 'player:c3', label: 'Wanderer99', world: 'world_nether', x: 18, z: -12, icon: 'player', metadata: { player: 'Wanderer99', playerId: '069a79f4-44e9-4726-a5be-fca90e38aaf5', world: 'world_nether', relation: 'neutral', avatarHint: 'W9', avatarUrl: `${MapConfig.avatarUrl}069a79f4-44e9-4726-a5be-fca90e38aaf5` } },
                    ],
                },
                {
                    type: 'RESOURCE_DISTRICTS',
                    territories: [],
                    markers: [
                        { id: 'resource:r1', label: 'Aether Dominion 资源区块', world: 'world', x: -32, z: -24, icon: 'resource-district', metadata: { nation: 'Aether Dominion', nationId: 'nation-001', nationKind: 'nation', founderName: 'Emperor_X', government: 'MONARCHY', nationLevel: '4', nationExperience: '920', nationExperienceProgress: '920', nationNextLevelExperience: '1750', nationExperienceRemaining: '830', nationMaxLevelReached: 'false', claimCount: '4', claimLimit: '20', cityStateCount: '1', cityStateLimit: '3', resourceDistrictCount: '1', resourceDistrictLimit: '1', memberCount: '8', displayColor: '#1E1E1E', relation: 'member', biome: 'forest', richness: '1.15', remainingResources: '28', totalExperience: '420', expectedResourceYield: '37', expectedExperienceYield: '138', refreshCooldownMinutes: '52', expectedResourceYieldPerHour: '42.69', expectedExperienceYieldPerHour: '159.23', forecastResourceYieldNext3Cycles: '111', forecastExperienceYieldNext3Cycles: '414', forecastWindowMinutesNext3Cycles: '156', migrationState: 'none', migrationLabel: '无', beaconPosition: '-24, 69, -24', nextRefreshAt: '2026-06-06T12:00:00Z' } },
                        { id: 'resource:r2', label: 'Verdant Republic 资源区块', world: 'world', x: 56, z: 24, icon: 'resource-district', metadata: { nation: 'Verdant Republic', nationId: 'nation-002', nationKind: 'nation', founderName: 'GreenLeader', government: 'REPUBLIC', nationLevel: '6', nationExperience: '1480', nationExperienceProgress: '230', nationNextLevelExperience: '2250', nationExperienceRemaining: '2020', nationMaxLevelReached: 'false', claimCount: '3', claimLimit: '28', cityStateCount: '2', cityStateLimit: '3', resourceDistrictCount: '1', resourceDistrictLimit: '1', memberCount: '11', displayColor: '#406B43', relation: 'friendly', biome: 'plains', richness: '0.95', remainingResources: '19', totalExperience: '260', expectedResourceYield: '30', expectedExperienceYield: '114', refreshCooldownMinutes: '63', expectedResourceYieldPerHour: '28.57', expectedExperienceYieldPerHour: '108.57', forecastResourceYieldNext3Cycles: '90', forecastExperienceYieldNext3Cycles: '342', forecastWindowMinutesNext3Cycles: '189', migrationState: 'waiting_depletion', migrationLabel: '等待旧资源枯竭，目标 world:5:2', pendingTarget: 'world:5:2', beaconPosition: '56, 72, 24', nextRefreshAt: '2026-06-06T13:30:00Z', forceMigrationAt: '2026-06-06T15:30:00Z' } },
                    ],
                },
            ],
        };

        this.health = {
            sseClients: 0,
            avatarCacheEnabled: true,
            onlinePlayers: 3,
        };
        this._localSnapshotAvailable = true;
        this._demoDataLoaded = true;
        this.snapshot = demo;
        this.renderSnapshot(demo, { forceRender: true, allowPulse: false });
    }
}

const bootStrategicMap = () => {
    if (window.location.protocol === 'file:') {
        document.body.classList.add('page-ready');
        window.setTimeout(() => {
            window.location.href = MapConfig.liveMapUrl;
        }, 700);
        return;
    }
    if (typeof window.L === 'undefined') {
        document.body.classList.add('page-ready');
        return;
    }
    window.strategicMap = new StrategicMap();
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootStrategicMap);
} else {
    bootStrategicMap();
}
