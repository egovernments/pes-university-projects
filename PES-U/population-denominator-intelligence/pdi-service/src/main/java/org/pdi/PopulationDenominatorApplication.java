package org.pdi;

import org.pdi.config.ArtifactProperties;
import org.pdi.config.CacheProperties;
import org.pdi.config.EngineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({EngineProperties.class, ArtifactProperties.class, CacheProperties.class})
public class PopulationDenominatorApplication {

    public static void main(String[] args) {
        // Run in UTC so the JDBC handshake never sends a legacy zone id the server rejects.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PopulationDenominatorApplication.class, args);
    }
}
