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
 * Shared test fixtures for VSBindingPane spec files.
 * Imported by VSBindingPane.interaction.tl.spec.ts, VSBindingPane.risk.tl.spec.ts,
 * and VSBindingPane.display.tl.spec.ts.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { EMPTY, NEVER, Subject, Subscription, of } from "rxjs";

import { MessageDialog } from "../../widget/dialog/message-dialog/message-dialog.component";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { ModelService } from "../../widget/services/model.service";
import { BindingService } from "../../binding/services/binding.service";
import { BindingTreeService } from "../../binding/widget/binding-tree/binding-tree.service";
import { ChartEditorService } from "../../binding/services/chart/chart-editor.service";
import { TableEditorService } from "../../binding/services/table/table-editor.service";
import { VSCalcTableEditorService } from "../../binding/services/table/vs-calc-table-editor.service";
import { DndService } from "../../common/dnd/dnd.service";
import { ChartService } from "../../graph/services/chart.service";
import { AssemblyActionFactory } from "../../vsobjects/action/assembly-action-factory.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { BindingEditor } from "../../binding/editor/binding-editor.component";
import { VSObjectView } from "../view/vs-object-view.component";
import { VSFormatsPane } from "../../vsobjects/format/vs-formats-pane.component";

import { VSBindingPane } from "./vs-binding-pane.component";

// ---------------------------------------------------------------------------
// Stub components to cut deep DI chains from VSBindingPane's imports array.
// BindingEditor, VSObjectView, and VSFormatsPane each pull in large component
// trees with many service dependencies. Using stubs avoids cascading NG0201
// errors while still allowing all component-logic tests to pass.
// ---------------------------------------------------------------------------

@Component({ selector: "binding-editor", template: "" })
class BindingEditorStub {
   @Input() linkUri: any;
   @Input() formatPaneDisabled: any;
   @Input() runtimeId: any;
   @Input() assetId: any;
   @Input() assemblyName: any;
   @Input() objectType: any;
   @Input() variableValues: any;
   @Input() bindingModel: any;
   @Input() objectModel: any;
   @Input() currentFormat: any;
   @Input() grayedOutFields: any;
   @Input() sourceName: any;
   @Input() goToWizardVisible: any;
   @Input() consoleMessages: any;
   @Output() onUpdateData = new EventEmitter<any>();
   @Output() onMessageChange = new EventEmitter<any>();
   @Output() onClose = new EventEmitter<any>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
   @Output() resizePane = new EventEmitter<any>();
   // Template-reference properties accessed via #bindingEditor in VSBindingPane template.
   formatsPane: any = null;
   formatsDisabled: any = false;
   formatsInactive: any = false;
   updateData(_evt: any): void {}
   updateChartMaxMode(_m: any): void {}
}

@Component({ selector: "vs-object-view", template: "" })
class VSObjectViewStub {
   @Input() model: any;
   @Input() linkUri: any;
   @Input() variableValues: any;
   @Output() onUpdateData = new EventEmitter<any>();
   @Output() onAddVSObjectCommand = new EventEmitter<any>();
   @Output() onRefreshVSObjectCommand = new EventEmitter<any>();
   @Output() chartMaxModeChange = new EventEmitter<any>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
   @Output() onOpenFormatPane = new EventEmitter<any>();
   @Output() onPopupNotifications = new EventEmitter<any>();
}

@Component({ selector: "vs-formats-pane", template: "" })
class VSFormatsPaneStub {
   @Input() vsId: any;
   @Input() format: any;
   @Input() formatPaneDisabled: any;
   @Input() inactive: any;
   @Input() focusedAssemblies: any;
   @Input() variableValues: any;
   @Output() onPresenterPropertiesChange = new EventEmitter<any>();
   @Output() onChangeFormat = new EventEmitter<any>();
}
import { BindingPaneData } from "../model/binding-pane-data";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";

// ViewsheetClientService mock — no-op for all connection/send operations.
// commands must be Observable (CommandProcessor subscribes to it in its constructor).
export const CLIENT_SERVICE_MOCK = {
   runtimeId: undefined as string | undefined,
   commands: NEVER,
   connect: vi.fn(),
   whenConnected: vi.fn().mockReturnValue(EMPTY),
   connectRefresh: vi.fn().mockReturnValue(new Subscription()),
   onHeartbeat: NEVER,
   sendEvent: vi.fn(),
};

// ModelService mock — returns minimal BindingPaneData for the open endpoint called in ngOnInit.
export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of({ runtimeId: "rt-test", useMeta: true } as BindingPaneData)),
   errorHandler: null as any,
};

// AiAssistantService mock — all methods are no-ops; loadCurrentUser() is called in the constructor.
export const AI_MOCK = {
   aiAssistantVisible: false,
   loadCurrentUser: vi.fn(),
   setContextType: vi.fn(),
   setDateComparisonContext: vi.fn(),
   setScriptContext: vi.fn(),
   setBindingContext: vi.fn(),
   setDataContext: vi.fn(),
};

// BindingEditor (in VSBindingPane.imports) reads and writes these properties / methods on BindingService.
export const BINDING_SERVICE_MOCK = {
   setClientService: vi.fn(),
   setGrayedOutFields: vi.fn(),
   clear: vi.fn(),
   assemblyName: null as string | null,
   runtimeId: null as string | null,
   objectType: null as string | null,
   bindingModel: null as any,
   variableValues: null as any,
};
export const TREE_SERVICE_MOCK    = { changeLoadingState: vi.fn(), resetTreeModel: vi.fn() };
export const DIALOG_SERVICE_MOCK  = { ngOnDestroy: vi.fn() };
// VSObjectView (in VSBindingPane.imports) calls actionFactory.createActions(model) in its model setter.
export const ACTION_FACTORY_MOCK  = { stateProvider: null as any, createActions: vi.fn().mockReturnValue({}) };

