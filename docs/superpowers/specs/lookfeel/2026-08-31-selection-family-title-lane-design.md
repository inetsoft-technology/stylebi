# The Selection Family's Title Lane — Design

**Date:** 2026-08-31
**Verified against:** community `viz-updates` @ `bee8d4169`, which is `HEAD`. Every file and line citation below was read at that commit.
**Follows:** [2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md), which converted the chart and the three table types and named this family as the deferred remainder.
**Roadmap entry:** the read-time resolver migration, ranked #3 in [chart-card-roadmap.md](./chart-card-roadmap.md)'s "What to pick up next". This design takes the first four of that item's five resolvers' worth of work.

---

## Scope

**In: selection list, selection tree, selection container, range slider.** `SelectionListVSAssemblyInfo` and `SelectionTreeVSAssemblyInfo` via their shared `SelectionBaseVSAssemblyInfo`, plus `CurrentSelectionVSAssemblyInfo` and `TimeSliderVSAssemblyInfo`. These are the four titled types whose `seedChromeDefaults` hook already runs.

**Out, and it needs its own design: checkbox, radio button, calendar.** All three are on `bypassesBaseChrome()` (`VSAssemblyInfo:1320-1330`), where the hook returns before writing anything. Seeding a title for them means deciding whether that predicate splits into a bypassable object-chrome half and an always-run title half, which is a change to the hook's contract rather than an application of it. The calendar carries two further problems: `initDefaultFormat` never calls `setDefaultFormat` at all, so the hook has never run for it at any point, and `AbstractVSExporter:2716-2721` fills a zero-width title border side from the object border, so a bottom-only rule would arrive in export as a four-sided box. Deferred whole.

**Consequence: `VSTitleChromeDefaults.applyModernDefaults` survives this design.** It is deleted only when the three deferred types convert. `isSeededTitle` (`VSTitleChromeDefaults:157`) grows from two branches to four here and to seven there.

---

## 1. What the four types actually look like today

The scoping assumption worth discarding first: this is not four instances of the same conversion. Three of the four **already carry the modern shape** in their legacy defaults, and only one is structurally the table's case.

| Type | Legacy TITLEPATH at creation | Written where |
|---|---|---|
| Selection list, selection tree | Fresh composite. **No background.** Bottom-only `THIN_LINE`. `0xc0c0c0` on all four colours | `SelectionBaseVSAssemblyInfo:889-901` |
| Range slider | Existing composite, mutated. **Background explicitly nulled.** Bottom-only `THIN_LINE`. `0xc0c0c0` on all four colours | `TimeSliderVSAssemblyInfo:703-716` |
| Selection container | The base's, untouched. `DEFAULT_TITLE_BG` = `0xf5f5f5`. Four-side `THIN_LINE`, since `initDefaultFormat` passes `border = true` | `VSAssemblyInfo:1191-1198`, `:1218-1227`; `CurrentSelectionVSAssemblyInfo:68-69` |

So for the first three the modern treatment is a **colour change and a foreground**, not a structural one — the lane is already unfilled with a bottom rule. Only the container needs the fill cleared and the box reduced to a rule.

**They look filled today only because of the read-time substitution.** `applyModernDefaults` writes `TITLE_BG` `#F1EFEA` onto the DEFAULT tier at every read when the context is modern, so a modern selection list currently draws a filled `#F1EFEA` band *and* a `0xc0c0c0` rule underneath it. Converting the type to seeding removes the fill with no code that clears anything: the shim early-returns, and nothing else writes a title background for these three.

---

## 2. The values

The widget spec is the authority and it is unambiguous — *"VSTitleChromeDefaults — one title bar across every titled assembly: #F1EFEA on #6A685F with a #D9D5CC rule, deliberately equal to the table header so chrome reads as one system. Substitution is keyed on whether the user or format.css set a value, not on matching a specific legacy colour — which is what lets white, #f5f5f5 and transparent titles converge."* The fill half of that sentence was reversed by the title lane design; the rule half stands, and the convergence clause is exactly what `0xc0c0c0` is: a hardcoded legacy default, not an author's choice.

**Modern branch**, identical to what the chart and the table family already seed. Add no new constant:

