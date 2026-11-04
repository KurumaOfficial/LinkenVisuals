package ru.method.linkenvisuals.utils.inv;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Единый парсер цены лота из имени/лора предмета (аукцион artygrief: «Цена $1,000,000»).
 * Число берётся СТРОГО рядом с ценовым ключом (цена/$/монет…), иначе -1 — чтобы не поймать срок
 * («6 дн») или бонусы брони («+6»). Используется и подсветкой (AuctionHelper), и AutoRelist.
 */
public final class AuctionPrice {

    // Число: группировка тысяч (1 000 000) ИЛИ дробь (1.5) ИЛИ целое, затем опциональный суффикс k/kk/m.
    private static final Pattern PRICE = Pattern.compile(
            "((?:\\d{1,3}(?:[ .,]\\d{3})+)|(?:\\d+(?:[.,]\\d+)?))\\s*(ккк|kkk|кк|kk|мм|mm|м|m|к|k)?");

    private AuctionPrice() {
    }

    /** Цена лота (или -1, если ценового ключа рядом с числом нет). */
    public static long parse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        StringBuilder sb = new StringBuilder();
        sb.append(strip(stack.getName().getString())).append(' ');
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) sb.append(strip(line.getString())).append(' ');
        }
        return findPrice(sb.toString().toLowerCase());
    }

    private static long findPrice(String s) {
        if (s == null || s.isEmpty()) return -1;
        Matcher m = PRICE.matcher(s);
        long best = -1;
        while (m.find()) {
            long v = amount(m.group(1), m.group(2));
            if (v <= 0) continue;
            int cs = Math.max(0, m.start() - 24);
            int ce = Math.min(s.length(), m.end() + 6);
            String ctx = s.substring(cs, ce);
            boolean kw = ctx.contains("цена") || ctx.contains("price") || ctx.contains("стоим")
                    || ctx.contains("$") || ctx.contains("монет") || ctx.contains("коин")
                    || ctx.contains("coins") || ctx.contains("руб") || ctx.contains("купить");
            if (kw && v > best) best = v;
        }
        return best;
    }

    private static long amount(String numRaw, String suf) {
        if (numRaw == null) return -1;
        long mult = suffixMult(suf);
        String s = numRaw.trim().replace(" ", "");
        if (mult > 1) {
            s = s.replace(",", ".");
            int dots = 0;
            for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '.') dots++;
            try {
                double d = dots <= 1 ? Double.parseDouble(s) : Double.parseDouble(s.replace(".", ""));
                return (long) Math.floor(d * mult);
            } catch (Exception e) {
                return digits(s) * mult;
            }
        }
        return digits(s);
    }

    private static long suffixMult(String suf) {
        if (suf == null) return 1L;
        return switch (suf) {
            case "к", "k" -> 1_000L;
            case "кк", "kk", "м", "m", "мм", "mm" -> 1_000_000L;
            case "ккк", "kkk" -> 1_000_000_000L;
            default -> 1L;
        };
    }

    private static long digits(String raw) {
        long v = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') v = v * 10L + (c - '0');
        }
        return v;
    }

    private static String strip(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
