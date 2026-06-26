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
 * SimpleScheduleDialog — Pass 3: Display / Computed Getters
 *
 * Covers pure logic on model state, not async HTTP:
 *   Group 1   dataSizeOptionVisible getter — all branches
 *   Group 2   showMeridian getter
 *   Group 3   isEmptyTable — all branches
 *   Group 4   formatChange — isReport vs non-report, onlyDataComponents reset
 *   Group 5   selectConditionType — EVERY_MONTH initializes monthlyDaySelected
 *   Group 6   changeStartTimeModel — enables/disables timeZone control
 */

import {
   makeComponent,
   makeModel,
   makeTimeCondition,
   makeEmailInfo,
} from "./simple-schedule-dialog.component.test-helpers";
import { TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { FileFormatType } from "../../vsobjects/model/file-format-type";
import { ActionModel } from "./action-model";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — dataSizeOptionVisible getter
// ---------------------------------------------------------------------------

describe("Group 1 — dataSizeOptionVisible: all branches", () => {
   it("should return false when isReport=true regardless of formatType", () => {
      const { comp } = makeComponent({ isReport: true });
      expect(comp.dataSizeOptionVisible).toBe(false);
   });

   it("should return false when formatType=HTML", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ formatType: FileFormatType.EXPORT_TYPE_HTML }),
         }),
      });
      const { comp } = makeComponent({ model, isReport: false });
      expect(comp.dataSizeOptionVisible).toBe(false);
   });

   it("should return false when formatType=CSV", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ formatType: FileFormatType.EXPORT_TYPE_CSV }),
         }),
      });
      const { comp } = makeComponent({ model, isReport: false });
      expect(comp.dataSizeOptionVisible).toBe(false);
   });

   it("should return true when isReport=false and formatType=Excel and no hasPrintLayout", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ formatType: FileFormatType.EXPORT_TYPE_EXCEL }),
         }),
      });
      const { comp } = makeComponent({ model, isReport: false });
      expect(comp.dataSizeOptionVisible).toBe(true);
   });

   it("should return true when isReport=false and formatType=PowerPoint", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ formatType: FileFormatType.EXPORT_TYPE_POWERPOINT }),
         }),
      });
      const { comp } = makeComponent({ model, isReport: false });
      expect(comp.dataSizeOptionVisible).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — showMeridian getter
// ---------------------------------------------------------------------------

describe("Group 2 — showMeridian getter", () => {
   it("should return true when model.twelveHourSystem=true", () => {
      const { comp } = makeComponent({ model: makeModel({ twelveHourSystem: true }) });
      expect(comp.showMeridian).toBe(true);
   });

   it("should return false when model.twelveHourSystem=false", () => {
      const { comp } = makeComponent({ model: makeModel({ twelveHourSystem: false }) });
      expect(comp.showMeridian).toBe(false);
   });

   it("should return falsy when model is null", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.model = null;
      expect(comp.showMeridian).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — isEmptyTable
// ---------------------------------------------------------------------------

describe("Group 3 — isEmptyTable: all branches", () => {
   it("should return false when formatType is not CSV", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({
               formatType: FileFormatType.EXPORT_TYPE_EXCEL,
               csvConfigModel: { delimiter: ",", quote: null, keepHeader: false, tabDelimited: false, selectedAssemblies: [] },
            }),
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.isEmptyTable()).toBe(false);
   });

   it("should return false when formatType=CSV but selectedAssemblies is null", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({
               formatType: FileFormatType.EXPORT_TYPE_CSV,
               csvConfigModel: { delimiter: ",", quote: null, keepHeader: false, tabDelimited: false, selectedAssemblies: null },
            }),
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.isEmptyTable()).toBe(false);
   });

   it("should return false when formatType=CSV and selectedAssemblies has items", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({
               formatType: FileFormatType.EXPORT_TYPE_CSV,
               csvConfigModel: { delimiter: ",", quote: null, keepHeader: false, tabDelimited: false, selectedAssemblies: ["table1"] },
            }),
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.isEmptyTable()).toBe(false);
   });

   it("should return true when formatType=CSV and selectedAssemblies is empty array", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({
               formatType: FileFormatType.EXPORT_TYPE_CSV,
               csvConfigModel: { delimiter: ",", quote: null, keepHeader: false, tabDelimited: false, selectedAssemblies: [] },
            }),
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.isEmptyTable()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — formatChange
// ---------------------------------------------------------------------------

describe("Group 4 — formatChange: isReport vs non-report, onlyDataComponents reset", () => {
   it("should set model.actionModel.emailInfoModel.formatType when isReport=false", () => {
      const { comp } = makeComponent({ isReport: false });

      comp.formatChange(FileFormatType.EXPORT_TYPE_PDF);

      expect(comp.model.actionModel.emailInfoModel.formatType).toBe(FileFormatType.EXPORT_TYPE_PDF);
   });

   it("should set formatStr on both comp and model when isReport=true", () => {
      const { comp } = makeComponent({ isReport: true });

      comp.formatChange("pdf");

      expect(comp.formatStr).toBe("pdf");
      expect(comp.model.actionModel.emailInfoModel.formatStr).toBe("pdf");
   });

   it("should clear onlyDataComponents when value is not EXPORT_TYPE_EXCEL", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ onlyDataComponents: true }),
         }),
      });
      const { comp } = makeComponent({ model });

      comp.formatChange(FileFormatType.EXPORT_TYPE_PDF);

      expect(comp.model.actionModel.emailInfoModel.onlyDataComponents).toBe(false);
   });

   it("should NOT clear onlyDataComponents when value equals EXPORT_TYPE_EXCEL", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ onlyDataComponents: true }),
         }),
      });
      const { comp } = makeComponent({ model });

      comp.formatChange(FileFormatType.EXPORT_TYPE_EXCEL);

      expect(comp.model.actionModel.emailInfoModel.onlyDataComponents).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — selectConditionType: EVERY_MONTH
