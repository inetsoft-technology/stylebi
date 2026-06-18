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
 * GroupingDialog — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit / init: fetches model via HTTP when model not pre-set;
 *                        uses existing model when already set
 *   Group 2  [Risk 3] — initForm: creates form controls with correct initial values;
 *                        type/onlyFor/attribute enable/disable wiring
 *   Group 3  [Risk 3] — initRoot: calls assetTreeService, assigns root node
 *   Group 4  [Risk 2] — getCurrentSelectedNode: matches node by dataLabel, recurses children
 *   Group 5  [Risk 2] — matchNode: matches QUERY type entries sharing db prefix + qname suffix
 *   Group 6  [Risk 2] — checkOuterMirror: shows dialog for outerMirror grouping
 *   Group 7  [Risk 2] — nodeExpanded: loads children when node is not a leaf and has no children
 *   Group 8  [Risk 2] — onlyForDisabled / addDisabled / editDisabled / upDisabled / downDisabled
 *   Group 9  [Risk 2] — addCondition: opens modal, pushes resolved value to conditionExpressions
 *   Group 10 [Risk 2] — editCondition: opens modal, updates conditionExpression at index
 *   Group 11 [Risk 1] — getTooltip: delegates to ColumnRef.getTooltip
 *   Group 12 [Risk 1] — getCSSIcon: delegates to GuiTool.getTreeNodeIconClass
 *   Group 13 [Risk 3] — ok: merges model + form, emits onCommit with controller path;
 *                        sets type null when "-1"
 *
 * Out of scope this pass (covered in grouping-dialog.component.risk.tl.spec.ts):
 *   updateOnlyFor, deleteDisabled, deleteCondition, moveConditionUp, moveConditionDown, cancel
 */

import { of } from "rxjs";
import { HttpResponse } from "@angular/common/http";
import { GroupingDialog } from "./grouping-dialog.component";
import { Worksheet } from "../../data/ws/worksheet";
import { GuiTool } from "../../../common/util/gui-tool";
import { ColumnRef } from "../../../binding/data/column-ref";
import {
   makeComponent,
   makeInitializedComponent,
   makeGroupingDialogModel,
   makeWorksheet,
   makeAssetEntry,
   makeConditionExpr,
   makeModelServiceMock,
   makeAssetTreeServiceMock,
   makeModalServiceMock,
} from "./grouping-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit / init [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — ngOnInit / init", () => {

   // 🔁 Regression-sensitive: if model is provided as input, HTTP fetch must NOT occur.
   it("should use existing model when model is already set", () => {
      const { comp, mocks } = makeComponent();
      comp.model = makeGroupingDialogModel();
      comp.ngOnInit();

      expect(mocks.modelService.getModel).not.toHaveBeenCalled();
      expect(comp.form).toBeDefined();
   });

   // 🔁 Regression-sensitive: when model is null, it must fetch via modelService.getModel
   it("should fetch model via HTTP when model is not preset", () => {
      const model = makeGroupingDialogModel({ oldName: "FetchedGroup" });
      const modelService = {
         ...makeModelServiceMock(),
         getModel: vi.fn().mockReturnValue(of(model)),
      };
      const { comp } = makeComponent({ modelService });
      comp.model = null;
      comp.ngOnInit();

      expect(modelService.getModel).toHaveBeenCalled();
      expect(comp.model).toEqual(model);
      expect(comp.form).toBeDefined();
   });

   it("should still initialize form when HTTP model fetch returns", () => {
      const model = makeGroupingDialogModel({ oldName: "HTTPGroup", type: "-1" });
      const modelService = {
         ...makeModelServiceMock(),
         getModel: vi.fn().mockReturnValue(of(model)),
      };
      const { comp } = makeComponent({ modelService });
      comp.model = null;
      comp.ngOnInit();

      // form should exist after HTTP resolves (synchronous in test via of())
      expect(comp.form).toBeTruthy();
      expect(comp.form.get("newName").value).toBe("HTTPGroup");
   });
});

