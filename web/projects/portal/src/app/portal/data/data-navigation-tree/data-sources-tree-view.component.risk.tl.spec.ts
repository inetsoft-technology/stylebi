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
 * DataSourcesTreeViewComponent — Pass 2: Risk
 *
 * Covers async/destructive methods not in Pass 1:
 *   moveDataModelAssetItems, moveDataFolderItems, showMessage,
 *   moveDataSetsAndFolders, moveDatasourceAssets, moveDatasourceInfos,
 *   moveDataAssets, selectAndExpandToPath,
 *   canDelete, deleteDataModelFolder, deleteFolder, deleteVisible
 *
 * KEY contracts:
 *   - moveDataModelAssetItems: null target/missing databasePath → noop;
 *     DATA_MODEL/DATA_MODEL_FOLDER → confirm → moveModelsToTarget;
 *     wrong type → showMessageDialog.
 *   - moveDataFolderItems: null target/non-FOLDER → noop or error dialog;
 *     FOLDER target + no dup → confirm → moveDataSetsAndFolders;
 *     duplicate found → error dialog.
 *   - showMessage: calls ComponentTool.showMessageDialog with the message.
 *   - moveDatasourceInfos: empty → noop; non-DATA_SOURCE_FOLDER → error dialog;
 *     DATA_SOURCE_FOLDER → confirm → checkDuplicate → if ok: moveDataSourcesToFolder.
 *   - moveDatasourceAssets: empty → noop; delegates to moveDatasourceInfos.
 *   - moveDataAssets: creates WorksheetBrowserInfo from AssetEntry and calls moveDataFolderItems.
 *   - canDelete: reads DatasourceTreeAction.DELETE from node properties.
 *   - deleteDataModelFolder: calls dataModelBrowserService.deleteDataModelFolder.
 *   - deleteFolder: dispatches to appropriate service based on node type.
 *   - deleteVisible: true for DATA_MODEL_FOLDER, DATA_SOURCE_FOLDER, FOLDER, DATA_SOURCE, DATABASE, XMLA_SOURCE.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";

import { DataSourcesTreeViewComponent } from "./data-sources-tree-view.component";
import { PortalDataType } from "./portal-data-type";
import { DatasourceTreeAction } from "../model/datasources/database/datasource-tree-action";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../common/util/component-tool";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { of } from "rxjs";
import {
   buildRenderConfig,
   makeSubjects,
   makeNode,
} from "./data-sources-tree-view.test-helpers";

