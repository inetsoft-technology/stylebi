# Inline-SVG Chart Rendering Under the Modern Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make inline-SVG chart rendering follow the org's modern-visualization gate instead of requiring a second, undiscoverable property, and stop a plot too large for SVG from rendering as a blank tile.

**Architecture:** A new gated resolver `VSChartInteractionDefaults.isInlineSvg()` derives the client `inlineSvg` flag from `viewsheet.modernVisualization`, with `graph.svg.inline` demoted to an explicit override in both directions and read org-scoped. Separately, `ChartInlineSvgDirective` learns to recognise the server's PNG fallback from the response content type and render it as an `<img>`; the existing `afterSvgInjected()` reset then leaves the hover index empty so every interaction path is inert without new guards.

**Tech Stack:** Java 21, JUnit 5 with Spring test context, Angular 21 standalone directives, TypeScript 5.9, Vitest 4 (via `@angular/build:unit-test`).

**Source spec:** `community/docs/superpowers/specs/2026-08-05-inline-svg-modern-gate-design.md`

## Global Constraints

- Every file is under `community/` — this is a **community-repo-only** change. Open the PR in the community repo; no enterprise PR is needed.
- Indentation is **3 spaces**, matching the surrounding code. Do not reformat existing lines.
- New Java **source** files require the AGPL license header — copy it verbatim from `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartChromeDefaults.java` lines 1-17. New Java **test** files in `core/src/test/java/inetsoft/uql/viewsheet/internal/` carry **no** header (every sibling `*DefaultsTest` starts at `package`).
- Code comments are short clauses, not full sentences, and must not reference tickets, PRs, mockups, or design docs.
- With `viewsheet.modernVisualization` off and `graph.svg.inline` unset, behavior must be byte-identical to today.
- Java tests in `core` require `@Tag("core")` — surefire is configured with `<groups>core</groups>` (`core/pom.xml:996`), so an untagged test never runs.
- Run Java tests from `community/`, frontend tests from `community/web/`. On Windows use `.\mvnw.cmd` in PowerShell or `./mvnw` under the Bash tool.

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaults.java` | create | Resolves whether chart plot areas render as inline SVG. Sole owner of the `graph.svg.inline` / modern-gate relationship. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaultsTest.java` | create | Locks the resolution truth table. |
| `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java` | modify (`:304`) | Sends the resolved flag to the client. |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts` | modify (`:244-253`, new private method) | Recognises the server's raster fallback and renders it as an image. |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts` | modify (append a describe block) | Covers svg load, raster fallback, and svg→raster transition. |

Two tasks, one per side of the wire. Task 1 is server-side and shippable on its own — it changes which flag reaches the client. Task 2 is client-side and independently valuable — it fixes a live bug in inline mode whether or not Task 1 lands. Task 3 is verification only.

---

### Task 1: Gate resolver and wiring

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaults.java`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaultsTest.java`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java:304`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern()` (existing, same package — reads `viewsheet.modernVisualization` org-scoped); `SreeEnv.getProperty(String name, boolean earlyLoaded, boolean orgScope)`.
- Produces: `public static boolean VSChartInteractionDefaults.isInlineSvg()`. No other task depends on it.

**Background the implementer needs:**

`CoreLifecycleService:304` currently reads the property directly:

```java
infoMap.put("inlineSvg", "true".equals(SreeEnv.getProperty("graph.svg.inline")));
```

This is the only read of `graph.svg.inline` in the tree. The value travels to the client in `SetViewsheetInfoCommand` and decides whether the chart plot area renders as inline SVG or an `<img>`. Nothing downstream changes in this task.

The third argument to `SreeEnv.getProperty` is `orgScope`. Writing it explicitly is **behaviorally identical** to the single-argument form: `SreeEnv.getProperty(String)` (`:53-55`) already delegates to `PropertiesEngine.getProperty(name, false)`, which defaults `orgScope` to `true` (`:102-104`). The explicit form is for clarity and to match the sibling resolvers. Do not add any migration, and do not expect a behavior change from this argument.

Deliberately **not** tested: that an org-qualified key overrides the global one. That behavior belongs to `PropertiesEngine`, needs a `ThreadContext` principal plus an `OrganizationManager` org to exercise, and none of the eight sibling `*DefaultsTest` classes set that up. The resolver's own contract is the truth table below.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaultsTest.java`:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartInteractionDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("graph.svg.inline", null);
   }

   @Test
   void offByDefaultWhenLegacy() {
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void explicitTrueStillWinsWhenLegacy() {
      SreeEnv.setProperty("graph.svg.inline", "true");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void followsModernWhenUnset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void explicitFalseOptsOutOfModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "false");
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void emptyValueCountsAsUnset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void anyNonTrueValueIsOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "no");
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `community/`:

