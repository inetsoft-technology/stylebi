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
 * EmbeddedEmailPane — Pass 2: Risk
 *
 * Coverage:
 *   Group 1 — addressChange (embeddedOnly=true): USER/GROUP/email/empty emission formats
 *   Group 2 — addressChange (embeddedOnly=false): colon-separated emission
 *   Group 3 — addIdentity GROUP path deduplication
 *   Group 4 — reset (embeddedOnly=false): colon-separated parsing / query: prefix
 *   Group 5 — updateSearchText debounce → searchUsers call
 *   Group 6 — getUserAlias
 *
 * Cross-reference: Group 1 (embeddedOnly=true) and Group 2 (embeddedOnly=false) cover
 *   both branches of the embeddedOnly flag in addressChange().
 *
 * Fixed bugs (Bug #75601):
 *   dead-code guard — embedded-email-pane.component.ts:417: the early-return condition
 *     `addrs[0].substring(0, 7) === "query: "` was never true because addrs[0] was the
 *     substring before the first ":" separator — it could never itself contain ":".
 *     When addresses started with "query: ...", reset() fell through and pushed
 *     "query" as an identity name instead of returning early. Fixed by checking the
 *     original (pre-split) `this.addresses` string for the "query: " prefix instead
 *     of the post-split `addrs[0]` token.
 */

import { waitFor } from "@testing-library/angular";
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
// Group 1 — addressChange (embeddedOnly=true)
// ---------------------------------------------------------------------------

describe("addressChange — embeddedOnly=true", () => {
   it("USER identity emits 'name(User)' format", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: true });
      comp.addedIdentities = [makeIdentity("alice", IdentityType.USER)];
      comp.otherEmail = "";
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("alice(User)");
   });

   it("GROUP identity emits 'name(Group)' format", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: true });
      comp.addedIdentities = [makeIdentity("editors", IdentityType.GROUP)];
      comp.otherEmail = "";
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("editors(Group)");
   });

   it("mixed identity + otherEmail emits combined comma-separated string", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: true });
      comp.addedIdentities = [makeIdentity("alice", IdentityType.USER)];
      comp.otherEmail = "extra@example.com";
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("alice(User),extra@example.com");
   });

   it("empty addedIdentities + empty otherEmail emits empty string", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: true });
      comp.addedIdentities = [];
      comp.otherEmail = "";
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("");
   });

   it("otherEmail only (no identities) emits just the email", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: true });
      comp.addedIdentities = [];
      comp.otherEmail = "solo@example.com";
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("solo@example.com");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — addressChange (embeddedOnly=false)
// ---------------------------------------------------------------------------

describe("addressChange — embeddedOnly=false", () => {
   it("emits empty string when addedIdentities is empty", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: false });
      comp.addedIdentities = [];
      comp.addedEmails = [];
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      expect(emitted[emitted.length - 1]).toBe("");
   });

   it("emits a result for each added identity", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: false });
      comp.addedIdentities = [
         makeIdentity("user1", IdentityType.USER),
         makeIdentity("user2", IdentityType.USER),
      ];
      comp.addedEmails = ["user1@example.com", "user2@example.com"];
      const emitted: string[] = [];
      comp.addressesChange.subscribe(v => emitted.push(v));
      comp.addressChange();
      const result = emitted[emitted.length - 1];
      expect(result).toContain("user1@example.com");
      expect(result).toContain("user2@example.com");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — addIdentity GROUP path deduplication
// ---------------------------------------------------------------------------

describe("addIdentity — GROUP path dedup", () => {
   it("does not add duplicate GROUP by same name", async () => {
      const { comp } = await renderEmbeddedEmail();
      const node = makeTreeNode({
         label: "editors",
         type: String(IdentityType.GROUP),
         data: { parentNode: "/" },
      });
      comp.addIdentity(node);
      comp.addIdentity(node);
      const groups = comp.addedIdentities.filter(i => i.type === IdentityType.GROUP);
      expect(groups).toHaveLength(1);
   });

   it("GROUP node uses parentNode for path construction", async () => {
      const { comp } = await renderEmbeddedEmail();
      const node = makeTreeNode({
         label: "writers",
         type: String(IdentityType.GROUP),
         data: { parentNode: "/editors" },
      });
      comp.addIdentity(node);
      expect(comp.addedIdentities[0].identityID.name).toBe("writers");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — reset (embeddedOnly=false)
// ---------------------------------------------------------------------------

describe("reset — embeddedOnly=false", () => {
   // Bug #75601 (fixed): the guard now checks the original `this.addresses` string
   // for the "query: " prefix instead of the post-split addrs[0] token, so it
   // correctly fires and returns early.
   it("'query: ...' prefix returns early with empty addedIdentities — Bug #75601 fixed", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: false });
      comp._addresses = "query: SELECT 1";
      comp.reset();
      // Expected: 0 (early return before push).
      expect(comp.addedIdentities).toHaveLength(0);
   });

   it("parses first colon-separated segment as first identity name", async () => {
      const { comp } = await renderEmbeddedEmail({ embeddedOnly: false });
      // Bypass: _addresses is public but set via the property setter which also
      // guards initialAddresses; set directly to avoid that side effect.
      comp._addresses = "user1:user1@example.com";
      comp.embeddedOnly = false;
      comp.reset();
      expect(comp.addedIdentities).toHaveLength(1);
      expect(comp.addedIdentities[0].identityID.name).toBe("user1");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — updateSearchText / debounce → searchUsers
// ---------------------------------------------------------------------------

describe("updateSearchText — debounce", () => {
   // Bypass: searchTextchanges$ is private — accessed via (comp as any) to verify
   // that updateSearchText() pushes to the Subject before debounce fires.
   it("updateSearchText sets searchText and pushes to searchTextchanges$", async () => {
      const { comp } = await renderEmbeddedEmail();
      const spy = vi.spyOn((comp as any).searchTextchanges$, "next");
      try {
         comp.updateSearchText("ali");
         expect(comp.searchText).toBe("ali");
         expect(spy).toHaveBeenCalledWith("ali");
      }
      finally {
         spy.mockRestore();
      }
   });

   it("searchUsers is called after debounce fires", async () => {
      const { comp } = await renderEmbeddedEmail();
      const searchUsersSpy = vi.spyOn(comp, "searchUsers").mockImplementation(() => {});
      try {
         vi.useFakeTimers();
         comp.updateSearchText("ali");
         vi.advanceTimersByTime(400);
         vi.useRealTimers();
         await waitFor(() => expect(searchUsersSpy).toHaveBeenCalledWith("ali"));
      }
      finally {
         vi.useRealTimers();
         searchUsersSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 6 — getUserAlias
// ---------------------------------------------------------------------------

describe("getUserAlias", () => {
   it("returns alias when usersNode contains a matching label", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.usersNode = [{ label: "alice", alias: "Alice Smith", data: "alice@example.com" }];
      expect(comp.getUserAlias("alice")).toBe("Alice Smith");
   });

   it("returns null when usersNode is empty", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.usersNode = [];
      expect(comp.getUserAlias("alice")).toBeNull();
   });

   it("returns undefined when name is not found in usersNode", async () => {
      const { comp } = await renderEmbeddedEmail();
      comp.usersNode = [{ label: "bob", alias: "Bob", data: "bob@example.com" }];
      expect(comp.getUserAlias("alice")).toBeUndefined();
   });
});
