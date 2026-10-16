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
 * Save Viewsheet / Save Worksheet / Select Emails dialogs: tab round trips driven through the DOM.
 *
 * Bug #78413: leaving the tab that hosts the asset tree destroyed it, and the re-created tree's
 * on-load auto-selection overwrote the user's folder (or query). These cases complement the
 * *.tab-switch.tl.spec.ts files: the picks are real mousedown/click events on the tree rows, folders
 * are nested and expanded through the toggle, Save As starts from a nested or private folder (where
 * the old code snapped back to that folder, not the root), several round trips are made and the
 * folder is changed again after one. They also check that an invalid edit the user makes on the
 * Options tab still disables OK and is still shown when the user goes back to it.
 */
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { ComponentFixture } from "@angular/core/testing";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { server } from "@test-mocks/server";
import { SaveViewsheetDialog } from "./save-viewsheet-dialog.component";
import { ViewsheetOptionsPane } from "./viewsheet-options-pane.component";
import { SaveWorksheetDialog } from "../ws/save-worksheet-dialog.component";
import { WorksheetOptionPane } from "../ws/worksheet-option-pane.component";
import { EmailAddrDialog } from "../../../widget/email-dialog/email-addr-dialog.component";
import { EmbeddedEmailPane } from "../../../widget/email-dialog/embedded-email-pane.component";
import { QueryEmailPane } from "../../../widget/email-dialog/query-email-pane.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { AssetTreeComponent } from "../../../widget/asset-tree/asset-tree.component";
import { AssetClientService } from "../../../common/asset-client/asset-client.service";
import { ModelService } from "../../../widget/services/model.service";

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderStub {
   @Input() title: any;
   @Input() cshid: any;
}

@Component({ selector: "embedded-email-pane", template: "<div class='embedded'></div>", standalone: true })
class EmbeddedStub {
   @Input() model: any;
   @Input() showRoot: any;
   @Input() addresses: any;
   @Input() embeddedOnly: any;
   @Input() emailForm: any;
   @Output() addressesChange = new EventEmitter<string>();
}

const G = "1^1^__NULL__^/";
const P = "4^1^admin~;~host-org^/";
const SALES = "1^1^__NULL__^Sales";
const Y2024 = "1^1^__NULL__^Sales/2024";
const PSUB = "4^1^admin~;~host-org^Mine";

function folder(identifier: string, label: string, path: string, scope: number, children: any[] = []) {
   return { label, leaf: false, expanded: false, children,
      data: { identifier, type: "FOLDER", path, scope, properties: {} } };
}

function tree(rootLabel: string) {
   const priv = folder(P, "Private", "/", 4, [folder(PSUB, "Mine", "Mine", 4)]);
   const sales = folder(SALES, "Sales", "Sales", 1, [folder(Y2024, "2024", "Sales/2024", 1)]);
   return { treeNodeModel: folder("root", "root", "/", 0, [folder(G, rootLabel, "/", 1, [priv, sales])]),
      parameters: null };
}

let treeLoads = 0;
let bodies: any[] = [];
const assetClientMock = () => ({ connect: vi.fn(), disconnect: vi.fn(), assetChanged: new Subject() });

function handlers(rootLabel: string, wsAlias = "") {
   treeLoads = 0;
   bodies = [];
   server.use(
      http.get("*/api/portal/get-current-user", () => HttpResponse.json({ name: { name: "admin", orgID: "host-org" } })),
      http.post("*/api/composer/asset_tree", () => { treeLoads++; return HttpResponse.json(tree(rootLabel)); }),
      http.post("*/api/composer/vs/save-viewsheet-dialog-model/*", async ({ request }) => {
         bodies.push(await request.json()); return HttpResponse.json({});
      }),
      http.get("*/api/composer/ws/dialog/save-worksheet-dialog-model/*", () => HttpResponse.json({
         assetRepositoryPaneModel: { name: "WS1", parentEntry: null, selectedEntry: null },
         worksheetOptionPaneModel: { name: "WS1", alias: wsAlias, dataSource: false, description: "" } })),
      http.post("*/api/composer/ws/dialog/save-worksheet-dialog-model/*", async ({ request }) => {
         bodies.push(await request.json()); return HttpResponse.json({});
      }),
   );
}

