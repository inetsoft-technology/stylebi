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
 * Bug #78726, through the real page, view, security tree, edit-identity editor and MatDialog:
 * unsaved edits to a user, group or role are kept when a switch to another identity is
 * cancelled (Cancel or Escape) more than once, after a re-click or ctrl-click back to the
 * identity being edited, and after Apply. Tree rows are clicked as rendered; HTTP is mocked
 * with MSW.
 */
import { CdkVirtualScrollViewport } from "@angular/cdk/scrolling";
import { provideHttpClient } from "@angular/common/http";
import { ErrorHandler } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { By } from "@angular/platform-browser";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { fireEvent, render, screen, within } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { EMPTY, of, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { UsersSettingsPageComponent } from "./users-settings-page.component";
import { SecurityTreeService } from "../security-tree.service";
import { SecurityTreeNodeModel } from "../users-settings-view/security-tree-node-model";
import { UsersSettingsViewComponent } from "../users-settings-view/users-settings-view.component";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { IdentityType } from "../../../../../../../shared/data/identity-type";

vi.setConfig({ testTimeout: 60000 });

const PROVIDER = "Primary";
const ORG = "host-org";

// tree sections, so that the same name can be told apart in the users and groups subtrees
const USERS = 0;
const GROUPS = 1;
const ROLES = 2;

type Close = "cancel" | "escape" | "ok";

// The page re-sends its selection from a dialog's afterClosed, which can run inside a change
// detection pass, so dev mode reports NG0100 on [selectedNodes] (it never runs in production
// builds, and it happens with and without the Bug #78726 fix). Tolerate exactly that error and
// fail the test on any other application error.
let appErrors: any[] = [];
const errorHandler: ErrorHandler = {
   handleError: (error: any) => {
      if(!String(error?.message ?? error).includes("NG0100")) {
         appErrors.push(error);
      }
   }
};

beforeEach(() => appErrors = []);
afterEach(() => expect(appErrors).toEqual([]));

const leaf = (name: string, type: IdentityType): SecurityTreeNodeModel =>
   ({ identityID: { name, orgID: ORG }, type, children: [], readOnly: false, organization: ORG, root: false });
const root = (name: string, type: IdentityType, children: SecurityTreeNodeModel[]): SecurityTreeNodeModel =>
   ({ identityID: { name, orgID: ORG }, type, children, readOnly: false, organization: ORG, root: true });

const identityModel = (name: string) => ({
   name, label: name, organization: ORG, root: false, identityNames: [], members: [], roles: [],
   permittedIdentities: [], editable: true, theme: "", alias: "", email: "", locale: "", localesList: [],
   status: true, password: null, currentUser: false, supportChangePassword: true, newUser: false,
   hasPassword: true, defaultRole: false, isSysAdmin: false, isOrgAdmin: false, description: ""
});

async function setup() {
   const gets: string[] = [];
   const posts: string[] = [];

   server.use(
      http.all("*/api/*", () => HttpResponse.json({})),
   );
   server.use(
      http.get("*/api/em/security/themes", () => HttpResponse.json({ themes: [] })),
      http.get("*/api/em/security/providers/*/identities/*", () => HttpResponse.json({ identityNames: [] })),
      http.get("*/api/em/security/user/get-security-tree-root/*", () => HttpResponse.json({
         users: root("Users", IdentityType.USERS, [
            leaf("alice", IdentityType.USER), leaf("bob", IdentityType.USER),
            leaf("Sales", IdentityType.USER), leaf("aliceX", IdentityType.USER)]),
         groups: root("Groups", IdentityType.GROUPS, [leaf("Sales", IdentityType.GROUP)]),
         roles: root("Roles", IdentityType.ROLES, [leaf("Designer", IdentityType.ROLE)]),
         organizations: root("Organizations", IdentityType.ORGANIZATION, []),
         editable: true, isMultiTenant: false, namedUsers: false,
      })),
      http.post("*/api/em/security/users/edit-user/*", async ({ request }) => {
         const body: any = await request.json();
         posts.push("user:" + body.name);
         return new HttpResponse("", { status: 200 });
      }),
   );

   for(const kind of ["users", "groups", "roles"]) {
      server.use(http.get(`*/api/em/security/providers/*/${kind}/:key/`, ({ params }) => {
         const name = decodeURIComponent(params.key as string).split("~")[0];
         gets.push(kind + "/" + name);
         return HttpResponse.json(identityModel(name));
      }));
   }

   const onRefresh = new Subject<any>();
   const orgDropdown = {
      onRefresh, onOrgChange: new Subject<any>(),
      getProvider: () => PROVIDER, authenticationProviders: [PROVIDER],
      loginUserOrgName: "Host Organization", loginUserOrgID: ORG,
      isSystemAdmin: () => true,
      refresh: vi.fn((p: string, c: boolean) => onRefresh.next({ provider: p, providerChanged: c })),
      refreshProviders: vi.fn(() => onRefresh.next({ provider: PROVIDER, providerChanged: false })),
      getCurrentOrgName: () => "Host Organization",
   };

   // give the virtual-scroll viewport a height so jsdom renders every tree row
   vi.spyOn(CdkVirtualScrollViewport.prototype, "getViewportSize").mockReturnValue(2000);

   const { fixture } = await render(UsersSettingsPageComponent, {
      configureTestBed: testBed => testBed.configureTestingModule({ rethrowApplicationErrors: false }),
      providers: [
         { provide: ErrorHandler, useValue: errorHandler },
         provideHttpClient(), provideNoopAnimations(), SecurityTreeService,
         { provide: MatSnackBar, useValue: { open: vi.fn() } },
         { provide: OrganizationDropdownService, useValue: orgDropdown },
         { provide: ScheduleUsersService, useValue: { loadScheduleUsers: vi.fn() } },
         { provide: ErrorHandlerService, useValue: { showSnackBar: vi.fn(() => EMPTY) } },
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: AppInfoService, useValue: { isEnterprise: () => of(true), getCurrentOrgInfo: () => of({}) } },
      ],
   });

   const page = fixture.componentInstance as UsersSettingsPageComponent;
   const cd = () => fixture.componentRef.changeDetectorRef.detectChanges();
   const settle = async (ms = 50) => {
      for(let i = 0; i < 6; i++) {
         await new Promise(r => setTimeout(r, ms));
         cd();
         await Promise.race([fixture.whenStable(), new Promise(r => setTimeout(r, ms))]);
      }
   };
   const user = userEvent.setup();
   const view = () => fixture.debugElement.query(By.directive(UsersSettingsViewComponent))
      .componentInstance as UsersSettingsViewComponent;
   const rows = () => Array.from(document.querySelectorAll(".mat-tree-node")) as HTMLElement[];
   const dialogs = () => document.querySelectorAll("mat-dialog-container").length;
   const nameInput = () => screen.queryByPlaceholderText("_#(Name)") as HTMLInputElement;

   const clickRow = async (name: string, section = USERS, ctrl = false) => {
      const all = rows();
      const rootIdx = ["Users", "Groups", "Roles"].map(r => all.findIndex(e =>
         (e.querySelector("button")?.getAttribute("aria-label") || "").includes(r)));
      const end = section < ROLES ? rootIdx[section + 1] : all.length;
      const row = all.slice(rootIdx[section] + 1, end)
         .find(e => e.querySelector("p")?.textContent.trim() === name);
      expect(row, `tree row ${name}`).toBeTruthy();

      if(ctrl) {
         await user.keyboard("{Control>}");
         await user.click(row);
         await user.keyboard("{/Control}");
      }
      else {
         await user.click(row);
      }

      await settle();
   };

   const closeDialog = async (how: Close) => {
      const container = document.querySelector("mat-dialog-container") as HTMLElement;
      expect(container, "dialog open").toBeTruthy();

      if(how === "escape") {
         fireEvent.keyDown(container, { key: "Escape", code: "Escape", keyCode: 27 });
      }
      else {
         await user.click(within(container).getByRole("button", { name: how === "ok" ? /OK/ : /Cancel/ }));
      }

      await settle();
   };

   const rename = async (suffix: string) => {
      await settle(60); // past the editor's 200 ms valueChanges subscription timer
      await user.type(nameInput(), suffix);
      await settle(60);
   };

   const apply = async () => {
      await user.click(screen.getByRole("button", { name: /Apply/ }));
      await settle();
   };

   const state = () => ({
      dialogs: dialogs(), name: nameInput()?.value,
      pageChanged: page.pageChanged, viewChanged: view().pageChanged
   });

   await settle();
   onRefresh.next({ provider: PROVIDER, providerChanged: false });
   await settle();

   for(const label of ["Users", "Groups", "Roles"]) {
      const toggle = Array.from(document.querySelectorAll("button[aria-label^='toggle ']"))
         .find(e => e.getAttribute("aria-label").includes(label)) as HTMLElement;
      expect(toggle, "toggle " + label).toBeTruthy();
      await user.click(toggle);
      await settle();
   }

   return { page, gets, posts, clickRow, closeDialog, rename, apply, state };
}

