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
 * BindingEditor — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — updateData(): 6 action branches (Bug #20163 getCurrentFormat path)
 *   Group 2 [Risk 2] — switchTab/formatPaneVisible/formatsInactive/formatsDisabled (Bug #20245)
 *   Group 3 [Risk 2] — bindingModel setter: showDragTip / showDcAppliedTip for chart and crosstab
 *   Group 4 [Risk 2] — grayedOutFields setter: populates grayedOutValues array
 *   Group 5 [Risk 2] — updateFormat(): emits "updateFormat" or "reset"
 *   Group 6 [Risk 2] — ngOnDestroy: calls bindingService.clear() (+memory leak openConsoleDialog)
 *   Group 7 [Risk 1] — showHighLowPane, changeMessage, hideDcTip, updateChartMaxMode
 *
 * Old spec ported (Risk 3):
 *   Bug #20163: updateData("getCurrentFormat") must emit "getCurrentFormat" and clear hideFormatPane
 *   Bug #20245: switchTab(FORMAT_PANE) → formatPaneVisible must be true
 *
 * Confirmed bugs (it.fails):
 *   Bug — openConsoleDialog subscription leak (Group 6): ngOnDestroy calls bindingService.clear()
 *     but the modelService.getModel() subscription inside openConsoleDialog() is never stored.
 *     If the component is destroyed before the HTTP response arrives, the callback still fires
 *     and mutates this.messageLevels on the dead component. Fix: store the subscription and
 *     unsubscribe in ngOnDestroy.
 *
 * Out of scope:
 *   openConsoleDialog() full modal flow — requires live NgbModal + ConsoleDialogComponent ViewChild.
 *   popUpWarning() — delegates to NotificationsComponent ViewChild; ViewChild not available in unit test.
 *   sizeChanged() — delegates to NotificationsComponent ViewChild; same constraint.
 *   bindingType getter — one-liner; covered transitively via bindingModel setter tests.
 *   variableValues setter — one-liner delegate to bindingService; no observable state to assert.
 *   assemblyName setter — delegates to bindingService; no observable state.
 *   ngAfterViewInit — emits onInitialized via EventEmitter; emitter lifecycle, Risk 1.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BindingEditor } from "./binding-editor.component";
import { BindingService } from "../services/binding.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ModelService } from "../../widget/services/model.service";
import { SidebarTab } from "../widget/binding-tree/data-editor-tab-pane.component";
import { GraphTypes } from "../../common/graph-types";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { CrosstabBindingModel } from "../data/table/crosstab-binding-model";
import { DataRef } from "../../common/data/data-ref";
import { TestUtils } from "../../common/test/test-utils";
import {
   BINDING_SERVICE_MOCK,
   MODEL_SERVICE_MOCK,
   MODAL_MOCK,
   UI_CONTEXT_MOCK,
   renderComponent as renderBindingEditor,
} from "./binding-editor.component.test-fixtures";

const bindingServiceMock = {
   runtimeId: "",
   assemblyName: "",
   objectType: "",
   bindingModel: null as any,
   variableValues: [] as string[],
   clear: vi.fn(),
   setGrayedOutFields: vi.fn(),
};

const uiContextMock = { isVS: vi.fn().mockReturnValue(false) };
const modalMock = {
   open: vi.fn().mockReturnValue({ result: Promise.resolve([]) }),
};
let modelServiceMock = { getModel: vi.fn() };

async function renderComponent(props: Record<string, any> = {}) {
   const { fixture } = await render(BindingEditor, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: UIContextService, useValue: uiContextMock },
         { provide: NgbModal, useValue: modalMock },
         { provide: ModelService, useValue: modelServiceMock },
      ],
      componentProperties: { consoleMessages: [], ...props },
   });
   return fixture.componentInstance as BindingEditor;
}

