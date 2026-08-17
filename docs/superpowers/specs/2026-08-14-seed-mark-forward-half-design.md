# Seed Mark — Forward Half — Design

**Date:** 2026-08-14
**Verified against:** community `viz-updates` @ `952614aa7`, which is `HEAD`. Every file and line cited below was
read at that commit.
**Covers:** item M in [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) — the per-assembly provenance
mark, the re-keying of the modern read paths onto it, the per-assembly browser scope, and the Modernize
action. This is the half of M that unblocks the six items behind it.
**Governed by:** [seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md).
That file holds the product decisions and is the authority on *what*; this file is the authority on *how*, and
records the two places the decisions needed refining to be implementable.
**Does not supersede:** `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the decisions file
already does that. §03's tri-state mark, clone-based reversal and automatic forward re-seed are all absent
here by decision, not by oversight.

---

## Scope

**In scope — the forward half.** The mark exists, is written at creation, travels with copies and imports, and
every modern read path consults it instead of the org gate. Existing dashboards have one route in: a manual
Modernize action.

**Out of scope, deliberately, and still gating the release:** the org-wide revert sweep (decisions 6 and 7)
with its restore point, scheduler blocking and composer-session blocking; bookmark resolve-on-restore
(decision 10); deleting the four old reversibility mechanisms (decision 12); and the card radius 12px → 6px.
None of those gate the six downstream items. All of them gate release, because the decisions file's write-off
of the pre-mark cohort holds only while `viz-updates` is unreleased.

**The premise this rests on, stated because it is easy to mistake for technical debt.** Older assets have no
mark and never will. Decision 2 makes *unmarked = never touched by any automatic behaviour, forever* — it is
the product rule, not a migration left undone. Retro-marking on load and a one-off migration task were both
considered and rejected in the decisions file. There is no backfill phase in this design because there is no
backfill phase in the product.

---

## The model in one page

1. A **new viewsheet** created while the gate is on is marked. A new assembly takes the **host viewsheet's**
   mark. Copies, pastes, embedded sheets and imported assets keep **their own** mark.
2. **Creation-time seeding reads the mark just stamped on the assembly**, never the gate. One consultation of
   the gate, at stamp time.
3. **Every read path resolves against the assembly's mark** — server resolvers, export painters, and the
   browser CSS scope alike.
4. **Unmarked content renders exactly as it does today**, under every gate setting.
5. **Modernize** is the only way an existing dashboard becomes modern: composer-only, per-dashboard,
   gate-on only, write permission required, one undo step, no bulk path.
6. **During the forward half only**, the read predicate carries an extra `gate &&` term, so an admin turning
   the gate off still gets legacy read-time chrome back. The revert sweep deletes that term.

---

## 1 · The mark

### Shape

`VizMark` — an enum with two values, `MODERN_LIGHT` and `MODERN_DARK`, held in a nullable field on
`VSAssemblyInfo`. `null` is the third state, *unmarked*. Persisted through the existing
`writeAttributes` (`VSAssemblyInfo.java:849,877-879`) / `parseAttributes` (`:887,927`) pair as one
`vizMark` attribute, omitted entirely when null.

Two values rather than one boolean because the dark bit is load-bearing: decision 9 keys the dark resolvers
off the mark so that unchecking dark mode cannot strand a persisted dark card background under light
read-time chrome, and decision 3 requires a dark-marked assembly pasted into a light dashboard to stay
internally consistent. Density is **not** in the mark — see §2.

`clone(boolean shallow)` needs no change: `super.clone()` (`:1123`) copies the enum reference.

The mark stays out of every `equals()`. Provenance must never become a change-detection trigger — the same
reasoning that kept `userTitleHeight` out of `TitleInfo.equals()` until strip-and-lane decision 7 had a
specific reason to put it in.

### The parse trap, and the rule that closes it

`ViewsheetVSAssemblyInfo`'s **constructor** calls `initDefaultFormat()` (`:47`), and `new Viewsheet()` runs
inside `RuntimeViewsheet.loadXml(new Viewsheet(), …)` (`RuntimeViewsheet.java:157,167,177,267`). So the
page-background seed at `ViewsheetVSAssemblyInfo:238` already executes on **every load of every legacy
viewsheet**, before parse overwrites the format from XML.

Any stamp reachable from that path would therefore be written on load and, finding no `vizMark` attribute in
the file, survive parse — silently retro-marking every dashboard ever saved. That is exactly the failure
decision 2 exists to prevent, and it would be invisible until the first gate-off sweep reverted content
nobody had opted in.

**Rule: `parseAttributes` clears the mark when the attribute is absent, unconditionally.** Not a
derive-on-parse like `userTitleHeight` — the opposite. Absent means unmarked and there is nothing to infer it
from. This is defensive rather than merely correct: it holds no matter which constructor paths later start or
stop running `initDefaultFormat`.

Of the three `initDefaultFormat()` call sites that looked like they might defeat this rule, only one is a
constructor: `ViewsheetVSAssemblyInfo:41-48`, which the rule is written for. `Viewsheet.java:2397` is
`Viewsheet`'s own override delegating to its info, not a construction. `Viewsheet.getWarningTextAssembly`
builds the sheet's warning label outside any funnel, and the per-assembly constructor stamp below covers it
without a special case.

### Where the mark is written

| Site | Value written |
|---|---|
| Any new viewsheet — `ViewsheetVSAssemblyInfo`'s constructor (`:46`) | `VizMark.fromGate()` |
| Any new assembly — `AbstractVSAssembly(Viewsheet, String)`'s constructor (`:128-136`) | the host viewsheet's mark, `null` included |
| `copyInfo` / `copyViewInfo` (`VSAssemblyInfo.java:561,622-625`) | carried from the source info |
| A type conversion — `ComposerRangeSliderService:129`, `ComposerVSSelectionListService:249` | the converted object's **own** mark, overriding host inheritance |

**Assembly creation has no single funnel, and assuming it did was this design's most expensive error.**
`VSEventUtil.createVSAssembly` looked like one; it is not. Dozens of sites construct assemblies directly,
spread across `core/src/main/java/inetsoft/web/`, the export and report-conversion code, and
`VSEventUtil` itself — dragging a field onto the canvas (`VSTableService.createSelectionVSAssembly`),
accepting a wizard recommendation (`VSWizardBindingHandler`), grouping two objects into a tab
(`GroupingService`), and more. Stamping at a funnel reached only some of them. The constructor reaches
all of them, plus any added later, because every intermediate abstract class chains through
`super(vs, name)`.

**Not `initDefaultFormat()`, though it looks like the ideal chokepoint.** Every one of those sites calls it,
but it conflates creation with format *reset*: `ComposerObjectService.java:763-771` re-inits an existing
assembly's format after it leaves a container. A stamp there would overwrite that assembly's mark with its
host's, so a modern assembly pasted into a legacy dashboard would lose its provenance the first time a user
dragged it out of a selection container — precisely the loss decision 3 exists to prevent.

**The `copyViewInfo` row is not bookkeeping.** `VSObjectPropertyService.isTableModeChange` (`~:1832`) and
`ComposerVSTableService.convertToFreehandTable` build a replacement assembly and copy the old info onto it.
Because the copy carries the mark, both satisfy the type-conversion rule with no code of their own — the two
mechanisms agree rather than competing.

**Copy, paste and import need no code.** They go through the parse funnel
(`AbstractVSAssembly.createVSAssembly(Element, Viewsheet, boolean)`, `AbstractVSAssembly.java:60-83`), which
constructs via the **no-arg** constructor and attaches the viewsheet afterwards, so neither constructor stamp
can fire. Marks travel in the asset XML. Decision 3's copy row and decision 8 in full are satisfied by
construction.

### The ordering constraint

The stamp must be written **before** `initDefaultFormat()` runs, so the creation seeds resolve against the
freshly-stamped mark rather than against the gate. Both stamps satisfy this for free by living in
constructors: an assembly's mark is set in `AbstractVSAssembly`'s two-arg constructor, and a viewsheet's in
`ViewsheetVSAssemblyInfo`'s, each before any caller can reach `initDefaultFormat()`.

Once that holds, `setDefaultFormat` never consults the gate at all: all four persisted seeds —
`objectBorderColor` (`VSAssemblyInfo.java:1191-1192` → `:1222`), the card radius (`:1195-1196` → `:1223`),
`cardBackgroundCss` (`ChartVSAssemblyInfo.java:89`, `TableDataVSAssemblyInfo.java:1578`) and
`pageBackgroundCss` (`ViewsheetVSAssemblyInfo.java:238`) — plus `barCornerRadius`/`smoothLines`
(`ChartVSAssemblyInfo.java:94-100`) go through one context. That is what lets creation and Modernize share a
single code path in §4.

### The sheet stamps itself in its constructor

`ViewsheetVSAssemblyInfo`'s constructor already calls `initDefaultFormat()`. The stamp goes immediately
before that call, so the page-background seed resolves against the mark on the first and only init, and no
second init is needed. It covers every route to a new viewsheet without enumerating them. It is safe only
because the parse clears the field: `Viewsheet.parseXML(Element, boolean)` clears the mark unconditionally at
`:4211`, before its own conditional parse of the sheet's `assemblyInfo` node, so a loaded sheet ends up with
whatever its file says, never the constructor's stamp.

**Rejected — removing the constructor's `initDefaultFormat()` call and placing it only at creation sites.**
It changes what a parsed legacy sheet falls back to when its XML carries no background value, which is a
rendering change to every saved dashboard in exchange for tidiness.

---

## 2 · `VizContext` and the re-keying

### The problem

The mark lives on `VSAssemblyInfo`. Every modern value is resolved by a **static** method on one of eight
`VS*Defaults` classes that reads `SreeEnv` directly, across roughly 115 call sites. Decision 4 requires all of
them to ask the assembly instead.

### Shape

`VizContext` — immutable, three fields: `modern`, `dark`, `density`. Four factories:

| Factory | Use |
|---|---|
| `of(VSAssemblyInfo)` | the normal case; reads the mark |
| `of(VizMark)` | where only the mark is at hand |
| `ofGate()` | the genuinely org-level shell paths, and the transitional state in phase P2 |
| `LEGACY` | report charts, which have no viewsheet and no mark |

Every `VS*Defaults` method takes it as its first parameter; the `SreeEnv` reads move into `ofGate()`.

**Density is not in the mark, deliberately.** The mark stores the modern/dark tuple only (decision 1).
Density is a live org preference: changing it should reflow marked assemblies and leave unmarked ones alone.
So `of(info)` takes `modern` and `dark` from the mark and `density` from `SreeEnv`. Decision 4's "the density
heights key off the mark" means *whether density applies at all*, not which mode is in force — it reads
ambiguously and this is the reading.

### The interim term

`of(info)` computes `modern = gate && mark != null`.

That `gate &&` is the whole of the forward half's reversibility story, and deleting it is a one-line change
when the sweep lands. What it buys is narrow and worth stating exactly: its **only** effect is on a marked
assembly in a gate-off product, where it restores today's behaviour — read-time chrome goes legacy, so an
admin can still turn the feature off and mostly get the old look back. Without it and without the sweep,
flipping the gate off would leave everything modern with no route back at all.

What it does **not** buy: the three persisted colour seeds are stored values read straight off the DEFAULT
tier, and no read predicate touches them. A marked assembly in a gate-off product keeps its modern border and
card colour either way. That is already true on `viz-updates` today — it is the decisions file's "the gate
seeds four values and exactly one is reversible" — so the forward half neither fixes nor worsens it. The
sweep fixes it.

The end state after the term is deleted is coherent: **the gate becomes purely a creation-time switch plus
the sweep trigger**, which is already how decision 9 describes the dark toggle.

`resolveSeededCorner` (`VSObjectChromeDefaults.java:79-81`), its tab carve-out
(`VSCompositeFormat.java:334-337`) and both `PlotDescriptor` seed booleans stay untouched. Decision 12
forbids deleting them before the mark is verified, and they are what keeps the radius reversible in the
meantime. **`VSCompositeFormat` therefore needs no changes in this scope at all** — which removes the
granularity problem the external ticket spends a section on.

### The four sub-gate properties — deleted in P2

**Decided 2026-08-14.** Four properties currently switch modern facets independently of the master gate, and
P2 deletes all four. `VizContext` keeps the three fields specified above.

`VSObjectChromeDefaults.isModern()` is `VSDensityDefaults.isModern() && viewsheet.modernObjectChrome !=
"false"`, and three siblings have the same shape: `viewsheet.modernChartChrome` (`VSChartChromeDefaults`),
`viewsheet.modernChartPalette` (`VSChartPaletteDefaults`) and `viewsheet.modernTableStructure`
(`VSTableStructureDefaults`). `viewsheet.modernObjectChrome` itself is shared by three classes —
`VSObjectChromeDefaults`, `VSTitleChromeDefaults` and `VSOutputChromeDefaults` — so it is four properties
guarding six classes. `VSCalendarChromeDefaults` has no `isModern()` at all and guards on `isDark()` alone.

**Why they go.** They were rollout scaffolding: they de-risked building the facets one phase at a time. All
six classes have shipped, so the per-facet switches have served their purpose. They are also undocumented,
absent from EM's Look and Feel pane, unwritten by `LookAndFeelService`, referenced nowhere outside the six
classes that read them, and one of them — `modernTableStructure` — has no test at all.

**What the deletion buys, beyond a simpler type.** With the sub-gates gone, "marked but unseeded" becomes
unreachable: a marked assembly is always seeded, because master-on-facet-off no longer exists. P3's
Modernize, P4's read flip and the revert sweep can all rely on that, and several later-phase claims already
did.

**The mechanics, and why they cost P2 nothing extra.** All six `isModern()` methods are structurally
identical, differing only in which property they read, so stripping the sub-gate term makes all six literally
`return VSDensityDefaults.isModern();`. They are then deleted rather than left as delegates, and each of the
~115 call sites reads `ctx.modern` instead of `X.isModern()` — the same edit P2 was already making at that
site, not an additional one.

**Zero behaviour change, and that is checkable.** All four default *on* — the predicate is
`!"false".equals(...)`, so absent means enabled — and `viz-updates` has never shipped, so no deployment can
have set one to `false`. Deleting them cannot alter what any dashboard renders.

**Test fallout: two tests and four lines.** `VSOutputChromeDefaultsTest:85-86` (`"modernObjectChrome=false
opts out"`) and `VSChartPaletteDefaultsTest.subGateFalseOptsOutEvenWhenBaseGateOn` *are* the escape hatch and
are deleted with it. Four `setProperty(…, null)` reset lines lose their subject:
`VSOutputChromeDefaultsTest:50`, `VSChartChromeDefaultsTest:27`, `VSChartPaletteDefaultsTest:31`,
`VGraphPairModernPaletteTest:47`.

