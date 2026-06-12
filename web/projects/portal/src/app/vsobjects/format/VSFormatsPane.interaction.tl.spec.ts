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
 * VSFormatsPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3]  — reset(): confirmed "yes" → onChangeFormat(null); "no" → no emit
 *   Group 2  [Risk 3]  — openPresenterPropertyDialog(): HTTP GET → modal resolve → onPresenterPropertiesChange emitted
 *   Group 3  [Risk 3]  — format setter: VSObjectFormatInfoModel colorType → getComboMode sets _colorType correctly;
 *                         plain FormatInfoModel → _color = null, _colorType = VALUE
 *   Group 4  [Risk 2]  — color / colorType / backgroundColor / backgroundColorType setters: side-effect contracts
 *   Group 4a [Risk 2]  — changeColor(): template-bound mutator; new value → emit; same value → no emit
 *   Group 5  [Risk 2]  — focusedAssemblies setter: null entries filtered, updateProperties triggered
 *   Group 6  [Risk 2]  — updatePresenter() / updatePresenterProperties(): emit contracts
 *   Group 7  [Risk 1]  — updateCSS(): cssID / cssClass updated; empty string → null
 *   Group 8  [Risk 1]  — changeAlphaWarning(): alphaInvalid toggled
 *   Group 9  [baseline] — ngOnInit: FontService.getAllFonts subscribed → fonts stored on component
 *   Group 10 [baseline] — updateProperties() orchestration: viewer=true flags
 *   Group 11 [baseline] — isFontDisabled / isColorDisabled: VSImage → both true; viewer=true, no assembly → false
 *   Group 12 [baseline] — isAlignDisabled: halign+valign both disabled → true; viewer=true + halign enabled → false
 *   Group 12a [baseline] — isVAlignmentEnabled: all 3 branches (format/assembly/viewer) × true+false
 *   Group 13 [baseline] — isBorderDisabled / isBackgroundDisabled: VSLine → border/background disabled
 *   Group 14 [baseline] — isDynamicColorDisabled: no assemblies viewer=false → true; viewer=true+whole chart → true
 *   Group 15 [baseline] — isCSSDisabled: viewer=true → true; table with selectedData → true
 *   Group 16 [baseline] — isValueFillVisible: VSGauge face 10910 → true; non-matching face → false
 *   Group 17 [baseline] — roundCornerMax / tableSelected / textSelected / borderTooltip getters
 *   Group 18 [baseline] — isRoundTopCornersOnlyVisible: VSTab without regions → true; with regions → false
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only): none
 *
 * Out of scope this pass:
 *   getFormat(), getColorLabel(), closeFormat(), isFormatDisabled(), updateFormat(), getBorderLabel(),
 *   isFormattingDisabled(), getCSSLabel(), showPresenter() — covered in VSFormatsPane.display.tl.spec.ts
 *   getFont(), getAlignment() — delegate entirely to FormatTool; zero unit-test value at this layer
 *   ngOnChanges() — only calls updateProperties() + CD attach/detach; covered transitively in other groups
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, of } from "rxjs";

import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { ModelService } from "../../widget/services/model.service";
import { FontService } from "../../widget/services/font.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { ComboMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { ColorDropdown } from "../../widget/color-picker/color-dropdown.component";
import { VSFormatsPane } from "./vs-formats-pane.component";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { TestUtils } from "../../common/test/test-utils";
import { PresenterPropertyDialogModel } from "../../widget/presenter/data/presenter-property-dialog-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

// FontService mock — returns fonts synchronously; no HTTP needed.
const FONT_SERVICE_MOCK = {
   getAllFonts: vi.fn().mockReturnValue(of(["Arial", "Roboto"])),
};

// ModelService mock — getModel returns null by default; override per test as needed.
const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of(null)),
};

// DebounceService mock — executes the callback synchronously so output events fire immediately.
const DEBOUNCE_MOCK = {
   debounce: vi.fn().mockImplementation((_key: string, fn: () => void) => fn()),
   cancel: vi.fn(),
};

// NgbModal mock — each call returns a fresh ref with a resolvable result promise.
// Works for both showMessageDialog/showConfirmDialog (via onCommit) and direct modal.result usage.
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

