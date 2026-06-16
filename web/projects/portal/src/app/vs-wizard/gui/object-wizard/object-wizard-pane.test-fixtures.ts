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

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { ObjectWizardPane } from "./object-wizard-pane.component";
import { ObjectWizardToolBarComponent } from "../object-wizard-tool-bar.component";
import { SplitPane } from "../../../widget/split-pane/split-pane.component";
import { WizardBindingTree } from "./wizard-binding-tree.component";
import { VSWizardAggregatePane } from "./wizard-aggregate-pane.component";
import { WizardVisualizationPane } from "./wizard-visualization-pane.component";
import { VSWizardPreviewPane } from "./wizard-preview-pane.component";
import { VSLoadingDisplay } from "../../../vsobjects/objects/vs-loading-display/vs-loading-display.component";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { VSChartService } from "../../../vsobjects/objects/chart/services/vs-chart.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { AiAssistantService } from "../../../../../../shared/ai-assistant/ai-assistant.service";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { VSWizardConstants } from "../../model/vs-wizard-constants";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Stubs
// The stubs must declare every @Input/@Output that the parent template binds.
// ---------------------------------------------------------------------------

@Component({ selector: "object-wizard-tool-bar", template: "", standalone: true })
export class ObjectWizardToolBarStub {
   @Input() runtimeId: any;
   @Input() assemblyType: any;
   @Input() isFullEditorVisible: any;
   @Input() vsObject: any;
   @Output() onFullEditor = new EventEmitter<any>();
   @Output() onClose = new EventEmitter<any>();
}

// SplitPane stub needs getSizes/setSizes/collapse for ViewChild usage
@Component({ selector: "split-pane", template: "<ng-content></ng-content>", standalone: true })
export class SplitPaneStub {
   @Input() direction: any;
   @Input() sizes: any;
   @Input() minSize: any;
   @Input() snapOffset: any;
   @Input() gutterSize: any;
   @Output() onDragEnd = new EventEmitter<any>();
   getSizes = vi.fn().mockReturnValue([55, 45]);
   setSizes = vi.fn();
   collapse = vi.fn();
}

@Component({ selector: "wizard-binding-tree", template: "", standalone: true })
export class WizardBindingTreeStub {
   @Input() runtimeId: any;
   @Input() originalMode: any;
   @Input() temporarySheet: any;
}

@Component({ selector: "wizard-aggregate-pane", template: "", standalone: true })
export class VSWizardAggregatePaneStub {
   @Input() dimensions: any;
   @Input() measures: any;
   @Input() details: any;
   @Input() isAssemblyBinding: any;
   @Input() isCube: any;
   @Input() fixedFormulaMap: any;
   @Input() isDetail: any;
   @Input() showAutoOrder: any;
   @Input() grayedOutFields: any;
   @Input() availableFields: any;
   @Input() formatMap: any;
   @Input() autoOrder: any;
   @Input() objectType: any;
   @Output() onAutoOrderChange = new EventEmitter<any>();
   @Output() onEditAggregate = new EventEmitter<any>();
   @Output() onEditSecondColumn = new EventEmitter<any>();
   @Output() onAddAggregate = new EventEmitter<any>();
   @Output() onDeleteAggregate = new EventEmitter<any>();
   @Output() onEditDimension = new EventEmitter<any>();
   @Output() onAddDimension = new EventEmitter<any>();
   @Output() onDeleteDimension = new EventEmitter<any>();
   @Output() onUpdateDetails = new EventEmitter<any>();
   @Output() onUpdateFormat = new EventEmitter<any>();
}

@Component({ selector: "wizard-visualization-pane", template: "", standalone: true })
export class WizardVisualizationPaneStub {
   @Input() model: any;
   @Output() onChangeSubtype = new EventEmitter<any>();
}

// VSWizardPreviewPane stub needs setPreviewPaneSize for ViewChild usage
@Component({ selector: "wizard-preview-pane", template: "", standalone: true })
export class VSWizardPreviewPaneStub {
   @Input() runtimeId: any;
   @Input() linkuri: any;
   @Input() formatMap: any;
   @Input() editMode: any;
   @Input() vsObject: any;
   @Input() showLegend: any;
   @Input() consoleMessages: any;
   @Output() showLegendChange = new EventEmitter<any>();
   @Output() onUpdateFormat = new EventEmitter<any>();
   @Output() onMessageChange = new EventEmitter<any>();
   setPreviewPaneSize = vi.fn();
}

@Component({ selector: "vs-loading-display", template: "", standalone: true })
export class VSLoadingDisplayStub {
   @Input() runtimeId: any;
   @Input() message: any;
   @Input() autoShowMetaButton: any;
   @Input() autoShowCancel: any;
   @Output() switchToMeta = new EventEmitter<any>();
}