**Explicitly not deleted: `graph.svg.inline`.** `VSChartInteractionDefaults.isInlineSvg()` looks adjacent and
is a different mechanism — an explicit value wins in either direction and only its absence falls back to
`isModern()`, so it can enable inline SVG *without* the master gate, which no sub-gate can. Its own comment
records that this is deliberate. It is a real toggle set through EM Properties, not scaffolding.

### The surface, sized for slicing

| Class | Sites | Reach |
|---|---|---|
| `VSChartChromeDefaults` | 38 | five descriptors via the existing `initDefaultFormat(boolean vs)` slot; the largest cluster is `web/composer/model/vs/ChartLinePaneModel.java` (10), with `web/graph/model/dialog/LegendFormatDialogModel.java` and `AxisPropertyDialogModel.java` alongside; `VGraphPair` holds about 4, `GraphTarget` holds none |
| `VSTitleChromeDefaults` | 19 | model builders and exporters, info in hand |
| `VSTableStructureDefaults` | 14 | all in `DataVSAQuery` |
| `VSObjectChromeDefaults` | 14 | creation seeds, `VSSlider`, `VSSliderModel` |
| `VSDensityDefaults` | 14 | `VSTableLens` (has `tinfo`), `BaseTableService`, `SelectionBaseVSAssemblyInfo`, `AbstractChartInfo` |
| `VSOutputChromeDefaults` | 7 | painter and models |
| `VSChartPaletteDefaults` | 4 | `VGraphPair`, `ChangeChartProcessor`, `CSSProcessor` |
| `VSCalendarChromeDefaults` · `VSChartInteractionDefaults` | 5 | painter, model, `CoreLifecycleService` |

