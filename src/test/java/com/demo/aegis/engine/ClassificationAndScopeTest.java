package com.demo.aegis.engine;

import com.demo.aegis.config.DetectionProperties;
import com.demo.aegis.config.DetectionProperties.ScopeCriteria;
import com.demo.aegis.rules.ExclusionList;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-FD-002 (internal / external), BR-FD-003 (scope) and the trader exclusion list. */
class ClassificationAndScopeTest {

    private static final Set<String> PORTFOLIO_CODES = Set.of("DEMOHK IRD DY", "DEMOHK FXD CK");

    private final ScopeFilter scopeFilter = new ScopeFilter(new DetectionProperties(1000, List.of("classpath:rules.yaml"),
            List.of(new ScopeCriteria("Hong Kong", List.of("GFM"), List.of("Close"), List.of("iWork only"))),
            "19:00", List.of()));

    @Test
    void counterpartyThatIsAPortfolioCodeIsInternal() {
        assertThat(classify("DEMOHK FXD CK", "Internal Party")).isEqualTo("ID");
        assertThat(classify("DEMOHK FXD CK", "External")).isEqualTo("ID");
    }

    @Test
    void bondCounterpartyIsInternalOnlyWithInternalPartyType() {
        assertThat(classify("INTERNAL BONDS HK", "Internal Party")).isEqualTo("ID");
        assertThat(classify("DEMO BOND BOOK", "Internal Party")).isEqualTo("ID");
        assertThat(classify("DEMO BOND BOOK", "External")).isEqualTo("ED");
    }

    @Test
    void everythingElseIsExternal() {
        assertThat(classify("ALPHA BANK LDN", "External")).isEqualTo("ED");
        assertThat(classify("ALPHA BANK LDN", "Internal Party")).isEqualTo("ED");
        assertThat(classify(null, null)).isEqualTo("ED");
    }

    @Test
    void hongKongGfmPortfolioIsInScope() {
        assertThat(scopeFilter.inScope(portfolio("Hong Kong", "GFM", "TRADING", "MXDEMOHK"))).isTrue();
        assertThat(scopeFilter.inScope(portfolio("hong kong", "gfm", "TRADING", "MXDEMOHK"))).isTrue();
    }

    @Test
    void portfolioFailingAnyCriterionIsOutOfScope() {
        assertThat(scopeFilter.inScope(portfolio("Singapore", "GFM", "TRADING", "MXDEMOHK"))).isFalse();
        assertThat(scopeFilter.inScope(portfolio("Hong Kong", "IBG", "TRADING", "MXDEMOHK"))).isFalse();
        assertThat(scopeFilter.inScope(portfolio("Hong Kong", "GFM", "Close", "MXDEMOHK"))).isFalse();
        assertThat(scopeFilter.inScope(portfolio("Hong Kong", "GFM", "TRADING", "iWork only"))).isFalse();
    }

    @Test
    void tradeOnAPortfolioMissingFromIworkStaysInScope() {
        assertThat(scopeFilter.inScope(null)).isTrue();
    }

    @Test
    void exclusionListMatchesExactIdsAndWildcards() {
        ExclusionList list = new ExclusionList(List.of("DBSINTF", "DBSINTFD", "SETL%", "FITX%", "SYNSGCREDITUS%"));

        assertThat(list.matches("DBSINTF")).isTrue();
        assertThat(list.matches("SETL_HK01")).isTrue();
        assertThat(list.matches("FITX_02")).isTrue();
        assertThat(list.matches("SYNSGCREDITUS")).isTrue();
        assertThat(list.matches("DBSINTFX")).isFalse();
        assertThat(list.matches("XSETL")).isFalse();
        assertThat(list.matches("TRD0001")).isFalse();
        assertThat(list.matches(null)).isFalse();
    }

    private static String classify(String counterparty, String counterpartyType) {
        TradeFacts trade = new TradeFacts(1L, LocalDate.of(2026, 7, 10), 1L, "TRD0001", "DEMOHK IRD DY", counterparty,
                counterpartyType, "IRD/IRS", List.of("USD"), "USD", LocalDate.of(2026, 7, 10), LocalTime.NOON, null, "MUREX");
        return DealClassifier.classify(trade, PORTFOLIO_CODES);
    }

    private static Portfolio portfolio(String country, String businessUnit, String classification, String mxEntity) {
        return new Portfolio("PF", country, businessUnit, classification, mxEntity, "BOOK", "Desk", "Sub", "Owner", "Reviewer");
    }
}
