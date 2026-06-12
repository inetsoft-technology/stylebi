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
 * VSFormatsPane — Pass 3: Display / Labels / Conditional Visibility
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3]  — isFormattingDisabled(): formatEnabled=false; VSTab/VSLine; viewer=true no asm;
 *                         chart editable; viewer=false no asm
 *   Group 2  [Risk 2]  — isFormatDisabled(): VSViewsheet/Thermometer; viewer=true; plain VSText
 *   Group 3  [Risk 2]  — getBorderLabel(): all-null styles → Default; any non-null → Custom
 *   Group 4  [Risk 1]  — updateFormat(): debounce+emit contract
 *   Group 5  [Risk 1]  — getColorLabel(): dynamic passthrough vs static label
 *   Group 6  [baseline] — closeFormat(): onCloseFormat emits ""
 *   Group 7  [baseline] — getCSSLabel(): None / cssID-only / cssClass-only / both
 *   Group 8  [baseline] — showPresenter(): table+row / table+title / VSText / no asm
 *   Group 9  [baseline] — getFormat(): delegates to FormatTool.getFormatString
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only): none
 *
 * In scope this pass:
 *   getFormat(), getColorLabel(), closeFormat(), isFormatDisabled(), updateFormat(),
 *   getBorderLabel(), isFormattingDisabled(), getCSSLabel(), showPresenter()
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";

import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { ModelService } from "../../widget/services/model.service";
import { FontService } from "../../widget/services/font.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { FormatTool } from "../../common/util/format-tool";
import { VSFormatsPane } from "./vs-formats-pane.component";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { TestUtils } from "../../common/test/test-utils";

// ---------------------------------------------------------------------------
// Shared fixtures (mirrors Pass 1 setup)
// ---------------------------------------------------------------------------

const FONT_SERVICE_MOCK = {
   getAllFonts: vi.fn().mockReturnValue(of(["Arial", "Roboto"])),
};

const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of(null)),
};

const DEBOUNCE_MOCK = {
   debounce: vi.fn().mockImplementation((_key: string, fn: () => void) => fn()),
   cancel: vi.fn(),
};

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => {
      let resolveResult: (val: any) => void;
      const result = new Promise<any>((res) => { resolveResult = res; });
      const onCommit = new Subject<string>();
      return {
         result,
         componentInstance: { onCommit },
         close: vi.fn().mockImplementation((val: any) => resolveResult(val)),
         dismiss: vi.fn(),
      };
   }),
};

beforeEach(() => {
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   MODAL_MOCK.open.mockClear();
   DEBOUNCE_MOCK.debounce.mockClear();
   FONT_SERVICE_MOCK.getAllFonts.mockClear();
   MODEL_SERVICE_MOCK.getModel.mockClear();
   MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(null));
});

interface RenderOptions {
   viewer?: boolean;
   vsId?: string;
   format?: FormatInfoModel | VSObjectFormatInfoModel;
}

