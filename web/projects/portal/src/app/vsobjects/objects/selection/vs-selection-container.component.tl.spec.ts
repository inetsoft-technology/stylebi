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
 * VSSelectionContainer - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - addFilter/openAddFilterDialog: HTTP load, dialog wiring, trap callbacks,
 *                      and insert-child event dispatch
 *   Group 2 [Risk 2] - actions setter: event-id dispatch for unselect, dropdown menus,
 *                      format pane, and max-mode
 *   Group 3 [Risk 2] - mobile max-selection subscription and ngOnDestroy cleanup
 *   Group 4 [Risk 1] - title selection/update/resize outputs and hidden rendering
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   - private processAddVSObjectCommand/processRefreshVSObjectCommand/processRemoveVSObjectCommand/
 *     processRenameVSObjectCommand: command-processor paths are not part of the current
 *     single-pass viewer interaction scope.
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - heavy child component -> importOverrides stub for VSTitle
 */

import { provideHttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription } from "rxjs";

import { server } from "@test-mocks/server";
import { Tool } from "../../../../../../shared/util/tool";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { VSUtil } from "../../util/vs-util";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { VSTrapService } from "../../util/vs-trap.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { SelectionContainerChildrenService } from "./services/selection-container-children.service";
import { SelectionMobileService } from "./services/selection-mobile.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VSSelectionContainerModel } from "../../model/vs-selection-container-model";
import { VSTitle } from "../title/vs-title.component";
import { VSSelectionContainer } from "./vs-selection-container.component";

@Component({
   selector: "vs-title",
   template: "",
   standalone: true,
})
class VSTitleStub {
   @Input() titleWidth: number;
   @Input() titleVisible: boolean;
   @Input() titleSelected: boolean;
   @Input() selected: boolean;
   @Input() formatPainterMode: boolean;
   @Input() titleFormat: any;
   @Input() titleContent: string;
   @Output() titleResizeMove = new EventEmitter<any>();
   @Output() selectTitle = new EventEmitter<void>();
   @Output() changeTitle = new EventEmitter<string>();
   @Output() titleResizeEnd = new EventEmitter<void>();
}

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<any>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeModel(overrides: Partial<VSSelectionContainerModel> = {}): VSSelectionContainerModel {
   const model = TestUtils.createMockVSSelectionContainerModel("SelectionContainer1");
   model.titleVisible = true;
   model.title = "Selection Title";
   model.titleFormat = TestUtils.createMockVSFormatModel();
   model.maxMode = false;
   model.selectedRegions = [];
   model.childObjects = [];
   model.objectFormat = {
      ...TestUtils.createMockVSFormatModel(),
      width: 320,
      height: 180,
      top: 10,
      left: 20,
      zIndex: 4,
      background: "#fff",
      foreground: "#111",
      font: "12px Arial",
      roundCorner: 4,
      border: {
         top: "2px solid #111",
         left: "3px solid #222",
         right: "1px solid #333",
         bottom: "1px solid #444",
      },
   };
   return Object.assign(model, overrides);
}

function makeVsInfo(overrides: Record<string, unknown> = {}) {
   return {
      formatPainterMode: false,
      vsObjects: [],
      isAssemblyFocused: vi.fn(() => false),
      ...overrides,
   };
}

