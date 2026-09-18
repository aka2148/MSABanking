# Coding Conventions

**Analysis Date:** 2026-09-18

## Naming Patterns

**Files:**
- Use lowercase, kebab-case feature and component filenames, with Angular suffixes for role-specific files: `src/app/core/services/auth.service.ts`, `src/app/features/auth/login/login.ts`, and `src/app/shared/components/glass-shell/glass-shell.ts`.
- Co-locate a component's template, stylesheet, and spec beside its TypeScript implementation: `src/app/features/auth/login/login.ts`, `login.html`, `login.scss`, and `login.spec.ts`.
- Use underscore-prefixed Sass partials for shared style modules, such as `src/app/shared/styles/_tokens.scss` and `src/app/shared/styles/_glass.scss`.

**Functions:**
- Use camelCase verbs for methods and handlers: `submit`, `onFileSelected`, `buildContext`, `detectChannel`, and `recompute` in `src/app/features/auth/signup/signup.ts` and `src/app/core/services/auth.service.ts`.
- Use Angular lifecycle names exactly as required by their interfaces (`ngOnInit`, `ngOnChanges`, `ngAfterViewInit`, and `ngOnDestroy`).
- Use concise predicate getters with `is` or descriptive state names, such as `isLastStep` and `passwordsMismatch` in `src/app/features/auth/signup/signup.ts`.

**Variables:**
- Use camelCase for fields and local variables: `errorMessage`, `currentStep`, `selectedValue`, and `requestId`.
- Use `readonly` for constants or configuration that should not be reassigned, such as `stepKeys`, `stepLabels`, and `baseUrl`.
- Use uppercase snake case for module-level immutable lookup constants: `CURRENCY_FOCAL_POINTS`, `IMG_W`, and `IMG_H`.

**Types:**
- Use PascalCase for classes, interfaces, and type aliases: `AuthService`, `GlassSelectOption`, `RequestContext`, and `PreferredCurrency`.
- Use explicit interfaces for request/response payloads and domain data in `src/app/core/models/auth.models.ts`.
- Use string-literal unions for bounded values such as `Channel`, `PreferredCurrency`, and `LegalEntityType`.
- Keep Angular component class names concise and feature-oriented (`Login`, `Signup`, `Landing`, `GlassShell`); selectors use the `app-` prefix configured in `FRONTEND/angular.json`.

## Code Style

**Formatting:**
- Use two-space indentation, single-quoted strings, semicolons, and trailing commas in multiline Angular metadata and function arguments, as shown in `src/app/features/auth/login/login.ts`.
- Keep Angular metadata explicit: standalone components declare `standalone: true`, direct dependencies in `imports`, and external `templateUrl`/`styleUrl` values.
- Use SCSS for component and global styles. Shared styles are imported from `src/styles.scss`, and design tokens are CSS custom properties in `src/app/shared/styles/_tokens.scss`.
- No Prettier, ESLint, or other formatter/linter configuration is present in `FRONTEND`; preserve the prevailing TypeScript formatting manually unless a tool is introduced.
- Existing formatting is not fully uniform: some imports and long expressions remain on one line, while other multiline expressions are vertically expanded. Prefer readable wrapping for new code and match the nearby file.

**Linting:**
- Not detected. TypeScript compiler checks are the enforced static checks visible in `FRONTEND/tsconfig.json`.
- Keep code compatible with `strict`, `noImplicitOverride`, `noPropertyAccessFromIndexSignature`, `noImplicitReturns`, and `noFallthroughCasesInSwitch`.
- Angular templates are compiled with `strictInjectionParameters`, `strictInputAccessModifiers`, and `strictTemplates`.

## Import Organization

**Order:**
1. Angular framework and platform imports, for example `@angular/core`, `@angular/common`, `@angular/forms`, and `@angular/router`.
2. Third-party libraries, such as `rxjs` and `three`.
3. Relative application imports, with core services/models before shared components where that grouping is useful.

This ordering is visible in `src/app/features/auth/login/login.ts`, `src/app/features/auth/signup/signup.ts`, and `src/app/features/landing/landing.ts`, although imports are not alphabetized consistently.

**Path Aliases:**
- No TypeScript path aliases are configured in `FRONTEND/tsconfig.json`.
- Use relative paths from the current feature, such as `../../../core/services/auth.service` and `../../../shared/components/glass-shell/glass-shell`.

## Error Handling

