package ru.method.linkenvisuals.utils.net;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Проверка подписи ответов бэкенда (HMAC-SHA256). Сервер (linkenvisuals_suite.py) подписывает тело
 * ответа общим секретом и кладёт его в заголовок {@code X-Signature}; клиент считает ту же подпись
 * и отбрасывает ответы без валидной. Это защищает от подделки ответа по открытому HTTP (MITM):
 * подмена состава пати/косметики, форс kill-switch запуска.
 *
 * <p>Секрет НЕ хранится строкой-константой (её нашёл бы {@code strings} по jar). Он лежит
 * XOR-обфусцированным в {@link #K}, а ключ XOR вшит прямо в {@link #secret()}. Защита J2C
 * виртуализирует методы этого класса в нативный код, поэтому в финальной джарке ни строки секрета,
 * ни ключа XOR в байткоде нет — их можно достать только реверсом нативной библиотеки.
 *
 * <p>Если реконструированный секрет пуст или начинается с {@code __} (плейсхолдер) — проверка
 * выключена (обратная совместимость: старый сервер без подписи продолжает работать).
 *
 * <p>Ограничение: секрет всё равно присутствует в распространяемом клиенте (в нативном виде),
 * поэтому защищает от пассивного MITM (общий Wi-Fi/роутер/ISP) и от простого просмотра jar, но не
 * от целенаправленного реверса нативной библиотеки. Для полной защиты канала нужен HTTPS — это уже
 * серверная инфраструктура.
 */
public final class SyncSignature {

    // XOR-обфусцированные байты секрета. Осмысленны только вместе с ключом из secret() (тот — в нативе).
    private static final byte[] K = {
            (byte) 0x39, (byte) 0x2d, (byte) 0x0a, (byte) 0x47, (byte) 0xf1, (byte) 0xbc, (byte) 0x1f, (byte) 0x51,
            (byte) 0xe0, (byte) 0x77, (byte) 0x96, (byte) 0x09, (byte) 0xa8, (byte) 0x8c, (byte) 0x7b, (byte) 0xd5,
            (byte) 0x6e, (byte) 0x2c, (byte) 0x56, (byte) 0x42, (byte) 0xa2, (byte) 0xbf, (byte) 0x1e, (byte) 0x5a,
            (byte) 0xe6, (byte) 0x76, (byte) 0xc3, (byte) 0x09, (byte) 0xa5, (byte) 0xda, (byte) 0x2b, (byte) 0x86,
            (byte) 0x3e, (byte) 0x7b, (byte) 0x52, (byte) 0x46, (byte) 0xa0, (byte) 0xe8, (byte) 0x4b, (byte) 0x0b,
            (byte) 0xe1, (byte) 0x77, (byte) 0x93, (byte) 0x0a, (byte) 0xf7, (byte) 0xd9, (byte) 0x79, (byte) 0x80,
            (byte) 0x38, (byte) 0x7e, (byte) 0x06, (byte) 0x44, (byte) 0xf6, (byte) 0xbc, (byte) 0x4a, (byte) 0x5c,
            (byte) 0xe3, (byte) 0x77, (byte) 0x9e, (byte) 0x0d, (byte) 0xa4, (byte) 0x8c, (byte) 0x7c, (byte) 0x8f
    };

    private SyncSignature() {
    }

    /**
     * Восстанавливает секрет из XOR-обфусцированных байт. Ключ вшит инлайном — после виртуализации J2C
     * и он, и логика реконструкции существуют только в нативной библиотеке.
     */
    private static String secret() {
        byte[] key = {
                (byte) 0x5a, (byte) 0x1f, (byte) 0x33, (byte) 0x77, (byte) 0xc4, (byte) 0x8b, (byte) 0x2e, (byte) 0x69,
                (byte) 0xd0, (byte) 0x14, (byte) 0xa7, (byte) 0x3c, (byte) 0x91, (byte) 0xe8, (byte) 0x4d, (byte) 0xb6
        };
        byte[] out = new byte[K.length];
        for (int i = 0; i < K.length; i++) {
            out[i] = (byte) (K[i] ^ key[i % key.length]);
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    /** Настроена ли проверка подписи (секрет непустой и не плейсхолдер). */
    public static boolean enabled() {
        String s = secret();
        return !s.isBlank() && !s.startsWith("__");
    }

    /**
     * Доверять ли ответу сервера. Если проверка выключена — доверяем всему (старое поведение).
     * Иначе требуем валидную подпись тела в заголовке {@code X-Signature}.
     *
     * @param body      сырое тело ответа (та же строка, что пришла от сервера)
     * @param signature значение заголовка X-Signature (может быть null)
     */
    public static boolean verify(String body, String signature) {
        if (!enabled()) {
            return true;
        }
        if (body == null || signature == null || signature.isBlank()) {
            return false;
        }
        String expected = hmacHex(body);
        return expected != null && constantTimeEquals(expected, signature.trim());
    }

    private static String hmacHex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Сравнение без утечки времени (не раскрывает, сколько символов совпало). */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
