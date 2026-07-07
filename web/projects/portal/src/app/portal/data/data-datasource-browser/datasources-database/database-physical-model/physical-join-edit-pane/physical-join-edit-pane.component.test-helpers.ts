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
 * Shared test helpers for PhysicalJoinEditPane P1/P2 specs.
 *
 * Uses direct instantiation (no ATL render) — 2-param constructor
 * (JoinThumbnailService, HttpClient). JoinThumbnailService is fully mocked
 * so no real jsPlumb initialization occurs.
 */

import { Subject, of } from "rxjs";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { PhysicalJoinEditPane } from "./physical-join-edit-pane.component";
import { GraphColumnInfo } from "../../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinEditPaneModel } from "../../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { TableGraphModel } from "../../../../model/datasources/database/physical-model/graph/table-graph-model";
import { JoinModel } from "../../../../model/datasources/database/physical-model/join-model";

// ─────────────────────────────────────────────────────────────────────────────
// Model factories
// ─────────────────────────────────────────────────────────────────────────────

export function makeColumn(name: string, table = "orders"): GraphColumnInfo {
   return { id: `${table}-${name}`, name, type: "string", table };
}

interface ModelOverrides {
   leftTable?: TableGraphModel;
   rightTable?: TableGraphModel;
   runtimeID?: string;
   tables?: TableGraphModel[];
}

export function makeJoin(column: string, foreignColumn: string, foreignTable: string): Partial<JoinModel> {
   return { column, foreignColumn, foreignTable, type: "=" as any, orderPriority: 0, weak: false };
}

export function makeTableModel(name: string, columns: GraphColumnInfo[], joins: Partial<JoinModel>[] = []): TableGraphModel {
   return {
      name,
      bounds: new Rectangle(0, 0, 100, 50),
      columns,
      joins: joins as JoinModel[],
   };
}

export function makeModel(overrides: ModelOverrides = {}): JoinEditPaneModel {
   const leftTable = overrides.leftTable
      ?? makeTableModel("orders", [makeColumn("id"), makeColumn("name")], []);
   const rightTable = overrides.rightTable
      ?? makeTableModel("customers", [makeColumn("id"), makeColumn("email")], []);
   return {
      runtimeID: overrides.runtimeID ?? "rt-abc",
      datasource: "TestDS",
      physicalView: "TestView",
      tables: overrides.tables ?? [leftTable, rightTable],
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Service mocks
// ─────────────────────────────────────────────────────────────────────────────

export function makeJspMock(suspendDrawing = false) {
   return {
      isSuspendDrawing: vi.fn().mockReturnValue(suspendDrawing),
      setSuspendDrawing: vi.fn(),
      makeSource: vi.fn(),
      deleteEveryConnection: vi.fn(),
      reset: vi.fn(),
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Component factory
// ─────────────────────────────────────────────────────────────────────────────

export interface PhysicalJoinEditPaneTestContext {
   comp: PhysicalJoinEditPane;
   thumbnailService: any;
   jspMock: ReturnType<typeof makeJspMock>;
   httpClient: any;
}

export function createComp(opts: {
   jspMock?: ReturnType<typeof makeJspMock>;
   suspendDrawing?: boolean;
   runtimeID?: string;
   httpGetResponse?: any;
   withModel?: boolean;
} = {}): PhysicalJoinEditPaneTestContext {
   const jspMock = opts.jspMock ?? makeJspMock(opts.suspendDrawing ?? false);

   const thumbnailService: any = {
      refreshGraph: new Subject<any>(),
      heartbeat: new Subject<number>().asObservable(),
      jsPlumbInstance: jspMock,
      setJoinEditPaneModel: vi.fn(),
      setContainer: vi.fn(),
      clear: vi.fn(),
      initJoinConnections: vi.fn(),
      cleanup: vi.fn(),
   };

   const httpClient: any = {
      get: vi.fn().mockReturnValue(of(opts.httpGetResponse ?? null)),
   };

   const comp = new PhysicalJoinEditPane(thumbnailService, httpClient);
   (comp as any).jspContainerMain = { nativeElement: document.createElement("div") };

   if(opts.withModel !== false) {
      comp.model = makeModel({ runtimeID: opts.runtimeID ?? "rt-abc" });
   }

   return { comp, thumbnailService, jspMock, httpClient };
}
