package com.vijaysinghpuwar.trustkart.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One specification a category's products carry, e.g. "socket" (TEXT) or "vramGb" (NUMBER, GB). */
@Entity
@Table(name = "spec_definition")
public class SpecDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private SpecDataType dataType;

    private String unit;

    @Column(name = "group_label", nullable = false)
    private String groupLabel;

    @Column(nullable = false)
    private boolean filterable;

    @Column(nullable = false)
    private boolean comparable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected SpecDefinition() {}

    public SpecDefinition(Category category, String key, String label, SpecDataType dataType, String unit,
            String groupLabel, boolean filterable, boolean comparable, int sortOrder) {
        this.category = category;
        this.key = key;
        this.label = label;
        this.dataType = dataType;
        this.unit = unit;
        this.groupLabel = groupLabel;
        this.filterable = filterable;
        this.comparable = comparable;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public SpecDataType getDataType() {
        return dataType;
    }

    public String getUnit() {
        return unit;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public boolean isComparable() {
        return comparable;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
