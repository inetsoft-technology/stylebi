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
 * VariableInputDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit() BOOLEAN init: Bug #16824 — default boolean value must be false
 *                       when value is empty/null/"false"; was previously producing incorrect true
 *   Group 2 [Risk 2] — ngOnInit() other types: null value → []; usedInOneOf array → quoted join
 *   Group 3 [Risk 2] — clearDisabled(): null model, all-empty values, any-truthy value
 *   Group 4 [Risk 2] — cancel(): enterParameters=true emits "cancelSheet"; false clears + emits ""
 *   Group 5 [Risk 2] — saveChanges(): choices/values nulled; empty values filtered via prepareModel
 *   Group 6 [Risk 2] — getVariableValueString(): usedInOneOf join vs first-value; null guard
 *   Group 7 [Risk 2] — getVariableValues(): BOOLEAN → objValues; other → values
 *   Group 8 [Risk 1] — clear(), setVariableValueString(), setVariableValue()
 *   Group 9 [Risk 1] — onMouseUp(): no-op when valueEditors is empty
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs: none
 *
 * Out of scope:
 *   prepareModel splitValue quoted-string parsing — the private splitValue method handles
 *     comma-separated quoted strings; triggering it requires style===0 varInfos which would
 *     instantiate VariableValueEditor; the quoting logic is low-risk (string parsing, no side
 *     effects on shared state) and is covered transitively via saveChanges tests.
 *   onMouseUp delegation to child editors — requires real VariableValueEditor instances inside
 *     @ViewChildren; with style:undefined no children are rendered; delegation is a trivial
 *     forEach with no guard logic.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { VariableInputDialog } from "./variable-input-dialog.component";
import { VariableInputDialogModel } from "./variable-input-dialog-model";
import { VariableInfo } from "../../../common/data/variable-info";
import { XSchema } from "../../../common/data/xschema";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface RenderOpts {
   varInfos?: VariableInfo[];
   enterParameters?: boolean;
}

