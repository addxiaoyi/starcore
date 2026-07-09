package dev.starcore.starcore.command;

import dev.starcore.starcore.foundation.economy.EconomyTransferSystem;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 经济命令处理器
 */
public final class EconomyCommandHandler implements CommandExecutor, TabCompleter {
    private final EconomyTransferSystem transferSystem;

    public EconomyCommandHandler(EconomyTransferSystem transferSystem) {
        this.transferSystem = transferSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // audit C-081: 权限校验
        if (!sender.hasPermission("starcore.pay")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§e[星尘] 用法: /pay <玩家> <金额>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§c玩家不在线");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§c不能转账给自己");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的金额");
            return true;
        }

        // audit C-083: 拒绝 NaN / Infinity / 负数
        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage("§c金额必须为大于0的有限数字");
            return true;
        }

        if (amount > 1_000_000_000D) {
            player.sendMessage("§c金额过大");
            return true;
        }

        // 执行转账
        var result = transferSystem.transfer(player.getUniqueId(), target.getUniqueId(), amount);

        if (result.success()) {
            player.sendMessage("§a========== 转账成功 ==========");
            player.sendMessage("§e收款人: §f" + target.getName());
            player.sendMessage("§e金额: §f" + result.amount() + " 星尘");
            player.sendMessage("§e手续费: §f" + result.tax() + " 星尘");
            player.sendMessage("§e实际支付: §f" + result.receiverAmount() + " 星尘");

            target.sendMessage("§a[星尘] §f你收到了 §e" + player.getName() + " §f转账的 §e" + result.receiverAmount() + " §f星尘");
        } else {
            player.sendMessage("§c转账失败: " + result.message());
        }

        return true;
    }

    // audit C-084: 实现 Tab 补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("starcore.pay")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 2) {
            List<String> amounts = new ArrayList<>();
            for (String s : List.of("100", "500", "1000", "10000", "100000")) {
                if (s.startsWith(args[1])) {
                    amounts.add(s);
                }
            }
            return amounts;
        }
        return List.of();
    }
}
