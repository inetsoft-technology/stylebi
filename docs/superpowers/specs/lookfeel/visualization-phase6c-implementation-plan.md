# StyleBI Visualization — Phase 6C (Smooth Lines Default) — Implementation Plan

## Scope

Phase 6C makes **line charts smooth under modern-visualization mode**, on both paths by which a chart
becomes a line chart:

1. **On creation** — a chart created while the gate is on carries `smoothLines = true`.
2. **On transition** — switching an existing chart to Line while the gate is on sets `smoothLines = true`,
   instead of the current behavior of clearing it.

It reuses Phase 6B's mechanism exactly: a design-time seed under `VSObjectChromeDefaults.isModern()`, plus
a marker so a gate-aware read can revert it when the gate is turned off. A user toggling the Smooth Lines
checkbox clears the marker and owns the value from then on.

### Why this is 6C and not an amendment to 6B

Phase 6B is complete and fully reviewed. Adding a third value to it would invalidate that review for no
benefit. 6C is a separate, smaller pass that depends on 6B only for the established pattern — and, if 6B's
`modernCornerSeed` marker is generalized (see D2), for the marker itself.

## Grounding (verified against current code, 2026-07-29; re-confirm line numbers before editing)

### What `smoothLines` does today

| Family | Control visible? | Current new-chart default | Effect of `true` |
|---|---|---|---|
| Line — `CHART_LINE`, `CHART_LINE_STACK` | yes | **`false`** (straight) | `LineElement.Type.CURVED` (`GraphGenerator:3569`) |
| Area — `CHART_AREA`, `CHART_AREA_STACK` | yes | **`true`** already | `LineElement.Type.CURVED` (`GraphGenerator:3553`) |
| Circular — `CHART_CIRCULAR` | yes | **`true`** already | `setSmoothEdges(true)` — bends chords, not lines (`GraphGenerator:3784`) |
| Step / Jump / Step-area | **hidden** | n/a | ignored; the explicit `STEP`/`JUMP` branch wins first |

Visibility is `ChartPlotOptionsPaneModel.isSmoothLinesVisible():155-162` — line or area, excluding
`CHART_STEP`, `CHART_STEP_STACK`, `CHART_JUMP`, `CHART_STEP_AREA`, `CHART_STEP_AREA_STACK`, plus
`CHART_CIRCULAR`. UI is a checkbox at `chart-plot-options-pane.component.html:235-241`.

### The two existing hooks — both ungated

`PlotDescriptor.smoothLines` is a plain boolean, field default `false` (`:1971`), with the comment
*"Default false so saved viewsheets without the smoothLines XML attribute keep their original straight-line
look."* `parseAttributes:1512` reads `"true".equals(...)`, so an absent attribute yields `false` and saved
charts are unaffected.

Two mechanisms already set it `true`, neither gated:

1. **Wizard** — `VSWizardBindingHandler:866-874`: if the wizard's chart type is `CHART_AREA`,
   `CHART_AREA_STACK` or `CHART_CIRCULAR`, set `true`. Sets the type directly, so no transition fires.
2. **Type transition** — `ChangeChartTypeService.applySmoothLinesTransition:320-333`, called from `:203`:

   | Transition | Current effect |
   |---|---|
   | → area (from non-area), → circular (from non-circular) | `true` |
   | → line, from area or circular | **`false`** |
   | → line, from anything else (e.g. bar) | untouched (so stays `false`) |
   | area → area, circular → circular | preserved (user setting respected) |

**So area and circular are already smooth by default.** The only family a modern default changes is
**line** — and doing so directly contradicts the `→ line sets false` rule, which exists to un-smooth when
the user picks a line chart.

### Accepted trade-off: curved line charts interpolate

Smoothing a line chart draws a curve through the sampled points, so the rendered path passes through values
that were never measured and can overshoot local minima and maxima. Several BI style guides treat smoothing
as acceptable for area fills and decorative chord diagrams but misleading for line charts, which is the most
likely reason the existing code splits precisely along that boundary.

This was raised with the initiative owner, who chose to proceed: line charts smooth under modern, on both
creation and transition. Recorded here as a deliberate decision, not an oversight. Two properties limit the
exposure: the value is a per-chart setting the author can switch off, and the gate reverts it wholesale.

