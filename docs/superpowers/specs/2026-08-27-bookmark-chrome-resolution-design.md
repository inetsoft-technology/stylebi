# Resolving chrome on bookmark restore — design

**Date:** 2026-08-27
**Verified against:** community `viz-updates` @ `9a1cc79b3`
**Implements:** decision 10 of
[seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md) — the last
release-gate item on this branch
**Supersedes decision 10's property list**, which points at the wrong values for charts. See §2.

Every claim below cites a file and line. Where this design departs from decision 10 it says so and why.

---

## 1. The defect

A viewsheet assembly's modern chrome is *seeded*: `seedChromeDefaults` writes the object border colour,
the card radius and the card background onto the DEFAULT tier of the assembly's `FormatInfo`, and — for a
chart — bar corner radius, smooth lines and the categorical palette onto the chart descriptor and the
aesthetic refs (`ChartVSAssemblyInfo:102-123`).

Bookmark restore replaces those stored values without consulting the assembly's mark. So:

- take a bookmark while an assembly is marked modern,
- **Revert** the dashboard, which clears the mark and re-seeds legacy values,
- restore the bookmark.

The assembly is unmarked, but its chrome comes back modern. The Revert is silently undone. The reverse
holds too: a bookmark taken before Modernize restores legacy chrome onto a marked assembly.

Confirmed unbuilt at this commit: neither `ChartVSAssembly` nor `TableVSAssembly` names
`seedChromeDefaults`, `VizModernizeUtil` or `VizContext`.

## 2. What is actually exposed — decision 10's list re-derived from the code

The roadmap instructed re-deriving this rather than trusting decision 10's table. Doing so found the
chart entry pointing at the wrong element.

| Class | Restore does | Seeded chrome exposed |
|---|---|---|
| `TableVSAssembly` | `state_tableformat` → `setFormatInfo(finfo)` (`:158-164` write, `:191-193` parse) — **replaces the whole `FormatInfo` object** | **All three format values**: border colour, card radius, card background |
| `CrosstabVSAssembly` | `state_crosstabformat`, same shape | same |
| `ChartVSAssembly` | `state_info` → `VSChartInfo` (`:470-472`); `state_descriptor` → `ChartDescriptor` (`:479-481`); `state_format` → **`getChartInfo().setFormat(fmt)`** (`:605`) | **All three format values** too — `getChartInfo()` returns the `ChartVSAssemblyInfo` itself (`:325-327`), so `setFormat(fmt)` replaces the assembly's own `OBJECTPATH` format exactly as the table's `setFormatInfo` does — plus **bar corners and smooth lines** (`PlotDescriptor`, inside `ChartDescriptor`) and **the palette** (`CategoricalColorFrame.defaultColors` on the aesthetic refs) |
| `CalcTableVSAssembly` | `state_calctable` → the **whole `CalcTableVSAssemblyInfo`, mark included** (`writeXML`/`parseXML`, not gated on `runtime`) | **All three format values, and the mark itself** — restore installs whatever mark the blob carried, which the hook then trusts unless the mark is explicitly re-applied first |

**The correction:** an earlier draft of this section costed the chart's chrome exposure through
`<state_format>` as replacing something other than the assembly's own format, on the theory that
`getChartInfo()` returns the internal `VSChartInfo` rather than the `ChartVSAssemblyInfo`. It does not:
`getChartInfo()` **is** `(ChartVSAssemblyInfo) getInfo()` (`:325-327`), so `state_format` reaches the same
`OBJECTPATH` format the table's `state_tableformat` reaches. Harmless for the code — `seedChromeDefaults`
re-derives from `getFormat()` fresh regardless of which element supplied the pre-reseed value — but the
distinction matters for a reader deciding what a regression test needs to cover: `<state_format>` is not
a safe element to skip on the chart. The chart's exposure beyond the shared format path is
`<state_descriptor>` and `<state_info>`, and the palette is in the latter — the same value `1b8eb3cea`
spent five bug-fix rounds getting right.

A plan built from the earlier draft's version of this table would have skipped covering `<state_format>`
on the chart and missed the palette.

