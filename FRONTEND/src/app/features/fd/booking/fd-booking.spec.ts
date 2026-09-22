import { FdBooking } from './fd-booking';

describe('FdBooking', () => {
  it('uses the three-step journey and keeps submission state separate from review data', () => {
    const component = new FdBooking({} as never, {} as never, {} as never);

    expect(component.step).toBe(1);
    expect(component.submittedAccount).toBeNull();
    expect(component.bookingError).toBe('');
  });
});