Three things this table is for.

**The chart descriptors already thread the parameter.** `AxisDescriptor:63`, `ChartRefImpl:56`,
`LegendDescriptor:69`, `LegendsDescriptor:95` and `TitleDescriptor:65` all take
`initDefaultFormat(boolean vs)`, where `vs` means "this is a viewsheet chart." It becomes
`initDefaultFormat(VizContext)`, with `LEGACY` where `false` is passed today. `VGraphPair` re-runs these on
roughly 30 lines every render (`VGraphPair.java:1026-1359`) and holds the chart assembly, so the chart
interior needs no plumbing invented for it.

**One genuine gap.** `CSSProcessor.applyCSS(ChartInfo, CSSDictionary, List<CSSParameter>)`
(`CSSProcessor.java:384`) has no route to an assembly. Its callers decide whether a context can be threaded
from above or whether it takes `ofGate()` with a comment. A plan-time question, not a design one.
`ChangeChartProcessor.updateColorFrameCSSParentParams(ChartVSAssemblyInfo, VSChartInfo)`
(`ChangeChartProcessor.java:1869`) and `CSSChartStyles.apply(…)` (`CSSChartStyles.java:89`) are both reached
with the assembly in hand.

**A related open question.** `VSChartChromeDefaults`'s biggest cluster is composer *dialog* models
(`ChartLinePaneModel`, `LegendFormatDialogModel`, `AxisPropertyDialogModel`), which may be reached without an
assembly in hand. Whether they can thread a context or need `ofGate()` too is open; the design does not
otherwise address it.