async function settle(f: ComponentFixture<any>) {
   for(let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 20));
      f.detectChanges();
   }
}

async function tab(f: ComponentFixture<any>, label: string) {
   const links: HTMLElement[] = Array.from(f.nativeElement.querySelectorAll("a.nav-link"));
   links.find(a => a.textContent.includes(label)).click();
   await settle(f);
}

function row(f: ComponentFixture<any>, label: string): HTMLElement {
   const labels: HTMLElement[] = Array.from(f.nativeElement.querySelectorAll("label.tree-node-label"));
   const l = labels.find(x => x.textContent.trim() == label);
   return l ? l.closest("[role=treeitem]") as HTMLElement : null;
}

async function expand(f: ComponentFixture<any>, label: string) {
   (row(f, label).querySelector(".tree-node-toggle") as HTMLElement).click();
   await settle(f);
}

async function pick(f: ComponentFixture<any>, label: string) {
   const r = row(f, label);
   const lbl = r.querySelector("label.tree-node-label") as HTMLElement;
   lbl.dispatchEvent(new MouseEvent("mousedown", { bubbles: true, button: 0 }));
   lbl.dispatchEvent(new MouseEvent("click", { bubbles: true, button: 0 }));
   await settle(f);
}

function selectedLabels(f: ComponentFixture<any>): string[] {
   return Array.from(f.nativeElement.querySelectorAll("[role=treeitem][aria-selected=true] label.tree-node-label"))
      .map((x: HTMLElement) => x.textContent.trim());
}

const ok = (f: ComponentFixture<any>) => f.nativeElement.querySelector(".modal-footer .btn-primary") as HTMLButtonElement;
const theTree = (f: ComponentFixture<any>) => f.debugElement.query(By.directive(AssetTreeComponent))?.componentInstance;

function optionsModel(alias: string): any {
   return { viewsheetParametersDialogModel: { enabledParameters: [], disabledParameters: [] },
      selectDataSourceDialogModel: { title: "x", dataSource: null }, useMetaData: false, promptForParams: false,
      selectionAssociation: true, createMv: false, onDemandMvEnabled: false, maxRows: 0, snapGrid: 10, alias,
      desc: "", serverSideUpdate: false, touchInterval: 60, listOnPortalTree: true, worksheet: false };
}

async function renderVs(defaultFolder: any, name = "Untitled-1", alias = "Sales") {
   handlers("Dashboard");
   const { fixture } = await render(SaveViewsheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: ModalHeaderComponent, with: ModalHeaderStub }],
      childComponentOverrides: [{ component: AssetTreeComponent,
         providers: [{ provide: AssetClientService, useFactory: assetClientMock }] }],
      providers: [provideHttpClient(), ModelService, { provide: NgbModal, useValue: {} }],
      componentProperties: { runtimeId: "vs-1", defaultFolder,
         model: { name, parentId: "", updateDepend: true, viewsheetOptionsPaneModel: optionsModel(alias) } as any },
   });
   await settle(fixture);
   return fixture;
}

async function renderWs(defaultFolder: any, alias = "") {
   handlers("Global Worksheet", alias);
   const { fixture } = await render(SaveWorksheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [{ replace: ModalHeaderComponent, with: ModalHeaderStub }],
      childComponentOverrides: [{ component: AssetTreeComponent,
         providers: [{ provide: AssetClientService, useFactory: assetClientMock }] }],
      providers: [provideHttpClient(), ModelService, { provide: NgbModal, useValue: {} }],
      componentProperties: { worksheet: { runtimeId: "ws-1" } as any, showReportRepository: false, defaultFolder },
   });
   await settle(fixture);
   return fixture;
}

