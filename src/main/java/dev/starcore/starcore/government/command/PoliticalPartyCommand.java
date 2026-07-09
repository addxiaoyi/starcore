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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 政党命令
 * 命令：/politicalparty <create|join|list|info|leave|dissolve>
 */
public class PoliticalPartyCommand implements CommandExecutor, TabCompleter {

    private final PartyService partyService;
    private final ParliamentService parliamentService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    public PoliticalPartyCommand(
            PartyService partyService,
            ParliamentService parliamentService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory
    ) {
        this.partyService = partyService;
        this.parliamentService = parliamentService;
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
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty create <名称> [缩写] [意识形态]");
                    return true;
                }
                return handleCreate(player, args);
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty join <政党ID>");
                    return true;
                }
                return handleJoin(player, args[1]);
            }
            case "list" -> {
                return handleList(player);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty info [政党ID|名称]");
                    return true;
                }
                return handleInfo(player, args.length > 1 ? args[1] : null);
            }
            case "leave" -> {
                return handleLeave(player);
            }
            case "dissolve" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty dissolve <政党ID>");
                    return true;
                }
                return handleDissolve(player, args[1]);
            }
            case "setpower" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty setpower <政党ID>");
                    return true;
                }
                return handleSetPower(player, args[1]);
            }
            case "members" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /politicalparty members <政党ID>");
                    return true;
                }
                return handleMembers(player, args[1]);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                return handleHelp(player);
            }
        }
    }

    private boolean handleHelp(Player player) {
        player.sendMessage("§6§l==== 政党命令帮助 ====");
        // 分隔
        player.sendMessage("§e/politicalparty create <名称> [缩写] §7- 创建政党");
        player.sendMessage("§e/politicalparty join <政党ID> §7- 加入政党");
        player.sendMessage("§e/politicalparty list §7- 查看所有政党");
        player.sendMessage("§e/politicalparty info [ID|名称] §7- 查看政党信息");
        player.sendMessage("§e/politicalparty members <政党ID> §7- 查看党员列表");
        player.sendMessage("§e/politicalparty leave §7- 离开当前政党");
        player.sendMessage("§e/politicalparty dissolve <政党ID> §7- 解散政党");
        player.sendMessage("§e/politicalparty setpower <政党ID> §7- 设置执政党");
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        String name = args[1];
        String abbreviation = args.length > 2 ? args[2].toUpperCase() : name.substring(0, Math.min(3, name.length())).toUpperCase();
        String ideology = args.length > 3 ? args[3] : null;

        // 检查是否已创建政党
        Optional<Integer> existingParty = partyService.getPlayerParty(player.getUniqueId());
        if (existingParty.isPresent()) {
            player.sendMessage("§c你已经是政党成员了，请先离开当前政党");
            return true;
        }

        Optional<PoliticalParty> partyOpt = partyService.createParty(name, abbreviation, player.getUniqueId(), ideology, null);

        if (partyOpt.isPresent()) {
            PoliticalParty party = partyOpt.get();
            player.sendMessage("§a政党创建成功!");
            player.sendMessage("§7政党名称: §f" + party.getName());
            player.sendMessage("§7缩写: §f" + party.getAbbreviation());
            player.sendMessage("§7ID: §e" + party.getPartyId());
            if (ideology != null) {
                player.sendMessage("§7意识形态: §f" + ideology);
            }
        } else {
            player.sendMessage("§c创建政党失败");
        }

        return true;
    }

    private boolean handleJoin(Player player, String partyIdStr) {
        int partyId;
        try {
            partyId = Integer.parseInt(partyIdStr);
        } catch (NumberFormatException e) {
            // 尝试按名称查找
            List<PoliticalParty> parties = partyService.getActiveParties();
            Optional<PoliticalParty> partyOpt = parties.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(partyIdStr) || p.getAbbreviation().equalsIgnoreCase(partyIdStr))
                    .findFirst();

            if (partyOpt.isEmpty()) {
                player.sendMessage("§c找不到政党: " + partyIdStr);
                return true;
            }
            partyId = partyOpt.get().getPartyId();
        }

        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        if (partyOpt.isEmpty()) {
            player.sendMessage("§c找不到政党: " + partyId);
            return true;
        }

        PoliticalParty party = partyOpt.get();

        // 检查是否已加入其他政党
        Optional<Integer> existingParty = partyService.getPlayerParty(player.getUniqueId());
        if (existingParty.isPresent()) {
            player.sendMessage("§c你已经是政党成员了，请先离开当前政党");
            return true;
        }

        if (partyService.addPartyMember(partyId, player.getUniqueId(), "MEMBER")) {
            player.sendMessage("§a成功加入政党: §f" + party.getName());
        } else {
            player.sendMessage("§c加入政党失败");
        }

        return true;
    }

    private boolean handleList(Player player) {
        List<PoliticalParty> parties = partyService.getActiveParties();

        player.sendMessage("§6§l==== 政党列表 ====");

        if (parties.isEmpty()) {
            player.sendMessage("§7暂无政党");
            return true;
        }

        for (PoliticalParty party : parties) {
            int memberCount = partyService.getPartyMemberCount(party.getPartyId());
            String leader = onlinePlayerDirectory.findPlayerById(party.getFounderId())
                    .map(p -> p.getName())
                    .orElse(party.getFounderId().toString().substring(0, 8));

            String status = party.isInPower() ? " §a[执政]" : "";

            player.sendMessage("§e[" + party.getPartyId() + "] §f" + party.getName() + " §7(" + party.getAbbreviation() + ")" + status);
            player.sendMessage("  §7创建者: " + leader + " | 党员: " + memberCount);
            party.getIdeology().ifPresent(ideology -> player.sendMessage("  §7意识形态: §f" + ideology));
        }

        return true;
    }

    private boolean handleInfo(Player player, String partyInfo) {
        if (partyInfo == null) {
            // 显示自己所在的政党
            Optional<Integer> partyIdOpt = partyService.getPlayerParty(player.getUniqueId());
            if (partyIdOpt.isEmpty()) {
                player.sendMessage("§c你还没有加入任何政党");
                return true;
            }
            return handleInfoById(player, partyIdOpt.get().toString());
        }

        return handleInfoById(player, partyInfo);
    }

    private boolean handleInfoById(Player player, String partyInfo) {
        int partyId;
        try {
            partyId = Integer.parseInt(partyInfo);
        } catch (NumberFormatException e) {
            // 按名称查找
            List<PoliticalParty> parties = partyService.getActiveParties();
            Optional<PoliticalParty> partyOpt = parties.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(partyInfo) || p.getAbbreviation().equalsIgnoreCase(partyInfo))
                    .findFirst();

            if (partyOpt.isEmpty()) {
                player.sendMessage("§c找不到政党: " + partyInfo);
                return true;
            }
            partyId = partyOpt.get().getPartyId();
        }

        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        if (partyOpt.isEmpty()) {
            player.sendMessage("§c找不到政党: " + partyId);
            return true;
        }

        PoliticalParty party = partyOpt.get();
        String founder = onlinePlayerDirectory.findPlayerById(party.getFounderId())
                .map(p -> p.getName())
                .orElse(party.getFounderId().toString().substring(0, 8));

        player.sendMessage("§6§l==== 政党信息 ====");
        player.sendMessage("§7ID: §f" + party.getPartyId());
        player.sendMessage("§7名称: §f" + party.getName());
        player.sendMessage("§7缩写: §f" + party.getAbbreviation());
        player.sendMessage("§7创建者: §f" + founder);
        player.sendMessage("§7成立时间: §f" + party.getFoundedAt().toString());
        player.sendMessage("§7党员数: §f" + partyService.getPartyMemberCount(party.getPartyId()));
        player.sendMessage("§7状态: " + (party.isInPower() ? "§a执政" : "§7在野"));
        player.sendMessage("§7席位: §f" + party.getTotalSeats());

        party.getIdeology().ifPresent(ideology -> player.sendMessage("§7意识形态: §f" + ideology));
        party.getPlatform().ifPresent(platform -> {
            player.sendMessage("§7政党纲领:");
            player.sendMessage("  §f" + platform);
        });

        // 检查玩家是否是党员
        Optional<Integer> playerParty = partyService.getPlayerParty(player.getUniqueId());
        if (playerParty.isPresent() && playerParty.get() == partyId) {
            // 分隔
            player.sendMessage("§a你是该政党的成员");
        }

        return true;
    }

    private boolean handleLeave(Player player) {
        Optional<Integer> partyIdOpt = partyService.getPlayerParty(player.getUniqueId());
        if (partyIdOpt.isEmpty()) {
            player.sendMessage("§c你还没有加入任何政党");
            return true;
        }

        int partyId = partyIdOpt.get();
        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        PoliticalParty party = partyOpt.orElse(null);

        // 创始人不能直接离开，需要先转让或解散
        if (party != null && party.getFounderId().equals(player.getUniqueId())) {
            player.sendMessage("§c你是政党创始人，不能直接离开");
            player.sendMessage("§7请先使用 §e/politicalparty dissolve " + partyId + " §7解散政党");
            return true;
        }

        if (partyService.removePartyMember(partyId, player.getUniqueId())) {
            String partyName = party != null ? party.getName() : "政党";
            player.sendMessage("§a已离开 " + partyName);
        } else {
            player.sendMessage("§c离开政党失败");
        }

        return true;
    }

    private boolean handleDissolve(Player player, String partyIdStr) {
        int partyId;
        try {
            partyId = Integer.parseInt(partyIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的政党ID: " + partyIdStr);
            return true;
        }

        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        if (partyOpt.isEmpty()) {
            player.sendMessage("§c找不到政党: " + partyId);
            return true;
        }

        PoliticalParty party = partyOpt.get();

        // 只有创始人可以解散
        if (!party.getFounderId().equals(player.getUniqueId())) {
            player.sendMessage("§c只有政党创始人可以解散政党");
            return true;
        }

        if (partyService.dissolveParty(partyId)) {
            player.sendMessage("§a政党 §f" + party.getName() + " §a已解散");
        } else {
            player.sendMessage("§c解散政党失败");
        }

        return true;
    }

    private boolean handleSetPower(Player player, String partyIdStr) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();

        // 只有君主或管理员可以设置执政党
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage("§c只有Nation君主可以设置执政党");
            return true;
        }

        int partyId;
        try {
            partyId = Integer.parseInt(partyIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的政党ID: " + partyIdStr);
            return true;
        }

        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        if (partyOpt.isEmpty()) {
            player.sendMessage("§c找不到政党: " + partyId);
            return true;
        }

        PoliticalParty party = partyOpt.get();

        if (partyService.setPartyInPower(partyId, true)) {
            player.sendMessage("§a已设置 §f" + party.getName() + " §a为执政党");
        } else {
            player.sendMessage("§c设置执政党失败");
        }

        return true;
    }

    private boolean handleMembers(Player player, String partyIdStr) {
        int partyId;
        try {
            partyId = Integer.parseInt(partyIdStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的政党ID: " + partyIdStr);
            return true;
        }

        Optional<PoliticalParty> partyOpt = partyService.getParty(partyId);
        if (partyOpt.isEmpty()) {
            player.sendMessage("§c找不到政党: " + partyId);
            return true;
        }

        PoliticalParty party = partyOpt.get();
        List<UUID> members = partyService.getPartyMembers(partyId);

        player.sendMessage("§6§l==== " + party.getName() + " 党员 ====");

        if (members.isEmpty()) {
            player.sendMessage("§7暂无党员");
            return true;
        }

        player.sendMessage("§7总党员数: " + members.size());
        // 分隔

        for (UUID memberId : members) {
            String memberName = onlinePlayerDirectory.findPlayerById(memberId)
                    .map(p -> p.getName())
                    .orElse(memberId.toString().substring(0, 8));

            String tag = memberId.equals(party.getFounderId()) ? " §a[创建者]" : "";
            player.sendMessage("  §f" + memberName + tag);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "list", "info", "leave", "dissolve", "setpower", "members")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "join", "dissolve", "members", "setpower" -> {
                    return partyService.getActiveParties().stream()
                            .map(p -> String.valueOf(p.getPartyId()))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
                case "info" -> {
                    List<String> suggestions = new ArrayList<>();
                    // 添加玩家所在政党的ID
                    if (sender instanceof Player player) {
                        partyService.getPlayerParty(player.getUniqueId())
                                .ifPresent(id -> suggestions.add(String.valueOf(id)));
                    }
                    // 添加所有政党名称
                    partyService.getActiveParties().stream()
                            .map(PoliticalParty::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(suggestions::add);
                    return suggestions;
                }
            }
        }

        return Collections.emptyList();
    }
}