### Rejected alternatives

**A `ThreadLocal` ambient scope** — `VizContext.with(info, () -> …)` at model build, painter entry and
`VGraphPair.render`, leaving the call sites untouched. Roughly 15 edits instead of 115. Rejected on failure
mode: a path that forgets to open a scope reads the org gate, which is *precisely today's wrong behaviour*, so
a missed site renders plausibly and wrong instead of failing to compile. `VGraphPair` renders on parallel
threads and export runs in scheduler threads — the two places a leak bites hardest and reproduces least.
Bounding it would need an audit that costs back most of the saving.

**Resolving at stamp time and persisting the values** — write the density- and dark-derived values into the
info when the mark is written, so read paths need no context. This is the direction decision 4 moves away
from: it persists more, not less, and re-introduces exactly the version-blindness the mark was designed to
make irrelevant.

---

## 3 · The browser scope

Today `viz-modern`, `viz-dark` and `viz-density-*` are toggled on `document.body` by three shells
(`viewer-app.component.ts:2790-2798`, `portal/app.component.ts:271`, `composer/app.component.ts:144`), and
`GuiTool.isVizModern()` reads the class back off the body (`gui-tool.ts:67`).

**The CSS half is a rename, not a rewrite.** `.vs-object-parent-container`
(`vs-object-container.component.html:41`) is already a per-assembly wrapper `<div>` enclosing every assembly
component. Binding `viz-modern`/`viz-dark` there from the model's mark makes every existing
`:host-context(.viz-modern)` and `.viz-modern <descendant>` rule per-assembly with **zero selector edits** —
`:host-context` matches any ancestor.

