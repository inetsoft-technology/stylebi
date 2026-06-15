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
 * VsWizardComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — ngOnInit case3 (finish): currentPane=WIZARD_PANE + RefreshVSWizardEvent
 *   Group 2  [Risk 3] — ngOnInit else (no runtimeId): OpenVsWizardEvent + currentPane=WIZARD_PANE
 *   Group 3  [Risk 3] — createNewRuntimeViewsheet HTTP (+竞态): loadingEventCount always decremented
 *   Group 4  [Risk 3] — close(save=false): emits onCommit {save:false} — NOT onCancel
 *   Group 5  [Risk 3] — processExpiredSheetCommand: dedup guard + onCancel on confirm
 *   Group 6  [Risk 2] — close(save=true, FULL_EDITOR/VIEWSHEET_PANE): editMode normalized
 *   Group 7  [Risk 2] — closeObjectWizard WIZARD_DASHBOARD branch: back to WIZARD_PANE
 *   Group 8  [Risk 2] — processSaveSheetCommand: model.assetId updated + onCommit emitted
 *   Group 9  [baseline] — cancel(): sendEvent(CLOSE_VIEWSHEET_URI) / onCancel emitted (split)
 *   Group 10 [baseline] — goToFullEditor(): onFullEditor emitted with cloned model
 *   Group 11 [baseline] — showLoading(): all three || guards (no runtimeId / objectWizardLoading / loadingEventCount)
 *   Group 12 [baseline] — hiddenNewBlockChanged() / changeCurrentObject()
 *   Group 13 [baseline] — processOpenObjectWizardCommand: currentPane / objectWizardLoading (split)
 *   Group 14 [baseline] — processShowLoadingMaskCommand: preparingData + loadingEventCount
 *   Group 15 [baseline] — goToComponentWizard: objectWizardLoading=true / sendEvent (split)
 *   Group 16 [baseline, +内存泄漏] — ngOnDestroy: heartbeat subscription cleaned up
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — close(save=false) emits onCommit {save:false} rather than onCancel.
 *     This is architecturally surprising (callers must check event.save to distinguish cancel from
 *     save), but appears intentional based on the existing code structure across all callers.
 *
 * Out of scope:
 *   getAssemblyName() — always returns null; zero test value
 *   switchToMeta() — entire implementation is commented out
 *   model/objectName/objectType getters — trivial accessors, covered transitively
 *   processSetViewsheetInfoCommand — simple field assignments, no risk
 *   processCollectParametersCommand / enterParameters — requires deep VariableInputDialog modal
 *     wiring beyond single-pass scope; no prescan risk flag
 *   ngOnInit case1 full success path — requires STOMP command chain
 *     (SetRuntimeIdCommand → updateRuntimeId → goToComponentWizard); error path covered in Group 3
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, NEVER, Subject, Subscription } from "rxjs";

import { server } from "@test-mocks/server";
import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { VsWizardComponent, WizardPanes } from "./vs-wizard.component";
import { VsWizardPane } from "./wizard-pane/vs-wizard-pane.component";
import { ObjectWizardPane } from "./object-wizard/object-wizard-pane.component";
import { VSLoadingDisplay } from "../../vsobjects/objects/vs-loading-display/vs-loading-display.component";
import { VsWizardModel } from "../model/vs-wizard-model";
import { VsWizardEditModes } from "../model/vs-wizard-edit-modes";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { VSWizardBindingTreeService } from "../services/vs-wizard-binding-tree.service";
import { BindingTreeService } from "../../binding/widget/binding-tree/binding-tree.service";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { ChartService } from "../../graph/services/chart.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { DndService } from "../../common/dnd/dnd.service";
import { OpenObjectWizardCommand } from "../model/command/open-object-wizard-command";
import { SaveSheetCommand } from "../../composer/gui/ws/socket/save-sheet-command";
import { ClearLoadingCommand } from "../../vsobjects/command/clear-loading-command";
import { ShowLoadingMaskCommand } from "../../vsobjects/command/show-loading-mask-command";
import { ExpiredSheetCommand } from "../../composer/gui/ws/socket/expired-sheet/expired-sheet-command";

