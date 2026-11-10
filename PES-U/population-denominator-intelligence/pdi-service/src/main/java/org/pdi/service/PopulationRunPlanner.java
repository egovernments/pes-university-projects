package org.pdi.service;

import org.pdi.web.models.BoundaryGeometry;
import org.pdi.web.models.BoundaryNode;
import org.pdi.web.models.BoundaryPopulation;
import org.pdi.web.models.BoundaryTree;
import org.pdi.web.models.EngineBoundaryPopulation;
import org.pdi.web.models.PopulationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.pdi.util.PdiConstants.GEOMETRY_POINT;
import static org.pdi.util.PdiConstants.GEOMETRY_POLYGON;
import static org.pdi.util.PdiConstants.METHOD_NO_GEOMETRY;
import static org.pdi.util.PdiConstants.METHOD_POINT_VORONOI;
import static org.pdi.util.PdiConstants.METHOD_ROLLUP;
import static org.pdi.util.PdiConstants.METHOD_ZONAL_UPLOAD;

/**
 * Decides which boundaries a run sends to the engine and assembles the run's complete set of
 * rows from the engine output, the rows copied from the active run, and parent roll-ups.
 *
 * <p>A parent takes the sum of its children when every child has a value; otherwise its own
 * area's value if it has a location; otherwise it has no value. A DELTA run reuses a
 * boundary's previous own value unless its location, its parent or its Voronoi neighbours
 * changed since the active run was created.
 */
@Component
public class PopulationRunPlanner {

    private static final Set<String> OWN_METHODS = Set.of(METHOD_ZONAL_UPLOAD, METHOD_POINT_VORONOI);

    private final GeometryResolver geometryResolver;

    @Autowired
    public PopulationRunPlanner(GeometryResolver geometryResolver) {
        this.geometryResolver = geometryResolver;
    }

    /** Codes a run sends to the engine, and codes whose previous own value it copies. */
    public record Plan(Set<String> compute, Set<String> reusable) {
    }

    /**
     * Plans a run. {@code previous} holds the active run's rows and is empty for a FULL run;
     * {@code activeCreatedTime} is when that run was created.
     */
    public Plan plan(BoundaryTree tree, Map<String, BoundaryGeometry> geometries,
                     Map<String, BoundaryPopulation> previous, long activeCreatedTime) {
        Map<String, BoundaryGeometry> located = locatedInTree(tree, geometries);
        Set<String> reusable = reusableCodes(tree, located, previous, activeCreatedTime);

        Set<String> compute = new LinkedHashSet<>();
        Map<String, Boolean> valued = new HashMap<>();
        for (BoundaryNode node : tree.deepestFirst()) {
            boolean childrenValued = hasChildren(node)
                    && node.getChildCodes().stream().allMatch(child -> valued.getOrDefault(child, false));
            boolean ownNeeded = located.containsKey(node.getCode()) && !childrenValued;
            if (ownNeeded && !reusable.contains(node.getCode())) {
                compute.add(node.getCode());
            }
            valued.put(node.getCode(), childrenValued || located.containsKey(node.getCode()));
        }
        Set<String> expanded = withWholeTessellationGroups(tree, located, compute);
        reusable.removeAll(expanded);
        return new Plan(expanded, reusable);
    }

    /** One row per boundary in the tree, parents rolled up from their children where possible. */
    public List<BoundaryPopulation> assemble(PopulationRun run, BoundaryTree tree,
                                             Map<String, EngineBoundaryPopulation> computed,
                                             Map<String, BoundaryPopulation> previous, Set<String> reused,
                                             long createdTime) {
        Map<String, BoundaryPopulation> rows = new LinkedHashMap<>();
        for (BoundaryNode node : tree.deepestFirst()) {
            BoundaryPopulation row = rowFor(node, computed, previous, reused, rows, run.getGroups());
            row.setId(UUID.randomUUID().toString());
            row.setRunId(run.getId());
            row.setTenantId(run.getTenantId());
            row.setHierarchyType(run.getHierarchyType());
            row.setBoundaryCode(node.getCode());
            row.setBoundaryType(node.getBoundaryType());
            row.setParentBoundaryCode(node.getParentCode());
            row.setCreatedTime(createdTime);
            rows.put(node.getCode(), row);
        }
        return new ArrayList<>(rows.values());
    }

    /** Codes whose own value in the active run can be copied as it is. */
    private Set<String> reusableCodes(BoundaryTree tree, Map<String, BoundaryGeometry> located,
                                     Map<String, BoundaryPopulation> previous, long activeCreatedTime) {
        Set<String> reusable = new HashSet<>();
        for (Map.Entry<String, BoundaryGeometry> entry : located.entrySet()) {
            BoundaryPopulation row = previous.get(entry.getKey());
            if (row != null && OWN_METHODS.contains(row.getMethod())
                    && Objects.equals(row.getParentBoundaryCode(), tree.get(entry.getKey()).getParentCode())
                    && lastModified(entry.getValue()) <= activeCreatedTime) {
                reusable.add(entry.getKey());
            }
        }
        reusable.removeAll(membersOfDirtyGroups(tree, located, previous, reusable, activeCreatedTime));
        return reusable;
    }

