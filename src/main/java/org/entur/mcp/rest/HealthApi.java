package org.entur.mcp.rest;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class HealthApi {

    @GetMapping("/readiness")
    public String readiness() {
        return "OK";
    }

    @GetMapping("/liveness")
    public String liveness() {
        return "OK";
    }
}
