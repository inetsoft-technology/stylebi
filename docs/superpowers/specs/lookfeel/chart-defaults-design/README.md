# Chart defaults design for modern visualization

This bundle is a **design**. It describes how StyleBI charts should look by default under the modern mark, why, and what each change would touch. It is not a spec and not an implementation plan. It is meant as input for engineers, and their Claude sessions, to write a spec and plans from.

## What is in it

| Path | What |
|---|---|
| `index.html` | Landing page with links to everything. |
| `common/` | The common rules, C1 to C24, with the build list, palette, line, text and legend specs. |
| `financial/`, `bar/`, `line/`, `point/`, `pie/`, `radar/`, `hierarchy/`, `relation/`, `gantt/`, `funnel-waterfall/`, `geo/` | One page per chart family: decisions, drawn proposed look in light and dark, shipped-versus-proposed table, live captures of every variant, build items. Each folder has its `img/`. |
| `md/` | A markdown version of every page, with the same text, tables and image links. The drawn cards are only in the HTML. |
| `open-questions-for-engineering.md` | What the design could not settle without the code: build items not traced to code, risks to check, and items blocked on the product or the build. |
| `repro/` | Data, chart plans and scripts to rebuild the captures. Optional. |

## How to read it

1. Read `common/common.html` (or `md/common.md`) first. Each rule has an ID. The family pages cite them ("follows C5") and only describe what differs.
2. Read the page for the family you are working on, starting with its Decisions.
3. Read `open-questions-for-engineering.md` before writing anything. It is the list of things to find out in the code.

## Labels

- **Decided or Confirmed on <date>**: the design owner agreed. Treat as a requirement.
- **Observed**: seen on a live chart on a server dashboard, with every style and format at its default, on made-up data. Captures come from the composer design view. Some were also compared with Preview and the portal viewer; the pages say which.
- **Traced**: read from the code.
- **Not traced**: the design names a behaviour but nobody found where it is set. The first job is to find it.
- **Seed only, Needs provenance, New behaviour**: the build classes defined in the common page. Seed only is a default-tier write under the modern mark. Needs provenance is a plain field that cannot tell a seeded value from an author's. New behaviour is a new property or layout rule.

## Constraints that apply to every change

- Only charts on a dashboard marked modern change. Classic charts keep their current look.
- An author's own value always wins over a default, and Revert must restore the old look. A Modernize and Revert round trip is part of every item.
- Defaults come from seeding the default tier of a value, not from changing constants that saved charts read.
- The translucent card is not in the build yet. Contrast figures use an estimate until it lands.

## Suggested start for a Claude session

> Read `common/common.html` and `open-questions-for-engineering.md`. Then read the page for the <family> family. Draft a design spec for the <family> changes in the repo's spec format. List each build item with the file or class it touches, resolving every "not traced" item from the code first. Do not change any decision. Ask when a decision seems to conflict with the code.

## Existing work to check for overlap

The repo's `docs/superpowers` folder already holds related work. The design was written without reconciling against it, so check these before writing a spec:

- density padding (C19 here), including its export and selection-family follow-ups
- corner rounding (C22 here: the plan seeds 0.3, the design changes the default to about 0.08 with a 2px floor and a quarter-thickness cap)
- the modern categorical palette, palette tail and retune, companion colours and ramps (C11, C12, C23)
- the translucent card default and the chart-card design docs (C1 and C8; C8 here reverses part of the PR #7319 spec by dropping the legend fill)
- smooth lines (the line family page changes the default to straight segments)

## Status when this was written

All decisions on every page are confirmed except where a page says otherwise. Comparisons of some captures with Preview and the portal viewer are still pending: the geo captures, the small-card captures on the funnel, waterfall, pareto, marimekko and Gantt pages, and the new radar captures. Two items are blocked: the per-panel value range on the financial page waits on the product, and the translucent-card contrast figures wait on the build.
