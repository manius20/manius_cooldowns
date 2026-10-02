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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class CustomCooldowns extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Material, Integer> itemCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCooldownsFromConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("cooldownreload") != null) getCommand("cooldownreload").setExecutor(this);
    }

    public void loadCooldownsFromConfig() {
        itemCooldowns.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("cooldowns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    itemCooldowns.put(mat, section.getInt(key));
                } else {
                    getLogger().warning("Nie znaleziono przedmiotu o nazwie: " + key);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cooldownreload")) {
            if (!sender.hasPermission("customcooldowns.admin")) {
                sender.sendMessage(getMessage("messages.no-permission"));
                return true;
            }
            reloadConfig();
            loadCooldownsFromConfig();
            sender.sendMessage(getMessage("messages.config-reloaded"));
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        Material mat = item.getType();

        if (checkAndApplyCooldown(player, mat)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material mat = event.getItem().getType();

        if (checkAndApplyCooldown(player, mat)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        Material totemMat = Material.TOTEM_OF_UNDYING;
        if (mainHand.getType() == totemMat || offHand.getType() == totemMat) {
            if (player.hasCooldown(totemMat)) {
                event.setCancelled(true);
            } else if (itemCooldowns.containsKey(totemMat)) {
                int seconds = itemCooldowns.get(totemMat);
                player.setCooldown(totemMat, seconds * 20);
            }
        }
    }

    private boolean checkAndApplyCooldown(Player player, Material mat) {
        if (!itemCooldowns.containsKey(mat)) return false;

        if (player.hasCooldown(mat)) {
            int ticksLeft = player.getCooldown(mat);
            int secondsLeft = (ticksLeft / 20) + 1;

            String actionbarMsg = getConfig().getString("messages.cooldown-actionbar", "&cOdczekaj {time}s!")
                    .replace("{time}", String.valueOf(secondsLeft));
            player.sendActionBar(parseColor(actionbarMsg));
            return true;
        } else {
            int seconds = itemCooldowns.get(mat);
            player.setCooldown(mat, seconds * 20);
            return false;
        }
    }

    private Component getMessage(String path) {
        return parseColor(getConfig().getString(path, ""));
    }

    private Component parseColor(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