- Borders: `VSTitleChromeDefaults.titleRuleBorders()` — `Insets(NONE, NONE, THIN_LINE, NONE)`.
- Border colours: `VSTitleChromeDefaults.titleRuleColors(ctx)` — `TITLE_BORDER` `0xD9D5CC`, dark `TITLE_BORDER_DARK` `0x49454F`, written to all four sides.
- Foreground: `VSTitleChromeDefaults.titleForegroundValue(ctx)`.
- Background: **no write for the first three** — they have never carried one. For the container, `null` on both `setBackgroundValue` and `setBackground`.

**Never write a per-side border palette on a title format.** `TextBoxElementDef.setBorderColors:209-210` keeps only `topColor`; writing one colour four times is what makes that lossy setter harmless.

**A seeded colour is one-way, and the design set says so** (widget spec, the paragraph on radius sniffing): *"#D9D5CC is a palette neutral a user could legitimately pick, so a colour strip would overwrite real choices. What is missing is not the strip — it is any statement that colours are deliberately one-way."* There is no safe read-back that distinguishes a seeded `#D9D5CC` from an author's. This is an argument for seeding it once and reverting through the mark, which is what the design does — not an argument against the colour.

---

## 3. The Revert contract, per type

`VizModernizeUtil.revert:102-111` clears the mark and re-runs `seedChromeDefaults`. There is no separate reverser, so the legacy branch **is** the revert, and it must reproduce a never-modernized assembly exactly rather than nearly.

**There are two distinct legacy behaviours across the four types, not three** — corrected 2026-08-31 after the whole-branch review pointed out the miscount. The selection list, the tree and the range slider share one behaviour *exactly*; the container has the other. Three classes implement them, which is what the original "three" counted. That matters because it is the argument for hoisting the shared override, recorded as a follow-up at the end of this file. Conflating the two behaviours is still the most likely defect in this work:

- **Selection list, selection tree:** bottom-only insets, `0xc0c0c0` ×4, foreground null. No background write — restoring one would give the type a fill it has never had.
- **Range slider:** the same, **and the background stays null.** Its own comment says the clear is deliberate, to match the selection base. A legacy branch that restores `DEFAULT_TITLE_BG` here is wrong.
- **Selection container:** `DEFAULT_TITLE_BG`, four-side `THIN_LINE` insets. Leave the border *colours* alone — this type does not hit `installsOwnTitleFormat()`, so the base hook's own block at `VSAssemblyInfo:1263-1267` already writes `bcolors` onto the title border and will keep doing so on both branches.

**Both `setForegroundValue(null)` and `setForeground(null)`.** `VSFormat.getForeground()` has the same field fallback `getBackground():276` has — it returns the `fg` field when `fgval` yields nothing — so a clear that touches only the value leaves a runtime foreground standing. This does not arise at creation, where nothing has set the field; it arises on the three paths that run the hook against a live assembly: Modernize, Revert and `reseedAfterRestore`. Same for the container's background clear.

---

## 4. The ordering trap, and why it is worse here than for the chart

`VSAssemblyInfo.setDefaultFormat` ends with `seedChromeDefaults(VizContext.of(this))` (`:1233`). Any subclass doing title work *after* `super.setDefaultFormat(border)` returns is working on top of the seed. Two of the three do, and they fail differently:

**`SelectionBaseVSAssemblyInfo:877-901` installs a fresh composite.** `format = new VSCompositeFormat(); … getFormatInfo().setFormat(TITLEPATH, format)` — the seeded composite is replaced wholesale and every value the hook wrote is gone. This is the chart's exact case, and the fix is the shipped precedent at `ChartVSAssemblyInfo:99-101`: re-run `seedChromeDefaults(VizContext.of(this))` as the last statement, with the comment that the hook is a set of unconditional writes and therefore idempotent.

**`TimeSliderVSAssemblyInfo:703-716` mutates the existing composite.** The seed survives and is then overwritten field by field with the hardcoded `0xc0c0c0`. Same fix, but a failure mode that presents as "the seed never ran" when it ran and was clobbered — worth the debugging time saved by writing it down.

**`CurrentSelectionVSAssemblyInfo` does not override `setDefaultFormat`.** No trap.

**`installsOwnTitleFormat()` is not modified.** It returns true for `SelectionBaseVSAssemblyInfo` and `TimeSliderVSAssemblyInfo` already (`VSAssemblyInfo:1341-1343`), and it should keep doing so: it guards the *base's* title border-colour block, and each type's seeding lives in its own override, which that predicate does not gate. This is the same disposition the title lane design reached for the chart.

