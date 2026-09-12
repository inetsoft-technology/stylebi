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
 * VSRangeSlider – Pass 1: Interaction
 *
 * Coverage:
 *   Group 1  - calculatePositions geometry: rangeLineWidth, widthBetweenTicks, handle positions, ticks array
 *   Group 2  - getCurrentLabel / getContainerLabel: upperInclusive, single-label, container all-selected vs partial
 *   Group 3  - mouseUp after Left handle drag: sendEvent when selection changed, no-op when unchanged
 *   Group 4  - mouseUp with Handle.None: no sendEvent (no drag)
 *   Group 5  - snapToSide: click left of left handle decreases selectStart; right of right increases selectEnd
 *   Group 6  - setMaxRange: sendEvent called once with update URL (server applies range, no local mutation)
 *   Group 7  - actions subscription: unselect → sendEvent; viewer-remove-from-container → removeChild
 *   Group 8  - navigate LEFT/RIGHT: moves left handle left/right by one tick
 *   Group 9  - toggleMaxMode via action: sendEvent with /max-mode/toggle; maxModeChange emitted
 *   Group 10 - onHide / onShow: sendEvent with /selectionContainer/update/ URL
 *   Group 11 - updateTitle: sendEvent with changeTitle URL; model.title updated
 *   Group 12 - title2ResizeEnd / updateTitleRatio: sendEvent with titleRatio URL
 *   Group 13 - ngOnDestroy: actionSubscription closed; adhocFilterListener cleanup called
 *   Group 14 - mouseDown: isMouseDown=true + mouseHandle set on button-1; guard skips when selectStart=selectEnd
 *   Group 15 - showAdvancedPaneDialog: modelService.getModel called with property URI; ComponentTool.showDialog opened
 */

import { EventEmitter } from "@angular/core";
import { of } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { RangeSliderActions } from "../../action/range-slider-actions";
import { NavigationKeys } from "../navigation-keys";
import {
   createVSRangeSlider,
   stubViewChildRefs,
} from "./vs-range-slider.component.test-helpers";

