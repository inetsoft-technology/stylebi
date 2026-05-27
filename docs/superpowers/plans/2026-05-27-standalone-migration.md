# Angular Standalone Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all Angular components, directives, and pipes in `community/web` from NgModule-based architecture to standalone, updating lazy-loaded routing to use route arrays, in preparation for upgrading to Angular 21.

**Architecture:** Three sequential phases (shared → em → portal), each producing two PRs: PR A runs `convert-to-standalone`, PR B runs `prune-ng-modules` and (for applications) `standalone-bootstrap`. The Angular CLI schematic automates the mechanical conversion; manual fixes are applied where the schematic cannot handle the pattern. Angular Elements entry points (`main-elements.ts`, `main-viewer-element.ts`) require a fully manual rewrite in the final PR.

**Tech Stack:** Angular 17.3, `@angular/core:standalone` schematic via Angular CLI, TypeScript 5.2, Jest 28, Node.js 18 (via nvm)

**Spec:** `docs/superpowers/specs/2026-05-27-standalone-migration-design.md`

---

## File Map

### Phase 1 — shared

| Action | Path |
|--------|------|
| Modify (standalone) | All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` under `projects/shared/` |
| Modify (tests) | All `*.spec.ts` under `projects/shared/` |
| Delete | `projects/shared/ai-assistant/ai-assistant.module.ts` |
| Delete | `projects/shared/ckeditor-wrapper/ckeditor-wrapper.module.ts` |
| Delete | `projects/shared/download/download.module.ts` |
| Delete | `projects/shared/resize-event/angular-resize-event.module.ts` |

### Phase 2 — em

| Action | Path |
|--------|------|
| Modify (standalone) | All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` under `projects/em/` |
| Modify (tests) | All `*.spec.ts` under `projects/em/` |
| Delete | All `*.module.ts` under `projects/em/` |
| Delete | All `*-routing.module.ts` under `projects/em/` |
| Create (routes) | `*.routes.ts` replacing each `*-routing.module.ts` (schematic generates) |
| Create | `projects/em/src/app/app.config.ts` (schematic generates) |
| Modify | `projects/em/src/main.ts` |

### Phase 3 — portal

| Action | Path |
|--------|------|
| Modify (standalone) | All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` under `projects/portal/` |
| Modify (tests) | All `*.spec.ts` under `projects/portal/` |
| Delete | All `*.module.ts` under `projects/portal/` |
| Delete | All `*-routing.module.ts` under `projects/portal/` |
| Create (routes) | `*.routes.ts` replacing each `*-routing.module.ts` (schematic generates) |
| Create | `projects/portal/src/app/app.config.ts` (schematic generates — portal app) |
| Create | `projects/portal/src/app/embed/embed-element.config.ts` (manual — Angular Elements) |
| Modify | `projects/portal/src/main.ts` |
| Rewrite | `projects/portal/src/main-elements.ts` |
| Rewrite | `projects/portal/src/main-viewer-element.ts` |
| Delete | `projects/portal/src/app/embed/app-base-element.module.ts` |
| Delete | `projects/portal/src/app/embed/app-elements.module.ts` |
| Delete | `projects/portal/src/app/embed/app-viewer-element.module.ts` |

---

## Task 1: Verify baseline before any migration

**Files:** None changed

- [ ] **Step 1: Navigate to the web directory and activate Node 18**

  ```bash
  cd community/web
  nvm use v18
  ```

  Expected output: `Now using node v18.x.x (npm v...)`

- [ ] **Step 2: Verify the full build passes**

  ```bash
  npm run build
  ```

  Expected: Build completes with no TypeScript errors. Warnings are acceptable. If the build fails before any migration work, fix the underlying issue before continuing.

- [ ] **Step 3: Verify portal tests pass**

  ```bash
  npm run test
  ```

  Expected: All tests pass.

- [ ] **Step 4: Verify em tests pass**

  ```bash
  npm run test:em
  ```

  Expected: All tests pass.

---

## Task 2: PR 1A — Convert shared library to standalone

**Branch:** `feature-<issue>-shared-convert` from `main`

**Files modified:** All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` and `*.spec.ts` under `projects/shared/`

