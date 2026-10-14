import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { FdService, FD_API_BASE_URL, normalizeApiError } from './fd.service';
import { authGuard } from '../guards/auth.guard';
import { authInterceptor } from '../interceptors/auth.interceptor';

describe('FdService authenticated transport', () => {
  let service: FdService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('token', 'session-token');
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        FdService,
        { provide: FD_API_BASE_URL, useValue: '/fd/v1' },
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(FdService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('sends bearer authorization for product reads', () => {
    service.listProducts().subscribe();

    const request = http.expectOne('/fd/v1/products');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer session-token');
    expect(request.request.urlWithParams).not.toContain('token');
    request.flush([]);
  });

  it('reuses the same request key for retries and rotates it after success', () => {
    service.book({ product_id: 7, principal_amount: 1000, tenure_months: 12, currency_code: 'INR' })
      .subscribe({ error: () => undefined });
    const first = http.expectOne('/fd/v1/accounts');
    const firstKey = first.request.headers.get('Idempotency-Key');
    expect(firstKey).toMatch(/^[0-9a-f-]{36}$/);
    first.error(new ProgressEvent('network-error'));

    service.book({ product_id: 7, principal_amount: 1000, tenure_months: 12, currency_code: 'INR' })
      .subscribe({ error: () => undefined });
    const retry = http.expectOne('/fd/v1/accounts');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({ account_id: 1 });

    service.book({ product_id: 7, principal_amount: 1200, tenure_months: 12, currency_code: 'INR' }).subscribe();
    const next = http.expectOne('/fd/v1/accounts');
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    next.flush({ account_id: 2 });
  });

  it('blocks a changed request while the previous result is uncertain', () => {
    service.book({ product_id: 7, principal_amount: 1000, tenure_months: 12, currency_code: 'INR' })
      .subscribe({ error: () => undefined });
    const first = http.expectOne('/fd/v1/accounts');
    first.error(new ProgressEvent('network-error'));

    let conflictStatus = 0;
    service.book({ product_id: 7, principal_amount: 1200, tenure_months: 12, currency_code: 'INR' })
      .subscribe({ error: (error) => conflictStatus = error.status });

    expect(conflictStatus).toBe(409);
    http.expectNone('/fd/v1/accounts');
    expect(service.hasPendingSubmission('/accounts')).toBeTrue();
  });

  it('lists customer accounts with server pagination parameters', () => {
    service.listAccounts(2, 10).subscribe();
    const request = http.expectOne('/fd/v1/accounts?page=2&page_size=10');
    expect(request.request.method).toBe('GET');
    request.flush({ data: [], pagination: { page: 2, page_size: 10, total_records: 11 } });
  });

  it('logs out on a 401 and redirects an unauthenticated guard to login', () => {
    TestBed.inject(HttpClient).get('/protected').subscribe({ error: () => undefined });
    const request = http.expectOne('/protected');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    const tree = authGuard({} as never, {} as never);
    expect(TestBed.inject(Router).serializeUrl(tree as never)).toBe('/login');
  });

  it('normalizes both supported backend error envelopes without exposing URLs', () => {
    const error = normalizeApiError({
      status: 409,
      error: { message: 'The request was already processed.' },
      url: 'http://secret.example/fd/v1/accounts',
    } as never);

    expect(error.status).toBe(409);
    expect(error.message).toBe('The request was already processed.');
    expect(error.replayHint).toContain('existing operation');
    expect(error.message).not.toContain('secret.example');
  });
});
