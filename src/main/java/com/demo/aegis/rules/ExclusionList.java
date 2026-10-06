package com.demo.aegis.rules;

import java.util.List;
import java.util.regex.Pattern;

/** The common exclusion list for trader checks. Entries are trader IDs; % matches any text. */
public class ExclusionList {

    private final List<Pattern> patterns;

    public ExclusionList(List<String> entries) {
        this.patterns = entries.stream().map(ExclusionList::toPattern).toList();
    }

    public boolean matches(String traderId) {
        return traderId != null && patterns.stream().anyMatch(p -> p.matcher(traderId).matches());
    }

    private static Pattern toPattern(String entry) {
        String[] literals = entry.trim().split("%", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < literals.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(literals[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
