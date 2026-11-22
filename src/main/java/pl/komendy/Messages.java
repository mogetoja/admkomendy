package pl.komendy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prywatne wiadomości: /msg <nick> <wiadomość> oraz /r <wiadomość>. */
public class Messages implements CommandExecutor, TabCompleter, Listener {

    private static final String CONSOLE = "Konsola";

    /** gracz -> ostatni rozmówca (do /r) */
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ komunikaty

    private void err(CommandSender s, String msg) {
        s.sendMessage(ChatColor.RED + "✘ " + ChatColor.GRAY + msg);
    }

    private void usage(CommandSender s, String msg) {
        s.sendMessage(ChatColor.YELLOW + "➤ " + ChatColor.GRAY + "Użycie: " + ChatColor.WHITE + msg);
    }

    private String hl(String text) {
        return ChatColor.AQUA + text + ChatColor.GRAY;
    }

    // ------------------------------------------------------------------ komendy

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("r")) {
            handleReply(sender, args);
        } else {
            handleMsg(sender, args);
        }
        return true;
    }

    private void handleMsg(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/msg <nick> <wiadomość>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { err(sender, "Gracz " + hl(args[0]) + " nie jest online."); return; }
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            err(sender, "Nie możesz napisać sam do siebie.");
            return;
        }
        deliver(sender, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    private void handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { err(sender, "Tę komendę może użyć tylko gracz."); return; }
        if (args.length < 1) { usage(sender, "/r <wiadomość>"); return; }

        UUID id = lastPartner.get(p.getUniqueId());
        if (id == null) { err(sender, "Nie masz komu odpisać."); return; }
        Player target = Bukkit.getPlayer(id);
        if (target == null) {
            lastPartner.remove(p.getUniqueId());
            err(sender, "Ten gracz nie jest już online.");
            return;
        }
        deliver(sender, target, String.join(" ", args));
    }

    private void deliver(CommandSender sender, Player target, String text) {
        String from = sender instanceof Player p ? p.getName() : CONSOLE;

        // [Ty ➜ Nick] wiadomość
        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + "Ty" + ChatColor.DARK_GRAY + " ➜ "
                + ChatColor.AQUA + target.getName() + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + text);

        // [Nick ➜ Ty] wiadomość
        target.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + from + ChatColor.DARK_GRAY + " ➜ "
                + ChatColor.GRAY + "Ty" + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + text);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);

        // zapamiętujemy rozmówców, żeby działało /r po obu stronach
        if (sender instanceof Player p) {
            lastPartner.put(p.getUniqueId(), target.getUniqueId());
            lastPartner.put(target.getUniqueId(), p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        lastPartner.remove(id);
        lastPartner.values().removeIf(id::equals);
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("msg") && args.length == 1) {
            String last = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        }
        return out;
    }
}
