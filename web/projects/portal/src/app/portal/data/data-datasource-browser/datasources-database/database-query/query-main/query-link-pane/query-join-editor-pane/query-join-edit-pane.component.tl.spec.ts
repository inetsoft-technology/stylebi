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
 * QueryJoinEditPane - single-pass (+timer/teardown)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnChanges reorder/populate and ngOnInit showTables selection
 *   Group 2 [Risk 3] - heartbeat, container wiring, and deferred initJoinConnections
 *   Group 3 [Risk 2] - jsPlumb drawing resume and cleanup on destroy
 */

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { ReplaySubject, Subject, of } from "rxjs";

import { Rectangle } from "../../../../../../../../common/data/rectangle";
import { QueryJoinEditPane } from "./query-join-edit-pane.component";

function makeColumn(name: string) {
   return { id: name, name, type: "string", table: "T1" };
}

function makeJoin(overrides: Record<string, unknown> = {}) {
   return {
      type: 0,
      orderPriority: 0,
      weak: false,
      mergingRule: 0,
      cardinality: 0,
      table: "T1",
      column: "C2",
      foreignTable: "T2",
      foreignColumn: "D2",
      baseJoin: false,
      ...overrides,
   };
}

function makeModel(overrides: Record<string, unknown> = {}) {
   return {
      runtimeID: "runtime-1",
      datasource: "Orders",
      physicalView: "view",
      tables: [
         {
            name: "T1",
            bounds: new Rectangle(0, 0, 100, 100),
            columns: [makeColumn("C1"), makeColumn("C2"), makeColumn("C3")],
            joins: [makeJoin()],
         },
         {
            name: "T2",
            bounds: new Rectangle(0, 0, 100, 100),
            columns: [
               { id: "D1", name: "D1", type: "string", table: "T2" },
               { id: "D2", name: "D2", type: "string", table: "T2" },
               { id: "D3", name: "D3", type: "string", table: "T2" },
            ],
            joins: [],
         },
      ],
      ...overrides,
   };
}

function createComponent() {
   const refreshGraph$ = new ReplaySubject<any>(1);
   const heartbeat$ = new Subject<void>();
   const jsPlumbInstance = {
      isSuspendDrawing: vi.fn(() => false),
      makeSource: vi.fn(),
      setSuspendDrawing: vi.fn(),
      deleteEveryConnection: vi.fn(),
   };
   const thumbnailService = {
      setDataType: vi.fn(),
      refreshGraph: refreshGraph$.asObservable(),
      heartbeat: heartbeat$.asObservable(),
      setJoinEditPaneModel: vi.fn(),
      setContainer: vi.fn(),
      clear: vi.fn(),
      initJoinConnections: vi.fn(),
      cleanup: vi.fn(),
      jsPlumbInstance,
   };

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
      ],
   });

   const comp = new QueryJoinEditPane(
      thumbnailService as never,
      TestBed.inject(HttpClient),
   );

   return { comp, http: TestBed.inject(HttpClient), thumbnailService, heartbeat$, refreshGraph$, jsPlumbInstance };
}

beforeEach(() => {
   vi.useFakeTimers();
});

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

describe("QueryJoinEditPane - single pass", () => {
   describe("Group 1 - changes and init", () => {
      it("should reorder joined columns to the front and mark the pane populated on model changes", () => {
         const { comp, thumbnailService } = createComponent();
         comp.model = makeModel() as never;

         comp.ngOnChanges({ model: { currentValue: comp.model, previousValue: null, firstChange: true, isFirstChange: () => true } });

         expect(thumbnailService.setJoinEditPaneModel).toHaveBeenCalledWith(comp.model);
         expect(comp.model.tables[0].columns.map(col => col.name)).toEqual(["C2", "C1", "C3"]);
         expect(comp.model.tables[1].columns.map(col => col.name)).toEqual(["D2", "D1", "D3"]);
      });

      it("should only keep the second table in showTables when both join tables have the same name", () => {
         const { comp } = createComponent();
         comp.model = makeModel({
            tables: [
               { name: "T1", bounds: new Rectangle(0, 0, 1, 1), columns: [], joins: [] },
               { name: "T1", bounds: new Rectangle(0, 0, 1, 1), columns: [], joins: [] },
            ],
         }) as never;

         comp.ngOnInit();

         expect(comp.showTables).toHaveLength(1);
         expect(comp.showTables[0].name).toBe("T1");
      });
   });

   describe("Group 2 - heartbeat and deferred graph init", () => {
      it("should send a heartbeat, wire the container, and defer join-connection initialization", () => {
         const { comp, http, thumbnailService, heartbeat$, jsPlumbInstance } = createComponent();
         comp.model = makeModel() as never;
         comp.jspContainerMain = { nativeElement: { id: "container" } } as never;
         jsPlumbInstance.isSuspendDrawing.mockReturnValue(true);
         vi.spyOn(http, "get").mockReturnValue(of(null));

         comp.ngOnInit();
         heartbeat$.next();
         comp.ngAfterViewInit();
         comp.ngOnChanges({ model: { currentValue: comp.model, previousValue: null, firstChange: true, isFirstChange: () => true } });
         comp.ngAfterViewChecked();

         expect(http.get).toHaveBeenCalledWith("../api/data/query/heartbeat", { params: expect.anything() });
         expect(thumbnailService.setContainer).toHaveBeenCalledWith(comp.jspContainerMain.nativeElement);
         expect(jsPlumbInstance.makeSource).toHaveBeenCalledWith("edit-join-table-column");
         expect(jsPlumbInstance.setSuspendDrawing).toHaveBeenCalledWith(false, true);

         vi.runAllTimers();

         expect(thumbnailService.clear).toHaveBeenCalled();
         expect(thumbnailService.initJoinConnections).toHaveBeenCalled();
      });
   });

   describe("Group 3 - destroy cleanup", () => {
      it("should emit refreshGraph from the thumbnail service and release resources on destroy", () => {
         const { comp, thumbnailService, refreshGraph$, jsPlumbInstance } = createComponent();
         const emitSpy = vi.spyOn(comp.onRefreshGraph, "emit");
         comp.model = makeModel() as never;

         refreshGraph$.next({ runtimeId: "runtime-1" });
         comp.ngOnDestroy();

         expect(emitSpy).toHaveBeenCalledWith({ runtimeId: "runtime-1" });
         expect(jsPlumbInstance.deleteEveryConnection).toHaveBeenCalledWith({ fireEvent: false });
         expect(thumbnailService.cleanup).toHaveBeenCalled();
         // subscription is private internal state — cast needed to verify teardown on ngOnDestroy
         expect((comp as unknown as { subscription: unknown }).subscription).toBeNull();
      });
   });
});
