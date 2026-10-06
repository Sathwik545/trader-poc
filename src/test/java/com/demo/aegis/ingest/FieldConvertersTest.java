package com.demo.aegis.ingest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FieldConvertersTest {

    @Test
    void epochFieldsAreMicrosecondsUtc() {
        assertThat(FieldConverters.epochMicrosToInstant(1783332848000000L)).isEqualTo(Instant.parse("2026-07-06T10:14:08Z"));
        assertThat(FieldConverters.epochMicrosToUtcDate(1783296000000000L)).isEqualTo(LocalDate.of(2026, 7, 6));
    }

    @Test
    void bytesLiteralBecomesBytes() {
        assertThat(FieldConverters.parseBytesLiteral("b'\\x00\\x00\\x00\\x00\\x00'")).isEqualTo(new byte[5]);
        assertThat(FieldConverters.parseBytesLiteral("b'\\x01'")).isEqualTo(new byte[] {1});
        assertThat(FieldConverters.parseBytesLiteral("b'A\\xff\\n'")).isEqualTo(new byte[] {'A', (byte) 0xFF, '\n'});
        assertThat(FieldConverters.parseBytesLiteral("b''")).isEmpty();
    }

    @Test
    void malformedBytesLiteralIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseBytesLiteral("0000"));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseBytesLiteral("b'\\x0'"));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseBytesLiteral("b'\\xzz'"));
    }

    @Test
    void cobDateIsYyyyMmDd() {
        assertThat(FieldConverters.parseCobDate("20260805")).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseCobDate("20261340"));
    }

    @Test
    void systemTimeIsHhMmSs() {
        assertThat(FieldConverters.parseTime("19:00:00")).isEqualTo(LocalTime.of(19, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseTime("25:00:00"));
    }

    @Test
    void numbersParse() {
        assertThat(FieldConverters.parseLong("-20000000")).isEqualTo(-20000000L);
        assertThat(FieldConverters.parseDecimal("12061.785")).isEqualByComparingTo(new BigDecimal("12061.785"));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldConverters.parseLong("abc"));
    }
}