// ---------------------------------------------------------------------------
// Child component stubs
// VsWizardPane, ObjectWizardPane, and VSLoadingDisplay are real imported components
// (in VsWizardComponent.imports[]). When currentPane=WIZARD_PANE or OBJECT_WIZARD_PANE
// they are rendered and Angular tries to instantiate them with their full DI trees.
// Stubs cut those chains without affecting the component-logic tests here.
// ---------------------------------------------------------------------------

@Component({ selector: "vs-wizard-pane", template: "" })
class VsWizardPaneStub {
   @Input() runtimeId: any;
   @Input() linkUri: any;
   @Input() componentWizardEnable: any;
   @Input() currentVSObject: any;
   @Input() editMode: any;
   @Input() hiddenNewBlock: any;
   @Output() onHiddenNewBlockChanged = new EventEmitter<any>();
   @Output() toComponentWizard = new EventEmitter<any>();
   @Output() onChangeCurrentObject = new EventEmitter<any>();
   @Output() onFinish = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<any>();
}

@Component({ selector: "object-wizard-pane", template: "" })
class ObjectWizardPaneStub {
   @Input() editMode: any;
   @Input() originalMode: any;
   @Input() linkuri: any;
   @Input() runtimeId: any;
   @Input() isNewViewsheetWizard: any;
   @Input() temporarySheet: any;
   @Input() originalObjectType: any;
   @Input() originalName: any;
   @Input() oldOriginalName: any;
   @Input() viewer: any;
   @Input() aiAssistantPermission: any;
   @Output() onClose = new EventEmitter<any>();
   @Output() onFullEditor = new EventEmitter<any>();
   @Output() onSwitchMeta = new EventEmitter<any>();
}

@Component({ selector: "vs-loading-display", template: "" })
class VSLoadingDisplayStub {
   @Input() preparingData: any;
}

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const heartbeatSubject = new Subject<void>();

const CLIENT_SERVICE_MOCK = {
   runtimeId: undefined as string | undefined,
   commands: NEVER,
   connect: vi.fn(),
   whenConnected: vi.fn().mockReturnValue(EMPTY),
   connectRefresh: vi.fn().mockReturnValue(new Subscription()),
   onHeartbeat: heartbeatSubject,
   sendEvent: vi.fn(),
};

const DIALOG_SERVICE_MOCK = {
   setSheetId: vi.fn(),
   ngOnDestroy: vi.fn(),
};

// ComponentTool.showConfirmDialog calls modal.open() and returns modal.result as a Promise.
// Default: never-resolving Promise keeps the dialog "open" for the duration of each test.
const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

beforeEach(() => {
   CLIENT_SERVICE_MOCK.runtimeId = undefined;
   CLIENT_SERVICE_MOCK.connect.mockClear();
   CLIENT_SERVICE_MOCK.sendEvent.mockClear();
   CLIENT_SERVICE_MOCK.whenConnected.mockClear().mockReturnValue(EMPTY);
   CLIENT_SERVICE_MOCK.connectRefresh.mockClear().mockReturnValue(new Subscription());
   DIALOG_SERVICE_MOCK.setSheetId.mockClear();
   DIALOG_SERVICE_MOCK.ngOnDestroy.mockClear();
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});

// Default model triggers ngOnInit case4/5 (sets runtimeId on client, no HTTP, no WS events):
//   - runtimeId is set → case1's else/else if/else is reached
//   - oinfo.editMode = WIZARD_DASHBOARD → case1 excluded (editMode != WIZARD_DASHBOARD fails)
//   - bindingOption undefined → case3 excluded (bindingOption != "finish")
//   - no objectModel → showComponentWizard not called
function makeModel(overrides: Partial<VsWizardModel> = {}): VsWizardModel {
   return {
      runtimeId: "rt-default",
      oinfo: {
         runtimeId: "rt-default",
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
      },
      ...overrides,
   };
}

interface RenderOptions {
   model?: VsWizardModel;
}