async function renderComponent(opts: {
   model?: VSSelectionContainerModel;
   viewer?: boolean;
   preview?: boolean;
   selected?: boolean;
   container?: Element;
   mobile?: boolean;
} = {}) {
   const mobileSubject = new Subject<{ obj: any; max: boolean }>();
   const viewsheetClient = {
      runtimeId: "runtime-1",
      sendEvent: vi.fn(),
      commands: new Subject<any>().asObservable(),
   };
   const selectionContainerChildrenService = {
      updateChild: vi.fn(),
      updateChildModel: vi.fn(),
   };
   const dataTipService = { isDataTip: vi.fn(() => false) };
   const dropdownService = {};
   const trapService = { checkTrap: vi.fn() };
   const selectionMobileService = {
      maxSelectionChanged: vi.fn(() => mobileSubject.asObservable()),
   };
   const context = {
      viewer: opts.viewer ?? true,
      preview: opts.preview ?? false,
      binding: false,
      embedAssembly: false,
   };

   vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(opts.mobile ?? false);

   const { fixture } = await render(VSSelectionContainer, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ViewsheetClientService, useValue: viewsheetClient },
         { provide: SelectionContainerChildrenService, useValue: selectionContainerChildrenService },
         { provide: ContextProvider, useValue: context },
         { provide: DataTipService, useValue: dataTipService },
         { provide: FixedDropdownService, useValue: dropdownService },
         { provide: VSTrapService, useValue: trapService },
         { provide: SelectionMobileService, useValue: selectionMobileService },
      ],
      componentInputs: {
         model: opts.model ?? makeModel(),
         selected: opts.selected ?? false,
         container: opts.container ?? document.createElement("div"),
         vsInfo: makeVsInfo(),
      },
      importOverrides: [
         { replace: VSTitle, with: VSTitleStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return {
      comp: fixture.componentInstance,
      fixture,
      mobileSubject,
      viewsheetClient,
      trapService,
      dropdownService,
   };
}

beforeEach(() => {
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<any>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
});

afterEach(() => vi.restoreAllMocks());

describe("VSSelectionContainer - render and title contracts", () => {
   it("should not render the container when viewer mode hides an invisible model", async () => {
      const { fixture } = await renderComponent({
         model: makeModel({ visible: false }),
         viewer: true,
      });

      expect(fixture.nativeElement.querySelector(".vs-selection-container")).toBeNull();
   });

   it("should compute top and left border widths from ngOnChanges", async () => {
      const model = makeModel();
      const { comp } = await renderComponent({ model });

      comp.ngOnChanges({
         model: {
            currentValue: model,
            previousValue: null,
            firstChange: true,
            isFirstChange: () => true,
         },
      } as any);

      expect(comp.leftBorderWidth).toBe(Tool.getMarginSize(model.objectFormat.border.left));
      expect(comp.topBorderWidth).toBe(Tool.getMarginSize(model.objectFormat.border.top));
   });

   it("should send a change-title event when updateTitle() runs outside viewer mode", async () => {
      const { comp, viewsheetClient } = await renderComponent({ viewer: false });

      comp.updateTitle("Renamed");

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/objects/changeTitle",
         expect.objectContaining({ text: "Renamed" }),
      );
   });

   it("should not send a change-title event when updateTitle() runs in viewer mode", async () => {
      const { comp, viewsheetClient } = await renderComponent({ viewer: true });

      comp.updateTitle("Renamed");

      expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
   });

   it("should leave selectedRegions unchanged when selectTitle() is blocked by the selection guard", async () => {
      const model = makeModel({ selectedRegions: [DataPathConstants.OBJECT] });
      const { comp } = await renderComponent({ model, selected: false });

      comp.selectTitle();

      expect(comp.model.selectedRegions).toEqual([DataPathConstants.OBJECT]);
   });

   it("should select the title region when selectTitle() is allowed", async () => {
      const model = makeModel({ selectedRegions: [] });
      const { comp } = await renderComponent({ model, selected: true });

      comp.selectTitle();

      expect(comp.model.selectedRegions).toEqual([DataPathConstants.TITLE]);
      expect(comp.isTitleSelected()).toBe(true);
   });

   it("should emit title resize move height", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onTitleResizeMove.subscribe(spy);

      comp.titleResizeMove({ rect: { height: 88 } });

      expect(spy).toHaveBeenCalledWith(88);
   });

   it("should emit title resize end", async () => {
      const { comp } = await renderComponent();
      const spy = vi.fn();
      comp.onTitleResizeEnd.subscribe(spy);

      comp.titleResizeEnd();

      expect(spy).toHaveBeenCalledTimes(1);
   });
});