- [ ] **Step 1: Create branch**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-shared-convert
  ```

- [ ] **Step 2: Run the convert-to-standalone schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/shared
  ```

  Expected: The schematic modifies multiple files, adding `standalone: true` to components/directives/pipes and updating `TestBed.configureTestingModule` calls in spec files to move declarations into `imports`.

- [ ] **Step 3: Check for build errors**

  ```bash
  npm run build 2>&1 | grep "error TS"
  ```

  Common errors and fixes:
  - `NG0961: Component 'X' is standalone, and cannot be declared in an NgModule` — the schematic missed a test file. Open it and move `X` from `declarations` to `imports` in the `TestBed.configureTestingModule` call.
  - `Cannot find module '...'` — check the import path was not accidentally rewritten. Restore the original path.

  Run `npm run build` until it exits with no `error TS` lines.

- [ ] **Step 4: Run portal tests**

  ```bash
  npm run test
  ```

  Expected: All tests pass. Standalone component test failures show as `NullInjectorError` (missing provider) or `Can't bind to 'X'` (missing import in component `imports` array). Fix by adding the missing module or provider to the component decorator's `imports` array.

- [ ] **Step 5: Run em tests**

  ```bash
  npm run test:em
  ```

  Expected: All tests pass.

- [ ] **Step 6: Commit**

  ```bash
  git add -A
  git commit -m "feat: convert shared library components to standalone"
  ```

- [ ] **Step 7: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-shared-convert
  gh pr create --title "feat: convert shared library to standalone components" \
    --body "Runs the Angular convert-to-standalone schematic on projects/shared. All 4 shared library modules' declarations are now standalone. Tests updated. Part of the Angular 21 upgrade preparation."
  ```

---

## Task 3: PR 1B — Prune shared NgModules

**Branch:** `feature-<issue>-shared-prune` from `main` after PR 1A merges

**Files deleted:** `ai-assistant.module.ts`, `ckeditor-wrapper.module.ts`, `download.module.ts`, `angular-resize-event.module.ts`

- [ ] **Step 1: Create branch from updated main**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-shared-prune
  ```

- [ ] **Step 2: Run the prune-ng-modules schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/shared
  ```

  Expected: The four shared NgModule files are deleted. References to them in other files within `projects/shared/` are updated.

- [ ] **Step 3: Check for cross-project reference errors**

  The `--path=projects/shared` scope may not update imports in `em` or `portal` that reference the now-deleted modules. Check:

  ```bash
  npm run build 2>&1 | grep "error TS"
  ```

  If `em` or `portal` report errors like `Module has no exported member 'AiAssistantModule'`, find each affected file:

  ```bash
  grep -rl "AiAssistantModule\|CkeditorWrapperModule\|DownloadModule\|AngularResizeEventModule" \
    projects/em/src projects/portal/src --include="*.ts"
  ```

  For each affected file, replace the deleted module import with direct imports of the standalone components it previously exported. For example, if `em/app.module.ts` imported `AiAssistantModule`, open `ai-assistant.module.ts` in git history (`git show HEAD~1:projects/shared/ai-assistant/ai-assistant.module.ts`) to see what it declared, then import those components directly.

- [ ] **Step 4: Verify build passes**

  ```bash
  npm run build
  ```

  Expected: Zero TypeScript errors.

- [ ] **Step 5: Run portal tests**

  ```bash
  npm run test
  ```

- [ ] **Step 6: Run em tests**

  ```bash
  npm run test:em
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add -A
  git commit -m "feat: prune shared library NgModules"
  ```

- [ ] **Step 8: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-shared-prune
  gh pr create --title "feat: prune shared library NgModules" \
    --body "Removes the 4 shared library NgModules now that all declarations are standalone. Updates cross-project references in em/portal as needed. Part of the Angular 21 upgrade preparation."
  ```