**The equality to verify:** a freshly created assembly of each type and a Modernized one must be identical. That equality is what `installsOwnTitleFormat()` exists to protect, and re-invoking the hook is what could break it.

---

## 5. Where the seeds go

- `SelectionBaseVSAssemblyInfo` — one override covering list and tree. Plus the re-run at the end of `setDefaultFormat`.
- `TimeSliderVSAssemblyInfo` — its own override. Plus the re-run.
- `CurrentSelectionVSAssemblyInfo` — its own override, the `TableDataVSAssemblyInfo:1601-1628` shape: clear the fill and write the rule on the modern branch, restore `DEFAULT_TITLE_BG` and the four-side insets on the legacy one.
- `VSTitleChromeDefaults.isSeededTitle:157` — add `SelectionBaseVSAssemblyInfo`, `TimeSliderVSAssemblyInfo` and `CurrentSelectionVSAssemblyInfo`, as three separate branches.

**The hierarchy, because two of the three are easy to get wrong.** `TimeSliderVSAssemblyInfo` and `SelectionBaseVSAssemblyInfo` are **siblings**, both extending `MaxModeSelectionVSAssemblyInfo` (which extends `SelectionVSAssemblyInfo`) — so an `instanceof SelectionBaseVSAssemblyInfo` test does not reach the range slider, and neither does an override placed on that class. This is the same trap `isCornerSeedTarget()` documents at `VSAssemblyInfo:1292-1294`, where the comment's "extends SelectionVSAssemblyInfo" is true only at a distance. And `CurrentSelectionVSAssemblyInfo extends ContainerVSAssemblyInfo`, not any selection class at all — it is grouped with this family by what its title looks like, not by its type. Note that `bypassesBaseChrome()` names `TabVSAssemblyInfo` specifically rather than `ContainerVSAssemblyInfo`, which is why the container runs the hook and the tab does not.

## Call sites to convert

Four, plus one that needs a decision rather than a conversion:

| Site | Covers | Note |
|---|---|---|
| `VSCompositeModel:38` | Selection list, tree **and** container, in one | `VSSelectionBaseModel` and `VSSelectionContainerModel` are its only subclasses — verified |
| `VSRangeSliderModel:108` | Range slider | |
| `VSSelectionListHelper:76` | List, export | |
| `VSSelectionTreeHelper:268` | Tree, export | |
| `ExportUtil.getBackGroundColor:111` | Range slider inside a current selection, PDF and SVG/PNG | **A signature widening plus a behaviour change. See below** |

### `ExportUtil.getBackGroundColor` needs its signature widened, and then changes behaviour

**It is on the two-argument form**, so it delegates with `info = null` and `isSeededTitle(null)` is false however wide that predicate grows. Widening the predicate does not reach this site and neither does converting any other call site: left alone it keeps painting `TITLE_BG` onto a seeded range slider's title format at export. The signature takes a fourth `VSAssemblyInfo` parameter, and both callers — `PDFVSExporter:653` and `SVGVSExporter:374`, each inside `writeTimeSlider(TimeSliderVSAssembly)` — already hold the info in scope. Widen rather than overload: an overload would leave a two-argument form that silently substitutes, which is the trap being closed.

Once converted, the behaviour changes. `ExportUtil:109-115` resolves a slider's export background as *title background, else object background*. The substitution used to make the title branch always win, so the exported slider title was `#F1EFEA`; with the title background null the expression falls through to `objectFormat.getBackground()` — the container's card background. That is the correct outcome for an unfilled lane, but **it must be verified by export rather than reasoned about**, on both branches and in both callers, because it is the first place in this work where removing a value changes which arm of an existing conditional runs.

---

## 6. Nested titles: the ladder already exists

A selection container draws its own title above its children, and each child draws its own. Confirmed in the browser code rather than assumed:

- **`childObjects`** — the expanded child filters — are rendered as their ordinary components with `[model]="childObject"` (`vs-selection-container-children.component.html:59-90`), so `vs-title.component.html:32-37` binds all four borders and the background from the **child's own** title format. A child selection list inside a container draws the `0xc0c0c0` rule from `SelectionBaseVSAssemblyInfo:894-899`, exactly as a standalone one does.
- **`outerSelections`** — the collapsed summary rows — are rendered by `current-selection`, fed `[titleFormat]="vsObject.objectFormat"`. That is the **container's object format**, not a title format, so these rows are untouched by this design. They already follow the modern object border colour, because the container is an `isCornerSeedTarget()` type whose object format the base hook seeds.
- **The container's own title** — `vs-selection-container.component.html:39-52`, its own `model.titleFormat`.

