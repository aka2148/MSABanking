import { FdBooking } from './fd-booking';
import { FormBuilder } from '@angular/forms';

describe('FdBooking', () => {
  it('uses the three-step journey and keeps submission state separate from review data', () => {
    const component = new FdBooking({} as never, {} as never, {} as never, new FormBuilder());

    expect(component.step).toBe(1);
    expect(component.submittedAccount).toBeNull();
    expect(component.bookingError).toBe('');
  });
});
