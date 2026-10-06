package com.demo.aegis.ingest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CSV reading without a database, using the header and first row of the synthetic file. */
class CsvTradeSourceTest {

    private static final TradeSchema SCHEMA = new TradeSchema();
    private static final TradeParser PARSER = new TradeParser(SCHEMA);

    private static String header;
    private static String goodRow;

    @BeforeAll
    static void readSampleLines() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Path.of("docs", "ada_deals_synthetic_10000.csv"), StandardCharsets.UTF_8)) {
            header = reader.readLine();
            goodRow = reader.readLine();
        }
    }

    @Test
    void schemaHasThe121Columns() {
        assertThat(SCHEMA.size()).isEqualTo(121);
        assertThat(SCHEMA.columns().get(0)).isEqualTo(new TradeColumn("M_NB", ColumnType.INTEGER));
        assertThat(SCHEMA.columns().get(SCHEMA.indexOf("M_TP_DTETRN")).type()).isEqualTo(ColumnType.EPOCH_MICROS);
        assertThat(SCHEMA.columns().get(SCHEMA.indexOf("M_EQ_PO")).type()).isEqualTo(ColumnType.BINARY);
        assertThat(SCHEMA.columns().get(SCHEMA.indexOf("OFF_PREMISES")).type()).isEqualTo(ColumnType.TEXT);
    }

    @Test
    void rowIsParsedIntoTypedValues() {
        Collected collected = read(header + "\n" + goodRow + "\n");

        assertThat(collected.errors).isEmpty();
        assertThat(collected.rows).hasSize(1);
        TradeRow row = collected.rows.get(0);
        assertThat(row.rowNumber()).isEqualTo(1);
        assertThat(row.tradeDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(value(row, "M_NB")).isEqualTo(337000003L);
        assertThat(value(row, "M_TP_DTETRN")).isEqualTo(1783296000000000L);
        assertThat(value(row, "M_TP_TIMSYS")).isEqualTo("10:14:08");
        assertThat(value(row, "M_EQ_PO")).isEqualTo(new byte[5]);
        assertThat(value(row, "COB_DATE")).isEqualTo(20260805L);
        assertThat(value(row, "M_TRN_TYPE")).as("empty cell").isNull();
    }

    @Test
    void byteOrderMarkBeforeTheHeaderIsIgnored() {
        Collected collected = read("﻿" + header + "\n" + goodRow + "\n");

        assertThat(collected.errors).isEmpty();
        assertThat(collected.rows).hasSize(1);
    }

    @Test
    void columnsAreMatchedByNameSoOrderAndExtrasDoNotMatter() {
        // move the first column (M_NB) to the end and add a column the schema does not know
        String reorderedHeader = header.substring(header.indexOf(',') + 1) + ",EXTRA,M_NB";
        String reorderedRow = goodRow.substring(goodRow.indexOf(',') + 1) + ",ignored," + goodRow.substring(0, goodRow.indexOf(','));

        Collected collected = read(reorderedHeader + "\n" + reorderedRow + "\n");

        assertThat(collected.errors).isEmpty();
        assertThat(value(collected.rows.get(0), "M_NB")).isEqualTo(337000003L);
        assertThat(value(collected.rows.get(0), "M_CNT_TYPO")).isEqualTo("IRS Vanilla");
    }

    @Test
    void badRowsAreReportedAndReadingContinues() {
        String badTime = goodRow.replace(",10:14:08,", ",25:99:00,");
        String badCobDate = goodRow.substring(0, goodRow.lastIndexOf(',')) + ",20261399";
        String noTradeDate = goodRow.replaceFirst(",1783296000000000,", ",,");

        Collected collected = read(String.join("\n", header, badTime, goodRow, "too,short", badCobDate, noTradeDate, goodRow) + "\n");

        assertThat(collected.rows).extracting(TradeRow::rowNumber).containsExactly(2L, 6L);
        assertThat(collected.errors).hasSize(4);
        assertThat(collected.errors.get(0)).startsWith("1: M_TP_TIMSYS");
        assertThat(collected.errors.get(1)).startsWith("3: expected 121 columns but the row has 2");
        assertThat(collected.errors.get(2)).startsWith("4: COB_DATE");
        assertThat(collected.errors.get(3)).startsWith("5: M_TP_DTETRN");
    }

    @Test
    void fileWithMissingColumnsIsRejectedAsAWhole() {
        String withoutLastColumn = header.substring(0, header.lastIndexOf(','));

        assertThatThrownBy(() -> read(withoutLastColumn + "\n"))
                .isInstanceOf(InvalidTradeFileException.class)
                .hasMessageContaining("missing 1 of the 121 expected columns: COB_DATE");
        assertThatThrownBy(() -> read(""))
                .isInstanceOf(InvalidTradeFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void brokenCsvIsRejectedWithTheLastGoodRowNumber() {
        String unclosedQuote = "\"" + goodRow;

        assertThatThrownBy(() -> read(header + "\n" + goodRow + "\n" + unclosedQuote + "\n"))
                .isInstanceOf(InvalidTradeFileException.class)
                .hasMessageContaining("could not be read after row 1");
    }

    private static Object value(TradeRow row, String column) {
        return row.values()[SCHEMA.indexOf(column)];
    }

    private static Collected read(String csv) {
        Collected collected = new Collected();
        new CsvTradeSource(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), SCHEMA, PARSER).read(collected);
        return collected;
    }

    private static final class Collected implements TradeSource.TradeRowHandler {

        private final List<TradeRow> rows = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void onRow(TradeRow row) {
            rows.add(row);
        }

        @Override
        public void onRowError(long rowNumber, String message) {
            errors.add(rowNumber + ": " + message);
        }
    }
}
