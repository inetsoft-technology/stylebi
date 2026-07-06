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
 * RangeSliderDataPane — single pass
 *
 * Direct instantiation — two constructor dependencies (ChangeDetectorRef,
 * DataTreeValidatorService), neither with `inject()` calls (including in the
 * TreeDataPane base class), so no TestBed wiring is needed. The `tree` ViewChild is
 * bypass-assigned directly since the template (and its real TreeComponent) is never
 * rendered.
 *
 * Scope: fills the gap flagged in the prescan against the existing
 * range-slider-data-pane.spec.ts (kept — it covers real-DOM composite button
 * enable/disable + focus-after-delete, a different concern from this logic-level
 * suite): selectColumn's rangeType dispatch (CUBE_DIMENSION/numeric/TIME/date),
 * switchType's model resets, ngAfterViewInit's column pre-population, and
 * updateSourceType's assemblySource logic — plus baseline coverage of every other
 * public method (selectTreeCompositeNodes, addCompositeNodes, getParentFolder(Label),
 * selectCompositeNode, isSameSource(OfTwoNodes), deleteCompositeNode, moveNodeUp/Down,
 * getCSSIcon, additionalTablesChanged).
 *
 * Risk-first coverage:
 *   Group 3 [Risk 3] — selectColumn: the CUBE_DIMENSION/numeric/TIME/date rangeType
 *                       dispatch table, the highest-value gap called out in the prescan
 *   Group 5 [Risk 3] — addCompositeNodes/addCompositeNode: dedup guards + table-vs-folder
 *                       child iteration + index bookkeeping
 *   Group 1 [Risk 2] — ngAfterViewInit: single vs composite pre-population, the
 *                       "filter out columns whose node no longer exists" cleanup
 *   Group 2 [Risk 2] — switchType: full state reset + which tree gets collapsed
 *   Group 7 [Risk 2] — updateSourceType: assemblySource derivation
 *   Group 9 [Risk 2] — isSameSourceOfTwoNodes: the "same source" predicate's branches
 *   Remaining groups [Risk 1/2] — single-purpose setters/mutators
 *
 * Confirmed bugs (it.fails):
 *   Bug — getParentFolderLabel (Group 6): getParentFolderLabel explicitly handles a null
 *   getParentFolder() result (`parentNode == null ? null : ...`), implying null is an
 *   anticipated outcome — but getParentFolder's own while-loop dereferences `parentNode.type`
 *   on every iteration including the first, so a getParentNode() that returns null before a
 *   matching ancestor type is found throws instead of ever producing that anticipated null.
 */

import { SourceInfoType } from "../../binding/data/source-info-type";
import { RangeSliderDataPane } from "./range-slider-data-pane.component";
import { RangeSliderDataPaneModel } from "../model/range-slider-data-pane-model";
import { RangeSliderSizePaneModel } from "../model/range-slider-size-pane-model";
import { OutputColumnRefModel } from "../model/output-column-ref-model";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { DataRefType } from "../../common/data/data-ref-type";
import { XSchema } from "../../common/data/xschema";
import { TimeInfoType } from "../../composer/data/vs/time-info-type";

afterEach(() => vi.restoreAllMocks());

function makeColumn(overrides: Partial<OutputColumnRefModel> = {}): OutputColumnRefModel {
   return Object.assign({
      entity: "", attribute: "col1", dataType: XSchema.STRING,
      name: "col1", table: "Query1", refType: 0,
   }, overrides);
}

function makeTreeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return Object.assign({
      label: "col1", type: "columnNode", data: makeColumn(), children: [], tooltip: "col1",
   }, overrides);
}

function makeModel(overrides: Partial<RangeSliderDataPaneModel> = {}): RangeSliderDataPaneModel {
   return Object.assign({
      selectedTable: "Query1",
      assemblySource: false,
      additionalTables: [],
      selectedColumns: [],
      targetTree: makeTreeNode({ type: "table" }),
      compositeTargetTree: makeTreeNode({ type: "table" }),
      composite: false,
      grayedOutFields: [],
   }, overrides);
}

