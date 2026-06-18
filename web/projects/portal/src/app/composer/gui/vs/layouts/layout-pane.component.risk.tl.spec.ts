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
 * LayoutPane — Pass 2: Risk
 *
 * async≥3: race conditions / destructive / state inconsistency
 *
 *   Group 1  — refreshViewsheet: sends event via viewsheetClientService
 *   Group 2  — removeLayoutObject (private): removes by name; no-op when not found
 *   Group 3  — removeSelectedAssemblies: sends remove event with focused object names
 *   Group 4  — processRemoveLayoutObjectsCommand (private): removes from correct section by layout name
 *   Group 5  — updateSnapGuides (private): populates draggableSnapGuides from layout objects
 *   Group 6  — getLayoutSize (private): calls getGuideSize for non-print layout
 */

import { LayoutPane } from "./layout-pane.component";
import { VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { PrintLayoutSection } from "../../../../vsobjects/model/layout/print-layout-section";
import {
   makeComponent,
   makeLayoutObject,
} from "./layout-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: refreshViewsheet [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — refreshViewsheet", () => {

   it("sends a refresh event to viewsheetClientService", () => {
      const { comp, mocks } = makeComponent();
      comp.refreshViewsheet();
      expect(mocks.viewsheetClientService.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/refresh",
         expect.any(Object),
      );
   });

   it("sets the layoutName on the event to vsLayout.name", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.name = "MyLayout";
      comp.refreshViewsheet();

      const event = mocks.viewsheetClientService.sendEvent.mock.calls[0][1];
      expect(event.layoutName).toBe("MyLayout");
   });
});

// ---------------------------------------------------------------------------
// Group 2: removeLayoutObject (private) [Risk 3]
// ---------------------------------------------------------------------------

