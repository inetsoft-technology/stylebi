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
 * ScreensPane — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — deleteDeviceLayout: Bug #19354 showConfirmDialog guard; splice on "yes";
 *     no-op on "no"
 *   Group 2 [Risk 2] — isClearPrintLayoutEnabled: Bug #18417 returns false when printLayout is null;
 *     true when set and no currentLayout conflict
 *   Group 3 [Risk 2] — isDeleteLayoutEnabled: selectedLayout guard (-1) and name-conflict guard
 *   Group 4 [Risk 2] — initForm / updateEnabledState: controls created; disabled when
 *     targetScreen=false; enabled when targetScreen=true
 *   Group 5 [Risk 2] — getPrintLayoutLabel: "No Layout" when null; formatted string when set;
 *     "Landscape" suffix when landscape=true
 *   Group 6 [Risk 2] — removePrintLayout: nulls model.printLayout and updates form control value
 *   Group 7 [Risk 1] — ngOnInit selectedLayout default; onKeyDown arrow-key navigation
 *
 * Confirmed bugs (it.fails):
 *   Bug — initForm valueChanges subscription leak (Group 4): initForm() subscribes to
 *     templateHeight.valueChanges and templateWidth.valueChanges without storing the subscription
 *     references. ScreensPane implements no ngOnDestroy, so callbacks are never unsubscribed.
 *     After the component is destroyed, calling setValue on the form controls still triggers the
 *     callbacks and mutates this.model. Fix: store subscriptions in fields and unsubscribe them in
 *     a new ngOnDestroy hook.
 *
 * Out of scope:
 *   showViewsheetDeviceLayoutDialog / showViewsheetPrintLayoutDialog — open NgbModal with a
 *     ViewChild TemplateRef that is unavailable in unit tests; integration-level modal flow.
 *   Bug #19349 (printLayout input readonly attribute) — DOM-attribute-level check; ATL unit tests
 *     focus on component logic, not Angular reactive-form attribute propagation to the DOM.
 *   ngOnChanges — called when @Input references change; side-effects already covered via initForm.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ScreensPane } from "./screens-pane.component";
import { ScreensPaneModel } from "../../data/vs/screens-pane-model";
import { ViewsheetPrintLayoutDialogModel } from "../../data/vs/viewsheet-print-layout-dialog-model";
import { ComponentTool } from "../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<ScreensPaneModel> = {}): ScreensPaneModel {
   return {
      targetScreen: false,
      editDevicesAllowed: true,
      templateWidth: 200,
      templateHeight: 200,
      scaleToScreen: false,
      fitToWidth: false,
      deviceLayouts: [{ name: "layout1", mobileOnly: true, selectedDevices: ["phone1", "phone2"], id: "foo001" } as any],
      printLayout: null,
      devices: [{ label: "device", description: "i am a device", id: "00", minWidth: 100, maxWidth: 300, tempId: null } as any],
      balancePadding: false,
      ...overrides,
   } as ScreensPaneModel;
}

function createPrintLayoutModel(overrides: Partial<ViewsheetPrintLayoutDialogModel> = {}): ViewsheetPrintLayoutDialogModel {
   return {
      paperSize: "Letter [8.5x11 in]",
      marginTop: 1,
      marginBottom: 1,
      marginRight: 1,
      marginLeft: 1,
      footerFromEdge: 0.75,
      headerFromEdge: 0.5,
      landscape: false,
      scaleFont: 1,
      numberingStart: 0,
      customWidth: 0,
      customHeight: 0,
      units: "inches",
      ...overrides,
   };
}

async function renderComponent(modelOverrides: Partial<ScreensPaneModel> = {}) {
   const form = new UntypedFormGroup({});
   const model = createModel(modelOverrides);
   const { fixture } = await render(ScreensPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: NgbModal, useValue: { open: vi.fn() } },
      ],
      componentInputs: {
         model,
         form,
         viewsheet: { currentLayout: null } as any,
         isPrintLayout: false,
      },
   });
   const comp = fixture.componentInstance as ScreensPane;
   return { comp, fixture, form, model };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: deleteDeviceLayout [Risk 3] — Bug #19354
// ---------------------------------------------------------------------------