---

## Task 4: PR 2A — Convert em to standalone

**Branch:** `feature-<issue>-em-convert` from `main` after PR 1B merges

**Files modified:** All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` and `*.spec.ts` under `projects/em/`

- [ ] **Step 1: Create branch from updated main**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-em-convert
  ```

- [ ] **Step 2: Run the convert-to-standalone schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/em
  ```

- [ ] **Step 3: Check for build errors**

  ```bash
  npm run build 2>&1 | grep "error TS"
  ```

  Fix these categories:

  - `NG0961: Component 'X' is standalone, and cannot be declared in an NgModule` — open the spec file and move `X` from `declarations` to `imports` in `TestBed.configureTestingModule`.

  - `Cannot find name 'NgIf'` / `Cannot find name 'NgFor'` / `Cannot find name 'NgSwitch'` — the component used these directives implicitly via `BrowserModule`. Add `CommonModule` to the component's `imports` array:
    ```typescript
    @Component({
      standalone: true,
      imports: [CommonModule, /* existing imports */],
      ...
    })
    ```

  - `Can't bind to 'formGroup'` — add `ReactiveFormsModule` to the component's `imports` array.
  - `Can't bind to 'ngModel'` — add `FormsModule` to the component's `imports` array.

  Run `npm run build` repeatedly until zero `error TS` lines. Pipe to a file to track progress:

  ```bash
  npm run build 2>&1 | grep "error TS" > /tmp/em-errors.txt
  wc -l /tmp/em-errors.txt
  ```

- [ ] **Step 4: Fix material-testing.module.ts if needed**

  Open `projects/em/src/app/testing/material-testing.module.ts`. If it still has now-standalone components in `declarations`, move them to `imports`:

  ```typescript
  // before (if schematic missed it)
  @NgModule({ declarations: [SomeStandaloneComponent] })

  // after
  @NgModule({ imports: [SomeStandaloneComponent] })
  ```

- [ ] **Step 5: Run em tests**

  ```bash
  npm run test:em
  ```

  Fix any failures: standalone component test failures show as `NullInjectorError` or template binding errors. Add the missing module or provider to the component's `imports` array.

- [ ] **Step 6: Run portal tests (regression check)**

  ```bash
  npm run test
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add -A
  git commit -m "feat: convert em application components to standalone"
  ```

- [ ] **Step 8: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-em-convert
  gh pr create --title "feat: convert em application to standalone components" \
    --body "Runs the Angular convert-to-standalone schematic on projects/em. All 72 feature module declarations are now standalone. Tests updated. Part of the Angular 21 upgrade preparation."
  ```

---

## Task 5: PR 2B — Prune em NgModules and migrate bootstrap

**Branch:** `feature-<issue>-em-prune` from `main` after PR 2A merges

**Files created:** `projects/em/src/app/app.config.ts`
**Files modified:** `projects/em/src/main.ts`
**Files deleted:** All `*.module.ts` and `*-routing.module.ts` under `projects/em/`
**Files created (by schematic):** `*.routes.ts` files replacing each `*-routing.module.ts`

- [ ] **Step 1: Create branch from updated main**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-em-prune
  ```