So legacy today already renders a filled `0xf5f5f5` container title with a four-side box, over N child rules at `0xc0c0c0`. **This design recolours an existing ladder rather than introducing one**, and it brings the container's own title into the same shape as its children instead of leaving it the odd one out. No suppression rule is designed. A populated container is a named manual check; if the recoloured ladder reads badly, that is a follow-up taken with the evidence in hand, not a conditional written speculatively into export.

---

## 7. What this does not touch

- **Density.** Nothing derived from `VSDensityDefaults.mode()` may be seeded — it reads a live org property. The 20/26/30 title lane row shipped as L′ and resolves through the read path; it is unaffected.
- **`isCornerSeedTarget()`.** Its membership is correct as it stands: list, tree and container are in, the time slider is deliberately out. This design changes title chrome, not card radius.
- **Cell and detail formats.** `SelectionBaseVSAssemblyInfo:879-887` writes the DETAIL composite's `0x2b2b2b` foreground as an unconditional creation default, and `SelectionListModel.getFormats()` already substitutes a dark foreground over it via `applyDarkForeground` — the 9B mechanism. Out of scope here and already handled.
- **The `vs-selection.component.scss` literals.** The widget spec calls that file "the largest untouched surface in the set" — five greys, a measure-bar baseline, a quick-switch overlay with its own everything. It is a separate item.

---

## 8. Test surface

- `SeedChromeDefaultsTest` — both branches for each of the three overrides, and the creation-versus-Modernize equality for the two types with the re-run.
- `VSTitleChromeDefaultsTest` — the widened `isSeededTitle`, including that `TimeSliderVSAssemblyInfo` is matched by its own branch and not by the selection-base one.
- `VSFormatModelTest` — what the browser actually receives for each of the four types, on both branches.
- `BookmarkChromeResolutionTest` — the seed resolving on restore, following the pattern the title lane work added for the chart and table.
- **Gate-off must stay byte-identical.** Every write sits inside a `ctx.modern` ternary whose legacy branch reproduces a gate-off creation. This matters more than usual right now: the plan to default `viewsheet.modernVisualization` to true is in flight alongside this, so a legacy branch that only nearly matches will stop being a rare path and start being the upgrade path.

## Manual checks

1. Each of the four types standalone, at all three densities, in the viewer — unfilled lane, one hairline at `#D9D5CC`.
2. The same four in dark — `#49454F`.
3. **A populated selection container**: its own title plus a child selection list, a child selection tree and a child range slider, and a collapsed outer selection row. This is the ladder check from §6.
4. Export the same container to PDF, PNG and Excel. The range-slider-inside-a-container background is §5's specific case.
5. Revert a dashboard holding all four and compare against one that was never modernized — the §3 contract, per type.
6. Gate off, whole matrix, against a build from before this change.
7. Export a viewsheet holding all four to a deployment JAR and import it into a second server. The portability case is why this is seeded rather than substituted.

---

## 9. What the implementation found, and what it left open

Recorded 2026-08-31, after the branch shipped as `c7790bbf0..e682a95bf` (7 commits) with the full `core`
suite at 5221 tests, 0 failures. These are the things execution discovered that this design did not
predict, kept here so the next person in this area does not rediscover them.

**The seed survives only because of a side effect nobody wrote down.** `FormatInfo.getFormat(TITLEPATH,
false)` is not a read — `FormatInfo:236-245` mutates the stored DEFAULT tier, copying the object format
down onto it via `copyDefaultFormat`, each copy guarded by `!tfmt.isXxxValueDefined()`. Every seeded
title value survives that copy-down only because its setter sets the matching `*ValDefined` flag —
including `setBackgroundValue(null)`, which still sets `bgValDefined = true`. Borders survive for a
different reason: `copyDefaultFormat` never copies borders at all. Without those side effects every seed
would be silently repainted from the object format on every read. This is now commented at
`VSTitleChromeDefaults.isSeededTitle`.