function makeSizeModel(overrides: Partial<RangeSliderSizePaneModel> = {}): RangeSliderSizePaneModel {
   return Object.assign({
      length: 1, logScale: false, upperInclusive: false,
      rangeType: TimeInfoType.NUMBER, rangeSize: 1, maxRangeSize: 1, submitOnChange: false,
   }, overrides);
}

function makeTreeMock(overrides: Record<string, any> = {}) {
   return Object.assign({
      // A generic non-null node by default: several methods (e.g. updateSourceType) feed this
      // straight into getParentFolder(), which dereferences `.data` unconditionally — tests
      // that specifically care about "the tree couldn't resolve this column" override this.
      getNodeByData: vi.fn(() => ({ type: "columnNode", data: {}, label: "DefaultNode" })),
      selectAndExpandToNode: vi.fn(),
      expandToNode: vi.fn(),
      collapseNode: vi.fn(),
      // Matches the default getTopAncestor's {type: "table"} so getParentFolder's while-loop
      // resolves immediately instead of walking off the end of a null chain — tests that
      // specifically exercise the parent-walk override both mocks together.
      getParentNode: vi.fn(() => ({ type: "table", label: "DefaultParent" })),
      getTopAncestor: vi.fn(() => ({ type: "table" })),
      getParentNodeByData: vi.fn(() => null),
   }, overrides);
}

function createComponent(modelOverrides: Partial<RangeSliderDataPaneModel> = {}) {
   const changeDetectorRef = { detectChanges: vi.fn() };
   const treeValidator = { validateTreeNode: vi.fn() };
   const comp = new RangeSliderDataPane(changeDetectorRef as any, treeValidator as any);
   comp.model = makeModel(modelOverrides);
   comp.sizeModel = makeSizeModel();
   comp.tree = makeTreeMock() as any;
   return { comp, changeDetectorRef, treeValidator };
}

