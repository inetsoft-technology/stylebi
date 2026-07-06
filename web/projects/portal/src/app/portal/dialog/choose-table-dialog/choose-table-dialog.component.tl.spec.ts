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
 * ChooseTableDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit root loading for TABLE and PHYSICAL_MODEL modes
 *   Group 2 [Risk 2] - title / tableNameNull / databaseParr getters
 *   Group 3 [Risk 2] - selectNode and ok/cancel payloads
 *
 * Confirmed bugs (it.fails): none
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - heavy child tree -> importOverrides stub with the methods the component calls
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { ConditionTypes } from "../../data/model/datasources/database/vpm/condition/condition-types.enum";
import { DatabaseTreeNodeType } from "../../data/model/datasources/database/database-tree-node-type";
import { LoadingIndicatorPaneComponent } from "../../data/data-datasource-browser/datasources-database/common-components/loading-indicator-pane/loading-indicator-pane.component";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { ChooseTableDialog } from "./choose-table-dialog.component";

@Component({ selector: "tree", standalone: true, template: "" })
class TreeComponentStub {
   @Input() searchEnabled: boolean;
   @Input() root: any;
   @Input() showRoot: boolean;
   @Input() showIcon: boolean;
   @Input() iconFunction: any;
   @Input() fillHeight: boolean;
   @Input() inputFocus: boolean;
   @Output() nodesSelected = new EventEmitter<any[]>();
   @Output() nodeExpanded = new EventEmitter<any>();
   @Output() searchStart = new EventEmitter<boolean>();
   expandAll = vi.fn();
   exclusiveSelectNode = vi.fn();
   selectAndExpandToNode = vi.fn();
}

function makeNode(overrides: Record<string, any> = {}) {
   return {
      children: [],
      data: {
         qualifiedName: "Orders",
         path: "SalesDB/Orders",
         parr: "SalesDB",
      },
      type: DatabaseTreeNodeType.TABLE,
      ...overrides,
   };
}

async function renderComp(
   inputs: Partial<ChooseTableDialog> = {},
   responses: {
      tableNodes?: any[];
      physicalNodes?: any[];
   } = {},
) {
   server.use(
      http.get("*/api/data/physicalmodel/tree/nodes", () =>
         HttpResponse.json(responses.tableNodes ?? []),
      ),
      http.get("*/api/data/physicalmodel/tree/allNodes", () =>
         HttpResponse.json(responses.physicalNodes ?? []),
      ),
      http.post(/.*\/api\/data\/physicalModel\/tablePath\/.*/, () =>
         HttpResponse.text("\"SalesDB/Orders\""),
      ),
      http.post(/.*\/api\/data\/vpm\/physicalModel\/tablePath\/.*/, () =>
         HttpResponse.text("\"SalesDB/Orders\""),
      ),
      http.get("*/api/data/vpm/physicalModels/nodes", () =>
         HttpResponse.json(responses.physicalNodes ?? []),
      ),
   );

   const { fixture } = await render(ChooseTableDialog, {
      providers: [provideHttpClient()],
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: TreeComponent, with: TreeComponentStub }],
      componentInputs: {
         databaseName: "SalesDB",
         conditionType: ConditionTypes.TABLE,
         tableName: "",
         ...inputs,
      },
   });

   return {
      comp: fixture.componentInstance as ChooseTableDialog,
      fixture,
      tree: fixture.componentInstance.tree as unknown as TreeComponentStub,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - ngOnInit root loading", () => {
   it("should load table nodes when conditionType is TABLE", async () => {
      const tableNodes = [makeNode({ data: { name: "Orders" } })];
      const { comp } = await renderComp({}, { tableNodes });

      await waitFor(() => expect(comp.rootNode.children).toHaveLength(1));
      expect(comp.loadingTree).toBe(false);
   });

   it("should load physical model nodes and select the matching table when conditionType is PHYSICAL_MODEL", async () => {
      const physicalNodes = [makeNode({ data: "Orders" })];
      const { comp, tree } = await renderComp(
         { conditionType: ConditionTypes.PHYSICAL_MODEL, tableName: "Orders" },
         { physicalNodes },
      );

      await waitFor(() => expect(comp.rootNode.children).toHaveLength(1));
      expect(tree.exclusiveSelectNode).toHaveBeenCalledWith(physicalNodes[0]);
   });
});

describe("Group 2 - getters", () => {
   it("should return the physical-model title when conditionType is PHYSICAL_MODEL", async () => {
      const { comp } = await renderComp({ conditionType: ConditionTypes.PHYSICAL_MODEL });

      expect(comp.title).toBe("_#(js:data.vpm.choosePhysicalModel)");
   });

   it("should return the table title when conditionType is TABLE", async () => {
      const { comp } = await renderComp({ conditionType: ConditionTypes.TABLE });

      expect(comp.title).toBe("_#(js:data.vpm.chooseTable)");
   });

   it("should report tableNameNull based on the current tableName", async () => {
      const { comp } = await renderComp({ tableName: "" });
      expect(comp.tableNameNull).toBe(true);

      comp.tableName = "Orders";
      expect(comp.tableNameNull).toBe(false);
   });

   it("should convert slashes to path array separators in databaseParr", async () => {
      const { comp } = await renderComp({ databaseName: "Sales/DB" });

      expect(comp.databaseParr).toBe("Sales^_^DB");
   });
});

describe("Group 3 - selection and actions", () => {
   it("should set tableName from a selected TABLE node", async () => {
      const { comp } = await renderComp({ conditionType: ConditionTypes.TABLE });
      const nodes = [makeNode({ data: { qualifiedName: "Orders" } })];

      comp.selectNode(nodes as any);

      expect(comp.tableName).toBe("Orders");
   });

   it("should set tableName from the selected physical-model node data", async () => {
      const { comp } = await renderComp({ conditionType: ConditionTypes.PHYSICAL_MODEL });
      const nodes = [makeNode({ data: "Orders" })];

      comp.selectNode(nodes as any);

      expect(comp.tableName).toBe("Orders");
   });

   it("should emit the current tableName on ok", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.tableName = "Orders";

      comp.ok();

      expect(emitSpy).toHaveBeenCalledWith("Orders");
   });

   it("should emit cancel on cancel", async () => {
      const { comp } = await renderComp();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});
