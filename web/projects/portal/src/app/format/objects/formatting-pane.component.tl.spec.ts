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
 * FormattingPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — increaseDecimal(): non-number format → no-op; Currency → "¤#.000";
 *     Percent → "#,##0.0%"; empty/null spec → "###0.0"; spec with dot → inserts "0" after dot;
 *     spec with % → inserts ".0" before "%"; spec with no dot/% → adds ".0" after last 0/#
 *   Group 2 [Risk 3] — decreaseDecimal(): non-number format → no-op; Currency → "¤#.0";
 *     Percent → no-op; empty spec → no-op; spec with dot+digit → removes one decimal;
 *     spec "0.0" → "0"; compound specs (semicolon) handled correctly
 *   Group 3 [Risk 2] — increaseDecimalDisabled(): false for Currency/Percent/Decimal; true for
 *     all others
 *   Group 4 [Risk 2] — decreaseDecimalDisabled(): rules per format/spec combinations
 *   Group 5 [Risk 2] — showFormatSpec(): null model/format → false; DecimalFormat/MessageFormat/
 *     DurationFormat → true; DateFormat+Custom → true; DateFormat+non-Custom → false
 *   Group 6 [Risk 2] — clearFormatSpec(): null model → returns; sets formatSpec=null;
 *     DateFormat → sets dateSpec="Custom"; DurationFormat → sets formatSpec to durationFmts[2];
 *     MessageFormat → only clears spec (does not set dateSpec)
 *   Group 7 [Risk 1] — typeChange(): sets format + calls clearFormatSpec
 *   Group 8 [Risk 1] — changeModel(): sets formatModel.formatSpec
 *   Group 9 [Risk 1] — getDecimalFormats(): merges formatModel.decimalFmts with "#,###" prefix;
 *     de-duplicates entries already in list
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Template rendering — FormattingPane uses DynamicComboBox/ComboBox children; DOM assertions
 *     are integration-level. Pure-logic methods are tested on the class instance directly.
 */

import { FormattingPane } from "./formatting-pane.component";
import { FormatInfoModel } from "../../common/data/format-info-model";

// ---------------------------------------------------------------------------
// Shared fixture helpers (pure class — no render() needed)
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<FormatInfoModel> = {}): FormatInfoModel {
   return {
      type: "",
      color: "",
      backgroundColor: "",
      font: null,
      align: null,
      format: null,
      formatSpec: null,
      dateSpec: null,
      ...overrides,
   } as any;
}

let pane: FormattingPane;

beforeEach(() => {
   pane = new FormattingPane();
});

// ---------------------------------------------------------------------------
// Group 1: increaseDecimal [Risk 3]
// ---------------------------------------------------------------------------

describe("FormattingPane — increaseDecimal", () => {
   it("should be a no-op for DateFormat", () => {
      pane.formatModel = makeModel({ format: "DateFormat", formatSpec: null });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should be a no-op for MessageFormat", () => {
      pane.formatModel = makeModel({ format: "MessageFormat", formatSpec: null });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should produce '¤#.000' for CurrencyFormat", () => {
      pane.formatModel = makeModel({ format: "CurrencyFormat", formatSpec: null });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("¤#.000");
      expect(pane.formatModel.format).toBe("DecimalFormat");
   });

   it("should produce '#,##0.0%' for PercentFormat", () => {
      pane.formatModel = makeModel({ format: "PercentFormat", formatSpec: null });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("#,##0.0%");
      expect(pane.formatModel.format).toBe("DecimalFormat");
   });

   // 🔁 Regression-sensitive: empty formatSpec should generate "###0.0" not fail.
   it("should produce '###0.0' for empty DecimalFormat spec", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "" });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("###0.0");
   });

   it("should produce '###0.0' for null DecimalFormat spec", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: null });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("###0.0");
   });

   it("should insert '0' after existing dot", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0" });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0.00");
   });

   it("should handle empty-dot spec '0.'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0." });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0.0");
   });

   it("should handle compound spec with semicolon", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "###0.0;(###0.0)" });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("###0.00;(###0.00)");
   });

   it("should insert '.0' before '%' when spec ends with '%'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "###0%;(###0%)" });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("###0.0%;(###0.0%)");
   });

   it("should add '.0' after last integer digit when no dot or %", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "text" });
      pane.increaseDecimal();
      expect(pane.formatModel.formatSpec).toBe(".0text");
   });
});

