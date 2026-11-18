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
 * DataFolderBrowserComponent - Bug #75145
 *
 *   A: the dataChanged subscription is released when the component is destroyed.
 *   B: deleteSelected is a no-op for a non-deletable selection (already fixed; kept as coverage).
 *   C: the folder is refreshed after a bulk delete whether removeAll succeeds or fails.
 *   D: moveSelected / the mobile Move item honour moveDisable.
 *   E: moveAssets0 passes each scope only its own assets, and the worksheet move request
 *      carries that scope as assetScope.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { ComponentTool } from "../../../common/util/component-tool";
import { server } from "@test-mocks/server";
import {
   clearMocks,
   makeFolder,
   makeInfo,
   REPO_CLIENT_MOCK,
   renderComponent,
} from "./data-folder-browser.test-helpers";

beforeEach(() => clearMocks());

afterEach(() => {
   vi.restoreAllMocks();
});

const EMPTY_BROWSER = { root: true, worksheetAccess: true, currentFolder: [], folders: [], files: [] };

describe("DataFolderBrowserComponent - Bug #75145", () => {
   it("A: should stop refreshing on dataChanged once destroyed", async () => {
      const { comp, fixture } = await renderComponent();
      const refreshSpy = vi.spyOn(comp as any, "refreshBrowserContent").mockImplementation(() => {});

      REPO_CLIENT_MOCK.dataChanged.next();
      expect(refreshSpy).toHaveBeenCalledTimes(1);

      fixture.destroy();
      REPO_CLIENT_MOCK.dataChanged.next();

      expect(refreshSpy).toHaveBeenCalledTimes(1);
   });

   it("B: deleteSelected should not query removeableStatuses for a non-deletable selection", async () => {
      const statusSpy = vi.fn();
      server.use(
         http.post("*/api/data/removeableStatuses", () => {
            statusSpy();
            return HttpResponse.json({ folderDependencies: [], datasetDependencies: [] });
         })
      );
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: false })];

      comp.deleteSelected();
      await new Promise(resolve => setTimeout(resolve, 50));

      expect(statusSpy).not.toHaveBeenCalled();
   });

   it.each([
      ["succeeds", 200],
      ["fails", 500]
   ])("C: should refresh the folder once when removeAll %s", async (_label, status) => {
      server.use(
         http.post("*/api/data/removeableStatuses", () =>
            HttpResponse.json({ folderDependencies: [], datasetDependencies: [] })),
         http.post("*/api/data/removeAll", () => new HttpResponse(null, { status }))
      );
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const refreshSpy = vi.spyOn(comp as any, "refreshBrowserContent").mockImplementation(() => {});
      comp.selectedItems = [makeInfo()];
      const notifications = (comp as any).dataNotifications.notifications;

      comp.deleteSelected();

      await waitFor(() => expect(refreshSpy).toHaveBeenCalled());
      expect(refreshSpy).toHaveBeenCalledTimes(1);
      expect(status === 200 ? notifications.success : notifications.danger).toHaveBeenCalled();
   });

   it("D: moveSelected should not move a selection that moveDisable rejects", async () => {
      const { comp } = await renderComponent();
      comp.selectedItems = [makeInfo({ deletable: false, editable: false })];
      const moveAssetsSpy = vi.spyOn(comp, "moveAssets").mockImplementation(() => {});

      expect(comp.moveDisable).toBe(true);
      comp.moveSelected();

      expect(moveAssetsSpy).not.toHaveBeenCalled();
   });

   it("D: the mobile Move menu item should follow moveDisable", async () => {
      const { comp, fixture } = await renderComponent();
      comp.selectionOn = true;
      comp.selectedItems = [makeInfo({ deletable: false, editable: false })];
      fixture.detectChanges();

      const findMove = () => Array.from(
         fixture.nativeElement.querySelectorAll("button.dropdown-item"))
         .find((b: any) => b.getAttribute("title") === "_#(data.datasets.moveSelected)") as HTMLButtonElement;
      const moveAssetsSpy = vi.spyOn(comp, "moveAssets").mockImplementation(() => {});

      expect(findMove()).toBeTruthy();
      expect(findMove().disabled).toBe(true);

      comp.selectedItems = [makeInfo()];
      fixture.detectChanges();
      expect(findMove().disabled).toBe(false);
      findMove().click();
      expect(moveAssetsSpy).toHaveBeenCalledTimes(1);
   });

   it("E: moveAssets0 should pass each scope only the assets of that scope", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComponent();
      const moveAssetsSpy = vi.spyOn(comp, "moveAssets").mockImplementation(() => {});
      const target = makeFolder({ path: "target", scope: AssetEntryHelper.GLOBAL_SCOPE });
      const globalSheet = makeInfo({ name: "G", path: "a/G", scope: AssetEntryHelper.GLOBAL_SCOPE });
      const userSheet = makeInfo({ name: "U", path: "b/U", scope: AssetEntryHelper.USER_SCOPE });

      (comp as any).moveAssets0([globalSheet, userSheet], target);

      await waitFor(() => expect(moveAssetsSpy).toHaveBeenCalledTimes(2));
      expect(moveAssetsSpy).toHaveBeenCalledWith([globalSheet], target, AssetEntryHelper.GLOBAL_SCOPE);
      expect(moveAssetsSpy).toHaveBeenCalledWith([userSheet], target, AssetEntryHelper.USER_SCOPE);
   });

   it("E: a worksheet move should send the asset scope, not the pane scope, as assetScope", async () => {
      let assetScope: string = null;
      server.use(
         http.post("*/api/data/datasets/moveDatasets", ({ request }) => {
            assetScope = new URL(request.url).searchParams.get("assetScope");
            return new HttpResponse(null, { status: 200 });
         }),
         http.get("*/api/portal/data/browser", () => HttpResponse.json(EMPTY_BROWSER))
      );
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "old/Sheet", scope: AssetEntryHelper.USER_SCOPE });
      const target = makeFolder({ path: "newFolder", scope: AssetEntryHelper.GLOBAL_SCOPE });
      comp.currentFolderPath = [makeFolder({ path: "old" })];
      comp.currentFolderPathString = "old";
      comp.currentFolderPathScope = String(AssetEntryHelper.GLOBAL_SCOPE);

      comp.moveAssets([ws], target, AssetEntryHelper.USER_SCOPE);

      await waitFor(() => expect(assetScope).toBe(String(AssetEntryHelper.USER_SCOPE)));
   });

   it("E: moveAssets without an explicit scope should still send the pane scope", async () => {
      let assetScope: string = null;
      server.use(
         http.post("*/api/data/datasets/moveDatasets", ({ request }) => {
            assetScope = new URL(request.url).searchParams.get("assetScope");
            return new HttpResponse(null, { status: 200 });
         }),
         http.get("*/api/portal/data/browser", () => HttpResponse.json(EMPTY_BROWSER))
      );
      const { comp } = await renderComponent();
      const ws = makeInfo({ path: "old/Sheet", scope: AssetEntryHelper.GLOBAL_SCOPE });
      const target = makeFolder({ path: "newFolder", scope: AssetEntryHelper.GLOBAL_SCOPE });
      comp.currentFolderPath = [makeFolder({ path: "old" })];
      comp.currentFolderPathString = "old";
      comp.currentFolderPathScope = String(AssetEntryHelper.USER_SCOPE);

      comp.moveAssets([ws], target);

      await waitFor(() => expect(assetScope).toBe(String(AssetEntryHelper.USER_SCOPE)));
   });
});