// ---------------------------------------------------------------------------
// Group 1: ngAfterViewInit [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — ngAfterViewInit", () => {
   it("should do nothing when there are no selected columns", () => {
      const { comp, changeDetectorRef } = createComponent({ selectedColumns: [] });
      comp.ngAfterViewInit();
      expect(changeDetectorRef.detectChanges).not.toHaveBeenCalled();
   });

   it("should select and expand to the single selected column outside composite mode", () => {
      const column = makeColumn({ attribute: "a" });
      const node = makeTreeNode({ data: column });
      const { comp, changeDetectorRef } = createComponent({ composite: false, selectedColumns: [column] });
      (comp.tree.getNodeByData as any).mockReturnValue(node);

      comp.ngAfterViewInit();

      expect(comp.tree.getNodeByData).toHaveBeenCalledWith("column", column);
      expect(comp.tree.selectAndExpandToNode).toHaveBeenCalledWith(node);
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should pre-populate composite nodes for every resolvable selected column", () => {
      const colA = makeColumn({ attribute: "a" });
      const colB = makeColumn({ attribute: "b" });
      const nodeA = makeTreeNode({ label: "A", data: colA, tooltip: "tipA" });
      const { comp } = createComponent({ composite: true, selectedColumns: [colA, colB] });
      (comp.tree.getNodeByData as any).mockImplementation((_type: string, col: OutputColumnRefModel) =>
         col.attribute === "a" ? nodeA : null);

      comp.ngAfterViewInit();

      expect(comp.tree.expandToNode).toHaveBeenCalledWith(nodeA);
      expect(comp.compositeNodes).toEqual([{ label: "A", column: colA }]);
      expect(comp.levelTooltips).toEqual(["tipA"]);
   });

   it("should drop selected columns whose node could no longer be resolved from the tree", () => {
      const colA = makeColumn({ attribute: "a" });
      const colGone = makeColumn({ attribute: "gone" });
      const nodeA = makeTreeNode({ label: "A", data: colA });
      const { comp } = createComponent({ composite: true, selectedColumns: [colA, colGone] });
      (comp.tree.getNodeByData as any).mockImplementation((_type: string, col: OutputColumnRefModel) =>
         col.attribute === "a" ? nodeA : null);

      comp.ngAfterViewInit();

      expect(comp.model.selectedColumns).toEqual([colA]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: switchType [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — switchType", () => {
   it("should do nothing when the mode is unchanged", () => {
      const { comp, changeDetectorRef } = createComponent({ composite: false });
      comp.sizeModel.length = 7;

      comp.switchType(false);

      expect(comp.sizeModel.length).toBe(7);
      expect(comp.tree.collapseNode).not.toHaveBeenCalled();
   });

   it("should reset all selection state and collapse the newly-shown tree when switching to composite", () => {
      const { comp } = createComponent({ composite: false });
      comp.compositeNodes = [{ label: "x", column: makeColumn() }];
      comp.selectedCompositeNodeIndex = 2;

      comp.switchType(true);

      expect(comp.sizeModel.length).toBe(3);
      expect(comp.model.selectedTable).toBeNull();
      expect(comp.tree.collapseNode).toHaveBeenCalledWith(comp.model.compositeTargetTree);
      expect(comp.model.selectedColumns).toEqual([]);
      expect(comp.compositeNodes).toEqual([]);
      expect(comp.levelTooltips).toEqual([]);
      expect(comp.selectedCompositeNodeIndex).toBe(-1);
      expect(comp.model.composite).toBe(true);
   });

   it("should collapse the single-select tree when switching back from composite", () => {
      const { comp } = createComponent({ composite: true });

      comp.switchType(false);

      expect(comp.tree.collapseNode).toHaveBeenCalledWith(comp.model.targetTree);
      expect(comp.model.composite).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectColumn [Risk 3]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — selectColumn", () => {
   it("should do nothing for a null node", () => {
      const { comp } = createComponent();
      const emitted: boolean[] = [];
      comp.validData.subscribe((v: boolean) => emitted.push(v));

      comp.selectColumn(null);

      expect(emitted).toHaveLength(0);
   });

   it("should clear the selection and mark data invalid for a non-column node", () => {
      const { comp } = createComponent({ selectedColumns: [makeColumn()] });
      const emitted: boolean[] = [];
      comp.validData.subscribe((v: boolean) => emitted.push(v));

      comp.selectColumn(makeTreeNode({ type: "folder" }));

      expect(emitted).toEqual([true]);
      expect(comp.model.selectedColumns).toEqual([]);
   });

   it("should set the cube table and MEMBER range type for a cube-dimension column", () => {
      const column = makeColumn({ table: "CubeTable", refType: DataRefType.CUBE_DIMENSION });
      const node = makeTreeNode({ data: column });
      const { comp } = createComponent();

      comp.selectColumn(node);

      expect(comp.model.selectedTable).toBe("CubeTable");
      expect(comp.sizeModel.rangeType).toBe(TimeInfoType.MEMBER);
      expect(comp.sizeModel.rangeSize).toBe(5);
   });

   it("should set the NUMBER range type for a numeric column", () => {
      const column = makeColumn({ dataType: XSchema.INTEGER });
      const { comp } = createComponent();

      comp.selectColumn(makeTreeNode({ data: column }));

      expect(comp.sizeModel.rangeType).toBe(TimeInfoType.NUMBER);
   });

   it("should set the MINUTE_OF_DAY range type and size 60 for a TIME column", () => {
      const column = makeColumn({ dataType: XSchema.TIME });
      const { comp } = createComponent();

      comp.selectColumn(makeTreeNode({ data: column }));

      expect(comp.sizeModel.rangeType).toBe(TimeInfoType.MINUTE_OF_DAY);
      expect(comp.sizeModel.rangeSize).toBe(60);
   });

   it("should set the MONTH range type and size 3 for a date column", () => {
      const column = makeColumn({ dataType: XSchema.DATE });
      const { comp } = createComponent();

      comp.selectColumn(makeTreeNode({ data: column }));

      expect(comp.sizeModel.rangeType).toBe(TimeInfoType.MONTH);
      expect(comp.sizeModel.rangeSize).toBe(3);
   });

   it("should leave the range type untouched for a column matching none of the special types", () => {
      const column = makeColumn({ dataType: XSchema.BOOLEAN });
      const { comp } = createComponent();
      const originalRangeType = comp.sizeModel.rangeType;

      comp.selectColumn(makeTreeNode({ data: column }));

      expect(comp.sizeModel.rangeType).toBe(originalRangeType);
   });

   it("should use the parent folder label as the selected table for a plain (non-cube) column", () => {
      const column = makeColumn({ dataType: XSchema.STRING });
      const node = makeTreeNode({ data: column });
      const { comp } = createComponent();
      vi.spyOn(comp, "getParentFolderLabel").mockReturnValue("SomeFolder");

      comp.selectColumn(node);

      expect(comp.model.selectedTable).toBe("SomeFolder");
   });

   it("should always call updateSourceType as the last step", () => {
      const { comp } = createComponent();
      // The real updateSourceType() would also run correctly here (the default tree mock is
      // crash-safe), but this test only cares that it was called, not what it does — suppress
      // the body so the assertion doesn't depend on that incidental safety.
      const spy = vi.spyOn(comp, "updateSourceType").mockImplementation(() => {});

      comp.selectColumn(makeTreeNode({ data: makeColumn() }));

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: selectTreeCompositeNodes [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — selectTreeCompositeNodes", () => {
   it("should keep column/table/folder/worksheet nodes and mark data invalid", () => {
      const { comp } = createComponent();
      const emitted: boolean[] = [];
      comp.validData.subscribe((v: boolean) => emitted.push(v));
      const columnNode = makeTreeNode({ type: "columnNode" });

      comp.selectTreeCompositeNodes([columnNode]);

      expect(comp.selectedTreeCompositeNodes).toEqual([columnNode]);
      expect(emitted).toEqual([false]);
   });

   it("should replace unrecognized node types with an empty placeholder", () => {
      const { comp } = createComponent();

      comp.selectTreeCompositeNodes([makeTreeNode({ type: "measureNode" })]);

      expect(comp.selectedTreeCompositeNodes).toEqual([{}]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: addCompositeNodes / addCompositeNode [Risk 3]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — addCompositeNodes (column node)", () => {
   it("should add a new column to compositeNodes and selectedColumns", () => {
      const column = makeColumn({ attribute: "a" });
      const node = makeTreeNode({ label: "A", data: column, tooltip: "tipA" });
      const { comp } = createComponent();
      comp.selectedTreeCompositeNodes = [node];

      comp.addCompositeNodes();

      expect(comp.compositeNodes).toEqual([{ label: "A", column }]);
      expect(comp.levelTooltips).toEqual(["tipA"]);
      expect(comp.selectedCompositeNodeIndex).toBe(0);
      expect(comp.model.selectedColumns).toEqual([column]);
      expect(comp.model.selectedTable).toBe("Query1"); // column.table
   });

   it("should skip a column already present in compositeNodes (dedup by attribute)", () => {
      const column = makeColumn({ attribute: "a" });
      const node = makeTreeNode({ data: column });
      const { comp } = createComponent();
      comp.compositeNodes = [{ label: "A", column }];
      comp.selectedTreeCompositeNodes = [node];

      comp.addCompositeNodes();

      expect(comp.compositeNodes).toHaveLength(1);
      expect(comp.model.selectedColumns).toEqual([]);
   });

   it("should skip a column when it is not from the same source as the current selection", () => {
      const column = makeColumn({ attribute: "a", table: null });
      const node = makeTreeNode({ data: column });
      const { comp } = createComponent({ selectedTable: "Query1", selectedColumns: [makeColumn({ attribute: "existing" })] });
      comp.compositeNodes = [{ label: "existing", column: makeColumn({ attribute: "existing" }) }];
      comp.selectedTreeCompositeNodes = [node];
      // Bypass: getParentTable is private with no public wrapper. isSameSourceOfTwoNodes's
      // columnNode branch with compositeNodes.length > 0 falls through to getParentTable(),
      // which needs the tree; force it to a non-matching value directly.
      vi.spyOn(comp as any, "getParentTable").mockReturnValue("SomeOtherTable");

      comp.addCompositeNodes();

      expect(comp.compositeNodes).toHaveLength(1);
   });

   it("should fall back to the parent folder label when the column has no table", () => {
      const column = makeColumn({ attribute: "a", table: null });
      const node = makeTreeNode({ data: column });
      const { comp } = createComponent();
      vi.spyOn(comp, "getParentFolderLabel").mockReturnValue("FolderX");

      comp.addCompositeNodes();
      comp.selectedTreeCompositeNodes = [node];
      comp.addCompositeNodes();

      expect(comp.model.selectedTable).toBe("FolderX");
   });
});

describe("RangeSliderDataPane — addCompositeNodes (folder/table node)", () => {
   it("should add every matching column child and set the selected table from the first child's table", () => {
      const childA = makeTreeNode({ label: "A", data: makeColumn({ attribute: "a", table: "Query1" }), tooltip: "tipA" });
      const childB = makeTreeNode({ label: "B", data: makeColumn({ attribute: "b", table: "Query1" }), tooltip: "tipB" });
      const folderNode = makeTreeNode({ type: "folder", label: "MyFolder", children: [childA, childB] });
      const { comp } = createComponent();
      comp.selectedTreeCompositeNodes = [folderNode];
      // isSameSourceOfTwoNodes(folderNode) is re-checked on every child iteration; once the
      // first child is added, model.selectedColumns is no longer empty, so later children
      // only pass via the "same folder as current selection" branch, which needs this to
      // resolve back to folderNode itself.
      (comp.tree.getParentNodeByData as any).mockReturnValue(folderNode);

      comp.addCompositeNodes();

      expect(comp.compositeNodes).toEqual([{ label: "A", column: childA.data }, { label: "B", column: childB.data }]);
      expect(comp.model.selectedTable).toBe("Query1");
      expect(comp.selectedCompositeNodeIndex).toBe(1);
   });

   it("should skip children already present by label and leave the index at -1 when nothing new was added", () => {
      const childA = makeTreeNode({ label: "A", data: makeColumn({ attribute: "a" }) });
      const folderNode = makeTreeNode({ type: "folder", label: "MyFolder", children: [childA] });
      const { comp } = createComponent();
      comp.compositeNodes = [{ label: "A", column: childA.data }];
      comp.selectedTreeCompositeNodes = [folderNode];

      comp.addCompositeNodes();

      expect(comp.selectedCompositeNodeIndex).toBe(-1);
   });

   it("should fall back to the node's own label when no child provided a table name", () => {
      const childA = makeTreeNode({ label: "A", data: makeColumn({ attribute: "a", table: null }) });
      const folderNode = makeTreeNode({ type: "folder", label: "MyFolder", children: [childA] });
      const { comp } = createComponent();
      comp.selectedTreeCompositeNodes = [folderNode];

      comp.addCompositeNodes();

      expect(comp.model.selectedTable).toBe("MyFolder");
   });
});

// ---------------------------------------------------------------------------
// Group 6: getParentFolder / getParentFolderLabel [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — getParentFolder / getParentFolderLabel", () => {
   it("should walk up to the nearest 'table' ancestor for a worksheet-descended column", () => {
      const tableNode = makeTreeNode({ type: "table", label: "Query1" });
      const columnNode = makeTreeNode({ type: "columnNode" });
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "worksheet" });
      (comp.tree.getParentNode as any).mockReturnValue(tableNode);

      const result = comp.getParentFolder(columnNode);

      expect(result).toBe(tableNode);
   });

   it("should walk up to the nearest 'folder' ancestor for a cube/logical-model column", () => {
      const folderNode = makeTreeNode({ type: "folder", label: "Cube1" });
      const columnNode = makeTreeNode({ type: "columnNode" });
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "folder" });
      (comp.tree.getParentNode as any).mockReturnValue(folderNode);

      const result = comp.getParentFolder(columnNode);

      expect(result).toBe(folderNode);
   });

   it("should walk up to the nearest 'table' ancestor when the top ancestor type is itself 'table'", () => {
      const tableNode = makeTreeNode({ type: "table", label: "Query2" });
      const columnNode = makeTreeNode({ type: "columnNode" });
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "table" });
      (comp.tree.getParentNode as any).mockReturnValue(tableNode);

      const result = comp.getParentFolder(columnNode);

      expect(result).toBe(tableNode);
   });

   it("should treat a VS_ASSEMBLY-sourced column as table-scoped even when the top ancestor is neither a worksheet nor a table", () => {
      const tableNode = makeTreeNode({ type: "table", label: "Assembly1" });
      const columnNode = makeTreeNode({
         type: "columnNode",
         data: makeColumn({ properties: { type: SourceInfoType.VS_ASSEMBLY } }),
      });
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "folder" });
      (comp.tree.getParentNode as any).mockReturnValue(tableNode);

      const result = comp.getParentFolder(columnNode);

      expect(result).toBe(tableNode);
   });

   // Bug: getParentFolderLabel explicitly handles a null getParentFolder() result
   // (`parentNode == null ? null : ...`), implying null is an anticipated outcome — but
   // getParentFolder's own while-loop dereferences `parentNode.type` on every iteration
   // including the first, so a getParentNode() that returns null before a matching
   // ancestor type is found crashes instead of ever producing that anticipated null.
   it.fails("should return null from getParentFolderLabel instead of crashing when no ancestor of the expected type exists", () => {
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "folder" });
      (comp.tree.getParentNode as any).mockReturnValue(null);

      expect(comp.getParentFolderLabel(makeTreeNode())).toBeNull();
   });

   it("should return the resolved parent's label from getParentFolderLabel", () => {
      const { comp } = createComponent();
      (comp.tree.getTopAncestor as any).mockReturnValue({ type: "folder" });
      (comp.tree.getParentNode as any).mockReturnValue(makeTreeNode({ type: "folder", label: "Cube1" }));

      expect(comp.getParentFolderLabel(makeTreeNode())).toBe("Cube1");
   });
});