async function renderComponent(opts: RenderOptions = {}) {
   const { fixture } = await render(VSFormatsPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: FontService, useValue: FONT_SERVICE_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      componentInputs: {
         viewer: opts.viewer ?? false,
         vsId: opts.vsId ?? "test-vs",
         format: opts.format ?? TestUtils.createMockVSObjectFormatInfoModel(),
      },
   });
   return { comp: fixture.componentInstance as VSFormatsPane, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — isFormattingDisabled() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isFormattingDisabled()", () => {
   // 🔁 Regression-sensitive: formatEnabled=false must short-circuit even when focused assemblies
   // could otherwise allow formatting.
   it("should return true when format.formatEnabled is false", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), formatEnabled: false };
      const { comp } = await renderComponent({ format });

      expect(comp.isFormattingDisabled()).toBe(true);
   });

   it("should return true when a VSTab assembly is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSTab", "tab1")];

      expect(comp.isFormattingDisabled()).toBe(true);
   });

   it("should return true when a VSLine assembly is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSLine", "line1")];

      expect(comp.isFormattingDisabled()).toBe(true);
   });

   it("should return false when viewer=true and no assemblies are focused", async () => {
      const { comp } = await renderComponent({ viewer: true });
      // no focusedAssemblies → falls through to !this.viewer = false

      expect(comp.isFormattingDisabled()).toBe(false);
   });

   // Chart with no VO selection → isChartEditableSelected() returns true → formatting enabled.
   it("should return false when a chart assembly is editable (no VO selected)", async () => {
      const { comp } = await renderComponent();
      // createMockVSChartModel has chartSelection=null → isNonEditableChartVOSelected=false
      comp.focusedAssemblies = [TestUtils.createMockVSChartModel("chart1")];

      expect(comp.isFormattingDisabled()).toBe(false);
   });

   it("should return true when viewer=false and no assemblies are focused", async () => {
      const { comp } = await renderComponent({ viewer: false });

      expect(comp.isFormattingDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — isFormatDisabled() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isFormatDisabled()", () => {
   it("should return true when a VSViewsheet assembly is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSViewsheet", "vs1")];

      expect(comp.isFormatDisabled()).toBe(true);
   });

   it("should return true when a VSThermometer assembly is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSThermometer", "therm1")];

      expect(comp.isFormatDisabled()).toBe(true);
   });

   it("should return true when viewer=true and no assemblies are focused", async () => {
      const { comp } = await renderComponent({ viewer: true });

      expect(comp.isFormatDisabled()).toBe(true);
   });

   it("should return false when a VSText assembly is focused (viewer=false)", async () => {
      const { comp } = await renderComponent({ viewer: false });
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSText", "text1")];

      expect(comp.isFormatDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getBorderLabel() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — getBorderLabel()", () => {
   it("should return Default when all four border styles are null", async () => {
      const format = {
         ...TestUtils.createMockVSObjectFormatInfoModel(),
         borderTopStyle: null,
         borderLeftStyle: null,
         borderBottomStyle: null,
         borderRightStyle: null,
      };
      const { comp } = await renderComponent({ format });

      expect(comp.getBorderLabel()).toBe("_#(js:Default)");
   });

   it("should return Custom when any border style is non-null", async () => {
      const format = {
         ...TestUtils.createMockVSObjectFormatInfoModel(),
         borderTopStyle: "DASHED",
         borderLeftStyle: null,
         borderBottomStyle: null,
         borderRightStyle: null,
      };
      const { comp } = await renderComponent({ format });

      expect(comp.getBorderLabel()).toBe("_#(js:Custom)");
   });

   // Empty string is not null: the default mock has borderTopStyle="" which counts as Custom.
   it("should return Custom when border styles are empty strings (not null)", async () => {
      // default mock has borderTopStyle: "" which is != null
      const { comp } = await renderComponent();

      expect(comp.getBorderLabel()).toBe("_#(js:Custom)");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — updateFormat() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — updateFormat()", () => {
   it("should call debounce with the vsId-scoped key and 50 ms delay", async () => {
      const { comp } = await renderComponent({ vsId: "vs-abc" });

      comp.updateFormat();

      expect(DEBOUNCE_MOCK.debounce).toHaveBeenCalledWith(
         "UpdateVSFormat.vs-abc",
         expect.any(Function),
         50,
      );
   });

   it("should emit the current format object via onChangeFormat when debounce fires", async () => {
      const format = TestUtils.createMockVSObjectFormatInfoModel();
      const { comp } = await renderComponent({ vsId: "vs-abc", format });
      const emitted: FormatInfoModel[] = [];
      comp.onChangeFormat.subscribe((v: FormatInfoModel) => emitted.push(v));

      comp.updateFormat();

      expect(emitted[0]).toBe(format);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — getColorLabel() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — getColorLabel()", () => {
   it("should return the value as-is when it is a dynamic variable ($)", async () => {
      const { comp } = await renderComponent();

      expect(comp.getColorLabel("$myVariable")).toBe("$myVariable");
   });

   it("should return the value as-is when it is a dynamic expression (=)", async () => {
      const { comp } = await renderComponent();

      expect(comp.getColorLabel("=SomeExpr()")).toBe("=SomeExpr()");
   });

   it("should return the Static label when the value is a plain color", async () => {
      const { comp } = await renderComponent();

      expect(comp.getColorLabel("Static")).toBe("_#(js:Static)");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — closeFormat() [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — closeFormat()", () => {
   it("should emit an empty string on onCloseFormat", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onCloseFormat.subscribe((v: string) => emitted.push(v));

      comp.closeFormat({} as MouseEvent);

      expect(emitted).toEqual([""]);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — getCSSLabel() [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — getCSSLabel()", () => {
   it("should return None when cssID and cssClass are both absent", async () => {
      // default mock: cssID=null, cssClass=""
      // Note: cssClass="" is treated as falsy here, unlike borderTopStyle=""
      // which getBorderLabel() treats as non-null and returns "Custom" (see Group 3).
      const { comp } = await renderComponent();

      expect(comp.getCSSLabel()).toBe("_#(js:None)");
   });

   it("should return the cssID-prefixed string when only cssID is set", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), cssID: "myId", cssClass: "" };
      const { comp } = await renderComponent({ format });

      expect(comp.getCSSLabel()).toBe("#myId ");
   });

   it("should return the cssClass-prefixed string when only cssClass is set", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), cssID: null, cssClass: "myClass" };
      const { comp } = await renderComponent({ format });

      expect(comp.getCSSLabel()).toBe(".myClass");
   });

   it("should combine cssID and cssClass when both are set", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), cssID: "myId", cssClass: "myClass" };
      const { comp } = await renderComponent({ format });

      expect(comp.getCSSLabel()).toBe("#myId .myClass");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — showPresenter() [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — showPresenter()", () => {
   it("should return true for a VSTable with a selected row (no title selected)", async () => {
      const { comp } = await renderComponent();
      const table = TestUtils.createMockVSTableModel("table1");
      // default: firstSelectedRow=0 >= 0, titleSelected=undefined (falsy)
      comp.focusedAssemblies = [table];

      expect(comp.showPresenter()).toBe(true);
   });

   it("should return false for a VSTable when the title row is selected", async () => {
      const { comp } = await renderComponent();
      const table = { ...TestUtils.createMockVSTableModel("table1"), titleSelected: true };
      comp.focusedAssemblies = [table];

      expect(comp.showPresenter()).toBe(false);
   });

   it("should return true for a VSText assembly", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSText", "text1")];

      expect(comp.showPresenter()).toBe(true);
   });

   it("should return false when no assemblies are focused", async () => {
      const { comp } = await renderComponent();
      // _focusedAssemblies is null by default

      expect(comp.showPresenter()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — getFormat() [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — getFormat()", () => {
   it("should delegate to FormatTool.getFormatString and return its result", async () => {
      const format = TestUtils.createMockVSObjectFormatInfoModel();
      const { comp } = await renderComponent({ format });
      const spy = vi.spyOn(FormatTool, "getFormatString").mockReturnValue("DATE");
      try {
         expect(comp.getFormat()).toBe("DATE");
      } finally {
         spy.mockRestore();
      }
   });
});
