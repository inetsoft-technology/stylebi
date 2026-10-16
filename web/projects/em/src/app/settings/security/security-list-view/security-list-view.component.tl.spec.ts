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
 * SecurityListViewComponent — Testing Library style, real DOM drag events
 *
 * Bug #78723: a drag never emitted reorder. The indices came from the rows' text, which is the
 * provider label plus the cache age, and the dragged row was left where it was dropped, outside
 * Angular's view order. These tests drag the rendered rows with dragstart/dragenter/dragend.
 */

import { ErrorHandler } from "@angular/core";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { SecurityProviderStatus } from "../security-provider/security-provider-model/security-provider-status-list";
import { SecurityListViewComponent } from "./security-list-view.component";

function makeProvider(name: string, overrides: Partial<SecurityProviderStatus> = {}): SecurityProviderStatus {
   return { name, label: name, cacheEnabled: false, cacheAge: 0, loading: false, ldap: false, ...overrides };
}

function cached(name: string, overrides: Partial<SecurityProviderStatus> = {}): SecurityProviderStatus {
   return makeProvider(name, { cacheEnabled: true, cacheAge: 3600000, cacheAgeLabel: "1:00:00", ...overrides });
}

function fire(el: Element, type: string): void {
   const event: any = new Event(type, { bubbles: true, cancelable: true });
   Object.defineProperty(event, "dataTransfer", {
      value: { effectAllowed: "", dropEffect: "", setData: () => {}, getData: () => "" },
   });
   el.dispatchEvent(event);
}

const rowsOf = (root: Element) => Array.from(root.querySelectorAll("mat-list-item.list-item"));
const labelsOf = (root: Element) =>
   rowsOf(root).map(row => row.querySelector(".provider-name").textContent.trim());

/** dragenter is fired on the row's inner label, so it bubbles to the row the way a browser event does */
function enter(root: Element, index: number): void {
   fire(rowsOf(root)[index].querySelector(".provider-name"), "dragenter");
}

function drag(root: Element, from: number, over: number[]): void {
   const source = rowsOf(root)[from];
   fire(source, "dragstart");
   over.forEach(i => enter(root, i));
   fire(source, "dragend");
}

async function renderList(providers: SecurityProviderStatus[]) {
   const errors: any[] = [];
   const result = await render(SecurityListViewComponent, {
      imports: [NoopAnimationsModule],
      providers: [{ provide: ErrorHandler, useValue: { handleError: (e: any) => errors.push(e) } }],
   });
   const setProviders = (list: SecurityProviderStatus[]) => {
      result.fixture.componentRef.setInput("providers", list);
      result.fixture.detectChanges();
   };
   setProviders(providers);
   const emitted: [number, number][] = [];
   result.fixture.componentInstance.reorder.subscribe(e => emitted.push(e));
   const root = result.fixture.nativeElement as Element;
   return { ...result, comp: result.fixture.componentInstance, root, emitted, errors, setProviders };
}

