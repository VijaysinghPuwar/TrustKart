# Resource usage and deployment audit

All numbers below are local observations on the audit machine or build artifacts. The application was not run against production services. No synthetic percentage improvement is promised.

| Resource | Measured result / scope |
|---|---|
| Frontend build | See FRONTEND_PERFORMANCE.md for all JS/CSS chunks and gzip sizes |
| Home initial desktop JS | 145,007 transferred bytes including shared chunks/response overhead |
| Home initial desktop CSS | 20,230 transferred bytes |
| Home initial desktop transfer / requests | ~948.2KiB / 83 requests; 7 API reads |
| Home initial mobile transfer / requests | ~1,031.0KiB / 48 requests at DPR 3 |
| Largest product image | 196,998 bytes, 800×800 WebP |
| Entire public asset tree | 149,513,494 bytes (142.6MiB) |
| Exact-duplicate asset storage | 21,273,614 redundant bytes upper bound |
| Backend Docker image | `docker image ls`: 459MB, tag codex-trustkart-audit:177fb10; local ARM build |
| Frontend Docker image | Not applicable: no frontend Dockerfile; static Vite deployment |
| Java late idle sample | RSS 187,152KiB (~182.8MiB), 0.0% CPU; audit process used -Xmx512m |
| Redis logical used memory | ~1.65MiB from INFO memory |
| Container memory/CPU | Point-in-time values in docker-resources.txt; not a peak/load benchmark |
| DB connections | 10 idle application connections plus active diagnostic query in initial sample |
| Slowest tested warm API median | Laptop facets 11.71ms / five local samples |
| INP / Lighthouse scores | Not measured |

Java RSS is an idle sample, not startup peak, container usage, retained-heap measurement or proof a 512MB hosted deployment is safe. Local host RAM/CPU and debug SQL differ from Render. Browser JavaScript heap medians are roughly 5–6MiB on measured pages; this excludes browser process, decoded images, GPU and native memory. No long-duration leak diagnosis was performed.

## Background resource behavior

Notification count polls every 120 seconds; hidden-tab polling requires browser-alert opt-in. Wallet and tracking have no dedicated frequent polling. Search placeholder rotates every 3.5 seconds; hero rotates every 7 seconds with cleanup/interaction/reduced-motion handling. Guest cleanup runs daily and demo stock replenishment every 15 minutes. These cadences do not justify WebSockets or a scheduler rewrite. BE-003 is the actionable background waste: unchanged notification polls can repeatedly write deleted milestones.

## Docker and deployment

Backend build passed using the existing multistage Dockerfile, Maven cache mounts, extracted jar layers, non-root JRE runtime and backend-only context. Runtime does not include Maven/node_modules or the build JDK. `.dockerignore` excludes target, IDE metadata and .env. No oversized frontend dependency tree was copied into that image. Retain debuggability/security; an unmeasured distroless/base-image migration is not a quick-win finding.

Render blueprint config sets pool size 5 and overrides JVM options for its declared small plan; the Docker default and local audit JVM settings differ. Comments claiming historical stress results were not independently accepted. This audit did not repeat a hosted 40-shopper memory test or verify current hosting quotas, so no arbitrary heap/pool values are proposed.

Vercel deploys static assets and proxies API requests. Production compression, CDN cache-hit rates, service cold starts and outgoing traffic were not measured. The 20.3MiB identical-image opportunity is build/deployment storage first; old URLs may be referenced by long-lived cached JSON. Coordinate compatibility instead of deleting assets immediately.

## Highest-value reductions

Prioritize DB-001, BE-002, BE-003, FE-001 and BE-001: query work, repeated writes and unused data. IMG-001 is lower priority because current images are already small and quality-preserving. No recommendation removes shopping functionality, high-resolution detail sources, useful notifications, security checks or transactional guarantees.

## Audit closeout

The named audit browser session, temporary Java API and Vite preview processes were stopped. Only `codex-trustkart-audit-postgres-1` and `codex-trustkart-audit-redis-1` were stopped; existing unrelated containers/processes were not stopped. The temporary snapshot, stopped isolated fixture containers/volume and local audit Docker image were retained for reproducibility; they consume disk but no running application CPU. Final Git checks showed no tracked or staged application diff, with only `codex/` plus pre-existing `scripts/renders/` untracked. No commits, pushes or deployments occurred.
