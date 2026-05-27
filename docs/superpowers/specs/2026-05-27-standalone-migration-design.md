# Angular Standalone Migration Design

**Date:** 2026-05-27
**Scope:** `community/web` — portal, em, shared

## Background

The Angular applications in `community/web` currently use NgModule-based architecture. Angular 21 (the target upgrade version) removes support for `loadChildren` with NgModules. This migration converts all components, directives, and pipes to standalone and rewrites lazy-loaded routing to use route arrays, unblocking the eventual Angular version upgrade. The Angular version itself is not upgraded as part of this effort — the codebase remains on Angular 17.3 throughout.

## Scope

| Project | Type | Feature modules | Routing modules | Components |
|---------|------|-----------------|-----------------|------------|
| `shared` | Library | 4 | 0 | ~few dozen |
| `em` | Application | 72 | ~8 | ~300 |
| `portal` | Application | 101 | ~10 | ~700 |

Zero components are currently standalone. All lazy loading uses the old `loadChildren: () => import('./foo.module').then(m => m.FooModule)` pattern.

## Approach

Use the official Angular migration schematic (`ng generate @angular/core:standalone`) to automate the conversion. Manual fixes are applied after each schematic run to resolve errors the schematic cannot handle automatically.

### Schematic passes

- **Pass 1 — `convert-to-standalone`**: Adds `standalone: true` to every component, directive, and pipe. Moves NgModule `imports` inline onto each component. Updates `TestBed.configureTestingModule` in test files (declarations → imports).
- **Pass 2 — `prune-ng-modules`**: Deletes NgModules that no longer serve a purpose. Converts routing modules (`.routing.module.ts`) to plain route array files (`.routes.ts`). Updates all `loadChildren` references.
- **Pass 3 — `standalone-bootstrap`**: Replaces `platformBrowserDynamic().bootstrapModule(AppModule)` with `bootstrapApplication(AppComponent, appConfig)`. Generates `app.config.ts` with all application providers.

`shared` skips Pass 3 (it is a library, not an application).

## Phase structure

The migration is split into three sequential phases. Each phase produces two PRs. All PRs must have a passing build including tests before merging.

### Phase 1 — `shared`

**PR 1A — Component conversion**
```bash
ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/shared
```
Converts all components/directives/pipes in `ai-assistant`, `ckeditor-wrapper`, `download`, and `resize-event` to standalone. Updates tests.

**PR 1B — Module pruning**
```bash
ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/shared
```
Deletes the four library NgModules. Library exports shift from exporting NgModules to exporting standalone components/directives directly.

**Cross-project reference caveat:** `em` and `portal` import the shared NgModules (e.g., `AiAssistantModule`). The schematic scoped to `--path=projects/shared` may not update those references automatically. After running the schematic, run `npm run build` to check — if `em` or `portal` report missing module errors, manually update their imports to reference the standalone components directly. Those fixes are included in PR 1B, not deferred to the em/portal phases.

### Phase 2 — `em`

**PR 2A — Component conversion**
```bash
ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/em
```
Converts all 72 feature modules' declarations to standalone. `em` imports services from `portal` (e.g., `EmClientInterceptor`) — these are services, not components, and require no changes.

**PR 2B — Module pruning + bootstrap**
```bash
ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/em
ng generate @angular/core:standalone --mode=standalone-bootstrap --path=projects/em
```
Deletes all prunable NgModules and routing modules. Generates `em/src/app/app.config.ts`. Updates `em/src/main.ts` to `bootstrapApplication(AppComponent, appConfig)`. Deletes `AppModule`.

### Phase 3 — `portal`

**PR 3A — Component conversion**
```bash
ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/portal
```
Converts all 101 feature modules' declarations to standalone, including the embed, viewer, composer, vsobjects, and widget modules.

