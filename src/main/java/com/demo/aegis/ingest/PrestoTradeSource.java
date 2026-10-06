package com.demo.aegis.ingest;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the real trade feed (a Presto query through JdbcTemplate). Not implemented
 * in the demo; it marks where the real source plugs in.
 */
@Component
@Profile("presto")
public class PrestoTradeSource implements TradeSource {

    @Override
    public void read(TradeRowHandler handler) {
        throw new UnsupportedOperationException("The Presto trade source is not implemented in the demo");
    }
}
