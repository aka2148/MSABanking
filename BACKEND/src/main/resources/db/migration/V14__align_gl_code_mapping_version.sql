ALTER TABLE public.gl_code_mapping
    ALTER COLUMN gcm_version TYPE VARCHAR(20)
        USING gcm_version::TEXT;
