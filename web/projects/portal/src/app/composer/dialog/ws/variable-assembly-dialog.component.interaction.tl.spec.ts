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
 * VariableAssemblyDialog — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit: fetches model via HTTP when model absent;
 *                        uses existing model when already set; passes variableName in params
 *   Group 2  [Risk 3] — initForm: form controls with correct initial state;
 *                        type/none/selectionList valueChanges disable sibling controls
 *   Group 3  [Risk 3] — saveChanges: merges form into model, trims name, emits onCommit
 *   Group 4  [Risk 2] — checkOuterMirror: sets outerMirror=true and valid()=false for outer-mirror variable
 *   Group 5  [Risk 2] — showVariableListDialog: modal resolves → selectionList = "embedded", model updated
 *   Group 6  [Risk 2] — showVariableTableListDialog: modal resolves → selectionList = "query", model updated
 *   Group 7  [Risk 2] — valid / okDisabled: outerMirror blocks; embeddedValid path; queryValid path
 *   Group 8  [Risk 2] — validateVariableList: removes pairs where both label and value are null
 *   Group 9  [Risk 2] — selectDefaultValueType / defaultExpressionValueChange: EXPRESSION ↔ VALUE switching
 *   Group 10 [Risk 1] — initDefaultValueType: sets defaultValueType from model.defaultValue.jsonType
 *   Group 11 [Risk 1] — getVariableTree / getVariableTreeModel: returns tree with variable nodes
 *   Group 12 [Risk 2] — Bug #20319: variableSpecialCharacters — &$@+ allowed, a% not
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (current behavior asserted — test will break if the bug is fixed):
 *   Suspicion A — validateVariableList trims whitespace-only values to "" but still keeps the pair
 *     (value != null check passes for ""), so a whitespace-only value with a null label is retained.
 *     See Group 8 negative-assertion test.
 *
 * Out of scope this pass (covered in variable-assembly-dialog.component.risk.tl.spec.ts):
 *   cancelChanges
 */

