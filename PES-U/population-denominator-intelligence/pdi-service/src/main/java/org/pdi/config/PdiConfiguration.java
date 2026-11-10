package org.pdi.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class PdiConfiguration {

    //Engine
    @Value("${pdi.engine.python}")
    private String enginePython;

    @Value("${pdi.engine.workdir}")
    private String engineWorkdir;

    @Value("${pdi.engine.timeout-seconds}")
    private Long engineTimeoutSeconds;

    @Value("${pdi.engine.persist-buildings}")
    private Boolean persistBuildings;

    //Artifacts
    @Value("${pdi.artifacts.dir}")
    private String artifactsDir;

    //Result cache
    @Value("${pdi.cache.enabled}")
    private Boolean cacheEnabled;

    //HCM boundary-service
    @Value("${egov.boundary.host}")
    private String boundaryHost;

    @Value("${egov.boundary.hierarchy.search.path}")
    private String hierarchySearchPath;

    @Value("${egov.boundary.relationship.search.path}")
    private String relationshipSearchPath;

    //HCM localization
    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.localization.search.path}")
    private String localizationSearchPath;

    @Value("${egov.localization.default.locale}")
    private String defaultLocale;

    //HTTP client
    @Value("${pdi.http.connect-timeout-ms}")
    private Integer httpConnectTimeoutMs;

    @Value("${pdi.http.read-timeout-ms}")
    private Integer httpReadTimeoutMs;

    //Boundary population
    @Value("${pdi.population.default-year}")
    private Integer populationDefaultYear;

    @Value("${pdi.population.search.max-codes}")
    private Integer populationSearchMaxCodes;

    @Value("${pdi.population.batch-size}")
    private Integer populationBatchSize;

    @Value("${pdi.population.runs-to-keep}")
    private Integer populationRunsToKeep;
}
