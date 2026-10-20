import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

@Component({
  selector: 'app-fd-not-found',
  standalone: true,
  imports: [TranslatePipe, RouterLink, WorkspaceShell],
  templateUrl: './fd-not-found.html',
  styleUrl: './fd-not-found.scss',
})
export class FdNotFound {}
