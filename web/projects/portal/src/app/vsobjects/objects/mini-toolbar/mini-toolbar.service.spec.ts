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
import { anchoredLaneHeight, isAnchoredAssemblyType, isAnchoredChromeSuppressed, isAnchoredResident, MiniToolbarService }
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

describe("isAnchoredResident", () => {
   it("is false when the modern gate is off, regardless of lane", () => {
      expect(isAnchoredResident("VSChart", false, 30)).toBe(false);
   });

   it("is false for a type outside the anchored set, however tall its lane", () => {
      expect(isAnchoredResident("VSCalendar", true, 30)).toBe(false);
   });

   it("is false at the dense lane, which cannot hold the strip", () => {
      expect(isAnchoredResident("VSChart", true, 20)).toBe(false);
   });

   it("is true at the compact and comfortable lanes", () => {
      expect(isAnchoredResident("VSChart", true, 26)).toBe(true);
      expect(isAnchoredResident("VSChart", true, 30)).toBe(true);
   });

   // the compact lane IS the threshold, so a pixel either side decides it and nothing else does
   it("switches on exactly at the threshold", () => {
      expect(isAnchoredResident("VSChart", true, 23)).toBe(false);
      expect(isAnchoredResident("VSChart", true, 24)).toBe(true);
      expect(isAnchoredResident("VSChart", true, 25)).toBe(true);
   });

   it("anchors a lane that holds the strip exactly, and one pixel over", () => {
      expect(isAnchoredResident("VSChart", true, 24)).toBe(true);
      expect(isAnchoredResident("VSChart", true, 25)).toBe(true);
   });

   it("is false at a zero lane, which is what a hidden title resolves to", () => {
      expect(isAnchoredResident("VSChart", true, 0)).toBe(false);
   });
});

// The other half of the split. Deliberately not the negation of isAnchoredResident: negation would be
// true for every non-anchored type and every gate-off assembly, stripping toolbars that ship today.
describe("isAnchoredChromeSuppressed", () => {
   it("is true for an anchored type whose lane cannot hold the strip", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 20)).toBe(true);
      expect(isAnchoredChromeSuppressed("VSChart", true, 23)).toBe(true);
   });

   it("is true at a zero lane, which is what a hidden title resolves to", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 0)).toBe(true);
   });

   it("is false once the lane holds the strip", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 24)).toBe(false);
      expect(isAnchoredChromeSuppressed("VSChart", true, 30)).toBe(false);
   });

   it("is false with the gate off, so a legacy assembly keeps its toolbar", () => {
      expect(isAnchoredChromeSuppressed("VSChart", false, 20)).toBe(false);
   });

   it("is false for a type outside the anchored set, so its toolbar is untouched", () => {
      expect(isAnchoredChromeSuppressed("VSCalendar", true, 20)).toBe(false);
   });
});

describe("anchoredLaneHeight", () => {
   it("is the title format height when the title is visible", () => {
      expect(anchoredLaneHeight(<any> {titleVisible: true, titleFormat: {height: 26}})).toBe(26);
   });

   it("is zero when the title is hidden, whatever the format says", () => {
      expect(anchoredLaneHeight(<any> {titleVisible: false, titleFormat: {height: 30}})).toBe(0);
   });

   it("is zero for a model with no title lane at all", () => {
      expect(anchoredLaneHeight(<any> {})).toBe(0);
   });

   it("is zero when titleVisible is absent, which is a model with no lane to anchor into", () => {
      expect(anchoredLaneHeight(<any> {titleFormat: {height: 30}})).toBe(0);
   });

   it("rounds a fractional lane, which the composer's drag path produces at zoom", () => {
      expect(anchoredLaneHeight(<any> {titleVisible: true, titleFormat: {height: 25.97}})).toBe(26);
      expect(anchoredLaneHeight(<any> {titleVisible: true, titleFormat: {height: 25.4}})).toBe(25);
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