// ---------------------------------------------------------------------------
// Group 2: decreaseDecimal [Risk 3]
// ---------------------------------------------------------------------------

describe("FormattingPane — decreaseDecimal", () => {
   it("should be a no-op for DateFormat", () => {
      pane.formatModel = makeModel({ format: "DateFormat", formatSpec: null });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should be a no-op for PercentFormat", () => {
      pane.formatModel = makeModel({ format: "PercentFormat", formatSpec: null });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should produce '¤#.0' for CurrencyFormat", () => {
      pane.formatModel = makeModel({ format: "CurrencyFormat", formatSpec: null });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("¤#.0");
      expect(pane.formatModel.format).toBe("DecimalFormat");
   });

   it("should be a no-op for empty DecimalFormat spec", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "" });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("");
   });

   it("should remove trailing zero after dot: '0.0' → '0'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0" });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0");
   });

   it("should remove empty dot: '0.' → '0'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0." });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0");
   });

   it("should handle compound spec: '0.0;(0.0)' → '0;(0)'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0;(0.0)" });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0;(0)");
   });

   it("should handle '0.00;(0.00)' → '0.0;(0.0)'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.00;(0.00)" });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0.0;(0.0)");
   });

   it("should handle percent in decimal: '0.0%;(0.0%)' → '0%;(0%)'", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0%;(0.0%)" });
      pane.decreaseDecimal();
      expect(pane.formatModel.formatSpec).toBe("0%;(0%)");
   });
});