// ---------------------------------------------------------------------------
// Group 7: updateSourceType [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — updateSourceType", () => {
   it("should do nothing when there is no model", () => {
      const { comp } = createComponent();
      comp.model = null;
      expect(() => comp.updateSourceType()).not.toThrow();
   });

   it("should set assemblySource=false when there are no selected columns", () => {
      const { comp } = createComponent({ selectedColumns: [], assemblySource: true });
      comp.updateSourceType();
      expect(comp.model.assemblySource).toBe(false);
   });

   it("should set assemblySource=true when the grandparent folder is the Components group", () => {
      const column = makeColumn();
      const { comp } = createComponent({ selectedColumns: [column] });
      const node = makeTreeNode({ data: column });
      (comp.tree.getNodeByData as any).mockReturnValue(node);
      vi.spyOn(comp, "getParentFolder").mockReturnValue(makeTreeNode());
      (comp.tree.getParentNode as any).mockReturnValue({ label: "_#(js:Components)" });

      comp.updateSourceType();

      expect(comp.model.assemblySource).toBe(true);
   });

   it("should set assemblySource=false when the grandparent folder is something else", () => {
      const column = makeColumn();
      const { comp } = createComponent({ selectedColumns: [column] });
      const node = makeTreeNode({ data: column });
      (comp.tree.getNodeByData as any).mockReturnValue(node);
      vi.spyOn(comp, "getParentFolder").mockReturnValue(makeTreeNode());
      (comp.tree.getParentNode as any).mockReturnValue({ label: "Other" });

      comp.updateSourceType();

      expect(comp.model.assemblySource).toBe(false);
   });

   it("should set assemblySource=false when getParentFolder resolves to null (no grandparent to check)", () => {
      const column = makeColumn();
      const { comp } = createComponent({ selectedColumns: [column], assemblySource: true });
      const node = makeTreeNode({ data: column });
      (comp.tree.getNodeByData as any).mockReturnValue(node);
      vi.spyOn(comp, "getParentFolder").mockReturnValue(null);

      comp.updateSourceType();

      expect(comp.tree.getParentNode).not.toHaveBeenCalled();
      expect(comp.model.assemblySource).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8: selectCompositeNode [Risk 1]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — selectCompositeNode", () => {
   it("should set the selected composite index", () => {
      const { comp } = createComponent();
      comp.selectCompositeNode(2);
      expect(comp.selectedCompositeNodeIndex).toBe(2);
   });
});

