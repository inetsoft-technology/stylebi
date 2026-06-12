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
 * WSPaneComponent — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — processWSRemoveAssemblyCommand (tables): splices correct index,
 *                        calls deselectAssembly, emits onRemoveWSAssembly
 *   Group 2  [Risk 3] — processWSRemoveAssemblyCommand (variables / groupings): same contract
 *   Group 3  [Risk 3] — processWSRemoveAssemblyCommand (not found): warns and does NOT splice
 *   Group 4  [Risk 3] — processMessageCommand routing: OK→success, INFO→info,
 *                        WARNING→processMessageCommand0 (modal), ERROR→clears saving + modal,
 *                        default→warning
 *   Group 5  [Risk 2] — sqlEnabled / freeFormSqlEnabled / crossJoinEnabled getters
 *                        proxy composerToolbarService fields
 *   Group 6  [Risk 2] — worksheetCancel: sets worksheet.current = joinPoint,
 *                        emits onWorksheetCancel with [worksheet, joinPoint]
 *   Group 7  [Risk 2] — processClearLoadingCommand / updateLoadingMask:
 *                        loadingEventCount decremented; worksheet.loading=false when counter=0
 *   Group 8  [Risk 2] — processShowLoadingMaskCommand: increments counter (unless preparingData),
 *                        sets preparingData flag, worksheet.loading=true
 *   Group 9  [Risk 2] — processWSMoveAssembliesCommand: updates top/left on matched assemblies
 *   Group 10 [Risk 1] — processWSLoadTableDataCountCommand: updates table's rowsCompleted/totalRows
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — processWSRemoveAssemblyCommand skips the warning branch if 'index' is left
 *     as 'undefined' (not initialized to -1); if neither tables/variables/groupings branch
 *     matches, `if(index < 0)` evaluates `undefined < 0` → false, so the splice branch runs
 *     with `assemblies` undefined → TypeError. (Risk: low, requires unusual server message.)
 *   Suspicion B — processClearLoadingCommand can drive loadingEventCount negative if the
 *     server sends more ClearLoading than ShowLoadingMask commands; worksheet.loading would
 *     then remain false when it should be true after a subsequent ShowLoadingMask that resets
 *     to 1. No guard against negative counter.
 *
 * Out of scope this pass (covered in ws-pane.component.interaction.tl.spec.ts):
 *   cut/copy/editJoin/addVariable/addGrouping/processWSInitCommand etc.
 * Out of scope this pass (covered in ws-pane.component.display.tl.spec.ts):
 *   toggleShowColumnName, processShowLoadingMaskCommand preparingData flag rendering
 */

import {
   renderComponent,
   makeMocks,
} from "./ws-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: processWSRemoveAssemblyCommand — tables [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSRemoveAssemblyCommand (tables)", () => {

   // 🔁 Regression-sensitive: splice must remove exactly the matched entry and forward
   //    the name via onRemoveWSAssembly so the parent can remove the canvas node.
   it("should remove a table by name and emit onRemoveWSAssembly", async () => {
      const { comp, mocks } = await renderComponent();
      const t1 = { name: "T1", classType: "TableAssembly" } as any;
      const t2 = { name: "T2", classType: "TableAssembly" } as any;
      comp.worksheet.tables = [t1, t2];
      const removeSpy = vi.fn();
      comp.onRemoveWSAssembly.subscribe(removeSpy);

      mocks.dispatchCommand("WSRemoveAssemblyCommand", { assemblyName: "T1" });

      expect(comp.worksheet.tables).toHaveLength(1);
      expect(comp.worksheet.tables[0]).toBe(t2);
      expect(removeSpy).toHaveBeenCalledWith("T1");
   });

   it("should call worksheet.deselectAssembly for the removed assembly", async () => {
      const { comp, mocks } = await renderComponent();
      const t = { name: "Q1", classType: "TableAssembly" } as any;
      comp.worksheet.tables = [t];

      mocks.dispatchCommand("WSRemoveAssemblyCommand", { assemblyName: "Q1" });

      expect(comp.worksheet.deselectAssembly).toHaveBeenCalledWith(t);
   });
});

