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
 * AutoJoinTablesDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit refreshColumns for metaAvailable=false / true
 *   Group 2 [Risk 2] - joinOnChanged swaps available columns between name/meta sets
 *   Group 3 [Risk 3] - ok() name mode builds joinItems and emits onCommit after POST
 *   Group 4 [Risk 2] - ok() meta mode ignores self-joins and existing joins
 *   Group 5 [Risk 1] - cancel emits cancel token
 *
 * Confirmed bugs (it.fails): none
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { AutoJoinTablesDialog } from "./auto-join-tables-dialog.component";
import { PhysicalModelDefinition } from "../../data/model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../data/model/datasources/database/physical-model/physical-table-model";
import { PhysicalTableType } from "../../data/model/datasources/database/physical-model/physical-table-type.enum";
import { JoinType } from "../../data/model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../data/model/datasources/database/physical-model/merging-rule.enum";
import { Cardinality } from "../../data/model/datasources/database/physical-model/cardinality.enum";

function makeTable(qualifiedName: string, joins: any[] = []): PhysicalTableModel {
   return {
      name: qualifiedName,
      catalog: "",
      schema: "",
      qualifiedName,
      path: qualifiedName,
      alias: "",
      sql: "",
      type: PhysicalTableType.PHYSICAL,
      joins,
      baseTable: true,
   } as PhysicalTableModel;
}

function makePhysicalModel(): PhysicalModelDefinition {
   return {
      name: "Model",
      folder: "",
      description: "",
      id: "model-1",
      tables: [makeTable("Orders"), makeTable("Customers")],
   } as PhysicalModelDefinition;
}

function makeNameColumn(column: string, tables: string[], selected = true) {
   return { column, label: column, tables, selected };
}

function makeMetaColumn(column: string, table: string, forColumns: any[], selected = true) {
   return { column, label: column, table, forColumns, selected };
}

async function renderComp(
   inputs: Partial<AutoJoinTablesDialog> = {},
   response: any = {
      nameColumns: [],
      metaColumns: [],
      metaAvailable: false,
   },
) {
   server.use(
      http.post("*/api/data/physicalmodel/autoJoin/*", () => HttpResponse.json(response)),
   );

   const { fixture } = await render(AutoJoinTablesDialog, {
      providers: [provideHttpClient()],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         physicalModel: makePhysicalModel(),
         databaseName: "SalesDB",
         ...inputs,
      },
   });

   return { comp: fixture.componentInstance as AutoJoinTablesDialog, fixture };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - ngOnInit refreshColumns", () => {
   it("should use name columns when metaAvailable is false", async () => {
      const response = {
         nameColumns: [makeNameColumn("id", ["Orders", "Customers"])],
         metaColumns: [],
         metaAvailable: false,
      };
      const { comp } = await renderComp({}, response);

      await waitFor(() => expect(comp.availableColumns).toHaveLength(1));
      expect(comp.joinOnName).toBe(true);
   });

   it("should switch to meta columns and disable joinOnName when metaAvailable is true", async () => {
      const response = {
         nameColumns: [makeNameColumn("id", ["Orders", "Customers"])],
         metaColumns: [makeMetaColumn("metaId", "Orders", [])],
         metaAvailable: true,
      };
      const { comp } = await renderComp({}, response);

      await waitFor(() => expect(comp.availableColumns).toHaveLength(1));
      expect(comp.joinOnName).toBe(false);
      expect(comp.availableColumns[0].column).toBe("metaId");
   });
});

describe("Group 2 - joinOnChanged", () => {
   it("should swap to nameColumns when joinOnName is true", async () => {
      const { comp } = await renderComp();
      comp.autoJoinColumns = {
         nameColumns: [makeNameColumn("id", ["Orders", "Customers"])],
         metaColumns: [makeMetaColumn("metaId", "Orders", [])],
         metaAvailable: true,
      };
      comp.joinOnName = true;

      comp.joinOnChanged();

      expect(comp.availableColumns).toEqual(comp.autoJoinColumns.nameColumns);
   });

   it("should swap to metaColumns when joinOnName is false", async () => {
      const { comp } = await renderComp();
      comp.autoJoinColumns = {
         nameColumns: [makeNameColumn("id", ["Orders", "Customers"])],
         metaColumns: [makeMetaColumn("metaId", "Orders", [])],
         metaAvailable: true,
      };
      comp.joinOnName = false;

      comp.joinOnChanged();

      expect(comp.availableColumns).toEqual(comp.autoJoinColumns.metaColumns);
   });
});

describe("Group 3 - ok() name mode", () => {
   it("should build a join item for the selected name column and emit onCommit after POST", async () => {
      let capturedBody: any;
      server.use(
         http.post("*/api/data/physicalmodel/add/autoJoin", async ({ request }) => {
            capturedBody = await request.json();
            return HttpResponse.json({});
         }),
      );
      const { comp } = await renderComp({
         physicalModel: makePhysicalModel(),
      }, {
         nameColumns: [makeNameColumn("id", ["Orders", "Customers"])],
         metaColumns: [],
         metaAvailable: false,
      });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.availableColumns = [makeNameColumn("id", ["Orders", "Customers"])];
      comp.availableColumns[0].selected = true;

      comp.ok();

      await waitFor(() => expect(capturedBody).toBeDefined());
      expect(capturedBody.joinItems).toHaveLength(1);
      expect(capturedBody.joinItems[0].join.table).toBe("Orders");
      expect(capturedBody.joinItems[0].join.foreignTable).toBe("Customers");
      await waitFor(() => expect(emitSpy).toHaveBeenCalledWith(null));
   });
});

describe("Group 4 - ok() meta mode", () => {
   it("should ignore self joins and existing joins", async () => {
      const existingJoin = {
         type: JoinType.EQUAL,
         orderPriority: 1,
         weak: false,
         mergingRule: MergingRule.AND,
         cardinality: Cardinality.MANY_TO_ONE,
         table: "Orders",
         column: "id",
         foreignTable: "Customers",
         foreignColumn: "id",
         baseJoin: false,
      };
      let capturedBody: any;
      server.use(
         http.post("*/api/data/physicalmodel/add/autoJoin", async ({ request }) => {
            capturedBody = await request.json();
            return HttpResponse.json({});
         }),
      );
      const { comp } = await renderComp(
         {
            physicalModel: {
               ...makePhysicalModel(),
               tables: [makeTable("Orders", [existingJoin]), makeTable("Customers")],
            } as PhysicalModelDefinition,
         },
         {
            nameColumns: [],
            metaColumns: [
               makeMetaColumn("id", "Orders", [
                  { table: "Orders", column: "id" },
                  { table: "Customers", column: "id" },
               ]),
            ],
            metaAvailable: true,
         },
      );
      comp.joinOnName = false;
      comp.availableColumns = [
         makeMetaColumn("id", "Orders", [
            { table: "Orders", column: "id" },
            { table: "Customers", column: "id" },
         ]),
      ];
      comp.availableColumns[0].selected = true;

      comp.ok();

      await waitFor(() => expect(capturedBody).toBeDefined());
      expect(capturedBody.joinItems).toHaveLength(0);
   });
});

describe("Group 5 - cancel()", () => {
   it("should emit cancel", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});
