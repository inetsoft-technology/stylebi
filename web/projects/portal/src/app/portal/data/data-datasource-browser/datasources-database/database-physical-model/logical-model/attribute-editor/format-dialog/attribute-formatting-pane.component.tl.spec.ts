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
 * AttributeFormattingPane - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - getDecimalFormats merges model/user formats without duplicates
 *   Group 2 [Risk 3] - ngOnChanges/updateFormatSpec initialize date/decimal/duration state
 *   Group 3 [Risk 2] - showFormatSpec, clearFormatSpec, changeModel, typeChange
 *   Group 4 [Risk 3] - updateDateFormat and decimal precision increment/decrement paths
 *   Group 5 [Risk 1] - disabled-state helpers and apply output
 *
 * Out of scope:
 *   ComboBox internal rendering - replaced with a stub because this file targets the pane's
 *   state transitions and emitted changes, not the shared combo-box widget itself.
 */

import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { AttributeFormattingPane } from "./attribute-formatting-pane.component";
import { ComboBox } from "../../../../../../../../format/objects/combo-box.component";
import { AttributeFormatInfoModel } from "../../../../../../model/datasources/database/physical-model/logical-model/attribute-format-info-model";

@Component({ selector: "combo-box", template: "", standalone: true })
class ComboBoxStub {
   @Input() dataModel: string;
   @Input() dataValues: string[];
   @Output() onDataChange = new EventEmitter<string>();
}

interface RenderOpts {
   formatModel?: AttributeFormatInfoModel | null;
   userDecimalFmts?: any[];
   popup?: boolean;
}

function makeFormatModel(overrides: Partial<AttributeFormatInfoModel> = {}): AttributeFormatInfoModel {
   return {
      format: null,
      formatSpec: null,
      decimalFmts: [],
      durationPadZeros: false,
      ...overrides,
   } as AttributeFormatInfoModel;
}

async function renderComponent(opts: RenderOpts = {}) {
   const onApplySpy = vi.fn();
   const { fixture } = await render(AttributeFormattingPane, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: ComboBox, with: ComboBoxStub },
      ],
      componentInputs: {
         popup: opts.popup ?? true,
         formatModel: opts.formatModel ?? makeFormatModel(),
         userDecimalFmts: opts.userDecimalFmts ?? [],
      },
      on: {
         onApply: onApplySpy,
      },
   });

   return {
      fixture,
      comp: fixture.componentInstance as AttributeFormattingPane,
      onApplySpy,
   };
}

afterEach(() => vi.restoreAllMocks());

describe("Group 1 - getDecimalFormats", () => {
   it("should merge model and user decimal formats without duplicates", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ decimalFmts: [".000", ".00"] }),
         userDecimalFmts: [".000", ".0000"],
      });

      expect(comp.getDecimalFormats()).toContain("#,###.000");
      expect(comp.getDecimalFormats()).toContain("#,###.00");
      expect(comp.getDecimalFormats()).toContain("#,###.0000");
      expect(comp.getDecimalFormats().filter(f => f === "#,###.000")).toHaveLength(1);
   });
});

describe("Group 2 - ngOnChanges and updateFormatSpec", () => {
   it("should mark dateFormat as Custom when the date formatSpec is not in the built-in list", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DateFormat", formatSpec: "yyyy/MM/custom" }),
      });

      comp.ngOnChanges({});

      expect(comp.dateFormat).toBe("Custom");
      expect(comp.formatModel.formatSpec).toBe("yyyy/MM/custom");
   });

   it("should default DecimalFormat formatSpec to the first decimal pattern", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DecimalFormat", formatSpec: null }),
      });

      comp.ngOnChanges({});

      expect(comp.formatModel.formatSpec).toBe(comp.decimalFmts[0]);
   });

   it("should default DateFormat formatSpec to the first date pattern and set dateFormat=Custom", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DateFormat", formatSpec: null }),
      });

      comp.ngOnChanges({});

      expect(comp.dateFormat).toBe("Custom");
      expect(comp.formatModel.formatSpec).toBe(comp.dateFmts[0]);
   });

   it("should default DurationFormat formatSpec to the third duration format", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DurationFormat", formatSpec: null }),
      });

      comp.ngOnChanges({});

      expect(comp.formatModel.formatSpec).toBe(comp.durationFmts[2]);
   });
});

