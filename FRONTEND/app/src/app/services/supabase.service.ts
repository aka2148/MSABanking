import { Injectable } from '@angular/core';
import { createClient, SupabaseClient } from '@supabase/supabase-js';

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class SupabaseService {
  private readonly client: SupabaseClient;

  constructor() {
    const { url, anonKey } = environment.supabase;

    if (!url || !anonKey) {
      throw new Error(
        'Missing Supabase environment values. Set NG_APP_SUPABASE_URL and NG_APP_SUPABASE_ANON_KEY in .env.'
      );
    }

    this.client = createClient(url, anonKey);
  }

  getClient(): SupabaseClient {
    return this.client;
  }
}
