# Per-Dashboard Visualization Override — Design

**Date:** 2026-09-18
**Status:** designed, not built
**Scope:** community only (`core/` + `web/projects/portal`). No enterprise or server change.
**Verified against:** `f897b9050` on `epic-74519`.

Let an author set modern/legacy, dark, and density **per dashboard**, from the Options pane of the
viewsheet property dialog, instead of taking whatever the org-wide settings say. This is not an item on
`chart-card-roadmap.md`; it is new work that sits on top of the seed-mark track that file describes.

---

## What already exists

Four facts about the shipped implementation shape this whole design. Each was verified, not assumed.

**1. Modern/legacy is per-assembly, and already per-dashboard one level up.**
`VizMark` (`MODERN_LIGHT` / `MODERN_DARK` / null) is stamped on each `VSAssemblyInfo`. Render-time
resolution goes through `VizContext.of(info)` (`VizContext.java:65`). The org gate
`viewsheet.modernVisualization` is creation-time only; P6 removed its read-time life apart from three
documented exceptions.

`VizMark.fromGate()` has exactly two callers: `ViewsheetVSAssemblyInfo`'s constructor
(`ViewsheetVSAssemblyInfo.java:47`) and `VizModernizeUtil.modernize()`. The constructor is the only
*creation* site, and it stamps the **sheet's own** info. Every other assembly inherits the host
sheet's mark:

```java
// AbstractVSAssembly.java:127-136
public AbstractVSAssembly(Viewsheet vs, String name) {
   ...
   VSAssemblyInfo hostInfo = vs == null ? null : vs.getVSAssemblyInfo();
   info.setVizMark(hostInfo == null ? null : hostInfo.getVizMark());
}
```

`VizModernizeUtil.collect()` includes `vs.getVSAssemblyInfo()` in both target lists, so Modernize and
Revert already flip it. **A persisted per-dashboard mode therefore already exists: the sheet's own
`VizMark`.** Revert a dashboard today and the chart added tomorrow is already stamped legacy.

**2. Density has exactly one reader.** `VSDensityDefaults.mode()` is called from nowhere in
`core/src/main/java` except `VizContext`'s three factories. Everything else takes density off a
`VizContext`. Sheet-scoped density is therefore a change to one class.

**3. Density is mostly live, not seeded.** Table row/header height (`VSTableLens.java:1779`,
`BaseTableService.java:462` and `:1168`), selection cell height
(`SelectionBaseVSAssemblyInfo.getEffectiveCellHeight`) and the title lane
(`VSDensityDefaults.titleHeight(info, stored)`) all resolve at render time from a `VizContext` built
from the assembly's own info. Only form-control heights are seeded into a persisted `Dimension`.

**4. No operation changes `MODERN_LIGHT` to `MODERN_DARK`.** `modernize()` targets only unmarked
content and `revert()` only marked content. A light-to-dark flip has no path today.

---

## Decisions

**D1 — The switch is a policy that drives the marks, not a render-time override.**
On OK the dialog runs the mark transition over the sheet's content, and the sheet's mark continues to
govern what assemblies created later are stamped with. Render-time resolution is unchanged, so this
adds no new read-time branch.

*Rejected: a render-time override above the mark.* It would reintroduce the read-time gate P6 deleted,
and it cannot reach anything `seedChromeDefaults` persisted — chart palettes, card backgrounds,
title-lane borders, corner radius. Switching to Legacy would leave modern palettes in place.

*Rejected: making the pane a pure front-end for Modernize/Revert with no stored state.* It cannot
govern what new assemblies are stamped with, so the dashboard re-mixes as soon as an object is added.

**D2 — Modern and Dark are separate switches; Dark is disabled unless Modern is on.**
An author can make one dashboard dark in a light org.

*Consequence, priced and accepted:* `viz-shell-dark` is one of the three reads still following the org
gate. It redefines only `--inet-viz-*` state tokens for surfaces **outside** any assembly wrapper
(`_viz-tokens.scss:175`). The dashboard's page background is *not* one of them — it is seeded onto the
sheet's own info from its own mark (`ViewsheetVSAssemblyInfo.java:244`), and tooltips already resolve
dark per-assembly via `widget__tooltip--dark` rather than off the shell, with a comment at
`_directives.scss:411` explaining that keying them to the shell was a bug. The residue is detached
overlays owned by a dashboard assembly but reparented to `body` — combo-box dropdown lists, context
menus, popups. Bounded list, same fix pattern the tooltip already uses. **Needs an audit during
implementation; it is not assumed to be empty.**

