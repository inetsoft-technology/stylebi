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
 * UsersSettingsPageComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — clearIncompleteNewUser(): newUserIdentity must remain trackable when delete
 *                       fails (it.fails — confirmed bug)
 *   Group 2 [Risk 3] — selectionChanged(): incomplete new user guard must confirm before navigating
 *                       away and restore selection on cancel
 *   Group 3 [Risk 2] — selectionChanged(): pageChanged guard must show dialog and honour
 *                       confirm/cancel decisions
 *   Group 4 [Risk 2] — changeProvider(): must clear pending new user silently on provider switch
 *   Group 5 [Risk 2] — deleteIdentities(): must clear newUserIdentity when the deleted identity
 *                       matches the pending new user
 *   Group 6 [Risk 2] — setUser(): must show logout confirmation only when editing own credentials
 *   Group 7 [Risk 2] — selfRefreshing guard: onRefresh events triggered by the component itself
 *                       must be ignored to prevent recursive refresh loops
 *   Group 8 [Risk 3] — deleteIdentities(): selectedNodes cleared before getSameTypeNode() and
 *                       namedUsers check — both branches become unreachable dead code
 *                       (it.fails — confirmed bug)
 *   Group 9 [Risk 2] — selectionChanged(): navigatingAway=false for event.length>1 — pageChanged
 *                       and incomplete-new-user guards both silently bypassed on multi-select
 *                       (Design Gap)
 *
 * Confirmed bugs (it.fails — remove wrapper once fixed):
 *
 *   Bug A — newUserIdentity premature nullification (Group 1):
 *     clearIncompleteNewUser() sets this.newUserIdentity = null BEFORE issuing the HTTP DELETE.
 *     If the DELETE fails and the caller swallows the error ({error: () => {}}), the component
 *     transitions to hasIncompleteNewUser = false, while the server still holds the incomplete user.
 *     Result: the phantom user persists on the server with no way for the component to retry cleanup.
 *
 *   Bug B — selectedNodes premature clear in deleteIdentities() (Group 8):
 *     The subscribe callback opens with this.selectedNodes = [] as its FIRST statement.
 *     getSameTypeNode() then iterates this.selectedNodes (now []) and always returns null,
 *     so the isSysAdmin auto-select branch is never reached.
 *     The namedUsers snackBar reads this.selectedNodes.some() which is always false for the same reason.
 *     Result: (1) after deletion a sysAdmin never auto-selects the next same-type sibling node;
 *             (2) the namedUsers license-change warning never appears after deleteIdentities().
 *
 * KEY contracts:
 *   - clearIncompleteNewUser(false) must NOT trigger refreshTree.
 *   - selectionChanged "navigatingAway" fires only when event.length == 1 AND
 *     selectedNodes[0] !== event[0] (reference inequality).
 *   - deleteIdentities() always clears selectedNodes before processing the response.
 *   - setUser() calls postUserInfo(model, logout=true) only when
 *     model.organization == userOrgID AND (oldName != name OR password is set).
 *   - selfRefreshing is a synchronous guard: set true → call refresh() → set false, so any
 *     onRefresh event fired synchronously during that window sees selfRefreshing == true.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { of, Subject } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "../../../../../../../../mocks/server";
import { UsersSettingsPageComponent } from "./users-settings-page.component";
import { SecurityTreeRootModel } from "../users-settings-view/security-tree-root-model";
import { SecurityTreeNodeModel } from "../users-settings-view/security-tree-node-model";
import { SecurityTreeNode } from "../../security-tree-view/security-tree-node";
import { SecurityTreeService } from "../security-tree.service";
import { SecurityBusyService } from "../security-busy.service";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { IdentityId } from "../identity-id";
import { EditUserPaneModel } from "../edit-identity-pane/edit-identity-pane.model";

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

