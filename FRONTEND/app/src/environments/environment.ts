export const environment = {
  production: import.meta.env['NG_APP_ENV'] === 'production',
  supabase: {
    url: import.meta.env['NG_APP_SUPABASE_URL'] ?? '',
    anonKey: import.meta.env['NG_APP_SUPABASE_ANON_KEY'] ?? ''
  }
} as const;
