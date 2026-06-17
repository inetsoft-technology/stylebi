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
 * WSAssemblyGraphPaneComponent — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — clearSelection: clears when target===currentTarget and focused>0;
 *                        does NOT clear otherwise
 *   Group 2  [Risk 3] — selectCompositeTable: always emits onEditJoin; emits
 *                        onSelectCompositeTable only for CompositeTableAssembly; stopPropagation
 *   Group 3  [Risk 3] — removeFocusedAssemblies: non-primary → calls removeAssemblies directly;
 *                        primary → shows confirm; cancel → does NOT call removeAssemblies
 *   Group 4  [Risk 3] — removeAssemblies: body===null → sendEvent directly; body==="true" →
 *                        shows confirm; "yes" → second HTTP call; "no" → sendEvent directly
 *   Group 5  [Risk 2] — destroyAssembly: removes from blockingDependeds, calls
 *                        jsp.removeAllEndpoints, cleans up assemblies and sourceIds maps
 *   Group 6  [Risk 2] — arrowKeyMove: arrow keys offset assembly positions; clamped to 0
 *   Group 7  [Risk 1] — getArrowKeyMoveFactor: base=2; shift→20; alt→1; shift+alt→10
 *   Group 8  [Risk 2] — arrowKeyMoveEnd: sends RELOCATE_ASSEMBLIES_URI event with positions
 *   Group 9  [Risk 2] — moveAssemblies: clamps positions to 0; sends OFFSET_ASSEMBLIES_URI
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — removeAssemblies: when modelService returns body==="true" and the user
 *     clicks "yes", a second modelService.sendModel call is made. If that second call also
 *     returns body==="true", another confirm dialog opens, creating an unexpected loop.
 *     The code only guards on the "all=true" params branch but both calls share the same
 *     endpoint.
 *
 * Out of scope this pass (covered in ws-assembly-graph-pane.component.interaction.tl.spec.ts):
 *   cutAssembly, copyAssembly, toggleAutoUpdate, paste, notify, selectAssembly,
 *   clickAssembly, onSelectionBox, getThumbnailClasses, oozKeyDown, getAssemblyName,
 *   trackByFn, isWorksheetEmpty, startEditName, tableEndpoints, selectDependent
 */

import { fakeAsync, tick } from "@angular/core/testing";
import { of } from "rxjs";
import { HttpResponse } from "@angular/common/http";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { ComponentTool } from "../../../../common/util/component-tool";
import { Point } from "../../../../common/data/point";
import {
   makeComponent,
   makeWorksheet,
   makeJspMock,
} from "./ws-assembly-graph-pane.component.test-helpers";

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeAssembly(overrides: Partial<WSAssembly> = {}): WSAssembly {
   return {
      name: "TestAssembly",
      description: "",
      top: 10,
      left: 10,
      width: 100,
      height: 50,
      dependeds: [],
      dependings: [],
      primary: false,
      info: { editable: true, mirrorInfo: null } as any,
      classType: "TableAssembly",
      ...overrides,
   };
}

function makeCompositeTable(): CompositeTableAssembly {
   return new CompositeTableAssembly({
      name: "CompositeTable",
      description: "",
      top: 0,
      left: 0,
      dependeds: [],
      dependings: [],
      primary: false,
      classType: "TableAssembly" as any,
      info: {
         editable: true,
         mirrorInfo: null,
         runtime: false,
         runtimeSelected: false,
         live: false,
         aggregate: false,
         hasAggregate: false,
         editMode: false,
         hasCondition: false,
         publicSelection: [],
         privateSelection: [],
      } as any,
      subtables: [],
      colInfos: [],
      tableClassType: "RelationalJoinTableAssembly" as any,
      duration: 0,
      totalRows: 0,
      rowsCompleted: false,
      columnTypeEnabled: false,
      hasMaxRow: false,
      exceededMaximum: null,
      aggregateInfo: null,
      crosstab: false,
      headers: [],
   } as any);
}

