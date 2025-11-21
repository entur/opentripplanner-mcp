package org.entur.mcp.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
