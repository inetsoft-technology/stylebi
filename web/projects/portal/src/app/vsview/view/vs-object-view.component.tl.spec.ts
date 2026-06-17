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
 * VSObjectView — single pass (+memory-leak)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit: vsInfo initialized with linkUri;
 *                        LocalStorage chart-edit-max-mode triggers chartMaxMode=true
 *   Group 2  [Risk 3] — onChartMaxModeChange: chartMaxMode update; chartMaxModeChange emit;
 *                        model.maxMode guarded by notAuto; LocalStorage persistence
 *   Group 3  [Risk 2] — getFormats @HostListener click: emits onUpdateData("getCurrentFormat")
 *                        after 250ms timeout
 *   Group 4  [Risk 2] — getAssemblyName: dot present; no dot → null; null model → null
 *   Group 5  [baseline] — model setter: createActions called with model, result stored in actions
 *   Group 6  [baseline] — getCalcTableLayout: stores layoutModel reference
 *   Group 7  [baseline] — showMiniToolbar: delegates to miniToolbarService
 *   Group 8  [baseline] — delegation: onMouseEnter / contextmenuOpened / contextmenuClosed
 *   Group 9  [Risk 2] — CommandProcessor commands: processRefreshVSObjectCommand /
 *                        processAddVSObjectCommand emit their respective outputs
 *   Group 10 [baseline +memory-leak] — ngOnDestroy: lifecycle called without error
 *
 * Suspected bugs (header only):
 *   Memory-leak in resizeModelView — calls new Date().getTime() which is mutable state;
 *   not a leak per se, but triggers ChangeDetectorRef.detectChanges on every resize.
 *
 * Out of scope:
 *   resizeModelView() — DOM-dependent (getBoundingClientRect / offsetWidth / offsetHeight
 *     always return 0 in jsdom; the resize branches are untestable without a real browser)
 *   ngAfterViewInit() — calls resizeModelView() which is DOM-dependent
 *   onResize() — depends on ViewChild "object" (AbstractVSObject), never instantiated
 *     under NO_ERRORS_SCHEMA; DOM-dependent
 *   resizeListener() @HostListener window:resize — delegates to resizeModelView()
 */