describe("ScreensPane — deleteDeviceLayout", () => {
   it("should call ComponentTool.showConfirmDialog when delete is invoked", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0;
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      comp.deleteDeviceLayout();

      expect(confirmSpy).toHaveBeenCalled();
   });

   it("should splice the selected layout when user confirms with 'yes'", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0;
      const initialLength = comp.model.deviceLayouts.length;
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");

      comp.deleteDeviceLayout();
      await Promise.resolve(); // flush the Promise.then microtask

      expect(comp.model.deviceLayouts).toHaveLength(initialLength - 1);
   });

   it("should NOT splice when user cancels with 'no'", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0;
      const initialLength = comp.model.deviceLayouts.length;
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("no");

      comp.deleteDeviceLayout();
      await Promise.resolve();

      expect(comp.model.deviceLayouts).toHaveLength(initialLength);
   });

   it("should clamp selectedLayout to last valid index after splice removes the last entry", async () => {
      const { comp } = await renderComponent();
      // Only one layout; after splice selectedLayout should drop to -1 (length - 1 = 0 - 1 = -1 is wrong,
      // but the code does: if(selectedLayout == length) selectedLayout--)
      // With one entry: splice → length=0, selectedLayout was 0; 0 == 0 → selectedLayout = -1
      comp.selectedLayout = 0;
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("yes");

      comp.deleteDeviceLayout();
      await Promise.resolve();

      expect(comp.selectedLayout).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: isClearPrintLayoutEnabled [Risk 2] — Bug #18417
// ---------------------------------------------------------------------------

describe("ScreensPane — isClearPrintLayoutEnabled", () => {
   it("should return false when model.printLayout is null (Bug #18417)", async () => {
      const { comp } = await renderComponent({ printLayout: null });
      expect(comp.isClearPrintLayoutEnabled()).toBeFalsy();
   });

   it("should return true when printLayout is set and viewsheet has no currentLayout", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();
      comp.viewsheet.currentLayout = null;

      expect(comp.isClearPrintLayoutEnabled()).toBeTruthy();
   });

   it("should return false when currentLayout has printLayout set", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();
      comp.viewsheet.currentLayout = { printLayout: true, name: "current" } as any;

      expect(comp.isClearPrintLayoutEnabled()).toBeFalsy();
   });

   it("should return true when currentLayout.printLayout is falsy", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();
      comp.viewsheet.currentLayout = { printLayout: false, name: "current" } as any;

      expect(comp.isClearPrintLayoutEnabled()).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 3: isDeleteLayoutEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("ScreensPane — isDeleteLayoutEnabled", () => {
   it("should return false when selectedLayout is -1 (nothing selected)", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = -1;
      expect(comp.isDeleteLayoutEnabled()).toBe(false);
   });

   it("should return false when selected layout name matches currentLayout name", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0; // layout1
      comp.viewsheet.currentLayout = { name: "layout1", printLayout: false } as any;

      expect(comp.isDeleteLayoutEnabled()).toBe(false);
   });

   it("should return true when selectedLayout is valid and no name conflict", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0;
      comp.viewsheet.currentLayout = null;

      expect(comp.isDeleteLayoutEnabled()).toBe(true);
   });

   it("should return true when currentLayout has a different name", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0; // layout1
      comp.viewsheet.currentLayout = { name: "otherLayout", printLayout: false } as any;

      expect(comp.isDeleteLayoutEnabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: initForm / updateEnabledState [Risk 2]
// ---------------------------------------------------------------------------

describe("ScreensPane — initForm / updateEnabledState", () => {
   it("should add templateHeight and templateWidth form controls on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["templateHeight"]).toBeDefined();
      expect(form.controls["templateWidth"]).toBeDefined();
   });

   it("should initialise templateHeight control with the model value", async () => {
      const { form } = await renderComponent({ templateHeight: 400 });
      expect(form.controls["templateHeight"].value).toBe(400);
   });

   it("should disable templateHeight and templateWidth when targetScreen is false", async () => {
      const { form } = await renderComponent({ targetScreen: false });
      expect(form.controls["templateHeight"].disabled).toBe(true);
      expect(form.controls["templateWidth"].disabled).toBe(true);
   });

   it("should enable templateHeight and templateWidth when targetScreen is true", async () => {
      const { form } = await renderComponent({ targetScreen: true });
      expect(form.controls["templateHeight"].enabled).toBe(true);
      expect(form.controls["templateWidth"].enabled).toBe(true);
   });

   it("should add a printLayout form control on init", async () => {
      const { form } = await renderComponent();
      expect(form.controls["printLayout"]).toBeDefined();
   });

   // Bug: initForm() subscribes to templateHeight.valueChanges and templateWidth.valueChanges
   // without storing the Subscription. ScreensPane has no ngOnDestroy, so the callbacks are
   // never cancelled. After the component is destroyed, setValue on the form control still
   // triggers the callback and mutates this.model. Fix: add an ngOnDestroy that unsubscribes.
   it.fails("should not mutate model.templateHeight after component is destroyed (valueChanges leak)", async () => {
      const { comp, fixture, form } = await renderComponent();
      const originalHeight = comp.model.templateHeight; // 200

      fixture.destroy(); // no ngOnDestroy → valueChanges subscriptions NOT cancelled

      form.controls["templateHeight"].setValue(999); // triggers the live callback

      // With fix: model is not mutated after destroy
      // Currently: FAILS — callback runs and sets model.templateHeight = 999
      expect(comp.model.templateHeight).toBe(originalHeight);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getPrintLayoutLabel [Risk 2]
// ---------------------------------------------------------------------------

describe("ScreensPane — getPrintLayoutLabel", () => {
   it("should return 'No Layout' label when printLayout is null", async () => {
      const { comp } = await renderComponent({ printLayout: null });
      expect(comp.getPrintLayoutLabel()).toContain("No Layout");
   });

   it("should include the paper size in the label when printLayout is set", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();
      expect(comp.getPrintLayoutLabel()).toContain("Letter [8.5x11 in]");
   });

   it("should include the margin values in the label", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel({ marginTop: 2, marginLeft: 3, marginBottom: 4, marginRight: 5 });
      const label = comp.getPrintLayoutLabel();
      expect(label).toContain("2");
      expect(label).toContain("3");
   });

   it("should include Landscape in the label when landscape is true", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel({ landscape: true });
      expect(comp.getPrintLayoutLabel()).toContain("Landscape");
   });

   it("should NOT include Landscape in the label when landscape is false", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel({ landscape: false });
      expect(comp.getPrintLayoutLabel()).not.toContain("Landscape");
   });
});

// ---------------------------------------------------------------------------
// Group 6: removePrintLayout [Risk 2]
// ---------------------------------------------------------------------------

describe("ScreensPane — removePrintLayout", () => {
   it("should set model.printLayout to null", async () => {
      const { comp } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();

      comp.removePrintLayout();

      expect(comp.model.printLayout).toBeNull();
   });

   it("should update the printLayout form control value to null", async () => {
      const { comp, form } = await renderComponent();
      comp.model.printLayout = createPrintLayoutModel();

      comp.removePrintLayout();

      expect(form.controls["printLayout"].value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7: ngOnInit default selection; onKeyDown navigation [Risk 1]
// ---------------------------------------------------------------------------

describe("ScreensPane — ngOnInit and onKeyDown", () => {
   it("should set selectedLayout=0 when deviceLayouts has at least one entry", async () => {
      const { comp } = await renderComponent(); // model has 1 layout
      expect(comp.selectedLayout).toBe(0);
   });

   it("should keep selectedLayout=-1 when deviceLayouts is empty", async () => {
      const { comp } = await renderComponent({ deviceLayouts: [] });
      expect(comp.selectedLayout).toBe(-1);
   });

   it("onKeyDown Up (38) should decrement selectedLayout when above 0", async () => {
      const { comp } = await renderComponent();
      comp.model.deviceLayouts.push({ name: "layout2", mobileOnly: false, selectedDevices: [], id: "bar002" } as any);
      comp.selectedLayout = 1;

      comp.onKeyDown({ keyCode: 38 } as KeyboardEvent);

      expect(comp.selectedLayout).toBe(0);
   });

   it("onKeyDown Up (38) should NOT go below 0", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0;

      comp.onKeyDown({ keyCode: 38 } as KeyboardEvent);

      expect(comp.selectedLayout).toBe(0);
   });

   it("onKeyDown Down (40) should increment selectedLayout when below max", async () => {
      const { comp } = await renderComponent();
      comp.model.deviceLayouts.push({ name: "layout2", mobileOnly: false, selectedDevices: [], id: "bar002" } as any);
      comp.selectedLayout = 0;

      comp.onKeyDown({ keyCode: 40 } as KeyboardEvent);

      expect(comp.selectedLayout).toBe(1);
   });

   it("onKeyDown Down (40) should NOT go past the last index", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = 0; // only 1 layout, max index is 0

      comp.onKeyDown({ keyCode: 40 } as KeyboardEvent);

      expect(comp.selectedLayout).toBe(0);
   });

   it("onKeyDown should not change selectedLayout when it is -1", async () => {
      const { comp } = await renderComponent();
      comp.selectedLayout = -1;

      comp.onKeyDown({ keyCode: 38 } as KeyboardEvent);
      comp.onKeyDown({ keyCode: 40 } as KeyboardEvent);

      expect(comp.selectedLayout).toBe(-1);
   });
});
