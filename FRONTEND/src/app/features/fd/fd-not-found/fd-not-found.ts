import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-fd-not-found',
  standalone: true,
  imports: [RouterLink, GlassShell],
  templateUrl: './fd-not-found.html',
  styleUrl: './fd-not-found.scss',
})
export class FdNotFound {}
