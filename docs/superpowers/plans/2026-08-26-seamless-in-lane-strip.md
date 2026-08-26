# Seamless In-Lane Strip and Derived Glyph Tone (§10.2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the anchored in-lane mini-toolbar draw no surface of its own, and colour its glyphs with whichever of two inks measures higher contrast against the colour they actually sit on.

**Architecture:** One new pure module resolves a background (title lane, then card, then canvas, then a light/dark default) and picks an ink by measured WCAG contrast rather than a luminance threshold, raising the ink's alpha only where the 3:1 floor demands it. The container binds the result onto `<mini-toolbar>` as `data-tone` plus one custom property. The SCSS then overrides the strip's fill, border and radius away under the anchored scope, colours the glyph from the tone, and deletes five rules that become redundant or actively harmful.

**Tech Stack:** Angular 21 / TypeScript 5.9, SCSS, Vitest, `@ctrl/tinycolor` (already a direct dependency).

**Spec:** `docs/superpowers/specs/lookfeel/chart-card-seamless-strip-design.md` — read it before starting. Its **§1 is the most important section**: four claims in the source design doc are wrong about this code, and following them instead of the spec produces either dead work (an inline-SVG conversion nobody needs) or a shipped accessibility defect (a threshold that picks the lower-contrast ink). §3.2's carve-out and §3.4's "one deletion is a translation" are the two places this is easy to get subtly wrong.

## Global Constraints

- **Branch:** `viz-updates`.
- **One commit per task.**
- **This change is browser-only.** Everything lives under `web/projects/portal/src`. No Java, no server, no export pass, no persisted state, no seed mark, no migration. If you find yourself opening a `.java` file, stop and report.
- **The contrast floor is 3:1** — WCAG 1.4.11, for a meaningful non-text control. It is the design's guarantee, and Task 1 asserts it as a property rather than trusting it.
- **Base resting alphas are 0.55 (light tone) and 0.80 (dark tone)**, from `Chart Card Spec v3.dc.html` §10.2's table. These are the *resting opacity*; **no further opacity multiplier may be applied on top of them.** That is why `mini-toolbar.component.scss:180` is deleted in Task 4.
- **There is no luminance threshold.** Do not add one, and do not reinstate §10.2's `L > 0.45`. It selects the lower-contrast ink for every background between L 0.235 and 0.45, which is where saturated brand teals and greens sit; on `#2FC4B2` it yields 1.86:1. Spec §1.2 has the measurements.
- **Do not add a scrim.** Spec §1.3: a tone-coloured scrim reduces contrast at every alpha, and the title-hidden case it was written for no longer exists post-L″.
- **`tone` names the background, not the ink.** `light` means a light ground and therefore dark ink; `dark` means a dark ground and light ink. This is §10.2's own naming and the CSS attribute values depend on it.
- **Do not reuse `GuiTool.getContrastColor`** (`gui-tool.ts:1285`), however apt the name looks. It decides via TinyColor's `isDark()` — YIQ brightness against 128, not relative luminance — and returns pure `#fff`/`#000`. That is a different rule, and using it reintroduces exactly the defect the measured pick exists to avoid. Its two existing callers (`viewsheet-pane.component.ts:842`, `vs-table-cell.component.ts:328`) are out of scope and must not change.
- **Do not touch resting visibility.** Whether the strip is drawn at rest versus revealed on hover is §10.1, a separate roadmap item scoped by pointer capability. This plan changes appearance only.
- **Do not touch glyph artwork.** No codepoint changes, no new glyphs, no `_icon-alias.scss` repointing.
- **Only `vs-object-container.component.html:343` binds `anchorInTitleLane`.** The other five `<mini-toolbar>` render sites (composer, embed, two wizard surfaces, vsview) must be unaffected, as must gate-off.
- **Never run the full frontend TL suite.** Scope every `*.tl.spec.ts` run with `--include` naming a single spec file. An unfiltered TL run exceeds the window and orphans multi-gigabyte worker processes. This plan adds no TL specs.
- TypeScript conventions in this codebase: 3-space indent, `if(cond)` with no space after `if`, brace on the same line, `else {` on its own line, comments kept to a short clause.
- No design-doc, decision-record, ticket or plan-phase references in source comments. State rules directly.
- No comments in Angular HTML templates.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.ts` | the contrast pick, the background chain, the memoized entry point | 1, 2 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.spec.ts` | swatch table, chain fallbacks, the 3:1 property test | 1, 2 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` | one delegate method | 3 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:342-366` | `data-tone` and `--viz-strip-glyph-a` bindings | 3 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` | seamless overrides, tone colours, hover formula, focus ring, divider suppression | 4 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` | the five deletions and the touch translation | 5 |
| `docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md` | §1's four corrections | 7 |
| `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` | move §10.2 out of "Ready now" into "Done" | 7 |

