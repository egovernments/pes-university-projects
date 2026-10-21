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
}
