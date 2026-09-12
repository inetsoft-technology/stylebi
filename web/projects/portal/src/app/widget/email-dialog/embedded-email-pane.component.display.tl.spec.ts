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
 * EmbeddedEmailPane — Pass 3: Display
 *
 * Coverage:
 *   Group 1 — addDisable edge cases (USERS/GROUPS with empty children)
 *   Group 2 — isSelectedIdentity
 *   Group 3 — getIdentityIcon
 *   Group 4 — sortIdentities
 *   Group 5 — findIdentityIndex
 *   Group 6 — trackByIndex
 */

import { Subject } from "rxjs";
import { IdentityType } from "../../../../../shared/data/identity-type";
import {
   MODAL_MOCK,
   makeIdentity,
   makeTreeNode,
   renderEmbeddedEmail,
} from "./embedded-email-pane.component.test-helpers";

beforeEach(() => {
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — addDisable edge cases
// ---------------------------------------------------------------------------

describe("addDisable — USERS/GROUPS container nodes", () => {
   it("returns true when USERS node has empty children", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [makeTreeNode({ type: String(IdentityType.USERS), children: [], leaf: false })];
      expect(comp.addDisable()).toBe(true);
   });

   it("returns true when GROUPS node has empty children", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [makeTreeNode({ type: String(IdentityType.GROUPS), children: [], leaf: false })];
      expect(comp.addDisable()).toBe(true);
   });

   it("returns false when USERS node has children", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [
         makeTreeNode({
            type: String(IdentityType.USERS),
            leaf: false,
            children: [makeTreeNode({ type: String(IdentityType.USER) })],
         }),
      ];
      expect(comp.addDisable()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — isSelectedIdentity
// ---------------------------------------------------------------------------

describe("isSelectedIdentity", () => {
   it("returns true when identity is in selectedAddedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      comp.selectedAddedIdentities = [id];
      expect(comp.isSelectedIdentity(id)).toBe(true);
   });

   it("returns false when identity is not in selectedAddedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      comp.selectedAddedIdentities = [];
      expect(comp.isSelectedIdentity(id)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getIdentityIcon
// ---------------------------------------------------------------------------

describe("getIdentityIcon", () => {
   it("returns 'account-icon' for USER type", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(comp.getIdentityIcon(IdentityType.USER)).toBe("account-icon");
   });

   it("returns 'user-group-icon' for GROUP type", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(comp.getIdentityIcon(IdentityType.GROUP)).toBe("user-group-icon");
   });

   it("returns 'user-group-icon' for ROLE type", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(comp.getIdentityIcon(IdentityType.ROLE)).toBe("user-group-icon");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — sortIdentities
// ---------------------------------------------------------------------------

describe("sortIdentities", () => {
   it("sorts by type first (GROUP=2 before USER=1 is NOT expected; USER=1 < GROUP=2)", async () => {
      const { comp } = await renderEmbeddedEmail();
      const user = makeIdentity("alice", IdentityType.USER);
      const group = makeIdentity("editors", IdentityType.GROUP);
      comp.addedIdentities = [group, user];
      comp.sortIdentities();
      expect(comp.addedIdentities[0].type).toBe(IdentityType.USER);
      expect(comp.addedIdentities[1].type).toBe(IdentityType.GROUP);
   });

   it("sorts alphabetically within same type", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedIdentities = [
         makeIdentity("zach", IdentityType.USER),
         makeIdentity("alice", IdentityType.USER),
      ];
      comp.sortIdentities();
      expect(comp.addedIdentities[0].identityID.name).toBe("alice");
      expect(comp.addedIdentities[1].identityID.name).toBe("zach");
   });

   it("does not throw on empty addedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedIdentities = [];
      expect(() => comp.sortIdentities()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — findIdentityIndex
// ---------------------------------------------------------------------------

describe("findIdentityIndex", () => {
   it("returns -1 when identity is not in the list", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      expect(comp.findIdentityIndex([], id)).toBe(-1);
   });

   it("returns correct index when identity is found by name and type", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id1 = makeIdentity("alice", IdentityType.USER);
      const id2 = makeIdentity("bob", IdentityType.USER);
      const list = [id1, id2];
      expect(comp.findIdentityIndex(list, id2)).toBe(1);
   });

   it("does not match when type differs", async () => {
      const { comp } = await renderEmbeddedEmail();
      const user = makeIdentity("alice", IdentityType.USER);
      const group = makeIdentity("alice", IdentityType.GROUP);
      expect(comp.findIdentityIndex([user], group)).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — trackByIndex
// ---------------------------------------------------------------------------

describe("trackByIndex", () => {
   it("returns the provided index value", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(comp.trackByIndex(0, null)).toBe(0);
      expect(comp.trackByIndex(5, null)).toBe(5);
   });
});
