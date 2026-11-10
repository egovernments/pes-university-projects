package org.pdi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.pdi.exception.CustomException;
import org.pdi.web.models.BoundaryGeometry;
import org.pdi.web.models.BoundaryNode;
import org.pdi.web.models.BoundaryTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.pdi.util.PdiConstants.ENGINE_ERROR;
import static org.pdi.util.PdiConstants.GEOMETRY_POINT;
import static org.pdi.util.PdiConstants.GEOMETRY_POLYGON;

/**
 * Joins a hierarchy with the stored locations and writes the units file the engine computes
 * over. Polygons are passed as they are; points are tagged with the nearest ancestor that has
 * a polygon, which the engine clips their Voronoi cells to.
 */
@Service
public class GeometryResolver {

    private static final String UNCLIPPED = "*";

    private final ObjectMapper objectMapper;

    @Autowired
    public GeometryResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Code of the nearest ancestor of {@code code} that has a polygon, or null when none has. */
    public String clipCode(BoundaryTree tree, Map<String, BoundaryGeometry> geometries, String code) {
        for (BoundaryNode ancestor : tree.ancestors(code)) {
            BoundaryGeometry geometry = geometries.get(ancestor.getCode());
            if (geometry != null && GEOMETRY_POLYGON.equals(geometry.getGeometryType())) {
                return ancestor.getCode();
            }
        }
        return null;
    }

    /**
     * Key of the set of points whose Voronoi cells are cut together: same boundary level, same
     * clip region. Null for a polygon, which needs no neighbours.
     */
    public String tessellationGroup(BoundaryTree tree, Map<String, BoundaryGeometry> geometries, String code) {
        BoundaryGeometry geometry = geometries.get(code);
        if (geometry == null || !GEOMETRY_POINT.equals(geometry.getGeometryType())) {
            return null;
        }
        String clip = clipCode(tree, geometries, code);
        return groupKey(clip, tree.get(code).getBoundaryType());
    }

    public String groupKey(String clipCode, String boundaryType) {
        return (clipCode == null ? UNCLIPPED : clipCode) + "|" + boundaryType;
    }

    /**
     * Writes the units of {@code codes} to {@code units}, and the ancestor polygons their points
     * are clipped to into {@code clips}. Returns {@code clips}, or null when no point needs one.
     */
    public Path writeUnits(BoundaryTree tree, Map<String, BoundaryGeometry> geometries, Collection<String> codes,
                           Path units, Path clips) {
        ArrayNode unitFeatures = objectMapper.createArrayNode();
        Set<String> clipCodes = new LinkedHashSet<>();
        for (String code : codes) {
            BoundaryGeometry geometry = geometries.get(code);
            ObjectNode properties = objectMapper.createObjectNode()
                    .put("boundaryCode", code)
                    .put("boundaryType", tree.get(code).getBoundaryType());
            if (GEOMETRY_POINT.equals(geometry.getGeometryType())) {
                String clip = clipCode(tree, geometries, code);
                properties.put("clipCode", clip);
                if (clip != null) {
                    clipCodes.add(clip);
                }
            }
            unitFeatures.add(feature(geometry, properties));
        }
        write(units, unitFeatures);
        if (clipCodes.isEmpty()) {
            return null;
        }
        ArrayNode clipFeatures = objectMapper.createArrayNode();
        for (String clip : clipCodes) {
            clipFeatures.add(feature(geometries.get(clip), objectMapper.createObjectNode().put("boundaryCode", clip)));
        }
        write(clips, clipFeatures);
        return clips;
    }

    /** Writes the given polygons as a feature collection keyed by boundary code. */
    public void writePolygons(Collection<BoundaryGeometry> geometries, Path target) {
        ArrayNode features = objectMapper.createArrayNode();
        for (BoundaryGeometry geometry : geometries) {
            if (GEOMETRY_POLYGON.equals(geometry.getGeometryType())) {
                features.add(feature(geometry,
                        objectMapper.createObjectNode().put("boundaryCode", geometry.getBoundaryCode())));
            }
        }
        write(target, features);
    }

    private ObjectNode feature(BoundaryGeometry geometry, ObjectNode properties) {
        ObjectNode feature = objectMapper.createObjectNode().put("type", "Feature");
        feature.set("geometry", geometry.getGeometry());
        feature.set("properties", properties);
        return feature;
    }

    private void write(Path target, ArrayNode features) {
        ObjectNode collection = objectMapper.createObjectNode().put("type", "FeatureCollection");
        collection.set("features", features);
        try {
            objectMapper.writeValue(target.toFile(), collection);
        } catch (IOException e) {
            throw new CustomException(ENGINE_ERROR, "could not write engine input " + target.getFileName(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
