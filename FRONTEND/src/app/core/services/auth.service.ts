import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import {
  LoginRequest,
  LoginResponse,
  SignupRequest,
  SignupResponse,
  RequestContext,
  Channel,
  AuthSession,
} from '../models/auth.models';
import { API_BASE_URL, apiUrl } from './api-config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'token';
  private readonly roleKey = 'role';
  private readonly sessionSubject = new BehaviorSubject<AuthSession>(this.readSession());

  readonly session$ = this.sessionSubject.asObservable();

  customerProfile(): Observable<{ kycStatus: string | null; riskCategory: string | null }> {
    return this.http.get<{ kycStatus: string | null; riskCategory: string | null }>(apiUrl(this.apiBaseUrl, '/api/v1/auth/me'));
  }

  constructor(private http: HttpClient, @Inject(API_BASE_URL) private readonly apiBaseUrl: string) {}

  login(identifier: string, password: string): Observable<LoginResponse> {
    const payload = {
      username: identifier,
      password,
      context: this.buildContext(),
    };
    return this.http.post<LoginResponse>(apiUrl(this.apiBaseUrl, '/api/v1/auth/login'), payload).pipe(
      tap((response) => {
        if (response.token) {
          const sessionRole = response.roles?.find((role) => role === 'ROLE_ADMIN')
            ?? response.roles?.find((role) => role === 'ROLE_BANK_OFFICER')
            ?? response.role
            ?? response.roles?.[0]
            ?? null;
          this.setSession(response.token, sessionRole);
        }
      })
    );
  }

  signup(form: Omit<SignupRequest, 'context'>): Observable<SignupResponse> {
    const payload: SignupRequest = { ...form, context: this.buildContext() };
    return this.http.post<SignupResponse>(apiUrl(this.apiBaseUrl, '/api/v1/auth/register'), payload);
  }

  /** Reuse this in every module that writes data — FD creation, transactions, forex. */
  buildContext(): RequestContext {
    return {
      channel: this.detectChannel(),
      localTimestamp: new Date().toISOString(),
      requestId: this.generateRequestId(),
    };
  }

  getToken(): string | null {
    return this.sessionSubject.value.token;
  }

  currentRole(): string | null {
    return this.sessionSubject.value.role;
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.roleKey);
    this.sessionSubject.next({ authenticated: false, token: null, role: null });
  }

  private setSession(token: string, role: string | null): void {
    localStorage.setItem(this.tokenKey, token);
    if (role) {
      localStorage.setItem(this.roleKey, role);
    } else {
      localStorage.removeItem(this.roleKey);
    }
    this.sessionSubject.next({ authenticated: true, token, role });
  }

  private readSession(): AuthSession {
    const token = localStorage.getItem(this.tokenKey);
    const role = localStorage.getItem(this.roleKey);
    return { authenticated: token !== null, token, role };
  }

  private detectChannel(): Channel {
    return typeof navigator !== 'undefined' && /Android|iPhone|iPad/i.test(navigator.userAgent)
      ? 'MOBILE'
      : 'WEB';
  }

  private generateRequestId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
