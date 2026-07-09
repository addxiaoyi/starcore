package dev.starcore.starcore.module.nation.tutorial;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * 国家教程配置加载器
 * 从 nation-tutorial.yml 加载教程内容
 */
public class NationTutorialConfig {

    private final Plugin plugin;
    private final File configFile;
    private YamlConfiguration config;

    // 缓存解析后的教程
    private List<TutorialContent> tutorials;

    public NationTutorialConfig(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "nation-tutorial.yml");
        this.config = loadConfig();
        reload();
    }

    /**
     * 加载或创建配置文件
     */
    private YamlConfiguration loadConfig() {
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream is = plugin.getResource("nation-tutorial.yml")) {
                if (is != null) {
                    Files.copy(is, configFile.toPath());
                } else {
                    // 创建默认配置
                    createDefaultConfig();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract nation-tutorial.yml: " + e.getMessage());
            }
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 创建默认教程配置
     */
    private void createDefaultConfig() {
        config = new YamlConfiguration();

        // 新手教程
        config.set("tutorials.beginner.enabled", true);
        config.set("tutorials.beginner.title", "新手入门");
        config.set("tutorials.beginner.description", "了解国家系统的基础功能");
        config.set("tutorials.beginner.trigger-first-login", true);

        // 步骤1
        config.set("tutorials.beginner.steps.step1.title", "欢迎来到国家系统！");
        config.set("tutorials.beginner.steps.step1.content",
            Arrays.asList(
                "欢迎加入 StarCore 国家系统！",
                "在这里你可以创建或加入国家，",
                "与其他玩家一起管理和扩展你的王国。",
                "",
                "让我们开始了解基本功能吧！"
            ));
        config.set("tutorials.beginner.steps.step1.hint", "点击任意位置继续...");
        config.set("tutorials.beginner.steps.step1.command", "/sc nation gui");

        // 步骤2
        config.set("tutorials.beginner.steps.step2.title", "创建或加入国家");
        config.set("tutorials.beginner.steps.step2.content",
            Arrays.asList(
                "你有两种选择：",
                "",
                "1. 创建自己的国家",
                "   消耗 500 星尘即可创建",
                "   使用指令: /sc nation create <名称>",
                "",
                "2. 加入现有国家",
                "   使用指令: /sc nation list 查看所有国家",
                "   然后申请加入你喜欢的国家"
            ));
        config.set("tutorials.beginner.steps.step2.hint", "准备好后继续下一步...");
        config.set("tutorials.beginner.steps.step2.command", "/sc nation list");

        // 步骤3
        config.set("tutorials.beginner.steps.step3.title", "国家核心功能");
        config.set("tutorials.beginner.steps.step3.content",
            Arrays.asList(
                "加入国家后，你可以：",
                "",
                "- 查看和管理成员",
                "- 管理和使用国库资金",
                "- 占领和扩展领土",
                "- 研发科技提升实力",
                "- 制定国家政策和外交关系"
            ));
        config.set("tutorials.beginner.steps.step3.hint", "继续了解更多信息...");
        config.set("tutorials.beginner.steps.step3.command", "");

        // 步骤4
        config.set("tutorials.beginner.steps.step4.title", "政体系统");
        config.set("tutorials.beginner.steps.step4.content",
            Arrays.asList(
                "国家有不同的政体类型：",
                "",
                "君主制 - 君主拥有最高决策权",
                "共和制 - 议会投票决策",
                "独裁制 - 快速决策，高效执行",
                "民主制 - 所有成员参与决策"
            ));
        config.set("tutorials.beginner.steps.step4.hint", "了解税率系统...");
        config.set("tutorials.beginner.steps.step4.command", "/sc government");

        // 步骤5
        config.set("tutorials.beginner.steps.step5.title", "税率与国库");
        config.set("tutorials.beginner.steps.step5.content",
            Arrays.asList(
                "国家会从成员收取税款：",
                "",
                "税率由管理员设置 (0-50%)",
                "税款自动存入国库",
                "国库用于：购买领土、科技研发等",
                "",
                "建议：初期设置低税率吸引成员"
            ));
        config.set("tutorials.beginner.steps.step5.hint", "了解领土系统...");
        config.set("tutorials.beginner.steps.step5.command", "/sc treasury");

        // 步骤6
        config.set("tutorials.beginner.steps.step6.title", "领土系统");
        config.set("tutorials.beginner.steps.step6.content",
            Arrays.asList(
                "领土是国家的重要组成部分：",
                "",
                "- 使用领地工具选择区域",
                "- 从国库支付购买费用",
                "- 领土提供资源采集加成",
                "- 保护成员免受入侵"
            ));
        config.set("tutorials.beginner.steps.step6.hint", "继续了解外交...");
        config.set("tutorials.beginner.steps.step6.command", "/sc claim");

        // 步骤7
        config.set("tutorials.beginner.steps.step7.title", "外交与战争");
        config.set("tutorials.beginner.steps.step7.content",
            Arrays.asList(
                "与其他国家的互动：",
                "",
                "- 联盟 - 互相帮助，共同防御",
                "- 停战 - 暂时休战",
                "- 宣战 - 发动战争掠夺资源",
                "- 吞并 - 战胜后可吞并敌国"
            ));
        config.set("tutorials.beginner.steps.step7.hint", "教程即将完成...");
        config.set("tutorials.beginner.steps.step7.command", "/sc diplomacy");

        // 步骤8 - 完成
        config.set("tutorials.beginner.steps.step8.title", "教程完成！");
        config.set("tutorials.beginner.steps.step8.content",
            Arrays.asList(
                "恭喜你完成了新手教程！",
                "",
                "你现在可以：",
                "1. 使用 /sc nation gui 打开国家菜单",
                "2. 邀请朋友加入你的国家",
                "3. 开始扩展领土和发展科技",
                "",
                "祝你游戏愉快！"
            ));
        config.set("tutorials.beginner.steps.step8.hint", "点击关闭教程气泡");
        config.set("tutorials.beginner.steps.step8.command", "/sc nation gui");

        // 访客教程
        config.set("tutorials.visitor.enabled", true);
        config.set("tutorials.visitor.title", "访客指南");
        config.set("tutorials.visitor.description", "为未加入国家的玩家提供指导");
        config.set("tutorials.visitor.trigger-no-nation", true);

        config.set("tutorials.visitor.steps.step1.title", "你还没有加入国家");
        config.set("tutorials.visitor.steps.step1.content",
            Arrays.asList(
                "欢迎来到 StarCore 世界！",
                "",
                "要参与更多玩法，你需要加入一个国家，",
                "或者创建自己的国家！"
            ));
        config.set("tutorials.visitor.steps.step1.hint", "查看创建国家选项...");
        config.set("tutorials.visitor.steps.step1.command", "/sc nation gui");

        config.set("tutorials.visitor.steps.step2.title", "快速开始");
        config.set("tutorials.visitor.steps.step2.content",
            Arrays.asList(
                "创建国家仅需 500 星尘：",
                "",
                "1. 输入 /sc nation create <国家名>",
                "2. 等待国家创建完成",
                "3. 使用 /sc nation gui 管理国家"
            ));
        config.set("tutorials.visitor.steps.step2.hint", "查看加入国家选项...");
        config.set("tutorials.visitor.steps.step2.command", "/sc nation create");

        config.set("tutorials.visitor.steps.step3.title", "加入现有国家");
        config.set("tutorials.visitor.steps.step3.content",
            Arrays.asList(
                "也可以加入已有国家：",
                "",
                "1. 输入 /sc nation list 查看所有国家",
                "2. 选择你喜欢的国家",
                "3. 发送加入申请",
                "4. 等待管理员批准"
            ));
        config.set("tutorials.visitor.steps.step3.hint", "教程完成！");
        config.set("tutorials.visitor.steps.step3.command", "/sc nation list");

        // 管理员教程
        config.set("tutorials.admin.enabled", true);
        config.set("tutorials.admin.title", "管理员指南");
        config.set("tutorials.admin.description", "帮助管理员更好地管理国家");
        config.set("tutorials.admin.trigger-admin-rank", true);

        config.set("tutorials.admin.steps.step1.title", "管理员权限");
        config.set("tutorials.admin.steps.step1.content",
            Arrays.asList(
                "作为管理员，你可以：",
                "",
                "- 修改国家名称",
                "- 设置税率",
                "- 变更政体",
                "- 管理成员权限",
                "- 邀请或踢出成员"
            ));
        config.set("tutorials.admin.steps.step1.hint", "继续了解...");
        config.set("tutorials.admin.steps.step1.command", "");

        config.set("tutorials.admin.steps.step2.title", "政体选择建议");
        config.set("tutorials.admin.steps.step2.content",
            Arrays.asList(
                "不同政体适合不同情况：",
                "",
                "君主制 - 适合小型、紧密的团队",
                "独裁制 - 适合军事化管理",
                "共和制 - 适合中等规模国家",
                "民主制 - 适合大型社区"
            ));
        config.set("tutorials.admin.steps.step2.hint", "继续...");
        config.set("tutorials.admin.steps.step2.command", "/sc government");

        config.set("tutorials.admin.steps.step3.title", "税率设置建议");
        config.set("tutorials.admin.steps.step3.content",
            Arrays.asList(
                "税率设置平衡指南：",
                "",
                "5% - 低税率，吸引成员",
                "10% - 中税率，平衡发展",
                "15% - 高税率，快速积累",
                "20%+ - 危险，可能流失成员"
            ));
        config.set("tutorials.admin.steps.step3.hint", "完成管理员教程");
        config.set("tutorials.admin.steps.step3.command", "/sc treasury");

        // 保存默认配置
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save default tutorial config: " + e.getMessage());
        }
    }

    /**
     * 重载配置
     */
    public void reload() {
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
        parseTutorials();
    }

    /**
     * 解析所有教程
     */
    private void parseTutorials() {
        tutorials = new ArrayList<>();

        if (!config.isConfigurationSection("tutorials")) {
            return;
        }

        for (String key : config.getConfigurationSection("tutorials").getKeys(false)) {
            TutorialContent tutorial = parseTutorial(key);
            if (tutorial != null) {
                tutorials.add(tutorial);
            }
        }
    }

    /**
     * 解析单个教程
     */
    private TutorialContent parseTutorial(String id) {
        String basePath = "tutorials." + id;

        if (!config.getBoolean(basePath + ".enabled", true)) {
            return null;
        }

        String title = config.getString(basePath + ".title", id);
        String description = config.getString(basePath + ".description", "");
        boolean triggerFirstLogin = config.getBoolean(basePath + ".trigger-first-login", false);
        boolean triggerNoNation = config.getBoolean(basePath + ".trigger-no-nation", false);
        boolean triggerAdminRank = config.getBoolean(basePath + ".trigger-admin-rank", false);

        List<TutorialStep> steps = new ArrayList<>();
        if (config.isConfigurationSection(basePath + ".steps")) {
            for (String stepKey : config.getConfigurationSection(basePath + ".steps").getKeys(false)) {
                TutorialStep step = parseStep(basePath + ".steps." + stepKey, stepKey, steps.size() + 1);
                if (step != null) {
                    steps.add(step);
                }
            }
        }

        return new TutorialContent(id, title, description, steps, triggerFirstLogin, triggerNoNation, triggerAdminRank);
    }

    /**
     * 解析教程步骤
     */
    private TutorialStep parseStep(String path, String stepId, int stepNumber) {
        String title = config.getString(path + ".title", "Step " + stepNumber);
        List<String> content = config.getStringList(path + ".content");
        String hint = config.getString(path + ".hint", "");
        String command = config.getString(path + ".command", "");

        return new TutorialStep(stepId, stepNumber, title, content, hint, command);
    }

    // ==================== Getters ====================

    public List<TutorialContent> getTutorials() {
        return tutorials;
    }

    public TutorialContent getTutorial(String id) {
        return tutorials.stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    public TutorialContent getBeginnerTutorial() {
        return getTutorial("beginner");
    }

    public TutorialContent getVisitorTutorial() {
        return getTutorial("visitor");
    }

    public TutorialContent getAdminTutorial() {
        return getTutorial("admin");
    }

    public TutorialContent getTutorialForPlayer(boolean hasNation, boolean isAdmin) {
        if (isAdmin) {
            TutorialContent admin = getAdminTutorial();
            if (admin != null && admin.enabled()) return admin;
        }
        if (!hasNation) {
            TutorialContent visitor = getVisitorTutorial();
            if (visitor != null && visitor.enabled()) return visitor;
        }
        return getBeginnerTutorial();
    }

    /**
     * 教程内容记录
     */
    public record TutorialContent(
        String id,
        String title,
        String description,
        List<TutorialStep> steps,
        boolean triggerFirstLogin,
        boolean triggerNoNation,
        boolean triggerAdminRank
    ) {
        public boolean enabled() {
            return steps != null && !steps.isEmpty();
        }

        public List<NationTutorialBubble.TutorialStep> toBubbleSteps() {
            if (steps == null) return Collections.emptyList();
            int total = steps.size();
            return steps.stream()
                .map(s -> new NationTutorialBubble.TutorialStep(
                    s.id(),
                    s.stepNumber(),
                    total,
                    s.title(),
                    s.content(),
                    s.hint(),
                    s.command()
                ))
                .toList();
        }
    }

    /**
     * 教程步骤记录
     */
    public record TutorialStep(
        String id,
        int stepNumber,
        String title,
        List<String> content,
        String hint,
        String command
    ) {}
}