// ---------------------------------------------------------------------------
// Group 1: clearSelection [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — clearSelection", () => {

   // 🔁 Regression-sensitive: target===currentTarget is the guard that prevents the
   //    click from clearing selection when the user clicks a child element (a thumbnail).
   it("should clear focused assemblies when target===currentTarget and assemblies are focused", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Sel1" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      const clearSpy = vi.spyOn(mocks.worksheet, "clearFocusedAssemblies");
      const div = {};
      const event = { target: div, currentTarget: div } as any;

      comp.clearSelection(event);

      expect(clearSpy).toHaveBeenCalled();
   });

   it("should NOT clear focused assemblies when target !== currentTarget", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "Sel2" });
      mocks.worksheet.currentFocusedAssemblies = [assembly];
      const clearSpy = vi.spyOn(mocks.worksheet, "clearFocusedAssemblies");
      const event = { target: {}, currentTarget: {} } as any;

      comp.clearSelection(event);

      expect(clearSpy).not.toHaveBeenCalled();
   });

   it("should NOT clear focused assemblies when no assemblies are focused", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.currentFocusedAssemblies = [];
      const clearSpy = vi.spyOn(mocks.worksheet, "clearFocusedAssemblies");
      const div = {};
      const event = { target: div, currentTarget: div } as any;

      comp.clearSelection(event);

      expect(clearSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: selectCompositeTable [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — selectCompositeTable", () => {

   // 🔁 Regression-sensitive: onEditJoin must always fire so the parent knows to
   //    open the join editor, regardless of whether the table is composite.
   it("should always emit onEditJoin", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onEditJoin.subscribe(spy);
      const table = makeAssembly({ name: "T1" }) as any;

      comp.selectCompositeTable(table);

      expect(spy).toHaveBeenCalled();
   });

   it("should emit onSelectCompositeTable for CompositeTableAssembly instances", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onSelectCompositeTable.subscribe(spy);
      const table = makeCompositeTable();

      comp.selectCompositeTable(table);

      expect(spy).toHaveBeenCalledWith(table);
   });

   it("should NOT emit onSelectCompositeTable for non-composite tables", () => {
      const { comp, mocks } = makeComponent();
      const spy = vi.fn();
      comp.onSelectCompositeTable.subscribe(spy);
      const table = makeAssembly({ name: "BoundTable" }) as any;

      comp.selectCompositeTable(table);

      expect(spy).not.toHaveBeenCalled();
   });

   it("should call event.stopPropagation when an event is provided", () => {
      const { comp, mocks } = makeComponent();
      const table = makeAssembly({ name: "T2" }) as any;
      const event = { stopPropagation: vi.fn() } as any;

      comp.selectCompositeTable(table, event);

      expect(event.stopPropagation).toHaveBeenCalled();
   });

   it("should NOT throw when no event is provided", () => {
      const { comp, mocks } = makeComponent();
      const table = makeAssembly({ name: "T3" }) as any;

      expect(() => comp.selectCompositeTable(table)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3: removeFocusedAssemblies [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — removeFocusedAssemblies", () => {

   // 🔁 Regression-sensitive: non-primary assemblies must NOT show a confirm dialog;
   //    showing one for every delete would seriously disrupt workflow.
   it("should call removeAssemblies directly for non-primary assemblies (no dialog)", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      const nonPrimary = makeAssembly({ name: "NP1", primary: false });
      mocks.worksheet.currentFocusedAssemblies = [nonPrimary];
      // modelService returns null body → removeAssemblies calls sendEvent directly
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: null })));
      const showConfirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog");

      comp.removeFocusedAssemblies();
      tick();

      expect(showConfirmSpy).not.toHaveBeenCalled();
      expect(mocks.wsClient.sendEvent).toHaveBeenCalled();
   }));

   it("should show confirm dialog when a primary assembly is focused", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      const primary = makeAssembly({ name: "Primary1", primary: true });
      mocks.worksheet.currentFocusedAssemblies = [primary];
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: null })));
      const showConfirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("delete");

      comp.removeFocusedAssemblies();
      tick();

      expect(showConfirmSpy).toHaveBeenCalled();
   }));

   it("should NOT call removeAssemblies when primary confirm is cancelled", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      const primary = makeAssembly({ name: "Primary2", primary: true });
      mocks.worksheet.currentFocusedAssemblies = [primary];
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: null })));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      comp.removeFocusedAssemblies();
      tick();

      expect(mocks.wsClient.sendEvent).not.toHaveBeenCalled();
   }));

   it("should call removeAssemblies when primary confirm returns 'delete'", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      const primary = makeAssembly({ name: "Primary3", primary: true });
      mocks.worksheet.currentFocusedAssemblies = [primary];
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: null })));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("delete");

      comp.removeFocusedAssemblies();
      tick();

      expect(mocks.wsClient.sendEvent).toHaveBeenCalled();
   }));
});

