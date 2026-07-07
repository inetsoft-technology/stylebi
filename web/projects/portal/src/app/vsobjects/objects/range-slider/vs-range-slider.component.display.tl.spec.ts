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
 * VSRangeSlider – Pass 3: Display / Rendering
 *
 * Coverage:
 *   Group 1  - getMinLabel / getMaxLabel: first and last label returned
 *   Group 2  - getBodyHeight: objectFormat.height when standalone; minus titleFormat.height in container
 *   Group 3  - ticks positions: first tick at pointerOffset; spacing equals widthBetweenTicks
 *   Group 4  - showToolbar: true when adhocFilter or selectionContainer; falsy otherwise
 *   Group 5  - getLabelPosition center path: midpoint minus half objectWidth plus offset
 *   Group 6  - sliderTop from setTopPositions: ceil(height/2)-4 standalone; uses bodyHeight in container
 *   Group 7  - extractTimeIncrement: brace-type prefix; colon→t; 0 dashes→y; 1 dash→m; 2 dashes→d
 *   Group 8  - toIncrementTimestamp: y→Jan-1; m→1st of month; d→date midnight; t→full timestamp
 *   Group 9  - labelToTimestamp: y/m/d/t plain strings; brace-escaped date string
 *   Group 10 - navigate keyboard matrix: first-entry (None→Left); DOWN cycles 2→0; UP wraps 0→2
 *   Group 11 - getDateStrings y/m/d/t: year appends -01-01/-12-31; month computes last day; d/t passthrough
 *   Group 12 - focusSelectedHandle: blur+focus Left/Right/Middle based on mouseHandle
 *   Group 13 - getLabelPosition overflow: leftOverflow<0 shifts right; rightOverflow<0 shifts left
 *   Group 14 - clearNavSelection: mouseHandle→None; menuFocus→NONE(-5); keyNav→false
 *   Group 15 - Legacy DOM regressions ported verbatim from vs-range-slider.component.spec.ts
 *              (bugs #18972, #20993): uses a real TestBed render (not direct instantiation)
 *              because these assertions are about real template output (the objectFormat
 *              text-decoration binding on the body, and the sliderTop-driven top style on
 *              the range-slider/range-line/current-value/range-value elements) that cannot
 *              be observed on a directly-instantiated component.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { NEVER } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { ContextProvider } from "../../context-provider.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { GlobalSubmitService } from "../../util/global-submit.service";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { TimerService } from "../data-tip/timer.service";
import { NavigationKeys } from "../navigation-keys";
import { VSRangeSlider } from "./vs-range-slider.component";
import {
   createVSRangeSlider,
   makeVSRangeSliderModel,
   stubViewChildRefs,
} from "./vs-range-slider.component.test-helpers";

describe("VSRangeSlider – display / rendering (P3)", () => {
   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1: getMinLabel / getMaxLabel ───────────────────────────────────

   describe("Group 1 – getMinLabel / getMaxLabel", () => {
      it("should return first label as minLabel", () => {
         const { comp } = createVSRangeSlider();
         expect(comp.minLabel).toBe("A");
      });

      it("should return last label as maxLabel", () => {
         const { comp } = createVSRangeSlider();
         expect(comp.maxLabel).toBe("E");
      });
   });

   // ─── Group 2: getBodyHeight ───────────────────────────────────────────────

   describe("Group 2 – getBodyHeight", () => {
      it("should return objectFormat.height when not in a selection container", () => {
         const { comp } = createVSRangeSlider();
         // model.containerType is not VSSelectionContainer by default
         expect(comp.bodyHeight).toBe(comp.model.objectFormat.height);
      });

      it("should return objectFormat.height minus titleFormat.height in a selection container", () => {
         const { comp } = createVSRangeSlider({
            containerType: "VSSelectionContainer",
         });
         // 80 - 30 = 50
         expect(comp.bodyHeight).toBe(50);
      });
   });

   // ─── Group 3: ticks positions ─────────────────────────────────────────────

   describe("Group 3 – ticks positions", () => {
      it("should compute first tick at pointerOffset (4)", () => {
         const { comp } = createVSRangeSlider();
         // ticks[0] = pointerOffset + 0 * widthBetweenTicks = 4
         expect(comp.ticks[0]).toBe(4);
      });

      it("should space ticks by widthBetweenTicks", () => {
         const { comp } = createVSRangeSlider();
         // ticks[1] - ticks[0] should equal widthBetweenTicks=47.75
         expect(comp.ticks[1] - comp.ticks[0]).toBe(47.75);
      });
   });

   // ─── Group 4: showToolbar flag ────────────────────────────────────────────

   describe("Group 4 – showToolbar", () => {
      it("should set showToolbar=true when adhocFilter=true", () => {
         const { comp } = createVSRangeSlider({ adhocFilter: true });
         expect(comp.showToolbar).toBe(true);
      });

      it("should set showToolbar=true when in a selection container", () => {
         const { comp } = createVSRangeSlider({
            container: "Container1",
            containerType: "VSSelectionContainer",
         });
         expect(comp.showToolbar).toBe(true);
      });

      it("should set showToolbar to a falsy value when neither adhocFilter nor selection container", () => {
         // model.adhocFilter=false, model.container=null → false || null = null (falsy)
         const { comp } = createVSRangeSlider();
         expect(comp.showToolbar).toBeFalsy();
      });
   });

   // ─── Group 5: getLabelPosition ────────────────────────────────────────────

   describe("Group 5 – getLabelPosition", () => {
      it("should return a finite number as label position", () => {
         const { comp } = createVSRangeSlider();
         const pos = comp.getLabelPosition();
         expect(isFinite(pos)).toBe(true);
      });

      it("should center label between left and right handle midpoint", () => {
         const { comp } = createVSRangeSlider();
         // Center = (rightPos - leftPos) / 2 + leftPos - objectWidth/2 + 4
         // = (143.25 - 47.75) / 2 + 47.75 - 220/2 + 4
         // = 47.75 + 47.75 - 110 + 4 = -10.5
         // (no overflow adjustment since textSize=10 is small)
         expect(comp.getLabelPosition()).toBeCloseTo(-10.5, 1);
      });
   });

   // ─── Group 6: sliderTop (setTopPositions) ─────────────────────────────────

   describe("Group 6 – sliderTop from setTopPositions", () => {
      it("should set sliderTop based on objectFormat.height when not in container", () => {
         const { comp } = createVSRangeSlider();
         // sliderTop = Math.ceil(objectHeight / 2) - 4 = Math.ceil(80/2) - 4 = 40 - 4 = 36
         expect(comp.sliderTop).toBe(36);
      });

      it("should set sliderTop based on body height when in selection container", () => {
         const { comp } = createVSRangeSlider({
            containerType: "VSSelectionContainer",
            container: "Container1",
         });
         // bodyHeight = 80 - 30 = 50; sliderTop = Math.ceil(50/2) - 4 = 25 - 4 = 21
         expect(comp.sliderTop).toBe(21);
      });
   });

   // ─── Group 7: extractTimeIncrement ───────────────────────────────────────

   describe("Group 7 – extractTimeIncrement", () => {
      it("should return first char of brace-type when format is '{type \\'...\\'}'", () => {
         const { comp } = createVSRangeSlider();
         // typeMatch[1] = "date" → charAt(0) = "d"
         expect(comp["extractTimeIncrement"]("{date '2024-01-15'}")).toBe("d");
      });

      it("should return 't' when sanitized value contains a colon", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["extractTimeIncrement"]("2024-01-15 10:30:00")).toBe("t");
      });

      it("should return 'y' when sanitized value has 0 dashes", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["extractTimeIncrement"]("2024")).toBe("y");
      });

      it("should return 'm' when sanitized value has exactly 1 dash", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["extractTimeIncrement"]("2024-06")).toBe("m");
      });

      it("should return 'd' when sanitized value has 2 dashes", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["extractTimeIncrement"]("2024-06-15")).toBe("d");
      });
   });

   // ─── Group 8: toIncrementTimestamp ───────────────────────────────────────

   describe("Group 8 – toIncrementTimestamp", () => {
      it("should return Jan-1 timestamp for 'y' increment", () => {
         const { comp } = createVSRangeSlider();
         const date = new Date(2024, 5, 15); // June 15, 2024
         expect(comp["toIncrementTimestamp"]("y", date)).toBe(new Date(2024, 0, 1).getTime());
      });

      it("should return 1st-of-month timestamp for 'm' increment", () => {
         const { comp } = createVSRangeSlider();
         const date = new Date(2024, 5, 15);
         expect(comp["toIncrementTimestamp"]("m", date)).toBe(new Date(2024, 5, 1).getTime());
      });

      it("should return date-at-midnight timestamp for 'd' increment", () => {
         const { comp } = createVSRangeSlider();
         const date = new Date(2024, 5, 15);
         expect(comp["toIncrementTimestamp"]("d", date)).toBe(new Date(2024, 5, 15).getTime());
      });

      it("should return full timestamp unchanged for 't' increment", () => {
         const { comp } = createVSRangeSlider();
         const date = new Date(2024, 5, 15, 10, 30, 0);
         expect(comp["toIncrementTimestamp"]("t", date)).toBe(date.getTime());
      });
   });

   // ─── Group 9: labelToTimestamp ────────────────────────────────────────────

   describe("Group 9 – labelToTimestamp", () => {
      it("should parse year-only label with 'y' increment", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["labelToTimestamp"]("y", "2024")).toBe(new Date(2024, 0, 1).getTime());
      });

      it("should parse year-month label with 'm' increment", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["labelToTimestamp"]("m", "2024-06")).toBe(new Date(2024, 5, 1).getTime());
      });

      it("should parse year-month-day label with 'd' increment", () => {
         const { comp } = createVSRangeSlider();
         expect(comp["labelToTimestamp"]("d", "2024-06-15")).toBe(
            new Date(2024, 5, 15).getTime(),
         );
      });

      it("should parse brace-escaped date label with 'd' increment", () => {
         const { comp } = createVSRangeSlider();
         // match[1] = "2024-06-15" → same result as plain "2024-06-15"
         expect(comp["labelToTimestamp"]("d", "{date '2024-06-15'}")).toBe(
            new Date(2024, 5, 15).getTime(),
         );
      });

      it("should parse datetime label with 't' increment (space → T)", () => {
         const { comp } = createVSRangeSlider();
         const expected = new Date("2024-06-15T10:30").getTime();
         expect(comp["labelToTimestamp"]("t", "2024-06-15 10:30")).toBe(expected);
      });
   });

   // ─── Group 10: navigate keyboard matrix ───────────────────────────────────

   describe("Group 10 – navigate keyboard matrix", () => {
      it("should set mouseHandle=Left on first navigation when handle is None", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         // Initial: mouseHandle=None, menuFocus=NONE → first-entry path
         comp["navigate"](NavigationKeys.DOWN);
         expect(comp["mouseHandle"]).toBe(comp.handleType.Left);
      });

      it("should cycle Right(2) → Left(0) when DOWN pressed (wrap on > 2)", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Right; // 2

         comp["navigate"](NavigationKeys.DOWN);

         // mouseHandle++ = 3 > 2 → wraps to Handle.Left (0)
         expect(comp["mouseHandle"]).toBe(comp.handleType.Left);
      });

      it("should wrap Left(0) → Right(2) when UP pressed (wrap on < 0)", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Left; // 0

         comp["navigate"](NavigationKeys.UP);

         // mouseHandle-- = -1 < 0 → wraps to Handle.Right (2)
         expect(comp["mouseHandle"]).toBe(comp.handleType.Right);
      });
   });

   // ─── Group 11: getDateStrings ─────────────────────────────────────────────

   describe("Group 11 – getDateStrings y/m/d/t branches", () => {
      it("should append -01-01 / -12-31 to year strings for 'y' increment", () => {
         const { comp } = createVSRangeSlider();
         const result = comp["getDateStrings"]("y", "2023", "2024", "2020", "2025");
         expect(result.currMinStr).toBe("2023-01-01");
         expect(result.currMaxStr).toBe("2024-12-31");
         expect(result.rangeMinStr).toBe("2020-01-01");
         expect(result.rangeMaxStr).toBe("2025-12-31");
      });

      it("should compute month-end day for currMax and rangeMax in 'm' increment", () => {
         const { comp } = createVSRangeSlider();
         // May has 31 days; December has 31 days
         const result = comp["getDateStrings"]("m", "2024-03", "2024-05", "2024-01", "2024-12");
         expect(result.currMinStr).toBe("2024-03-01");
         expect(result.currMaxStr).toBe("2024-05-31"); // May → 31
         expect(result.rangeMinStr).toBe("2024-01-01");
         expect(result.rangeMaxStr).toBe("2024-12-31"); // Dec → 31
      });

      it("should return sanitized passthrough for 'd' increment", () => {
         const { comp } = createVSRangeSlider();
         const result = comp["getDateStrings"]("d", "2024-06-15", "2024-06-20",
            "2024-01-01", "2024-12-31");
         expect(result.currMinStr).toBe("2024-06-15");
         expect(result.currMaxStr).toBe("2024-06-20");
      });

      it("should return sanitized passthrough for 't' increment", () => {
         const { comp } = createVSRangeSlider();
         const result = comp["getDateStrings"]("t", "2024-06-15 10:00", "2024-06-15 12:00",
            "2024-01-01 00:00", "2024-12-31 23:59");
         expect(result.currMinStr).toBe("2024-06-15 10:00");
         expect(result.currMaxStr).toBe("2024-06-15 12:00");
      });
   });

   // ─── Group 12: focusSelectedHandle ───────────────────────────────────────

   describe("Group 12 – focusSelectedHandle blurs then focuses the active handle", () => {
      it("should blur and focus leftHandle when mouseHandle=Left", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Left;

         comp["focusSelectedHandle"]();

         expect((comp as any).leftHandle.nativeElement.blur).toHaveBeenCalledTimes(1);
         expect((comp as any).leftHandle.nativeElement.focus).toHaveBeenCalledTimes(1);
      });

      it("should blur and focus rightHandle when mouseHandle=Right", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Right;

         comp["focusSelectedHandle"]();

         expect((comp as any).rightHandle.nativeElement.blur).toHaveBeenCalledTimes(1);
         expect((comp as any).rightHandle.nativeElement.focus).toHaveBeenCalledTimes(1);
      });

      it("should blur and focus middleHandle when mouseHandle=Middle", () => {
         const { comp } = createVSRangeSlider();
         stubViewChildRefs(comp);
         comp["mouseHandle"] = comp.handleType.Middle;

         comp["focusSelectedHandle"]();

         expect((comp as any).middleHandle.nativeElement.blur).toHaveBeenCalledTimes(1);
         expect((comp as any).middleHandle.nativeElement.focus).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 13: getLabelPosition overflow ──────────────────────────────────

   describe("Group 13 – getLabelPosition overflow adjustments", () => {
      it("should shift label right when leftOverflow < 0 (rightPos < textSize)", () => {
         // Default positions: rightPos=143.25; textSize=150 → leftOverflow = 143.25-150 = -6.75 < 0
         // return centerPosition - leftOverflow/2 = -10.5 - (-6.75/2) = -10.5 + 3.375 = -7.125
         const { comp } = createVSRangeSlider();
         comp["textSize"] = 150;
         expect(comp.getLabelPosition()).toBeCloseTo(-7.125, 1);
      });

      it("should shift label left when rightOverflow < 0 (objectWidth - leftPos < textSize)", () => {
         // selectStart=3, selectEnd=4 → leftPos=143.25, rightPos=191
         // textSize=100: leftOverflow=191-100=91 > 0 ✓; rightOverflow=220-143.25-100=-23.25 < 0
         // centerPosition = (191-143.25)/2 + 143.25 - 110 + 4 = 61.125
         // return 61.125 + (-23.25/2) = 49.5
         const { comp } = createVSRangeSlider({ selectStart: 3, selectEnd: 4 });
         comp["textSize"] = 100;
         expect(comp.getLabelPosition()).toBeCloseTo(49.5, 1);
      });
   });

   // ─── Group 14: clearNavSelection ──────────────────────────────────────────

   describe("Group 14 – clearNavSelection resets keyboard navigation state", () => {
      it("should set mouseHandle to None", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Left;
         comp["clearNavSelection"]();
         expect(comp["mouseHandle"]).toBe(comp.handleType.None);
      });

      it("should set menuFocus to NONE (-5)", () => {
         const { comp } = createVSRangeSlider();
         comp["menuFocus"] = 1; // some non-NONE value

         comp["clearNavSelection"]();

         // FocusRegions.NONE = -5
         expect(comp["menuFocus"]).toBe(-5);
      });

      it("should set keyNav to false", () => {
         const { comp } = createVSRangeSlider();
         comp["keyNav"] = true;
         comp["clearNavSelection"]();
         expect(comp["keyNav"]).toBe(false);
      });
   });

   // ─── Group 15: Legacy DOM regressions ported from vs-range-slider.component.spec.ts ──

   describe("Group 15 – legacy DOM regressions", () => {
      let fixture: ComponentFixture<VSRangeSlider>;

      beforeEach(waitForAsync(() => {
         const viewsheetClient: any = { sendEvent: vi.fn(), runtimeId: "vs-1^128^__^Sheet1" };
         const formDataService = { checkFormData: vi.fn() };
         const modelService = { getModel: vi.fn() };
         const modalService = { open: vi.fn() };
         const adhocFilterService = { showFilter: vi.fn().mockReturnValue(() => {}) };
         const dataTipService = { isDataTip: vi.fn().mockReturnValue(false) };
         const debounceService = {
            debounce: vi.fn().mockImplementation((_key: any, fn: any) => fn()),
         };
         const dropdownService = { open: vi.fn() };
         const globalSubmitService = {
            globalSubmit: vi.fn().mockReturnValue(NEVER),
            updateState: vi.fn(),
         };
         const timerService = { defer: vi.fn((fn: any) => fn()) };
         const context = new ContextProvider(
            false, false, false, false, false, false, false, false, false, false, false,
         );

         TestBed.configureTestingModule({
            imports: [VSRangeSlider],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
               { provide: ViewsheetClientService, useValue: viewsheetClient },
               { provide: CheckFormDataService, useValue: formDataService },
               { provide: ModelService, useValue: modelService },
               { provide: DialogService, useValue: modalService },
               { provide: AdhocFilterService, useValue: adhocFilterService },
               { provide: ContextProvider, useValue: context },
               { provide: DataTipService, useValue: dataTipService },
               { provide: DebounceService, useValue: debounceService },
               { provide: FixedDropdownService, useValue: dropdownService },
               { provide: GlobalSubmitService, useValue: globalSubmitService },
               { provide: TimerService, useValue: timerService },
               PopComponentService,
            ],
         });
         TestBed.compileComponents();

         fixture = TestBed.createComponent(VSRangeSlider);
         fixture.componentInstance.model = makeVSRangeSliderModel();
         fixture.detectChanges();
      }));

      // Bug #18972
      it("should apply text-decoration format to the range slider body", () => {
         const model = makeVSRangeSliderModel();
         model.objectFormat.decoration = "underline";
         fixture.componentInstance.model = model;
         fixture.detectChanges();

         const body = fixture.nativeElement.querySelector("div.vs-range-slider-body");
         expect(body.style["text-decoration"]).toBe("underline");
      });

      // Bug #20993
      it("should position the slider elements using sliderTop", () => {
         const model = makeVSRangeSliderModel();
         model.objectFormat.height = 80;
         fixture.componentInstance.model = model;
         fixture.detectChanges();

         // sliderTop = Math.ceil(objectFormat.height / 2) - 4 = Math.ceil(80/2) - 4 = 36
         // rangeValueOffset = 10 → range-value top = 36 + 10 = 46
         const rangeSlider = fixture.nativeElement.querySelector("div.range-slider");
         const rangeLine = fixture.nativeElement.querySelector("div.range-line");
         const currentValue = fixture.nativeElement.querySelector("div.current-value");
         const maxValue = fixture.nativeElement.querySelector("div.range-value.range-right");
         const minValue = fixture.nativeElement.querySelector("div.range-value.range-left");

         expect(rangeSlider.style["top"]).toBe("36px");
         expect(rangeLine.style["top"]).toBe("36px");
         expect(currentValue.style["top"]).toBe("calc(36px - 1em)");
         expect(maxValue.style["top"]).toBe("46px");
         expect(minValue.style["top"]).toBe("46px");
      });
   });
});
