# The Title Lane Height Row (L′) — Design

**Date:** 2026-08-25
**Covers:** the title-lane density row, its use-the-default affordance, and the cell-height affordance that
shares the same defect and the same pane
**Verified against:** community `viz-updates` @ `f69751842` (the card radius constant). Every line cited
below was read at that commit.
**Implements:** [the strip and lane decisions](./lookfeel/chart-card-anchored-strip-lane-decisions.md)
decisions 1, 2, 5, 6, 7 and 8, and
[seeded-value reversibility](./lookfeel/seeded-value-reversibility-decisions.md) decisions 2 and 4.
**Depends on:** the seed mark, all six phases, shipped. This design is startable because M-P4 supplied the
read path that consults the mark; nothing here is dormant.

**This design corrects three things in the documents it implements.** They are called out in §2 rather than
buried, because two of them would send an implementer the wrong way and the third changes the scope:

1. Decision 5's "one control shape across all three heights" does not survive contact with the code. Two of
   the three flags are not inferred at all.
2. Decision 6's included calendar cannot work with the guard the sibling features use.
3. The "eleven call-site rewrites" costing counts dialog services. The read surface is 111 sites.

---

## 1. What is being built

The title lane is 20px at every density today, because `VSDensityDefaults.titleHeight(VizContext)`
(`:104-106`) resolves 30/26/`AssetUtil.defh` and **has zero production callers** — verified. The lane row is
the last of the six items the seed mark existed to free, and the highest-value visible one.

Two halves, and both are needed:

- **The resolver.** A marked assembly whose author has not set a title height takes the density row:
  20 / 26 / 30 for dense / compact / comfortable.
- **The affordance.** A checkbox, "follow the default density", beside the title-height stepper and beside
  the cell-height stepper. It replaces the value-comparison that currently infers authorship.

Without the affordance an author cannot pin the height the dialog is showing them, and cannot opt back in
once the flag is set. Decision 5 establishes both as blocking; §2.1 narrows which heights they apply to.

### Why the row needs both the mark and the flag

They answer different questions and the row is wrong without either. `userTitleHeight` answers *did an
author choose this height*, so the row does not overwrite a deliberate choice. The mark answers *is this
assembly modern*, so the row does not reach dashboards nobody opted into. **On the flag alone the row would
resize fifteen years of saved dashboards on next open.**

---

## 2. Three corrections to the source decisions

### 2.1 The four height flags are not equivalent, and two of them need no affordance

Decision 5 says the checkbox covers title height, table row height and selection cell height, because "one
control shape across all three is the point". The three are not alike in the way that matters — **how
authorship is captured**:

| Flag | Captured by | Density row live? | Needs the checkbox |
|---|---|---|---|
| `userTitleHeight` | dialog value comparison | no — this design adds it | **yes** |
| `userCellHeight` | dialog value comparison | **yes**, already shipping | **yes** |
| `userDataRowHeight` | row-drag gesture | yes, already shipping | no |
| `userHeaderRowHeight` | row-drag gesture | yes, already shipping | no |

The two table row heights have **no dialog field anywhere**. They are set by dragging a row border:
`ComposerVSTableService.resizeTableCell` calls `setUserHeaderRowHeight(true)` (`:963`) and
`setUserDataRowHeight(true)` (`:974`). The gesture *is* the signal — there is nothing to disambiguate, so
decision 5's problem 1 ("an author cannot pin the height the dialog is showing them") cannot arise. And
problem 2 ("the flag is one-way") is already answered for them: reset-layout clears both
(`ComposerVSTableService:412-413`).

Title height and cell height, by contrast, are adjacent numeric steppers in the same template
(`size-position-pane.component.html:69-88`) and both infer the flag from a value comparison. Both problems
are live for both — and for cell height they are live **in production**, because
`SelectionListPropertyDialogService:224` and `SelectionTreePropertyDialogService:250` already compare against
`getEffectiveCellHeight()`.