**PR 3B — Module pruning + bootstrap + Angular Elements manual migration**
```bash
ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/portal
ng generate @angular/core:standalone --mode=standalone-bootstrap --path=projects/portal
```
Followed by manual migration of Angular Elements entry points (see below).

## Routing migration

Routing modules are replaced by plain route array files during `prune-ng-modules`.

**Before (`monitoring-routing.module.ts`):**
```typescript
@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class MonitoringRoutingModule {}
```

**After (`monitoring.routes.ts`):**
```typescript
export const monitoringRoutes: Routes = [...];
```

**`loadChildren` references update:**
```typescript
// before
loadChildren: () => import("./monitoring/monitoring.module").then(m => m.MonitoringModule)

// after
loadChildren: () => import("./monitoring/monitoring.routes").then(m => m.monitoringRoutes)
```

**App-level router configuration** moves into `app.config.ts` via `provideRouter()`:
```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(
      routes,
      withRouterConfig({ onSameUrlNavigation: 'reload' }),
      withInMemoryScrolling({ anchorScrolling: 'enabled' })
    ),
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimations(),
    // remaining providers from AppModule
  ]
};
```

`HttpClientModule` and `BrowserAnimationsModule` are replaced by `provideHttpClient()` and `provideAnimations()`. Existing `HTTP_INTERCEPTORS` multi-providers continue to work unchanged via `withInterceptorsFromDi()` — interceptors are not rewritten as functional interceptors in this effort.

## Angular Elements manual migration (portal PR 3B)

`main-elements.ts` and `main-viewer-element.ts` use `DoBootstrap` modules and call `createCustomElement()` in NgModule constructors. The schematic cannot handle this pattern. After the schematic runs, these entry points must be manually rewritten:

**`main-elements.ts` after migration:**
```typescript
import { createApplication } from '@angular/core';
import { createCustomElement } from '@angular/elements';
import { EmbedChartComponent } from './app/embed/chart/embed-chart.component';
import { appConfig } from './app/app.config';
import './main-base-element';

createApplication(appConfig).then(appRef => {
  const embedChart = createCustomElement(EmbedChartComponent, { injector: appRef.injector });
  customElements.define('inetsoft-chart', embedChart);
});

(window as any).checkInetsoftConnection(null, false);
```

**`main-viewer-element.ts` after migration:**
```typescript
import { createApplication } from '@angular/core';
import { createCustomElement } from '@angular/elements';
import { EmbedViewerComponent } from './app/embed/viewer/embed-viewer.component';
import { appConfig } from './app/app.config';
import './main-base-element';

(window as any).globalPostParams = null;

createApplication(appConfig).then(appRef => {
  const embedViewer = createCustomElement(EmbedViewerComponent, { injector: appRef.injector });
  customElements.define('inetsoft-viewer', embedViewer);
});

(window as any).checkInetsoftConnection(null, false);
```

`AppElementsModule`, `AppViewerElementModule`, and `AppBaseElementModule` are deleted. Their providers move into `app.config.ts`.

## Known manual intervention points

After each schematic run, fix these categories of errors before opening a PR:

1. **`NgbModalModule` on individual components** — Currently imported once in `AppModule` and shared globally. After migration, components that open modals need `NgbModalModule` (or the specific ng-bootstrap modules they use) added to their own `imports` array.

2. **`CommonModule` / `BrowserModule` directives** — Components that implicitly relied on `NgIf`, `NgFor`, `NgSwitch`, etc. being globally available may fail with `Unknown element` or `Can't bind to` errors. Add `CommonModule` (or the specific directive imports) to those components.

3. **Test helper modules** — `em`'s `material-testing.module.ts` and similar test utilities may not be processed correctly by the schematic and may need manual updates.

4. **Angular Elements entry points** — Covered above; portal PR 3B only.

## Success criteria per PR

- `npm run build` exits with no TypeScript errors
- `npm run test` passes with no failures
- No regressions in any other build target (`elements`, `viewer-element` builds for portal PR 3B)
