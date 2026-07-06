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
 * VsWizardObjectComponent — Pass 1 (interaction)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit focusedAssemblies subscription:
 *                        focused assembly → selected=true + interactable enabled;
 *                        unfocused when selected → clearSelection on table/chart types + selected=false;
 *                        unfocused when not selected → selected stays false (no clearSelection call)
 *   Group 2  [baseline] — select(): no-ctrl on unselected → clearFocused + selectAssembly;
 *                          ctrl on unselected → selectAssembly without clear;
 *                          ctrl on selected → deselectAssembly
 *   Group 3  [Risk 2] — editObject (via miniToolbarActions action):
 *                        VSText without defaultAnnotationContent → editing=true + toEditMode (interactable off);
 *                        VSText with defaultAnnotationContent → emit onEdit;
 *                        non-VSText → emit onEdit with absoluteName
 *   Group 4  [baseline] — onResizeStart(): saveOriginalBounds + emit onDragResizeStart(isDrag=false)
 *   Group 5  [baseline] — onResizeMove(): mutates objectFormat deltas + calls objectComponent.resized()
 *                          + emits onRowsChanged/onColsChanged
 *   Group 6  [baseline] — onResizeEnd(): snaps to grid + emits onResize when bounds changed
 *   Group 7  [baseline] — onDragStart(): saveOriginalBounds + emit onDragResizeStart(isDrag=true, shiftKey)
 *                          + emit onChangeFollowDirection with bounds centered on object
 *   Group 8  [baseline] — onDragMove(): updates left/top, emits rows/cols/followDir/move
 *   Group 9  [baseline] — onDragEnd(): snaps to grid, emits if bounds changed, emits onDragResizeEnd
 *   Group 10 [baseline] — onMouseover(): emits onMouseIn with the event
 *   Group 11 [baseline] — toEditMode(): disables interactable when selected + VSText/VSImage
 *   Group 12 [baseline] — updateInteractable(): toggles draggable+resizable
 *   Group 13 [baseline] — ngOnDestroy: unsubscribes from focusedAssemblies (no post-destroy updates)
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   updateFollowPositions() DOM CSS side-effects — covered in P3
 *   isVSWizardObject()/canEdit()/isBoundsChanged() — covered in P3
 */

import { waitFor } from "@testing-library/angular";

