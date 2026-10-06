package com.demo.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;
import java.util.List;

/**
 * @param chunkSize         stored trades read and evaluated per chunk
 * @param rulesLocations    Spring resource locations for rules.yaml; the first that exists is used
 * @param scope             BR-FD-003 criteria; a portfolio is in scope when it meets any of them
 * @param murexRolloverTime HH:mm, compared with M_TP_TIMSYS for BR-UC1-010
 * @param exclusionList     trader IDs skipped by the trader checks; % is a wildcard
 */
@ConfigurationProperties(prefix = "aegis.detection")
public record DetectionProperties(@DefaultValue("1000") int chunkSize,
                                  @DefaultValue("classpath:rules.yaml") List<String> rulesLocations,
                                  @DefaultValue List<ScopeCriteria> scope,
                                  @DefaultValue("19:00") String murexRolloverTime,
                                  @DefaultValue List<String> exclusionList) {

    public LocalTime rolloverTime() {
        return LocalTime.parse(murexRolloverTime);
    }

    /**
     * @param country                 iWork country the criteria apply to
     * @param businessUnits           allowed business units
     * @param excludedClassifications classifications that are out of scope
     * @param excludedMxEntities      MxEntity values that are out of scope
     */
    public record ScopeCriteria(String country,
                                @DefaultValue List<String> businessUnits,
                                @DefaultValue List<String> excludedClassifications,
                                @DefaultValue List<String> excludedMxEntities) {
    }
}
