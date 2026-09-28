package com.vijaysinghpuwar.trustkart.catalog.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 19, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    /** Category-specific specifications, validated against {@link SpecDefinition}s before being stored. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> specs = new LinkedHashMap<>();

    /** Selectable purchase options; see {@link ProductOptions}. Empty for products sold in one configuration. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> options = new ArrayList<>();

    @Column(nullable = false)
    private String keywords;

    /** Brand, category path and spec values, denormalised so the generated tsvector column can index them. */
    @Column(name = "search_text", nullable = false)
    private String searchText = "";

    @Column(nullable = false)
    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private Inventory inventory;

    protected Product() {}

    public Product(String sku, String slug, String name, Brand brand, Category category, String summary,
            String description, BigDecimal price, BigDecimal compareAtPrice, int warrantyMonths,
            Map<String, Object> specs, String keywords, boolean featured, ProductStatus status) {
        this.sku = sku;
        this.slug = slug;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.summary = summary;
        this.description = description;
        this.price = price;
        this.compareAtPrice = compareAtPrice;
        this.warrantyMonths = warrantyMonths;
        this.specs = new LinkedHashMap<>(specs);
        this.keywords = keywords;
        this.featured = featured;
        this.status = status;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void addImage(ProductImage image) {
        image.attachTo(this);
        images.add(image);
    }

    public void attachInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public StockStatus stockStatus() {
        return inventory.stockStatus(status);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public Brand getBrand() {
        return brand;
    }

    public Category getCategory() {
        return category;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCompareAtPrice() {
        return compareAtPrice;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public Map<String, Object> getSpecs() {
        return Map.copyOf(specs);
    }

    public List<ProductOptions.Group> getOptions() {
        return ProductOptions.fromJson(options);
    }

    /** Replaces the options after validating them against the base price. Returns whether anything changed. */
    public boolean setOptions(List<ProductOptions.Group> groups) {
        ProductOptions.validate(groups, price);
        List<Map<String, Object>> json = ProductOptions.toJson(groups);
        if (json.equals(options)) {
            return false;
        }
        this.options = json;
        return true;
    }

    public String getKeywords() {
        return keywords;
    }

    public boolean isFeatured() {
        return featured;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ProductImage> getImages() {
        return List.copyOf(images);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