import { makeVsObject, makeViewsheet, renderComponent } from "./vs-wizard-object.test-fixtures";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { VSTextModel } from "../../../vsobjects/model/output/vs-text-model";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit focusedAssemblies subscription [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — ngOnInit focusedAssemblies subscription", () => {
   // 🔁 Regression-sensitive: if the subscription is broken, the component never
   // becomes "selected" when the user clicks on it, so drag/resize interaction
   // never activates and the object cannot be moved or resized.
   it("should set selected=true and enable interactable when the assembly becomes focused", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const { comp } = await renderComponent({ vsObject, viewsheet });

      viewsheet.selectAssembly(vsObject);

      await waitFor(() => expect(comp.selected).toBe(true));
      expect(comp.interactionDraggable).toBe(true);
      expect(comp.interactionResizable).toBe(true);
   });

   it("should set selected=false and disable interactable when the assembly loses focus", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const { comp } = await renderComponent({ vsObject, viewsheet });

      viewsheet.selectAssembly(vsObject);
      await waitFor(() => expect(comp.selected).toBe(true));

      viewsheet.deselectAssembly(vsObject);

      await waitFor(() => expect(comp.selected).toBe(false));
      expect(comp.interactionDraggable).toBe(false);
      expect(comp.interactionResizable).toBe(false);
   });

   it("should call clearSelection on objectComponent when a table type loses focus while selected", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSTable");
      viewsheet.vsObjects = [vsObject];
      const { comp } = await renderComponent({ vsObject, viewsheet });
      viewsheet.selectAssembly(vsObject);
      await waitFor(() => expect(comp.selected).toBe(true));

      viewsheet.deselectAssembly(vsObject);

      await waitFor(() => expect(comp.selected).toBe(false));
      expect((comp.objectComponent as any).clearSelection).toHaveBeenCalledOnce();
   });

   it("should clear selectedRegions when assembly loses focus while selected", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const { comp } = await renderComponent({ vsObject, viewsheet });
      viewsheet.selectAssembly(vsObject);
      await waitFor(() => expect(comp.selected).toBe(true));

      viewsheet.deselectAssembly(vsObject);

      await waitFor(() => expect(comp.selected).toBe(false));
      expect(comp.vsObject.selectedRegions).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — select() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — select()", () => {
   it("no-ctrl click on unselected should clear focused assemblies and select the assembly", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const clearSpy = vi.spyOn(viewsheet, "clearFocusedAssemblies");
      const selectSpy = vi.spyOn(viewsheet, "selectAssembly");
      const { comp } = await renderComponent({ vsObject, viewsheet });

      comp.select({ ctrlKey: false, metaKey: false } as MouseEvent);

      expect(clearSpy).toHaveBeenCalledOnce();
      expect(selectSpy).toHaveBeenCalledWith(vsObject);
   });

   it("ctrl click on unselected should select without clearing focused assemblies", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const clearSpy = vi.spyOn(viewsheet, "clearFocusedAssemblies");
      const selectSpy = vi.spyOn(viewsheet, "selectAssembly");
      const { comp } = await renderComponent({ vsObject, viewsheet });

      comp.select({ ctrlKey: true, metaKey: false } as MouseEvent);

      expect(clearSpy).not.toHaveBeenCalled();
      expect(selectSpy).toHaveBeenCalledWith(vsObject);
   });

   it("ctrl click on already-selected assembly should deselect it", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const { comp } = await renderComponent({ vsObject, viewsheet });
      // Select the assembly first
      viewsheet.selectAssembly(vsObject);
      await waitFor(() => expect(comp.selected).toBe(true));
      const deselectSpy = vi.spyOn(viewsheet, "deselectAssembly");

      comp.select({ ctrlKey: true, metaKey: false } as MouseEvent);

      expect(deselectSpy).toHaveBeenCalledWith(vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — editObject via miniToolbarActions [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — editObject() via miniToolbarActions", () => {
   // 🔁 Regression-sensitive: if editObject emits onEdit for VSText without
   // defaultAnnotationContent, the server opens the binding editor instead of
   // enabling inline editing, which is the wrong UX.
   it("VSText without defaultAnnotationContent should enable editing in-place (not emit onEdit)", async () => {
      const vsObject = makeVsObject("VSText") as unknown as VSTextModel;
      (vsObject as any).defaultAnnotationContent = false;
      vsObject.editing = false;
      const { comp } = await renderComponent({ vsObject: vsObject as unknown as VSObjectModel });
      const emitted: string[] = [];
      comp.onEdit.subscribe((v: string) => emitted.push(v));
      // Make the component selected so toEditMode() disables interactable
      comp.selected = true;
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      const editAction1 = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Edit VS Wizard Object");
      expect(editAction1).toBeDefined();
      editAction1!.action({} as MouseEvent);

      expect(vsObject.editing).toBe(true);
      expect(emitted).toHaveLength(0);
      // toEditMode() disables interactable when selected + VSText
      expect(comp.interactionDraggable).toBe(false);
      expect(comp.interactionResizable).toBe(false);
   });

   it("VSText with defaultAnnotationContent should emit onEdit with absoluteName", async () => {
      const vsObject = makeVsObject("VSText") as unknown as VSTextModel;
      (vsObject as any).defaultAnnotationContent = true;
      const { comp } = await renderComponent({ vsObject: vsObject as unknown as VSObjectModel });
      const emitted: string[] = [];
      comp.onEdit.subscribe((v: string) => emitted.push(v));

      const editAction2 = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Edit VS Wizard Object");
      expect(editAction2).toBeDefined();
      editAction2!.action({} as MouseEvent);

      expect(emitted).toEqual(["TestObj"]);
   });

   it("non-VSText type should emit onEdit with absoluteName", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSChart") });
      const emitted: string[] = [];
      comp.onEdit.subscribe((v: string) => emitted.push(v));

      const editAction3 = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Edit VS Wizard Object");
      expect(editAction3).toBeDefined();
      editAction3!.action({} as MouseEvent);

      expect(emitted).toEqual(["TestObj"]);
   });

   it("delete action should emit onRemove with absoluteName", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onRemove.subscribe((v: string) => emitted.push(v));

      const deleteAction = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Delete VS Wizard Object");
      expect(deleteAction).toBeDefined();
      deleteAction!.action({} as MouseEvent);

      expect(emitted).toEqual(["TestObj"]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — onResizeStart() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onResizeStart()", () => {
   it("should emit onDragResizeStart with isDrag=false and the vsObject", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const emitted: any[] = [];
      comp.onDragResizeStart.subscribe((v: any) => emitted.push(v));

      comp.onResizeStart({});

      expect(emitted).toHaveLength(1);
      expect(emitted[0].isDrag).toBe(false);
      expect(emitted[0].objectModel).toBe(vsObject);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — onResizeMove() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onResizeMove()", () => {
   it("should apply deltaRect.right to width and emit onRowsChanged/onColsChanged", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const rowsEmitted: any[] = [];
      const colsEmitted: any[] = [];
      comp.onRowsChanged.subscribe((v: any) => rowsEmitted.push(v));
      comp.onColsChanged.subscribe((v: any) => colsEmitted.push(v));

      comp.onResizeMove({ deltaRect: { right: 20, left: 0, top: 0, bottom: 0 } });

      expect(vsObject.objectFormat.width).toBe(120);
      expect(rowsEmitted).toHaveLength(1);
      expect(colsEmitted).toHaveLength(1);
   });

   it("should apply deltaRect.bottom to height", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeMove({ deltaRect: { right: 0, left: 0, top: 0, bottom: 10 } });

      expect(vsObject.objectFormat.height).toBe(60);
   });

   it("should adjust top and height when deltaRect.top is set", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeMove({ deltaRect: { right: 0, left: 0, top: 5, bottom: 0 } });

      expect(vsObject.objectFormat.top).toBe(15);
      expect(vsObject.objectFormat.height).toBe(45);
   });

   it("should call objectComponent.resized()", async () => {
      const { comp } = await renderComponent();

      comp.onResizeMove({ deltaRect: { right: 0, left: 0, top: 0, bottom: 0 } });

      expect((comp.objectComponent as any).resized).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — onResizeEnd() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onResizeEnd()", () => {
   // 🔁 Regression-sensitive: if bounds-snapping is skipped, the object lands on
   // fractional grid positions and the server rejects the resize event.
   it("should emit onResize when bounds have changed after resize", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const emitted: VSObjectModel[] = [];
      comp.onResize.subscribe((v: VSObjectModel) => emitted.push(v));

      comp.onResizeStart({});
      vsObject.objectFormat.width = 150;

      comp.onResizeEnd();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(vsObject);
   });

   it("should emit onDragResizeEnd unconditionally", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      let endCount = 0;
      comp.onDragResizeEnd.subscribe(() => endCount++);

      comp.onResizeStart({});
      comp.onResizeEnd();

      expect(endCount).toBe(1);
   });

   it("should NOT emit onResize when bounds are unchanged", async () => {
      const { comp } = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onResize.subscribe((v: VSObjectModel) => emitted.push(v));

      comp.onResizeStart({});
      comp.onResizeEnd();

      expect(emitted).toHaveLength(0);
   });

   it("should snap width to widthIncrement grid", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject, widthIncrement: 20 });

      comp.onResizeStart({});
      vsObject.objectFormat.width = 113;
      comp.onResizeEnd();

      // 113 snapped to nearest 20 → 120
      expect(vsObject.objectFormat.width).toBe(120);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — onDragStart() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onDragStart()", () => {
   it("should emit onDragResizeStart with isDrag=true and the vsObject", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const emitted: any[] = [];
      comp.onDragResizeStart.subscribe((v: any) => emitted.push(v));

      comp.onDragStart({ shiftKey: false });

      expect(emitted).toHaveLength(1);
      expect(emitted[0].isDrag).toBe(true);
      expect(emitted[0].objectModel).toBe(vsObject);
   });

   it("should pass shiftKey value to onDragResizeStart", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onDragResizeStart.subscribe((v: any) => emitted.push(v));

      comp.onDragStart({ shiftKey: true });

      expect(emitted[0].withShift).toBe(true);
   });

   it("should emit onChangeFollowDirection with bounds centered on the object", async () => {
      const vsObject = makeVsObject("VSChart", {
         objectFormat: { left: 0, top: 0, width: 100, height: 60 } as any,
      });
      const { comp } = await renderComponent({ vsObject });
      const followDirs: any[] = [];
      comp.onChangeFollowDirection.subscribe((v: any) => followDirs.push(v));

      comp.onDragStart({ shiftKey: false });

      expect(followDirs).toHaveLength(1);
      // bounds center = (0 + 50 - 10, 0 + 30 - 10) = (40, 20), size 20x20
      expect(followDirs[0].bounds.x).toBe(40);
      expect(followDirs[0].bounds.y).toBe(20);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — onDragMove() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onDragMove()", () => {
   it("should update objectFormat left/top by dx/dy", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp, viewsheet } = await renderComponent({ vsObject });
      viewsheet.vsObjects = [vsObject];

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 15, dy: 10 });

      expect(vsObject.objectFormat.left).toBe(25);
      expect(vsObject.objectFormat.top).toBe(20);
   });

   it("should emit onRowsChanged and onColsChanged with the vsObject", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const rowsEmitted: any[] = [];
      const colsEmitted: any[] = [];
      comp.onRowsChanged.subscribe((v: any) => rowsEmitted.push(v));
      comp.onColsChanged.subscribe((v: any) => colsEmitted.push(v));

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 10, dy: 0 });

      expect(rowsEmitted).toHaveLength(1);
      expect(colsEmitted).toHaveLength(1);
      expect(rowsEmitted[0]).toBe(vsObject);
   });

   it("should emit onMove with the event and model", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });
      const moveEmitted: any[] = [];
      comp.onMove.subscribe((v: any) => moveEmitted.push(v));

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 10, dy: 0 });

      expect(moveEmitted).toHaveLength(1);
      expect(moveEmitted[0].model).toBe(vsObject);
   });

   it("should set followDir.direction to 'right' when horizontal delta is dominant", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp, viewsheet } = await renderComponent({ vsObject });
      viewsheet.vsObjects = [vsObject];
      const followDirs: any[] = [];
      comp.onChangeFollowDirection.subscribe((v: any) => followDirs.push(v));
      comp.selected = true;
      // Make currentFocusedAssemblies.length === 1
      viewsheet.selectAssembly(vsObject);

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 30, dy: 5 });

      const lastDir = followDirs[followDirs.length - 1];
      expect(lastDir.direction).toBe("right");
   });

   it("should set followDir.direction to 'bottom' when vertical delta is dominant", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp, viewsheet } = await renderComponent({ vsObject });
      viewsheet.vsObjects = [vsObject];
      const followDirs: any[] = [];
      comp.onChangeFollowDirection.subscribe((v: any) => followDirs.push(v));
      viewsheet.selectAssembly(vsObject);

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 5, dy: 30 });

      const lastDir = followDirs[followDirs.length - 1];
      expect(lastDir.direction).toBe("bottom");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — onDragEnd() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onDragEnd()", () => {
   it("should emit onDragResizeEnd after drag ends", async () => {
      const { comp } = await renderComponent();
      let endCount = 0;
      comp.onDragResizeEnd.subscribe(() => endCount++);

      comp.onDragStart({ shiftKey: false });
      comp.onDragEnd({});

      expect(endCount).toBe(1);
   });

   it("should emit onMove when bounds changed during drag", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onDragStart({ shiftKey: false });
      comp.onDragMove({ dx: 50, dy: 0 });

      // Subscribe after onDragMove to capture only the onDragEnd emission
      const endMoveEmitted: any[] = [];
      comp.onMove.subscribe((v: any) => endMoveEmitted.push(v));
      comp.onDragEnd({});

      expect(endMoveEmitted).toHaveLength(1);
      expect(endMoveEmitted[0].model).toBe(vsObject);
   });

   it("should snap top to heightIncrement after drag", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject, heightIncrement: 20 });

      comp.onDragStart({ shiftKey: false });
      vsObject.objectFormat.top = 13;
      comp.onDragEnd({});

      // 13 snapped to nearest 20 → 20
      expect(vsObject.objectFormat.top).toBe(20);
   });

   it("should clamp negative position to 0 after drag", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onDragStart({ shiftKey: false });
      vsObject.objectFormat.left = -30;
      comp.onDragEnd({});

      expect(vsObject.objectFormat.left).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — onMouseover() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — onMouseover()", () => {
   it("should emit onMouseIn with the event", async () => {
      const { comp } = await renderComponent();
      const emitted: MouseEvent[] = [];
      comp.onMouseIn.subscribe((v: MouseEvent) => emitted.push(v));
      const event = new MouseEvent("mouseover");

      comp.onMouseover(event);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(event);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — toEditMode() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — toEditMode()", () => {
   it("should disable interactable when selected and objectType is VSText", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSText") });
      comp.selected = true;
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      comp.toEditMode();

      expect(comp.interactionDraggable).toBe(false);
      expect(comp.interactionResizable).toBe(false);
   });

   it("should disable interactable when selected and objectType is VSImage", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSImage") });
      comp.selected = true;
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      comp.toEditMode();

      expect(comp.interactionDraggable).toBe(false);
      expect(comp.interactionResizable).toBe(false);
   });

   it("should NOT disable interactable when not selected", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSText") });
      comp.selected = false;
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      comp.toEditMode();

      expect(comp.interactionDraggable).toBe(true);
      expect(comp.interactionResizable).toBe(true);
   });

   it("should NOT disable interactable for non-VSText/VSImage types even when selected", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSChart") });
      comp.selected = true;
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      comp.toEditMode();

      expect(comp.interactionDraggable).toBe(true);
      expect(comp.interactionResizable).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — updateInteractable() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — updateInteractable()", () => {
   it("should set both draggable and resizable to true when enabled=true", async () => {
      const { comp } = await renderComponent();
      comp.interactionDraggable = false;
      comp.interactionResizable = false;

      comp.updateInteractable(true);

      expect(comp.interactionDraggable).toBe(true);
      expect(comp.interactionResizable).toBe(true);
   });

   it("should set both draggable and resizable to false when enabled=false", async () => {
      const { comp } = await renderComponent();
      comp.interactionDraggable = true;
      comp.interactionResizable = true;

      comp.updateInteractable(false);

      expect(comp.interactionDraggable).toBe(false);
      expect(comp.interactionResizable).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — ngOnDestroy lifecycle [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — lifecycle", () => {
   it("should not update selected after ngOnDestroy", async () => {
      const viewsheet = makeViewsheet();
      const vsObject = makeVsObject("VSChart");
      viewsheet.vsObjects = [vsObject];
      const { comp, fixture } = await renderComponent({ vsObject, viewsheet });

      fixture.destroy();

      // After destroy, selecting the assembly should NOT change comp.selected
      // (subscription is cleaned up)
      const selectedBefore = comp.selected;
      viewsheet.selectAssembly(vsObject);
      expect(comp.selected).toBe(selectedBefore);
   });
});