describe("VSRangeSlider – interaction (P1)", () => {
   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1: calculatePositions geometry ────────────────────────────────

   describe("Group 1 – calculatePositions geometry", () => {
      it("should compute rangeLineWidth as maxRangeBarWidth - 2*pointerOffset - 1", () => {
         const { comp } = createVSRangeSlider();
         // 200 - (2×4) - 1 = 191
         expect(comp.rangeLineWidth).toBe(191);
      });

      it("should compute widthBetweenTicks as rangeLineWidth / (labels.length - 1)", () => {
         const { comp } = createVSRangeSlider();
         // 191 / 4 = 47.75
         expect(comp.widthBetweenTicks).toBe(47.75);
      });

      it("should set leftHandlePosition to selectStart × widthBetweenTicks", () => {
         const { comp } = createVSRangeSlider();
         // 1 × 47.75 = 47.75 (mobilePadding=0 since context.viewer=false)
         expect(comp.leftHandlePosition).toBe(47.75);
      });

      it("should set rightHandlePosition to selectEnd × widthBetweenTicks", () => {
         const { comp } = createVSRangeSlider();
         // 3 × 47.75 = 143.25
         expect(comp.rightHandlePosition).toBe(143.25);
      });

      it("should produce ticks array with one entry per label", () => {
         const { comp } = createVSRangeSlider();
         // 5 labels → 5 tick positions
         expect(comp.ticks).toHaveLength(5);
      });
   });

   // ─── Group 2: getCurrentLabel / getContainerLabel ────────────────────────

   describe("Group 2 – getCurrentLabel / getContainerLabel", () => {
      it("should return 'start..end' when upperInclusive=true", () => {
         const { comp } = createVSRangeSlider({ upperInclusive: true });
         // selectStart=1 → "B", selectEnd=3 → "D"
         expect(comp.currentLabel).toBe("B..D");
      });

      it("should return 'start->end' when upperInclusive=false", () => {
         const { comp } = createVSRangeSlider({ upperInclusive: false });
         expect(comp.currentLabel).toBe("B->D");
      });

      it("should return single label string when labels has one element", () => {
         const { comp } = createVSRangeSlider({
            labels: ["OnlyLabel"],
            values: ["42"],
            selectStart: 0,
            selectEnd: 0,
         });
         expect(comp.currentLabel).toBe("OnlyLabel");
      });

      it("should return '(none)' as containerLabel when in container with full range selected", () => {
         const { comp } = createVSRangeSlider({
            container: "Container1",
            containerType: "VSSelectionContainer",
            selectStart: 0,
            selectEnd: 4, // labels.length - 1
         });
         expect(comp.containerLabel).toBe("(none)");
      });

      it("should return current label as containerLabel when in container but partial range", () => {
         const { comp } = createVSRangeSlider({
            container: "Container1",
            containerType: "VSSelectionContainer",
            selectStart: 1,
            selectEnd: 3,
         });
         expect(comp.containerLabel).toBe("B..D");
      });
   });

   // ─── Group 3: mouseUp after Left handle drag ──────────────────────────────

   describe("Group 3 – Left handle drag triggers sendEvent via updateSelections", () => {
      it("should call sendEvent when Left handle moved and selection changed", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp["startingSelectStart"] = comp.model.selectStart; // 1
         comp["startingSelectEnd"] = comp.model.selectEnd;     // 3
         comp["mouseHandle"] = comp.handleType.Left;
         comp.isMouseDown = true;
         comp.model.selectStart = 0; // changed from 1 → selectionChanged=true

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionList/update/"),
            expect.anything(),
         );
      });

      it("should not call sendEvent when Left handle used but selection unchanged", () => {
         // no-change path — moved=true but selectionChanged=false
         // Complement: selection-changed path covered by test 1 above
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         comp["mouseHandle"] = comp.handleType.Left;
         comp.isMouseDown = true;
         // selectStart/selectEnd not changed → selectionChanged=false

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });
   });

   // ─── Group 4: mouseUp with Handle.None ────────────────────────────────────

   describe("Group 4 – mouseUp with Handle.None does not send event", () => {
      it("should not call sendEvent when mouseHandle is None (no drag)", () => {
         // moved=false path → updateSelections skipped entirely
         // Complement: moved=true path covered in Group 3 test 1
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.None;
         comp.isMouseDown = true;

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });
   });

   // ─── Group 5: snapToSide ─────────────────────────────────────────────────

   describe("Group 5 – snapToSide", () => {
      it("should decrease selectStart when click is left of left handle", () => {
         const { comp } = createVSRangeSlider();
         // leftHandlePosition ≈ 47.75 — click at offsetX=10
         comp.snapToSide({ offsetX: 10 } as MouseEvent);
         expect(comp.model.selectStart).toBe(0);
      });

      it("should increase selectEnd when click is right of right handle", () => {
         const { comp } = createVSRangeSlider();
         // rightHandlePosition ≈ 143.25 — click at offsetX=180
         comp.snapToSide({ offsetX: 180 } as MouseEvent);
         expect(comp.model.selectEnd).toBe(4);
      });

      it("should not change selectStart when click is within the range", () => {
         const { comp } = createVSRangeSlider();
         // Click between handles (47.75 – 143.25) → moveMiddle(0) changes nothing
         comp.snapToSide({ offsetX: 95 } as MouseEvent);
         expect(comp.model.selectStart).toBe(1);
      });
   });

   // ─── Group 6: setMaxRange ─────────────────────────────────────────────────

   describe("Group 6 – setMaxRange", () => {
      it("should call sendEvent with the update URL", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.setMaxRange();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionList/update/"),
            expect.anything(),
         );
      });

      it("should send the event exactly once", () => {
         // setMaxRange → updateSelections(0, 4) → updateSelections0 → checkFormData → sendEvent
         // Local model.selectStart/selectEnd are NOT mutated — server applies the new range
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.setMaxRange();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 7: actions event subscription ─────────────────────────────────

   describe("Group 7 – actions event subscription", () => {
      it("should call sendEvent when 'range-slider unselect' action fires", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;

         onAssemblyActionEvent.emit({ id: "range-slider unselect" });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionList/update/"),
            expect.anything(),
         );
      });

      it("should emit removeChild when 'range-slider viewer-remove-from-container' fires", () => {
         const { comp } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;

         const emitted: any[] = [];
         comp.removeChild.subscribe(() => emitted.push(true));
         onAssemblyActionEvent.emit({ id: "range-slider viewer-remove-from-container" });

         expect(emitted).toHaveLength(1);
      });
   });

   // ─── Group 8: navigate keyboard ──────────────────────────────────────────

   describe("Group 8 – navigate LEFT key moves left handle", () => {
      it("should decrease selectStart when LEFT pressed with Left handle active", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         // Pre-set Left handle active so navigate() skips the focus-setup early return
         comp["mouseHandle"] = comp.handleType.Left;

         comp["navigate"](NavigationKeys.LEFT);

         // widthBetweenTicks=47.75, leftPos 47.75-47.75=0 → selectStart=Math.round(0/47.75)=0
         expect(comp.model.selectStart).toBe(0);
      });

      it("should increase selectStart when RIGHT pressed with Left handle active", () => {
         const { comp } = createVSRangeSlider({ selectStart: 0, selectEnd: 3 });
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Left;

         comp["navigate"](NavigationKeys.RIGHT);

         // leftPos 0+47.75=47.75 → selectStart=Math.round(47.75/47.75)=1
         expect(comp.model.selectStart).toBe(1);
      });
   });

   // ─── Group 9: toggleMaxMode via action ────────────────────────────────────

   describe("Group 9 – toggleMaxMode via action event", () => {
      it("should call sendEvent with max-mode URL when 'range-slider open-max-mode' fires", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;

         onAssemblyActionEvent.emit({ id: "range-slider open-max-mode" });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/max-mode/toggle"),
            expect.anything(),
         );
      });

      it("should emit maxModeChange when 'range-slider open-max-mode' fires", () => {
         const { comp } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;

         const emitted: any[] = [];
         comp.maxModeChange.subscribe((e: any) => emitted.push(e));
         onAssemblyActionEvent.emit({ id: "range-slider open-max-mode" });

         expect(emitted).toHaveLength(1);
         expect(emitted[0].maxMode).toBe(true);
      });
   });

   // ─── Group 10: onHide / onShow ────────────────────────────────────────────

   describe("Group 10 – onHide / onShow", () => {
      it("should call sendEvent with selectionContainer URL on onHide()", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.onHide();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionContainer/update/"),
            expect.anything(),
         );
      });

      it("should call sendEvent with selectionContainer URL on onShow()", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.onShow();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionContainer/update/"),
            expect.anything(),
         );
      });
   });

   // ─── Group 11: updateTitle ────────────────────────────────────────────────

   describe("Group 11 – updateTitle", () => {
      it("should call sendEvent with changeTitle URL when not in viewer", () => {
         // context.viewer=false (default factory) → updateTitle sends event
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.updateTitle("New Title");
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("changeTitle"),
            expect.anything(),
         );
      });

      it("should update model.title to the new value", () => {
         const { comp } = createVSRangeSlider();
         comp.updateTitle("Renamed");
         expect(comp.model.title).toBe("Renamed");
      });
   });

   // ─── Group 12: title2ResizeEnd / updateTitleRatio ─────────────────────────

   describe("Group 12 – title2ResizeEnd / updateTitleRatio", () => {
      it("should call sendEvent with titleRatio URL on title2ResizeEnd()", () => {
         const { comp, viewsheetClient } = createVSRangeSlider();
         comp.model.titleRatio = 0.3;
         comp.title2ResizeEnd();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("titleRatio"),
            expect.anything(),
         );
      });
   });

   // ─── Group 13: ngOnDestroy ────────────────────────────────────────────────

   describe("Group 13 – ngOnDestroy cleanup", () => {
      it("should close actionSubscription on destroy", () => {
         const { comp } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;
         const sub = (comp as any).actionSubscription;

         comp.ngOnDestroy();

         expect(sub.closed).toBe(true);
      });

      it("should call adhocFilterListener cleanup on destroy when listener exists", () => {
         const { comp } = createVSRangeSlider();
         const listener = vi.fn();
         (comp as any).adhocFilterListener = listener;

         comp.ngOnDestroy();

         expect(listener).toHaveBeenCalledTimes(1);
      });

      it("should call showRangeSliderEditDialog when 'range-slider edit-range' action fires", () => {
         const { comp } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;
         const dialogSpy = vi.spyOn(comp as any, "showRangeSliderEditDialog")
            .mockImplementation(() => {});
         try {
            onAssemblyActionEvent.emit({ id: "range-slider edit-range" });
            expect(dialogSpy).toHaveBeenCalledTimes(1);
         } finally {
            dialogSpy.mockRestore();
         }
      });
   });

   // ─── Group 14: mouseDown ──────────────────────────────────────────────────

   describe("Group 14 – mouseDown lifecycle", () => {
      it("should set isMouseDown=true and mouseHandle when left button pressed", () => {
         const { comp } = createVSRangeSlider();
         const button1Spy = vi.spyOn(GuiTool, "isButton1" as any).mockReturnValue(true);
         const pageXSpy   = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(50);
         const isTouchSpy = vi.spyOn(GuiTool, "isTouch" as any).mockReturnValue(false);
         try {
            comp.mouseDown(new MouseEvent("mousedown") as any, comp.handleType.Left);
            expect(comp.isMouseDown).toBe(true);
            expect(comp["mouseHandle"]).toBe(comp.handleType.Left);
         } finally {
            button1Spy.mockRestore();
            pageXSpy.mockRestore();
            isTouchSpy.mockRestore();
         }
      });

      it("should NOT set isMouseDown when selectEnd equals selectStart (guard)", () => {
         // Guard: selectStart === selectEnd → early return, no state mutation
         // Complement: normal path covered by test 1 above
         const { comp } = createVSRangeSlider({ selectStart: 2, selectEnd: 2 });

         comp.mouseDown(new MouseEvent("mousedown") as any, comp.handleType.Left);

         expect(comp.isMouseDown).toBe(false);
      });
   });

   // ─── Group 15: showAdvancedPaneDialog ─────────────────────────────────────

   describe("Group 15 – showAdvancedPaneDialog via 'viewer-advanced-pane' action", () => {
      it("should call modelService.getModel with range-slider-property URI", () => {
         const { comp, modelService } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;
         const dialogMock = {
            model: null, advancedPaneOnly: false, scriptTreeModel: null,
            variableValues: null, runtimeId: null, assemblyName: null,
         };
         modelService.getModel.mockReturnValueOnce(of([{}, null]));
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog")
            .mockReturnValue(dialogMock as any);
         try {
            onAssemblyActionEvent.emit({ id: "range-slider viewer-advanced-pane" });
            expect(modelService.getModel).toHaveBeenCalledWith(
               expect.stringContaining("range-slider-property-dialog-model"),
            );
         } finally {
            showDialogSpy.mockRestore();
         }
      });

      it("should open ComponentTool.showDialog after getModel resolves", () => {
         const { comp, modelService } = createVSRangeSlider();
         const onAssemblyActionEvent = new EventEmitter<any>();
         comp.actions = { onAssemblyActionEvent } as unknown as RangeSliderActions;
         const dialogMock = {
            model: null, advancedPaneOnly: false, scriptTreeModel: null,
            variableValues: null, runtimeId: null, assemblyName: null,
         };
         modelService.getModel.mockReturnValueOnce(of([{}, null]));
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog")
            .mockReturnValue(dialogMock as any);
         try {
            onAssemblyActionEvent.emit({ id: "range-slider viewer-advanced-pane" });
            expect(showDialogSpy).toHaveBeenCalledTimes(1);
         } finally {
            showDialogSpy.mockRestore();
         }
      });
   });
});
