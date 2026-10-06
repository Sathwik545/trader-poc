package com.demo.aegis.ingest;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Turns the raw cells of one row into a {@link TradeRow}, following the {@link TradeSchema}. */
@Component
public class TradeParser {

    private static final int MAX_VALUE_IN_MESSAGE = 40;

    private final List<TradeColumn> columns;
    private final int tradeDateIndex;
    private final int cobDateIndex;

    public TradeParser(TradeSchema schema) {
        this.columns = schema.columns();
        this.tradeDateIndex = schema.indexOf("M_TP_DTETRN");
        this.cobDateIndex = schema.indexOf("COB_DATE");
    }

    /**
     * @param cells raw text in schema column order; null or empty means no value
     * @throws RowParseException naming the first field that could not be parsed
     */
    public TradeRow parse(long rowNumber, String[] cells) {
        Object[] values = new Object[columns.size()];
        for (int i = 0; i < values.length; i++) {
            String cell = cells[i];
            if (cell == null || cell.isEmpty()) {
                continue;
            }
            TradeColumn column = columns.get(i);
            try {
                values[i] = convert(column.type(), cell);
                if (i == cobDateIndex) {
                    FieldConverters.parseCobDate(cell);
                }
            } catch (IllegalArgumentException e) {
                throw new RowParseException(column.name() + ": cannot read '" + abbreviate(cell) + "' as "
                        + column.type() + (e instanceof NumberFormatException ? "" : " (" + e.getMessage() + ")"));
            }
        }
        if (values[tradeDateIndex] == null) {
            throw new RowParseException("M_TP_DTETRN: transaction date is empty, so the trade has no trade date");
        }
        LocalDate tradeDate = FieldConverters.epochMicrosToUtcDate((Long) values[tradeDateIndex]);
        return new TradeRow(rowNumber, tradeDate, values);
    }

    private static Object convert(ColumnType type, String cell) {
        return switch (type) {
            case INTEGER, EPOCH_MICROS -> FieldConverters.parseLong(cell);
            case DECIMAL -> FieldConverters.parseDecimal(cell);
            case TIME -> validatedTime(cell);
            case BINARY -> FieldConverters.parseBytesLiteral(cell);
            case TEXT -> cell;
        };
    }

    /** Times are stored as the source text, once it is known to be a valid HH:mm:ss. */
    private static String validatedTime(String cell) {
        FieldConverters.parseTime(cell);
        return cell.trim();
    }

    private static String abbreviate(String value) {
        return value.length() <= MAX_VALUE_IN_MESSAGE ? value : value.substring(0, MAX_VALUE_IN_MESSAGE) + "...";
    }
}
