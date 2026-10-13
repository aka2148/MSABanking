import { FdAccountDetail } from './fd-account-detail';

describe('FdAccountDetail lifecycle actions', () => {
  it('allows funding only while an account is pending', () => {
    expect(FdAccountDetail.actionsForStatus('PENDING')).toEqual(['FUND']);
    expect(FdAccountDetail.actionsForStatus('PENDING')).not.toContain('ACCRUE');
    expect(FdAccountDetail.actionsForStatus('PENDING')).not.toContain('CLOSE');
  });

  it('leaves accrual and maturity processing to the batch while active', () => {
    expect(FdAccountDetail.actionsForStatus('ACTIVE')).toEqual(['REQUEST_PREMATURE']);
    expect(FdAccountDetail.actionsForStatus('ACTIVE')).not.toContain('ACCRUE');
    expect(FdAccountDetail.actionsForStatus('ACTIVE')).not.toContain('MATURE');
    expect(FdAccountDetail.actionsForStatus('ACTIVE')).not.toContain('CLOSE');
  });

  it('allows closure and history after maturity', () => {
    expect(FdAccountDetail.actionsForStatus('MATURED')).toContain('CLOSE');
    expect(FdAccountDetail.actionsForStatus('MATURED')).not.toContain('FUND');
  });

  it('does not expose mutating actions after closure', () => {
    expect(FdAccountDetail.actionsForStatus('CLOSED')).toEqual([]);
    expect(FdAccountDetail.actionsForStatus('PREMATURELY_CLOSED')).toEqual([]);
  });

  it('requires confirmation for final actions', () => {
    expect(FdAccountDetail.requiresConfirmation('FUND')).toBeTrue();
    expect(FdAccountDetail.requiresConfirmation('CLOSE')).toBeTrue();
    expect(FdAccountDetail.requiresConfirmation('REQUEST_PREMATURE')).toBeTrue();
    expect(FdAccountDetail.requiresConfirmation('APPROVE_PREMATURE')).toBeTrue();
  });

  it('labels quarterly capitalization as a customer-readable history item', () => {
    const component = new FdAccountDetail({} as never, {} as never);
    expect(component.entryLabel('CAPITALIZATION')).toBe('Interest capitalized');
    expect(component.formatFrequency('AT_MATURITY')).toBe('At Maturity');
  });
});