// ---------------------------------------------------------------------------
// Group 4: removeAssemblies [Risk 3]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — removeAssemblies", () => {

   // 🔁 Regression-sensitive: the body=null path must NOT show a confirm dialog; it
   //    is the common case (no dependency conflict).
   it("should call sendEvent directly when body is null (no dependency conflict)", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: null })));
      const showConfirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog");

      const event = { setAssemblyNames: vi.fn() } as any;
      comp.removeAssemblies(event);
      tick();

      expect(showConfirmSpy).not.toHaveBeenCalled();
      expect(mocks.wsClient.sendEvent).toHaveBeenCalled();
   }));

   it("should show a confirm dialog when body === 'true' (dependency conflict)", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      // First call returns "true" to trigger confirm
      mocks.modelService.sendModel
         .mockReturnValueOnce(of(new HttpResponse({ body: "true" })))
         // Second call (when user says yes) returns non-true to avoid recursion
         .mockReturnValueOnce(of(new HttpResponse({ body: "Delete these?" })));
      const showConfirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("no");

      const event = { setAssemblyNames: vi.fn() } as any;
      comp.removeAssemblies(event);
      tick();

      expect(showConfirmSpy).toHaveBeenCalled();
   }));

   it("should send event when user clicks 'no' in dependency confirm dialog", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: "true" })));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      const event = { setAssemblyNames: vi.fn() } as any;
      comp.removeAssemblies(event);
      tick();

      expect(mocks.wsClient.sendEvent).toHaveBeenCalled();
   }));

   it("should make second sendModel call when user clicks 'yes' in dependency confirm", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      // First call returns "true" (conflict); second call returns a message string
      mocks.modelService.sendModel
         .mockReturnValueOnce(of(new HttpResponse({ body: "true" })))
         .mockReturnValueOnce(of(new HttpResponse({ body: "Confirm full delete?" })));
      // First dialog returns "yes"; second returns "ok"
      vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValueOnce("yes")
         .mockResolvedValueOnce("ok");

      const event = { setAssemblyNames: vi.fn() } as any;
      comp.removeAssemblies(event);
      tick();

      expect(mocks.modelService.sendModel).toHaveBeenCalledTimes(2);
      expect(mocks.wsClient.sendEvent).toHaveBeenCalled();
   }));

   it("should NOT send event when user clicks 'cancel' in dependency confirm", fakeAsync(() => {
      const { comp, mocks } = makeComponent();
      mocks.modelService.sendModel.mockReturnValue(of(new HttpResponse({ body: "true" })));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      const event = { setAssemblyNames: vi.fn() } as any;
      comp.removeAssemblies(event);
      tick();

      expect(mocks.wsClient.sendEvent).not.toHaveBeenCalled();
   }));
});