afterEach(() => vi.restoreAllMocks());
vi.setConfig({ testTimeout: 30000 });

describe("SaveViewsheetDialog - DOM-driven tab round trips (Bug #78413)", () => {
   const variants: [string, any, string][] = [
      ["new, no defaultFolder", null, "Untitled-1"],
      ["new, defaultFolder=global root", { path: "/", scope: 1, identifier: G }, "Untitled-1"],
      ["Save As from nested Sales/2024", { path: "Sales/2024", scope: 1, identifier: Y2024 }, "Existing"],
      ["Save As from private Mine", { path: "Mine", scope: 4, identifier: PSUB }, "Existing"],
   ];

   for(const [title, df, name] of variants) {
      it(`real clicks, 3 round trips, change folder after a round trip - ${title}`, async () => {
         const f = await renderVs(df, name);
         const comp = f.componentInstance as SaveViewsheetDialog;
         const t = theTree(f);
         const nameInput = f.nativeElement.querySelector("input#name") as HTMLInputElement;

         if(!row(f, "Sales")) { await expand(f, "Dashboard"); }
         if(!row(f, "2024")) { await expand(f, "Sales"); }
         await pick(f, "2024");
         expect(comp.model.parentId).toBe(Y2024);

         for(let i = 0; i < 3; i++) {
            await tab(f, "Options");
            expect(f.debugElement.query(By.directive(ViewsheetOptionsPane))).not.toBeNull();
            await tab(f, "Repository");
         }

         expect(comp.model.parentId).toBe(Y2024);
         expect(selectedLabels(f)).toEqual(["2024"]);
         expect(row(f, "2024")).not.toBeNull(); // still expanded
         expect(theTree(f)).toBe(t);
         expect(f.nativeElement.querySelector("input#name")).toBe(nameInput);

         // change the folder after a round trip, then another round trip
         if(!row(f, "Mine")) { await expand(f, "Private"); }
         await pick(f, "Mine");
         await tab(f, "Options");
         await tab(f, "Repository");
         expect(selectedLabels(f)).toEqual(["Mine"]);

         comp.form.controls["name"].setValue("Saved");
         f.detectChanges();
         expect(ok(f).disabled).toBe(false);
         ok(f).click();
         await settle(f);
         expect(bodies.length).toBe(1);
         expect(bodies[0].parentId).toBe(PSUB);
         expect(bodies[0].name).toBe("Saved");
         expect(treeLoads).toBe(1);
      });
   }

   for(const alias of ["销售", "_sales", "(Q1) Sales", "", "1abc"]) {
      it(`OK enabled at open for alias "${alias}"`, async () => {
         const f = await renderVs(null, "Existing", alias);
         expect(f.debugElement.query(By.directive(ViewsheetOptionsPane))).toBeNull();
         expect(ok(f).disabled).toBe(false);
      });
   }

   it("a real invalid Options edit disables OK and stays visible after a round trip", async () => {
      const f = await renderVs(null, "Existing");
      await tab(f, "Options");
      const snap = () => f.nativeElement.querySelector("input[formcontrolname=snapGrid]") as HTMLInputElement;
      snap().value = "3";
      snap().dispatchEvent(new Event("input"));
      f.detectChanges();
      expect(ok(f).disabled).toBe(true);
      expect(snap().classList.contains("is-invalid")).toBe(true);

      await tab(f, "Repository");
      expect(ok(f).disabled).toBe(true);
      await tab(f, "Options");
      expect(snap().value).toBe("3");
      expect(snap().classList.contains("is-invalid")).toBe(true);
      expect(ok(f).disabled).toBe(true);

      snap().value = "15";
      snap().dispatchEvent(new Event("input"));
      f.detectChanges();
      expect(ok(f).disabled).toBe(false);
   });

   it("keyboard: arrow keys move focus between the nav links; the Repository pane survives", async () => {
      const f = await renderVs(null);
      const links: HTMLElement[] = Array.from(f.nativeElement.querySelectorAll("a.nav-link"));
      links[0].focus();
      links[0].dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
      f.detectChanges();
      expect(document.activeElement).toBe(links[1]);
      (document.activeElement as HTMLElement).click();
      await settle(f);
      expect(f.debugElement.query(By.directive(ViewsheetOptionsPane))).not.toBeNull();
      // the hidden repository pane is still in the DOM but not displayed (no .active/.show)
      const panes: HTMLElement[] = Array.from(f.nativeElement.querySelectorAll(".tab-pane"));
      expect(panes.length).toBe(2);
      expect(panes.filter(p => p.classList.contains("active")).length).toBe(1);
   });
});

