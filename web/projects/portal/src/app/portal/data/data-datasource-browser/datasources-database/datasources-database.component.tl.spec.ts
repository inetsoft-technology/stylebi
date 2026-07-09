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
 * DatasourcesDatabaseComponent - Angular Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - selectAdditional: ctrl selection must not duplicate or corrupt selection state
 *   Group 2 [Risk 3] - deleteProperty: batch delete must remove data and clear selected rows consistently
 *   Group 3 [Risk 3] - getDBUrl: generated JDBC URLs must match database type-specific contracts
 *
 * Fixed bugs (Issue #75583):
 *
 *   Bug A - selectAdditional duplicate ctrl-click (Group 1):
 *     Ctrl-clicking the same additional connection twice appended the same index twice.
 *
 *   Bug B - deleteProperty leaves stale selected rows (Group 2):
 *     The method iterated over a cloned selection but spliced the live selectedProperty array by
 *     the clone index, leaving later selections active after deletion.
 *
 *   Bug C - ACCESS URL ignores dataSourceName (Group 3):
 *     Additional ACCESS connections could display an undefined URL because getDBUrl only fell back
 *     to dataSourceName when customUrl equaled an exact sentinel value, not when it was unset.
 *
 *   Bug D - INFORMIX URL branches were inverted (Group 3):
 *     A populated serverName was not emitted as INFORMIXSERVER and an empty locale was emitted
 *     instead.
 *
 * KEY contracts:
 *   Additional connection selection is a multi-select list: normal click replaces selection,
 *   ctrl-click adds/toggles one item, and each selected index appears at most once.
 *   Connection pool property deletion must clear both the poolProperties keys and UI selection.
 */

import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { EMPTY, of } from "rxjs";

import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import {
   DataSourceEditorModel
} from "../../../../../../../shared/util/datasource/data-source-settings-page";
import { DataSourceSettingsModel } from "../../../../../../../shared/util/model/data-source-settings-model";
import { DatabaseDefinitionModel } from "../../../../../../../shared/util/model/database-definition-model";
import { DatasourceDatabaseType } from "../../../../../../../shared/util/model/datasource-database-type";
import { ComponentTool } from "../../../../common/util/component-tool";
import {
   GettingStartedService
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { DatasourcesDatabaseComponent } from "./datasources-database.component";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

function createDatabase(overrides: Partial<DatabaseDefinitionModel> = {}): DatabaseDefinitionModel {
   return {
      name: "SalesDB",
      type: DatasourceDatabaseType.MYSQL,
      info: {
         poolProperties: {},
         customEditMode: false,
         databaseName: "sales",
      } as any,
      network: {
         hostName: "localhost",
         portNumber: 3306,
      },
      authentication: {
         required: false,
         userName: null,
         password: null,
         useCredentialId: false,
         credentialId: null,
         credentialVisible: false,
      },
      deletable: true,
      ansiJoin: false,
      transactionIsolation: -1,
      tableNameOption: 3,
      defaultDatabase: null,
      changeDefaultDB: false,
      ...overrides,
   };
}

function createModel(database: DatabaseDefinitionModel): DataSourceEditorModel {
   const settings: DataSourceSettingsModel = {
      dataSource: database,
      permissions: null,
      additionalDataSources: [],
      uploadEnabled: false,
   };

   return {
      path: "SalesDB",
      type: null,
      settings,
   };
}

function createProviders() {
   const router = { navigate: vi.fn() };
   const stompConnection = {
      subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
      disconnect: vi.fn(),
   };

   return {
      router,
      providers: [
         { provide: ActivatedRoute, useValue: { paramMap: EMPTY } },
         { provide: Router, useValue: router },
         { provide: NgbModal, useValue: {} },
         { provide: AppInfoService, useValue: { isEnterprise: vi.fn(() => of(false)) } },
         {
            provide: GettingStartedService,
            useValue: {
               isConnectTo: vi.fn(() => false),
               setDataSourcePath: vi.fn(),
               continue: vi.fn(),
            },
         },
         {
            provide: StompClientService,
            useValue: {
               connect: vi.fn(() => of(stompConnection)),
            },
         },
      ],
   };
}

async function renderDatabase(database: DatabaseDefinitionModel = createDatabase()) {
   const { providers, router } = createProviders();
   const result = await render(DatasourcesDatabaseComponent, {
      imports: [CommonModule, FormsModule, ReactiveFormsModule, HttpClientTestingModule],
      providers,
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         database,
         model: createModel(database),
         additionalVisible: false,
      },
   });

   return { ...result, router };
}

function mouse(ctrlKey = false, shiftKey = false): MouseEvent {
   return { ctrlKey, shiftKey, preventDefault: vi.fn() } as unknown as MouseEvent;
}

// ---------------------------------------------------------------------------
// Group 1 - selectAdditional: multi-select state [Risk 3]
// ---------------------------------------------------------------------------

describe("DatasourcesDatabaseComponent - selectAdditional - multi-select state [Group 1, Risk 3]", () => {

   // Regression-sensitive: normal click must reset prior ctrl selections to one active row.
   it("should replace the selection with a single index on a normal click", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;
      comp.selectedAdditionalIndex = [0, 1];

      comp.selectAdditional(mouse(false), 2);

      expect(comp.selectedAdditionalIndex).toEqual([2]);
      expect(comp.additionalSelected(2)).toBe(true);
      expect(comp.additionalSelected(0)).toBe(false);
   });

   // Regression-sensitive: ctrl multi-select must preserve each selected index once.
   it("should add a second index on ctrl-click without losing the first index", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;

      comp.selectAdditional(mouse(false), 0);
      comp.selectAdditional(mouse(true), 2);

      expect(comp.selectedAdditionalIndex).toEqual([0, 2]);
      expect(comp.additionalSelected(0)).toBe(true);
      expect(comp.additionalSelected(2)).toBe(true);
   });

   // Regression-sensitive: duplicate selected indexes corrupt delete/edit enablement and selected datasource payloads.
   // Risk Point/Contract: ctrl-click on an already selected row should toggle or preserve uniqueness, not append a duplicate.
   it("should not append a duplicate index when ctrl-clicking an already selected additional connection", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;

      comp.selectAdditional(mouse(false), 1);
      comp.selectAdditional(mouse(true), 1);

      expect(new Set(comp.selectedAdditionalIndex).size).toBe(comp.selectedAdditionalIndex.length);
   });
});