// ---------------------------------------------------------------------------
// Group 5: destroyAssembly [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — destroyAssembly", () => {

   // 🔁 Regression-sensitive: cleanup must remove ALL three references (blockingDependeds,
   //    assemblies map, sourceIds map) or later registerAssembly calls will reconnect stale
   //    entries and draw duplicate arrows.
   it("should remove assembly name from blockingDependeds", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "ToDestroy" });
      const sourceId = "source-001";

      // Seed internal maps
      (comp as any).blockingDependeds.set("ToDestroy", ["Dependent"]);
      (comp as any).sourceIds["ToDestroy"] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;

      comp.destroyAssembly(assembly);

      expect((comp as any).blockingDependeds.has("ToDestroy")).toBe(false);
   });

   it("should call jsp.removeAllEndpoints with the sourceId", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "ToDestroy2" });
      const sourceId = "source-002";

      (comp as any).sourceIds["ToDestroy2"] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;

      comp.destroyAssembly(assembly);

      expect(mocks.jsp.removeAllEndpoints).toHaveBeenCalledWith(sourceId);
   });

   it("should remove assembly entry from assemblies map", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "ToDestroy3" });
      const sourceId = "source-003";

      (comp as any).sourceIds["ToDestroy3"] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;

      comp.destroyAssembly(assembly);

      expect((comp as any).assemblies[sourceId]).toBeUndefined();
   });

   it("should remove assembly entry from sourceIds map", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "ToDestroy4" });
      const sourceId = "source-004";

      (comp as any).sourceIds["ToDestroy4"] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;

      comp.destroyAssembly(assembly);

      expect((comp as any).sourceIds["ToDestroy4"]).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 6: arrowKeyMove [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — arrowKeyMove (private)", () => {

   function makeKeyEvent(keyCode: number, shiftKey = false, altKey = false): KeyboardEvent {
      return {
         keyCode,
         key: "",
         shiftKey,
         altKey,
         ctrlKey: false,
         repeat: false,
         preventDefault: vi.fn(),
      } as any;
   }

   it("left arrow (37) should decrement assembly.left by factor", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "A", left: 100, top: 100 });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      (comp as any).arrowKeyMove(makeKeyEvent(37));

      expect(assembly.left).toBe(98); // 100 - 2 (base factor)
   });

   it("right arrow (39) should increment assembly.left by factor", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "A", left: 100, top: 100 });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      (comp as any).arrowKeyMove(makeKeyEvent(39));

      expect(assembly.left).toBe(102); // 100 + 2
   });

   it("up arrow (38) should decrement assembly.top by factor", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "A", left: 100, top: 100 });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      (comp as any).arrowKeyMove(makeKeyEvent(38));

      expect(assembly.top).toBe(98); // 100 - 2
   });

   it("down arrow (40) should increment assembly.top by factor", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "A", left: 100, top: 100 });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      (comp as any).arrowKeyMove(makeKeyEvent(40));

      expect(assembly.top).toBe(102); // 100 + 2
   });

   it("should clamp positions to a minimum of 0 (no negative coordinates)", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "AtEdge", left: 1, top: 1 });
      mocks.worksheet.currentFocusedAssemblies = [assembly];

      // left arrow with base factor 2 → 1 - 2 = -1 → clamped to 0
      (comp as any).arrowKeyMove(makeKeyEvent(37));

      expect(assembly.left).toBe(0);
   });

   it("should not move assemblies when none are focused", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.currentFocusedAssemblies = [];

      // Should not throw
      expect(() => (comp as any).arrowKeyMove(makeKeyEvent(37))).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7: getArrowKeyMoveFactor [Risk 1]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — getArrowKeyMoveFactor (private)", () => {

   function makeKeyEvent(shiftKey: boolean, altKey: boolean): KeyboardEvent {
      return { shiftKey, altKey } as KeyboardEvent;
   }

   it("no modifiers → returns 2 (ARROW_KEY_MOVE_FACTOR_BASE)", () => {
      const { comp } = makeComponent();
      expect((comp as any).getArrowKeyMoveFactor(makeKeyEvent(false, false))).toBe(2);
   });

   it("shift only → returns 2 * 10 = 20", () => {
      const { comp } = makeComponent();
      expect((comp as any).getArrowKeyMoveFactor(makeKeyEvent(true, false))).toBe(20);
   });

   it("alt only → returns 2 * 0.5 = 1", () => {
      const { comp } = makeComponent();
      expect((comp as any).getArrowKeyMoveFactor(makeKeyEvent(false, true))).toBe(1);
   });

   it("shift + alt → returns 2 * 10 * 0.5 = 10", () => {
      const { comp } = makeComponent();
      expect((comp as any).getArrowKeyMoveFactor(makeKeyEvent(true, true))).toBe(10);
   });
});

