package com.demo.aegis.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The trade fields, in file order, read once from ada_deal_columns.csv. The parser, the
 * writer and the trades table all follow this list, so a new field is a new line in that
 * file plus a migration.
 */
@Component
public class TradeSchema {

    private static final String RESOURCE = "ada_deal_columns.csv";

    private final List<TradeColumn> columns;

    public TradeSchema() {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        List<TradeColumn> loaded = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                loaded.add(new TradeColumn(record.get("field").trim(), ColumnType.fromSchemaType(record.get("data_type"))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        this.columns = List.copyOf(loaded);
    }

    public List<TradeColumn> columns() {
        return columns;
    }

    public int size() {
        return columns.size();
    }

    /** Position of a field in {@link #columns()}; fails fast if the schema does not have it. */
    public int indexOf(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Trade schema has no column " + name);
    }
}
