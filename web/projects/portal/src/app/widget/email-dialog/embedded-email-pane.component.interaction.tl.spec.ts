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
 * EmbeddedEmailPane — Pass 1: Interaction
 *
 * Coverage:
 *   Group 1  — constructor HTTP init (usersNode, currOrg, reset)
 *   Group 2  — ngOnInit / initForm (emailForm control, mobile, otherEmail)
 *   Group 3  — ngOnDestroy (subscription cleanup)
 *   Group 4  — addresses setter / reset (embeddedOnly=true parsing)
 *   Group 5  — nodeSelected (type filtering)
 *   Group 6  — select (single / ctrl click multi-select)
 *   Group 7  — addIdentities (USER nodes)
 *   Group 8  — removeIdentities
 *   Group 9  — updateOtherEmail
 *   Group 10 — changeEmail
 *   Group 11 — addDisable
 *   Group 12 — onSearchIdentity
 *   Group 13 — searchAllIdentities
 *
 * Cross-reference: Groups 7/8 cover both sides of the add/remove pair.
 */

import { waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";
import { IdentityType } from "../../../../../shared/data/identity-type";
import { GuiTool } from "../../common/util/gui-tool";
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
// Group 1 — constructor HTTP init
// ---------------------------------------------------------------------------

describe("constructor HTTP init", () => {
   it("usersNode is empty array after MSW returns []", async () => {
      const { comp } = await renderEmbeddedEmail();
      await waitFor(() => expect(comp.usersNode).toEqual([]));
   });

   it("currOrg is set from get-current-user response (null when orgID absent)", async () => {
      const { comp } = await renderEmbeddedEmail();
      // MSW returns { name: "admin" } — name.orgID is undefined → currOrg becomes null
      await waitFor(() => expect(comp.currOrg).toBeNull());
   });

   it("addedIdentities is empty after constructor completes", async () => {
      const { comp } = await renderEmbeddedEmail();
      await waitFor(() => expect(comp.addedIdentities).toHaveLength(0));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit / initForm
// ---------------------------------------------------------------------------

describe("ngOnInit / initForm", () => {
   it("emailForm has otherEmail control after init", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(Object.keys(comp.emailForm.controls)).toContain("otherEmail");
   });

   it("otherEmail defaults to empty string", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(comp.otherEmail).toBe("");
   });

   it("mobile reflects GuiTool.isMobileDevice()", async () => {
      const spy = vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
      try {
         const { comp } = await renderEmbeddedEmail();
         expect(comp.mobile).toBe(true);
      }
      finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("ngOnDestroy", () => {
   it("does not throw when fixture is destroyed", async () => {
      const { fixture } = await renderEmbeddedEmail();
      expect(() => fixture.destroy()).not.toThrow();
   });

   it("subjects are closed after destroy", async () => {
      const { comp, fixture } = await renderEmbeddedEmail();
      fixture.destroy();
      // Bypass: searchTextchanges$ and searchIdentitychanges$ are private; accessed
      // via (comp as any) to verify closed state after ngOnDestroy.
      // Subject.unsubscribe() sets .closed = true (not .isStopped which is for complete/error).
      expect((comp as any).searchTextchanges$.closed).toBe(true);
      expect((comp as any).searchIdentitychanges$.closed).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — addresses setter / reset (embeddedOnly=true)
// ---------------------------------------------------------------------------

describe("addresses setter / reset (embeddedOnly=true)", () => {
   it("initialAddresses is set to the first addresses value", async () => {
      const { comp } = await renderEmbeddedEmail({ addresses: "alice(User)" });
      expect(comp.initialAddresses).toBe("alice(User)");
   });

   it("USER_SUFFIX address parsed into addedIdentities as USER type", async () => {
      const { comp } = await renderEmbeddedEmail({ addresses: "alice(User)" });
      await waitFor(() => expect(comp.addedIdentities).toHaveLength(1));
      expect(comp.addedIdentities[0].type).toBe(IdentityType.USER);
      expect(comp.addedIdentities[0].identityID.name).toBe("alice");
   });

   it("GROUP_SUFFIX address parsed into addedIdentities as GROUP type", async () => {
      const { comp } = await renderEmbeddedEmail({ addresses: "editors(Group)" });
      await waitFor(() => expect(comp.addedIdentities).toHaveLength(1));
      expect(comp.addedIdentities[0].type).toBe(IdentityType.GROUP);
      expect(comp.addedIdentities[0].identityID.name).toBe("editors");
   });

   it("valid email address goes into otherEmail", async () => {
      const { comp } = await renderEmbeddedEmail({ addresses: "user@example.com" });
      await waitFor(() => expect(comp.otherEmail).toBe("user@example.com"));
   });

   it("second call to addresses setter does not overwrite initialAddresses", async () => {
      const { comp } = await renderEmbeddedEmail({ addresses: "first(User)" });
      // Set addresses a second time
      comp.addresses = "second(User)";
      expect(comp.initialAddresses).toBe("first(User)");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — nodeSelected
// ---------------------------------------------------------------------------

describe("nodeSelected", () => {
   it("keeps USER, GROUP, USERS, GROUPS type nodes", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.nodeSelected([
         makeTreeNode({ type: String(IdentityType.USER) }),
         makeTreeNode({ type: String(IdentityType.GROUP) }),
         makeTreeNode({ type: String(IdentityType.USERS) }),
         makeTreeNode({ type: String(IdentityType.GROUPS) }),
      ]);
      expect(comp.selectedNodes).toHaveLength(4);
   });

   it("discards ROOT (5) type nodes", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.nodeSelected([makeTreeNode({ type: String(IdentityType.ROOT) })]);
      expect(comp.selectedNodes).toHaveLength(0);
   });

   it("accepts null input without throwing", async () => {
      const { comp } = await renderEmbeddedEmail();
      expect(() => comp.nodeSelected(null)).not.toThrow();
      expect(comp.selectedNodes).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — select (single / ctrl click multi-select)
// ---------------------------------------------------------------------------

describe("select — single and ctrl click", () => {
   it("single click replaces selectedAddedIdentities with [identity]", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id1 = makeIdentity("alice", IdentityType.USER);
      const id2 = makeIdentity("bob", IdentityType.USER);
      comp.addedIdentities = [id1, id2];
      comp.selectedAddedIdentities = [id1];
      comp.select(id2, new MouseEvent("click"));
      expect(comp.selectedAddedIdentities).toEqual([id2]);
   });

   it("ctrl+click adds identity to selectedAddedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id1 = makeIdentity("alice", IdentityType.USER);
      const id2 = makeIdentity("bob", IdentityType.USER);
      comp.selectedAddedIdentities = [id1];
      comp.select(id2, new MouseEvent("click", { ctrlKey: true }));
      expect(comp.selectedAddedIdentities).toHaveLength(2);
      expect(comp.selectedAddedIdentities[1].identityID.name).toBe("bob");
   });

   it("ctrl+click on already-selected identity removes it", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      comp.selectedAddedIdentities = [id];
      comp.select(id, new MouseEvent("click", { ctrlKey: true }));
      expect(comp.selectedAddedIdentities).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — addIdentities (USER nodes)
// ---------------------------------------------------------------------------

describe("addIdentities — USER nodes", () => {
   it("adds USER node to addedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [makeTreeNode({ label: "alice", type: String(IdentityType.USER), data: "alice@example.com" })];
      comp.addIdentities();
      expect(comp.addedIdentities).toHaveLength(1);
      expect(comp.addedIdentities[0].identityID.name).toBe("alice");
   });

   it("emits addressesChange when USER is added", async () => {
      const { comp } = await renderEmbeddedEmail();
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.selectedNodes = [makeTreeNode({ label: "alice", type: String(IdentityType.USER), data: "alice@example.com" })];
      comp.addIdentities();
      expect(emitted[emitted.length - 1]).toContain("alice(User)");
   });

   it("duplicate USER is not re-added", async () => {
      const { comp } = await renderEmbeddedEmail();
      const node = makeTreeNode({ label: "alice", type: String(IdentityType.USER), data: "alice@example.com" });
      comp.selectedNodes = [node];
      comp.addIdentities();
      comp.selectedNodes = [node];
      comp.addIdentities();
      expect(comp.addedIdentities).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — removeIdentities
// ---------------------------------------------------------------------------

describe("removeIdentities", () => {
   it("removes all selectedAddedIdentities from addedIdentities", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      comp.addedIdentities = [id];
      comp.selectedAddedIdentities = [id];
      comp.removeIdentities();
      expect(comp.addedIdentities).toHaveLength(0);
   });

   it("resets selectedAddedIdentities to first remaining item", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id1 = makeIdentity("alice", IdentityType.USER);
      const id2 = makeIdentity("bob", IdentityType.USER);
      comp.addedIdentities = [id1, id2];
      comp.selectedAddedIdentities = [id1];
      comp.removeIdentities();
      expect(comp.selectedAddedIdentities).toEqual([id2]);
   });

   it("emits empty addressesChange after removing all identities", async () => {
      const { comp } = await renderEmbeddedEmail();
      const id = makeIdentity("alice", IdentityType.USER);
      comp.addedIdentities = [id];
      comp.selectedAddedIdentities = [id];
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.removeIdentities();
      expect(emitted[emitted.length - 1]).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — updateOtherEmail
// ---------------------------------------------------------------------------

describe("updateOtherEmail", () => {
   it("sets otherEmail to new value", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.updateOtherEmail("new@example.com");
      expect(comp.otherEmail).toBe("new@example.com");
   });

   it("emits addressesChange after update", async () => {
      const { comp } = await renderEmbeddedEmail();
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.updateOtherEmail("x@example.com");
      expect(emitted.length).toBeGreaterThan(0);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — changeEmail
// ---------------------------------------------------------------------------

describe("changeEmail", () => {
   it("updates addedEmails[index] to new value", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedEmails = ["old@example.com"];
      comp.changeEmail(0, "new@example.com");
      expect(comp.addedEmails[0]).toBe("new@example.com");
   });

   it("sets addedEmails[index] to empty string when email is empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedEmails = ["old@example.com"];
      comp.changeEmail(0, "");
      expect(comp.addedEmails[0]).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 11 — addDisable
// ---------------------------------------------------------------------------

describe("addDisable", () => {
   it("returns true when selectedNodes is empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [];
      expect(comp.addDisable()).toBe(true);
   });

   it("returns false when selectedNodes has a USER node", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.selectedNodes = [makeTreeNode({ type: String(IdentityType.USER) })];
      expect(comp.addDisable()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — onSearchIdentity
// ---------------------------------------------------------------------------

describe("onSearchIdentity", () => {
   it("sets searchIdentityMode=true when val is non-empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.onSearchIdentity("alice");
      expect(comp.searchIdentityMode).toBe(true);
   });

   it("sets searchIdentityMode=false when val is empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.searchIdentityMode = true;
      comp.onSearchIdentity("");
      expect(comp.searchIdentityMode).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — searchAllIdentities
// ---------------------------------------------------------------------------

describe("searchAllIdentities", () => {
   it("returns all addedIdentities when searchIdentityText is empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedIdentities = [makeIdentity("alice", IdentityType.USER)];
      comp.searchIdentityText = "";
      comp.searchIdentityMode = false;
      const result = comp.searchAllIdentities();
      expect(result).toHaveLength(1);
   });

   it("returns filtered subset matching searchIdentityText", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedIdentities = [
         makeIdentity("alice", IdentityType.USER),
         makeIdentity("bob", IdentityType.USER),
      ];
      comp.searchIdentityText = "ali";
      comp.searchIdentityMode = true;
      const result = comp.searchAllIdentities();
      expect(result).toHaveLength(1);
      expect(result[0].identityID.name).toBe("alice");
   });

   it("returns empty array when no identities match", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.addedIdentities = [makeIdentity("alice", IdentityType.USER)];
      comp.searchIdentityText = "zzz";
      comp.searchIdentityMode = true;
      const result = comp.searchAllIdentities();
      expect(result).toHaveLength(0);
   });
});
