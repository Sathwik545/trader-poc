package com.demo.aegis.engine;

/** An iWork portfolio record. */
public record Portfolio(String code, String country, String businessUnit, String classification, String mxEntity,
                        String riskBook, String aegisDesk, String aegisSubdesk, String owner, String reviewer) {
}