## 3. Rejected: converting the seeded values to read-time resolution

This branch's other four geometry values resolve at read time and are therefore bookmark-safe by
construction — nothing is stored, so nothing can be restored stale. The obvious question is whether
converting the *seeded* values the same way would close decision 10 by removing its subject.

It does not. Assessed per value:

| Value | Convertible? |
|---|---|
| Bar corner radius, smooth lines | **Yes**, cheaply — plain values read by `GraphGenerator` |
| The categorical palette | **No.** `CategoricalColorFrame.setDefaultColors` is copied into several stores on the way to the screen; that is exactly what `1b8eb3cea` spent five rounds discovering |
| Border colour, card radius, card background | **No.** DEFAULT-tier format values read at dozens of points. The dark-mode work established that a format substitution must run at *every* read point or the composer's format pane disagrees with the canvas — it took five read points for one selection-text colour |

So the design resolves on restore. Recorded because the idea is attractive and someone will raise it
again.

## 4. The design

### 4.1 One hook at the final chokepoint

`AbstractVSAssembly.parseState(Element)` is `final` (`:641-643`) and its entire body is
`parseStateContent(elem)`. Every assembly's restore passes through it. The re-seed goes immediately
after, so it runs once restore has finished writing:

```java
   @Override
   public final void parseState(Element elem) throws Exception {
      parseStateContent(elem);
      VizModernizeUtil.reseedAfterRestore(getVSAssemblyInfo());
   }
```

### 4.2 Why a named helper rather than an inline call

`seedChromeDefaults` is `protected` on `VSAssemblyInfo` in `inetsoft.uql.viewsheet.internal`.
`AbstractVSAssembly` is in `inetsoft.uql.viewsheet` and is not a subclass, so it cannot reach it.

`VizModernizeUtil` is in the right package and is already the home for moving a dashboard's content
between classic and modern chrome. Restore-reseed is the third member of that family beside `modernize`
(`:55`) and `revert` (`:102`), and it is where the reasoning belongs:

```java
   /**
    * Re-resolve an assembly's seeded chrome after its state has been parsed. Restore replaces stored
    * formats and descriptors without consulting the mark, so the chrome that arrives can disagree with
    * the assembly it lands on: a bookmark taken before a Revert would otherwise un-revert it, and one
    * taken before Modernize would leave legacy chrome on a marked assembly.
    *
    * Safe because the mark never travels in state. It lives in writeAttributes, which is asset XML,
    * and writeState emits only the class, the name and writeStateContent - so the assembly still knows
    * what it should be whatever the blob said. seedChromeDefaults writes DEFAULT tiers and the palette
    * only, so a USER format the bookmark legitimately restored survives untouched.
    *
    * Null-tolerant: a mocked or partially constructed assembly can have no info yet.
    */
   public static void reseedAfterRestore(VSAssemblyInfo info) {
      if(info != null) {
         info.seedChromeDefaults(VizContext.of(info));
      }
   }
```

### 4.3 What the placement covers, and what it deliberately does not

**`parseState` is not bookmark-only.** Three callers, and re-seeding is correct in all three:

| Caller | What it is | Why re-seeding is right |
|---|---|---|
| `VSBookmark:267` → `Viewsheet.parseState(elem, true)` → `Viewsheet:2215` `assembly.parseState(anode)` | bookmark restore | the target case |
| `RuntimeViewsheet.setEntry` (`:422-428`) → `gotoDefaultBookmark` (`:489-490`) → `ibookmark.getBookmark(VSBookmark.INITIAL_STATE, vs)` → `vs.parseState(elem, true)` | **every viewer open**, not only an explicit bookmark switch — `INITIAL_STATE` takes the same `parseState` branch as a named bookmark | the target case, reached far more often than the name suggests |
| `RuntimeViewsheet.refresh()` (`:630`) → `gotoDefaultBookmark` | every viewsheet refresh | same |
| `VSEventUtil.updateViewsheet:4063-4072` | writes the old viewsheet's state and parses it into a new one on refresh | the new assembly carries its own mark; the state blob does not |
| `Viewsheet:607-615` | embedded viewsheet refresh, same write-then-parse shape | same |