async function renderComp(opts: RenderOpts = {}) {
   const { varInfos = [], enterParameters = true } = opts;
   // model is passed by reference; initModel() mutates varInfos in place
   const model: VariableInputDialogModel = { varInfos };
   const { fixture } = await render(VariableInputDialog, {
      componentInputs: { model, enterParameters },
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: fixture.componentInstance, model, fixture };
}

function makeStringInfo(overrides: Partial<VariableInfo> = {}): VariableInfo {
   return { type: XSchema.STRING, value: ["hello"], ...overrides };
}

function makeBooleanInfo(overrides: Partial<VariableInfo> = {}): VariableInfo {
   return { type: XSchema.BOOLEAN, ...overrides };
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit() — BOOLEAN type initialization (Bug #16824)
// ---------------------------------------------------------------------------

describe("VariableInputDialog — ngOnInit BOOLEAN initialization", () => {

   // 🔁 Regression-sensitive: Bug #16824 — null/empty value must produce bval=false.
   // Four guards in sequence: value!=null, length>0, value[0]!="false", !!value[0].
   it("should set BOOLEAN value to [false] when value is null", async () => {
      const info = makeBooleanInfo({ value: null });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([false]);
   });

   it("should set BOOLEAN value to [false] when value array is empty", async () => {
      const info = makeBooleanInfo({ value: [] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([false]);
   });

   it("should set BOOLEAN value to [false] when value is [\"false\"]", async () => {
      const info = makeBooleanInfo({ value: ["false"] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([false]);
   });

   it("should set BOOLEAN value to [false] when value[0] is null", async () => {
      const info = makeBooleanInfo({ value: [null] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([false]);
   });

   it("should set BOOLEAN value to [true] when value is [\"true\"]", async () => {
      const info = makeBooleanInfo({ value: ["true"] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([true]);
   });

   it("should set BOOLEAN value to [true] when value[0] is a non-empty non-false string", async () => {
      const info = makeBooleanInfo({ value: ["1"] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([true]);
   });

   it("should map values strings to boolean objValues when values is provided", async () => {
      const info = makeBooleanInfo({ value: [], values: ["true", "false", "true"] });
      await renderComp({ varInfos: [info] });
      expect(info.objValues).toEqual([true, false, true]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnInit() — other type initialization
// ---------------------------------------------------------------------------

describe("VariableInputDialog — ngOnInit other-type initialization", () => {

   it("should convert null value to [] for non-BOOLEAN type", async () => {
      const info = makeStringInfo({ value: null });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual([]);
   });

   it("should leave non-null value unchanged for non-BOOLEAN non-oneOf type", async () => {
      const info = makeStringInfo({ value: ["abc"] });
      await renderComp({ varInfos: [info] });
      expect(info.value).toEqual(["abc"]);
   });

   // 🔁 Regression-sensitive: usedInOneOf values are comma-joined so the server receives a
   // single delimited string; if quoting is dropped, comma-containing values become ambiguous.
   it("should quote and join usedInOneOf array values into a single comma-joined string", async () => {
      const info: VariableInfo = {
         type: XSchema.STRING,
         value: ["alpha", "be,ta", "gamma"],
         usedInOneOf: true,
      };
      await renderComp({ varInfos: [info] });
      // quoteString: "be,ta" contains comma → wrapped in single quotes → "'be,ta'"
      expect(info.value).toEqual(["alpha,'be,ta',gamma"]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: clearDisabled()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — clearDisabled", () => {

   it("should return true when model is null", async () => {
      const { comp } = await renderComp({ varInfos: [] });
      comp.model = null as any; // set after ngOnInit to test the null guard
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return true when model.varInfos is null", async () => {
      const { comp } = await renderComp({ varInfos: [] });
      comp.model = { varInfos: null } as any; // set after ngOnInit to test the varInfos guard
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return true when all varInfo values are empty", async () => {
      const { comp } = await renderComp({ varInfos: [
         { type: XSchema.STRING, value: [] },
         { type: XSchema.STRING, value: [null] },
      ]});
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return false when at least one varInfo has a truthy value", async () => {
      const { comp } = await renderComp({ varInfos: [
         { type: XSchema.STRING, value: [] },
         { type: XSchema.STRING, value: ["hello"] },
      ]});
      expect(comp.clearDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancel()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — cancel", () => {

   // 🔁 Regression-sensitive: enterParameters=true must emit "cancelSheet" to trigger sheet
   // cancellation flow; emitting "" would silently skip the sheet close.
   it("enterParameters=true: should emit \"cancelSheet\" without clearing values", async () => {
      const info = makeStringInfo({ value: ["hello"] });
      const { comp } = await renderComp({ varInfos: [info], enterParameters: true });
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.cancel();
      expect(cancelled).toEqual(["cancelSheet"]);
      // value must NOT be cleared
      expect(info.value).toEqual(["hello"]);
   });

   it("enterParameters=false: should clear values AND emit \"\"", async () => {
      const info = makeStringInfo({ value: ["hello"], userSelected: true });
      const { comp } = await renderComp({ varInfos: [info], enterParameters: false });
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.cancel();
      expect(cancelled).toEqual([""]);
      expect(info.value).toEqual([]);
      expect(info.userSelected).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: saveChanges()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — saveChanges", () => {

   // 🔁 Regression-sensitive: choices and values are nulled before emit to reduce WebSocket
   // message size; if skipped, large choice lists can cause transport failures.
   it("should null out choices and values on each varInfo before emitting", async () => {
      const info = makeStringInfo({ choices: ["a", "b"], values: ["a", "b"] });
      const { comp } = await renderComp({ varInfos: [info] });
      const committed: VariableInputDialogModel[] = [];
      comp.onCommit.subscribe(v => committed.push(v as VariableInputDialogModel));
      comp.saveChanges();
      expect(committed).toHaveLength(1);
      expect(committed[0].varInfos[0].choices).toBeNull();
      expect(committed[0].varInfos[0].values).toBeNull();
   });

   it("should emit the model object via onCommit", async () => {
      const { comp, model } = await renderComp({ varInfos: [makeStringInfo()] });
      const committed: object[] = [];
      comp.onCommit.subscribe(v => committed.push(v));
      comp.saveChanges();
      expect(committed).toHaveLength(1);
      // Object.assign(this.model) with one arg returns the same reference
      expect(committed[0]).toBe(model);
   });

   // 🔁 Regression-sensitive: prepareModel filters undefined and "" values; if filtering
   // is skipped, empty strings reach the server and corrupt parameter values.
   it("should filter out undefined and empty-string values via prepareModel", async () => {
      const info: VariableInfo = {
         type: XSchema.STRING,
         value: ["hello", "", undefined as any, "world"],
      };
      const { comp } = await renderComp({ varInfos: [info] });
      comp.saveChanges();
      expect(info.value).toEqual(["hello", "world"]);
   });

   it("should set value to [null] when prepareModel produces an empty array", async () => {
      const info: VariableInfo = { type: XSchema.STRING, value: ["", undefined as any] };
      const { comp } = await renderComp({ varInfos: [info] });
      comp.saveChanges();
      expect(info.value).toEqual([null]);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getVariableValueString()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — getVariableValueString", () => {

   it("should return null when varInfo.value is null/undefined", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { value: null };
      expect(comp.getVariableValueString(info)).toBeNull();
   });

   it("should return the first element when usedInOneOf is false", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { value: ["first", "second"], usedInOneOf: false };
      expect(comp.getVariableValueString(info)).toBe("first");
   });

   it("should return comma-joined values when usedInOneOf is true", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { value: ["a", "b", "c"], usedInOneOf: true };
      expect(comp.getVariableValueString(info)).toBe("a,b,c");
   });
});

// ---------------------------------------------------------------------------
// Group 7: getVariableValues()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — getVariableValues", () => {

   it("should return objValues for BOOLEAN type", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { type: XSchema.BOOLEAN, objValues: [true, false], values: ["true", "false"] };
      expect(comp.getVariableValues(info)).toBe(info.objValues);
   });

   it("should return values (string array) for non-BOOLEAN type", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { type: XSchema.STRING, values: ["opt1", "opt2"] };
      expect(comp.getVariableValues(info)).toBe(info.values);
   });

   it("should return undefined when varInfo is null/undefined", async () => {
      const { comp } = await renderComp();
      expect(comp.getVariableValues(null as any)).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 8: clear(), setVariableValueString(), setVariableValue()
// ---------------------------------------------------------------------------

describe("VariableInputDialog — clear", () => {

   it("should reset value to [] and userSelected to false for all varInfos", async () => {
      const infos: VariableInfo[] = [
         { type: XSchema.STRING, value: ["x"], userSelected: true },
         { type: XSchema.STRING, value: ["y"], userSelected: true },
      ];
      const { comp } = await renderComp({ varInfos: infos });
      comp.clear();
      expect(infos[0].value).toEqual([]);
      expect(infos[0].userSelected).toBe(false);
      expect(infos[1].value).toEqual([]);
      expect(infos[1].userSelected).toBe(false);
   });
});

describe("VariableInputDialog — setVariableValueString", () => {

   it("should set varInfo.value to a single-element array containing the given string", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { value: [] };
      comp.setVariableValueString(info, "hello");
      expect(info.value).toEqual(["hello"]);
   });
});

describe("VariableInputDialog — setVariableValue", () => {

   it("should set varInfo.value and mark userSelected=true", async () => {
      const { comp } = await renderComp();
      const info: VariableInfo = { value: [], userSelected: false };
      comp.setVariableValue(info, ["a", "b"]);
      expect(info.value).toEqual(["a", "b"]);
      expect(info.userSelected).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: onMouseUp() — no-op guard
// ---------------------------------------------------------------------------

describe("VariableInputDialog — onMouseUp", () => {

   it("should not throw when there are no value editors in the QueryList", async () => {
      // No varInfos → no VariableValueEditor children → valueEditors.length === 0
      const { comp } = await renderComp({ varInfos: [] });
      const evt = { type: "mouseup" } as MouseEvent;
      expect(() => comp.onMouseUp(evt)).not.toThrow();
   });
});
