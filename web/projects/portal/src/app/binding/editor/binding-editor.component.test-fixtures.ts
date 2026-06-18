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
 * Shared test fixtures for BindingEditor multi-pass .tl.spec.ts files.
 * Consumed by:
 *   binding-editor.component.interaction.tl.spec.ts  (P1)
 *   binding-editor.component.display.tl.spec.ts      (P3)
 */

import { Component, Input, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { BindingEditor } from "./binding-editor.component";
import { BindingService } from "../services/binding.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ModelService } from "../../widget/services/model.service";

// heavy child components (import only for importOverrides identity comparison)
import { EditorTitleBar } from "./editor-title-bar.component";
import { SplitPane } from "../../widget/split-pane/split-pane.component";
import { DataEditorTabPane } from "../widget/binding-tree/data-editor-tab-pane.component";
import { FormatsPane } from "./formats-pane.component";
import { DataEditorBindingTree } from "../widget/binding-tree/data-editor-binding-tree.component";
import { ChartEditorToolbar } from "./chart/chart-editor-toolbar.component";
import { ChartHighLowPane } from "./chart/chart-high-low-pane.component";
import { AestheticPane } from "./chart/aesthetic/aesthetic-pane.component";
import { TableOption } from "./table/table-option.component";
import { CrosstabOption } from "./table/crosstab-option.component";
import { CalcOptionPane } from "./table/calc-option-pane.component";
import { ChartDataPane } from "./chart/chart-data-pane.component";
import { TableDataPane } from "./table/table-data-pane.component";
import { CrosstabDataPane } from "./table/crosstab-data-pane.component";
import { CalcDataPane } from "./table/calc-data-pane.component";
import { StatusBar } from "../../status-bar/status-bar.component";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { ConsoleDialogComponent } from "../../widget/console-dialog/console-dialog.component";

import type { BindingModel } from "../data/binding-model";

// ---------------------------------------------------------------------------
// Stubs — empty shells to block cascading DI failures from child components.
// ---------------------------------------------------------------------------

@Component({ selector: "editor-title-bar", template: "", standalone: true })
export class EditorTitleBarStub {}

@Component({ selector: "split-pane", template: "<ng-content></ng-content>", standalone: true })
export class SplitPaneStub {}

@Component({ selector: "data-editor-tab-pane", template: "", standalone: true })
export class DataEditorTabPaneStub {}

@Component({ selector: "formats-pane", template: "", standalone: true })
export class FormatsPaneStub {}

@Component({ selector: "data-editor-binding-tree", template: "", standalone: true })
export class DataEditorBindingTreeStub {}

@Component({ selector: "chart-editor-toolbar", template: "", standalone: true })
export class ChartEditorToolbarStub {}

@Component({ selector: "chart-high-low-pane", template: "", standalone: true })
export class ChartHighLowPaneStub {}

@Component({ selector: "aesthetic-pane", template: "", standalone: true })
export class AestheticPaneStub {}

@Component({ selector: "table-option", template: "", standalone: true })
export class TableOptionStub {}

@Component({ selector: "crosstab-option", template: "", standalone: true })
export class CrosstabOptionStub {}

@Component({ selector: "calc-option-pane", template: "", standalone: true })
export class CalcOptionPaneStub {}

@Component({ selector: "chart-data-pane", template: "", standalone: true })
export class ChartDataPaneStub {}

@Component({ selector: "table-data-pane", template: "", standalone: true })
export class TableDataPaneStub {}

@Component({ selector: "crosstab-data-pane", template: "", standalone: true })
export class CrosstabDataPaneStub {}

@Component({ selector: "calc-data-pane", template: "", standalone: true })
export class CalcDataPaneStub {}

@Component({ selector: "status-bar", template: "", standalone: true })
export class StatusBarStub {}

// NotificationsComponent stub exposes info/warning/danger for ViewChild calls in tests
@Component({ selector: "notifications", template: "", standalone: true })
export class NotificationsStub {
   @Input() timeout: any;
   info    = vi.fn();
   warning = vi.fn();
   danger  = vi.fn();
}

@Component({ selector: "console-dialog", template: "", standalone: true })
export class ConsoleDialogStub {}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const BINDING_SERVICE_MOCK: {
   assemblyName: string | null;
   runtimeId: string | null;
   objectType: string | null;
   bindingModel: BindingModel | null;
   variableValues: string[] | null;
   setGrayedOutFields: ReturnType<typeof vi.fn>;
   clear: ReturnType<typeof vi.fn>;
} = {
   assemblyName: null,
   runtimeId: null,
   objectType: null,
   bindingModel: null,
   variableValues: null,
   setGrayedOutFields: vi.fn(),
   clear: vi.fn(),
};

export const UI_CONTEXT_MOCK = {
   isVS: vi.fn().mockReturnValue(true),
};

export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn(),
};