// Reset message-dialog dedup guard and mock call counters before every test.
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
// Group 1 — reset(): confirm-dialog flow [Risk 3]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — reset(): confirm dialog flow", () => {
   // 🔁 Regression-sensitive: reset() must emit null — not the current format — so the server
   // clears the format rather than applying the stale stored state.
   it("should emit null on onChangeFormat when user clicks Yes", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.reset();

      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("yes");

      await waitFor(() => expect(changeSpy).toHaveBeenCalledWith(null));
   });

   it("should NOT emit onChangeFormat when user clicks No", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.reset();

      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.componentInstance.onCommit.next("no");

      expect(changeSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — openPresenterPropertyDialog(): HTTP + modal flow [Risk 3]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — openPresenterPropertyDialog(): HTTP and modal", () => {
   // 🔁 Regression-sensitive: onPresenterPropertiesChange must carry [eventUri, model]; if the
   // [eventUri, result] tuple is ever destructured in the wrong order the server event fires at
   // the wrong URI causing a silent no-op on the presenter.
   it("should emit an event URI matching 'presenter-property-dialog-model' when modal is confirmed (table)", async () => {
      const presenterModel: PresenterPropertyDialogModel = {
         presenter: "inetsoft.report.TablePresenter",
         descriptors: null,
      };
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(presenterModel));
      const { comp } = await renderComponent();
      const table = TestUtils.createMockVSTableModel("Table1");
      table.firstSelectedRow = 1;
      table.firstSelectedColumn = 0;
      comp._focusedAssemblies = [table];
      comp.vsObjectFormat.presenter = "inetsoft.report.TablePresenter";
      const propertiesSpy = vi.fn();
      comp.onPresenterPropertiesChange.subscribe(propertiesSpy);
      comp.openPresenterPropertyDialog();
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.close(presenterModel);
      await waitFor(() => expect(propertiesSpy).toHaveBeenCalled());
      expect(propertiesSpy.mock.calls[0][0][0]).toMatch(/presenter-property-dialog-model/);
   });

   it("should emit the confirmed presenter model as the payload when modal is confirmed (table)", async () => {
      const presenterModel: PresenterPropertyDialogModel = {
         presenter: "inetsoft.report.TablePresenter",
         descriptors: null,
      };
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(presenterModel));
      const { comp } = await renderComponent();
      const table = TestUtils.createMockVSTableModel("Table1");
      table.firstSelectedRow = 1;
      table.firstSelectedColumn = 0;
      comp._focusedAssemblies = [table];
      comp.vsObjectFormat.presenter = "inetsoft.report.TablePresenter";
      const propertiesSpy = vi.fn();
      comp.onPresenterPropertiesChange.subscribe(propertiesSpy);
      comp.openPresenterPropertyDialog();
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.close(presenterModel);
      await waitFor(() => expect(propertiesSpy).toHaveBeenCalled());
      expect(propertiesSpy.mock.calls[0][0][1]).toEqual(presenterModel);
   });

   it("should emit onPresenterPropertiesChange when VSText is the focused assembly", async () => {
      const presenterModel: PresenterPropertyDialogModel = {
         presenter: "inetsoft.report.TextPresenter",
         descriptors: null,
      };

      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(presenterModel));

      const { comp } = await renderComponent();

      const text = TestUtils.createMockVSTextModel("Text1");
      comp._focusedAssemblies = [text];
      comp.vsObjectFormat.presenter = "inetsoft.report.TextPresenter";

      const propertiesSpy = vi.fn();
      comp.onPresenterPropertiesChange.subscribe(propertiesSpy);

      comp.openPresenterPropertyDialog();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());

      const modalRef = MODAL_MOCK.open.mock.results[0].value;
      modalRef.close(presenterModel);

      await waitFor(() => expect(propertiesSpy).toHaveBeenCalled());
      const emittedArgs: [string, PresenterPropertyDialogModel] = propertiesSpy.mock.calls[0][0];
      expect(emittedArgs[1]).toEqual(presenterModel);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — format setter: VSObjectFormatInfoModel type detection [Risk 3]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — format setter: VSObjectFormatInfoModel type detection", () => {
   // 🔁 Regression-sensitive: _colorType drives whether the dynamic-combo-box shows VALUE /
   // VARIABLE / EXPRESSION mode; wrong getComboMode result silently freezes the color picker UI.
   it("should set _colorType to EXPRESSION when colorType starts with '='", async () => {
      const { comp } = await renderComponent();

      const vsFormat: VSObjectFormatInfoModel = {
         ...TestUtils.createMockVSObjectFormatInfoModel(),
         colorType: "=someExpression",
         backgroundColorType: "Static",
      };

      comp.format = vsFormat;

      expect(comp.colorType).toBe(ComboMode.EXPRESSION);
   });

   it("should set _colorType to VARIABLE when colorType starts with '$'", async () => {
      const { comp } = await renderComponent();

      const vsFormat: VSObjectFormatInfoModel = {
         ...TestUtils.createMockVSObjectFormatInfoModel(),
         colorType: "$colorVariable",
         backgroundColorType: "Static",
      };

      comp.format = vsFormat;

      expect(comp.colorType).toBe(ComboMode.VARIABLE);
   });

   it("should set _color to null and _colorType to VALUE for a plain FormatInfoModel", async () => {
      const { comp } = await renderComponent();

      const plain: FormatInfoModel = TestUtils.createMockFromatInfo(); // type = "" → no "VSObjectFormatInfoModel"

      comp.format = plain;

      expect(comp.colorType).toBe(ComboMode.VALUE);
      // Access private _color via bracket notation to verify null assignment
      expect((comp as any)._color).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — color / colorType / backgroundColor / backgroundColorType setters [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — color / colorType setter side effects", () => {
   it("should call onChangeFormat once when color is set to a different value", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.color = "#ff0000";

      expect(changeSpy).toHaveBeenCalledTimes(1);
   });

   it("should emit the updated colorType in onChangeFormat when color is set", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.color = "#ff0000";

      expect((changeSpy.mock.calls[0][0] as VSObjectFormatInfoModel).colorType).toBe("#ff0000");
   });

   it("should NOT trigger onChangeFormat when color is set to the same value", async () => {
      const { comp } = await renderComponent();
      comp.color = "#ff0000"; // pre-set via public setter (spy not yet subscribed)

      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.color = "#ff0000"; // same value — guard should short-circuit

      expect(changeSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: switching the combo to VALUE must reset the color to STATIC so the
   // picker shows a solid colour swatch, not a raw expression string.
   it("should reset _color backing field to STATIC when colorType is switched to VALUE", async () => {
      const { comp } = await renderComponent({
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), colorType: "=myExpr" },
      });

      comp.colorType = ComboMode.VALUE;

      expect((comp as any)._color).toBe(ColorDropdown.STATIC);
   });

   it("should set vsObjectFormat.colorType to STATIC when colorType is switched to VALUE", async () => {
      const { comp } = await renderComponent({
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), colorType: "=myExpr" },
      });

      comp.colorType = ComboMode.VALUE;

      expect(comp.vsObjectFormat.colorType).toBe(ColorDropdown.STATIC);
   });
});