// ---------------------------------------------------------------------------
// Group 2: processWSRemoveAssemblyCommand — variables / groupings [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSRemoveAssemblyCommand (variables/groupings)", () => {

   it("should remove a variable by name", async () => {
      const { comp, mocks } = await renderComponent();
      const v = { name: "Var1", classType: "VariableAssembly" } as any;
      comp.worksheet.variables = [v];

      mocks.dispatchCommand("WSRemoveAssemblyCommand", { assemblyName: "Var1" });

      expect(comp.worksheet.variables).toHaveLength(0);
   });

   it("should remove a grouping by name", async () => {
      const { comp, mocks } = await renderComponent();
      const g = { name: "Grp1", classType: "GroupingAssembly" } as any;
      comp.worksheet.groupings = [g];

      mocks.dispatchCommand("WSRemoveAssemblyCommand", { assemblyName: "Grp1" });

      expect(comp.worksheet.groupings).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: processWSRemoveAssemblyCommand — not found [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSRemoveAssemblyCommand (not found)", () => {

   // 🔁 Regression-sensitive: when the assembly is missing the component must show a
   //    warning instead of silently mutating the array with an undefined splice.
   it("should warn and NOT remove anything when assembly is not found", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.worksheet.tables = [];
      comp.worksheet.variables = [];
      comp.worksheet.groupings = [];
      const removeSpy = vi.fn();
      comp.onRemoveWSAssembly.subscribe(removeSpy);

      mocks.dispatchCommand("WSRemoveAssemblyCommand", { assemblyName: "Ghost" });

      expect(mocks.notifications.warning).toHaveBeenCalled();
      expect(removeSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: processMessageCommand routing [Risk 3]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processMessageCommand routing", () => {

   // 🔁 Regression-sensitive: each MessageCommand type maps to a distinct UI action;
   //    misrouting (e.g. ERROR treated as INFO) suppresses user-visible save failures.

   it("should call notifications.success for type=OK", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("MessageCommand", { type: "OK", message: "Saved!" });

      expect(mocks.notifications.success).toHaveBeenCalledWith("Saved!");
   });

   it("should call notifications.info for type=INFO", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("MessageCommand", { type: "INFO", message: "FYI" });

      expect(mocks.notifications.info).toHaveBeenCalledWith("FYI");
   });

   it("should call notifications.warning for default/unknown type", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      mocks.dispatchCommand("MessageCommand", { type: "UNKNOWN", message: "?" });

      expect(mocks.notifications.warning).toHaveBeenCalledWith("?");
   });

   it("should set worksheet.saving=false for type=ERROR before showing modal", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.saving = true;

      (comp as any).processMessageCommand({ type: "ERROR", message: "bad" });

      expect(comp.worksheet.saving).toBe(false);
   });

   it("should NOT clear worksheet.saving for type=WARNING", async () => {
      const { comp } = await renderComponent();
      comp.worksheet.saving = true;

      try {
         (comp as any).processMessageCommand({ type: "WARNING", message: "warn" });
      } catch { /* processMessageCommand0 may throw in test env — we only care about saving */ }

      expect(comp.worksheet.saving).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: sqlEnabled / freeFormSqlEnabled / crossJoinEnabled getters [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — toolbar flag getters", () => {

   // These getters proxy composerToolbarService — they must NOT cache or shadow the value.
   it("sqlEnabled should reflect composerToolbarService.sqlEnabled", async () => {
      const mocks = makeMocks();
      mocks.composerToolbarService.sqlEnabled = false;
      const { comp } = await renderComponent(mocks);

      expect(comp.sqlEnabled).toBe(false);

      mocks.composerToolbarService.sqlEnabled = true;
      expect(comp.sqlEnabled).toBe(true);
   });

   it("freeFormSqlEnabled should reflect composerToolbarService.freeFormSqlEnabled", async () => {
      const mocks = makeMocks();
      mocks.composerToolbarService.freeFormSqlEnabled = false;
      const { comp } = await renderComponent(mocks);

      expect(comp.freeFormSqlEnabled).toBe(false);

      mocks.composerToolbarService.freeFormSqlEnabled = true;
      expect(comp.freeFormSqlEnabled).toBe(true);
   });

   it("crossJoinEnabled should reflect composerToolbarService.crossJoinEnabled", async () => {
      const mocks = makeMocks();
      mocks.composerToolbarService.crossJoinEnabled = false;
      const { comp } = await renderComponent(mocks);

      expect(comp.crossJoinEnabled).toBe(false);

      mocks.composerToolbarService.crossJoinEnabled = true;
      expect(comp.crossJoinEnabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: worksheetCancel [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — worksheetCancel", () => {

   // 🔁 Regression-sensitive: worksheetCancel must write worksheet.current = joinPoint
   //    BEFORE emitting so the parent receives the correct snapshot.
   it("should set worksheet.current to joinPoint and emit onWorksheetCancel", async () => {
      const { comp } = await renderComponent();
      (comp as any).joinPoint = 3;
      comp.worksheet.current = 10;
      const spy = vi.fn();
      comp.onWorksheetCancel.subscribe(spy);

      comp.worksheetCancel();

      expect(comp.worksheet.current).toBe(3);
      expect(spy).toHaveBeenCalledWith([comp.worksheet, 3]);
   });
});

// ---------------------------------------------------------------------------
// Group 7: processClearLoadingCommand / updateLoadingMask [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processClearLoadingCommand / updateLoadingMask", () => {

   // 🔁 Regression-sensitive: each ShowLoadingMask increments a counter, ClearLoading
   //    decrements it. worksheet.loading should only become false when counter reaches 0.
   it("should decrement loadingEventCount and clear worksheet.loading when it reaches 0", async () => {
      const { comp, mocks } = await renderComponent();
      // Simulate one ShowLoadingMask first
      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false, count: 1 });

      expect(comp.worksheet.loading).toBe(true);

      mocks.dispatchCommand("ClearLoadingCommand", { count: 1 });

      expect(comp.worksheet.loading).toBe(false);
   });

   it("should keep worksheet.loading true if count > cleared amount", async () => {
      const { comp, mocks } = await renderComponent();
      // Simulate two ShowLoadingMask events
      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false, count: 1 });
      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false, count: 1 });

      mocks.dispatchCommand("ClearLoadingCommand", { count: 1 });

      expect(comp.worksheet.loading).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: processShowLoadingMaskCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processShowLoadingMaskCommand", () => {

   it("should set worksheet.loading=true after ShowLoadingMaskCommand", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false, count: 1 });

      expect(comp.worksheet.loading).toBe(true);
   });

   it("should NOT increment loadingEventCount when preparingData=true", async () => {
      const { comp, mocks } = await renderComponent();
      // Dispatch once with preparingData=true then clear 0 — counter should still be 0
      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: true, count: 1 });
      mocks.dispatchCommand("ClearLoadingCommand", { count: 0 });

      // Loading counter was never incremented, so worksheet.loading should be false
      expect(comp.worksheet.loading).toBe(false);
   });

   it("should set preparingData flag from the command", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: true, count: 1 });

      expect((comp as any).preparingData).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: processWSMoveAssembliesCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSMoveAssembliesCommand", () => {

   // 🔁 Regression-sensitive: positions must match by name; wrong index lookup would
   //    move the wrong table and desync the canvas layout.
   it("should update top/left for each named assembly", async () => {
      const { comp, mocks } = await renderComponent();
      const a1 = { name: "T1", top: 0, left: 0 };
      const a2 = { name: "T2", top: 0, left: 0 };
      comp.worksheet.assemblies = vi.fn().mockReturnValue([a1, a2]);

      mocks.dispatchCommand("WSMoveAssembliesCommand", {
         assemblyNames: ["T1", "T2"],
         tops: [100, 200],
         lefts: [10, 20],
      });

      expect(a1.top).toBe(100);
      expect(a1.left).toBe(10);
      expect(a2.top).toBe(200);
      expect(a2.left).toBe(20);
   });

   it("should skip assemblies not found by name without throwing", async () => {
      const { comp, mocks } = await renderComponent();
      comp.worksheet.assemblies = vi.fn().mockReturnValue([]);

      expect(() => {
         mocks.dispatchCommand("WSMoveAssembliesCommand", {
            assemblyNames: ["NotFound"],
            tops: [50],
            lefts: [5],
         });
      }).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 10: processWSLoadTableDataCountCommand [Risk 1]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — processWSLoadTableDataCountCommand", () => {

   it("should update table rowsCompleted and totalRows when found", async () => {
      const { comp, mocks } = await renderComponent();
      const table = {
         name: "Q1",
         classType: "TableAssembly",
         tableClassType: "BoundTableAssembly",
         rowsCompleted: false,
         totalRows: 0,
         duration: 0,
         exceededMaximum: null,
         info: { live: false, runtime: false },
         isAssemblyFocused: vi.fn().mockReturnValue(false),
      } as any;
      comp.worksheet.tables = [table];
      comp.worksheet.isAssemblyFocused = vi.fn().mockReturnValue(false);

      mocks.dispatchCommand("WSLoadTableDataCountCommand", {
         name: "Q1",
         count: 42,
         completed: true,
         duration: 100,
         exceededMsg: null,
      });

      // rowsCompleted comes from the refreshed (possibly new) table object at index 0
      expect(comp.worksheet.tables[0].rowsCompleted).toBe(true);
      expect(comp.worksheet.tables[0].totalRows).toBe(42);
   });

   it("should do nothing when table is not found", async () => {
      const { comp, mocks } = await renderComponent();
      comp.worksheet.tables = [];

      expect(() => {
         mocks.dispatchCommand("WSLoadTableDataCountCommand", {
            name: "NotFound",
            count: 5,
            completed: true,
            duration: 0,
            exceededMsg: null,
         });
      }).not.toThrow();
   });
});