The whole resolver is one file because the three functions are one responsibility — turning a model into a glyph treatment — and they are only ever used together. Keeping them in `mini-toolbar/` rather than `common/util/` is deliberate: nothing outside the strip consumes them, and `gui-tool.ts` is already large.

---

## Task 1: The contrast pick

**Files:**
- Create: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.spec.ts`

**Interfaces:**
- Consumes: `@ctrl/tinycolor` — `TinyColor` (for `setAlpha`, `onBackground`, `isValid`, `getAlpha`) and the exported `readability(c1, c2)` function. Both come from the package root; `main` is `dist/public_api.js`, which re-exports `./readability.js`.
- Produces:
  - `export interface StripGlyphTone { tone: "light" | "dark"; alpha: number; }`
  - `export function stripGlyphInk(background: string): StripGlyphTone`

`readability()` computes luminance per colour and ignores alpha, so a translucent ink must be composited onto the background **before** measuring. `TinyColor.onBackground(bg)` does exactly that compositing.

- [ ] **Step 1: Write the failing test**

Create `strip-glyph-tone.spec.ts`. Copy the 17-line AGPL header from `mini-toolbar.service.spec.ts` verbatim, then:

```ts
import { readability, TinyColor } from "@ctrl/tinycolor";
import { stripGlyphInk } from "./strip-glyph-tone";

// Measured independently of the module under test: composite the returned ink at the returned alpha
// and read the ratio straight from TinyColor. The pick and the alpha climb are not reused here.
function achieved(background: string): number {
   const { tone, alpha } = stripGlyphInk(background);
   const ink = tone === "light" ? "#000000" : "#FFFFFF";
   const composited = new TinyColor(ink).setAlpha(alpha).onBackground(background);

   return readability(composited, background);
}

