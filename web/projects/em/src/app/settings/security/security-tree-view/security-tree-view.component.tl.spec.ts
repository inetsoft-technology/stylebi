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
 * SecurityTreeViewComponent - Testing Library style
 *
 * Regression-focused coverage:
 *   Group 1 [Risk 3] - selectNode() Ctrl+click: deselection must remain correct after
 *                       filter() rebuilds the visible node objects.
 *   Group 2 [Risk 3] - selectNode() Shift+click: the component must handle an anchor that
 *                       is no longer present in flattenTree.
 *   Group 3 [Risk 2] - dragStart(): folder-name checks must exclude all root folders from
 *                       the drag payload.
 *
 * Key contracts:
 *   - isSelected() uses value equality (identityID.name + type); Ctrl+click deselect must
 *     also use value equality to stay consistent with the visual selection state.
 *   - Shift+click with lastIndex = -1 must be treated as "no anchor" with a graceful
 *     fallback instead of crashing.
 *   - All five root folder names ["Users", "Groups", "Roles", "Organizations",
 *     "Organization Roles"] must be excluded from the drag payload by isIdentityFolder.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { it } from "@jest/globals";
import { render } from "@testing-library/angular";
import { SecurityTreeViewComponent } from "./security-tree-view.component";
import { FlatSecurityTreeNode, SecurityTreeNode } from "./security-tree-node";
import { IdentityType } from "../../../../../../shared/data/identity-type";

// -----------------------------------------------------------------------------
// Fixtures
// -----------------------------------------------------------------------------

function makeNode(
   name: string,
   type: IdentityType,
   children: SecurityTreeNode[] = [],
   readOnly = false,
): SecurityTreeNode {
   return new SecurityTreeNode({ name, orgID: "TestOrg" }, type, children, readOnly);
}

function clickEvent(overrides: Partial<MouseEvent> = {}): MouseEvent {
   return { ctrlKey: false, metaKey: false, shiftKey: false, ...overrides } as MouseEvent;
}

function makeDragEvent() {
   const dataTransfer = { setData: jest.fn(), getData: jest.fn() };
   return {
      dataTransfer,
      preventDefault: jest.fn(),
   } as unknown as DragEvent & { dataTransfer: typeof dataTransfer };
}

// -----------------------------------------------------------------------------
// Render helper
// -----------------------------------------------------------------------------

async function renderComponent(treeData: SecurityTreeNode[]) {
   // Do not pass treeData as a componentProperty: Angular calls ngOnChanges before
   // ngOnInit, so flattenedDataChanged fires before the subscription is set up.
   // That leaves expandedState undefined and can crash restoreExpandedState().
   // Set treeData through setInput() after ngOnInit instead.
   const result = await render(SecurityTreeViewComponent, {
      imports: [FormsModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: { selectedNodes: [] },
   });

   result.fixture.componentRef.setInput("treeData", treeData);
   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance as SecurityTreeViewComponent,
   };
}

/** Read the private flattenTree field. */
function flatTree(comp: SecurityTreeViewComponent): FlatSecurityTreeNode[] {
   return (comp as any)["flattenTree"] ?? [];
}

/** Apply a search filter synchronously and run change detection. */
function applyFilter(comp: SecurityTreeViewComponent, text: string, fixture: any): void {
   comp.filter(text);
   fixture.detectChanges();
}

// =============================================================================
// Group 1 [Risk 3] - selectNode() Ctrl+click: deselection after filter
// =============================================================================

describe("SecurityTreeViewComponent - selectNode() Ctrl+click: deselection after filter", () => {
   // After filtering, SecurityTreeNode.filter() rebuilds matching nodes.
   // Ctrl+click deselect must therefore remain stable even when references change.
   it("should deselect a selected node when Ctrl+clicked after the tree is filtered", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent()); // single-click -> selectedNodes = [alice]
      expect(comp.selectedNodes).toHaveLength(1);

      applyFilter(comp, "alice", fixture); // SecurityTreeNode.filter() creates new references

      const filteredAliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      // isSelected uses value equality, so the filtered node should still be recognized.
      expect(comp.isSelected(filteredAliceFlat)).toBe(true);

      comp.selectNode(filteredAliceFlat, clickEvent({ ctrlKey: true })); // should deselect
      expect(comp.selectedNodes).toHaveLength(0);
   });

   // Baseline: Ctrl+click must still add a node after filtering when it is not selected yet.
   it("should add a node on Ctrl+click when it is not yet selected (after filter)", async () => {
      const bob = makeNode("bob", IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [bob]),
      ]);

      applyFilter(comp, "bob", fixture);

      const bobFlat = flatTree(comp).find(n => n.getData().identityID.name === "bob");
      comp.selectNode(bobFlat, clickEvent({ ctrlKey: true }));

      expect(comp.selectedNodes.some(n => n.identityID.name === "bob")).toBe(true);
   });

   // selectionChanged must reflect the actual post-click selection state after filtering.
   it("selectionChanged must emit [] after a Ctrl+click deselect following a filter", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent()); // select

      applyFilter(comp, "alice", fixture);

      const emitted: SecurityTreeNode[][] = [];
      comp.selectionChanged.subscribe(v => emitted.push(v as SecurityTreeNode[]));

      const filteredAliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(filteredAliceFlat, clickEvent({ ctrlKey: true })); // should deselect

      expect(emitted[0]).toHaveLength(0);
   });
});

