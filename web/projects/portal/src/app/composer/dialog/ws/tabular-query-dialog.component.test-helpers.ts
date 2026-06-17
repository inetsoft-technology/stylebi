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
 * Shared test helpers for tabular-query-dialog.component P1/P2 spec files.
 *
 * Uses direct class instantiation.  Constructor takes 5 deps: ModelService,
 * HttpClient, OAuthAuthorizationService, ChangeDetectorRef, NgbModal.
 * ViewChild refs are undefined; methods that need them must stub the ref on
 * comp directly.
 */

import { of } from "rxjs";
import { TabularQueryDialog } from "./tabular-query-dialog.component";
import { TabularQueryDialogModel } from "../../data/ws/tabular-query-dialog-model";
import { TabularView } from "../../../common/data/tabular/tabular-view";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";

// ---------------------------------------------------------------------------
// Model / view factories
// ---------------------------------------------------------------------------

export function makeTabularView(overrides: Partial<TabularView> = {}): TabularView {
   return {
      row: 0,
      col: 0,
      type: "TEXT",
      views: [],
      editor: null,
      button: null,
      value: null,
      ...overrides,
   } as TabularView;
}

export function makeModel(overrides: Partial<TabularQueryDialogModel> = {}): TabularQueryDialogModel {
   return {
      dataSource: "rest-ds1",
      dataSources: ["rest-ds1"],
      tableName: "orders",
      tabularView: makeTabularView(),
      ...overrides,
   };
}

export function makeDataSourceType(overrides: Partial<TabularDataSourceTypeModel> = {}): TabularDataSourceTypeModel {
   return {
      name: "Rest",
      label: "REST JSON",
      dataSource: "rest-ds1",
      ...overrides,
   } as TabularDataSourceTypeModel;
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeModelService(model: TabularQueryDialogModel = makeModel()) {
   return { getModel: vi.fn().mockReturnValue(of(model)) };
}

export function makeHttp() {
   return {
      post: vi.fn().mockReturnValue(of(makeTabularView())),
      get: vi.fn().mockReturnValue(of(null)),
   };
}

export function makeOAuthService() {
   return {
      authorize: vi.fn().mockReturnValue(of({ access_token: "tok-123" })),
   };
}

export function makeChangeRef() {
   return { detectChanges: vi.fn() };
}

export function makeModal() {
   return {
      open: vi.fn().mockReturnValue({
         componentInstance: {},
         result: Promise.reject("dismissed"),
      }),
   };
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: TabularQueryDialog;
   http: ReturnType<typeof makeHttp>;
   modelSvc: ReturnType<typeof makeModelService>;
   oauthSvc: ReturnType<typeof makeOAuthService>;
   changeRef: ReturnType<typeof makeChangeRef>;
   modal: ReturnType<typeof makeModal>;
}

export function makeComponent(opts: {
   model?: TabularQueryDialogModel;
   http?: ReturnType<typeof makeHttp>;
   modelSvc?: ReturnType<typeof makeModelService>;
   oauthSvc?: ReturnType<typeof makeOAuthService>;
   changeRef?: ReturnType<typeof makeChangeRef>;
   modal?: ReturnType<typeof makeModal>;
   runtimeId?: string;
   tableName?: string;
   initTableName?: string;
   tables?: any[];
   dataSourceType?: TabularDataSourceTypeModel;
   skipNgOnInit?: boolean;
} = {}): ComponentResult {
   const model = opts.model ?? makeModel();
   const modelSvc = opts.modelSvc ?? makeModelService(model);
   const http = opts.http ?? makeHttp();
   const oauthSvc = opts.oauthSvc ?? makeOAuthService();
   const changeRef = opts.changeRef ?? makeChangeRef();
   const modal = opts.modal ?? makeModal();

   const comp = new TabularQueryDialog(
      modelSvc as any, http as any, oauthSvc as any, changeRef as any, modal as any,
   );

   comp.runtimeId = opts.runtimeId ?? "rt-123";
   comp.tableName = opts.tableName ?? "orders";
   comp.initTableName = opts.initTableName ?? "orders";
   comp.tables = opts.tables ?? [];
   comp.dataSourceType = opts.dataSourceType ?? makeDataSourceType();
   comp.applyVisible = true;

   if(!opts.skipNgOnInit) {
      comp.ngOnInit();
   }

   return { comp, http, modelSvc, oauthSvc, changeRef, modal };
}
