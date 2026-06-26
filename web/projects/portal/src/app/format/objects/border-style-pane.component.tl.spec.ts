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
 * BorderStylePane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — setBorderStyle: guard on null/mixed selection; multi-side update + emit
 *   Group 2 [Risk 2] — getCurrentBorderStyle: MIXED when toggle-all with differing styles
 *   Group 3 [Risk 2] — getStyleCssClass / getStyleLabel: style lookup
 *   Group 4 [Risk 1] — isNullSelectedBorder / isToggleAll / isSameBorderStyle predicates
 *
 * Out of scope: dropdown.close() DOM interaction (FixedDropdownDirective stubbed via NO_ERRORS_SCHEMA)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { StyleConstants } from "../../common/util/style-constants";
import { BorderStylePane } from "./border-style-pane.component";

function createFormatModel(overrides: Partial<FormatInfoModel> = {}): FormatInfoModel {
   return {
      borderTopStyle: String(StyleConstants.THIN_LINE),
      borderLeftStyle: String(StyleConstants.THIN_LINE),
      borderBottomStyle: String(StyleConstants.THIN_LINE),
      borderRightStyle: String(StyleConstants.THIN_LINE),
      ...overrides,
   } as FormatInfoModel;
}

function createSelectedBorder(overrides: Record<string, boolean> = {}) {
   return {
      borderTop: true,
      borderLeft: false,
      borderBottom: false,
      borderRight: false,
      ...overrides,
   };
}

async function renderPane(props: {
   formatModel?: FormatInfoModel;
   selectedBorder?: ReturnType<typeof createSelectedBorder>;
   composerPane?: boolean;
} = {}) {
   const result = await render(BorderStylePane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         formatModel: props.formatModel ?? createFormatModel(),
         selectedBorder: props.selectedBorder ?? createSelectedBorder(),
         composerPane: props.composerPane ?? false,
      },
   });
   return result.fixture.componentInstance;
}

describe("BorderStylePane — setBorderStyle — guards and side updates [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: no-op when no border side selected prevents corrupting formatModel
   it("should not mutate formatModel when no border side is selected", async () => {
      const comp = await renderPane({
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: false, borderBottom: false, borderRight: false,
         }),
      });
      const model = comp.formatModel;
      const emitSpy = vi.spyOn(comp.onClick, "emit");
      const event = { stopPropagation: vi.fn() };

      comp.setBorderStyle(event, String(StyleConstants.MEDIUM_LINE));

      expect(model.borderTopStyle).toBe(String(StyleConstants.THIN_LINE));
      expect(emitSpy).not.toHaveBeenCalled();
      expect(event.stopPropagation).toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: MIXED_BORDER style is display-only and must not be applied
   it("should ignore setBorderStyle when style is MIXED_BORDER", async () => {
      const comp = await renderPane();
      const emitSpy = vi.spyOn(comp.onClick, "emit");

      comp.setBorderStyle({ stopPropagation: vi.fn() }, String(StyleConstants.MIXED_BORDER));

      expect(comp.formatModel.borderTopStyle).toBe(String(StyleConstants.THIN_LINE));
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should apply style to selected sides and emit onClick", async () => {
      const comp = await renderPane({
         selectedBorder: createSelectedBorder({
            borderTop: true, borderLeft: true, borderBottom: false, borderRight: false,
         }),
      });
      const emitSpy = vi.spyOn(comp.onClick, "emit");
      const selected = comp.selectedBorder;
      const newStyle = String(StyleConstants.DASH_LINE);

      comp.setBorderStyle({ stopPropagation: vi.fn() }, newStyle);

      expect(comp.formatModel.borderTopStyle).toBe(newStyle);
      expect(comp.formatModel.borderLeftStyle).toBe(newStyle);
      expect(comp.formatModel.borderBottomStyle).toBe(String(StyleConstants.THIN_LINE));
      expect(emitSpy).toHaveBeenCalledWith(selected);
      expect(comp.currentBorderStyle).toBe(newStyle);
   });
});

describe("BorderStylePane — ngOnChanges — reactive parameter refresh [Group 2, Risk 2]", () => {

   it("should refresh currentBorderStyle when selectedBorder input changes", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderLeftStyle: String(StyleConstants.DASH_LINE),
         }),
         selectedBorder: createSelectedBorder({ borderTop: true, borderLeft: false }),
      });
      expect(comp.currentBorderStyle).toBe(String(StyleConstants.THIN_LINE));

      comp.selectedBorder = createSelectedBorder({ borderTop: false, borderLeft: true });
      comp.ngOnChanges({
         selectedBorder: {
            previousValue: null,
            currentValue: comp.selectedBorder,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.currentBorderStyle).toBe(String(StyleConstants.DASH_LINE));
   });
});