**D3 — No new field for mode or dark. One new field for density.**

```
per-dashboard mode    ->  vs.getVSAssemblyInfo().getVizMark()      EXISTING
per-dashboard dark    ->  same mark (MODERN_DARK vs MODERN_LIGHT)  EXISTING
per-dashboard density ->  ViewsheetInfo.vizDensity : String        NEW, null = inherit org
```

Adding `vizModern`/`vizDark` to `ViewsheetInfo` would be a second, competing representation of a bit
that already round-trips. Using the sheet mark also makes the switch position honest for a mixed
dashboard, and shows a pre-mark dashboard as Legacy — which is what it renders — rather than showing
the org's value.

`vizDensity` follows the `snapGrid` pattern in `ViewsheetInfo` (`:762` write, `:829` parse) but writes
its attribute **only when non-null**, so existing files, exports and bookmarks stay byte-identical
until someone sets it. Resolution is `sheet value ?? SreeEnv "viewsheet.density" ?? "compact"`.

**D4 — Embedded content takes the outermost sheet's density, but keeps its own mark.**
Density is a live layout property of the page in front of you; the mark is provenance.
`VizModernizeUtil.collect()` deliberately skips embedded content as "another asset's content" and that
stays true. The asymmetry is intentional and **must be commented at both sites**, or a later reader
will "fix" one to match the other. Editing an embedded asset standalone makes it outermost, so it
shows its own density — same rule, no special case.

**D5 — The dialog confirms on downgrade; the Modernize bar and Revert menu item stay.**
All three routes converge on the same `VizModernizeUtil` call and the same single undo step.

---

## Section 1 — data model

See D3. `ViewsheetInfo` gains one nullable `String vizDensity` with the omit-when-null serialization
rule, and `VSDensityDefaults` gains a `mode(ViewsheetInfo)` overload that applies the
sheet-then-org-then-default fallback and clamps through the existing `normalizeMode`. Nothing else is
persisted.

## Section 2 — server-side resolution

`VizContext.of(VSAssemblyInfo)` resolves density from the sheet:

```java
public static VizContext of(VSAssemblyInfo info) {
   VizMark mark = info == null ? null : info.getVizMark();
   boolean modern = mark != null;
   return new VizContext(modern, modern && mark == VizMark.MODERN_DARK, densityOf(info));
}

private static String densityOf(VSAssemblyInfo info) {
   Viewsheet vs = info == null ? null : info.getViewsheet();

   // an embedded asset's content takes the page's density, not its own - deliberately unlike the
   // mark, which VizModernizeUtil.collect() leaves to the embedded asset
   for(Viewsheet parent; vs != null && (parent = vs.getViewsheet()) != null; vs = parent) {
   }

   return vs == null ? VSDensityDefaults.mode() : VSDensityDefaults.mode(vs.getViewsheetInfo());
}
```

`Viewsheet` is itself a `VSAssembly`, so `Viewsheet.getViewsheet()` (`Viewsheet.java:1412`) gives the
container and the loop terminates at the top.

Roughly 60 of the ~65 `VizContext` construction sites go through `of(VSAssemblyInfo)`, so every live
density read inherits this with no further edits, and export agrees for free because it reads the same
`VSTableLens`. The five that bypass it:

| Site | Density needed? | Action |
|---|---|---|
| `ofGate()` x3 — palette picker fallback, `ChartColorPaletteController` | no, palette only | leave |
| `of(VizMark)` — picker's resolved path | no | leave |
| `ofTransition(mark)` x2 in `VizModernizeUtil` | **yes** — seeding writes control heights | -> `ofTransition(vs, mark)` |

**Control-height widening.** Seeding is guarded on the legacy default, so once a control is seeded to
24/28/30 the modern branch can never fire again and a density change would strand it:

```java
// ComboBoxVSAssemblyInfo.java:427 - same shape in Spinner, Submit, TextInput;
// CheckBox and RadioButton use the doubled form
if(ctx.modern && getPixelSize().height == AssetUtil.defh) {
   setPixelSize(..., VSDensityDefaults.controlHeight(ctx));
}
else if(!ctx.modern && VSDensityDefaults.isControlHeight(getPixelSize().height)) {
   setPixelSize(..., AssetUtil.defh);
}
```