// ---------------------------------------------------------------------------
// Group 8: arrowKeyMoveEnd [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — arrowKeyMoveEnd (private)", () => {

   // 🔁 Regression-sensitive: the event must contain all names, tops, and lefts in
   //    index-parallel arrays so the server relocates each assembly correctly.
   it("should send RELOCATE_ASSEMBLIES_URI event when assemblies exist", () => {
      const { comp, mocks } = makeComponent();
      const a1 = makeAssembly({ name: "R1", top: 50, left: 30 });
      const a2 = makeAssembly({ name: "R2", top: 80, left: 60 });
      mocks.worksheet.tables = [a1, a2] as any;
      mocks.worksheet.groupings = [] as any;
      mocks.worksheet.variables = [] as any;

      (comp as any).arrowKeyMoveEnd();

      // WSRelocateAssembliesEvent uses private fields so we verify the URI and that an
      // event object (any) was passed.
      expect(mocks.wsClient.sendEvent).toHaveBeenCalledWith(
         expect.stringContaining("relocate"),
         expect.any(Object),
      );
   });

   it("should NOT send event when no assemblies exist", () => {
      const { comp, mocks } = makeComponent();
      mocks.worksheet.tables = [] as any;
      mocks.worksheet.groupings = [] as any;
      mocks.worksheet.variables = [] as any;

      (comp as any).arrowKeyMoveEnd();

      expect(mocks.wsClient.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 9: moveAssemblies [Risk 2]
// ---------------------------------------------------------------------------

describe("WSAssemblyGraphPaneComponent — moveAssemblies", () => {

   // 🔁 Regression-sensitive: offset is applied to the assembly model (not just the DOM
   //    element) so that subsequent arrowKeyMoveEnd reads the correct positions.
   it("should update assembly.left and assembly.top by the offset", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "M1", left: 50, top: 40 });

      // Register assembly in sourceIds so moveAssemblies can look it up
      const sourceId = "source-m1";
      (comp as any).sourceIds[assembly.name] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;
      (comp as any).dragAssemblies = [assembly];

      // getElementById returns a mock element
      const el = { style: {} };
      mocks.document.getElementById.mockReturnValue(el);

      comp.moveAssemblies(new Point(10, 5));

      expect(assembly.left).toBe(60);
      expect(assembly.top).toBe(45);
   });

   it("should clamp positions to 0 when offset would produce negative coordinates", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "M2", left: 5, top: 3 });

      const sourceId = "source-m2";
      (comp as any).sourceIds[assembly.name] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;
      (comp as any).dragAssemblies = [assembly];

      const el = { style: {} };
      mocks.document.getElementById.mockReturnValue(el);

      comp.moveAssemblies(new Point(-20, -20));

      expect(assembly.left).toBe(0);
      expect(assembly.top).toBe(0);
   });

   it("should send OFFSET_ASSEMBLIES_URI event when drag assemblies exist", () => {
      const { comp, mocks } = makeComponent();
      const assembly = makeAssembly({ name: "M3", left: 100, top: 100 });

      const sourceId = "source-m3";
      (comp as any).sourceIds[assembly.name] = sourceId;
      (comp as any).assemblies[sourceId] = assembly;
      (comp as any).dragAssemblies = [assembly];

      const el = { style: {} };
      mocks.document.getElementById.mockReturnValue(el);

      comp.moveAssemblies(new Point(15, 20));

      expect(mocks.wsClient.sendEvent).toHaveBeenCalledWith(
         expect.stringContaining("offset"),
         expect.anything(),
      );
   });

   it("should NOT send event when dragAssemblies is empty", () => {
      const { comp, mocks } = makeComponent();
      (comp as any).dragAssemblies = [];

      comp.moveAssemblies(new Point(10, 10));

      expect(mocks.wsClient.sendEvent).not.toHaveBeenCalled();
   });
});
