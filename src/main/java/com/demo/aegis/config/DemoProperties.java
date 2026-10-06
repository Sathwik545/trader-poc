package com.demo.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param sampleFile the synthetic trade file the demo screen's "Run sample file" button loads,
 *                   relative to the folder the app is started from
 */
@ConfigurationProperties(prefix = "aegis.demo")
public record DemoProperties(@DefaultValue("docs/ada_deals_synthetic_10000.csv") String sampleFile) {
}
