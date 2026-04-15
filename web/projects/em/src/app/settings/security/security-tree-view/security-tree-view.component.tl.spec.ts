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
 * SecurityTreeViewComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectNode() Ctrl+click: deselection is silently broken after the tree
 *                       is filtered — indexOf uses reference equality on filter-rebuilt node
 *                       objects (it.fails — confirmed bug).
 *   Group 2 [Risk 3] — selectNode() Shift+click: TypeError crash when the shift-click anchor
 *                       node has been filtered out of flattenTree (it.fails — confirmed bug).
 *   Group 3 [Risk 2] — dragStart(): isIdentityFolder omits "Organizations" and "Organization
 *                       Roles" — these folder nodes leak into the drag payload as real
 *                       identities (it.fails — confirmed bug).
 *
 * Confirmed bugs (it.fails — remove wrapper once fixed):
 *
 *   Bug A — Ctrl+click deselect broken after filter (Group 1):
 *     selectNode() uses selectedNodes.indexOf(nodeData) — reference equality.
 *     SecurityTreeNode.filter() returns brand-new SecurityTreeNode instances for every
 *     matching node, so the old references stored in selectedNodes never appear in the
 *     new flattenTree.
 *     Result: indexOf always returns -1 after any filter, so Ctrl+click can only ADD nodes,
 *             never remove them — even when isSelected() (which uses name+type value equality)
 *             shows the node as selected. selectionChanged also emits a grown array.
 *
 *   Bug B — Shift+click crash when anchor is filtered out (Group 2):
 *     lastIndex = flattenTree.findIndex(n => n.name === anchor.name && n.type === anchor.type).
 *     When the anchor is no longer visible (filtered out), lastIndex = -1.
 *     JS array access with index -1 returns undefined; undefined.getData() throws TypeError,
 *     crashing the component for any user who searches after shift-selecting.
 *     Result: unhandled TypeError; the tree becomes non-interactive until page reload.
 *
 *   Bug C — Organizations / Organization Roles folders leak into drag payload (Group 3):
 *     isIdentityFolder() only checks ["Users", "Groups", "Roles"] — three of the five
 *     root folder names. FlatSecurityTreeNode.rootNodes includes all five.
 *     Drop path: users-settings-view (tree) → security-table-view.onDrop() (table);
 *     onDrop() JSON.parses dataTransfer and inserts each entry into a membership table.
 *     Result: dragging an "Organizations" or "Organization Roles" folder node serializes it
 *             as a real IdentityModel, which reaches the backend as a bogus identity member.
 *
 * KEY contracts:
 *   - isSelected() uses value equality (identityID.name + type); Ctrl+click deselect must
 *     also use value equality to stay consistent with the visual selection state.
 *   - Shift+click with lastIndex = -1 must be treated as "no anchor" — graceful fallback
 *     (e.g., treat as a single-node add) instead of crashing.
 *   - All five root folder names ["Users","Groups","Roles","Organizations","Organization Roles"]
 *     must be excluded from drag payload by isIdentityFolder.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { it } from "@jest/globals";
import { render } from "@testing-library/angular";
import { SecurityTreeViewComponent } from "./security-tree-view.component";
import { FlatSecurityTreeNode, SecurityTreeNode } from "./security-tree-node";
import { IdentityType } from "../../../../../../shared/data/identity-type";

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Render helper
// ─────────────────────────────────────────────────────────────────────────────

