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
 * BindingBorderPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — setDefault(): all styles null, all colors = defaultColor (Bug #10699, #17594)
 *   Group 2 [Risk 2] — updateBorder(op): four branches set selectedBorder + currentColor
 *   Group 3 [Risk 2] — selectAll(): toggle between all-selected and none-selected
 *   Group 4 [Risk 2] — isSameBorderColor(): null model returns true; mixed colors return false
 *   Group 5 [Risk 2] — repaintBorder(): updates model border colors for selected borders
 *   Group 6 [Risk 1] — isDefaultBorder, isToggleAll, isNullSelectedBorder
 *
 * Old spec ported (Risk 3):
 *   Bug #10699 + #17594: setDefault() must null all border styles and reset all colors to defaultColor.
 *
 * Out of scope:
 *   populateCanvas() / drawBorders() / draw*Border() — pure canvas rendering, no testable state.
 *   drawBackground() — creates Image with onload callback; browser API only.
 *   getCanvasSettings() — canvas context method calls; rendering-only.
 *   ngAfterViewInit() — delegates to populateCanvas; covered implicitly by canvas mock in setup.
 *   ngOnChanges() — color initialisation branches; the boundary is covered via updateBorder and
 *     isSameBorderColor tests.
 *   selectBorderStyle() — one-liner delegating to selectedBorder setter + repaintBorder; Risk 1.
 *   setNullBorderStyle / setDefaultBorderStyle / setDefaultBorderColor / setNullSelectedBorder /
 *     setAllSelectedBorder — called only by setDefault() and selectAll(); covered transitively.
 *   isSameBorderStyle() — identical comparison pattern to isSameBorderColor; Risk 1.
 *   onApply output — declared in source but never emitted internally; cannot be triggered.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { BindingBorderPane } from "./binding-border-pane.component";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { BaseHrefService } from "../../common/services/base-href.service";
import { StyleConstants } from "../../common/util/style-constants";

const BASE_HREF_MOCK: Partial<BaseHrefService> = {
   getBaseHref: vi.fn().mockReturnValue(""),
};

function makeFormatModel(overrides: Partial<FormatInfoModel> = {}): FormatInfoModel {
   return {
      type: "",
      color: "",
      backgroundColor: "",
      font: null,
      align: null,
      format: "",
      borderColor: "",
      borderTopStyle: "0",
      borderTopColor: "#000000",
      borderTopWidth: "0",
      borderLeftStyle: "0",
      borderLeftColor: "#000000",
      borderLeftWidth: "0",
      borderBottomStyle: "0",
      borderBottomColor: "#000000",
      borderBottomWidth: "0",
      borderRightStyle: "0",
      borderRightColor: "#000000",
      borderRightWidth: "0",
      ...overrides,
   } as FormatInfoModel;
}

async function renderComponent(formatModel = makeFormatModel()) {
   const { fixture } = await render(BindingBorderPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: BaseHrefService, useValue: BASE_HREF_MOCK }],
      componentProperties: { formatModel },
   });
   return fixture.componentInstance as BindingBorderPane;
}

