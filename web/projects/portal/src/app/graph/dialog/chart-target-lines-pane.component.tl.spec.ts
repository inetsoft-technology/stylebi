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
 * ChartTargetLinesPane - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit / select: default selection and multi-select rules
 *   Group 2 [Risk 3] - addTarget / editTarget: modal result handling and model mutation
 *   Group 3 [Risk 2] - deleteTarget: descending removal, deleted index tracking, reselection
 *   Group 4 [Risk 2] - createTargetString: line, band, and strategy display branches
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   Template rendering and the chart-target-dialog child component internals
 *
 * Mocking strategy:
 *   - direct class instantiation with a deferred NgbModal result stub
 *   - deterministic TargetInfo factories via TestUtils helpers
 */

import { TestUtils } from "../../common/test/test-utils";
import { StyleConstants } from "../../common/util/style-constants";
import { TargetInfo } from "../../widget/target/target-info";
import { ChartTargetLinesPaneModel } from "../model/dialog/chart-target-lines-pane-model";
import { ChartTargetLinesPane } from "./chart-target-lines-pane.component";

afterEach(() => vi.restoreAllMocks());

function makeTarget(overrides: Partial<TargetInfo> = {}): TargetInfo {
   return { ...TestUtils.createMockTargetInfo(), ...overrides };
}

function makeModel(overrides: Partial<ChartTargetLinesPaneModel> = {}): ChartTargetLinesPaneModel {
   return {
      mapInfo: false,
      supportsTarget: true,
      chartTargets: [],
      newTargetInfo: makeTarget(),
      availableFields: [],
      deletedIndexList: [],
      ...overrides,
   };
}

function deferred<T>() {
   let resolve: (value: T) => void;
   let reject: (reason?: any) => void;
   const promise = new Promise<T>((res, rej) => {
      resolve = res;
      reject = rej;
   });
   return { promise, resolve: resolve!, reject: reject! };
}

function createComponent(modelOverrides: Partial<ChartTargetLinesPaneModel> = {}) {
   const modalResult = deferred<TargetInfo>();
   const modalService = {
      open: vi.fn().mockReturnValue({ result: modalResult.promise }),
   };
   const comp = new ChartTargetLinesPane(modalService as any);
   comp.model = makeModel(modelOverrides);
   comp.variables = [];
   return { comp, modalService, modalResult };
}

function makeClickEvent(overrides: Partial<MouseEvent> = {}): MouseEvent {
   return {
      ctrlKey: false,
      metaKey: false,
      shiftKey: false,
      ...overrides,
   } as MouseEvent;
}

describe("ChartTargetLinesPane - Group 1: selection", () => {
   it("should select the first target on init when chartTargets is not empty", () => {
      const { comp } = createComponent({ chartTargets: [makeTarget()] });

      comp.ngOnInit();

      expect(comp.selectedIndexes).toEqual([0]);
   });

   it("should replace the selection on a plain click", () => {
      const { comp } = createComponent();
      comp.selectedIndexes = [0, 1];

      comp.select(2, makeClickEvent());

      expect(comp.selectedIndexes).toEqual([2]);
   });

   it("should create an inclusive range on shift-click using the last selected index", () => {
      const { comp } = createComponent();
      comp.selectedIndexes = [1, 4];

      comp.select(2, makeClickEvent({ shiftKey: true }));

      expect(comp.selectedIndexes).toEqual([2, 3, 4]);
   });

   it("should toggle an item off on ctrl-click when it is already selected", () => {
      const { comp } = createComponent();
      comp.selectedIndexes = [1, 2, 4];

      comp.select(2, makeClickEvent({ ctrlKey: true }));

      expect(comp.selectedIndexes).toEqual([1, 4]);
   });
});

