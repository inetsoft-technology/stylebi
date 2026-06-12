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
 * ComponentsPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — updateRoots: null guard; object in-layout vs not-in-layout node
 *                       attributes (dragName/dragData/cssClass); VSAnnotation filtering;
 *                       alphabetical sort
 *   Group 2 [Risk 2] — dragNode: "object-exist" guard shows warning and blocks createDragImage;
 *                       newObject type maps to correct i18n label
 *   Group 3 [Risk 1] — ngOnInit: contentToolBox has PageBreak child with correct dragData
 *   Group 4 [Risk 1] — ngOnChanges: inactive=true → detach; inactive=false → reattach
 *   Group 5 [Risk 1] — Output emitters (copy/cut/remove/bringToFront/sendToBack/clearFocused)
 *   Group 6 [Risk 1] — nodesSelected: clears focused objects; selects matched layout object
 *   Group 7 [Risk 1] — getters layoutMode, showToolBox, showContentToolBox (both directions)
 *
 * Out of scope: private helpers (findLayoutObject, getIcon, getDatatipSource,
 *   getPopComponentSource, getDatatipAndPopCompHint, isDatatipOrPopComp) — covered transitively
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, EMPTY } from "rxjs";

import { ComponentsPane } from "./components-pane.component";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { DomService } from "../../../widget/dom-service/dom.service";
import { AssemblyType } from "../vs/assembly-type";
import { PrintLayoutSection } from "../../../vsobjects/model/layout/print-layout-section";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const MODAL_MOCK = {
   open: vi.fn().mockReturnValue({
      result: EMPTY,
      componentInstance: { onCommit: new Subject() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }),
};

async function renderComponent() {
   const { fixture } = await render(ComponentsPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DomService, useValue: {} },
      ],
   });
   return fixture.componentInstance as ComponentsPane;
}

function makeLayout(overrides: {
   objects?: any[];
   headerObjects?: any[];
   footerObjects?: any[];
   currentPrintSection?: PrintLayoutSection;
   printLayout?: boolean;
} = {}): any {
   return {
      objects: overrides.objects ?? [],
      headerObjects: overrides.headerObjects ?? [],
      footerObjects: overrides.footerObjects ?? [],
      currentPrintSection: overrides.currentPrintSection ?? PrintLayoutSection.HEADER,
      printLayout: overrides.printLayout ?? false,
      clearFocusedObjects: vi.fn(),
      selectObject: vi.fn(),
   };
}

function makeObjectTree(items: Array<{ objectType: string; absoluteName: string } | null> = []): any {
   return {
      children: items.map(item =>
         item
            ? { model: { objectType: item.objectType, absoluteName: item.absoluteName } }
            : { model: null },
      ),
   };
}

function makeDragEvent(data: object): any {
   return {
      dataTransfer: { getData: vi.fn().mockReturnValue(JSON.stringify(data)) },
   };
}

beforeEach(() => MODAL_MOCK.open.mockClear());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: updateRoots — null guard + node attributes
// ---------------------------------------------------------------------------

