import { FdAccountDetail } from './fd-account-detail';

describe('FdAccountDetail lifecycle actions', () => {
  it('allows funding only while an account is pending', () => {
    expect(FdAccountDetail.actionsForStatus('PENDING')).toEqual(['FUND']);
    expect(FdAccountDetail.actionsForStatus('PENDING')).not.toContain('ACCRUE');
    expect(FdAccountDetail.actionsForStatus('PENDING')).not.toContain('CLOSE');
  });

  it('allows processing and premature request while active', () => {
    expect(FdAccountDetail.actionsForStatus('ACTIVE')).toEqual(['ACCRUE', 'MATURE', 'REQUEST_PREMATURE']);
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
    expect(FdAccountDetail.requiresConfirmation('ACCRUE')).toBeFalse();
    expect(FdAccountDetail.requiresConfirmation('MATURE')).toBeFalse();
  });
});
