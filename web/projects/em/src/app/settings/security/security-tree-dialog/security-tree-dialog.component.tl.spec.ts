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
 * SecurityTreeDialogComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - getTreeData(): tenant mode and enabled flags choose exactly which
 *                      identity roots are selectable.
 *   Group 2 [Risk 3] - getTreeData(): read-only wrapper nodes are removed while same-type
 *                      descendants are promoted.
 *   Group 3 [Risk 2] - selection/add/cancel: root-node validity and addPermission output
 *                      must match the dialog contract.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - resource-tree request forwards provider, hideOrgAdminRole, and isTimeRange query params.
 *   - A root node is selectable only when it is the USER root; GROUP/ROLE/ORGANIZATION roots
 *     remain invalid as direct selections.
 *   - Selecting the USER root expands to its children and excludes the USER root itself.
 */

import { CommonModule } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { firstValueFrom } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "../../../../../../../mocks/server";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { SecurityTreeNode } from "../security-tree-view/security-tree-node";
import { SecurityTreeService } from "../users/security-tree.service";
import { SecurityTreeRootModel } from "../users/users-settings-view/security-tree-root-model";
import { SecurityTreeNodeModel } from "../users/users-settings-view/security-tree-node-model";
import { SecurityTreeDialogData } from "./security-tree-dialog-data";
import { SecurityTreeDialogComponent } from "./security-tree-dialog.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeNodeModel(
   name: string,
   type: IdentityType,
   children: SecurityTreeNodeModel[] = [],
   overrides: Partial<SecurityTreeNodeModel> = {},
): SecurityTreeNodeModel {
   return {
      identityID: { name, orgID: overrides.organization ?? "OrgA" },
      type,
      children,
      root: false,
      readOnly: false,
      organization: "OrgA",
      ...overrides,
   };
}

function makeTreeNode(
   name: string,
   type: IdentityType,
   children: SecurityTreeNode[] = [],
   root = false,
): SecurityTreeNode {
   return new SecurityTreeNode({ name, orgID: "OrgA" }, type, children, false, root);
}

function makeRootModel(overrides: Partial<SecurityTreeRootModel> = {}): SecurityTreeRootModel {
   const users = makeNodeModel("Users", IdentityType.USERS, [
      makeNodeModel("alice", IdentityType.USER),
   ]);
   const groups = makeNodeModel("Groups", IdentityType.GROUPS, [
      makeNodeModel("analysts", IdentityType.GROUP),
   ]);
   const roles = makeNodeModel("Roles", IdentityType.ROLES, [
      makeNodeModel("viewer", IdentityType.ROLE),
   ]);
   const organizations = makeNodeModel("Organizations", IdentityType.ORGANIZATION, [
      makeNodeModel("Users", IdentityType.USERS, [makeNodeModel("org-user", IdentityType.USER)]),
      makeNodeModel("Groups", IdentityType.GROUPS, [makeNodeModel("org-group", IdentityType.GROUP)]),
      makeNodeModel("Organization Roles", IdentityType.ROLES, [makeNodeModel("org-role", IdentityType.ROLE)]),
   ]);

   return {
      users,
      groups,
      roles,
      organizations,
      editable: true,
      isMultiTenant: false,
      namedUsers: true,
      ...overrides,
   };
}

function rootNames(nodes: SecurityTreeNode[]): string[] {
   return nodes.map(node => node.identityID.name);
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   dialogData?: SecurityTreeDialogData;
   rootModel?: SecurityTreeRootModel;
   captureParams?: (params: Record<string, string>) => void;
}

