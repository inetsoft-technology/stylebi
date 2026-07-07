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
 * CalculatePaneDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit: cube vs non-cube calcDatas initialization
 *   Group 2 [Risk 3] - calcTypeChange: PERCENT/CHANGE/RUNNINGTOTAL/MOVING/VALUE/COMPOUNDGROWTH
 *   Group 3 [Risk 3] - ok(): emits onCommit with calc + percentageDirection; resetLevel fallback
 *   Group 4 [Risk 2] - cancel(): emits cancel token
 *   Group 5 [Risk 2] - calculator setter: parses n from aggregate like "Sum(3)"
 *   Group 6 [Risk 2] - isNValid(), getPercentOfLabel(), percOfValue getter/setter
 *
 * Confirmed bugs (it.fails): none
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { StyleConstants } from "../../../../common/util/style-constants";
import {
   ChangeCalcInfo,
   CompoundGrowthCalcInfo,
   MovingCalcInfo,
   PercentCalcInfo,
   RunningTotalCalcInfo,
   RunningTotalCalcRestLevel,
   ValueOfCalcInfo,
} from "../../../data/chart/calculate-info";
import { DimensionInfo } from "../../../data/dimension-info";
import { CalculatePaneDialog } from "./calculate-pane-dialog.component";

const VALUE_OF_DIMS: DimensionInfo[] = [
   { data: "OrderDate", label: "Order Date" },
];

const PERC_LEVELS: DimensionInfo[] = [
   { data: "1", label: "Grand Total" },
];

const PERC_DIMS: DimensionInfo[] = [
   { data: "Region", label: "Region" },
];

interface RenderOpts {
   cube?: boolean;
   calculator?: any;
   percentageDirection?: string;
   percOfLevels?: DimensionInfo[];
   percOfDims?: DimensionInfo[];
   valueOfDatas?: DimensionInfo[];
   resetOptsMap?: Map<string, Array<any>>;
   supportResetMap?: Map<string, boolean>;
   crosstab?: boolean;
   hasRow?: boolean;
   hasCol?: boolean;
}

async function renderDialog(opts: RenderOpts = {}) {
   const { fixture } = await render(CalculatePaneDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         cube: opts.cube ?? false,
         valueOfDatas: opts.valueOfDatas ?? VALUE_OF_DIMS,
         percOfLevels: opts.percOfLevels ?? PERC_LEVELS,
         percOfDims: opts.percOfDims ?? PERC_DIMS,
         percentageDirection: opts.percentageDirection ?? String(StyleConstants.PERCENTAGE_BY_COL),
         crosstab: opts.crosstab ?? false,
         hasRow: opts.hasRow ?? false,
         hasCol: opts.hasCol ?? false,
         resetOptsMap: opts.resetOptsMap ?? new Map(),
         supportResetMap: opts.supportResetMap ?? new Map(),
      },
   });

   const comp = fixture.componentInstance as CalculatePaneDialog;

   if(opts.calculator) {
      comp.calculator = opts.calculator;
   }

   return { comp, fixture };
}

function makeMouseEvent(): MouseEvent {
   return { stopPropagation: vi.fn() } as unknown as MouseEvent;
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - ngOnInit calcDatas", () => {
   it("should expose only Percent when cube mode is enabled", async () => {
      const { comp } = await renderDialog({ cube: true });

      comp.ngOnInit();

      expect(comp.calcDatas).toEqual([
         { label: "_#(js:Percent)", data: "PERCENT" },
      ]);
   });

   it("should expose all built-in calc types when cube mode is disabled", async () => {
      const { comp } = await renderDialog({ cube: false });

      comp.ngOnInit();

      expect(comp.calcDatas).toEqual([
         { label: "_#(js:Percent)", data: "PERCENT" },
         { label: "_#(js:Change)", data: "CHANGE" },
         { label: "_#(js:Running)", data: "RUNNINGTOTAL" },
         { label: "_#(js:Sliding)", data: "MOVING" },
         { label: "_#(js:Value of)", data: "VALUE" },
         { label: "_#(js:Compound Growth)", data: "COMPOUNDGROWTH" },
      ]);
   });
});

