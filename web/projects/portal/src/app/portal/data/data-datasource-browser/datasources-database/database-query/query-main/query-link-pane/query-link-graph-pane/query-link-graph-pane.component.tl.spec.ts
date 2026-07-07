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
 * QueryLinkGraphPaneComponent - single-pass (+memory leak audit)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - refresh and graph-model loading
 *   Group 2 [Risk 2] - restoreJoinEditPaneModel and restoreGraphViewModel
 *   Group 3 [Risk 2] - closeJoinEditPane and derived getters
 *   Group 4 [Risk 3] - graphViewChange subscription teardown
 */

import { HttpClient, HttpParams, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { Subject, of } from "rxjs";

import { Rectangle } from "../../../../../../../../common/data/rectangle";
import { DataQueryModelService } from "../../../data-query-model.service";
import { QueryLinkGraphPaneComponent } from "./query-link-graph-pane.component";

function makeGraphModel(overrides: Record<string, unknown> = {}) {
   return {
      joinEdit: false,
      graphViewModel: {
         graphs: [
            { node: { id: "t1" }, showColumns: false },
         ],
      },
      joinEditPaneModel: {
         runtimeID: "runtime-2",
         tables: [
            { name: "T1", bounds: new Rectangle(1, 2, 3, 4), columns: [], joins: [] },
         ],
      },
      ...overrides,
   };
}

function createComponent() {
   const graphViewChange$ = new Subject<void>();

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         {
            provide: DataQueryModelService,
            useValue: {
               graphViewChange: graphViewChange$.asObservable(),
            },
         },
      ],
   });

   const comp = new QueryLinkGraphPaneComponent(
      TestBed.inject(HttpClient),
      TestBed.inject(DataQueryModelService),
   );
   comp.datasource = "Orders";
   comp.runtimeId = "runtime-1";

   return { comp, http: TestBed.inject(HttpClient), graphViewChange$ };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("QueryLinkGraphPaneComponent - single pass", () => {
   describe("Group 1 - refresh and graph loading", () => {
      it("should emit query-properties change and load a graph model on refresh", () => {
         const { comp, http } = createComponent();
         const model = makeGraphModel({ joinEdit: true });
         const postSpy = vi.spyOn(http, "post").mockReturnValue(of(model as never));
         const emitSpy = vi.spyOn(comp.onQueryPropertiesChanged, "emit");
         const editingSpy = vi.spyOn(comp.editingJoinChanged, "emit");

         comp.refresh();

         expect(emitSpy).toHaveBeenCalled();
         expect(postSpy).toHaveBeenCalledWith(
            "../api/data/datasource/query/graph",
            expect.objectContaining({ datasource: "Orders", runtimeID: "runtime-1" }),
         );
         expect(comp.loadingGraphPane).toBe(false);
         expect(comp.graphPaneModel).toBe(model);
         expect(editingSpy).toHaveBeenCalledWith(true);
      });
   });

   describe("Group 2 - model restoration", () => {
      it("should restore join pane bounds and graph showColumns from the previous model", () => {
         const { comp } = createComponent();
         const oldModel = makeGraphModel({
            joinEdit: true,
            graphViewModel: { graphs: [{ node: { id: "t1" }, showColumns: true }] },
            joinEditPaneModel: {
               runtimeID: "runtime-1",
               tables: [{ name: "T1", bounds: new Rectangle(9, 9, 9, 9), columns: [], joins: [] }],
            },
         });
         const newModel = makeGraphModel({
            joinEdit: true,
            graphViewModel: { graphs: [{ node: { id: "t1" }, showColumns: false }] },
            joinEditPaneModel: {
               runtimeID: "runtime-2",
               tables: [{ name: "T1", bounds: new Rectangle(1, 1, 1, 1), columns: [], joins: [] }],
            },
         });

         comp.restoreJoinEditPaneModel(newModel as never, oldModel as never);
         comp.restoreGraphViewModel(newModel as never, oldModel as never);

         expect(newModel.joinEditPaneModel.tables[0].bounds).toEqual(new Rectangle(9, 9, 9, 9));
         expect(newModel.graphViewModel.graphs[0].showColumns).toBe(true);
      });
   });

   describe("Group 3 - close and derived getters", () => {
      it("should close the join edit pane through HTTP, refresh the graph, and update editJoinRuntimeId", () => {
         const { comp, http } = createComponent();
         comp.graphPaneModel = makeGraphModel({ joinEdit: true }) as never;
         const getSpy = vi.spyOn(http, "get").mockReturnValue(of(null));
         const refreshSpy = vi.spyOn(comp, "onRefreshGraph").mockImplementation(() => {});

         comp.closeJoinEditPane(true);

         expect(getSpy).toHaveBeenCalledTimes(1);
         const [url, options] = getSpy.mock.calls[0];
         expect(url).toBe("../api/data/datasource/query/join-edit/close");
         expect((options as { params: HttpParams }).params.get("newRuntimeId")).toBe("runtime-2");
         expect((options as { params: HttpParams }).params.get("originRuntimeId")).toBe("runtime-1");
         expect((options as { params: HttpParams }).params.get("save")).toBe("true");
         expect(refreshSpy).toHaveBeenCalled();
      });

      it("should compute toolbarHeight, graphContainerHeight, and isJoinEditView from graphPaneModel", () => {
         const { comp } = createComponent();
         comp.graphPaneModel = makeGraphModel({ joinEdit: true }) as never;

         expect(comp.toolbarHeight).toBe(40);
         expect(comp.graphContainerHeight).toBe("calc(100% - 40px)");
         expect(comp.isJoinEditView()).toBe(true);
      });

      it("should return falsy for isJoinEditView when graphPaneModel is absent or joinEdit is false", () => {
         const { comp } = createComponent();

         // graphPaneModel not yet assigned
         expect(comp.isJoinEditView()).toBeFalsy();

         comp.graphPaneModel = makeGraphModel({ joinEdit: false }) as never;
         expect(comp.isJoinEditView()).toBeFalsy();
      });
   });

   describe("Group 4 - subscription cleanup", () => {
      it("should refresh on graphViewChange before destroy and stop reacting after destroy", () => {
         const { comp, graphViewChange$ } = createComponent();
         const refreshSpy = vi.spyOn(comp, "onRefreshGraph").mockImplementation(() => {});

         graphViewChange$.next();
         expect(refreshSpy).toHaveBeenCalledTimes(1);

         comp.ngOnDestroy();
         graphViewChange$.next();

         expect(refreshSpy).toHaveBeenCalledTimes(1);
         // subscriptions is private internal state — cast needed to verify teardown on ngOnDestroy
         expect((comp as unknown as { subscriptions: unknown }).subscriptions).toBeNull();
      });
   });
});