// ---------------------------------------------------------------------------

describe("Group 5 — selectConditionType: EVERY_MONTH initializes monthlyDaySelected", () => {
   it("should set monthlyDaySelected=true when selecting EVERY_MONTH and it was null", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ monthlyDaySelected: null as any }),
      });
      const { comp } = makeComponent({ model });

      comp.selectConditionType(TimeConditionType.EVERY_MONTH);

      expect(comp.model.timeConditionModel.monthlyDaySelected).toBe(true);
   });

   it("should leave monthlyDaySelected unchanged when selecting EVERY_MONTH and it is already set", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ monthlyDaySelected: false }),
      });
      const { comp } = makeComponent({ model });

      comp.selectConditionType(TimeConditionType.EVERY_MONTH);

      // false is not null — so the guard `if(monthlyDaySelected == null)` is false → no assignment
      expect(comp.model.timeConditionModel.monthlyDaySelected).toBe(false);
   });

   it("should not change monthlyDaySelected when selecting EVERY_DAY", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ monthlyDaySelected: null as any }),
      });
      const { comp } = makeComponent({ model });

      comp.selectConditionType(TimeConditionType.EVERY_DAY);

      expect(comp.model.timeConditionModel.monthlyDaySelected).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — changeStartTimeModel: timeZone enable/disable
// ---------------------------------------------------------------------------

describe("Group 6 — changeStartTimeModel: enables/disables timeZone control", () => {
   it("should enable the timeZone form control when startTimeSelected=true", () => {
      const { comp } = makeComponent();

      comp.changeStartTimeModel({ startTime: { hour: 8, minute: 0, second: 0 }, timeRange: null, startTimeSelected: true, valid: true });

      expect(comp.form.get("timeZone")?.enabled).toBe(true);
   });

   it("should disable the timeZone form control when startTimeSelected=false", () => {
      const { comp } = makeComponent();

      comp.changeStartTimeModel({ startTime: null, timeRange: null, startTimeSelected: false, valid: true });

      expect(comp.form.get("timeZone")?.disabled).toBe(true);
   });

   it("should update startTimeData on the component to the passed model", () => {
      const { comp } = makeComponent();
      const newData = { startTime: { hour: 10, minute: 30, second: 0 }, timeRange: null, startTimeSelected: true, valid: true };

      comp.changeStartTimeModel(newData);

      expect(comp.startTimeData).toBe(newData);
   });
});
