-- Add embedding column to skill_definitions for vector discovery
ALTER TABLE skill_definitions ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_skill_def_embedding ON skill_definitions USING hnsw (embedding vector_cosine_ops);