// =============================================================================
// Group 2 [Risk 3] - selectNode() Shift+click: crash when anchor filtered out
// =============================================================================

describe("SecurityTreeViewComponent - selectNode() Shift+click: crash when anchor is filtered out", () => {
   // Regression-sensitive workflow: shift-select a node, then narrow the list via search.
   // If lastIndex becomes -1, the component must fall back gracefully on the next shift-click.
   // Issue #74592 fixed: this check is now a normal regression case.
   it("should not throw when Shift+clicking after the anchor node is filtered out", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent()); // alice becomes the shift-click anchor

      applyFilter(comp, "bob", fixture); // alice is removed from flattenTree

      const bobFlat = flatTree(comp).find(n => n.getData().identityID.name === "bob");
      // lastIndex = -1 means the original anchor is no longer in the filtered tree.
      expect(() => comp.selectNode(bobFlat, clickEvent({ shiftKey: true }))).not.toThrow();
      expect(comp.selectedNodes.map(n => n.identityID.name)).toEqual(["bob"]);
   });

   // Regression-sensitive: the visible range-selection path must still work after adding
   // a guard for a missing anchor.
   it("should select all nodes between anchor and clicked node when both are visible", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const carol = makeNode("carol", IdentityType.USER);
      const { comp } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob, carol]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      const carolFlat = flatTree(comp).find(n => n.getData().identityID.name === "carol");

      comp.selectNode(aliceFlat, clickEvent());                    // anchor = alice
      comp.selectNode(carolFlat, clickEvent({ shiftKey: true })); // range to carol

      const names = comp.selectedNodes.map(n => n.identityID.name);
      expect(names).toContain("alice");
      expect(names).toContain("bob");
      expect(names).toContain("carol");
   });

   // Boundary: shift-clicking the anchor again must keep exactly one selected node.
   it("should keep exactly one node when Shift+clicking the same node as the anchor", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const { comp } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent());                    // anchor
      comp.selectNode(aliceFlat, clickEvent({ shiftKey: true })); // re-click same node

      expect(comp.selectedNodes).toHaveLength(1);
      expect(comp.selectedNodes[0].identityID.name).toBe("alice");
   });
});

// =============================================================================
// Group 3 [Risk 2] - dragStart(): isIdentityFolder folder-name completeness
// =============================================================================

// Drag path: users-settings-view hosts this tree on the left and
// identity-tables-pane / security-table-view on the right.
// security-table-view.component.html has (drop)="onDrop($event)", which reads
// dataTransfer and pushes the parsed IdentityModels into a membership table.
// This group is not relevant to security-tree-dialog, which has no drop target.
// Bug #74591 fixed: the folder-name checks are merged into the single case below.
describe("SecurityTreeViewComponent - dragStart(): isIdentityFolder must cover all five folder names", () => {
   // All five root folder names must be treated as folders and excluded from the drag payload.
   it("should exclude all five root folders from the drag payload models", async () => {
      const orgFolder = makeNode("Organizations", IdentityType.ORGANIZATION, [], false);
      const orgRolesFolder = makeNode("Organization Roles", IdentityType.ROLE, [], false);
      const usersFolder  = makeNode("Users",  IdentityType.USERS, [], false);
      const groupsFolder = makeNode("Groups", IdentityType.GROUP, [], false);
      const rolesFolder  = makeNode("Roles",  IdentityType.ROLE,  [], false);
      const { comp } = await renderComponent([
         usersFolder,
         groupsFolder,
         rolesFolder,
         orgFolder,
         orgRolesFolder,
      ]);

      for(const folderName of ["Users", "Groups", "Roles", "Organizations", "Organization Roles"]) {
         const folderFlat = flatTree(comp).find(n => n.getData().identityID.name === folderName);
         expect(folderFlat).toBeTruthy();
         comp.selectedNodes = [];
         (comp as any).selectedIdentity = null;

         const event = makeDragEvent();
         const result = comp.dragStart(event as any, folderFlat);

         // Folder nodes should be filtered from dragModels, leaving an empty payload.
         expect(result).toBe(true);
         expect(event.dataTransfer.setData).toHaveBeenCalledTimes(1);
         const models = JSON.parse(event.dataTransfer.setData.mock.calls[0][1]);
         expect(models).toHaveLength(0);
      }
   });
});
