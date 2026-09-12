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
 * Shared test helpers for DatasourceSelectionViewComponent P1/P2 spec files.
 *
 * Mocking strategy:
 *   - DatasourceSelectionService — componentProviders override (component is self-provided);
 *     mock returns of(MOCK_MODEL) for getDatasourceSelectionViewModel() and a configurable
 *     DatasourceType from getDataSourceType().
 *   - GettingStartedService — { isConnectTo: vi.fn(), continue: vi.fn() }.
 *   - Router — { navigate: vi.fn() }.
 *   - ActivatedRoute — { paramMap, queryParamMap, parent } built from opts.
 *   - No HttpClient / MSW needed because DatasourceSelectionService is fully mocked.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { of } from "rxjs";

import { DatasourceSelectionViewComponent } from "./datasource-selection-view.component";
import { DatasourceSelectionService } from "./datasource-selection.service";
import { GettingStartedService } from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { DataSourceListing } from "./datasource-listing/datasource-listing";
import { DatasourceSelectionViewModel } from "./datasource-selection-view-model";
import { DatasourceType } from "../datasource-type";

// ── Shared fixture data ──────────────────────────────────────────────────────

export const MOCK_LISTING_MYSQL: DataSourceListing = {
   name: "MySQL",
   category: "Database",
   iconUrl: "/icons/mysql.png",
   keywords: ["mysql", "sql", "relational"],
};

export const MOCK_LISTING_SALESFORCE: DataSourceListing = {
   name: "Salesforce",
   category: "Cloud",
   iconUrl: "/icons/salesforce.png",
   keywords: ["salesforce", "crm", "cloud"],
};

export const MOCK_MODEL: DatasourceSelectionViewModel = {
   listings: [MOCK_LISTING_MYSQL, MOCK_LISTING_SALESFORCE],
   categories: ["Database", "Cloud"],
};

// ── Render helper ────────────────────────────────────────────────────────────

export interface SelectionViewRenderOpts {
   /** Route param parentPath — defaults to empty string */
   parentPath?: string;
   /** When true, adds gettingStartedRouteTime to query params */
   gettingStarted?: boolean;
   /** DatasourceType returned by getDataSourceType() — defaults to JDBC */
   datasourceType?: DatasourceType;
}

export async function renderComp(opts: SelectionViewRenderOpts = {}) {
   const {
      parentPath = "",
      gettingStarted = false,
      datasourceType = DatasourceType.JDBC,
   } = opts;

   const routerMock = { navigate: vi.fn() };
   const parentRouteMock = {};

   const gettingStartedMock = {
      isConnectTo: vi.fn<() => boolean>().mockReturnValue(false),
      continue: vi.fn(),
   };

   const datasourceServiceMock = {
      getDatasourceSelectionViewModel: vi.fn().mockReturnValue(of(MOCK_MODEL)),
      getDataSourceType: vi.fn().mockReturnValue(of(datasourceType)),
   };

   const queryParams: Record<string, string> = {};
   if(gettingStarted) {
      queryParams["gettingStartedRouteTime"] = "1700000000000";
   }

   const route = {
      paramMap: of(convertToParamMap({ parentPath })),
      queryParamMap: of(convertToParamMap(queryParams)),
      parent: parentRouteMock,
   };

   const { fixture } = await render(DatasourceSelectionViewComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: Router, useValue: routerMock },
         { provide: ActivatedRoute, useValue: route },
         { provide: GettingStartedService, useValue: gettingStartedMock },
      ],
      componentProviders: [
         { provide: DatasourceSelectionService, useValue: datasourceServiceMock },
      ],
   });

   const comp = fixture.componentInstance as DatasourceSelectionViewComponent;
   return {
      comp,
      fixture,
      routerMock,
      gettingStartedMock,
      datasourceServiceMock,
      parentRouteMock,
      route,
   };
}