describe("Group 3 - format visibility and mutation helpers", () => {
   it("should show the custom format spec control only for supported combinations", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "MessageFormat", formatSpec: "x" }),
      });
      expect(comp.showFormatSpec()).toBe(true);

      comp.formatModel.format = "DateFormat";
      comp.dateFormat = "Custom";
      expect(comp.showFormatSpec()).toBe(true);

      comp.dateFormat = "SHORT";
      expect(comp.showFormatSpec()).toBe(false);
   });

   it("should clear formatSpec when clearFormatSpec is called with a format model", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DecimalFormat", formatSpec: "#,##0.00" }),
      });

      comp.clearFormatSpec();

      expect(comp.formatModel.formatSpec).toBeNull();
   });

   it("should update formatSpec through changeModel and typeChange", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "MessageFormat", formatSpec: "old" }),
      });

      comp.changeModel("new-format");
      expect(comp.formatModel.formatSpec).toBe("new-format");

      comp.typeChange("DurationFormat");
      expect(comp.formatModel.format).toBe("DurationFormat");
      expect(comp.formatModel.formatSpec).toBe(comp.durationFmts[2]);
   });
});

describe("Group 4 - date format and decimal precision controls", () => {
   it("should switch to the first custom date pattern when updateDateFormat receives Custom", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DateFormat", formatSpec: "SHORT" }),
      });

      comp.updateDateFormat("Custom");

      expect(comp.dateFormat).toBe("Custom");
      expect(comp.formatModel.formatSpec).toBe(comp.dateFmts[0]);
   });

   it("should write the selected built-in date format directly into formatSpec", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DateFormat", formatSpec: "SHORT" }),
      });

      comp.updateDateFormat("LONG");

      expect(comp.dateFormat).toBe("LONG");
      expect(comp.formatModel.formatSpec).toBe("LONG");
   });

   it("should increase decimal precision for empty and existing decimal specs", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DecimalFormat", formatSpec: "" }),
      });

      comp.formatModel.formatSpec = "";
      comp.increaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("###0.0");

      comp.formatModel.formatSpec = "#,##0.00";
      comp.increaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("#,##0.000");
   });

   it("should convert CurrencyFormat and PercentFormat to decimal specs when increasing precision", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "CurrencyFormat", formatSpec: null }),
      });

      comp.increaseDecimal();
      expect(comp.formatModel.format).toBe("DecimalFormat");
      expect(comp.formatModel.formatSpec).toBe("¤#.000");

      comp.formatModel = makeFormatModel({ format: "PercentFormat", formatSpec: null });
      comp.increaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("#,##0.0%");
   });

   it("should decrease decimal precision for decimal and currency specs", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DecimalFormat", formatSpec: "#,##0.00" }),
      });

      comp.decreaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("#,##0.0");

      comp.formatModel = makeFormatModel({ format: "CurrencyFormat", formatSpec: null });
      comp.decreaseDecimal();
      expect(comp.formatModel.format).toBe("DecimalFormat");
      expect(comp.formatModel.formatSpec).toBe("¤#.0");
   });

   it("should leave the spec unchanged when decreasing PercentFormat or unsupported formats", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "PercentFormat", formatSpec: "#,##0.0%" }),
      });

      comp.decreaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("#,##0.0%");

      comp.formatModel = makeFormatModel({ format: "DateFormat", formatSpec: "SHORT" });
      comp.decreaseDecimal();
      expect(comp.formatModel.formatSpec).toBe("SHORT");
   });
});

describe("Group 5 - disabled helpers and apply output", () => {
   it("should disable decimal buttons when the current format is not numeric", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "DateFormat", formatSpec: "SHORT" }),
      });

      expect(comp.increaseDecimalDisabled()).toBe(true);
      expect(comp.decreaseDecimalDisabled()).toBe(true);
   });

   it("should disable only the decrease button for percent and integer-only decimal specs", async () => {
      const { comp } = await renderComponent({
         formatModel: makeFormatModel({ format: "PercentFormat", formatSpec: "#,##0.0%" }),
      });
      expect(comp.increaseDecimalDisabled()).toBe(false);
      expect(comp.decreaseDecimalDisabled()).toBe(true);

      comp.formatModel = makeFormatModel({ format: "DecimalFormat", formatSpec: "#,##0" });
      expect(comp.decreaseDecimalDisabled()).toBe(true);
   });

   it("should emit onApply when the apply output is triggered", async () => {
      const { comp, onApplySpy } = await renderComponent();

      comp.onApply.emit(true);

      expect(onApplySpy).toHaveBeenCalledWith(true);
   });
});