import { of } from "rxjs";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { XSchema } from "../../../common/data/xschema";
import {
   makeComponent,
   makeModel,
   makeModelService,
   makeModalService,
   makeWorksheet,
} from "./variable-assembly-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: HTTP model fetch
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: HTTP model fetch", () => {
   // 🔁 Regression-sensitive: form is only built after model is received; early access breaks
   it("should fetch model via HTTP when model is not pre-set and build the form", () => {
      const modelSvc = makeModelService();
      const modalSvc = makeModalService();
      const m = makeModel({ oldName: "fetchedVar" });
      modelSvc.getModel.mockReturnValue(of(m));

      const { comp } = makeComponent({ modelSvc, modalSvc });

      expect(modelSvc.getModel).toHaveBeenCalled();
      expect(comp.model).toBe(m);
      expect(comp.form).toBeTruthy();
      expect(comp.form.get("newName").value).toBe("fetchedVar");
   });

   it("should use pre-set model without HTTP call when model is already set", () => {
      const modelSvc = makeModelService();
      const { comp } = makeComponent({ presetModel: makeModel({ oldName: "presetVar" }), modelSvc });

      expect(modelSvc.getModel).not.toHaveBeenCalled();
      expect(comp.form.get("newName").value).toBe("presetVar");
   });

   it("should include variableName in HTTP params when variableName input is set", () => {
      const modelSvc = makeModelService();
      modelSvc.getModel.mockReturnValue(of(makeModel()));
      makeComponent({ modelSvc, variableName: "mySpecificVar" });

      const [, params] = modelSvc.getModel.mock.calls[0];
      expect(params.get("variable")).toBe("mySpecificVar");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — initForm: form controls and reactive wiring
// ---------------------------------------------------------------------------

describe("Group 2 — initForm: form control initial state and reactive wiring", () => {
   it("should create form with initial values from the model", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({ oldName: "myVar", selectionList: "embedded", none: false }),
      });

      expect(comp.form.get("newName").value).toBe("myVar");
      expect(comp.form.get("selectionList").value).toBe("embedded");
      expect(comp.form.get("none").value).toBe(false);
   });

   it("should disable defaultValue when none=true on init", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ none: true }) });
      expect(comp.form.get("defaultValue").disabled).toBe(true);
   });

   it("should disable displayStyle when selectionList is none on init", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ selectionList: "none" }) });
      expect(comp.form.get("displayStyle").disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: type change must clear embedded list so stale entries don't persist
   it("should clear variableListDialogModel labels/values and update dataType when type changes", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({
            variableListDialogModel: { labels: ["L1"], values: ["V1"], dataType: XSchema.STRING },
         }),
      });

      comp.form.get("type").setValue(XSchema.INTEGER);

      expect(comp.model.variableListDialogModel.labels).toEqual([]);
      expect(comp.model.variableListDialogModel.values).toEqual([]);
      expect(comp.model.variableListDialogModel.dataType).toBe(XSchema.INTEGER);
   });

   it("should set defaultValue to false when type changes to boolean and none is false", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ none: false }) });

      comp.form.get("type").setValue("boolean");

      expect(comp.form.get("defaultValue").value).toBe(false);
   });

   it("should disable defaultValue and set it to null when none changes to true", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ none: false }) });
      comp.form.get("defaultValue").setValue("initial");
      comp.form.get("none").setValue(true);

      expect(comp.form.get("defaultValue").disabled).toBe(true);
      expect(comp.form.get("defaultValue").value).toBeNull();
   });

   it("should enable displayStyle when selectionList changes from none to embedded", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ selectionList: "none" }) });
      expect(comp.form.get("displayStyle").disabled).toBe(true);

      comp.form.get("selectionList").setValue("embedded");
      expect(comp.form.get("displayStyle").disabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — saveChanges: payload, trim, emit
// ---------------------------------------------------------------------------

describe("Group 3 — saveChanges: merges model, trims name, emits onCommit", () => {
   // 🔁 Regression-sensitive: controller path must match server endpoint exactly
   it("should emit onCommit with merged model and correct controller path", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ oldName: "myVar" }) });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(emitSpy).toHaveBeenCalledWith({
         model: expect.objectContaining({ oldName: "myVar" }),
         controller: "/events/ws/dialog/variable-assembly-dialog-model",
      });
   });

   it("should trim leading and trailing whitespace from newName before emitting", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.form.get("newName").setValue("  spacedName  ");

      comp.saveChanges();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.model.newName).toBe("spacedName");
   });

   it("should preserve expression defaultValue when defaultValueType is EXPRESSION", () => {
      const exprValue = { type: ExpressionType.JS, expression: "now()", jsonType: "expression" };
      const { comp } = makeComponent({ presetModel: makeModel({ defaultValue: exprValue }) });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.defaultValueType = ConditionValueType.EXPRESSION;
      comp.saveChanges();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.model.defaultValue).toEqual(exprValue);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — checkOuterMirror
// ---------------------------------------------------------------------------

describe("Group 4 — checkOuterMirror: outer mirror detection", () => {
   // 🔁 Regression-sensitive: outer mirror must block ok; missing this check allows bad saves
   it("should set outerMirror=true for a variable with outerMirror mirrorInfo", () => {
      const ws = makeWorksheet({
         variables: [{ name: "testVar", info: { mirrorInfo: { outerMirror: true } } }] as any,
      });
      const { comp } = makeComponent({ presetModel: makeModel({ oldName: "testVar" }), worksheet: ws });

      expect(comp.outerMirror).toBe(true);
   });

   it("should leave outerMirror falsy when the variable has no mirrorInfo", () => {
      const ws = makeWorksheet({
         variables: [{ name: "testVar", info: {} }] as any,
      });
      const { comp } = makeComponent({ presetModel: makeModel({ oldName: "testVar" }), worksheet: ws });

      expect(comp.outerMirror).toBeFalsy();
   });

   it("should make valid() return false when outerMirror is true", () => {
      const ws = makeWorksheet({
         variables: [{ name: "testVar", info: { mirrorInfo: { outerMirror: true } } }] as any,
      });
      const { comp } = makeComponent({ presetModel: makeModel({ oldName: "testVar" }), worksheet: ws });

      expect(comp.valid()).toBe(false);
      expect(comp.okDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — showVariableListDialog
// ---------------------------------------------------------------------------

describe("Group 5 — showVariableListDialog: opens modal, resolves to embedded", () => {
   it("should set selectionList to embedded and update variableListDialogModel on modal resolve", async () => {
      const resolvedList = { labels: ["L1"], values: ["V1"], dataType: XSchema.STRING };
      const { comp, modalSvc } = makeComponent({ presetModel: makeModel() });
      modalSvc.open.mockReturnValue({ result: Promise.resolve(resolvedList) });

      comp.showVariableListDialog();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(comp.form.get("selectionList").value).toBe("embedded");
      expect(comp.model.variableListDialogModel).toBe(resolvedList);
   });

   it("should not change selectionList when modal is dismissed", async () => {
      const { comp, modalSvc } = makeComponent({ presetModel: makeModel({ selectionList: "none" }) });
      modalSvc.open.mockReturnValue({ result: Promise.reject("dismissed") });

      comp.showVariableListDialog();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(comp.form.get("selectionList").value).toBe("none");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — showVariableTableListDialog
// ---------------------------------------------------------------------------

describe("Group 6 — showVariableTableListDialog: opens modal, resolves to query", () => {
   it("should set selectionList to query and update variableTableListDialogModel on resolve", async () => {
      const tableModel = { tableName: "tbl", label: "col1", value: "col2" };
      const { comp, modalSvc } = makeComponent({ presetModel: makeModel() });
      modalSvc.open.mockReturnValue({ result: Promise.resolve(tableModel) });

      comp.showVariableTableListDialog();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(comp.form.get("selectionList").value).toBe("query");
      expect(comp.model.variableTableListDialogModel).toBe(tableModel);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — valid / okDisabled
// ---------------------------------------------------------------------------

describe("Group 7 — valid / okDisabled: condition paths", () => {
   it("should return false (okDisabled=true) when form is invalid (required field empty)", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });
      comp.form.get("newName").setValue("");

      expect(comp.valid()).toBe(false);
      expect(comp.okDisabled()).toBe(true);
   });

   it("should return false when selectionList is embedded but variableListDialogModel has no valid pairs", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({
            selectionList: "embedded",
            variableListDialogModel: { labels: [], values: [], dataType: XSchema.STRING },
         }),
      });
      // manually update the form control to match
      comp.form.get("selectionList").setValue("embedded");

      expect(comp.valid()).toBe(false);
   });

   it("should return false when selectionList is query but variableTableListDialogModel is null", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({ selectionList: "query", variableTableListDialogModel: null }),
      });
      comp.form.get("selectionList").setValue("query");

      expect(comp.valid()).toBe(false);
   });

   it("should return true (okDisabled=false) with a valid form and none selectionList", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ selectionList: "none", none: true }) });

      expect(comp.valid()).toBe(true);
      expect(comp.okDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — validateVariableList
// ---------------------------------------------------------------------------

describe("Group 8 — validateVariableList: removes null-null pairs", () => {
   // 🔁 Regression-sensitive: null pairs corrupt the server-side list model
   it("should remove pairs where both label and value are null", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({
            selectionList: "none",
            none: true,
            variableListDialogModel: {
               labels: ["L1", null, "L3"],
               values: ["V1", null, "V3"],
               dataType: XSchema.STRING,
            },
         }),
      });
      vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(comp.model.variableListDialogModel.labels).toEqual(["L1", "L3"]);
      expect(comp.model.variableListDialogModel.values).toEqual(["V1", "V3"]);
   });

   it("should keep pairs where only one of label/value is null", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({
            selectionList: "none",
            none: true,
            variableListDialogModel: {
               labels: ["L1", null],
               values: ["V1", "V2"],
               dataType: XSchema.STRING,
            },
         }),
      });
      vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(comp.model.variableListDialogModel.labels.length).toBe(2);
   });

   // Suspicion A: validateVariableList trims a whitespace-only value to "" but the
   // `"" != null` check keeps the (null, "") pair — it should be treated as empty
   // and removed. This test documents the current (buggy) behavior; if it starts
   // failing the bug has been fixed and the assertion should flip to length === 0.
   it("currently retains a pair with null label and whitespace-only value (Suspicion A)", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({
            selectionList: "none",
            none: true,
            variableListDialogModel: {
               labels: [null],
               values: ["   "],
               dataType: XSchema.STRING,
            },
         }),
      });
      vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      // Bug: trimmed "" passes `!= null` so the (null, "") pair survives the filter.
      expect(comp.model.variableListDialogModel.labels.length).toBe(1);
      expect(comp.model.variableListDialogModel.values[0]).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — selectDefaultValueType / defaultExpressionValueChange