**Scope: title height and cell height, with an independent checkbox each.** The table row flags are not
touched.

**Accepted, recorded, not fixed:** reset-layout is blunt. It clears column widths, row heights, explicit
table width and title height in one action, so a table author who dragged one row and wants the density
default back loses their column widths too. Pre-existing; a narrower action is a follow-up, not part of this.

### 2.2 The guard cannot test `AssetUtil.defh`, or the calendar never joins

Every sibling feature guards on the *global* default. `SelectionBaseVSAssemblyInfo.getEffectiveCellHeight()`
(`:138-142`):

```java
return ctx.modern && !userCellHeight && cellHeight == AssetUtil.defh ?
   VSDensityDefaults.cellHeight(ctx) : cellHeight;
```

and `VSTableLens:1782` does the same with `dataRowHeight == AssetUtil.defh`.

`AssetUtil.defh` is 20 (`:3493`). `CalendarVSAssemblyInfo`'s constructor stores **36**
(`titleInfo.setTitleHeightValue(36)`, `:90-91`) and its `getDefaultTitleHeight()` returns 36 (`:94-96`). So a
`defh` test excludes **every** calendar, new or old — the exact opposite of decision 6, which admits the
calendar and says newly created ones take the density sizing.

**The guard tests the type's own legacy default instead.** That produces decision 6's behaviour exactly:

| | stored | legacy default | modern | result |
|---|---|---|---|---|
| Marked calendar | 36 | 36 | yes | takes the density row |
| Unmarked calendar | 36 | 36 | no | keeps 36 |
| Marked chart / table / selection | 20 | 20 | yes | takes the density row |
| Marked chart, author set 25 | 25 | 20 | yes | keeps 25 |

**Consequence, accepted 2026-08-25:** a marked calendar at **dense** resolves to 20 where legacy is 36 — a
16px shorter lane. Decision 1's "dense is pinned to `AssetUtil.defh` so it stays legacy-exact" holds for the
other four types and breaks for the calendar, whose legacy default was never `defh`. Accepted because
dense-plus-marked is an opt-in modern look and no unmarked content moves. The clipping worry is settled
independently: `VSCalendar.getTitleHeight(boolean)` floors the painted lane at the font height
(`Math.max(info.getTitleHeight(), Common.getHeight(font))`, `:563`), so a 20px lane cannot clip the title.

**Rejected — keep 36 at dense and take 26/30 above.** It makes dense (36) taller than compact (26),
inverting the density ladder.
**Rejected — floor the calendar at 36 at every tier.** That excludes the calendar, reversing decision 6.

**Rename `getDefaultTitleHeight()` to `getLegacyTitleHeight()`.** Decision 6 asks for this and it is now
load-bearing: with two defaults in play, the current name invites a later reader to "correct" the calendar's
36 to match the density row, silently re-deriving every calendar ever saved. One interface default
(`TitledVSAssemblyInfo:107`), one override (`CalendarVSAssemblyInfo:94`), nine `parseXML` call sites, two
test assertions.

### 2.3 The read surface is 111 sites, not eleven

Decision 5's "eleven call-site rewrites" counts dialog services. Measured at `f69751842`:
`getTitleHeight()` has **135** hits, of which 24 are `SizePositionPaneModel.getTitleHeight()` — a different
class — leaving **111 on an assembly info**:

| Area | Sites |
|---|---|
| `report/io/viewsheet` + `/html` + `/svg` + `/pdf` — the exporters | **46** |
| `uql/viewsheet/internal` — 16 of them the eight per-type delegations themselves | 27 |
| `web/viewsheet/service` | 9 |
| `web/viewsheet/model` (+ table, chart, calendar) | 10 |
| `web/composer/vs/objects/controller` (+ event) | 7 |
| `web/composer/vs/dialog` | 5 |
| `report/gui/viewsheet` — the painters | 5 |
| `uql/viewsheet/vslayout`, `report/composition/graph` | 2 |

