package com.fcaj.hackathon.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationalController {
    private final JdbcTemplate jdbc;
    private final String revision;
    public OperationalController(JdbcTemplate jdbc,@Value("${app.build.revision:unknown}") String revision) {
        this.jdbc=jdbc;this.revision=revision.matches("[a-f0-9]{40}")?revision:"unknown";
    }
    @GetMapping("/health/live") public Map<String,String> live() { return Map.of("status","UP"); }
    @GetMapping("/health/ready") public ResponseEntity<Map<String,String>> ready() {
        try { jdbc.queryForObject("SELECT 1",Integer.class);return ResponseEntity.ok(Map.of("status","UP")); }
        catch(RuntimeException unavailable) { return ResponseEntity.status(503).body(Map.of("status","DOWN")); }
    }
    @GetMapping("/health/version") public Map<String,String> version() { return Map.of("revision",revision); }
}
