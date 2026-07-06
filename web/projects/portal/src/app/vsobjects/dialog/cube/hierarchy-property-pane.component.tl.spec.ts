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
 * HierarchyPropertyPane — single pass (per prescan: single-pass, dispatchPoints=0)
 *
 * Direct instantiation — the component has no constructor dependencies at all
 * (no injected services), so `new HierarchyPropertyPane()` needs no TestBed wiring.
 *
 * Scope: ngOnInit/initLocalColumnList, getInputClass, mouseDownSelect, allowDrop,
 * columnDragStart/columnDragEnd/columnDrop, contentDragStart/contentDragEnter/
 * contentDragEnd/contentDragLeave/contentDrop, newDimensionDrop, isDateType,
 * getDateDimension, getMemberName (16-case date-level switch), clear, isDragAccepted,
 * plus the private helpers setDateLevel/isDateDroppable/removeDuplicateMembers/
 * removeUndroppedColumn/removeEmptyDimensions (exercised both indirectly through the
 * public drag methods and, for the trickier branches, via direct bypass calls).
 *
 * Risk-first coverage:
 *   Group 9  [Risk 3] — contentDragEnter: the core drag-hover state machine (ghost
 *                       insert/move/revert), the largest and most stateful method
 *   Group 14 [Risk 3] — contentDrop: drop finalization, including the counterintuitive
 *                       "splice at dropIndex, not at the found duplicate index" detail
 *   Group 15 [Risk 3] — newDimensionDrop: new-dimension creation from column list vs.
 *                       from an existing dimension, with date-duplicate guards
 *   Group 7  [Risk 3] — columnDrop: the drop-back-on-column-list finalization
 *   Groups 1/2/20 [Risk 2] — initLocalColumnList, getInputClass, isDragAccepted:
 *                       filtering/gating logic with real branching
 *   Remaining groups [Risk 1/2] — single-purpose state setters and the getMemberName
 *                       date-label dispatch table
 *
 * Confirmed bugs (it.fails): none
 *
 * Mocking strategy: direct instantiation; `dragSourceEl` (the shared mutable drag-session
 * state) is bypass-set directly for tests that exercise a mid-drag method in isolation,
 * since driving it through the full columnDragStart -> contentDragEnter -> contentDrop
 * call chain for every case would obscure what each test is actually verifying.
 */

import { HierarchyPropertyPane } from "./hierarchy-property-pane.component";
import { HierarchyPropertyPaneModel } from "../../model/hierarchy-property-pane-model";
import { OutputColumnRefModel } from "../../model/output-column-ref-model";
import { VSDimensionMemberModel } from "../../model/vs-dimension-member-model";
import { VSDimensionModel } from "../../model/vs-dimension-model";
import { XSchema } from "../../../common/data/xschema";
import { DateRangeRef } from "../../../common/util/date-range-ref";

afterEach(() => vi.restoreAllMocks());

function makeColumn(overrides: Partial<OutputColumnRefModel> = {}): OutputColumnRefModel {
   return Object.assign({
      entity: "", attribute: "", dataType: XSchema.STRING, name: "col1",
      view: "", table: "t1", alias: "", refType: 0, properties: null, path: "",
   }, overrides);
}

function makeMember(overrides: Partial<VSDimensionMemberModel> = {}): VSDimensionMemberModel {
   return Object.assign({
      option: 0,
      dataRef: makeColumn({ name: "m1" }),
   }, overrides);
}

function makeDimension(overrides: Partial<VSDimensionModel> = {}): VSDimensionModel {
   return Object.assign({ members: [] }, overrides);
}

function makeModel(overrides: Partial<HierarchyPropertyPaneModel> = {}): HierarchyPropertyPaneModel {
   return Object.assign({
      isCube: false,
      hierarchyEditorModel: {} as any,
      columnList: [],
      dimensions: [],
      grayedOutFields: [],
   }, overrides);
}

function createComponent(modelOverrides: Partial<HierarchyPropertyPaneModel> = {}): HierarchyPropertyPane {
   const comp = new HierarchyPropertyPane();
   comp.model = makeModel(modelOverrides);
   return comp;
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit / initLocalColumnList [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — ngOnInit / initLocalColumnList", () => {
   it("should filter out a non-date column already used as a dimension member", () => {
      const usedCol = makeColumn({ name: "used" });
      const freeCol = makeColumn({ name: "free" });
      const comp = createComponent({
         columnList: [usedCol, freeCol],
         dimensions: [makeDimension({ members: [makeMember({ dataRef: makeColumn({ name: "used" }) })] })],
      });

      comp.ngOnInit();

      expect(comp.localColumnList).toEqual([freeCol]);
   });

   it("should keep a date column in the list even if a dimension member already uses that name", () => {
      const dateCol = makeColumn({ name: "used", dataType: XSchema.DATE });
      const comp = createComponent({
         columnList: [dateCol],
         dimensions: [makeDimension({ members: [makeMember({ dataRef: makeColumn({ name: "used" }) })] })],
      });

      comp.ngOnInit();

      expect(comp.localColumnList).toEqual([dateCol]);
   });

   it("should detect IE from the user agent string", () => {
      const uaSpy = vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (Trident/7.0)");
      const comp = createComponent();

      comp.ngOnInit();

      // Bypass: isIE is private with no public getter; verified indirectly elsewhere via
      // columnDragStart's dataTransfer.setData skip, but checked directly here since this
      // is the one test whose whole point is the detection itself.
      expect((comp as any).isIE).toBe(true);
      uaSpy.mockRestore();
   });
});