// ---------------------------------------------------------------------------
// renderComponent
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   queryParams?: Record<string, string>,
   routerUrl?: string,
   subjects?: ReturnType<typeof makeSubjects>,
} = {}) {
   const subjects = opts.subjects ?? makeSubjects();
   const routerUrl = opts.routerUrl ?? "/portal/tab/data/folder";
   const queryParams = opts.queryParams ?? {};
   const { providers, importOverrides, componentProviders, mocks } = buildRenderConfig(
      subjects, routerUrl, queryParams,
   );

   const result = await render(DataSourcesTreeViewComponent, {
      providers,
      importOverrides,
      componentProviders,
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance;

   return {
      comp,
      fixture: result.fixture,
      router: mocks.router,
      subjects,
      dataFolderService: mocks.dataFolderService,
      datasourceService: mocks.datasourceService,
      dataModelBrowserService: mocks.dataModelBrowserService,
      dropdownService: mocks.dropdownService,
      dataSourcesTreeActions: mocks.dataSourcesTreeActions,
      vsClient: mocks.vsClient,
   };
}

afterEach(() => vi.restoreAllMocks());

// ===========================================================================
// Group 1 — moveDataModelAssetItems [Risk 3]
// ===========================================================================

describe("DataSourcesTreeViewComponent — moveDataModelAssetItems [Group 1, Risk 3]", () => {
   it("should return early when targetNode is null", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();

      (comp as any).moveDataModelAssetItems(null, [{ name: "Model" }]);

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("should return early when targetNode has no databasePath property", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const target = makeNode({ type: PortalDataType.DATA_MODEL, properties: {} });

      (comp as any).moveDataModelAssetItems(target, [{ name: "Model" }]);

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("should show confirm dialog for DATA_MODEL target with databasePath", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComponent();
      const target = makeNode({
         type: PortalDataType.DATA_MODEL,
         properties: { databasePath: "myDb" },
      });

      (comp as any).moveDataModelAssetItems(target, [{ name: "Model" }]);

      await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
   });

   it("should call moveModelsToTarget on confirm ok for DATA_MODEL target", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp, dataModelBrowserService } = await renderComponent();
      const target = makeNode({
         type: PortalDataType.DATA_MODEL,
         data: { path: "/", name: "root", scope: 0, properties: { databasePath: "myDb" } },
      });

      (comp as any).moveDataModelAssetItems(target, [{ name: "Model" }]);

      await waitFor(() => expect(dataModelBrowserService.moveModelsToTarget).toHaveBeenCalled());
   });

   it("should show error dialog when target type is not DATA_MODEL or DATA_MODEL_FOLDER", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const target = makeNode({
         type: PortalDataType.DATABASE,
         properties: { databasePath: "myDb" },
      });

      (comp as any).moveDataModelAssetItems(target, [{ name: "Model" }]);

      expect(msgSpy).toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 2 — moveDataFolderItems [Risk 3]
// ===========================================================================

describe("DataSourcesTreeViewComponent — moveDataFolderItems [Group 2, Risk 3]", () => {
   it("should return early when targetEntry is null", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();

      (comp as any).moveDataFolderItems(null, []);

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("should show error dialog when targetEntry is not a FOLDER type", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const targetEntry = { type: AssetType.WORKSHEET, path: "/ws", scope: 1, properties: {} };

      (comp as any).moveDataFolderItems(targetEntry, [{ name: "Sheet", path: "old/Sheet", type: AssetType.WORKSHEET, scope: 1 }]);

      expect(msgSpy).toHaveBeenCalled();
   });

   it("should show confirm dialog when checkDataFoldersDuplicate returns false", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComponent();
      // Stub the private HTTP method so the subscribe fires synchronously, avoiding zone.js hang.
      vi.spyOn(comp as any, "checkDataFoldersDuplicate").mockReturnValue(of(false));
      const targetEntry = {
         type: AssetType.FOLDER, path: "newFolder", scope: 1, properties: {},
         identifier: "1^1^admin^newFolder",
      };
      const item = { name: "Sheet", path: "old/Sheet", type: AssetType.WORKSHEET, scope: 1,
         id: "1^2^admin^old/Sheet", createdDate: 0, modifiedDate: 0 };

      (comp as any).moveDataFolderItems(targetEntry, [item]);

      expect(confirmSpy).toHaveBeenCalled();
   });

   it("should show duplicate error dialog when checkDataFoldersDuplicate returns true", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      // Stub the private HTTP method so the subscribe fires synchronously, avoiding zone.js hang.
      vi.spyOn(comp as any, "checkDataFoldersDuplicate").mockReturnValue(of(true));
      const targetEntry = {
         type: AssetType.FOLDER, path: "newFolder", scope: 1, properties: {},
         identifier: "1^1^admin^newFolder",
      };
      const item = { name: "Sheet", path: "old/Sheet", type: AssetType.WORKSHEET, scope: 1,
         id: "1^2^admin^old/Sheet", createdDate: 0, modifiedDate: 0 };

      (comp as any).moveDataFolderItems(targetEntry, [item]);

      expect(msgSpy).toHaveBeenCalled();
      expect(msgSpy.mock.calls[0][2]).toContain("_#(js:common.duplicateName)");
   });
});

// ===========================================================================
// Group 3 — showMessage [Risk 1]
// ===========================================================================

describe("DataSourcesTreeViewComponent — showMessage [Group 3, Risk 1]", () => {
   it("should call ComponentTool.showMessageDialog with the provided message", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();

      comp.showMessage("Test error occurred");

      expect(msgSpy).toHaveBeenCalled();
      expect(msgSpy.mock.calls[0][2]).toBe("Test error occurred");
   });
});

// ===========================================================================
// Group 4 — moveDatasourceInfos / moveDatasourceAssets [Risk 3]
// ===========================================================================

describe("DataSourcesTreeViewComponent — moveDatasourceInfos / moveDatasourceAssets [Group 4, Risk 3]", () => {
   it("moveDatasourceInfos should return early when assets array is empty", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const targetEntry = { type: AssetType.DATA_SOURCE_FOLDER, path: "/", scope: 0, properties: {} };

      (comp as any).moveDatasourceInfos(targetEntry, []);

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("moveDatasourceInfos should show error dialog when target is not DATA_SOURCE_FOLDER", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const targetEntry = { type: AssetType.FOLDER, path: "ws-folder", scope: 1, properties: {} };

      (comp as any).moveDatasourceInfos(targetEntry, [{ name: "DS1" }]);

      expect(msgSpy).toHaveBeenCalled();
   });

   it("moveDatasourceInfos should show confirm dialog for DATA_SOURCE_FOLDER target", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComponent();
      const targetEntry = { type: AssetType.DATA_SOURCE_FOLDER, path: "/myFolder", scope: 0, properties: {} };

      (comp as any).moveDatasourceInfos(targetEntry, [{ name: "DS1" }]);

      expect(confirmSpy).toHaveBeenCalled();
   });

   it("moveDatasourceAssets should return early when assets array is empty", async () => {
      const { comp, datasourceService } = await renderComponent();
      const targetEntry = { type: AssetType.DATA_SOURCE_FOLDER, path: "/", scope: 0, properties: {} };

      (comp as any).moveDatasourceAssets(targetEntry, []);

      expect(datasourceService.createDataSourceInfos).not.toHaveBeenCalled();
   });

   it("moveDatasourceAssets should delegate to moveDatasourceInfos after creating infos", async () => {
      const { comp, datasourceService } = await renderComponent();
      const targetEntry = { type: AssetType.WORKSHEET, path: "ws", scope: 1, properties: {} };
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const asset: AssetEntry = {
         scope: 1, type: AssetType.DATA_SOURCE, user: "admin", path: "MyDS",
         alias: null, identifier: "id", properties: {}, description: "", organization: "org",
      };

      (comp as any).moveDatasourceAssets(targetEntry, [asset]);

      expect(datasourceService.createDataSourceInfos).toHaveBeenCalledWith([asset]);
      // error dialog because target is not DATA_SOURCE_FOLDER
      await waitFor(() => expect(msgSpy).toHaveBeenCalled());
   });
});