describe("BorderStylePane — getCurrentBorderStyle — side priority chain [Group 2, Risk 2]", () => {

   it("should return left border style when only left is selected", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderLeftStyle: String(StyleConstants.MEDIUM_LINE),
         }),
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: true, borderBottom: false, borderRight: false,
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.MEDIUM_LINE));
   });

   it("should return bottom border style when only bottom is selected", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderBottomStyle: String(StyleConstants.THICK_LINE),
         }),
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: false, borderBottom: true, borderRight: false,
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.THICK_LINE));
   });

   it("should return right border style when only right is selected", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderRightStyle: String(StyleConstants.DOUBLE_LINE),
         }),
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: false, borderBottom: false, borderRight: true,
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.DOUBLE_LINE));
   });
});

describe("BorderStylePane — getCurrentBorderStyle — mixed detection [Group 2, Risk 2]", () => {

   it("should return MIXED_BORDER when all sides selected with different styles", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: String(StyleConstants.THIN_LINE),
            borderLeftStyle: String(StyleConstants.DASH_LINE),
            borderBottomStyle: String(StyleConstants.THIN_LINE),
            borderRightStyle: String(StyleConstants.THIN_LINE),
         }),
         selectedBorder: createSelectedBorder({
            borderTop: true, borderLeft: true, borderBottom: true, borderRight: true,
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.MIXED_BORDER));
   });

   it("should return top border style when only top is selected", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: String(StyleConstants.DOT_LINE),
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.DOT_LINE));
   });

   it("should return NO_BORDER when no side matches selectedBorder flags", async () => {
      const comp = await renderPane({
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: false, borderBottom: false, borderRight: false,
         }),
      });

      expect(comp.getCurrentBorderStyle()).toBe(String(StyleConstants.NO_BORDER));
   });
});

describe("BorderStylePane — style label and css class lookup [Group 3, Risk 2]", () => {

   it("should resolve css class and label for THIN_LINE", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: String(StyleConstants.THIN_LINE),
         }),
      });
      comp.updateParameters();

      expect(comp.getStyleCssClass()).toBe("line-style-THIN_LINE");
      expect(comp.getStyleLabel()).toBeNull();
   });

   it("should resolve label for None border style", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: String(StyleConstants.NO_BORDER),
         }),
      });
      comp.updateParameters();

      expect(comp.getStyleLabel()).toBe("_#(js:None)");
   });
});

describe("BorderStylePane — border selection predicates [Group 4, Risk 1]", () => {

   it("should report isNullSelectedBorder true when no side selected", async () => {
      const comp = await renderPane({
         selectedBorder: createSelectedBorder({
            borderTop: false, borderLeft: false, borderBottom: false, borderRight: false,
         }),
      });

      expect(comp.isNullSelectedBorder()).toBe(true);
      expect(comp.isToggleAll()).toBe(false);
   });

   it("should report isToggleAll true when all four sides selected", async () => {
      const comp = await renderPane({
         selectedBorder: createSelectedBorder({
            borderTop: true, borderLeft: true, borderBottom: true, borderRight: true,
         }),
      });

      expect(comp.isToggleAll()).toBe(true);
   });

   it("should report isSameBorderStyle true when all sides share one style", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: "4097",
            borderLeftStyle: "4097",
            borderBottomStyle: "4097",
            borderRightStyle: "4097",
         }),
      });

      expect(comp.isSameBorderStyle()).toBe(true);
   });

   it("should report isSameBorderStyle false when styles differ", async () => {
      const comp = await renderPane({
         formatModel: createFormatModel({
            borderTopStyle: String(StyleConstants.THIN_LINE),
            borderLeftStyle: String(StyleConstants.DASH_LINE),
            borderBottomStyle: String(StyleConstants.THIN_LINE),
            borderRightStyle: String(StyleConstants.THIN_LINE),
         }),
      });

      expect(comp.isSameBorderStyle()).toBe(false);
   });
});

describe("BorderStylePane — ngOnInit composerPane styles list [Group 4, Risk 1]", () => {

   it("should include 3D border styles when composerPane is false", async () => {
      const comp = await renderPane({ composerPane: false });

      expect(comp.styles.some(s => s.cssClass === "line-style-RAISED_3D")).toBe(true);
      expect(comp.styles.length).toBeGreaterThan(10);
   });

   it("should omit 3D border styles when composerPane is true", async () => {
      const comp = await renderPane({ composerPane: true });

      expect(comp.styles.some(s => s.cssClass === "line-style-RAISED_3D")).toBe(false);
   });
});