const makeTreeNodeModel = (
   name: string,
   type: IdentityType,
   children: SecurityTreeNodeModel[] = [],
): SecurityTreeNodeModel => ({
   identityID: { name, orgID: "TestOrg" },
   type,
   children,
   readOnly: false,
   organization: "TestOrg",
});

const DEFAULT_TREE_ROOT: SecurityTreeRootModel = {
   users: makeTreeNodeModel("Users", IdentityType.USERS),
   groups: makeTreeNodeModel("Groups", IdentityType.GROUPS),
   roles: makeTreeNodeModel("Roles", IdentityType.ROLES),
   organizations: makeTreeNodeModel("Organizations", IdentityType.ORGANIZATION),
   editable: true,
   isMultiTenant: false,
   namedUsers: false,
};

const makeUserNode = (name: string, orgID = "TestOrg"): SecurityTreeNode =>
   new SecurityTreeNode({ name, orgID }, IdentityType.USER);

// ─────────────────────────────────────────────────────────────────────────────
// Render helper
// ─────────────────────────────────────────────────────────────────────────────

interface RenderOpts {
   /** Simulates the provider returned by getProvider() on construction. Empty = skip initial load. */
   initialProvider?: string;
   dialogClosesWith?: unknown;
   isSysAdmin?: boolean;
   loginUserOrgName?: string;
   loginUserOrgID?: string;
}

