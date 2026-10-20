import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSelector } from './shared/components/language-selector/language-selector';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LanguageSelector],
  templateUrl: './app.html',
})
export class App {}
