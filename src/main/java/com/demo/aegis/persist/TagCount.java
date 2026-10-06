package com.demo.aegis.persist;

/** How many trades of a run a rule tagged. */
public record TagCount(String brRef, String tag, String category, String blotterRef, String workflowRole, int count) {
}
