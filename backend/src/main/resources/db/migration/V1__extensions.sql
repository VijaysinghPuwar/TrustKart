-- pg_trgm powers fuzzy product-name matching; vector is used by semantic search (Phase 9).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
