import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSelector } from './shared/components/language-selector/language-selector';
import { SessionTimeout } from './core/components/session-timeout/session-timeout';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LanguageSelector, SessionTimeout],
  templateUrl: './app.html',
})
export class App {}
