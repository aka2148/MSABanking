import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Signup } from './signup';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('Signup', () => {
  let component: Signup;
  let fixture: ComponentFixture<Signup>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [provideHttpClient(), provideRouter([]), provideNoopAnimations()]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Signup);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
  it('collects only basic and account details across two steps', () => {
    expect(component.stepLabels).toEqual(['Basic details', 'Account setup']);
    expect(Object.keys(component.personalGroup.controls)).toEqual(['fullName', 'preferredLanguage']);
    expect(component.form.get('documents')).toBeNull();
    component.personalGroup.patchValue({ fullName: 'Demo Customer' });
    component.next();
    expect(component.currentStep).toBe(1);
    expect(component.isLastStep).toBeTrue();
  });
});
