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
 * Bug #78447, through the real UI (Add dialog, Remove, Apply): the organization reload after a
 * save can be answered by a cluster node that still has the properties from before the save.
 * Complements users-settings-page-org-properties.tl.spec.ts with a removal, an id change,
 * switching organizations around the save, and the other identity types.
 */
import { provideHttpClient } from "@angular/common/http";
import { MatSnackBar } from "@angular/material/snack-bar";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, within } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { EMPTY, of, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { UsersSettingsPageComponent } from "./users-settings-page.component";
import { SavedOrgPropertiesService } from "../saved-org-properties.service";
import { SecurityTreeService } from "../security-tree.service";
import { SecurityTreeRootModel } from "../users-settings-view/security-tree-root-model";
import { SecurityTreeNodeModel } from "../users-settings-view/security-tree-node-model";
import { SecurityTreeNode } from "../../security-tree-view/security-tree-node";
import { EditIdentityViewComponent } from "../edit-identity-view/edit-identity-view.component";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { PropertyModel } from "../../property-table-view/property-model";
import { EditOrganizationPaneModel } from "../edit-identity-pane/edit-identity-pane.model";
import { By } from "@angular/platform-browser";

const PROVIDER = "Primary";

interface Org { name: string; id: string; }

const orgNode = (o: Org): SecurityTreeNodeModel => ({
   identityID: { name: o.name, orgID: o.id }, type: IdentityType.ORGANIZATION, children: [],
   readOnly: false, organization: o.id,
});

// in-memory "cluster": fresh = what the node that handled the POST has, stale = what another node
// has until its debounced reload
class FakeCluster {
   orgs: Org[] = [{ name: "OrgA", id: "orga" }, { name: "OrgB", id: "orgb" }];
   props = new Map<string, PropertyModel[]>([
      ["orga", [{ name: "max.row.count", value: "100" }]],
      ["orgb", [{ name: "max.col.count", value: "7" }]],
   ]);
   staleNextGet = new Map<string, PropertyModel[]>();
   staleAfterSave = false;
   failSave = false;
   failTree = false;
   posts: any[] = [];
   orgGets: string[] = [];

   handlers() {
      return [
         http.get("*/api/em/security/themes", () => HttpResponse.json({ themes: [] })),
         http.get("*/api/em/security/providers/*/identities/*", () => HttpResponse.json({ identityNames: [] })),
         http.get("*/api/em/security/user/get-security-tree-root/*", () => {
            if(this.failTree) {
               return HttpResponse.json({ message: "tree failed" }, { status: 500 });
            }

            const root = (name: string, type: IdentityType, children: SecurityTreeNodeModel[] = []) =>
               ({ identityID: { name, orgID: "host-org" }, type, children, readOnly: false, organization: "host-org" });
            return HttpResponse.json(<SecurityTreeRootModel>{
               users: root("Users", IdentityType.USERS),
               groups: root("Groups", IdentityType.GROUPS),
               roles: root("Roles", IdentityType.ROLES),
               organizations: root("Organizations", IdentityType.ORGANIZATION, this.orgs.map(orgNode)),
               editable: true, isMultiTenant: true, namedUsers: false,
            });
         }),
         http.get("*/api/em/security/providers/*/organization/:key/", ({ params }) => {
            const key = decodeURIComponent(params.key as string);
            const org = this.orgs.find(o => key.endsWith(o.id) && key.startsWith(o.name));
            this.orgGets.push(key);

            if(!org) {
               return new HttpResponse(null, { status: 404 });
            }

            let props = this.props.get(org.id);

            if(this.staleNextGet.has(org.id)) {
               props = this.staleNextGet.get(org.id);
               this.staleNextGet.delete(org.id);
            }

            return HttpResponse.json(<EditOrganizationPaneModel>{
               name: org.name, organization: org.id, root: false, identityNames: [], members: [],
               roles: [], permittedIdentities: [], editable: true, id: org.id,
               properties: JSON.parse(JSON.stringify(props)), locale: "", localesList: [],
               currentUserName: "admin", theme: "",
            });
         }),
         http.post("*/api/em/security/users/edit-organization/*", async ({ request }) => {
            const body: any = await request.json();
            this.posts.push(body);

            if(this.failSave) {
               return HttpResponse.json({ message: "boom" }, { status: 500 });
            }

            const old = this.orgs.find(o => o.name === body.oldName);
            const before = this.props.get(old.id);
            this.props.delete(old.id);
            old.name = body.name;
            old.id = body.id;
            this.props.set(body.id, body.properties);

            if(this.staleAfterSave) {
               this.staleNextGet.set(body.id, before);
            }

            return new HttpResponse("", { status: 200 });
         }),
      ];
   }
}

async function setup(cluster: FakeCluster) {
   server.use(http.all("*/api/*", ({ request }) => {
      return HttpResponse.json({});
   }));
   server.use(...cluster.handlers());
   const onRefresh = new Subject<{ provider: string, providerChanged: boolean }>();
   const orgDropdown = {
      onRefresh, onOrgChange: new Subject<any>(),
      getProvider: () => PROVIDER, authenticationProviders: [PROVIDER],
      loginUserOrgName: "Host Organization", loginUserOrgID: "host-org",
      isSystemAdmin: () => true,
      refresh: vi.fn((provider: string, providerChanged: boolean) => onRefresh.next({ provider, providerChanged })),
      refreshProviders: vi.fn(() => onRefresh.next({ provider: PROVIDER, providerChanged: false })),
      getCurrentOrgName: () => "Host Organization",
   };
   const snackBar = { open: vi.fn() };
   const errorService = { showSnackBar: vi.fn(() => EMPTY) };

   const result = await render(UsersSettingsPageComponent, {
      providers: [
         provideHttpClient(), provideNoopAnimations(), SecurityTreeService,
         { provide: MatSnackBar, useValue: snackBar },
         { provide: OrganizationDropdownService, useValue: orgDropdown },
         { provide: ScheduleUsersService, useValue: { loadScheduleUsers: vi.fn() } },
         { provide: ErrorHandlerService, useValue: errorService },
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: AppInfoService, useValue: { isEnterprise: () => of(true), getCurrentOrgInfo: () => of({}) } },
      ],
   });
   const fixture = result.fixture;
   const page = fixture.componentInstance;
   const saved = fixture.debugElement.injector.get(SavedOrgPropertiesService);
   const cd = () => fixture.componentRef.changeDetectorRef.detectChanges();
   const settle = async (ms = 50) => {
      for(let i = 0; i < 6; i++) {
         await new Promise(r => setTimeout(r, ms));
         cd();
         // whenStable() waits for pending HTTP requests, which never settle while a request is gated
         await Promise.race([fixture.whenStable(), new Promise(r => setTimeout(r, ms))]);
      }
   };
   const view = () => fixture.debugElement.query(By.directive(EditIdentityViewComponent))?.componentInstance as EditIdentityViewComponent;
   const select = async (o: Org) => {
      page.selectionChanged([new SecurityTreeNode({ name: o.name, orgID: o.id }, IdentityType.ORGANIZATION)]);
      await settle();
   };
   const propertyCard = () => {
      const header = screen.getAllByText(/Properties/i).find(e => e.tagName.toLowerCase() === "mat-card-header");
      return header.closest("mat-card") as HTMLElement;
   };
   const tableText = () => propertyCard().querySelector("table").textContent;
   const user = userEvent.setup();
   const clickApply = async () => {
      const apply = screen.getByRole("button", { name: /Apply/ });
      expect(apply.hasAttribute("disabled")).toBe(false);
      await user.click(apply);
      await settle();
   };
   const addPropertyViaDialog = async (value: string) => {
      await user.click(within(propertyCard()).getByRole("button", { name: /Add/ }));
      await settle();
      const dialog = await screen.findByRole("dialog");
      await user.click(within(dialog).getByRole("combobox"));
      await settle();
      await user.click(screen.getByRole("option", { name: /Max User Count/ }));
      await settle();
      const input = within(dialog).getByRole("spinbutton");
      await user.clear(input);
      await user.type(input, value);
      await settle();
      await user.click(within(dialog).getByRole("button", { name: /OK/ }));
      await settle(250); // 200 ms valueChanges timer
   };

   onRefresh.next({ provider: PROVIDER, providerChanged: false });
   await settle();
   return { fixture, page, saved, view, select, settle, tableText, clickApply, addPropertyViaDialog, propertyCard, user, snackBar, cd };
}

