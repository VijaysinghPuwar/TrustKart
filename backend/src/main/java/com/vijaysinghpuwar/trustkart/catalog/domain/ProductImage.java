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

@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "url_large", nullable = false)
    private String urlLarge;

    @Column(name = "url_small", nullable = false)
    private String urlSmall;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private String alt;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private ImageMatch matchType;

    @Column(nullable = false)
    private String credit;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImage() {}

    public ProductImage(String urlLarge, String urlSmall, int width, int height, String alt, ImageMatch matchType,
            String credit, String sourceUrl, int sortOrder) {
        this.urlLarge = urlLarge;
        this.urlSmall = urlSmall;
        this.width = width;
        this.height = height;
        this.alt = alt;
        this.matchType = matchType;
        this.credit = credit;
        this.sourceUrl = sourceUrl;
        this.sortOrder = sortOrder;
    }

    /** Replaces the picture itself (files, alt text and provenance), keeping its place on the product. */
    public boolean replaceWith(String urlLarge, String urlSmall, int width, int height, String alt, ImageMatch matchType,
            String credit, String sourceUrl) {
        if (urlLarge.equals(this.urlLarge) && urlSmall.equals(this.urlSmall) && width == this.width
                && height == this.height && alt.equals(this.alt) && matchType == this.matchType
                && credit.equals(this.credit) && sourceUrl.equals(this.sourceUrl)) {
            return false;
        }
        this.urlLarge = urlLarge;
        this.urlSmall = urlSmall;
        this.width = width;
        this.height = height;
        this.alt = alt;
        this.matchType = matchType;
        this.credit = credit;
        this.sourceUrl = sourceUrl;
        return true;
    }

    void attachTo(Product product) {
        this.product = product;
    }

    public String getUrlLarge() {
        return urlLarge;
    }

    public String getUrlSmall() {
        return urlSmall;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getAlt() {
        return alt;
    }

    public ImageMatch getMatchType() {
        return matchType;
    }

    public String getCredit() {
        return credit;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
