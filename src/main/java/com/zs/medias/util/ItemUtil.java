package com.zs.medias.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemUtil {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final String PLACEHOLDER = "PEGAR_TEXTURE_AQUI";

    private ItemUtil() {
    }

    /** Material desde un texto; si es inválido devuelve el fallback. */
    public static Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }

    /** Crea un ítem con nombre y lore (acepta colores y hex). */
    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        apply(item, name, lore);
        return item;
    }

    /** Aplica nombre y lore a un ítem existente (conserva la textura de las cabezas). */
    public static void apply(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (name != null) {
            meta.setDisplayName(Text.color(name));
        }
        if (lore != null) {
            meta.setLore(Text.color(lore));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    /** Hace brillar el ítem sin mostrar el encantamiento. */
    public static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (enchantment == null) {
            return;
        }
        meta.addEnchant(enchantment, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    /**
     * Crea una cabeza con la textura indicada (valor base64 de minecraft-heads.com o URL directa).
     * Si la textura está vacía o es inválida devuelve una cabeza normal.
     */
    public static ItemStack createHead(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        URL skin = parseSkin(texture);
        if (skin == null) {
            return head;
        }

        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(texture.trim().getBytes(StandardCharsets.UTF_8))
            );
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(skin);
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
            head.setItemMeta(skull);
        }
        return head;
    }

    /** Obtiene el valor base64 de la textura de una cabeza, o null si no tiene. */
    public static String getTexture(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof SkullMeta skull)) {
            return null;
        }

        PlayerProfile profile = skull.getOwnerProfile();
        if (profile == null) {
            return null;
        }

        URL skin = profile.getTextures().getSkin();
        if (skin == null) {
            return null;
        }

        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skin + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static URL parseSkin(String texture) {
        if (texture == null) {
            return null;
        }

        String value = texture.trim();
        if (value.isEmpty() || value.equalsIgnoreCase(PLACEHOLDER)) {
            return null;
        }

        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return new URL(value);
            }

            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (matcher.find()) {
                return new URL(matcher.group(1));
            }
        } catch (IllegalArgumentException | MalformedURLException ignored) {
            // textura inválida: se usa una cabeza normal
        }
        return null;
    }
        }
