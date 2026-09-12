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
 * FontPane — single pass (+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — toggle methods: toggleWeight/toggleStyle/toggleUnderline/toggleStrikethrough
 *     correctly flip values and call checkFontFamily (sets "Default" when family is empty);
 *     fireChangeEvent copies _font back to fontModel and increments pending
 *   Group 2 [Risk 3] — font_size getter/setter: null _font → "11"; null fontSize → "11";
 *     valid size → max(1, val); setter enforces min-1 and stores string; non-numeric input is ignored
 *   Group 3 [Risk 2] — font_family getter: null _font → "Default"; null fontFamily → "Default";
 *     valid family → returns as-is
 *   Group 4 [Risk 2] — ngOnInit: creates _font from fontModel; with no fonts calls getFonts();
 *     with fonts[] populated skips getFonts()
 *   Group 5 [Risk 2] — changeFontSize: debounces checkFontFamily + fireChangeEvent
 *   Group 6 [Risk 1] — defaultFont: delegates to fontService.defaultFont
 *
 * Fixed bugs:
 *   Bug #75598 — getFonts() subscribe leak: fontService.getAllFonts().subscribe() stored no
 *     Subscription. If the component was destroyed while the HTTP observable was in-flight, the
 *     callback ran and set this.fonts on a destroyed component. Fixed by storing and
 *     unsubscribing in ngOnDestroy.
 *
 * Out of scope:
 *   changeFontFamily — accesses scrollBar ViewChild (template-only); not testable at unit level.
 *   ngOnChanges debounce path — requires pending>0 state + DebounceService timing; integration-level.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { FontPane } from "./font-pane.component";
import { FontInfo } from "../../common/data/format-info-model";
import { FontService } from "../services/font.service";
import { DebounceService } from "../services/debounce.service";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const FONT_SERVICE_MOCK = {
   getAllFonts: vi.fn(() => of(["Arial", "Roboto"])),
   defaultFont: "Roboto",
};

// Execute debounce callbacks immediately in tests.
const DEBOUNCE_SERVICE_MOCK = {
   debounce: vi.fn((key: string, fn: () => void) => fn()),
   cancel: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared fixture
// ---------------------------------------------------------------------------

function makeFont(overrides: Partial<FontInfo> = {}): FontInfo {
   return {
      fontFamily: "",
      fontSize: "11",
      fontStyle: "normal",
      fontUnderline: "normal",
      fontStrikethrough: "normal",
      fontWeight: "normal",
      ...overrides,
   };
}

async function renderComponent(fontModelOverrides: Partial<FontInfo> | null = {}) {
   const fontModel = fontModelOverrides === null ? null : makeFont(fontModelOverrides);
   const { fixture } = await render(FontPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: FontService, useValue: FONT_SERVICE_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
      ],
      componentInputs: {
         fontModel,
         fonts: ["Arial", "Roboto"],
         isOpen: false,
      },
   });
   const comp = fixture.componentInstance as FontPane;
   return { comp, fixture, fontModel };
}