describe("SecurityListViewComponent — drag to reorder (Bug #78723)", () => {

   // 🔁 Regression-sensitive: the drag emitted nothing because dragEnd looked a string up in a status array
   it("should emit the dragged row's start and drop positions and put the row back", async () => {
      const { root, emitted, comp } = await renderList(
         [makeProvider("Primary"), makeProvider("Corp LDAP"), makeProvider("Accounts")]);

      drag(root, 0, [1, 2]);

      expect(emitted).toEqual([[0, 2]]);
      // the page moves the row once the server saves the order; until then the rows keep the model's order
      expect(labelsOf(root)).toEqual(["Primary", "Corp LDAP", "Accounts"]);
      expect(comp.dragSource).toBeNull();
   });

   it("should emit an upward drag", async () => {
      const { root, emitted } = await renderList(
         [makeProvider("Primary"), makeProvider("Corp LDAP"), makeProvider("Accounts")]);

      drag(root, 2, [1, 0]);

      expect(emitted).toEqual([[2, 0]]);
   });

   // 🔁 Regression-sensitive: a cached row's text also holds "Cache Age: 1:00:00", so a text match never found it
   it("should emit for cached rows", async () => {
      const { root, emitted } = await renderList([cached("Primary"), makeProvider("Corp LDAP"), cached("Accounts")]);

      drag(root, 0, [1, 2]);

      expect(emitted).toEqual([[0, 2]]);
      expect(labelsOf(root)).toEqual(["Primary", "Corp LDAP", "Accounts"]);
   });

   // 🔁 Regression-sensitive: the row shows the localized label, not the provider name
   it("should emit for rows whose label differs from the provider name", async () => {
      const { root, emitted } = await renderList([
         makeProvider("p1", { label: "Primär" }),
         makeProvider("ldap1", { label: "Firmen-LDAP" }),
         makeProvider("db1", { label: "Konten" }),
      ]);

      drag(root, 0, [1, 2]);

      expect(emitted).toEqual([[0, 2]]);
      expect(labelsOf(root)).toEqual(["Primär", "Firmen-LDAP", "Konten"]);
   });

   it("should not emit when the row is dropped where it started", async () => {
      const { root, emitted } = await renderList(
         [makeProvider("Primary"), makeProvider("Corp LDAP"), makeProvider("Accounts")]);

      drag(root, 1, [2, 1]);

      expect(emitted).toEqual([]);
      expect(labelsOf(root)).toEqual(["Primary", "Corp LDAP", "Accounts"]);
   });

   // 🔁 Regression-sensitive: drag state was never reset, so a later file/text drag over the list moved the old row
   it("should ignore a dragenter that did not start on a row, before and after a completed drag", async () => {
      const { root, emitted, errors, comp } = await renderList(
         [makeProvider("Primary"), makeProvider("Corp LDAP"), makeProvider("Accounts")]);

      enter(root, 1); // nothing dragged yet: used to throw reading dragSource.parentNode
      drag(root, 0, [1]);
      emitted.length = 0;
      enter(root, 2); // e.g. a file dragged over the list after the drag ended

      expect(errors).toEqual([]);
      expect(labelsOf(root)).toEqual(["Primary", "Corp LDAP", "Accounts"]);
      expect(emitted).toEqual([]);
      expect(comp.dragSource).toBeNull();
   });

   // 🔁 Regression-sensitive: the page's poll removing the dragged provider left a detached ghost row in the list
   it("should leave no ghost row when a refresh removes the dragged provider mid-drag", async () => {
      const { root, emitted, errors, setProviders } = await renderList(
         [makeProvider("Primary"), makeProvider("Corp LDAP"), makeProvider("Accounts")]);
      const source = rowsOf(root)[0];

      fire(source, "dragstart");
      setProviders([makeProvider("Corp LDAP"), makeProvider("Accounts")]);
      enter(root, 1);
      fire(source, "dragend");

      expect(rowsOf(root).length).toBe(2);
      expect(labelsOf(root)).toEqual(["Corp LDAP", "Accounts"]);
      expect(emitted).toEqual([]);
      expect(errors).toEqual([]);
   });

   it("should keep the dragged row when a refresh replaces the providers with new objects mid-drag", async () => {
      const { root, emitted, setProviders } = await renderList([cached("Primary"), makeProvider("Corp LDAP"), cached("Accounts")]);
      const source = rowsOf(root)[0];

      fire(source, "dragstart");
      enter(root, 1);
      setProviders([cached("Primary", { cacheAgeLabel: "1:00:05" }), makeProvider("Corp LDAP"),
                    cached("Accounts", { cacheAgeLabel: "1:00:05" })]);
      enter(root, 2);
      fire(source, "dragend");

      expect(rowsOf(root).length).toBe(3);
      expect(emitted).toEqual([[0, 2]]);
      expect(labelsOf(root)).toEqual(["Primary", "Corp LDAP", "Accounts"]);
   });
});