// ---------------------------------------------------------------------------
// Group 2 - deleteProperty: selection/data consistency [Risk 3]
// ---------------------------------------------------------------------------

describe("DatasourcesDatabaseComponent - deleteProperty - batch delete consistency [Group 2, Risk 3]", () => {

   // Regression-sensitive: single-row delete is the baseline contract for poolProperties and selectedProperty.
   it("should remove one selected pool property and clear its selected row after confirmation", async () => {
      const { fixture } = await renderDatabase(createDatabase({
         info: { poolProperties: { fetchSize: "100" } } as any,
      }));
      const comp = fixture.componentInstance;
      comp.selectedProperty = [{ key: "fetchSize", value: "100" }];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("yes" as any);

      try {
         comp.deleteProperty();
         await Promise.resolve();

         expect(comp.database.info.poolProperties).toEqual({});
         expect(comp.selectedProperty).toEqual([]);
      }
      finally {
         confirmSpy.mockRestore();
      }
   });

   // Regression-sensitive: deleting several selected properties must not leave a stale selected row in the UI.
   // Risk Point/Contract: data removal and UI selection clearing are one atomic state transition.
   it("should remove all selected pool properties and clear the full selection after confirmation", async () => {
      const { fixture } = await renderDatabase(createDatabase({
         info: { poolProperties: { fetchSize: "100", maxRows: "500" } } as any,
      }));
      const comp = fixture.componentInstance;
      comp.selectedProperty = [
         { key: "fetchSize", value: "100" },
         { key: "maxRows", value: "500" },
      ];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("yes" as any);

      try {
         comp.deleteProperty();
         await Promise.resolve();

         expect(comp.database.info.poolProperties).toEqual({});
         expect(comp.selectedProperty).toEqual([]);
      }
      finally {
         confirmSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3 - getDBUrl: URL construction [Risk 3]
// ---------------------------------------------------------------------------

describe("DatasourcesDatabaseComponent - getDBUrl - type-specific JDBC contracts [Group 3, Risk 3]", () => {

   // Regression-sensitive: canonical MySQL URLs are used in the additional connection list and tooltips.
   it("should build a MySQL URL with host, port, and database name", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;

      const url = comp.getDBUrl(
         DatasourceDatabaseType.MYSQL,
         { hostName: "db.example.com", portNumber: 3306 },
         { databaseName: "warehouse" } as any);

      expect(url).toBe("jdbc:mysql://db.example.com:3306/warehouse");
   });

   // Regression-sensitive: ACCESS additional connections must display the selected file path, not undefined.
   // Risk Point/Contract: AccessDatabaseInfoModel stores the file in dataSourceName.
   it("should build an ACCESS URL from dataSourceName when customUrl is absent", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;

      const url = comp.getDBUrl(
         DatasourceDatabaseType.ACCESS,
         null,
         { dataSourceName: "C:/data/sales.accdb" } as any);

      expect(url).toBe("jdbc:ucanaccess://C:/data/sales.accdb");
   });

   // Regression-sensitive: Informix serverName is required connection identity; omitting it points users at the wrong DB.
   // Risk Point/Contract: a populated serverName must be emitted as INFORMIXSERVER.
   it("should include INFORMIXSERVER for Informix when serverName is populated and db locale is empty", async () => {
      const { fixture } = await renderDatabase();
      const comp = fixture.componentInstance;

      const url = comp.getDBUrl(
         DatasourceDatabaseType.INFORMIX,
         { hostName: "informix.example.com", portNumber: 1526 },
         {
            databaseName: "stores",
            serverName: "ol_srv",
            databaseLocale: "",
         } as any);

      expect(url).toBe("jdbc:informix-sqli://informix.example.com:1526/stores:INFORMIXSERVER=ol_srv");
   });
});
