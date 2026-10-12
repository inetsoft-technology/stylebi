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
import { Tool } from "../../../../../../../shared/util/tool";

const TREE = "../api/em/content/repository/tree";
const DELETE = "../api/em/content/repository/tree/delete";
const MOVE = "../api/em/content/repository/tree/move";
const VS = "../api/em/content/repository/viewsheet";
const FOLDER = "../api/em/content/repository/folder/";
const DATA_SOURCE = "../api/data/databases";

const NOT_FOUND = { status: 404, statusText: "Not Found" };
const SERVER_ERROR = { status: 500, statusText: "Server Error" };

function node(path: string, type: RepositoryEntryType, children: any[] = null, owner: any = null) {
   return { label: path.substring(path.lastIndexOf("/") + 1), path, type, owner, children,
      visible: true };
}

const vs = (path: string) => node(path, RepositoryEntryType.VIEWSHEET);

/**
 * Bug #78414, reloads of every editor type, moves, parent folder deletes and the carry-over of a
 * superseded reload's selection, checked against the real page, data source and service.
 */
describe("ContentRepositoryPageComponent reloads by node type", () => {
   let http: HttpTestingController;
   let snack: { open: ReturnType<typeof vi.fn> };
   let contentChanged: () => void;
   let comp: ContentRepositoryPageComponent;
   let fixture: any;
   let service: ContentRepositoryService;
   let unhandled: any[];
   let consoleError: any;

   beforeEach(() => {
      unhandled = [];
      config.onUnhandledError = (e) => unhandled.push(e);
      consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
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
      fixture = TestBed.createComponent(ContentRepositoryPageComponent);
      comp = fixture.componentInstance;
      fixture.detectChanges();
      http.match(r => r.url.includes("/mv/")).forEach(r => r.flush({ enabled: false, allow: false }));
   });

   afterEach(() => {
      config.onUnhandledError = null;
      consoleError.mockRestore();
      http.verify({ ignoreCancelled: true });
   });

   const treeRequests = () => http.match(r => r.url === TREE).filter(r => !r.cancelled);

   function oneTreeRequest(): TestRequest {
      const requests = treeRequests();
      expect(requests.length).toBe(1);
      return requests[0];
   }

   const flushTree = (request: TestRequest, nodes: any[]) => request.flush({ nodes });
   const selectedPaths = () => service.selectedNodes.map(n => n.data.path);
   const treePaths = () => comp.dataSource.data.map(n => n.data.path);
   const editorRequests = () => http.match(r => r.url !== TREE && !r.url.includes("/mv/"));

   function select(path: string): RepositoryFlatNode {
      const flatNode = comp.dataSource.data.find(n => n.data.path === path);
      comp.selectNode([flatNode]);
      return flatNode;
   }

   function openViewsheet(path: string, extra: any[] = []) {
      flushTree(oneTreeRequest(), [vs(path), ...extra]);
      select(path);
      http.expectOne(r => r.url === VS).flush({ name: path });
   }

   const tick = () => new Promise(resolve => setTimeout(resolve));

   // every getEditor branch that makes an HTTP request, re-selected by a reload whose item is gone
   const TYPES: [string, any][] = [
      ["worksheet", node("W/ws1", RepositoryEntryType.WORKSHEET)],
      ["dashboard", node("db1", RepositoryEntryType.DASHBOARD)],
      ["repository folder", node("F1", RepositoryEntryType.REPOSITORY_FOLDER, [])],
      ["worksheet folder", node("Worksheets/WF", RepositoryEntryType.WORKSHEET_FOLDER, [])],
      ["trashcan folder", node("Trashcan", RepositoryEntryType.TRASHCAN_FOLDER, [])],
      ["dashboard folder", node("Dashboards", RepositoryEntryType.DASHBOARD_FOLDER, [])],
      ["recycle bin folder", node(Tool.RECYCLE_BIN, RepositoryEntryType.RECYCLEBIN_FOLDER, [])],
      ["recycle bin entry", node(Tool.RECYCLE_BIN + "/x", RepositoryEntryType.VIEWSHEET)],
      ["data source", node("ds1", RepositoryEntryType.DATA_SOURCE)],
      ["data source folder", node("dsf", RepositoryEntryType.DATA_SOURCE_FOLDER, [])],
      ["data source root folder", node("/", RepositoryEntryType.DATA_SOURCE_FOLDER, [])],
      ["schedule task folder", node("tf", RepositoryEntryType.SCHEDULE_TASK_FOLDER, [])],
      ["query (permission)", node("ds1/q1", RepositoryEntryType.QUERY)],
      ["script (library)", node("s1", RepositoryEntryType.SCRIPT)],
      ["table style (library permission)", node("ts1", RepositoryEntryType.TABLE_STYLE)],
      ["viewsheet", vs("F/vs1")],
   ];

   for(const [name, n] of TYPES) {
      it(`${name}: a reload whose editor 404s is quiet, handled, clears the editor, no reload loop`, async () => {
         flushTree(oneTreeRequest(), [n]);
         select(n.path);
         editorRequests().forEach(r => r.flush({}));

         contentChanged();
         flushTree(oneTreeRequest(), [n]);
         const reqs = editorRequests();
         expect(reqs.length).toBe(1);
         if(n.type === RepositoryEntryType.DATA_SOURCE) {
            // what the server really sends for a missing data source
            reqs[0].flush({ message: "Datasource does not exist!" }, SERVER_ERROR);
         }
         else {
            reqs[0].flush({ type: "MissingResourceException", message: "missing" }, NOT_FOUND);
         }
         await tick();

         expect(unhandled).toEqual([]);
         expect(snack.open).not.toHaveBeenCalled();
         expect(consoleError).not.toHaveBeenCalled();
         // no reload loop
         expect(treeRequests().length).toBe(0);
         expect(editorRequests().length).toBe(0);
      });

      it(`${name}: a non-missing reload editor error is never uncaught`, async () => {
         flushTree(oneTreeRequest(), [n]);
         select(n.path);
         editorRequests().forEach(r => r.flush({}));

         contentChanged();
         flushTree(oneTreeRequest(), [n]);
         editorRequests()[0].flush({ message: "boom" }, SERVER_ERROR);
         await tick();

         expect(unhandled).toEqual([]);
         expect(treeRequests().length).toBe(0);
      });
   }

   it("move: a stale reload in flight is cancelled by the move response, no stale re-select", () => {
      openViewsheet("F/vs1", [node("G", RepositoryEntryType.REPOSITORY_FOLDER, [])]);
      contentChanged();
      const stale = oneTreeRequest();

      const target = comp.dataSource.data.find(n => n.data.path === "G");
      service.moveSelectedNodes(target);
      http.expectOne(MOVE).flush(vs("G/vs1"));
      expect(stale.cancelled).toBe(true);
      expect(comp.loading).toBe(false);

      flushTree(oneTreeRequest(), [node("G", RepositoryEntryType.REPOSITORY_FOLDER, [vs("G/vs1")])]);
      expect(http.match(r => r.url === VS && r.params.get("path") === "F~_2f_~vs1").length).toBe(0);
      expect(treePaths()).not.toContain("F/vs1");
      expect(snack.open).not.toHaveBeenCalled();
      http.match(r => r.url === VS).forEach(r => r.flush({ name: "vs1" }));
      http.match(r => r.url.startsWith(TREE)).forEach(r => r.flush({ nodes: [] }));
   });

   it("delete of a selected parent folder with a stale reload in flight: no re-select, no snackbar", () => {
      flushTree(oneTreeRequest(), [node("F1", RepositoryEntryType.REPOSITORY_FOLDER, [vs("F1/vs1")])]);
      select("F1");
      http.expectOne(r => r.url === FOLDER).flush({ folderName: "F1" });
      contentChanged();
      const stale = oneTreeRequest();

      comp.deleteNodes();
      http.expectOne(DELETE).flush({ status: null, connected: true });
      expect(stale.cancelled).toBe(true);
      flushTree(oneTreeRequest(), []);

      expect(editorRequests().length).toBe(0);
      expect(selectedPaths()).toEqual([]);
      expect(comp.editorModel.value).toBeNull();
      expect(snack.open).not.toHaveBeenCalled();
   });

   it("user click on a folder deleted elsewhere still shows the server message", () => {
      flushTree(oneTreeRequest(), [node("F1", RepositoryEntryType.REPOSITORY_FOLDER, [])]);
      select("F1");
      http.expectOne(r => r.url === FOLDER).flush({ type: "MessageException",
         message: "The selected item no longer exists. Please refresh the repository" }, SERVER_ERROR);
      expect(snack.open).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toContain("no longer exists");
      expect(comp.editorModel.value).toBeNull();
   });

   it("user click on a data source deleted elsewhere still shows the missing data source message", () => {
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      select("ds1");
      http.expectOne(r => r.url === DATA_SOURCE).flush({ message: "Datasource does not exist!" }, SERVER_ERROR);
      expect(snack.open).toHaveBeenCalledTimes(1);
      expect(snack.open.mock.calls[0][0]).toContain("findDataSourceError");
      expect(comp.editorModel.value).toBeNull();
   });

   it("viewsheet J ordering: stale editor failure before the delete response", () => {
      openViewsheet("F/vs1");
      contentChanged();
      flushTree(oneTreeRequest(), [vs("F/vs1")]);
      comp.deleteNodes();
      http.expectOne(r => r.url === VS).flush({ type: "MissingResourceException", message: "m" }, NOT_FOUND);
      http.expectOne(DELETE).flush({ status: null, connected: true });
      flushTree(oneTreeRequest(), []);
      expect(snack.open).not.toHaveBeenCalled();
      expect(comp.editorModel.value).toBeNull();
   });

   it("destroying the page aborts the in-flight reload and leaves no half state", async () => {
      openViewsheet("F/vs1");
      contentChanged();
      const reload = oneTreeRequest();
      fixture.destroy();
      expect(reload.cancelled).toBe(true);
      await tick();
      expect(unhandled).toEqual([]);
      expect(comp.loading).toBe(false);
      expect(service.selectedNodes).toEqual([]);
   });

   it("an aborted reload does not touch the page or tree loading flags", () => {
      let treeLoading: boolean = null;
      flushTree(oneTreeRequest(), [vs("F/vs1")]);
      comp.dataSource.loading.subscribe(l => treeLoading = l);
      contentChanged();
      const r1 = oneTreeRequest();
      contentChanged();
      expect(r1.cancelled).toBe(true);
      expect(treeLoading).toBe(false);
      expect(comp.loading).toBe(false);
      flushTree(oneTreeRequest(), [vs("F/vs1")]);
      expect(treePaths()).toEqual(["F/vs1"]);
   });

   it("import superseding a rename reload keeps the rename selection", () => {
      openViewsheet("F/vs1");
      const model: any = comp.editorModel.value;
      model.path = "F/vs2";
      comp.editorChanged(model);
      const renameReload = oneTreeRequest();
      comp.importAsset();
      expect(renameReload.cancelled).toBe(true);
      flushTree(oneTreeRequest(), [vs("F/vs2")]);
      http.expectOne(r => r.url === VS && r.params.get("path") === "F~_2f_~vs2").flush({ name: "vs2" });
      expect(selectedPaths()).toEqual(["F/vs2"]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe("vs2");
   });

   it("new data source then MV force then em-content-changed: selection and force both carried", () => {
      flushTree(oneTreeRequest(), []);
      comp.finalizeNewDataSource(<any> { path: "ds1", type: RepositoryEntryType.DATA_SOURCE, owner: null });
      comp.refreshTree(null, true);
      contentChanged();
      const updates: any[] = [];
      comp.editorModel.subscribe(m => updates.push(m));
      flushTree(oneTreeRequest(), [node("ds1", RepositoryEntryType.DATA_SOURCE)]);
      http.expectOne(r => r.url === DATA_SOURCE).flush({ dataSource: { name: "ds1" } });
      expect(updates).toContain(null);
      expect(selectedPaths()).toEqual(["ds1"]);
      expect(comp.editorModel.value["settings"].dataSource.name).toBe("ds1");
   });

   it("the carried event is consumed: a later plain reload does not re-apply it", () => {
      openViewsheet("F/vs1", [vs("F/vs2")]);
      const model: any = comp.editorModel.value;
      model.path = "F/vs2";
      comp.editorChanged(model);
      flushTree(oneTreeRequest(), [vs("F/vs1"), vs("F/vs2")]);
      http.expectOne(r => r.url === VS).flush({ name: "vs2" });
      // the user then clicks vs1; a later em-content-changed must keep vs1
      select("F/vs1");
      http.expectOne(r => r.url === VS).flush({ name: "vs1" });
      contentChanged();
      flushTree(oneTreeRequest(), [vs("F/vs1"), vs("F/vs2")]);
      http.expectOne(r => r.url === VS).flush({ name: "vs1" });
      expect(selectedPaths()).toEqual(["F/vs1"]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toBe("vs1");
   });

   it("owner of a carried user-scope event is used for the replacement reload", () => {
      const owner = { name: "u1", orgID: "host-org" };
      flushTree(oneTreeRequest(), []);
      comp.editorChanged(<any> { path: "My Dashboards/vs1", type: RepositoryEntryType.VIEWSHEET, owner });
      oneTreeRequest();
      contentChanged();
      const replacement = oneTreeRequest();
      expect(JSON.stringify(replacement.request.body)).toContain("u1");
      flushTree(replacement, []);
   });

   it("user click while a reload is in flight ends with the clicked item's editor", () => {
      flushTree(oneTreeRequest(), [vs("F/vs1"), vs("F/vs2")]);
      contentChanged();
      const reload = oneTreeRequest();
      select("F/vs2");
      const click = http.expectOne(r => r.url === VS);
      flushTree(reload, [vs("F/vs1"), vs("F/vs2")]);
      const quiet = http.expectOne(r => r.url === VS);
      click.flush({ name: "vs2-click" });
      quiet.flush({ name: "vs2-reload" });
      expect(selectedPaths()).toEqual(["F/vs2"]);
      expect(comp.editorModel.value["viewsheetSettings"].name).toMatch(/vs2/);
   });
});

describe("Repository settings pages with settings", () => {
   it("viewsheet page sets oname from real settings", () => {
      const page = new RepositoryViewsheetSettingsPageComponent(null, null);
      page.model = <any> { path: "F/vs1", type: RepositoryEntryType.VIEWSHEET, viewsheetSettings: { name: "vs1" } };
      page.ngOnChanges(<any> { model: {} });
      expect(page.sheetModel.name).toBe("vs1");
      expect(page.sheetModel.oname).toBe("vs1");
   });

   it("script page fills the form from real settings", () => {
      const page = new RepositoryScriptSettingsPageComponent(null);
      page.model = <any> { path: "s1", type: RepositoryEntryType.SCRIPT,
         scriptSettings: { name: "s1", description: "d" } };
      page.ngOnInit();
      expect(page.form.get("name").value).toBe("s1");
      expect(page.form.get("description").value).toBe("d");
   });

   it("data source page applies real settings", () => {
      const page = new RepositoryDataSourceSettingsPageComponent(null, null, null);
      page.model = <any> { path: "ds1", type: RepositoryEntryType.DATA_SOURCE,
         settings: { dataSource: { name: "ds1", info: {} }, permissions: { x: 1 } } };
      page.ngOnChanges(<any> { model: {} });
      expect((<any> page).database.name).toBe("ds1");
      expect((<any> page).permissions).toEqual({ x: 1 });
   });
});

