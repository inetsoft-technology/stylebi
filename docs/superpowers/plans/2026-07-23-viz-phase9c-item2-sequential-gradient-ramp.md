# Modern Default Sequential Gradient Ramp — Phase 9C Item 2 — SUPERSEDED (non-issue)

> **Status: WITHDRAWN 2026-07-23. Do not implement.** The full implementation plan that previously
> lived here was executed, reviewed, and then **reverted** after a grounding error was discovered. This
> stub records the finding so the mistake is not repeated.

## Why item 2 was withdrawn

The premise — inherited from the Phase 8 D5 deferral — was that `GradientColorFrame` is *"the default
continuous ramp when a measure is dropped on Color"*, with a dated magenta→green default
(`#FF99CC`→`#008000`) that needed modernizing. **That premise is wrong.**

The actual default continuous color frame for a measure bound to Color is **`BluesColorFrame`** (a
ColorBrewer single-hue light→dark blue ramp), assigned at:

- `GraphUtil.java:942-944` — for an `XAggregateRef` (measure): `frame = new BluesColorFrame()`.
- `ChangeChartTypeProcessor.java:863` — `measure ? new BluesColorFrame() : new CategoricalColorFrame()`
  (and the same at `:166/:1176/:1250/:1344/:1420/:1594`).

`BluesColorFrame` is hardcoded ColorBrewer with **no gate**, so it renders identically in legacy and
modern mode — and it is already a clean, perceptually-good single-hue blue ramp (the "Single Hue → Blues"
selection users see by default).

`GradientColorFrame` — the only frame item 2 touched — is the model behind the opt-in **"Custom"** ramp
option (`ColorFrameModelFactory:173`). It is **never** the default. So the reverted change only altered
the *initial endpoints of a manually-selected Custom gradient* (magenta→green → blue→blue) — a marginal
picker nicety, not a change to what any chart shows by default.

**Conclusion:** there is no "legacy continuous color" problem to fix for the default experience. The
default sequential ramp is already modern-appropriate. Item 2 is a non-issue.

## Lesson

Ground a "modernize the default X" task against the code that actually *selects the default X*, not
against the field defaults of a class that merely *could* hold X. Here, verifying
`GraphUtil.getColorFrame` / `ChangeChartTypeProcessor` up front would have shown the default is
`BluesColorFrame`, not `GradientColorFrame`.

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](../specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md) — scheduled tier item 2 (now marked non-issue)
- [visualization-phase8-implementation-plan.md](../specs/lookfeel/visualization-phase8-implementation-plan.md) — Phase 8 D5, the origin of the incorrect premise