export const MODAL_MOCK = {
   open: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared reset — call in each spec file's top-level beforeEach
// ---------------------------------------------------------------------------

export function resetMocks(): void {
   BINDING_SERVICE_MOCK.assemblyName = null;
   BINDING_SERVICE_MOCK.runtimeId    = null;
   BINDING_SERVICE_MOCK.objectType   = null;
   BINDING_SERVICE_MOCK.bindingModel = null;
   BINDING_SERVICE_MOCK.variableValues = null;
   BINDING_SERVICE_MOCK.setGrayedOutFields.mockClear();
   BINDING_SERVICE_MOCK.clear.mockClear();

   UI_CONTEXT_MOCK.isVS.mockClear().mockReturnValue(true);

   MODEL_SERVICE_MOCK.getModel.mockClear();

   MODAL_MOCK.open.mockClear();
}

// ---------------------------------------------------------------------------
// importOverrides list — shared by both spec files
// ---------------------------------------------------------------------------

export const IMPORT_OVERRIDES = [
   { replace: EditorTitleBar,         with: EditorTitleBarStub },
   { replace: SplitPane,              with: SplitPaneStub },
   { replace: DataEditorTabPane,      with: DataEditorTabPaneStub },
   { replace: FormatsPane,            with: FormatsPaneStub },
   { replace: DataEditorBindingTree,  with: DataEditorBindingTreeStub },
   { replace: ChartEditorToolbar,     with: ChartEditorToolbarStub },
   { replace: ChartHighLowPane,       with: ChartHighLowPaneStub },
   { replace: AestheticPane,          with: AestheticPaneStub },
   { replace: TableOption,            with: TableOptionStub },
   { replace: CrosstabOption,         with: CrosstabOptionStub },
   { replace: CalcOptionPane,         with: CalcOptionPaneStub },
   { replace: ChartDataPane,          with: ChartDataPaneStub },
   { replace: TableDataPane,          with: TableDataPaneStub },
   { replace: CrosstabDataPane,       with: CrosstabDataPaneStub },
   { replace: CalcDataPane,           with: CalcDataPaneStub },
   { replace: StatusBar,              with: StatusBarStub },
   { replace: NotificationsComponent, with: NotificationsStub },
   { replace: ConsoleDialogComponent, with: ConsoleDialogStub },
];

// ---------------------------------------------------------------------------
// Shared render helper
// ---------------------------------------------------------------------------

export interface RenderOptions {
   runtimeId?: string;
   objectType?: string;
   assemblyName?: string;
   bindingModel?: BindingModel;
   rmode?: number;
}

export interface RenderResult {
   comp: BindingEditor;
   fixture: any;
}

export async function renderComponent(opts: RenderOptions = {}): Promise<RenderResult> {
   const { fixture } = await render(BindingEditor, {
      inputs: {
         runtimeId:    opts.runtimeId    ?? "rt-test",
         objectType:   opts.objectType   ?? "VSChart",
         assemblyName: opts.assemblyName,
         bindingModel: opts.bindingModel,
         rmode:        opts.rmode,
      },
      providers: [
         { provide: BindingService,   useValue: BINDING_SERVICE_MOCK },
         { provide: UIContextService, useValue: UI_CONTEXT_MOCK },
         { provide: ModelService,     useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal,         useValue: MODAL_MOCK },
      ],
      importOverrides: IMPORT_OVERRIDES,
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: fixture.componentInstance as BindingEditor, fixture };
}