async function renderComponent(treeData: SecurityTreeNode[]) {
   // Do NOT pass treeData as a componentProperty: Angular calls ngOnChanges before ngOnInit,
   // so flattenedDataChanged fires with non-empty data before the subscription is set up.
   // That leaves expandedState = undefined, causing restoreExpandedState to crash.
   // Instead, set treeData via setInput() after ngOnInit has established subscriptions.
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

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — selectNode() Ctrl+click: deselection after filter
// ════════════════════════════════════════════════════════════════════════════

describe("SecurityTreeViewComponent — selectNode() Ctrl+click: deselection after filter", () => {

   // indexOf is the ONLY guard that decides add-vs-remove.
   // After any filter, every SecurityTreeNode is a new object (filter() creates new instances),
   // so indexOf always returns -1, permanently disabling the deselect path.
   // Bug #74586
   it.failing("should deselect a selected node when Ctrl+clicked after the tree is filtered (Bug A)", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent());           // single-click → selectedNodes = [alice]
      expect(comp.selectedNodes).toHaveLength(1);

      applyFilter(comp, "alice", fixture);               // SecurityTreeNode.filter() creates new refs

      const filteredAliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      // isSelected still returns true — value-equality comparison in isSelected works correctly
      expect(comp.isSelected(filteredAliceFlat)).toBe(true);

      comp.selectNode(filteredAliceFlat, clickEvent({ ctrlKey: true })); // should deselect

      // Bug A: indexOf(filteredAliceRef) = -1 → push instead of splice → length becomes 2
      expect(comp.selectedNodes).toHaveLength(0);
   });

   // Baseline: adding an unselected node via Ctrl+click must always work, even after filter.
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

   // selectionChanged emission must reflect the actual visual state.
   // Bug A causes it to emit a grown array instead of an empty one, misleading the parent.
   // Bug #74586
   it.failing("selectionChanged must emit [] after a Ctrl+click deselect following a filter (Bug A side-effect)", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent());          // select

      applyFilter(comp, "alice", fixture);

      const emitted: SecurityTreeNode[][] = [];
      comp.selectionChanged.subscribe(v => emitted.push(v as SecurityTreeNode[]));

      const filteredAliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(filteredAliceFlat, clickEvent({ ctrlKey: true })); // should deselect

      // Bug A: emits [alice_old, alice_new] (length 2) instead of [] (deselected)
      expect(emitted[0]).toHaveLength(0);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 3] — selectNode() Shift+click: crash when anchor filtered out
// ════════════════════════════════════════════════════════════════════════════