// VSCalcTableEditorService mock — getTableLayout returns null by default; override per test.
export const CALC_TABLE_MOCK = {
   getTableLayout: vi.fn().mockReturnValue(null),
};

// ChartEditorService mock — measureName/textFormat read by getCurrentFormat / isAggregateTextFormat.
export const CHART_EDITOR_MOCK = {
   measureName: null as string | null,
   textFormat: false,
};

// NgbModal mock — each call returns a fresh ref with a pending Promise as result.
// ComponentTool.showMessageDialog returns modal.result, and callers call .then() on it,
// so result MUST be a Promise (not EMPTY Observable).
// Using a never-resolving Promise matches dialogs that stay open for the duration of a test.
export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export function resetMocks(): void {
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   CLIENT_SERVICE_MOCK.connect.mockClear();
   CLIENT_SERVICE_MOCK.sendEvent.mockClear();
   CLIENT_SERVICE_MOCK.whenConnected.mockClear().mockReturnValue(EMPTY);
   CLIENT_SERVICE_MOCK.connectRefresh.mockClear().mockReturnValue(new Subscription());
   MODEL_SERVICE_MOCK.getModel.mockClear()
      .mockReturnValue(of({ runtimeId: "rt-test", useMeta: true } as BindingPaneData));
   AI_MOCK.loadCurrentUser.mockClear();
   AI_MOCK.setContextType.mockClear();
   AI_MOCK.setDateComparisonContext.mockClear();
   AI_MOCK.setScriptContext.mockClear();
   AI_MOCK.setBindingContext.mockClear();
   AI_MOCK.setDataContext.mockClear();
   BINDING_SERVICE_MOCK.setClientService.mockClear();
   BINDING_SERVICE_MOCK.setGrayedOutFields.mockClear();
   BINDING_SERVICE_MOCK.clear.mockClear();
   BINDING_SERVICE_MOCK.assemblyName = null;
   BINDING_SERVICE_MOCK.runtimeId = null;
   BINDING_SERVICE_MOCK.objectType = null;
   BINDING_SERVICE_MOCK.bindingModel = null;
   BINDING_SERVICE_MOCK.variableValues = null;
   TREE_SERVICE_MOCK.changeLoadingState.mockClear();
   TREE_SERVICE_MOCK.resetTreeModel.mockClear();
   DIALOG_SERVICE_MOCK.ngOnDestroy.mockClear();
   ACTION_FACTORY_MOCK.createActions.mockClear().mockReturnValue({});
   CALC_TABLE_MOCK.getTableLayout.mockClear().mockReturnValue(null);
   CHART_EDITOR_MOCK.measureName = null;
   CHART_EDITOR_MOCK.textFormat = false;
   MODAL_MOCK.open.mockClear();
}

export interface RenderOptions {
   assemblyName?: string;
   objectType?: string;
   isCube?: boolean;
   wizardOriginalInfo?: any;
}

export async function renderComponent(opts: RenderOptions = {}) {
   const { fixture } = await render(VSBindingPane, {
      schemas: [NO_ERRORS_SCHEMA],
      // Replace deep-dependency components with stubs so no cascading DI errors occur when
      // objectModel is set and detectChanges() re-renders the template.
      importOverrides: [
         { replace: BindingEditor, with: BindingEditorStub },
         { replace: VSObjectView, with: VSObjectViewStub },
         { replace: VSFormatsPane, with: VSFormatsPaneStub },
      ],
      providers: [
         // AiAssistantService is providedIn: 'root', not in component providers — mock at module level.
         { provide: AiAssistantService, useValue: AI_MOCK },
      ],
      componentProviders: [
         // Override all component-level providers injected in VSBindingPane's constructor
         // or accessed via this.injector.get() in the component body.
         { provide: ViewsheetClientService, useValue: CLIENT_SERVICE_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: BindingService, useValue: BINDING_SERVICE_MOCK },
         { provide: BindingTreeService, useValue: TREE_SERVICE_MOCK },
         { provide: ChartEditorService, useValue: CHART_EDITOR_MOCK },
         { provide: TableEditorService, useValue: {} },
         { provide: DndService, useValue: {} },
         { provide: VSCalcTableEditorService, useValue: CALC_TABLE_MOCK },
         { provide: ChartService, useValue: {} },
         { provide: DialogService, useValue: DIALOG_SERVICE_MOCK },
         { provide: AssemblyActionFactory, useValue: ACTION_FACTORY_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ScaleService, useValue: {} },
         { provide: ContextProvider, useValue: {} },
         // Kept in case any non-stubbed component in the import chain still needs MiniToolbarService.
         { provide: MiniToolbarService, useValue: {} },
      ],
      componentInputs: {
         runtimeId: "vs-test",
         assemblyName: opts.assemblyName ?? "Chart1",
         objectType: opts.objectType ?? "VSChart",
         isCube: opts.isCube ?? false,
         wizardOriginalInfo: opts.wizardOriginalInfo ?? null,
         viewer: false,
         variableValues: [],
         linkUri: "../",
         temporarySheet: false,
         calculatedFieldEnabled: false,
      },
   });
   return { comp: fixture.componentInstance as VSBindingPane, fixture };
}
