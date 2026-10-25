import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { throwError } from 'rxjs';
import { FdService } from '../../core/services/fd.service';
import { SavingsAccounts } from './savings-accounts';

describe('SavingsAccounts transfer form', () => {
  it('reuses a retry key until the explicit transfer fields change', () => {
    const fd = {
      transferSavings: jasmine.createSpy('transferSavings').and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Conflict' } })),
      ),
    } as unknown as FdService;
    const component = new SavingsAccounts(fd, new FormBuilder());
    component.transferForm.setValue({ fromAccountId: '1', toAccountNumber: 'SA-DEST-1234', amount: 10, remark: '' });

    component.transfer();
    const firstKey = (fd.transferSavings as jasmine.Spy).calls.mostRecent().args[1];
    component.transfer();
    const retryKey = (fd.transferSavings as jasmine.Spy).calls.mostRecent().args[1];

    expect(retryKey).toBe(firstKey);
    component.transferForm.controls.amount.setValue(11);
    component.transfer();
    const changedRequestKey = (fd.transferSavings as jasmine.Spy).calls.mostRecent().args[1];
    expect(changedRequestKey).not.toBe(firstKey);
  });
});