async function renderComponent(opts: RenderOpts = {}) {
   const dialogRefSpy = { close: jest.fn() };
   const dialogData = opts.dialogData ?? {
      dialogTitle: "Add identity",
      usersEnabled: true,
      groupsEnabled: true,
      rolesEnabled: true,
      organizationsEnabled: false,
   };

   server.use(
      http.get("*/api/em/settings/content/resource-tree", ({ request }) => {
         const url = new URL(request.url);
         opts.captureParams?.(Object.fromEntries(url.searchParams.entries()));
         return HttpResponse.json(opts.rootModel ?? makeRootModel());
      },
      ),
   );

   const result = await render(SecurityTreeDialogComponent, {
      imports: [CommonModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         SecurityTreeService,
         { provide: MatDialogRef, useValue: dialogRefSpy },
         { provide: MAT_DIALOG_DATA, useValue: dialogData },
      ],
   });

   return {
      ...result,
      comp: result.fixture.componentInstance as SecurityTreeDialogComponent,
      dialogRefSpy,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - getTreeData(): tenant roots and query params
// ---------------------------------------------------------------------------

describe("SecurityTreeDialogComponent - getTreeData(): tenant roots and query params", () => {

   // Regression-sensitive: enabled flags are the only barrier preventing users from selecting
   // an identity type that the parent table cannot accept.
   it("should include only enabled roots in non-multi-tenant mode", async () => {
      const { comp } = await renderComponent({
         dialogData: {
            dialogTitle: "Add identity",
            usersEnabled: true,
            groupsEnabled: false,
            rolesEnabled: true,
            organizationsEnabled: false,
         },
      });

      const nodes = await firstValueFrom(comp.treeData);

      expect(rootNames(nodes)).toEqual(["Users", "Roles"]);
   });

   // Regression-sensitive: in multi-tenant org mode, disabled child folders must be removed
   // from the organization root or users can select identity classes the parent disabled.
   it("should filter organization children by enabled flags in multi-tenant organization mode", async () => {
      const { comp } = await renderComponent({
         rootModel: makeRootModel({ isMultiTenant: true }),
         dialogData: {
            dialogTitle: "Add identity",
            usersEnabled: false,
            groupsEnabled: true,
            rolesEnabled: false,
            organizationsEnabled: true,
         },
      });

      const nodes = await firstValueFrom(comp.treeData);

      expect(rootNames(nodes)).toEqual(["Organizations"]);
      expect(nodes[0].children.map(child => child.identityID.name)).toEqual(["Groups"]);
   });

   // Regression-sensitive: when organizations are disabled, the dialog must split the
   // Organizations folder into user/group/org-role roots and append global roles separately.
   it("should split organization children and append global roles when organizations are disabled", async () => {
      const { comp } = await renderComponent({
         rootModel: makeRootModel({ isMultiTenant: true }),
         dialogData: {
            dialogTitle: "Add identity",
            usersEnabled: true,
            groupsEnabled: true,
            rolesEnabled: true,
            organizationsEnabled: false,
         },
      });

      const nodes = await firstValueFrom(comp.treeData);

      expect(rootNames(nodes)).toEqual(["Users", "Groups", "Organization Roles", "Roles"]);
   });

   // Regression-sensitive: provider/time-range flags scope the backend tree. Missing params
   // make the dialog show identities from the wrong provider or permission mode.
   it("should forward provider, hideOrgAdminRole, and isTimeRange query params", async () => {
      let capturedParams: Record<string, string> = {};

      const { comp } = await renderComponent({
         dialogData: {
            dialogTitle: "Add identity",
            provider: "ProviderA",
            hideOrgAdminRole: true,
            isTimeRange: true,
            usersEnabled: true,
         },
         captureParams: params => capturedParams = params,
      });

      await firstValueFrom(comp.treeData);

      expect(capturedParams).toEqual({
         provider: "ProviderA",
         hideOrgAdminRole: "true",
         isTimeRange: "true",
      });
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - getTreeData(): read-only child filtering
// ---------------------------------------------------------------------------

describe("SecurityTreeDialogComponent - getTreeData(): read-only child filtering", () => {

   // Regression-sensitive: read-only wrapper nodes should not be selectable, but their concrete
   // same-type descendants must remain selectable after promotion.
   it("should remove read-only wrapper nodes and promote same-type descendants", async () => {
      const promoted = makeNodeModel("promoted-user", IdentityType.USER);
      const readOnlyWrapper = makeNodeModel("readonly-wrapper", IdentityType.USER, [promoted], {
         readOnly: true,
      });
      const users = makeNodeModel("Users", IdentityType.USERS, [readOnlyWrapper]);

      const { comp } = await renderComponent({
         rootModel: makeRootModel({ users }),
         dialogData: {
            dialogTitle: "Add identity",
            usersEnabled: true,
            groupsEnabled: false,
            rolesEnabled: false,
         },
      });

      const nodes = await firstValueFrom(comp.treeData);

      expect(nodes[0].children.map(child => child.identityID.name)).toEqual(["promoted-user"]);
      expect(nodes[0].children.some(child => child.readOnly)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - selection/add/cancel
// ---------------------------------------------------------------------------

describe("SecurityTreeDialogComponent - selection validity and close payloads", () => {

   // Regression-sensitive: root folders are structural except the USER root, which means
   // "all users" and expands later in addPermission().
   it("should mark selections valid only for non-root identities or the USER root", async () => {
      const { comp } = await renderComponent();

      comp.onTreeSelectionChange([makeTreeNode("Groups", IdentityType.GROUP, [], true)]);
      expect(comp.validTreeSelection).toBe(false);

      comp.onTreeSelectionChange([makeTreeNode("Users", IdentityType.USER, [], true)]);
      expect(comp.validTreeSelection).toBe(true);

      comp.onTreeSelectionChange([makeTreeNode("viewer", IdentityType.ROLE, [], false)]);
      expect(comp.validTreeSelection).toBe(true);
   });

   // Regression-sensitive: selecting the USER root is a bulk-add shortcut. The close payload
   // must contain the children and not the root itself.
   it("should expand a selected USER root to its children and preserve non-user selections", async () => {
      const user1 = makeTreeNode("alice", IdentityType.USER);
      const user2 = makeTreeNode("bob", IdentityType.USER);
      const userRoot = makeTreeNode("Users", IdentityType.USER, [user1, user2], true);
      const role = makeTreeNode("viewer", IdentityType.ROLE);
      const { comp, dialogRefSpy } = await renderComponent();
      const tree = {
         sendSelection: jest.fn().mockReturnValue([userRoot, user1, role]),
      };

      comp.addPermission(tree as any);

      expect(dialogRefSpy.close).toHaveBeenCalledWith([user1, user2, role]);
   });

   // Regression-sensitive: Cancel must close with null, not an empty array, so parent code can
   // distinguish "no user decision" from an explicit empty selection.
   it("should close with null on cancel", async () => {
      const { comp, dialogRefSpy } = await renderComponent();

      comp.cancel();

      expect(dialogRefSpy.close).toHaveBeenCalledWith(null);
   });
});