**The refresh and default-bookmark paths have the same latent defect and are not in decision 10 at
all.** Fixing at the chokepoint closes them for free. That is the main argument for this placement over
per-class overrides. It also means `parseState` runs on every viewer open and every refresh of every
dashboard, not only when someone explicitly switches bookmarks — see the correction to §6's performance
risk.

**Types the hook is a no-op for, by existing design:** `bypassesBaseChrome()`
(`VSAssemblyInfo:1320-1330`) returns early for `CheckBox`, `ComboBox`, `RadioButton`, `Spinner`,
`Submit`, `TextInput`, `Text`, `Calendar` and `Tab`. No new predicate is introduced — this design adds
no third mechanism beside `bypassesBaseChrome()` and `isCornerSeedTarget()`.

**Embedded viewsheets do not inherit the hook**, and that is correct rather than a gap.
`Viewsheet extends AbstractSheet implements VSAssembly` (`Viewsheet:74`) — not `AbstractVSAssembly` — and
its state is parsed through its own `parseState(Element, boolean)` overload, which then recurses into its
children's final `parseState` (`:2211-2216`). A viewsheet assembly is a container with no chrome of its
own to seed; its children each get re-seeded individually.

### 4.4 The per-assembly hook is not sufficient on its own — the sheet caches must be cleared too

**Found by this design's own self-review, and it would otherwise have shipped the defect
`1b8eb3cea` spent five rounds closing.** `VizModernizeUtil.revert` does not stop at re-seeding each
assembly. After the loop it clears two sheet-level caches (`:113-118`), with its reason in the code:

```java
      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go or the modern palette survives
         vs.clearSharedFrames();
         vs.clearDimensionColors();
      }
```

A render prefers the sheet's shared frame over an assembly's own. So re-seeding a chart's palette in
`parseState` writes the assembly's frame and the render then clones the stale shared one in preference —
the palette stays wrong, exactly as it did before Revert learned to clear these.

**The caches are per-sheet and `parseState` is per-assembly**, so this half cannot live at the assembly
chokepoint. It goes at the sheet-level entry, `Viewsheet.parseState(Element, boolean)`, after the loop
that recurses into children (`Viewsheet:2211-2216`, second pass at `:2254`/`:2266`) — the direct analogue
of where `revert` does it. `clearSharedFrames()` is `Viewsheet:3748`.

**Correction: `parseState` clears only `clearSharedFrames()`, not `clearDimensionColors()`.** The whole-
branch review found `dimensionColors` is not a cache — it is persisted asset content (a user's fixed
cross-chart colour assignment), `writeState` never emits it, and nothing rebuilds it. `revert`'s own call
above is unaffected by that finding and is out of scope for this design; only the mirroring call this
section originally described adding to `parseState` was wrong, and it was not added.

**Clear unconditionally, not on a "did anything re-seed" flag.** `revert` can guard on
`!targets.isEmpty()` because it built the target list itself; a restore has no equivalent signal without
plumbing a return value up from every `reseedAfterRestore` call through two recursion passes. Restore
already parses XML for every assembly in the sheet, so the cache invalidation is small beside it, and the
cache rebuilds lazily on the next render. **The plan must measure this rather than assume it** — if the
rebuild proves expensive on a chart-heavy dashboard, the fallback is to have `reseedAfterRestore` return a
boolean and accumulate it, which is more plumbing for the same outcome.

### 4.5 Ordering

Two ordering constraints, both satisfied by the placements above:

1. The per-assembly re-seed must run **after** `parseStateContent`. For a table, `parseStateContent`
   calls `setFormatInfo(finfo)`, replacing the object the seed would otherwise write into. §4.1's
   placement makes this structural — no path through `parseState` reaches the helper first.
2. The sheet cache clear must run **after every child has been re-seeded**, so it cannot sit inside the
   child loop. Note `Viewsheet.parseState` parses children in **two** passes — the main loop and a
   second pass over `clist` for deferred types (`:2254`, `:2266`) — so the clear belongs after both, not
   after the first.