- [ ] **Step 2: Run prune-ng-modules schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/em
  ```

  Expected: All 72 NgModule files deleted, routing modules replaced with `*.routes.ts` files, `loadChildren` references updated to point to the new route arrays.

- [ ] **Step 3: Run standalone-bootstrap schematic**

  ```bash
  npx ng generate @angular/core:standalone --mode=standalone-bootstrap --path=projects/em
  ```

  Expected: Creates `projects/em/src/app/app.config.ts`, updates `projects/em/src/main.ts` to call `bootstrapApplication`, deletes `AppModule`.

- [ ] **Step 4: Verify app.config.ts has all required providers**

  Open `projects/em/src/app/app.config.ts`. Confirm it contains everything from the old `AppModule`. Cross-reference with git:

  ```bash
  git show HEAD:projects/em/src/app/app.module.ts
  ```

  Required providers:
  - `provideRouter(routes)` — check it includes the `onSameUrlNavigation: 'reload'` and `anchorScrolling: 'enabled'` options. If the schematic dropped them, update to:
    ```typescript
    provideRouter(
      routes,
      withRouterConfig({ onSameUrlNavigation: 'reload' }),
      withInMemoryScrolling({ anchorScrolling: 'enabled' })
    )
    ```
  - `provideHttpClient(withInterceptorsFromDi())` — replaces `HttpClientModule` and preserves all `HTTP_INTERCEPTORS` providers
  - `provideAnimations()` — replaces `BrowserAnimationsModule`
  - `{ provide: RouteReuseStrategy, useClass: CustomRouteReuseStrategy }`
  - `SsoHeartbeatService`, `ScheduleUsersService`, `ScheduleTaskNamesService`
  - All six HTTP interceptor providers (`CsrfInterceptor`, `HttpParamsCodecInterceptor`, `RequestedWithInterceptor`, `InvalidSessionInterceptor`, `EmClientInterceptor`, `SsoHeartbeatInterceptor`)

  Add any that are missing. Required imports at the top of `app.config.ts`:
  ```typescript
  import { ApplicationConfig } from '@angular/core';
  import { provideRouter, withRouterConfig, withInMemoryScrolling } from '@angular/router';
  import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
  import { provideAnimations } from '@angular/platform-browser/animations';
  import { HTTP_INTERCEPTORS } from '@angular/common/http';
  import { RouteReuseStrategy } from '@angular/router';
  ```

- [ ] **Step 5: Verify build passes**

  ```bash
  npm run build 2>&1 | grep "error TS"
  ```

  Common post-prune errors:

  - `Unknown element 'mat-button'` / `Unknown element 'mat-icon'` etc. — the component depended on Material modules from a now-deleted NgModule. Add the specific module to the component's `imports` array (e.g., `MatButtonModule`, `MatIconModule`).

  - Missing `NgbModalModule` — components that open modals need it explicitly. Find them:
    ```bash
    grep -rl "NgbModal\b" projects/em/src --include="*.component.ts"
    ```
    For each file found, add `NgbModalModule` from `@ng-bootstrap/ng-bootstrap` to the component's `imports` array.

  Repeat `npm run build` until zero errors.

- [ ] **Step 6: Run em tests**

  ```bash
  npm run test:em
  ```

- [ ] **Step 7: Run portal tests (regression check)**

  ```bash
  npm run test
  ```

- [ ] **Step 8: Commit**

  ```bash
  git add -A
  git commit -m "feat: prune em NgModules and migrate to standalone bootstrap"
  ```

- [ ] **Step 9: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-em-prune
  gh pr create --title "feat: prune em NgModules and migrate to standalone bootstrap" \
    --body "Removes all em NgModules, converts routing modules to route arrays, and migrates the em app to bootstrapApplication. Part of the Angular 21 upgrade preparation."
  ```

---

## Task 6: PR 3A — Convert portal to standalone

**Branch:** `feature-<issue>-portal-convert` from `main` after PR 2B merges

**Files modified:** All `*.component.ts`, `*.directive.ts`, `*.pipe.ts` and `*.spec.ts` under `projects/portal/`

- [ ] **Step 1: Create branch from updated main**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-portal-convert
  ```

- [ ] **Step 2: Run the convert-to-standalone schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=convert-to-standalone --path=projects/portal
  ```

  This is the largest schematic run (~700 components). It may take several minutes. Expected: the schematic modifies a large number of files.

