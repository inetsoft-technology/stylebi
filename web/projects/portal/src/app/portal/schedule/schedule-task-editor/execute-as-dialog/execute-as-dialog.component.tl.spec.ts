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
 * ExecuteAsDialog - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - users/groups setters and limit-warning trees
 *   Group 2 [Risk 3] - search mode transitions and search-tree hydration
 *   Group 3 [Risk 2] - node selection, root getters, and commit payloads
 *   Group 4 [Risk 1] - cancel flow and limit-warning label generation
 *
 * Mocking strategy:
 *   - pure class logic -> direct instantiation
 *   - IdentityTree ViewChilds -> minimal injected stubs
 */

import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { ExpandStringDirective } from "../../../../widget/expand-string/expand-string.directive";
import { ExecuteAsDialog } from "./execute-as-dialog.component";

interface IdentityIdStub {
   name: string;
   orgID: string | null;
}

interface IdentityIdWithLabelStub {
   identityID: IdentityIdStub;
   label?: string;
}

interface IdentityTreeStub {
   searchMode: boolean;
   nodeExpanded: ReturnType<typeof vi.fn>;
   tree: {
      selectedNodes: unknown[];
   };
}

function createComponent(): ExecuteAsDialog {
   return new ExecuteAsDialog();
}

function makeIdentityId(name: string): IdentityIdStub {
   return {
      name,
      orgID: null,
   };
}

function makeLabeledUser(
   name: string,
   label?: string,
): IdentityIdWithLabelStub {
   return {
      identityID: makeIdentityId(name),
      label,
   };
}

function attachIdentityTrees(comp: ExecuteAsDialog): {
   identityTree: IdentityTreeStub;
   searchIdentityTree: IdentityTreeStub;
} {
   const identityTree = {
      searchMode: false,
      nodeExpanded: vi.fn(),
      tree: {
         selectedNodes: [],
      },
   };
   const searchIdentityTree = {
      searchMode: false,
      nodeExpanded: vi.fn(),
      tree: {
         selectedNodes: [],
      },
   };

   // Tests inject the minimal @ViewChild surface directly because searchUsers toggles these tree APIs.
   (comp as unknown as { identityTree: IdentityTreeStub }).identityTree = identityTree;
   (comp as unknown as { searchIdentityTree: IdentityTreeStub }).searchIdentityTree = searchIdentityTree;

   return { identityTree, searchIdentityTree };
}

