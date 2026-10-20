import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LANGUAGES } from '../../../core/i18n/language';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-language-selector',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  template: `
    <div class="language-selector">
      <label for="site-language"><span aria-hidden="true">◎</span> {{ 'Language' | translate }}</label>
      <select id="site-language" [ngModel]="translations.language()"
              (ngModelChange)="translations.setLanguage($event)">
        @for (language of languages; track language.code) {
          <option [value]="language.code" [lang]="language.code" dir="auto">{{ language.name }}</option>
        }
      </select>
    </div>
  `,
  styles: [`
    :host { position: fixed; bottom: max(16px, env(safe-area-inset-bottom)); right: max(16px, env(safe-area-inset-right)); z-index: 1000; max-width: calc(100vw - 32px); }
    .language-selector { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 8px 12px; border: 1px solid #82704b; border-radius: 12px; background: #12121a; color: #fafafa; box-shadow: 0 4px 18px #0006; font: 500 .875rem/1.5 system-ui, sans-serif; }
    label { display: flex; align-items: center; gap: 6px; }
    label span { color: #f1c976; font-size: 1.3rem; }
    select { min-height: 44px; max-width: 100%; padding: 6px 10px; border: 1px solid #777; border-radius: 6px; background: #1a1a24; color: #fff; font: inherit; cursor: pointer; }
    select:focus-visible { outline: 3px solid #f1c976; outline-offset: 2px; }
    @media (max-width: 480px) { :host { bottom: max(8px, env(safe-area-inset-bottom)); right: 8px; } .language-selector { padding: 4px 8px; } }
  `],
})
export class LanguageSelector {
  readonly translations = inject(TranslationService);
  readonly languages = LANGUAGES;
}