import { Component, Directive, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";

import { VSObjectView } from "./vs-object-view.component";
import { VSChart } from "../../vsobjects/objects/chart/vs-chart.component";
import { VSTable } from "../../vsobjects/objects/table/vs-table.component";
import { VSCrosstab } from "../../vsobjects/objects/table/vs-crosstab.component";
import { CalcTableLayoutPane } from "./vs-calc-table-layout.component";
import { MiniToolbar } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.component";
import { ActionsContextmenuAnchorDirective } from "../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { BTableActionHandlerDirective } from "../action/b-table-action-handler.directive";
import { BCrosstabActionHandlerDirective } from "../action/b-crosstab-action-handler.directive";
import { BCalcTableActionHandlerDirective } from "../action/b-calctable-action-handler.directive";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../vsobjects/action/assembly-action-factory.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { LocalStorage } from "../../common/util/local-storage.util";
import { RefreshVSObjectCommand } from "../../vsobjects/command/refresh-vs-object-command";
import { AddVSObjectCommand } from "../../vsobjects/command/add-vs-object-command";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Stubs — replace heavy component/directive tree with empty shells to prevent
// cascading DI failures from each child component's own dependency graph.
// ---------------------------------------------------------------------------

@Component({ selector: "vs-chart", template: "", standalone: true })
class VSChartStub {}

@Component({ selector: "vs-table", template: "", standalone: true })
class VSTableStub {}

@Component({ selector: "vs-crosstab", template: "", standalone: true })
class VSCrosstabStub {}

@Component({ selector: "vs-calc-table-layout", template: "", standalone: true })
class CalcTableLayoutPaneStub {}

@Component({ selector: "mini-toolbar", template: "", standalone: true })
class MiniToolbarStub {}

@Directive({ selector: "[actionsContextmenuAnchor]", standalone: true })
class ActionsContextmenuAnchorStub {
   @Input() actionsContextmenuAnchor: any;
}

@Directive({ selector: "[bTableActionHandler]", standalone: true })
class BTableActionHandlerStub {}

@Directive({ selector: "[bCrosstabActionHandler]", standalone: true })
class BCrosstabActionHandlerStub {}

@Directive({ selector: "[bCalcTableActionHandler]", standalone: true })
class BCalcTableActionHandlerStub {}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

const COMMANDS_SUBJECT = new Subject<any>();
const VS_CLIENT_MOCK = {
   sendEvent: vi.fn(),
   commands: COMMANDS_SUBJECT,
};

const ACTION_FACTORY_MOCK = {
   createActions: vi.fn().mockReturnValue({ type: "mockActions" }),
};

const MINI_TOOLBAR_MOCK = {
   isMiniToolbarHidden: vi.fn().mockReturnValue(false),
   handleMouseEnter: vi.fn(),
   hiddenFreeze: vi.fn(),
   hiddenUnfreeze: vi.fn(),
};

const MODAL_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeModel(objectType: string, overrides: Partial<VSObjectModel> = {}): VSObjectModel {
   return {
      absoluteName: "Parent.child",
      objectType,
      objectFormat: { left: 0, top: 0, width: 100, height: 50 },
      ...overrides,
   } as unknown as VSObjectModel;
}

interface RenderOptions {
   model?: VSObjectModel;
   linkUri?: string;
}

async function renderComponent(opts: RenderOptions = {}) {
   const model = opts.model ?? makeModel("VSChart");
   const result = await render(VSObjectView, {
      inputs: { model, linkUri: opts.linkUri ?? "http://localhost" },
      providers: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
         { provide: AssemblyActionFactory,  useValue: ACTION_FACTORY_MOCK },
         { provide: MiniToolbarService,     useValue: MINI_TOOLBAR_MOCK },
         { provide: NgbModal,               useValue: MODAL_MOCK },
      ],
      importOverrides: [
         { replace: VSChart,                          with: VSChartStub },
         { replace: VSTable,                          with: VSTableStub },
         { replace: VSCrosstab,                       with: VSCrosstabStub },
         { replace: CalcTableLayoutPane,              with: CalcTableLayoutPaneStub },
         { replace: MiniToolbar,                      with: MiniToolbarStub },
         { replace: ActionsContextmenuAnchorDirective, with: ActionsContextmenuAnchorStub },
         { replace: BTableActionHandlerDirective,     with: BTableActionHandlerStub },
         { replace: BCrosstabActionHandlerDirective,  with: BCrosstabActionHandlerStub },
         { replace: BCalcTableActionHandlerDirective, with: BCalcTableActionHandlerStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeEach(() => {
   vi.useFakeTimers();
   vi.spyOn(LocalStorage, "getItem").mockReturnValue(null);
   ACTION_FACTORY_MOCK.createActions.mockClear();
   MINI_TOOLBAR_MOCK.isMiniToolbarHidden.mockClear().mockReturnValue(false);
   MINI_TOOLBAR_MOCK.handleMouseEnter.mockClear();
   MINI_TOOLBAR_MOCK.hiddenFreeze.mockClear();
   MINI_TOOLBAR_MOCK.hiddenUnfreeze.mockClear();
});

afterEach(() => {
   vi.useRealTimers();
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — ngOnInit: vsInfo initialization", () => {
   it("should initialize vsInfo after render", async () => {
      const { comp } = await renderComponent({ linkUri: "http://example.com" });
      expect(comp.vsInfo.linkUri).toBe("http://example.com");
   });

   it("should set chartMaxMode=true when chart-edit-max-mode exists in LocalStorage", async () => {
      vi.spyOn(LocalStorage, "getItem").mockReturnValue("true");
      const model = makeModel("VSChart");
      const { comp } = await renderComponent({ model });
      expect(comp.chartMaxMode).toBe(true);
   });

   it("should NOT set chartMaxMode when chart-edit-max-mode is absent from LocalStorage", async () => {
      const { comp } = await renderComponent();
      expect(comp.chartMaxMode).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — onChartMaxModeChange() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSObjectView — onChartMaxModeChange", () => {
   // 🔁 Regression-sensitive: chartMaxMode controls max-mode chart rendering on the server.
   // An incorrect value causes the chart to render at the wrong size on all subsequent frames.

   it("should set chartMaxMode to the event's maxMode value", async () => {
      const { comp } = await renderComponent();
      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: true });
      expect(comp.chartMaxMode).toBe(true);
   });

   it("should emit chartMaxModeChange with the full event payload", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.chartMaxModeChange.subscribe((v: any) => emitted.push(v));

      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: true });

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual({ assembly: "Chart1", maxMode: true });
   });

   it("should set model.maxMode when objectType is VSChart and notAuto is true", async () => {
      const model = makeModel("VSChart", { notAuto: true } as any);
      const { comp } = await renderComponent({ model });

      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: true });

      expect((model as any).maxMode).toBe(true);
   });

   it("should NOT set model.maxMode when notAuto is false", async () => {
      const model = makeModel("VSChart", { notAuto: false } as any);
      const { comp } = await renderComponent({ model });
      (model as any).maxMode = undefined;

      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: true });

      expect((model as any).maxMode).toBeUndefined();
   });

   it("should write 'true' to LocalStorage when maxMode is true", async () => {
      const setItemSpy = vi.spyOn(LocalStorage, "setItem");
      const { comp } = await renderComponent();

      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: true });

      expect(setItemSpy).toHaveBeenCalledWith("chart-edit-max-mode", "true");
   });

   it("should write '' to LocalStorage when maxMode is false", async () => {
      const setItemSpy = vi.spyOn(LocalStorage, "setItem");
      const { comp } = await renderComponent();

      comp.onChartMaxModeChange({ assembly: "Chart1", maxMode: false });

      expect(setItemSpy).toHaveBeenCalledWith("chart-edit-max-mode", "");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getFormats() @HostListener click [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — getFormats @HostListener click", () => {
   // 🔁 Regression-sensitive: the 250ms delay is intentional (debounces rapid clicks);
   // removing the timeout causes the format pane to update before selection stabilizes.

   it("should emit onUpdateData('getCurrentFormat') after exactly 250ms on click", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe((v: string) => emitted.push(v));

      comp.getFormats({} as MouseEvent);

      vi.advanceTimersByTime(249);
      expect(emitted).toHaveLength(0);

      vi.advanceTimersByTime(1);
      expect(emitted).toEqual(["getCurrentFormat"]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — getAssemblyName() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — getAssemblyName()", () => {
   it("should return the portion before the last dot when absoluteName contains a dot", async () => {
      const model = makeModel("VSChart", { absoluteName: "ParentAssembly.childObj" } as any);
      const { comp } = await renderComponent({ model });
      expect(comp.getAssemblyName()).toBe("ParentAssembly");
   });

   it("should return null when absoluteName has no dot", async () => {
      const model = makeModel("VSChart", { absoluteName: "NoDotHere" } as any);
      const { comp } = await renderComponent({ model });
      expect(comp.getAssemblyName()).toBeNull();
   });

   it("should return null when _model is null", async () => {
      const { comp } = await renderComponent();
      // Bypass model setter (which also calls resizeModelView() — DOM-dependent in jsdom)
      (comp as any)["_model"] = null;
      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — model setter [baseline]
// ---------------------------------------------------------------------------

describe("VSObjectView — model setter", () => {
   it("should call actionFactory.createActions with the model and store the result in actions", async () => {
      const mockActions = { type: "testActions" };
      ACTION_FACTORY_MOCK.createActions.mockReturnValue(mockActions);
      const model = makeModel("VSChart");

      const { comp } = await renderComponent({ model });

      expect(ACTION_FACTORY_MOCK.createActions).toHaveBeenCalledWith(model);
      expect(comp.actions).toBe(mockActions);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — getCalcTableLayout() [baseline]
// ---------------------------------------------------------------------------

describe("VSObjectView — getCalcTableLayout()", () => {
   it("should store the provided calcTableLayout in layoutModel", async () => {
      const { comp } = await renderComponent();
      const layout = {} as any;

      comp.getCalcTableLayout(layout);

      expect(comp.layoutModel).toBe(layout);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — showMiniToolbar() [baseline]
// ---------------------------------------------------------------------------

describe("VSObjectView — showMiniToolbar()", () => {
   it("should return true when miniToolbarService.isMiniToolbarHidden returns false", async () => {
      MINI_TOOLBAR_MOCK.isMiniToolbarHidden.mockReturnValue(false);
      const { comp } = await renderComponent();
      expect(comp.showMiniToolbar()).toBe(true);
   });

   it("should return false when miniToolbarService.isMiniToolbarHidden returns true", async () => {
      MINI_TOOLBAR_MOCK.isMiniToolbarHidden.mockReturnValue(true);
      const { comp } = await renderComponent();
      expect(comp.showMiniToolbar()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — delegation methods [baseline]
// ---------------------------------------------------------------------------

describe("VSObjectView — delegation: onMouseEnter / contextmenuOpened / contextmenuClosed", () => {
   it("onMouseEnter should delegate to miniToolbarService.handleMouseEnter with absoluteName and event", async () => {
      const model = makeModel("VSChart", { absoluteName: "TestChart" } as any);
      const { comp } = await renderComponent({ model });
      const mockEvent = { type: "mouseenter" };

      comp.onMouseEnter(mockEvent);

      expect(MINI_TOOLBAR_MOCK.handleMouseEnter).toHaveBeenCalledWith("TestChart", mockEvent);
   });

   it("contextmenuOpened should delegate to miniToolbarService.hiddenFreeze with absoluteName", async () => {
      const model = makeModel("VSChart", { absoluteName: "TestChart" } as any);
      const { comp } = await renderComponent({ model });

      comp.contextmenuOpened();

      expect(MINI_TOOLBAR_MOCK.hiddenFreeze).toHaveBeenCalledWith("TestChart");
   });

   it("contextmenuClosed should delegate to miniToolbarService.hiddenUnfreeze with absoluteName", async () => {
      const model = makeModel("VSChart", { absoluteName: "TestChart" } as any);
      const { comp } = await renderComponent({ model });

      comp.contextmenuClosed();

      expect(MINI_TOOLBAR_MOCK.hiddenUnfreeze).toHaveBeenCalledWith("TestChart");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — CommandProcessor command handling [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — processRefreshVSObjectCommand / processAddVSObjectCommand", () => {
   // 🔁 Regression-sensitive: if these emitters are removed or renamed, the parent viewer
   // component stops receiving VS model updates and the viewsheet silently goes stale.

   it("processRefreshVSObjectCommand should emit the command via onRefreshVSObjectCommand", async () => {
      const { comp } = await renderComponent();
      const emitted: RefreshVSObjectCommand[] = [];
      comp.onRefreshVSObjectCommand.subscribe((v: RefreshVSObjectCommand) => emitted.push(v));
      const cmd = { objectName: "Table1" } as unknown as RefreshVSObjectCommand;

      // processRefreshVSObjectCommand is private; no public wrapper exists — bypass is intentional
      (comp as any).processRefreshVSObjectCommand(cmd);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(cmd);
   });

   it("processAddVSObjectCommand should emit the command via onAddVSObjectCommand", async () => {
      const { comp } = await renderComponent();
      const emitted: AddVSObjectCommand[] = [];
      comp.onAddVSObjectCommand.subscribe((v: AddVSObjectCommand) => emitted.push(v));
      const cmd = { objectName: "NewChart" } as unknown as AddVSObjectCommand;

      // processAddVSObjectCommand is private; no public wrapper exists — bypass is intentional
      (comp as any).processAddVSObjectCommand(cmd);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(cmd);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — ngOnDestroy() [baseline +memory-leak]
// ---------------------------------------------------------------------------

describe("VSObjectView — ngOnDestroy lifecycle", () => {
   // 🔁 Memory-leak guard: CommandProcessor subscribes to clientService.commands in the
   // constructor and must unsubscribe in cleanup() (called by ngOnDestroy) to prevent
   // the subscription from keeping the component alive after the view is destroyed.

   it("should unsubscribe from commands stream on destroy (memory-leak guard)", async () => {
      const commandsSubject = new Subject<any>();

      const result = await render(VSObjectView, {
         inputs: { model: makeModel("VSChart"), linkUri: "http://localhost" },
         providers: [
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn(), commands: commandsSubject } },
            { provide: AssemblyActionFactory,  useValue: ACTION_FACTORY_MOCK },
            { provide: MiniToolbarService,     useValue: MINI_TOOLBAR_MOCK },
            { provide: NgbModal,               useValue: MODAL_MOCK },
         ],
         importOverrides: [
            { replace: VSChart,                          with: VSChartStub },
            { replace: VSTable,                          with: VSTableStub },
            { replace: VSCrosstab,                       with: VSCrosstabStub },
            { replace: CalcTableLayoutPane,              with: CalcTableLayoutPaneStub },
            { replace: MiniToolbar,                      with: MiniToolbarStub },
            { replace: ActionsContextmenuAnchorDirective, with: ActionsContextmenuAnchorStub },
            { replace: BTableActionHandlerDirective,     with: BTableActionHandlerStub },
            { replace: BCrosstabActionHandlerDirective,  with: BCrosstabActionHandlerStub },
            { replace: BCalcTableActionHandlerDirective, with: BCalcTableActionHandlerStub },
         ],
         schemas: [NO_ERRORS_SCHEMA],
      });

      expect(commandsSubject.observed).toBe(true);

      result.fixture.destroy();

      expect(commandsSubject.observed).toBe(false);
   });
});
