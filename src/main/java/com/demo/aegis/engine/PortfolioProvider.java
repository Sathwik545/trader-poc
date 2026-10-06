package com.demo.aegis.engine;

import java.util.Map;

/** Source of iWork portfolios. Demo: the portfolio table. Later: the daily iWork file. */
public interface PortfolioProvider {

    /** Every portfolio, keyed by portfolio code. */
    Map<String, Portfolio> loadAll();
}