describe("Group 2 - calcTypeChange", () => {
   it("should create a PercentCalcInfo for PERCENT", async () => {
      const { comp } = await renderDialog();
      comp.ngOnInit();

      comp.calcTypeChange("PERCENT");

      expect(comp.calculator).toBeInstanceOf(PercentCalcInfo);
      expect(comp.calculator.classType).toBe("PERCENT");
      expect((comp.calculator as PercentCalcInfo).level).toBe(1);
      expect(comp.calculator.view).toBe("Percent of");
   });

   it("should create a ChangeCalcInfo for CHANGE", async () => {
      const { comp } = await renderDialog();
      comp.ngOnInit();

      comp.calcTypeChange("CHANGE");

      expect(comp.calculator).toBeInstanceOf(ChangeCalcInfo);
      expect(comp.calculator.classType).toBe("CHANGE");
      expect((comp.calculator as ChangeCalcInfo).columnName).toBe("OrderDate");
      expect((comp.calculator as ChangeCalcInfo).from).toBe(0);
      expect((comp.calculator as ChangeCalcInfo).asPercent).toBe(false);
   });

   it("should create a RunningTotalCalcInfo for RUNNINGTOTAL", async () => {
      const { comp } = await renderDialog();
      comp.ngOnInit();

      comp.calcTypeChange("RUNNINGTOTAL");

      expect(comp.calculator).toBeInstanceOf(RunningTotalCalcInfo);
      expect(comp.calculator.classType).toBe("RUNNINGTOTAL");
      expect((comp.calculator as RunningTotalCalcInfo).aggregate).toBe("Sum");
      expect((comp.calculator as RunningTotalCalcInfo).resetLevel).toBe(-1);
   });

   it("should create a MovingCalcInfo for MOVING with crosstab inner dimension", async () => {
      const { comp } = await renderDialog({ crosstab: true, hasRow: true, hasCol: false });
      comp.ngOnInit();

      comp.calcTypeChange("MOVING");

      expect(comp.calculator).toBeInstanceOf(MovingCalcInfo);
      expect(comp.calculator.classType).toBe("MOVING");
      expect((comp.calculator as MovingCalcInfo).aggregate).toBe("Average");
      expect((comp.calculator as MovingCalcInfo).innerDim).toBe("0");
   });

   it("should create a ValueOfCalcInfo for VALUE", async () => {
      const { comp } = await renderDialog();
      comp.ngOnInit();

      comp.calcTypeChange("VALUE");

      expect(comp.calculator).toBeInstanceOf(ValueOfCalcInfo);
      expect(comp.calculator.classType).toBe("VALUE");
      expect((comp.calculator as ValueOfCalcInfo).columnName).toBe("OrderDate");
      expect((comp.calculator as ValueOfCalcInfo).from).toBe(0);
   });

   it("should create a CompoundGrowthCalcInfo for COMPOUNDGROWTH", async () => {
      const { comp } = await renderDialog();
      comp.ngOnInit();

      comp.calcTypeChange("COMPOUNDGROWTH");

      expect(comp.calculator).toBeInstanceOf(CompoundGrowthCalcInfo);
      expect(comp.calculator.classType).toBe("COMPOUNDGROWTH");
      expect((comp.calculator as CompoundGrowthCalcInfo).resetLevel).toBe(-1);
   });
});

describe("Group 3 - ok()", () => {
   it("should emit onCommit with calc and percentageDirection", async () => {
      const { comp } = await renderDialog({
         percentageDirection: String(StyleConstants.PERCENTAGE_BY_ROW),
      });
      comp.ngOnInit();
      comp.calcTypeChange("PERCENT");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok(makeMouseEvent());

      expect(emitSpy).toHaveBeenCalledWith({
         calc: comp.calculator,
         percentageDirection: String(StyleConstants.PERCENTAGE_BY_ROW),
      });
   });

   it("should reset resetLevel to NONE when reset options are unavailable", async () => {
      const calc = new RunningTotalCalcInfo();
      calc.classType = "RUNNINGTOTAL";
      calc.aggregate = "Sum";
      calc.resetLevel = RunningTotalCalcRestLevel.YEAR;
      const { comp } = await renderDialog({
         calculator: calc,
         resetOptsMap: new Map(),
      });
      comp.ngOnInit();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok(makeMouseEvent());

      expect((comp.calculator as RunningTotalCalcInfo).resetLevel)
         .toBe(RunningTotalCalcRestLevel.NONE);
      expect(emitSpy).toHaveBeenCalled();
   });
});

describe("Group 4 - cancel()", () => {
   it("should emit cancel and stop event propagation", async () => {
      const { comp } = await renderDialog();
      const cancelSpy = vi.spyOn(comp.onCancel, "emit");
      const evt = makeMouseEvent();

      comp.cancel(evt);

      expect(evt.stopPropagation).toHaveBeenCalled();
      expect(cancelSpy).toHaveBeenCalledWith("cancel");
   });
});

describe("Group 5 - calculator setter", () => {
   it("should parse n from aggregate formulas like Sum(3)", async () => {
      const calc = new RunningTotalCalcInfo();
      calc.classType = "RUNNINGTOTAL";
      calc.aggregate = "Sum(3)";
      const { comp } = await renderDialog();

      comp.calculator = calc;

      expect(comp.n).toBe(3);
      expect((comp.calculator as RunningTotalCalcInfo).aggregate).toBe("Sum");
   });
});

describe("Group 6 - validation and percent-of helpers", () => {
   it("should report n as valid only when greater than zero", async () => {
      const { comp } = await renderDialog();
      comp.n = 2;
      expect(comp.isNValid()).toBe(true);

      comp.n = 0;
      expect(comp.isNValid()).toBe(false);
   });

   it("should return the row label when percentageDirection is by row", async () => {
      const { comp } = await renderDialog({
         percentageDirection: String(StyleConstants.PERCENTAGE_BY_ROW),
      });

      expect(comp.getPercentOfLabel()).toBe("_#(js:Rows)");
   });

   it("should return the column label when percentageDirection is by column", async () => {
      const { comp } = await renderDialog({
         percentageDirection: String(StyleConstants.PERCENTAGE_BY_COL),
      });

      expect(comp.getPercentOfLabel()).toBe("_#(js:Columns)");
   });

   it("should read and write percOfValue using level or column name", async () => {
      const calc = new PercentCalcInfo();
      calc.classType = "PERCENT";
      calc.level = 1;
      const { comp } = await renderDialog({ calculator: calc });
      comp.ngOnInit();

      expect(comp.percOfValue).toBe(1);

      comp.percOfValue = "Region";
      expect((comp.calculator as PercentCalcInfo).columnName).toBe("Region");
      expect((comp.calculator as PercentCalcInfo).level).toBe(1);

      comp.percOfValue = "1";
      expect((comp.calculator as PercentCalcInfo).columnName).toBeNull();
      expect((comp.calculator as PercentCalcInfo).level).toBe("1");
   });
});
