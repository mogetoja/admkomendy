package pl.admkomendy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bany i wyciszenia (stałe i czasowe, po nicku i po IP). Kary zapisują się w punishments.yml.
 */
public class Punishments implements CommandExecutor, TabCompleter, Listener {

    private static final String PREFIX = "";

    private static final String DEFAULT_REASON = "Brak powodu";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d{1,9})([smhdw])");
    private static final long MAX_DURATION_MS = 3650L * 24L * 60L * 60L * 1000L; // 10 lat

    /** Komendy zablokowane dla wyciszonych graczy (poza zwykłym czatem). */
    private static final Set<String> MUTED_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "m", "t", "r", "reply", "me", "say", "pm", "emsg",
            "minecraft:msg", "minecraft:tell", "minecraft:w", "minecraft:me", "minecraft:say");

    private enum Type { BAN, MUTE }

    private static final class Entry {
        Type type;
        boolean ip;          // true = kara na adres IP
        String target;       // nick (małymi literami) albo adres IP
        String display;      // to, co pokazujemy na czacie (nick albo zamaskowane IP)
        String uuid;         // opcjonalnie, tylko gdy gracz był online
        long expires;        // 0 = na stałe
        String reason;
        String by;

        boolean active() {
            return expires == 0 || expires > System.currentTimeMillis();
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public Punishments(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punishments.yml");
        load();
    }

    // ------------------------------------------------------------------ zapis / odczyt

    private void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("entries");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Entry e = new Entry();
                e.type = Type.valueOf(s.getString("type", "BAN"));
                e.ip = s.getBoolean("ip");
                e.target = s.getString("target", "");
                e.display = s.getString("display", e.target);
                e.uuid = s.getString("uuid");
                e.expires = s.getLong("expires");
                e.reason = s.getString("reason", DEFAULT_REASON);
                e.by = s.getString("by", "Konsola");
                if (e.target.isEmpty() || !e.active()) continue;
                entries.add(e);
            } catch (IllegalArgumentException ignored) {
                // uszkodzony wpis - pomijamy
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Entry e : entries) {
            if (!e.active()) continue;
            String p = "entries." + (i++) + ".";
            cfg.set(p + "type", e.type.name());
            cfg.set(p + "ip", e.ip);
            cfg.set(p + "target", e.target);
            cfg.set(p + "display", e.display);
            cfg.set(p + "uuid", e.uuid);
            cfg.set(p + "expires", e.expires);
            cfg.set(p + "reason", e.reason);
            cfg.set(p + "by", e.by);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udało się zapisać punishments.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ komunikaty

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

    private String timeLeft(Entry e) {
        if (e.expires == 0) return "na stałe";
        return "pozostało " + formatDuration(e.expires - System.currentTimeMillis());
    }

    private String kickMessage(Entry e) {
        return ChatColor.RED + "" + ChatColor.BOLD + "ZOSTAŁEŚ ZBANOWANY" + (e.ip ? " (IP)" : "") + "\n\n"
                + ChatColor.GRAY + "Powód: " + ChatColor.WHITE + e.reason + "\n"
                + ChatColor.GRAY + "Czas: " + ChatColor.WHITE + timeLeft(e) + "\n"
                + ChatColor.GRAY + "Nałożył: " + ChatColor.WHITE + e.by;
    }

    private String muteMessage(Entry e) {
        return PREFIX + ChatColor.RED + "✘ " + ChatColor.GRAY + "Jesteś wyciszony. "
                + ChatColor.DARK_GRAY + "(" + ChatColor.WHITE + e.reason + ChatColor.DARK_GRAY + ", "
                + ChatColor.WHITE + timeLeft(e) + ChatColor.DARK_GRAY + ")";
    }

    // ------------------------------------------------------------------ czas

    /** Zamienia np. "1d12h30m" na milisekundy. Zwraca -1, gdy format jest zły. */
    private long parseDuration(String input) {
        String s = input.toLowerCase(Locale.ROOT);
        if (!s.matches("(\\d{1,9}[smhdw])+")) return -1;
        long total = 0;
        Matcher m = DURATION_PART.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L; // w
            };
            total += n * unit;
            if (total > MAX_DURATION_MS) return MAX_DURATION_MS;
        }
        return total;
    }

    private String formatDuration(long ms) {
        long secs = Math.max(0, (ms + 999) / 1000);
        long d = secs / 86400;
        long h = (secs % 86400) / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        List<String> parts = new ArrayList<>();
        if (d > 0) parts.add(d + "d");
        if (h > 0) parts.add(h + "h");
        if (m > 0) parts.add(m + "m");
        if (s > 0) parts.add(s + "s");
        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }

    // ------------------------------------------------------------------ pomocnicze

    private String ipOf(Player p) {
        if (p.getAddress() == null || p.getAddress().getAddress() == null) return null;
        return p.getAddress().getAddress().getHostAddress();
    }

    /** Żeby nie pokazywać pełnego IP na czacie: 192.168.*.* */
    private String maskIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return "adres IP";
        return parts[0] + "." + parts[1] + ".*.*";
    }

    private boolean matches(Entry e, Player p) {
        if (e.ip) return e.target.equals(ipOf(p));
        if (e.target.equals(p.getName().toLowerCase(Locale.ROOT))) return true;
        return e.uuid != null && e.uuid.equals(p.getUniqueId().toString());
    }

    private Entry find(Type type, String name, String uuid, String ip) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (e.type != type || !e.active()) continue;
            if (e.ip) {
                if (ip != null && e.target.equals(ip)) return e;
            } else {
                if (e.target.equals(lower)) return e;
                if (uuid != null && uuid.equals(e.uuid)) return e;
            }
        }
        return null;
    }

    private Entry findFor(Type type, Player p) {
        return find(type, p.getName(), p.getUniqueId().toString(), ipOf(p));
    }

    // ------------------------------------------------------------------ komendy

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "ban" -> punish(sender, args, Type.BAN, false, false);
            case "tempban" -> punish(sender, args, Type.BAN, true, false);
            case "tempbanip" -> punish(sender, args, Type.BAN, true, true);
            case "mute" -> punish(sender, args, Type.MUTE, false, false);
            case "tempmute" -> punish(sender, args, Type.MUTE, true, false);
            case "tempmuteip" -> punish(sender, args, Type.MUTE, true, true);
            case "unban" -> revoke(sender, args, Type.BAN);
            case "unmute" -> revoke(sender, args, Type.MUTE);
            default -> { return false; }
        }
        return true;
    }

    private void punish(CommandSender sender, String[] args, Type type, boolean temp, boolean ipMode) {
        String cmdName = (temp ? "temp" : "") + (type == Type.BAN ? "ban" : "mute") + (ipMode ? "ip" : "");
        int minArgs = temp ? 2 : 1;
        if (args.length < minArgs) {
            usage(sender, "/" + cmdName + " <" + (ipMode ? "nick|ip" : "nick") + ">" + (temp ? " <czas>" : "") + " [powód]");
            if (temp) {
                sender.sendMessage(ChatColor.DARK_GRAY + "   Czas: " + ChatColor.GRAY + "np. 30m, 2h, 7d, 1d12h " + ChatColor.DARK_GRAY + "(s/m/h/d/w)");
            }
            return;
        }

        long expires = 0;
        if (temp) {
            long ms = parseDuration(args[1]);
            if (ms <= 0) {
                err(sender, "Zły czas " + hl(args[1]) + ". Użyj np. " + hl("30m") + ", " + hl("2h") + ", " + hl("7d") + " albo " + hl("1d12h") + ".");
                return;
            }
            expires = System.currentTimeMillis() + ms;
        }

        int reasonStart = temp ? 2 : 1;
        String reason = args.length > reasonStart
                ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                : DEFAULT_REASON;

        String input = args[0];
        Player online = Bukkit.getPlayerExact(input);

        Entry e = new Entry();
        e.type = type;
        e.expires = expires;
        e.reason = reason;
        e.by = sender.getName();

        if (ipMode) {
            String ip;
            if (IPV4.matcher(input).matches()) {
                ip = input;
            } else if (online != null && ipOf(online) != null) {
                ip = ipOf(online);
            } else {
                err(sender, "Podaj adres IP albo nick gracza, który jest teraz online.");
                return;
            }
            if (sender instanceof Player self && ip.equals(ipOf(self))) {
                err(sender, "To jest Twój własny adres IP.");
                return;
            }
            e.ip = true;
            e.target = ip;
            e.display = online != null ? online.getName() : maskIp(ip);
            e.uuid = null;
        } else {
            if (sender instanceof Player self && self.getName().equalsIgnoreCase(input)) {
                err(sender, "Nie możesz ukarać samego siebie.");
                return;
            }
            e.ip = false;
            e.display = online != null ? online.getName() : input;
            e.target = e.display.toLowerCase(Locale.ROOT);
            e.uuid = online != null ? online.getUniqueId().toString() : null;
        }

        // nowa kara zastępuje starą tego samego typu dla tego samego celu
        entries.removeIf(old -> old.type == e.type && old.ip == e.ip && old.target.equals(e.target));
        entries.add(e);
        save();

        announce(e);
        enforce(e);
    }

    private void announce(Entry e) {
        String what;
        ChatColor color;
        if (e.type == Type.BAN) {
            what = e.ip ? "zbanowany na IP" : "zbanowany";
            color = ChatColor.RED;
        } else {
            what = e.ip ? "wyciszony na IP" : "wyciszony";
            color = ChatColor.GOLD;
        }
        String duration = e.expires == 0
                ? "na stałe"
                : "na " + hl(formatDuration(e.expires - System.currentTimeMillis()));
        Bukkit.broadcastMessage(PREFIX + color + "⚔ " + ChatColor.GRAY + "Gracz " + hl(e.display)
                + " został " + color + what + ChatColor.GRAY + " " + duration + " przez " + hl(e.by) + ".");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "   » " + ChatColor.GRAY + "Powód: " + ChatColor.WHITE + e.reason);
    }

    private void enforce(Entry e) {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!matches(e, p)) continue;
            if (e.type == Type.BAN) {
                p.kickPlayer(kickMessage(e));
            } else {
                p.sendMessage(muteMessage(e));
            }
        }
    }

    private void revoke(CommandSender sender, String[] args, Type type) {
        String cmdName = type == Type.BAN ? "unban" : "unmute";
        if (args.length != 1) {
            usage(sender, "/" + cmdName + " <nick|ip>");
            return;
        }
        String q = args[0];
        List<Entry> removed = new ArrayList<>();
        for (Entry e : entries) {
            if (e.type == type && e.active()
                    && (e.target.equalsIgnoreCase(q) || e.display.equalsIgnoreCase(q))) {
                removed.add(e);
            }
        }
        if (removed.isEmpty()) {
            err(sender, hl(q) + " nie ma aktywnego " + (type == Type.BAN ? "bana" : "wyciszenia") + ".");
            return;
        }
        entries.removeAll(removed);
        save();

        String what = type == Type.BAN ? "Odbanowano" : "Zdjęto wyciszenie z";
        String shown = removed.get(0).display;
        Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "✔ " + ChatColor.GRAY + what + " " + hl(shown)
                + " (przez " + hl(sender.getName()) + ").");

        if (type == Type.MUTE) {
            for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                for (Entry e : removed) {
                    if (matches(e, p)) {
                        p.sendMessage(PREFIX + ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Twoje wyciszenie zostało zdjęte.");
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventy

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent ev) {
        String ip = ev.getAddress() != null ? ev.getAddress().getHostAddress() : null;
        Entry e = find(Type.BAN, ev.getName(), ev.getUniqueId().toString(), ip);
        if (e != null) {
            ev.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage(e));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent ev) {
        Entry e = findFor(Type.MUTE, ev.getPlayer());
        if (e != null) {
            ev.setCancelled(true);
            ev.getPlayer().sendMessage(muteMessage(e));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandUse(PlayerCommandPreprocessEvent ev) {
        String msg = ev.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!MUTED_COMMANDS.contains(label)) return;
        Entry e = findFor(Type.MUTE, ev.getPlayer());
        if (e != null) {
            ev.setCancelled(true);
            ev.getPlayer().sendMessage(muteMessage(e));
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            if (name.equals("unban") || name.equals("unmute")) {
                Type t = name.equals("unban") ? Type.BAN : Type.MUTE;
                for (Entry e : entries) {
                    if (e.type == t && e.active()) out.add(e.display);
                }
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (args.length == 2 && name.startsWith("temp")) {
            out.addAll(Arrays.asList("30m", "1h", "12h", "1d", "7d", "30d"));
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
