package com.vijaysinghpuwar.trustkart.catalog.application;

import com.vijaysinghpuwar.trustkart.catalog.domain.Category;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, in-memory view of the (small) category hierarchy with O(1) lookups. */
public final class CategoryTree {

    public record Node(long id, String slug, String name, String description, Long parentId, int sortOrder) {}

    private final Map<Long, Node> byId = new LinkedHashMap<>();
    private final Map<String, Node> bySlug = new HashMap<>();
    private final Map<Long, List<Node>> children = new HashMap<>();

    public CategoryTree(List<Category> categories) {
        categories.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getName))
                .forEach(c -> {
                    Node node = new Node(c.getId(), c.getSlug(), c.getName(), c.getDescription(),
                            c.getParent() == null ? null : c.getParent().getId(), c.getSortOrder());
                    byId.put(node.id(), node);
                    bySlug.put(node.slug(), node);
                });
        byId.values().forEach(n -> children.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n));
    }

    public Optional<Node> bySlug(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    public Node byId(long id) {
        return byId.get(id);
    }

    public List<Node> roots() {
        return children.getOrDefault(null, List.of());
    }

    public List<Node> childrenOf(long id) {
        return children.getOrDefault(id, List.of());
    }

    /** The node and every descendant, breadth first. */
    public List<Long> selfAndDescendantIds(long id) {
        List<Long> out = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            long next = queue.poll();
            out.add(next);
            childrenOf(next).forEach(c -> queue.add(c.id()));
        }
        return out;
    }

    /** Root first, ending with the node itself: used for breadcrumbs and spec-definition inheritance. */
    public List<Node> pathTo(long id) {
        List<Node> path = new ArrayList<>();
        for (Node n = byId.get(id); n != null; n = n.parentId() == null ? null : byId.get(n.parentId())) {
            path.addFirst(n);
        }
        return path;
    }
}
