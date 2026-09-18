# Testing Patterns

**Analysis Date:** 2026-09-18

## Test Framework

**Runner:**
- Angular `TestBed` APIs are used by all repository specs, with Vitest-style global `describe`, `it`, and `expect` calls.
- No test runner configuration file is present in `FRONTEND`.
- `FRONTEND/tsconfig.spec.json` declares `vitest/globals`, but `vitest` is not declared in `FRONTEND/package.json` and `npm ls vitest --depth=0` reports no installed Vitest package.

**Assertion Library:**
- Global `expect` assertions are used, currently only for truthiness and text containment: `toBeTruthy()` and `toContain()` in `FRONTEND/src/app/app.spec.ts` and the component specs.
- No custom matchers or assertion helpers are present.

**Run Commands:**
```bash
npm test                 # Not available: no "test" script exists in FRONTEND/package.json
npm run build            # Available compile/build check; does not execute specs
npm run test -- --run    # Not available until a test script and runner are configured
```

The repository's current `npm run build` completes successfully, but it uses `tsconfig.app.json`, which includes the application entry point rather than the `*.spec.ts` files. Treat a passing build as separate from test validation.

## Test File Organization

**Location:**
- Specs are co-located with the unit under test: `src/app/app.spec.ts`, `src/app/core/services/auth.spec.ts`, `src/app/features/auth/login/login.spec.ts`, `src/app/features/auth/signup/signup.spec.ts`, and `src/app/shared/components/glass-shell/glass-shell.spec.ts`.
- There are no separate `tests/`, `e2e/`, fixture, or test-helper directories detected.

**Naming:**
- Use the implementation basename plus `.spec.ts`: `login.ts` → `login.spec.ts`, `signup.ts` → `signup.spec.ts`, and `glass-shell.ts` → `glass-shell.spec.ts`.
- Use a `describe` suite named after the class/component and `it` descriptions beginning with behavior-oriented phrases such as `should create`.

**Structure:**
```text
FRONTEND/src/app/<unit>.spec.ts
FRONTEND/src/app/core/services/auth.spec.ts
FRONTEND/src/app/features/auth/<feature>/<feature>.spec.ts
FRONTEND/src/app/shared/components/<component>/<component>.spec.ts
```

## Test Structure

**Suite Organization:**
```typescript
describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

This pattern is used in `FRONTEND/src/app/features/auth/login/login.spec.ts`, `FRONTEND/src/app/features/auth/signup/signup.spec.ts`, and `FRONTEND/src/app/shared/components/glass-shell/glass-shell.spec.ts`.

**Patterns:**
- Import standalone components directly in `TestBed.configureTestingModule({ imports: [Component] })`; do not declare them in a declarations array. See `FRONTEND/src/app/features/auth/login/login.spec.ts`.
- Compile component metadata asynchronously with `await ...compileComponents()` and wait for stabilization with `await fixture.whenStable()`.
- Retain both the `ComponentFixture<T>` and component instance when tests need to interact with the DOM or component state.
- The root component spec uses the same TestBed setup and additionally reads `fixture.nativeElement as HTMLElement` in `FRONTEND/src/app/app.spec.ts`.
- Service setup currently uses synchronous `beforeEach`, `TestBed.configureTestingModule({})`, and `TestBed.inject(...)` in `FRONTEND/src/app/core/services/auth.spec.ts`.

## Mocking

**Framework:**
- No mocking framework, spy usage, HTTP testing provider, or test double factory is present.

**Patterns:**
```typescript
// No repository mocking pattern is established.
```

**What to Mock:**
- When testing `Login` or `Signup` behavior beyond construction, provide a mock `AuthService` and `Router` in the component TestBed so tests do not issue real requests or perform real navigation. The current specs do not yet do this.
- For `AuthService`, use Angular HTTP testing facilities and a request controller once the test runner is configured; assert URL, method, payload, and request context rather than calling the hard-coded backend directly.
- Mock browser-only APIs for isolated tests of `GlassShell` and `GlobeHero`, including `window.matchMedia`, `IntersectionObserver`, `requestAnimationFrame`, and WebGL/Three.js setup as applicable.

**What NOT to Mock:**
- Keep reactive-form validators and straightforward model transformations real in component tests; these are core behavior in `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts`.
- Do not mock the component under test or Angular's `TestBed` itself.

## Fixtures and Factories

**Test Data:**
```typescript
// No shared fixture or factory pattern is present.
```

**Location:**
- No fixtures, factories, or test data modules are detected.
- For future auth tests, keep reusable valid login/signup payload builders near the auth specs or in a clearly named test-support module under `FRONTEND/src/app/core/` only if multiple specs need them.

## Coverage

**Requirements:**
- No coverage threshold, coverage configuration, or CI test gate is detected.
- Current visible coverage is limited to construction smoke tests plus one root-template text assertion. There are no tests for `FRONTEND/src/app/core/services/auth.service.ts` behavior despite the stale service spec filename.

**View Coverage:**
```bash
npm run test -- --coverage     # Not available until a test script/runner is configured
```

## Test Types

**Unit Tests:**
- The repository contains only intended unit/component-style specs using Angular `TestBed`.
- Existing assertions verify component/service creation and, in `FRONTEND/src/app/app.spec.ts`, that an `h1` contains `Hello, frontend`.
- Add unit coverage for form validity, dynamic document validators, step navigation, payload mapping, loading/error transitions, request-context generation, and custom-control value access in `FRONTEND/src/app/features/auth/signup/signup.ts`, `FRONTEND/src/app/features/auth/login/login.ts`, `FRONTEND/src/app/core/services/auth.service.ts`, and `FRONTEND/src/app/shared/components/glass-select/glass-select.ts`.

**Integration Tests:**
- Not detected. No HTTP integration specs, router integration specs, or Supabase/backend integration tests are present.

**E2E Tests:**
- Not detected. No Playwright, Cypress, Webdriver, or Angular E2E configuration is present.

## Common Patterns

**Async Testing:**
```typescript
beforeEach(async () => {
  await TestBed.configureTestingModule({
    imports: [Login]
  }).compileComponents();

  const fixture = TestBed.createComponent(Login);
  await fixture.whenStable();
});
```

Use the existing async TestBed pattern for component compilation and stabilization. For observable tests, prefer explicit completion or Angular HTTP test-controller verification once the runner and testing providers are installed.

**Error Testing:**
```typescript
// No error-path test pattern is currently present.
```

Add assertions for user-visible fallback messages and `loading === false` in both success-with-`success: false` and observable-error paths, matching the handlers in `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts`.

## Current Test Health

- Five spec files are present under `FRONTEND/src`: `app.spec.ts`, `core/services/auth.spec.ts`, `features/auth/login/login.spec.ts`, `features/auth/signup/signup.spec.ts`, and `shared/components/glass-shell/glass-shell.spec.ts`.
- `FRONTEND/src/app/core/services/auth.spec.ts` imports `Auth` from `./auth`, but the implementation present is `FRONTEND/src/app/core/services/auth.service.ts` exporting `AuthService`; this spec is not aligned with the current source tree.
- `FRONTEND/src/app/app.spec.ts` expects `Hello, frontend`, while the current root template is `FRONTEND/src/app/app.html`; keep template assertions synchronized with the actual UI.
- Before relying on specs, add/restore a test runner script and dependencies consistent with `FRONTEND/tsconfig.spec.json`, then run all specs independently from `npm run build`.

---

*Testing analysis: 2026-09-18*