describe("VSSelectionContainer - actions setter", () => {
   function createActions() {
      return {
         onAssemblyActionEvent: new Subject<any>(),
         showingActions: [{ id: "show" }],
         menuActions: [{ id: "menu-action" }],
         getMoreActions: vi.fn(() => [{ id: "more-action" }]),
      };
   }

   it("should unsubscribe the previous actionSubscription when actions changes", async () => {
      const { comp } = await renderComponent();
      const previous = { unsubscribe: vi.fn() };

      comp["actionSubscription"] = previous as any;
      comp.actions = createActions() as any;

      expect(previous.unsubscribe).toHaveBeenCalled();
   });

   it("should send the unselect-all event when action id is selection-container unselect-all", async () => {
      const { comp, viewsheetClient } = await renderComponent();
      const actions = createActions();

      comp.actions = actions as any;
      actions.onAssemblyActionEvent.next({ id: "selection-container unselect-all" });

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/selectionContainer/unselectAll/SelectionContainer1",
      );
   });

   it("should show dropdown menus for the menu actions branch", async () => {
      const { comp, dropdownService } = await renderComponent();
      const actions = createActions();
      const dropdownSpy = vi.spyOn(VSUtil, "showDropdownMenus").mockImplementation(() => {});

      try {
         comp.actions = actions as any;
         actions.onAssemblyActionEvent.next({ id: "menu actions", event: "menu-event" });

         expect(dropdownSpy).toHaveBeenCalledWith("menu-event", actions.menuActions, dropdownService);
      } finally {
         dropdownSpy.mockRestore();
      }
   });

   it("should emit onOpenFormatPane for the format-pane branch", async () => {
      const { comp } = await renderComponent();
      const actions = createActions();
      const spy = vi.fn();

      comp.onOpenFormatPane.subscribe(spy);
      comp.actions = actions as any;
      actions.onAssemblyActionEvent.next({ id: "selection-container show-format-pane" });

      expect(spy).toHaveBeenCalledWith(comp.model);
   });

   it("should toggle max mode for the open-max-mode branch", async () => {
      const { comp, viewsheetClient } = await renderComponent();
      const actions = createActions();
      const spy = vi.fn();

      comp.maxModeChange.subscribe(spy);
      comp.actions = actions as any;
      actions.onAssemblyActionEvent.next({ id: "selection-container open-max-mode" });

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/vs/assembly/max-mode/toggle",
         expect.any(Object),
      );
      expect(spy).toHaveBeenCalledWith({
         assembly: "SelectionContainer1",
         maxMode: true,
      });
   });

   it("should show dropdown menus for the more actions branch", async () => {
      const { comp, dropdownService } = await renderComponent();
      const actions = createActions();
      const dropdownSpy = vi.spyOn(VSUtil, "showDropdownMenus").mockImplementation(() => {});

      try {
         comp.actions = actions as any;
         actions.onAssemblyActionEvent.next({ id: "more actions", event: "more-event" });

         expect(dropdownSpy).toHaveBeenCalledWith(
            "more-event",
            [{ id: "more-action" }],
            dropdownService,
         );
      } finally {
         dropdownSpy.mockRestore();
      }
   });
});

