# Consolidate seeded-value reversibility on the seed mark

**Type:** refactor, cross-project · **Read against:** `viz-updates` @ tree `52c127c128c3`, 2026-08-12
(tree hash, not a commit sha) · **Blocked by:** the seed mark landing (Visualization Widget Spec §03)

## Summary

Three mechanisms now answer one question — *was this value put here by the modern gate, so may the
gate take it back?* — and they answer it three different ways. Consolidate on the seed mark, delete
the other two. One problem has to be solved first, and it is not the plumbing.

## The three mechanisms

**1 · Value-sniffing, for the card corner radius.** `VSObjectChromeDefaults`:

```java
public static int resolveSeededCorner(int radius) {
   return radius == CARD_CORNER_RADIUS && !isModern() ? 0 : radius;
}
```

Called from `VSCompositeFormat.resolveDefaultTierCorner`, which exempts tab formats. The test is
exact equality with the seed constant.

**2 · A per-value provenance boolean, for the bar corner radius.** `PlotDescriptor`:

```java
/** Effective bar corner radius; a gate-seeded value collapses to 0 when the gate is off. */
public double getBarCornerRadius() {
   return modernCornerSeed && !VSObjectChromeDefaults.isModern() ? 0 : barCornerRadius;
}
```

With the field commented: "true means modern mode seeded the radius rather than a user setting it, so
the gate may take it away again." This is the same idea as the mark, at per-value granularity, and it
is the best-designed of the three.

**3 · The tri-state seed mark**, decided in the sibling project (Visualization Widget Spec §03): a
nullable provenance field on `VSAssemblyInfo` recording which branch ran at creation — `gate-on`,
`gate-off`, or absent (read as *unclaimed*). Not yet landed.

## Decision

Consolidate on the mark. Delete `resolveSeededCorner` and its tab carve-out; fold
`PlotDescriptor.modernCornerSeed` into the same path. Replacing only mechanism 1 trades two for two
and is not worth doing.

## The problem to solve first: the mark is version-blind

`resolveSeededCorner` asks *is this value the one I seed?* The mark asks *was this assembly created
under the gate?* Those coincide only while the set of seeded defaults never changes.

Add a second seeded default after the mark ships, and an assembly marked `gate-on` from before that
default existed is assumed to carry it. The mark records which branch ran, not which defaults existed
at the time. Value-sniffing is immune to this by construction — it is the one thing it is genuinely
good at, and the reason to read this ticket before deleting it.

Two ways out, neither free: version the mark (a schema decision, in a rollout whose early steps are
deliberately export-safe), or keep a per-value check for defaults added after the mark. **Decide this
before the mark is the only mechanism**, because it is the failure that surfaces later and quietly —
long after the code that would have explained it is gone.

## The engineering cost: granularity

The mark lives on `VSAssemblyInfo`. The sniff happens in `VSCompositeFormat.resolveDefaultTierCorner`
— a method on a *format*, with no back-pointer to its assembly. Formats exist per region and per data
path, so either the mark is plumbed down into format resolution, or the decision moves up to runtime
model build and the export painters, where the other resolvers already run. The second is more likely
right and is the shape the sibling spec already describes for the re-seed path.

## What consolidation buys

- **The tab carve-out disappears.** `resolveDefaultTierCorner` exempts tab formats only because
  `FormatInfo.copyDefaultFormat` launders a composite-resolved radius onto a tab's default tier, where
  it can equal the seed. A fragile exception that exists purely because the test is by value.
- **A real false positive goes away.** A pre-gate assembly whose radius was set to 12px by
  `format.css` or a table style is wrongly stripped today. The mark leaves it alone.
- **One vocabulary.** Every future seeded default inherits reversibility instead of each one arriving
  with its own ad-hoc flag — which is how three mechanisms happened.

## Sequencing

1. Mark lands and is verified (sibling project owns this).
2. Answer the version-blindness question above.
3. Move the radius decision to model build / export painters; delete `resolveSeededCorner` and the tab
   exemption.
4. Fold in `modernCornerSeed`.

Deleting before step 1 leaves a window with no reversibility at all.

## Cross-project

The mark is owned by the sibling project, so this cannot be decided here alone. Expect the argument
that `modernCornerSeed`'s per-value boolean should generalize *instead of* the mark — it is
version-proof where the mark is not. The counter is that a boolean per seeded value does not scale to
a set of defaults and gives no answer for content created before any of them, which is the reversal
record the mark exists to provide. Worth settling explicitly rather than by whichever lands first.

## Related, not in scope

- **The card radius value itself.** Decided separately: the server seed drops 12px → 6px to match
  `--inet-radius-xl` (chart card spec §01). That change interacts with this one — `resolveSeededCorner`
  keys on exact equality, so any already-seeded 12px asset stops being stripped the moment the constant
  moves. Confirm the seeded cohort is empty before landing either.
- **`CalendarVSAssemblyInfo:1420`** seeds `setRoundCornerValue(10)` — a third off-scale radius (card 12,
  calendar 10, DOM scale tops at 6). Same reversibility question, not audited here.
- **The seeded colours** (`objectBorderColor`, `pageBackgroundCss`, `cardBackgroundCss`) are computed
  live at read time rather than written and sniffed, so they need nothing from this ticket. The radius
  is the odd one out precisely because it is persisted, to be visible in the format editor.
