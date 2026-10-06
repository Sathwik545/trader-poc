package com.demo.aegis.ingest;

import java.util.Locale;

/** One of the 121 trade fields: its name in the source file and how it is parsed. */
public record TradeColumn(String name, ColumnType type) {

    /** Column name in the trades table (lower case of the source name). */
    public String dbName() {
        return name.toLowerCase(Locale.ROOT);
    }
}