describe("VSFormatsPane — backgroundColor / backgroundColorType setter side effects", () => {
   it("should call onChangeFormat once when backgroundColor is set to a different value", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.backgroundColor = "#00ff00";

      expect(changeSpy).toHaveBeenCalledTimes(1);
   });

   it("should emit the updated backgroundColorType in onChangeFormat when backgroundColor is set", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.backgroundColor = "#00ff00";

      expect((changeSpy.mock.calls[0][0] as VSObjectFormatInfoModel).backgroundColorType).toBe("#00ff00");
   });

   it("should NOT trigger onChangeFormat when backgroundColor is set to the same value", async () => {
      const { comp } = await renderComponent();
      comp.backgroundColor = "#00ff00"; // pre-set via public setter (spy not yet subscribed)

      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.backgroundColor = "#00ff00"; // same value — guard should short-circuit

      expect(changeSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: switching the background combo to VALUE must reset backgroundColor
   // to STATIC, mirroring the same contract for the foreground color.
   it("should reset _backgroundColor backing field to STATIC when backgroundColorType is switched to VALUE", async () => {
      const { comp } = await renderComponent({
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), backgroundColorType: "=myBgExpr" },
      });

      comp.backgroundColorType = ComboMode.VALUE;

      expect((comp as any)._backgroundColor).toBe(ColorDropdown.STATIC);
   });

   it("should set vsObjectFormat.backgroundColorType to STATIC when backgroundColorType is switched to VALUE", async () => {
      const { comp } = await renderComponent({
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), backgroundColorType: "=myBgExpr" },
      });

      comp.backgroundColorType = ComboMode.VALUE;

      expect(comp.vsObjectFormat.backgroundColorType).toBe(ColorDropdown.STATIC);
   });
});

