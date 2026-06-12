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
 * WSCompositeTableSidebarPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnChanges (join path): GET from join URI; compatibleTables populated
 *                       by filtering worksheet.tables with server-returned names
 *   Group 2 [Risk 3] — ngOnChanges (concat path): GET from concat URI; correct param key used
 *   Group 3 [Risk 2] — ngOnChanges (null worksheet): no HTTP call, compatibleTables reset to null
 *   Group 4 [Risk 2] — ngOnChanges (non-compositeTable change): no-op; prior state preserved
 *   Group 5 [Risk 2] — ngOnChanges: always sets compatibleTables=null before HTTP response
 *   Group 6 [Risk 2] — ngOnChanges: cancels prior in-flight subscription before starting a new one
 *   Group 7 [Risk 2] — dragCompatibleTable: calls dragService.put(DRAG_TABLE_ID, tableName)
 *   Group 8 [Risk 1] — isMergeJoinTable: true only when tableClassType="MergeJoinTableAssembly"
 *   Group 9 [Risk 1] — ngOnDestroy: unsubscribes from in-flight modelSub (memory-leak guard)
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — updateCompatibleTables does not guard against a null compositeTable.name when
 *     compositeTable is set to null between ngOnChanges firing and the HTTP subscription executing.
 *     With synchronous mocks this is invisible but could NullPointerError under high-frequency tab
 *     switching.
 */

import { NO_ERRORS_SCHEMA, SimpleChanges } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject, of, EMPTY } from "rxjs";

import {
   WSCompositeTableSidebarPane,
   DRAG_TABLE_ID,
} from "./ws-composite-table-sidebar-pane.component";
import { ModelService } from "../../../../widget/services/model.service";
import { DragService } from "../../../../widget/services/drag.service";
import { RelationalJoinTableAssembly } from "../../../data/ws/relational-join-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { Tool } from "../../../../../../../shared/util/tool";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const MODEL_SERVICE_MOCK = {
   getModel: vi.fn(),
};

const DRAG_SERVICE_MOCK = {
   put: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared factories
// ---------------------------------------------------------------------------

async function renderComponent() {
   const { fixture } = await render(WSCompositeTableSidebarPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
      ],
   });
   return fixture;
}

/** Minimal worksheet mock with pre-filled tables. */
function makeWorksheet(overrides: {
   selectedCompositeTable?: any;
   tables?: Array<{ name: string }>;
   runtimeId?: string;
} = {}): any {
   return {
      selectedCompositeTable: overrides.selectedCompositeTable ?? null,
      tables: (overrides.tables ?? []).map(t => ({ name: t.name })),
      runtimeId: overrides.runtimeId ?? "rt1",
   };
}

/** Returns a fake compositeTable input with a name property. */
function makeCompositeInput(name = "Composite1"): any {
   return { name };
}

/**
 * Creates an instance that satisfies `instanceof RelationalJoinTableAssembly`
 * (and therefore `instanceof AbstractJoinTableAssembly`) without executing the
 * constructor chain — avoiding the need to supply deep WSCompositeTableAssembly data.
 */
function makeJoinTableInstance(): any {
   return Object.create(RelationalJoinTableAssembly.prototype);
}

/**
 * Creates an instance that satisfies `instanceof ConcatenatedTableAssembly`
 * without executing the constructor.
 */
function makeConcatTableInstance(): any {
   return Object.create(ConcatenatedTableAssembly.prototype);
}