describe("UsersSettingsPage - switching identity with unsaved edits (Bug #78726)", () => {
   // [label, edited identity, its section, its GET kind, target identity, its section, its GET kind]
   const cases: [string, string, number, string, string, number, string][] = [
      ["user -> user", "alice", USERS, "users", "bob", USERS, "users"],
      ["group -> role", "Sales", GROUPS, "groups", "Designer", ROLES, "roles"],
      ["role -> group", "Designer", ROLES, "roles", "Sales", GROUPS, "groups"],
      ["user -> group with the same name", "Sales", USERS, "users", "Sales", GROUPS, "groups"],
   ];

   for(const [label, first, firstSection, firstKind, other, otherSection, otherKind] of cases) {
      for(const how of ["cancel", "escape"] as Close[]) {
         it(`${label}: keeps the edits over two switches closed by ${how}`, async () => {
            const h = await setup();
            await h.clickRow(first, firstSection);
            await h.rename("X");
            expect(h.gets).toEqual([`${firstKind}/${first}`]);

            for(let i = 0; i < 2; i++) {
               await h.clickRow(other, otherSection);
               expect(h.state().dialogs).toBe(1);
               // the other identity is not loaded behind the dialog
               expect(h.gets).toEqual([`${firstKind}/${first}`]);

               await h.closeDialog(how);
               expect(h.state()).toEqual({ dialogs: 0, name: first + "X", pageChanged: true, viewChanged: true });
               // and the edited identity is not reloaded from the server
               expect(h.gets).toEqual([`${firstKind}/${first}`]);
            }

            expect(h.gets).not.toContain(`${otherKind}/${other}`);
         });
      }
   }

   it("OK switches to the other identity, then its edits are tracked from scratch", async () => {
      const h = await setup();
      await h.clickRow("alice");
      await h.rename("X");
      await h.clickRow("bob");
      await h.closeDialog("ok");
      expect(h.state()).toEqual({ dialogs: 0, name: "bob", pageChanged: false, viewChanged: false });
      expect(h.gets).toEqual(["users/alice", "users/bob"]);

      // not dirty: a switch needs no confirmation
      await h.clickRow("alice");
      expect(h.state()).toEqual({ dialogs: 0, name: "alice", pageChanged: false, viewChanged: false });

      await h.rename("Y");
      await h.clickRow("bob");
      expect(h.state().dialogs).toBe(1);
      await h.closeDialog("cancel");
      await h.clickRow("bob");
      await h.closeDialog("cancel");
      expect(h.state()).toEqual({ dialogs: 0, name: "aliceY", pageChanged: true, viewChanged: true });
      expect(h.gets).toEqual(["users/alice", "users/bob", "users/alice"]);
   });

   it("OK after two cancelled switches loads the other identity and clears both flags", async () => {
      const h = await setup();
      await h.clickRow("alice");
      await h.rename("X");

      for(let i = 0; i < 2; i++) {
         await h.clickRow("bob");
         await h.closeDialog("cancel");
      }

      expect(h.gets).toEqual(["users/alice"]);
      await h.clickRow("bob");
      expect(h.state().dialogs).toBe(1);
      await h.closeDialog("ok");
      expect(h.state()).toEqual({ dialogs: 0, name: "bob", pageChanged: false, viewChanged: false });
      expect(h.gets).toEqual(["users/alice", "users/bob"]);

      // not dirty any more: the next switch needs no confirmation
      await h.clickRow("alice");
      expect(h.state()).toEqual({ dialogs: 0, name: "alice", pageChanged: false, viewChanged: false });
   });

   it("OK from a user to the group with the same name loads the group", async () => {
      const h = await setup();
      await h.clickRow("Sales", USERS);
      await h.rename("X");
      await h.clickRow("Sales", GROUPS);
      await h.closeDialog("ok");
      expect(h.state()).toEqual({ dialogs: 0, name: "Sales", pageChanged: false, viewChanged: false });
      expect(h.gets).toEqual(["users/Sales", "groups/Sales"]);
   });

   it("keeps the edits when the edited user is clicked again before a cancelled switch", async () => {
      const h = await setup();
      await h.clickRow("alice");
      await h.rename("X");
      await h.clickRow("alice");
      expect(h.state()).toEqual({ dialogs: 0, name: "aliceX", pageChanged: true, viewChanged: true });

      await h.clickRow("bob");
      expect(h.state().dialogs).toBe(1);
      expect(h.gets).toEqual(["users/alice"]);
      await h.closeDialog("cancel");
      expect(h.state()).toEqual({ dialogs: 0, name: "aliceX", pageChanged: true, viewChanged: true });
      expect(h.gets).toEqual(["users/alice"]);
   });

   it("keeps the edits after a ctrl-click away and back before a cancelled switch", async () => {
      const h = await setup();
      await h.clickRow("alice");
      await h.rename("X");
      await h.clickRow("bob", USERS, true);
      expect(h.page.selectedNodes.map(n => n.identityID.name)).toEqual(["alice", "bob"]);
      await h.clickRow("bob", USERS, true);
      expect(h.page.selectedNodes.map(n => n.identityID.name)).toEqual(["alice"]);

      await h.clickRow("bob");
      expect(h.state().dialogs).toBe(1);
      await h.closeDialog("cancel");
      expect(h.state()).toEqual({ dialogs: 0, name: "aliceX", pageChanged: true, viewChanged: true });
      expect(h.gets).toEqual(["users/alice"]);
   });

   it("keeps edits made after Apply over two cancelled switches", async () => {
      const h = await setup();
      await h.clickRow("alice");
      await h.rename("X");
      await h.apply();
      expect(h.posts).toEqual(["user:aliceX"]);
      expect(h.state()).toEqual({ dialogs: 0, name: "aliceX", pageChanged: false, viewChanged: false });
      const loaded = [...h.gets];

      await h.rename("Z");

      for(let i = 0; i < 2; i++) {
         await h.clickRow("bob");
         expect(h.state().dialogs).toBe(1);
         await h.closeDialog("cancel");
         expect(h.state()).toEqual({ dialogs: 0, name: "aliceXZ", pageChanged: true, viewChanged: true });
      }

      expect(h.gets).toEqual(loaded);
   });
});