**Patterns:**
- Validate reactive forms before side effects. `Login.submit` and `Signup.submit` mark invalid controls touched, set a user-facing `errorMessage`, and return early in `src/app/features/auth/login/login.ts` and `src/app/features/auth/signup/signup.ts`.
- Handle HTTP observables with an object passed to `subscribe`, separating `next` and `error` behavior. Reset `loading` in both paths and show a safe fallback message in `src/app/features/auth/login/login.ts` and `src/app/features/auth/signup/signup.ts`.
- Prefer nullish coalescing for optional backend messages, for example `res.message ?? 'Unable to sign in.'` in `src/app/features/auth/login/login.ts`.
- Keep validation and transport errors in component state for template rendering; templates display `errorMessage` through the `glass-error` element in `src/app/features/auth/login/login.html` and `src/app/features/auth/signup/signup.html`.
- Guard browser-only work for SSR with `typeof window !== 'undefined'` in `src/app/shared/components/glass-shell/glass-shell.ts`.
- Let bootstrap failures reach a terminal `console.error` handler in `src/main.ts`; no centralized error boundary or logging service is present.

## Logging

**Framework:** console only.

**Patterns:**
- Use user-safe text in component state for expected form/API failures.
- The only observed process-level logging is `console.error(err)` in `src/main.ts` for bootstrap rejection.
- Do not log credentials, request payloads, or identity-document values when extending `src/app/core/services/auth.service.ts`.

## Comments

**When to Comment:**
- Use comments for domain/schema decisions, browser/rendering calculations, and temporary integration assumptions. Examples include the schema mapping notes in `src/app/core/models/auth.models.ts`, the focal-point math in `src/app/shared/components/glass-shell/glass-shell.ts`, and the placeholder branch list in `src/app/features/auth/signup/signup.ts`.
- Keep comments near the behavior they explain. Block comments are used for multi-step algorithms and dynamic validation; short inline comments identify temporary or backend-dependent behavior.

**JSDoc/TSDoc:**
- JSDoc is occasional rather than systematic. `AuthService.buildContext` has a short documentation comment in `src/app/core/services/auth.service.ts`.
- Use typed signatures and descriptive names first; add JSDoc when a public method has domain rules or integration constraints that are not obvious from its types.

## Function Design

**Size:**
- Keep ordinary UI handlers small and focused, as in `Login.submit` in `src/app/features/auth/login/login.ts`.
- Large orchestration methods exist where a feature owns complex form construction and payload mapping: `Signup` in `src/app/features/auth/signup/signup.ts` is 494 lines and contains the form, validation, step navigation, and submission workflow. New features should separate similarly growing concerns when practical.

**Parameters:**
- Use explicit types for public methods and event handlers (`Event`, `PointerEvent`, `MouseEvent`, `FormGroup`, and domain interfaces).
- Use object payloads for multi-field service requests. `AuthService.signup` accepts `Omit<SignupRequest, 'context'>` and adds the request context internally in `src/app/core/services/auth.service.ts`.
- Prefer Angular forms and typed domain models over untyped template state, while recognizing that the current forms use `FormGroup` rather than typed form builders.

**Return Values:**
- Annotate methods with meaningful return types, including `void`, `Observable<LoginResponse>`, `Observable<SignupResponse>`, and domain types.
- Return RxJS observables from service methods; subscribe in components where UI state and navigation are coordinated, as in `src/app/core/services/auth.service.ts` and `src/app/features/auth/login/login.ts`.
- Use getters for derived component state (`currentGroup`, `isLastStep`, `selectedLabel`) in `src/app/features/auth/signup/signup.ts` and `src/app/shared/components/glass-select/glass-select.ts`.

## Module Design

**Exports:**
- Export standalone Angular components and injectable services directly from their feature files.
- Export model interfaces and unions directly from `src/app/core/models/auth.models.ts`.
- Keep implementation-only interfaces and constants private to their feature module when not reused, as `Product` in `src/app/features/landing/landing.ts` and `TransactionArc` in `src/app/features/landing/components/globe-hero/globe-hero.ts`.

**Barrel Files:**
- Not detected. Import concrete files directly using relative paths.
- Do not add a barrel solely for convenience unless a feature has a clear, stable public API.

**Angular Architecture:**
- Use standalone components and route-level lazy `loadComponent` imports in `src/app/app.routes.ts`.
- Put cross-feature services/models under `src/app/core/`, reusable UI under `src/app/shared/`, and user-facing feature code under `src/app/features/`.
- Register application-wide providers in `src/app/app.config.ts`; register singleton services with `@Injectable({ providedIn: 'root' })` as in `src/app/core/services/auth.service.ts`.

---

*Convention analysis: 2026-09-18*