describe("ComponentsPane — updateRoots", () => {

   it("should set componentRoot and toolBox to null when objectTree is not set", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout();
      comp.objectTree = null as any;
      comp.updateRoots();
      expect(comp.componentRoot).toBeNull();
      expect(comp.toolBox).toBeNull();
   });

   it("should set componentRoot and toolBox to null when layout is not set", async () => {
      const comp = await renderComponent();
      comp.objectTree = makeObjectTree([{ objectType: "VSTable", absoluteName: "T1" }]);
      comp.layout = null as any;
      comp.updateRoots();
      expect(comp.componentRoot).toBeNull();
      expect(comp.toolBox).toBeNull();
   });

   // 🔁 Regression-sensitive: dragData=null for already-placed objects prevents double-insert
   // on drop; cssClass=text-muted gives the user a visual disabled cue.
   it("should mark object already in layout with dragName=object-exist, dragData=null, cssClass=text-muted", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ objects: [{ objectModel: { absoluteName: "ChartA" } }] });
      comp.objectTree = makeObjectTree([{ objectType: "VSChart", absoluteName: "ChartA" }]);
      comp.updateRoots();
      const node = comp.componentRoot!.children[0];
      expect(node.dragName).toBe("object-exist");
      expect(node.dragData).toBeNull();
      expect(node.cssClass).toBe("text-muted");
   });

   // 🔁 Regression-sensitive: dragData=absoluteName is required so the layout drop handler
   // knows which assembly to place.
   it("should mark object NOT in layout with dragName=object, dragData=absoluteName, cssClass=null", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout();
      comp.objectTree = makeObjectTree([{ objectType: "VSChart", absoluteName: "ChartB" }]);
      comp.updateRoots();
      const node = comp.componentRoot!.children[0];
      expect(node.dragName).toBe("object");
      expect(node.dragData).toBe("ChartB");
      expect(node.cssClass).toBeNull();
   });

   it("should filter out VSAnnotation nodes and null-model entries", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout();
      comp.objectTree = makeObjectTree([
         null,
         { objectType: "VSAnnotation", absoluteName: "Anno1" },
         { objectType: "VSTable", absoluteName: "TableX" },
      ]);
      comp.updateRoots();
      expect(comp.componentRoot!.children).toHaveLength(1);
      expect(comp.componentRoot!.children[0].label).toBe("TableX");
   });

   it("should sort component nodes alphabetically by label", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout();
      comp.objectTree = makeObjectTree([
         { objectType: "VSTable", absoluteName: "Zebra" },
         { objectType: "VSTable", absoluteName: "Alpha" },
         { objectType: "VSTable", absoluteName: "Mango" },
      ]);
      comp.updateRoots();
      const labels = comp.componentRoot!.children.map(n => n.label);
      expect(labels).toEqual(["Alpha", "Mango", "Zebra"]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: dragNode
// ---------------------------------------------------------------------------

describe("ComponentsPane — dragNode", () => {

   // 🔁 Regression-sensitive: guard must return early so no drag ghost is attached for
   // already-placed objects — a ghost would mislead users into thinking the drop will succeed.
   it("should show warning dialog and NOT call createDragImage when dragName is object-exist", async () => {
      const comp = await renderComponent();
      const showSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const createSpy = vi.spyOn(GuiTool, "createDragImage").mockReturnValue(null as any);

      comp.dragNode(makeDragEvent({ dragName: "object-exist", object: null }));

      expect(showSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Warning)",
         "_#(js:viewer.viewsheet.layout.objectExists)",
      );
      expect(createSpy).not.toHaveBeenCalled();
   });

   it("should pass ['_#(js:Image)'] to createDragImage when newObject is IMAGE_ASSET", async () => {
      const comp = await renderComponent();
      vi.spyOn(GuiTool, "createDragImage").mockReturnValue(document.createElement("div"));
      vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();

      comp.dragNode(makeDragEvent({ dragName: "newObject", newObject: String(AssemblyType.IMAGE_ASSET), object: null }));

      expect(GuiTool.createDragImage).toHaveBeenCalledWith(["_#(js:Image)"], "newObject");
   });

   it("should pass ['_#(js:Text)'] to createDragImage when newObject is TEXT_ASSET", async () => {
      const comp = await renderComponent();
      vi.spyOn(GuiTool, "createDragImage").mockReturnValue(document.createElement("div"));
      vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();

      comp.dragNode(makeDragEvent({ dragName: "newObject", newObject: String(AssemblyType.TEXT_ASSET), object: null }));

      expect(GuiTool.createDragImage).toHaveBeenCalledWith(["_#(js:Text)"], "newObject");
   });

   it("should use srcData.object labels directly when provided", async () => {
      const comp = await renderComponent();
      vi.spyOn(GuiTool, "createDragImage").mockReturnValue(document.createElement("div"));
      vi.spyOn(GuiTool, "setDragImage").mockResolvedValue();

      comp.dragNode(makeDragEvent({ dragName: "object", object: ["MyAssembly"] }));

      expect(GuiTool.createDragImage).toHaveBeenCalledWith(["MyAssembly"], "object");
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit
// ---------------------------------------------------------------------------

describe("ComponentsPane — ngOnInit", () => {

   it("should create contentToolBox with a single PageBreak child on init", async () => {
      const comp = await renderComponent();
      expect(comp.contentToolBox).toBeDefined();
      expect(comp.contentToolBox!.label).toBe("_#(js:Toolbox)");
      expect(comp.contentToolBox!.children).toHaveLength(1);
      expect(comp.contentToolBox!.children[0].label).toBe("_#(js:PageBreak)");
      expect(comp.contentToolBox!.children[0].dragData).toBe(String(AssemblyType.PAGEBREAK_ASSET));
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnChanges — inactive flag
// ---------------------------------------------------------------------------

describe("ComponentsPane — ngOnChanges inactive", () => {

   it("should call changeDetector.detach() when inactive is true", async () => {
      const comp = await renderComponent();
      const detachSpy = vi.spyOn((comp as any).changeDetector, "detach");
      comp.inactive = true;
      comp.ngOnChanges({} as any);
      expect(detachSpy).toHaveBeenCalledTimes(1);
   });

   it("should call changeDetector.reattach() when inactive is false", async () => {
      const comp = await renderComponent();
      const reattachSpy = vi.spyOn((comp as any).changeDetector, "reattach");
      comp.inactive = false;
      comp.ngOnChanges({} as any);
      expect(reattachSpy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: Output emitters + click
// ---------------------------------------------------------------------------

describe("ComponentsPane — output emitters and click", () => {

   const MOCK_MODEL: any = { objectType: "VSChart", absoluteName: "Chart1" };

   it("should emit onCopy when copyAssembly is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onCopy.subscribe(spy);
      comp.copyAssembly(MOCK_MODEL);
      expect(spy).toHaveBeenCalledWith(MOCK_MODEL);
   });

   it("should emit onCut when cutAssembly is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onCut.subscribe(spy);
      comp.cutAssembly(MOCK_MODEL);
      expect(spy).toHaveBeenCalledWith(MOCK_MODEL);
   });

   it("should emit onRemove when removeAssembly is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onRemove.subscribe(spy);
      comp.removeAssembly(MOCK_MODEL);
      expect(spy).toHaveBeenCalledWith(MOCK_MODEL);
   });

   it("should emit onBringToFront when bringAssemblyToFront is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onBringToFront.subscribe(spy);
      comp.bringAssemblyToFront(MOCK_MODEL);
      expect(spy).toHaveBeenCalledWith(MOCK_MODEL);
   });

   it("should emit onSendToBack when sendAssemblyToBack is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onSendToBack.subscribe(spy);
      comp.sendAssemblyToBack(MOCK_MODEL);
      expect(spy).toHaveBeenCalledWith(MOCK_MODEL);
   });

   it("should emit onClearFocusedObjects with true when click() is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onClearFocusedObjects.subscribe(spy);
      comp.click({} as MouseEvent);
      expect(spy).toHaveBeenCalledWith(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: nodesSelected
// ---------------------------------------------------------------------------

describe("ComponentsPane — nodesSelected", () => {

   it("should call clearFocusedObjects and selectObject for matched layout objects", async () => {
      const comp = await renderComponent();
      const layoutObj: any = { objectModel: { absoluteName: "Table1" } };
      comp.layout = makeLayout({ objects: [layoutObj] });
      comp.nodesSelected([{ label: "Table1" } as any]);
      expect(comp.layout.clearFocusedObjects).toHaveBeenCalledTimes(1);
      expect(comp.layout.selectObject).toHaveBeenCalledWith(layoutObj);
   });

   it("should call clearFocusedObjects but not selectObject when node has no matching layout object", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ objects: [] });
      comp.nodesSelected([{ label: "NotInLayout" } as any]);
      expect(comp.layout.clearFocusedObjects).toHaveBeenCalledTimes(1);
      expect(comp.layout.selectObject).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7: getters
// ---------------------------------------------------------------------------

describe("ComponentsPane — getters", () => {

   it("should return false when layout is null and true when layout is set (layoutMode)", async () => {
      const comp = await renderComponent();
      comp.layout = null as any;
      expect(comp.layoutMode).toBe(false);
      comp.layout = makeLayout();
      expect(comp.layoutMode).toBe(true);
   });

   it("should return true for showToolBox when layout exists and section is not CONTENT", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ currentPrintSection: PrintLayoutSection.HEADER });
      expect(comp.showToolBox).toBe(true);
   });

   it("should return false for showToolBox when layout section is CONTENT", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ currentPrintSection: PrintLayoutSection.CONTENT });
      expect(comp.showToolBox).toBe(false);
   });

   it("should return true for showContentToolBox when printLayout=true and section=CONTENT", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ printLayout: true, currentPrintSection: PrintLayoutSection.CONTENT });
      expect(comp.showContentToolBox).toBe(true);
   });

   it("should return false for showContentToolBox when printLayout=false", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ printLayout: false, currentPrintSection: PrintLayoutSection.CONTENT });
      expect(comp.showContentToolBox).toBe(false);
   });

   it("should return false for showContentToolBox when section is not CONTENT", async () => {
      const comp = await renderComponent();
      comp.layout = makeLayout({ printLayout: true, currentPrintSection: PrintLayoutSection.HEADER });
      expect(comp.showContentToolBox).toBe(false);
   });
});