// ---------------------------------------------------------------------------
// Group 2: getInputClass [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — getInputClass", () => {
   it("should return an empty class when there are no grayed-out fields", () => {
      const comp = createComponent({ grayedOutFields: null });
      expect(comp.getInputClass(makeColumn({ name: "a" }))).toBe("");
   });

   it("should mark a column whose name matches a grayed-out field", () => {
      const comp = createComponent({ grayedOutFields: [{ name: "a" } as any] });
      expect(comp.getInputClass(makeColumn({ name: "a" }))).toBe("grayed-out-field");
   });

   it("should check dataRef.name for a dimension-member-shaped column", () => {
      const comp = createComponent({ grayedOutFields: [{ name: "a" } as any] });
      const member = makeMember({ dataRef: makeColumn({ name: "a" }) });
      expect(comp.getInputClass(member)).toBe("grayed-out-field");
   });

   it("should normalize a ':' in the name to '.' before comparing", () => {
      const comp = createComponent({ grayedOutFields: [{ name: "a.b" } as any] });
      expect(comp.getInputClass(makeColumn({ name: "a:b" }))).toBe("grayed-out-field");
   });

   it("should return an empty class when nothing matches", () => {
      const comp = createComponent({ grayedOutFields: [{ name: "other" } as any] });
      expect(comp.getInputClass(makeColumn({ name: "a" }))).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 3: mouseDownSelect [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — mouseDownSelect", () => {
   it("should set the selected column", () => {
      const comp = createComponent();
      const col = makeColumn();
      comp.mouseDownSelect(col);
      expect(comp.selectedColumn).toBe(col);
   });
});

// ---------------------------------------------------------------------------
// Group 4: allowDrop [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — allowDrop", () => {
   it("should preventDefault when date-drop is not blocked", () => {
      const comp = createComponent();
      const evt = { preventDefault: vi.fn() };
      comp.allowDrop(evt);
      expect(evt.preventDefault).toHaveBeenCalled();
   });

   it("should NOT preventDefault while a conflicting date drop is blocked", () => {
      const comp = createComponent();
      (comp as any).preventDateDrop = true;
      const evt = { preventDefault: vi.fn() };
      comp.allowDrop(evt);
      expect(evt.preventDefault).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: columnDragStart [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — columnDragStart", () => {
   it("should prime the drag session with a cloned column and YEAR_INTERVAL for a non-time column", () => {
      const comp = createComponent();
      const col = makeColumn({ dataType: XSchema.STRING, name: "a" });
      const evt = { target: { id: "src" }, dataTransfer: { setData: vi.fn() } } as any;

      comp.columnDragStart(evt, col, 2);

      expect(comp.dragSourceEl.sourceEl).toBe(evt.target);
      expect(comp.dragSourceEl.sourceRef.option).toBe(DateRangeRef.YEAR_INTERVAL);
      expect(comp.dragSourceEl.sourceRef.dataRef).toEqual(col);
      expect(comp.dragSourceEl.sourceRef.dataRef).not.toBe(col); // Tool.clone, not the same reference
      expect(comp.dragSourceEl.sourceList).toBe(comp.localColumnList);
      expect(comp.dragSourceEl.sourceIndex).toBe(2);
      expect(comp.dragSourceEl.sourceDimensionIndex).toBeNull();
      expect(comp.dragSourceEl.dropList).toBeNull();
      expect(comp.dragSourceEl.dropIndex).toBeNull();
   });

   it("should use HOUR_INTERVAL for a time column", () => {
      const comp = createComponent();
      const col = makeColumn({ dataType: XSchema.TIME });
      const evt = { target: {}, dataTransfer: { setData: vi.fn() } } as any;

      comp.columnDragStart(evt, col, 0);

      expect(comp.dragSourceEl.sourceRef.option).toBe(DateRangeRef.HOUR_INTERVAL);
   });

   it("should populate dataTransfer for non-IE browsers", () => {
      const comp = createComponent();
      const evt = { target: {}, dataTransfer: { setData: vi.fn() } } as any;

      comp.columnDragStart(evt, makeColumn(), 0);

      expect(evt.dataTransfer.setData).toHaveBeenCalledWith("data", expect.any(String));
   });

   it("should skip dataTransfer on IE", () => {
      const comp = createComponent();
      (comp as any).isIE = true;
      const evt = { target: {}, dataTransfer: { setData: vi.fn() } } as any;

      comp.columnDragStart(evt, makeColumn(), 0);

      expect(evt.dataTransfer.setData).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: columnDragEnd -> removeUndroppedColumn [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — columnDragEnd / removeUndroppedColumn", () => {
   it("should remove the dropped-preview item from dropList when it matches the drop index", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "a" });
      const dropList = [makeMember({ dataRef })];
      comp.dragSourceEl = {
         sourceRef: makeMember({ dataRef }),
         dropList,
         dropIndex: 0,
      };

      comp.columnDragEnd({} as any);

      expect(dropList).toHaveLength(0);
   });

   it("should NOT remove anything when the matching item isn't at the recorded drop index", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "a" });
      const other = makeMember({ dataRef: makeColumn({ name: "other" }) });
      const dropList = [other, makeMember({ dataRef })];
      comp.dragSourceEl = {
         sourceRef: makeMember({ dataRef }),
         dropList,
         dropIndex: 0, // matching item is actually at index 1
      };

      comp.columnDragEnd({} as any);

      expect(dropList).toHaveLength(2);
   });

   it("should do nothing when there is no dropList", () => {
      const comp = createComponent();
      comp.dragSourceEl = { sourceRef: makeMember() };
      expect(() => comp.columnDragEnd({} as any)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7: columnDrop [Risk 3]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — columnDrop", () => {
   it("should always preventDefault", () => {
      const comp = createComponent();
      comp.dragSourceEl = {
         sourceEl: { parentNode: "same" },
         sourceRef: makeMember(),
         sourceList: [],
         sourceIndex: 0,
      };
      const evt = { preventDefault: vi.fn(), target: { parentNode: "same" } };
      comp.columnDrop(evt);
      expect(evt.preventDefault).toHaveBeenCalled();
   });

   it("should remove the member from its source dimension and re-add a non-date column to the list", () => {
      const dataRef = makeColumn({ name: "m1", dataType: XSchema.STRING });
      const member = makeMember({ dataRef });
      const dim = makeDimension({ members: [member] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceEl: { parentNode: "dimList" },
         sourceRef: member,
         sourceList: dim.members,
         sourceIndex: 0,
      };
      const evt = { preventDefault: vi.fn(), target: { parentNode: "columnList" } };

      comp.columnDrop(evt);

      expect(dim.members).toHaveLength(0);
      expect(comp.model.dimensions).toHaveLength(0); // emptied dimension removed
      expect(comp.localColumnList.some(c => c.name === "m1")).toBe(true);
   });

   it("should NOT re-add a date-type member back to the column list", () => {
      const dataRef = makeColumn({ name: "m1", dataType: XSchema.DATE });
      const member = makeMember({ dataRef });
      const dim = makeDimension({ members: [member] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceEl: { parentNode: "dimList" },
         sourceRef: member,
         sourceList: dim.members,
         sourceIndex: 0,
      };
      const evt = { preventDefault: vi.fn(), target: { parentNode: "columnList" } };

      comp.columnDrop(evt);

      expect(comp.localColumnList).toHaveLength(0);
   });

   it("should NOT duplicate an entry already present in the column list", () => {
      const dataRef = makeColumn({ name: "m1", dataType: XSchema.STRING });
      const member = makeMember({ dataRef });
      const dim = makeDimension({ members: [member] });
      const comp = createComponent({
         dimensions: [dim],
         columnList: [dataRef],
      });
      comp.localColumnList = [dataRef];
      comp.dragSourceEl = {
         sourceEl: { parentNode: "dimList" },
         sourceRef: member,
         sourceList: dim.members,
         sourceIndex: 0,
      };
      const evt = { preventDefault: vi.fn(), target: { parentNode: "columnList" } };

      comp.columnDrop(evt);

      expect(comp.localColumnList).toHaveLength(1);
   });

   it("should do nothing when dropped back onto the same list it came from", () => {
      const dataRef = makeColumn({ name: "m1" });
      const member = makeMember({ dataRef });
      const dim = makeDimension({ members: [member] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceEl: { parentNode: "same" },
         sourceRef: member,
         sourceList: dim.members,
         sourceIndex: 0,
      };
      const evt = { preventDefault: vi.fn(), target: { parentNode: "same" } };

      comp.columnDrop(evt);

      expect(dim.members).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8: contentDragStart [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — contentDragStart", () => {
   it("should prime the drag session from the dimension member", () => {
      const comp = createComponent();
      const member = makeMember();
      const dim = makeDimension({ members: [member] });
      const evt = { target: {}, dataTransfer: { setData: vi.fn() } } as any;

      comp.contentDragStart(evt, member, dim, 1, 0);

      expect(comp.dragSourceEl.sourceRef).toEqual(member);
      expect(comp.dragSourceEl.sourceRef).not.toBe(member);
      expect(comp.dragSourceEl.sourceList).toBe(dim.members);
      expect(comp.dragSourceEl.sourceIndex).toBe(0);
      expect(comp.dragSourceEl.sourceDimensionIndex).toBe(1);
      expect(evt.dataTransfer.setData).toHaveBeenCalledWith("data", expect.any(String));
   });
});

// ---------------------------------------------------------------------------
// Group 9: contentDragEnter [Risk 3]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — contentDragEnter", () => {
   function primeDrag(comp: any, sourceRef: VSDimensionMemberModel, sourceList: any[]) {
      comp.dragSourceEl = {
         sourceEl: { className: "column-item" },
         sourceRef, sourceList,
         sourceIndex: sourceList.indexOf(sourceRef as any),
         dropList: null, dropIndex: null,
      };
   }

   it("should do nothing when the drag is not accepted", () => {
      const dateRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const otherDim = makeDimension({ members: [makeMember({ dataRef: dateRef })] });
      const targetDim = makeDimension({ members: [] });
      const comp = createComponent({ dimensions: [otherDim, targetDim] });
      const sourceRef = makeMember({ dataRef: dateRef });
      primeDrag(comp, sourceRef, comp.localColumnList);

      comp.contentDragEnter({}, targetDim, 1, 0);

      expect(targetDim.members).toHaveLength(0);
   });

   it("should insert a cloned ghost member before the hovered member on first entering a target", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ name: "a", dataType: XSchema.STRING }) });
      // contentDragEnter indexes into dimension.members[memberIndex] before insertion, so the
      // hovered position must already contain a real member (the row being hovered over) —
      // it isn't used to drop into a wholly empty dimension.
      const target = makeMember({ dataRef: makeColumn({ name: "target" }) });
      const dim = makeDimension({ members: [target] });
      primeDrag(comp, sourceRef, comp.localColumnList);

      comp.contentDragEnter({}, dim, 0, 0);

      expect(dim.members).toEqual([sourceRef, target]);
      expect(comp.dragSourceEl.dropList).toBe(dim.members);
      expect(comp.dragSourceEl.dropIndex).toBe(0);
   });

   it("should compute the date level via setDateLevel when dragging a date column from the column list", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef, option: -1 });
      const target = makeMember({ dataRef: makeColumn({ name: "target" }) });
      const dim = makeDimension({ members: [target] });
      primeDrag(comp, sourceRef, comp.localColumnList);

      comp.contentDragEnter({}, dim, 0, 0);

      expect(sourceRef.option).toBe(DateRangeRef.YEAR_INTERVAL);
   });

   it("should be a no-op re-entering the same drop position", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ name: "a" }) });
      const target = makeMember({ dataRef: makeColumn({ name: "target" }) });
      const dim = makeDimension({ members: [target] });
      primeDrag(comp, sourceRef, comp.localColumnList);
      comp.contentDragEnter({}, dim, 0, 0); // first enter creates the ghost at index 0

      comp.contentDragEnter({}, dim, 0, 0); // re-enter same spot

      expect(dim.members).toHaveLength(2);
   });

   it("should move the ghost member to a new index within the same dimension", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ name: "a" }) });
      const existing = makeMember({ dataRef: makeColumn({ name: "existing" }) });
      const dim = makeDimension({ members: [existing] });
      primeDrag(comp, sourceRef, comp.localColumnList);
      comp.contentDragEnter({}, dim, 0, 0); // ghost inserted before "existing" -> [ghost, existing]

      comp.contentDragEnter({}, dim, 0, 1); // move ghost to index 1 -> [existing, ghost]

      expect(dim.members).toHaveLength(2);
      expect(dim.members[1]).toEqual(sourceRef);
      expect(comp.dragSourceEl.dropIndex).toBe(1);
   });

   it("should revert the ghost to its previous slot instead of creating a duplicate at the new position", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ name: "a" }) });
      const duplicate = makeMember({ dataRef: makeColumn({ name: "a" }) }); // deep-equal to sourceRef
      const dim = makeDimension({ members: [duplicate] });
      // sourceList must be localColumnList for the `exist` (deep-equality) guard to run at all.
      primeDrag(comp, sourceRef, comp.localColumnList);
      comp.contentDragEnter({}, dim, 0, 0); // ghost inserted before duplicate -> [ghost, duplicate]

      comp.contentDragEnter({}, dim, 0, 1); // attempt move to where "duplicate" already matches

      // The ghost is put back at its previous index rather than moved — the list still shows
      // exactly one ghost + the pre-existing duplicate, just without relocating.
      expect(dim.members).toHaveLength(2);
      expect(dim.members[0]).toEqual(sourceRef);
      expect(dim.members[1]).toEqual(duplicate);
      expect(comp.dragSourceEl.dropIndex).toBe(0);
   });

   it("should bail out early when the target member is a non-droppable date already present later in the list", () => {
      const comp = createComponent();
      const dateRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef: dateRef, option: DateRangeRef.YEAR_INTERVAL });
      const laterDuplicate = makeMember({ dataRef: dateRef, option: DateRangeRef.YEAR_INTERVAL });
      const targetMember = makeMember({ dataRef: dateRef, option: DateRangeRef.YEAR_INTERVAL });
      const dim = makeDimension({ members: [targetMember, laterDuplicate] });
      comp.dragSourceEl = {
         sourceEl: { className: "column-item" }, // does NOT contain "hierarchy-content-item"
         sourceRef, sourceList: comp.localColumnList, sourceIndex: 0,
         dropList: null, dropIndex: null,
      };

      comp.contentDragEnter({}, dim, 0, 0);

      expect(dim.members).toHaveLength(2); // unchanged — bailed out before any splice
   });
});