This is what drives the architecture in §3.

---

## 3. Architecture — substitute in the per-type `getTitleHeight()`

### The split that already exists

`getTitleHeight()` is the **runtime** read (`TitleInfo:149-150`, `getIntValue(false, …)`) and
`getTitleHeightValue()` is the **design-time/stored** read (`:166-167`, `getIntValue(true, …)`). The dialogs
already use the stored one — `ChartPropertyDialogService:198` reads `getTitleHeightValue()`. The raw/effective
separation this design needs is therefore already in place and does not have to be invented.

### The resolver

One new overload on `VSDensityDefaults`, reusing the existing `titleHeight(VizContext)` and its mode switch:

```java
public static <T extends VSAssemblyInfo & TitledVSAssemblyInfo> int titleHeight(T info, int stored) {
   VizContext ctx = VizContext.of(info);

   return ctx.modern && !info.isUserTitleHeight() && stored == info.getLegacyTitleHeight()
      ? titleHeight(ctx) : stored;
}
```

The generic intersection bound avoids a cast — every caller is both types. `TitledVSAssemblyInfo` is a bare
interface (`:31`), so neither type alone is sufficient.

`stored` is a **parameter rather than read inside** deliberately: it lets the composer services pass the
design-time value and still get the density substitution (§4.2).

Each of the five included infos changes its one-line delegation:

```java
@Override
public int getTitleHeight() {
   return VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight());
}
```

| Info | Line today | Covers |
|---|---|---|
| `ChartVSAssemblyInfo` | `:2716-2717` | chart |
| `TableDataVSAssemblyInfo` | `:241-242` | table, crosstab, calc table, embedded table |
| `SelectionBaseVSAssemblyInfo` | `:300-301` | selection list, selection tree |
| `CurrentSelectionVSAssemblyInfo` | `:205-206` | selection container |
| `CalendarVSAssemblyInfo` | `:634-635` | calendar |

**Not changed, and that is the whole of the exclusion:** `CheckBoxVSAssemblyInfo:134-135`,
`RadioButtonVSAssemblyInfo:156-157`, `TimeSliderVSAssemblyInfo:428-429`. Widget spec §05 excludes these three.
Because the substitution is per-info, the exclusion is structural rather than a condition maintained at N
call sites.

### Why `TitleInfo` is not touched

`TitleInfo` is shared by all eight titled types, so putting the substitution there would leak into the three
excluded ones. Two of its own methods make this critical rather than merely tidy:

- **`writeAttributes` persists `getTitleHeight()`** (`:325`). Left raw, so the stored height round-trips
  through save/load unchanged and only what painters and models read moves. This is the property that makes
  the row reversible with the mark.
- **`equals()` compares `getTitleHeight()`** (`:423`). Left raw, so decision 7's change (§5.1) stays
  independent of the substitution.

Both call `TitleInfo`'s own method, not the info's override, so both are unaffected by this design.

### All 111 sites become correct without being touched

Including all 46 exporter sites, the five painters, `AnnotationVSUtil:1194`, `TabVSAssemblyInfo:573/578`,
`VsToReportConverter:1036/1418/1510` and `VSUtil:612`. Only the five `web/composer/vs/dialog` sites that use
the runtime getter need review, and §4 replaces those anyway.

### Rejected alternatives

**`getEffectiveTitleHeight()` plus a sweep of the read sites** — the `getEffectiveCellHeight()` precedent.
Rejected on the evidence of how that precedent scaled: **14 effective against 18 raw call sites**, with no way
to tell by inspection whether the 18 are deliberate or missed. Scaled to 111 that is roughly 50 individual
judgements, 46 of them inside exporters, and a missed one is a **silent** view/export disagreement — the
same bug class the palette work (`1b8eb3cea`) spent five rounds closing.