describe("SecurityTreeViewComponent — selectNode() Shift+click: crash when anchor is filtered out", () => {

   // 🔁 Regression-sensitive: This crash is triggered by a common user workflow —
   // shift-select a node, then type in the search box to narrow the list.
   // flattenTree[-1] returns undefined in JS; undefined.getData() is an unhandled TypeError.
   // Issue #74592 
   it.failing("should not throw when Shift+clicking after the anchor node is filtered out (Bug B)", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const { comp, fixture } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      comp.selectNode(aliceFlat, clickEvent());          // alice becomes the shift-click anchor

      applyFilter(comp, "bob", fixture);                // alice is removed from flattenTree

      const bobFlat = flatTree(comp).find(n => n.getData().identityID.name === "bob");
      // lastIndex = -1 (alice absent from filtered tree) → flattenTree[-1].getData() throws
      expect(() => comp.selectNode(bobFlat, clickEvent({ shiftKey: true }))).not.toThrow();
   });

   // 🔁 Regression-sensitive: the range-selection happy path must still work after the -1
   // guard is added — confirm the fix does not break visible-range selection.
   it("should select all nodes between anchor and clicked node when both are visible", async () => {
      const alice = makeNode("alice", IdentityType.USER);
      const bob   = makeNode("bob",   IdentityType.USER);
      const carol = makeNode("carol", IdentityType.USER);
      const { comp } = await renderComponent([
         makeNode("Users", IdentityType.USERS, [alice, bob, carol]),
      ]);

      const aliceFlat = flatTree(comp).find(n => n.getData().identityID.name === "alice");
      const carolFlat = flatTree(comp).find(n => n.getData().identityID.name === "carol");

      comp.selectNode(aliceFlat, clickEvent());                     // anchor = alice
      comp.selectNode(carolFlat, clickEvent({ shiftKey: true }));  // range to carol

      const names = comp.selectedNodes.map(n => n.identityID.name);
      expect(names).toContain("alice");
      expect(names).toContain("bob");
      expect(names).toContain("carol");
   });

   // Boundary: Shift+clicking the exact anchor node must keep only that node selected.
   // Risk Point: slice(n+1, n) is empty; only the single push runs — must not double-add.
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

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — dragStart(): isIdentityFolder folder-name completeness
//
// Drag path: users-settings-view hosts this tree on the left and
// identity-tables-pane / security-table-view on the right.
// security-table-view.component.html has (drop)="onDrop($event)" which reads
// dataTransfer and pushes the parsed IdentityModels into a membership table.
// This group is NOT relevant to security-tree-dialog (picker modal, no drop target).
// ════════════════════════════════════════════════════════════════════════════

describe("SecurityTreeViewComponent — dragStart(): isIdentityFolder must cover all five folder names", () => {

   // "Organizations" is absent from isIdentityFolder's hard-coded list.
   // Drop path: security-table-view.onDrop() JSON.parses dataTransfer and inserts every entry
   // into the membership table — a folder IdentityModel reaches the backend as a real identity.
   // Issue #74591 
   it.failing("should exclude 'Organizations' folder from the drag payload models (Bug C)", async () => {
      // Root-level node — appears directly in flattenTree (no expansion needed)
      const orgFolder = makeNode("Organizations", IdentityType.ORGANIZATION, [], false);
      const { comp } = await renderComponent([orgFolder]);

      const orgFlat = flatTree(comp).find(n => n.getData().identityID.name === "Organizations");
      comp.selectedNodes = [];
      (comp as any).selectedIdentity = null;

      const event = makeDragEvent();
      comp.dragStart(event as any, orgFlat);

      // setData is called because dragNodes.length > 0 (not readOnly)
      expect(event.dataTransfer.setData).toHaveBeenCalled();
      const models = JSON.parse(event.dataTransfer.setData.mock.calls[0][1]);
      // Bug C: models = [{identityID: {...}, type: 4}] — folder NOT excluded by isIdentityFolder
      expect(models).toHaveLength(0);
   });

   // Note: In multi-tenant UI, "Organization Roles" is often shown under "Host Organizations"
   // (not necessarily as a top-level root row). Whether it is directly draggable depends on the
   // runtime node shape/flags (e.g. root value + [draggable] binding) and rendering branch.
   // This case still matters as a logic-level regression guard for dragStart()/isIdentityFolder:
   // regardless of UI shape, this folder label must never leak into payload models.
   // Same drop path as above — "Organization Roles" is equally missing from the check.
   it.failing("should exclude 'Organization Roles' folder from the drag payload models (Bug C variant)", async () => {
      const orgRolesFolder = makeNode("Organization Roles", IdentityType.ROLE, [], false);
      const { comp } = await renderComponent([orgRolesFolder]);

      const orgRolesFlat = flatTree(comp).find(n => n.getData().identityID.name === "Organization Roles");
      comp.selectedNodes = [];
      (comp as any).selectedIdentity = null;

      const event = makeDragEvent();
      comp.dragStart(event as any, orgRolesFlat);

      expect(event.dataTransfer.setData).toHaveBeenCalled();
      const models = JSON.parse(event.dataTransfer.setData.mock.calls[0][1]);
      // Bug C: "Organization Roles" also absent — folder model leaks into payload
      expect(models).toHaveLength(0);
   });

   // 🔁 Regression-sensitive: the three existing names must stay excluded after any fix that
   // adds the two missing names. A fix that accidentally removes an entry would silently
   // allow a folder node into permission tables.
   // All three are placed in the same tree so a single render covers all three.
   it("should exclude 'Users', 'Groups', and 'Roles' folders from drag payload (existing behavior)", async () => {
      const usersFolder  = makeNode("Users",  IdentityType.USERS, [], false);
      const groupsFolder = makeNode("Groups", IdentityType.GROUP, [], false);
      const rolesFolder  = makeNode("Roles",  IdentityType.ROLE,  [], false);
      const { comp } = await renderComponent([usersFolder, groupsFolder, rolesFolder]);

      for(const folderName of ["Users", "Groups", "Roles"]) {
         const folderFlat = flatTree(comp).find(n => n.getData().identityID.name === folderName);
         expect(folderFlat).toBeTruthy();
         comp.selectedNodes = [];
         (comp as any).selectedIdentity = null;

         const event = makeDragEvent();
         const result = comp.dragStart(event as any, folderFlat);

         // For these non-readOnly folder nodes, dragStart should proceed and write an empty payload:
         // folder nodes are excluded from dragModels by isIdentityFolder().
         expect(result).toBe(true);
         expect(event.dataTransfer.setData).toHaveBeenCalledTimes(1);
         const models = JSON.parse(event.dataTransfer.setData.mock.calls[0][1]);
         expect(models).toHaveLength(0);
      }
   });

});