describe("stripGlyphInk", () => {
   it("leaves every seeded default at its base alpha", () => {
      // white card, the filled title lane, and both dark-mode surfaces
      expect(stripGlyphInk("#FFFFFF")).toEqual({ tone: "light", alpha: 0.55 });
      expect(stripGlyphInk("#F1EFEA")).toEqual({ tone: "light", alpha: 0.55 });
      expect(stripGlyphInk("#2D2B30")).toEqual({ tone: "dark", alpha: 0.8 });
      expect(stripGlyphInk("#252428")).toEqual({ tone: "dark", alpha: 0.8 });
   });

   it("picks dark ink on the mid-tone saturated colours a luminance threshold gets wrong", () => {
      // a fixed L > 0.45 threshold selects the light ink here and lands at 1.86:1
      expect(stripGlyphInk("#2FC4B2").tone).toBe("light");
      expect(stripGlyphInk("#E8563F").tone).toBe("light");
      expect(achieved("#2FC4B2")).toBeGreaterThanOrEqual(3);
      expect(achieved("#E8563F")).toBeGreaterThanOrEqual(3);
   });

   it("picks light ink on genuinely dark grounds", () => {
      expect(stripGlyphInk("#000000").tone).toBe("dark");
      expect(stripGlyphInk("#1C1B1F").tone).toBe("dark");
   });

   it("raises the alpha only where the floor demands it, and only slightly", () => {
      expect(stripGlyphInk("#EE1199").alpha).toBeGreaterThan(0.55);
      expect(stripGlyphInk("#EE1199").alpha).toBeLessThan(0.6);
      expect(stripGlyphInk("#DD5544").alpha).toBeGreaterThan(0.8);
      expect(stripGlyphInk("#DD5544").alpha).toBeLessThan(0.85);
      expect(stripGlyphInk("#778888").alpha).toBeGreaterThan(0.55);
      expect(stripGlyphInk("#778888").alpha).toBeLessThan(0.6);
   });

   it("never returns a pair below the contrast floor, across the colour cube", () => {
      const failures: string[] = [];

      for(let r = 0; r < 256; r += 17) {
         for(let g = 0; g < 256; g += 17) {
            for(let b = 0; b < 256; b += 17) {
               const bg = new TinyColor({ r, g, b }).toHexString();

               if(achieved(bg) < 3) {
                  failures.push(bg);
               }
            }
         }
      }

      expect(failures).toEqual([]);
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd community/web && npx ng test portal --include='**/strip-glyph-tone.spec.ts'
```

Expected: FAIL — the module does not exist, so the import cannot resolve.

- [ ] **Step 3: Write the implementation**

Create `strip-glyph-tone.ts` with the same AGPL header, then:

```ts
import { readability, TinyColor } from "@ctrl/tinycolor";

/** WCAG 1.4.11 floor for a meaningful non-text control. */
const MIN_CONTRAST = 3;
/** Resting alphas. The ink is raised above these only where the floor demands it. */
const LIGHT_BASE_ALPHA = 0.55;
const DARK_BASE_ALPHA = 0.8;
/** Climb step, within half a percent of the lowest alpha that clears the floor. */
const ALPHA_STEP = 0.005;

/** Which ground the glyph sits on: light means dark ink, dark means light ink. */
export interface StripGlyphTone {
   tone: "light" | "dark";
   alpha: number;
}

function contrast(ink: string, alpha: number, background: string): number {
   return readability(new TinyColor(ink).setAlpha(alpha).onBackground(background), background);
}

/**
 * The lowest alpha at or above base that clears the floor, and the ratio it reaches. Returns base
 * untouched when the floor is already met, so an unlifted alpha compares exactly.
 */
function climb(ink: string, base: number, background: string): { alpha: number, ratio: number } {
   let alpha = base;
   let ratio = contrast(ink, alpha, background);

   while(ratio < MIN_CONTRAST && alpha < 1) {
      alpha = Math.min(1, alpha + ALPHA_STEP);
      ratio = contrast(ink, alpha, background);
   }

   return { alpha, ratio };
}

/**
 * The ink that measures higher against the background at its resting alpha, raised only as far as
 * the contrast floor requires. Deliberately no luminance threshold: a fixed one picks the
 * lower-contrast ink for every background between L 0.235 and 0.45, which is where saturated teals
 * and greens sit.
 *
 * The ink is chosen before the climb, not after. Where both inks start under the floor they both
 * climb to just above it, and comparing the climbed ratios decides the tone on fourth-decimal noise
 * — so two backgrounds a viewer cannot tell apart get opposite glyphs. The base comparison is the
 * stable one.
 */
export function stripGlyphInk(background: string): StripGlyphTone {
   const lightBase = contrast("#000000", LIGHT_BASE_ALPHA, background);
   const darkBase = contrast("#FFFFFF", DARK_BASE_ALPHA, background);

   return lightBase >= darkBase
      ? { tone: "light", alpha: climb("#000000", LIGHT_BASE_ALPHA, background).alpha }
      : { tone: "dark", alpha: climb("#FFFFFF", DARK_BASE_ALPHA, background).alpha };
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd community/web && npx ng test portal --include='**/strip-glyph-tone.spec.ts'
```

Expected: PASS, 5 tests. If the "never returns a pair below the contrast floor" test reports failures, the alpha climb has a bug — do **not** lower `MIN_CONTRAST` or widen the tolerance to make it green.

Floating-point note: the alphas are built by repeated `+ 0.005` from `0.55` and `0.8`, so the base-alpha assertions in Step 1 compare exact untouched values and the lifted ones use range assertions. If an exact comparison on a lifted alpha is ever needed, round it at the assertion, not in the implementation.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.spec.ts
git commit -m "feat(vsobjects): pick a strip glyph ink by measured contrast, not a luminance threshold"
```

---

## Task 2: The background chain and the memoized entry point

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.spec.ts`

**Interfaces:**
- Consumes: `StripGlyphTone` and `stripGlyphInk` from Task 1; `VSObjectModel` from `../../model/vs-object-model`.
- Produces:
  - `export function resolveStripBackground(model: VSObjectModel, canvasBackground?: string): string`
  - `export function stripGlyphTone(model: VSObjectModel, canvasBackground?: string): StripGlyphTone`

Two facts about the model that shape this. `objectFormat: VSFormatModel`, `vizModern` and `vizDark` are declared on `VSObjectModel` (`vs-object-model.ts:27,67,68`), but **`titleFormat` is not** — it lives on titled subtypes only. `anchoredLaneHeight` (`mini-toolbar.service.ts:77`) handles this with `const titled = <any> model;`; follow that precedent rather than widening the base interface.

The final fallback keys on `model.vizDark` rather than assuming white. An author who clears both the title and card backgrounds in a dark-mode org would otherwise get dark ink on a dark canvas.

- [ ] **Step 1: Write the failing test**

Append to `strip-glyph-tone.spec.ts`, and extend the existing import to
`import { resolveStripBackground, stripGlyphInk, stripGlyphTone } from "./strip-glyph-tone";`:

```ts
function model(over: any = {}): any {
   return Object.assign({ objectFormat: {}, vizModern: true, vizDark: false }, over);
}

describe("resolveStripBackground", () => {
   it("prefers the title lane, which is what the anchored strip physically sits in", () => {
      const m = model({
         titleFormat: { background: "#F1EFEA" },
         objectFormat: { background: "#FFFFFF" }
      });

      expect(resolveStripBackground(m)).toBe("#F1EFEA");
   });

   it("falls through to the card when the lane is unfilled", () => {
      expect(resolveStripBackground(model({ objectFormat: { background: "#252428" } })))
         .toBe("#252428");
      expect(resolveStripBackground(model({
         titleFormat: { background: null },
         objectFormat: { background: "#252428" }
      }))).toBe("#252428");
   });

   it("treats transparent, empty and unparseable values as absent", () => {
      const card = { background: "#FFFFFF" };

      expect(resolveStripBackground(model({ titleFormat: { background: "" }, objectFormat: card })))
         .toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "transparent" }, objectFormat: card }))).toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "rgba(0,0,0,0)" }, objectFormat: card }))).toBe("#FFFFFF");
      expect(resolveStripBackground(
         model({ titleFormat: { background: "not-a-colour" }, objectFormat: card }))).toBe("#FFFFFF");
   });

   it("uses the canvas only when both the lane and the card are absent", () => {
      expect(resolveStripBackground(model(), "#1C1B1F")).toBe("#1C1B1F");
      expect(resolveStripBackground(model({ objectFormat: { background: "#FFFFFF" } }), "#1C1B1F"))
         .toBe("#FFFFFF");
   });

   it("ends on a default that follows the dark flag, not a fixed white", () => {
      expect(resolveStripBackground(model())).toBe("#FFFFFF");
      expect(resolveStripBackground(model({ vizDark: true }))).toBe("#252428");
   });

   it("does not throw on a model with no formats at all", () => {
      expect(resolveStripBackground(<any> {})).toBe("#FFFFFF");
      expect(resolveStripBackground(null)).toBe("#FFFFFF");
   });
});