beforeEach(() => {
   FONT_SERVICE_MOCK.getAllFonts.mockReturnValue(of(["Arial", "Roboto"]));
   DEBOUNCE_SERVICE_MOCK.debounce.mockImplementation((_key: string, fn: () => void) => fn());
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: toggle methods [Risk 3]
// ---------------------------------------------------------------------------

describe("FontPane — toggle methods", () => {
   // 🔁 Regression-sensitive (Bug #19781): toggling any style attribute when fontFamily is empty
   //    must set it to "Default" so the backend receives a valid family name.
   it("toggleWeight should flip fontWeight to 'bold' and set fontFamily to 'Default'", async () => {
      const { comp, fontModel } = await renderComponent();
      comp.toggleWeight();
      expect(fontModel.fontWeight).toBe("bold");
      expect(fontModel.fontFamily).toBe("Default");
   });

   it("toggleWeight should flip fontWeight back to 'normal' on second call", async () => {
      const { comp, fontModel } = await renderComponent({ fontWeight: "bold" });
      comp.toggleWeight();
      expect(fontModel.fontWeight).toBe("normal");
   });

   it("toggleStyle should flip fontStyle to 'italic' and set fontFamily to 'Default'", async () => {
      const { comp, fontModel } = await renderComponent();
      comp.toggleStyle();
      expect(fontModel.fontStyle).toBe("italic");
      expect(fontModel.fontFamily).toBe("Default");
   });

   it("toggleStyle should flip fontStyle back to 'normal' on second call", async () => {
      const { comp, fontModel } = await renderComponent({ fontStyle: "italic" });
      comp.toggleStyle();
      expect(fontModel.fontStyle).toBe("normal");
   });

   it("toggleUnderline should flip fontUnderline to 'underline'", async () => {
      const { comp, fontModel } = await renderComponent();
      comp.toggleUnderline();
      expect(fontModel.fontUnderline).toBe("underline");
      expect(fontModel.fontFamily).toBe("Default");
   });

   it("toggleUnderline should flip fontUnderline back to 'normal'", async () => {
      const { comp, fontModel } = await renderComponent({ fontUnderline: "underline" });
      comp.toggleUnderline();
      expect(fontModel.fontUnderline).toBe("normal");
   });

   it("toggleStrikethrough should flip fontStrikethrough to 'strikethrough'", async () => {
      const { comp, fontModel } = await renderComponent();
      comp.toggleStrikethrough();
      expect(fontModel.fontStrikethrough).toBe("strikethrough");
      expect(fontModel.fontFamily).toBe("Default");
   });

   it("toggleStrikethrough should flip fontStrikethrough back to 'normal'", async () => {
      const { comp, fontModel } = await renderComponent({ fontStrikethrough: "strikethrough" });
      comp.toggleStrikethrough();
      expect(fontModel.fontStrikethrough).toBe("normal");
   });

   it("toggling should emit onFontChange with true", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onFontChange, "emit");
      comp.toggleWeight();
      expect(emitSpy).toHaveBeenCalledWith(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: font_size getter/setter [Risk 3]
// ---------------------------------------------------------------------------

describe("FontPane — font_size getter/setter", () => {
   it("getter should return '11' when _font is null", async () => {
      const { comp } = await renderComponent(null);
      comp["_font"] = null;
      expect(comp.font_size).toBe("11");
   });

   it("getter should return '11' when fontSize is null and sets it to '11'", async () => {
      const { comp } = await renderComponent({ fontSize: null });
      expect(comp.font_size).toBe("11");
      expect(comp["_font"].fontSize).toBe("11");
   });

   it("getter should clamp to minimum 1 for zero input", async () => {
      const { comp } = await renderComponent({ fontSize: "0" });
      expect(comp.font_size).toBe("1");
   });

   it("getter should return the font size string as-is for valid size", async () => {
      const { comp } = await renderComponent({ fontSize: "14" });
      expect(comp.font_size).toBe("14");
   });

   it("setter should store valid numeric string as max(1, val)", async () => {
      const { comp } = await renderComponent();
      comp.font_size = "24";
      expect(comp["_font"].fontSize).toBe("24");
   });

   it("setter should enforce minimum 1", async () => {
      const { comp } = await renderComponent();
      comp.font_size = "0";
      expect(comp["_font"].fontSize).toBe("1");
   });

   it("setter should ignore non-numeric strings", async () => {
      const { comp } = await renderComponent({ fontSize: "12" });
      comp.font_size = "abc";
      expect(comp["_font"].fontSize).toBe("12");
   });

   it("setter should ignore empty string", async () => {
      const { comp } = await renderComponent({ fontSize: "12" });
      comp.font_size = "";
      expect(comp["_font"].fontSize).toBe("12");
   });
});

// ---------------------------------------------------------------------------
// Group 3: font_family getter [Risk 2]
// ---------------------------------------------------------------------------

describe("FontPane — font_family getter", () => {
   it("should return 'Default' when _font is null", async () => {
      const { comp } = await renderComponent(null);
      comp["_font"] = null;
      expect(comp.font_family).toBe("Default");
   });

   it("should return 'Default' when fontFamily is null", async () => {
      const { comp } = await renderComponent({ fontFamily: null });
      expect(comp.font_family).toBe("Default");
   });

   it("should return the font family name when set", async () => {
      const { comp } = await renderComponent({ fontFamily: "Arial" });
      expect(comp.font_family).toBe("Arial");
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("FontPane — ngOnInit", () => {
   it("should set _font from fontModel on init", async () => {
      const { comp, fontModel } = await renderComponent({ fontFamily: "Roboto" });
      expect(comp["_font"]).toBe(fontModel);
   });

   it("should create new FontInfo when fontModel is null", async () => {
      const { comp } = await renderComponent(null);
      expect(comp["_font"]).toBeInstanceOf(FontInfo);
   });

   it("should NOT call getFonts when fonts are pre-populated", async () => {
      const getFontsSpy = vi.spyOn(FONT_SERVICE_MOCK, "getAllFonts");
      await renderComponent();
      expect(getFontsSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: changeFontSize [Risk 2]
// ---------------------------------------------------------------------------

describe("FontPane — changeFontSize", () => {
   it("should call debounce and then emit onFontChange after debounce fires", async () => {
      const { comp, fontModel } = await renderComponent({ fontFamily: "Arial", fontSize: "14" });
      const emitSpy = vi.spyOn(comp.onFontChange, "emit");
      comp.font_size = "16";
      comp.changeFontSize();
      expect(emitSpy).toHaveBeenCalledWith(true);
      expect(fontModel.fontSize).toBe("16");
   });
});

// ---------------------------------------------------------------------------
// Group 6: defaultFont [Risk 1]
// ---------------------------------------------------------------------------

describe("FontPane — defaultFont", () => {
   it("should return the defaultFont from FontService", async () => {
      const { comp } = await renderComponent();
      expect(comp.defaultFont).toBe("Roboto");
   });
});

// ---------------------------------------------------------------------------
// Memory leak (regression test for Bug #75598)
// ---------------------------------------------------------------------------

describe("FontPane — subscribe leak", () => {
   // Regression test for Bug #75598: getFonts() called fontService.getAllFonts().subscribe()
   // without storing the Subscription. After component destruction the callback still ran and set
   // this.fonts on a destroyed component. Fixed by storing the subscription and unsubscribing in
   // ngOnDestroy.
   it("should not update fonts after component is destroyed (subscribe leak)", async () => {
      const subject = new Subject<string[]>();
      FONT_SERVICE_MOCK.getAllFonts.mockReturnValue(subject.asObservable());

      const { fixture } = await render(FontPane, {
         schemas: [NO_ERRORS_SCHEMA],
         componentImports: [],
         providers: [
            { provide: FontService, useValue: FONT_SERVICE_MOCK },
            { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
         ],
         componentInputs: {
            fontModel: makeFont(),
            fonts: [],
            isOpen: false,
         },
      });
      const comp = fixture.componentInstance as FontPane;
      comp["fonts"] = [];

      fixture.destroy();
      subject.next(["Roboto"]);

      // fonts should remain [] because the subscription was unsubscribed before emit
      expect(comp["fonts"]).toEqual([]);
   });
});
