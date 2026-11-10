package org.pdi.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.pdi.config.PdiConfiguration;
import org.pdi.exception.CustomException;
import org.pdi.repository.BoundaryGeometryRepository;
import org.pdi.repository.BoundaryPopulationRepository;
import org.pdi.repository.PopulationRunRepository;
import org.pdi.service.enrichment.PopulationRunEnrichmentService;
import org.pdi.util.PythonEngineUtil;
import org.pdi.validator.population.BoundaryPopulationValidator;
import org.pdi.web.models.BoundaryGeometry;
import org.pdi.web.models.BoundaryPopulation;
import org.pdi.web.models.BoundaryPopulationResult;
import org.pdi.web.models.BoundaryPopulationSearchCriteria;
import org.pdi.web.models.BoundaryPopulationSearchRequest;
import org.pdi.web.models.BoundaryPopulationSearchResponse;
import org.pdi.web.models.BoundaryTree;
import org.pdi.web.models.EngineBoundaryPopulation;
import org.pdi.web.models.PopulationRun;
import org.pdi.web.models.PopulationRunRequest;
import org.pdi.web.models.PopulationRunResponse;
import org.pdi.web.models.PopulationRunSearchCriteria;
import org.pdi.web.models.PopulationRunSearchRequest;
import org.pdi.web.models.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.pdi.util.PdiConstants.DEFAULT_PAGE_SIZE;
import static org.pdi.util.PdiConstants.ENGINE_ERROR;
import static org.pdi.util.PdiConstants.ENGINE_LOG_FILE;
import static org.pdi.util.PdiConstants.METHOD_NO_GEOMETRY;
import static org.pdi.util.PdiConstants.MODE_DELTA;
import static org.pdi.util.PdiConstants.MODE_FULL;
import static org.pdi.util.PdiConstants.RUN_IN_PROGRESS;
import static org.pdi.util.PdiConstants.RUN_IN_PROGRESS_MSG;
import static org.pdi.util.PdiConstants.STATUS_RUNNING;
import static org.pdi.util.PdiConstants.TOTAL_GROUP;
import static org.pdi.util.PdiServiceUtil.deleteDirQuietly;
import static org.pdi.util.PdiServiceUtil.lastLine;
import static org.pdi.util.PdiServiceUtil.readableFailure;

/**
 * WorldPop population per HCM boundary code: starts runs, executes them asynchronously,
 * activates them atomically, and answers excel-ingestion's lookups from the active run.
 */
@Slf4j
@Service
public class BoundaryPopulationService {

    private static final int PROGRESS_TREE_LOADED = 10;

    private static final int PROGRESS_ENGINE_START = 15;

    private static final int PROGRESS_ENGINE_SPAN = 70;

    private final HcmBoundaryClient hcmBoundaryClient;

    private final BoundaryGeometryRepository geometryRepository;

    private final PopulationRunRepository runRepository;

    private final BoundaryPopulationRepository populationRepository;

    private final GeometryResolver geometryResolver;

    private final PopulationRunPlanner planner;

    private final PythonEngineUtil pythonEngineUtil;

    private final PopulationRunEnrichmentService enrichmentService;

    private final BoundaryPopulationValidator validator;

    private final TransactionTemplate transactionTemplate;