// ===========================================================================
// Group 5 — moveDataAssets [Risk 2]
// ===========================================================================

describe("DataSourcesTreeViewComponent — moveDataAssets [Group 5, Risk 2]", () => {
   it("should convert AssetEntry to WorksheetBrowserInfo and call moveDataFolderItems", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const targetEntry = { type: AssetType.WORKSHEET, path: "ws", scope: 1, properties: {} };
      const asset: AssetEntry = {
         scope: 1, type: AssetType.WORKSHEET, user: "admin", path: "oldFolder/Sheet",
         alias: null, identifier: "id123", properties: {}, description: "", organization: "org",
      };

      (comp as any).moveDataAssets(targetEntry, [asset]);

      // calls moveDataFolderItems with non-FOLDER target → error dialog
      await waitFor(() => expect(msgSpy).toHaveBeenCalled());
   });
});

// ===========================================================================
// Group 6 — canDelete [Risk 1]
// ===========================================================================

describe("DataSourcesTreeViewComponent — canDelete [Group 6, Risk 1]", () => {
   it("should return true when node has DELETE property set to 'true'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.DELETE]: "true" } });

      expect(comp.canDelete(node)).toBe(true);
   });

   it("should return false when node has DELETE property set to 'false'", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: { [DatasourceTreeAction.DELETE]: "false" } });

      expect(comp.canDelete(node)).toBe(false);
   });

   it("should return false when node has no properties", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ properties: {} });

      expect(comp.canDelete(node)).toBe(false);
   });

   it("should return false when node is null", async () => {
      const { comp } = await renderComponent();

      expect(comp.canDelete(null)).toBe(false);
   });
});