**Four call sites, not three, and the fourth was in another Maven module.**
`ExportUtil.getBackGroundColor` had a fourth caller in `utils/inetsoft-xml-formats`
(`PPTVSExporter:734`). Widening the signature rather than adding an overload turned that into a compile
error instead of a silent stale fill. **This is the second time a signature change on this branch has
reached that module** — [the title lane design](./2026-08-28-title-lane-unfilled-design.md) records the
same surprise for `getValueFormat` (Excel list, Excel tree, PPT). Treat the caller sweep as load-bearing.

**~~Open item~~ FIXED: a bookmark restore half-reverted a selection container's children.**
`CurrentSelectionVSAssembly.writeStateContent:316-331` writes each child with `assembly.writeXML`, so a
container's bookmark state carries a full `<VSCompositeFormat>` set per child — and the child's `vizMark`
with it, since the mark rides in `VSAssemblyInfo.writeAttributes:877-879` — unlike the list, tree and
slider, whose state carries no format at all. On restore, `Viewsheet.parseState` removes those children
and the container's own `parseStateContent:353-371` re-creates them with `createVSAssembly`, so **a child
never passes through `AbstractVSAssembly.parseState`** and neither the live-mark re-application nor
`reseedAfterRestore` (`:641-657`) reached it. Bookmark a dashboard holding a populated container, Revert
it, restore the bookmark, and the container's own title returned to the filled band while its children
came back modern.

**Reproduced before being fixed** — `aStaleBookmarkDoesNotUnRevertAContainersChildren` failed on
`expected: <null> but was: <MODERN_LIGHT>`, confirming the chain above rather than inferring it.

**The fix mirrors the annotation branch in the same method**, which had the identical problem and already
solved it: capture each child's live mark in the removal loop while the assembly still exists, then hand
it back and call `reseedAfterRestore` once the children have been re-created — after *both* container
`parseState` call sites, the same-class one and the class-changed one. It could not go in the container:
by the time `parseStateContent` runs the live children are gone, which is what
`CurrentSelectionVSAssembly:343-351`'s own comment means when it says the Viewsheet owns that removal.

**A child that exists only in the blob** — added by the bookmark, absent from the live sheet — has no
live mark to inherit and keeps whatever it was created with. That mirrors the annotation branch's
`containsKey` guard rather than inventing a fallback. Gate-off is unaffected for free:
`reseedAfterRestore` resolves `VizContext.of(info)` from `mark != null`, so an unmarked child takes the
legacy branch, which is what a gate-off creation writes.

**It was never a regression from this work** — before it, the children's chrome resolved through the
read-time substitution keyed on the same blob-sourced mark, producing the same half-reverted result. What
this work widened was the *scope* of what un-reverted: previously the fill and the text colour, now the
rule colour too, which was never substituted. That widening is what made it worth fixing rather than
recording.

**Follow-up: hoist the shared override.** `SelectionBaseVSAssemblyInfo.seedChromeDefaults` and
`TimeSliderVSAssemblyInfo.seedChromeDefaults` are byte-identical seven-line bodies, plus two
byte-identical four-line `legacyTitleRuleColors()` helpers — 22 duplicated lines expressing one of the
two legacy behaviours twice. Hoisting both to their shared parent `MaxModeSelectionVSAssemblyInfo` is
mechanical and safe: it has exactly those two subclasses, and it is a ~35-line max-mode mixin with no
`setDefaultFormat` or `seedChromeDefaults` of its own. It would also collapse two predicates to one
branch each — `installsOwnTitleFormat()` and `isSeededTitle()` — which is what currently requires three
separate comments warning that the two classes are siblings rather than parent and child. The **re-runs**
must stay in both subclasses either way: their reasons differ (one replaces the title composite, one
mutates it) and their `setDefaultFormat` bodies differ. Kept duplicated during execution on a cohesion
argument — that the parent owns nothing else title-related — which the review correctly judged the weaker
of the two positions.

**Two surfaces for the manual matrix that this design did not think of.** An **ad-hoc filter** has its
title background forced to white on the stored DEFAULT tier (`CoreLifecycleService:1256-1265`,
`ComposerAdhocFilterService:560-561`); the substitution used to overwrite that, so with it off the lane is
white rather than unfilled — check it in both modes. And the **container's print-layout title** will draw
a four-sided box, because `VsToReportConverter.getTitleBorders:2158-2173` takes the wider of the title's
and the object's border per side and the container is created with `border = true`. That matches what the
table has done since `f499c0ffa`, so confirm it is not something worse rather than expecting a rule.