/** Triggers the compositeTable input-change lifecycle. */
function triggerCompositeTableChange(comp: WSCompositeTableSidebarPane, value: any): void {
   comp.compositeTable = value;
   const changes: SimpleChanges = {
      compositeTable: {
         currentValue: value,
         previousValue: null,
         firstChange: true,
         isFirstChange: () => true,
      },
   };
   comp.ngOnChanges(changes);
}

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   MODEL_SERVICE_MOCK.getModel.mockReset().mockReturnValue(of([]));
   DRAG_SERVICE_MOCK.put.mockClear();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnChanges — join table path [Risk 3]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: join path", () => {

   // 🔁 Regression-sensitive: wrong URI key ("concatTable" vs "joinTable") or wrong endpoint
   //    causes the server to return incompatible tables or a 400, breaking the join-table picker.
   it("should call getModel with the join URI when selectedCompositeTable is a join type", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([]));
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeJoinTableInstance() });

      triggerCompositeTableChange(comp, makeCompositeInput("JoinT1"));

      const [uri] = MODEL_SERVICE_MOCK.getModel.mock.calls[0];
      expect(uri).toContain("/join/compatible-insertion-tables/");
      expect(uri).not.toContain("/concat/");
   });

   it("should filter worksheet.tables by the names returned from the server", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(["Table1", "Table3"]));
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: makeJoinTableInstance(),
         tables: [{ name: "Table1" }, { name: "Table2" }, { name: "Table3" }],
      });

      triggerCompositeTableChange(comp, makeCompositeInput());

      expect(comp.compatibleTables).toHaveLength(2);
      expect(comp.compatibleTables.map((t: any) => t.name)).toEqual(
         expect.arrayContaining(["Table1", "Table3"]),
      );
   });

   it("should set compatibleTables to [] when no tables match the server response", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(["NotInWorksheet"]));
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: makeJoinTableInstance(),
         tables: [{ name: "TableA" }],
      });

      triggerCompositeTableChange(comp, makeCompositeInput());

      expect(comp.compatibleTables).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnChanges — concat table path [Risk 3]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: concat path", () => {

   // 🔁 Regression-sensitive: join and concat URIs differ; using the wrong one causes mismatch
   //    between the expected composite type and the server endpoint.
   it("should call getModel with the concat URI when selectedCompositeTable is a concat type", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([]));
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeConcatTableInstance() });

      triggerCompositeTableChange(comp, makeCompositeInput("ConcatT1"));

      const [uri] = MODEL_SERVICE_MOCK.getModel.mock.calls[0];
      expect(uri).toContain("/concat/compatible-insertion-tables/");
      expect(uri).not.toContain("/join/");
   });

   it("should use 'concatTable' as the HttpParams key for concat tables", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([]));
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeConcatTableInstance() });

      triggerCompositeTableChange(comp, makeCompositeInput("ConcatT1"));

      const [, params] = MODEL_SERVICE_MOCK.getModel.mock.calls[0];
      // HttpParams.get() reads the param value
      expect(params.get("concatTable")).toBe("ConcatT1");
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnChanges — null worksheet guard [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: null worksheet", () => {

   // 🔁 Regression-sensitive: guard must prevent HTTP calls when the parent worksheet
   //    input has not yet been bound (can happen during initial render ordering).
   it("should NOT call getModel when worksheet is null", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = null as any;

      triggerCompositeTableChange(comp, makeCompositeInput());

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
   });

   it("should reset compatibleTables to null even when worksheet is null", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      (comp as any).compatibleTables = [{ name: "Stale" }];
      comp.worksheet = null as any;

      triggerCompositeTableChange(comp, makeCompositeInput());

      expect(comp.compatibleTables).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnChanges — non-compositeTable change is no-op [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: unrelated input", () => {

   it("should not call getModel when 'worksheet' input changes but not compositeTable", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      (comp as any).compatibleTables = [{ name: "Preserved" }];

      comp.ngOnChanges({
         worksheet: {
            currentValue: makeWorksheet(),
            previousValue: null,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
      expect(comp.compatibleTables).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnChanges — compatibleTables reset [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: reset before response", () => {

   // 🔁 Regression-sensitive: stale data must be cleared immediately on change so the UI
   //    does not show the old compatible-table list while the new request is in-flight.
   it("should set compatibleTables=null before the HTTP response arrives", async () => {
      const pending$ = new Subject<string[]>();
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(pending$.asObservable());

      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      (comp as any).compatibleTables = [{ name: "OldTable" }];
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeJoinTableInstance() });

      triggerCompositeTableChange(comp, makeCompositeInput());

      // pending$ hasn't emitted yet — compatibleTables must already be null
      expect(comp.compatibleTables).toBeNull();

      pending$.complete();
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnChanges — subscription cancellation [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnChanges: cancels prior subscription", () => {

   // 🔁 Regression-sensitive: stale HTTP responses from prior compositeTable must not
   //    overwrite compatibleTables that belong to a newer composite table selection.
   it("should unsubscribe from the prior in-flight subscription on re-change", async () => {
      const first$ = new Subject<string[]>();
      const second$ = new Subject<string[]>();
      MODEL_SERVICE_MOCK.getModel
         .mockReturnValueOnce(first$.asObservable())
         .mockReturnValueOnce(second$.asObservable());

      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeJoinTableInstance() });

      // First change → in-flight first$
      triggerCompositeTableChange(comp, makeCompositeInput("First"));
      const firstSub = (comp as any).modelSub;
      expect(firstSub.closed).toBe(false);

      // Second change → should cancel first$ subscription
      triggerCompositeTableChange(comp, makeCompositeInput("Second"));
      expect(firstSub.closed).toBe(true);

      second$.complete();
   });

   it("should NOT update compatibleTables when the cancelled prior subscription eventually emits", async () => {
      const first$ = new Subject<string[]>();
      const second$ = new Subject<string[]>();
      MODEL_SERVICE_MOCK.getModel
         .mockReturnValueOnce(first$.asObservable())
         .mockReturnValueOnce(second$.asObservable());

      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: makeJoinTableInstance(),
         tables: [{ name: "T1" }, { name: "T2" }],
      });

      triggerCompositeTableChange(comp, makeCompositeInput("First"));
      triggerCompositeTableChange(comp, makeCompositeInput("Second")); // cancels first

      // second$ emits its result
      second$.next(["T2"]);
      second$.complete();

      expect(comp.compatibleTables).toHaveLength(1);
      expect((comp.compatibleTables as any)[0].name).toBe("T2");

      // Now the (already-unsubscribed) first$ emits — must NOT overwrite
      first$.next(["T1", "T2"]);
      first$.complete();

      expect(comp.compatibleTables).toHaveLength(1); // unchanged
   });
});

// ---------------------------------------------------------------------------
// Group 7: dragCompatibleTable [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — dragCompatibleTable", () => {

   // 🔁 Regression-sensitive: DRAG_TABLE_ID is the key the drop handler reads; a wrong key
   //    causes silent drop failures in the join/concat editor.
   it("should call dragService.put(DRAG_TABLE_ID, tableName)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const event: any = { dataTransfer: { setData: vi.fn() } };
      vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

      comp.dragCompatibleTable(event, "AddMe");

      expect(DRAG_SERVICE_MOCK.put).toHaveBeenCalledWith(DRAG_TABLE_ID, "AddMe");
   });

   it("should call Tool.setTransferData with an empty object", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const event: any = { dataTransfer: { setData: vi.fn() } };
      const setTransferSpy = vi.spyOn(Tool, "setTransferData").mockImplementation(() => {});

      comp.dragCompatibleTable(event, "AnyTable");

      expect(setTransferSpy).toHaveBeenCalledWith(event.dataTransfer, {});
   });
});

