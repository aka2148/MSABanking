-- Complete the existing Java Long identifier alignment without changing V1-V27.
-- Existing foreign-key values and constraints are preserved.
ALTER TABLE public.branches ALTER COLUMN bra_branch_id TYPE BIGINT;
ALTER TABLE public.customer_classification_types ALTER COLUMN cct_type_id TYPE BIGINT;