// ---------------------------------------------------------------------------
// Group 3: increaseDecimalDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("FormattingPane — increaseDecimalDisabled", () => {
   it("should return false for CurrencyFormat", () => {
      pane.formatModel = makeModel({ format: "CurrencyFormat" });
      expect(pane.increaseDecimalDisabled()).toBe(false);
   });

   it("should return false for PercentFormat", () => {
      pane.formatModel = makeModel({ format: "PercentFormat" });
      expect(pane.increaseDecimalDisabled()).toBe(false);
   });

   it("should return false for DecimalFormat", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat" });
      expect(pane.increaseDecimalDisabled()).toBe(false);
   });

   it("should return true for DateFormat", () => {
      pane.formatModel = makeModel({ format: "DateFormat" });
      expect(pane.increaseDecimalDisabled()).toBe(true);
   });

   it("should return true for MessageFormat", () => {
      pane.formatModel = makeModel({ format: "MessageFormat" });
      expect(pane.increaseDecimalDisabled()).toBe(true);
   });

   it("should return true for null format", () => {
      pane.formatModel = makeModel({ format: null });
      expect(pane.increaseDecimalDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: decreaseDecimalDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("FormattingPane — decreaseDecimalDisabled", () => {
   it("should return true for non-number format (DateFormat)", () => {
      pane.formatModel = makeModel({ format: "DateFormat", formatSpec: "0.0" });
      expect(pane.decreaseDecimalDisabled()).toBe(true);
   });

   it("should return true for PercentFormat", () => {
      pane.formatModel = makeModel({ format: "PercentFormat", formatSpec: "0.0" });
      expect(pane.decreaseDecimalDisabled()).toBe(true);
   });

   it("should return false for CurrencyFormat", () => {
      pane.formatModel = makeModel({ format: "CurrencyFormat", formatSpec: "0.0" });
      expect(pane.decreaseDecimalDisabled()).toBe(false);
   });

   it("should return false for DecimalFormat with decimal", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0" });
      expect(pane.decreaseDecimalDisabled()).toBe(false);
   });

   it("should return true for DecimalFormat without decimal", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0" });
      expect(pane.decreaseDecimalDisabled()).toBe(true);
   });

   it("should return true for DecimalFormat with null formatSpec", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: null });
      expect(pane.decreaseDecimalDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: showFormatSpec [Risk 2]
// ---------------------------------------------------------------------------

describe("FormattingPane — showFormatSpec", () => {
   it("should return false when formatModel is null", () => {
      pane.formatModel = null;
      expect(pane.showFormatSpec()).toBe(false);
   });

   it("should return false when format is null", () => {
      pane.formatModel = makeModel({ format: null });
      expect(pane.showFormatSpec()).toBe(false);
   });

   it("should return true for DecimalFormat", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat" });
      expect(pane.showFormatSpec()).toBe(true);
   });

   it("should return true for MessageFormat", () => {
      pane.formatModel = makeModel({ format: "MessageFormat" });
      expect(pane.showFormatSpec()).toBe(true);
   });

   it("should return true for DurationFormat", () => {
      pane.formatModel = makeModel({ format: "DurationFormat" });
      expect(pane.showFormatSpec()).toBe(true);
   });

   it("should return true for DateFormat when dateSpec is 'Custom'", () => {
      pane.formatModel = makeModel({ format: "DateFormat", dateSpec: "Custom" });
      expect(pane.showFormatSpec()).toBe(true);
   });

   it("should return false for DateFormat when dateSpec is not 'Custom'", () => {
      pane.formatModel = makeModel({ format: "DateFormat", dateSpec: "SHORT" });
      expect(pane.showFormatSpec()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: clearFormatSpec [Risk 2]
// ---------------------------------------------------------------------------

describe("FormattingPane — clearFormatSpec", () => {
   it("should be a no-op when formatModel is null", () => {
      pane.formatModel = null;
      expect(() => pane.clearFormatSpec()).not.toThrow();
   });

   it("should set formatSpec to null", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat", formatSpec: "0.0" });
      pane.clearFormatSpec();
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should set dateSpec to 'Custom' for DateFormat", () => {
      pane.formatModel = makeModel({ format: "DateFormat", formatSpec: "yyyy-MM-dd" });
      pane.clearFormatSpec();
      expect(pane.formatModel.dateSpec).toBe("Custom");
   });

   it("should set formatSpec to durationFmts[2] for DurationFormat", () => {
      pane.formatModel = makeModel({ format: "DurationFormat" });
      pane.clearFormatSpec();
      expect(pane.formatModel.formatSpec).toBe("dd HH:mm:ss");
   });

   it("should only clear formatSpec for MessageFormat (no dateSpec change)", () => {
      pane.formatModel = makeModel({ format: "MessageFormat", formatSpec: "text", dateSpec: "FULL" });
      pane.clearFormatSpec();
      expect(pane.formatModel.formatSpec).toBeNull();
      expect(pane.formatModel.dateSpec).toBe("FULL");
   });
});

// ---------------------------------------------------------------------------
// Group 7: typeChange [Risk 1]
// ---------------------------------------------------------------------------

describe("FormattingPane — typeChange", () => {
   it("should set format and clear formatSpec", () => {
      pane.formatModel = makeModel({ format: "MessageFormat", formatSpec: "old" });
      pane.typeChange("DecimalFormat");
      expect(pane.formatModel.format).toBe("DecimalFormat");
      expect(pane.formatModel.formatSpec).toBeNull();
   });

   it("should be a no-op when formatModel is null", () => {
      pane.formatModel = null;
      expect(() => pane.typeChange("DecimalFormat")).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 8: changeModel [Risk 1]
// ---------------------------------------------------------------------------

describe("FormattingPane — changeModel", () => {
   it("should set formatModel.formatSpec to the given string", () => {
      pane.formatModel = makeModel({ format: "DecimalFormat" });
      pane.changeModel("#,##0.00");
      expect(pane.formatModel.formatSpec).toBe("#,##0.00");
   });

   it("should be a no-op when formatModel is null", () => {
      pane.formatModel = null;
      expect(() => pane.changeModel("#,##0.00")).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 9: getDecimalFormats [Risk 1]
// ---------------------------------------------------------------------------

describe("FormattingPane — getDecimalFormats", () => {
   it("should return base decimalFmts when formatModel has no custom decimalFmts", () => {
      pane.formatModel = makeModel();
      const result = pane.getDecimalFormats();
      expect(result).toBe(pane.decimalFmts);
      expect(result.length).toBeGreaterThan(0);
   });

   it("should merge formatModel.decimalFmts with '#,###' prefix", () => {
      pane.formatModel = makeModel({ decimalFmts: [".00", ".0"] } as any);
      const result = pane.getDecimalFormats();
      expect(result).toContain("#,###.00");
      expect(result).toContain("#,###.0");
   });

   it("should not add duplicates if format is already in the list", () => {
      pane.formatModel = makeModel({ decimalFmts: [".00", ".00"] } as any);
      const before = pane.getDecimalFormats().length;
      // calling again should not add duplicates
      const after = pane.getDecimalFormats().length;
      expect(after).toBe(before);
   });
});
