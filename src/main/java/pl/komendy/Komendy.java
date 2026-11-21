package pl.komendy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Komendy extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "";

    @Override
    public void onEnable() {
        for (String name : new String[]{"tp", "gm", "fly", "flyspeed"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        Punishments punishments = new Punishments(this);
        for (String name : new String[]{"ban", "tempban", "tempbanip", "mute", "tempmute", "tempmuteip", "unban", "unmute"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(punishments);
                getCommand(name).setTabCompleter(punishments);
            }
        }
        getServer().getPluginManager().registerEvents(punishments, this);
        getLogger().info("Plugin wlaczony!");
    }

    // ------------------------------------------------------------------ helpers

    private void ok(CommandSender s, String msg) {
        s.sendMessage(PREFIX + ChatColor.GREEN + "✔ " + ChatColor.GRAY + msg);
    }

    private void err(CommandSender s, String msg) {
        s.sendMessage(PREFIX + ChatColor.RED + "✘ " + ChatColor.GRAY + msg);
    }

    private void usage(CommandSender s, String msg) {
        s.sendMessage(PREFIX + ChatColor.YELLOW + "➤ " + ChatColor.GRAY + "Użycie: " + ChatColor.WHITE + msg);
    }

    private String hl(String text) {
        return ChatColor.AQUA + text + ChatColor.GRAY;
    }

    private void ding(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    private Double parseCoord(String input, double base) {
        try {
            if (input.startsWith("~")) {
                if (input.length() == 1) return base;
                return base + Double.parseDouble(input.substring(1));
            }
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean looksLikeCoord(String s) {
        return s.startsWith("~") || s.matches("-?\\d+(\\.\\d+)?");
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "tp" -> handleTp(sender, args);
            case "gm" -> handleGm(sender, args);
            case "fly" -> handleFly(sender, args);
            case "flyspeed" -> handleFlySpeed(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void handleTp(CommandSender sender, String[] args) {
        // /tp <nick>
        if (args.length == 1) {
            if (!(sender instanceof Player self)) { err(sender, "Tę komendę może użyć tylko gracz."); return; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { err(sender, "Gracz " + hl(args[0]) + " nie jest online."); return; }
            self.teleport(target.getLocation());
            ok(self, "Teleportowano do gracza " + hl(target.getName()) + ".");
            ding(self);
            return;
        }

        // /tp <nick> <nick2>
        if (args.length == 2) {
            Player who = Bukkit.getPlayerExact(args[0]);
            Player target = Bukkit.getPlayerExact(args[1]);
            if (who == null) { err(sender, "Gracz " + hl(args[0]) + " nie jest online."); return; }
            if (target == null) { err(sender, "Gracz " + hl(args[1]) + " nie jest online."); return; }
            who.teleport(target.getLocation());
            ok(sender, "Teleportowano " + hl(who.getName()) + " do " + hl(target.getName()) + ".");
            if (sender != who) ok(who, "Zostałeś teleportowany do " + hl(target.getName()) + ".");
            ding(who);
            return;
        }

        // /tp <x> <y> <z>
        if (args.length == 3) {
            if (!(sender instanceof Player self)) { err(sender, "Tę komendę może użyć tylko gracz."); return; }
            teleportToCoords(sender, self, args[0], args[1], args[2]);
            return;
        }

        // /tp <nick> <x> <y> <z>
        if (args.length == 4) {
            Player who = Bukkit.getPlayerExact(args[0]);
            if (who == null) { err(sender, "Gracz " + hl(args[0]) + " nie jest online."); return; }
            teleportToCoords(sender, who, args[1], args[2], args[3]);
            return;
        }

        usage(sender, "/tp <nick> " + ChatColor.GRAY + "|" + ChatColor.WHITE + " /tp <nick> <nick2> "
                + ChatColor.GRAY + "|" + ChatColor.WHITE + " /tp <x> <y> <z> "
                + ChatColor.GRAY + "|" + ChatColor.WHITE + " /tp <nick> <x> <y> <z>");
    }

    private void teleportToCoords(CommandSender sender, Player who, String xs, String ys, String zs) {
        Location base = who.getLocation();
        Double x = parseCoord(xs, base.getX());
        Double y = parseCoord(ys, base.getY());
        Double z = parseCoord(zs, base.getZ());
        if (x == null || y == null || z == null) {
            err(sender, "Nieprawidłowe koordynaty. Użyj liczb lub " + hl("~") + ".");
            return;
        }
        Location dest = new Location(base.getWorld(), x, y, z, base.getYaw(), base.getPitch());
        who.teleport(dest);
        String coords = hl(String.format(Locale.US, "%.1f, %.1f, %.1f", x, y, z));
        if (sender == who) {
            ok(who, "Teleportowano na koordynaty " + coords + ".");
        } else {
            ok(sender, "Teleportowano " + hl(who.getName()) + " na " + coords + ".");
            ok(who, "Zostałeś teleportowany na " + coords + ".");
        }
        ding(who);
    }

    private void handleGm(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            usage(sender, "/gm <0|1|2|3> [nick]");
            sender.sendMessage(ChatColor.DARK_GRAY + "   0 " + ChatColor.GRAY + "Survival  "
                    + ChatColor.DARK_GRAY + "1 " + ChatColor.GRAY + "Creative  "
                    + ChatColor.DARK_GRAY + "2 " + ChatColor.GRAY + "Adventure  "
                    + ChatColor.DARK_GRAY + "3 " + ChatColor.GRAY + "Spectator");
            return;
        }

        GameMode mode;
        String modeName;
        ChatColor color;
        switch (args[0]) {
            case "0" -> { mode = GameMode.SURVIVAL;  modeName = "Survival";  color = ChatColor.GREEN; }
            case "1" -> { mode = GameMode.CREATIVE;  modeName = "Creative";  color = ChatColor.GOLD; }
            case "2" -> { mode = GameMode.ADVENTURE; modeName = "Adventure"; color = ChatColor.AQUA; }
            case "3" -> { mode = GameMode.SPECTATOR; modeName = "Spectator"; color = ChatColor.LIGHT_PURPLE; }
            default -> { err(sender, "Tryb musi być liczbą od " + hl("0") + " do " + hl("3") + "."); return; }
        }

        Player target;
        if (args.length == 2) {
            if (!sender.hasPermission("komendy.others")) { err(sender, "Nie masz uprawnień do zmiany trybu innym graczom."); return; }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { err(sender, "Gracz " + hl(args[1]) + " nie jest online."); return; }
        } else {
            if (!(sender instanceof Player p)) { err(sender, "Podaj nick gracza: /gm <0-3> <nick>"); return; }
            target = p;
        }

        target.setGameMode(mode);
        String shown = color + "" + ChatColor.BOLD + modeName + ChatColor.RESET + ChatColor.GRAY;
        ok(target, "Twój tryb gry to teraz " + shown + ".");
        if (sender != target) ok(sender, "Ustawiono tryb " + shown + " dla gracza " + hl(target.getName()) + ".");
        ding(target);
    }

    private void handleFly(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("komendy.others")) { err(sender, "Nie masz uprawnień do zmiany latania innym graczom."); return; }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { err(sender, "Gracz " + hl(args[0]) + " nie jest online."); return; }
        } else {
            if (!(sender instanceof Player p)) { err(sender, "Podaj nick gracza: /fly <nick>"); return; }
            target = p;
        }

        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);
        if (!enable) target.setFlying(false);

        String state = enable
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "WŁĄCZONE" + ChatColor.RESET + ChatColor.GRAY
                : ChatColor.RED + "" + ChatColor.BOLD + "WYŁĄCZONE" + ChatColor.RESET + ChatColor.GRAY;
        ok(target, "Latanie jest teraz " + state + ".");
        if (sender != target) ok(sender, "Latanie dla gracza " + hl(target.getName()) + ": " + state + ".");
        ding(target);
    }

    private void handleFlySpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { err(sender, "Tę komendę może użyć tylko gracz."); return; }
        if (args.length != 1) { usage(sender, "/flyspeed <1-10>"); return; }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            err(sender, "Prędkość musi być liczbą całkowitą od " + hl("1") + " do " + hl("10") + ".");
            return;
        }
        if (level < 1 || level > 10) {
            err(sender, "Prędkość musi być w zakresie od " + hl("1") + " do " + hl("10") + ".");
            return;
        }

        p.setFlySpeed(level / 10f);

        StringBuilder bar = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            bar.append(i <= level ? ChatColor.AQUA : ChatColor.DARK_GRAY).append("▌");
        }
        ok(p, "Prędkość latania ustawiona na " + hl(String.valueOf(level)) + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + "10 " + bar);
        ding(p);
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        switch (name) {
            case "tp" -> {
                if (args.length == 1 || (args.length == 2 && !looksLikeCoord(args[0]))) addPlayers(out, last);
                if (args.length == 1) out.add("~");
            }
            case "gm" -> {
                if (args.length == 1) out.addAll(Arrays.asList("0", "1", "2", "3"));
                else if (args.length == 2) addPlayers(out, last);
            }
            case "fly" -> { if (args.length == 1) addPlayers(out, last); }
            case "flyspeed" -> {
                if (args.length == 1) for (int i = 1; i <= 10; i++) out.add(String.valueOf(i));
            }
            default -> { }
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void addPlayers(List<String> out, String prefix) {
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
    }
}
