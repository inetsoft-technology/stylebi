/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { isAnchoredAssemblyType, isAnchoredChromeSuppressed, isAnchoredResident, MiniToolbarService }
   from "./mini-toolbar.service";

// The rollout boundary, asserted explicitly rather than left implied. Each family slice moves types
// from the second test to the first; the last one deletes the predicate entirely and the gate
// becomes the only condition.
describe("isAnchoredAssemblyType", () => {
   it("anchors the chart pilot, the table family and the selection family", () => {
      expect(isAnchoredAssemblyType("VSChart")).toBe(true);
      expect(isAnchoredAssemblyType("VSTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSCrosstab")).toBe(true);
      expect(isAnchoredAssemblyType("VSCalcTable")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionList")).toBe(true);
      expect(isAnchoredAssemblyType("VSSelectionTree")).toBe(true);
   });

   it("matches case-insensitively, as the Tool.equalsIgnoreCase calls it replaces did", () => {
      expect(isAnchoredAssemblyType("vschart")).toBe(true);
      expect(isAnchoredAssemblyType("VSCHART")).toBe(true);
      expect(isAnchoredAssemblyType("vstable")).toBe(true);
   });

   it("does not anchor the types whose rollout slices have not landed", () => {
      expect(isAnchoredAssemblyType("VSCalendar")).toBe(false);
      // The container is its own slice: four toolbar actions rather than nine, a vs-title lane in
      // normal flow rather than a ratio-split header, and it governs whether its children get a
      // strip at all.
      expect(isAnchoredAssemblyType("VSSelectionContainer")).toBe(false);
   });

   it("never anchors the range slider, which is excluded from the rollout permanently", () => {
      // Case 4: alone among the eight it declares no titleVisible, so it has no lane to anchor into.
      expect(isAnchoredAssemblyType("VSRangeSlider")).toBe(false);
   });

   it("does not throw on a missing objectType", () => {
      expect(isAnchoredAssemblyType(null)).toBe(false);
      expect(isAnchoredAssemblyType(undefined)).toBe(false);
      expect(isAnchoredAssemblyType("")).toBe(false);
   });
});

// The single source of truth VSObjectContainerComponent.isKebabResident and AbstractVSActions.
// resident both delegate to, so the two cannot drift apart the way they once did (resident stayed
// true under dense after the container started opting out).
describe("isAnchoredResident", () => {
   afterEach(() => {
      document.body.classList.remove(
         "viz-modern", "viz-density-dense", "viz-density-compact", "viz-density-comfortable");
   });

   it("is false when the modern gate is off, regardless of type", () => {
      expect(isAnchoredResident("VSChart")).toBe(false);
   });

   it("is false under dense, even for an anchored type with the gate on", () => {
      document.body.classList.add("viz-modern", "viz-density-dense");
      expect(isAnchoredResident("VSChart")).toBe(false);
   });

   it("is true under compact and comfortable, for an anchored type with the gate on", () => {
      document.body.classList.add("viz-modern", "viz-density-compact");
      expect(isAnchoredResident("VSChart")).toBe(true);
      document.body.classList.remove("viz-density-compact");
      document.body.classList.add("viz-density-comfortable");
      expect(isAnchoredResident("VSChart")).toBe(true);
   });

   it("is false under compact for a type outside the anchored set", () => {
      document.body.classList.add("viz-modern", "viz-density-compact");
      expect(isAnchoredResident("VSCalendar")).toBe(false);
   });
});

// The other half of the density split. Deliberately not the negation of isAnchoredResident: dense
// removes chrome from anchored types only, so the two predicates are both false for a calendar
// under compact and both false for anything with the gate off.
describe("isAnchoredChromeSuppressed", () => {
   afterEach(() => {
      document.body.classList.remove(
         "viz-modern", "viz-density-dense", "viz-density-compact", "viz-density-comfortable");
   });

   it("is true under dense for an anchored type with the gate on", () => {
      document.body.classList.add("viz-modern", "viz-density-dense");
      expect(isAnchoredChromeSuppressed("VSChart")).toBe(true);
      expect(isAnchoredChromeSuppressed("VSSelectionList")).toBe(true);
   });

   it("treats a bare modern gate as dense, matching the token fallback", () => {
      document.body.classList.add("viz-modern");
      expect(isAnchoredChromeSuppressed("VSChart")).toBe(true);
   });

   it("is false when the modern gate is off, so gate-off output is untouched", () => {
      document.body.classList.add("viz-density-dense");
      expect(isAnchoredChromeSuppressed("VSChart")).toBe(false);
   });

   it("is false under compact and comfortable", () => {
      document.body.classList.add("viz-modern", "viz-density-compact");
      expect(isAnchoredChromeSuppressed("VSChart")).toBe(false);
      document.body.classList.remove("viz-density-compact");
      document.body.classList.add("viz-density-comfortable");
      expect(isAnchoredChromeSuppressed("VSChart")).toBe(false);
   });

   it("is false under dense for a type outside the anchored set", () => {
      document.body.classList.add("viz-modern", "viz-density-dense");
      expect(isAnchoredChromeSuppressed("VSCalendar")).toBe(false);
      expect(isAnchoredChromeSuppressed("VSRangeSlider")).toBe(false);
   });

   it("is never true at the same time as isAnchoredResident", () => {
      for(const density of ["viz-density-dense", "viz-density-compact", "viz-density-comfortable"]) {
         document.body.classList.add("viz-modern", density);

         for(const type of ["VSChart", "VSSelectionList", "VSCalendar"]) {
            expect(isAnchoredResident(type) && isAnchoredChromeSuppressed(type)).toBe(false);
         }

         document.body.classList.remove(density);
      }
   });
});

// The selection component has two kebabs: the anchored strip the container mounts, and an inline
// mini-menu in its own header (vs-selection.component.html, .selection-list__header-buttons). They
// are mutually exclusive — the inline one renders only for dropdown and container-child selections,
// which are exactly the cases isMiniToolbarVisible() excludes. Neither file states the other's half
// of that contract, so it is asserted here.
describe("isMiniToolbarVisible: the anchored strip and the inline header kebab never co-render", () => {
   const service = new MiniToolbarService(
      { runOutsideAngular: (fn: () => any) => fn() } as any);

   // isMiniToolbarVisible reads only these four fields, so a literal is clearer than a full mock.
   const selection = (overrides: any = {}) => Object.assign(
      { objectType: "VSSelectionList", enabled: true, dropdown: false, containerType: null },
      overrides) as any;

   it("suppresses the anchored strip for a dropdown selection, which mounts the inline kebab", () => {
      expect(service.isMiniToolbarVisible(selection({ dropdown: true }))).toBe(false);
   });

   it("suppresses the anchored strip for a container child, which mounts the inline kebab", () => {
      expect(service.isMiniToolbarVisible(
         selection({ containerType: "VSSelectionContainer" }))).toBe(false);
   });

   it("allows the anchored strip for a standalone selection, which mounts no inline kebab", () => {
      expect(service.isMiniToolbarVisible(selection())).toBe(true);
   });
});
