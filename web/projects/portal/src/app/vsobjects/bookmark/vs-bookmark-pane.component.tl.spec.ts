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
 * VsBookmarkPaneComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — bookmarkVisible(): three-branch filter + search-string contract
 *   Group 2 [Risk 2] — deleteBookmarks(): principal/addBookmarkDisabled guard before emit
 *   Group 3 [Risk 2] — isEditBookmarkDisabled(): owner key must match principal exactly
 *   Group 4 [Risk 1] — Bookmark action dispatchers: all six EventEmitter outputs
 *   Group 5 [Risk 1] — Visibility helpers: isSetDefaultBookmarkVisible, isEditBookmarkVisible
 *   Group 6 [Risk 1] — resetSearchMode, closeFilterDropDown null guard
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   ngOnInit() — empty body, no observable side effects
 *   isBookmarkHome() — covered transitively via isEditBookmarkVisible tests
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { VsBookmarkPaneComponent } from "./vs-bookmark-pane.component";
import { VSBookmarkInfoModel } from "../model/vs-bookmark-info-model";

const MODAL_MOCK = { open: vi.fn() };

function makeBookmark(overrides: Partial<VSBookmarkInfoModel> = {}): VSBookmarkInfoModel {
   return {
      name: "My Bookmark",
      label: "My Bookmark",
      owner: { name: "alice", orgID: "org1" },
      defaultBookmark: false,
      currentBookmark: false,
      ...overrides,
   };
}