describe("UsersSettingsPageComponent - organization properties through the UI after Apply (Bug #78447)", { timeout: 30000 }, () => {
   it("stale GET after Apply: an added property stays (real Add dialog + Apply)", async () => {
      const cluster = new FakeCluster();
      cluster.staleAfterSave = true;
      const h = await setup(cluster);
      await h.select(cluster.orgs[0]);
      expect(h.tableText()).toContain("100");

      await h.addPropertyViaDialog("4321");
      expect(h.tableText()).toContain("4321");
      await h.clickApply();

      expect(cluster.posts.length).toBe(1);
      expect(cluster.posts[0].properties).toContainEqual({ name: "max.user.count", value: 4321 });
      // the GET after save was served stale
      expect(cluster.orgGets.length).toBe(2);
      expect(cluster.staleNextGet.size).toBe(0);
      expect(h.tableText()).toContain("4321");
      expect(h.tableText()).toContain("100");
   });

   it("stale GET after Apply: a removed property stays removed (real Remove + Apply)", async () => {
      const cluster = new FakeCluster();
      cluster.staleAfterSave = true;
      const h = await setup(cluster);
      await h.select(cluster.orgs[0]);
      expect(h.tableText()).toContain("100");

      const row = within(h.propertyCard()).getByText("100").closest("tr");
      await h.user.click(within(row).getByRole("checkbox"));
      await h.settle();
      await h.user.click(within(h.propertyCard()).getByRole("button", { name: /Remove/ }));
      await h.settle(250);
      expect(h.tableText()).not.toContain("100");
      await h.clickApply();

      expect(cluster.posts[0].properties).toEqual([]);
      expect(cluster.orgGets.length).toBe(2);
      expect(cluster.staleNextGet.size).toBe(0);
      expect(h.view().model.name).toBe("OrgA");
      expect(h.propertyCard().querySelector("table")).not.toBeNull();
      expect(h.tableText()).not.toContain("100");
   });

   it("id-only change (same name), stale GET: property still shows under the new id", async () => {
      const cluster = new FakeCluster();
      cluster.staleAfterSave = true;
      const h = await setup(cluster);
      await h.select(cluster.orgs[0]);
      await h.addPropertyViaDialog("4321");
      const idInput = screen.getByPlaceholderText("_#(ID)") as HTMLInputElement;
      await h.user.clear(idInput);
      await h.user.type(idInput, "orgnewid");
      await h.settle(250);
      await h.clickApply();

      expect(cluster.posts[0].id).toBe("orgnewid");
      expect(cluster.orgGets[cluster.orgGets.length - 1]).toContain("orgnewid");
      expect(cluster.staleNextGet.size).toBe(0);
      expect(h.tableText()).toContain("4321");
      expect((h.saved as any).saved).toBeNull();
   });

   it("switching to another org right after Apply (reload never reached the view) shows that org's own properties", async () => {
      const cluster = new FakeCluster();
      cluster.staleAfterSave = true;
      const h = await setup(cluster);
      await h.select(cluster.orgs[0]);
      await h.addPropertyViaDialog("4321");
      cluster.failTree = true; // the post-save reload of org A never happens
      await h.clickApply();
      cluster.failTree = false;

      await h.select(cluster.orgs[1]);
      expect(h.tableText()).toContain("7");
      expect(h.tableText()).not.toContain("4321");
      expect(h.tableText()).not.toContain("100");
      expect((h.saved as any).saved).toBeNull();
   });

   it("switching to another org while the save is in flight: B shows B, then the reload of A shows the saved list", async () => {
      const cluster = new FakeCluster();
      cluster.staleAfterSave = true;
      const h = await setup(cluster);
      let release: () => void;
      const gate = new Promise<void>(r => release = r);
      server.use(http.post("*/api/em/security/users/edit-organization/*", async ({ request }) => {
         await gate;
         const body: any = await request.json();
         cluster.posts.push(body);
         const before = cluster.props.get("orga");
         cluster.props.set("orga", body.properties);
         cluster.staleNextGet.set("orga", before);
         return new HttpResponse("", { status: 200 });
      }));
      await h.select(cluster.orgs[0]);
      await h.addPropertyViaDialog("4321");
      await h.clickApply();
      await h.select(cluster.orgs[1]);
      expect(h.tableText()).toContain("7");
      expect(h.tableText()).not.toContain("4321");
      release();
      await h.settle();
      // the page re-selects the saved org after a save; it shows the saved list despite the stale GET
      expect(h.view().model.name).toBe("OrgA");
      expect(h.tableText()).toContain("4321");
   });

   it("users/groups/roles: their saves never record org properties and don't consume an org memo", async () => {
      const cluster = new FakeCluster();
      const h = await setup(cluster);
      server.use(
         http.post("*/api/em/security/user/edit-role/*", () => new HttpResponse("", { status: 200 })),
         http.post("*/api/em/security/providers/*/groups/*", () => new HttpResponse("", { status: 200 })),
         http.post("*/api/em/security/users/edit-user/*", () => new HttpResponse("", { status: 200 })),
      );

      for(const kind of ["users", "groups", "roles"]) {
         server.use(http.get(`*/api/em/security/providers/*/${kind}/:key/`, ({ params }) => {
            const name = decodeURIComponent(params.key as string).split("~")[0];
            return HttpResponse.json({ name, organization: "orga", root: false, identityNames: [], members: [], roles: [],
               permittedIdentities: [], editable: true, theme: "", alias: "", email: "", locale: "", localesList: [],
               status: true, password: null, currentUser: false, supportChangePassword: true, newUser: false,
               hasPassword: true, defaultRole: false, isSysAdmin: false, isOrgAdmin: false, description: "" });
         }));
      }

      h.saved.set("orga", [{ name: "max.row.count", value: "42" }]);
      const setSpy = vi.spyOn(h.saved, "set");
      const takeSpy = vi.spyOn(h.saved, "take");
      const base = { root: false, identityNames: [], members: [], roles: [], permittedIdentities: [], editable: true, organization: "orga", oldName: "x" };
      h.page.setRole(<any>{ ...base, name: "r1", defaultRole: false, isSysAdmin: false, isOrgAdmin: false, description: "" });
      h.page.setGroup(<any>{ ...base, name: "g1" });
      h.page.setUser(<any>{ ...base, name: "u1", oldName: "u1", password: null, alias: "", email: "", locale: "", status: true });
      await h.settle();
      expect(setSpy).not.toHaveBeenCalled();
      expect(takeSpy).not.toHaveBeenCalled();
      expect((h.saved as any).saved?.orgId).toBe("orga");
      // the user save re-selects the user; its edit view renders normally
      expect(h.view()?.model?.name).toBe("u1");
   });
});
