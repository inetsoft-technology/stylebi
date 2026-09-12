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
 * VSTab - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - changeTab event dispatch, chart deselection, and selected-region transitions
 *   Group 2 [Risk 2] - scroll controls and keyboard navigation
 *   Group 3 [Risk 1] - border/margin display helpers and hover border derivation
 *
 * Confirmed bugs (it.fails): none
 */

import { TinyColor } from "@ctrl/tinycolor";
import { TestUtils } from "../../../common/test/test-utils";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { VSTabModel } from "../../model/vs-tab-model";
import { VSTabService } from "../../util/vs-tab.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { NavigationKeys } from "../navigation-keys";
import { VSTab } from "./vs-tab.component";

afterEach(() => {
   vi.runOnlyPendingTimers();
   vi.useRealTimers();
   vi.restoreAllMocks();
});

function makeModel(overrides: Partial<VSTabModel> = {}): VSTabModel {
   const model = TestUtils.createMockVSTabModel("Tab1");
   model.enabled = true;
   model.labels = ["Tab A", "Tab B"];
   model.childrenNames = ["ChildA", "ChildB"];
   model.selected = "ChildA";
   model.selectedRegions = [];
   model.objectFormat = {
      ...TestUtils.createMockVSFormatModel(),
      width: 200,
      height: 30,
      zIndex: 3,
      background: "#ffffff",
      roundCorner: 0,
      border: {
         top: "1px solid #111111",
         right: "0px none #222222",
         bottom: "2px solid #333333",
         left: "0px none #444444",
      },
      wrapping: { whiteSpace: "nowrap", wordWrap: "normal", overflow: "hidden" },
   };
   model.activeFormat = {
      ...TestUtils.createMockVSFormatModel(),
      background: "#eeeeee",
      roundCorner: 0,
      border: {
         top: "3px solid #555555",
         right: "2px solid #666666",
         bottom: "1px solid #777777",
         left: "4px solid #888888",
      },
      wrapping: { whiteSpace: "nowrap", wordWrap: "normal", overflow: "hidden" },
   };
   model.roundTopCornersOnly = false;
   model.bottomTabs = false;
   return Object.assign(model, overrides);
}

function createComponent(opts: { model?: VSTabModel; viewer?: boolean; preview?: boolean } = {}) {
   vi.useFakeTimers();

   const viewsheetClientService = { sendEvent: vi.fn() };
   const changeDetectionRef = { detectChanges: vi.fn() };
   const renderer = {
      setProperty: vi.fn(),
   };
   const context = {
      viewer: opts.viewer ?? true,
      preview: opts.preview ?? false,
      binding: false,
      embedAssembly: false,
      vsWizard: false,
      vsWizardPreview: false,
   };
   const dataTipService = { isDataTip: vi.fn(() => false) };
   const tabService = { deselect: vi.fn() };
   const zone = { run: (fn: Function) => fn() };

   const comp = new VSTab(
      viewsheetClientService as unknown as ViewsheetClientService,
      changeDetectionRef as any,
      renderer as any,
      context as ContextProvider,
      dataTipService as unknown as DataTipService,
      tabService as unknown as VSTabService,
      zone as any,
   );

   const child0Focus = vi.fn();
   const child1Focus = vi.fn();
   comp.hostElement = {
      nativeElement: {
         scrollWidth: 220,
         offsetWidth: 100,
         scrollLeft: 0,
         children: [{ focus: child0Focus }, { focus: child1Focus }],
      },
   } as any;
   comp.model = opts.model ?? makeModel();
   comp.vsInfo = {
      vsObjects: [{ absoluteName: "ChildA", objectType: "VSChart", chartSelection: { regions: ["x"] } }],
      isAssemblyFocused: vi.fn(() => false),
   } as any;

   return {
      comp,
      viewsheetClientService,
      changeDetectionRef,
      renderer,
      tabService,
      child0Focus,
      child1Focus,
   };
}