**Resolve inline at each render site** — what row height does (`VSTableLens:1783`,
`BaseTableService:466/1168`). Rejected: it repeats the mark, flag and default tests at every site. Row height
gets away with three; this has 46 in exporters alone.

**Inside `TitleInfo.getTitleHeight()`** — rejected by the strip and lane decisions, and correctly: its
callers include the three excluded types, plus serialization and `equals()`.

---

## 4. The affordance

### 4.1 Model

Two new fields on `SizePositionPaneModel` (Java, field list at `:114`) and
`size-position-pane-model.ts` (`:25-26`):

```java
private Boolean titleHeightFollowsDensity;   // null: this assembly does not follow the density row
private Boolean cellHeightFollowsDensity;
```

Nullable, and **null means no checkbox for this assembly**. **Corrected 2026-08-25, after the final review:
this is a per-assembly condition, not a per-type one, and an earlier draft of this section got it wrong.**
Two populations send null, and only the first is about the type:

- an **excluded type** — the range slider shows a title-height stepper but is excluded from the row; and
- **any unmarked assembly**, of any included type. The resolver returns `stored` untouched when
  `getVizMark() == null`, so an unmarked chart or table cannot follow the density row either.

The second is the one the earlier draft missed, and missing it was a visible regression rather than a
technicality. `isUserTitleHeight()` is `false` for any saved assembly sitting at its type's default
(`TitleInfo:314` derives it that way), so a per-type reading sent `!false` = `true` for every classic
assembly — which rendered the checkbox **checked** and, via `size-position-pane.component.ts:88`, left the
Title Height stepper **disabled**. Every chart, table, crosstab, calc table, selection list, selection tree,
selection container and calendar on every existing classic dashboard would have opened with a read-only
height field, behind a checkbox describing a row it does not participate in.

So each read gates on the mark:

```java
model.setTitleHeightFollowsDensity(
   info.getVizMark() == null ? null : !info.isUserTitleHeight());
```

The existing `showTitleHeight`/`showCellHeight` continue to control the stepper; the null check controls the
checkbox independently.

**The height itself stays a non-null `int`.** Decision 5 floated making it nullable; that must not be done —
`size-position-pane.component.ts:111-112` derives visibility from truthiness
(`showTitleHeight = !!this.model.titleHeight`), so a null height would make the whole control disappear.
That truthiness is also a pre-existing latent bug for a height of 0; out of scope here.

### 4.2 Read: the stepper shows the effective value

```java
model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
model.setTitleHeightFollowsDensity(
   info.getVizMark() == null ? null : !info.isUserTitleHeight());
```

Passing `getTitleHeightValue()` keeps the dialog on the design-time value, per the composer convention, while
still applying the density substitution. This is why `stored` is a parameter in §3.

### 4.3 Apply: the comparison guard is deleted

**Corrected twice on 2026-08-25 — once during implementation and once at the final review. Both earlier
versions of this snippet were wrong, and each was wrong in a way that shipped a real defect.**

```java
Boolean follows = model.getTitleHeightFollowsDensity();

if(follows == null) {
   if(model.getTitleHeight() != info.getTitleHeightValue()) {
      info.setUserTitleHeight(true);
      info.setTitleHeightValue(model.getTitleHeight());
   }
}
else if(follows) {
   info.setUserTitleHeight(false);
   info.setTitleHeightValue(info.getLegacyTitleHeight());
}
else {
   info.setUserTitleHeight(true);
   info.setTitleHeightValue(model.getTitleHeight());
}
```

**Ticked resets the stored height as well as clearing the flag.** The first draft cleared the flag and wrote
nothing, on the reasoning that the stored height "stays at the legacy default" — true only for an assembly
nobody had ever customised, and false for exactly the population the affordance exists for. §3's guard also
requires `stored == getLegacyTitleHeight()`, so on an assembly with a pinned 25 the flag alone changes
nothing: the author ticks the box, applies, and sees the lane stay at 25. Resetting the stored height is what
makes the opt-back-in real.