// NotificationsComponent stub needs success/info for ViewChild usage
@Component({ selector: "notifications", template: "", standalone: true })
export class NotificationsStub {
   @Input() timeout: any;
   success = vi.fn();
   info = vi.fn();
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const TEMP_PREFIX = VSWizardConstants.TEMP_ASSEMBLY_PREFIX;

export function tempName(ts: number): string {
   return `${TEMP_PREFIX}${ts}`;
}

export const commandsSubject = new Subject<any>();

export const CLIENT_SERVICE_MOCK = {
   commands: commandsSubject,
   sendEvent: vi.fn(),
};

export const BINDING_TREE_MOCK = {
   recommender: vi.fn(),
   selectedNodes: [] as any[],
   treeInfo: null as any,
   getSelectedBindingNodePaths: vi.fn().mockReturnValue([]),
   checkAggTrap: vi.fn().mockReturnValue(of(false)),
   unSelectNode: vi.fn(),
   reset: vi.fn(),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const UI_CONTEXT_MOCK = {
   sheetClose: vi.fn(),
};

export const AI_ASSISTANT_MOCK = {
   aiAssistantVisible: false,
   loadCurrentUser: vi.fn(),
   setBindingContext: vi.fn(),
   setDataContext: vi.fn(),
   setDateComparisonContext: vi.fn(),
   setContextType: vi.fn(),
   setScriptContext: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared reset — call in each spec file's top-level beforeEach
// ---------------------------------------------------------------------------

export function resetMocks(): void {
   CLIENT_SERVICE_MOCK.sendEvent.mockClear();
   BINDING_TREE_MOCK.recommender.mockClear();
   BINDING_TREE_MOCK.unSelectNode.mockClear();
   BINDING_TREE_MOCK.reset.mockClear();
   BINDING_TREE_MOCK.checkAggTrap.mockReturnValue(of(false));
   BINDING_TREE_MOCK.selectedNodes = [];
   BINDING_TREE_MOCK.treeInfo = null;
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   UI_CONTEXT_MOCK.sheetClose.mockClear();
   AI_ASSISTANT_MOCK.aiAssistantVisible = false;
   AI_ASSISTANT_MOCK.loadCurrentUser.mockClear();
   AI_ASSISTANT_MOCK.setBindingContext.mockClear();
   AI_ASSISTANT_MOCK.setContextType.mockClear();
}

// ---------------------------------------------------------------------------
// Helper factories
// ---------------------------------------------------------------------------

export function makeVSObject(overrides: Partial<VSObjectModel> = {}): VSObjectModel {
   return {
      absoluteName: "Chart1",
      objectType: "VSChart",
      objectFormat: { top: 10, left: 10, width: 200, height: 150 } as any,
      interactionDisabled: false,
      ...overrides,
   } as VSObjectModel;
}

export interface RenderResult {
   comp: ObjectWizardPane;
   fixture: any;
}

export async function renderComponent(
   inputs: Partial<{ runtimeId: string; editMode: VsWizardEditModes; originalMode: VsWizardEditModes; originalObjectType: string; originalName: string; viewer: boolean }> = {}
): Promise<RenderResult> {
   const { fixture } = await render(ObjectWizardPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         runtimeId: "rt-1",
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
         originalMode: VsWizardEditModes.WIZARD_DASHBOARD,
         ...inputs,
      },
      importOverrides: [
         { replace: ObjectWizardToolBarComponent, with: ObjectWizardToolBarStub },
         { replace: SplitPane, with: SplitPaneStub },
         { replace: WizardBindingTree, with: WizardBindingTreeStub },
         { replace: VSWizardAggregatePane, with: VSWizardAggregatePaneStub },
         { replace: WizardVisualizationPane, with: WizardVisualizationPaneStub },
         { replace: VSWizardPreviewPane, with: VSWizardPreviewPaneStub },
         { replace: VSLoadingDisplay, with: VSLoadingDisplayStub },
         { replace: NotificationsComponent, with: NotificationsStub },
      ],
      componentProviders: [
         { provide: ViewsheetClientService, useValue: CLIENT_SERVICE_MOCK },
         { provide: VSWizardBindingTreeService, useValue: BINDING_TREE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ModelService, useValue: { getModel: vi.fn(), putModel: vi.fn() } },
         { provide: VSChartService, useValue: { showAllLegends: vi.fn(), hideLegend: vi.fn() } },
         { provide: UIContextService, useValue: UI_CONTEXT_MOCK },
         { provide: AiAssistantService, useValue: AI_ASSISTANT_MOCK },
      ],
   });
   return { comp: fixture.componentInstance as ObjectWizardPane, fixture };
}
