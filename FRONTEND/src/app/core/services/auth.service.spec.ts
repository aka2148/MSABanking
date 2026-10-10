import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService session boundary', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('stores the returned token and role after login', () => {
    service.login('customer', 'password').subscribe();

    const request = http.expectOne('http://localhost:8080/api/v1/auth/login');
    request.flush({ success: true, token: 'session-token', roles: ['CUSTOMER'] });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getToken()).toBe('session-token');
    expect(service.currentRole()).toBe('CUSTOMER');
    expect(localStorage.getItem('token')).toBe('session-token');
    expect(localStorage.getItem('role')).toBe('CUSTOMER');
  });

  it('prioritizes a staff role when the token response contains multiple roles', () => {
    service.login('staff', 'password').subscribe();
    http.expectOne('http://localhost:8080/api/v1/auth/login').flush({
      success: true,
      token: 'staff-token',
      roles: ['ROLE_CUSTOMER', 'ROLE_BANK_OFFICER'],
    });

    expect(service.currentRole()).toBe('ROLE_BANK_OFFICER');
    expect(localStorage.getItem('role')).toBe('ROLE_BANK_OFFICER');
  });

  it('clears token and role and emits an unauthenticated session on logout', () => {
    localStorage.setItem('token', 'session-token');
    localStorage.setItem('role', 'CUSTOMER');
    const states: boolean[] = [];
    service.session$.subscribe((session) => states.push(session.authenticated));

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
    expect(service.currentRole()).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('role')).toBeNull();
    expect(states.at(-1)).toBeFalse();
  });
});
