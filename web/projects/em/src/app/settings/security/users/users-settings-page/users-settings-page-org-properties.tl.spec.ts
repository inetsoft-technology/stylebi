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
 * UsersSettingsPageComponent -> EditIdentityViewComponent, organization Properties after Apply.
 *
 * Bug #78447: the reload after an organization save can be answered by a cluster node that has
 * not picked up the saved properties yet. The whole chain is rendered (page, tree, pane, view)
 * and MSW plays both nodes: the save updates the server's properties, and with staleAfterSave
 * the next GET of the organization returns the properties from before the save.
 */

import { provideHttpClient } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { By } from "@angular/platform-browser";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { EMPTY, of, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { PropertyModel } from "../../property-table-view/property-model";
import { SecurityTreeNode } from "../../security-tree-view/security-tree-node";
import { EditOrganizationPaneModel } from "../edit-identity-pane/edit-identity-pane.model";
import { EditIdentityViewComponent } from "../edit-identity-view/edit-identity-view.component";
import { SecurityTreeService } from "../security-tree.service";
import { SecurityTreeNodeModel } from "../users-settings-view/security-tree-node-model";
import { SecurityTreeRootModel } from "../users-settings-view/security-tree-root-model";
import { UsersSettingsPageComponent } from "./users-settings-page.component";

const PROVIDER = "Primary";

interface Org {
   name: string;
   id: string;
}

class FakeServer {
   orgs: Org[] = [{ name: "OrgA", id: "orga" }, { name: "OrgB", id: "orgb" }];
   props = new Map<string, PropertyModel[]>([
      ["orga", [{ name: "max.row.count", value: "100" }]],
      ["orgb", [{ name: "max.col.count", value: "7" }]],
   ]);
   staleAfterSave = false;
   failSave = false;
   private staleNextGet = new Map<string, PropertyModel[]>();

   handlers() {
      const node = (name: string, type: IdentityType, children: SecurityTreeNodeModel[] = []) =>
         <SecurityTreeNodeModel> { identityID: { name, orgID: "host-org" }, type, children,
                                   readOnly: false, organization: "host-org" };

      return [
         http.get("*/api/em/navbar/organization", () => HttpResponse.json("host-org")),
         http.get("*/api/em/security/themes", () => HttpResponse.json({ themes: [] })),
         http.get("*/api/em/security/providers/*/identities/*", () =>
            HttpResponse.json({ identityNames: [] })),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(<SecurityTreeRootModel> {
               users: node("Users", IdentityType.USERS),
               groups: node("Groups", IdentityType.GROUPS),
               roles: node("Roles", IdentityType.ROLES),
               organizations: node("Organizations", IdentityType.ORGANIZATION, this.orgs.map(o =>
                  <SecurityTreeNodeModel> { identityID: { name: o.name, orgID: o.id },
                     type: IdentityType.ORGANIZATION, children: [], readOnly: false, organization: o.id })),
               editable: true,
               isMultiTenant: true,
               namedUsers: false,
            })),
         http.get("*/api/em/security/providers/*/organization/:key/", ({ params }) => {
            const key = decodeURIComponent(params.key as string);
            const org = this.orgs.find(o => key.startsWith(o.name) && key.endsWith(o.id));

            if(!org) {
               return new HttpResponse(null, { status: 404 });
            }

            let props = this.props.get(org.id);

            if(this.staleNextGet.has(org.id)) {
               props = this.staleNextGet.get(org.id);
               this.staleNextGet.delete(org.id);
            }

            return HttpResponse.json(<EditOrganizationPaneModel> {
               name: org.name, organization: org.id, id: org.id, root: false, identityNames: [],
               members: [], roles: [], permittedIdentities: [], editable: true, theme: null,
               properties: structuredClone(props), locale: "", localesList: [], currentUserName: "admin",
            });
         }),
         http.post("*/api/em/security/users/edit-organization/*", async ({ request }) => {
            const body = <EditOrganizationPaneModel> await request.json();

            if(this.failSave) {
               return HttpResponse.json({ message: "refused" }, { status: 500 });
            }

            const org = this.orgs.find(o => o.name === body.oldName);
            const before = this.props.get(org.id);
            this.props.delete(org.id);
            org.name = body.name;
            org.id = body.id;
            this.props.set(org.id, body.properties);

            if(this.staleAfterSave) {
               this.staleNextGet.set(org.id, before);
            }

            return HttpResponse.json("");
         }),
      ];
   }
}

