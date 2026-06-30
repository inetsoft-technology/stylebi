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
 * ScriptPane - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - getCSSIcon node classification branches
 *   Group 2 [Risk 2] - functionOperatorTreeRoot composition and cache
 *   Group 3 [Risk 2] - expression/return and grayed-field predicates
 *   Group 4 [Risk 2] - static insertText selection replacement
 *
 * Out of scope this pass: CodeMirror lifecycle and async races.
 */

import { DataRef } from "../../../common/data/data-ref";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { ScriptPane } from "./script-pane.component";
import { createScriptPane, cleanupScriptPaneDom } from "./script-pane.component.test-helpers";

afterEach(() => {
   cleanupScriptPaneDom();
});

describe("ScriptPane - getCSSIcon [Group 1, Risk 2]", () => {
   it("should classify aggregate, model, table, field, and folder nodes", () => {
      const { comp } = createScriptPane();

      expect(comp.getCSSIcon({ data: { data: "New Aggregate" } })).toBe("summary-icon");
      expect(comp.getCSSIcon({ data: { useragg: "true" } })).toBe("summary-icon");
      expect(comp.getCSSIcon({ data: { name: "LOGIC_MODEL" } })).toBe("db-model-icon");
      expect(comp.getCSSIcon({ data: { isTable: "true" } })).toBe("data-table-icon");
      expect(comp.getCSSIcon({ type: "entity", data: {} })).toBe("data-table-icon");
      expect(comp.getCSSIcon({ data: { isField: "true" } })).toBe("column-icon");
      expect(comp.getCSSIcon({ type: "field", data: {} })).toBe("column-icon");
      expect(comp.getCSSIcon({ children: [{}], expanded: true })).toBe("folder-open-icon");
      expect(comp.getCSSIcon({ children: [{}], expanded: false })).toBe("folder-icon");
      expect(comp.getCSSIcon({ data: {} })).toBe("");
   });
});

describe("ScriptPane - functionOperatorTreeRoot [Group 2, Risk 2]", () => {
   it("should combine function children with operator root in javascript mode", () => {
      const { comp } = createScriptPane();
      const functionChild = { label: "avg" } as TreeNodeModel;
      const functionRoot = { label: "functions", expanded: true, children: [functionChild] } as TreeNodeModel;
      const operatorRoot = { label: "operators", expanded: true, children: [] } as TreeNodeModel;
      comp.functionTreeRoot = functionRoot;
      comp.operatorTreeRoot = operatorRoot;

      const root = comp.functionOperatorTreeRoot;

      expect(functionRoot.expanded).toBe(false);
      expect(operatorRoot.expanded).toBe(false);
      expect(root.children).toEqual([functionChild, operatorRoot]);
      expect(comp.functionOperatorTreeRoot).toBe(root);
   });

   it("should include the function root itself in SQL mode", () => {
      const { comp } = createScriptPane();
      const functionRoot = { label: "functions", children: [{ label: "sum" }] } as TreeNodeModel;
      const operatorRoot = { label: "operators", children: [] } as TreeNodeModel;
      comp.functionTreeRoot = functionRoot;
      comp.operatorTreeRoot = operatorRoot;
      comp.sql = true;

      expect(comp.functionOperatorTreeRoot.children).toEqual([functionRoot, operatorRoot]);
   });

   it("should return the single available root or an empty object", () => {
      const { comp } = createScriptPane();
      const functionRoot = { label: "functions", children: [] } as TreeNodeModel;

      comp.functionTreeRoot = functionRoot;
      expect(comp.functionOperatorTreeRoot).toBe(functionRoot);

      comp.functionTreeRoot = null;
      expect(comp.functionOperatorTreeRoot).toEqual({});
   });
});

describe("ScriptPane - predicates [Group 3, Risk 2]", () => {
   it("should report expressionMissing only when required expression is blank", () => {
      const { comp } = createScriptPane();

      comp.required = true;
      comp.expression = "   ";
      expect(comp.expressionMissing).toBe(true);

      comp.expression = "value";
      expect(comp.expressionMissing).toBe(false);

      comp.required = false;
      comp.expression = "";
      expect(comp.expressionMissing).toBe(false);
   });

   it("should report returnError only when expression exists and return token was detected", () => {
      const { comp } = createScriptPane();

      (comp as any)._expression = "return 1;";
      (comp as any).returnToken = true;
      expect(comp.returnError).toBe(true);

      (comp as any)._expression = "";
      expect(comp.returnError).toBe(false);
   });

   it("should match grayed fields by colon-normalized data string or DataRef name", () => {
      const { comp } = createScriptPane();
      comp.grayedOutFields = ["orders.state", { name: "orders.city" } as DataRef] as any;

      expect(comp.isGrayedOutField({ data: { data: "orders:state" } })).toBe(true);
      expect(comp.isGrayedOutField({ data: { data: "orders:city" } })).toBe(true);
      expect(comp.isGrayedOutField({ data: { data: "orders:zip" } })).toBe(false);
      expect(comp.isGrayedOutField({ data: {} })).toBe(false);
   });
});

describe("ScriptPane - insertText [Group 4, Risk 2]", () => {
   it("should replace a single-line selection and insert the provided value", () => {
      const result = ScriptPane.insertText("abcdef", "XX", {
         from: { line: 0, ch: 2 },
         to: { line: 0, ch: 4 }
      });

      expect(result).toBe("abXXef");
   });

   it("should replace a multi-line selection and keep suffix after the end cursor", () => {
      const result = ScriptPane.insertText("first\nsecond\nthird", "MID", {
         from: { line: 0, ch: 2 },
         to: { line: 2, ch: 2 }
      });

      expect(result).toBe("fiMIDird");
   });
});