describe("ExecuteAsDialog", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   describe("Group 1 - identity setters", () => {
      it("users should map labeled user nodes and append a limit-warning node when the list is truncated", () => {
         const expandSpy = vi.spyOn(ExpandStringDirective, "expandString")
            .mockReturnValue("Limited to 1000");
         const comp = createComponent();
         const users = Array.from({ length: 1001 }, (_, idx) =>
            makeLabeledUser(`user-${idx}`, idx === 0 ? "Alice" : undefined),
         );

         comp.users = users as never;

         expect(comp.users).toBe(users);
         expect(comp.usersNode.children.length).toBe(1001);
         expect(comp.usersNode.children[0]).toEqual(expect.objectContaining({
            label: "Alice",
            type: String(IdentityType.USER),
         }));
         expect(comp.usersNode.children[1000]).toEqual(expect.objectContaining({
            label: "Limited to 1000",
            cssClass: "alert alert-danger disable-actions",
         }));
         expect(expandSpy).toHaveBeenCalledWith(
            "_#(js:schedule.task.options.userTreeLimited)",
            ["1000"],
         );
      });

      it("groups should map group nodes and append a limit-warning node when the list is truncated", () => {
         vi.spyOn(ExpandStringDirective, "expandString").mockReturnValue("Limited to 1000");
         const comp = createComponent();
         const groups = Array.from({ length: 1001 }, (_, idx) => makeIdentityId(`group-${idx}`));

         comp.groups = groups as never;

         expect(comp.groups).toBe(groups);
         expect(comp.groupsNode.children[0]).toEqual(expect.objectContaining({
            label: "group-0",
            type: String(IdentityType.GROUP),
         }));
         expect(comp.groupsNode.children[1000]).toEqual(expect.objectContaining({
            label: "Limited to 1000",
         }));
      });
   });

   describe("Group 2 - search mode", () => {
      it("searchUsers should rebuild the search tree and enter search mode for a matching user query", () => {
         const comp = createComponent();
         const { searchIdentityTree } = attachIdentityTrees(comp);
         comp.type = IdentityType.USER;
         comp.users = [
            makeLabeledUser("alpha"),
            makeLabeledUser("alphabet", "Alphabet"),
            makeLabeledUser("beta"),
         ] as never;
         comp.searchText = "alpha";
         comp.selectedNode = { label: "stale" } as never;

         comp.searchUsers();

         expect(comp.searchText).toBe("alpha");
         expect(comp.selectedNode).toBeNull();
         expect(comp.searchMode).toBe(true);
         expect(comp.searchTree.expanded).toBe(true);
         expect(searchIdentityTree.searchMode).toBe(true);
         expect(searchIdentityTree.nodeExpanded).toHaveBeenCalledWith(comp.searchTree);
         expect(comp.searchTree.children.map(node => node.label)).toEqual(["alpha", "Alphabet"]);
      });

      it("searchUsers should clear both trees and exit search mode when the search text is empty", () => {
         const comp = createComponent();
         const { identityTree, searchIdentityTree } = attachIdentityTrees(comp);
         comp.searchMode = true;
         comp.searchText = "";
         comp.selectedNode = { label: "stale" } as never;
         identityTree.tree.selectedNodes = ["selected"];

         comp.searchUsers();

         expect(comp.selectedNode).toBeNull();
         expect(identityTree.tree.selectedNodes).toEqual([]);
         expect(searchIdentityTree.searchMode).toBe(false);
         expect(comp.searchMode).toBe(false);
      });

      it("initSearchTree should search group names when the dialog is in group mode", () => {
         const comp = createComponent();
         comp.type = IdentityType.GROUP;
         comp.groups = [
            makeIdentityId("admins"),
            makeIdentityId("sales-admins"),
            makeIdentityId("users"),
         ] as never;
         comp.searchText = "admin";

         comp.initSearchTree();

         expect(comp.searchTree.children).toEqual([
            expect.objectContaining({ label: "admins", type: String(IdentityType.GROUP) }),
            expect.objectContaining({ label: "sales-admins", type: String(IdentityType.GROUP) }),
         ]);
      });
   });

   describe("Group 3 - selection and commit", () => {
      it("nodeSelected should accept only user/group leaf nodes", () => {
         const comp = createComponent();
         const userNode = {
            label: "Alice",
            data: makeIdentityId("alice"),
            type: String(IdentityType.USER),
         };

         comp.nodeSelected([userNode] as never);
         expect(comp.selectedNode).toBe(userNode as never);

         comp.nodeSelected([{
            label: "Users",
            type: String(IdentityType.USERS),
         }] as never);
         expect(comp.selectedNode).toBeNull();
      });

      it("ok should emit the selected user name, type, and alias", () => {
         const comp = createComponent();
         const commitSpy = vi.spyOn(comp.onCommit, "emit");
         comp.selectedNode = {
            label: "Alice",
            data: makeIdentityId("alice"),
            type: String(IdentityType.USER),
         } as never;

         comp.ok();

         expect(commitSpy).toHaveBeenCalledWith({
            name: "alice",
            type: IdentityType.USER,
            alias: "Alice",
         });
      });

      it("ok should emit group selections without a user alias", () => {
         const comp = createComponent();
         const commitSpy = vi.spyOn(comp.onCommit, "emit");
         comp.selectedNode = {
            label: "admins",
            data: makeIdentityId("admins"),
            type: String(IdentityType.GROUP),
         } as never;

         comp.ok();

         expect(commitSpy).toHaveBeenCalledWith({
            name: "admins",
            type: IdentityType.GROUP,
            alias: null,
         });
      });

      it("getRoot should return usersNode for USER type and groupsNode for GROUP type", () => {
         const comp = createComponent();
         comp.type = IdentityType.USER;
         expect(comp.getRoot()).toBe(comp.usersNode);

         comp.type = IdentityType.GROUP;
         expect(comp.getRoot()).toBe(comp.groupsNode);
      });

      it("searchTree should return usersSearchTree for USER type and groupsSearchTree for GROUP type", () => {
         const comp = createComponent();
         comp.type = IdentityType.USER;
         expect(comp.searchTree).toBe(comp.usersSearchTree);

         comp.type = IdentityType.GROUP;
         expect(comp.searchTree).toBe(comp.groupsSearchTree);
      });
   });

   describe("Group 4 - close and warning node", () => {
      it("close should emit the cancel token", () => {
         const comp = createComponent();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.close();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });

      it("getLimitWarningNode should build the localized warning node", () => {
         vi.spyOn(ExpandStringDirective, "expandString").mockReturnValue("Limited to 1000");
         const comp = createComponent();

         const node = comp.getLimitWarningNode();

         expect(node).toEqual({
            label: "Limited to 1000",
            leaf: true,
            expanded: false,
            icon: "alert-circle-icon",
            cssClass: "alert alert-danger disable-actions",
         });
      });
   });
});
