package org.entur.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The server is deliberately open. If Spring Security reaches the classpath, Spring Boot's
 * default configuration answers every request - /mcp included - with 401, and MCP clients
 * read that as "this server needs OAuth" and never connect.
 *
 * <p>It arrives transitively: entur cloud-logging's web starter depends on
 * spring-boot-starter-security, which the pom excludes. This fails if a dependency change
 * brings it back.
 */
class NoSpringSecurityTest {

    @Test
    void springSecurityIsNotOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.security.web.SecurityFilterChain"))
            .isInstanceOf(ClassNotFoundException.class);
    }
}
