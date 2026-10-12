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
 * SaveViewsheetDialog — Repository/Options tab switching with the real asset tree.
 *
 * Bug #78413: the Repository pane was destroyed when the Options tab was shown. Coming back
 * re-created the asset tree, whose on-load auto-selection (defaultFolder or the first root child)
 * emitted nodeSelected and overwrote the folder the user had picked, so OK saved to the default
 * folder. The Repository nav item now keeps its pane ([destroyOnHide]="false").
 *
 * The Options pane must stay lazy: if it were built at open, its alias validator
 * (doesNotStartWithNumberOrLetter) would disable OK for EM-set aliases like "销售" or "_sales"
 * while the error sits on a hidden tab.
 *
 * Uses the real AssetTreeComponent (+ AssetTreeService, CurrentUserService, ModelService) with MSW,
 * and asserts the request body sent to the validation endpoint.
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
import { SaveViewsheetDialog } from "./save-viewsheet-dialog.component";
import { ViewsheetOptionsPane } from "./viewsheet-options-pane.component";
import { ViewsheetOptionsPaneModel } from "../../data/vs/viewsheet-options-pane-model";
import { SaveViewsheetDialogModel } from "../../data/vs/save-viewsheet-dialog-model";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { AssetTreeComponent } from "../../../widget/asset-tree/asset-tree.component";
import { AssetClientService } from "../../../common/asset-client/asset-client.service";
import { ModelService } from "../../../widget/services/model.service";

@Component({ selector: "viewsheet-options-pane", template: "<div class='opts'></div>", standalone: true })
class ViewsheetOptionsPaneStub {
   @Input() model: any;
   @Input() form: any;
   @Input() runtimeId: any;
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
   const privateRoot = folder(PRIVATE_ROOT_ID, "Private Dashboard", "/", 4);
   const sales = folder(SALES_ID, "Sales", "Sales", 1);
   const dashboard = folder(GLOBAL_ROOT_ID, "Dashboard", "/", 1, [privateRoot, sales]);
   return { treeNodeModel: folder("root", "root", "/", 0, [dashboard]), parameters: null };
}

let treeLoads = 0;
let validateBodies: any[] = [];