beforeEach(() => {
   bindingServiceMock.clear.mockClear();
   bindingServiceMock.setGrayedOutFields.mockClear();
   modalMock.open.mockClear();
   modelServiceMock = { getModel: vi.fn() };
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: updateData() [Risk 3]
// ---------------------------------------------------------------------------

describe("BindingEditor — updateData action branches", () => {
   // 🔁 Regression-sensitive (Bug #20163): "getCurrentFormat" must emit "getCurrentFormat" and clear
   //    hideFormatPane; if hideFormatPane stays true, the format pane won't appear.
   it("should emit getCurrentFormat and clear hideFormatPane for action=getCurrentFormat", async () => {
      const comp = await renderComponent();
      comp.hideFormatPane = true;
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("getCurrentFormat");

      expect(emitted).toContain("getCurrentFormat");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit getTextFormat and switch to FORMAT_PANE tab for action=showTextFormat", async () => {
      const comp = await renderComponent();
      comp.selectedTab = SidebarTab.BINDING_TREE;
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("showTextFormat");

      expect(emitted).toContain("getTextFormat");
      expect(comp.selectedTab).toBe(SidebarTab.FORMAT_PANE);
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should emit getTextFormat and clear hideFormatPane for action=getTextFormat", async () => {
      const comp = await renderComponent();
      comp.hideFormatPane = true;
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("getTextFormat");

      expect(emitted).toContain("getTextFormat");
      expect(comp.hideFormatPane).toBe(false);
   });

   it("should set hideFormatPane=true for action=hideFormatPane without emitting", async () => {
      const comp = await renderComponent();
      comp.hideFormatPane = false;
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("hideFormatPane");

      expect(comp.hideFormatPane).toBe(true);
      expect(emitted).toHaveLength(0);
   });

   it("should switch to FORMAT_PANE and emit getCurrentFormat for action=openFormatPane", async () => {
      const comp = await renderComponent();
      comp.selectedTab = SidebarTab.BINDING_TREE;
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("openFormatPane");

      expect(comp.selectedTab).toBe(SidebarTab.FORMAT_PANE);
      expect(comp.hideFormatPane).toBe(false);
      expect(emitted).toContain("getCurrentFormat");
   });

   it("should emit the action string unchanged for unknown actions (default branch)", async () => {
      const comp = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      comp.updateData("someCustomAction");

      expect(emitted).toContain("someCustomAction");
   });
});

// ---------------------------------------------------------------------------
// Group 2: switchTab / formatPaneVisible / formatsInactive / formatsDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — tab visibility flags", () => {
   // 🔁 Regression-sensitive (Bug #20245): switching to FORMAT_PANE must make formatPaneVisible true;
   //    if the flag is wrong, the format pane is hidden even after the user clicks its tab.
   it("should make formatPaneVisible true after switchTab(FORMAT_PANE)", async () => {
      const comp = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      expect(comp.formatPaneVisible).toBe(true);
   });

   it("should make formatsInactive false when selectedTab is FORMAT_PANE", async () => {
      const comp = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      expect(comp.formatsInactive).toBe(false);
   });

   it("should make formatsInactive true when selectedTab is BINDING_TREE", async () => {
      const comp = await renderComponent();
      comp.switchTab(SidebarTab.BINDING_TREE);
      expect(comp.formatsInactive).toBe(true);
   });

   it("should return true from formatsDisabled when hideFormatPane is true", async () => {
      const comp = await renderComponent();
      comp.hideFormatPane = true;
      expect(comp.formatsDisabled).toBe(true);
   });

   it("should return true from formatsDisabled when formatPaneDisabled is true", async () => {
      const comp = await renderComponent({ formatPaneDisabled: true });
      expect(comp.formatsDisabled).toBe(true);
   });

   it("should return false from formatsDisabled when neither flag is set", async () => {
      const comp = await renderComponent({ formatPaneDisabled: false });
      comp.hideFormatPane = false;
      expect(comp.formatsDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: bindingModel setter [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — bindingModel setter", () => {
   it("should set showDragTip=false when binding model is bound (calctable)", async () => {
      const comp = await renderComponent();
      comp.bindingModel = { type: "calctable" } as any;
      expect(comp.showDragTip).toBe(false);
   });

   it("should set showDragTip=true when binding model is not bound (empty table)", async () => {
      const comp = await renderComponent();
      comp.bindingModel = { type: "table", groups: [], details: [], aggregates: [] } as any;
      expect(comp.showDragTip).toBe(true);
   });

   it("should set showDcAppliedTip from hasDateComparison for chart type after ngOnInit", async () => {
      const chartModel: Partial<ChartBindingModel> = {
         type: "chart",
         hasDateComparison: true,
         xfields: [],
         yfields: [],
         geoFields: [],
         groupFields: [],
         geoCols: [],
      };
      const comp = await renderComponent();
      comp.bindingModel = chartModel as ChartBindingModel;
      comp.ngOnInit();
      expect(comp.showDcAppliedTip).toBe(true);
   });

   it("should set showDcAppliedTip from hasDateComparison for crosstab type after ngOnInit", async () => {
      const crosstabModel: Partial<CrosstabBindingModel> = {
         type: "crosstab",
         hasDateComparison: false,
         rows: [],
         cols: [],
         aggregates: [],
      };
      const comp = await renderComponent();
      comp.bindingModel = crosstabModel as CrosstabBindingModel;
      comp.ngOnInit();
      expect(comp.showDcAppliedTip).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: grayedOutFields setter [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — grayedOutFields setter", () => {
   it("should populate grayedOutValues from DataRef.name array", async () => {
      const comp = await renderComponent();
      const fields = [{ name: "fieldA" }, { name: "fieldB" }] as DataRef[];
      comp.grayedOutFields = fields;
      expect(comp.grayedOutValues).toEqual(["fieldA", "fieldB"]);
   });

   it("should pass fields to bindingService.setGrayedOutFields", async () => {
      const comp = await renderComponent();
      const fields = [{ name: "x" }] as DataRef[];
      comp.grayedOutFields = fields;
      expect(bindingServiceMock.setGrayedOutFields).toHaveBeenCalledWith(fields);
   });

   it("should reset grayedOutValues to empty array when fields is empty", async () => {
      const comp = await renderComponent();
      comp.grayedOutFields = [{ name: "old" }] as DataRef[];
      comp.grayedOutFields = [];
      expect(comp.grayedOutValues).toHaveLength(0);
   });

   it("should not mutate grayedOutValues when fields is null", async () => {
      const comp = await renderComponent();
      comp.grayedOutValues = ["preserved"];
      comp.grayedOutFields = null;
      expect(comp.grayedOutValues).toEqual(["preserved"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateFormat() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — updateFormat", () => {
   it("should emit updateFormat action when model is truthy", async () => {
      const comp = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));
      comp.updateFormat({ some: "model" });
      expect(emitted).toContain("updateFormat");
   });

   it("should emit reset action when model is null/falsy", async () => {
      const comp = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));
      comp.updateFormat(null);
      expect(emitted).toContain("reset");
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnDestroy + memory leak [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — ngOnDestroy and lifecycle", () => {
   it("should call bindingService.clear() when the component is destroyed", async () => {
      const { fixture } = await render(BindingEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: bindingServiceMock },
            { provide: UIContextService, useValue: uiContextMock },
            { provide: NgbModal, useValue: modalMock },
            { provide: ModelService, useValue: { getModel: vi.fn() } },
         ],
         componentProperties: { consoleMessages: [] },
      });
      fixture.destroy();
      expect(bindingServiceMock.clear).toHaveBeenCalledOnce();
   });

   // Bug: openConsoleDialog() never stores the modelService.getModel() subscription, so
   // if the component is destroyed before the HTTP response arrives the callback still fires
   // and mutates this.messageLevels. Fix: store the subscription in a field and unsubscribe
   // in ngOnDestroy alongside bindingService.clear().
   it.fails("should not mutate messageLevels after destroy when HTTP response arrives late (openConsoleDialog leak)", async () => {
      const lateSource = new Subject<string[]>();
      const { fixture } = await render(BindingEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: bindingServiceMock },
            { provide: UIContextService, useValue: uiContextMock },
            { provide: NgbModal, useValue: modalMock },
            { provide: ModelService, useValue: { getModel: vi.fn().mockReturnValue(lateSource.asObservable()) } },
         ],
         componentProperties: { runtimeId: "test-rt", consoleMessages: [] },
      });
      const comp = fixture.componentInstance as BindingEditor;

      comp.openConsoleDialog(); // starts subscription; not stored
      fixture.destroy();        // ngOnDestroy → bindingService.clear(); subscription NOT cancelled

      lateSource.next(["INFO", "WARNING"]); // HTTP response arrives after destroy

      // If properly cleaned up, messageLevels stays []. Currently it's set to ["INFO","WARNING"].
      expect(comp.messageLevels).toHaveLength(0); // FAILS — proves the leak
   });
});

// ---------------------------------------------------------------------------
// Group 7: misc [Risk 1]
// ---------------------------------------------------------------------------

describe("BindingEditor — showHighLowPane, changeMessage, hideDcTip, updateChartMaxMode", () => {
   it("should return true from showHighLowPane for CHART_STOCK chart type", async () => {
      const comp = await renderComponent();
      comp.bindingModel = {
         type: "chart",
         chartType: GraphTypes.CHART_STOCK,
         hasOwnProperty: (k: string) => k === "chartType",
      } as any;
      expect(comp.showHighLowPane()).toBe(true);
   });

   it("should return false from showHighLowPane for table binding model", async () => {
      const comp = await renderComponent();
      comp.bindingModel = { type: "table", groups: [], details: [], aggregates: [] } as any;
      // chartBinding is null → && short-circuits to null, not strict false
      expect(comp.showHighLowPane()).toBeFalsy();
   });

   it("should return false from showHighLowPane when bindingModel is null", async () => {
      const comp = await renderComponent();
      comp.bindingModel = null;
      // chartBinding is null → && short-circuits to null, not strict false
      expect(comp.showHighLowPane()).toBeFalsy();
   });

   it("should update consoleMessages and emit onMessageChange via changeMessage", async () => {
      const comp = await renderComponent();
      const emitted: any[] = [];
      comp.onMessageChange.subscribe(v => emitted.push(v));
      const messages = [{ message: "err", level: "error" } as any];
      comp.changeMessage(messages);
      expect(comp.consoleMessages).toBe(messages);
      expect(emitted[0]).toBe(messages);
   });

   it("should set showDcAppliedTip=false when hideDcTip is called", async () => {
      const comp = await renderComponent();
      comp.showDcAppliedTip = true;
      comp.hideDcTip();
      expect(comp.showDcAppliedTip).toBe(false);
   });

   it("should update chartMaxMode from updateChartMaxMode", async () => {
      const comp = await renderComponent();
      comp.updateChartMaxMode({ assembly: "Chart1", maxMode: true });
      expect(comp.chartMaxMode).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: crosstab template regressions [legacy]
// ---------------------------------------------------------------------------

describe("BindingEditor — crosstab template regressions", () => {
   it("should not render percent-by option for VS crosstab binding", async () => {
      UI_CONTEXT_MOCK.isVS.mockReturnValue(true);
      const bindingModel = TestUtils.createMockCrosstabBindingModel();
      bindingModel.aggregates = [TestUtils.createMockBAggregateRef("customer_id")];
      bindingModel.option = {
         colTotalVisibleValue: "false",
         rowTotalVisibleValue: "false",
         percentageByValue: "1",
         summarySideBySide: false,
      };

      const { fixture } = await renderBindingEditor({
         objectType: "VSCrosstab",
         assemblyName: "Crosstab1",
         bindingModel,
         runtimeId: "crosstab-15096061975720",
         objectModel: TestUtils.createMockVSObjectModel("VSCrosstab", "Crosstab1"),
         currentFormat: TestUtils.createMockFromatInfo(),
         linkUri: "/sree/",
         formatPaneDisabled: false,
         variableValues: [],
      });

      expect(fixture.nativeElement.querySelector(".percentBy_label_id")).toBeNull();
   });
});
