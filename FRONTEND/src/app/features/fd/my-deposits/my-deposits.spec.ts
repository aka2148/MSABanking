import { of, throwError } from 'rxjs';
import { FdService } from '../../../core/services/fd.service';
import { MyDeposits } from './my-deposits';

describe('MyDeposits', () => {
  it('loads and displays the authenticated customer account summaries', () => {
    const component = new MyDeposits({
      hasPendingSubmission: () => false,
      listAccounts: () => of({
        data: [{
          account_id: 9, account_number: 'FD-0009', product_code: 'FD-12',
          product_name: 'Academic Saver', currency_code: 'USD', principal_amount: 1000,
          tenure_months: 12, annual_rate: 0.05, open_date: '2026-10-13',
          maturity_date: '2027-10-13', maturity_amount: 1050, status: 'ACTIVE', funding_recorded: true,
        }],
        pagination: { page: 1, page_size: 20, total_records: 1 },
      }),
    } as unknown as FdService);

    component.ngOnInit();

    expect(component.loading).toBeFalse();
    expect(component.loaded).toBeTrue();
    expect(component.accounts[0].account_number).toBe('FD-0009');
    expect(component.totalPages).toBe(1);
  });

  it('shows an error state and allows retry after a failed list request', () => {
    let attempts = 0;
    const component = new MyDeposits({
      hasPendingSubmission: () => false,
      listAccounts: () => {
        attempts++;
        return attempts === 1 ? throwError(() => ({ status: 503 })) : of({
          data: [], pagination: { page: 1, page_size: 20, total_records: 0 },
        });
      },
    } as unknown as FdService);

    component.ngOnInit();
    expect(component.errorMessage).toBeTruthy();
    component.load();
    expect(component.errorMessage).toBe('');
    expect(component.loaded).toBeTrue();
    expect(component.accounts).toEqual([]);
  });
});