function installHandlers() {
   treeLoads = 0;
   validateBodies = [];
   server.use(
      http.get("*/api/portal/get-current-user", () =>
         HttpResponse.json({ name: { name: "admin", orgID: "host-org" } })),
      http.post("*/api/composer/asset_tree", () => {
         treeLoads++;
         return HttpResponse.json(treeResponse());
      }),
      http.post("*/api/composer/vs/save-viewsheet-dialog-model/*", async ({ request }) => {
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

function optionsModel(alias: string): ViewsheetOptionsPaneModel {
   return {
      viewsheetParametersDialogModel: { enabledParameters: [], disabledParameters: [] },
      selectDataSourceDialogModel: { title: "Select Data Source", dataSource: null },
      useMetaData: false,
      promptForParams: false,
      selectionAssociation: true,
      createMv: false,
      onDemandMvEnabled: false,
      maxRows: 0,
      snapGrid: 10,
      alias,
      desc: "",
      serverSideUpdate: false,
      touchInterval: 60,
      listOnPortalTree: true,
      worksheet: false,
   } as ViewsheetOptionsPaneModel;
}

async function renderDialog(defaultFolder: any, opts: { realOptions?: boolean, model?: SaveViewsheetDialogModel } = {}) {
   installHandlers();
   const model: SaveViewsheetDialogModel = opts.model ?? {
      name: "Untitled-1", parentId: "", updateDepend: true, viewsheetOptionsPaneModel: {} as any
   };
   const { fixture } = await render(SaveViewsheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderComponentStub },
         ...(opts.realOptions ? [] : [{ replace: ViewsheetOptionsPane, with: ViewsheetOptionsPaneStub }]),
      ],
      childComponentOverrides: [
         { component: AssetTreeComponent, providers: [{ provide: AssetClientService, useFactory: assetClientMock }] },
      ],
      providers: [provideHttpClient(), ModelService, { provide: NgbModal, useValue: {} }],
      componentProperties: { model, runtimeId: "vs-123", defaultFolder },
   });
   await settle(fixture);
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("SaveViewsheetDialog — save location survives an Options round trip (Bug #78413)", () => {
   const cases: [string, any][] = [
      ["without defaultFolder (selectNodeOnLoadFn)", null],
      ["with defaultFolder (parent of the sheet)", { path: "/", scope: 1, identifier: GLOBAL_ROOT_ID }],
   ];

   for(const [title, defaultFolder] of cases) {
      it(`keeps the folder picked before visiting Options — ${title}`, async () => {
         const fixture = await renderDialog(defaultFolder);
         const comp = fixture.componentInstance as SaveViewsheetDialog;
         const committed = vi.fn();
         comp.onCommit.subscribe(committed);
         const tree = assetTree(fixture);

         // on open the tree selects the default: the global Dashboard root
         expect(comp.model.parentId).toBe(GLOBAL_ROOT_ID);

         // user picks Private Dashboard (the handler bound to the tree's nodesSelected on click)
         tree.selectNodes([tree.root.children[0].children[0]]);
         fixture.detectChanges();
         expect(comp.model.parentId).toBe(PRIVATE_ROOT_ID);

         clickTab(fixture, "Options");
         await settle(fixture);
         clickTab(fixture, "Repository");
         await settle(fixture);


         comp.form.controls["name"].setValue("Mine");
         fixture.detectChanges();
         expect(okButton(fixture).disabled).toBe(false);
         okButton(fixture).click();
         await settle(fixture);

         expect(validateBodies.length).toBe(1);
         expect(validateBodies[0].parentId).toBe(PRIVATE_ROOT_ID);
         expect(validateBodies[0].name).toBe("Mine");
         expect(committed).toHaveBeenCalledTimes(1);
         expect(committed.mock.calls[0][0].parentId).toBe(PRIVATE_ROOT_ID);

         // the tree was kept, not re-created and re-selected
         expect(assetTree(fixture)).toBe(tree);
         expect(treeLoads).toBe(1);
         expect(tree.selectedNodes.map(n => n.data.identifier)).toEqual([PRIVATE_ROOT_ID]);
      });
   }

   it("keeps the folder picked after visiting Options first", async () => {
      const fixture = await renderDialog(null);
      const comp = fixture.componentInstance as SaveViewsheetDialog;

      clickTab(fixture, "Options");
      await settle(fixture);
      clickTab(fixture, "Repository");
      await settle(fixture);

      const tree = assetTree(fixture);
      tree.selectNodes([tree.root.children[0].children[0]]);
      fixture.detectChanges();
      comp.form.controls["name"].setValue("Mine");
      fixture.detectChanges();
      expect(okButton(fixture).disabled).toBe(false);
      okButton(fixture).click();
      await settle(fixture);

      expect(validateBodies[0].parentId).toBe(PRIVATE_ROOT_ID);
   });
});

describe("SaveViewsheetDialog — Options pane stays lazy (Bug #78413)", () => {
   // An alias set in EM may start with CJK or punctuation, which the Options pane's alias
   // validator rejects. Building that pane at open would disable OK with the error on a hidden tab.
   for(const alias of ["销售", "_sales", "(Q1) Sales"]) {
      it(`enables OK at open for the existing alias "${alias}"`, async () => {
         const fixture = await renderDialog(null, {
            realOptions: true,
            model: {
               name: "Existing", parentId: "", updateDepend: true,
               viewsheetOptionsPaneModel: optionsModel(alias),
            },
         });
         const comp = fixture.componentInstance as SaveViewsheetDialog;
         const optionsForm: any = comp.form.controls["viewsheetOptionsPaneForm"];

         expect(fixture.debugElement.query(By.directive(ViewsheetOptionsPane))).toBeNull();
         expect(optionsForm.controls["alias"]).toBeUndefined();
         expect(comp.form.valid).toBe(true);
         expect(okButton(fixture).disabled).toBe(false);

         okButton(fixture).click();
         await settle(fixture);
         expect(validateBodies.length).toBe(1);
         expect(validateBodies[0].viewsheetOptionsPaneModel.alias).toBe(alias);
      });
   }
});
