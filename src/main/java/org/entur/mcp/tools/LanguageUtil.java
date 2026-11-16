package org.entur.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;

class LanguageUtil {
    private static final Logger log = LoggerFactory.getLogger(LanguageUtil.class);
    private static final Set<String> VALID = Set.of("en", "nb", "nn");

    static String normalize(String lang) {
        if (lang != null && VALID.contains(lang)) return lang;
        if (lang != null && !lang.isEmpty()) {
            // The value is caller input of any length and content (newlines included): not at WARN
            log.warn("Unknown language code, defaulting to 'en'");
            log.debug("Unknown language code '{}'", lang);
        }
        return "en";
    }
}