async function renderComponent(opts: RenderOptions = {}) {
   const { fixture } = await render(VsWizardComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [provideHttpClient()],
      importOverrides: [
         { replace: VsWizardPane, with: VsWizardPaneStub },
         { replace: ObjectWizardPane, with: ObjectWizardPaneStub },
         { replace: VSLoadingDisplay, with: VSLoadingDisplayStub },
      ],
      componentProviders: [
         { provide: ViewsheetClientService, useValue: CLIENT_SERVICE_MOCK },
         { provide: DialogService, useValue: DIALOG_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: AdhocFilterService, useValue: {} },
         { provide: DataTipService, useValue: {} },
         { provide: VSWizardBindingTreeService, useValue: {} },
         { provide: BindingTreeService, useValue: {} },
         { provide: VSChartService, useValue: {} },
         { provide: ChartService, useValue: {} },
         { provide: PopComponentService, useValue: {} },
         { provide: ScaleService, useValue: {} },
         { provide: DndService, useValue: {} },
      ],
      componentInputs: {
         model: opts.model ?? makeModel(),
      },
   });
   return { comp: fixture.componentInstance as VsWizardComponent, fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit case3 (finish): currentPane + RefreshVSWizardEvent [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — ngOnInit case3 (bindingOption=finish)", () => {
   // 🔁 Regression-sensitive: when the user finishes editing in the full editor and returns
   // to the wizard dashboard, the wizard pane must reload. Breaking this silently shows the
   // user a stale / empty pane.
   function makeFinishModel(): VsWizardModel {
      // oinfo.editMode = WIZARD_DASHBOARD → case1 excluded; bindingOption=finish → case3 runs
      return makeModel({
         runtimeId: "rt-finish",
         oinfo: { runtimeId: "rt-finish", editMode: VsWizardEditModes.WIZARD_DASHBOARD },
         bindingOption: "finish",
      });
   }

   it("should set currentPane to WIZARD_PANE when bindingOption=finish", async () => {
      const { comp } = await renderComponent({ model: makeFinishModel() });

      expect(comp.currentPane).toBe(WizardPanes.WIZARD_PANE);
   });

   it("should send RefreshVSWizardEvent when bindingOption=finish", async () => {
      await renderComponent({ model: makeFinishModel() });

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/wizard-pane/refresh",
         expect.objectContaining({ runtimeId: "rt-finish" })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit else (no runtimeId): OpenVsWizardEvent [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — ngOnInit else (new viewsheet, no runtimeId)", () => {
   // 🔁 Regression-sensitive: when a user creates a brand-new viewsheet, the wizard must be
   // opened via the WS event. If this is broken the wizard dialog shows a blank/stuck state.
   function makeNewVsModel(): VsWizardModel {
      return {
         runtimeId: undefined,
         oinfo: { runtimeId: null, editMode: VsWizardEditModes.VIEWSHEET_PANE },
      };
   }

   it("should set currentPane to WIZARD_PANE when no runtimeId", async () => {
      const { comp } = await renderComponent({ model: makeNewVsModel() });

      expect(comp.currentPane).toBe(WizardPanes.WIZARD_PANE);
   });

   it("should send OpenVsWizardEvent to the wizard open endpoint when no runtimeId", async () => {
      await renderComponent({ model: makeNewVsModel() });

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/dialog/open",
         expect.any(Object)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3 — createNewRuntimeViewsheet HTTP (+竞态) [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — createNewRuntimeViewsheet HTTP race condition", () => {
   // 🔁 Regression-sensitive: loadingEventCount must be decremented even when the HTTP call
   // fails. If the error handler omits the decrement, showLoading() is permanently true and
   // the UI is stuck in a loading spinner with no recovery path.
   function makeCase1Model(): VsWizardModel {
      // oinfo.editMode = VIEWSHEET_PANE (truthy, != WIZARD_DASHBOARD) and no bindingOption
      // → case1 condition is true → createNewRuntimeViewsheet() called
      return makeModel({
         runtimeId: "rt-original",
         oinfo: { runtimeId: "rt-original", editMode: VsWizardEditModes.VIEWSHEET_PANE },
      });
   }

   it("should clear loading state (showLoading=false) after HTTP success", async () => {
      server.use(
         http.get("*/api/vswizard/dialog/open", () => HttpResponse.json("rt-new"))
      );
      const { comp } = await renderComponent({ model: makeCase1Model() });

      // loadingEventCount was incremented before HTTP and decremented after success
      await waitFor(() => expect(comp.showLoading()).toBe(false));
   });

   it("should clear loading state (showLoading=false) after HTTP error (+竞态)", async () => {
      server.use(
         http.get("*/api/vswizard/dialog/open", () => HttpResponse.error())
      );
      const { comp } = await renderComponent({ model: makeCase1Model() });

      // loadingEventCount must be decremented in the error handler too
      await waitFor(() => expect(comp.showLoading()).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — close(save=false): emits onCommit {save:false} [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — close(save=false): onCommit contract", () => {
   // 🔁 Regression-sensitive: close(save=false) must emit onCommit (not onCancel) with
   // save=false. Callers check event.save to detect the cancel path.
   // Changing this to emit onCancel breaks all callers silently.

   it("should emit onCommit with save=false when close(false) is called", async () => {
      const { comp } = await renderComponent();
      const committed: any[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      comp.close(false, VsWizardEditModes.VIEWSHEET_PANE);

      expect(committed).toHaveLength(1);
      expect(committed[0].save).toBe(false);
   });

   it("should NOT emit onCancel when close(save=false) is called", async () => {
      const { comp } = await renderComponent();
      const cancelled: any[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      comp.close(false, VsWizardEditModes.VIEWSHEET_PANE);

      expect(cancelled).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processExpiredSheetCommand: dedup guard [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — processExpiredSheetCommand dedup guard", () => {
   // 🔁 Regression-sensitive: the confirmExpiredDisplayed flag prevents duplicate expired-session
   // dialogs if the server sends multiple commands in rapid succession.

   it("should open the confirm dialog on the first processExpiredSheetCommand", async () => {
      const { comp } = await renderComponent();

      // Bypass: processExpiredSheetCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processExpiredSheetCommand"]({ message: "wizard expired" } as ExpiredSheetCommand);

      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });

   it("should ignore duplicate processExpiredSheetCommand and return null", async () => {
      const { comp } = await renderComponent();
      const command: ExpiredSheetCommand = { message: "wizard expired" };

      // Bypass: processExpiredSheetCommand is a private STOMP handler — called directly to test dedup behavior.
      (comp as any)["processExpiredSheetCommand"](command); // sets confirmExpiredDisplayed = true
      const result = (comp as any)["processExpiredSheetCommand"](command); // should be deduped

      expect(result).toBeNull();
      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1); // only the first call opened a dialog
   });

   it("should emit onCancel after user confirms the expired-session dialog", async () => {
      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      const { comp } = await renderComponent();
      const cancelled: any[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      // Bypass: processExpiredSheetCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processExpiredSheetCommand"]({ message: "expired" } as ExpiredSheetCommand);

      await waitFor(() => expect(cancelled).toHaveLength(1));
      expect(cancelled[0]).toMatchObject({ save: false });
   });
});

// ---------------------------------------------------------------------------
// Group 6 — close(save=true): editMode normalization [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — close(save=true) editMode normalization", () => {
   // 🔁 Regression-sensitive: when the original editMode was FULL_EDITOR or VIEWSHEET_PANE,
   // close() must override the passed editMode and set it to VIEWSHEET_PANE on both
   // model.editMode and model.oinfo.editMode. Omitting this sends the user to the wrong pane.

   function makeFullEditorModel(): VsWizardModel {
      // bindingOption=cancel → skips case1 (avoids HTTP call in ngOnInit)
      return makeModel({
         runtimeId: "rt-full",
         oinfo: { runtimeId: "rt-full", editMode: VsWizardEditModes.FULL_EDITOR },
         bindingOption: "cancel",
      });
   }

   it("should normalize model.editMode to VIEWSHEET_PANE when oinfo.editMode is FULL_EDITOR", async () => {
      const model = makeFullEditorModel();
      const { comp } = await renderComponent({ model });

      comp.close(true, VsWizardEditModes.FULL_EDITOR);

      expect(comp.model.editMode).toBe(VsWizardEditModes.VIEWSHEET_PANE);
   });

   it("should normalize model.oinfo.editMode to VIEWSHEET_PANE when oinfo.editMode is FULL_EDITOR", async () => {
      const model = makeFullEditorModel();
      const { comp } = await renderComponent({ model });

      comp.close(true, VsWizardEditModes.FULL_EDITOR);

      expect(comp.model.oinfo.editMode).toBe(VsWizardEditModes.VIEWSHEET_PANE);
   });

   it("should send VIEWSHEET_WIZARD_CLOSE_URI event when close(save=true) is called", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.close(true, VsWizardEditModes.WIZARD_DASHBOARD);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/dialog/close",
         expect.any(Object)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 7 — closeObjectWizard WIZARD_DASHBOARD branch [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — closeObjectWizard WIZARD_DASHBOARD branch", () => {
   // 🔁 Regression-sensitive: when the user finishes in the object wizard on a wizard-dashboard
   // viewsheet, closing the object wizard must return to the wizard pane (not close the whole
   // wizard). Breaking this collapses the entire wizard flow prematurely.

   it("should set currentPane to WIZARD_PANE when closeObjectWizard called on WIZARD_DASHBOARD", async () => {
      // Default model has oinfo.editMode = WIZARD_DASHBOARD → isNewViewsheetWizard = true
      const { comp } = await renderComponent();

      comp.closeObjectWizard({ currentObject: null, save: true, editMode: VsWizardEditModes.WIZARD_DASHBOARD });

      expect(comp.currentPane).toBe(WizardPanes.WIZARD_PANE);
   });

   it("should send RefreshVSWizardEvent when closeObjectWizard called on WIZARD_DASHBOARD", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.closeObjectWizard({ currentObject: null, save: true, editMode: VsWizardEditModes.WIZARD_DASHBOARD });

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/wizard-pane/refresh",
         expect.any(Object)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 8 — processSaveSheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — processSaveSheetCommand", () => {
   // 🔁 Regression-sensitive: processSaveSheetCommand is the server-side confirmation that the
   // wizard state was saved. It must update model.assetId AND emit onCommit {save:true}.
   // Missing either breaks the parent's ability to navigate away with the correct asset ID.

   it("should update model.assetId when processSaveSheetCommand arrives", async () => {
      const { comp } = await renderComponent();
      const command: SaveSheetCommand = { id: "new-asset-id", savePoint: 1 };

      // Bypass: processSaveSheetCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processSaveSheetCommand"](command);

      expect(comp.model.assetId).toBe("new-asset-id");
   });

   it("should emit onCommit with save=true when processSaveSheetCommand arrives", async () => {
      const { comp } = await renderComponent();
      const committed: any[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      // Bypass: processSaveSheetCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processSaveSheetCommand"]({ id: "asset-id", savePoint: 1 } as SaveSheetCommand);

      expect(committed).toHaveLength(1);
      expect(committed[0].save).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — cancel() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — cancel()", () => {
   it("should send CLOSE_VIEWSHEET_URI when cancel() is called", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.cancel();

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/close",
         expect.any(Object)
      );
   });

   it("should emit onCancel with save=false when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const cancelled: any[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      comp.cancel();

      expect(cancelled).toHaveLength(1);
      expect(cancelled[0]).toMatchObject({ save: false });
   });
});

// ---------------------------------------------------------------------------
// Group 10 — goToFullEditor() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — goToFullEditor()", () => {
   it("should emit onFullEditor with a cloned model containing the new editMode", async () => {
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.onFullEditor.subscribe(v => emitted.push(v));

      comp.goToFullEditor({ editMode: VsWizardEditModes.FULL_EDITOR, objectModel: null });

      expect(emitted).toHaveLength(1);
      expect(emitted[0].model.editMode).toBe(VsWizardEditModes.FULL_EDITOR);
      // Must be a clone, not the same reference
      expect(emitted[0].model).not.toBe(comp.model);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — showLoading() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — showLoading()", () => {
   it("should return true when model has no runtimeId", async () => {
      const { comp } = await renderComponent({
         model: makeModel({ runtimeId: undefined }),
      });

      expect(comp.showLoading()).toBe(true);
   });

   it("should return false when runtimeId is set and no active loading", async () => {
      const { comp } = await renderComponent();
      // Default model has runtimeId='rt-default', loadingEventCount=0, objectWizardLoading=false

      expect(comp.showLoading()).toBe(false);
   });

   it("should return true when objectWizardLoading is true", async () => {
      const { comp } = await renderComponent();
      comp.objectWizardLoading = true;

      expect(comp.showLoading()).toBe(true);
   });

   it("should return true when a loading event is in progress (loadingEventCount > 0)", async () => {
      const { comp } = await renderComponent();
      // Bypass: processShowLoadingMaskCommand is a private STOMP handler — called directly to increment loadingEventCount.
      (comp as any)["processShowLoadingMaskCommand"]({ preparingData: false } as ShowLoadingMaskCommand);

      expect(comp.showLoading()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — hiddenNewBlockChanged() / changeCurrentObject() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — hiddenNewBlockChanged() / changeCurrentObject()", () => {
   it("should update model.hiddenNewBlock when hiddenNewBlockChanged() is called", async () => {
      const { comp } = await renderComponent();

      comp.hiddenNewBlockChanged(true);

      expect(comp.model.hiddenNewBlock).toBe(true);
   });

   it("should update model.objectModel when changeCurrentObject() is called", async () => {
      const { comp } = await renderComponent();
      const newObj: any = { absoluteName: "Table1", objectType: "VSTable" };

      comp.changeCurrentObject(newObj);

      expect(comp.model.objectModel).toBe(newObj);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — processOpenObjectWizardCommand [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — processOpenObjectWizardCommand", () => {
   it("should set currentPane to OBJECT_WIZARD_PANE when processOpenObjectWizardCommand arrives with open=true", async () => {
      const { comp } = await renderComponent();
      comp.objectWizardLoading = true;

      // Bypass: processOpenObjectWizardCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processOpenObjectWizardCommand"]({ open: true } as OpenObjectWizardCommand);

      expect(comp.currentPane).toBe(WizardPanes.OBJECT_WIZARD_PANE);
   });

   it("should clear objectWizardLoading when processOpenObjectWizardCommand arrives with open=true", async () => {
      const { comp } = await renderComponent();
      comp.objectWizardLoading = true;

      // Bypass: processOpenObjectWizardCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processOpenObjectWizardCommand"]({ open: true } as OpenObjectWizardCommand);

      expect(comp.objectWizardLoading).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — processShowLoadingMaskCommand [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — processShowLoadingMaskCommand", () => {
   it("should set preparingData=true and not increment loadingEventCount when preparingData=true", async () => {
      const { comp } = await renderComponent();
      // Bypass: loadingEventCount is a private field with no public reader — read directly to verify count.
      const before = (comp as any)["loadingEventCount"];

      // Bypass: processShowLoadingMaskCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processShowLoadingMaskCommand"]({ preparingData: true } as ShowLoadingMaskCommand);

      expect(comp.preparingData).toBe(true);
      expect((comp as any)["loadingEventCount"]).toBe(before); // not incremented
   });

   it("should increment loadingEventCount when preparingData=false", async () => {
      const { comp } = await renderComponent();
      // Bypass: loadingEventCount is a private field with no public reader — read directly to verify count.
      const before = (comp as any)["loadingEventCount"];

      // Bypass: processShowLoadingMaskCommand is a private STOMP handler — called directly to simulate server push.
      (comp as any)["processShowLoadingMaskCommand"]({ preparingData: false } as ShowLoadingMaskCommand);

      expect((comp as any)["loadingEventCount"]).toBe(before + 1);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — goToComponentWizard [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — goToComponentWizard()", () => {
   it("should set objectWizardLoading=true when goToComponentWizard() is called", async () => {
      const { comp } = await renderComponent();

      comp.goToComponentWizard({ objectType: "VSChart", objectName: "Chart1" }, true);

      expect(comp.objectWizardLoading).toBe(true);
   });

   it("should send OPEN_OBJECT_WIZARD_URI event when goToComponentWizard() is called", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.goToComponentWizard({ objectType: "VSChart", objectName: "Chart1" }, true);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vswizard/object/open",
         expect.any(Object)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 16 — ngOnDestroy subscription cleanup (+内存泄漏) [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardComponent — ngOnDestroy subscription cleanup", () => {
   // 🔁 Regression-sensitive: the heartbeat subscription must be cleaned up on destroy.
   // If not, the destroyed component's touchAsset() still fires on every heartbeat, which
   // can write to a detached component and interfere with new components in the same session.
   it("should stop forwarding heartbeat events to sendEvent after ngOnDestroy (+内存泄漏)", async () => {
      const { comp, fixture } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      // Subscription is active: heartbeat fires → touchAsset() → sendEvent called
      heartbeatSubject.next();
      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledTimes(1);

      CLIENT_SERVICE_MOCK.sendEvent.mockClear();
      fixture.destroy(); // triggers ngOnDestroy → subscriptions.unsubscribe()

      // After destroy, heartbeat must not reach the dead component
      heartbeatSubject.next();
      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });
});
