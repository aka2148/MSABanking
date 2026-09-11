import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginRequest, AuthResponse, RequestContext, Channel } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = 'http://localhost:8080/api/auth'; // update once backend confirms port

  constructor(private http: HttpClient) {}

  login(identifier: string, password: string): Observable<AuthResponse> {
    const payload: LoginRequest = { identifier, password, context: this.buildContext() };
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, payload);
  }

  buildContext(): RequestContext {
    return {
      channel: this.detectChannel(),
      localTimestamp: new Date().toISOString(),
      requestId: crypto.randomUUID(),
    };
  }

  private detectChannel(): Channel {
    return /Android|iPhone|iPad/i.test(navigator.userAgent) ? 'MOBILE' : 'WEB';
  }
}