// ---------------------------------------------------------------------------
// Group 8: isMergeJoinTable [Risk 1]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — isMergeJoinTable", () => {

   it("should return true when selectedCompositeTable.tableClassType is 'MergeJoinTableAssembly'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: { tableClassType: "MergeJoinTableAssembly" },
      });

      expect(comp.isMergeJoinTable()).toBe(true);
   });

   it("should return false when selectedCompositeTable is null", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: null });

      expect(comp.isMergeJoinTable()).toBe(false);
   });

   it("should return false when tableClassType is 'RelationalJoinTableAssembly'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: { tableClassType: "RelationalJoinTableAssembly" },
      });

      expect(comp.isMergeJoinTable()).toBe(false);
   });

   it("should return false when tableClassType is 'ConcatenatedTableAssembly'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({
         selectedCompositeTable: { tableClassType: "ConcatenatedTableAssembly" },
      });

      expect(comp.isMergeJoinTable()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9: ngOnDestroy — memory-leak guard [Risk 1]
// ---------------------------------------------------------------------------

describe("WSCompositeTableSidebarPane — ngOnDestroy", () => {

   // 🔁 Regression-sensitive: an un-unsubscribed HTTP observable holds a reference to the
   //    component and prevents GC after the tab is closed.
   it("should unsubscribe from an in-flight getModel subscription on destroy", async () => {
      const pending$ = new Subject<string[]>();
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(pending$.asObservable());

      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.worksheet = makeWorksheet({ selectedCompositeTable: makeJoinTableInstance() });
      triggerCompositeTableChange(comp, makeCompositeInput());

      const sub = (comp as any).modelSub;
      expect(sub?.closed).toBe(false);

      fixture.destroy();

      expect(sub.closed).toBe(true);
   });

   it("should not throw when destroyed with no active subscription", async () => {
      const fixture = await renderComponent();
      // No ngOnChanges call → modelSub is undefined
      expect(() => fixture.destroy()).not.toThrow();
   });
});