beforeEach(() => {
   // Canvas rendering is not testable in jsdom; mock the 2d context so populateCanvas
   // and drawBorders don't throw during component initialisation.
   vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      clearRect: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      stroke: vi.fn(),
      drawImage: vi.fn(),
      fillText: vi.fn(),
      setLineDash: vi.fn(),
      lineWidth: 1,
      strokeStyle: "",
      fillStyle: "",
   } as any);
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: setDefault() [Risk 3]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — setDefault", () => {
   // 🔁 Regression-sensitive (Bug #10699, #17594): setDefault must null all border styles and reset
   //    colors to defaultColor; if any style remains non-null the border still renders visibly.
   it("should set all border styles to null", async () => {
      const comp = await renderComponent();
      comp.setDefault();

      expect(comp.formatModel.borderTopStyle).toBeNull();
      expect(comp.formatModel.borderLeftStyle).toBeNull();
      expect(comp.formatModel.borderBottomStyle).toBeNull();
      expect(comp.formatModel.borderRightStyle).toBeNull();
   });

   it("should set all border colors to defaultColor", async () => {
      const comp = await renderComponent();
      comp.setDefault();

      expect(comp.formatModel.borderTopColor).toBe(comp.defaultColor);
      expect(comp.formatModel.borderLeftColor).toBe(comp.defaultColor);
      expect(comp.formatModel.borderBottomColor).toBe(comp.defaultColor);
      expect(comp.formatModel.borderRightColor).toBe(comp.defaultColor);
   });

   it("should deselect all borders after setDefault", async () => {
      const comp = await renderComponent();
      comp.setDefault();
      expect(comp.isNullSelectedBorder()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateBorder(op) [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — updateBorder", () => {
   it("should select only the top border and set currentColor from formatModel.borderTopColor", async () => {
      const model = makeFormatModel({ borderTopColor: "#FF0000" });
      const comp = await renderComponent(model);

      comp.updateBorder("top");

      expect(comp.selectedBorder.borderTop).toBe(true);
      expect(comp.selectedBorder.borderLeft).toBe(false);
      expect(comp.selectedBorder.borderBottom).toBe(false);
      expect(comp.selectedBorder.borderRight).toBe(false);
      expect(comp.currentColor.colorString).toBe("#FF0000");
   });

   it("should select only the left border and set currentColor from formatModel.borderLeftColor", async () => {
      const model = makeFormatModel({ borderLeftColor: "#00FF00" });
      const comp = await renderComponent(model);

      comp.updateBorder("left");

      expect(comp.selectedBorder.borderLeft).toBe(true);
      expect(comp.selectedBorder.borderTop).toBe(false);
      expect(comp.currentColor.colorString).toBe("#00FF00");
   });

   it("should select only the bottom border", async () => {
      const model = makeFormatModel({ borderBottomColor: "#0000FF" });
      const comp = await renderComponent(model);

      comp.updateBorder("bottom");

      expect(comp.selectedBorder.borderBottom).toBe(true);
      expect(comp.selectedBorder.borderTop).toBe(false);
      expect(comp.currentColor.colorString).toBe("#0000FF");
   });

   it("should select only the right border", async () => {
      const model = makeFormatModel({ borderRightColor: "#FFFF00" });
      const comp = await renderComponent(model);

      comp.updateBorder("right");

      expect(comp.selectedBorder.borderRight).toBe(true);
      expect(comp.selectedBorder.borderLeft).toBe(false);
      expect(comp.currentColor.colorString).toBe("#FFFF00");
   });

   it("should assign defaultColor when the selected border has no existing color", async () => {
      const model = makeFormatModel({ borderTopColor: null });
      const comp = await renderComponent(model);

      comp.updateBorder("top");

      expect(comp.formatModel.borderTopColor).toBe(comp.defaultColor);
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectAll() toggle [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — selectAll toggle", () => {
   it("should select all borders when none are currently selected", async () => {
      const comp = await renderComponent();
      // initial state: none selected
      expect(comp.isNullSelectedBorder()).toBe(true);

      comp.selectAll();

      expect(comp.isToggleAll()).toBe(true);
   });

   it("should deselect all borders when all are currently selected", async () => {
      const comp = await renderComponent();
      comp.setAllSelectedBorder(); // select all first
      expect(comp.isToggleAll()).toBe(true);

      comp.selectAll();

      expect(comp.isNullSelectedBorder()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: isSameBorderColor() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — isSameBorderColor", () => {
   it("should return true when formatModel is null", async () => {
      const comp = await renderComponent();
      comp.formatModel = null;
      expect(comp.isSameBorderColor()).toBe(true);
   });

   it("should return true when all border colors are identical", async () => {
      const model = makeFormatModel({
         borderTopColor: "#AAA", borderLeftColor: "#AAA",
         borderBottomColor: "#AAA", borderRightColor: "#AAA",
      });
      const comp = await renderComponent(model);
      expect(comp.isSameBorderColor()).toBe(true);
   });

   it("should return false when border colors differ", async () => {
      const model = makeFormatModel({
         borderTopColor: "#AAA", borderLeftColor: "#BBB",
         borderBottomColor: "#AAA", borderRightColor: "#AAA",
      });
      const comp = await renderComponent(model);
      expect(comp.isSameBorderColor()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: repaintBorder() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — repaintBorder", () => {
   it("should update borderTopColor when top border is selected", async () => {
      const comp = await renderComponent();
      comp.updateBorder("top"); // selects top
      comp.repaintBorder("#123456");
      expect(comp.formatModel.borderTopColor).toBe("#123456");
   });

   it("should update borderLeftColor when left border is selected", async () => {
      const comp = await renderComponent();
      comp.updateBorder("left");
      comp.repaintBorder("#ABCDEF");
      expect(comp.formatModel.borderLeftColor).toBe("#ABCDEF");
   });

   it("should update borderBottomColor and borderRightColor when both are selected", async () => {
      const comp = await renderComponent();
      comp.selectedBorder = {
         borderTop: false, borderLeft: false,
         borderBottom: true, borderRight: true,
      };
      comp.repaintBorder("#999999");
      expect(comp.formatModel.borderBottomColor).toBe("#999999");
      expect(comp.formatModel.borderRightColor).toBe("#999999");
   });

   it("should update currentColor.colorString to the new color", async () => {
      const comp = await renderComponent();
      comp.repaintBorder("#FFFFFF");
      expect(comp.currentColor.colorString).toBe("#FFFFFF");
   });
});

// ---------------------------------------------------------------------------
// Group 6: isDefaultBorder, isToggleAll, isNullSelectedBorder [Risk 1]
// ---------------------------------------------------------------------------

describe("BindingBorderPane — boolean state getters", () => {
   it("should return true from isDefaultBorder when all styles are THIN_LINE", async () => {
      const thin = String(StyleConstants.THIN_LINE);
      const model = makeFormatModel({
         borderTopStyle: thin, borderLeftStyle: thin,
         borderBottomStyle: thin, borderRightStyle: thin,
      });
      const comp = await renderComponent(model);
      expect(comp.isDefaultBorder()).toBe(true);
   });

   it("should return false from isDefaultBorder when a style differs from THIN_LINE", async () => {
      const thin = String(StyleConstants.THIN_LINE);
      const model = makeFormatModel({
         borderTopStyle: thin, borderLeftStyle: "0",
         borderBottomStyle: thin, borderRightStyle: thin,
      });
      const comp = await renderComponent(model);
      expect(comp.isDefaultBorder()).toBe(false);
   });

   it("should return true from isToggleAll when all four borders are selected", async () => {
      const comp = await renderComponent();
      comp.setAllSelectedBorder();
      expect(comp.isToggleAll()).toBe(true);
   });

   it("should return false from isToggleAll when not all borders are selected", async () => {
      const comp = await renderComponent();
      comp.updateBorder("top"); // only top selected
      expect(comp.isToggleAll()).toBe(false);
   });

   it("should return true from isNullSelectedBorder when no border is selected", async () => {
      const comp = await renderComponent();
      expect(comp.isNullSelectedBorder()).toBe(true);
   });

   it("should return false from isNullSelectedBorder when at least one border is selected", async () => {
      const comp = await renderComponent();
      comp.updateBorder("right");
      expect(comp.isNullSelectedBorder()).toBe(false);
   });
});
