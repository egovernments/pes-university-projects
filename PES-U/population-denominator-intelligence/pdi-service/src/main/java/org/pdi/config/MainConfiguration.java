package org.pdi.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.TimeZone;

@Configuration
public class MainConfiguration implements WebMvcConfigurer {

    @Value("${app.timezone}")
    private String timeZone;

    @Value("${pdi.cors.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;

    // Run in UTC so the JDBC handshake never sends a legacy zone id the server rejects.
    // The pool connects lazily on first use, so this is in place before any connection opens.
    @PostConstruct
    public void initialize() {
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/population/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
