# Portal TL test conventions

Shared helpers for `*.tl.spec.ts` live here. Setup runs via `vitest-setup-tl.ts`
(`isTouchDevice` mock, `vi.useRealTimers()`, schedule `clearStoredCondition()`,
`testTimeout: 15000` buffer).

## Prefer

| Need | Use |
|---|---|
| Dialog / confirm / submit Promise mocks | `syncResolve` / `syncReject` from `tl-async.util.ts` |
| Drain Promise microtasks only | `flushMicrotasks` from `tl-async.util.ts` |
| HTTP in TL | Immediate MSW responses or RxJS `of(...)` (global defaults include `date-level-examples`, `imageShapes`) |
| Timer / race / destroy+`setTimeout` | `*.spec.ts` + `vi.useFakeTimers()` / `advanceTimersByTimeAsync` |
| Viewport-sensitive placement | Pin `window.innerWidth` in the test (or `beforeEach`) |
| Schedule dialog history isolation | `clearStoredCondition()` (also run in TL `afterEach`) |

## Avoid in `*.tl.spec.ts`

1. **`await new Promise(r => setTimeout(r, 0))`** — hangs under Zone + a loaded Vitest worker.
2. **`mockResolvedValue(...)` + macrotask flush** for dialogs — use `syncResolve` instead so `from(promise)` / `.then` complete in-stack.
3. **Sleep-to-prove-absence** (double `setTimeout(0)` after destroy) — move to unit specs with fake timers.
4. **Hanging MSW Promises + open-ended `waitFor`** when a synchronous emit would do.
5. **Documenting known race “broken” ordering** in the TL full suite — not stability coverage.

## Unhandled errors (tests can pass and still fail the run)

Vitest reports `Vitest caught N unhandled errors` separately from failed `it(...)` cases.
Treat them as suite defects:

| Pattern | Fix |
|---|---|
| Child `ngOnInit` HTTP (e.g. DimensionEditor → `date-level-examples`) | Mock the service in helpers **or** add a global MSW handler under `community/web/mocks/handlers/` |
| `modalService.open is not a function` | Provide `NgbModal` with `open()` (see `createNgbModalMock` in aesthetic `field-mc-test-helpers.ts`) — real `ModelService` / `ComponentTool` may call it on HTTP errors |
| Intentional 4xx/5xx still “Uncaught” | Handle in UI then complete with `EMPTY` (or subscribe `error` callback); do not `throwError` when nobody listens |

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
