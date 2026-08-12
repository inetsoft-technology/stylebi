# Chart card dark values

**Type:** reconciliation, gated · **Branch:** `viz-updates` · **Depends on:** the card-radius conflict
(below) and the seed mark reaching this card the same way it reaches modern

## This is a reconciliation, not a blank page

The server already has dark values. `VSObjectChromeDefaults` defines a dark card background `#252428`,
object border `#49454F`, page canvas `#1C1B1F`, and text `#E6E0E9`, plus an `applyDarkForeground`
resolver that substitutes light text onto a clone at both model build and export. This spec's greys are
light-mode literals that have never been checked against those four values — the gap is verification and
binding, not invention.

**Confirmed correct already:** the toolbar glyph's luminance-derived tone (§10.2) needs no dark branch.
The shipped dark card background (`#252428`) computes to L ≈ 0.02, well under the 0.45 threshold, so the
glyph resolves to the light tone automatically.

**Genuinely undecided, not just unverified:** the chart interior. Interior greys (gridlines, axis lines)
come from server-side `GDefaults`, which has no dark branch today — this is a real gap, not a reconciliation
task.

## What needs doing

1. **Bind the card's dark literals to the four shipped `VSObjectChromeDefaults` values** instead of writing
   new ones. Verify against the actual render, not just the Java constants.
2. **Resolve the card radius conflict first, or this reconciliation binds to a value about to change.**
   The server seeds `CARD_CORNER_RADIUS = 12px` into new assets; this spec's light-mode card binds to
   `--inet-radius-xl` (6px), which is the top of the DOM scale — 12px isn't on the scale at all. Either the
   scale gains a 12px step or the seed drops to 6px. Whichever way that resolves, the dark binding should
   use the same final value, not the pre-conflict one.
3. **Decide the chart interior palette** (gridlines, axis lines, GDefaults) for dark — no server value exists
   to reconcile against, so this is new work, and it's the one piece of this ticket that's a real decision
   rather than a verification.
4. **Verify, don't assume, that selection/annotation/tooltip tokens already resolve under dark.** Selection
   stroke/fill and the annotation border are bound to `--inet-primary-color`/`--inet-focus-ring-color`; the
   tooltip ramp to `--inet-shadow-overlay`/`--inet-radius-*`/theme text tokens. These are generic theme
   tokens, not viz-scoped, so they likely already resolve — confirm rather than re-implement.

## Sequencing

Independent of the toolbar/selection/tooltip work already shipped — modern/legacy and light/dark are
separate axes. Sequence the radius-conflict resolution (item 2) before finishing item 1's binding, since
otherwise the dark card binds to a value that's about to move. Item 3 (chart interior) has no dependency
and can start any time — it's server-only design work, not a code reconciliation.