// ---------------------------------------------------------------------------
// Group 4a — changeColor(): template-bound color mutator [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — changeColor(): template-bound entry point", () => {
   // changeColor() is the template-facing API (bound to (colorChanged) events); distinct from
   // the programmatic `color` setter. Both paths must be tested independently.
   it("should call onChangeFormat once when changeColor is applied with a new color", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.changeColor("#ff0000", "color");

      expect(changeSpy).toHaveBeenCalledTimes(1);
   });

   it("should emit the updated color value in onChangeFormat when changeColor is applied", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.changeColor("#ff0000", "color");

      expect((changeSpy.mock.calls[0][0] as VSObjectFormatInfoModel).color).toBe("#ff0000");
   });

   it("should NOT emit onChangeFormat when changeColor is called with the same value already on the format", async () => {
      const { comp } = await renderComponent();
      comp.vsObjectFormat.color = "#ff0000"; // pre-set format field — same as incoming value

      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.changeColor("#ff0000", "color");

      expect(changeSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — focusedAssemblies setter: null filtering [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — focusedAssemblies setter: null filtering", () => {
   // 🔁 Regression-sensitive: null entries in _focusedAssemblies cause NPEs inside all the
   // is*Disabled() predicates that call object.objectType without null-checking.
   it("should filter out null entries: resulting array has length 1", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");

      comp.focusedAssemblies = [chart, null, null] as unknown as typeof chart[];

      expect(comp.focusedAssemblies).toHaveLength(1);
   });

   it("should filter out null entries: remaining entry is the non-null assembly", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");

      comp.focusedAssemblies = [chart, null, null] as unknown as typeof chart[];

      expect(comp.focusedAssemblies[0]).toBe(chart);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — updatePresenter() / updatePresenterProperties() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — updatePresenter / updatePresenterProperties", () => {
   it("should set vsObjectFormat.presenterLabel when updatePresenter is called", async () => {
      const { comp } = await renderComponent();
      comp.updatePresenter({ label: "My Presenter", presenter: "com.example.MyPresenter", hasDescriptors: true });
      expect(comp.vsObjectFormat.presenterLabel).toBe("My Presenter");
   });

   it("should set vsObjectFormat.presenter when updatePresenter is called", async () => {
      const { comp } = await renderComponent();
      comp.updatePresenter({ label: "My Presenter", presenter: "com.example.MyPresenter", hasDescriptors: true });
      expect(comp.vsObjectFormat.presenter).toBe("com.example.MyPresenter");
   });

   it("should set vsObjectFormat.presenterHasDescriptors when updatePresenter is called", async () => {
      const { comp } = await renderComponent();
      comp.updatePresenter({ label: "My Presenter", presenter: "com.example.MyPresenter", hasDescriptors: true });
      expect(comp.vsObjectFormat.presenterHasDescriptors).toBe(true);
   });

   it("should emit the updated presenter value via onChangeFormat when updatePresenter is called", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.fn();
      comp.onChangeFormat.subscribe(changeSpy);

      comp.updatePresenter({ label: "My Presenter", presenter: "com.example.MyPresenter", hasDescriptors: true });

      expect((changeSpy.mock.calls[0][0] as VSObjectFormatInfoModel).presenter).toBe("com.example.MyPresenter");
   });

   // 🔁 Regression-sensitive: onPresenterPropertiesChange must carry the exact [eventUri, model]
   // tuple; the server-side @Undoable handler depends on the correct URI.
   it("should emit [uri, model] via onPresenterPropertiesChange when updatePresenterProperties is called", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onPresenterPropertiesChange.subscribe(spy);

      const payload: [string, PresenterPropertyDialogModel] = [
         "/events/composer/vs/presenter-property-dialog-model/Table1/0/0/false/0",
         { presenter: "com.example.MyPresenter", descriptors: null },
      ];

      comp.updatePresenterProperties(payload);

      expect(spy).toHaveBeenCalledWith(payload);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — updateCSS(): cssID / cssClass contracts [Risk 1]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — updateCSS()", () => {
   it("should update cssID when isID=true and the new value differs", async () => {
      const { comp } = await renderComponent();

      comp.updateCSS("myId", true);

      expect(comp.vsObjectFormat.cssID).toBe("myId");
   });

   it("should set cssClass to null when empty string is passed with isID=false", async () => {
      const { comp } = await renderComponent();
      comp.vsObjectFormat.cssClass = "some-class";

      comp.updateCSS("", false);

      expect(comp.vsObjectFormat.cssClass).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 8 — changeAlphaWarning() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — changeAlphaWarning()", () => {
   it("should set alphaInvalid to true when called with true", async () => {
      const { comp } = await renderComponent();

      comp.changeAlphaWarning(true);

      expect(comp.alphaInvalid).toBe(true);
   });

   it("should set alphaInvalid to false when called with false", async () => {
      const { comp } = await renderComponent();
      comp.alphaInvalid = true; // start true

      comp.changeAlphaWarning(false);

      expect(comp.alphaInvalid).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — ngOnInit: FontService wired [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — ngOnInit: fonts loaded", () => {
   it("should call FontService.getAllFonts during init", async () => {
      const { comp } = await renderComponent();
      expect(FONT_SERVICE_MOCK.getAllFonts).toHaveBeenCalled();
   });

   it("should populate comp.fonts with the getAllFonts result", async () => {
      FONT_SERVICE_MOCK.getAllFonts.mockReturnValue(of(["Arial", "Roboto", "Georgia"]));
      const { comp } = await renderComponent();
      expect(comp.fonts).toEqual(["Arial", "Roboto", "Georgia"]);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — updateProperties() orchestration: viewer=true flags [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — updateProperties(): viewer=true orchestration", () => {
   it("should set cssDisabled=true when viewer=true (CSS editing is disallowed in viewer)", async () => {
      const { comp } = await renderComponent({ viewer: true });

      expect(comp.cssDisabled).toBe(true);
   });

   it("should set fontDisabled=false when viewer=true and no assemblies are focused", async () => {
      // viewer=true + no focusedAssemblies → isFontDisabled() returns !viewer = false
      const { comp } = await renderComponent({ viewer: true });

      expect(comp.fontDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — isFontDisabled / isColorDisabled: VSImage [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isFontDisabled / isColorDisabled with VSImage", () => {
   it("should set fontDisabled=true when VSImage is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSImageModel("Image1")];
      expect(comp.fontDisabled).toBe(true);
   });

   it("should set colorDisabled=true when VSImage is focused", async () => {
      const { comp } = await renderComponent();
      comp.focusedAssemblies = [TestUtils.createMockVSImageModel("Image1")];
      expect(comp.colorDisabled).toBe(true);
   });

   it("should set colorDisabled=false when a non-image assembly is focused", async () => {
      const { comp } = await renderComponent();

      comp.focusedAssemblies = [TestUtils.createMockVSTextModel("Text1")];

      expect(comp.colorDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — isAlignDisabled: halign + valign flags [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isAlignDisabled: alignment flag logic", () => {
   it("should set alignDisabled=true when both halignmentEnabled=false and valignmentEnabled=false", async () => {
      const { comp } = await renderComponent({
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), halignmentEnabled: false, valignmentEnabled: false },
      });

      expect(comp.alignDisabled).toBe(true);
   });

   it("should set alignDisabled=false when viewer=true and halignmentEnabled=true", async () => {
      const { comp } = await renderComponent({
         viewer: true,
         format: { ...TestUtils.createMockVSObjectFormatInfoModel(), halignmentEnabled: true, valignmentEnabled: false },
      });

      expect(comp.alignDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12a — isVAlignmentEnabled: direct branch coverage [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isVAlignmentEnabled()", () => {
   it("should return false when format.valignmentEnabled is false (format-level override)", async () => {
      // viewer=true so that if branch 1 were absent the fallback would return true —
      // proving the false result is caused specifically by valignmentEnabled:false, not by viewer.
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), valignmentEnabled: false };
      const { comp } = await renderComponent({ viewer: true, format });

      expect(comp.isVAlignmentEnabled()).toBe(false);
   });

   it("should return false when a VSTextInput assembly is focused", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), valignmentEnabled: true };
      const { comp } = await renderComponent({ format });
      comp._focusedAssemblies = [TestUtils.createMockVSObjectModel("VSTextInput", "input1")];

      expect(comp.isVAlignmentEnabled()).toBe(false);
   });

   it("should return true when focused assembly is not a VSTextInput or VSComboBox", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), valignmentEnabled: true };
      const { comp } = await renderComponent({ format });
      comp._focusedAssemblies = [TestUtils.createMockVSObjectModel("VSText", "text1")];

      expect(comp.isVAlignmentEnabled()).toBe(true);
   });

   it("should return false when no assemblies are focused and viewer=false", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), valignmentEnabled: true };
      const { comp } = await renderComponent({ viewer: false, format });

      expect(comp.isVAlignmentEnabled()).toBe(false);
   });

   it("should return true when no assemblies are focused and viewer=true", async () => {
      const format = { ...TestUtils.createMockVSObjectFormatInfoModel(), valignmentEnabled: true };
      const { comp } = await renderComponent({ viewer: true, format });

      expect(comp.isVAlignmentEnabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — isBorderDisabled / isBackgroundDisabled: VSLine [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isBorderDisabled / isBackgroundDisabled with VSLine", () => {
   it("should set borderDisabled=true when VSLine is focused (Bug #18597)", async () => {
      const { comp } = await renderComponent();

      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSLine", "Line1")];

      expect(comp.borderDisabled).toBe(true);
   });

   it("should set backgroundDisabled=true when VSLine is focused", async () => {
      const { comp } = await renderComponent();

      comp.focusedAssemblies = [TestUtils.createMockVSObjectModel("VSLine", "Line1")];

      expect(comp.backgroundDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — isDynamicColorDisabled [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isDynamicColorDisabled", () => {
   it("should set dynamicColorDisabled=true when viewer=false and no assemblies are focused", async () => {
      // No assemblies + viewer=false → !this.viewer = true
      const { comp } = await renderComponent({ viewer: false });
      comp._focusedAssemblies = null;
      comp.updateProperties();

      expect(comp.dynamicColorDisabled).toBe(true);
   });

   it("should set dynamicColorDisabled=true for VSChart whole-chart selection in viewer mode (Bug #18664)", async () => {
      // viewer=true, chart with no selection regions and no chartObject → whole chart selected
      // → isDynamicColorDisabled returns true via the `else if(this.viewer)` branch
      const { comp } = await renderComponent({ viewer: true });
      const chart = TestUtils.createMockVSChartModel("Chart1");
      // regions: [] (not null) so ChartTool.isNonEditableVOSelected can read .length safely;
      // empty array → whole-chart selection path → isDynamicColorDisabled returns true in viewer.
      chart.chartSelection = { chartObject: { areaName: "plot_area" } as any, regions: [] };

      comp.focusedAssemblies = [chart];

      expect(comp.dynamicColorDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — isCSSDisabled [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isCSSDisabled", () => {
   it("should set cssDisabled=true when viewer=true (Bug #18855)", async () => {
      const { comp } = await renderComponent({ viewer: true });

      expect(comp.cssDisabled).toBe(true);
   });

   it("should set cssDisabled=true when table has selectedData (header/data cell selected)", async () => {
      const { comp } = await renderComponent();
      const table = TestUtils.createMockVSTableModel("Table1");
      table.selectedData = new Map([[1, [2]]]);

      comp.focusedAssemblies = [table];

      expect(comp.cssDisabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — isValueFillVisible: VSGauge face matching [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isValueFillVisible: VSGauge face values", () => {
   it("should return true when VSGauge face is 10910", async () => {
      const { comp } = await renderComponent();
      const gauge = TestUtils.createMockVSGaugeModel("Gauge1");
      gauge.face = 10910;

      comp._focusedAssemblies = [gauge];

      expect(comp.isValueFillVisible()).toBe(true);
   });

   it("should return false when the focused assembly is not a matching VSGauge", async () => {
      const { comp } = await renderComponent();
      const gauge = TestUtils.createMockVSGaugeModel("Gauge1");
      gauge.face = 9999; // no match

      comp._focusedAssemblies = [gauge];

      expect(comp.isValueFillVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 17 — roundCornerMax / tableSelected / textSelected / borderTooltip [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — roundCornerMax / tableSelected / textSelected / borderTooltip", () => {
   it("should return 20 for roundCornerMax when no assemblies are focused", async () => {
      const { comp } = await renderComponent();
      comp._focusedAssemblies = null;

      expect(comp.roundCornerMax).toBe(20);
   });

   it("should return min(height, width) of focused assemblies for roundCornerMax", async () => {
      const { comp } = await renderComponent();
      const obj = TestUtils.createMockVSObjectModel("VSText", "Text1");
      obj.objectFormat.height = 30;
      obj.objectFormat.width = 50;

      comp._focusedAssemblies = [obj];

      expect(comp.roundCornerMax).toBe(30); // min(30, 50)
   });

   it("should return true for tableSelected when a VSTable is the first focused assembly", async () => {
      const { comp } = await renderComponent();
      const table = TestUtils.createMockVSTableModel("Table1");
      comp._focusedAssemblies = [table];

      expect(comp.tableSelected).toBe(true);
   });

   it("should return true for textSelected when a VSText is the first focused assembly", async () => {
      const { comp } = await renderComponent();
      const text = TestUtils.createMockVSTextModel("Text1");
      comp._focusedAssemblies = [text];

      expect(comp.textSelected).toBe(true);
   });

   it("should return false for tableSelected when a VSText (not a table) is focused", async () => {
      const { comp } = await renderComponent();
      const text = TestUtils.createMockVSTextModel("Text1");
      comp._focusedAssemblies = [text];

      expect(comp.tableSelected).toBe(false);
   });

   it("should return the shape-border tooltip string when a VSLine is focused", async () => {
      const { comp } = await renderComponent();
      const line = TestUtils.createMockVSObjectModel("VSLine", "Line1") as any;
      comp._focusedAssemblies = [line];

      expect(comp.borderTooltip).toBe("_#(js:vs.format.shapeBorder)");
   });

   it("should return null for borderTooltip when a non-shape assembly is focused", async () => {
      const { comp } = await renderComponent();
      const text = TestUtils.createMockVSTextModel("Text1");
      comp._focusedAssemblies = [text];

      expect(comp.borderTooltip).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 18 — isRoundTopCornersOnlyVisible: VSTab regions [baseline]
// ---------------------------------------------------------------------------

describe("VSFormatsPane — isRoundTopCornersOnlyVisible: VSTab region check", () => {
   // 🔁 Regression-sensitive: roundTopCornersOnly checkbox is only meaningful for the whole-tab
   // state (no selected regions). Showing it when a region is selected confuses users because the
   // property then applies to an individual cell, not the tab header corners.
   it("should set roundTopCornersOnlyVisible=true when VSTab has no selectedRegions", async () => {
      const { comp } = await renderComponent();
      const tab = TestUtils.createMockVSTabModel("Tab1");
      tab.selectedRegions = [];

      comp.focusedAssemblies = [tab];

      expect(comp.roundTopCornersOnlyVisible).toBe(true);
   });

   it("should set roundTopCornersOnlyVisible=false when VSTab has selected regions", async () => {
      const { comp } = await renderComponent();
      const tab = TestUtils.createMockVSTabModel("Tab1");
      tab.selectedRegions = [TestUtils.createMockselectedRegion()];

      comp.focusedAssemblies = [tab];

      expect(comp.roundTopCornersOnlyVisible).toBe(false);
   });
});