// ---------------------------------------------------------------------------

describe("Group 9 — selectDefaultValueType / defaultExpressionValueChange: EXPRESSION ↔ VALUE", () => {
   it("should set model.defaultValue to ExpressionValue and clear defaultValue form control when switching to EXPRESSION", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });

      comp.selectDefaultValueType(ConditionValueType.EXPRESSION);

      expect(comp.defaultValueType).toBe(ConditionValueType.EXPRESSION);
      expect(comp.model.defaultValue).toMatchObject({ jsonType: "expression", expression: null });
      expect(comp.form.get("defaultValue").value).toBeNull();
   });

   it("should set model.defaultValue to null and clear form control when switching to VALUE", () => {
      const exprValue = { type: ExpressionType.JS, expression: "now()", jsonType: "expression" };
      const { comp } = makeComponent({ presetModel: makeModel({ defaultValue: exprValue }) });
      comp.defaultValueType = ConditionValueType.EXPRESSION;

      comp.selectDefaultValueType(ConditionValueType.VALUE);

      expect(comp.defaultValueType).toBe(ConditionValueType.VALUE);
      expect(comp.model.defaultValue).toBeNull();
   });

   it("should update model.defaultValue.expression when defaultExpressionValueChange is called", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ defaultValue: { type: ExpressionType.JS, expression: null, jsonType: "expression" } }) });
      comp.defaultValueType = ConditionValueType.EXPRESSION;

      comp.defaultExpressionValueChange({ type: ExpressionType.JS, expression: "today()" } as any);

      expect(comp.model.defaultValue.expression).toBe("today()");
   });
});