What must change is the body class, renamed (`viz-shell` / `viz-shell-dark`) for the genuinely org-level
surfaces so it stops matching assembly-scoped rules. Roughly 35 occurrences across nine stylesheet and
template files, of which `_viz-tokens.scss` (15) and `_themeable.scss` (9) are most of the count.

**Density stays on the body.** It is an org preference; the mark only decides whether an assembly honours it.

**The TS half is 17 call sites** of `GuiTool.isVizModern()` / `isVizDensityAtLeastCompact()` across nine
files — `chart-actions.ts` (4), `abstract-vs-actions.ts` (3), `mini-toolbar.service.ts` (2), `chart-tool.ts`
(2), and one each in `table-actions.ts`, `crosstab-actions.ts`, `calc-table-actions.ts`,
`date-tip-helper.ts`, `highlight-pane.component.ts`, `tooltip-tail-placement.ts`. All nine already hold the
assembly model.

**The mark reaches the browser on `VSObjectModel`** — one field on the base class (`:559` onward), populated
in the constructor that already receives the info (`VSObjectModel(T assembly, RuntimeViewsheet rvs)`), and
inherited by every assembly model. Data tips and tooltips are appended to `body` rather than inside the
wrapper, so they take the class explicitly from their source assembly's model; `date-tip-helper.ts` and
`tooltip-tail-placement.ts` are already among the 17.