    private final PdiConfiguration config;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pdi-population-run");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public BoundaryPopulationService(HcmBoundaryClient hcmBoundaryClient, BoundaryGeometryRepository geometryRepository,
                                     PopulationRunRepository runRepository,
                                     BoundaryPopulationRepository populationRepository,
                                     GeometryResolver geometryResolver, PopulationRunPlanner planner,
                                     PythonEngineUtil pythonEngineUtil, PopulationRunEnrichmentService enrichmentService,
                                     BoundaryPopulationValidator validator, TransactionTemplate transactionTemplate,
                                     PdiConfiguration config) {
        this.hcmBoundaryClient = hcmBoundaryClient;
        this.geometryRepository = geometryRepository;
        this.runRepository = runRepository;
        this.populationRepository = populationRepository;
        this.geometryResolver = geometryResolver;
        this.planner = planner;
        this.pythonEngineUtil = pythonEngineUtil;
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.transactionTemplate = transactionTemplate;
        this.config = config;
    }

    public PopulationRunResponse compute(PopulationRunRequest request) {
        validator.validateComputeRequest(request);
        PopulationRun run = submit(request.getPopulationRun(), request.getRequestInfo());
        return PopulationRunResponse.builder()
                .responseInfo(enrichmentService.responseInfo(request.getRequestInfo()))
                .populationRuns(List.of(run))
                .build();
    }

    /** Saves a PENDING run and queues it. Fails with 409 when the hierarchy already has one in flight. */
    public PopulationRun submit(PopulationRun run, RequestInfo requestInfo) {
        enrichmentService.enrichComputeRequest(run, requestInfo);
        try {
            runRepository.create(run);
        } catch (DuplicateKeyException e) {
            throw new CustomException(RUN_IN_PROGRESS, RUN_IN_PROGRESS_MSG, HttpStatus.CONFLICT, e);
        }
        executor.submit(() -> execute(run, requestInfo));
        log.info("Queued {} population run {} for {}/{}", run.getMode(), run.getId(), run.getTenantId(),
                run.getHierarchyType());
        return run;
    }

    public Optional<PopulationRun> findActiveRun(String tenantId, String hierarchyType) {
        return runRepository.findActive(tenantId, hierarchyType);
    }

    public PopulationRunResponse searchRuns(PopulationRunSearchRequest request) {
        validator.validateRunSearchRequest(request);
        PopulationRunSearchCriteria criteria = request.getPopulationRunSearchCriteria();
        criteria.setLimit(criteria.getLimit() == null ? DEFAULT_PAGE_SIZE : criteria.getLimit());
        criteria.setOffset(criteria.getOffset() == null ? 0 : criteria.getOffset());
        return PopulationRunResponse.builder()
                .responseInfo(enrichmentService.responseInfo(request.getRequestInfo()))
                .populationRuns(runRepository.search(criteria))
                .build();
    }

    /** Population for a list of codes from the active run; codes not in that run are left out. */
    public BoundaryPopulationSearchResponse search(BoundaryPopulationSearchRequest request) {
        validator.validateSearchRequest(request);
        BoundaryPopulationSearchCriteria criteria = request.getBoundaryPopulationSearchCriteria();
        Optional<PopulationRun> active = runRepository.findActive(criteria.getTenantId(), criteria.getHierarchyType());
        if (active.isEmpty()) {
            return BoundaryPopulationSearchResponse.builder()
                    .responseInfo(enrichmentService.responseInfo(request.getRequestInfo()))
                    .boundaryPopulation(List.of())
                    .build();
        }
        Set<String> codes = new HashSet<>(criteria.getBoundaryCodes());
        List<BoundaryPopulation> rows = populationRepository.findByRun(active.get().getId(), codes);
        List<String> groups = criteria.getGroups() == null || criteria.getGroups().isEmpty()
                ? List.of(TOTAL_GROUP) : criteria.getGroups();
        rows.forEach(row -> row.setPopulation(onlyGroups(row.getPopulation(), groups)));
        log.info("Population lookup {}/{}: {} codes requested, {} returned", criteria.getTenantId(),
                criteria.getHierarchyType(), codes.size(), rows.size());
        return BoundaryPopulationSearchResponse.builder()
                .responseInfo(enrichmentService.responseInfo(request.getRequestInfo()))
                .populationRun(active.get())
                .boundaryPopulation(rows)
                .build();
    }

    /**
     * Runs left PENDING or RUNNING by a previous process can never finish: their engine died
     * with it. Failing them frees the hierarchy for a new run.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedRuns() {
        long now = System.currentTimeMillis();
        long timeoutMs = config.getEngineTimeoutSeconds() * 1000L;
        long createdBefore = timeoutMs > 0 ? now - timeoutMs : now;
        try {
            int failed = runRepository.failInFlightCreatedBefore(createdBefore,
                    "the service restarted while this run was in progress", now);
            if (failed > 0) {
                log.warn("Marked {} orphaned population run(s) as FAILED", failed);
            }
        } catch (DataAccessException e) {
            log.warn("Could not check for orphaned population runs: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void execute(PopulationRun run, RequestInfo requestInfo) {
        Path work = null;
        try {
            runRepository.updateStatus(run.getId(), STATUS_RUNNING, 0, System.currentTimeMillis());
            work = Files.createTempDirectory("pdi-population-" + run.getId() + "-");

            BoundaryTree tree = hcmBoundaryClient.fetchTree(run.getTenantId(), run.getHierarchyType(), requestInfo);
            Map<String, BoundaryGeometry> geometries = geometryRepository
                    .search(run.getTenantId(), run.getHierarchyType(), null, true).stream()
                    .collect(Collectors.toMap(BoundaryGeometry::getBoundaryCode, Function.identity()));
            runRepository.updateProgress(run.getId(), PROGRESS_TREE_LOADED, System.currentTimeMillis());

            Optional<PopulationRun> active = runRepository.findActive(run.getTenantId(), run.getHierarchyType());
            Map<String, BoundaryPopulation> previous = previousRows(run, active);
            long activeCreated = active.map(a -> a.getAuditDetails().getCreatedTime()).orElse(0L);
            PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, activeCreated);
            log.info("Run {}: {} boundaries, {} to compute, {} reused", run.getId(), tree.size(),
                    plan.compute().size(), plan.reusable().size());

            Map<String, EngineBoundaryPopulation> computed = computeUnits(run, tree, geometries, plan.compute(), work);
            List<BoundaryPopulation> rows = planner.assemble(run, tree, computed, previous, plan.reusable(),
                    System.currentTimeMillis());

            run.setBoundaryCount(rows.size());
            run.setComputedCount(computed.size());
            run.setNoGeometryCount((int) rows.stream().filter(row -> METHOD_NO_GEOMETRY.equals(row.getMethod())).count());
            activate(run, rows);
            runRepository.prune(run.getTenantId(), run.getHierarchyType(), config.getPopulationRunsToKeep());
            log.info("Run {} completed: {} rows, {} computed, {} without geometry", run.getId(),
                    run.getBoundaryCount(), run.getComputedCount(), run.getNoGeometryCount());
        } catch (RuntimeException | IOException e) {
            log.error("Population run {} failed: {}", run.getId(), e.getMessage(), e);
            runRepository.fail(run.getId(), readableFailure(lastLine(e.getMessage())), System.currentTimeMillis());
        } finally {
            deleteDirQuietly(work);
        }
    }

    /**
     * The active run's rows, which a DELTA run copies from. A run for another country, year or
     * set of groups cannot reuse them and is computed in full.
     */
    private Map<String, BoundaryPopulation> previousRows(PopulationRun run, Optional<PopulationRun> active) {
        if (!MODE_DELTA.equals(run.getMode()) || active.isEmpty()) {
            return Map.of();
        }
        PopulationRun current = active.get();
        boolean compatible = current.getIso3().equals(run.getIso3())
                && current.getYear().equals(run.getYear())
                && new HashSet<>(current.getGroups()).equals(new HashSet<>(run.getGroups()));
        if (!compatible) {
            log.info("Run {} differs from active run {} in country, year or groups; computing in {} mode",
                    run.getId(), current.getId(), MODE_FULL);
            return Map.of();
        }
        return populationRepository.findByRun(current.getId(), null).stream()
                .collect(Collectors.toMap(BoundaryPopulation::getBoundaryCode, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private Map<String, EngineBoundaryPopulation> computeUnits(PopulationRun run, BoundaryTree tree,
                                                               Map<String, BoundaryGeometry> geometries,
                                                               Set<String> codes, Path work) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        Path units = work.resolve("units.geojson");
        Path clips = geometryResolver.writeUnits(tree, geometries, codes, units, work.resolve("clips.geojson"));
        BoundaryPopulationResult result = pythonEngineUtil.boundaryPopulation(units, clips, run.getIso3(),
                run.getYear(), run.getGroups(), work.resolve(ENGINE_LOG_FILE),
                percent -> runRepository.updateProgress(run.getId(),
                        PROGRESS_ENGINE_START + percent * PROGRESS_ENGINE_SPAN / 100, System.currentTimeMillis()));
        if (result == null || result.getResults() == null) {
            throw new CustomException(ENGINE_ERROR, "the engine returned no boundary population",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (result.getSkipped() != null && !result.getSkipped().isEmpty()) {
            log.warn("Run {}: engine skipped {} boundaries, e.g. {}", run.getId(), result.getSkipped().size(),
                    result.getSkipped().get(0).getBoundaryCode() + ": " + result.getSkipped().get(0).getReason());
        }
        run.setWorldpopRelease(result.getWorldpopRelease());
        return result.getResults().stream()
                .collect(Collectors.toMap(EngineBoundaryPopulation::getBoundaryCode, Function.identity()));
    }

    /** Saves the rows and switches the active run in one transaction, so readers never see a half-written run. */
    private void activate(PopulationRun run, List<BoundaryPopulation> rows) {
        boolean activate = Boolean.TRUE.equals(run.getActivateOnSuccess());
        transactionTemplate.executeWithoutResult(status -> {
            populationRepository.saveAll(rows, config.getPopulationBatchSize());
            long now = System.currentTimeMillis();
            if (activate) {
                runRepository.deactivate(run.getTenantId(), run.getHierarchyType(), now);
            }
            runRepository.complete(run, activate, now);
        });
    }

    private Map<String, Integer> onlyGroups(Map<String, Integer> population, List<String> groups) {
        Map<String, Integer> selected = new LinkedHashMap<>();
        for (String group : groups) {
            selected.put(group, population == null ? null : population.get(group));
        }
        return selected;
    }
}