- [ ] **Step 3: Check for build errors systematically**

  ```bash
  npm run build 2>&1 | grep "error TS" > /tmp/portal-convert-errors.txt
  echo "Total errors: $(wc -l < /tmp/portal-convert-errors.txt)"
  ```

  Work through error categories one at a time. Fix each category fully before moving to the next:

  **Category 1 — Standalone components still in declarations (test files):**
  ```bash
  grep "cannot be declared" /tmp/portal-convert-errors.txt
  ```
  For each: open the spec file, move the component from `declarations` to `imports`.

  **Category 2 — Missing CommonModule directives:**
  ```bash
  grep "Cannot find name 'NgIf'\|Cannot find name 'NgFor'\|Cannot find name 'NgClass'" /tmp/portal-convert-errors.txt
  ```
  For each affected component file: add `CommonModule` to the `imports` array in the `@Component` decorator.

  **Category 3 — Missing form modules:**
  ```bash
  grep "Can't bind to 'formGroup'\|Can't bind to 'ngModel'" /tmp/portal-convert-errors.txt
  ```
  Add `ReactiveFormsModule` or `FormsModule` to the affected component's `imports` array.

  After fixing all categories: run `npm run build` and regenerate the error file until it is empty.

- [ ] **Step 4: Run portal tests**

  ```bash
  npm run test
  ```

  Fix any failures before continuing.

- [ ] **Step 5: Run em tests (regression check)**

  ```bash
  npm run test:em
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add -A
  git commit -m "feat: convert portal application components to standalone"
  ```