describe("SaveWorksheetDialog - DOM-driven tab round trips (Bug #78413)", () => {
   const variants: [string, any][] = [
      ["no defaultFolder", null],
      ["defaultFolder nested Sales/2024", { path: "Sales/2024", scope: 1, identifier: Y2024 }],
   ];

   for(const [title, df] of variants) {
      it(`real clicks, 3 round trips, change folder after - ${title}`, async () => {
         const f = await renderWs(df);
         const comp = f.componentInstance as SaveWorksheetDialog;
         if(!row(f, "Sales")) { await expand(f, "Global Worksheet"); }
         if(!row(f, "2024")) { await expand(f, "Sales"); }
         await pick(f, "2024");
         expect(comp.model.assetRepositoryPaneModel.parentEntry.identifier).toBe(Y2024);

         for(let i = 0; i < 3; i++) {
            await tab(f, "Options");
            await tab(f, "Repository");
         }
         expect(comp.model.assetRepositoryPaneModel.parentEntry.identifier).toBe(Y2024);
         expect(selectedLabels(f)).toEqual(["2024"]);

         if(!row(f, "Mine")) { await expand(f, "Private"); }
         await pick(f, "Mine");
         await tab(f, "Options");
         await tab(f, "Repository");
         expect(ok(f).disabled).toBe(false);
         ok(f).click();
         await settle(f);
         expect(bodies.length).toBe(1);
         expect(bodies[0].assetRepositoryPaneModel.parentEntry.identifier).toBe(PSUB);
         expect(treeLoads).toBe(1);
      });
   }

   for(const alias of ["销售", "_sales", "(Q1) Sales", ""]) {
      it(`OK enabled at open for alias "${alias}"`, async () => {
         const f = await renderWs(null, alias);
         expect(f.debugElement.query(By.directive(WorksheetOptionPane))).toBeNull();
         expect(ok(f).disabled).toBe(false);
      });
   }

   it("a real invalid alias edit disables OK and stays visible after a round trip", async () => {
      const f = await renderWs(null, "good");
      await tab(f, "Options");
      const a = () => f.nativeElement.querySelector("input[formcontrolname=alias]") as HTMLInputElement;
      a().value = "_bad";
      a().dispatchEvent(new Event("input"));
      f.detectChanges();
      expect(ok(f).disabled).toBe(true);
      await tab(f, "Repository");
      expect(ok(f).disabled).toBe(true);
      await tab(f, "Options");
      expect(a().value).toBe("_bad");
      expect(a().classList.contains("is-invalid")).toBe(true);
   });
});

