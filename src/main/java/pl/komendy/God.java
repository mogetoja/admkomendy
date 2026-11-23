package pl.komendy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /god: gracz nie dostaje obrażeń i nie głodnieje. Nie ma żadnego bypassa, tryb działa na każdego,
 * kto go ma włączonego. Wyjątki: pustka i /kill, żeby nie dało się utknąć w nieskończonym spadaniu.
 */
public class God implements CommandExecutor, TabCompleter, Listener {

    private static final String MODE = "tryb nieśmiertelności";

    private final JavaPlugin plugin;
    private final Vanish vanish;
    private final File file;
    private final Set<UUID> gods = ConcurrentHashMap.newKeySet();

    public God(JavaPlugin plugin, Vanish vanish) {
        this.plugin = plugin;
        this.vanish = vanish;
        this.file = new File(plugin.getDataFolder(), "gods.yml");
        load();
    }

    // ------------------------------------------------------------------ stan

    private void load() {
        if (!file.exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("gods")) {
            try {
                gods.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // zły wpis - pomijamy
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : gods) ids.add(id.toString());
        cfg.set("gods", ids);
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udało się zapisać gods.yml: " + ex.getMessage());
        }
    }

    private boolean isGod(Player p) {
        return gods.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------ komenda

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 1) {
            ModeNotice.usage(sender, "/god [nick]");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!sender.hasPermission("komendy.god.others")) {
                ModeNotice.err(sender, "Nie masz uprawnień do zmiany trybu nieśmiertelności innym graczom.");
                return true;
            }
            target = vanish.find(sender, args[0]);
            if (target == null) {
                ModeNotice.err(sender, "Gracz " + args[0] + " nie jest online.");
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                ModeNotice.err(sender, "Podaj nick gracza: /god <nick>");
                return true;
            }
            target = p;
        }

        boolean on;
        if (gods.remove(target.getUniqueId())) {
            on = false;
        } else {
            gods.add(target.getUniqueId());
            on = true;
            target.setFireTicks(0);
        }
        save();

        if (sender.equals(target)) {
            ModeNotice.self(target, MODE, on);
        } else {
            ModeNotice.other(sender, target, MODE, on);
        }
        return true;
    }

    // ------------------------------------------------------------------ eventy

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p) || !isGod(p)) return;
        EntityDamageEvent.DamageCause cause = ev.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL) return;
        ev.setCancelled(true);
        p.setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent ev) {
        if (ev.getEntity() instanceof Player p && isGod(p) && ev.getFoodLevel() < p.getFoodLevel()) {
            ev.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("komendy.god.others")) {
            String last = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (vanish.canSee(sender, p) && p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        }
        return out;
    }
}