### Dead test class blocking this work

`ChangeChartTypeServiceSmoothLinesTransitionTest` — **14 tests covering exactly the transition matrix this
phase modifies** — carries no `@Tag("core")`, no `@SreeHome` and no Spring context. Two consequences:

- `core/pom.xml:996` sets surefire `<groups>core</groups>`, so the class is silently excluded from every
  run and from CI. Its 14 tests have never executed.
- Its helper calls `new PlotDescriptor()`, and `PlotDescriptor:1969` initializes a field via
  `SreeEnv.getProperty("webmap.default")`, so the class would error on every test if it were collected.

This is the fifth never-executing class found in this initiative, after `PlotDescriptorXmlTest`,
`ChartPlotOptionsPaneModelTest`, `IntervalElementStackOutermostTest` and `GraphTypeUtilCheckTypeTest` —
together 83 tests that read as coverage and ran zero times. (`VSObjectChromeDefaultsTest` was a different
defect: it ran, but could not instantiate the table types until `LibManagerTestConfiguration` was added.)
**Repair it first** — changing an un-executed transition matrix with no working test is the riskiest
possible order of operations.

## Decisions

### D1 — Reuse Phase 6B's seed + gate-aware-read mechanism → **DECIDED**

| | Existing charts | Charts created or switched to Line under the gate |
|---|---|---|
| Gate ON | unchanged | smooth |
| Gate OFF | unchanged | revert to straight |
| User toggled the checkbox | honored | honored, in both gate states |

Same shape as 6B's bar radius, for the same reason: the gate must stay an escape hatch rather than a
one-way door.

### D2 — A second, independent marker: `modernSmoothSeed` → **DECIDED**

6B added `PlotDescriptor.modernCornerSeed` for `barCornerRadius`. `smoothLines` gets its own flag,
`modernSmoothSeed`, rather than sharing it.

Rejected alternative — generalizing `modernCornerSeed` to mark both values. That would save one serialized
attribute but couple the two: bar radius applies to bar / pareto / waterfall / gantt / interval, smooth lines
to line / area / circular. They are disjoint families that are never edited together, so a shared marker
would let an edit to one silently change whether the *other* still tracks the gate. That is a correctness
defect, not a cosmetic one.

Consequences: `modernCornerSeed` keeps its 6B name and scope untouched — no rename, so 6B's reviewed code and
its XML attribute are unchanged. `PlotDescriptor` ends up with two independent gate markers, which is the
intended shape; if a third gated value is ever added it takes a third flag on the same pattern.

### D3 — Creation seam: seed type-agnostically at assembly creation → **DECIDED**

Seed in `ChartVSAssemblyInfo.setDefaultFormat()`, beside 6B's bar-radius seed. The chart type is not yet
known there (the constructor sets `cinfo = new DefaultVSChartInfo()`), so the seed is type-agnostic. That is
acceptable and in fact desirable:

- Any chart that later becomes line, area or circular is smooth without needing a per-path hook.
- For every other family the value is inert — `GraphGenerator` reads it only in the area, line and circular
  branches, and the step/jump branches take precedence.

Accepted cost: a chart that ends up as a bar chart still carries `smoothLines="true"` in its saved XML.
Functionally inert, but it is noise, and it means the attribute no longer implies "this chart is smooth".

This also covers the wizard path with no change to `VSWizardBindingHandler` — its hook only ever sets
`true`, so it cannot undo the seed.

### D4 — Transition: under the gate, → line sets `true` → **DECIDED**

`applySmoothLinesTransition` must become gate-aware. Under the gate, the `→ line` branch sets `true` (with
the marker) instead of `false`. It must **set**, not merely skip the reset, because the owner's requirement
covers a *saved* chart being switched to Line — that chart has `smoothLines = false` and no marker, so
skipping the reset would leave it straight.

Gate off, behavior is byte-identical to today.

This inverts two currently-passing (but never-executed) tests under the gate:
`areaToLine_resetsSmoothLinesFalse` and `circularToLine_resetsSmoothLinesFalse`. Both must gain a gate-on
counterpart rather than being edited in place — the gate-off assertions stay exactly as they are.

### D5 — Area and circular are left alone → **DECIDED**

