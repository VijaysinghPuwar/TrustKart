# Image and asset audit

Independent binary/dimension/hash scan: **7,841 public files, 149,513,494 bytes (142.6MiB)**, including **7,833 WebP** images and four PNGs. All 2,201 product primary image metadata records were checked for referenced file existence and primary metadata/alt coverage by the audit script; no validation errors were found. This is not a full independent licensing/model-authenticity investigation, nor proof that every option photo reference was tested in the browser.

| Asset group | Files | Mean bytes | Median bytes | Largest bytes |
|---|---:|---:|---:|---:|
| 400px WebP | 3,925 | 8,601 | 7,276 | 42,366 |
| 800px WebP | 3,907 | 29,538 | 22,602 | 196,998 |

Largest public asset: `brand/icon-512.png`, 219,131 bytes. Largest product file: `images/catalog/samsung-micro-rgb-r95h-800.webp`, 196,998 bytes at 800×800. The remaining WebP inventory includes assets outside those filename groups. No multi-megabyte 5,000px product-image pattern was found; recommending universal recompression or smaller source quality would be unsupported.

## Delivery and visual behavior

`ProductImage.tsx` supplies 400w/800w srcset, sizes, intrinsic width/height, async decoding, priority-aware eager/lazy loading and an accessible named fallback. Images use object-contain. Hero first-slide priority is explicit; the first home LCP candidate was the hero product image. Browser-selected sources account for high-DPR mobile transfer: fewer requests can still download more bytes than desktop. Preserve 800px options for sharp mobile/detail images.

The route sweep detected no broken loaded product images across 207 captures. Product detail sends gallery/options metadata, but image downloads are governed by rendered elements and lazy loading. The 30 performance runs quantify initial versus after-scroll transfer in FRONTEND_PERFORMANCE.md. No evidence supports stripping useful gallery content or removing premium hero imagery.

## Findings

[IMG-001](OPTIMIZATION_FINDINGS.md#img-001): 1,094 byte-identical hash groups account for 21,273,614 redundant bytes (~20.3MiB). This is a deployment-storage upper bound, not a promise of that much less per-page transfer. Canonical URLs can also help reuse between products, but model/color matching, attribution and old CDN-cached URLs must be preserved.

[QA-001](OPTIMIZATION_FINDINGS.md#qa-001): the repository validator passed with 25 warnings but checked only the 113 demo products. Extend it to the actual expanded catalog and variant references. The audit's broader primary-image scan should not be mistaken for existing CI coverage.

[FE-001](OPTIMIZATION_FINDINGS.md#fe-001) removes unused card metadata; it does not remove displayed imagery. No blanket AVIF conversion, lower quality setting or gallery deletion is recommended without side-by-side quality/transfer comparisons.

Evidence: [asset summary](evidence/assets.json), [file inventory](evidence/asset-inventory.json), [exact duplicates](evidence/asset-duplicates.json), [validator output](evidence/image-validation.log). Source/provenance docs were read as claims and compared to manifests; they were not rewritten.
