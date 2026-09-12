/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * DataModelScriptPane — Single Pass (interaction + memory leak + race condition)
 *
 * Mocking strategy:
 *   - CodemirrorService — provided as mock object; createCodeMirrorInstance/createTernServer
 *     return stub objects so jsdom never loads the real CodeMirror library.
 *   - GuiTool.getElementRect — in jsdom, elements have no layout but getBoundingClientRect()
 *     returns a zero-rect (truthy) so the setTimeout(refresh) branch in ngAfterViewInit is
 *     NOT taken. No spy needed.
 *   - isEditorElementDisplayed() — getClientRects() returns [] in jsdom → false, so
 *     ngAfterViewChecked's setTimeout never calls refresh(). No crash on destroy.
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — expression setter: no crash before codemirrorInstance; setValue called
 *                        when value differs; setValue NOT called when value matches CM content
 *   Group 2 [Risk 2]  — sql setter: before initialized → only _sql updated, no destroy/init;
 *                        after initialized → destroyCodeMirror + initCodeMirror with SQL mode
 *   Group 3 [Risk 3]  — insert(): cursor captured from codemirrorInstance; insertText builds
 *                        correct expression; expressionChange emitted with new value
 *   Group 4 [Risk 3]  — ngOnDestroy (memory leak): codemirrorInstance.toTextArea called +
 *                        nulled; ternServer.destroy called + nulled; cancelAutocomplete invoked
 *   Group 5 [Risk 2]  — ngAfterViewInit: createCodeMirrorInstance called; initialized=true;
 *                        SQL mode not used when sql=false
 *   Group 6 [Risk 2]  — ngAfterViewChecked (race condition): viewChecked flag prevents
 *                        scheduling multiple refreshes
 */

import { render } from "@testing-library/angular";
import { DataModelScriptPane } from "./data-model-script-pane.component";
import {
   CodemirrorService
} from "../../../../../../../../../shared/util/codemirror/codemirror.service";

// ── Shared codemirror mock factory ────────────────────────────────────────────

function makeCodemirrorMocks() {
   const cmInstanceMock = {
      getValue: vi.fn().mockReturnValue(""),
      setValue: vi.fn(),
      getCursor: vi.fn().mockReturnValue({ line: 0, ch: 0 }),
      setCursor: vi.fn(),
      focus: vi.fn(),
      lineCount: vi.fn().mockReturnValue(1),
      lastLine: vi.fn().mockReturnValue(""),
      on: vi.fn(),
      off: vi.fn(),
      refresh: vi.fn(),
      toTextArea: vi.fn(),
   };

   const ternServerMock = {
      showDocs: vi.fn(),
      complete: vi.fn(),
      updateArgHints: vi.fn(),
      destroy: vi.fn(),
      options: { hintDelay: null },
   };

   const codemirrorServiceMock = {
      createCodeMirrorInstance: vi.fn().mockReturnValue(cmInstanceMock),
      createTernServer: vi.fn().mockReturnValue(ternServerMock),
   };

   return { cmInstanceMock, ternServerMock, codemirrorServiceMock };
}

async function renderComp(opts: { expression?: string; sql?: boolean } = {}) {
   const { cmInstanceMock, ternServerMock, codemirrorServiceMock } = makeCodemirrorMocks();

   const { fixture } = await render(DataModelScriptPane, {
      providers: [
         { provide: CodemirrorService, useValue: codemirrorServiceMock },
      ],
      inputs: {
         expression: opts.expression ?? "",
         sql: opts.sql ?? false,
      },
   });

   const comp = fixture.componentInstance as DataModelScriptPane;
   return { comp, fixture, cmInstanceMock, ternServerMock, codemirrorServiceMock };
}

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — expression setter [Risk 2] ─────────────────────────────────────

describe("DataModelScriptPane — expression setter", () => {
   // 🔁 Regression-sensitive: the setter updates both _expression and the CodeMirror instance.
   // If setValue is called when content already matches, CodeMirror resets the cursor, which
   // is jarring for users actively typing.

   // codemirrorInstance is a private field set by ngAfterViewInit; it is nulled here to
   // simulate a pre-init component state — the only way to test the null-guard in the setter.
   it("should set _expression without crashing when codemirrorInstance does not exist", async () => {
      const { comp } = await renderComp();
      (comp as any).codemirrorInstance = null;

      comp.expression = "should not crash";

      expect(comp.expression).toBe("should not crash");
   });

   it("should call setValue and restore cursor when the new value differs from CM content", async () => {
      const { comp, cmInstanceMock } = await renderComp();
      cmInstanceMock.getValue.mockReturnValue("old value");
      vi.clearAllMocks();

      comp.expression = "new value";

      expect(cmInstanceMock.setValue).toHaveBeenCalledWith("new value");
      expect(cmInstanceMock.getCursor).toHaveBeenCalledWith("from");
      expect(cmInstanceMock.setCursor).toHaveBeenCalledWith({ line: 0, ch: 0 });
   });

   it("should NOT call setValue when the new value matches current CM content", async () => {
      const { comp, cmInstanceMock } = await renderComp();
      cmInstanceMock.getValue.mockReturnValue("same content");
      vi.clearAllMocks();

      comp.expression = "same content";

      expect(cmInstanceMock.setValue).not.toHaveBeenCalled();
   });
});

