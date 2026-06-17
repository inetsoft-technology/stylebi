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

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { VsWizardPane } from "./vs-wizard-pane.component";
import { WizardToolBarComponent } from "../wizard-tool-bar/wizard-tool-bar.component";
import { VsWizardGridPaneComponent } from "./vs-wizard-grid-pane.component";
import { WizardNewObject } from "../objects/wizard-new-object.component";
import { VsWizardObjectComponent } from "../objects/vs-wizard-object.component";
import { SelectionBoxDirective } from "../../../widget/directive/selection-box.directive";
import { InteractContainerDirective } from "../../../widget/interact/interact-container.directive";
import { OutOfZoneDirective } from "../../../widget/directive/out-of-zone.directive";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ModelService } from "../../../widget/services/model.service";
import { EventQueueService } from "../../../composer/gui/vs/event-queue.service";
import { FontService } from "../../../widget/services/font.service";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Child component / directive stubs
// The stubs must declare every @Input/@Output that the parent template binds.
// Missing bindings cause NG0300 errors; extra ones are harmless.
// ---------------------------------------------------------------------------

@Component({ selector: "wizard-tool-bar", template: "", standalone: true })
export class WizardToolBarStub {
   @Input() sheet: any;
   @Input() hiddenNewBlock: any;
   @Output() onClose = new EventEmitter<any>();
   @Output() onHiddenNewBlockChanged = new EventEmitter<any>();
}

@Component({ selector: "vs-wizard-grid-pane", template: "", standalone: true })
export class VsWizardGridPaneStub {
   @Input() rowCount: any;
   @Input() colCount: any;
   @Input() cellHeight: any;
   @Input() cellWidth: any;
   @Output() onChangeNewObject = new EventEmitter<any>();
}

@Component({ selector: "wizard-new-object", template: "", standalone: true })
export class WizardNewObjectStub {
   @Input() model: any;
   @Output() toComponentWizard = new EventEmitter<any>();
   @Output() doInsertObject = new EventEmitter<any>();
}

@Component({ selector: "vs-wizard-object", template: "", standalone: true })
export class VsWizardObjectStub {
   @Input() vsObject: any;
   @Input() viewsheet: any;
   @Input() willFollow: any;
   @Input() heightIncrement: any;
   @Input() widthIncrement: any;
   @Input() maxHeight: any;
   @Input() maxWidth: any;
   @Output() onRowsChanged = new EventEmitter<any>();
   @Output() onColsChanged = new EventEmitter<any>();
   @Output() onDragResizeStart = new EventEmitter<any>();
   @Output() onDragResizeEnd = new EventEmitter<any>();
   @Output() onResize = new EventEmitter<any>();
   @Output() onMove = new EventEmitter<any>();
   @Output() onRemove = new EventEmitter<any>();
   @Output() onEdit = new EventEmitter<any>();
   @Output() onMouseIn = new EventEmitter<any>();
   @Output() onChangeFollowDirection = new EventEmitter<any>();
}

@Directive({ selector: "[selectionBox]", standalone: true })
export class SelectionBoxStub {
   @Input() selectionBoxBannedSelector: any;
   @Input() selectOnMouseMove: any;
   @Output() onSelectionBox = new EventEmitter<any>();
}

@Directive({ selector: "[wInteractContainer]", standalone: true })
export class InteractContainerStub {
   @Input() draggableRestriction: any;
}

@Directive({ selector: "[outOfZone]", standalone: true })
export class OutOfZoneStub {
   @Output() onDocKeydown = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const commandsSubject = new Subject<any>();

export const CLIENT_SERVICE_MOCK = {
   commands: commandsSubject,
   sendEvent: vi.fn(),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const EVENT_QUEUE_MOCK = {
   addWizardMoveEvent: vi.fn(),
};

export const FONT_SERVICE_MOCK = {
   getFontFamilies: vi.fn().mockReturnValue([]),
   getFontList: vi.fn().mockReturnValue([]),
   getStyleList: vi.fn().mockReturnValue([]),
   getFontSizeList: vi.fn().mockReturnValue([]),
};

export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn(),
   putModel: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared reset — call in each spec file's top-level beforeEach
// ---------------------------------------------------------------------------

export function resetMocks(): void {
   CLIENT_SERVICE_MOCK.sendEvent.mockClear();
   EVENT_QUEUE_MOCK.addWizardMoveEvent.mockClear();
   MODEL_SERVICE_MOCK.getModel.mockClear();
   MODEL_SERVICE_MOCK.putModel.mockClear();
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
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
   comp: VsWizardPane;
   fixture: any;
}

export async function renderComponent(): Promise<RenderResult> {
   const { fixture } = await render(VsWizardPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [provideHttpClient()],
      importOverrides: [
         { replace: WizardToolBarComponent, with: WizardToolBarStub },
         { replace: VsWizardGridPaneComponent, with: VsWizardGridPaneStub },
         { replace: WizardNewObject, with: WizardNewObjectStub },
         { replace: VsWizardObjectComponent, with: VsWizardObjectStub },
         { replace: SelectionBoxDirective, with: SelectionBoxStub },
         { replace: InteractContainerDirective, with: InteractContainerStub },
         { replace: OutOfZoneDirective, with: OutOfZoneStub },
      ],
      componentProviders: [
         { provide: ViewsheetClientService, useValue: CLIENT_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: EventQueueService, useValue: EVENT_QUEUE_MOCK },
         { provide: FontService, useValue: FONT_SERVICE_MOCK },
      ],
   });
   return { comp: fixture.componentInstance as VsWizardPane, fixture };
}