describe("ChartTargetLinesPane - Group 2: addTarget / editTarget", () => {
   it("should append a new changed target from the modal result and select it", async () => {
      const { comp, modalService, modalResult } = createComponent({
         newTargetInfo: makeTarget({
            value: "=1234567890123456789012",
            lineStyle: StyleConstants.THIN_LINE,
            measure: { name: "sales", label: "Sales", dateField: false },
         }),
      });

      comp.addTarget();
      modalResult.resolve(comp.chartTargetModel);
      await modalResult.promise;

      expect(comp.editing).toBe(false);
      expect(modalService.open).toHaveBeenCalledWith(
         comp.chartTargetDialog,
         { windowClass: "chart-target-dialog", backdrop: "static" }
      );
      expect(comp.model.chartTargets).toHaveLength(1);
      expect(comp.model.chartTargets[0]).toEqual(
         expect.objectContaining({
            index: -1,
            changed: true,
            targetString: "Line 12345678901234567... of Sales [THIN_LINE]",
         })
      );
      expect(comp.selectedIndexes).toEqual([0]);
   });

   it("should replace the selected target with the edited modal result", async () => {
      const existing = makeTarget({
         index: 7,
         tabFlag: 1,
         value: "=1",
         toValue: "=3",
         measure: { name: "profit", label: "Profit", dateField: false },
         lineStyle: StyleConstants.DASH_LINE,
      });
      const edited = makeTarget({
         index: 7,
         tabFlag: 1,
         value: "=5",
         toValue: "=9",
         measure: { name: "profit", label: "Profit", dateField: false },
         lineStyle: StyleConstants.MEDIUM_LINE,
      });
      const { comp, modalResult } = createComponent({ chartTargets: [existing] });
      comp.selectedIndexes = [0];

      comp.editTarget();
      modalResult.resolve(edited);
      await modalResult.promise;

      expect(comp.editing).toBe(true);
      expect(comp.model.chartTargets[0]).toEqual(
         expect.objectContaining({
            index: 7,
            changed: true,
            targetString: "Band 5 ->9 of Profit [MEDIUM_LINE]",
         })
      );
   });
});

describe("ChartTargetLinesPane - Group 3: deleteTarget", () => {
   it("should delete selected targets in descending order, track persisted indexes, and reselect", () => {
      const targets = [
         makeTarget({ index: 3 }),
         makeTarget({ index: -1 }),
         makeTarget({ index: 8 }),
      ];
      const remainingTarget = targets[1];
      const { comp } = createComponent({ chartTargets: targets });
      comp.selectedIndexes = [0, 2];

      comp.deleteTarget();

      expect(comp.model.deletedIndexList).toEqual([8, 3]);
      expect(comp.model.chartTargets).toEqual([remainingTarget]);
      expect(comp.selectedIndexes).toEqual([0]);
   });

   it("should clear selectedIndexes when the last target is deleted", () => {
      const { comp } = createComponent({ chartTargets: [makeTarget({ index: 2 })] });
      comp.selectedIndexes = [0];

      comp.deleteTarget();

      expect(comp.model.chartTargets).toEqual([]);
      expect(comp.selectedIndexes).toBeUndefined();
   });
});

describe("ChartTargetLinesPane - Group 4: createTargetString", () => {
   it("should build a line target string with value trimming and measure label", () => {
      const { comp } = createComponent();

      expect(comp.createTargetString(makeTarget({
         tabFlag: 0,
         value: "=1234567890123456789012",
         measure: { name: "sales", label: "Sales", dateField: false },
         lineStyle: StyleConstants.THIN_LINE,
      }))).toBe("Line 12345678901234567... of Sales [THIN_LINE]");
   });

   it("should build a confidence interval strategy string with a percent suffix", () => {
      const { comp } = createComponent();

      expect(comp.createTargetString(makeTarget({
         tabFlag: 2,
         strategyInfo: {
            name: "Confidence Interval",
            value: "95",
            percentageAggregateVal: "",
            standardIsSample: true,
         },
         measure: { name: "sales", label: "Sales", dateField: false },
         lineStyle: StyleConstants.DOT_LINE,
      }))).toBe(" 95% Confidence Interval of Sales [DOT_LINE]");
   });

   it("should build a percentage strategy string with the aggregate label", () => {
      const { comp } = createComponent();

      expect(comp.createTargetString(makeTarget({
         tabFlag: 2,
         strategyInfo: {
            name: "Percentage",
            value: "15",
            percentageAggregateVal: "Sum(Sales)",
            standardIsSample: true,
         },
         lineStyle: StyleConstants.DASH_LINE,
      }))).toBe(" 15 Percentages Sum(Sales) [DASH_LINE]");
   });
});