## 5. Testing

Java, `@Tag("core")`, in `core/src/test/java/inetsoft/uql/viewsheet/`.

**The two defect cases, one per direction:**

| # | Setup | Expect |
|---|---|---|
| 1 | Mark a chart, write its state, clear the mark (as Revert does), parse the state back | bar corner radius is the legacy 0 and the palette is the legacy one — **not** the modern values the blob carried |
| 2 | Leave a chart unmarked, write its state, mark it (as Modernize does), parse the state back | bar corners 0.3, smooth lines true, modern palette |

**The table case, which is the widest:**

| # | Setup | Expect |
|---|---|---|
| 3 | Mark a table, write state, clear the mark, parse back | border colour, card radius and card background are all legacy, despite `setFormatInfo` having replaced the whole `FormatInfo` |
| 3b | Mark a chart, write sheet state, clear the mark, parse the **sheet** state back, then read the frame a render would clone | the sheet's shared frame is gone or legacy — not the modern palette. This is the §4.4 case, and a per-assembly-only fix fails it |

**The guards — these are what must not break:**

| # | Setup | Expect |
|---|---|---|
| 4 | An **unmarked** assembly, state written and parsed back with the mark unchanged | bit-for-bit unchanged. This is the branch's governing constraint |
| 5 | A bookmark carrying a **USER-tier** format (an author's own border colour), restored onto a marked assembly | the author's colour survives; only the DEFAULT tier moves |
| 6 | An assembly whose type is in `bypassesBaseChrome()` | unchanged, and the helper does not throw |
| 7 | `reseedAfterRestore(null)` | no throw |

**Not unit-testable, so it goes in the manual pass:** the two refresh paths
(`VSEventUtil.updateViewsheet`, embedded viewsheet refresh) need a running server. One check each:
refresh a dashboard holding a marked chart and a reverted chart, and confirm neither changes appearance.

## 6. Risks

**Performance.** **Corrected: this is not a per-bookmark cost.** §4.3 originally listed only the two
refresh paths beside the named-bookmark case, which read as "mostly a bookmark-open cost." It is not —
`RuntimeViewsheet.gotoDefaultBookmark`, reached from both `setEntry` (every viewer open) and `refresh()`,
takes the identical `parseState` branch as a named bookmark restore, so the hook runs per assembly on
**every viewer open and every refresh of every dashboard**, not only when someone explicitly switches
bookmarks. `seedColorPalette` (`ChartVSAssemblyInfo:129-142`) loops `getAestheticRefs(runtime)` for both
runtime values per chart. Expected negligible against the XML parse that precedes it, but it is a new
per-restore cost on a hot path reached far more often than the original wording suggested, and should be
sanity-checked on a dashboard with many charts rather than assumed.

**A bookmark predating the whole branch.** Such a bookmark carries no seeded values at all and the
assembly's mark decides everything, which is the desired behaviour and needs no special handling. Worth
one manual check because it is the most common real-world case at release.

**The palette's several stores — answered in §4.4, not an open risk.** `revert` clears two sheet-level
caches outside `seedChromeDefaults`, and restore needs the same. This was an open question when §6 was
first drafted; the self-review resolved it and the answer became part of the design. Left here as a
pointer because a reader arriving at the risks section should not conclude it is unhandled.

**What this does not fix.** Values that are neither seeded nor read-time resolved are out of scope: the
unconditional creation defaults `seedChromeDefaults` is documented never to touch
(`VSAssemblyInfo:1245-1246`), and the two accepted gate-read costs, `AbstractChartInfo.getTooltipStyle`
and `VSChartInteractionDefaults.isInlineSvg`.

## 7. Related documents

- [seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md) —
  decision 10 is what this implements; its property list is superseded by §2 above
- [chart-card-geometry-decisions.md](./lookfeel/chart-card-geometry-decisions.md) — the read-time
  resolution pattern §3 assessed and rejected for these values
- [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) — this is the last release-gate item on the
  ranking
