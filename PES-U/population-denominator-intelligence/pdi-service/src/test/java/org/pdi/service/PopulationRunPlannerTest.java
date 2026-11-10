package org.pdi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pdi.web.models.AuditDetails;
import org.pdi.web.models.BoundaryGeometry;
import org.pdi.web.models.BoundaryNode;
import org.pdi.web.models.BoundaryPopulation;
import org.pdi.web.models.BoundaryTree;
import org.pdi.web.models.EngineBoundaryPopulation;
import org.pdi.web.models.PopulationRun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationRunPlannerTest {

    private static final long ACTIVE_CREATED = 1_000L;

    private static final long BEFORE = 500L;

    private static final long AFTER = 2_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PopulationRunPlanner planner = new PopulationRunPlanner(new GeometryResolver(objectMapper));

    private BoundaryTree tree;

    private Map<String, BoundaryGeometry> geometries;

    @BeforeEach
    void setUp() {
        tree = new BoundaryTree("t", "ADMIN", List.of("Country", "District", "Village"));
        node("C", "Country", null, 1, "D1", "D2");
        node("D1", "District", "C", 2, "V1", "V2");
        node("D2", "District", "C", 2);
        node("V1", "Village", "D1", 3);
        node("V2", "Village", "D1", 3);
        geometries = new HashMap<>();
        geometries.put("D1", geometry("D1", "POLYGON", BEFORE));
        geometries.put("V1", geometry("V1", "POINT", BEFORE));
        geometries.put("V2", geometry("V2", "POINT", BEFORE));
    }

    @Test
    void fullRunComputesLeavesAndSkipsParentsThatRollUp() {
        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, Map.of(), 0L);

        assertThat(plan.compute()).containsExactlyInAnyOrder("V1", "V2");
        assertThat(plan.reusable()).isEmpty();
    }

    @Test
    void parentWithAnUnlocatedChildNeedsItsOwnArea() {
        node("V3", "Village", "D1", 3);
        tree.get("D1").getChildCodes().add("V3");

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, Map.of(), 0L);

        assertThat(plan.compute()).containsExactlyInAnyOrder("D1", "V1", "V2");
    }

    @Test
    void assembleRollsUpCompleteChildrenAndMarksTheRestNoGeometry() {
        Map<String, EngineBoundaryPopulation> computed = Map.of(
                "V1", engine("V1", 100), "V2", engine("V2", 50));

        Map<String, BoundaryPopulation> rows = byCode(planner.assemble(run(), tree, computed, Map.of(), Set.of(), 1L));

        assertThat(rows.get("D1").getMethod()).isEqualTo("ROLLUP");
        assertThat(rows.get("D1").getPopulation()).containsEntry("total", 150);
        assertThat(rows.get("D2").getMethod()).isEqualTo("NO_GEOMETRY");
        assertThat(rows.get("C").getMethod()).isEqualTo("NO_GEOMETRY");
        assertThat(rows.get("C").getPopulation()).isNull();
    }

    @Test
    void deltaWithNothingChangedCopiesEveryValue() {
        Map<String, BoundaryPopulation> previous = previousRun();

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, ACTIVE_CREATED);
        Map<String, BoundaryPopulation> rows = byCode(
                planner.assemble(run(), tree, Map.of(), previous, plan.reusable(), 1L));

        assertThat(plan.compute()).isEmpty();
        assertThat(rows.get("V1").getPopulation()).containsEntry("total", 100);
        assertThat(rows.get("D1").getPopulation()).containsEntry("total", 150);
    }

    @Test
    void newPointRecomputesItsWholeVoronoiGroup() {
        Map<String, BoundaryPopulation> previous = previousRun();
        node("V3", "Village", "D1", 3);
        tree.get("D1").getChildCodes().add("V3");
        geometries.put("V3", geometry("V3", "POINT", AFTER));

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, ACTIVE_CREATED);

        assertThat(plan.compute()).containsExactlyInAnyOrder("V1", "V2", "V3");
    }

    @Test
    void changedPolygonElsewhereLeavesOtherGroupsAlone() {
        Map<String, BoundaryPopulation> previous = previousRun();
        geometries.put("D2", geometry("D2", "POLYGON", AFTER));

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, ACTIVE_CREATED);

        assertThat(plan.compute()).containsExactly("D2");
        assertThat(plan.reusable()).containsExactlyInAnyOrder("V1", "V2");
    }

    @Test
    void changedClipPolygonRecomputesThePointsInsideIt() {
        Map<String, BoundaryPopulation> previous = previousRun();
        node("V3", "Village", "D1", 3);
        tree.get("D1").getChildCodes().add("V3");
        geometries.put("D1", geometry("D1", "POLYGON", AFTER));

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, ACTIVE_CREATED);

        assertThat(plan.compute()).containsExactlyInAnyOrder("D1", "V1", "V2");
    }

    @Test
    void removedPointRecomputesItsFormerNeighbours() {
        Map<String, BoundaryPopulation> previous = previousRun();
        tree.get("D1").getChildCodes().remove("V2");
        tree.getNodes().remove("V2");
        geometries.remove("V2");

        PopulationRunPlanner.Plan plan = planner.plan(tree, geometries, previous, ACTIVE_CREATED);

        assertThat(plan.compute()).containsExactly("V1");
    }

    private Map<String, BoundaryPopulation> previousRun() {
        Map<String, BoundaryPopulation> previous = new HashMap<>();
        previous.put("V1", previousRow("V1", "D1", "Village", "POINT_VORONOI", 100));
        previous.put("V2", previousRow("V2", "D1", "Village", "POINT_VORONOI", 50));
        previous.put("D1", previousRow("D1", "C", "District", "ROLLUP", 150));
        previous.put("D2", BoundaryPopulation.builder().boundaryCode("D2").parentBoundaryCode("C")
                .boundaryType("District").method("NO_GEOMETRY").build());
        previous.put("C", BoundaryPopulation.builder().boundaryCode("C").boundaryType("Country")
                .method("NO_GEOMETRY").build());
        return previous;
    }

    private BoundaryPopulation previousRow(String code, String parent, String type, String method, int total) {
        return BoundaryPopulation.builder().boundaryCode(code).parentBoundaryCode(parent).boundaryType(type)
                .method(method).population(Map.of("total", total)).areaKm2(1.0).build();
    }

    private void node(String code, String type, String parent, int level, String... children) {
        tree.add(BoundaryNode.builder().code(code).boundaryType(type).parentCode(parent).level(level)
                .childCodes(new ArrayList<>(List.of(children))).build());
    }

    private BoundaryGeometry geometry(String code, String type, long lastModified) {
        ObjectNode geometry = objectMapper.createObjectNode().put("type", type.equals("POINT") ? "Point" : "Polygon");
        return BoundaryGeometry.builder().boundaryCode(code).geometryType(type).geometry(geometry)
                .auditDetails(AuditDetails.builder().lastModifiedTime(lastModified).build()).build();
    }

    private EngineBoundaryPopulation engine(String code, int total) {
        return EngineBoundaryPopulation.builder().boundaryCode(code).method("POINT_VORONOI").areaKm2(1.0)
                .population(Map.of("total", total)).build();
    }

    private PopulationRun run() {
        return PopulationRun.builder().id("run").tenantId("t").hierarchyType("ADMIN").groups(List.of("total")).build();
    }

    private Map<String, BoundaryPopulation> byCode(List<BoundaryPopulation> rows) {
        return rows.stream().collect(Collectors.toMap(BoundaryPopulation::getBoundaryCode, Function.identity()));
    }
}
