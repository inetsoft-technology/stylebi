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
 * SheetTabSelectorComponent — single pass (+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — onSelect: emits onTabSelected when not selected; no emit when already selected
 *   Group 2 [Risk 3] — closeTab non-viewsheet: emits onTabClosed immediately without HTTP
 *   Group 3 [Risk 3] — closeTab viewsheet, no form tables: getModel returns false → emits onTabClosed
 *   Group 4 [Risk 3] — closeTab viewsheet, form tables, user confirms: getModel true → confirm "ok" → emits onTabClosed
 *   Group 5 [Risk 2] — closeTab viewsheet, form tables, user cancels: user clicks cancel → does NOT emit onTabClosed
 *   Group 6 [Risk 2] — getTabClass: returns correct CSS class per type; empty string for unknown
 *   Group 7 [Risk 1] — getLabel: Untitled prefix handling; non-Untitled label; null label
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { SheetTabSelectorComponent } from "./sheet-tab-selector.component";
import { ModelService } from "../../../widget/services/model.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { ComposerTabModel } from "../composer-tab-model";
import { Sheet } from "../../data/sheet";
import { LibraryAsset } from "../../data/library-asset";

const MODEL_SERVICE_MOCK = { getModel: vi.fn() };
const MODAL_SERVICE_MOCK = {};

function makeViewsheetTab(isFocused: boolean, runtimeId = "vs-runtime-1"): ComposerTabModel {
   const asset = {
      label: "My Viewsheet",
      isFocused,
      runtimeId,
      isModified: vi.fn(() => false),
   } as unknown as Sheet;
   return new ComposerTabModel("viewsheet", asset);
}

function makeWorksheetTab(isFocused = false): ComposerTabModel {
   const asset = {
      label: "My Worksheet",
      isFocused,
      runtimeId: "ws-runtime-1",
      isModified: vi.fn(() => false),
   } as unknown as Sheet;
   return new ComposerTabModel("worksheet", asset);
}

function makeScriptTab(): ComposerTabModel {
   const asset: LibraryAsset = {
      id: "script-1",
      type: "script",
      label: "My Script",
      isFocused: false,
      isModified: false,
      newAsset: false,
   };
   return new ComposerTabModel("script", asset);
}

function makeTableStyleTab(): ComposerTabModel {
   const asset: LibraryAsset = {
      id: "ts-1",
      type: "tableStyle",
      label: "My Table Style",
      isFocused: false,
      isModified: false,
      newAsset: false,
   };
   return new ComposerTabModel("tableStyle", asset);
}

async function renderComponent() {
   return render(SheetTabSelectorComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
   });
}

beforeEach(() => {
   MODEL_SERVICE_MOCK.getModel.mockReset();
   vi.mocked(ComponentTool.showConfirmDialog as any)?.mockRestore?.();
});

afterEach(() => vi.restoreAllMocks());

describe("SheetTabSelectorComponent — onSelect", () => {
   // 🔁 Regression-sensitive: onSelect must not re-emit when tab is already focused to prevent
   // redundant server round-trips that reset viewsheet state.
   it("should emit onTabSelected when the tab is not selected", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);

      const emitted: ComposerTabModel[] = [];
      comp.onTabSelected.subscribe((t) => emitted.push(t));

      comp.onSelect(tab);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(tab);
   });

   it("should NOT emit onTabSelected when the tab is already selected (isFocused=true)", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(true);

      const emitted: ComposerTabModel[] = [];
      comp.onTabSelected.subscribe((t) => emitted.push(t));

      comp.onSelect(tab);

      expect(emitted).toHaveLength(0);
   });
});

describe("SheetTabSelectorComponent — closeTab non-viewsheet", () => {
   // 🔁 Regression-sensitive: non-viewsheet tabs must close synchronously without any HTTP call,
   // otherwise the tab stays open until an unrelated HTTP response arrives.
   it("should emit onTabClosed immediately for a worksheet tab without calling modelService", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeWorksheetTab();

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(tab);
   });

   it("should emit onTabClosed immediately for a script tab without calling modelService", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeScriptTab();

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
   });

   it("should emit onTabClosed immediately for a tableStyle tab without calling modelService", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeTableStyleTab();

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);

      expect(MODEL_SERVICE_MOCK.getModel).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
   });
});

describe("SheetTabSelectorComponent — closeTab viewsheet, no form tables", () => {
   // 🔁 Regression-sensitive: when getModel returns false the close must proceed without
   // a confirm dialog, so an extra dialog prompt would be a regression.
   it("should emit onTabClosed directly when getModel returns false (no unsaved form table data)", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);

      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(false));

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);

      expect(MODEL_SERVICE_MOCK.getModel).toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(tab);
   });
});

describe("SheetTabSelectorComponent — closeTab viewsheet, form tables, user confirms", () => {
   // 🔁 Regression-sensitive: confirmation dialog must be shown when form tables have unsaved
   // data, and the tab must close only after user clicks "ok".
   it("should emit onTabClosed after user confirms the dialog", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);

      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(true));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);
      // Allow the async promise chain to resolve
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(tab);
   });
});

describe("SheetTabSelectorComponent — closeTab viewsheet, form tables, user cancels", () => {
   it("should NOT emit onTabClosed when user cancels the confirm dialog", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);

      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(true));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      const emitted: ComposerTabModel[] = [];
      comp.onTabClosed.subscribe((t) => emitted.push(t));

      comp.closeTab(tab);
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
      expect(emitted).toHaveLength(0);
   });
});

describe("SheetTabSelectorComponent — getTabClass", () => {
   it("should return 'viewsheet' for viewsheet type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      expect(comp.getTabClass(makeViewsheetTab(false))).toBe("viewsheet");
   });

   it("should return 'worksheet' for worksheet type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      expect(comp.getTabClass(makeWorksheetTab())).toBe("worksheet");
   });

   it("should return 'script' for script type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      expect(comp.getTabClass(makeScriptTab())).toBe("script");
   });

   it("should return 'tableStyle' for tableStyle type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      expect(comp.getTabClass(makeTableStyleTab())).toBe("tableStyle");
   });

   it("should return empty string for unknown type", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = { type: "unknown", asset: { label: "x", isFocused: false } } as unknown as ComposerTabModel;
      expect(comp.getTabClass(tab)).toBe("");
   });
});

describe("SheetTabSelectorComponent — getLabel", () => {
   it("should return '_#(js:Untitled)1' for label 'Untitled1'", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);
      (tab.asset as any).label = "Untitled1";
      expect(comp.getLabel(tab)).toBe("_#(js:Untitled)1");
   });

   it("should return label as-is for non-Untitled label", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);
      (tab.asset as any).label = "Sales Dashboard";
      expect(comp.getLabel(tab)).toBe("Sales Dashboard");
   });

   it("should return empty string when label is null", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;
      const tab = makeViewsheetTab(false);
      (tab.asset as any).label = null;
      expect(comp.getLabel(tab)).toBe("");
   });
});