describe("VSSelectionContainer - addFilter and dialog flow", () => {
   it("should request the add-filter tree with the current runtimeId and open the dialog", async () => {
      let requestedVsId: string | null = null;
      const targetTree = { label: "Measures", children: [] };
      const grayedOutFields = [{ name: "DisabledField" }];
      const dialogRef: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef);

      server.use(
         http.get("*/api/selectioncontainer/add-filter/tree", ({ request }) => {
            requestedVsId = new URL(request.url).searchParams.get("vsId");
            return MswHttpResponse.json({ targetTree, grayedOutFields });
         }),
      );

      try {
         const { comp, trapService, viewsheetClient } = await renderComponent();

         comp.addFilter();

         await waitFor(() => expect(showDialogSpy).toHaveBeenCalled());
         expect(requestedVsId).toBe("runtime-1");
         expect(dialogRef.model).toEqual(targetTree);
         expect(dialogRef.grayedOutFields).toEqual(grayedOutFields);
         expect(trapService.checkTrap).not.toHaveBeenCalled();
         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should not check trap or send events when the dialog result is null", async () => {
      const dialogRef: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef);

      try {
         const { comp, trapService, viewsheetClient } = await renderComponent();

         comp.openAddFilterDialog({ label: "root" } as TreeNodeModel, []);

         const onCommit = showDialogSpy.mock.calls[0][2] as (value: TreeNodeModel[] | null) => void;
         onCommit(null);

         expect(trapService.checkTrap).not.toHaveBeenCalled();
         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should check trap and send an insert-child event when the dialog commits columns", async () => {
      const dialogRef: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef);

      try {
         const model = makeModel({
            childObjects: [TestUtils.createMockVSObjectModel("VSSelectionList", "Child1")],
         });
         const { comp, trapService, viewsheetClient } = await renderComponent({ model });

         trapService.checkTrap.mockImplementation(
            (_info: unknown, continueFn?: () => void) => continueFn?.(),
         );
         comp.openAddFilterDialog({ label: "root" } as TreeNodeModel, []);

         const onCommit = showDialogSpy.mock.calls[0][2] as (value: TreeNodeModel[]) => void;
         onCommit([
            { data: { name: "ColumnA" } } as TreeNodeModel,
            { data: { name: "ColumnB" } } as TreeNodeModel,
         ]);

         expect(trapService.checkTrap).toHaveBeenCalledWith(
            expect.objectContaining({
               controllerURI: "../api/viewsheet/objects/checkSelectionTrap/runtime-1",
            }),
            expect.any(Function),
            expect.any(Function),
            expect.any(Function),
         );
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/selectionContainer/insertChild/SelectionContainer1",
            expect.objectContaining({
               toIndex: 1,
               columns: [{ name: "ColumnA" }, { name: "ColumnB" }],
            }),
         );
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should send the insert-child event through the noTrap callback when no trap is reported", async () => {
      const dialogRef: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef);

      try {
         const { comp, trapService, viewsheetClient } = await renderComponent();

         trapService.checkTrap.mockImplementation(
            (
               _info: unknown,
               _continueFn?: () => void,
               _stopFn?: () => void,
               noTrapFn?: () => void,
            ) => noTrapFn?.(),
         );
         comp.openAddFilterDialog({ label: "root" } as TreeNodeModel, []);

         const onCommit = showDialogSpy.mock.calls[0][2] as (value: TreeNodeModel[]) => void;
         onCommit([{ data: { name: "ColumnZ" } } as TreeNodeModel]);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/selectionContainer/insertChild/SelectionContainer1",
            expect.objectContaining({
               toIndex: 0,
               columns: [{ name: "ColumnZ" }],
            }),
         );
      } finally {
         showDialogSpy.mockRestore();
      }
   });
});

describe("VSSelectionContainer - lifecycle and mobile max mode", () => {
   it("should toggle max mode when the mobile max-selection event matches the current model", async () => {
      const { comp, mobileSubject, viewsheetClient } = await renderComponent({
         mobile: true,
         container: document.createElement("div"),
      });
      const spy = vi.fn();

      comp.maxModeChange.subscribe(spy);
      mobileSubject.next({
         obj: { objectType: "VSSelectionContainer", absoluteName: "SelectionContainer1" },
         max: true,
      });

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/vs/assembly/max-mode/toggle",
         expect.any(Object),
      );
      expect(spy).toHaveBeenCalledWith({
         assembly: "SelectionContainer1",
         maxMode: true,
      });
   });

   it("should unsubscribe actionSubscription and subscriptions on ngOnDestroy", async () => {
      const { comp, fixture } = await renderComponent();
      const actionSubscription = { unsubscribe: vi.fn() };
      const subscriptions = new Subscription();
      const unsubscribeSpy = vi.spyOn(subscriptions, "unsubscribe");

      comp["actionSubscription"] = actionSubscription as any;
      comp["subscriptions"] = subscriptions;

      fixture.destroy();

      expect(actionSubscription.unsubscribe).toHaveBeenCalled();
      expect(unsubscribeSpy).toHaveBeenCalled();
      expect(comp["actionSubscription"]).toBeNull();
      expect(comp["subscriptions"]).toBeNull();
   });
});
