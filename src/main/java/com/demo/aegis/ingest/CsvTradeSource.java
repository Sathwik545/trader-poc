package com.demo.aegis.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads trades from a CSV stream with a header row. Rows are streamed, never held in memory
 * together. Columns are matched by header name, so their order in the file does not matter
 * and extra columns are ignored.
 */
public class CsvTradeSource implements TradeSource {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .build();
    private static final int MAX_MISSING_LISTED = 10;

    private final InputStream input;
    private final TradeSchema schema;
    private final TradeParser parser;

    public CsvTradeSource(InputStream input, TradeSchema schema, TradeParser parser) {
        this.input = input;
        this.schema = schema;
        this.parser = parser;
    }

    @Override
    public void read(TradeRowHandler handler) {
        long rowNumber = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            skipByteOrderMark(reader);
            try (CSVParser csv = FORMAT.parse(reader)) {
                int[] positions = columnPositions(csv.getHeaderNames());
                String[] cells = new String[positions.length];
                for (CSVRecord record : csv) {
                    rowNumber = record.getRecordNumber();
                    if (!record.isConsistent()) {
                        handler.onRowError(rowNumber, "expected " + csv.getHeaderNames().size()
                                + " columns but the row has " + record.size());
                        continue;
                    }
                    for (int i = 0; i < positions.length; i++) {
                        cells[i] = record.get(positions[i]);
                    }
                    try {
                        handler.onRow(parser.parse(rowNumber, cells));
                    } catch (RowParseException e) {
                        handler.onRowError(rowNumber, e.getMessage());
                    }
                }
            }
        } catch (IOException | UncheckedIOException | IllegalStateException e) {
            // commons-csv reports malformed input (for example an unclosed quote) through these
            throw new InvalidTradeFileException("The CSV could not be read after row " + rowNumber + ": " + e.getMessage(), e);
        }
    }

    /** For each schema column, its position in the file; fails if the file lacks any of them. */
    private int[] columnPositions(List<String> headerNames) {
        if (headerNames.isEmpty()) {
            throw new InvalidTradeFileException("The file is empty or has no header row");
        }
        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < headerNames.size(); i++) {
            byName.putIfAbsent(headerNames.get(i).trim().toUpperCase(Locale.ROOT), i);
        }
        int[] positions = new int[schema.size()];
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            String name = schema.columns().get(i).name();
            Integer position = byName.get(name.toUpperCase(Locale.ROOT));
            if (position == null) {
                missing.add(name);
            } else {
                positions[i] = position;
            }
        }
        if (!missing.isEmpty()) {
            String listed = String.join(", ", missing.subList(0, Math.min(MAX_MISSING_LISTED, missing.size())));
            throw new InvalidTradeFileException("The file is missing " + missing.size() + " of the " + schema.size()
                    + " expected columns: " + listed + (missing.size() > MAX_MISSING_LISTED ? ", ..." : ""));
        }
        return positions;
    }

    private static void skipByteOrderMark(BufferedReader reader) throws IOException {
        reader.mark(1);
        if (reader.read() != '﻿') {
            reader.reset();
        }
    }
}
