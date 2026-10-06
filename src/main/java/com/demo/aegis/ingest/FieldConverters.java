package com.demo.aegis.ingest;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;

/**
 * Converts raw cell text into typed values. Every method throws IllegalArgumentException
 * (or a subclass) on bad input; the caller turns that into a row error.
 */
public final class FieldConverters {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter COB_DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private FieldConverters() {
    }

    public static long parseLong(String text) {
        return Long.parseLong(text.trim());
    }

    public static BigDecimal parseDecimal(String text) {
        return new BigDecimal(text.trim());
    }

    /** Epoch fields in the trade file are microseconds since 1970-01-01 UTC. */
    public static Instant epochMicrosToInstant(long micros) {
        return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
    }

    public static LocalDate epochMicrosToUtcDate(long micros) {
        return epochMicrosToInstant(micros).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** M_TP_TIMSYS format: HH:mm:ss. */
    public static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim(), TIME);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a HH:mm:ss time");
        }
    }

    /** COB_DATE format: integer yyyyMMdd. */
    public static LocalDate parseCobDate(String text) {
        try {
            return LocalDate.parse(text.trim(), COB_DATE);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a yyyyMMdd date");
        }
    }

    /** Parses a Python bytes literal such as {@code b'\x00\x01'} into the bytes it denotes. */
    public static byte[] parseBytesLiteral(String text) {
        String s = text.trim();
        if (s.length() < 3 || s.charAt(0) != 'b' || (s.charAt(1) != '\'' && s.charAt(1) != '"')
                || s.charAt(s.length() - 1) != s.charAt(1)) {
            throw new IllegalArgumentException("not a bytes literal");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int end = s.length() - 1;
        for (int i = 2; i < end; i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                if (c > 0x7F) {
                    throw new IllegalArgumentException("non-ASCII character in bytes literal");
                }
                out.write(c);
                continue;
            }
            if (++i >= end) {
                throw new IllegalArgumentException("dangling escape in bytes literal");
            }
            char escape = s.charAt(i);
            switch (escape) {
                case 'x' -> {
                    if (i + 2 >= end) {
                        throw new IllegalArgumentException("incomplete \\x escape in bytes literal");
                    }
                    out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                }
                case 'n' -> out.write('\n');
                case 'r' -> out.write('\r');
                case 't' -> out.write('\t');
                case '0' -> out.write(0);
                case '\\', '\'', '"' -> out.write(escape);
                default -> throw new IllegalArgumentException("unsupported escape \\" + escape + " in bytes literal");
            }
        }
        return out.toByteArray();
    }
}