```
./mvnw test -pl core -Dtest=VSChartInteractionDefaultsTest
```

Expected: compilation failure — `cannot find symbol: class VSChartInteractionDefaults`.

- [ ] **Step 3: Write the resolver**

Create `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaults.java`. Copy the 17-line AGPL header verbatim from `VSChartChromeDefaults.java` in the same directory, then:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;

/**
 * Resolves whether chart plot areas render as inline SVG in the page DOM rather than as an
 * image. Inline SVG is what makes the chart interactions reachable — hover dimming, snap and
 * series dimming, and the card tooltip tail — so it follows the org modern-visualization gate.
 *
 * graph.svg.inline is an explicit override in both directions: set it to opt one org (or the
 * whole install) out while keeping modern chrome, or to turn inline SVG on with modern off.
 * Unlike the chrome sub-gates, which only ever switch a modern feature off, this one has to
 * support inline SVG without modern, so it is not an isModern() && != "false" expression.
 */
public final class VSChartInteractionDefaults {
   private VSChartInteractionDefaults() {
   }

   /**
    * Whether the chart plot area is delivered as inline SVG. An explicit graph.svg.inline wins;
    * unset follows the modern gate. Read org-scoped, so a per-org override resolves before the
    * global value.
    */
   public static boolean isInlineSvg() {
      String prop = SreeEnv.getProperty("graph.svg.inline", false, true);

      if(prop != null && !prop.isEmpty()) {
         return "true".equals(prop);
      }

      return VSDensityDefaults.isModern();
   }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run from `community/`:

```
./mvnw test -pl core -Dtest=VSChartInteractionDefaultsTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Wire the resolver into the viewsheet-open payload**

In `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java`, replace line 304:

```java
         infoMap.put("inlineSvg", "true".equals(SreeEnv.getProperty("graph.svg.inline")));
```

with:

```java
         infoMap.put("inlineSvg", VSChartInteractionDefaults.isInlineSvg());
```

**No import is needed** — line 45 is already `import inetsoft.uql.viewsheet.internal.*;`. Leave the import block alone, and leave the `SreeEnv` import in place (lines 299 and 303 still use it).

Do not touch the adjacent `modernVisualization`, `vizDensity` or `darkMode` entries at `:305-310`.

- [ ] **Step 6: Verify the module still compiles**

Run from `community/`:

```
./mvnw install -pl core -DskipTests -o
```

Expected: `BUILD SUCCESS`. (The `-o` offline flag keeps it fast; drop it if Maven complains about a missing artifact.)

- [ ] **Step 7: Stop — do not commit**

The repo owner commits manually. Report the three touched files and the test result.

---

### Task 2: Raster fallback in the inline-SVG directive

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts:244-253`, plus one new private method
- Modify: `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts` (append one `describe` block inside the existing top-level `describe`)

**Interfaces:**
- Consumes: nothing from Task 1. This task is independent.
- Produces: `private showRaster(body: ArrayBuffer, type: string): void` on `ChartInlineSvgDirective`. Nothing outside the directive calls it.

**Background the implementer needs:**

`AssemblyImageService.java:820-828` refuses SVG for a plot area of 10,000 rows or more and falls back to a PNG, and `:1204-1217` labels the response honestly — `image/png` for a raster, `image/svg+xml` otherwise.

The directive ignores that. `loadSvg()` requests `responseType: "text"` (`:233`) and assigns `response.body` straight to `innerHTML` (`:249`), so an oversized plot injects PNG bytes decoded as text and the tile renders blank.

The raster branch must render from the bytes of that same response rather than pointing an `<img>` at the URL again. The plot URL is outside the `/api/**` pattern `CacheInterceptor` covers, and `AssemblyImageService.processImageRenderResult` (`:1195-1235`) sets no `Cache-Control`, `Expires`, `ETag` or `Last-Modified`, so a second GET is served by the server and re-rasterizes the plot — the exact cost the 10,000-row threshold exists to avoid — and an `<img>` cannot honour the empty-body-plus-`Retry-After` contract that `loadSvg` handles for its own fetch. So the request becomes `responseType: "arraybuffer"`: decoded as UTF-8 text on the SVG path, wrapped in a `Blob` object url on the raster path.

**The fix needs no per-method guards.** `afterSvgInjected()` (`:665-705`) clears `elementGroupMap`, `anchorGroupMap`, `labelGroupMap`, the relation and treemap state and every hover flag, then sets `svgRootEl = this.element.nativeElement.querySelector("svg")` — which is `null` when the host holds an `<img>`. With that state:

- `activateKeys` (`:438-501`) finds nothing in `elementGroupMap`, and its `inetsoft-dim-all` branch requires `elementGroupMap.size > 0 && this.svgRootEl` — both false.
- `highlightSnapSeries` (`:354-356`) early-returns on `!isLineSeriesHover`.
- `setExternalSeriesDim` (`:1484`) and `setExternalRelationHighlight` (`:1422`) query the host and find no elements; both null-guard `svgRootEl`.
- `getElementAnchor` (`:328-333`) returns null on an empty `anchorGroupMap`, and `chart-plot-area.component.ts:859-871` already falls back to `regionAnchor`, so the tooltip tail still places from the region centroid.

So calling `afterSvgInjected()` on the raster path is the whole guard — and it is also what prevents the real hazard: a tile that previously held real SVG and then reloads oversized would otherwise keep a populated index and a **detached** `svgRootEl`, and `getElementAnchor` would hand the tooltip a bounding rect from a detached element. Step 1's third test covers exactly that.

`scheduleReady()` (`:633-643`) already returns early when there is no `<svg>`, so it is safe to keep calling unconditionally.

Content-type rule: treat the response as a raster only when the server explicitly says so — `type.startsWith("image/") && !type.includes("svg")`. A missing or unexpected content type keeps today's inline behavior, so a proxy that strips the header cannot break a working chart.

- [ ] **Step 1: Write the failing tests**

Append to `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts`, inside the existing top-level `describe("ChartInlineSvgDirective cross-tile dim", ...)` block (before its closing `});`):

```ts
   describe("raster fallback (server refused svg)", () => {
      const SVG_BODY = `<svg><g class="inetsoft-bar" data-row="0" data-col="0"></g></svg>`;

      /**
       * A directive wired to a stub HttpClient. respond() sets the next response and assigns a
       * fresh url, which is what triggers loadSvg.
       */
      function makeLoader(): {
         dir: ChartInlineSvgDirective,
         host: HTMLElement,
         respond: (contentType: string | null, body: string) => void
      } {
         const host = document.createElement("div");
         let next = new HttpResponse<ArrayBuffer>({
            body: new ArrayBuffer(0), headers: new HttpHeaders()
         });
         const http = { get: () => of(next) } as any;
         const dir = new ChartInlineSvgDirective(new ElementRef(host), http);
         let n = 0;

         return {
            dir, host,
            respond: (contentType: string | null, body: string) => {
               // The directive fetches bytes, so the stub responds with the encoded body.
               next = new HttpResponse<ArrayBuffer>({
                  body: new TextEncoder().encode(body).buffer as ArrayBuffer,
                  headers: contentType == null
                     ? new HttpHeaders() : new HttpHeaders({ "Content-Type": contentType })
               });
               dir.chartInlineSvg = `/plot_area/${n++}`;
            }
         };
      }

      it("inlines the svg and indexes it when the server sent svg", () => {
         const { dir, host, respond } = makeLoader();
         const loaded: number[] = [];
         dir.onLoaded.subscribe(() => loaded.push(1));

         respond("image/svg+xml", SVG_BODY);

         expect(host.querySelector("svg")).not.toBeNull();
         expect(host.querySelector("img")).toBeNull();
         expect((dir as any).anchorGroupMap.size).toBe(1);
         expect(loaded.length).toBe(1);
      });

      it("shows an image and builds no index when the server sent png", () => {
         const { dir, host, respond } = makeLoader();
         const loaded: number[] = [];
         dir.onLoaded.subscribe(() => loaded.push(1));

         respond("image/png", "\x89PNG\r\n\x1a\n binary");

         const img = host.querySelector("img");
         expect(img).not.toBeNull();
         // Built from the bytes already fetched, so no second GET re-rasterizes the plot.
         expect(img.getAttribute("src")).toMatch(/^blob:/);
         expect(img.getAttribute("alt")).toBe("");
         expect(host.querySelector("svg")).toBeNull();
         expect((dir as any).anchorGroupMap.size).toBe(0);
         expect((dir as any).svgRootEl).toBeNull();
         // The tail asks for an anchor on every hover; null sends it to the region centroid.
         expect(dir.getElementAnchor(0, 0)).toBeNull();
         expect(loaded.length).toBe(1);
      });

      it("drops the stale index when a tile reloads oversized", () => {
         const { dir, host, respond } = makeLoader();

         respond("image/svg+xml", SVG_BODY);
         expect((dir as any).anchorGroupMap.size).toBe(1);

         respond("image/png", "binary");

         expect((dir as any).anchorGroupMap.size).toBe(0);
         expect((dir as any).elementGroupMap.size).toBe(0);
         expect((dir as any).svgRootEl).toBeNull();
         expect(dir.getElementAnchor(0, 0)).toBeNull();
         expect(host.querySelector("svg")).toBeNull();
      });

      it("keeps inlining when the content type is missing", () => {
         const { host, respond } = makeLoader();

         respond(null, SVG_BODY);

         expect(host.querySelector("svg")).not.toBeNull();
         expect(host.querySelector("img")).toBeNull();
      });
   });
```

Add these two imports at the top of the spec file, after the existing `ElementRef` import:

```ts
import { HttpHeaders, HttpResponse } from "@angular/common/http";
import { of } from "rxjs";
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from `community/web/`:

```
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

Expected: the two new `png` tests fail — `host.querySelector("img")` is null because the PNG body was injected as markup — and the svg and missing-content-type tests pass. Running `npx vitest` directly instead fails with "describe is not defined" because it bypasses the builder's setup files.

- [ ] **Step 3: Implement the raster branch**

In `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts`, replace lines 244-253:

```ts
               else if(requestedUrl === this._url) {
                  // SVG content comes from our own server (same origin, server-controlled).
                  // Direct innerHTML assignment is intentional — Angular's DomSanitizer only
                  // intercepts [innerHTML] template bindings, not programmatic ElementRef access.
                  // This must NOT be used with user-supplied or externally sourced SVG content.
                  this.element.nativeElement.innerHTML = this.uniquifyIds(response.body);
                  this.afterSvgInjected();
                  this.scheduleReady();
                  this.onLoaded.emit();
               }
```

with:

```ts
               else if(requestedUrl === this._url) {
                  const type = response.headers?.get("Content-Type") || "";

                  // Either branch replaces the host's contents, so any raster held by the previous
                  // load is gone.
                  this.revokeRasterUrl();

                  // Only an explicit raster type diverges, so a stripped header keeps inlining.
                  if(type.startsWith("image/") && !type.includes("svg")) {
                     this.showRaster(response.body, type);
                  }
                  else {
                     // Batik writes UTF-8, so decoding the bytes yields the same markup a text
                     // response would have.
                     const svg = new TextDecoder("utf-8").decode(response.body);
                     // SVG content comes from our own server (same origin, server-controlled).
                     // Direct innerHTML assignment is intentional — Angular's DomSanitizer only
                     // intercepts [innerHTML] template bindings, not programmatic ElementRef access.
                     // This must NOT be used with user-supplied or externally sourced SVG content.
                     this.element.nativeElement.innerHTML = this.uniquifyIds(svg);
                  }

                  this.afterSvgInjected();
                  this.scheduleReady();
                  this.onLoaded.emit();
               }
```

Change the request itself to `responseType: "arraybuffer"` (`:233`), and revoke the object url on the cleared-url branch of `loadSvg()` and in `ngOnDestroy()` as well, mirroring `ChartImageDirective` (`:72-75`, `:110-115`).

- [ ] **Step 4: Add the showRaster method**

Insert immediately after `loadSvg()` ends (after the closing brace of the `else` block at what is currently line 269), so it sits between `loadSvg` and `highlightElement`:

```ts
   /**
    * Show the plot as an image, built from the bytes already fetched. A plot with too many data
    * points comes back as png instead of svg. afterSvgInjected then finds no <svg>, leaving the
    * hover index empty and every interaction path inert.
    */
   private showRaster(body: ArrayBuffer, type: string): void {
      this.rasterUrl = URL.createObjectURL(new Blob([body], { type }));
      const img = document.createElement("img");
      // Decorative — the plot conveys nothing a screen reader can use, and announcing the object
      // url would be noise.
      img.alt = "";
      img.src = this.rasterUrl;
      this.element.nativeElement.innerHTML = "";
      this.element.nativeElement.appendChild(img);
   }

   /** Release the object url backing a raster <img>, if one is held. */
   private revokeRasterUrl(): void {
      if(this.rasterUrl !== null) {
         URL.revokeObjectURL(this.rasterUrl);
         this.rasterUrl = null;
      }
   }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run from `community/web/`:

```
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

Expected: every test in the file passes, including the pre-existing ones — the svg path is unchanged.

- [ ] **Step 6: Confirm the portal still builds**

Run from `community/web/`:

```
npx ng build portal
```

Expected: build succeeds. Note that the `portal:test-tl` suite is pre-broken on the `viz-updates` branch by unrelated stale specs; do not treat that as a regression from this task and do not try to fix it here.

- [ ] **Step 7: Stop — do not commit**

Report the two touched files and the test output.

---

### Task 3: Runtime verification

**Files:** none — this task changes no code.

**Interfaces:**
- Consumes: Tasks 1 and 2, both complete.
- Produces: a pass/fail report for the owner.

**Background the implementer needs:**

The gate is read at viewsheet open (`CoreLifecycleService:304`) and pushed to the client in `SetViewsheetInfoCommand`, so every check below needs only a viewsheet reopen — no rebuild between property changes. But the property must be set through **EM → Settings → Properties**: `SreeEnv` resolves from the key-value store and classpath defaults, so putting `graph.svg.inline` under `additionalProperties` in `server/inetsoft.yaml` does nothing, and `-Dgraph.svg.inline=true` does nothing either (the system-property layer is only consulted on the early-loaded path).

Note that `spring-boot:run -pl server -o` resolves `core` and `web` from the local Maven repository, not from `target/`, so both modules must be `install`ed and the server restarted before these checks mean anything.

Inline mode is observable in devtools: the plot tile is a `<div class="chart-plot-area__inline-svg">` containing an `<svg>`, versus an `<img>` in legacy mode.

- [ ] **Step 1: Install and start**

Run from `community/`:

```
./mvnw install -pl core,web -DskipTests
```

Then start the server and open a viewsheet containing a bar chart.

- [ ] **Step 2: Verify the legacy default is unchanged**

With both `viewsheet.modernVisualization` and `graph.svg.inline` unset, open the chart.

Expected: the plot tile is an `<img>`. No hover dimming. This is today's behavior and must not have changed.

- [ ] **Step 3: Verify modern turns inline SVG on**

EM → Settings → Presentation → Look and Feel → tick **Modern Visualization**, save. Reopen the viewsheet.

Expected: the plot tile is a `<div class="chart-plot-area__inline-svg">` holding an `<svg>`. Hovering a bar dims the others. On a chart whose tooltip style is Card, the tooltip grows a tail pointing at the hovered bar.

- [ ] **Step 4: Verify the explicit opt-out**

EM → Settings → Properties → set `graph.svg.inline` = `false`. Reopen the viewsheet.

Expected: back to an `<img>` with no hover dimming, while modern chrome (gridline and label colors, title bar) stays modern. Then delete the property and confirm inline SVG returns.

- [ ] **Step 5: Verify the raster fallback**

Open or build a chart whose plot area has 10,000 or more rows, with Modern Visualization on.

Expected: the plot renders as a PNG inside the inline-svg div — not a blank tile. Hover dimming is absent for that chart, which is correct. Confirm the network tab shows a single `plot_area` request with `Content-Type: image/png`.

- [ ] **Step 6: Report**

Report each step's result. If step 5 cannot be exercised for lack of a large enough dataset, say so explicitly rather than reporting it as passed — Task 2's third unit test covers the state-reset half of that path, but not the end-to-end render.

---

## Notes for the reviewer

Two details changed while writing this plan, both because reading the directive's internals showed the spec's first approach was unnecessary. The spec has been updated to match, so the two documents agree; the reasoning is recorded here:

1. **No `pngFallback` flag and no per-method guards.** The spec called for guarding six interaction methods. `afterSvgInjected()` already clears every index and nulls `svgRootEl`, which makes all six inert, and calling it on the raster path is also what prevents a stale index surviving an svg→raster reload. Six guards would be dead code.
2. **No org-override unit test.** The spec's testing section asked for one. It would exercise `PropertiesEngine`, not this resolver, and needs `ThreadContext` and `OrganizationManager` setup that no sibling `*DefaultsTest` performs. The truth table covers the resolver's own contract.