- [ ] **Step 7: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-portal-convert
  gh pr create --title "feat: convert portal application to standalone components" \
    --body "Runs the Angular convert-to-standalone schematic on projects/portal. All 101 feature module declarations (~700 components) are now standalone. Tests updated. Part of the Angular 21 upgrade preparation."
  ```

---

## Task 7: PR 3B — Prune portal NgModules, migrate bootstrap, and migrate Angular Elements

**Branch:** `feature-<issue>-portal-prune` from `main` after PR 3A merges

**Files created:** `projects/portal/src/app/app.config.ts`, `projects/portal/src/app/embed/embed-element.config.ts`
**Files rewritten:** `projects/portal/src/main.ts`, `projects/portal/src/main-elements.ts`, `projects/portal/src/main-viewer-element.ts`
**Files deleted:** All `*.module.ts` and `*-routing.module.ts` under `projects/portal/`, including `app-base-element.module.ts`, `app-elements.module.ts`, `app-viewer-element.module.ts`

- [ ] **Step 1: Create branch from updated main**

  ```bash
  git switch main && git pull
  git switch -c feature-<issue>-portal-prune
  ```

- [ ] **Step 2: Run prune-ng-modules schematic**

  ```bash
  cd community/web
  nvm use v18
  npx ng generate @angular/core:standalone --mode=prune-ng-modules --path=projects/portal
  ```

- [ ] **Step 3: Run standalone-bootstrap schematic**

  ```bash
  npx ng generate @angular/core:standalone --mode=standalone-bootstrap --path=projects/portal
  ```

  Expected: Creates `projects/portal/src/app/app.config.ts`, updates `projects/portal/src/main.ts`, deletes `AppModule`.

- [ ] **Step 4: Verify portal app.config.ts has all required providers**

  ```bash
  git show HEAD:projects/portal/src/app/app.module.ts
  ```

  `projects/portal/src/app/app.config.ts` must contain:
  - `provideRouter(routes, withRouterConfig({...}), withInMemoryScrolling({...}))` — preserve any options from `AppRoutingModule.forRoot(routes, { onSameUrlNavigation: ... })`
  - `provideHttpClient(withInterceptorsFromDi())`
  - `provideAnimations()`
  - `{ provide: UrlSerializer, useClass: StandardUrlSerializer }`
  - `SsoHeartbeatService`, `ViewDataService`, `DateLevelExamplesService`, `LicenseInfoService`, `FirstDayOfWeekService`, `CollapseRepositoryTreeService`, `PageTabService`
  - `DragService`, `FontService`, `ModelService`, `DebounceService`, `DomService`
  - All six HTTP interceptors (`HttpDebounceInterceptor`, `HttpParamsCodecInterceptor`, `CsrfInterceptor`, `RequestedWithInterceptor`, `InvalidSessionInterceptor`, `SsoHeartbeatInterceptor`)

- [ ] **Step 5: Create embed-element.config.ts**

  The Angular Elements builds (`main-elements.ts`, `main-viewer-element.ts`) use a different provider set than the main portal app — they come from the deleted `AppBaseElementModule`. Create a new file for these providers:

  Create `projects/portal/src/app/embed/embed-element.config.ts`:

  ```typescript
  import { APP_BASE_HREF } from "@angular/common";
  import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from "@angular/common/http";
  import { ApplicationConfig } from "@angular/core";
  import { SsoHeartbeatInterceptor } from "../../../../shared/sso/sso-heartbeat-interceptor";
  import { SsoHeartbeatService } from "../../../../shared/sso/sso-heartbeat.service";
  import { BaseUrlInterceptor } from "../common/services/base-url-interceptor";
  import { CsrfInterceptor } from "../common/services/csrf-interceptor";
  import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
  import { HttpDebounceInterceptor } from "../common/services/http-debounce-interceptor";
  import { HttpParamsCodecInterceptor } from "../common/services/http-params-codec-interceptor";
  import { LicenseInfoService } from "../common/services/license-info.service";
  import { RequestedWithInterceptor } from "../common/services/requested-with-interceptor";
  import { ViewDataService } from "../viewer/services/view-data.service";
  import { DebounceService } from "../widget/services/debounce.service";
  import { DomService } from "../widget/dom-service/dom.service";
  import { DragService } from "../widget/services/drag.service";
  import { FontService } from "../widget/services/font.service";
  import { ModelService } from "../widget/services/model.service";

  function getAppBaseHref(): string {
    return document.getElementsByTagName("inetsoft-base")
      ?.item(0)?.attributes?.getNamedItem("href")?.value;
  }

  export const embedElementConfig: ApplicationConfig = {
    providers: [
      provideHttpClient(withInterceptorsFromDi()),
      SsoHeartbeatService,
      ViewDataService,
      LicenseInfoService,
      FirstDayOfWeekService,
      DragService,
      FontService,
      ModelService,
      DebounceService,
      DomService,
      { provide: APP_BASE_HREF, useFactory: getAppBaseHref },
      { provide: HTTP_INTERCEPTORS, useClass: HttpDebounceInterceptor, multi: true },
      { provide: HTTP_INTERCEPTORS, useClass: HttpParamsCodecInterceptor, multi: true },
      { provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true },
      { provide: HTTP_INTERCEPTORS, useClass: RequestedWithInterceptor, multi: true },
      { provide: HTTP_INTERCEPTORS, useClass: SsoHeartbeatInterceptor, multi: true },
      { provide: HTTP_INTERCEPTORS, useClass: BaseUrlInterceptor, multi: true },
    ]
  };
  ```

  Cross-reference `projects/portal/src/app/embed/app-base-element.module.ts` in git history to confirm all providers are captured:
  ```bash
  git show HEAD:projects/portal/src/app/embed/app-base-element.module.ts
  ```

- [ ] **Step 6: Rewrite main-elements.ts**

  Replace the entire content of `projects/portal/src/main-elements.ts`:

  ```typescript
  import { createApplication } from '@angular/core';
  import { createCustomElement } from '@angular/elements';
  import { EmbedChartComponent } from './app/embed/chart/embed-chart.component';
  import { embedElementConfig } from './app/embed/embed-element.config';
  import './main-base-element';

  createApplication(embedElementConfig).then(appRef => {
    const embedChart = createCustomElement(EmbedChartComponent, { injector: appRef.injector });
    customElements.define('inetsoft-chart', embedChart);
  });

  (window as any).checkInetsoftConnection(null, false);
  ```

- [ ] **Step 7: Rewrite main-viewer-element.ts**

  Replace the entire content of `projects/portal/src/main-viewer-element.ts`:

  ```typescript
  import { createApplication } from '@angular/core';
  import { createCustomElement } from '@angular/elements';
  import { EmbedViewerComponent } from './app/embed/viewer/embed-viewer.component';
  import { embedElementConfig } from './app/embed/embed-element.config';
  import './main-base-element';

  (window as any).globalPostParams = null;

  createApplication(embedElementConfig).then(appRef => {
    const embedViewer = createCustomElement(EmbedViewerComponent, { injector: appRef.injector });
    customElements.define('inetsoft-viewer', embedViewer);
  });

  (window as any).checkInetsoftConnection(null, false);
  ```

- [ ] **Step 8: Delete the three embed bootstrap modules**

  If the schematic did not already delete them:
  ```bash
  rm -f projects/portal/src/app/embed/app-base-element.module.ts
  rm -f projects/portal/src/app/embed/app-elements.module.ts
  rm -f projects/portal/src/app/embed/app-viewer-element.module.ts
  ```

  Remove any remaining imports of these modules:
  ```bash
  grep -rl "AppBaseElementModule\|AppElementsModule\|AppViewerElementModule" \
    projects/portal/src --include="*.ts"
  ```

  Remove the import lines from each file found.

- [ ] **Step 9: Verify build passes for all targets**

  ```bash
  npm run build 2>&1 | grep "error TS" > /tmp/portal-prune-errors.txt
  echo "Total errors: $(wc -l < /tmp/portal-prune-errors.txt)"
  ```

  Fix post-prune errors using the same categories as Task 5 Step 5. Portal has the most components so expect more occurrences:

  **Missing NgbModalModule** — find affected components:
  ```bash
  grep -rl "NgbModal\b" projects/portal/src --include="*.component.ts"
  ```
  Add `NgbModalModule` from `@ng-bootstrap/ng-bootstrap` to each found component's `imports` array.

  **Missing Material modules** — for each `Unknown element 'mat-X'` error, add the matching `MatXModule` to the component's `imports` array.

  **Missing CommonModule** — add `CommonModule` (or specific directive like `NgIf`, `NgFor` from `@angular/common`) to the component's `imports` array.

  Repeat `npm run build` until zero errors.

- [ ] **Step 10: Verify Angular Elements builds specifically**

  The `elements` and `viewer-element` build targets must also pass. Look for their output in the build log:

  ```bash
  npm run build 2>&1 | grep -E "elements|viewer-element|error"
  ```

  If `createApplication` is reported as not found, verify the import:
  ```bash
  node -e "const c = require('@angular/core'); console.log(typeof c.createApplication)"
  ```
  Expected: `function`

- [ ] **Step 11: Run portal tests**

  ```bash
  npm run test
  ```

- [ ] **Step 12: Run em tests (regression check)**

  ```bash
  npm run test:em
  ```

- [ ] **Step 13: Commit**

  ```bash
  git add -A
  git commit -m "feat: prune portal NgModules, migrate bootstrap, and migrate Angular Elements"
  ```

- [ ] **Step 14: Push and open PR**

  ```bash
  git push -u origin feature-<issue>-portal-prune
  gh pr create --title "feat: prune portal NgModules, migrate bootstrap, and migrate Angular Elements" \
    --body "Removes all portal NgModules, converts routing modules to route arrays, migrates the portal app to bootstrapApplication, and manually rewrites the Angular Elements entry points (main-elements.ts, main-viewer-element.ts) to use createApplication. This is the final PR in the standalone migration. Part of the Angular 21 upgrade preparation."
  ```