describe("stripGlyphTone", () => {
   it("returns the ink for the resolved background", () => {
      expect(stripGlyphTone(model({ titleFormat: { background: "#252428" } })))
         .toEqual({ tone: "dark", alpha: 0.8 });
      expect(stripGlyphTone(model({ objectFormat: { background: "#FFFFFF" } })))
         .toEqual({ tone: "light", alpha: 0.55 });
   });

   it("returns the identical object for a repeated background, so change detection is cheap", () => {
      const a = stripGlyphTone(model({ objectFormat: { background: "#2FC4B2" } }));
      const b = stripGlyphTone(model({ objectFormat: { background: "#2FC4B2" } }));

      expect(b).toBe(a);
   });

   it("does not confuse two assemblies whose backgrounds differ only in the dark flag", () => {
      expect(stripGlyphTone(model({ vizDark: false })).tone).toBe("light");
      expect(stripGlyphTone(model({ vizDark: true })).tone).toBe("dark");
   });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd community/web && npx ng test portal --include='**/strip-glyph-tone.spec.ts'
```

Expected: FAIL — `resolveStripBackground` and `stripGlyphTone` are not exported.

- [ ] **Step 3: Write the implementation**

Add to the imports at the top of `strip-glyph-tone.ts`:

```ts
import { VSObjectModel } from "../../model/vs-object-model";
```

Add to the constants block:

```ts
const CANVAS_LIGHT = "#FFFFFF";
const CANVAS_DARK = "#252428";
/** Distinct backgrounds per session are few; the cap only stops unbounded growth. */
const MEMO_LIMIT = 64;
```

Then append:

```ts
const toneMemo = new Map<string, StripGlyphTone>();

/** Present, parseable, and not fully transparent. */
function usable(color: string): boolean {
   if(!color) {
      return false;
   }

   const parsed = new TinyColor(color);
   return parsed.isValid && parsed.getAlpha() > 0;
}

/**
 * What the strip actually sits on. The anchored strip lives in the title lane, so the lane comes
 * first; the lane is unfilled on some assemblies and is due to become unfilled on all of them, at
 * which point the card behind it shows through. The last step follows the dark flag rather than
 * assuming white, so a cleared card in a dark org does not get dark ink on a dark canvas.
 */
export function resolveStripBackground(model: VSObjectModel, canvasBackground?: string): string {
   const titled = <any> model;

   if(usable(titled?.titleFormat?.background)) {
      return titled.titleFormat.background;
   }

   if(usable(titled?.objectFormat?.background)) {
      return titled.objectFormat.background;
   }

   if(usable(canvasBackground)) {
      return canvasBackground;
   }

   return titled?.vizDark ? CANVAS_DARK : CANVAS_LIGHT;
}

/**
 * The glyph treatment for an assembly's strip, memoized on the colours it derives from so the
 * alpha climb runs once per distinct background rather than once per change-detection pass. The
 * same object identity comes back for a repeated background, which keeps the template binding
 * stable.
 */
export function stripGlyphTone(model: VSObjectModel, canvasBackground?: string): StripGlyphTone {
   const titled = <any> model;
   const key = [titled?.titleFormat?.background, titled?.objectFormat?.background,
                canvasBackground, titled?.vizDark].join("|");
   let tone = toneMemo.get(key);

   if(!tone) {
      tone = stripGlyphInk(resolveStripBackground(model, canvasBackground));

      if(toneMemo.size >= MEMO_LIMIT) {
         toneMemo.clear();
      }

      toneMemo.set(key, tone);
   }

   return tone;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd community/web && npx ng test portal --include='**/strip-glyph-tone.spec.ts'
```

Expected: PASS, 14 tests (5 from Task 1, 9 added here).

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/strip-glyph-tone.spec.ts
git commit -m "feat(vsobjects): resolve the strip's background from the lane, the card, then the canvas"
```

---

## Task 3: Bind the tone onto the strip

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` (imports, and one new method beside `isKebabResident` at `:485`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:342-366`

**Interfaces:**
- Consumes: `stripGlyphTone` and `StripGlyphTone` from Task 2.
- Produces: `public getStripGlyphTone(object: VSObjectModel): StripGlyphTone` on `VSObjectContainerComponent`, read by the template.

The container is the right call site because both formats are already in hand per `vsObject` there, and it is the only one of the six `<mini-toolbar>` render sites that anchors. `canvasBackground` is left unsupplied: `viewsheetBackground` lives on `viewer-app.component.ts:463` and threading it down is not part of this change. The parameter exists so that step of the chain is available to a later caller without reopening the resolver.

- [ ] **Step 1: Add the delegate method**

Extend the existing mini-toolbar import at `vs-object-container.component.ts:57`:

```ts
import { anchoredLaneHeight, isAnchoredResident, MiniToolbarService } from "./mini-toolbar/mini-toolbar.service";
import { StripGlyphTone, stripGlyphTone } from "./mini-toolbar/strip-glyph-tone";
```

Add immediately after `isKebabResident` (which ends at `:487`):

```ts
   /**
    * The glyph treatment for this assembly's anchored strip: which of the two inks reads against
    * the colour the strip sits on, and at what alpha. Memoized in the resolver, so calling this
    * from the template costs a map lookup per pass.
    */
   public getStripGlyphTone(object: VSObjectModel): StripGlyphTone {
      return stripGlyphTone(object);
   }
```

- [ ] **Step 2: Bind it in the template**

In `vs-object-container.component.html`, the `<mini-toolbar>` block starts at `:343` inside the
`@if` opened at `:342`. Insert an `@let` between the `@if` and the element, then two bindings.
`@let` is already used in this template at `:419-421`.

Change:

```html
      @if (!hideMiniToolbar && isMiniToolbarVisible(vsObject) && vsObject.enabled) {
        <mini-toolbar
          VSDataTip [dataTipName]="vsObject.absoluteName" [miniToolbar]="true"
          [class.viz-modern]="vsObject.vizModern"
          [class.viz-dark]="vsObject.vizDark"
```

to:

```html
      @if (!hideMiniToolbar && isMiniToolbarVisible(vsObject) && vsObject.enabled) {
        @let glyphTone = getStripGlyphTone(vsObject);
        <mini-toolbar
          VSDataTip [dataTipName]="vsObject.absoluteName" [miniToolbar]="true"
          [class.viz-modern]="vsObject.vizModern"
          [class.viz-dark]="vsObject.vizDark"
          [attr.data-tone]="glyphTone.tone"
          [style.--viz-strip-glyph-a]="glyphTone.alpha"
```

Leave every other binding on the element untouched.

- [ ] **Step 3: Verify it compiles and nothing regresses**

```bash
cd community/web && npx ng build portal --configuration development
```

Expected: build succeeds. A failure naming `--viz-strip-glyph-a` means the custom-property binding
syntax was mistyped — the working precedents are `vs-table-cell.component.html:115` and
`vs-slider.component.html:56`.

Then confirm the container's existing behaviour is intact:

```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.service.spec.ts'
cd community/web && npx ng test portal --include='**/mini-toolbar.component.spec.ts'
```

Expected: PASS, unchanged counts. Nothing in this step changes what those cover; they are run to
prove the container edit did not disturb them.

- [ ] **Step 4: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html
git commit -m "feat(vsobjects): carry the resolved glyph tone onto the mini-toolbar"
```

---

## Task 4: The seamless strip

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss`

This is the whole visible change. Four things happen together because none of them is correct alone:
the surface goes, the glyph takes the tone, the divider stops drawing a fixed border on a colour the
author owns, and the kebab's `opacity: 0.55` goes because multiplying it by the light tone's own
`.55` yields an effective 0.303 — **2.12:1 on a white card**, which would make the one control
visible at rest the least legible thing on it.

Two specificity facts that decide how this is written:

- The surface declarations at `:31-33` are **unconditional**, so they are overridden under the anchored scope, not deleted. `.mini-toolbar.mini-toolbar--anchored .mini-toolbar-container` is (0,3,0) against `.mini-toolbar .mini-toolbar-container`'s (0,2,0), so it wins without depending on source order — the convention this file states for itself at `:156-161`.
- `assets/ineticons/style.scss:48` sets `color: var(--inet-icon-color)` on the glyph element itself, so a colour on the button alone never reaches the `<i>`. The rule must name **both**, and `button i` at (0,2,2) beats `.ineticons` at (0,1,0) with no `!important`.

`.mini-toolbar--anchored` already carries both the per-assembly gate and the anchored-set condition
on its own (see the comment at `:93-98`), so none of this needs a separate `.viz-modern` scope.

- [ ] **Step 1: Add the seamless surface and tone rules**

Append to `mini-toolbar.component.scss`:

```scss
// The card background is author-set, so the strip cannot carry a fixed treatment: no fill, no
// border, no radius, and the glyph takes whichever of two inks reads against what it sits on. The
// declarations overridden here are unconditional above, so this scope wins on specificity (0,3,0
// against 0,2,0) rather than on source order. Nothing to clip once the radius is gone.
.mini-toolbar.mini-toolbar--anchored .mini-toolbar-container {
  background-color: transparent;
  border: none;
  border-radius: 0;
  overflow: visible;
}

// The resting alpha arrives as --viz-strip-glyph-a, solved per background so the pair always clears
// 3:1. The revealed alpha is derived from it under a second name: a custom property whose own value
// references itself is a cycle and resolves to nothing, so the step cannot be written onto
// --viz-strip-glyph-a directly. One TS-supplied value, the relationship stated here.
.mini-toolbar--anchored {
  --viz-strip-glyph-a-hover: min(1, calc(var(--viz-strip-glyph-a, 0.55) + 0.3));
}

// The colour is set on the button as well as the glyph: .ineticons sets its own color on the <i>, so
// the button alone would never reach it, and the focus ring below reads currentColor off the button.
// auto-refresh-true/false carry a deliberate red/green state colour and are left alone.
// data-tone and the inline alpha are set on the host element by the container, while
// .mini-toolbar--anchored is the inner div, so the tone is matched with :host() and the alpha
// inherits down. Both mirror the :host-context(.viz-modern) idiom already in this file.
:host([data-tone="light"]) .mini-toolbar--anchored .mini-toolbar-container {
  --viz-strip-ink: 0 0 0;
  --viz-strip-hover-bg: rgba(0, 0, 0, 0.06);
}

:host([data-tone="dark"]) .mini-toolbar--anchored .mini-toolbar-container {
  --viz-strip-ink: 255 255 255;
  --viz-strip-hover-bg: rgba(255, 255, 255, 0.18);
}

.mini-toolbar--anchored .mini-toolbar-container {
  button,
  button i:not(.auto-refresh-true):not(.auto-refresh-false) {
    color: rgb(var(--viz-strip-ink) / var(--viz-strip-glyph-a, 0.55));
  }

  button {
    transition: color 120ms ease-in-out;

    &:not([disabled]):hover,
    &:focus-visible {
      background-color: var(--viz-strip-hover-bg) !important;
    }

    &:not([disabled]):hover,
    &:not([disabled]):hover i,
    &:focus-visible,
    &:focus-visible i {
      color: rgb(var(--viz-strip-ink) / var(--viz-strip-glyph-a-hover));
    }

    // currentColor is the tone ink at the revealed alpha, so the inset arm follows the card with no
    // extra plumbing. The outer ring is unchanged.
    &:focus-visible {
      box-shadow: inset 0 0 0 1px currentColor, var(--inet-focus-ring) !important;
    }
  }
}

// A 1px --inet-default-border-color divider dissolves on a light card and clashes on a saturated
// one. Spacing separates the groups instead.
.mini-toolbar--anchored .mini-toolbar-button-group + .mini-toolbar-button-group {
  border-left: none;
}
```

Both syntax dependencies here — space-separated `rgb(r g b / a)` and `min()` in a custom-property
value — are **confirmed safe** for this project's target and ship with no fallback. There is no
`.browserslistrc` in `web/`, so Angular's own default applies; `npx browserslist` resolves the oldest
real engines to Chrome 109, Firefox 140, Safari 26.3 / iOS 18.5, Edge 146 and Samsung 28, while the
two features need Chrome 65+/79+ respectively.

One correction to an earlier draft of this plan, kept because the claim was wrong in an instructive
way: it said a failed `rgb()` "leaves the glyph at `--inet-icon-color`". It would not. An
unresolvable `var()` makes the declaration *invalid at computed-value time*, which for an inherited
property like `color` means **inherit** — not fall back to the losing `.ineticons` declaration. The
state is unreachable anyway, since `getStripGlyphTone` always returns `"light"` or `"dark"`.

- [ ] **Step 2: Delete the resting opacity multiplier**

At `:179-183` the rule currently reads:

```scss
.mini-toolbar--anchored:not(.hidden-mini-toolbar) .mini-toolbar-kebab {
  opacity: 0.55;
  pointer-events: auto;
  transition: opacity 120ms ease-in-out;
}
```

Replace with:

```scss
.mini-toolbar--anchored:not(.hidden-mini-toolbar) .mini-toolbar-kebab {
  pointer-events: auto;
}
```

`pointer-events: auto` is load-bearing and stays — the comment above the rule explains why
`:not(.hidden-mini-toolbar)` guards it. The transition moves to `color` on the button rule in Step 1.
The resting-versus-revealed distinction the opacity provided is now carried by the tone's own
resting and hover alphas, which is why the multiplier must not survive alongside them.

Then delete the hover/focus companion at `:185-188` entirely:

```scss
.mini-toolbar--anchored:not(.hidden-mini-toolbar):hover .mini-toolbar-kebab,
.mini-toolbar--anchored:not(.hidden-mini-toolbar):focus-within .mini-toolbar-kebab {
  opacity: 1;
}
```

- [ ] **Step 3: Build, then look at it**

```bash
cd community/web && npx ng build portal --configuration development
```

Expected: build succeeds; a Sass error here is almost always a stray brace in the appended block.

Then start the app and check a chart on a modern dashboard, at compact or comfortable density so the
lane holds the strip:

- the strip has no box: no fill, no border, no rounded outline, on the card and on the title lane
- the resting kebab is a bare glyph and is clearly legible, not washed out
- hovering the assembly reveals the action glyphs, each with a soft translucent hover patch and no divider lines between the groups
- tabbing to the kebab draws a visible ring
- setting the assembly's title background to a saturated colour (`#2FC4B2` is the useful one) flips the glyphs to white and keeps them readable
- the strip on a *non-anchored* assembly — a gauge or a text object — still has its white bordered pill

- [ ] **Step 4: Confirm nothing else moved**

```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.component.spec.ts'
cd community/web && npx ng test portal --include='**/strip-glyph-tone.spec.ts'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss
git commit -m "feat(vsobjects): draw the in-lane strip as bare glyphs toned to the card behind them"
```

---

## Task 5: Remove the rules that became dead

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss`

Separate from Task 4 because a reviewer should be able to check this on its own question: were exactly
the dead rules removed, and was the one that is *not* dead translated rather than dropped.

- [ ] **Step 1: Delete the two divider suppress/restore rules**

Both exist only to hide the group divider at rest and put it back on reveal. Task 4 removed the
divider under the anchored scope, so neither has anything left to do. Delete the rule at `:162-164`
together with its comment block at `:156-161`:

```scss
.mini-toolbar--anchored .mini-toolbar-button-group.mini-toolbar-kebab-group {
  border-left: none;
}
```

and the rule at `:166-169`:

```scss
.mini-toolbar--anchored:hover .mini-toolbar-button-group + .mini-toolbar-button-group.mini-toolbar-kebab-group,
.mini-toolbar--anchored:focus-within .mini-toolbar-button-group + .mini-toolbar-button-group.mini-toolbar-kebab-group {
  border-left: 1px solid var(--inet-default-border-color);
}
```

Leave the unconditional `.mini-toolbar-button-group + .mini-toolbar-button-group` divider at `:76-78`
in place — the floating strip on the five non-anchored render sites still draws it.

- [ ] **Step 2: Drop the redundant coarse-pointer overflow override**

In the `@media (pointer: coarse)` block, the rule currently overrides `height` and `overflow`
together because the container clipped the 44px touch target back to 24px. Task 4 made the anchored
container `overflow: visible` unconditionally, so that half is now redundant. Remove only the
`overflow` line and trim its share of the comment:

```scss
  :host-context(.viz-modern) .mini-toolbar--anchored:not(.hidden-mini-toolbar) .mini-toolbar-container {
    height: auto;
    min-height: var(--inet-control-height-touch);
  }
```

`height: auto` and `min-height` stay: the 24px row pin at `:82` is still there and still needs
beating on touch.

- [ ] **Step 3: Translate the coarse kebab rule instead of deleting it**

This is the one that is **not** dead. The rule at `:207-211` reads:

```scss
  .mini-toolbar--anchored:not(.hidden-mini-toolbar) .mini-toolbar-kebab {
    min-width: var(--inet-control-height-touch);
    min-height: var(--inet-control-height-touch);
    opacity: 1;
  }
```

Touch has no hover to sweep, so the kebab is drawn at rest as the assembly's only chrome and
`opacity: 1` is what keeps it at full strength. Replace that line with the tone equivalent — the same
step the hover rule applies — so the behaviour survives the move from opacity to colour:

```scss
  .mini-toolbar--anchored:not(.hidden-mini-toolbar) .mini-toolbar-kebab {
    min-width: var(--inet-control-height-touch);
    min-height: var(--inet-control-height-touch);
    --viz-strip-glyph-a: var(--viz-strip-glyph-a-hover);
  }
```

Referencing the derived property rather than re-deriving it is deliberate: writing the `min()`
expression onto `--viz-strip-glyph-a` here would be the same self-reference Task 4 avoids.

Do **not** take this further and rework when the strip is drawn at rest on a pointer device. That is
§10.1, a separate roadmap item keyed on pointer capability rather than on geometry, and this plan must
leave the resting predicate alone.

- [ ] **Step 4: Build, then check touch**

```bash
cd community/web && npx ng build portal --configuration development
```

Expected: build succeeds.

In the browser, emulate a touch device (device toolbar with a mobile profile, so
`pointer: coarse` matches) and confirm on a modern chart:

- the kebab is drawn at rest at full strength, not dimmed
- its hit target is the full 44px, not clipped to the 24px row
- no divider line appears to the left of the kebab at rest

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss
git commit -m "refactor(vsobjects): retire the strip rules the seamless treatment made redundant"
```

---

## Task 6: Full verification

**Files:** none — this task changes nothing and exists to prove the previous five did not.

- [ ] **Step 1: Run the portal suite**

```bash
cd community/web && npx ng test portal
```

Expected: PASS. Record the count; the only change against the pre-plan baseline should be the 14
tests added by Tasks 1 and 2.

- [ ] **Step 2: Run the em suite**

```bash
cd community/web && npx ng test em
```

Expected: PASS, unchanged. Nothing in this plan touches `projects/em`; this run is to prove the
shared SCSS edits did not leak, since `_icons.scss` and the ineticons font are shared.

- [ ] **Step 3: Cross-module build**

```bash
cd community && ./mvnw clean install -DskipTests -Pcommunity,enterprise
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual pass**

Work through each row. All of it on a modern dashboard at a density whose lane holds the strip
(compact 26 or comfortable 30); dense 20 suppresses the strip entirely and is a check that *nothing*
is drawn.

| Surface | Check |
|---|---|
| White card, light mode | Bare glyphs, dark ink, legible at rest and on hover |
| Filled `#F1EFEA` title lane | Same treatment; the strip does not reintroduce a box against the lane fill |
| Dark mode (`#252428` card) | Glyphs flip to white with no dark-mode rule involved |
| Author-set `#2FC4B2` title background | White glyphs, readable — this is the case a luminance threshold gets wrong |
| Author-set `#C8CCC9` title background | Dark glyphs, readable — the case a white box used to dissolve on |
| All three densities | Compact and comfortable draw the strip; dense draws no chrome at all |
| Fine pointer | Kebab at rest, action groups revealed on hover, hover patch present, no dividers |
| Coarse pointer | Kebab at rest at full strength, 44px target |
| Keyboard | Tab reaches the kebab; the focus ring is visible on both a light and a dark card |
| Chart with manual/auto refresh reachable | The red/green state glyph keeps its colour and is not toned |
| Gauge or text object | Floating strip unchanged: white pill, border, radius |
| Composer | Strip unchanged — it never anchors |
| Gate off | Everything byte-identical to before this plan |

- [ ] **Step 5: Commit nothing, report**

This task produces no commit. Report the three suite results, the build result, and any manual row
that did not pass.

---

## Task 7: Correct the source docs and close the roadmap entry

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md`
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`

The four corrections must land here and **not** inside `chart-card-design3/`. That folder is
regenerated wholesale by an external sync, and the roadmap records that the 2026-08-12 regeneration
destroyed four corrections written into it. A note placed there has a lifetime of one sync.

- [ ] **Step 1: Add the four corrections**

Add a section to `chart-card-source-doc-corrections.md` recording, each with the measurement or file
citation from the spec:

1. §10.2's `<img>` precondition is false — `mini-toolbar.component.html:48,72` render `<i [class]=...>`, and all eighteen glyphs the chart toolbar can draw resolve to ineticons font codepoints, so `color` tints them. No inline-SVG conversion and no `filter: invert()` pair are needed.
2. §10.2's `L > 0.45` threshold selects the lower-contrast ink between L 0.235 and 0.45, including 1.86:1 on `#2FC4B2` — the swatch §10.2 itself flags as the one to check first. Two of its four demonstration swatches fail 3:1 with the tone the rule picks.
3. §10.2's scrim reduces contrast at every alpha (2.97:1 → 2.76:1 at the prescribed ~8%), and the title-hidden case it was written for no longer exists on the anchored path post-L″.
4. §10.2's hover background replaces nothing — `mini-toolbar.component.scss:40` sets `background-color: transparent !important`, which beats `.icon-hover-bg`, so the buttons have no hover fill today and the tint is new behaviour.

- [ ] **Step 2: Move the roadmap entry**

In `chart-card-roadmap.md`, remove the **§10.2 — the seamless in-lane strip** row from the *Ready now*
table and add it to the *Done* table, cited by commit subject rather than hash — the file's own
instruction, since the branch has been rewritten three times and every hash a prior revision carried
went stale. Note in the Done row that the implementation diverged from the source spec on the tone
rule, and point at the corrections doc.

Leave the **§10.1** row in *Ready now* untouched. It is now the smallest remaining item on that list
and this work does not start it.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md \
        docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record the seamless strip and four corrections to its source spec"
```
