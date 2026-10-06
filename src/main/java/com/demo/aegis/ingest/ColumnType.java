package com.demo.aegis.ingest;

/** How a trade field is parsed from the file and stored in the trades table. */
public enum ColumnType {
    /** Stored as BIGINT. */
    INTEGER,
    /** Stored as DECIMAL. */
    DECIMAL,
    /** Epoch microseconds UTC, stored as BIGINT. */
    EPOCH_MICROS,
    /** HH:mm:ss, stored as text. */
    TIME,
    /** Python bytes literal such as b'\x00\x01', stored as VARBINARY. */
    BINARY,
    /** Stored as VARCHAR. */
    TEXT;

    /** Maps the data_type column of ada_deal_columns.csv; anything unrecognised is kept as text. */
    static ColumnType fromSchemaType(String dataType) {
        return switch (dataType.trim()) {
            case "Integer" -> INTEGER;
            case "Decimal" -> DECIMAL;
            case "Date/Time (epoch)" -> EPOCH_MICROS;
            case "Time" -> TIME;
            case "Binary" -> BINARY;
            default -> TEXT;
        };
    }
}
