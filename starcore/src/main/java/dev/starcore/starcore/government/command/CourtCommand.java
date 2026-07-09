package dev.starcore.starcore.government.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.government.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 法庭命令
 * 命令：/court <file|appoint|assign|jury|verdict|appeal>
 */
public class CourtCommand implements CommandExecutor, TabCompleter {

    private final CourtService courtService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final CourtExecutionService executionService;

    public CourtCommand(
            CourtService courtService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory,
            CourtExecutionService executionService
    ) {
        this.courtService = courtService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.executionService = executionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleHelp(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "file" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /court file <被告> <类型> <描述...>");
                    return true;
                }
                return handleFile(player, args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            }
            case "appoint" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /court appoint <玩家> [任期天数]");
                    return true;
                }
                return handleAppoint(player, args[1], args.length > 2 ? args[2] : null);
            }
            case "list" -> {
                return handleList(player);
            }
            case "case" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /court case <case_id> [info|assign|schedule|verdict]");
                    return true;
                }
                return handleCase(player, Arrays.copyOfRange(args, 1, args.length));
            }
            case "jury" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /court jury <case_id> [select|vote|info]");
                    return true;
                }
                return handleJury(player, args[1], args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0]);
            }
            case "verdict" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /court verdict <case_id> <类型> [理由]");
                    player.sendMessage("§e附加选项: -fine <金额> | -jail <分钟> | -banish");
                    player.sendMessage("§7示例: /court verdict 1 GUILTY 罪名成立 -fine 1000 -jail 60");
                    return true;
                }
                return handleVerdict(player, Arrays.copyOfRange(args, 1, args.length));
            }
            case "appeal" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /court appeal <case_id|appeal_id> <理由...|审查结果>");
                    player.sendMessage("§7提交上诉: /court appeal <case_id> <理由>");
                    player.sendMessage("§7审查上诉: /court appeal <appeal_id> <upheld|reversed|remanded|dismissed> [理由]");
                    return true;
                }
                // 根据参数判断是提交上诉还是审查上诉
                int firstArg;
                try {
                    firstArg = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的参数");
                    return true;
                }
                // 检查是否是上诉ID（已有判决的案件）还是新案件
                Optional<CourtCase> caseOpt = courtService.getCase(firstArg);
                if (caseOpt.isPresent() && caseOpt.get().getVerdictId().isEmpty()) {
                    // 新案件，需要先上诉
                    return handleAppeal(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                } else {
                    // 可能是上诉ID
                    Optional<Appeal> appealOpt = courtService.getAppeal(firstArg);
                    if (appealOpt.isPresent()) {
                        // 审查上诉
                        return handleAppealReview(player, Arrays.copyOfRange(args, 1, args.length));
                    } else if (caseOpt.isPresent()) {
                        // 有判决的案件，上诉
                        return handleAppeal(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                    } else {
                        player.sendMessage("§c找不到案件或上诉: " + firstArg);
                        return true;
                    }
                }
            }
            case "my" -> {
                return handleMyCases(player);
            }
            case "judges" -> {
                return handleListJudges(player);
            }
            case "history" -> {
                return handleHistory(player, Arrays.copyOfRange(args, 1, args.length));
            }
            case "execute" -> {
                return handleExecute(player, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                return handleHelp(player);
            }
        }
    }

    private boolean handleHelp(Player player) {
        player.sendMessage("§6§l==== 法庭命令帮助 ====");
        // 分隔
        player.sendMessage("§e案件管理:");
        player.sendMessage("§e/court file <被告> <类型> <描述> §7- 提起诉讼");
        player.sendMessage("§e/court list §7- 查看所有案件");
        player.sendMessage("§e/court case <id> info §7- 查看案件详情");
        player.sendMessage("§e/court case <id> assign <法官> §7- 分配法官");
        player.sendMessage("§e/court case <id> schedule §7- 安排听证");
        // 分隔
        player.sendMessage("§e判决执行:");
        player.sendMessage("§e/court verdict <id> <类型> [理由] §7- 宣判");
        player.sendMessage("§e  §7附加: §e-fine <金额> -jail <分钟> -banish §7- 设置惩罚");
        player.sendMessage("§e  §7示例: /court verdict 1 GUILTY 罪名成立 -fine 1000 -jail 60");
        player.sendMessage("§e/court execute <verdict_id> §7- 重新执行判决");
        // 分隔
        player.sendMessage("§e上诉:");
        player.sendMessage("§e/court appeal <case_id> <理由> §7- 提交上诉");
        player.sendMessage("§e/court appeal <appeal_id> <结果> §7- 审查上诉");
        player.sendMessage("§e  §7结果: upheld(维持) reversed(改判) remanded(发回) dismissed(驳回)");
        // 分隔
        player.sendMessage("§e陪审团:");
        player.sendMessage("§e/court jury <id> select §7- 抽选陪审团");
        player.sendMessage("§e/court jury <id> vote §7<guilty|notguilty|abstain> §7- 陪审投票");
        // 分隔
        player.sendMessage("§e其他:");
        player.sendMessage("§e/court my §7- 查看我的案件");
        player.sendMessage("§e/court judges §7- 查看法官列表");
        player.sendMessage("§e/court history [玩家] §7- 查看法庭历史");
        player.sendMessage("§e/court appoint <玩家> [任期天数] §7- 任命法官");
        // 分隔
        player.sendMessage("§7案件类型: CRIMINAL(刑事) | CIVIL(民事) | ADMINISTRATIVE(行政)");
        player.sendMessage("§7判决类型: GUILTY(有罪) | NOT_GUILTY(无罪) | DISMISSED(撤诉) | SETTLED(和解)");
        player.sendMessage("§7附加条件: ban:区域 | limit:金额:天数 | probation:天数 | strip:权利 | silence:天数");
        return true;
    }

    private boolean handleFile(Player player, String defendantName, String caseTypeStr, String description) {
        Optional<? extends Player> defendantOpt = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(defendantName))
                .findFirst();

        if (defendantOpt.isEmpty()) {
            player.sendMessage("§c找不到玩家: " + defendantName);
            return true;
        }

        Player defendant = defendantOpt.get();

        if (defendant.equals(player)) {
            player.sendMessage("§c不能对自己提起诉讼");
            return true;
        }

        // 解析案件类型
        CourtCase.CaseType caseType;
        try {
            caseType = CourtCase.CaseType.valueOf(caseTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的案件类型: " + caseTypeStr);
            player.sendMessage("§7有效类型: CRIMINAL | CIVIL | ADMINISTRATIVE | CONSTITUTIONAL");
            return true;
        }

        if (description.isEmpty()) {
            player.sendMessage("§c请提供案件描述");
            return true;
        }

        Optional<CourtCase> caseOpt = courtService.fileCase(
                player.getUniqueId(),
                defendant.getUniqueId(),
                caseType,
                description
        );

        if (caseOpt.isPresent()) {
            CourtCase courtCase = caseOpt.get();
            player.sendMessage("§a案件已提交!");
            player.sendMessage("§7案件ID: §e" + courtCase.getCaseId());
            player.sendMessage("§7类型: " + caseType.name());
            player.sendMessage("§7被告: §f" + defendant.getName());

            // 通知被告
            defendant.sendMessage("§6§l==== 法庭传票 ====");
            defendant.sendMessage("§7原告: §f" + player.getName());
            defendant.sendMessage("§7案件ID: §e" + courtCase.getCaseId());
            defendant.sendMessage("§7类型: " + caseType.name());
            defendant.sendMessage("§7描述: §f" + description);
            // 分隔
            defendant.sendMessage("§e/court case " + courtCase.getCaseId() + " info §7- 查看详情");
        } else {
            player.sendMessage("§c提交案件失败");
        }

        return true;
    }

    private boolean handleAppoint(Player player, String targetName, String termDaysStr) {
        // 只有君主可以任命法官
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage("§c只有Nation君主可以任命法官");
            return true;
        }

        Optional<? extends Player> targetOpt = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(targetName))
                .findFirst();

        if (targetOpt.isEmpty()) {
            player.sendMessage("§c找不到玩家: " + targetName);
            return true;
        }

        Player target = targetOpt.get();
        java.time.Instant termEnds = null;
        if (termDaysStr != null) {
            try {
                int days = Integer.parseInt(termDaysStr);
                termEnds = java.time.Instant.now().plus(days, java.time.temporal.ChronoUnit.DAYS);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的天数: " + termDaysStr);
                return true;
            }
        }

        if (courtService.appointJudge(target.getUniqueId(), java.time.Instant.now(), termEnds)) {
            player.sendMessage("§a已任命 §f" + target.getName() + " §a为法官");
            target.sendMessage("§a你已被任命为法官!");
            if (termEnds != null) {
                target.sendMessage("§7任期至: " + termEnds.toString());
            }
        } else {
            player.sendMessage("§c任命法官失败");
        }

        return true;
    }

    private boolean handleList(Player player) {
        List<CourtCase> pendingCases = courtService.getPendingCases();

        player.sendMessage("§6§l==== 待分配案件 ====");

        if (pendingCases.isEmpty()) {
            player.sendMessage("§7暂无待分配案件");
            return true;
        }

        for (CourtCase courtCase : pendingCases.stream().limit(10).collect(Collectors.toList())) {
            String plaintiff = onlinePlayerDirectory.findPlayerById(courtCase.getPlaintiff())
                    .map(p -> p.getName())
                    .orElse(courtCase.getPlaintiff().toString().substring(0, 8));

            String defendant = onlinePlayerDirectory.findPlayerById(courtCase.getDefendant())
                    .map(p -> p.getName())
                    .orElse(courtCase.getDefendant().toString().substring(0, 8));

            player.sendMessage("§e[" + courtCase.getCaseId() + "] §f" + courtCase.getCaseType().name());
            player.sendMessage("  §7原告: " + plaintiff + " | 被告: " + defendant);
            player.sendMessage("  §7状态: §e" + courtCase.getStatus());
        }

        return true;
    }

    private boolean handleCase(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§c用法: /court case <case_id> [info|assign|schedule]");
            return true;
        }

        int caseId;
        try {
            caseId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的案件ID: " + args[0]);
            return true;
        }

        Optional<CourtCase> caseOpt = courtService.getCase(caseId);
        if (caseOpt.isEmpty()) {
            player.sendMessage("§c找不到案件: " + caseId);
            return true;
        }

        CourtCase courtCase = caseOpt.get();

        if (args.length == 1 || args[1].equalsIgnoreCase("info")) {
            return handleCaseInfo(player, courtCase);
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "assign" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /court case " + caseId + " assign <法官名>");
                    return true;
                }
                return handleCaseAssign(player, courtCase, args[2]);
            }
            case "schedule" -> {
                return handleCaseSchedule(player, courtCase);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                return true;
            }
        }
    }

    private boolean handleCaseInfo(Player player, CourtCase courtCase) {
        String plaintiff = onlinePlayerDirectory.findPlayerById(courtCase.getPlaintiff())
                .map(p -> p.getName())
                .orElse(courtCase.getPlaintiff().toString().substring(0, 8));

        String defendant = onlinePlayerDirectory.findPlayerById(courtCase.getDefendant())
                .map(p -> p.getName())
                .orElse(courtCase.getDefendant().toString().substring(0, 8));

        player.sendMessage("§6§l==== 案件详情 ====");
        player.sendMessage("§7案件ID: §f" + courtCase.getCaseId());
        player.sendMessage("§7类型: §f" + courtCase.getCaseType());
        player.sendMessage("§7原告: §f" + plaintiff);
        player.sendMessage("§7被告: §f" + defendant);
        player.sendMessage("§7状态: §f" + courtCase.getStatus());
        player.sendMessage("§7提交时间: §f" + courtCase.getFiledAt().toString());
        // 分隔
        player.sendMessage("§7描述:");
        player.sendMessage("  §f" + courtCase.getDescription());

        if (courtCase.getAssignedJudge().isPresent()) {
            UUID judgeId = courtCase.getAssignedJudge().get();
            String judge = onlinePlayerDirectory.findPlayerById(judgeId)
                    .map(p -> p.getName())
                    .orElse(judgeId.toString().substring(0, 8));
            // 分隔
            player.sendMessage("§7负责法官: §f" + judge);
        }

        if (courtCase.getHearingDate().isPresent()) {
            player.sendMessage("§7听证时间: §f" + courtCase.getHearingDate().get().toString());
        }

        if (!courtCase.getJury().isEmpty()) {
            player.sendMessage("§7陪审团: §f" + courtCase.getJury().size() + " 人");
        }

        courtCase.getVerdictId().ifPresent(verdictId -> {
            player.sendMessage("§7判决ID: §e" + verdictId);
        });

        return true;
    }

    private boolean handleCaseAssign(Player player, CourtCase courtCase, String judgeName) {
        Optional<? extends Player> judgeOpt = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(judgeName))
                .findFirst();

        if (judgeOpt.isEmpty()) {
            player.sendMessage("§c找不到玩家: " + judgeName);
            return true;
        }

        Player judge = judgeOpt.get();

        // 检查是否是法官
        Optional<Judge> judgeRecordOpt = courtService.getJudge(judge.getUniqueId());
        if (judgeRecordOpt.isEmpty() || !judgeRecordOpt.get().isActive()) {
            player.sendMessage("§c" + judge.getName() + " 不是现任法官");
            return true;
        }

        if (courtService.assignJudgeToCase(courtCase.getCaseId(), judge.getUniqueId())) {
            player.sendMessage("§a已将案件分配给 §f" + judge.getName());
            judge.sendMessage("§6§l==== 新案件指派 ====");
            judge.sendMessage("§7案件ID: §e" + courtCase.getCaseId());
            judge.sendMessage("§7类型: " + courtCase.getCaseType().name());
            // 分隔
            judge.sendMessage("§7使用 §e/court verdict " + courtCase.getCaseId() + " <类型> [理由] §7宣判");
        } else {
            player.sendMessage("§c分配法官失败");
        }

        return true;
    }

    private boolean handleCaseSchedule(Player player, CourtCase courtCase) {
        if (courtCase.getAssignedJudge().isEmpty()) {
            player.sendMessage("§c请先分配法官");
            return true;
        }

        // 安排3天后听证
        java.time.Instant hearingDate = java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);

        if (courtService.scheduleHearing(courtCase.getCaseId(), hearingDate)) {
            player.sendMessage("§a已安排听证时间: " + hearingDate.toString());
        } else {
            player.sendMessage("§c安排听证失败");
        }

        return true;
    }

    private boolean handleJury(Player player, String caseIdStr, String[] args) {
        int caseId;
        try {
            caseId = Integer.parseInt(caseIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的案件ID: " + caseIdStr);
            return true;
        }

        Optional<CourtCase> caseOpt = courtService.getCase(caseId);
        if (caseOpt.isEmpty()) {
            player.sendMessage("§c找不到案件: " + caseId);
            return true;
        }

        CourtCase courtCase = caseOpt.get();

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            // 显示陪审团信息
            Optional<Jury> juryOpt = courtService.getJury(caseId);

            player.sendMessage("§6§l==== 陪审团信息 ====");
            player.sendMessage("§7案件ID: §f" + caseId);

            if (juryOpt.isEmpty() || juryOpt.get().getJurors().isEmpty()) {
                player.sendMessage("§7陪审团: §c未抽选");
                player.sendMessage("§e使用 §e/court jury " + caseId + " select §7抽选陪审团");
                return true;
            }

            Jury jury = juryOpt.get();
            player.sendMessage("§7陪审员数: §f" + jury.getJurors().size());

            for (UUID jurorId : jury.getJurors()) {
                String juror = onlinePlayerDirectory.findPlayerById(jurorId)
                        .map(p -> p.getName())
                        .orElse(jurorId.toString().substring(0, 8));

                String vote = jury.getVote(jurorId)
                        .map(v -> " §7[投票: " + v.name() + "]")
                        .orElse(" §e[未投票]");

                String tag = jurorId.equals(player.getUniqueId()) ? " §a[你]" : "";
                player.sendMessage("  §f- " + juror + tag + vote);
            }

            if (jury.isDeliberationComplete()) {
                // 分隔
                player.sendMessage("§a陪审团已完成审议");

                jury.getMajorityVerdict().ifPresent(verdict ->
                        player.sendMessage("§7多数意见: §f" + verdict.name())
                );
            }

            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "select" -> {
                // 抽选陪审团
                return handleJurySelect(player, courtCase, args);
            }
            case "vote" -> {
                // 陪审投票
                return handleJuryVote(player, courtCase, args);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                return true;
            }
        }
    }

    private boolean handleJurySelect(Player player, CourtCase courtCase, String[] args) {
        // 获取合格陪审员（Nation成员，排除原告和被告）
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        List<UUID> eligiblePlayers = nation.members().stream()
                .map(m -> m.playerId())
                .filter(id -> !id.equals(courtCase.getPlaintiff()) && !id.equals(courtCase.getDefendant()))
                .collect(Collectors.toList());

        int jurySize = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        if (eligiblePlayers.size() < jurySize) {
            player.sendMessage("§c合格陪审员不足，需要 " + jurySize + " 人，但只有 " + eligiblePlayers.size() + " 人");
            return true;
        }

        Optional<Jury> juryOpt = courtService.selectJury(courtCase.getCaseId(), eligiblePlayers, jurySize);

        if (juryOpt.isPresent()) {
            player.sendMessage("§a陪审团抽选完成，共 " + jurySize + " 人");
            player.sendMessage("§7案件ID: " + courtCase.getCaseId());
        } else {
            player.sendMessage("§c抽选陪审团失败");
        }

        return true;
    }

    private boolean handleJuryVote(Player player, CourtCase courtCase, String[] args) {
        Optional<Jury> juryOpt = courtService.getJury(courtCase.getCaseId());
        if (juryOpt.isEmpty()) {
            player.sendMessage("§c该案件还没有陪审团");
            return true;
        }

        Jury jury = juryOpt.get();

        if (!jury.isJuror(player.getUniqueId())) {
            player.sendMessage("§c你不是陪审员");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /court jury " + courtCase.getCaseId() + " vote <guilty|notguilty|abstain>");
            return true;
        }

        Jury.JuryVote vote;
        switch (args[1].toLowerCase()) {
            case "guilty", "g", "有罪" -> vote = Jury.JuryVote.GUILTY;
            case "notguilty", "ng", "无罪" -> vote = Jury.JuryVote.NOT_GUILTY;
            case "abstain", "a", "弃权" -> vote = Jury.JuryVote.ABSTAIN;
            default -> {
                player.sendMessage("§c无效的投票: " + args[1]);
                return true;
            }
        }

        if (courtService.recordJuryVote(courtCase.getCaseId(), player.getUniqueId(), vote)) {
            player.sendMessage("§a投票成功: " + vote.name());

            // 检查是否所有人都投票了
            if (jury.hasAllVoted()) {
                jury.setDeliberationComplete(true);
                player.sendMessage("§e陪审团已完成审议!");
            }
        } else {
            player.sendMessage("§c投票失败");
        }

        return true;
    }

    private boolean handleVerdict(Player player, String[] args) {
        // args: [caseId, verdictType, reason?, -fine, amount?, -jail, minutes?, -banish?]
        String caseIdStr = args[0];
        String verdictTypeStr = args[1];

        int caseId;
        try {
            caseId = Integer.parseInt(caseIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的案件ID: " + caseIdStr);
            return true;
        }

        Optional<CourtCase> caseOpt = courtService.getCase(caseId);
        if (caseOpt.isEmpty()) {
            player.sendMessage("§c找不到案件: " + caseId);
            return true;
        }

        CourtCase courtCase = caseOpt.get();

        // 检查是否是负责该案件的法官
        if (courtCase.getAssignedJudge().isEmpty() || !courtCase.getAssignedJudge().get().equals(player.getUniqueId())) {
            player.sendMessage("§c你不是负责该案件的法官");
            return true;
        }

        Verdict.VerdictType verdictType;
        try {
            verdictType = Verdict.VerdictType.valueOf(verdictTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的判决类型: " + verdictTypeStr);
            player.sendMessage("§7有效类型: GUILTY | NOT_GUILTY | DISMISSED | SETTLED");
            return true;
        }

        // 解析参数
        StringBuilder reasoning = new StringBuilder();
        Double fineAmount = null;
        Integer jailMinutes = null;
        Boolean banishment = null;

        int i = 2;
        while (i < args.length) {
            String arg = args[i];
            switch (arg.toLowerCase()) {
                case "-fine" -> {
                    if (i + 1 < args.length) {
                        try {
                            fineAmount = Double.parseDouble(args[++i]);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c无效的罚款金额: " + args[i]);
                            return true;
                        }
                    }
                }
                case "-jail" -> {
                    if (i + 1 < args.length) {
                        try {
                            jailMinutes = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§c无效的监禁时间: " + args[i]);
                            return true;
                        }
                    }
                }
                case "-banish" -> banishment = true;
                default -> {
                    if (!arg.startsWith("-")) {
                        if (reasoning.length() > 0) reasoning.append(" ");
                        reasoning.append(arg);
                    }
                }
            }
            i++;
        }

        String reasoningStr = reasoning.length() > 0 ? reasoning.toString() : "依法判决";

        Optional<Verdict> verdictOpt = courtService.issueVerdict(caseId, player.getUniqueId(), verdictType, reasoningStr);

        if (verdictOpt.isEmpty()) {
            player.sendMessage("§c发布判决失败");
            return true;
        }

        Verdict verdict = verdictOpt.get();

        // 设置惩罚
        boolean hasPunishment = false;
        if (fineAmount != null || jailMinutes != null || banishment != null) {
            courtService.setVerdictPunishment(verdict.getVerdictId(), fineAmount, jailMinutes, banishment);
            // 重新加载判决
            verdictOpt = courtService.getVerdict(verdict.getVerdictId());
            if (verdictOpt.isPresent()) {
                verdict = verdictOpt.get();
            }
            hasPunishment = verdict.hasPunishment();
        }

        player.sendMessage("§a判决已发布!");
        player.sendMessage("§7判决ID: §e" + verdict.getVerdictId());

        // 显示惩罚信息
        if (hasPunishment) {
            // 分隔
            player.sendMessage("§e§l判决惩罚:");
            if (fineAmount != null) {
                player.sendMessage("  §c罚款: §f" + fineAmount);
            }
            if (jailMinutes != null) {
                player.sendMessage("  §c监禁: §f" + jailMinutes + " 分钟");
            }
            if (banishment != null && banishment) {
                player.sendMessage("  §c驱逐: §f是");
            }

            // 执行判决
            if (verdictType == Verdict.VerdictType.GUILTY && executionService != null) {
                // 分隔
                player.sendMessage("§e正在执行判决惩罚...");

                CourtExecutionService.ExecutionResult result = executionService.executeVerdict(verdict);

                if (result.isSuccess()) {
                    player.sendMessage("§a✓ 所有惩罚执行成功!");
                } else {
                    player.sendMessage("§c部分惩罚执行失败:");
                    for (String error : result.getErrors()) {
                        player.sendMessage("§c  - " + error);
                    }
                }

                // 记录到法庭历史
                executionService.recordExecutionHistory(verdict.getVerdictId(), caseId,
                    courtCase.getDefendant(), result.getMessage());
            }
        }

        // 通知原告和被告
        String plaintiffName = onlinePlayerDirectory.findPlayerById(courtCase.getPlaintiff())
                .map(p -> p.getName())
                .orElse("原告");

        String defendantName = onlinePlayerDirectory.findPlayerById(courtCase.getDefendant())
                .map(p -> p.getName())
                .orElse("被告");

        String verdictMsg = String.format("§6§l[判决] 案件 #%d 判决结果: %s", caseId, verdictType.name());
        if (hasPunishment) {
            verdictMsg += " §7(含惩罚措施)";
        }

        Player plaintiff = onlinePlayerDirectory.findOnlinePlayer(courtCase.getPlaintiff()).orElse(null);
        if (plaintiff != null) {
            plaintiff.sendMessage(verdictMsg);
        }

        Player defendant = onlinePlayerDirectory.findOnlinePlayer(courtCase.getDefendant()).orElse(null);
        if (defendant != null) {
            defendant.sendMessage(verdictMsg);
        }

        return true;
    }

    private boolean handleAppeal(Player player, String caseIdStr, String grounds) {
        int caseId;
        try {
            caseId = Integer.parseInt(caseIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的案件ID: " + caseIdStr);
            return true;
        }

        Optional<CourtCase> caseOpt = courtService.getCase(caseId);
        if (caseOpt.isEmpty()) {
            player.sendMessage("§c找不到案件: " + caseId);
            return true;
        }

        CourtCase courtCase = caseOpt.get();

        // 检查是否是原告或被告
        if (!courtCase.getPlaintiff().equals(player.getUniqueId()) && !courtCase.getDefendant().equals(player.getUniqueId())) {
            player.sendMessage("§c只有原告或被告可以上诉");
            return true;
        }

        // 检查是否有判决
        if (courtCase.getVerdictId().isEmpty()) {
            player.sendMessage("§c该案件还没有判决，不能上诉");
            return true;
        }

        // 检查是否已经上诉
        List<Appeal> existingAppeals = courtService.getAppealsByCase(caseId);
        boolean hasPendingAppeal = existingAppeals.stream()
                .anyMatch(a -> !a.isCompleted() && a.getAppellant().equals(player.getUniqueId()));
        if (hasPendingAppeal) {
            player.sendMessage("§c你已提交过上诉，请等待处理");
            return true;
        }

        Optional<Integer> verdictIdOpt = courtCase.getVerdictId();
        if (verdictIdOpt.isEmpty()) {
            player.sendMessage("§c该案件没有判决，无法上诉");
            return true;
        }
        int verdictId = verdictIdOpt.get();
        Optional<Appeal> appealOpt = courtService.fileAppeal(caseId, verdictId, player.getUniqueId(), grounds);

        if (appealOpt.isPresent()) {
            Appeal appeal = appealOpt.get();
            player.sendMessage("§a上诉已提交!");
            player.sendMessage("§7上诉ID: §e" + appeal.getAppealId());
            player.sendMessage("§7原判决ID: §e" + verdictId);
            // 分隔
            player.sendMessage("§7上诉将在法院审查后处理，请等待通知");
        } else {
            player.sendMessage("§c提交上诉失败");
        }

        return true;
    }

    private boolean handleAppealReview(Player player, String[] args) {
        // /court appeal <appeal_id> <upheld|reversed|remanded|dismissed> [decision]
        if (args.length < 2) {
            player.sendMessage("§c用法: /court appeal <appeal_id> <upheld|reversed|remanded|dismissed> [决定理由]");
            return true;
        }

        int appealId;
        try {
            appealId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的上诉ID: " + args[0]);
            return true;
        }

        Optional<Appeal> appealOpt = courtService.getAppeal(appealId);
        if (appealOpt.isEmpty()) {
            player.sendMessage("§c找不到上诉: " + appealId);
            return true;
        }

        Appeal appeal = appealOpt.get();

        // 解析结果
        Appeal.AppealStatus resultStatus;
        try {
            resultStatus = Appeal.AppealStatus.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的上诉结果: " + args[1]);
            player.sendMessage("§7有效结果: UPHELD | REVERSED | REMANDED | DISMISSED");
            return true;
        }

        if (resultStatus == Appeal.AppealStatus.FILED || resultStatus == Appeal.AppealStatus.UNDER_REVIEW) {
            player.sendMessage("§c无效的上诉结果状态");
            return true;
        }

        String decision = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        // 完成上诉
        if (courtService.completeAppeal(appealId, resultStatus, decision, null)) {
            player.sendMessage("§a上诉审查完成!");

            // 如果是改判，撤销原判决惩罚
            if (resultStatus == Appeal.AppealStatus.REVERSED && executionService != null) {
                CourtExecutionService.ExecutionResult execResult = executionService.revokeVerdict(appeal.getOriginalVerdictId());
                if (execResult.isSuccess()) {
                    player.sendMessage("§a原判决惩罚已撤销");
                } else {
                    player.sendMessage("§e部分惩罚撤销失败: " + execResult.getMessage());
                }
            }

            // 通知上诉人
            UUID appellantId = appeal.getAppellant();
            onlinePlayerDirectory.findOnlinePlayer(appellantId).ifPresent(p -> {
                p.sendMessage("§6§l[上诉通知]");
                p.sendMessage("§7你的上诉 #" + appealId + " 审查结果: §e" + resultStatus.name());
                if (!decision.isEmpty()) {
                    p.sendMessage("§7理由: §f" + decision);
                }
            });
        } else {
            player.sendMessage("§c处理上诉失败");
        }

        return true;
    }

    private boolean handleMyCases(Player player) {
        List<CourtCase> asPlaintiff = courtService.getCasesByPlaintiff(player.getUniqueId());
        List<CourtCase> asDefendant = courtService.getCasesByDefendant(player.getUniqueId());

        player.sendMessage("§6§l==== 我的案件 ====");

        if (asPlaintiff.isEmpty() && asDefendant.isEmpty()) {
            player.sendMessage("§7你还没有任何案件");
            return true;
        }

        if (!asPlaintiff.isEmpty()) {
            // 分隔
            player.sendMessage("§a§l作为原告的案件:");
            for (CourtCase courtCase : asPlaintiff) {
                player.sendMessage("  §e[" + courtCase.getCaseId() + "] §f" + courtCase.getCaseType().name() + " §7- " + courtCase.getStatus());
            }
        }

        if (!asDefendant.isEmpty()) {
            // 分隔
            player.sendMessage("§c§l作为被告的案件:");
            for (CourtCase courtCase : asDefendant) {
                player.sendMessage("  §e[" + courtCase.getCaseId() + "] §f" + courtCase.getCaseType().name() + " §7- " + courtCase.getStatus());
            }
        }

        return true;
    }

    private boolean handleListJudges(Player player) {
        List<Judge> judges = courtService.getActiveJudges();

        player.sendMessage("§6§l==== 法官列表 ====");

        if (judges.isEmpty()) {
            player.sendMessage("§7暂无在职法官");
            return true;
        }

        for (Judge judge : judges) {
            String name = onlinePlayerDirectory.findPlayerById(judge.getPlayerId())
                    .map(p -> p.getName())
                    .orElse(judge.getPlayerId().toString().substring(0, 8));

            String termInfo = judge.getTermEndsAt()
                    .map(t -> " §7(任期至: " + t.toString() + ")")
                    .orElse("");

            player.sendMessage("§e- §f" + name);
            player.sendMessage("  §7受理案件: " + judge.getCasesHandled() + termInfo);
        }

        return true;
    }

    private boolean handleHistory(Player player, String[] args) {
        // /court history [玩家] [页数]
        UUID targetId = player.getUniqueId();
        int limit = 10;
        int page = 1;

        if (args.length > 0) {
            // 尝试解析玩家名
            Optional<? extends Player> targetOpt = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(args[0]))
                    .findFirst();
            if (targetOpt.isPresent()) {
                targetId = targetOpt.get().getUniqueId();
            } else {
                player.sendMessage("§c找不到玩家: " + args[0]);
                return true;
            }
        }

        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的页数");
                return true;
            }
        }

        int offset = (page - 1) * limit;
        List<CourtExecutionService.CourtHistoryRecord> history = executionService.getPlayerCourtHistory(targetId, limit);

        String targetName = onlinePlayerDirectory.findPlayerById(targetId)
                .map(p -> p.getName())
                .orElse(targetId.toString().substring(0, 8));

        player.sendMessage("§6§l==== " + targetName + " 的法庭历史 ====");

        if (history.isEmpty()) {
            player.sendMessage("§7暂无法庭记录");
            return true;
        }

        for (CourtExecutionService.CourtHistoryRecord record : history) {
            player.sendMessage("§e[案件 #" + record.caseId() + "]");
            player.sendMessage("  §7判决ID: §f" + record.verdictId());
            player.sendMessage("  §7执行详情: §f" + record.executionDetails());
            player.sendMessage("  §7时间: §f" + record.executedAt().toString());
            // 分隔
        }

        return true;
    }

    private boolean handleExecute(Player player, String[] args) {
        // /court execute <verdict_id> - 重新执行判决
        if (!player.hasPermission("starcore.court.judge")) {
            player.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c用法: /court execute <verdict_id>");
            return true;
        }

        int verdictId;
        try {
            verdictId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的判决ID: " + args[0]);
            return true;
        }

        // 检查判决是否已执行
        if (executionService.isVerdictExecuted(verdictId)) {
            player.sendMessage("§c判决 #" + verdictId + " 已执行过");
            player.sendMessage("§e使用 §f/court appeal " + verdictId + " reversed §e撤销原判决");
            return true;
        }

        // 获取判决
        var verdictOpt = courtService.getVerdict(verdictId);
        if (verdictOpt.isEmpty()) {
            player.sendMessage("§c找不到判决: " + verdictId);
            return true;
        }

        player.sendMessage("§e正在执行判决 #" + verdictId + "...");

        CourtExecutionService.ExecutionResult result = executionService.executeVerdict(verdictOpt.get());

        if (result.isSuccess()) {
            player.sendMessage("§a✓ 判决执行成功!");
        } else {
            player.sendMessage("§c判决执行失败:");
            for (String error : result.getErrors()) {
                player.sendMessage("§c  - " + error);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("file", "appoint", "list", "case", "jury", "verdict", "appeal", "my", "judges", "history", "execute")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "case", "jury" -> {
                    return courtService.getPendingCases().stream()
                            .map(c -> String.valueOf(c.getCaseId()))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
                case "verdict" -> {
                    return courtService.getPendingCases().stream()
                            .map(c -> String.valueOf(c.getCaseId()))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
                case "appeal" -> {
                    return courtService.getPendingCases().stream()
                            .filter(c -> c.getVerdictId().isPresent())
                            .map(c -> String.valueOf(c.getCaseId()))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "file" -> {
                    return Arrays.asList("CRIMINAL", "CIVIL", "ADMINISTRATIVE", "CONSTITUTIONAL")
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "verdict" -> {
                    return Arrays.asList("GUILTY", "NOT_GUILTY", "DISMISSED", "SETTLED")
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "case" -> {
                    if ("assign".equalsIgnoreCase(args[1])) {
                        return courtService.getActiveJudges().stream()
                                .map(j -> onlinePlayerDirectory.findPlayerById(j.getPlayerId())
                                        .map(p -> p.getName())
                                        .orElse(j.getPlayerId().toString().substring(0, 8)))
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                case "jury" -> {
                    if ("vote".equalsIgnoreCase(args[1])) {
                        return Arrays.asList("guilty", "notguilty", "abstain")
                                .stream()
                                .filter(s -> s.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}