// ---------------------------------------------------------------------------
// Group 10 — initDefaultValueType
// ---------------------------------------------------------------------------

describe("Group 10 — initDefaultValueType: sets defaultValueType from model", () => {
   it("should set defaultValueType to EXPRESSION when model.defaultValue.jsonType is expression", () => {
      const { comp } = makeComponent({
         presetModel: makeModel({ defaultValue: { jsonType: "expression", type: ExpressionType.JS, expression: "now()" } }),
      });

      expect(comp.defaultValueType).toBe(ConditionValueType.EXPRESSION);
   });

   it("should set defaultValueType to VALUE when model.defaultValue is not an expression object", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ defaultValue: "2024-01-01" }) });

      expect(comp.defaultValueType).toBe(ConditionValueType.VALUE);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — getVariableTree / getVariableTreeModel
// ---------------------------------------------------------------------------

describe("Group 11 — getVariableTree / getVariableTreeModel: variable tree structure", () => {
   it("should return an Observable that resolves to a root node containing a Variables child", (done) => {
      const { comp } = makeComponent({ presetModel: makeModel({ otherVariables: ["v1", "v2"] }) });

      comp.getVariableTree({} as any).subscribe((root) => {
         expect(root.children.length).toBe(1);
         expect(root.children[0].label).toContain("Variables");
         expect(root.children[0].expanded).toBe(true);
         done();
      });
   });

   it("should return leaf nodes for each otherVariable in getVariableTreeModel", () => {
      const { comp } = makeComponent({ presetModel: makeModel({ otherVariables: ["alpha", "beta"] }) });

      const treeModel = comp.getVariableTreeModel();

      expect(treeModel.children.length).toBe(2);
      expect(treeModel.children[0].label).toBe("alpha");
      expect(treeModel.children[0].data).toBe("parameter.alpha");
      expect(treeModel.children[0].leaf).toBe(true);
      expect(treeModel.children[1].label).toBe("beta");
   });

   it("should return tree root with no children when otherVariables is empty", (done) => {
      const { comp } = makeComponent({ presetModel: makeModel({ otherVariables: [] }) });

      comp.getVariableTree({} as any).subscribe((root) => {
         // root has one child (the Variables node) but Variables node has no children
         expect(root.children[0].children.length).toBe(0);
         done();
      });
   });
});

// ---------------------------------------------------------------------------
// Group 12 — Bug #20319: variableSpecialCharacters validation
// ---------------------------------------------------------------------------

describe("Group 12 — Bug #20319: variableSpecialCharacters — allowed vs disallowed characters", () => {
   // 🔁 Regression-sensitive: Bug #20319 — &$@+ must NOT trigger validation error
   it("should allow variable names containing &$@+", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });
      comp.form.get("newName").setValue("&$@+");

      const errors = comp.form.get("newName").errors;
      expect(errors?.["variableSpecialCharacters"]).toBeFalsy();
   });

   it("should reject variable names containing % (invalid special character)", () => {
      const { comp } = makeComponent({ presetModel: makeModel() });
      comp.form.get("newName").setValue("a%b");

      const errors = comp.form.get("newName").errors;
      expect(errors?.["variableSpecialCharacters"]).toBeTruthy();
   });
});