// ---------------------------------------------------------------------------
// Group 9: isSameSource / isSameSourceOfTwoNodes [Risk 2]
// ---------------------------------------------------------------------------

// Bypass: isSameSourceOfTwoNodes and getParentTable are private with no public wrapper; both
// are accessed via `as any` casts to unit-test their branches directly, since they drive
// addCompositeNode's dedup guard (Group 5) but have no other externally-observable seam.
describe("RangeSliderDataPane — isSameSourceOfTwoNodes", () => {
   it("should be false for a node with no type", () => {
      const { comp } = createComponent();
      expect((comp as any).isSameSourceOfTwoNodes({})).toBe(false);
   });

   it("should be true for a table/folder node when there are no selected columns yet", () => {
      const { comp } = createComponent({ selectedColumns: [] });
      const node = makeTreeNode({ type: "table", children: [makeTreeNode({ type: "columnNode" })] });
      expect((comp as any).isSameSourceOfTwoNodes(node)).toBe(true);
   });

   it("should be true for a table node matching the currently selected table", () => {
      const { comp } = createComponent({ selectedTable: "Query1", selectedColumns: [makeColumn()] });
      const node = makeTreeNode({
         type: "table", data: "Query1", children: [makeTreeNode({ type: "columnNode" })],
      });
      expect((comp as any).isSameSourceOfTwoNodes(node)).toBe(true);
   });

   it("should be true for a column node when no composite nodes have been added yet", () => {
      const { comp } = createComponent();
      comp.compositeNodes = [];
      expect((comp as any).isSameSourceOfTwoNodes(makeTreeNode({ type: "columnNode" }))).toBe(true);
   });

   it("should be true for a column node whose parent table matches the current selection", () => {
      const { comp } = createComponent({ selectedTable: "Query1" });
      comp.compositeNodes = [{ label: "existing", column: makeColumn() }];
      vi.spyOn(comp as any, "getParentTable").mockReturnValue("Query1");

      expect((comp as any).isSameSourceOfTwoNodes(makeTreeNode({ type: "columnNode" }))).toBe(true);
   });

   it("should be false for a column node from a different table", () => {
      const { comp } = createComponent({ selectedTable: "Query1" });
      comp.compositeNodes = [{ label: "existing", column: makeColumn() }];
      vi.spyOn(comp as any, "getParentTable").mockReturnValue("OtherTable");

      expect((comp as any).isSameSourceOfTwoNodes(makeTreeNode({ type: "columnNode" }))).toBe(false);
   });

   it("should be true for a folder node matching the parent of the current selection via getParentNodeByData", () => {
      const { comp } = createComponent({ selectedTable: "Query1", selectedColumns: [makeColumn()] });
      const folderNode = makeTreeNode({ type: "folder", children: [makeTreeNode({ type: "columnNode" })] });
      (comp.tree.getParentNodeByData as any).mockReturnValue(folderNode);

      expect((comp as any).isSameSourceOfTwoNodes(folderNode)).toBe(true);
   });

   it("should be false for a table/folder node when none of the source-matching conditions apply", () => {
      const { comp } = createComponent({ selectedTable: "Query1", selectedColumns: [makeColumn()] });
      const tableNode = makeTreeNode({
         type: "table", data: "OtherTable", children: [makeTreeNode({ type: "columnNode" })],
      });
      (comp.tree.getParentNodeByData as any).mockReturnValue(null);

      expect((comp as any).isSameSourceOfTwoNodes(tableNode)).toBe(false);
   });

   it("should be true for a cube column node whose parent matches the current selection's parent", () => {
      const { comp } = createComponent({ selectedColumns: [makeColumn()] });
      comp.model.selectedTable = comp.cubeString + "Cube1";
      comp.compositeNodes = [{ label: "existing", column: makeColumn() }];
      const sameParent = { type: "table", label: "P" };
      (comp.tree.getParentNodeByData as any).mockReturnValue(sameParent);
      (comp.tree.getParentNode as any).mockReturnValue(sameParent);

      expect((comp as any).isSameSourceOfTwoNodes(makeTreeNode({ type: "columnNode" }))).toBe(true);
   });

   it("should be false for a cube column node whose parent does not match the current selection's parent", () => {
      const { comp } = createComponent({ selectedColumns: [makeColumn()] });
      comp.model.selectedTable = comp.cubeString + "Cube1";
      comp.compositeNodes = [{ label: "existing", column: makeColumn() }];
      (comp.tree.getParentNodeByData as any).mockReturnValue({ type: "table", label: "A" });
      (comp.tree.getParentNode as any).mockReturnValue({ type: "table", label: "B" });

      expect((comp as any).isSameSourceOfTwoNodes(makeTreeNode({ type: "columnNode" }))).toBe(false);
   });

   it("isSameSource should require every selected node to share the same source", () => {
      const { comp } = createComponent({ selectedTable: "Query1" });
      comp.compositeNodes = [{ label: "existing", column: makeColumn() }];
      comp.selectedTreeCompositeNodes = [makeTreeNode({ type: "columnNode" })];
      vi.spyOn(comp as any, "getParentTable").mockReturnValue("OtherTable");

      expect(comp.isSameSource()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: deleteCompositeNode [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — deleteCompositeNode", () => {
   it("should remove the entry at the selected index from all three parallel arrays", () => {
      const colA = makeColumn({ attribute: "a" });
      const colB = makeColumn({ attribute: "b" });
      const { comp } = createComponent({ selectedColumns: [colA, colB] });
      comp.compositeNodes = [{ label: "A", column: colA }, { label: "B", column: colB }];
      comp.levelTooltips = ["tipA", "tipB"];
      comp.selectedCompositeNodeIndex = 0;

      comp.deleteCompositeNode();

      expect(comp.model.selectedColumns).toEqual([colB]);
      expect(comp.compositeNodes).toEqual([{ label: "B", column: colB }]);
      expect(comp.levelTooltips).toEqual(["tipB"]);
      // No clamp needed here (0 < the new length of 1) — contrasts with the clamp case below.
      expect(comp.selectedCompositeNodeIndex).toBe(0);
   });

   it("should clamp the selected index to the new last item when deleting the last row", () => {
      const colA = makeColumn({ attribute: "a" });
      const colB = makeColumn({ attribute: "b" });
      const { comp } = createComponent({ selectedColumns: [colA, colB] });
      comp.compositeNodes = [{ label: "A", column: colA }, { label: "B", column: colB }];
      comp.levelTooltips = ["tipA", "tipB"];
      comp.selectedCompositeNodeIndex = 1;

      comp.deleteCompositeNode();

      expect(comp.selectedCompositeNodeIndex).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 11: moveNodeUp / moveNodeDown [Risk 2]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — moveNodeUp / moveNodeDown", () => {
   it("should swap the selected row with the previous one and decrement the index", () => {
      const colA = makeColumn({ attribute: "a" });
      const colB = makeColumn({ attribute: "b" });
      const { comp } = createComponent({ selectedColumns: [colA, colB] });
      comp.compositeNodes = [{ label: "A", column: colA }, { label: "B", column: colB }];
      comp.levelTooltips = ["tipA", "tipB"];
      comp.selectedCompositeNodeIndex = 1;

      comp.moveNodeUp();

      expect(comp.model.selectedColumns).toEqual([colB, colA]);
      expect(comp.compositeNodes).toEqual([{ label: "B", column: colB }, { label: "A", column: colA }]);
      expect(comp.levelTooltips).toEqual(["tipB", "tipA"]);
      expect(comp.selectedCompositeNodeIndex).toBe(0);
   });

   it("should swap the selected row with the next one and increment the index", () => {
      const colA = makeColumn({ attribute: "a" });
      const colB = makeColumn({ attribute: "b" });
      const { comp } = createComponent({ selectedColumns: [colA, colB] });
      comp.compositeNodes = [{ label: "A", column: colA }, { label: "B", column: colB }];
      comp.levelTooltips = ["tipA", "tipB"];
      comp.selectedCompositeNodeIndex = 0;

      comp.moveNodeDown();

      expect(comp.model.selectedColumns).toEqual([colB, colA]);
      expect(comp.selectedCompositeNodeIndex).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 12: getCSSIcon / additionalTablesChanged [Risk 1]
// ---------------------------------------------------------------------------

describe("RangeSliderDataPane — getCSSIcon / additionalTablesChanged", () => {
   it("should delegate icon lookup to GuiTool", () => {
      const { comp } = createComponent();
      const node = makeTreeNode({ type: "table" });
      expect(typeof comp.getCSSIcon(node)).toBe("string");
   });

   it("should store the updated additional tables list", () => {
      const { comp } = createComponent();
      comp.additionalTablesChanged(["T1", "T2"]);
      expect(comp.model.additionalTables).toEqual(["T1", "T2"]);
   });
});