**Null means no opinion, and keeps the old comparison guard.** Null is not "not following": it is the normal
payload for every unmarked assembly (§4.1) and for any client predating the field. Treating it as
not-following would stamp `userTitleHeight = true` on every legitimate unmarked apply, permanently
disqualifying that assembly from the row if its dashboard were later Modernized. The null branch therefore
reproduces the pre-change-set behaviour exactly — provenance is stamped only when the height actually
changed — which also means a stale browser bundle cannot silently pin a height.

**Unticked pins the submitted height** and sets the flag.

The cell-height apply is the same three branches, with `AssetUtil.defh` in place of `getLegacyTitleHeight()`
(there is no per-type cell default — `getEffectiveCellHeight` compares against `AssetUtil.defh` directly) and
the null branch comparing against `getEffectiveCellHeight()`, which is what its own pre-change-set guard
compared against.

The value-comparison guards are **deleted from the two branches that carry a real signal**, and survive only
as the null branch's fallback. An earlier draft said they were deleted outright; that was true of the design
before null acquired a meaning. This covers `ChartPropertyDialogService:412-414` and the cell-height
comparisons at `SelectionListPropertyDialogService:224` and `SelectionTreePropertyDialogService:250`.

### 4.4 Untick pins what you see

Decided 2026-08-25. Ticked, the stepper is disabled and displays the density-derived value. Unticking leaves
the number where it is and makes it editable, so the pinned height is exactly what the author was looking at.

This needs **no client-side value juggling**: §4.2 already sends the effective value, so untick-then-apply
writes it verbatim. It is the direct answer to decision 5's problem 1 — an author at compact who wants 26
permanently unticks, and 26 is pinned.

**Accepted cost:** an author who had 25 pinned, ticks, then unticks in the same session gets 26 rather than
25. One retype, against a rule that is otherwise WYSIWYG at the moment control is taken.

**Rejected — untick restores the previously stored value.** The number jumps at the moment of unticking, and
pinning the displayed value takes two steps.
**Rejected — always editable, typing unticks.** The checkbox moves without being clicked, and a stray
keystroke silently pins a height.

### 4.5 Template

`number-stepper` gains `[disabled]="model.titleHeightFollowsDensity"` — the input exists
(`number-stepper.component.ts:55-57`). A checkbox follows inside the same column, rendered only when the
field is non-null, labelled "Follow the default density". Same shape for cell height
(`size-position-pane.component.html:80-88`). Two independent checkboxes.

No comments in the template file.

### 4.6 The services

Eight of the nine participate. Because the split is per-service, no service needs a null branch — a
participating one always populates the field, an excluded one never does.

| Service | Info | Title | Cell |
|---|---|---|---|
| `ChartPropertyDialogService` | Chart | yes | — |
| `TableViewPropertyDialogService` | TableData | yes | — |
| `CrosstabPropertyDialogService` | TableData | yes | — |
| `CalcTablePropertyDialogService` | TableData | yes | — |
| `SelectionListPropertyDialogService` | SelectionBase | yes | yes |
| `SelectionTreePropertyDialogService` | SelectionBase | yes | yes |
| `SelectionContainerPropertyDialogService` | CurrentSelection | yes | — |
| `CalendarPropertyDialogService` | Calendar | yes | — |
| `RangeSliderPropertyDialogService` | TimeSlider | **no — unchanged** | — |

**To enumerate at plan time rather than assume:** which types actually render the title-height stepper in
this pane. The service split above is verified; each service's `showTitleHeight` path is not, and check-box
and radio-button title height appears to live in their own general panes
(`checkbox-general-pane.component.html`, `radiobutton-general-pane.component.html`) rather than here.

---

## 5. Two cleanups this forces

