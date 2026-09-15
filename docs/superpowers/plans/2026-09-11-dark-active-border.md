# Dark active-border value — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Give `--inet-viz-active-border` a dark value so the assembly focus outline stops drawing the light brand orange on dark cards, and make the outline's DOM position able to resolve that value.

**Architecture:** Four edits across three files. Two author the token (`_viz-tokens.scss`): a new `--inet-viz-active-border-dark` in the customer-overridable dark block, and the dark scope re-pointed at it. One re-points `.bd-selected-assembly` (`_themeable.scss`) from `--inet-primary-color` to `--inet-viz-active-border`, which is what the affordance actually is. One adds the per-assembly viz classes to the `.focus-assembly` overlay div, without which the other three are inert for the assembly outline — the overlay is a *preceding sibling* of the wrapper that carries them.

**Tech Stack:** Dart Sass (`node_modules/.bin/sass`), Angular 21 template bindings, Vitest 4.1.7 via `ng run portal:test-tl`.

**Spec:** `docs/superpowers/specs/lookfeel/2026-09-11-dark-active-border-design.md`

> **Executed, with one addition this plan did not contain.** A second defect with the same symptom —
> the light brand orange on a dark card — turned up while verifying Task 2, in a mechanism no token
> change can reach: chart mark selection is painted on a `<canvas>` from the computed style of
> `.viz-modern .chart-object-canvas`, which named `--inet-primary-color` directly. It was fixed on
> this branch rather than deferred, in the commit *"Draw chart mark selection in the selected family,
> dark only"*, and is written up in the design's **The second defect** section. It is recorded here
> rather than retrofitted as a Task 5, so the plan still reads as what was planned.
>
> The branch was later rebased onto `epic-74519` after the palette re-tune (#5188) landed there,
> which changed every `Modern Dark` head colour and so changed the ΔE figures quoted throughout the
> design. They were re-measured; the conclusions held.

## Global Constraints

- **Branch:** `feature-dark-active-border`, cut from `epic-74519`. Do not commit to `epic-74519`.
- **The value is an alias, never a literal hex:** `--inet-viz-active-border-dark: var(--inet-primary-color-dark)`. A hex would stop being the brand colour after a customer rebrands primary.
- **Border weight and style do not change.** `.bd-selected-assembly` stays `1px dotted`.
- **Light mode does not change**, in any form, at any step. Every verification below asserts this explicitly.
- **Anchor edits on surrounding text, not line numbers.** Task 1 inserts a line into `_viz-tokens.scss`, so every line number after `:62` in that file shifts by one. Line numbers in this plan describe the file *before* Task 1.
- **Repo root for all commands:** `community/web` for `sass` and `ng`; `community/` for `git`.
- **Commit trailer** on every commit: `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`
- **`git add` / `git commit` are permission-gated in this environment.** If a commit step returns `git add/commit/push requires explicit user approval`, stop and ask the operator to approve or run it, rather than retrying or working around it.

---

### Task 1: Author the dark active-border token

The deliverable is standalone: after this task `--inet-viz-active-border` resolves to `#C96F12` in dark, which already changes `.vs-combo-box-trigger:focus` (`_bootstrap-override.scss:1318`). The assembly outline does not change yet — it still reads `--inet-primary-color` directly. That happens in Task 2.

**Files:**
- Modify: `web/projects/portal/src/scss/_viz-tokens.scss` (insert after `:62`; edit `:176`)

**Interfaces:**
- Consumes: `--inet-primary-color-dark`, declared at `web/projects/portal/src/scss/_variables.scss:495` inside the `:root` block that spans `:248-614`. Verified to emit `#C96F12`.
- Produces: `--inet-viz-active-border-dark`, a `:root`-level custom property; and `--inet-viz-active-border` resolving to it inside `.viz-dark, .viz-shell-dark`. Task 2 consumes `--inet-viz-active-border`.

- [x] **Step 1: Write the failing assertion**

This change is CSS custom properties, so the falsifiable check is the compiled stylesheet. Save this script as `check-active-border.sh` in your scratch directory (not in the repo):

```bash
#!/usr/bin/env bash
# Compiles the overridable stylesheet and asserts the dark token chain.
set -u
cd "$(git rev-parse --show-toplevel)/web" || exit 2
OUT="$(mktemp -t global-XXXX).css"
npx sass --no-source-map --quiet --load-path=. --load-path=node_modules \
    projects/portal/src/global.scss "$OUT" || { echo "FAIL: sass did not compile"; exit 1; }

fail=0
check() { if grep -qF -- "$2" "$OUT"; then echo "PASS: $1"; else echo "FAIL: $1"; fail=1; fi; }

check "brand deep step still emits #C96F12"        "--inet-primary-color-dark: #C96F12;"
check "dark token declared"                        "--inet-viz-active-border-dark: var(--inet-primary-color-dark);"
check "dark scope consumes it"                     "--inet-viz-active-border: var(--inet-viz-active-border-dark);"
echo "-- light mode must be untouched --"
# :root and .viz-modern must both still resolve active-border to the light accent.
[ "$(grep -cF -- '--inet-viz-active-border: var(--inet-primary-color);' "$OUT")" = "2" ] \
  && echo "PASS: exactly 2 light active-border declarations remain" \
  || { echo "FAIL: light active-border count is $(grep -cF -- '--inet-viz-active-border: var(--inet-primary-color);' "$OUT"), expected 2"; fail=1; }
rm -f "$OUT"
exit $fail
```

- [x] **Step 2: Run it to verify it fails**

Run: `bash check-active-border.sh`

Expected, before any edit — the first and last checks pass, the two middle checks fail, and the light count is **3** not 2 (`:root`, `.viz-modern` and `.viz-dark` all currently declare the light value):

```
PASS: brand deep step still emits #C96F12
FAIL: dark token declared
FAIL: dark scope consumes it
-- light mode must be untouched --
FAIL: light active-border count is 3, expected 2
```

- [x] **Step 3: Insert the dark token**

In `web/projects/portal/src/scss/_viz-tokens.scss`, find this line in the customer-overridable dark block:

```scss
  --inet-viz-selected-border-dark: #2DD4BF;
```

Insert directly **after** it (mirroring the light block's order at `:33-44`, where `active-border` follows `selected-border`):

```scss
  // An alias, not a literal like its neighbours above: a hex here would keep drawing the old
  // orange after a customer rebrands primary. An override of this token still wins.
  --inet-viz-active-border-dark: var(--inet-primary-color-dark);
```

- [x] **Step 4: Re-point the dark scope**

In the same file, inside the `.viz-dark, .viz-shell-dark` block, find:

```scss
  --inet-viz-active-border: var(--inet-primary-color);
```

There are three occurrences of that exact text in the file — at `:38` (`:root`), `:109` (`.viz-modern`) and `:176` (the dark block). Change **only the one inside `.viz-dark, .viz-shell-dark`**, the last of the three, to:

```scss
  --inet-viz-active-border: var(--inet-viz-active-border-dark);
```

- [x] **Step 5: Run the assertion to verify it passes**

Run: `bash check-active-border.sh`

Expected — all four lines PASS, including the light-mode count dropping from 3 to exactly 2:

```
PASS: brand deep step still emits #C96F12
PASS: dark token declared
PASS: dark scope consumes it
-- light mode must be untouched --
PASS: exactly 2 light active-border declarations remain
```

If the light count reads 1, you edited `:38` or `:109` by mistake — revert and change only the occurrence inside the dark block.

- [x] **Step 6: Commit**

```bash
git add web/projects/portal/src/scss/_viz-tokens.scss
git commit -m "$(cat <<'EOF'
Give the viz active border a dark value

--inet-viz-active-border was the one token whose dark-block entry re-pointed
at the light primary accent instead of taking a dark value, so a focus
outline drew #E58A2A on a dark card. It now resolves to the brand accent's
existing deep step, #C96F12, which measures dE 0.155 from the nearest
Modern Dark member against today's 0.119 and clears the 3:1 non-text floor
at 4.23:1.

An alias rather than a literal so it follows a primary rebrand.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Point the focus outline at the active token, and make the scope reachable

These two edits ship together because either alone is wrong: re-pointing the class without the bindings leaves the outline resolving `:root`'s light orange (the overlay sits outside every dark scope), and adding the bindings without re-pointing the class changes nothing, since the class reads `--inet-primary-color` directly.

**Files:**
- Modify: `web/projects/portal/src/scss/_themeable.scss:117`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html:23`

**Interfaces:**
- Consumes: `--inet-viz-active-border` from Task 1; `VSObjectModel.vizModern` and `VSObjectModel.vizDark` (`vs-object-container.component.ts` model, declared at `vs-object-model.ts:67-68`, both `boolean`, and `vizDark` is never true unless `vizModern` is).
- Produces: nothing consumed by a later task.

- [x] **Step 1: Write the failing assertions**

Two checks, because the change has two halves. Save as `check-focus-outline.sh` in your scratch directory:

```bash
#!/usr/bin/env bash
set -u
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT/web" || exit 2
fail=0

# Half 1: the class reads the active token, not the raw accent.
OUT="$(mktemp -t global-XXXX).css"
npx sass --no-source-map --quiet --load-path=. --load-path=node_modules \
    projects/portal/src/global.scss "$OUT" || { echo "FAIL: sass did not compile"; exit 1; }
if grep -A1 -F '.bd-selected-assembly {' "$OUT" | grep -qF 'border: 1px dotted var(--inet-viz-active-border) !important;'; then
  echo "PASS: .bd-selected-assembly reads --inet-viz-active-border at 1px dotted"
else
  echo "FAIL: .bd-selected-assembly is $(grep -A1 -F '.bd-selected-assembly {' "$OUT" | tail -1 | tr -s ' ')"
  fail=1
fi
rm -f "$OUT"

# Half 2: the overlay that carries the class can resolve a per-assembly dark token.
HTML="projects/portal/src/app/vsobjects/objects/vs-object-container.component.html"
BLOCK="$(sed -n '/class="focus-assembly/,/mouseenter)="onMouseEnter/p' "$HTML")"
for cls in viz-modern viz-dark; do
  if printf '%s' "$BLOCK" | grep -qF "[class.$cls]="; then
    echo "PASS: .focus-assembly binds $cls"
  else
    echo "FAIL: .focus-assembly does not bind $cls"; fail=1
  fi
done
exit $fail
```

- [x] **Step 2: Run it to verify it fails**

Run: `bash check-focus-outline.sh`

Expected — all three FAIL:

```
FAIL: .bd-selected-assembly is  border: 1px dotted var(--inet-primary-color) !important;
FAIL: .focus-assembly does not bind viz-modern
FAIL: .focus-assembly does not bind viz-dark
```

- [x] **Step 3: Re-point the class**

In `web/projects/portal/src/scss/_themeable.scss`, find:

```scss
.bd-selected-assembly {
  border: 1px dotted var(--inet-primary-color) !important;
}
```

Replace the declaration with:

```scss
.bd-selected-assembly {
  // active, not selected: the class binds to isFocused(). --inet-viz-active-border is the
  // outline-no-fill token that names this state; --inet-viz-selected-border is the teal family.
  border: 1px dotted var(--inet-viz-active-border) !important;
}
```

Leave `.bd-selected-cell` immediately above it untouched — it is the selected family and keeps `--inet-viz-selected-border`.

- [x] **Step 4: Bind the viz classes onto the focus overlay**

In `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html`, find the opening of the focus overlay:

```html
      <div class="focus-assembly {{getAssemblyAsClass(vsObject)}}"
        [id]="getAssemblyDivId(vsObject)"
```

Insert the two bindings directly after the `class` attribute:

```html
      <div class="focus-assembly {{getAssemblyAsClass(vsObject)}}"
        [class.viz-modern]="vsObject.vizModern"
        [class.viz-dark]="vsObject.vizDark"
        [id]="getAssemblyDivId(vsObject)"
```

Bind both, not only `viz-dark`: the dark block in `_viz-tokens.scss` states the invariant that assembly wrappers carry the two together, and the sibling `.vs-object-parent-container` below already binds both. `viz-modern` is visually inert here — the light `.viz-modern` block resolves `--inet-viz-active-border` to the same value `:root` does — and the div has no children, so neither class can reach a subtree.

Do **not** add a comment inside the HTML file; this project does not comment Angular templates.

- [x] **Step 5: Run the assertions to verify they pass**

Run: `bash check-focus-outline.sh`

Expected — all three PASS:

```
PASS: .bd-selected-assembly reads --inet-viz-active-border at 1px dotted
PASS: .focus-assembly binds viz-modern
PASS: .focus-assembly binds viz-dark
```

- [x] **Step 6: Run the container's test suite for regressions**

The template edit is not itself guarded — none of the four `vs-object-container` spec files renders the template — so this run is a regression check, not a proof of the change.

Run from `community/web`:

```bash
npx ng run portal:test-tl --include='**/vs-object-container.component.*.tl.spec.ts'
```

Expected: `Test Files 3 passed (3)`, `Tests 98 passed (98)`. That is the measured baseline on this branch.

**If it reports 0 tests, the command is wrong, not the code.** `ng test portal --include='**/*.tl.spec.ts'` matches nothing and exits 0; TL specs only run under the `portal:test-tl` target.

- [x] **Step 7: Run the portal unit suite**

Run from `community/web`: `npm run test:portal`

Expected: green. This is the slow gate; it catches nothing specific to this change but is the branch's standing requirement.

- [x] **Step 8: Commit**

```bash
git add web/projects/portal/src/scss/_themeable.scss \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html
git commit -m "$(cat <<'EOF'
Draw the assembly focus outline in the dark accent

.bd-selected-assembly binds to isFocused(), so it is an active outline
rather than a selection one and belongs on --inet-viz-active-border, which
_viz-tokens.scss already defines as outline-no-fill.

The overlay that carries the class is a preceding sibling of the wrapper
holding viz-modern/viz-dark, so it sat outside every per-assembly dark scope
and no dark token could reach it. The body-level viz-shell-dark could, but
follows the org flag rather than the assembly mark, which draws the wrong
palette for a MODERN_DARK assembly in a light org. It now binds both classes
from the model, as its sibling already does.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Register the work in the roadmap

The ticket was never referenced from `chart-card-roadmap.md`, so the defect and now its fix are invisible to anyone reading the track's entry point. This task also closes a question the design answered in passing, so a future owner does not re-open it.

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` (the `## Done` table, which starts at `:1536` with its header row at `:1556-1557`; and the `## Still undecided` list)

**Interfaces:**
- Consumes: nothing. Produces: nothing.

- [x] **Step 1: Add the Done row**

The table's shape is `| Item | Commit |`, with the commit column quoting subjects in italics. Insert a new row directly beneath the header separator at `:1557`:

```markdown
| **The dark active-border value.** `--inet-viz-active-border` was the one token whose dark-block entry re-pointed at the *light* primary accent rather than taking a dark value, so every assembly's focus outline drew `#E58A2A` on a dark card while the selected cell four lines away in the same stylesheet drew teal. Predates the whole track — `0d2cdd433a`, 2024-07-12 — and is not a regression from the dark pass or the palette re-tune. **Two of the recording ticket's three framing questions fell to measurement rather than taste**: matching the cell's teal would have collapsed the active/selected split `_viz-tokens.scss:100` documents as deliberate *and* fed a family this file ranks for retirement; and going neutral, which the palette-coordination decision test argues for in the abstract, measures ΔE **0.060** against `Modern Dark`'s slate `#94A3B8` — worse than the orange it replaces. The value is `var(--inet-primary-color-dark)`, `#C96F12`, the brand accent's existing deep step: ΔE 0.155 here and 0.130 against the re-tuned palette, both clear of the design set's 0.106 line, at 4.23:1 on the card. **A dark accent has to go deeper, not lighter** — every `Modern Dark` member is high-lightness by design, so lightness is the axis that separates. The ticket also had the blast radius wrong in both directions: the class occurs **once** in the repo, not across composer and viewer (the composer solved this for its own handles long ago, at `editable-object-container.component.scss:42`), but its overlay div is a *preceding sibling* of the wrapper carrying `viz-modern`/`viz-dark`, so no dark token could reach it and the reachability half was unmentioned. Design: [the dark active-border value](./2026-09-11-dark-active-border-design.md) | *"Give the viz active border a dark value"* · *"Draw the assembly focus outline in the dark accent"* |
```

- [x] **Step 2: Annotate the teal question**

In `## Still undecided`, find the final bullet:

```markdown
- **Whether the teal selection family has an owner.** It is unchanged in `_viz-tokens.scss:51-53`, and v3
  deleted the paragraphs that tracked it without resolving it.
```

Append to it:

```markdown
  **2026-09-11: still open, and now with one fewer consumer to migrate.** The dark active-border work
  deliberately did *not* point the assembly focus outline at this family — it is an *active* affordance,
  which `_viz-tokens.scss:100` keeps on the accent on purpose, and the family's `#2DD4BF` measures
  ΔE 0.072 from the re-tuned dark palette's own teal. Pointing chrome at it would have made the
  retirement more expensive, not less.
```

- [x] **Step 3: Verify both edits landed**

Run from `community/`:

```bash
grep -c "2026-09-11-dark-active-border-design.md" docs/superpowers/specs/lookfeel/chart-card-roadmap.md
grep -c "one fewer consumer to migrate" docs/superpowers/specs/lookfeel/chart-card-roadmap.md
```

Expected: `1` and `1`.

- [x] **Step 4: Commit**

```bash
git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "$(cat <<'EOF'
Register the dark active-border work in the roadmap

The recording ticket was referenced from nowhere in the track's entry point.
Also annotates the teal-family question, which this work deliberately did
not feed, so it is not re-opened on the assumption that it was.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The manual dark matrix

This is the release gate the design and the ticket both name, and nothing above substitutes for it: the change is a colour, and no automated check in this plan looks at a rendered pixel. **Do not mark the plan complete without it.**

**Files:** none.

- [x] **Step 1: Build and start the server**

From the enterprise root, per `CLAUDE.md`:

```bash
./mvnw clean install -DskipTests
cd docker/target/docker-test && docker compose up -d
```

Access `http://localhost:8080`. If a build is already current, `npm run build` from `community/web` plus a restart is enough — the change is stylesheet and template only.

- [x] **Step 2: Check each assembly type, focused, in a dark org**

Set `viewsheet.darkMode` on the org, open a dashboard whose assemblies carry a modern mark, and click each in turn to focus it. Confirm the 1px dotted ring is the deep orange `#C96F12` and not `#E58A2A`, and that it is legible against the card:

- [x] chart
- [x] table
- [x] crosstab
- [x] calc table
- [x] selection list
- [x] selection tree
- [x] range slider
- [x] calendar
- [x] a text or gauge output assembly

- [x] **Step 3: Check the case the body-class route gets wrong**

In a **light** org, place an assembly whose own mark is `MODERN_DARK`. Focus it. The ring must be the **dark** value, following the assembly's mark — not the light orange the org gate would have given. This is the check that proves Task 2 Step 4 was necessary; if this one is wrong, the bindings did not land.

- [x] **Step 4: Confirm light mode is unchanged, and check gate-off in both orgs**

Focus assemblies in a light org, modern and gate-off both. The ring must still be `#E58A2A`. A gate-off assembly there carries neither viz class and resolves `:root`.

Then focus a **gate-off** assembly in a **dark** org. This one DOES change: with no viz class of its own it inherits the body-level `viz-shell-dark` token, so its ring becomes `#C96F12`. On its white card that is an improvement, 2.62:1 to 3.64:1. Confirm it looks deliberate rather than like a stray colour.

- [x] **Step 5: Check the token's other consumer**

Focus a combo-box assembly in dark so `.vs-combo-box-trigger:focus` applies. Its focus border changes with this work, by design. Confirm it reads as a focus state and not as a disabled or error one.

- [x] **Step 6: Record the result**

Append the outcome to the design document's Verification section — which assemblies were checked, on what build, and anything that looked wrong. If something fails, stop and report rather than patching the colour: the value came from a measurement, and changing it invalidates the design's argument.

---

## Self-Review

**Spec coverage.** All four changes in the design's "The change" section map to tasks: items 1 and 2 to Task 1, items 3 and 4 to Task 2. The design's Verification section maps to Task 2 steps 6-7 and Task 4. Its "Blast radius" claims about `.vs-combo-box-trigger:focus` and light-mode immutability are asserted in Task 1 step 5 (the count check) and Task 4 steps 4-5. Task 3, the roadmap registration, is scope this plan adds rather than something the design requests — recorded here so the addition is visible rather than looking like a spec requirement. The design's "What this leaves open" items are deliberately unimplemented and need no task.

**Placeholders.** None. Every command was executed against this branch before being written down: `sass` compiles `global.scss` in ~1.4s and emits `--inet-primary-color-dark: #C96F12`; the scoped TL run reports 98 tests across 3 files.

**Type consistency.** `vizModern` and `vizDark` are used with those exact names in Task 2, matching `vs-object-model.ts:67-68`. Token names are spelled identically in Tasks 1 and 2 and in both check scripts.

**One known fragility, stated rather than hidden.** Task 1's light-mode count check expects exactly 2 remaining `--inet-viz-active-border: var(--inet-primary-color);` declarations. If a future change adds another scope that declares the light value, that assertion needs updating — it is pinned to a count deliberately, because a count is what catches editing the wrong one of three identical lines.
