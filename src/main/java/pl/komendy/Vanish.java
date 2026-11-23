package pl.komendy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /vanish: gracz znika całkowicie (to NIE jest niewidzialność z mikstury).
 * Inni gracze nie widzą go w świecie, w tab-liście, w podpowiedziach komend tego pluginu,
 * w liście serwera, w wiadomościach o wejściu/wyjściu/śmierci. Mob-y go nie namierzają.
 * Widzą go tylko osoby z uprawnieniem komendy.vanish.bypass.
 */
public class Vanish implements CommandExecutor, Listener {

    public static final String BYPASS = "komendy.vanish.bypass";

    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public Vanish(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vanished.yml");
        load();
    }

    // ------------------------------------------------------------------ stan

    private void load() {
        if (!file.exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("vanished")) {
            try {
                vanished.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // zły wpis - pomijamy
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : vanished) ids.add(id.toString());
        cfg.set("vanished", ids);
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udało się zapisać vanished.yml: " + ex.getMessage());
        }
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    /** Czy {@code viewer} może zobaczyć {@code target} (czyli czy target nie jest w vanish albo viewer ma bypass). */
    public boolean canSee(CommandSender viewer, Player target) {
        if (!isVanished(target)) return true;
        if (viewer instanceof Player v && v.getUniqueId().equals(target.getUniqueId())) return true;
        return viewer.hasPermission(BYPASS);
    }

    /** Jak Bukkit.getPlayerExact, ale zwraca null, gdy gracz jest w vanish i szukający nie ma bypass. */
    public Player find(CommandSender viewer, String name) {
        Player p = Bukkit.getPlayerExact(name);
        return p != null && canSee(viewer, p) ? p : null;
    }

    private void hideFromEveryone(Player target) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(target) || other.hasPermission(BYPASS)) continue;
            other.hidePlayer(plugin, target);
        }
    }

    private void showToEveryone(Player target) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(target)) continue;
            other.showPlayer(plugin, target);
        }
    }

    // ------------------------------------------------------------------ komenda

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "✘ " + ChatColor.GRAY + "Tę komendę może użyć tylko gracz.");
            return true;
        }

        if (vanished.remove(p.getUniqueId())) {
            save();
            showToEveryone(p);
            p.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Vanish "
                    + ChatColor.RED + "" + ChatColor.BOLD + "WYŁĄCZONY" + ChatColor.RESET + ChatColor.GRAY
                    + ". Jesteś znów widoczny dla wszystkich.");
        } else {
            vanished.add(p.getUniqueId());
            save();
            hideFromEveryone(p);
            p.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Vanish "
                    + ChatColor.GREEN + "" + ChatColor.BOLD + "WŁĄCZONY" + ChatColor.RESET + ChatColor.GRAY
                    + ". Nikt bez uprawnienia " + ChatColor.AQUA + BYPASS + ChatColor.GRAY + " Cię nie widzi.");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------ eventy

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();

        // wchodzący gracz nie ma widzieć tych, którzy są w vanish (chyba że ma bypass)
        if (!p.hasPermission(BYPASS)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p) && isVanished(other)) p.hidePlayer(plugin, other);
            }
        }

        // gracz, który był w vanish przed wyjściem, wchodzi po cichu i zostaje ukryty
        if (isVanished(p)) {
            ev.setJoinMessage(null);
            hideFromEveryone(p);
            p.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Jesteś w trybie " + ChatColor.AQUA + "vanish"
                    + ChatColor.GRAY + ". Wpisz " + ChatColor.WHITE + "/vanish" + ChatColor.GRAY + ", żeby się pokazać.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent ev) {
        if (isVanished(ev.getPlayer())) ev.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent ev) {
        if (isVanished(ev.getEntity())) ev.setDeathMessage(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent ev) {
        if (ev.getTarget() instanceof Player p && isVanished(p)) ev.setCancelled(true);
    }

    @EventHandler
    public void onPing(ServerListPingEvent ev) {
        try {
            Iterator<Player> it = ev.iterator();
            while (it.hasNext()) {
                if (isVanished(it.next())) it.remove();
            }
        } catch (UnsupportedOperationException ignored) {
            // niektóre implementacje nie pozwalają usuwać z listy
        }
    }
}