Widen the modern branch to `height == AssetUtil.defh || VSDensityDefaults.isControlHeight(height)`.
This reuses the best-effort rule `isControlHeight` already establishes, including its accepted false
positive — a control hand-sized to exactly 24/28/30 is moved — documented at
`VSDensityDefaults.java:120`. Six info classes, all identically.

## Section 3 — how density reaches the browser

Density takes the shape `viz-modern` and `viz-dark` already have: **server-resolved, carried on the
assembly wrapper.** `VSObjectModel.java:103` already builds the context and reads two fields off it:

```java
VizContext vizContext = VizContext.of(assemblyInfo);
vizModern = vizContext.modern;
vizDark = vizContext.dark;
vizDensity = vizContext.density;   // new - same object, already resolved per sheet
```

The binding goes on the same elements as the existing pair —
`vs-object-container.component.html:24`, `editable-object-container.component.html:23`,
`layout-object`, `embed-chart`, the two wizard templates, `vs-object-view`:

```html
[class.viz-modern]="vsObject.vizModern"
[class.viz-dark]="vsObject.vizDark"
[class]="'viz-density-' + vsObject.vizDensity"
```

CSS: **replace** the descendant rules with compound ones on the wrapper.

```scss
.viz-modern { /* dense fallback - (0,1,0), loses to the compound */ }

.viz-modern.viz-density-compact     { }   // was .viz-density-compact .viz-modern
.viz-modern.viz-density-comfortable { }   // was .viz-density-comfortable .viz-modern

.viz-density-compact.viz-shell      { }   // body, org chrome - unchanged
.viz-density-comfortable.viz-shell  { }   // body, org chrome - unchanged
```

The descendant forms must be **deleted, not kept alongside**: `.viz-density-comfortable .viz-modern`
and `.viz-modern.viz-density-compact` are both specificity (0,2,0), so leaving both in puts the
outcome back on source order, where comfortable wins. Once they are gone, the body density class
reaches only `.viz-shell` chrome.

Why the wrapper rather than a dashboard-root container, even though D4 gives one density per page:

- **Composer tabs.** Several dashboards are open at once, so `body` cannot express it.
- **Embed.** The embed renders into a Shadow DOM with the stylesheet copied in (`ShadowDomService`), so
  a `body` class has never matched it — today it silently takes the bare dense fallback whatever the
  org is set to. A wrapper class inside the shadow root matches. Fixed as a side effect.
- **Binding pane and wizard preview** render assemblies outside any dashboard root, and both already
  bind `viz-modern` on the wrapper.
- **Export** is untouched; it is server-rendered through `VSTableLens`.

Two loose ends in the same change: `GuiTool.vizDensityMode()` (`gui-tool.ts:91`) reads the body class
and has **zero production callers** — only its own spec — so it goes or is re-pointed at an element.
And surfaces reparented to `body` that carry `.viz-modern` fall to the dense fallback rather than their
sheet's density; that list needs an audit rather than an assumption.

## Section 4 — the dialog and the apply path

Three controls in the Options fieldset of `viewsheet-options-pane.component.html`, following the
`form-check form-switch` markup already used for Use Metadata and Selection Association:

```
[on ] Modern Visualization
[off] Dark                    (disabled unless Modern)
Density:  [ Compact ]         (Default / Dense / Compact / Comfortable)
```

