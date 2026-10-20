import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslationService } from './translation.service';

// Impure so existing literal bindings update immediately after a language change.
@Pipe({ name: 'translate', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly translations = inject(TranslationService);
  transform(value: unknown, parameters: readonly unknown[] = []): string {
    return this.translations.translate(value, parameters);
  }
}
