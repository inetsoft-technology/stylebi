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
 * LayoutPane — Pass 3: Display
 *
 * dispatch≥3: label/icon/conditional display / boundary inputs
 *
 *   Group 1  — showContent getter: true when not printLayout OR section is CONTENT
 *   Group 2  — showHeader getter: true only when printLayout && section is HEADER
 *   Group 3  — showFooter getter: true only when printLayout && section is FOOTER
 *   Group 4  — processSetCurrentFormatCommand (private): sets vsLayout.currentFormat and origFormat
 */

import { LayoutPane } from "./layout-pane.component";
import { PrintLayoutSection } from "../../../../vsobjects/model/layout/print-layout-section";
import { Tool } from "../../../../../../../shared/util/tool";
import {
   makeComponent,
} from "./layout-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: showContent [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — showContent getter", () => {

   it("is true when printLayout is false", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      expect(comp.showContent).toBe(true);
   });

   it("is true when printLayout is true and section is CONTENT", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      expect(comp.showContent).toBe(true);
   });

   it("is false when printLayout is true and section is HEADER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      expect(comp.showContent).toBe(false);
   });

   it("is false when printLayout is true and section is FOOTER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      expect(comp.showContent).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: showHeader [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — showHeader getter", () => {

   it("is true only when printLayout is true AND section is HEADER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      expect(comp.showHeader).toBe(true);
   });

   it("is false when printLayout is false", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      expect(comp.showHeader).toBe(false);
   });

   it("is false when printLayout is true but section is CONTENT", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      expect(comp.showHeader).toBe(false);
   });

   it("is false when printLayout is true but section is FOOTER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      expect(comp.showHeader).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: showFooter [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — showFooter getter", () => {

   it("is true only when printLayout is true AND section is FOOTER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      expect(comp.showFooter).toBe(true);
   });

   it("is false when printLayout is false", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      expect(comp.showFooter).toBe(false);
   });

   it("is false when printLayout is true but section is CONTENT", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      expect(comp.showFooter).toBe(false);
   });

   it("is false when printLayout is true but section is HEADER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      expect(comp.showFooter).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: processSetCurrentFormatCommand (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — processSetCurrentFormatCommand (private)", () => {

   it("sets vsLayout.currentFormat to command.model", () => {
      const { comp, mocks } = makeComponent();
      const model = { color: "#ff0000", font: { fontSize: "14" } } as any;

      (comp as any).processSetCurrentFormatCommand({ model });

      expect(mocks.vsLayout.currentFormat).toBe(model);
   });

   it("sets vsLayout.origFormat to a deep clone of command.model", () => {
      const { comp, mocks } = makeComponent();
      const model = { color: "#00ff00" } as any;

      (comp as any).processSetCurrentFormatCommand({ model });

      // origFormat should equal model but be a different object reference
      expect(mocks.vsLayout.origFormat).toEqual(model);
      expect(mocks.vsLayout.origFormat).not.toBe(model);
   });

   it("overrides a previously set currentFormat", () => {
      const { comp, mocks } = makeComponent();
      (comp as any).processSetCurrentFormatCommand({ model: { color: "#aaa" } });
      (comp as any).processSetCurrentFormatCommand({ model: { color: "#bbb" } });

      expect((mocks.vsLayout.currentFormat as any).color).toBe("#bbb");
   });
});
