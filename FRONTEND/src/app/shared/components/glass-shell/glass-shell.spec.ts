import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GlassShell } from './glass-shell';

describe('GlassShell', () => {
  let component: GlassShell;
  let fixture: ComponentFixture<GlassShell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GlassShell]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GlassShell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