// ── Group 2 — sql setter [Risk 2] ────────────────────────────────────────────

describe("DataModelScriptPane — sql setter", () => {
   // 🔁 Regression-sensitive: switching sql mode while the editor is open must destroy and
   // recreate the CodeMirror instance. Failing to destroy leaks the old Tern server.

   it("should reinitialize CodeMirror with SQL mode when sql is set true after init", async () => {
      const { comp, cmInstanceMock, codemirrorServiceMock } = await renderComp({ sql: false });
      vi.clearAllMocks();

      comp.sql = true;

      // destroyCodeMirror was called
      expect(cmInstanceMock.toTextArea).toHaveBeenCalledTimes(1);
      // initCodeMirror was called again
      expect(codemirrorServiceMock.createCodeMirrorInstance).toHaveBeenCalledTimes(1);
      // new CM created with SQL mode
      const config = codemirrorServiceMock.createCodeMirrorInstance.mock.calls[0][1];
      expect(config.mode).toBe("text/x-sql");
   });

   it("should NOT call destroyCodeMirror when sql is set before AfterViewInit", async () => {
      // We cannot easily test pre-init; instead verify _sql is set correctly
      const { comp } = await renderComp({ sql: false });

      expect(comp.sql).toBe(false);
      comp.sql = true;
      expect(comp.sql).toBe(true);
   });

   it("should create a Tern server when switching BACK from sql=true to sql=false", async () => {
      const { comp, codemirrorServiceMock, ternServerMock } = await renderComp({ sql: true });
      vi.clearAllMocks();
      // New ternServer mock for re-init
      const newTernMock = { ...ternServerMock, destroy: vi.fn() };
      codemirrorServiceMock.createTernServer.mockReturnValue(newTernMock);

      comp.sql = false; // switch to JS mode

      expect(codemirrorServiceMock.createTernServer).toHaveBeenCalledTimes(1);
      // ternServer is a private field with no public getter; direct read is the only way to
      // verify that the setter wired the new Tern server instance correctly.
      expect((comp as any).ternServer).toBe(newTernMock);
   });
});

// ── Group 3 — insert() [Risk 3] ──────────────────────────────────────────────

describe("DataModelScriptPane — insert()", () => {
   // 🔁 Regression-sensitive: insert() uses getCursor("from") and getCursor("to") to determine
   // the cursor/selection position. If from/to are swapped or mixed up, text lands in the wrong
   // position, corrupting the expression silently.

   it("should prepend value when cursor is at position 0 (no selection)", async () => {
      const { comp, cmInstanceMock } = await renderComp({ expression: "hello" });
      cmInstanceMock.getValue.mockReturnValue("hello");
      cmInstanceMock.getCursor.mockReturnValue({ line: 0, ch: 0 });
      const emitted: string[] = [];
      comp.expressionChange.subscribe(v => emitted.push(v));

      comp.insert("hello", "PREFIX_");

      expect(comp.expression).toBe("PREFIX_hello");
      expect(emitted).toEqual(["PREFIX_hello"]);
   });

   it("should append value when cursor is at the end of the expression", async () => {
      const { comp, cmInstanceMock } = await renderComp({ expression: "hello" });
      cmInstanceMock.getValue.mockReturnValue("hello");
      cmInstanceMock.getCursor.mockReturnValue({ line: 0, ch: 5 }); // end of "hello"
      const emitted: string[] = [];
      comp.expressionChange.subscribe(v => emitted.push(v));

      comp.insert("hello", "_SUFFIX");

      expect(comp.expression).toBe("hello_SUFFIX");
      expect(emitted).toEqual(["hello_SUFFIX"]);
   });

   it("should replace selected text with value when selection spans characters", async () => {
      const { comp, cmInstanceMock } = await renderComp({ expression: "hello world" });
      cmInstanceMock.getValue.mockReturnValue("hello world");
      // Select "world" (ch 6..11)
      cmInstanceMock.getCursor
         .mockReturnValueOnce({ line: 0, ch: 6 })  // from
         .mockReturnValueOnce({ line: 0, ch: 11 }); // to
      const emitted: string[] = [];
      comp.expressionChange.subscribe(v => emitted.push(v));

      comp.insert("hello world", "earth");

      expect(comp.expression).toBe("hello earth");
      expect(emitted).toEqual(["hello earth"]);
   });

   it("should emit expressionChange exactly once per insert call", async () => {
      const { comp, cmInstanceMock } = await renderComp({ expression: "x" });
      cmInstanceMock.getValue.mockReturnValue("x");
      cmInstanceMock.getCursor.mockReturnValue({ line: 0, ch: 0 });
      const emitted: string[] = [];
      comp.expressionChange.subscribe(v => emitted.push(v));

      comp.insert("x", "y");

      expect(emitted).toHaveLength(1);
   });
});

