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
import { BreakpointObserver } from "@angular/cdk/layout";
import { provideHttpClient } from "@angular/common/http";
import {
   HttpTestingController,
   provideHttpClientTesting,
   TestRequest
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { config, of } from "rxjs";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import { RepositoryDataSourceSettingsPageComponent } from "../repository-data-source-settings-page/repository-data-source-settings-page.component";
import { RepositoryScriptSettingsPageComponent } from "../repository-script-settings-page/repository-script-settings-page.component";
import { RepositoryFlatNode } from "../repository-tree-node";
import { RepositoryViewsheetSettingsPageComponent } from "../repository-viewsheet-settings-page/repository-viewsheet-settings-page.component";
import { ContentRepositoryPageComponent } from "./content-repository-page.component";
import { ContentRepositoryService } from "./content-repository.service";

const TREE = "../api/em/content/repository/tree";
const DELETE = "../api/em/content/repository/tree/delete";
const VS = "../api/em/content/repository/viewsheet";
const WS = "../api/em/content/repository/worksheet";
const FOLDER = "../api/em/content/repository/folder/";
const SCRIPT = "../api/em/settings/content/repository/script";
const DATA_SOURCE = "../api/data/databases";
const DATA_SOURCE_FOLDER = "../api/em/settings/content/repository/dataSourceFolder";
const MISSING_DS_FOLDER = { type: "MessageException",
   message: "Cannot find dsf. It may have been moved or deleted" };
const SERVER_ERROR = { status: 500, statusText: "Server Error" };

const MISSING_VS = { type: "MissingResourceException", message: "missing" };
const NOT_FOUND = { status: 404, statusText: "Not Found" };

function node(path: string, type: RepositoryEntryType, children: any[] = null) {
   return { label: path.substring(path.lastIndexOf("/") + 1), path, type, owner: null, children,
      visible: true };
}

function vs(path: string) {
   return node(path, RepositoryEntryType.VIEWSHEET);
}

/**
 * Bug #78414, a tree reload sent before a rename or delete (e.g. by the debounced
 * em-content-changed message of an earlier change) can return a tree computed before the change.
 * It must not bring the old item back, re-select it, or report it as missing, and a reload that
 * replaces another must keep the selection the replaced reload was carrying.
 */
describe("ContentRepositoryPageComponent tree reloads", () => {
   let http: HttpTestingController;
   let snack: { open: ReturnType<typeof vi.fn> };
   let contentChanged: () => void;
   let comp: ContentRepositoryPageComponent;
   let service: ContentRepositoryService;
   let unhandled: any[];

   beforeEach(() => {
      unhandled = [];
      config.onUnhandledError = (e) => unhandled.push(e);
      snack = { open: vi.fn() };
      const stomp = {
         connect: () => of({
            subscribe: (topic: string, callback: () => void) => {
               if(topic === "/user/em-content-changed") {
                  contentChanged = callback;
               }
            },
            disconnect: () => {}
         })
      };

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(true) })) } },
            { provide: MatSnackBar, useValue: snack },
            { provide: StompClientService, useValue: stomp },
            { provide: PageHeaderService, useValue: {} },
            { provide: Router, useValue: {} },
            { provide: ActivatedRoute, useValue: {} },
            { provide: BreakpointObserver, useValue: { isMatched: () => false } },
            { provide: TopScrollService, useValue: { scroll: () => {} } }
         ]
      });
      TestBed.overrideComponent(ContentRepositoryPageComponent, { set: { template: "", imports: [] } });

      http = TestBed.inject(HttpTestingController);
      service = TestBed.inject(ContentRepositoryService);
      const fixture = TestBed.createComponent(ContentRepositoryPageComponent);
      comp = fixture.componentInstance;
      fixture.detectChanges();
      http.match(r => r.url.includes("/mv/")).forEach(r => r.flush({ enabled: false, allow: false }));
   });

   afterEach(() => {
      config.onUnhandledError = null;
      // the stale requests are cancelled, not answered
      http.verify({ ignoreCancelled: true });
   });

   function treeRequests(): TestRequest[] {
      return http.match(r => r.url === TREE).filter(r => !r.cancelled);
   }

   function oneTreeRequest(): TestRequest {
      const requests = treeRequests();
      expect(requests.length).toBe(1);
      return requests[0];
   }

   function flushTree(request: TestRequest, nodes: any[]) {
      request.flush({ nodes });
   }

   function select(path: string): RepositoryFlatNode {
      const flatNode = comp.dataSource.data.find(n => n.data.path === path);
      comp.selectNode([flatNode]);
      return flatNode;
   }

   function selectedPaths(): string[] {
      return service.selectedNodes.map(n => n.data.path);
   }

   function treePaths(): string[] {
      return comp.dataSource.data.map(n => n.data.path);
   }

   function openViewsheet(path: string) {
      flushTree(oneTreeRequest(), [vs(path)]);
      select(path);
      http.expectOne(r => r.url === VS).flush({ name: path });
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe(path);
   }

   function deleteSelected() {
      comp.deleteNodes();
      http.expectOne(DELETE).flush({ status: null, connected: true });
   }

   function rename(path: string) {
      // what RepositoryEditorPageComponent.handleEditorChange does after the settings are saved
      const model: any = comp.editorModel.value;
      model.path = path;
      comp.editorChanged(model);
   }

   // A: stale reload in flight when the delete returns, B/H: answered before or after the fresh one
   it("should cancel a reload sent before a delete, so the deleted viewsheet is not re-selected", () => {
      openViewsheet("F/vs1");
      contentChanged();
      const staleReload = oneTreeRequest();

      deleteSelected();
      const freshReload = oneTreeRequest();
      expect(staleReload.cancelled).toBe(true);

      flushTree(freshReload, []);

      expect(http.match(r => r.url === VS).length).toBe(0);
      expect(treePaths()).toEqual([]);
      expect(selectedPaths()).toEqual([]);
      expect(comp.editorModel.value).toBeNull();
      expect(snack.open).not.toHaveBeenCalled();
   });

   // J: the stale tree returns before the delete response
   it("should not report a deleted viewsheet re-selected by a tree returned before the delete response", () => {
      openViewsheet("F/vs1");
      contentChanged();
      const staleReload = oneTreeRequest();

      flushTree(staleReload, [vs("F/vs1")]);
      const staleEditor = http.expectOne(r => r.url === VS);

      deleteSelected();
      const freshReload = oneTreeRequest();
      // the fresh reload replaces the editor load of the stale one
      expect(staleEditor.cancelled).toBe(true);

      flushTree(freshReload, []);
      expect(treePaths()).toEqual([]);
      expect(comp.editorModel.value).toBeNull();
      expect(snack.open).not.toHaveBeenCalled();
   });

   it("should not report a re-selected viewsheet that turns out to be missing", () => {
      openViewsheet("F/vs1");
      contentChanged();
      flushTree(oneTreeRequest(), [vs("F/vs1")]);

      http.expectOne(r => r.url === VS).flush(MISSING_VS, NOT_FOUND);

      expect(snack.open).not.toHaveBeenCalled();
      expect(comp.editorModel.value).toBeNull();
   });

   it("should still report a viewsheet that is missing when the user clicks it", () => {
      flushTree(oneTreeRequest(), [vs("F/vs1")]);
      select("F/vs1");

      http.expectOne(r => r.url === VS).flush(MISSING_VS, NOT_FOUND);

      expect(snack.open).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toContain("em.repository.missingResource");
      // the failed load has no editor instead of one with null settings
      expect(comp.editorModel.value).toBeNull();
   });

   // C and I: rename, the fresh reload answered first or the stale one never answered
   it("should keep the renamed viewsheet selected when a stale reload was in flight", () => {
      openViewsheet("F/vs1");
      contentChanged();
      const staleReload = oneTreeRequest();

      rename("F/vs2");
      const renameReload = oneTreeRequest();
      expect(staleReload.cancelled).toBe(true);

      flushTree(renameReload, [vs("F/vs2")]);
      http.expectOne(r => r.url === VS && r.params.get("path") === "F~_2f_~vs2")
         .flush({ name: "vs2" });

      expect(treePaths()).toEqual(["F/vs2"]);
      expect(selectedPaths()).toEqual(["F/vs2"]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe("vs2");
      expect(snack.open).not.toHaveBeenCalled();
   });

   // D: rename a repository folder
   it("should not report the old folder name after a folder rename", () => {
      flushTree(oneTreeRequest(), [node("Folder1", RepositoryEntryType.REPOSITORY_FOLDER, [])]);
      select("Folder1");
      http.expectOne(r => r.url === FOLDER).flush({ folderName: "Folder1" });
      const model: any = comp.editorModel.value;

      // the folder is renamed on the server while a reload sent earlier returns the old tree
      contentChanged();
      flushTree(oneTreeRequest(), [node("Folder1", RepositoryEntryType.REPOSITORY_FOLDER, [])]);
      http.expectOne(r => r.url === FOLDER).flush({ type: "MessageException",
         message: "The selected item no longer exists. Please refresh the repository" },
         { status: 500, statusText: "Server Error" });
      expect(snack.open).not.toHaveBeenCalled();

      // the rename response arrives
      model.path = "Folder2";
      comp.editorChanged(model);
      flushTree(oneTreeRequest(), [node("Folder2", RepositoryEntryType.REPOSITORY_FOLDER, [])]);
      http.expectOne(r => r.url === FOLDER).flush({ folderName: "Folder2" });

      expect(snack.open).not.toHaveBeenCalled();
      expect(selectedPaths()).toEqual(["Folder2"]);
      expect(comp.editorModel.value["folderModel"].folderName).toBe("Folder2");
   });

   // E: the worksheet editor request has no error handling of its own
   it("should not leave the worksheet editor error of a re-selected deleted worksheet uncaught", async () => {
      const ws = node("W/ws1", RepositoryEntryType.WORKSHEET);
      flushTree(oneTreeRequest(), [ws]);
      select("W/ws1");
      http.expectOne(r => r.url === WS).flush({ name: "ws1" });

      contentChanged();
      flushTree(oneTreeRequest(), [ws]);
      http.expectOne(r => r.url === WS).flush(MISSING_VS, NOT_FOUND);

      deleteSelected();
      flushTree(oneTreeRequest(), []);
      // rxjs reports an unhandled error in a timeout
      await new Promise(resolve => setTimeout(resolve));

      expect(unhandled).toEqual([]);
      expect(snack.open).not.toHaveBeenCalled();
      expect(comp.editorModel.value).toBeNull();
   });

   it("should drop an editor loaded for a node that is no longer selected", () => {
      flushTree(oneTreeRequest(), [vs("F/vs1")]);
      const vs1 = select("F/vs1");
      const editor = http.expectOne(r => r.url === VS);

      service.clearSelectedNodes();
      editor.flush({ name: "vs1" });

      expect(comp.editorModel.value).toBeNull();
      expect(comp.editingNode).toBeNull();
      comp.updateEditorModel(vs1, <any> { path: "F/vs1" });
      expect(comp.editorModel.value).toBeNull();
   });

   it("should carry the rename selection over to an em-content-changed reload that replaces it", () => {
      openViewsheet("F/vs1");
      rename("F/vs2");
      const renameReload = oneTreeRequest();

      // the rename's own em-content-changed arrives while its reload is in flight
      contentChanged();
      const changedReload = oneTreeRequest();
      expect(renameReload.cancelled).toBe(true);

      flushTree(changedReload, [vs("F/vs2")]);
      http.expectOne(r => r.url === VS && r.params.get("path") === "F~_2f_~vs2")
         .flush({ name: "vs2" });

      expect(selectedPaths()).toEqual(["F/vs2"]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe("vs2");
   });

   it("should carry a new data source selection over to a reload that replaces it", () => {
      flushTree(oneTreeRequest(), []);
      comp.finalizeNewDataSource(<any> { path: "ds1", type: RepositoryEntryType.DATA_SOURCE, owner: null });
      const newReload = oneTreeRequest();

      contentChanged();
      const changedReload = oneTreeRequest();
      expect(newReload.cancelled).toBe(true);

      flushTree(changedReload, [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      http.expectOne(r => r.url === DATA_SOURCE).flush({ dataSource: { name: "ds1" } });

      expect(selectedPaths()).toEqual(["ds1"]);
      expect(comp.editorModel.value["settings"].dataSource.name).toBe("ds1");
   });

   it("should carry the MV force refresh over to a reload that replaces it", () => {
      openViewsheet("F/vs1");
      comp.mvChanged(comp.editorModel.value);
      const mvReload = oneTreeRequest();

      contentChanged();
      const changedReload = oneTreeRequest();
      expect(mvReload.cancelled).toBe(true);

      const editorUpdates: any[] = [];
      comp.editorModel.subscribe(model => editorUpdates.push(model));
      flushTree(changedReload, [vs("F/vs1")]);
      http.expectOne(r => r.url === VS).flush({ name: "F/vs1 reloaded" });

      // forced: the editor is cleared and loaded again
      expect(editorUpdates.slice(1, 2)).toEqual([null]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe("F/vs1 reloaded");
   });

   it("should still report a data source error that is not a missing data source", () => {
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      select("ds1");
      http.expectOne(r => r.url === DATA_SOURCE).flush({ dataSource: { name: "ds1" } });

      contentChanged();
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      http.expectOne(r => r.url === DATA_SOURCE).flush({ message: "Connection refused" },
         { status: 500, statusText: "Server Error" });

      expect(snack.open).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toContain("em.data.databases.getDatabaseError");
      expect(comp.editorModel.value).toBeNull();
   });

   it("should not report a missing data source re-selected by a reload", () => {
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      select("ds1");
      http.expectOne(r => r.url === DATA_SOURCE).flush({ dataSource: { name: "ds1" } });

      contentChanged();
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      http.expectOne(r => r.url === DATA_SOURCE).flush({ message: "Datasource does not exist!" },
         { status: 500, statusText: "Server Error" });

      expect(snack.open).not.toHaveBeenCalled();
      expect(comp.editorModel.value).toBeNull();
   });

   it("should not report a missing data source folder re-selected by a reload", () => {
      flushTree(oneTreeRequest(), [node("dsf", RepositoryEntryType.DATA_SOURCE_FOLDER, [])]);
      select("dsf");
      http.expectOne(r => r.url === DATA_SOURCE_FOLDER).flush({ name: "dsf" });

      // the stale tree still lists the folder deleted on the server
      contentChanged();
      flushTree(oneTreeRequest(), [node("dsf", RepositoryEntryType.DATA_SOURCE_FOLDER, [])]);
      http.expectOne(r => r.url === DATA_SOURCE_FOLDER).flush(MISSING_DS_FOLDER, SERVER_ERROR);

      expect(snack.open).not.toHaveBeenCalled();
      expect(comp.editorModel.value).toBeNull();
   });

   it("should still report a missing data source folder when the user clicks it", () => {
      flushTree(oneTreeRequest(), [node("dsf", RepositoryEntryType.DATA_SOURCE_FOLDER, [])]);
      select("dsf");
      http.expectOne(r => r.url === DATA_SOURCE_FOLDER).flush(MISSING_DS_FOLDER, SERVER_ERROR);

      expect(snack.open).toHaveBeenCalledWith(MISSING_DS_FOLDER.message, null, expect.anything());
      expect(comp.editorModel.value).toBeNull();
   });

   it("should have no script editor when the script fails to load", () => {
      flushTree(oneTreeRequest(), [node("s1", RepositoryEntryType.SCRIPT)]);
      select("s1");
      http.expectOne(r => r.url === SCRIPT).flush({ type: "MessageException", message: "denied" },
         { status: 500, statusText: "Server Error" });

      expect(snack.open).toHaveBeenCalledWith("denied", null, expect.anything());
      expect(comp.editorModel.value).toBeNull();
   });
});

// F, G and the viewsheet page: a model without settings must not throw
describe("Repository settings pages without settings", () => {
   it("should not throw in the viewsheet settings page", () => {
      const page = new RepositoryViewsheetSettingsPageComponent(null, null);
      page.model = <any> { path: "F/vs1", type: RepositoryEntryType.VIEWSHEET, viewsheetSettings: null };

      expect(() => page.ngOnChanges(<any> { model: {} })).not.toThrow();
      expect(page.sheetModel).toBeNull();
   });

   it("should not throw in the script settings page", () => {
      const page = new RepositoryScriptSettingsPageComponent(null);
      page.model = <any> { path: "s1", type: RepositoryEntryType.SCRIPT, scriptSettings: null };

      expect(() => page.ngOnInit()).not.toThrow();
   });

   it("should not throw in the data source settings page", () => {
      const page = new RepositoryDataSourceSettingsPageComponent(null, null, null);
      page.model = <any> { path: "ds1", type: RepositoryEntryType.DATA_SOURCE, settings: null };

      expect(() => page.ngOnChanges(<any> { model: {} })).not.toThrow();
   });
});