// ---------------------------------------------------------------------------
// Group 10: setDateLevel [Risk 2] (private — exercised directly)
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — setDateLevel", () => {
   it("should default a time column to HOUR_INTERVAL with no preceding sibling", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ dataType: XSchema.TIME }) });

      (comp as any).setDateLevel(sourceRef, 0, []);

      expect(sourceRef.option).toBe(DateRangeRef.HOUR_INTERVAL);
   });

   it("should default a date column to YEAR_INTERVAL with no preceding sibling", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ dataType: XSchema.DATE }) });

      (comp as any).setDateLevel(sourceRef, 0, []);

      expect(sourceRef.option).toBe(DateRangeRef.YEAR_INTERVAL);
   });

   it("should derive the next date level from the nearest preceding member with the same field", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef });
      const preceding = makeMember({ dataRef, option: DateRangeRef.YEAR_INTERVAL });
      const members = [preceding, makeMember({ dataRef: makeColumn({ name: "other" }) })];

      (comp as any).setDateLevel(sourceRef, 2, members);

      expect(sourceRef.option).toBe(DateRangeRef.getNextDateLevel(DateRangeRef.YEAR_INTERVAL, XSchema.DATE));
   });
});

// ---------------------------------------------------------------------------
// Group 11: isDateDroppable [Risk 2] (private — exercised directly)
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — isDateDroppable", () => {
   it("should be false when an identical member exists later in the list", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef, option: DateRangeRef.YEAR_INTERVAL });
      const laterDuplicate = makeMember({ dataRef, option: DateRangeRef.YEAR_INTERVAL });
      const members = [makeMember(), laterDuplicate];

      expect((comp as any).isDateDroppable(sourceRef, 0, members)).toBe(false);
   });

   it("should be true when there is no later duplicate", () => {
      const comp = createComponent();
      const sourceRef = makeMember({ dataRef: makeColumn({ name: "d", dataType: XSchema.DATE }) });
      const members = [makeMember()];

      expect((comp as any).isDateDroppable(sourceRef, 0, members)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12: contentDragEnd -> removeDuplicateMembers [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — contentDragEnd / removeDuplicateMembers", () => {
   it("should remove a duplicate of the source item elsewhere in the drop list", () => {
      const comp = createComponent();
      const dataRef = makeColumn({ name: "a" });
      const sourceItem = makeMember({ dataRef });
      const duplicate = makeMember({ dataRef });
      const dropList = [sourceItem, duplicate];
      comp.dragSourceEl = { sourceIndex: 0, dropList };

      comp.contentDragEnd({} as any);

      expect(dropList).toHaveLength(1);
      expect(dropList[0]).toEqual(sourceItem);
   });

   it("should dedupe any other pair of equal members in the list", () => {
      const comp = createComponent();
      const uniqueDataRef = makeColumn({ name: "unique" });
      const dupDataRef = makeColumn({ name: "dup" });
      const sourceItem = makeMember({ dataRef: uniqueDataRef });
      const dropList = [sourceItem, makeMember({ dataRef: dupDataRef }), makeMember({ dataRef: dupDataRef })];
      comp.dragSourceEl = { sourceIndex: 0, dropList };

      comp.contentDragEnd({} as any);

      expect(dropList).toHaveLength(2);
   });

   it("should do nothing when there is no dropList", () => {
      const comp = createComponent();
      comp.dragSourceEl = {};
      expect(() => comp.contentDragEnd({} as any)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 13: contentDragLeave [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — contentDragLeave", () => {
   it("should remove the ghost preview and clear the drop target when leaving the content list", () => {
      const comp = createComponent();
      const dropList = [makeMember()];
      comp.dragSourceEl = { sourceDimensionIndex: 0, dropList, dropIndex: 0 };
      (comp as any).inItem = false;

      comp.contentDragLeave({ target: { className: "hierarchy-content-list" } });

      expect(dropList).toHaveLength(0);
      expect(comp.dragSourceEl.dropList).toBeNull();
      expect(comp.dragSourceEl.dropIndex).toBeNull();
   });

   it("should NOT clear the drop target while still hovering an inner item", () => {
      const comp = createComponent();
      const dropList = [makeMember()];
      comp.dragSourceEl = { sourceDimensionIndex: 0, dropList, dropIndex: 0 };
      (comp as any).inItem = true;

      comp.contentDragLeave({ target: { className: "hierarchy-content-list" } });

      expect(dropList).toHaveLength(1);
      expect(comp.dragSourceEl.dropList).toBe(dropList);
   });

   it("should mark inItem=false when leaving a content item element", () => {
      const comp = createComponent();
      (comp as any).inItem = true;
      comp.dragSourceEl = {};

      comp.contentDragLeave({ target: { className: "hierarchy-content-item" } });

      expect((comp as any).inItem).toBe(false);
   });

   it("should mark inItem=false when leaving the column icon element", () => {
      const comp = createComponent();
      (comp as any).inItem = true;
      comp.dragSourceEl = {};

      comp.contentDragLeave({ target: { className: "column-icon" } });

      expect((comp as any).inItem).toBe(false);
   });

   it("should not touch drag state for an unrelated target", () => {
      const comp = createComponent();
      const dropList = [makeMember()];
      comp.dragSourceEl = { sourceDimensionIndex: 0, dropList, dropIndex: 0 };
      (comp as any).inItem = false;

      comp.contentDragLeave({ target: { className: "something-else" } });

      expect(dropList).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 14: contentDrop [Risk 3]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — contentDrop", () => {
   it("should remove the moved item from its source list and reset the drag session", () => {
      const dataRef = makeColumn({ name: "a", dataType: XSchema.STRING });
      const sourceRef = makeMember({ dataRef });
      const sourceList = [sourceRef];
      const dim = makeDimension({ members: [sourceRef] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceRef, sourceList, sourceIndex: 0,
         dropList: dim.members, dropIndex: 0,
      };

      comp.contentDrop({}, dim, 0, 0);

      expect(sourceList).toHaveLength(0);
      expect(comp.dragSourceEl).toEqual({});
   });

   it("should skip removal entirely when dropIndex is null (drag never entered a valid target)", () => {
      const dataRef = makeColumn({ name: "a" });
      const sourceRef = makeMember({ dataRef });
      const sourceList = [sourceRef];
      const comp = createComponent();
      comp.dragSourceEl = { sourceRef, sourceList, sourceIndex: 0, dropList: null, dropIndex: null };

      comp.contentDrop({}, makeDimension(), 0, 0);

      expect(sourceList).toHaveLength(1);
   });

   it("should NOT remove a date column from the column list it was dragged from", () => {
      const dataRef = makeColumn({ name: "a", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef });
      const comp = createComponent();
      const dim = makeDimension({ members: [sourceRef] });
      comp.dragSourceEl = {
         sourceRef, sourceList: comp.localColumnList, sourceIndex: 0,
         dropList: dim.members, dropIndex: 0,
      };
      comp.localColumnList = [dataRef];

      comp.contentDrop({}, dim, 0, 0);

      expect(comp.localColumnList).toHaveLength(1);
   });

   it("should shift the source index by one when reordering within the same list past the drop point", () => {
      const dataRef = makeColumn({ name: "a" });
      const sourceRef = makeMember({ dataRef });
      const keep = makeMember({ dataRef: makeColumn({ name: "keep" }) });
      const dim = makeDimension({ members: [keep, sourceRef] }); // sourceIndex will resolve to 1
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceRef, sourceList: dim.members, sourceIndex: 1,
         dropList: dim.members, dropIndex: 0, // dropped before its own original position
      };

      comp.contentDrop({}, dim, 0, 0);

      // sourceItemIndex shifts from 1 to 2 (out of bounds by the time of removal), so the
      // original "keep" entry survives the splice — confirms the +1 compensation ran.
      expect(dim.members.some(m => m.dataRef.name === "keep")).toBe(true);
   });

   it("should remove a stray duplicate at the drop position when a date column already exists elsewhere in the dimension", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const sourceRef = makeMember({ dataRef });
      const existingElsewhere = makeMember({ dataRef });
      const ghostAtDropIndex = makeMember({ dataRef: makeColumn({ name: "ghost" }) });
      const dim = makeDimension({ members: [ghostAtDropIndex, existingElsewhere] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         sourceRef, sourceList: comp.localColumnList, sourceIndex: 0,
         dropList: dim.members, dropIndex: 0,
      };

      comp.contentDrop({}, dim, 0, 0);

      // The duplicate check finds "existingElsewhere" (index 1) but the source splices at
      // dropIndex (0) — the ghost preview, not the found duplicate, is what gets removed.
      expect(dim.members).toEqual([existingElsewhere]);
   });

   it("should clean up any dimension left with no members", () => {
      const dataRef = makeColumn({ name: "a" });
      const sourceRef = makeMember({ dataRef });
      const dim = makeDimension({ members: [sourceRef] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = {
         // sourceList must be the SAME array as dim.members — splicing a standalone copy
         // wouldn't actually empty the dimension being cleaned up.
         sourceRef, sourceList: dim.members, sourceIndex: 0,
         dropList: dim.members, dropIndex: 0,
      };

      comp.contentDrop({}, dim, 0, 0);

      expect(comp.model.dimensions).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 15: newDimensionDrop [Risk 3]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — newDimensionDrop", () => {
   it("should create a new dimension from a column-list item and remove it from the list", () => {
      const dataRef = makeColumn({ name: "a", dataType: XSchema.STRING });
      const comp = createComponent({ columnList: [dataRef] });
      comp.localColumnList = [dataRef];
      const sourceRef = makeMember({ dataRef });
      comp.dragSourceEl = { sourceRef, sourceList: comp.localColumnList, sourceIndex: 0 };
      const evt = { preventDefault: vi.fn() };

      comp.newDimensionDrop(evt);

      expect(evt.preventDefault).toHaveBeenCalled();
      expect(comp.model.dimensions).toHaveLength(1);
      expect(comp.model.dimensions[0].members).toEqual([sourceRef]);
      expect(comp.selectedColumn).toBe(sourceRef);
      expect(comp.localColumnList).toHaveLength(0);
      expect(comp.dragSourceEl).toEqual({});
   });

   it("should keep a date column in the column list after creating its new dimension", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const comp = createComponent();
      comp.localColumnList = [dataRef];
      const sourceRef = makeMember({ dataRef });
      comp.dragSourceEl = { sourceRef, sourceList: comp.localColumnList, sourceIndex: 0 };

      comp.newDimensionDrop({ preventDefault: vi.fn() });

      expect(comp.localColumnList).toHaveLength(1);
   });

   it("should refuse to create a second dimension for a date field that already has one", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const existingDim = makeDimension({ members: [makeMember({ dataRef })] });
      const comp = createComponent({ dimensions: [existingDim] });
      comp.localColumnList = [dataRef];
      const sourceRef = makeMember({ dataRef });
      comp.dragSourceEl = { sourceRef, sourceList: comp.localColumnList, sourceIndex: 0 };

      comp.newDimensionDrop({ preventDefault: vi.fn() });

      expect(comp.model.dimensions).toHaveLength(1); // no second dimension created
      expect(comp.dragSourceEl.sourceRef).toBe(sourceRef); // bailed out before the reset
   });

   it("should create a new dimension from a member dragged out of an existing dimension", () => {
      const dataRef = makeColumn({ name: "a" });
      const sourceRef = makeMember({ dataRef });
      const oldDim = makeDimension({ members: [sourceRef] });
      const comp = createComponent({ dimensions: [oldDim] });
      comp.dragSourceEl = { sourceRef, sourceList: oldDim.members, sourceIndex: 0 };

      comp.newDimensionDrop({ preventDefault: vi.fn() });

      expect(oldDim.members).toHaveLength(0);
      // the emptied original dimension is cleaned up, leaving only the freshly created one
      expect(comp.model.dimensions).toHaveLength(1);
      expect(comp.model.dimensions[0].members).toEqual([sourceRef]);
   });

   it("should refuse to move a member out when more than one copy exists in its source dimension", () => {
      const dataRef = makeColumn({ name: "a" });
      const sourceRef = makeMember({ dataRef });
      const secondCopy = makeMember({ dataRef });
      const oldDim = makeDimension({ members: [sourceRef, secondCopy] });
      const comp = createComponent({ dimensions: [oldDim] });
      comp.dragSourceEl = { sourceRef, sourceList: oldDim.members, sourceIndex: 0 };

      comp.newDimensionDrop({ preventDefault: vi.fn() });

      expect(comp.model.dimensions).toHaveLength(1); // still just the original dimension
      expect(oldDim.members).toHaveLength(2); // nothing removed
   });
});

// ---------------------------------------------------------------------------
// Group 16: isDateType [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — isDateType", () => {
   it.each([
      [XSchema.DATE, true],
      [XSchema.TIME_INSTANT, true],
      [XSchema.TIME, true],
      [XSchema.STRING, false],
   ])("should return %s for dataType %s", (dataType, expected) => {
      const comp = createComponent();
      expect(comp.isDateType(dataType)).toBe(expected);
   });
});

// ---------------------------------------------------------------------------
// Group 17: getDateDimension [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — getDateDimension", () => {
   it("should find the dimension containing a matching dataRef", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const dim = makeDimension({ members: [makeMember({ dataRef })] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef }) };

      expect(comp.getDateDimension()).toBe(dim);
   });

   it("should return null when no dimension has a matching member", () => {
      const comp = createComponent({ dimensions: [makeDimension({ members: [makeMember()] })] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef: makeColumn({ name: "nomatch" }) }) };

      expect(comp.getDateDimension()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 18: getMemberName [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — getMemberName", () => {
   it("should return the raw field name for a non-date member", () => {
      const comp = createComponent();
      const member = makeMember({ dataRef: makeColumn({ name: "field1", dataType: XSchema.STRING }) });
      expect(comp.getMemberName(member)).toBe("field1");
   });

   it.each([
      [DateRangeRef.YEAR_INTERVAL, "_#(js:Year)(f)"],
      [DateRangeRef.QUARTER_OF_YEAR_PART, "_#(js:QuarterOfYear)(f)"],
      [DateRangeRef.MONTH_OF_YEAR_PART, "_#(js:MonthOfYear)(f)"],
      [DateRangeRef.WEEK_OF_YEAR_PART, "_#(js:WeekOfYear)(f)"],
      [DateRangeRef.DAY_OF_WEEK_PART, "_#(js:DayOfWeek)(f)"],
      [DateRangeRef.DAY_OF_MONTH_PART, "_#(js:DayOfMonth)(f)"],
      [DateRangeRef.HOUR_OF_DAY_PART, "_#(js:HourOfDay)(f)"],
      [DateRangeRef.MINUTE_OF_HOUR_PART, "_#(js:MinuteOfHour)(f)"],
      [DateRangeRef.SECOND_OF_MINUTE_PART, "_#(js:SecondOfMinute)(f)"],
      [DateRangeRef.QUARTER_INTERVAL, "_#(js:Quarter)(f)"],
      [DateRangeRef.MONTH_INTERVAL, "_#(js:Month)(f)"],
      [DateRangeRef.WEEK_INTERVAL, "_#(js:Week)(f)"],
      [DateRangeRef.DAY_INTERVAL, "_#(js:Day)(f)"],
      [DateRangeRef.HOUR_INTERVAL, "_#(js:Hour)(f)"],
      [DateRangeRef.MINUTE_INTERVAL, "_#(js:Minute)(f)"],
      [DateRangeRef.SECOND_INTERVAL, "_#(js:Second)(f)"],
   ])("should label option %s as %s", (option, expected) => {
      const comp = createComponent();
      const member = makeMember({ option, dataRef: makeColumn({ name: "f", dataType: XSchema.DATE }) });
      expect(comp.getMemberName(member)).toBe(expected);
   });

   it("should fall back to the raw field name for an unrecognized date option", () => {
      const comp = createComponent();
      const member = makeMember({ option: -999, dataRef: makeColumn({ name: "f", dataType: XSchema.DATE }) });
      expect(comp.getMemberName(member)).toBe("f");
   });
});

// ---------------------------------------------------------------------------
// Group 19: clear [Risk 1]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — clear", () => {
   it("should empty the dimensions, recompute the column list, and deselect", () => {
      const freeCol = makeColumn({ name: "free" });
      const comp = createComponent({
         columnList: [freeCol],
         dimensions: [makeDimension({ members: [makeMember()] })],
      });
      comp.selectedColumn = makeColumn();

      comp.clear();

      expect(comp.model.dimensions).toEqual([]);
      expect(comp.localColumnList).toEqual([freeCol]);
      expect(comp.selectedColumn).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 20: isDragAccepted [Risk 2]
// ---------------------------------------------------------------------------

describe("HierarchyPropertyPane — isDragAccepted", () => {
   it("should always accept a non-date column", () => {
      const comp = createComponent();
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef: makeColumn({ dataType: XSchema.STRING }) }) };

      expect(comp.isDragAccepted({}, makeDimension())).toBe(true);
      expect((comp as any).preventDateDrop).toBe(false);
   });

   it("should accept a date column when no other dimension already has that field", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const comp = createComponent({ dimensions: [makeDimension({ members: [] })] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef }) };

      expect(comp.isDragAccepted({}, comp.model.dimensions[0])).toBe(true);
   });

   it("should reject a date column already used by a different dimension", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const otherDim = makeDimension({ members: [makeMember({ dataRef })] });
      const targetDim = makeDimension({ members: [] });
      const comp = createComponent({ dimensions: [otherDim, targetDim] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef }) };

      expect(comp.isDragAccepted({}, targetDim)).toBe(false);
      expect((comp as any).preventDateDrop).toBe(true);
   });

   it("should accept re-dropping into the SAME dimension that already holds the field", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const dim = makeDimension({ members: [makeMember({ dataRef })] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef }) };

      expect(comp.isDragAccepted({}, dim)).toBe(true);
   });

   it("should check every dimension when no target dimension is specified", () => {
      const dataRef = makeColumn({ name: "d", dataType: XSchema.DATE });
      const dim = makeDimension({ members: [makeMember({ dataRef })] });
      const comp = createComponent({ dimensions: [dim] });
      comp.dragSourceEl = { sourceRef: makeMember({ dataRef }) };

      expect(comp.isDragAccepted({}, null)).toBe(false);
   });
});
