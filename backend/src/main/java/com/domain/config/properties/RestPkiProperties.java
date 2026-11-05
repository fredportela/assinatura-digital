package com.domain.config.properties;

import com.lacunasoftware.restpki.RestPkiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestPkiProperties {

    @Value("${spring.restpki.accessToken}")
    private String accessToken;

    @Value("${spring.restpki.endpoint}")
    private String endPoint;

    public String getAccessToken() {
        return accessToken;
    }

    @Bean
    public RestPkiClient restPkiClient() {
        return new RestPkiClient(endPoint, accessToken);
    }
}
