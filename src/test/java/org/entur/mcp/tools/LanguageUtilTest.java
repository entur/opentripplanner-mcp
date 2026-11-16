package org.entur.mcp.tools;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

class LanguageUtilTest {

    @Test
    void normalize_validCodes_returnAsIs() {
        assertThat(LanguageUtil.normalize("en")).isEqualTo("en");
        assertThat(LanguageUtil.normalize("nb")).isEqualTo("nb");
        assertThat(LanguageUtil.normalize("nn")).isEqualTo("nn");
    }

    @Test
    void normalize_unknownCode_returnsEn() {
        assertThat(LanguageUtil.normalize("fr")).isEqualTo("en");
        assertThat(LanguageUtil.normalize("no")).isEqualTo("en");
        assertThat(LanguageUtil.normalize("")).isEqualTo("en");
    }

    @Test
    void normalize_null_returnsEn() {
        assertThat(LanguageUtil.normalize(null)).isEqualTo("en");
    }

    @Test
    void normalize_unknownCode_doesNotLogTheInputAboveDebug() {
        Logger logger = (Logger) LoggerFactory.getLogger(LanguageUtil.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LanguageUtil.normalize("xx\nFAKE LOG LINE");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
            .filteredOn(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
            .isNotEmpty()
            .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain("FAKE"));
    }
}
