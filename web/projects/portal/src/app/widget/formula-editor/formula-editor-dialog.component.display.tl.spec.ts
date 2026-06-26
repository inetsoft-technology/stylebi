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
 * FormulaEditorDialog — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — expressionChange VS context branches (parameter, component, field, cell, cube)
 *   Group 2 [Risk 2] — expressionChange chart/binding branches + operator fallback
 *   Group 3 [Risk 2] — getters: title, aggregateOnly, scriptDefinitions, validExpression, validFunctionRoot
 *   Group 4 [Risk 2] — checkValid warnings, getGrayedOutValues, isSqlType, hasMenu
 *   Group 5 [Risk 2] — populate* sync branches, getFullName / getAggrExpression
 *
 * Out of scope this pass:
 *   HTTP subscribe races → formula-editor-dialog.component.risk.tl.spec.ts
 *   ok / cancel / modal flows → formula-editor-dialog.component.interaction.tl.spec.ts
 */

import { ComponentTool } from "../../common/util/component-tool";
import { AggregateRef } from "../../common/data/aggregate-ref";
import { FormulaField } from "../../common/data/formula-field";
import { FormulaType } from "../../common/data/formula-type";
import { AnalysisResult } from "../dialog/script-pane/analysis-result";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { FormulaEditorService } from "./formula-editor.service";
import {
   createDialog,
   expressionChangePayload,
   leafNode,
   prepareExpressionEditor,
} from "./formula-editor-dialog.component.test-helpers";

describe("FormulaEditorDialog — expressionChange VS context [Group 1, Risk 2]", () => {

   // 🔁 Regression-sensitive: Bug #20047 parameter tree selection
   it("should build parameter property access for parameter nodes", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "_ROLES_",
            name: "param",
            parentData: "parameter",
            parentLabel: "Parameter",
            parentName: "parameter",
         }, "_ROLES_"),
      }));

      expect(comp.expression).toBe("parameter._ROLES_");
   });

   // 🔁 Regression-sensitive: Bug #18631 component property with suffix
   it("should append component method suffix from tree metadata", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "addConfidenceIntervalTarget",
            name: "compProp",
            parentData: "Chart1",
            parentLabel: "Chart1",
            parentName: "component",
            suffix: "()",
         }, "addConfidenceIntervalTarget"),
      }));

      expect(comp.expression).toBe("Chart1.addConfidenceIntervalTarget()");
   });

   // 🔁 Regression-sensitive: Bug #18933 calc field inserts field['name']
   it("should wrap calc field selections as field references", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcType = "field";
      comp.expression = null;
      comp.initForm();
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "city",
            name: "folder_0_field_0",
            parentLabel: "Fields",
            parentName: "field",
         }, "city"),
      }));

      expect(comp.expression).toBe("field['city']");
   });

   it("should prefix cell selections with $", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "A1",
            name: "cell0",
            parentName: "cell",
            parentLabel: "Cells",
         }, "A1"),
      }));

      expect(comp.expression).toBe("$A1");
   });

   it("should wrap cube measure selections in MDX-style brackets", () => {
      const { comp } = createDialog();
      comp.isCube = true;
      comp.expression = null;
      comp.initForm();
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "Sales",
            properties: { attribute: "Sales" },
            name: "measure0",
            parentName: "field",
            parentLabel: "Measures",
         }, "Sales"),
      }));

      expect(comp.expression).toBe("[Measures].[Sales]");
   });

   it("should ignore non-leaf nodes without changing expression", () => {
      const { comp } = createDialog();
      comp.expression = "keep";
      comp.initForm();
      comp.expressionChange(expressionChangePayload({
         node: { label: "Folder", leaf: false, children: [] },
      }));

      expect(comp.expression).toBe("keep");
   });
});

describe("FormulaEditorDialog — expressionChange chart and operator paths [Group 2, Risk 2]", () => {

   it("should build bindingInfo paths for chart binding nodes", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "color",
            name: "bindingInfo",
            parentLabel: "Chart1",
            parentName: "component",
         }, "color"),
      }));

      expect(comp.expression).toBe("Chart1.bindingInfo.color");
   });

   it("should build highlighted property paths", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "fill",
            name: "highlighted",
            parentLabel: "Chart1",
            parentName: "component",
         }, "fill"),
      }));

      expect(comp.expression).toBe("Chart1.highlighted.fill");
   });

   it("should build viewsheet table column references for non-identifier table names", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: "Amount",
            name: "COLUMN",
            parentData: "Order Details",
            parentName: "TABLE",
            parentLabel: "Order Details",
         }, "Amount"),
      }));

      expect(comp.expression).toBe(`viewsheet['Order Details']['Amount']`);
   });

   // 🔁 Regression-sensitive: Bug #21460 operator nodes without columnTree target
   it("should use raw operator token when target is null", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expressionChange(expressionChangePayload({
         target: null,
         node: leafNode({
            data: "[]",
            name: "Operator4|4",
            parentName: "Operator4",
         }, "[] (Array)"),
      }));

      expect(comp.expression).toBe("[]");
   });

   it("should open aggregate dialog when New Aggregate leaf is selected", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      const spy = vi.spyOn(comp, "showAggregateDialog").mockImplementation(() => {});

      comp.expressionChange(expressionChangePayload({
         node: leafNode({
            data: comp.NEW_AGGREGATE,
            expression: comp.NEW_AGGREGATE,
            name: "newAggr",
            parentName: "field",
         }, comp.NEW_AGGREGATE),
      }));

      expect(spy).toHaveBeenCalled();
   });
});

