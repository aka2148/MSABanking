import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-dashboard-stub',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div style="min-height:100vh; display:flex; align-items:center; justify-content:center;
                flex-direction:column; gap:12px; font-family:'Inter',sans-serif; background:#1F140B; color:white;">
      <h2 style="font-family:'Outfit',sans-serif; margin:0;">You're in.</h2>
      <p style="color:rgba(255,255,255,0.6); max-width:360px; text-align:center; margin:0;">
        Dashboard hasn't been built yet — this is just here so login/signup have somewhere to redirect to.
      </p>
      <a routerLink="/login" style="color:#C79A3E;">&larr; Back to sign in</a>
    </div>
  `,
})
export class DashboardStub {}
