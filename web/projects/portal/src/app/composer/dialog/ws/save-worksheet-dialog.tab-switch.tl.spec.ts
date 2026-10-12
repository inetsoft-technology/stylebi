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
 * SaveWorksheetDialog — Repository/Options tab switching with the real asset tree.
 *
 * Bug #78413: leaving the Repository tab destroyed AssetRepositoryPane and its asset tree. The
 * re-created tree auto-selected the default folder on load and AssetRepositoryPane.pathSelected wrote
 * it into model.assetRepositoryPaneModel.parentEntry, so OK saved to the default folder. The
 * Repository nav item now keeps its pane ([destroyOnHide]="false"); the Options pane stays lazy so
 * its alias validator cannot gate OK from a hidden tab.
 *
 * Uses the real AssetRepositoryPane + AssetTreeComponent with MSW and asserts the validation request body.
 */

import { Component, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { ComponentFixture } from "@angular/core/testing";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { server } from "@test-mocks/server";
import { SaveWorksheetDialog } from "./save-worksheet-dialog.component";
import { WorksheetOptionPane } from "./worksheet-option-pane.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { AssetTreeComponent } from "../../../widget/asset-tree/asset-tree.component";
import { AssetClientService } from "../../../common/asset-client/asset-client.service";
import { ModelService } from "../../../widget/services/model.service";

@Component({ selector: "worksheet-option-pane", template: "<div class='opts'></div>", standalone: true })
class WorksheetOptionPaneStub {
   @Input() model: any;
   @Input() form: any;
}

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderComponentStub {
   @Input() title: any;
   @Input() cshid: any;
}

const GLOBAL_ROOT_ID = "1^1^__NULL__^/";
const PRIVATE_ROOT_ID = "4^1^admin~;~host-org^/";
const SALES_ID = "1^1^__NULL__^Sales";

function folder(identifier: string, label: string, path: string, scope: number, children: any[] = []) {
   return {
      label, leaf: false, expanded: false, children,
      data: { identifier, type: "FOLDER", path, scope, properties: {} },
   };
}

function treeResponse() {
   const privateRoot = folder(PRIVATE_ROOT_ID, "Private Worksheet", "/", 4);
   const sales = folder(SALES_ID, "Sales", "Sales", 1);
   const global = folder(GLOBAL_ROOT_ID, "Global Worksheet", "/", 1, [privateRoot, sales]);
   return { treeNodeModel: folder("root", "root", "/", 0, [global]), parameters: null };
}

let treeLoads = 0;
let validateBodies: any[] = [];

function installHandlers(alias: string) {
   treeLoads = 0;
   validateBodies = [];
   server.use(
      http.get("*/api/portal/get-current-user", () =>
         HttpResponse.json({ name: { name: "admin", orgID: "host-org" } })),
      http.post("*/api/composer/asset_tree", () => {
         treeLoads++;
         return HttpResponse.json(treeResponse());
      }),
      http.get("*/api/composer/ws/dialog/save-worksheet-dialog-model/*", () =>
         HttpResponse.json({
            assetRepositoryPaneModel: { name: "WS1", parentEntry: null, selectedEntry: null },
            worksheetOptionPaneModel: { name: "WS1", alias, dataSource: false, description: "" },
         })),
      http.post("*/api/composer/ws/dialog/save-worksheet-dialog-model/*", async ({ request }) => {
         validateBodies.push(await request.json());
         return HttpResponse.json({});
      }),
   );
}

const assetClientMock = () => ({ connect: vi.fn(), disconnect: vi.fn(), assetChanged: new Subject() });

// poll instead of waitFor: MSW-driven state + waitFor can hang vitest
async function settle(fixture: ComponentFixture<any>) {
   for(let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 20));
      fixture.detectChanges();
   }
}

function clickTab(fixture: ComponentFixture<any>, label: string): void {
   const links: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll("a.nav-link"));
   links.find(a => a.textContent.includes(label)).click();
}

function assetTree(fixture: ComponentFixture<any>): AssetTreeComponent {
   return fixture.debugElement.query(By.directive(AssetTreeComponent))?.componentInstance;
}

function okButton(fixture: ComponentFixture<any>): HTMLButtonElement {
   return fixture.nativeElement.querySelector(".modal-footer .btn-primary");
}

async function renderDialog(defaultFolder: any, opts: { realOptions?: boolean, alias?: string } = {}) {
   installHandlers(opts.alias ?? "");
   const { fixture } = await render(SaveWorksheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderComponentStub },
         ...(opts.realOptions ? [] : [{ replace: WorksheetOptionPane, with: WorksheetOptionPaneStub }]),
      ],
      childComponentOverrides: [
         { component: AssetTreeComponent, providers: [{ provide: AssetClientService, useFactory: assetClientMock }] },
      ],
      providers: [provideHttpClient(), ModelService, { provide: NgbModal, useValue: {} }],
      componentProperties: { worksheet: { runtimeId: "ws-1" } as any, showReportRepository: false, defaultFolder },
   });
   await settle(fixture);
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("SaveWorksheetDialog — save location survives an Options round trip (Bug #78413)", () => {
   const cases: [string, any][] = [
      ["without defaultFolder", null],
      ["with defaultFolder", { path: "/", scope: 1, identifier: GLOBAL_ROOT_ID }],
   ];

   for(const [title, defaultFolder] of cases) {
      it(`keeps the folder picked before visiting Options — ${title}`, async () => {
         const fixture = await renderDialog(defaultFolder);
         const comp = fixture.componentInstance as SaveWorksheetDialog;
         const committed = vi.fn();
         comp.onCommit.subscribe(committed);
         const tree = assetTree(fixture);

         // user picks Sales (pathSelected needs the parent expanded to build the path)
         tree.root.children[0].expanded = true;
         tree.selectNodes([tree.root.children[0].children[1]]);
         fixture.detectChanges();
         expect(comp.model.assetRepositoryPaneModel.parentEntry.identifier).toBe(SALES_ID);

         clickTab(fixture, "Options");
         await settle(fixture);
         clickTab(fixture, "Repository");
         await settle(fixture);

         okButton(fixture).click();
         await settle(fixture);

         expect(validateBodies.length).toBe(1);
         expect(validateBodies[0].assetRepositoryPaneModel.parentEntry.identifier).toBe(SALES_ID);
         expect(committed).toHaveBeenCalledTimes(1);
         expect(assetTree(fixture)).toBe(tree);
         expect(treeLoads).toBe(1);
      });
   }
});

describe("SaveWorksheetDialog — Options pane stays lazy (Bug #78413)", () => {
   for(const alias of ["_sales", "(Q1) Sales"]) {
      it(`enables OK at open for the existing alias "${alias}"`, async () => {
         const fixture = await renderDialog(null, { realOptions: true, alias });
         const comp = fixture.componentInstance as SaveWorksheetDialog;

         expect(fixture.debugElement.query(By.directive(WorksheetOptionPane))).toBeNull();
         expect(comp.form.valid).toBe(true);
         expect(okButton(fixture).disabled).toBe(false);
      });
   }
});