describe("FormulaEditorDialog — display getters [Group 3, Risk 2]", () => {

   it("should resolve title for cube, calc, and default editors", () => {
      const { comp } = createDialog();
      expect(comp.title).toBe("_#(js:Formula Editor)");

      comp.isCalc = true;
      expect(comp.title).toBe("_#(js:Edit Calculated Field)");

      comp.isCalc = false;
      comp.isCube = true;
      expect(comp.title).toBe("_#(js:Create Measure)");
   });

   it("should expose aggregateOnly when calcType is aggregate", () => {
      const { comp } = createDialog();
      comp.calcType = "field";
      expect(comp.aggregateOnly).toBe(false);

      comp.calcType = "aggregate";
      expect(comp.aggregateOnly).toBe(true);
   });

   it("should hide scriptDefinitions for SQL formula type", () => {
      const { comp } = createDialog();
      comp["_scriptDefinitions"] = { foo: "bar" };
      comp.initForm();
      comp.form.get("formulaType").setValue(FormulaType.SQL);

      expect(comp.isSqlType()).toBe(true);
      expect(comp.scriptDefinitions).toBeNull();
   });

   it("should invalidate expression when form, analysis, or duplicate name fails", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.expression = "valid";
      comp.formulaFields = [{ name: "Other" } as FormulaField];
      comp.form.get("formulaName").patchValue("Other");

      expect(comp.validExpression).toBe(false);

      comp.form.get("formulaName").patchValue("CalcField1");
      comp.analysisResults = [{ level: "error", message: "error" } as unknown as AnalysisResult];
      expect(comp.validExpression).toBe(false);

      comp.analysisResults = [];
      expect(comp.validExpression).toBe(true);
   });

   it("should return function tree root only when showFunctionTree is enabled", () => {
      const { comp } = createDialog();
      comp.functionTreeRoot = { label: "Functions", children: [] };
      comp.showFunctionTree = false;
      expect(comp.validFunctionRoot).toBeNull();

      comp.showFunctionTree = true;
      expect(comp.validFunctionRoot).toBe(comp.functionTreeRoot);
   });
});

describe("FormulaEditorDialog — validation and grayed-out fields [Group 4, Risk 2]", () => {

   // 🔁 Regression-sensitive: Bug #20195 non-mergeable SQL warning
   it("should warn when SQL is selected on non-mergeable data", () => {
      const { comp } = createDialog();
      comp.sqlMergeable = false;
      comp.initForm();
      comp.form.get("formulaType").setValue(FormulaType.SQL);
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");

      comp["checkValid"]();

      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Warning)",
         "_#(js:common.formulaDataUnmergeable)",
         expect.anything(),
         expect.anything(),
      );
   });

   it("should map grayed-out fields to model column names when logical model columns are present", () => {
      const { comp } = createDialog();
      comp.columns = [
         { name: "Entity" } as any,
         { name: "Entity:Revenue" } as any,
      ];
      comp.grayedOutFields = [{ name: "Entity.Revenue", attribute: "Revenue" } as any];

      expect(comp.getGrayedOutValues()).toEqual(["Entity:Revenue"]);
   });

   it("should map grayed-out fields to attributes for non-model worksheets", () => {
      const { comp } = createDialog();
      comp.columns = [{ name: "Revenue" } as any];
      comp.grayedOutFields = [{ name: "Revenue", attribute: "Revenue" } as any];

      expect(comp.getGrayedOutValues()).toEqual(["Revenue"]);
   });

   it("should expose context menu only for user aggregate nodes", () => {
      const { comp } = createDialog();
      const hasMenu = comp.hasMenu();

      expect(hasMenu({ data: { useragg: "true" } })).toBe(true);
      expect(hasMenu({ data: { useragg: "false" } })).toBe(false);
   });
});

describe("FormulaEditorDialog — tree population and aggregate labels [Group 5, Risk 2]", () => {

   it("should build SQL function and operator trees synchronously", () => {
      const { comp } = createDialog();
      prepareExpressionEditor(comp);
      comp.form.get("formulaType").setValue(FormulaType.SQL);

      comp["populateFunctionTree"]();
      comp["populateOperatorTree"]();

      expect(comp.functionTreeRoot.children.length).toBe(FormulaEditorService.sqlFunctions.length);
      expect(comp.operatorTreeRoot.children.length).toBe(FormulaEditorService.sqlOperators.length);
   });

   it("should remove date-part children when repopulating SQL column tree locally", () => {
      const { comp } = createDialog();
      comp.columnTreeRoot = {
         label: "Fields",
         children: [{
            label: "OrderDate",
            leaf: true,
            icon: "column-icon",
            children: [{ label: "year(OrderDate)", leaf: true }],
         }],
         leaf: false,
      };
      comp._columnTreeRoot = comp.columnTreeRoot;
      comp.originalColumnTreeRoot = comp.columnTreeRoot;
      comp.initForm();
      comp.form.get("formulaType").setValue(FormulaType.SQL);

      comp["populateColumnTree"]();

      expect(comp._columnTreeRoot.children[0].children).toEqual([]);
   });

   it("should format aggregate display names and expressions", () => {
      const { comp } = createDialog();
      const aggregate: AggregateRef = {
         name: "Sales",
         formulaName: "Sum",
         ref: { name: "Sales" },
      } as AggregateRef;

      expect(comp["getFullName"](aggregate)).toBe("Sum(Sales)");
      expect(comp["getAggrExpression"](aggregate)).toBe("Sum([Sales])");
   });
});