**A serialization note for P5.** Jackson serializes `VizMark` by `name()` (`MODERN_LIGHT`), not `value()`
(`modern-light`); the browser needs to key off whichever one actually reaches it, or the enum needs
`@JsonValue` on `value()` — not added here, untested in P1.

**A correction to the roadmap.** It records that "M does not gate F" — true, the toolbar rollout persists
nothing. But decision 4 turns the toolbar's *gate* test into a *mark* test, so shipped slices 1–3 are
**modified** when this lands. M does not block F; M changes F. Named here so it is not met as a regression.

---

## 4 · The enumeration point and Modernize

### Decision 11's virtual method

`setDefaultFormat` keeps building and installing the composite. The default-tier computation moves to
`protected void applyModernDefaults(VSCompositeFormat fmt, VizContext ctx)`, which both creation and
Modernize call, and which the three existing subclass overrides extend —
`ChartVSAssemblyInfo:86`, `TableDataVSAssemblyInfo:1575`, `ViewsheetVSAssemblyInfo:235`, each of which today
calls `super` and then adds its own seeds.

**The contract is the part to get right: it mutates the format it is given and never installs a new
composite.** `setDefaultFormat` does `new VSCompositeFormat()` (`:1185`) → `setFormat(format)` (`:1236`) →
`fmtInfo.setFormat(OBJECTPATH, fmt)`, leaving the new composite's USER tier empty. Harmless at creation;
destroys an existing dashboard's formatting on recompute. This is why "just call `setDefaultFormat` again" is
not the implementation of Modernize.

Two things travel with the extraction:

- **The `format.css` TableStyle branch** (`:1211-1217`) comes along, so Modernize picks up the customer's
  current table style rather than dropping it. Its real scope is narrow — tables only, and only when
  `border` is true.
- **The title-border ordering oddity is preserved deliberately.** The title border colour is set at `:1209`,
  *before* the stylesheet override at `:1211-1217`, so a table's stylesheet colour reaches the object border
  and never the title border. This predates all of this work. A refactor is exactly when someone notices and
  "fixes" it, which would change rendering. Preserve unless someone confirms it is a bug.

**Rejected — a static helper alone, with no virtual method.** Seeding is already polymorphic across three
subclasses, so a static helper would need either an `instanceof` switch (a second copy of the type knowledge
`isCornerSeedTarget()` already holds at `VSAssemblyInfo.java:1261`) or a convention that each subclass calls
the helper and then adds its own values — which is the drift being prevented. The static helper still exists,
holding the constants and shared computation.

### Modernize

Per-dashboard, composer-only, gate-on only, write permission required, applying modern defaults wholesale to
**unmarked** assemblies and stamping them, as one `@Undoable` composer step (decision 5). Because it routes
through `applyModernDefaults`, it is close to: *for each unmarked assembly — stamp it, then run the
computation.*

**Author-provenance flags are not touched.** `userTitleHeight`, `userDataRowHeight`, `userHeaderRowHeight`
and `userCellHeight` record that a person set a value, which the mark never knows. The decisions file's
addendum is explicit that they survive the mark; "wholesale" invites the opposite reading, so it is stated
here too.

**Idempotent by construction** — it only touches unmarked assemblies, so a second run is a no-op.

### The affordance — a bar, dismissable per composer session

Decided 2026-08-14, refining decision 5, which specified the behaviour but not the surface.

**Composer only.** Never the viewer, and never composer preview, which is a viewer. This follows decision 5's
own rationale — modernizing changes chart geometry and row heights, so it always leaves resizing work behind,
and that is authoring — and it removes the viewer-side plumbing entirely.

**A bar, not a menu item, and dismissable for the composer session.** Visible when: the gate is on **and**
the sheet holds at least one unmarked assembly **and** the user has write permission **and** it has not been
dismissed for this open sheet.

**Dismissal state is client-side, on the composer's open-sheet object** (`ComposerMainComponent`'s `sheets[]`
entry) — never on the server, never in the asset. Three consequences, all intended:

- Switching composer tabs away and back keeps it dismissed. The composer hides inactive sheets with
  `display: none` rather than destroying them, so the sheet object survives.
- Closing and reopening the dashboard brings the bar back.
- Dismissing never dirties the sheet and never triggers autosave — which matters, because the dashboard being
  offered modernization is a legacy asset this design must not write to.

**Visibility is recomputed from the model, not latched**, so the bar disappears when Modernize completes and
reappears if the user undoes it, for free.

**A permanent menu entry accompanies the bar.** The bar is the discovery affordance; without a second route,
dismissing it costs the feature until the dashboard is closed and reopened. The action must be **absent, not
disabled**, where nothing is unmarked — otherwise every fully-modern dashboard carries a permanently greyed
menu item.

---

## 5 · Phases

Six phases. The ordering principle is that **the mechanical cost and the behaviour change never land in the
same commit** — that is what makes each phase reviewable and each phase's verification a claim that can
actually be checked.

### P0 — discard the pre-mark cohort

Not code. Delete the dev and test dashboards created on `viz-updates` with the gate on. They carry seeded
modern values with no mark; under decision 2 they are never touched again, so leaving them in place means
every later phase is verified against content that is permanently half-and-half. The roadmap already argues
for doing this before the mark rather than after, and it is the cheapest it will ever be.

### P1 — the field

`VizMark`, the nullable field, `writeAttributes`/`parseAttributes` with the clear-on-absent rule, the
`copyViewInfo` carry, and the two constructor stamps — a sheet's own from the gate, an assembly's from its
host — plus the type-conversion exception that keeps a converted object's own mark. **Nothing reads it.**

*Verification:* unit-level — persistence round-trip; absent attribute clears a stamped field; `copyInfo`
carries; a new assembly inherits the host's mark; change-object-type and type conversion both preserve the
source's mark; a gate-off creation stamps nothing; a format reset on an existing assembly never clears its
mark. Plus one manual check that a legacy dashboard opens, saves and re-opens with no
`vizMark` attribute anywhere in its XML. Zero visual change. This is the shape `userTitleHeight` shipped in
at `07c91926e` — "nothing reads the flag yet."

### P2 — `VizContext`, threaded, behaviour-identical

Introduce the class. Widen all eight `VS*Defaults` classes. Every one of the ~115 call sites passes
`ofGate()`. The five chart descriptors' `initDefaultFormat(boolean vs)` becomes `(VizContext)`.

`ofGate()` reads exactly what the statics read today, so this is a no-op by construction.

*Verification:* the whole existing suite green — the portal action specs, unit specs and TL specs, plus the
`core` Java tests; the roadmap's counts at `55c3bad1a` were 255 / 83 / 60, so re-count rather than assume —
and a visual spot-check that a gate-on dashboard is unchanged. A weak claim about correctness and a strong one
about risk, which is why the 115 edits belong in a commit of their own.

### P3 — the enumeration point and Modernize

Extract `applyModernDefaults(fmt, ctx)`; move the three subclass overrides' seeds into it; route creation
through it; build the action, the bar and the menu entry. Reads are still `ofGate()`, so nobody's rendering
changes unless they press the button.

*Verification:* the claim is sharp enough to assert directly — **Modernize on a legacy assembly produces the
same persisted values as a freshly created assembly of the same type**, checked by comparing the two infos in
a test, per type, in light and dark. Plus: idempotence; the bar's four visibility conditions; dismissal
surviving a tab switch and not surviving a reopen; one undo step restoring the unmarked state and the bar
with it; a mixed dashboard stamping only its unmarked assemblies (decision 3).

P3 comes before the flip rather than after because it is what makes the flip testable: without Modernize
there is no marked cohort to compare against, and every scenario needs a hand-built assembly.

### P4 — flip the server reads to the mark

`ofGate()` → `of(info)` across the eight classes, plus the `gate &&` interim term. **This is the behaviour
reversal**, and it is where dev dashboards go legacy.

*Verification:* an unmarked dashboard under gate-on renders legacy; a Modernized copy of it renders modern;
export agrees with view for both; the gate off reverts read-time chrome on marked assemblies; dark off no
longer strands anything, which closes decision 9's reproducible defect. A manual export spot-check across
PDF, PNG and Excel — the chrome colours move even though no geometry does.

