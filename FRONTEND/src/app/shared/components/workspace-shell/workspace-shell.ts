import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { GlassShell } from '../glass-shell/glass-shell';

@Component({
  selector: 'app-workspace-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, GlassShell],
  templateUrl: './workspace-shell.html',
  styleUrl: './workspace-shell.scss',
})
export class WorkspaceShell {
  @Input() guest = false;
  @Input() backgroundUrl = '/world-map.png';
  @Input() focalX = 50;
  @Input() focalY = 42;
  @Input() zoom = 1.15;

  constructor(private readonly auth: AuthService) {}

  get staff(): boolean {
    return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(this.auth.currentRole() ?? '');
  }

  get admin(): boolean {
    return this.auth.currentRole() === 'ROLE_ADMIN';
  }
}
