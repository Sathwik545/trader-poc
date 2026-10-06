package com.demo.aegis.rules;

import com.demo.aegis.config.DetectionProperties;
import com.demo.aegis.persist.DetectionRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the rules in step with rules.yaml: reads the file, validates it, copies it into the
 * detection_rule table and hands the saved rules to the {@link RuleEngine}. Runs at start-up
 * and on demand, so a changed rule needs no rebuild or restart.
 */
@Component
public class RuleLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);

    private final DetectionProperties properties;
    private final ResourceLoader resourceLoader;
    private final DetectionRuleRepository repository;
    private final RuleEngine engine;

    public RuleLoader(DetectionProperties properties, ResourceLoader resourceLoader, DetectionRuleRepository repository,
                      RuleEngine engine) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.repository = repository;
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    /**
     * @return every rule now in the table, enabled or not
     * @throws InvalidRulesException if the file is unusable; the rules already loaded stay in force
     */
    @Transactional
    public List<DetectionRule> reload() {
        Resource file = rulesFile();
        List<DetectionRule> fromFile = read(file);
        fromFile.forEach(engine::compile);

        Set<String> tags = new HashSet<>();
        for (int i = 0; i < fromFile.size(); i++) {
            repository.upsert(fromFile.get(i), i + 1);
            tags.add(fromFile.get(i).tag());
        }
        // rules dropped from the file are switched off, not deleted: old tags still point at them
        repository.disableAllExcept(tags);

        List<DetectionRule> saved = repository.findAll();
        engine.install(saved);
        log.info("Loaded {} rules from {}", fromFile.size(), file.getDescription());
        return saved;
    }

    /** Every rule in the table, enabled or not, in BR Ref order. */
    public List<DetectionRule> currentRules() {
        return repository.findAll();
    }

    private Resource rulesFile() {
        for (String location : properties.rulesLocations()) {
            Resource resource = resourceLoader.getResource(location);
            if (resource.exists()) {
                return resource;
            }
        }
        throw new InvalidRulesException("No rules file found at any of " + properties.rulesLocations());
    }

    /** Reads and structurally checks a rules file; expressions are not parsed here. */
    @SuppressWarnings("unchecked")
    static List<DetectionRule> read(Resource file) {
        Object root;
        try (InputStream input = file.getInputStream()) {
            root = new Yaml().load(input);
        } catch (IOException | RuntimeException e) {
            throw new InvalidRulesException("Cannot read " + file.getDescription() + ": " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map) || !(map.get("rules") instanceof List<?> entries) || entries.isEmpty()) {
            throw new InvalidRulesException(file.getDescription() + " must have a non-empty 'rules' list");
        }
        List<DetectionRule> rules = new ArrayList<>();
        Set<String> tags = new HashSet<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> fields)) {
                throw new InvalidRulesException("Every entry under 'rules' must be a mapping");
            }
            DetectionRule rule = toRule((Map<String, Object>) fields);
            if (!tags.add(rule.tag())) {
                throw new InvalidRulesException("Tag " + rule.tag() + " is used by more than one rule");
            }
            rules.add(rule);
        }
        return rules;
    }

    private static DetectionRule toRule(Map<String, Object> fields) {
        String brRef = required(fields, "brRef", "?");
        Map<String, String> details = new LinkedHashMap<>();
        if (fields.get("details") instanceof Map<?, ?> given) {
            given.forEach((name, expression) -> details.put(String.valueOf(name), String.valueOf(expression)));
        }
        return new DetectionRule(null, brRef, required(fields, "tag", brRef), text(fields, "category"), text(fields, "blotter"),
                required(fields, "condition", brRef), text(fields, "workflowRole"),
                !Boolean.FALSE.equals(fields.get("enabled")), details);
    }

    private static String required(Map<String, Object> fields, String name, String brRef) {
        String value = text(fields, name);
        if (value == null || value.isBlank()) {
            throw new InvalidRulesException("Rule " + brRef + " is missing '" + name + "'");
        }
        return value;
    }

    private static String text(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : value.toString().trim();
    }
}