`VSOptionsPaneModel` gains `vizModern`, `vizDark` (both from the sheet mark) and `vizDensity` (the
sheet's stored value, empty meaning inherit). The read side in
`ViewsheetPropertyDialogService.java:110` is three lines. The write side at `:290`:

```java
VizMark target = model.isVizModern()
   ? (model.isVizDark() ? VizMark.MODERN_DARK : VizMark.MODERN_LIGHT) : null;

if(target != viewsheet.getVSAssemblyInfo().getVizMark()) {
   VizModernizeUtil.applyMark(viewsheet, target);
}

info.setVizDensity(model.getVizDensity());   // null/empty = inherit
```

**`applyMark(vs, mark)` is new**, forced by D2 and fact 4 above. It targets every collected info whose
mark differs from the target, stamps it, and seeds through `VizContext.ofTransition(vs, mark)`.
`modernize()` becomes `applyMark(vs, VizMark.fromGate())` behind the existing gate floor, and
`revert()` becomes `applyMark(vs, null)` — so the bar, the menu item and the dialog cannot drift
apart, which is the property `VizModernizeUtil`'s header comment already says the class exists to
protect.

A density change with no mark change still runs the same traversal with the target mark unchanged,
because the control-height widening needs the seed to fire.

**Confirmation and undo.** The client compares the switch against its loaded value and raises
`composer.vs.revert.confirm` before submitting when it moved Modern to Legacy. The existing
`@Undoable` on `setViewsheetInfo` already gives one undo step for the whole dialog.

**Refresh.** Mark and density changes alter `VSObjectModel` for every assembly, so the save path needs
a full `refreshViewsheet`, not the targeted commands the pane sends today for `snapGrid` and friends.

## Section 5 — testing

Java, extending files that already exist:

- **`VizContextTest`** — a sheet's density overrides the org; no sheet value falls back to the org; a
  null sheet falls back to the org; an assembly in an embedded viewsheet takes the outermost sheet's
  density, and the same assembly edited standalone takes its own.
- **`VizModernizeUtilTest`** — `applyMark` handles `MODERN_LIGHT` -> `MODERN_DARK` by re-seeding every
  target, and is not a no-op. All 34 existing cases must pass unchanged once `modernize`/`revert`
  delegate; if any needs editing, the delegation is wrong.
  `modernizeReachesAnEmbeddedViewsheetContainerButNotItsChildren` pins the mark's embedded rule and
  must stay green while density does the opposite — D4's asymmetry deserves a test of its own so it
  reads as intended rather than as a bug.
- **`ControlHeightFollowDensityTest`** — comfortable-to-dense re-substitution per control type.
  `spinnerHeightIsLeftAloneWhenAlreadyResized` uses height 40, not a tier value, so it stays green;
  that is luck rather than design, so add an explicit case at 28 documenting that a hand-sized control
  matching a tier *is* moved.
- **`VizContextReadFlipTest`** — `onlyTheDocumentedSitesStillReadTheOrgGate` and
  `exactlyTwoDocumentedSitesStillReadTheOrgGate` are architectural guards. This change adds no gate
  reads (density is a separate property), so they should pass untouched. If they do not, something
  read the gate that should not have.
- **`ViewsheetInfo` round-trip** — `vizDensity` written only when set, absent attribute parses as null,
  existing file byte-identical. Import/export and bookmarks ride on the same serialization.

Frontend:

- **`viewsheet-options-pane.component.tl.spec.ts`** — the three controls render, Dark is disabled
  unless Modern, Density offers Default plus the three tiers, and the save payload carries only what
  moved.
- A **`*.spec.ts`** for the confirm-on-downgrade branch: Modern to Legacy prompts, Legacy to Modern
  does not, density alone does not.
- **`vs-object-container` / `editable-object-container`** — the wrapper carries `viz-density-<mode>`
  from the model, alongside the `viz-modern` assertions already there.
- **`gui-tool.spec.ts`** — the `vizDensityMode()` cases go with the method if it is deleted.

Manual, because no automated gate covers them: two composer tabs at different densities; an embedded
viewsheet adopting the host's density; the embed web component honouring density through the Shadow
DOM for the first time; a PDF/PNG export agreeing with the screen.

---

## Open items for implementation

1. **Audit the reparented-overlay list** for D2 — which `body`-appended surfaces owned by a dashboard
   assembly need a per-assembly dark or density class. Assumed small; not verified.
2. **Audit every `.viz-modern` binding site** for Section 3 — confirm each one's model carries a
   resolved density, so nothing silently falls to the dense fallback.
3. **Confirm the localisation keys** for the three new labels and the Density options.
4. **Decide whether `GuiTool.vizDensityMode()` is deleted or re-pointed**; it currently has no
   production callers.

## Relationship to the roadmap

`chart-card-roadmap.md` is the entry point for the modern-visualization track and its release gate is
empty. This is additive work on top of it and does not change anything that file records as shipped.
The one shipped behaviour it generalises is `VizModernizeUtil`'s pair of one-way operations, which
become callers of `applyMark`.
