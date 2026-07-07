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
 * ColorMappingDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit: clone color maps and seed empty dialog with one row
 *   Group 2 [Risk 3] — ok: dedupe duplicate options and write maps to model (Bug #21331)
 *   Group 3 [Risk 2] — close/toggleGlobal/addRow/deleteRow/reset: dialog edit lifecycle
 *   Group 4 [Risk 2] — onManualInputToggle: date-level label/value round-trip
 *   Group 5 [Risk 1] — truncatedDimensionData: cap dimension list at 5000 entries
 *
 * HTTP: no HTTP — dialog operates on in-memory ColorMappingDialogModel
 *
 * Out of scope:
 *   browseDataErrorMsg dialog — opens ComponentTool.showMessageDialog modal side effect
 *   isValid — reads model.colorMaps not currentColorMaps; no template submit guard entry point
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ColorMap } from "../../../common/data/color-map";
import { TestUtils } from "../../../common/test/test-utils";
import { ColorMappingDialogModel } from "../../data/chart/color-mapping-dialog-model";
import { ColorMappingDialog } from "./color-mapping-dialog.component";

function createModel(overrides: Partial<ColorMappingDialogModel> = {}): ColorMappingDialogModel {
   return {
      colorMaps: [
         { option: "1997", color: "#ffff00", manualInput: false },
         { option: "1997", color: "#ff0000", manualInput: false },
         { option: "1997", color: "#0000ff", manualInput: false }
      ],
      globalModel: {
         colorMaps: [],
         dimensionData: [],
         globalModel: null as any,
         useGlobal: true,
         shareColors: false
      },
      useGlobal: false,
      shareColors: false,
      dimensionData: [
         { label: "1997", value: "1997" },
         { label: "1998", value: "1998" }
      ],
      ...overrides
   };
}

function createDialog(model = createModel()): ColorMappingDialog {
   const dialog = new ColorMappingDialog({} as NgbModal);
   dialog.model = model;
   dialog.field = TestUtils.createMockAestheticInfo("orderdate");
   dialog.field.dataInfo = TestUtils.createMockChartDimensionRef("orderdate");
   return dialog;
}

describe("ColorMappingDialog — ngOnInit [Group 1, Risk 2]", () => {
   it("should clone existing color maps on init", () => {
      const dialog = createDialog();

      dialog.ngOnInit();

      expect(dialog.currentColorMaps).toHaveLength(3);
      expect(dialog.currentColorMaps[0]).not.toBe(dialog.model.colorMaps[0]);
   });

   it("should add a starter row when model has no color maps", () => {
      const dialog = createDialog(createModel({
         colorMaps: [],
         dimensionData: [{ label: "A", value: "A" }]
      }));

      dialog.ngOnInit();

      expect(dialog.currentColorMaps).toHaveLength(1);
      expect(dialog.currentColorMaps[0].option).toBe("A");
   });
});

describe("ColorMappingDialog — ok [Group 2, Risk 3]", () => {
   // 🔁 Regression-sensitive: Bug #21331 — duplicate options must collapse to one map entry
   it("should emit deduplicated color maps on commit", () => {
      const dialog = createDialog();
      dialog.ngOnInit();
      const committed = vi.fn();
      dialog.onCommit.subscribe(committed);

      dialog.ok();

      expect(committed).toHaveBeenCalledWith([
         { option: "1997", color: "#0000ff", manualInput: false }
      ]);
      expect(dialog.model.colorMaps).toHaveLength(1);
   });

   it("should write committed maps to globalModel when useGlobal is true", () => {
      const model = createModel({ useGlobal: true });
      const dialog = createDialog(model);
      dialog.ngOnInit();
      dialog.currentColorMaps = [{ option: "1998", color: "#123456", manualInput: false }];

      dialog.ok();

      expect(model.globalModel.colorMaps).toEqual([
         { option: "1998", color: "#123456", manualInput: false }
      ]);
   });
});

describe("ColorMappingDialog — edit lifecycle [Group 3, Risk 2]", () => {
   it("should emit initial maps on close and cancel", () => {
      const dialog = createDialog();
      dialog.ngOnInit();
      const committed = vi.fn();
      const cancelled = vi.fn();
      dialog.onCommit.subscribe(committed);
      dialog.onCancel.subscribe(cancelled);

      dialog.close();

      expect(committed).toHaveBeenCalled();
      expect(cancelled).toHaveBeenCalledWith("cancel");
   });

   it("should add, delete, reset rows and toggle global mode", () => {
      const dialog = createDialog(createModel({ useGlobal: false }));
      dialog.ngOnInit();

      dialog.addRow();
      expect(dialog.currentColorMaps.length).toBe(4);

      dialog.deleteRow(0);
      expect(dialog.currentColorMaps.length).toBe(3);

      dialog.reset();
      expect(dialog.currentColorMaps.length).toBe(1);

      dialog.toggleGlobal();
      expect(dialog.model.useGlobal).toBe(true);
   });
});

describe("ColorMappingDialog — onManualInputToggle [Group 4, Risk 2]", () => {
   it("should convert raw value to label when enabling manual input for date-level field", () => {
      const dialog = createDialog(createModel({
         colorMaps: [{ option: "1970-01-01 16:00:00", color: "#ff0000" }],
         dimensionData: [
            { label: "16", value: "1970-01-01 16:00:00" }
         ]
      }));
      dialog.field.dataInfo = Object.assign(
         TestUtils.createMockChartDimensionRef("orderdate"),
         { dateLevel: 8 }
      );
      dialog.ngOnInit();
      const colorMap = dialog.currentColorMaps[0];

      dialog.onManualInputToggle(colorMap, true);

      expect(colorMap.manualInput).toBe(true);
      expect(colorMap.option).toBe("16");
   });

   it("should restore raw value when disabling manual input", () => {
      const dialog = createDialog(createModel({
         colorMaps: [{ option: "1970-01-01 16:00:00", color: "#ff0000" }],
         dimensionData: [
            { label: "16", value: "1970-01-01 16:00:00" }
         ]
      }));
      dialog.field.dataInfo = Object.assign(
         TestUtils.createMockChartDimensionRef("orderdate"),
         { dateLevel: 8 }
      );
      dialog.ngOnInit();
      const colorMap = dialog.currentColorMaps[0];

      dialog.onManualInputToggle(colorMap, true);
      expect(colorMap.option).toBe("16");
      dialog.onManualInputToggle(colorMap, false);

      expect(colorMap.manualInput).toBe(false);
      expect(colorMap.option).toBe("1970-01-01 16:00:00");
   });
});

describe("ColorMappingDialog — truncatedDimensionData [Group 5, Risk 1]", () => {
   it("should cap dimensionData at 5000 entries for rendering", () => {
      const dialog = createDialog();
      dialog.dimensionData = Array.from({ length: 5001 }, (_, i) => ({
         label: `L${i}`,
         value: `V${i}`
      }));

      expect(dialog.truncatedDimensionData).toHaveLength(5000);
   });
});
