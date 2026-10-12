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
 * EmailAddrDialog — Query/Embedded tab switching with the real QueryEmailPane + asset tree.
 *
 * Bug #78413 (same cause as the composer save dialogs): leaving the Query tab destroyed
 * QueryEmailPane. The re-created pane was fed the dialog's opening queryAddress, and its asset tree
 * re-selected the opening query through dataSourcePath, while `addresses` still held the user's
 * pick. Touching a dropdown on the reverted pane then silently saved the opening query. The Query
 * nav item now keeps its pane ([destroyOnHide]="false").
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { ComponentFixture } from "@angular/core/testing";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { Subject } from "rxjs";
import { server } from "@test-mocks/server";
import { EmailAddrDialog } from "./email-addr-dialog.component";
import { EmbeddedEmailPane } from "./embedded-email-pane.component";
import { QueryEmailPane } from "./query-email-pane.component";
import { ModalHeaderComponent } from "../modal-header/modal-header.component";
import { AssetTreeComponent } from "../asset-tree/asset-tree.component";
import { AssetClientService } from "../../common/asset-client/asset-client.service";

@Component({ selector: "embedded-email-pane", template: "<div class='embedded'></div>", standalone: true })
class EmbeddedEmailPaneStub {
   @Input() model: any;
   @Input() showRoot: any;
   @Input() addresses: any;
   @Input() embeddedOnly: any;
   @Input() emailForm: any;
   @Output() addressesChange = new EventEmitter<string>();
}

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderComponentStub {
   @Input() title: any;
   @Input() cshid: any;
}

function query(source: string) {
   return {
      label: source, leaf: true, expanded: false, children: [],
      data: {
         identifier: "0^65605^__NULL__^ds/" + source, type: "QUERY", path: "ds/" + source, scope: 0,
         properties: { prefix: "ds", source, type: "query" },
      },
   };
}

function treeResponse() {
   const ds = {
      label: "ds", leaf: false, expanded: true, children: [query("q1"), query("q2")],
      data: { identifier: "0^65605^__NULL__^ds", type: "DATA_SOURCE", path: "ds", scope: 0, properties: {} },
   };
   const root = {
      label: "root", leaf: false, expanded: true, children: [ds],
      data: { identifier: "root", type: "FOLDER", path: "/", scope: 0, properties: {} },
   };
   return { treeNodeModel: root, parameters: null };
}

let treeLoads = 0;

function installHandlers() {
   treeLoads = 0;
   server.use(
      http.get("*/api/portal/get-current-user", () =>
         HttpResponse.json({ name: { name: "admin", orgID: "host-org" } })),
      http.post("*/api/composer/asset_tree", () => {
         treeLoads++;
         return HttpResponse.json(treeResponse());
      }),
      http.post("*/api/portal/schedule/emails/query/columns", () =>
         HttpResponse.json({ columns: ["U1", "U2"], columnLabels: ["U1", "U2"] })),
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

function queryPane(fixture: ComponentFixture<any>): QueryEmailPane {
   return fixture.debugElement.query(By.directive(QueryEmailPane))?.componentInstance;
}

function chooseOption(fixture: ComponentFixture<any>, selectIndex: number, value: string): void {
   const selects: HTMLSelectElement[] =
      Array.from(fixture.debugElement.query(By.directive(QueryEmailPane)).nativeElement.querySelectorAll("select"));
   selects[selectIndex].value = value;
   selects[selectIndex].dispatchEvent(new Event("change"));
   fixture.detectChanges();
}

async function renderDialog(addresses: string) {
   installHandlers();
   const { fixture } = await render(EmailAddrDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: EmbeddedEmailPane, with: EmbeddedEmailPaneStub },
         { replace: ModalHeaderComponent, with: ModalHeaderComponentStub },
      ],
      childComponentOverrides: [
         { component: AssetTreeComponent, providers: [{ provide: AssetClientService, useFactory: assetClientMock }] },
      ],
      providers: [provideHttpClient()],
      componentInputs: { embeddedOnly: false, model: {} as any, addresses },
   });
   await settle(fixture);
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("EmailAddrDialog — query pick survives an Embedded round trip (Bug #78413)", () => {
   it("keeps the picked query and user, and saves them after a later dropdown edit", async () => {
      const fixture = await renderDialog("query: ds.q1, user: U1");
      const comp = fixture.componentInstance as EmailAddrDialog;
      const committed = vi.fn();
      comp.onCommit.subscribe(committed);
      const pane = queryPane(fixture);
      expect(pane.query).toBe("ds.q1");

      // user picks q2 in the tree, then the User column U2
      const tree: AssetTreeComponent = fixture.debugElement.query(By.directive(AssetTreeComponent)).componentInstance;
      tree.selectNodes([tree.root.children[0].children[1]]);
      await settle(fixture);
      chooseOption(fixture, 0, "U2");
      expect(comp.addresses).toBe("query: ds.q2, user: U2");

      clickTab(fixture, "Embedded");
      await settle(fixture);
      clickTab(fixture, "Query");
      await settle(fixture);

      // a later edit on the Query tab must not bring back the opening query
      chooseOption(fixture, 1, "U2");
      expect(comp.addresses).toBe("query: ds.q2, user: U2, email: U2");

      comp.ok();
      expect(committed).toHaveBeenCalledWith({ emails: "query: ds.q2, user: U2, email: U2", type: "query" });

      // the pane was kept and still shows the user's pick
      expect(queryPane(fixture)).toBe(pane);
      expect(treeLoads).toBe(1);
      expect(pane.query).toBe("ds.q2");
      expect(pane.user).toBe("U2");
   });
});
