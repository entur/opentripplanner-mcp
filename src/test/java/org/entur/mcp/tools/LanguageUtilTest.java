package org.entur.mcp.tools;

import org.junit.jupiter.api.Test;
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
}