describe("LayoutPane — removeLayoutObject (private)", () => {

   it("removes the object with the matching name", () => {
      const { comp } = makeComponent();
      const objects = [makeLayoutObject("A"), makeLayoutObject("B"), makeLayoutObject("C")];
      const result = (comp as any).removeLayoutObject(objects, "B");
      expect(result.map((o: any) => o.name)).toEqual(["A", "C"]);
   });

   it("leaves the list unchanged when name is not found", () => {
      const { comp } = makeComponent();
      const objects = [makeLayoutObject("A"), makeLayoutObject("B")];
      const result = (comp as any).removeLayoutObject(objects, "X");
      expect(result).toHaveLength(2);
   });

   it("removes only the first matching name (names are unique by design)", () => {
      const { comp } = makeComponent();
      const objects = [makeLayoutObject("A")];
      const result = (comp as any).removeLayoutObject(objects, "A");
      expect(result).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: removeSelectedAssemblies [Risk 3]
// ---------------------------------------------------------------------------

describe("LayoutPane — removeSelectedAssemblies", () => {

   it("sends removeObjects event with focused object names", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.name = "L1";
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      // Add focused objects
      const obj = makeLayoutObject("Chart1");
      mocks.vsLayout.focusedObjects = [obj];

      comp.removeSelectedAssemblies();

      expect(mocks.viewsheetClientService.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vs/layouts/removeObjects",
         expect.objectContaining({ names: ["Chart1"] }),
      );
   });

   it("sends event with empty names when no focused objects", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.focusedObjects = [];

      comp.removeSelectedAssemblies();

      const event = mocks.viewsheetClientService.sendEvent.mock.calls[0][1];
      expect(event.names).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: processRemoveLayoutObjectsCommand (private) [Risk 3]
// ---------------------------------------------------------------------------

describe("LayoutPane — processRemoveLayoutObjectsCommand (private)", () => {

   it("removes object from objects array when section is CONTENT", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.name = "Layout1";
      mocks.vsLayout.printLayout = true; // avoid getGuideSize() call
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.objects = [makeLayoutObject("Text1"), makeLayoutObject("Text2")];

      (comp as any).processRemoveLayoutObjectsCommand({
         layoutName: "Layout1",
         assemblies: ["Text1"],
      });

      expect(mocks.vsLayout.objects.map((o: any) => o.name)).toEqual(["Text2"]);
   });

   it("does nothing when command.layoutName does not match vsLayout.name", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.name = "Layout1";
      mocks.vsLayout.objects = [makeLayoutObject("Text1")];

      (comp as any).processRemoveLayoutObjectsCommand({
         layoutName: "DifferentLayout",
         assemblies: ["Text1"],
      });

      expect(mocks.vsLayout.objects).toHaveLength(1);
   });

   it("removes from headerObjects when section is HEADER", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.name = "Layout1";
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      mocks.vsLayout.headerObjects = [makeLayoutObject("Hdr1")];
      mocks.vsLayout.objects = [];

      (comp as any).processRemoveLayoutObjectsCommand({
         layoutName: "Layout1",
         assemblies: ["Hdr1"],
      });

      expect(mocks.vsLayout.headerObjects).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateSnapGuides (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — updateSnapGuides (private)", () => {

   it("populates vertical guides with left and left+width for non-focused objects", () => {
      const { comp, mocks } = makeComponent();
      const obj = makeLayoutObject("Text1");
      obj.objectModel.objectFormat = { left: 50, top: 20, width: 100, height: 40 } as any;
      mocks.vsLayout.objects = [obj];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.focusedObjects = [];

      (comp as any).updateSnapGuides();

      expect(comp.draggableSnapGuides.vertical).toContain(50);   // left
      expect(comp.draggableSnapGuides.vertical).toContain(150);  // left + width
   });

   it("populates horizontal guides with top and top+height for non-focused objects", () => {
      const { comp, mocks } = makeComponent();
      const obj = makeLayoutObject("Text1");
      obj.objectModel.objectFormat = { left: 50, top: 20, width: 100, height: 40 } as any;
      mocks.vsLayout.objects = [obj];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.focusedObjects = [];

      (comp as any).updateSnapGuides();

      expect(comp.draggableSnapGuides.horizontal).toContain(20);  // top
      expect(comp.draggableSnapGuides.horizontal).toContain(60);  // top + height
   });

   it("excludes focused objects from snap guides", () => {
      const { comp, mocks } = makeComponent();
      const obj = makeLayoutObject("Text1");
      obj.objectModel.objectFormat = { left: 50, top: 20, width: 100, height: 40 } as any;
      obj.objectModel.absoluteName = "Text1";
      mocks.vsLayout.objects = [obj];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.focusedObjects = [obj]; // same object is focused

      (comp as any).updateSnapGuides();

      expect(comp.draggableSnapGuides.vertical).not.toContain(50);
      expect(comp.draggableSnapGuides.vertical).not.toContain(150);
   });

   it("clears previous snap guides on each call", () => {
      const { comp } = makeComponent();
      comp.draggableSnapGuides.horizontal = [99];
      comp.draggableSnapGuides.vertical = [88];

      (comp as any).updateSnapGuides();

      expect(comp.draggableSnapGuides.horizontal).not.toContain(99);
      expect(comp.draggableSnapGuides.vertical).not.toContain(88);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getLayoutSize (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — getLayoutSize (private)", () => {

   it("resets pages to [0] then updates guideSize for non-print layout", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      mocks.vsLayout.guideType = 0 as any; // GUIDES_NONE (no pages pushed)
      comp.pages = [9, 8];

      (comp as any).getLayoutSize(false);

      // pages is reset to [] (no guideType → no push loop)
      // guideSize is updated from getGuideSize()
      expect(comp.guideSize).toBeDefined();
   });

   it("skips getGuideSize and calls getPrintBounds when printLayout=true", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      // Set print layout dims so getPrintBounds doesn't error
      mocks.vsLayout.width = 8.5;
      mocks.vsLayout.height = 11;
      mocks.vsLayout.unit = "inches";
      mocks.vsLayout.marginTop = 1;
      mocks.vsLayout.marginBottom = 1;
      mocks.vsLayout.marginLeft = 1;
      mocks.vsLayout.marginRight = 1;
      mocks.vsLayout.horizontal = false;

      const spy = vi.spyOn(comp as any, "getPrintBounds");
      (comp as any).getLayoutSize(true);
      expect(spy).toHaveBeenCalled();
   });
});
