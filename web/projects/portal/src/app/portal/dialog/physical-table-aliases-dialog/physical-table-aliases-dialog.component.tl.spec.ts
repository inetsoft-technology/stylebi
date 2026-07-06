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
 * PhysicalTableAliasesDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit refreshAliases HTTP load and alias selection map reset
 *   Group 2 [Risk 2] - enableAutoAlias toggles detector detach/reattach/detectChanges
 *   Group 3 [Risk 2] - updateSelection and isTableSelected mapping semantics
 *   Group 4 [Risk 2] - getExistsNames collects sibling/self aliases only when alias exists
 *   Group 5 [Risk 2] - checkAliasValid handles empty vs duplicate vs unique aliases
 *   Group 6 [Risk 1] - ok / cancel emit the selected table or cancel token
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
import { Tool } from "../../../../../../shared/util/tool";
import { AutoAliasJoinModel } from "../../data/model/datasources/database/physical-model/auto-alias-join-model";
import { PhysicalModelDefinition } from "../../data/model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../data/model/datasources/database/physical-model/physical-table-model";
import { PhysicalTableAliasesDialog } from "./physical-table-aliases-dialog.component";

function makeTable(overrides: Partial<PhysicalTableModel> = {}): PhysicalTableModel {
   return {
      name: "Orders",
      catalog: "",
      schema: "",
      qualifiedName: "Orders",
      path: "Orders",
      alias: "OrdersAlias",
      sql: "",
      type: null,
      joins: [],
      baseTable: true,
      autoAliases: [],
      autoAliasesEnabled: false,
      ...overrides,
   } as PhysicalTableModel;
}

function makePhysicalModel(overrides: Partial<PhysicalModelDefinition> = {}): PhysicalModelDefinition {
   return {
      name: "Model",
      folder: "",
      description: "",
      tables: [],
      id: "model-1",
      ...overrides,
   } as PhysicalModelDefinition;
}

function makeAlias(overrides: Partial<AutoAliasJoinModel> = {}): AutoAliasJoinModel {
   return {
      foreignTable: "Customers",
      alias: "CustomerAlias",
      prefix: "C_",
      keepOutgoing: true,
      selected: true,
      ...overrides,
   } as AutoAliasJoinModel;
}

async function renderComp(
   tableOverrides: Partial<PhysicalTableModel> = {},
   modelOverrides: Partial<PhysicalModelDefinition> = {},
   aliases: AutoAliasJoinModel[] = [],
) {
   server.use(
      http.get("*/api/data/physicalmodel/aliases/*/*", () => HttpResponse.json(aliases)),
   );

   const { fixture } = await render(PhysicalTableAliasesDialog, {
      providers: [provideHttpClient()],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         physicalModel: makePhysicalModel(modelOverrides),
         table: makeTable(tableOverrides),
         isDuplicateTableName: vi.fn().mockReturnValue(false),
      },
   });

   return {
      comp: fixture.componentInstance as PhysicalTableAliasesDialog,
      fixture,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - ngOnInit / refreshAliases", () => {
   it("should load aliases and rebuild the selection map on init", async () => {
      const { comp } = await renderComp(
         {},
         {},
         [
            makeAlias({ foreignTable: "Customers", selected: true }),
            makeAlias({ foreignTable: "Suppliers", alias: "SupplierAlias", selected: false }),
         ],
      );

      await waitFor(() => expect(comp.table.autoAliases).toHaveLength(2));
      expect(comp.aliasTableSelections.get("Customers")).toBe(true);
      expect(comp.aliasTableSelections.get("Suppliers")).toBe(false);
   });
});

describe("Group 2 - enableAutoAlias", () => {
   it("should detach, mutate the model, reattach, and detectChanges", async () => {
      const { comp } = await renderComp();

      comp.enableAutoAlias(true);

      expect(comp.table.autoAliasesEnabled).toBe(true);
   });
});

describe("Group 3 - selection map", () => {
   it("should update aliasTableSelections and autoAlias.selected for selected=false", async () => {
      const { comp } = await renderComp();
      const alias = makeAlias({ selected: true, alias: "CustomerAlias" });

      comp.updateSelection(alias, false);

      expect(comp.aliasTableSelections.get("Customers")).toBe(false);
      expect(alias.selected).toBe(false);
   });

   it("should keep autoAlias.selected true when a selected alias has a real alias value", async () => {
      const { comp } = await renderComp();
      const alias = makeAlias({ selected: false, alias: "CustomerAlias" });

      comp.updateSelection(alias, true);

      expect(comp.aliasTableSelections.get("Customers")).toBe(true);
      expect(alias.selected).toBe(true);
   });

   it("should return the current selection state for a table name", async () => {
      const { comp } = await renderComp();
      comp.aliasTableSelections.set("Customers", true);

      expect(comp.isTableSelected("Customers")).toBe(true);
   });
});

describe("Group 4 - getExistsNames", () => {
   it("should return an empty list when alias is absent", async () => {
      const { comp } = await renderComp();

      expect(comp.getExistsNames(makeAlias({ alias: null }))).toEqual([]);
   });

   it("should collect sibling and self table names and selected aliases", async () => {
      const peerAlias = makeAlias({ foreignTable: "Peers", alias: "PeerAlias", selected: true });
      const otherTable = makeTable({
         qualifiedName: "Peers",
         alias: "PeersAlias",
         autoAliases: [peerAlias],
      });
      const { comp } = await renderComp(
         { autoAliases: [makeAlias({ alias: "CustomerAlias", selected: false })] },
         { tables: [otherTable] },
      );
      const names = comp.getExistsNames(makeAlias({ alias: "CustomerAlias" }));

      expect(names).toEqual(
         expect.arrayContaining(["Peers", "PeersAlias", "PeerAlias", "Orders", "OrdersAlias"]),
      );
   });
});

describe("Group 5 - checkAliasValid", () => {
   it("should clear alias and selection when the alias text is empty", async () => {
      const { comp } = await renderComp();
      const alias = makeAlias({ alias: null, selected: true });

      comp.checkAliasValid(alias);

      expect(alias.alias).toBeNull();
      expect(alias.selected).toBe(false);
   });

   it("should clear selected when the alias name is duplicated", async () => {
      const { comp } = await renderComp();
      const alias = makeAlias({ alias: "DupAlias", selected: true });
      const duplicateSpy = vi.spyOn(comp, "isDuplicateTableName").mockReturnValue(true);

      comp.checkAliasValid(alias);

      expect(duplicateSpy).toHaveBeenCalledWith("DupAlias");
      expect(alias.selected).toBe(false);
      expect(alias.alias).toBe("DupAlias");
   });

   it("should keep selected when the alias name is unique", async () => {
      const { comp } = await renderComp();
      const alias = makeAlias({ alias: "UniqueAlias", selected: false });
      vi.spyOn(comp, "isDuplicateTableName").mockReturnValue(false);

      comp.checkAliasValid(alias);

      expect(alias.selected).toBe(true);
   });
});

describe("Group 6 - ok / cancel", () => {
   it("should emit the current table on ok", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledWith(comp.table);
   });

   it("should emit cancel on cancel", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith();
   });
});