async function setup(fake: FakeServer) {
   server.use(...fake.handlers());
   const onRefresh = new Subject<{ provider: string, providerChanged: boolean }>();
   const orgDropdown = {
      onRefresh,
      onOrgChange: new Subject<unknown>(),
      getProvider: () => PROVIDER,
      authenticationProviders: [PROVIDER],
      loginUserOrgName: "Host Organization",
      loginUserOrgID: "host-org",
      isSystemAdmin: () => true,
      refresh: vi.fn((provider: string, providerChanged: boolean) =>
         onRefresh.next({ provider, providerChanged })),
      refreshProviders: vi.fn(),
      getCurrentOrgName: () => "Host Organization",
   };
   const errorService = { showSnackBar: vi.fn().mockReturnValue(EMPTY) };

   const { fixture } = await render(UsersSettingsPageComponent, {
      providers: [
         provideHttpClient(),
         provideNoopAnimations(),
         SecurityTreeService,
         { provide: MatDialog, useValue: { open: vi.fn() } },
         { provide: MatSnackBar, useValue: { open: vi.fn() } },
         { provide: OrganizationDropdownService, useValue: orgDropdown },
         { provide: ScheduleUsersService, useValue: { loadScheduleUsers: vi.fn() } },
         { provide: ErrorHandlerService, useValue: errorService },
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: AppInfoService, useValue: { isEnterprise: () => of(true), getCurrentOrgInfo: () => of({}) } },
      ],
   });

   const page = fixture.componentInstance;
   // the runner is zoneless: render the page itself after each step
   const settle = async () => {
      for(let i = 0; i < 6; i++) {
         await new Promise(resolve => setTimeout(resolve, 30));
         fixture.componentRef.changeDetectorRef.detectChanges();
         await fixture.whenStable();
      }
   };
   const view = () => fixture.debugElement.query(By.directive(EditIdentityViewComponent))
      ?.componentInstance as EditIdentityViewComponent;
   const select = async (org: Org) => {
      page.selectionChanged([new SecurityTreeNode({ name: org.name, orgID: org.id }, IdentityType.ORGANIZATION)]);
      await settle();
   };
   const apply = async (properties: PropertyModel[], name?: string) => {
      view().onPropertyChanged(properties);

      if(name) {
         view().form.get("name").setValue(name);
      }

      view().apply();
      await settle();
   };

   onRefresh.next({ provider: PROVIDER, providerChanged: false });
   await settle();
   return { page, view, select, apply, settle, errorService };
}

describe("UsersSettingsPageComponent - organization properties after Apply (Bug #78447)", { timeout: 30000 }, () => {
   const added = [{ name: "max.row.count", value: "100" }, { name: "max.user.count", value: "5" }];

   // 🔁 Regression-sensitive: the reload after the save is answered with the old properties
   it("should keep an added property when the reload after the save is stale", async () => {
      const fake = new FakeServer();
      fake.staleAfterSave = true;
      const h = await setup(fake);
      await h.select(fake.orgs[0]);
      expect(h.view().properties).toEqual([{ name: "max.row.count", value: "100" }]);

      await h.apply(added);

      expect(h.view().model.name).toBe("OrgA");
      expect(h.view().properties).toEqual(added);
   });

   it("should show the server's properties when the organization is selected again", async () => {
      const fake = new FakeServer();
      fake.staleAfterSave = true;
      const h = await setup(fake);
      await h.select(fake.orgs[0]);
      await h.apply(added);

      const changed = [{ name: "max.row.count", value: "50" }];
      fake.props.set("orga", changed);
      await h.select(fake.orgs[1]);
      await h.select(fake.orgs[0]);

      expect(h.view().properties).toEqual(changed);
   });

   // a rename refreshes the provider and doesn't reload the organization, so nothing may be
   // left to show over a later load of it
   it("should show the server's properties after a rename when the organization is selected", async () => {
      const fake = new FakeServer();
      const h = await setup(fake);
      await h.select(fake.orgs[0]);
      await h.apply(added, "OrgRenamed");

      const changed = [{ name: "max.row.count", value: "50" }];
      fake.props.set("orga", changed);
      await h.select(fake.orgs[0]);

      expect(h.view().model.name).toBe("OrgRenamed");
      expect(h.view().properties).toEqual(changed);
   });

   it("should show the server's properties after a failed save", async () => {
      const fake = new FakeServer();
      fake.failSave = true;
      const h = await setup(fake);
      await h.select(fake.orgs[0]);
      await h.apply(added);
      expect(h.errorService.showSnackBar).toHaveBeenCalled();

      await h.select(fake.orgs[1]);
      await h.select(fake.orgs[0]);

      expect(h.view().properties).toEqual([{ name: "max.row.count", value: "100" }]);
   });
});