async function renderComponent(opts: RenderOpts = {}) {
   const onRefreshSubject = new Subject<{ provider: string; providerChanged: boolean }>();

   const orgDropdownSpy = {
      onRefresh: onRefreshSubject,
      // Return "" so changeProvider early-returns and skips the constructor HTTP call.
      // Tests that need a loaded tree set comp.selectedProvider + comp.model directly.
      getProvider: jest.fn().mockReturnValue(opts.initialProvider ?? ""),
      authenticationProviders: ["TestProvider"],
      loginUserOrgName: opts.loginUserOrgName ?? "CurrentUser",
      loginUserOrgID: opts.loginUserOrgID ?? "CurrentOrgID",
      isSystemAdmin: jest.fn().mockReturnValue(opts.isSysAdmin ?? false),
      refresh: jest.fn().mockImplementation((provider: string, providerChanged: boolean) => {
         onRefreshSubject.next({ provider, providerChanged });
      }),
      refreshProviders: jest.fn(),
   };

   const dialogSpy = {
      open: jest.fn().mockReturnValue({
         afterClosed: () =>
            of(opts.dialogClosesWith !== undefined ? opts.dialogClosesWith : true),
      }),
   };

   const snackBarSpy = { open: jest.fn() };
   const usersServiceSpy = { loadScheduleUsers: jest.fn() };
   const orgBusySpy = { beginOrgSave: jest.fn(), endOrgSave: jest.fn() };
   const errorServiceSpy = { showSnackBar: jest.fn().mockReturnValue(of(null)) };
   const pageHeaderSpy = { title: "" };

   // Default MSW: tree refresh after ngOnInit and refreshTree / deleteIdentities
   server.use(
      http.get("*/api/em/navbar/organization", () => HttpResponse.json("TestOrg")),
      http.get("*/api/em/security/user/get-security-tree-root/*", () =>
         HttpResponse.json(DEFAULT_TREE_ROOT),
      ),
   );

   const result = await render(UsersSettingsPageComponent, {
      imports: [HttpClientModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         SecurityTreeService,
         { provide: MatDialog, useValue: dialogSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
         { provide: OrganizationDropdownService, useValue: orgDropdownSpy },
         { provide: ScheduleUsersService, useValue: usersServiceSpy },
         { provide: SecurityBusyService, useValue: orgBusySpy },
         { provide: ErrorHandlerService, useValue: errorServiceSpy },
         { provide: PageHeaderService, useValue: pageHeaderSpy },
      ],
   });

   const comp = result.fixture.componentInstance as UsersSettingsPageComponent;
   // Provide a baseline model so methods that read this.model don't throw.
   comp.model = { ...DEFAULT_TREE_ROOT };
   comp.selectedProvider = "TestProvider";

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return { ...result, comp, dialogSpy, snackBarSpy, orgDropdownSpy, usersServiceSpy, errorServiceSpy, onRefreshSubject };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — clearIncompleteNewUser(): state consistency on failure
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — clearIncompleteNewUser(): state consistency on failure", () => {

   // 🔁 Regression-sensitive: newUserIdentity is pre-cleared before the HTTP call.
   // Risk Point/Contract: On delete failure, hasIncompleteNewUser must remain true so the
   // caller can retry; instead it becomes false — the phantom user is now untrackable.
   it.failing("should keep hasIncompleteNewUser true when HTTP delete fails", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            new HttpResponse(null, { status: 500 }),
         ),
      );

      const { comp } = await renderComponent();
      comp.newUserIdentity = { name: "pending-user", orgID: "TestOrg" };

      // Swallow errors the same way callers in the component do
      comp.clearIncompleteNewUser(false).subscribe({ error: () => {} });

      await waitFor(() => {
         // BUG: this assertion fails because newUserIdentity was nulled before the request
         expect(comp.hasIncompleteNewUser).toBe(true);
      });
   });

   // 🔁 Regression-sensitive: successful delete must null out the tracked identity.
   it("should clear newUserIdentity when delete succeeds", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
      );

      const { comp } = await renderComponent();
      comp.newUserIdentity = { name: "pending-user", orgID: "TestOrg" };

      let completed = false;
      comp.clearIncompleteNewUser(false).subscribe({ complete: () => (completed = true) });

      await waitFor(() => {
         expect(completed).toBe(true);
         expect(comp.hasIncompleteNewUser).toBe(false);
      });
   });

   // No identity present → must return immediately without any HTTP call.
   it("should return of(undefined) without an HTTP call when no pending identity exists", async () => {
      const postSpy = jest.fn();
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () => {
            postSpy();
            return HttpResponse.json({ warnings: [] });
         }),
      );

      const { comp } = await renderComponent();
      expect(comp.newUserIdentity).toBeNull();

      let completed = false;
      comp.clearIncompleteNewUser(false).subscribe({ complete: () => (completed = true) });

      await waitFor(() => expect(completed).toBe(true));
      expect(postSpy).not.toHaveBeenCalled();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 3] — selectionChanged(): incomplete new user navigation guard
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — selectionChanged(): incomplete new user guard", () => {

   // 🔁 Regression-sensitive: confirm clears the pending new user and updates the selection.
   // If clearIncompleteNewUser is not called on confirm, the phantom user lingers.
   it("should show dialog, clear pending user, and update selection when user confirms", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
      );

      const { comp, dialogSpy } = await renderComponent({ dialogClosesWith: true });

      const pendingId: IdentityId = { name: "new-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const currentNode = makeUserNode("new-user");
      currentNode.identityID = pendingId;
      comp.selectedNodes = [currentNode];

      const nextNode = makeUserNode("other-user");

      comp.selectionChanged([nextNode]);

      expect(dialogSpy.open).toHaveBeenCalledTimes(1);

      await waitFor(() => {
         expect(comp.newUserIdentity).toBeNull();
         expect(comp.selectedNodes).toEqual([nextNode]);
         expect(comp.pageChanged).toBe(false);
      });
   });

   // 🔁 Regression-sensitive: cancel must restore selectedNodes via splice(0) to force the
   // tree view to re-render its selection — a plain assignment would not trigger change detection.
   it("should restore selection and leave newUserIdentity intact when user cancels", async () => {
      const { comp, dialogSpy } = await renderComponent({ dialogClosesWith: false });

      const pendingId: IdentityId = { name: "new-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const currentNode = makeUserNode("new-user");
      currentNode.identityID = pendingId;
      comp.selectedNodes = [currentNode];

      const nextNode = makeUserNode("other-user");
      comp.selectionChanged([nextNode]);

      // Dialog opens but user cancels
      expect(dialogSpy.open).toHaveBeenCalledTimes(1);

      await waitFor(() => {
         expect(comp.newUserIdentity).toBe(pendingId); // not cleared
         // selectedNodes is reassigned via splice(0) — still contains the original node
         expect(comp.selectedNodes.length).toBe(1);
         expect(comp.selectedNodes[0]).toBe(currentNode);
      });
   });

   // When navigating to the same node, "navigatingAway" is false → no dialog at all.
   it("should not open dialog when the same node is re-selected", async () => {
      const { comp, dialogSpy } = await renderComponent();

      const pendingId: IdentityId = { name: "new-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const currentNode = makeUserNode("new-user");
      currentNode.identityID = pendingId;
      comp.selectedNodes = [currentNode];

      // Same node reference passed in event
      comp.selectionChanged([currentNode]);

      expect(dialogSpy.open).not.toHaveBeenCalled();
   });

   // newUserIdentity set, but selected node is a DIFFERENT user → no incomplete-user dialog.
   // Risk Point: isNewUserSelected check uses both name AND orgID — must not false-positive.
   it("should not open incomplete-user dialog when selection does not match newUserIdentity", async () => {
      const { comp, dialogSpy } = await renderComponent({ dialogClosesWith: false });

      comp.newUserIdentity = { name: "new-user", orgID: "TestOrg" };
      const differentCurrentNode = makeUserNode("completely-different-user");
      comp.selectedNodes = [differentCurrentNode];
      comp.pageChanged = false;

      const nextNode = makeUserNode("another-user");
      comp.selectionChanged([nextNode]);

      // Falls straight through — no dialog for either guard
      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(comp.selectedNodes).toEqual([nextNode]);
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — selectionChanged(): pageChanged navigation guard
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — selectionChanged(): pageChanged guard", () => {

   // 🔁 Regression-sensitive: pageChanged must be reset to false only on confirm.
   it("should update selection and reset pageChanged when user confirms", async () => {
      const { comp, dialogSpy } = await renderComponent({ dialogClosesWith: true });

      comp.pageChanged = true;
      const currentNode = makeUserNode("user-a");
      comp.selectedNodes = [currentNode];

      const nextNode = makeUserNode("user-b");
      comp.selectionChanged([nextNode]);

      expect(dialogSpy.open).toHaveBeenCalledTimes(1);

      await waitFor(() => {
         expect(comp.pageChanged).toBe(false);
         expect(comp.selectedNodes).toEqual([nextNode]);
      });
   });

   // Cancel must leave pageChanged=true and selection unchanged.
   it("should keep pageChanged and restore selection when user cancels", async () => {
      const { comp, dialogSpy } = await renderComponent({ dialogClosesWith: false });

      comp.pageChanged = true;
      const currentNode = makeUserNode("user-a");
      comp.selectedNodes = [currentNode];

      const nextNode = makeUserNode("user-b");
      comp.selectionChanged([nextNode]);

      expect(dialogSpy.open).toHaveBeenCalledTimes(1);

      await waitFor(() => {
         expect(comp.pageChanged).toBe(true);
         expect(comp.selectedNodes[0]).toBe(currentNode);
      });
   });

   // pageChanged=false → dialog must never open; selection updates immediately.
   it("should update selection directly without a dialog when pageChanged is false", async () => {
      const { comp, dialogSpy } = await renderComponent();

      comp.pageChanged = false;
      const currentNode = makeUserNode("user-a");
      comp.selectedNodes = [currentNode];

      const nextNode = makeUserNode("user-b");
      comp.selectionChanged([nextNode]);

      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(comp.selectedNodes).toEqual([nextNode]);
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — changeProvider(): clears pending new user silently
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — changeProvider(): clears pending new user", () => {

   // 🔁 Regression-sensitive: switching provider while a new user is pending must clear it
   // without prompting — the user explicitly chose to switch providers.
   it("should call clearIncompleteNewUser when a provider switch has a pending new user", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp } = await renderComponent();
      comp.newUserIdentity = { name: "pending-user", orgID: "TestOrg" };

      comp.changeProvider("AnotherProvider", true, false);

      // After provider change, pending user must be cleared
      await waitFor(() => expect(comp.newUserIdentity).toBeNull());
   });

   // Empty/falsy provider → guard returns early, pending user is NOT cleared.
   it("should return early without clearing newUserIdentity when provider is falsy", async () => {
      const { comp } = await renderComponent();
      const pendingId: IdentityId = { name: "pending-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      comp.changeProvider("", true, false);

      expect(comp.newUserIdentity).toBe(pendingId);
   });

   // No pending user → clearIncompleteNewUser must not trigger an HTTP call.
   it("should not issue a delete HTTP call when there is no pending new user", async () => {
      server.use(
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const deleteSpy = jest.fn();
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () => {
            deleteSpy();
            return HttpResponse.json({ warnings: [] });
         }),
      );

      const { comp } = await renderComponent();
      expect(comp.newUserIdentity).toBeNull();

      comp.changeProvider("AnotherProvider", true, false);

      // Allow any pending microtasks to settle
      await new Promise(r => setTimeout(r, 0));
      expect(deleteSpy).not.toHaveBeenCalled();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — deleteIdentities(): newUserIdentity synchronisation
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — deleteIdentities(): newUserIdentity sync", () => {

   // 🔁 Regression-sensitive: deleting the pending-new-user node must clear newUserIdentity
   // so the guard no longer treats the deleted user as needing cleanup.
   it("should clear newUserIdentity when the matching identity is among those deleted", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp } = await renderComponent({ isSysAdmin: true });

      const pendingId: IdentityId = { name: "pending-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const nodeToDelete = makeUserNode("pending-user");
      nodeToDelete.identityID = pendingId;
      comp.selectedNodes = [nodeToDelete];

      comp.deleteIdentities();

      await waitFor(() => expect(comp.newUserIdentity).toBeNull());
   });

   // Deleting a DIFFERENT user must not touch newUserIdentity.
   it("should NOT clear newUserIdentity when the deleted identity does not match", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp } = await renderComponent();

      const pendingId: IdentityId = { name: "pending-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const otherNode = makeUserNode("completely-different-user");
      comp.selectedNodes = [otherNode];

      comp.deleteIdentities();

      await waitFor(() => expect(comp.selectedNodes.length).toBe(0)); // post-delete clear
      expect(comp.newUserIdentity).toBe(pendingId); // still set
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 6 [Risk 2] — setUser(): logout confirmation for own credentials
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — setUser(): logout dialog for own-credential changes", () => {

   const makeEditUserModel = (overrides: Partial<EditUserPaneModel> = {}): EditUserPaneModel => ({
      name: "CurrentUser",
      oldName: "CurrentUser",
      organization: "CurrentOrgID",
      root: false,
      identityNames: [],
      members: [],
      roles: [],
      permittedIdentities: [],
      editable: true,
      status: true,
      currentUser: true,
      localesList: [],
      ...overrides,
   });

   // 🔁 Regression-sensitive: editing own username must always show the logout dialog to warn
   // the user they will be logged out. Skipping the dialog would silently log them out.
   it("should open logout confirmation dialog when user renames their own account", async () => {
      server.use(
         http.post("*/api/em/security/users/edit-user/*", () =>
            HttpResponse.json(null),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp, dialogSpy } = await renderComponent({
         loginUserOrgName: "CurrentUser",
         loginUserOrgID: "CurrentOrgID",
         dialogClosesWith: false, // user cancels → no logout
      });

      const model = makeEditUserModel({ name: "NewName", oldName: "CurrentUser" });
      comp.setUser(model);

      expect(dialogSpy.open).toHaveBeenCalledTimes(1);
   });

   // Editing own account but with a password change (not a rename) must also show the dialog.
   it("should open logout confirmation dialog when user changes their own password", async () => {
      const { comp, dialogSpy } = await renderComponent({
         loginUserOrgName: "CurrentUser",
         loginUserOrgID: "CurrentOrgID",
         dialogClosesWith: false,
      });

      const model = makeEditUserModel({ password: "newPass123" });
      comp.setUser(model);

      expect(dialogSpy.open).toHaveBeenCalledTimes(1);
   });

   // Editing a DIFFERENT user → no dialog, direct HTTP post.
   it("should not open a dialog when editing another user's account", async () => {
      server.use(
         http.post("*/api/em/security/users/edit-user/*", () =>
            HttpResponse.json(null),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp, dialogSpy } = await renderComponent({
         loginUserOrgName: "CurrentUser",
         loginUserOrgID: "CurrentOrgID",
      });

      const model = makeEditUserModel({
         name: "OtherUser",
         oldName: "OtherUser",
         organization: "OtherOrg",
         password: undefined,
      });
      comp.setUser(model);

      expect(dialogSpy.open).not.toHaveBeenCalled();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 7 [Risk 2] — selfRefreshing guard: ignores own-triggered onRefresh
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — selfRefreshing guard: ignores self-triggered refresh", () => {

   // 🔁 Regression-sensitive: refreshTree(selectProvider=true) calls orgDropdownService.refresh(),
   // which fires onRefresh synchronously. The selfRefreshing flag must suppress the re-entrant
   // call; without it, refreshTree() would recurse indefinitely.
   it("should not change selectedProvider when onRefresh fires during selfRefreshing=true window", async () => {
      server.use(
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp, orgDropdownSpy, onRefreshSubject } = await renderComponent();

      comp.selectedProvider = "OriginalProvider";

      // Simulate the scenario: refreshTree calls orgDropdownService.refresh() while
      // selfRefreshing is true. The mock implementation fires onRefreshSubject.next().
      // The constructor subscription should see selfRefreshing=true and return early.
      (comp as any).selfRefreshing = true;
      onRefreshSubject.next({ provider: "IncomingProvider", providerChanged: true });
      (comp as any).selfRefreshing = false;

      // No provider change should have occurred
      expect(comp.selectedProvider).toBe("OriginalProvider");
   });

   // External onRefresh (selfRefreshing=false) must update the provider normally.
   it("should update selectedProvider when an external onRefresh fires", async () => {
      server.use(
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp, onRefreshSubject } = await renderComponent();
      comp.selectedProvider = "OldProvider";

      onRefreshSubject.next({ provider: "NewProvider", providerChanged: false });

      await waitFor(() => expect(comp.selectedProvider).toBe("NewProvider"));
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 8 [Risk 3] — deleteIdentities(): dead auto-select and dead namedUsers warning
//   (it.fails — confirmed bug: selectedNodes cleared before getSameTypeNode() is called)
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — deleteIdentities(): dead auto-select and namedUsers warning", () => {

   // 🔁 Regression-sensitive: when isSysAdmin=true and a sibling of the same type exists in
   // the model, refreshTree must be called with that sibling's identityID so the tree
   // auto-selects it.  The bug: selectedNodes=[] before getSameTypeNode() → sameTypeNode=null
   // → refreshTree(null, null, false, false) is called instead.
   it.failing("should call refreshTree with the next same-type node ID when isSysAdmin is true", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
      );

      const { comp } = await renderComponent({ isSysAdmin: true });

      const nextUserModel = makeTreeNodeModel("next-user", IdentityType.USER);
      comp.model = {
         ...DEFAULT_TREE_ROOT,
         users: makeTreeNodeModel("Users", IdentityType.USERS, [nextUserModel]),
      };

      comp.selectedNodes = [makeUserNode("deleted-user")];

      const refreshTreeSpy = jest.spyOn(comp as any, "refreshTree");

      comp.deleteIdentities();

      await waitFor(() => expect(refreshTreeSpy).toHaveBeenCalled());

      // BUG: called with (null, null, false, false) because selectedNodes was [] when
      // getSameTypeNode() ran → sameTypeNode is always null
      const lastCall = refreshTreeSpy.mock.calls[refreshTreeSpy.mock.calls.length - 1];
      expect(lastCall[0]).toEqual(nextUserModel.identityID);
      expect(lastCall[1]).toBe(IdentityType.USER);
   });

   // 🔁 Regression-sensitive: when namedUsers=true and a USER is among the deleted identities,
   // the license-change snackBar must appear.  The bug: selectedNodes=[] before the
   // this.selectedNodes.some() check → .some() is always false → snackBar never opens.
   it.failing("should show namedUsers snackBar when a USER is deleted and namedUsers is true", async () => {
      server.use(
         http.post("*/api/em/security/user/delete-identities/*", () =>
            HttpResponse.json({ warnings: [] }),
         ),
         http.get("*/api/em/security/user/get-security-tree-root/*", () =>
            HttpResponse.json(DEFAULT_TREE_ROOT),
         ),
      );

      const { comp, snackBarSpy } = await renderComponent();

      comp.model = { ...DEFAULT_TREE_ROOT, namedUsers: true };
      comp.selectedNodes = [makeUserNode("some-user")];

      comp.deleteIdentities();

      // Wait for the HTTP response to be processed (selectedNodes cleared is the observable side-effect)
      await waitFor(() => expect(comp.selectedNodes.length).toBe(0));

      // BUG: snackBar.open is never reached because this.selectedNodes.some() is evaluated
      // after selectedNodes has already been set to []
      expect(snackBarSpy.open).toHaveBeenCalled();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 9 [Risk 2] — selectionChanged(): multi-select bypasses guards
//   (Design Gap: navigatingAway = false when event.length > 1)
// ════════════════════════════════════════════════════════════════════════════

describe("UsersSettingsPageComponent — selectionChanged(): multi-select bypasses guards (Design Gap)", () => {

   // Design Gap: navigatingAway requires event.length == 1. When the user selects two or more
   // nodes simultaneously while pageChanged=true, the unsaved-change dialog never fires and
   // the unsaved edits are silently discarded.
   // This test documents the current behavior as a specification target.
   it("should skip the pageChanged dialog and silently accept a multi-select", async () => {
      const { comp, dialogSpy } = await renderComponent();

      comp.pageChanged = true;
      comp.selectedNodes = [makeUserNode("user-a")];

      const node1 = makeUserNode("user-b");
      const node2 = makeUserNode("user-c");

      comp.selectionChanged([node1, node2]);

      // Design Gap: guard does not fire for multi-select
      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(comp.selectedNodes).toEqual([node1, node2]);
   });

   // Design Gap: same navigatingAway condition means the incomplete-new-user guard is also
   // bypassed on multi-select. The pending user is never cleared and no dialog appears.
   it("should skip the incomplete-new-user dialog and silently accept a multi-select", async () => {
      const { comp, dialogSpy } = await renderComponent();

      const pendingId: IdentityId = { name: "new-user", orgID: "TestOrg" };
      comp.newUserIdentity = pendingId;

      const currentNode = makeUserNode("new-user");
      currentNode.identityID = pendingId;
      comp.selectedNodes = [currentNode];

      const node1 = makeUserNode("user-b");
      const node2 = makeUserNode("user-c");

      comp.selectionChanged([node1, node2]);

      // Design Gap: incomplete-user dialog does not fire for multi-select
      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(comp.newUserIdentity).toBe(pendingId); // pending user still tracked but not cleaned up
      expect(comp.selectedNodes).toEqual([node1, node2]);
   });
});
