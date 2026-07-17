# Portal TL test conventions

Shared helpers for `*.tl.spec.ts` live here. Setup runs via `vitest-setup-tl.ts`
(`isTouchDevice` mock, **`setDragImage` stub**, `vi.useRealTimers()`, schedule
`clearStoredCondition()`, `testTimeout: 15000` buffer).

**Verification (2026-07-16):** Full portal TL suite (`ng run portal:test-tl`) — **7/7** consecutive green runs (0 failed, 0 unhandled). CI/dev entry: `npm run test:portal` (runs unit `*.spec.ts` then TL `*.tl.spec.ts`). See plan Task 5.

## Prefer

| Need | Use |
|---|---|
| Dialog / confirm / submit Promise mocks | `syncResolve` / `syncReject` from `tl-async.util.ts` |
| Drain Promise microtasks only | `flushMicrotasks` from `tl-async.util.ts` |
| DomService in providers | `createDomServiceMock()` — never `useValue: {}` |
| Pane-id / binding-state assertions | Read `fixture.componentInstance` (e.g. `editPaneId`) — do not open dropdowns via `userEvent` / `whenStable` |
| HTTP in TL | Immediate MSW responses or RxJS `of(...)` (global defaults include `date-level-examples`, `imageShapes`) |
| Timer / race / destroy+`setTimeout` | `*.spec.ts` + `vi.useFakeTimers()` / `advanceTimersByTimeAsync` |
| Viewport-sensitive placement | Pin `window.innerWidth` in the test (or `beforeEach`) |
| Schedule dialog history isolation | `clearStoredCondition()` (also run in TL `afterEach`) |

## Avoid in `*.tl.spec.ts`

1. **`await new Promise(r => setTimeout(r, 0|50))`** — hangs under Zone + a loaded Vitest worker (CI full suite).
2. **`mockResolvedValue(...)` + macrotask flush** for dialogs — use `syncResolve` instead so `from(promise)` / `.then` complete in-stack.
3. **Sleep-to-prove-absence** (double `setTimeout(0)` after destroy) — assert immediately, or move to unit specs with fake timers.
4. **Hanging MSW Promises + open-ended `waitFor`** when a synchronous emit would do.
5. **`userEvent` + `whenStable` for aesthetic pane ids** — assert `editPaneId` / method result directly.
6. **Documenting known race “broken” ordering** in the TL full suite — not stability coverage.

## Unhandled errors (tests can pass and still fail the run)

Vitest reports `Vitest caught N unhandled errors` separately from failed `it(...)` cases.
Treat them as suite defects:

| Pattern | Fix |
|---|---|
| Child `ngOnInit` HTTP (e.g. DimensionEditor → `date-level-examples`) | Mock the service in helpers **or** add a global MSW handler under `community/web/mocks/handlers/` |
| `modalService.open is not a function` | Provide `NgbModal` with `open()` (see `createNgbModalMock` in aesthetic `field-mc-test-helpers.ts`) — real `ModelService` / `ComponentTool` may call it on HTTP errors |
| Intentional 4xx/5xx still “Uncaught” | Handle in UI then complete with `EMPTY` (or subscribe `error` callback); do not `throwError` when nobody listens |
| `classList.contains` / DOM handler after another file | Document/`Renderer2.listen` must be removed in `ngOnDestroy`; null-check `event?.target` (operator precedence: wrap `\|\|` groups) |
| `domService.requestRead is not a function` (often blamed on a later file) | Global `GuiTool.setDragImage` stub in `vitest-setup-tl.ts` + `createDomServiceMock()` + null-guard in `GuiTool.setDragImage` |

Global MSW defaults that already cover common editor noise: `POST */api/date-level-examples`, `GET */api/composer/imageShapes` (`model.handlers.ts`).

## Example

```ts
import { syncResolve } from "../../../testing/tl-async.util"; // adjust depth

vi.spyOn(ComponentTool, "showConfirmDialog")
   .mockImplementation(() => syncResolve("ok"));

comp.closeTab(tab);
// no await setTimeout — assert immediately
expect(emitted).toHaveLength(1);
```

Timing regression example: see
`logical-model-attribute-dialog.component.spec.ts` (#75599), not the TL file.

## Related plan

`docs/superpowers/plans/2026-07-16-portal-tl-suite-stability.md`
