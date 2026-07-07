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
 * Shared test helpers for PhysicalGraphPane P1/P2 specs.
 *
 * Uses direct instantiation (no ATL render) — the component has a 3-param constructor
 * (HttpClient, DebounceService, DataPhysicalModelService) with no jsPlumb dependency.
 * HTTP calls are made by the component itself (this.httpClient.post/put/get), so
 * httpClient is mocked directly with vi.fn().mockReturnValue(of(...)).
 */

import { ReplaySubject, Subject, of } from "rxjs";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { PhysicalGraphPane } from "./physical-graph-pane.component";
import { JoinGraphModel } from "../../../../model/datasources/database/physical-model/graph/join-graph-model";
import { JoinEditPaneModel } from "../../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { TableGraphModel } from "../../../../model/datasources/database/physical-model/graph/table-graph-model";
import { GraphViewModel } from "../../../../model/datasources/database/physical-model/graph/graph-view-model";
import { GraphModel } from "../../../../model/datasources/database/physical-model/graph/graph-model";
import { GraphColumnInfo } from "../../../../model/datasources/database/physical-model/graph/graph-column-info";

// ─────────────────────────────────────────────────────────────────────────────
// Model factories
// ─────────────────────────────────────────────────────────────────────────────

export function makeGraphModel(id: string, overrides: Partial<GraphModel> = {}): GraphModel {
   return {
      node: {
         id, name: id, label: id, tooltip: id,
         treeLink: null, aliasSource: null, outgoingAliasSource: null,
      } as any,
      edge: { input: [], output: [] },
      cols: [],
      bounds: new Rectangle(0, 0, 100, 50),
      showColumns: false,
      alias: false,
      autoAlias: false,
      sql: false,
      baseTable: false,
      autoAliasByOutgoing: false,
      designModeAlias: false,
      ...overrides,
   } as GraphModel;
}

export function makeGraphViewModel(graphs: GraphModel[] = []): GraphViewModel {
   return { graphs };
}

export function makeJoinGraphModel(overrides: Partial<JoinGraphModel> = {}): JoinGraphModel {
   return {
      joinEdit: false,
      graphViewModel: makeGraphViewModel(),
      joinEditPaneModel: null,
      ...overrides,
   };
}

export function makeJoinEditPaneModel(tables: TableGraphModel[] = []): JoinEditPaneModel {
   return {
      runtimeID: "join-runtime-456",
      datasource: "TestDS",
      physicalView: "TestView",
      tables,
   };
}

export function makeTableGraphModel(name: string, columns: GraphColumnInfo[] = [], joins: any[] = []): TableGraphModel {
   return {
      name,
      bounds: new Rectangle(0, 0, 100, 50),
      columns,
      joins: joins as any,
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Service mocks
// ─────────────────────────────────────────────────────────────────────────────

export function createMockPhysicalModelService() {
   const modelChangeSubject = new Subject<boolean>();
   const svc: any = {
      _modelChangeSubject: modelChangeSubject,
      modelChange: modelChangeSubject.asObservable(),
      onHighlightConnections: new Subject<any[]>(),
      onFullScreen: new ReplaySubject<boolean>(1),
      onRefreshWarning: new Subject<string>(),
      refreshWarning: vi.fn(),
      emitModelChange: vi.fn(),
      loadingModel: false,
   };
   return svc;
}

export function createMockHttpClient(defaults: {
   postResponse?: any;
   putResponse?: any;
   getResponse?: any;
} = {}) {
   return {
      post: vi.fn().mockReturnValue(of(defaults.postResponse ?? makeJoinGraphModel())),
      put: vi.fn().mockReturnValue(of(defaults.putResponse ?? null)),
      get: vi.fn().mockReturnValue(of(defaults.getResponse ?? null)),
   };
}

export function createMockDebounceService(immediate = true) {
   return {
      debounce: vi.fn().mockImplementation((_key: string, fn: () => void) => {
         if(immediate) fn();
      }),
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Component factory
// ─────────────────────────────────────────────────────────────────────────────

export interface PhysicalGraphPaneTestContext {
   comp: PhysicalGraphPane;
   physicalModelService: ReturnType<typeof createMockPhysicalModelService>;
   httpClient: ReturnType<typeof createMockHttpClient>;
   debounceService: ReturnType<typeof createMockDebounceService>;
   onPhysicalGraphSpy: ReturnType<typeof vi.fn>;
   onJoinEditingSpy: ReturnType<typeof vi.fn>;
   onModifiedSpy: ReturnType<typeof vi.fn>;
   onZoomSpy: ReturnType<typeof vi.fn>;
}

export function createPhysicalGraphPaneComp(overrides: {
   physicalModelService?: ReturnType<typeof createMockPhysicalModelService>;
   httpClient?: ReturnType<typeof createMockHttpClient>;
   debounceService?: ReturnType<typeof createMockDebounceService>;
   graphContainerDims?: { width: number; height: number };
} = {}): PhysicalGraphPaneTestContext {
   const physicalModelService = overrides.physicalModelService ?? createMockPhysicalModelService();
   const httpClient = overrides.httpClient ?? createMockHttpClient();
   const debounceService = overrides.debounceService ?? createMockDebounceService();

   const comp = new PhysicalGraphPane(
      httpClient as any,
      debounceService as any,
      physicalModelService as any,
   );
   comp.runtimeId = "runtime-123";
   comp.datasource = "TestDS";
   comp.physicalView = "TestView";
   comp.selectedGraphModels = [];

   const dims = overrides.graphContainerDims ?? { width: 800, height: 600 };
   (comp as any).graphContainer = {
      nativeElement: {
         clientWidth: dims.width,
         clientHeight: dims.height,
         scrollTop: 0,
         scrollLeft: 0,
      },
   };

   const onPhysicalGraphSpy = vi.fn();
   const onJoinEditingSpy = vi.fn();
   const onModifiedSpy = vi.fn();
   const onZoomSpy = vi.fn();

   comp.onPhysicalGraph.subscribe(onPhysicalGraphSpy);
   comp.onJoinEditing.subscribe(onJoinEditingSpy);
   comp.onModified.subscribe(onModifiedSpy);
   comp.onZoom.subscribe(onZoomSpy);

   return { comp, physicalModelService, httpClient, debounceService, onPhysicalGraphSpy, onJoinEditingSpy, onModifiedSpy, onZoomSpy };
}