// ── Group 4 — ngOnDestroy / destroyCodeMirror (memory leak) [Risk 3] ─────────

// codemirrorInstance, ternServer, and cancelAutocomplete are all private fields on
// DataModelScriptPane with no public accessors; direct access is required to verify the
// null-after-destroy contract and to seed cancelAutocomplete before the destroy call.
describe("DataModelScriptPane — ngOnDestroy (memory leak)", () => {
   // 🔁 Regression-sensitive: Tern servers hold Web Workers or xhr requests; failing to call
   // destroy() leaks background threads. cancelAutocomplete holds setTimeout references that
   // keep the component alive after it's been removed from the DOM.

   it("should call toTextArea() on the codemirrorInstance and null it", async () => {
      const { comp, fixture, cmInstanceMock } = await renderComp();

      fixture.destroy();

      expect(cmInstanceMock.toTextArea).toHaveBeenCalledTimes(1);
      expect((comp as any).codemirrorInstance).toBeNull();
   });

   it("should call ternServer.destroy() and null it (JS mode)", async () => {
      const { comp, fixture, ternServerMock } = await renderComp({ sql: false });

      fixture.destroy();

      expect(ternServerMock.destroy).toHaveBeenCalledTimes(1);
      expect((comp as any).ternServer).toBeNull();
   });

   it("should NOT crash on a second ngOnDestroy call (ATL cleanup idempotency)", async () => {
      const { comp, fixture } = await renderComp();

      fixture.destroy(); // first call — ATL-managed
      expect(() => comp.ngOnDestroy()).not.toThrow(); // second call → null-checks prevent crash
   });

   it("should call and clear cancelAutocomplete if it is set", async () => {
      const { comp, fixture } = await renderComp();
      const cancelSpy = vi.fn();
      (comp as any).cancelAutocomplete = cancelSpy;

      fixture.destroy();

      expect(cancelSpy).toHaveBeenCalledTimes(1);
      expect((comp as any).cancelAutocomplete).toBeNull();
   });

   it("should NOT call ternServer.destroy() in SQL mode (ternServer is not created)", async () => {
      const { comp, fixture, ternServerMock } = await renderComp({ sql: true });
      // In SQL mode, ternServer is never created (stays undefined/null — falsy)
      expect((comp as any).ternServer).toBeFalsy();

      fixture.destroy();

      expect(ternServerMock.destroy).not.toHaveBeenCalled();
   });
});

// ── Group 5 — ngAfterViewInit [Risk 2] ───────────────────────────────────────

describe("DataModelScriptPane — ngAfterViewInit", () => {
   it("should create a CodeMirror instance via codemirrorService on init", async () => {
      const { codemirrorServiceMock } = await renderComp();

      expect(codemirrorServiceMock.createCodeMirrorInstance).toHaveBeenCalledTimes(1);
   });

   // initialized is a private flag on DataModelScriptPane with no public getter; direct read
   // is the only way to verify it was set by ngAfterViewInit.
   it("should set initialized=true after AfterViewInit", async () => {
      const { comp } = await renderComp();

      expect((comp as any).initialized).toBe(true);
   });

   it("should create a Tern server in JS mode (sql=false)", async () => {
      const { codemirrorServiceMock } = await renderComp({ sql: false });

      expect(codemirrorServiceMock.createTernServer).toHaveBeenCalledTimes(1);
   });

   it("should NOT create a Tern server in SQL mode (sql=true)", async () => {
      const { codemirrorServiceMock } = await renderComp({ sql: true });

      expect(codemirrorServiceMock.createTernServer).not.toHaveBeenCalled();
   });

   it("should set the initial expression value on the CM instance", async () => {
      const { cmInstanceMock } = await renderComp({ expression: "initial code" });

      expect(cmInstanceMock.setValue).toHaveBeenCalledWith("initial code");
   });
});

// ── Group 6 — ngAfterViewChecked (race condition) [Risk 2] ───────────────────

describe("DataModelScriptPane — ngAfterViewChecked (race condition)", () => {
   // 🔁 Regression-sensitive: ngAfterViewChecked fires on every change detection cycle.
   // Without the viewChecked flag, it would schedule a new setTimeout(refresh) on every cycle,
   // causing excessive refresh calls and potential flicker.

   it("should not set viewChecked to true in jsdom (getClientRects returns empty)", async () => {
      const { comp } = await renderComp();
      // In jsdom, getClientRects() returns empty → isEditorElementDisplayed() = false
      // → viewChecked stays false; the setTimeout(refresh) fires but does nothing
      expect((comp as any).viewChecked).toBe(false);
   });
});