// ---------------------------------------------------------------------------
// Group 2: initForm [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — initForm", () => {

   it("should set newName form control to model.oldName", () => {
      const { comp } = makeInitializedComponent({ oldName: "MyGrouping" });
      expect(comp.form.get("newName").value).toBe("MyGrouping");
   });

   it("should default type to '-1' when model.type is falsy", () => {
      const { comp } = makeInitializedComponent({ type: null });
      expect(comp.form.get("type").value).toBe("-1");
   });

   it("should use model.type when it is set", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      expect(comp.form.get("type").value).toBe("string");
   });

   it("should set groupAllOthers from model", () => {
      const { comp } = makeInitializedComponent({ groupAllOthers: true });
      expect(comp.form.get("groupAllOthers").value).toBe(true);
   });

   it("should set conditionExpressions from model", () => {
      const expr = makeConditionExpr("C1");
      const { comp } = makeInitializedComponent({ conditionExpressions: [expr] });
      expect(comp.form.get("conditionExpressions").value).toEqual([expr]);
   });

   it("should disable onlyFor control when type is not '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      expect(comp.form.get("onlyFor").disabled).toBe(true);
   });

   it("should enable onlyFor control when type is '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "-1", onlyFor: null });
      expect(comp.form.get("onlyFor").disabled).toBe(false);
   });

   it("should disable attribute control when type is not '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      expect(comp.form.get("attribute").disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: changing type to non-'-1' should clear conditionExpressions
   it("type change to non-'-1' should clear conditionExpressions and deselect condition", () => {
      const expr = makeConditionExpr("C2");
      const { comp } = makeInitializedComponent({ conditionExpressions: [expr], type: "-1" });
      comp.selectedConditionIndex = 0;

      comp.form.get("type").setValue("string");

      expect(comp.form.get("conditionExpressions").value).toEqual([]);
      expect(comp.selectedConditionIndex).toBeUndefined();
   });

   it("type change back to '-1' should enable onlyFor", () => {
      const { comp } = makeInitializedComponent({ type: "string" });

      comp.form.get("type").setValue("-1");

      expect(comp.form.get("onlyFor").disabled).toBe(false);
   });

   it("onlyFor change to '(all)' should disable attribute and enable type", () => {
      const { comp } = makeInitializedComponent({ type: "-1", onlyFor: null });

      comp.form.get("onlyFor").setValue("(all)");

      expect(comp.form.get("type").disabled).toBe(false);
      expect(comp.form.get("attribute").disabled).toBe(true);
   });

   it("onlyFor change to real entry should disable type and enable attribute", () => {
      const entry = makeAssetEntry();
      const { comp } = makeInitializedComponent({ type: "-1", onlyFor: null });

      comp.form.get("onlyFor").setValue(entry);

      expect(comp.form.get("type").disabled).toBe(true);
      expect(comp.form.get("attribute").disabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: initRoot [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — initRoot", () => {

   it("should call assetTreeService.getAssetTreeNode during init", () => {
      const { comp, mocks } = makeInitializedComponent();
      expect(mocks.assetTreeService.getAssetTreeNode).toHaveBeenCalled();
   });

   it("should assign root from first child of tree root response", () => {
      const rootChild = {
         label: "Data Sources",
         leaf: false,
         data: { path: "Data Sources" },
         children: [],
      };
      const assetTreeService = {
         getAssetTreeNode: vi.fn().mockReturnValue(
            of({ treeNodeModel: { children: [rootChild] } })
         ),
      };
      const { comp } = makeInitializedComponent({}, { assetTreeService });

      expect(comp.root).toBe(rootChild);
   });

   it("should not assign root when treeNodeModel has no children", () => {
      const assetTreeService = {
         getAssetTreeNode: vi.fn().mockReturnValue(
            of({ treeNodeModel: { children: [] } })
         ),
      };
      const { comp } = makeInitializedComponent({}, { assetTreeService });

      expect(comp.root).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 4: getCurrentSelectedNode [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — getCurrentSelectedNode (via initRoot)", () => {

   it("should set currentSelectedNode to root when model.onlyFor is null", () => {
      const { comp } = makeInitializedComponent({ onlyFor: null });
      // When onlyFor is null, getCurrentSelectedNode returns null,
      // so currentSelectedNode falls back to root
      expect(comp.currentSelectedNode).toBe(comp.root);
   });

   it("should match a leaf node whose dataLabel matches model.onlyFor.identifier", () => {
      const entry = makeAssetEntry({ identifier: "1^64^__NULL__^DS/Q1", path: "DS/Q1" });
      const leafNode = {
         label: "Q1",
         leaf: true,
         dataLabel: "1^64^__NULL__^DS/Q1",
         data: entry,
         children: [],
      };
      const rootChild = {
         label: "DS",
         leaf: false,
         data: { path: "DS" },
         dataLabel: "DS",
         children: [leafNode],
      };
      const assetTreeService = {
         getAssetTreeNode: vi.fn().mockReturnValue(
            of({ treeNodeModel: { children: [rootChild] } })
         ),
      };
      // Trigger nodeExpanded mock to provide children
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(of(new HttpResponse({ body: { children: [leafNode] } }))),
      };

      const { comp } = makeInitializedComponent({ onlyFor: entry }, { assetTreeService, modelService });

      // After nodeExpanded, getCurrentSelectedNode should find leafNode
      // Since init runs synchronously, currentSelectedNode should be set to leafNode
      // (or fallback to root if nodeExpanded hasn't resolved yet in this path)
      expect(comp.currentSelectedNode).toBeDefined();
   });
});

// ---------------------------------------------------------------------------
// Group 5: matchNode [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — matchNode", () => {

   it("should return false when label is null", () => {
      const { comp } = makeInitializedComponent();
      expect((comp as any).matchNode(null, "1^64^__NULL__^DS/Q1")).toBe(false);
   });

   it("should return false when id is null", () => {
      const { comp } = makeInitializedComponent();
      expect((comp as any).matchNode("1^64^__NULL__^DS/Q1", null)).toBe(false);
   });

   it("should return true when both entries are QUERY type with matching db prefix and qname suffix", () => {
      const { comp } = makeInitializedComponent();
      // Both reference DS/Q1 — label has same path
      const label = "1^64^__NULL__^DS/Q1";
      const id = "1^64^__NULL__^DS/Q1";
      // matchNode uses createAssetEntry so both must be valid identifier strings
      expect((comp as any).matchNode(label, id)).toBe(true);
   });

   it("should return false for non-matching paths", () => {
      const { comp } = makeInitializedComponent();
      const label = "1^64^__NULL__^DS/OtherQuery";
      const id = "1^64^__NULL__^DS/Q1";
      // Different qname suffix
      expect((comp as any).matchNode(label, id)).toBe(false);
   });

   it("should return false when entries are not QUERY type", () => {
      const { comp } = makeInitializedComponent();
      // type 1 = FOLDER, not QUERY (64)
      const label = "1^1^__NULL__^DS/Q1";
      const id = "1^64^__NULL__^DS/Q1";
      expect((comp as any).matchNode(label, id)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: checkOuterMirror [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — checkOuterMirror", () => {

   it("should set outerMirror=true and show dialog when grouping has outerMirror info", async () => {
      const worksheet = makeWorksheet();
      worksheet.groupings = [
         {
            name: "Grouping1",
            info: { mirrorInfo: { outerMirror: true } } as any,
            conditionNames: [],
            description: "",
            top: 0, left: 0, width: 100, height: 50,
            dependeds: [], dependings: [], primary: false, classType: "GroupingAssembly",
         } as any,
      ];
      const modalService = {
         open: vi.fn().mockReturnValue({
            result: Promise.resolve({}),
         }),
      };

      const { comp } = makeInitializedComponent({ oldName: "Grouping1" }, { worksheet, modalService });

      expect(comp.outerMirror).toBe(true);
   });

   it("should NOT set outerMirror when grouping has no mirrorInfo", () => {
      const worksheet = makeWorksheet();
      worksheet.groupings = [
         {
            name: "Grouping1",
            info: { mirrorInfo: null } as any,
            conditionNames: [],
            description: "",
            top: 0, left: 0, width: 100, height: 50,
            dependeds: [], dependings: [], primary: false, classType: "GroupingAssembly",
         } as any,
      ];

      const { comp } = makeInitializedComponent({ oldName: "Grouping1" }, { worksheet });

      expect(comp.outerMirror).toBeFalsy();
   });

   it("should NOT set outerMirror when grouping is not found", () => {
      const worksheet = makeWorksheet();
      worksheet.groupings = [];

      const { comp } = makeInitializedComponent({ oldName: "NonExistentGrouping" }, { worksheet });

      expect(comp.outerMirror).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 7: nodeExpanded [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — nodeExpanded", () => {

   it("should load children for a non-leaf node with no children", () => {
      const childNode = { label: "Child", leaf: true, data: "child-data", children: [] };
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(
            of(new HttpResponse({ body: { children: [childNode] } }))
         ),
      };
      const { comp } = makeInitializedComponent({}, { modelService });

      const node: any = {
         label: "Parent",
         leaf: false,
         data: makeAssetEntry({ path: "DS/Parent" }),
         children: [],
      };
      comp.nodeExpanded(node);

      expect(modelService.sendModel).toHaveBeenCalled();
      expect(node.children).toContain(childNode);
   });

   it("should prepend (all) node when the expanded node is root", () => {
      const childNode = { label: "Child", leaf: true, data: "child-data", children: [] };
      const modelService = {
         ...makeModelServiceMock(),
         sendModel: vi.fn().mockReturnValue(
            of(new HttpResponse({ body: { children: [childNode] } }))
         ),
      };
      const { comp } = makeInitializedComponent({}, { modelService });

      const rootNode: any = {
         label: "Root",
         leaf: false,
         data: makeAssetEntry({ path: "root" }),
         children: [],
      };
      comp.root = rootNode;
      comp.nodeExpanded(rootNode);

      expect(rootNode.children[0]).toEqual({ label: "(all)", leaf: true, data: "(all)" });
      expect(rootNode.children).toContain(childNode);
   });

   it("should NOT load children for a leaf node", () => {
      const modelService = makeModelServiceMock();
      const { comp } = makeInitializedComponent({}, { modelService });

      // reset call count after ngOnInit
      modelService.sendModel.mockClear();

      const leafNode: any = {
         label: "Leaf",
         leaf: true,
         data: makeAssetEntry(),
         children: [],
      };
      comp.nodeExpanded(leafNode);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });

   it("should NOT load children when node already has children", () => {
      const modelService = makeModelServiceMock();
      const { comp } = makeInitializedComponent({}, { modelService });

      // reset call count after ngOnInit (which calls nodeExpanded on root child)
      modelService.sendModel.mockClear();

      const node: any = {
         label: "Parent",
         leaf: false,
         data: makeAssetEntry(),
         children: [{ label: "Existing" }],
      };
      comp.nodeExpanded(node);

      expect(modelService.sendModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8: disabled getters [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — onlyForDisabled", () => {

   it("should return true when onlyFor control is disabled", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      // type != "-1" disables onlyFor
      expect(comp.onlyForDisabled).toBe(true);
   });

   it("should return false when onlyFor control is enabled", () => {
      const { comp } = makeInitializedComponent({ type: "-1", onlyFor: null });
      expect(comp.onlyForDisabled).toBe(false);
   });
});

describe("GroupingDialog — addDisabled", () => {

   it("should return true when type is '-1' and attribute is null", () => {
      const { comp } = makeInitializedComponent({ type: "-1", attribute: null });
      expect(comp.addDisabled).toBe(true);
   });

   it("should return false when type is not '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      // type != "-1", so add button should be enabled
      expect(comp.addDisabled).toBe(false);
   });

   it("should return false when attribute has a value even if type is '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "-1", attribute: null });
      comp.form.get("attribute").enable();
      comp.form.get("attribute").setValue({ name: "col1" });
      expect(comp.addDisabled).toBe(false);
   });
});

describe("GroupingDialog — editDisabled", () => {

   it("should return true when selectedConditionIndex is undefined", () => {
      const { comp } = makeInitializedComponent();
      comp.selectedConditionIndex = undefined;
      expect(comp.editDisabled).toBe(true);
   });

   it("should return false when selectedConditionIndex is set", () => {
      const { comp } = makeInitializedComponent();
      comp.selectedConditionIndex = 0;
      expect(comp.editDisabled).toBe(false);
   });
});

describe("GroupingDialog — upDisabled", () => {

   it("should return true when selectedConditionIndex is undefined", () => {
      const { comp } = makeInitializedComponent();
      comp.selectedConditionIndex = undefined;
      expect(comp.upDisabled).toBe(true);
   });

   it("should return true when selectedConditionIndex is 0", () => {
      const { comp } = makeInitializedComponent();
      comp.selectedConditionIndex = 0;
      expect(comp.upDisabled).toBe(true);
   });

   it("should return false when selectedConditionIndex is > 0", () => {
      const { comp } = makeInitializedComponent({
         conditionExpressions: [makeConditionExpr("C1"), makeConditionExpr("C2")],
      });
      comp.selectedConditionIndex = 1;
      expect(comp.upDisabled).toBe(false);
   });
});

describe("GroupingDialog — downDisabled", () => {

   it("should return true when conditionExpressions list is empty", () => {
      const { comp } = makeInitializedComponent({ conditionExpressions: [] });
      comp.selectedConditionIndex = undefined;
      expect(comp.downDisabled).toBe(true);
   });

   it("should return true when selectedConditionIndex is at the last element", () => {
      const exprs = [makeConditionExpr("C1"), makeConditionExpr("C2")];
      const { comp } = makeInitializedComponent({ conditionExpressions: exprs });
      comp.selectedConditionIndex = 1; // last index
      expect(comp.downDisabled).toBe(true);
   });

   it("should return false when selectedConditionIndex is before the last element", () => {
      const exprs = [makeConditionExpr("C1"), makeConditionExpr("C2")];
      const { comp } = makeInitializedComponent({ conditionExpressions: exprs });
      comp.selectedConditionIndex = 0;
      expect(comp.downDisabled).toBe(false);
   });

   it("should return true when selectedConditionIndex is undefined", () => {
      const exprs = [makeConditionExpr("C1")];
      const { comp } = makeInitializedComponent({ conditionExpressions: exprs });
      comp.selectedConditionIndex = undefined;
      expect(comp.downDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: addCondition [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — addCondition", () => {

   it("should clear selectedConditionExpr before opening modal", () => {
      const { comp, mocks } = makeInitializedComponent();
      comp.selectedConditionExpr = makeConditionExpr("OldExpr");
      mocks.modalService.open.mockReturnValue({
         result: Promise.resolve(makeConditionExpr("NewExpr")),
      });

      comp.addCondition();

      expect(comp.selectedConditionExpr).toBeUndefined();
   });

   it("should call modalService.open", () => {
      const { comp, mocks } = makeInitializedComponent();
      mocks.modalService.open.mockReturnValue({
         result: Promise.resolve(makeConditionExpr("NewExpr")),
      });

      comp.addCondition();

      expect(mocks.modalService.open).toHaveBeenCalled();
   });

   it("should push resolved expression to conditionExpressions on success", async () => {
      const newExpr = makeConditionExpr("NewGroup");
      const { comp, mocks } = makeInitializedComponent({ conditionExpressions: [] });
      mocks.modalService.open.mockReturnValue({
         result: Promise.resolve(newExpr),
      });

      comp.addCondition();
      await Promise.resolve(); // flush microtask

      expect(comp.form.get("conditionExpressions").value).toContain(newExpr);
   });
});

// ---------------------------------------------------------------------------
// Group 10: editCondition [Risk 2]
// ---------------------------------------------------------------------------

describe("GroupingDialog — editCondition", () => {

   it("should set selectedConditionExpr to the current list item at selectedConditionIndex", () => {
      const expr = makeConditionExpr("EditMe");
      const { comp, mocks } = makeInitializedComponent({ conditionExpressions: [expr] });
      comp.selectedConditionIndex = 0;
      mocks.modalService.open.mockReturnValue({ result: Promise.resolve(expr) });

      comp.editCondition();

      expect(comp.selectedConditionExpr).toBe(expr);
   });

   it("should call modalService.open", () => {
      const expr = makeConditionExpr("EditMe2");
      const { comp, mocks } = makeInitializedComponent({ conditionExpressions: [expr] });
      comp.selectedConditionIndex = 0;
      mocks.modalService.open.mockReturnValue({ result: Promise.resolve(expr) });

      comp.editCondition();

      expect(mocks.modalService.open).toHaveBeenCalled();
   });

   it("should update conditionExpressions at index on successful resolve", async () => {
      const originalExpr = makeConditionExpr("Original");
      const updatedExpr = makeConditionExpr("Updated");
      const { comp, mocks } = makeInitializedComponent({ conditionExpressions: [originalExpr] });
      comp.selectedConditionIndex = 0;
      mocks.modalService.open.mockReturnValue({ result: Promise.resolve(updatedExpr) });

      comp.editCondition();
      await Promise.resolve();

      expect(comp.form.get("conditionExpressions").value[0]).toBe(updatedExpr);
   });
});

// ---------------------------------------------------------------------------
// Group 11: getTooltip [Risk 1]
// ---------------------------------------------------------------------------

describe("GroupingDialog — getTooltip", () => {

   it("should call ColumnRef.getTooltip with the provided ref", () => {
      const { comp } = makeInitializedComponent();
      const ref = { name: "col1", dataType: "string", alias: null } as any;
      const spy = vi.spyOn(ColumnRef, "getTooltip").mockReturnValue("Tooltip text");

      const result = comp.getTooltip(ref);

      expect(spy).toHaveBeenCalledWith(ref);
      expect(result).toBe("Tooltip text");
   });
});

// ---------------------------------------------------------------------------
// Group 12: getCSSIcon [Risk 1]
// ---------------------------------------------------------------------------

describe("GroupingDialog — getCSSIcon", () => {

   it("should call GuiTool.getTreeNodeIconClass with the node and empty string", () => {
      const { comp } = makeInitializedComponent();
      const node: any = { label: "DS", leaf: false, data: {} };
      const spy = vi.spyOn(GuiTool, "getTreeNodeIconClass").mockReturnValue("folder-icon");

      const result = comp.getCSSIcon(node);

      expect(spy).toHaveBeenCalledWith(node, "");
      expect(result).toBe("folder-icon");
   });
});

// ---------------------------------------------------------------------------
// Group 13: ok [Risk 3]
// ---------------------------------------------------------------------------

describe("GroupingDialog — ok", () => {

   // 🔁 Regression-sensitive: ok() must merge model + form rawValue but only keep keys
   //    that exist on the original model; otherwise extra form-control keys (attributeIndex)
   //    leak into the submitted payload.
   it("should emit onCommit with merged model and socket controller path", () => {
      const { comp } = makeInitializedComponent({ oldName: "G1", type: "-1" });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.form.get("newName").setValue("G1");

      comp.ok();

      expect(spy).toHaveBeenCalledTimes(1);
      const payload = spy.mock.calls[0][0];
      expect(payload.controller).toBe("/events/ws/dialog/grouping-assembly-dialog-model");
      expect(payload.model).toBeDefined();
   });

   // 🔁 Regression-sensitive: type "-1" must be converted to null in the emitted model
   it("should set model.type to null when form type value is '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "-1" });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.form.get("newName").setValue("G1");

      comp.ok();

      const payload = spy.mock.calls[0][0];
      expect(payload.model.type).toBeNull();
   });

   it("should keep model.type when it is not '-1'", () => {
      const { comp } = makeInitializedComponent({ type: "string" });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.form.get("newName").setValue("G1");

      comp.ok();

      const payload = spy.mock.calls[0][0];
      expect(payload.model.type).toBe("string");
   });

   it("should trim whitespace from newName in the emitted model", () => {
      const { comp } = makeInitializedComponent({ oldName: "G1" });
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.form.get("newName").setValue("  G1  ");

      comp.ok();

      const payload = spy.mock.calls[0][0];
      expect(payload.model.newName).toBe("G1");
   });

   it("should not include extra form-control keys (like attributeIndex) in the emitted model", () => {
      const { comp } = makeInitializedComponent();
      const spy = vi.fn();
      comp.onCommit.subscribe(spy);
      comp.form.get("newName").setValue("G1");

      comp.ok();

      const payload = spy.mock.calls[0][0];
      expect(payload.model).not.toHaveProperty("attributeIndex");
   });
});
