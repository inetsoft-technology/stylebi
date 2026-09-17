# Handoff: StyleBI chart palette redesign

## What this is

A redesign of StyleBI's chart colour palettes for the refresh shell: **11
`ChartPalette` names reduced to 6**, plus the engine changes needed to make
them work.

This is **not a UI feature handoff.** There are no screens to rebuild. There
are two deliverables:

1. **`CSS.md`** — a drop-in replacement block for
   `core/src/main/resources/inetsoft/util/css/defaults.css`. Every hex is
   final. This is most of the visible change and needs no code.
2. **`ENGINE.md`** — seven changes in `inetsoft.graph`, ordered by
   dependency, each with its exact call site. Phase 0 unblocks the rest and is
   worth shipping alone.

`SBI Color and Type Pairings.dc.html` is the **design document** — open it in
a browser. It is the reasoning and the visual reference, not code to port. Read
it when a decision in `ENGINE.md` looks arbitrary; every one is argued there
against a real chart.

## Fidelity

High. Every colour is a final hex, derived in OKLCH and checked in gamut.
Where a value is computed by rule rather than authored, the rule is stated with
its constants so an implementation reproduces the same output.

## The six palettes

| Palette | Slots | Role |
|---|---|---|
| **Default** | 8 + 8 companions | The categorical default. Slot 3 is a near-black anchor. |
| **Soft** | 8 | Low chroma, for dashboards with many small charts. |
| **Contrast** | 8 | Projection, monochrome print, low vision. No dark variant, deliberately. |
| **Amber** | 7 | Sequential ramp. Replaces Heat 8/16/24. |
| **Teal** | 7 | Sequential ramp. Replaces Blue and Green. |
| **Variance** | 7 | Diverging. New — nothing today serves above/below target. |

Retired: Pastel (folded into companions), Heat 8/16/24, Blue, Green, Red,
Orange, Gray.

## Migration — the part with a deadline

**Nine of the eleven names stop resolving.** Only Default and Soft survive. At
the lookup site a merge is indistinguishable from a deletion, so every one of
these needs an alias if saved dashboards reference palettes by string:

| Retired | Alias to | Faithful? |
|---|---|---|
| Pastel | Default companions | ✅ intent carries |
| Heat 8 / 16 / 24 | Amber | ✅ |
| Blue, Green | Teal | ✅ |
| Red, Orange | Amber | ⚠️ lossy — different hue family |
| Gray | *(none)* | ⚠️ no successor |

Gray should probably **hard-fail with a migration warning** rather than quietly
resolve to something else. Confirmed with the team that no known dashboards
encode data with it, but a silent substitution is worse than an error.

**Open question for engineering:** are palettes referenced by name string in
saved viewsheet XML? If yes, aliases are required before release. If they are
stored by index or by resolved colour, this section is moot. This was never
resolved during design.

## Design tokens this depends on

From the shell palette spec, already implemented:

| Token | Value | Used for |
|---|---|---|
| canvas | `#F8F7F4` | Every palette is judged against this, not white |
| primary | `#E58A2A` | `--inet-viz-active-border`; **never a series colour** |
| anomaly bg | `#F7DEDE` | Variance's warm end must clear it |
| target line | `#AFAFAD` | `GDefaults.DEFAULT_TARGET_LINE_COLOR` — chrome, not data |

The amber constraint is load-bearing: six of the eleven `--inet-viz-*` state
tokens sit in a 40–110° hue wedge, so amber is the visualisation layer's state
language. A series colour in that band competes with six meanings at once.
Default's amber sits at 75° and ΔE 0.106 from the active border — close enough
to need the white-hairline treatment on active cells, far enough not to read as
state.

## Order of work

```
CSS.md                     ← ships alone, no code, most of the visible change
  └── ENGINE.md §0         ← getCompanionColor + OKLab utility
        ├── §1 target band          (wiring only, ~2 lines)
        ├── §2 brushing             (signature change)
        ├── §3 overflow generator   (shares the OKLab utility)
        └── §5 area fill            (new property; buys area + line + radar)
  §4 ramps                 ← independent; choice of frame, not new machinery
  §6 multi-level           ← largest; needs upstream plumbing first
```

## Files

| File | What it is |
|---|---|
| `CSS.md` | The palette definitions. Copy-paste. |
| `ENGINE.md` | Seven changes with call sites, constants and traps. |
| `SBI Color and Type Pairings.dc.html` | Design doc — open in a browser. |
| `github.md` | Which repo files each section was built from. |

Repo: `inetsoft-technology/stylebi`, branch `epic-74519`.
