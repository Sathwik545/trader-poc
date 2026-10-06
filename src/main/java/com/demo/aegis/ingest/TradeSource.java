package com.demo.aegis.ingest;

/**
 * Where trades come from. The demo reads an uploaded CSV ({@link CsvTradeSource}); the real
 * feed ({@link PrestoTradeSource}) plugs in here without touching the rest of the pipeline.
 */
public interface TradeSource {

    /**
     * Streams every row to the handler, one at a time. A row that cannot be parsed goes to
     * {@link TradeRowHandler#onRowError} and reading continues.
     *
     * @throws InvalidTradeFileException if the source as a whole cannot be read
     */
    void read(TradeRowHandler handler);

    interface TradeRowHandler {

        void onRow(TradeRow row);

        void onRowError(long rowNumber, String message);
    }
}
