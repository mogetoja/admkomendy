package pl.komendy;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Wspólne komunikaty dla trybów (vanish, god): jednocześnie czat i title.
 * Dostają je tylko gracz, którego dotyczy zmiana, oraz administrator, który ją wykonał (nikt więcej).
 */
final class ModeNotice {

    private ModeNotice() {
    }

    static void err(CommandSender s, String msg) {
        s.sendMessage(ChatColor.RED + "✘ " + ChatColor.GRAY + msg);
    }

    static void usage(CommandSender s, String msg) {
        s.sendMessage(ChatColor.YELLOW + "➤ " + ChatColor.GRAY + "Użycie: " + ChatColor.WHITE + msg);
    }

    private static String text(String mode, boolean on) {
        return (on ? "Włączono " : "Wyłączono ") + mode;
    }

    private static ChatColor color(boolean on) {
        return on ? ChatColor.GREEN : ChatColor.RED;
    }

    private static void title(Player p, ChatColor c, String title, String subtitle) {
        p.sendTitle(c + "" + ChatColor.BOLD + title,
                subtitle.isEmpty() ? "" : c + "" + ChatColor.BOLD + subtitle,
                10, 50, 10);
    }

    /** Gracz zmienił tryb sobie. Czat (bez pogrubienia) i title (pogrubiony). */
    static void self(Player p, String mode, boolean on) {
        ChatColor c = color(on);
        String t = text(mode, on);
        p.sendMessage(c + t);
        title(p, c, t, "");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, on ? 1.4f : 0.8f);
    }

    /** Administrator zmienił tryb innemu graczowi. Gracz dostaje swoją wersję, administrator swoją. */
    static void other(CommandSender admin, Player target, String mode, boolean on) {
        self(target, mode, on);

        ChatColor c = color(on);
        String t = text(mode, on);
        admin.sendMessage(c + t + " dla gracza " + target.getName());
        if (admin instanceof Player a) title(a, c, t, "dla gracza " + target.getName());
    }
}
