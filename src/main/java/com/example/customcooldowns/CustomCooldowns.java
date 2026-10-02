package com.example.customcooldowns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CustomCooldowns extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Material, Integer> configCooldowns = new HashMap<>();
    // Mapa: UUID gracza -> (Material -> Czas w ms, kiedy cooldown wygaśnie)
    private final Map<UUID, Map<Material, Long>> activeCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCooldownsFromConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("cooldownreload") != null) getCommand("cooldownreload").setExecutor(this);
    }

    public void loadCooldownsFromConfig() {
        configCooldowns.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("cooldowns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    configCooldowns.put(mat, section.getInt(key));
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cooldownreload")) {
            if (!sender.hasPermission("customcooldowns.admin")) {
                sender.sendMessage(parseColor(getConfig().getString("messages.no-permission", "&cBrak uprawnień!")));
                return true;
            }
            reloadConfig();
            loadCooldownsFromConfig();
            sender.sendMessage(parseColor(getConfig().getString("messages.config-reloaded", "&aPrzeładowano!")));
            return true;
        }
        return false;
    }

    // 1. BLOKADA I REJESTRACJA DLA JEDZENIA (Złote jabłka, koksy, chorusy)
    // Wywołuje się DOPIERO GDY GRACZ SKOŃCZY EATOWANIE
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material mat = event.getItem().getType();

        if (isCooldownActive(player, mat)) {
            event.setCancelled(true);
            sendCooldownMessage(player, mat);
            return;
        }

        // Zjedzenie zakończone -> startujemy odliczanie od TERAZ
        startCooldown(player, mat);
    }

    // 2. BLOKADA PRZED ROZPOCZĘCIEM JEDZENIA / RZUCANIA PERŁY
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        Material mat = item.getType();

        if (isCooldownActive(player, mat)) {
            event.setCancelled(true);
            sendCooldownMessage(player, mat);
            return;
        }

        // Przedmioty natychmiastowe (nie jedzenie), np. Ender Pearl
        if (mat == Material.ENDER_PEARL) {
            startCooldown(player, mat);
        }
    }

    // 3. OBSŁUGA TOTEMU
    @EventHandler(priority = EventPriority.HIGH)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Material mat = Material.TOTEM_OF_UNDYING;

        if (isCooldownActive(player, mat)) {
            event.setCancelled(true);
            sendCooldownMessage(player, mat);
            return;
        }

        startCooldown(player, mat);
    }

    private boolean isCooldownActive(Player player, Material mat) {
        if (!configCooldowns.containsKey(mat)) return false;

        Map<Material, Long> playerMap = activeCooldowns.get(player.getUniqueId());
        if (playerMap == null || !playerMap.containsKey(mat)) return false;

        long expireTime = playerMap.get(mat);
        return System.currentTimeMillis() < expireTime;
    }

    private void startCooldown(Player player, Material mat) {
        if (!configCooldowns.containsKey(mat)) return;

        int seconds = configCooldowns.get(mat);
        long expireTime = System.currentTimeMillis() + (seconds * 1000L);

        activeCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(mat, expireTime);
    }

    private void sendCooldownMessage(Player player, Material mat) {
        Map<Material, Long> playerMap = activeCooldowns.get(player.getUniqueId());
        if (playerMap == null || !playerMap.containsKey(mat)) return;

        long expireTime = playerMap.get(mat);
        long millisLeft = expireTime - System.currentTimeMillis();
        long secondsLeft = (millisLeft / 1000) + 1;

        String msg = getConfig().getString("messages.cooldown-actionbar", "&cOdczekaj {time}s!")
                .replace("{time}", String.valueOf(secondsLeft));
        player.sendActionBar(parseColor(msg));
    }

    private Component parseColor(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
