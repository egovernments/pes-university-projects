package org.pdi.web.models;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One hierarchy type's boundaries as read from boundary-service, in depth-first order.
 */
@Getter
public class BoundaryTree {

    private final String tenantId;

    private final String hierarchyType;

    private final List<String> levels;

    private final Map<String, BoundaryNode> nodes = new LinkedHashMap<>();

    public BoundaryTree(String tenantId, String hierarchyType, List<String> levels) {
        this.tenantId = tenantId;
        this.hierarchyType = hierarchyType;
        this.levels = levels;
    }

    public void add(BoundaryNode node) {
        nodes.put(node.getCode(), node);
    }

    public BoundaryNode get(String code) {
        return nodes.get(code);
    }

    public boolean contains(String code) {
        return nodes.containsKey(code);
    }

    public Collection<BoundaryNode> all() {
        return nodes.values();
    }

    public int size() {
        return nodes.size();
    }

    /** Ancestors of {@code code}, nearest first. */
    public List<BoundaryNode> ancestors(String code) {
        List<BoundaryNode> ancestors = new ArrayList<>();
        BoundaryNode node = nodes.get(code);
        while (node != null && node.getParentCode() != null) {
            node = nodes.get(node.getParentCode());
            if (node != null) {
                ancestors.add(node);
            }
        }
        return ancestors;
    }

    /** Every node ordered from the lowest level up, so children are always seen before parents. */
    public List<BoundaryNode> deepestFirst() {
        List<BoundaryNode> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparingInt(BoundaryNode::getLevel).reversed());
        return ordered;
    }
}
