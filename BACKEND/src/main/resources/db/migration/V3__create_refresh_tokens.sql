-- Role 2: Refresh token persistence table for JWT rotation and replay protection
-- Shared schema change requiring team leader approval before applying to production PostgreSQL.
CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     INT NOT NULL REFERENCES public.users(usr_user_id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON public.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON public.refresh_tokens(token);