They already default to smooth via the wizard and transition hooks, ungated. Bringing them under the gate
would make new area charts straight in non-modern orgs — a regression for no benefit. Do not touch either
hook's area/circular branches.

### D6 — The script setter must clear the marker → **DECIDED**

`ChartProcessor:242-243` exposes `smoothLines` with `isSmoothLines`/`setSmoothLines`. This is the identical
read-effective/write-raw asymmetry that shipped as a defect in 6B and was fixed there: if the getter becomes
gate-aware and the setter leaves the marker set, then `chart.smoothLines = true` on a seeded chart in a
gate-off org stores `true` while the getter still returns `false` — silently ignored.

`setSmoothLines()` must clear the marker, exactly as `setBarCornerRadius()` now does. Verify the ordering at
every call site first: a caller that sets the marker *before* calling the setter would have its seed
destroyed. 6B's three call sites were all marker-after-setter; re-check for this value.

### D7 — Step and jump variants stay straight → **DECIDED**

No change needed. `GraphGenerator:3550` and `:3565` test the step/jump types before consulting
`smoothLines`, and `isSmoothLinesVisible` hides the control for them. A seeded `true` is inert there.

## Changes

1. **Repair `ChangeChartTypeServiceSmoothLinesTransitionTest`** — add `@Tag("core")` plus the full Spring
   harness matching `PlotDescriptorTextLayoutTest:21-26`. Confirm all 14 tests execute and pass *before*
   touching the matrix. Report any that fail: those are latent defects this repair exposes.
2. **`PlotDescriptor`** — add the marker (D2), make `isSmoothLines()` gate-aware, add a raw
   `isSmoothLinesValue()` for serialization and `equalsContent()`, clear the marker in `setSmoothLines()`
   (D6), and wire the marker through `parseAttributes`/`writeAttributes`/`equalsContent`. Field default stays
   `false`.
3. **`ChartVSAssemblyInfo.setDefaultFormat()`** — seed `setSmoothLines(true)` + marker under
   `VSObjectChromeDefaults.isModern()`, beside 6B's bar-radius seed.
4. **`ChangeChartTypeService.applySmoothLinesTransition()`** — make gate-aware per D4. Keep the method
   package-private and pure so it stays unit-testable; pass the gate state in as a parameter rather than
   reading `VSObjectChromeDefaults` inside it, so the tests need no Spring context for the matrix itself.
5. **`ChartPlotOptionsPaneModel`** — the checkbox round-trip needs the same no-op guard 6B added for the bar
   radius: `:199` writes `plotDesc.setSmoothLines(smoothLines)` unconditionally on every dialog apply, and
   `:314` reads the now-gate-aware getter, so a no-op OK would clear the marker. Guard on actual change.
6. **Tests** — gate-on counterparts for the two inverted transitions, a creation-seed test, a
   gate-off-reverts test, a marker-cleared-on-explicit-set test, and a no-op-dialog-save test.

## Validation

- Gate off: byte-identical to today, including all 14 existing transition assertions.
- Gate on, new chart → Line: smooth. Gate on, saved bar chart switched to Line: smooth.
- Gate on → off: charts seeded either way revert to straight; a chart whose checkbox the user set stays as
  the user left it.
- Area and circular unchanged in both gate states.
- Step, jump and step-area unchanged and still hiding the control.
- Script: `chart.smoothLines = true` takes effect in both gate states.
- Saved-sheet parity: a chart saved before this phase loads straight in both gate states
  (`parseAttributes` yields marker `false`, `smoothLines` `false`).

## Known risk

The transition matrix has 14 test cases and has never executed. Change 1 exists to close that gap before
change 4 touches it. If change 1 surfaces failures, stop and reassess before proceeding — a matrix that was
already wrong is a different problem from the one this phase is solving.

## Branching

Community-only, same as 6B. No enterprise-side change.

## Related

- [visualization-phase6b-implementation-plan.md](visualization-phase6b-implementation-plan.md) — the
  seed + gate-aware-read mechanism and the `modernCornerSeed` marker this phase reuses
- [visualization-implementation-roadmap.md](visualization-implementation-roadmap.md) — Phase 6C
- [visualization-design-spec.md](visualization-design-spec.md)
