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
 * VSCrosstab — Pass 3: Display
 *
 * Covers pure display-logic methods and computed properties.
 *
 *   Group 1 — isSorted: all 5 switch cases (asc/desc/val-asc/val-desc/none)
 *   Group 2 — showDCIcon: dataTipVisible / mobileDevice / vsWizardPreview / description guards
 *   Group 3 — dateComparisonTipStyle: drillTip flag shifts right value
 *   Group 4 — isTipOverlapToolbar: vsWizardPreview / top-position / embeddedVS branches
 *   Group 5 — sortEnable / sortDimensionEnable: field / sortColumnVisible / period / hasCalc guards
 *   Group 6 — isFullHorizontalWrapper: rbTableWidth vs objectWidth/2 threshold
 *   Group 7 — getObjectHeight: shrink + viewer adjusts height; no-shrink returns model height
 */

import { XConstants } from "../../../common/util/xconstants";
import {
   createCrosstabComponent,
   makeTableCell,
} from "./vs-crosstab.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSCrosstab — Pass 3: Display", () => {
   describe("Group 1 — isSorted", () => {
      it("should return true for 'asc' when sortTypeMap has SORT_ASC", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = { col1: XConstants.SORT_ASC };

         expect(comp.isSorted("col1", "asc")).toBe(true);
      });

      it("should return false for 'desc' when sortTypeMap has SORT_ASC", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = { col1: XConstants.SORT_ASC };

         expect(comp.isSorted("col1", "desc")).toBe(false);
      });

      it("should return true for 'desc' when sortTypeMap has SORT_DESC", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = { col1: XConstants.SORT_DESC };

         expect(comp.isSorted("col1", "desc")).toBe(true);
      });

      it("should return true for 'val-asc' when sortTypeMap has SORT_VALUE_ASC", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = { col1: XConstants.SORT_VALUE_ASC };

         expect(comp.isSorted("col1", "val-asc")).toBe(true);
      });

      it("should return true for 'val-desc' when sortTypeMap has SORT_VALUE_DESC", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = { col1: XConstants.SORT_VALUE_DESC };

         expect(comp.isSorted("col1", "val-desc")).toBe(true);
      });

      it("should return true for 'none' when field is absent from sortTypeMap", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = {};

         expect(comp.isSorted("col1", "none")).toBe(true);
      });

      it("should return false for 'asc' when field is absent from sortTypeMap", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = {};

         expect(comp.isSorted("col1", "asc")).toBe(false);
      });
   });

   describe("Group 2 — showDCIcon", () => {
      it("should return true when description is set and no guards block it", () => {
         const dataTipService = {
            isDataTipVisible: vi.fn().mockReturnValue(false),
            isDataTip: vi.fn().mockReturnValue(false),
            freeze: vi.fn(),
            unfreeze: vi.fn(),
            hideDataTip: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ dataTipService });
         comp.model.dateComparisonDescription = "Last quarter vs this quarter";
         comp.mobileDevice = false;

         expect(comp.showDCIcon).toBe(true);
      });

      it("should return false when dataTipVisible returns true", () => {
         const dataTipService = {
            isDataTipVisible: vi.fn().mockReturnValue(true),
            isDataTip: vi.fn().mockReturnValue(false),
            freeze: vi.fn(),
            unfreeze: vi.fn(),
            hideDataTip: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ dataTipService });
         comp.model.dateComparisonDescription = "Last quarter vs this quarter";
         comp.mobileDevice = false;

         expect(comp.showDCIcon).toBe(false);
      });

      it("should return false when mobileDevice is true", () => {
         const dataTipService = {
            isDataTipVisible: vi.fn().mockReturnValue(false),
            isDataTip: vi.fn().mockReturnValue(false),
            freeze: vi.fn(),
            unfreeze: vi.fn(),
            hideDataTip: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ dataTipService });
         comp.model.dateComparisonDescription = "Last quarter vs this quarter";
         comp.mobileDevice = true;

         expect(comp.showDCIcon).toBe(false);
      });

      it("should return false when vsWizardPreview is true", () => {
         const contextProvider = {
            viewer: true,
            preview: false,
            binding: false,
            composer: false,
            vsWizardPreview: true,
         };
         const dataTipService = {
            isDataTipVisible: vi.fn().mockReturnValue(false),
            isDataTip: vi.fn().mockReturnValue(false),
            freeze: vi.fn(),
            unfreeze: vi.fn(),
            hideDataTip: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ contextProvider, dataTipService });
         comp.model.dateComparisonDescription = "Last quarter vs this quarter";
         comp.mobileDevice = false;

         expect(comp.showDCIcon).toBe(false);
      });

      it("should return false when dateComparisonDescription is null", () => {
         const dataTipService = {
            isDataTipVisible: vi.fn().mockReturnValue(false),
            isDataTip: vi.fn().mockReturnValue(false),
            freeze: vi.fn(),
            unfreeze: vi.fn(),
            hideDataTip: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ dataTipService });
         comp.model.dateComparisonDescription = null;
         comp.mobileDevice = false;

         expect(comp.showDCIcon).toBe(false);
      });
   });

   describe("Group 3 — dateComparisonTipStyle", () => {
      it("should return 'right: 20px' when drillTip is truthy", () => {
         const { comp } = createCrosstabComponent();
         comp.model.drillTip = "tip";

         expect(comp.dateComparisonTipStyle).toBe("right: 20px");
      });

      it("should return 'right: 3px' when drillTip is falsy", () => {
         const { comp } = createCrosstabComponent();
         comp.model.drillTip = null;

         expect(comp.dateComparisonTipStyle).toBe("right: 3px");
      });
   });

   describe("Group 4 — isTipOverlapToolbar", () => {
      it("should return true when vsWizardPreview is true", () => {
         const contextProvider = {
            viewer: true,
            preview: false,
            binding: false,
            composer: false,
            vsWizardPreview: true,
         };
         const { comp } = createCrosstabComponent({ contextProvider });

         expect(comp.isTipOverlapToolbar()).toBe(true);
      });

      it("should return true when not embeddedVS and objectFormat.top <= 20", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.top = 10;
         comp.embeddedVS = false;

         expect(comp.isTipOverlapToolbar()).toBe(true);
      });

      it("should return false when not embeddedVS and objectFormat.top > 20", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.top = 50;
         comp.embeddedVS = false;

         expect(comp.isTipOverlapToolbar()).toBe(false);
      });

      it("should return true when embeddedVS and embeddedVSBounds.y <= 20", () => {
         const { comp } = createCrosstabComponent();
         comp.embeddedVS = true;
         comp.embeddedVSBounds = { x: 0, y: 5, width: 300, height: 200 } as any;
         comp.model.objectFormat.top = 100;

         expect(comp.isTipOverlapToolbar()).toBe(true);
      });
   });

   // Group 5: sortColumnVisible is a protected field on BaseTable with no public setter.
   // It is written via (comp as any) to isolate the column-visibility guard from cell-level
   // conditions; the alternative (calling the private updateVisibleCols path) would require
   // DOM-dependent rendering that direct-instantiation tests deliberately avoid.
   describe("Group 5 — sortEnable / sortDimensionEnable", () => {
      it("should return false when cell has no field", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortTypeMap = {};
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: null, hasCalc: false, period: false });

         expect(comp.sortEnable(cell)).toBeFalsy();
      });

      it("should return false when sortColumnVisible is false", () => {
         const { comp } = createCrosstabComponent();
         (comp as any).sortColumnVisible = false;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortEnable(cell)).toBe(false);
      });

      it("should return false when cell.hasCalc is true", () => {
         const { comp } = createCrosstabComponent();
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: true, period: false });

         expect(comp.sortEnable(cell)).toBe(false);
      });

      it("should return false when cell.period is true", () => {
         const { comp } = createCrosstabComponent();
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: true });

         expect(comp.sortEnable(cell)).toBe(false);
      });

      it("should return true when field exists, sortColumnVisible, not hasCalc, not period, not timeSeries", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).timeSeriesNames = [];
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortEnable(cell)).toBe(true);
      });

      it("should return false for sortEnable when field is in timeSeriesNames", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).timeSeriesNames = ["col1"];
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortEnable(cell)).toBe(false);
      });

      it("should return true for sortDimensionEnable when sortOnHeader is true", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortOnHeader = true;
         (comp.model as any).timeSeriesNames = [];
         (comp.model as any).aggrNames = [];
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortDimensionEnable(cell)).toBe(true);
      });

      it("should return true for sortDimensionEnable when field is a dimension and sortDimension is true", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortOnHeader = false;
         (comp.model as any).sortDimension = true;
         (comp.model as any).aggrNames = [];
         (comp.model as any).timeSeriesNames = [];
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortDimensionEnable(cell)).toBe(true);
      });

      it("should return false for sortDimensionEnable when field is aggregate and sortOnHeader is false", () => {
         const { comp } = createCrosstabComponent();
         (comp.model as any).sortOnHeader = false;
         (comp.model as any).sortDimension = true;
         (comp.model as any).aggrNames = ["col1"];
         (comp.model as any).timeSeriesNames = [];
         (comp as any).sortColumnVisible = true;
         const cell = makeTableCell({ field: "col1", hasCalc: false, period: false });

         expect(comp.sortDimensionEnable(cell)).toBe(false);
      });
   });

   describe("Group 6 — isFullHorizontalWrapper", () => {
      it("should return true when rbTableWidth < half of object width", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.width = 300;
         comp.rbTableWidth = 100;

         expect(comp.isFullHorizontalWrapper).toBe(true);
      });

      it("should return false when rbTableWidth >= half of object width", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.width = 300;
         comp.rbTableWidth = 200;

         expect(comp.isFullHorizontalWrapper).toBe(false);
      });

      it("should return false when model is not set", () => {
         const { comp } = createCrosstabComponent();
         (comp as any)._model = null;

         expect(comp.isFullHorizontalWrapper).toBe(false);
      });
   });

   describe("Group 7 — getObjectHeight", () => {
      it("should return model.objectFormat.height when shrink is false", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.height = 200;
         comp.model.shrink = false;

         expect(comp.getObjectHeight()).toBe(200);
      });

      it("should subtract titleFormat.height when shrink is true and titleVisible is false", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.height = 200;
         comp.model.titleFormat.height = 20;
         comp.model.shrink = true;
         comp.model.titleVisible = false;
         // scrollHeight must be >= tableHeight (objectFormat.height - headerHeight - titleFormat.height)
         // so that super.getObjectHeight() takes the else branch and returns objectFormat.height.
         comp.model.scrollHeight = 200;

         expect(comp.getObjectHeight()).toBe(180);
      });

      it("should not subtract titleFormat.height when titleVisible is true", () => {
         const { comp } = createCrosstabComponent();
         comp.model.objectFormat.height = 200;
         comp.model.titleFormat.height = 20;
         comp.model.shrink = true;
         comp.model.titleVisible = true;
         comp.model.scrollHeight = 200;

         expect(comp.getObjectHeight()).toBe(200);
      });
   });
});