    /**
     * Points whose Voronoi cells may have moved: a group gained, lost or moved a point, or the
     * polygon it is clipped to changed.
     */
    private Set<String> membersOfDirtyGroups(BoundaryTree tree, Map<String, BoundaryGeometry> located,
                                             Map<String, BoundaryPopulation> previous, Set<String> reusable,
                                             long activeCreatedTime) {
        Map<String, List<String>> groups = tessellationGroups(tree, located);
        Set<String> dirty = new HashSet<>();
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            for (String code : group.getValue()) {
                String clip = geometryResolver.clipCode(tree, located, code);
                boolean clipChanged = clip != null && lastModified(located.get(clip)) > activeCreatedTime;
                if (!reusable.contains(code) || clipChanged) {
                    dirty.add(group.getKey());
                }
            }
        }
        for (BoundaryPopulation row : previous.values()) {
            if (!METHOD_POINT_VORONOI.equals(row.getMethod()) || stillAPointUnderSameParent(tree, located, row)) {
                continue;
            }
            String formerParent = row.getParentBoundaryCode();
            if (formerParent != null && tree.contains(formerParent)) {
                String formerClip = nearestPolygon(tree, located, formerParent);
                dirty.add(geometryResolver.groupKey(formerClip, row.getBoundaryType()));
            } else {
                groups.keySet().stream()
                        .filter(key -> key.endsWith("|" + row.getBoundaryType()))
                        .forEach(dirty::add);
            }
        }
        Set<String> members = new HashSet<>();
        dirty.forEach(key -> members.addAll(groups.getOrDefault(key, List.of())));
        return members;
    }

    private boolean stillAPointUnderSameParent(BoundaryTree tree, Map<String, BoundaryGeometry> located,
                                               BoundaryPopulation row) {
        BoundaryNode node = tree.get(row.getBoundaryCode());
        BoundaryGeometry geometry = located.get(row.getBoundaryCode());
        return node != null && geometry != null && GEOMETRY_POINT.equals(geometry.getGeometryType())
                && Objects.equals(node.getParentCode(), row.getParentBoundaryCode());
    }

    /** {@code code} itself when it has a polygon, else its nearest ancestor with one. */
    private String nearestPolygon(BoundaryTree tree, Map<String, BoundaryGeometry> located, String code) {
        BoundaryGeometry own = located.get(code);
        if (own != null && GEOMETRY_POLYGON.equals(own.getGeometryType())) {
            return code;
        }
        return geometryResolver.clipCode(tree, located, code);
    }

    private Set<String> withWholeTessellationGroups(BoundaryTree tree, Map<String, BoundaryGeometry> located,
                                                    Set<String> compute) {
        Map<String, List<String>> groups = tessellationGroups(tree, located);
        Set<String> expanded = new LinkedHashSet<>(compute);
        for (String code : compute) {
            String key = geometryResolver.tessellationGroup(tree, located, code);
            if (key != null) {
                expanded.addAll(groups.get(key));
            }
        }
        return expanded;
    }

    private Map<String, List<String>> tessellationGroups(BoundaryTree tree, Map<String, BoundaryGeometry> located) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String code : located.keySet()) {
            String key = geometryResolver.tessellationGroup(tree, located, code);
            if (key != null) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(code);
            }
        }
        return groups;
    }

    private BoundaryPopulation rowFor(BoundaryNode node, Map<String, EngineBoundaryPopulation> computed,
                                      Map<String, BoundaryPopulation> previous, Set<String> reused,
                                      Map<String, BoundaryPopulation> rows, List<String> groups) {
        if (hasChildren(node) && node.getChildCodes().stream().allMatch(child -> hasValue(rows.get(child)))) {
            return rollUp(node, rows, groups);
        }
        EngineBoundaryPopulation fresh = computed.get(node.getCode());
        if (fresh != null) {
            return BoundaryPopulation.builder().population(fresh.getPopulation()).method(fresh.getMethod())
                    .areaKm2(fresh.getAreaKm2()).build();
        }
        BoundaryPopulation copied = reused.contains(node.getCode()) ? previous.get(node.getCode()) : null;
        if (copied != null) {
            return BoundaryPopulation.builder().population(copied.getPopulation()).method(copied.getMethod())
                    .areaKm2(copied.getAreaKm2()).build();
        }
        return BoundaryPopulation.builder().method(METHOD_NO_GEOMETRY).build();
    }

    private BoundaryPopulation rollUp(BoundaryNode node, Map<String, BoundaryPopulation> rows, List<String> groups) {
        Map<String, Integer> population = new LinkedHashMap<>();
        double area = 0;
        boolean areaKnown = true;
        for (String group : groups) {
            population.put(group, 0);
        }
        for (String child : node.getChildCodes()) {
            BoundaryPopulation row = rows.get(child);
            for (String group : groups) {
                Integer value = row.getPopulation().get(group);
                population.merge(group, value == null ? 0 : value, Integer::sum);
            }
            if (row.getAreaKm2() == null) {
                areaKnown = false;
            } else {
                area += row.getAreaKm2();
            }
        }
        return BoundaryPopulation.builder().population(population).method(METHOD_ROLLUP)
                .areaKm2(areaKnown ? area : null).build();
    }

    private Map<String, BoundaryGeometry> locatedInTree(BoundaryTree tree, Map<String, BoundaryGeometry> geometries) {
        Map<String, BoundaryGeometry> located = new LinkedHashMap<>();
        for (BoundaryNode node : tree.all()) {
            BoundaryGeometry geometry = geometries.get(node.getCode());
            if (geometry != null) {
                located.put(node.getCode(), geometry);
            }
        }
        return located;
    }

    private boolean hasChildren(BoundaryNode node) {
        return node.getChildCodes() != null && !node.getChildCodes().isEmpty();
    }

    private boolean hasValue(BoundaryPopulation row) {
        return row != null && row.getPopulation() != null && !METHOD_NO_GEOMETRY.equals(row.getMethod());
    }

    private long lastModified(BoundaryGeometry geometry) {
        Long time = geometry.getAuditDetails() == null ? null : geometry.getAuditDetails().getLastModifiedTime();
        return time == null ? Long.MAX_VALUE : time;
    }
}