async function renderComp(): Promise<VsBookmarkPaneComponent> {
   const { fixture } = await render(VsBookmarkPaneComponent, {
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return fixture.componentInstance;
}

// ---------------------------------------------------------------------------
// Group 1: bookmarkVisible()
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — bookmarkVisible", () => {

   // 🔁 Regression-sensitive: three filter branches share the same method; a change to one
   // silently breaks another — especially the home-bookmark bypass logic.
   it("should show all bookmarks when filter is ALL and searchString is empty", async () => {
      const comp = await renderComp();
      const other = makeBookmark({ owner: { name: "bob", orgID: "org1" } });
      expect(comp.bookmarkVisible(other)).toBe(true);
   });

   it("should hide other users' bookmarks in OWNED_BY_ME filter but keep home visible", async () => {
      const comp = await renderComp();
      comp.principal = "alice~;~org1";
      comp.filterByValue = comp.BookmarkFilter.OWNED_BY_ME;
      const other = makeBookmark({ name: "Bob's", owner: { name: "bob", orgID: "org1" } });
      const home = makeBookmark({ name: VSBookmarkInfoModel.HOME, owner: { name: "bob", orgID: "org1" } });
      expect(comp.bookmarkVisible(other)).toBe(false);
      expect(comp.bookmarkVisible(home)).toBe(true);
   });

   it("should hide own bookmarks in SHARE_BY_OTHERS filter but keep home visible", async () => {
      const comp = await renderComp();
      comp.principal = "alice~;~org1";
      comp.filterByValue = comp.BookmarkFilter.SHARE_BY_OTHERS;
      const own = makeBookmark({ owner: { name: "alice", orgID: "org1" } });
      const home = makeBookmark({ name: VSBookmarkInfoModel.HOME, owner: { name: "alice", orgID: "org1" } });
      expect(comp.bookmarkVisible(own)).toBe(false);
      expect(comp.bookmarkVisible(home)).toBe(true);
   });

   it("should filter case-insensitively by label and treat whitespace-only search as empty", async () => {
      const comp = await renderComp();
      comp.searchString = "sales";
      const matching = makeBookmark({ label: "Sales Dashboard" });
      const notMatching = makeBookmark({ label: "HR Overview" });
      expect(comp.bookmarkVisible(matching)).toBe(true);
      expect(comp.bookmarkVisible(notMatching)).toBe(false);

      comp.searchString = "   ";
      expect(comp.bookmarkVisible(notMatching)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: deleteBookmarks()
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — deleteBookmarks", () => {

   // 🔁 Regression-sensitive: removing either guard allows anonymous or read-only users
   // to trigger bulk-delete, which is a destructive irreversible action.
   it("should not emit when principal is not set", async () => {
      const comp = await renderComp();
      comp.addBookmarkDisabled = false;
      const spy = vi.fn();
      comp.onDeleteBookmarks.subscribe(spy);
      comp.deleteBookmarks();
      expect(spy).not.toHaveBeenCalled();
   });

   it("should not emit when addBookmarkDisabled is true", async () => {
      const comp = await renderComp();
      comp.principal = "alice~;~org1";
      comp.addBookmarkDisabled = true;
      const spy = vi.fn();
      comp.onDeleteBookmarks.subscribe(spy);
      comp.deleteBookmarks();
      expect(spy).not.toHaveBeenCalled();
   });

   it("should emit onDeleteBookmarks when principal is set and not disabled", async () => {
      const comp = await renderComp();
      comp.principal = "alice~;~org1";
      comp.addBookmarkDisabled = false;
      const spy = vi.fn();
      comp.onDeleteBookmarks.subscribe(spy);
      comp.deleteBookmarks();
      expect(spy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3: isEditBookmarkDisabled()
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — isEditBookmarkDisabled", () => {

   // 🔁 Regression-sensitive: convertToKey formats owner as "name~;~orgID"; a format
   // change silently disables editing for all own bookmarks with no visible error.
   it("should be true when addBookmarkDisabled is true even for own bookmark", async () => {
      const comp = await renderComp();
      comp.addBookmarkDisabled = true;
      comp.principal = "alice~;~org1";
      expect(comp.isEditBookmarkDisabled(makeBookmark({ owner: { name: "alice", orgID: "org1" } }))).toBe(true);
   });

   it("should be true when bookmark owner key does not match principal", async () => {
      const comp = await renderComp();
      comp.addBookmarkDisabled = false;
      comp.principal = "alice~;~org1";
      expect(comp.isEditBookmarkDisabled(makeBookmark({ owner: { name: "bob", orgID: "org1" } }))).toBe(true);
   });

   it("should be false when owner key matches principal and not disabled", async () => {
      const comp = await renderComp();
      comp.addBookmarkDisabled = false;
      comp.principal = "alice~;~org1";
      expect(comp.isEditBookmarkDisabled(makeBookmark({ owner: { name: "alice", orgID: "org1" } }))).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: EventEmitter outputs (bookmark action dispatchers)
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — bookmark action dispatchers", () => {

   it("should emit the bookmark from setDefaultBookmark", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onSetDefaultBookmark.subscribe(spy);
      comp.setDefaultBookmark(makeBookmark());
      expect(spy).toHaveBeenCalledWith(makeBookmark());
   });

   it("should emit the bookmark from editBookmark", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onEditBookmark.subscribe(spy);
      comp.editBookmark(makeBookmark());
      expect(spy).toHaveBeenCalledWith(makeBookmark());
   });

   it("should emit the bookmark from deleteBookmark", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onDeleteBookmark.subscribe(spy);
      comp.deleteBookmark(makeBookmark());
      expect(spy).toHaveBeenCalledWith(makeBookmark());
   });

   it("should emit the bookmark from gotoBookmark", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onGoToBookmark.subscribe(spy);
      comp.gotoBookmark(makeBookmark());
      expect(spy).toHaveBeenCalledWith(makeBookmark());
   });

   it("should emit from addBookmark with no payload", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onAddBookmark.subscribe(spy);
      comp.addBookmark();
      expect(spy).toHaveBeenCalledTimes(1);
   });

   it("should emit from saveBookmark with no payload", async () => {
      const comp = await renderComp();
      const spy = vi.fn();
      comp.onSaveBookmark.subscribe(spy);
      comp.saveBookmark();
      expect(spy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: Visibility helpers
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — visibility helpers", () => {

   it("isSetDefaultBookmarkVisible: should be false when defaultBookmark is true", async () => {
      const comp = await renderComp();
      expect(comp.isSetDefaultBookmarkVisible(makeBookmark({ defaultBookmark: true }))).toBe(false);
   });

   it("isSetDefaultBookmarkVisible: should be false when isDefaultOrgAsset is true", async () => {
      const comp = await renderComp();
      comp.isDefaultOrgAsset = true;
      expect(comp.isSetDefaultBookmarkVisible(makeBookmark({ defaultBookmark: false }))).toBe(false);
   });

   it("isSetDefaultBookmarkVisible: should be true when not default and not org asset", async () => {
      const comp = await renderComp();
      comp.isDefaultOrgAsset = false;
      expect(comp.isSetDefaultBookmarkVisible(makeBookmark({ defaultBookmark: false }))).toBe(true);
   });

   it("isEditBookmarkVisible: should be false for the home bookmark and true for others", async () => {
      const comp = await renderComp();
      expect(comp.isEditBookmarkVisible(makeBookmark({ name: VSBookmarkInfoModel.HOME }))).toBe(false);
      expect(comp.isEditBookmarkVisible(makeBookmark({ name: "Custom Report" }))).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: resetSearchMode / closeFilterDropDown
// ---------------------------------------------------------------------------

describe("VsBookmarkPaneComponent — resetSearchMode / closeFilterDropDown", () => {

   it("resetSearchMode: should set searchString to null", async () => {
      const comp = await renderComp();
      comp.searchString = "foo";
      comp.resetSearchMode();
      expect(comp.searchString).toBeNull();
   });

   it("closeFilterDropDown: should not throw when dropdown ViewChild is not initialized", async () => {
      const comp = await renderComp();
      // NO_ERRORS_SCHEMA stubs the directive; the ViewChild will be undefined in this env
      expect(() => comp.closeFilterDropDown()).not.toThrow();
   });
});