describe("EmailAddrDialog - DOM-driven tab round trips (Bug #78413)", () => {
   function q(source: string) {
      return { label: source, leaf: true, expanded: false, children: [], data: {
         identifier: "0^65605^__NULL__^ds/" + source, type: "QUERY", path: "ds/" + source, scope: 0,
         properties: { prefix: "ds", source, type: "query" } } };
   }
   let colPosts = 0;

   async function renderEmail(addresses: string) {
      treeLoads = 0;
      colPosts = 0;
      server.use(
         http.get("*/api/portal/get-current-user", () => HttpResponse.json({ name: { name: "admin", orgID: "host-org" } })),
         http.post("*/api/composer/asset_tree", () => {
            treeLoads++;
            const ds = { label: "ds", leaf: false, expanded: true, children: [q("q1"), q("q2")],
               data: { identifier: "0^65605^__NULL__^ds", type: "DATA_SOURCE", path: "ds", scope: 0, properties: {} } };
            return HttpResponse.json({ treeNodeModel: { label: "root", leaf: false, expanded: true, children: [ds],
               data: { identifier: "root", type: "FOLDER", path: "/", scope: 0, properties: {} } }, parameters: null });
         }),
         http.post("*/api/portal/schedule/emails/query/columns", () => {
            colPosts++;
            return HttpResponse.json({ columns: ["U1", "U2"], columnLabels: ["U1", "U2"] });
         }),
      );
      const { fixture } = await render(EmailAddrDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         importOverrides: [{ replace: EmbeddedEmailPane, with: EmbeddedStub },
            { replace: ModalHeaderComponent, with: ModalHeaderStub }],
         childComponentOverrides: [{ component: AssetTreeComponent,
            providers: [{ provide: AssetClientService, useFactory: assetClientMock }] }],
         providers: [provideHttpClient()],
         componentInputs: { embeddedOnly: false, model: {} as any, addresses },
      });
      await settle(fixture);
      return fixture;
   }

   function choose(f: ComponentFixture<any>, idx: number, v: string) {
      const s: HTMLSelectElement[] = Array.from(f.debugElement.query(By.directive(QueryEmailPane))
         .nativeElement.querySelectorAll("select"));
      s[idx].value = v;
      s[idx].dispatchEvent(new Event("change"));
      f.detectChanges();
   }

   it("open on Embedded: hidden Query pane built at open does not touch addresses; query pick survives 3 round trips", async () => {
      const f = await renderEmail("a@b.com");
      const comp = f.componentInstance as EmailAddrDialog;
      const committed = vi.fn();
      comp.onCommit.subscribe(committed);
      expect(comp.addresses).toBe("a@b.com");

      await tab(f, "Query");
      await pick(f, "q2");
      choose(f, 0, "U2");
      expect(comp.addresses).toBe("query: ds.q2, user: U2");
      for(let i = 0; i < 3; i++) {
         await tab(f, "Embedded");
         await tab(f, "Query");
      }
      const pane = f.debugElement.query(By.directive(QueryEmailPane)).componentInstance as QueryEmailPane;
      expect(pane.query).toBe("ds.q2");
      expect(selectedLabels(f)).toEqual(["q2"]);
      choose(f, 1, "U1");
      comp.ok();
      expect(committed).toHaveBeenCalledWith({ emails: "query: ds.q2, user: U2, email: U1", type: "query" });
      expect(treeLoads).toBe(1);
   });

   it("open on Query: switch to another query via real click, round trips, OK", async () => {
      const f = await renderEmail("query: ds.q1, user: U1, email: U1");
      const comp = f.componentInstance as EmailAddrDialog;
      const committed = vi.fn();
      comp.onCommit.subscribe(committed);
      expect(selectedLabels(f)).toEqual(["q1"]);
      await pick(f, "q2");
      choose(f, 0, "U1");
      choose(f, 1, "U2");
      for(let i = 0; i < 2; i++) {
         await tab(f, "Embedded");
         await tab(f, "Query");
      }
      expect(selectedLabels(f)).toEqual(["q2"]);
      comp.ok();
      expect(committed).toHaveBeenCalledWith({ emails: "query: ds.q2, user: U1, email: U2", type: "query" });
      expect(treeLoads).toBe(1);
   });

   it("open on Query, round trips, OK with no edits closes (unchanged behaviour)", async () => {
      const f = await renderEmail("query: ds.q1, user: U1");
      const comp = f.componentInstance as EmailAddrDialog;
      const committed = vi.fn();
      const cancelled = vi.fn();
      comp.onCommit.subscribe(committed);
      comp.onCancel.subscribe(cancelled);
      await tab(f, "Embedded");
      await tab(f, "Query");
      comp.ok();
      expect(committed).not.toHaveBeenCalled();
      expect(cancelled).toHaveBeenCalled();
   });
});
