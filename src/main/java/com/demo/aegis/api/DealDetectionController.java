package com.demo.aegis.api;

import com.demo.aegis.config.DemoProperties;
import com.demo.aegis.engine.DetectionEngine;
import com.demo.aegis.ingest.CsvTradeSource;
import com.demo.aegis.ingest.IngestService;
import com.demo.aegis.ingest.InvalidTradeFileException;
import com.demo.aegis.ingest.RunSummary;
import com.demo.aegis.ingest.TradeParser;
import com.demo.aegis.ingest.TradeSchema;
import com.demo.aegis.persist.FlaggedTrade;
import com.demo.aegis.persist.TagCount;
import com.demo.aegis.persist.TradeTagRepository;
import com.demo.aegis.rules.DetectionRule;
import com.demo.aegis.rules.RuleLoader;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class DealDetectionController {

    private final IngestService ingestService;
    private final DetectionEngine detectionEngine;
    private final TradeTagRepository tradeTags;
    private final RuleLoader ruleLoader;
    private final TradeSchema schema;
    private final TradeParser parser;
    private final DemoProperties demo;

    public DealDetectionController(IngestService ingestService, DetectionEngine detectionEngine, TradeTagRepository tradeTags,
                                   RuleLoader ruleLoader, TradeSchema schema, TradeParser parser, DemoProperties demo) {
        this.ingestService = ingestService;
        this.detectionEngine = detectionEngine;
        this.tradeTags = tradeTags;
        this.ruleLoader = ruleLoader;
        this.schema = schema;
        this.parser = parser;
        this.demo = demo;
    }

    /** Stores every row of the uploaded CSV in the trades table, runs detection on it and returns the run summary. */
    @PostMapping(path = "/trades/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunSummary upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidTradeFileException("The uploaded file is empty");
        }
        try (InputStream input = file.getInputStream()) {
            return storeAndDetect(file.getOriginalFilename(), input);
        } catch (IOException e) {
            throw new InvalidTradeFileException("The uploaded file could not be read: " + e.getMessage(), e);
        }
    }

    /** Runs the pipeline on the synthetic sample file kept on the server; used by the demo screen. */
    @PostMapping("/trades/sample")
    public RunSummary runSample() {
        Path sample = Path.of(demo.sampleFile());
        if (!Files.isRegularFile(sample)) {
            throw new InvalidTradeFileException("The sample file was not found at " + sample.toAbsolutePath()
                    + "; start the app from the project folder or set aegis.demo.sample-file");
        }
        try (InputStream input = Files.newInputStream(sample)) {
            return storeAndDetect(sample.getFileName().toString(), input);
        } catch (IOException e) {
            throw new InvalidTradeFileException("The sample file could not be read: " + e.getMessage(), e);
        }
    }

    private RunSummary storeAndDetect(String fileName, InputStream input) {
        long runId = ingestService.ingest(fileName, new CsvTradeSource(input, schema, parser)).runId();
        detectionEngine.detect(runId);
        return ingestService.summary(runId);
    }

    /** The most recent runs, newest first. */
    @GetMapping("/runs")
    public List<RunSummary> runs(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ingestService.recentRuns(limit);
    }

    /** Re-runs detection on the trades already stored for the run, replacing its tags. */
    @PostMapping("/runs/{id}/detect")
    public RunSummary detect(@PathVariable long id) {
        detectionEngine.detect(id);
        return ingestService.summary(id);
    }

    @GetMapping("/runs/{id}")
    public RunSummary run(@PathVariable long id) {
        return ingestService.summary(id);
    }

    /** Number of trades tagged per rule, one line per rule in BR Ref order. */
    @GetMapping("/runs/{id}/tags")
    public List<TagCount> tags(@PathVariable long id) {
        ingestService.summary(id);
        return tradeTags.countsByRun(id);
    }

    /** Flagged trades with their tags and tag details, optionally only those carrying one tag. */
    @GetMapping("/runs/{id}/trades")
    public FlaggedTrade.Page trades(@PathVariable long id,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        ingestService.summary(id);
        return tradeTags.flaggedTrades(id, tag == null || tag.isBlank() ? null : tag, page, size);
    }

    @GetMapping("/rules")
    public List<DetectionRule> rules() {
        return ruleLoader.currentRules();
    }

    /** Re-reads rules.yaml into the detection_rule table and the rule engine; no restart needed. */
    @PostMapping("/rules/reload")
    public List<DetectionRule> reloadRules() {
        return ruleLoader.reload();
    }
}
