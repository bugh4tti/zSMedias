package com.zs.medias.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    // Acepta &#RRGGBB y #RRGGBB
    private static final Pattern HEX = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private static final String CENTER_TAG = "[center]";
    private static final int CENTER_PX = 154;

    private Text() {
    }

    /** Traduce &colores, &#RRGGBB y #RRGGBB. */
    public static String color(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = ChatColor.of("#" + matcher.group(1)).toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> color(List<String> lines) {
        List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(color(line));
        }
        return result;
    }

    /** Quita todos los colores y formatos. */
    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    /** Reemplaza pares clave/valor: replace(texto, "{player}", nombre, "{link}", link). */
    public static String replace(String text, String... pairs) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = pairs[i + 1] == null ? "" : pairs[i + 1];
            text = text.replace(pairs[i], value);
        }
        return text;
    }

    public static List<String> replace(List<String> lines, String... pairs) {
        List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(replace(line, pairs));
        }
        return result;
    }

    public static boolean isCentered(String raw) {
        return raw != null && raw.startsWith(CENTER_TAG);
    }

    /** Procesa una línea: colores y, si empieza con [center], la centra. */
    public static String line(String raw) {
        if (raw == null) {
            return "";
        }
        if (isCentered(raw)) {
            return center(color(raw.substring(CENTER_TAG.length())));
        }
        return color(raw);
    }

    /** Centra un texto ya coloreado en el chat. */
    public static String center(String colored) {
        if (colored == null || colored.isEmpty()) {
            return "";
        }

        int pixels = 0;
        boolean bold = false;

        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == '§' && i + 1 < colored.length()) {
                char code = Character.toLowerCase(colored.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if (code != 'k' && code != 'm' && code != 'n' && code != 'o') {
                    bold = false;
                }
                i++;
                continue;
            }
            pixels += charWidth(c, bold);
        }

        int toCompensate = CENTER_PX - (pixels / 2);
        if (toCompensate <= 0) {
            return colored;
        }

        StringBuilder sb = new StringBuilder();
        int compensated = 0;
        while (compensated < toCompensate) {
            sb.append(' ');
            compensated += 4;
        }
        return sb + colored;
    }

    private static int charWidth(char c, boolean bold) {
        int width;
        switch (c) {
            case '!':
            case '.':
            case ',':
            case ':':
            case ';':
            case 'i':
            case '|':
            case '\'':
                width = 2;
                break;
            case 'l':
            case '`':
                width = 3;
                break;
            case 'I':
            case 't':
            case ' ':
            case '[':
            case ']':
            case '"':
                width = 4;
                break;
            case 'f':
            case 'k':
            case '<':
            case '>':
            case '(':
            case ')':
            case '{':
            case '}':
            case '*':
                width = 5;
                break;
            case '@':
            case '~':
                width = 7;
                break;
            default:
                width = 6;
                break;
        }
        return bold ? width + 1 : width;
    }

    /** Formatea segundos como "1d 3h 20m" (con segundos si es menos de 1 hora). */
    public static String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (days == 0 && hours == 0 && (secs > 0 || minutes == 0)) {
            sb.append(secs).append("s");
        }
        return sb.toString().trim();
    }
}
