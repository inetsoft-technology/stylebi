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
 * DataSourcesTreeViewComponent — Pass 3: Display
 *
 * Covers pure/near-pure display methods:
 *   getAssetIcon    — type-to-CSS-class mapping
 *   getIconFunction — returns a thunk calling getAssetIcon
 *   getEntryLabel   — HTML string with icon class + entry name label (private)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";

import { DataSourcesTreeViewComponent } from "./data-sources-tree-view.component";
import { PortalDataType } from "./portal-data-type";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import {
   buildRenderConfig,
   makeSubjects,
} from "./data-sources-tree-view.test-helpers";

async function renderComponent() {
   const subjects = makeSubjects();
   const { providers, importOverrides, componentProviders } = buildRenderConfig(
      subjects,
      "/portal/tab/data/folder",
      {},
   );

   const result = await render(DataSourcesTreeViewComponent, {
      providers,
      importOverrides,
      componentProviders,
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance;
   await waitFor(() => expect(comp.loading).toBe(false));

   return { comp, fixture: result.fixture };
}

afterEach(() => vi.restoreAllMocks());

// ===========================================================================
// Group 1 — getAssetIcon [Display]
// ===========================================================================

describe("DataSourcesTreeViewComponent — getAssetIcon [Group 1, Display]", () => {
   it("should return 'database-icon' for DATABASE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATABASE)).toBe("database-icon");
   });

   it("should return 'db-model-icon' for DATA_MODEL type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATA_MODEL)).toBe("db-model-icon");
   });

   it("should return 'cube-icon' for XMLA_SOURCE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.XMLA_SOURCE)).toBe("cube-icon");
   });

   it("should return 'tabular-data-icon' for DATA_SOURCE type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATA_SOURCE)).toBe("tabular-data-icon");
   });

   it("should return 'data-source-folder-icon' for DATA_SOURCE_ROOT_FOLDER type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.DATA_SOURCE_ROOT_FOLDER)).toBe("data-source-folder-icon");
   });

   it.each([
      PortalDataType.FOLDER,
      PortalDataType.DATA_SOURCE_FOLDER,
      PortalDataType.DATA_MODEL_FOLDER,
   ])("should return 'folder-icon' for %s type", async (nodeType) => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(nodeType)).toBe("folder-icon");
   });

   it("should return 'logical-model-icon' for LOGIC_MODEL type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.LOGIC_MODEL)).toBe("logical-model-icon");
   });

   it("should return 'logical-model-icon' for EXTENDED_LOGIC_MODEL type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.EXTENDED_LOGIC_MODEL)).toBe("logical-model-icon");
   });

   it("should return 'vpm-icon' for VPM type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon(PortalDataType.VPM)).toBe("vpm-icon");
   });

   it("should return 'materialized-worksheet-icon' when materialized=true and no matching type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon("UNKNOWN_TYPE", true)).toBe("materialized-worksheet-icon");
   });

   it("should return 'worksheet-icon' as fallback for unknown type", async () => {
      const { comp } = await renderComponent();
      expect(comp.getAssetIcon("UNKNOWN_TYPE")).toBe("worksheet-icon");
   });
});

// ===========================================================================
// Group 2 — getIconFunction [Display]
// ===========================================================================

describe("DataSourcesTreeViewComponent — getIconFunction [Group 2, Display]", () => {
   it("should return a function", async () => {
      const { comp } = await renderComponent();
      expect(typeof comp.getIconFunction()).toBe("function");
   });

   it("returned function should map DATABASE node to 'database-icon'", async () => {
      const { comp } = await renderComponent();
      const fn = comp.getIconFunction();
      const node = { type: PortalDataType.DATABASE } as TreeNodeModel;

      expect(fn(node)).toBe("database-icon");
   });

   it("returned function should pass materialized flag to getAssetIcon", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "getAssetIcon");
      const fn = comp.getIconFunction();
      const node = { type: "SOME_TYPE", materialized: true } as TreeNodeModel;

      fn(node);

      expect(spy).toHaveBeenCalledWith("SOME_TYPE", true);
   });
});

// ===========================================================================
// Group 3 — getEntryLabel [Display]
// ===========================================================================

describe("DataSourcesTreeViewComponent — getEntryLabel [Group 3, Display]", () => {
   it("should return an HTML string containing the icon CSS class for the asset type", async () => {
      const { comp } = await renderComponent();
      const asset = {
         path: "parentFolder/MyDS",
         type: PortalDataType.DATA_SOURCE as any,
         scope: 0,
         user: "admin",
         alias: null,
         identifier: "",
         properties: {},
         description: "",
         organization: "org",
      } as AssetEntry;

      const label = (comp as any).getEntryLabel(asset);

      expect(label).toContain("tabular-data-icon");
   });

   it("should return an HTML string containing the entry name as text", async () => {
      const { comp } = await renderComponent();
      const asset = {
         path: "parentFolder/MyDS",
         type: PortalDataType.DATA_SOURCE as any,
         scope: 0,
         user: "admin",
         alias: null,
         identifier: "",
         properties: {},
         description: "",
         organization: "org",
      } as AssetEntry;

      const label = (comp as any).getEntryLabel(asset);

      expect(label).toContain("MyDS");
   });

   it("should return an HTML string with correct icon class for DATABASE asset", async () => {
      const { comp } = await renderComponent();
      const asset = {
         path: "myDb",
         type: PortalDataType.DATABASE as any,
         scope: 0,
         user: "admin",
         alias: null,
         identifier: "",
         properties: {},
         description: "",
         organization: "org",
      } as AssetEntry;

      const label = (comp as any).getEntryLabel(asset);

      expect(label).toContain("database-icon");
   });
});