### 5.1 `userTitleHeight` joins `TitleInfo.equals()`

Decision 7, and now mandatory rather than tidy. The checkbox lets an author change the flag **without
changing the number**, so two `TitleInfo`s differing only in the flag become routine — and each info's
`copyViewInfo` transfers the whole `TitleInfo` only when it compares unequal, so the change would be silently
dropped on apply.

The audit that makes it safe is done and re-verified at `f69751842`: `equals()` has exactly eight consumers
repo-wide, all of them the `copyViewInfo` guard in the eight titled infos, and nothing outside
`uql/viewsheet/internal` compares a `TitleInfo` at all — no change detection, no undo, no dirty-checking.

**Two call styles, and a grep for one of them undercounts.** Seven guards use
`Tool.equals(titleInfo, x.titleInfo)` — `CalendarVSAssemblyInfo:1090`, `ChartVSAssemblyInfo:1804`,
`CheckBoxVSAssemblyInfo:377`, `CurrentSelectionVSAssemblyInfo:506`, `RadioButtonVSAssemblyInfo:344`,
`TableDataVSAssemblyInfo:1378`, `TimeSliderVSAssemblyInfo:753`. The eighth,
`SelectionBaseVSAssemblyInfo:819`, calls `titleInfo.equals(sinfo.titleInfo)` **directly**. A plan-time grep
for `Tool.equals(titleInfo` finds seven and appears to contradict decision 7; it does not. Note also that
decision 7 cites `ChartVSAssemblyInfo:1724` and `SelectionBaseVSAssemblyInfo:818`, both of which have since
moved by a line or two.

**This is title height only — cell height needs no `equals()` change.** `userCellHeight` lives on
`SelectionBaseVSAssemblyInfo` rather than on `TitleInfo`, and its `copyViewInfo` already compares it
explicitly (`:799-801`). Only `userTitleHeight` rides inside `TitleInfo` and therefore depends on
`equals()` telling the truth.

**Rejected — copy the flag explicitly in each `copyViewInfo`.** Eight blocks instead of one line, `equals()`
keeps lying, and a ninth consumer added later silently reintroduces the defect.

### 5.2 `TitleInfo.isUserTitleHeight()`'s javadoc is replaced

The 40-line javadoc at `:178-220` documents the inference guard, why the displayed value cannot be pinned,
why the flag must join `equals()`, and why the flag is one-way for five of eight types. This design resolves
all four. It is replaced by a short statement of what the flag now means: the author's answer to the
follow-the-default-density control.

---

## 6. Verification

### 6.1 The automated blast radius is two lines

No existing test enables the modern gate or sets a mark on a titled assembly — checked across
`TabVSAssemblyInfoTest`, `SelectionListPropertyDialogServiceTest` and `TitleInfoTest`, all zero. So
`ctx.modern` is false throughout and the resolver returns `stored` unchanged. The only existing assertions
that break are `TitleInfoTest:193,195`, from the §2.2 rename.

That is also the warning: **existing coverage proves nothing about the new behaviour.** All of it is new.

### 6.2 New tests

| Target | What it proves |
|---|---|
| `VSDensityDefaultsTest`, new overload | four guard combinations × three densities; the calendar's 36-is-its-own-default case, marked and unmarked |
| **New** per-info override test | the five included types return the density value under a mark; **the three excluded return raw**. The regression fence for the whole approach |
| `TitleInfoTest` | the stored height survives `writeXML`/`parseXML` unchanged while the row resolves it live |
| **New** `copyViewInfo` test | a `TitleInfo` differing only in the flag now transfers — unobservable before the checkbox exists |
| 8 dialog service tests | read sends effective + follows; apply ticked writes nothing and clears the flag; apply unticked writes the height and sets it. Three exist (`Calendar`, `SelectionList`, `SelectionTree`); five are new |
| `size-position-pane.spec.ts` | checkbox absent when the field is null; stepper disabled when ticked; the number does not move on untick |