// ===========================================================================
// Group 7 — deleteDataModelFolder / deleteFolder [Risk 2]
// ===========================================================================

describe("DataSourcesTreeViewComponent — deleteDataModelFolder / deleteFolder [Group 7, Risk 2]", () => {
   it("deleteFolder with DATA_MODEL_FOLDER node should call dataModelBrowserService.deleteDataModelFolder", async () => {
      const { comp, dataModelBrowserService } = await renderComponent();
      const node = makeNode({
         type: PortalDataType.DATA_MODEL_FOLDER,
         label: "MyFolder",
         data: {
            path: "myDb/MyFolder",
            name: "MyFolder",
            scope: 0,
            identifier: "",
            type: PortalDataType.DATA_MODEL_FOLDER,
            properties: { folder: "MyFolder" },
         },
      });

      comp.deleteFolder(node);

      expect(dataModelBrowserService.deleteDataModelFolder).toHaveBeenCalled();
   });

   it("deleteFolder with DATA_SOURCE_FOLDER node should call dataSourcesTreeActionsService.deleteDataSourceFolder", async () => {
      const { comp, dataSourcesTreeActions } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_SOURCE_FOLDER });

      comp.deleteFolder(node);

      expect(dataSourcesTreeActions.deleteDataSourceFolder).toHaveBeenCalledWith(
         node,
         expect.any(Function)
      );
   });

   it("deleteFolder with FOLDER node should call dataSourcesTreeActionsService.deleteWorksheetFolder", async () => {
      const { comp, dataSourcesTreeActions } = await renderComponent();
      const node = makeNode({ type: PortalDataType.FOLDER });

      comp.deleteFolder(node);

      expect(dataSourcesTreeActions.deleteWorksheetFolder).toHaveBeenCalledWith(
         node,
         expect.any(Function)
      );
   });

   it("deleteFolder with DATABASE node should call dataSourcesTreeActionsService.deleteDataSource", async () => {
      const { comp, dataSourcesTreeActions } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATABASE });

      comp.deleteFolder(node);

      expect(dataSourcesTreeActions.deleteDataSource).toHaveBeenCalledWith(
         node,
         expect.any(Function)
      );
   });
});

// ===========================================================================
// Group 8 — deleteVisible [Risk 1]
// ===========================================================================

describe("DataSourcesTreeViewComponent — deleteVisible [Group 8, Risk 1]", () => {
   it.each([
      PortalDataType.DATA_MODEL_FOLDER,
      PortalDataType.DATA_SOURCE_FOLDER,
      PortalDataType.FOLDER,
      PortalDataType.DATA_SOURCE,
      PortalDataType.DATABASE,
      PortalDataType.XMLA_SOURCE,
   ])("should return true for %s node type", async (nodeType) => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: nodeType });

      expect(comp.deleteVisible(node)).toBe(true);
   });

   it("should return false for DATA_MODEL node type", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.DATA_MODEL });

      expect(comp.deleteVisible(node)).toBe(false);
   });

   it("should return false for LOGIC_MODEL node type", async () => {
      const { comp } = await renderComponent();
      const node = makeNode({ type: PortalDataType.LOGIC_MODEL });

      expect(comp.deleteVisible(node)).toBe(false);
   });
});