describe("VSTab - changeTab and scroll behavior", () => {
   it("should deselect the current tab chart, send the change event, and clear selected regions for a new tab", () => {
      const { comp, viewsheetClientService, tabService } = createComponent({
         model: makeModel({ selectedRegions: ["DETAIL"] as any }),
      });
      comp.selected = true;

      comp.changeTab("ChildB");

      expect(tabService.deselect).toHaveBeenCalledWith("ChildA");
      expect((comp.vsInfo.vsObjects[0] as any).chartSelection.regions).toEqual([]);
      expect(viewsheetClientService.sendEvent).toHaveBeenCalledWith(
         "/events/tab/changetab/Tab1",
         expect.objectContaining({ target: "ChildB" }),
      );
      expect(comp.model.selectedRegions).toEqual([]);
   });

   it("should select the detail region when the clicked tab is already selected", () => {
      const { comp } = createComponent();
      comp.selected = true;

      comp.changeTab("ChildA");

      expect(comp.model.selectedRegions).toEqual([DataPathConstants.DETAIL]);
   });

   it("should update scroll flags from the host element dimensions", () => {
      const { comp } = createComponent();
      comp.hostElement.nativeElement.scrollLeft = 30;

      const changed = comp.updateScrolls();

      expect(changed).toBe(true);
      expect(comp.leftScroll).toBe(true);
      expect(comp.rightScroll).toBe(true);
   });

   it("should detect only right scroll available when content overflows only on the right", () => {
      const { comp } = createComponent();

      const changed = comp.updateScrolls();

      expect(changed).toBe(true);
      expect(comp.leftScroll).toBe(false);
      expect(comp.rightScroll).toBe(true);
   });

   it("should report no change when scroll flags stay the same across calls", () => {
      const { comp } = createComponent();
      comp.hostElement.nativeElement.scrollLeft = 30;
      comp.updateScrolls();

      const changed = comp.updateScrolls();

      expect(changed).toBe(false);
      expect(comp.leftScroll).toBe(true);
      expect(comp.rightScroll).toBe(true);
   });

   it("should scroll left and right while the primary button is held", async () => {
      const { comp, renderer } = createComponent();
      comp.hostElement.nativeElement.scrollLeft = 40;

      comp.scrollToLeft({ button: 0 });
      await vi.advanceTimersByTimeAsync(50);
      comp.stopScrolling({});
      comp.scrollToRight({ button: 0 });

      expect(renderer.setProperty).toHaveBeenNthCalledWith(
         1,
         comp.hostElement.nativeElement,
         "scrollLeft",
         36,
      );
      expect(renderer.setProperty).toHaveBeenCalledWith(
         comp.hostElement.nativeElement,
         "scrollLeft",
         44,
      );
   });
});

describe("VSTab - navigation and display helpers", () => {
   it("should move focus with arrow keys and change the focused tab on SPACE", () => {
      const { comp, child0Focus, child1Focus } = createComponent();
      const changeTabSpy = vi.spyOn(comp, "changeTab");

      (comp as any).navigate(NavigationKeys.RIGHT);
      (comp as any).navigate(NavigationKeys.RIGHT);
      (comp as any).navigate(NavigationKeys.SPACE);

      expect(child0Focus).toHaveBeenCalledTimes(1);
      expect(child1Focus).toHaveBeenCalledTimes(1);
      expect(changeTabSpy).toHaveBeenCalledWith("ChildB");
   });

   it("should return the hover border for hovered tabs and transparent side borders for none-style tabs", () => {
      const { comp } = createComponent();
      comp.ngOnChanges({ model: {} as any } as any);
      comp.tabHovered.add(0);

      expect(comp.getBorder(0)).toEqual(comp.hoverBorder);

      comp.tabHovered.clear();
      expect(comp.getBorder(1)).toEqual(
         expect.objectContaining({
            left: "1px solid transparent",
            right: "1px solid transparent",
         }),
      );
   });

   it("should derive the hover border color by tinting the first non-none border color", () => {
      const { comp } = createComponent();

      comp.ngOnChanges({ model: {} as any } as any);

      // model.objectFormat.border.bottom = "2px solid #333333" is the first non-empty
      // side checked by initHoverBorder (order: bottom, left, top, right).
      const expectedColor = new TinyColor("#333333").tint(20).toRgbString();
      expect(comp.hoverBorder).toEqual({
         left: "4px solid " + expectedColor,
         right: "2px solid " + expectedColor,
         top: "3px solid " + expectedColor,
         bottom: "2px solid " + expectedColor,
      });
   });

   it("should compute max border widths, margins, and a bottom-border style object from ngOnChanges", () => {
      const { comp } = createComponent();

      comp.ngOnChanges({ model: {} as any } as any);

      expect(comp.maxBorderWidths).toEqual({ left: 4, right: 2, top: 3, bottom: 2 });
      expect(comp.getMargin(0, "left")).toBe(0);
      expect(comp.getMargin(1, "left")).toBe(3);
      expect(comp.getMargin(1, "right")).toBe(1);
      expect(comp.getTabItemBottomBorderStyle(1)).toEqual({
         left: "-1px",
         bottom: "-2px",
         width: "calc(100% + 2px)",
         height: "calc(100% + 3px)",
      });
   });

   it("should suppress the container bottom border when round corners are enabled", () => {
      const { comp } = createComponent({
         model: makeModel({
            objectFormat: { ...makeModel().objectFormat, roundCorner: 8 },
            roundTopCornersOnly: false,
         }),
      });

      expect(comp.getBottomBorder()).toBe("");
   });

   it("should expose disableEvents only when viewer mode is active and the tab is disabled", () => {
      const { comp } = createComponent({
         viewer: true,
         model: makeModel({ enabled: false }),
      });

      expect(comp.disableEvents).toBe(true);
      expect(comp.getFormat(0)).toBe(comp.model.activeFormat);
      expect(comp.getFormat(1)).toBe(comp.model.objectFormat);
   });
});
