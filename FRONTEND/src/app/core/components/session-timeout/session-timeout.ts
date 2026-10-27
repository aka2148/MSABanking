import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-session-timeout',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="warning" class="session-shade" role="presentation">
      <section class="session-dialog" role="alertdialog" aria-modal="true" aria-labelledby="session-title">
        <h2 id="session-title">Still there?</h2>
        <p>Your session will end soon due to inactivity. Continue to stay signed in.</p>
        <button type="button" (click)="continueSession()">Continue session</button>
        <button type="button" class="secondary" (click)="signOut()">Sign out</button>
      </section>
    </div>
  `,
  styles: [`
    .session-shade { position:fixed; inset:0; z-index:2000; display:grid; place-items:center; background:#0009; }
    .session-dialog { width:min(90vw,420px); padding:24px; border:1px solid #63533b; border-radius:16px; background:#17171d; color:#fff; box-shadow:0 20px 60px #0008; }
    .session-dialog h2 { margin:0 0 12px; } .session-dialog p { color:#c5c5ce; line-height:1.5; }
    .session-dialog button { margin:12px 10px 0 0; padding:10px 16px; border:0; border-radius:8px; background:#ee9b12; color:#17120a; font-weight:700; cursor:pointer; }
    .session-dialog button.secondary { background:transparent; color:#ddd; border:1px solid #666; }
  `],
})
export class SessionTimeout implements OnInit, OnDestroy {
  warning = false;
  private idleSeconds = 300;
  private idle = 0;
  private warningSeconds = 0;
  private expiresAt = 0;
  private tickHandle?: number;
  private lastHeartbeat = 0;
  private heartbeatPending = false;
  private active = false;
  private subscription?: Subscription;

  constructor(private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.subscription = this.auth.session$.subscribe((session) => {
      this.active = session.authenticated;
      const staff = session.role?.startsWith('ROLE_ADMIN') || session.role?.includes('OFFICER') || session.role?.includes('STAFF');
      this.idleSeconds = staff ? 900 : 300;
      this.expiresAt = this.readTokenExpiry(session.token);
      this.idle = 0;
      this.warning = false;
    });
    this.tickHandle = window.setInterval(() => this.tick(), 1000);
  }

  ngOnDestroy(): void { this.subscription?.unsubscribe(); if (this.tickHandle) window.clearInterval(this.tickHandle); }

  @HostListener('document:pointerdown') onActivity(): void { this.recordActivity(); }
  @HostListener('document:keydown') onKeyActivity(): void { this.recordActivity(); }
  @HostListener('document:touchstart') onTouchActivity(): void { this.recordActivity(); }
  @HostListener('document:wheel') onWheelActivity(): void { this.recordActivity(); }

  continueSession(): void {
    this.idle = 0; this.warningSeconds = 0; this.warning = false;
    this.auth.heartbeat().subscribe({ error: () => this.signOut() });
  }

  signOut(): void { this.auth.endSession(); this.warning = false; void this.router.navigate(['/login']); }

  private recordActivity(): void {
    if (!this.active || this.warning) return;
    this.idle = 0;
    const now = Date.now();
    if (now - this.lastHeartbeat >= 45_000) this.sendHeartbeat(now);
  }

  private tick(): void {
    if (!this.active) return;
    if (this.expiresAt > 0 && Date.now() >= this.expiresAt) { this.signOut(); return; }
    if (this.warning) {
      this.warningSeconds++;
      if (this.warningSeconds >= 60) this.signOut();
      return;
    }
    this.idle++;
    if (this.idle >= this.idleSeconds - 60) { this.warning = true; this.warningSeconds = 0; }
    if (this.idle >= this.idleSeconds) this.signOut();
  }

  private sendHeartbeat(now: number): void {
    if (this.heartbeatPending) return;
    this.heartbeatPending = true;
    this.lastHeartbeat = now;
    this.auth.heartbeat().subscribe({ next: () => this.heartbeatPending = false, error: () => { this.heartbeatPending = false; this.signOut(); } });
  }

  private readTokenExpiry(token: string | null): number {
    try {
      const payload = token?.split('.')[1];
      if (!payload) return 0;
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return Number(JSON.parse(json).exp) * 1000 || 0;
    } catch { return 0; }
  }
}
