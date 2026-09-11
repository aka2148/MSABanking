import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  LoginRequest,
  LoginResponse,
  SignupRequest,
  SignupResponse,
  RequestContext,
  Channel,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = 'http://localhost:8080/api/auth'; // update once backend confirms port

  constructor(private http: HttpClient) {}

  login(identifier: string, password: string): Observable<LoginResponse> {
    const payload: LoginRequest = { identifier, password, context: this.buildContext() };
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, payload);
  }

  signup(form: Omit<SignupRequest, 'context'>): Observable<SignupResponse> {
    const payload: SignupRequest = { ...form, context: this.buildContext() };
    return this.http.post<SignupResponse>(`${this.baseUrl}/signup`, payload);
  }

  /** Reuse this in every module that writes data — FD creation, transactions, forex. */
  buildContext(): RequestContext {
    return {
      channel: this.detectChannel(),
      localTimestamp: new Date().toISOString(),
      requestId: this.generateRequestId(),
    };
  }

  private detectChannel(): Channel {
    return /Android|iPhone|iPad/i.test(navigator.userAgent) ? 'MOBILE' : 'WEB';
  }

  private generateRequestId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