### P5 — flip the browser reads to the mark

`VSObjectModel` field; per-assembly `viz-modern`/`viz-dark` on `.vs-object-parent-container`; the body class
renamed for the org-level surfaces; the 17 `GuiTool` sites take the model's mark.

*Verification:* the same two dashboards are consistent end to end; a marked assembly pasted into an unmarked
sheet renders modern beside legacy siblings (decision 3's mixed dashboard); shipped toolbar slices 1–3
behave per-assembly rather than per-org.

### The one interim state worth declaring

**P4 and P5 should land back-to-back, and the commit message should say why.** Between them, an unmarked
dashboard under gate-on has legacy server chrome under modern browser CSS. That is bounded, understood, and
the same class of mismatch the branch carries today — but it is a real intermediate state and eliding it in
review would be dishonest. Splitting them is still right: 115 server edits and ~50 browser edits in one commit
is not reviewable. Server first, so the last commit is the one that makes it coherent.

---

## Refinements this design makes to the decisions file

Two, both because the decision as written had no implementable form.

**Decision 3's host mark is the sheet's own mark.** The decisions file says a dashboard's modern-ness is
"derived — every assembly in it is marked," which has no answer for a newly created empty sheet, and the
sheet already carries a persisted seed of its own (the page background, `ViewsheetVSAssemblyInfo:238`). So
the sheet's `ViewsheetVSAssemblyInfo` carries a real mark, stamped from the gate at creation, and that is the
host mark new assemblies inherit.

**Decision 4's "density heights key off the mark" means whether, not which.** Density stays a live org
preference read from `SreeEnv`; the mark decides whether an assembly honours it. See §2.

---

## Accepted costs

**The pre-mark cohort is written off**, per the decisions file, and P0 disposes of it rather than inheriting
it. The write-off holds only while `viz-updates` is unreleased.

**The three persisted colour seeds stay modern in a gate-off product.** Pre-existing, unchanged by this
design, fixed by the sweep. See §2.

**The gate-off state is only partly reverted until the sweep lands.** Read-time chrome reverts via the
`gate &&` term; the persisted seeds do not. This is the status quo, not a regression.

---

## Open items to settle while writing the plans

Small enough to answer in the plan rather than before it.

1. **`CSSProcessor.applyCSS`'s context.** Threaded from its callers, or `ofGate()` with a comment.
2. ~~**The composer's new-viewsheet funnel.**~~ Closed: stamping in `ViewsheetVSAssemblyInfo`'s constructor
   covers every construction path, so no funnel needs locating.
3. ~~**The three legacy `initDefaultFormat()` call sites.**~~ Closed: only one is a constructor, and the
   per-assembly constructor stamp covers the other creation path without a special case. See §1.
4. **Where the Modernize bar sits in the composer chrome**, and whether an existing banner mechanism already
   exists to host it.
5. **The `VizContext` name.** It carries the mark plus density, not just the mark; if a better name appears
   while writing, take it — this is the cheapest moment.

---

## What this unblocks

When P5 lands: **L′** the title lane height row (which needs both the mark and `userTitleHeight` — the mark
says *is this assembly modern*, the flag says *did an author choose this height*), **L″** behind it, §07
derived selection and the retirement of the teal family, the range slider's painter half, and the outlined
text conversion. The card radius 12px → 6px additionally needs the sweep, because retiring
`resolveSeededCorner` without it leaves no reversal path.

What it does **not** clear is the release gate. That needs the sweep, the bookmark path and the deletion of
the four old mechanisms.

---

## Related

- [seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md) — the twelve
  product decisions this implements, and the addendum on what `userTitleHeight` changed
- [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) — where M sits in the dependency picture
- [chart-card-anchored-strip-lane-decisions.md](./lookfeel/chart-card-anchored-strip-lane-decisions.md) —
  decision 8 schedules L′ after the mark; decision 7 is the `TitleInfo.equals()` precedent
- `chart-card-design3/Seeded value reversibility - ticket.md` — the external ticket. Its central worry,
  version-blindness, needs no schema version (reversal is not subtractive), and its granularity section is
  moot here: `VSCompositeFormat` is untouched
- `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the mark's original specification, superseded
  by the decisions file