### 6.3 The manual pass

Title height is persisted and painter-read, so this moves PDF, PNG, Excel, HTML and scheduled output and
shifts everything below the title. It is not a CSS change.

**A — the row.** Each of the eight included assemblies at dense / compact / comfortable → 20 / 26 / 30,
calendar included. Unmarked equivalents unchanged at every density.

**B — the exclusions.** Check box, radio button, range slider: title lane unchanged at every density, marked
or not. Any movement here means the per-info substitution has leaked.

**C — the checkbox.** Ticked shows the derived value, disabled. Untick and the number does not move; apply,
save, reopen — pinned. Tick an assembly already carrying the flag and it follows density again: the
opt-back-in that exists nowhere today outside table reset-layout.

**D — export agreement.** PDF, PNG, Excel and HTML at each density for a marked and an unmarked dashboard.
Content *below* the title must shift consistently, not just the lane.

**E — two interactions not named in any decision document.** `AnnotationVSUtil:1194` positions annotations
off a chart's title height, so annotations on a chart move with the lane. `TabVSAssemblyInfo:573/578` derives
tab bounds from a calendar's or selection's title height, so tabs containing those resize. Check an annotated
chart and a tab holding a calendar and a selection list.

**F — scheduled export.** Different thread, different entry point.

**G — Revert.** A modernized dashboard reverted returns every lane to its legacy height. This is the
reversibility property, and it is why `TitleInfo` staying raw matters.

---

## 7. Out of scope

- **L″, geometric suppression.** Decision 3's sequencing rule is one-directional: L″ must follow L′, never
  accompany it. Worth noting L′ *improves* the interim — the density-keyed suppression in `f5f568f12`
  approximated a 26px lane, and after this the lane actually is 26.
- **A narrower opt-back-in for table row heights** (§2.1). Follow-up.
- **`showTitleHeight` truthiness** (`size-position-pane.component.ts:111-112`) — a height of 0 hides the
  control. Pre-existing.
- **`--inet-viz-chrome-row-height` against the lane row.** The token is declared four times
  (`_viz-tokens.scss:25`, then `:119`, `:132`, `:143` for dense, compact and comfortable) at 22 / 26 / 30. It
  agrees with the lane at compact and comfortable and disagrees at dense, 22px against the lane's 20px.
  Verified 0 consumers outside its own declarations, so nothing disagrees yet; it will the moment something
  binds it. The roadmap cites this as `:112`, which has moved.
- **The 44px touch target against a 26px lane.** Belongs to the strip, unresolved in every document, not
  blocking this.
- **Whether 1px of clearance reads tight at compact.** Judged during manual step A rather than decided now;
  the lever is compact's lane row, not the strip.

---

## 8. Open questions

- **The `showTitleHeight` enumeration** (§4.6). Which types render the stepper in this pane. Resolved by
  reading the nine services while writing the plan, not a design decision.
- **`VizContext.of(info)` cost.** `getTitleHeight()` becomes context-dependent at 111 call sites, some in
  render loops. Worth one look at whether any caller invokes it per-row rather than per-assembly. Not
  expected to matter — `getEffectiveCellHeight()` already does the same in
  `SelectionValueModel:60` — but it is the one performance question this approach raises and it has not been
  measured.

---

## 9. Related

- [The anchored strip and the title lane — decisions](./lookfeel/chart-card-anchored-strip-lane-decisions.md)
  — decisions 1, 2, 5, 6, 7, 8
- [Seeded-value reversibility decisions](./lookfeel/seeded-value-reversibility-decisions.md) — decisions 2
  and 4 key the density heights off the mark
- [Chart card roadmap](./lookfeel/chart-card-roadmap.md) — L′ is ranking item 2 at the thirteenth revision
- [The seed mark's forward half](./2026-08-14-seed-mark-forward-half-design.md) — the mark this row reads
