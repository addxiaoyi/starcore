package dev.starcore.starcore.government.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.government.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 议会命令
 * 命令：/parliament <election|propose|vote>
 */
public class ParliamentCommand implements CommandExecutor, TabCompleter {

    private final ParliamentService parliamentService;
    private final PartyService partyService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    public ParliamentCommand(
            ParliamentService parliamentService,
            PartyService partyService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory
    ) {
        this.parliamentService = parliamentService;
        this.partyService = partyService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
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
            case "election" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /parliament election <start|schedule|info>");
                    return true;
                }
                return handleElection(player, args[1], Arrays.copyOfRange(args, 2, args.length));
            }
            case "propose" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /parliament propose <title> <content>");
                    return true;
                }
                return handlePropose(player, args);
            }
            case "vote" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /parliament vote <bill_id> <for|against|abstain> [comment]");
                    return true;
                }
                return handleVote(player, args[1], args.length > 2 ? args[2] : null, args.length > 3 ? args[3] : null);
            }
            case "list" -> {
                return handleList(player);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /parliament info [bill_id]");
                    return true;
                }
                return handleInfo(player, args.length > 1 ? args[1] : null);
            }
            case "schedule" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /parliament schedule <bill_id> <days>");
                    return true;
                }
                return handleSchedule(player, args[1], args.length > 2 ? args[2] : "1");
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                return handleHelp(player);
            }
        }
    }

    private boolean handleHelp(Player player) {
        player.sendMessage("§6§l==== 议会命令帮助 ====");
        // 分隔
        player.sendMessage("§e/parliament election start §7- 开始选举");
        player.sendMessage("§e/parliament election schedule <天数> §7- 安排选举");
        player.sendMessage("§e/parliament election info §7- 查看选举信息");
        // 分隔
        player.sendMessage("§e/parliament propose <标题> §7- 提交新议案");
        player.sendMessage("§e/parliament vote <id> <for|against|abstain> §7- 投票");
        player.sendMessage("§e/parliament list §7- 查看所有议案");
        player.sendMessage("§e/parliament info [id] §7- 查看议案详情");
        player.sendMessage("§e/parliament schedule <id> <天数> §7- 安排投票");
        return true;
    }

    private boolean handleElection(Player player, String action, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        switch (action.toLowerCase()) {
            case "start" -> {
                // 开始选举
                String nationId = nation.id().value().toString();
                Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

                if (parliamentOpt.isEmpty()) {
                    // 创建议会
                    int totalSeats = Math.max(1, nation.members().size() / 2);
                    parliamentOpt = parliamentService.createParliament(nationId, nation.name() + "议会", totalSeats, 30);
                    if (parliamentOpt.isEmpty()) {
                        player.sendMessage("§c创建议会失败");
                        return true;
                    }
                    player.sendMessage("§a已创建议会，席位: " + totalSeats);
                }

                Parliament parliament = parliamentOpt.get();

                // 安排选举
                Instant electionDate = Instant.now().plus(3, ChronoUnit.DAYS);
                if (parliamentService.scheduleNextElection(parliament.getParliamentId(), electionDate)) {
                    player.sendMessage("§a已安排选举，时间: " + electionDate.toString());
                } else {
                    player.sendMessage("§c安排选举失败");
                }
                return true;
            }

            case "schedule" -> {
                // 安排选举
                if (args.length < 1) {
                    player.sendMessage("§c用法: /parliament election schedule <天数>");
                    return true;
                }
                int days;
                try {
                    days = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的天数: " + args[0]);
                    return true;
                }

                String nationId = nation.id().value().toString();
                Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

                if (parliamentOpt.isEmpty()) {
                    player.sendMessage("§c你的Nation还没有议会");
                    return true;
                }

                Instant electionDate = Instant.now().plus(days, ChronoUnit.DAYS);
                if (parliamentService.scheduleNextElection(parliamentOpt.get().getParliamentId(), electionDate)) {
                    player.sendMessage("§a已安排选举，时间: " + electionDate.toString());
                } else {
                    player.sendMessage("§c安排选举失败");
                }
                return true;
            }

            case "info" -> {
                // 查看选举信息
                String nationId = nation.id().value().toString();
                Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

                if (parliamentOpt.isEmpty()) {
                    player.sendMessage("§c你的Nation还没有议会");
                    return true;
                }

                Parliament parliament = parliamentOpt.get();
                List<Member> members = parliamentService.getActiveMembers(parliament.getParliamentId());

                player.sendMessage("§6§l==== 议会信息 ====");
                player.sendMessage("§7议会名称: §f" + parliament.getName());
                player.sendMessage("§7总席位: §f" + parliament.getTotalSeats());
                player.sendMessage("§7当前议员: §f" + members.size());
                player.sendMessage("§7任期时长: §f" + parliament.getTermLengthDays() + "天");

                if (parliament.getNextElectionAt().isPresent()) {
                    player.sendMessage("§7下次选举: §f" + parliament.getNextElectionAt().get().toString());
                } else {
                    player.sendMessage("§7下次选举: §c未安排");
                }

                return true;
            }

            default -> {
                player.sendMessage("§c用法: /parliament election <start|schedule|info>");
                return true;
            }
        }
    }

    private boolean handlePropose(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        String nationId = nation.id().value().toString();
        Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

        if (parliamentOpt.isEmpty()) {
            player.sendMessage("§c你的Nation还没有议会，请先使用 /parliament election start 创建");
            return true;
        }

        Parliament parliament = parliamentOpt.get();

        // 检查是否是议员
        Optional<Member> memberOpt = parliamentService.getMember(parliament.getParliamentId(), player.getUniqueId());
        if (memberOpt.isEmpty() || !memberOpt.get().isActive()) {
            player.sendMessage("§c只有议员才能提交议案");
            return true;
        }

        // 解析标题和内容
        // args[1] 是标题，其余是内容
        String title = args[1];
        String content = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        if (title.length() > 200) {
            player.sendMessage("§c标题过长（最多200字符）");
            return true;
        }

        // 默认为立法议案
        Bill.BillType billType = Bill.BillType.LEGISLATIVE;
        Optional<Bill> billOpt = parliamentService.proposeBill(
                parliament.getParliamentId(),
                player.getUniqueId(),
                title,
                content,
                billType
        );

        if (billOpt.isPresent()) {
            player.sendMessage("§a议案已提交，ID: §e" + billOpt.get().getBillId());
        } else {
            player.sendMessage("§c提交议案失败");
        }

        return true;
    }

    private boolean handleVote(Player player, String billIdStr, String choiceStr, String comment) {
        int billId;
        try {
            billId = Integer.parseInt(billIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的议案ID: " + billIdStr);
            return true;
        }

        Optional<Bill> billOpt = parliamentService.getBill(billId);
        if (billOpt.isEmpty()) {
            player.sendMessage("§c找不到议案: " + billId);
            return true;
        }

        Bill bill = billOpt.get();

        // 检查是否正在投票
        if (bill.getStatus() != Bill.BillStatus.VOTING) {
            player.sendMessage("§c该议案当前不在投票中，状态: " + bill.getStatus());
            return true;
        }

        // 检查投票是否还在进行
        if (!bill.isVotingActive()) {
            if (bill.isVotingEnded()) {
                player.sendMessage("§c投票已结束");
            } else {
                player.sendMessage("§c投票尚未开始");
            }
            return true;
        }

        // 解析投票选择
        Vote.VoteChoice choice;
        if (choiceStr == null) {
            player.sendMessage("§c用法: /parliament vote <bill_id> <for|against|abstain> [comment]");
            return true;
        }

        switch (choiceStr.toLowerCase()) {
            case "for", "yes", "赞成", "y" -> choice = Vote.VoteChoice.FOR;
            case "against", "no", "反对", "n" -> choice = Vote.VoteChoice.AGAINST;
            case "abstain", "弃权", "a" -> choice = Vote.VoteChoice.ABSTAIN;
            default -> {
                player.sendMessage("§c无效的投票选择: " + choiceStr + "，请使用 for/against/abstain");
                return true;
            }
        }

        // 检查是否已投票
        if (parliamentService.hasVoted(billId, player.getUniqueId())) {
            player.sendMessage("§c你已经投过票了");
            return true;
        }

        Optional<Vote> voteOpt = parliamentService.castVote(billId, player.getUniqueId(), choice, comment);

        if (voteOpt.isPresent()) {
            player.sendMessage("§a投票成功！");
            // 显示当前投票情况
            Bill updatedBill = parliamentService.getBill(billId).orElse(bill);
            player.sendMessage("§7当前: §a赞成 " + updatedBill.getVotesFor() +
                    " §c反对 " + updatedBill.getVotesAgainst() +
                    " §7弃权 " + updatedBill.getVotesAbstain());
        } else {
            player.sendMessage("§c投票失败");
        }

        return true;
    }

    private boolean handleList(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        String nationId = nation.id().value().toString();
        Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

        if (parliamentOpt.isEmpty()) {
            player.sendMessage("§c你的Nation还没有议会");
            return true;
        }

        Parliament parliament = parliamentOpt.get();
        List<Bill> bills = parliamentService.getBillsByParliament(parliament.getParliamentId());

        player.sendMessage("§6§l==== 议案列表 ====");

        if (bills.isEmpty()) {
            player.sendMessage("§7暂无议案");
            return true;
        }

        for (Bill bill : bills.stream().limit(10).collect(Collectors.toList())) {
            String statusColor = switch (bill.getStatus()) {
                case PROPOSED, UNDER_REVIEW -> "§e";
                case SCHEDULED, VOTING -> "§b";
                case PASSED, ENACTED -> "§a";
                case REJECTED, WITHDRAWN -> "§c";
            };

            player.sendMessage(statusColor + "[" + bill.getBillId() + "] §f" + bill.getTitle());
            player.sendMessage("  §7类型: " + bill.getBillType() + " | 状态: " + statusColor + bill.getStatus());
            player.sendMessage("  §7赞成: §a" + bill.getVotesFor() + " §c反对: " + bill.getVotesAgainst() + " §7弃权: " + bill.getVotesAbstain());
        }

        if (bills.size() > 10) {
            // 分隔
            player.sendMessage("§7...还有 " + (bills.size() - 10) + " 条议案");
        }

        return true;
    }

    private boolean handleInfo(Player player, String billIdStr) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        if (billIdStr == null) {
            // 显示当前正在投票的议案
            String nationId = nation.id().value().toString();
            Optional<Parliament> parliamentOpt = parliamentService.getParliamentByNation(nationId);

            if (parliamentOpt.isEmpty()) {
                player.sendMessage("§c你的Nation还没有议会");
                return true;
            }

            Parliament parliament = parliamentOpt.get();
            List<Bill> votingBills = parliamentService.getVotingBills(parliament.getParliamentId());

            player.sendMessage("§6§l==== 正在投票的议案 ====");

            if (votingBills.isEmpty()) {
                player.sendMessage("§7暂无正在投票的议案");
                return true;
            }

            for (Bill bill : votingBills) {
                String proposer = onlinePlayerDirectory.findPlayerById(bill.getProposerId())
                        .map(p -> p.getName())
                        .orElse(bill.getProposerId().toString());

                player.sendMessage("§e[" + bill.getBillId() + "] §f" + bill.getTitle());
                player.sendMessage("  §7提出者: " + proposer);
                player.sendMessage("  §7赞成: §a" + bill.getVotesFor() + " §c反对: " + bill.getVotesAgainst() + " §7弃权: " + bill.getVotesAbstain());
                player.sendMessage("  §7结束时间: " + (bill.getVotingEndsAt().isPresent() ? bill.getVotingEndsAt().get().toString() : "未知"));
            }

            return true;
        }

        // 显示指定议案详情
        int billId;
        try {
            billId = Integer.parseInt(billIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的议案ID: " + billIdStr);
            return true;
        }

        Optional<Bill> billOpt = parliamentService.getBill(billId);
        if (billOpt.isEmpty()) {
            player.sendMessage("§c找不到议案: " + billId);
            return true;
        }

        Bill bill = billOpt.get();
        String proposer = onlinePlayerDirectory.findPlayerById(bill.getProposerId())
                .map(p -> p.getName())
                .orElse(bill.getProposerId().toString());

        player.sendMessage("§6§l==== 议案详情 ====");
        player.sendMessage("§7ID: §f" + bill.getBillId());
        player.sendMessage("§7标题: §f" + bill.getTitle());
        player.sendMessage("§7内容: §f" + bill.getContent());
        player.sendMessage("§7类型: §f" + bill.getBillType());
        player.sendMessage("§7提出者: §f" + proposer);
        player.sendMessage("§7提出时间: §f" + bill.getProposedAt().toString());
        player.sendMessage("§7状态: §f" + bill.getStatus());
        // 分隔
        player.sendMessage("§7投票结果:");
        player.sendMessage("  §a赞成: " + bill.getVotesFor());
        player.sendMessage("  §c反对: " + bill.getVotesAgainst());
        player.sendMessage("  §7弃权: " + bill.getVotesAbstain());
        player.sendMessage("  §7支持率: " + String.format("%.1f%%", bill.getSupportRate() * 100));

        return true;
    }

    private boolean handleSchedule(Player player, String billIdStr, String daysStr) {
        int billId;
        try {
            billId = Integer.parseInt(billIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的议案ID: " + billIdStr);
            return true;
        }

        int days;
        try {
            days = Integer.parseInt(daysStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的天数: " + daysStr);
            return true;
        }

        Optional<Bill> billOpt = parliamentService.getBill(billId);
        if (billOpt.isEmpty()) {
            player.sendMessage("§c找不到议案: " + billId);
            return true;
        }

        Bill bill = billOpt.get();

        if (bill.getStatus() != Bill.BillStatus.PROPOSED) {
            player.sendMessage("§c只有已提交的议案才能安排投票");
            return true;
        }

        Instant votingStarts = Instant.now();
        Instant votingEnds = Instant.now().plus(days, ChronoUnit.DAYS);

        if (parliamentService.scheduleBillVoting(billId, votingStarts, votingEnds)) {
            // 立即开始投票
            parliamentService.startVoting(billId);
            player.sendMessage("§a已安排投票并开始!");
            player.sendMessage("§7投票将在 " + days + " 天后结束");
        } else {
            player.sendMessage("§c安排投票失败");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("election", "propose", "vote", "list", "info", "schedule")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "election" -> {
                    return Arrays.asList("start", "schedule", "info")
                            .stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "info", "schedule" -> {
                    // 返回待投票的议案
                    return parliamentService.getPendingBills(0).stream()
                            .map(b -> String.valueOf(b.getBillId()))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("vote")) {
            return Arrays.asList("for", "against", "abstain")
                    .stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
