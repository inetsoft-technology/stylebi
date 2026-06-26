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
 * VSAssemblyScriptPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — onExpressionChange: field / parameter / component column-tree inserts
 *   Group 2 [Risk 2] — onExpressionChange: axis legend, non-columnTree suffix, guards
 *
 * Direct instantiation — ScriptPane child not rendered.
 */

import { ScriptTreeNodeData } from "../../formula-editor/script-tree-node-data";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { VSAssemblyScriptPaneModel } from "./vsassembly-script-pane-model";
import { VSAssemblyScriptPane } from "./vsassembly-script-pane.component";

const selection = {
   from: { line: 0, ch: 0 },
   to: { line: 0, ch: 0 },
};

function createPane(expression = "") {
   const comp = new VSAssemblyScriptPane();
   comp.model = { expression, scriptEnabled: true } as VSAssemblyScriptPaneModel;
   return comp;
}

function nodeData(overrides: Partial<ScriptTreeNodeData>): ScriptTreeNodeData {
   return {
      data: "col",
      expression: null,
      name: "field",
      parentData: null,
      parentLabel: "Data",
      parentName: "fields",
      suffix: null,
      ...overrides,
   } as ScriptTreeNodeData;
}

function leafNode(data: ScriptTreeNodeData, type: string = null): TreeNodeModel {
   return { leaf: true, data, type } as TreeNodeModel;
}

function expressionEvent(
   node: TreeNodeModel,
   opts: { target?: string; expression?: string; suffix?: string } = {},
) {
   if(opts.suffix != null && node.data) {
      node.data.suffix = opts.suffix;
   }

   return {
      target: opts.target ?? "columnTree",
      node,
      expression: opts.expression ?? "",
      selection,
   };
}

describe("VSAssemblyScriptPane — onExpressionChange — column tree inserts [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: Bug #10560 field tree click must insert into expression
   it("should insert data field reference for field node", () => {
      const comp = createPane();
      const event = expressionEvent(leafNode(nodeData({ name: "field", data: "Agent" })));

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("data['Agent']");
      expect(comp.cursor).toEqual({ line: 0, ch: 13 });
   });

   it("should bracket non-identifier parameter names", () => {
      const comp = createPane();
      const event = expressionEvent(leafNode(nodeData({
         name: "param1",
         data: "my-param",
         parentName: "parameter",
         parentData: "parameter",
      })));

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("parameter['my-param']");
   });

   it("should dot-join identifier parameter names", () => {
      const comp = createPane();
      const event = expressionEvent(leafNode(nodeData({
         name: "param1",
         data: "region",
         parentName: "parameter",
         parentData: "parameter",
      })));

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("parameter.region");
   });

   it("should quote component parent labels containing spaces", () => {
      const comp = createPane();
      const event = expressionEvent(leafNode(nodeData({
         name: "title",
         data: "title",
         parentName: "component",
         parentLabel: "Chart 1",
      })));

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("viewsheet['Chart 1'].title");
   });
});

describe("VSAssemblyScriptPane — onExpressionChange — other branches [Group 2, Risk 2]", () => {

   it("should insert axis legend path for legend field nodes", () => {
      const comp = createPane();
      const event = expressionEvent(leafNode(nodeData({
         name: "colorLegends",
         data: "Sales",
         parentName: "chart",
         parentLabel: "Chart1",
      })));

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("Chart1.colorLegends[].Sales");
   });

   it("should append suffix when target is not columnTree", () => {
      const comp = createPane();
      const event = expressionEvent(
         leafNode(nodeData({ name: "fn", data: "sum()", expression: "sum()" })),
         { target: "functionTree", suffix: ";" },
      );

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("sum();");
   });

   it("should assign expression from event when node is not a leaf", () => {
      const comp = createPane("existing");
      const event = expressionEvent(
         { leaf: false, data: nodeData({ name: "folder" }) } as TreeNodeModel,
         { expression: "onlyExpr" },
      );

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("onlyExpr");
   });

   it("should not insert text when leaf node has no data", () => {
      const comp = createPane("start");
      const event = expressionEvent({ leaf: true, data: null } as TreeNodeModel);

      comp.onExpressionChange(event);

      expect(comp.model.expression).toBe("");
   });
});
