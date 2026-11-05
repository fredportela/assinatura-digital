package com.domain.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebPkiConfigController {
    private final Config config;

    public WebPkiConfigController(@Value("${app.web-pki.rest-pki-url}") String restPkiUrl) {
        this.config = new Config(restPkiUrl);
    }

    @GetMapping("/api/web-pki/config")
    public ResponseEntity<Config> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config);
    }

    public record Config(String restPkiUrl) {}
}
